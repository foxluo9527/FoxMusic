package com.fox.music.core.data.realtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveChatTracker @Inject constructor() {

    private val _currentPeerUserId = MutableStateFlow<Long?>(null)
    val currentPeerUserId: StateFlow<Long?> = _currentPeerUserId.asStateFlow()

    fun setActivePeer(userId: Long?) {
        _currentPeerUserId.value = userId
    }
}
