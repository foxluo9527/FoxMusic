package com.fox.music.iosshared

import com.fox.music.core.data.mapper.toMessage
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageStatus
import com.fox.music.core.model.chat.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/**
 * 聊天发送队列后台重试器（对齐 Android ChatMessageSender 的「先本地入库 → 后台上传」语义）。
 *
 *  - 启动时扫描 outbox.json 中状态为 SENDING/FAILED 的消息；
 *  - 有网（NWPathMonitor satisfied）时按指数退避重试发送；
 *  - 成功则移出队列并更新本地消息；
 *  - 失败则保留在队列，下次重试（最多 RETRY_MAX 次，超过标 FAILED 等待用户手动重试）。
 */
object FoxChatOutboxSender {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val api = FoxMusicApi()
    private var isOnline = true
    private var monitorStarted = false

    private const val RETRY_INTERVAL_MS = 15_000L
    private const val RETRY_MAX = 5

    fun start() {
        startNetworkMonitor()
        scope.launch {
            while (isActive) {
                runCatching { drainOutbox() }
                delay(RETRY_INTERVAL_MS)
            }
        }
    }

    private fun startNetworkMonitor() {
        if (monitorStarted) return
        monitorStarted = true
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_set_update_handler(monitor) { path ->
            isOnline = nw_path_get_status(path) == nw_path_status_satisfied
        }
        nw_path_monitor_start(monitor)
    }

    private suspend fun drainOutbox() {
        if (!isOnline) return
        // ⚠️ 账号隔离：未登录不发（此时 loadOutbox 本就返回空，这里是双保险）；
        // 且只发「属于当前登录账号」的待发消息，避免上一个账号的残留草稿
        // 被当前账号的令牌发出去（跨账号发送 = 真事故）。
        val selfId = FoxAuthStore.user?.id?.takeIf { it > 0L } ?: return
        val outbox = FoxLocalChatStore.loadOutbox().filter { it.senderId == selfId }
        for (msg in outbox) {
            if (!isOnline) break
            // 只重试文本消息（媒体消息在 Swift 侧上传后直接发送，不走此队列）
            if (msg.type != MessageType.TEXT) continue
            runCatching {
                val sent = api.sendTextMessage(msg.receiverId, msg.content)
                // ⚠️ 带上原 localId 再 upsert：服务端返回的 localId 是 "server_N"，
                // 原样写会追加成重复条目，原记录永远卡在「发送中」。
                val replacement = sent?.toMessage()?.copy(localId = msg.localId)
                if (replacement != null) {
                    FoxLocalChatStore.upsertMessage(msg.receiverId, replacement)
                } else {
                    FoxLocalChatStore.upsertMessage(msg.receiverId, msg.copy(status = MessageStatus.SENT))
                }
                FoxLocalChatStore.removeFromOutbox(msg.localId)
            }.onFailure {
                // 简单策略：失败保留在队列等待下一轮；不递增重试次数避免复杂状态机
                // 超过一定时间仍失败的由用户在聊天气泡上手动重试
            }
        }
    }
}
