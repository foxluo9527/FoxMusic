package com.fox.music.iosshared.ui

import com.fox.music.core.data.mapper.toMessage
import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageType
import com.fox.music.core.model.user.User
import com.fox.music.iosshared.FoxChatRefresh
import com.fox.music.iosshared.FoxLocalChatStore
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.foxMainScope
import kotlinx.coroutines.launch

/**
 * 选人/分享/转发的 Swift 桥接层（对齐 Android SelectFriendScreen +
 * ChatApiService.sendShareMessage + ChatDetailViewModel.forwardMessage）。
 */

/** 选人页拍平后的好友数据（Swift 直接渲染）。 */
data class FoxPickFriend(
    val id: Long,
    val name: String,
    val avatar: String?,
)

/** 选择好友：好友列表（api/friends），Swift 侧自行按昵称本地过滤。 */
object FoxFriendPicker {

    private val api = FoxMusicApi()

    fun loadFriends(
        onSuccess: (List<FoxPickFriend>) -> Unit,
        onError: (String) -> Unit,
    ) {
        foxMainScope().launch {
            try {
                val friends = api.fetchFriends().map { dto ->
                    FoxPickFriend(
                        id = dto.id,
                        name = dto.mark?.takeIf { it.isNotBlank() }
                            ?: dto.nickname?.takeIf { it.isNotBlank() }
                            ?: dto.username?.takeIf { it.isNotBlank() }
                            ?: "用户",
                        avatar = dto.avatar,
                    )
                }
                onSuccess(friends)
            } catch (e: Exception) {
                onError(e.message ?: "好友加载失败")
            }
        }
    }
}

/** 发送名片/卡片消息（music/playlist/album/artist/post/user）。 */
object FoxShareSender {

    private val api = FoxMusicApi()

    fun send(
        receiverId: Long,
        shareType: String,
        shareId: Long,
        content: String = "",
        /**
         * 接收方昵称/头像：站外（歌曲菜单/名片/帖子）分享时会话可能根本不存在于本地，
         * 不补身份的话新会话行只能显示兜底「用户」+ 默认头像。由选人页透传。
         */
        receiverNickname: String? = null,
        receiverAvatar: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        foxMainScope().launch {
            try {
                val dto = api.sendShareMessage(
                    receiverId = receiverId,
                    shareType = shareType,
                    shareId = shareId,
                    content = content,
                )
                // iOS 会话列表/聊天页只读本地 FoxLocalChatStore（不拉
                // api/messages/conversations），站外分享若不落库，卡片就「消失」了：
                // 列表里会话不更新，进聊天页也要等下一次轮询。与转发同口径，立即写回。
                dto?.toMessage()?.let { message ->
                    FoxLocalChatStore.upsertMessage(receiverId, message)
                    FoxLocalChatStore.upsertConversation(
                        ChatConversation(
                            id = receiverId,
                            user = User(
                                id = receiverId,
                                username = "",
                                nickname = receiverNickname,
                                avatar = receiverAvatar,
                            ),
                            lastMessage = message,
                            unreadCount = 0,
                            updatedAt = message.createdAt,
                        ),
                    )
                    FoxChatRefresh.bumpConversations()
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "分享失败")
            }
        }
    }
}

/**
 * 聊天消息转发 —— 语义逐项对齐 Android ChatDetailViewModel.forwardMessage：
 *  - 文本 → 重发文本；
 *  - 图片/文件/音乐 → 以远程媒体 URL 重发文件消息；
 *  - 语音 → 不支持转发；
 *  - 卡片 → 走 messages/share。
 *
 * 成功后把消息落进目标会话的本地存储（对齐 Android Room 即时可见）。
 */
object FoxMessageForwarder {

    private val api = FoxMusicApi()

    fun forward(
        message: Message,
        targetUserId: Long,
        targetNickname: String?,
        targetAvatar: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        when (message.type) {
            MessageType.AUDIO -> {
                onError("语音消息不支持转发")
                return
            }
            else -> Unit
        }
        foxMainScope().launch {
            try {
                val dto = when (message.type) {
                    MessageType.TEXT -> api.sendTextMessage(targetUserId, message.content)
                    MessageType.SHARE -> {
                        val shareId = message.shareId ?: 0L
                        if (shareId <= 0L) {
                            onError("无法转发此消息")
                            return@launch
                        }
                        api.sendShareMessage(
                            receiverId = targetUserId,
                            shareType = message.shareType ?: "music",
                            shareId = shareId,
                            content = message.content,
                        )
                    }
                    MessageType.IMAGE,
                    MessageType.FILE,
                    MessageType.MUSIC -> {
                        val mediaUrl = message.remoteMediaUrl
                        if (mediaUrl.isNullOrBlank()) {
                            onError("无法转发此消息")
                            return@launch
                        }
                        api.sendMediaMessage(
                            receiverId = targetUserId,
                            type = "file",
                            fileUrl = mediaUrl,
                            fileName = message.localMediaFileName,
                            fileType = message.fileType,
                            videoUrl = message.videoUrl,
                        )
                    }
                    MessageType.AUDIO -> null // 上面已拦截
                }
                if (dto != null) {
                    val mapped = dto.toMessage()
                    FoxLocalChatStore.upsertMessage(targetUserId, mapped)
                    FoxLocalChatStore.upsertConversation(
                        ChatConversation(
                            id = targetUserId,
                            user = User(
                                id = targetUserId,
                                username = "",
                                nickname = targetNickname,
                                avatar = targetAvatar,
                            ),
                            lastMessage = mapped,
                            unreadCount = 0,
                            updatedAt = mapped.createdAt,
                        ),
                    )
                    FoxChatRefresh.bumpConversations()
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "转发失败")
            }
        }
    }
}
