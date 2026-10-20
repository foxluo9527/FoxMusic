package com.fox.music.core.data.realtime

import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.Notification

/**
 * Android 侧的实时消息落地：把跨端的 [RealtimeMessageGateway] 接到现有 [ChatRepository]。
 *
 * ChatRepository 本身因为 `sendMediaMessage` 带 `android.net.Uri` 参数而留在
 * :core:domain 的 androidMain，iOS 拿不到，所以跨端侧只依赖这个窄接口。
 *
 * 不加 @Inject：由 [com.fox.music.core.data.di.RealtimeModule] 手工构造，
 * 避免依赖 KSP 生成工厂。
 */
class ChatRepositoryMessageGateway(
    private val chatRepository: ChatRepository,
) : RealtimeMessageGateway {

    override suspend fun onIncomingMessage(message: Message) {
        chatRepository.ingestIncomingMessage(message)
    }

    override suspend fun onMessageNotification(notification: Notification) {
        chatRepository.landMessageNotification(notification)
    }

    override suspend fun syncUnread(peerUserId: Long) {
        chatRepository.syncUnreadMessages(peerUserId = peerUserId)
    }
}
