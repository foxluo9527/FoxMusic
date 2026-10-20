package com.fox.music.core.data.realtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 记录当前正在查看的聊天对象，用于抑制「正在聊天的那个人的消息通知」。
 *
 * 从 Android 专属改为跨端：去掉了 @Inject/@Singleton，改由各平台的装配层
 * 手工创建单例（Android 走 :core:data-android 的 Hilt Module，iOS 走 iosShared）。
 */
class ActiveChatTracker {

    private val _currentPeerUserId = MutableStateFlow<Long?>(null)
    val currentPeerUserId: StateFlow<Long?> = _currentPeerUserId.asStateFlow()

    fun setActivePeer(userId: Long?) {
        _currentPeerUserId.value = userId
    }
}
