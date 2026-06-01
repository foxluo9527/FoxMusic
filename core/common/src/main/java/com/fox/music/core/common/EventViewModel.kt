package com.fox.music.core.common

import androidx.lifecycle.MutableLiveData
import com.fox.music.core.common.realtime.NavigationRequest
import com.fox.music.core.common.realtime.RealtimeNotificationEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 *    Author : 罗福林
 *    Date   : 2026/1/4
 *    Desc   : 全局事件管理
 */
object EventViewModel {

    val showMainPageRoute = MutableStateFlow<String?>(null)

    val appInForeground by lazy {
        MutableLiveData<Boolean>(true)
    }

    private val _inAppNotificationEvents = MutableSharedFlow<RealtimeNotificationEvent>(
        extraBufferCapacity = 16,
    )
    val inAppNotificationEvents = _inAppNotificationEvents.asSharedFlow()

    private val _pendingNavigation = MutableSharedFlow<NavigationRequest>(
        replay = 1,
        extraBufferCapacity = 8,
    )
    val pendingNavigation = _pendingNavigation.asSharedFlow()

    private val _notificationsUpdated = MutableSharedFlow<Unit>(
        extraBufferCapacity = 8,
    )
    val notificationsUpdated = _notificationsUpdated.asSharedFlow()

    suspend fun emitInAppNotification(event: RealtimeNotificationEvent) {
        _inAppNotificationEvents.emit(event)
    }

    suspend fun requestNavigation(route: String) {
        _pendingNavigation.emit(NavigationRequest(route))
    }

    fun notifyNotificationsUpdated() {
        _notificationsUpdated.tryEmit(Unit)
    }
}