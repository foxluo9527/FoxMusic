package com.fox.music.core.data.realtime

import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.Notification

/**
 * 实时消息的落地出口，由各平台提供实现。
 *
 * 为什么不直接用 [com.fox.music.core.domain.repository.ChatRepository]：
 * 后者的 `sendMediaMessage` 带 `android.net.Uri` 参数，被钉死在 :core:domain 的 androidMain，
 * iOS 拿不到；而为了一个 Uri 去改整个接口签名会波及全部聊天调用点，不划算。
 *
 * 这个接口只描述「实时协调器需要落地什么」，与存储实现解耦：
 *   - Android：委托给 ChatRepository（Room 落库 + 刷新会话）
 *   - iOS：调 REST 拉未读（iOS 暂无本地库），后续补本地存储时只改实现
 */
interface RealtimeMessageGateway {

    /** 收到对方发来的聊天消息：落库并刷新会话列表 */
    suspend fun onIncomingMessage(message: Message)

    /** 收到聊天类通知：本地落地（无法解析对方 ID 时可回退拉未读） */
    suspend fun onMessageNotification(notification: Notification)

    /** 回前台补拉未读。传 0 表示同步全部（对话列表刷新） */
    suspend fun syncUnread(peerUserId: Long = 0)
}
