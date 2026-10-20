package com.fox.music.iosshared.realtime

import com.fox.music.core.common.util.FoxLog
import com.fox.music.core.data.mapper.toMessage
import com.fox.music.core.data.realtime.RealtimeMessageGateway
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.user.User
import com.fox.music.core.network.FoxNetworkConfig
import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.MessageDto
import com.fox.music.iosshared.FoxChatRefresh
import com.fox.music.iosshared.FoxLocalChatStore
import com.fox.music.iosshared.normalizeBearerToken
import com.fox.music.iosshared.FoxAuthStore
import com.fox.music.iosshared.FoxPeerUnread
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * iOS 侧的消息落地出口 —— 实时协调器与「存储」之间的那一层。
 *
 * Android 的实现委托给 ChatRepository（Room 落库）；iOS 目前**没有本地消息库**
 * （消息功能本身还没迁到 iOS），所以这里的职责收敛成两件现在就有意义的事：
 *
 *   1. [syncUnread]：调后端 `api/messages/unread/{peerUserId}` 把未读数拉回来 ——
 *      这是「退出 App 期间的消息不丢」的兜底。iOS 进后台会被挂起、socket 断开，
 *      后台期间的消息只能靠回前台这一次补拉拿到（APNs 本项目不做）。
 *   2. 把未读数通过 [unreadCount] 暴露给 Swift，Tab 角标 / 消息入口直接订阅。
 *
 * 为什么不是空实现：如果这里什么都不做，「退出就算了」就真的变成「消息丢了」——
 * 服务端的未读记录是唯一真相，必须有一条路径能把它取回来。
 *
 * 将来 iOS 消息页落地、引入本地库（SQLDelight）时，只需在这里补落库逻辑，
 * 上层（RealtimeMessageCoordinator / Swift）零改动。
 */
class IosRealtimeMessageGateway : RealtimeMessageGateway {

    private val client = HttpClient(Darwin) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    explicitNulls = false
                }
            )
        }
        // 与 FoxMusicApi 相同的约定：token 已带 `Bearer ` 前缀，不能再拼一次。
        defaultRequest {
            normalizeBearerToken(FoxAuthStore.token)?.let {
                header(HttpHeaders.Authorization, it)
            }
        }
    }

    private val _unreadCount = MutableStateFlow(0)

    /** 服务端未读消息数（未登录或拉取失败时保持上一次的值）。 */
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    /**
     * 收到对方实时推送的聊天消息。
     *
     * 做两件事（真正的对账仍由回前台的 [syncUnread] 完成，服务端才是真相）：
     *  1. 消息落库（不落就丢了，见 [syncUnread] 注释）；
     *  2. **会话维度**未读 +1 —— 否则会话列表永远显示 0 未读
     *     （会话列表的 unreadCount 只认本地 FoxLocalChatStore）。
     *     Tab 红点随后由 [syncBadgeFromConversations] 从会话求和得出，天然同步。
     *
     * ⚠️ 协调器对本账号自己发出的消息也会调这里（判重在其后），必须先过滤。
     */
    override suspend fun onIncomingMessage(message: Message) {
        val selfId = FoxAuthStore.user?.id ?: 0L
        if (selfId > 0L && message.senderId == selfId) return
        if (message.senderId <= 0L) return
        FoxLocalChatStore.upsertMessage(message.senderId, message)
        FoxLocalChatStore.bumpUnread(
            peer = User(
                id = message.senderId,
                username = "",
                nickname = null,
                avatar = null,
            ),
            delta = 1,
            lastMessage = message,
        )
        FoxChatRefresh.bumpConversations()
        syncBadgeFromConversations()
    }

    /** 收到聊天类通知：能定位到发送方就按会话维度累加，否则只给全局 +1。 */
    override suspend fun onMessageNotification(notification: Notification) {
        if (notification.isRead) return
        val peer = notification.sender
            ?: notification.senderId?.takeIf { it > 0L }?.let {
                User(id = it, username = "", nickname = null, avatar = null)
            }
        if (peer == null) {
            _unreadCount.value = _unreadCount.value + 1
            return
        }
        FoxLocalChatStore.bumpUnread(
            peer = peer,
            delta = 1,
            lastMessage = Message(
                id = notification.id,
                localId = null,
                senderId = peer.id,
                receiverId = FoxAuthStore.user?.id ?: 0L,
                content = notification.content,
                createdAt = notification.createdAt,
            ),
        )
        FoxChatRefresh.bumpConversations()
        syncBadgeFromConversations()
    }

    /**
     * Tab 红点 = 各会话未读之和。
     *
     * 之前是「服务端本次拉回的条数」，而后端 unread 是一次性消费 —— 第二次拉永远是 0，
     * 于是「读了 A → 重算」会把 B 的未读一起抹成 0（红点没了，列表里 B 还挂着数）。
     * 改成从本地会话求和，红点与列表永远同一个口径。
     */
    private fun syncBadgeFromConversations() {
        _unreadCount.value = FoxLocalChatStore.totalUnread()
    }

    /**
     * 回前台补拉未读。[peerUserId] 传 0 表示「全部未读」（与后端约定一致）。
     *
     * ⚠️ 后端 unread 接口是**一次性消费**（查询后立刻置 is_notified=1），而聊天页的
     * 轮询用的同一个接口 —— 网关把消息拉回来后**必须落库**（FoxLocalChatStore），
     * 否则聊天页永远拿不到这批消息（曾因此出现「注入了消息但聊天页一直空」）。
     * 落库后 bump 会话刷新总线，让会话列表与打开中的聊天页重新加载本地存储。
     *
     * 失败不抛：这是兜底路径，网络抖动时保持上次的未读数即可，
     * 抛出去只会让协调器的 runCatching 多打一行日志。
     */
    override suspend fun syncUnread(peerUserId: Long) {
        if (!FoxAuthStore.isLoggedIn) {
            _unreadCount.value = 0
            return
        }
        try {
            val list = fetchUnread(peerUserId)
            archiveMessages(list)
            // 只有「全量未读」（peerUserId == 0）才按发送方累加到会话维度。
            // 指定会话的那次（markPeerRead 里消费当前会话）只落消息，
            // 未读由后面的 markConversationRead 归零，不能重复累加。
            if (peerUserId == 0L && list.isNotEmpty()) {
                FoxLocalChatStore.accumulateUnread(buildSnapshot(list))
                FoxChatRefresh.bumpConversations()
            }
            syncBadgeFromConversations()
            FoxLog.i(TAG, "syncUnread ok: ${list.size} 条未读")
        } catch (e: Exception) {
            FoxLog.w(TAG, "syncUnread error: ${e.message}")
        }
    }

    /**
     * 进入某个会话（读完该会话）：先消费掉这一会话的未读，再按服务端剩余未读重算角标。
     *
     * ⚠️ 为什么不能只调 `syncUnread(0)`：后端 unread 接口按 **`is_notified = 0`** 过滤
     * （一次性消费），而「标记已读」只 `UPDATE messages SET is_read = 1`，
     * **不会让未读数下降** —— 直接拉全量，刚点开的会话仍会被算进去，Tab 红点不消失。
     * 所以必须先 [fetchUnread] 这个会话（服务端顺手把这批标成已通知），
     * 再 [markConversationRead] 把本会话清零、拉一次全量把其它会话的未读补累加，
     * 红点（= 各会话未读之和）才会掉。
     *
     * 失败不抛：角标是提示性 UI，网络抖动时保持上次的值即可。
     */
    suspend fun markPeerRead(peerUserId: Long) {
        if (peerUserId <= 0L || !FoxAuthStore.isLoggedIn) return
        try {
            // 这一批是「刚点开的会话」的未读：落库即可，别再累加（用户正在读）
            archiveMessages(fetchUnread(peerUserId))
            FoxLocalChatStore.markConversationRead(peerUserId)
            // 再拉一次全量：把**其它**会话期间到达的未读补累加进来
            syncUnread(0L)
            FoxChatRefresh.bumpConversations()
        } catch (e: Exception) {
            FoxLog.w(TAG, "markPeerRead error: ${e.message}")
        }
    }

    /** 拉一次未读（副作用：服务端会把这批消息标成已通知）。失败返回空列表。 */
    private suspend fun fetchUnread(peerUserId: Long): List<MessageDto> {
        val response: ApiResponse<List<MessageDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/messages/unread/$peerUserId")
            .body()
        if (!response.isSuccess) {
            FoxLog.w(TAG, "syncUnread failed: ${response.message}")
            return emptyList()
        }
        return response.data.orEmpty()
    }

    /**
     * 未读落库：都是「别人发给我」的消息，按发送方归档到各自的会话存储。
     * 落库后 bump 会话刷新总线，让会话列表与打开中的聊天页重新加载本地存储。
     */
    private fun archiveMessages(list: List<MessageDto>) {
        if (list.isEmpty()) return
        list.map { it.toMessage() }
            .forEach { message ->
                val peerId = message.senderId
                if (peerId > 0L) FoxLocalChatStore.upsertMessage(peerId, message)
            }
        FoxChatRefresh.bumpConversations()
    }

    /**
     * 未读 → 会话维度快照：按发送方聚合条数，并带上发送方资料
     * （MessageDto 有 sender_nickname / sender_avatar，新建会话行时不用再补一次资料接口）。
     */
    private fun buildSnapshot(list: List<MessageDto>): List<FoxPeerUnread> =
        list.mapNotNull { dto ->
            val peerId = dto.senderId
            if (peerId <= 0L) return@mapNotNull null
            dto to FoxPeerUnread(
                peer = User(
                    id = peerId,
                    username = "",
                    nickname = dto.senderNickname?.takeIf { it.isNotBlank() },
                    avatar = dto.senderAvatar?.takeIf { it.isNotBlank() },
                ),
                count = 1,
                lastMessage = dto.toMessage(),
            )
        }
            .groupBy { (dto, _) -> dto.senderId }
            .map { (_, group) ->
                group.map { it.second }.reduce { acc, item ->
                    val newer = item.lastMessage.createdAt?.filter { c -> c.isDigit() }?.toLongOrNull() ?: 0L
                    val current = acc.lastMessage.createdAt?.filter { c -> c.isDigit() }?.toLongOrNull() ?: 0L
                    val lastMessage = if (newer >= current) item.lastMessage else acc.lastMessage
                    FoxPeerUnread(
                        peer = acc.peer,
                        count = acc.count + item.count,
                        lastMessage = lastMessage,
                    )
                }
            }

    /** 本地已读处理后把角标清零（消息页落地后调用）。 */
    fun clearUnread() {
        _unreadCount.value = 0
    }

    private companion object {
        const val TAG = "IosMessageGateway"
    }
}
