package com.fox.music.iosshared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 会话 / 通知列表刷新总线（iOS 专用）。
 *
 * 为什么需要它：SwiftUI 的 NavigationStack 里，消息页的 Compose VC 是**常驻**的
 * （push 聊天页只是叠一层，pop 不会重建消息页），所以 `LaunchedEffect(Unit)` 只在
 * 进 App 时跑一次。结果是「发完消息退回列表，列表还是进入前的快照」，刚聊的人不出现；
 * 通知分类页里点了通知标记已读，退回消息页红点也还挂着，必须下拉刷新才消失。
 *
 * 聊天页发消息/收消息后 [bumpConversations]、通知分类页标已读后 [bumpNotifications]，
 * 消息页用 `LaunchedEffect(..., conversationRevision, notificationRevision)` 重新拉取。
 */
object FoxChatRefresh {

    var conversationRevision by mutableIntStateOf(0)
        private set

    var notificationRevision by mutableIntStateOf(0)
        private set

    fun bumpConversations() {
        conversationRevision++
    }

    fun bumpNotifications() {
        notificationRevision++
    }
}
