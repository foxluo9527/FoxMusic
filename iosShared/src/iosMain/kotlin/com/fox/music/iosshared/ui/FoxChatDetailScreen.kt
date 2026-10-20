package com.fox.music.iosshared.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.fox.music.core.data.mapper.toMessage
import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageStatus
import com.fox.music.core.model.user.User
import com.fox.music.feature.chat.ui.thread.ChatThreadContent
import com.fox.music.feature.chat.ui.thread.ChatThreadState
import com.fox.music.feature.chat.util.parseTimestampMillis
import com.fox.music.iosshared.FoxAuthStore
import com.fox.music.iosshared.FoxChatInputBridge
import com.fox.music.iosshared.FoxChatRefresh
import com.fox.music.iosshared.FoxChatSettings
import com.fox.music.iosshared.FoxLocalChatStore
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.foxComposeViewController
import com.fox.music.iosshared.realtime.FoxRealtimeService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS 端聊天详情页：复用 feature:chat commonMain 的 ChatThreadContent。
 *
 * 数据来源（与 Android 的**根本差异**，必须知悉）：
 *  - Android 的消息主源是本地 Room（`ChatRepository.observeMessages`），发消息走
 *    `ChatMessageSender`（先本地入库 → 后台上传），所以断网也有完整会话；
 *  - iOS 没有本地库，**不拉历史消息**（后端 history 接口有故障且产品不需要首屏历史），
 *    以 `POST api/messages` 发送 + 轮询 `api/messages/unread/{userId}` 增量接收为准，
 *    失败就把气泡标成「发送失败」（可重试）。
 *
 * 实时性：iOS 侧暂用**轮询**（页面可见期间每 4 秒拉一次未读并合并）而不是复用
 * WebSocket —— 现有实时链路（RealtimeMessageCoordinator）是「写入本地库」语义，
 * iOS 无本地库，直接复用会把消息写丢。轮询虽朴素但与页面生命周期绑定，
 * 开销可控（`api/messages/unread/{userId}` 只回未读）。后续统一时把
 * `RealtimeMessageGateway` 扩成「可订阅的消息流」即可去掉轮询。
 *
 * 相册/拍照/文件/语音录制由 Swift 侧桥接（PHPicker / UIDocumentPicker /
 * AVAudioRecorder），语音播放由 Swift 的 VoiceMessagePlayer 承担，播放中态
 * 通过 [FoxChatVoiceController] 回写 [ChatThreadState.playingVoiceLocalId]。
 */

/**
 * 语音播放态回传控制器：Swift 侧语音自然播完时调用 [stopPlaybackState]，
 * 让 Compose 气泡的播放中图标复位（点击同一气泡的停播由 Kotlin 侧自行处理）。
 */
class FoxChatVoiceController {
    internal var reset: (() -> Unit)? = null

    fun stopPlaybackState() {
        reset?.invoke()
    }
}

object FoxChatDetailScreen {

    private val api = FoxMusicApi()

    private const val POLL_INTERVAL_MS = 4_000L

    fun makeChatDetailViewController(
        userId: Long,
        peerNickname: String?,
        peerAvatar: String?,
        onBack: () -> Unit,
        onSettings: (userId: Long, peerNickname: String?, peerAvatar: String?) -> Unit,
        onUserProfile: (userId: Long) -> Unit,
        onMediaPreview: (mediaUrl: String, videoUrl: String?, previewType: String) -> Unit,
        onToast: (String) -> Unit,
        onPickImage: () -> Unit,
        onTakePhoto: () -> Unit,
        onCaptureVideo: () -> Unit,
        onPickFile: () -> Unit,
        onVoiceStart: () -> Unit,
        onVoiceEnd: () -> Unit,
        onVoiceCancel: () -> Unit,
        /** 切到语音输入模式：Swift 侧提前申请麦克风权限，避免按住时弹窗打断手势。 */
        onEnterVoiceMode: () -> Unit,
        /** 长按「转发」：Swift 侧弹选人页，选中后走 FoxMessageForwarder。 */
        onForwardMessage: (Message) -> Unit,
        /**
         * 点击卡片消息：music 时音乐字段拍平在后面几个参数（直接起播），
         * 其余类型（playlist/album/artist/post/user）只用 shareId 做导航。
         */
        onOpenShare: (
            shareType: String,
            shareId: Long,
            musicTitle: String?,
            musicUrl: String?,
            musicCover: String?,
            musicDuration: Long,
            musicArtists: List<String>,
        ) -> Unit,
        /** 点击语音气泡：isPlaying=true 开始播放，false 停止（同一气泡再点一次）。 */
        onPlayVoice: (voiceUrl: String, localId: String, durationMs: Long, isPlaying: Boolean) -> Unit,
        voiceController: FoxChatVoiceController,
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        val scope = rememberCoroutineScope()
        var state by remember {
            mutableStateOf(
                ChatThreadState(
                    peerUserId = userId,
                    peerNickname = peerNickname,
                    peerAvatar = peerAvatar,
                    currentUserId = FoxAuthStore.user?.id ?: 0L,
                    currentUserAvatar = FoxAuthStore.user?.avatarUrl,
                    backgroundImagePath = FoxChatSettings.backgroundPath(userId),
                    isLoading = true,
                ),
            )
        }

        // 聊天信息页改了背景/置顶等设置后回到本页（本 Compose VC 常驻不会重建），
        // 靠 FoxChatSettings.revision 把新背景路径拉回来。
        LaunchedEffect(userId) {
            snapshotFlow { FoxChatSettings.revision }.collect {
                val path = FoxChatSettings.backgroundPath(userId)
                if (path != state.backgroundImagePath) {
                    state = state.copy(backgroundImagePath = path)
                }
            }
        }

        // Swift 侧语音自然播完后通过 controller 复位气泡播放态；离开页面时断开关联。
        DisposableEffect(voiceController) {
            voiceController.reset = { state = state.copy(playingVoiceLocalId = null) }
            onDispose { voiceController.reset = null }
        }

        // 录音计时：按住说话期间每秒 +1，松手/取消即停（对齐 Android ChatDetailScreen）。
        LaunchedEffect(state.isRecordingVoice) {
            if (!state.isRecordingVoice) return@LaunchedEffect
            var seconds = 0
            while (true) {
                delay(1_000)
                if (!state.isRecordingVoice) break
                seconds++
                state = state.copy(recordingDurationSec = seconds)
            }
        }

        /** 合并服务端返回的消息（按「服务端 id 优先，其次 localId」去重，按时间升序）。 */
        fun merge(incoming: List<Message>): List<Message> {
            if (incoming.isEmpty()) return state.messages
            val merged = LinkedHashMap<String, Message>()
            state.messages.forEach { merged[keyOf(it)] = it }
            incoming.forEach { message ->
                val key = keyOf(message)
                val optimistic = merged.entries.firstOrNull { entry ->
                    entry.value.status == MessageStatus.SENDING &&
                        entry.value.senderId == message.senderId &&
                        entry.value.content == message.content
                }
                if (optimistic != null) merged.remove(optimistic.key)
                merged[key] = message
            }
            return merged.values.sortedBy { parseTimestampMillis(it.createdAt) ?: 0L }
        }

        /**
         * 把本会话写进本地会话表（会话列表的数据源之一）。
         *
         * 对齐 Android：Android 发消息时 `conversationDao.upsertConversation(...)` 立刻落
         * 本地会话（ChatRepositoryImpl），所以发完退回列表一定能看到这一条。
         * iOS 之前**只落消息不落会话**，而后端 `api/messages/conversations` 又还没部署
         * （线上 404），于是「发完消息 → 会话列表里没有刚才的对话」。
         */
        fun syncLocalConversation(lastMessage: Message) {
            val peer = User(
                id = userId,
                username = "",
                nickname = state.peerNickname,
                avatar = state.peerAvatar,
            )
            FoxLocalChatStore.upsertConversation(
                ChatConversation(
                    id = userId,
                    user = peer,
                    lastMessage = lastMessage,
                    unreadCount = 0,
                    updatedAt = lastMessage.createdAt,
                ),
            )
            // 通知消息页重新拉列表（它的 Compose VC 常驻，返回时不会自己刷新）
            FoxChatRefresh.bumpConversations()
        }

        suspend fun poll() {
            if (!FoxAuthStore.isLoggedIn) return
            val unread = runCatching { api.fetchUnreadMessages(userId) }
                .onFailure { println("FoxChatPoll[$userId]: fetch failed: ${it::class.simpleName}: ${it.message}") }
                .getOrNull() ?: return
            if (unread.isEmpty()) return
            val mapped = unread.map { it.toMessage() }
            state = state.copy(messages = merge(mapped))
            // 写回本地存储
            mapped.forEach { FoxLocalChatStore.upsertMessage(userId, it) }
            mapped.lastOrNull()?.let { syncLocalConversation(it) }
            runCatching { api.markChatAsRead(userId) }
            // 聊天中对方又发来消息时也要重算（WebSocket 收到会 +1，这里消费掉后归位）
            FoxRealtimeService.markPeerRead(userId)
        }

        /**
         * 进入会话：
         *  1. 先从本地 JSON 加载历史消息（断网也能看）；
         *  2. 清掉未读角标；
         *  3. 轮询增量接收新消息并写回本地。
         */
        LaunchedEffect(userId) {
            // 从会话列表点进来只带 userId（昵称传 null）。先用本地已存会话回填，
            // 否则：① 标题要等网络资料回来才显示；② 下面 syncLocalConversation 会在
            // 资料拉到之前用 null 昵称覆盖本地会话（列表退回兜底「用户」）。
            val storedPeer = FoxLocalChatStore.loadConversations()
                .firstOrNull { it.user.id == userId }?.user
            if (storedPeer != null) {
                state = state.copy(
                    peerNickname = state.peerNickname?.takeIf { it.isNotBlank() }
                        ?: storedPeer.nickname?.takeIf { it.isNotBlank() },
                    peerAvatar = state.peerAvatar?.takeIf { it.isNotBlank() }
                        ?: storedPeer.avatar?.takeIf { it.isNotBlank() },
                )
            }
            // 本地历史
            val local = FoxLocalChatStore.loadMessages(userId)
            // 清理孤儿「发送中」：id==0 且不在 outbox 里 → 永远不会有回写来替换它
            // （历史遗留：旧版发送成功后没带原 localId 回写，乐观记录卡在 SENDING）。
            val outboxIds = FoxLocalChatStore.loadOutbox().mapNotNull { it.localId }.toSet()
            val cleaned = local.filterNot {
                it.id == 0L && it.status == MessageStatus.SENDING && it.localId !in outboxIds
            }
            if (cleaned.size != local.size) FoxLocalChatStore.saveMessages(userId, cleaned)
            state = state.copy(isLoading = false, messages = cleaned)
            // 本地已有往来 → 会话列表里就该有这一条（后端 conversations 未部署时的兜底）
            local.lastOrNull()?.let { syncLocalConversation(it) }
            if (!FoxAuthStore.isLoggedIn) return@LaunchedEffect
            // 昵称/头像缺失时补一次资料：从会话列表点进来只带 userId，若不补，
            // 会话列表那一行会显示成 displayName 的兜底值「用户」。
            if (state.peerNickname.isNullOrBlank()) {
                val profile = runCatching { api.fetchUserHomeProfile(userId) }.getOrNull()
                if (profile != null) {
                    state = state.copy(
                        peerNickname = profile.nickname,
                        peerAvatar = profile.avatar ?: state.peerAvatar,
                    )
                    state.messages.lastOrNull()?.let { syncLocalConversation(it) }
                }
            }
            runCatching { api.markChatAsRead(userId) }
            // 会话列表的未读数只认本地 FoxLocalChatStore（后端没有 conversations 接口），
            // 进会话先把这一行清零 —— 否则「收到 3 条未读 → 点进去 → 退回列表」数字还在。
            if (FoxLocalChatStore.markConversationRead(userId)) FoxChatRefresh.bumpConversations()
            // 标记已读不会让服务端未读数下降（unread 按 is_notified 过滤）：
            // 消费掉本会话未读后重算一次，Tab 红点才会掉。
            FoxRealtimeService.markPeerRead(userId)
            while (true) {
                delay(POLL_INTERVAL_MS)
                runCatching { poll() }
            }
        }
        // 全局网关回前台 syncUnread 也会消费服务端未读并把消息落库（见
        // IosRealtimeMessageGateway）—— 这条路径不带本页轮询的合并结果。
        // 订阅会话刷新总线：总线 bump 后把本地存储重新合并进来，保证
        // 「网关先拉走了消息」时当前会话也能显示。
        LaunchedEffect(userId) {
            var lastSeen = FoxChatRefresh.conversationRevision
            snapshotFlow { FoxChatRefresh.conversationRevision }.collect { revision ->
                if (revision != lastSeen) {
                    lastSeen = revision
                    val local = FoxLocalChatStore.loadMessages(userId)
                    if (local.isNotEmpty()) state = state.copy(messages = merge(local))
                }
            }
        }

        fun sendText(text: String) {
            val content = text.trim()
            if (content.isBlank()) return
            val localId = "local_${nowMillis()}"
            val optimistic = Message(
                id = 0L,
                localId = localId,
                senderId = state.currentUserId,
                receiverId = userId,
                content = content,
                type = com.fox.music.core.model.chat.MessageType.TEXT,
                status = MessageStatus.SENDING,
                createdAt = null,
            )
            state = state.copy(
                messages = state.messages + optimistic,
                inputText = "",
                showEmojiPanel = false,
                showAttachmentPanel = false,
                isSending = true,
            )
            // 本地持久化 + 入发送队列
            FoxLocalChatStore.upsertMessage(userId, optimistic)
            FoxLocalChatStore.addToOutbox(optimistic)
            // 立刻落会话：即使发送失败/后端会话接口不可用，列表里也能看到刚聊的人
            syncLocalConversation(optimistic)
            scope.launch {
                try {
                    val sent = api.sendTextMessage(userId, content)
                    val replacement = sent?.toMessage()
                    // ⚠️ 回写必须带原 localId：服务端返回的消息 localId 是 "server_N"，
                    // 若原样 upsert，store 里按 localId 匹配不到乐观记录 → 追加成重复条目，
                    // 原记录永远卡在「发送中」。带上原 localId 才能原地替换。
                    val stored = (replacement?.copy(localId = localId))
                        ?: optimistic.copy(status = MessageStatus.SENT)
                    state = state.copy(
                        messages = state.messages.map { if (it.localId == localId) stored else it },
                        isSending = false,
                    )
                    // 写回本地 + 移出队列
                    FoxLocalChatStore.upsertMessage(userId, stored)
                    FoxLocalChatStore.removeFromOutbox(localId)
                    syncLocalConversation(stored)
                } catch (e: Exception) {
                    state = state.copy(
                        messages = state.messages.map {
                            if (it.localId == localId) {
                                it.copy(status = MessageStatus.FAILED, errorMessage = e.message)
                            } else {
                                it
                            }
                        },
                        isSending = false,
                    )
                    // 失败也持久化（标记 FAILED），队列保留供重试
                    FoxLocalChatStore.upsertMessage(userId, optimistic.copy(status = MessageStatus.FAILED, errorMessage = e.message))
                    onToast(e.message ?: "发送失败")
                }
            }
        }

        /** 发送媒体消息（image/video/audio/file）—— Swift 侧选好/上传后通过桥接调用。 */
        fun sendMedia(type: String, url: String, durationMs: Long?, fileName: String?) {
            val localId = "local_${nowMillis()}"
            val msgType = when (type) {
                "image" -> com.fox.music.core.model.chat.MessageType.IMAGE
                "video" -> com.fox.music.core.model.chat.MessageType.IMAGE // 视频复用图片预览
                "audio", "voice" -> com.fox.music.core.model.chat.MessageType.AUDIO
                else -> com.fox.music.core.model.chat.MessageType.FILE
            }
            val optimistic = Message(
                id = 0L,
                localId = localId,
                senderId = state.currentUserId,
                receiverId = userId,
                content = fileName ?: url,
                type = msgType,
                status = MessageStatus.SENDING,
                remoteMediaUrl = url,
                audioDurationMs = durationMs,
                localMediaFileName = fileName,
            )
            state = state.copy(
                messages = state.messages + optimistic,
                showAttachmentPanel = false,
                isSending = true,
            )
            FoxLocalChatStore.upsertMessage(userId, optimistic)
            syncLocalConversation(optimistic)
            scope.launch {
                try {
                    val sent = api.sendMediaMessage(userId, type, url, fileName, null, durationMs)
                    // 同文本路径：回写必须带原 localId，原地替换乐观记录，避免重复。
                    // 服务端回包只保证它认识的列完整（语音列仅在 type=voice 时回吐），
                    // 这里对媒体字段做「只补不删」：缺失时沿用乐观消息已知的类型/地址/时长，
                    // 免得回包缺字段时语音气泡被降级成 content="/" 的 TEXT。
                    val replacement = sent?.toMessage()?.let { server ->
                        server.copy(
                            localId = localId,
                            type = if (server.type == com.fox.music.core.model.chat.MessageType.TEXT &&
                                optimistic.type != com.fox.music.core.model.chat.MessageType.TEXT
                            ) {
                                optimistic.type
                            } else {
                                server.type
                            },
                            remoteMediaUrl = server.remoteMediaUrl ?: optimistic.remoteMediaUrl,
                            audioDurationMs = server.audioDurationMs ?: optimistic.audioDurationMs,
                            localMediaFileName = server.localMediaFileName ?: optimistic.localMediaFileName,
                        )
                    }
                    state = state.copy(
                        messages = state.messages.map { if (it.localId == localId) (replacement ?: it.copy(status = MessageStatus.SENT)) else it },
                        isSending = false,
                    )
                    val stored = replacement ?: optimistic.copy(status = MessageStatus.SENT)
                    FoxLocalChatStore.upsertMessage(userId, stored)
                    syncLocalConversation(stored)
                } catch (e: Exception) {
                    state = state.copy(
                        messages = state.messages.map {
                            if (it.localId == localId) it.copy(status = MessageStatus.FAILED, errorMessage = e.message) else it
                        },
                        isSending = false,
                    )
                    FoxLocalChatStore.upsertMessage(userId, optimistic.copy(status = MessageStatus.FAILED, errorMessage = e.message))
                    onToast(e.message ?: "发送失败")
                }
            }
        }

        // 注册 Swift → Kotlin 媒体输入桥
        DisposableEffect(userId) {
            FoxChatInputBridge.onMediaResult = { type, url, durationMs, fileName ->
                sendMedia(type, url, durationMs, fileName)
            }
            onDispose { FoxChatInputBridge.onMediaResult = null }
        }

        ChatThreadContent(
            state = state,
            onBack = onBack,
            // iOS 无独立齿轮：showSettingsAction=false 隐藏齿轮，
            // 顶栏唯一的「⋮ 更多」按钮走 onSettings 回调（Swift 侧进入聊天信息页）。
            showSettingsAction = false,
            onSettingsClick = {
                onSettings(userId, state.peerNickname, state.peerAvatar)
            },
            onMoreClick = {
                onSettings(userId, state.peerNickname, state.peerAvatar)
            },
            onAvatarClick = { senderId ->
                // 点对方头像进资料页；点自己的不动
                if (senderId != state.currentUserId) onUserProfile(senderId)
            },
            onInputChange = { state = state.copy(inputText = it) },
            onSendClick = { sendText(state.inputText) },
            onSendEmoji = { emoji ->
                val next = state.inputText + emoji
                state = state.copy(inputText = next)
            },
            onToggleEmoji = {
                state = state.copy(
                    showEmojiPanel = !state.showEmojiPanel,
                    showAttachmentPanel = false,
                )
            },
            onToggleAttachment = {
                state = state.copy(
                    showAttachmentPanel = !state.showAttachmentPanel,
                    showEmojiPanel = false,
                )
            },
            onToggleVoiceInputMode = {
                // 语音模式与表情/附件面板互斥；退出语音模式时清掉录音态。
                val entering = !state.isVoiceInputMode
                state = state.copy(
                    isVoiceInputMode = entering,
                    showEmojiPanel = false,
                    showAttachmentPanel = false,
                    isRecordingVoice = false,
                    recordingDurationSec = 0,
                )
                if (entering) onEnterVoiceMode()
            },
            onVoicePressStart = {
                // 已在录音中（按住期间重复回调）不重置计时。
                if (!state.isRecordingVoice) {
                    state = state.copy(isRecordingVoice = true, recordingDurationSec = 0)
                    onVoiceStart()
                }
            },
            onVoicePressEnd = {
                state = state.copy(isRecordingVoice = false, recordingDurationSec = 0)
                onVoiceEnd()
            },
            onVoicePressCancel = {
                state = state.copy(isRecordingVoice = false, recordingDurationSec = 0)
                onVoiceCancel()
            },
            onPickMedia = { onPickImage() },
            onTakePhoto = { onTakePhoto() },
            onCaptureVideo = { onCaptureVideo() },
            onPickFile = { onPickFile() },
            onRetry = { localId ->
                val target = state.messages.firstOrNull { it.localId == localId } ?: return@ChatThreadContent
                state = state.copy(messages = state.messages.filterNot { it.localId == localId })
                FoxLocalChatStore.deleteMessage(userId, localId)
                // ⚠️ 媒体消息必须走 sendMedia 重发：曾把图片消息当文本重发，
                // 结果对方收到一条内容为「IMG_xxx.jpeg」的纯文本气泡。
                val mediaUrl = target.remoteMediaUrl
                if (mediaUrl != null && target.type != com.fox.music.core.model.chat.MessageType.TEXT) {
                    val mediaType = when {
                        target.type == com.fox.music.core.model.chat.MessageType.AUDIO -> "audio"
                        target.fileType?.lowercase()?.startsWith("video") == true ||
                            target.localMediaFileName?.lowercase()?.let { it.endsWith(".mp4") || it.endsWith(".mov") } == true -> "video"
                        target.fileType?.lowercase()?.startsWith("image") == true ||
                            target.localMediaFileName?.lowercase()?.let {
                                it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") ||
                                    it.endsWith(".gif") || it.endsWith(".webp")
                            } == true -> "image"
                        else -> "file"
                    }
                    sendMedia(mediaType, mediaUrl, target.audioDurationMs, target.localMediaFileName)
                } else {
                    sendText(target.content)
                }
            },
            onRecall = { messageId, localId ->
                scope.launch {
                    try {
                        api.recallMessage(messageId)
                        state = state.copy(
                            messages = state.messages.map {
                                if (it.localId == localId) it.copy(isRecalled = true) else it
                            },
                        )
                        // 会话列表的预览也要跟着变（「xxx撤回了一条消息」）
                        state.messages.firstOrNull { it.localId == localId }
                            ?.copy(isRecalled = true)
                            ?.let { syncLocalConversation(it) }
                    } catch (e: Exception) {
                        onToast(e.message ?: "撤回失败")
                    }
                }
            },
            onDelete = { localId ->
                // 后端没有「删除单条消息」接口（Android 侧也只是删本地 Room 记录），
                // iOS 因此只做本地删除，重新进页面从本地加载时也不再出现。
                state = state.copy(
                    messages = state.messages.filterNot { it.localId == localId },
                )
                FoxLocalChatStore.deleteMessage(userId, localId)
            },
            onCopy = { onToast("已复制：${it.take(20)}") },
            onForward = { message -> onForwardMessage(message) },
            onEnterSelection = { localId ->
                state = state.copy(isSelectionMode = true, selectedLocalIds = setOf(localId))
            },
            onToggleSelection = { localId ->
                val selected = state.selectedLocalIds
                state = state.copy(
                    selectedLocalIds = if (localId in selected) selected - localId else selected + localId,
                )
            },
            onExitSelection = {
                state = state.copy(isSelectionMode = false, selectedLocalIds = emptySet())
            },
            onDeleteSelected = {
                val ids = state.selectedLocalIds
                state = state.copy(
                    messages = state.messages.filterNot { it.localId in ids },
                    selectedLocalIds = emptySet(),
                    isSelectionMode = false,
                )
            },
            onMediaClick = { viewer ->
                onMediaPreview(
                    viewer.mediaUrl,
                    viewer.videoUrl,
                    viewer.previewType.name,
                )
            },
            onShareClick = { message ->
                val shareId = message.shareId
                if (shareId == null || shareId <= 0L) {
                    onToast("分享的内容已不可用")
                    return@ChatThreadContent
                }
                val data = message.shareData
                onOpenShare(
                    message.shareType ?: "music",
                    shareId,
                    data?.title ?: data?.name,
                    data?.url,
                    data?.coverImage ?: data?.avatar,
                    data?.duration ?: 0L,
                    data?.artists?.mapNotNull { it.name } ?: emptyList(),
                )
            },
            onPlayVoice = { message ->
                val voiceUrl = message.remoteMediaUrl
                if (voiceUrl.isNullOrBlank()) {
                    onToast("语音加载失败")
                    return@ChatThreadContent
                }
                val localId = message.localId ?: ""
                if (state.playingVoiceLocalId != null && state.playingVoiceLocalId == localId) {
                    // 同一气泡再点一次：停止播放
                    state = state.copy(playingVoiceLocalId = null)
                    onPlayVoice(voiceUrl, localId, message.audioDurationMs ?: 0L, false)
                } else {
                    state = state.copy(playingVoiceLocalId = localId)
                    onPlayVoice(voiceUrl, localId, message.audioDurationMs ?: 0L, true)
                }
            },
        )
    }

    private fun keyOf(message: Message): String =
        if (message.id > 0L) "server_${message.id}" else "local_${message.localId}"

    private fun nowMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
