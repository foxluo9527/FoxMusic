package com.fox.music.core.common

import com.fox.music.core.common.realtime.NavigationRequest
import com.fox.music.core.common.realtime.RealtimeNotificationEvent
import com.fox.music.core.common.post.PostPublishMessages
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 *    Author : 罗福林
 *    Date   : 2026/1/4
 *    Desc   : 全局事件管理
 *
 * 从 androidMain 下沉到 commonMain：这里全是 Flow/StateFlow，没有任何平台依赖，
 * iOS 端的应用内通知横幅同样需要订阅它。
 *
 * 注意 appInForeground 原先是 MutableLiveData（Android 专有），已改为
 * MutableStateFlow —— 写入方 `.value = x`、读取方 `.value` 用法不变，
 * 只有 LiveData 专有扩展（如 `asFlow()`）需要调整。
 */
object EventViewModel {

    val showMainPageRoute = MutableStateFlow<String?>(null)

    val appInForeground = MutableStateFlow(true)

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

    /** 播放历史上报成功后发出，供「我的」页轻量刷新听歌统计 */
    private val _listeningStatsUpdated = MutableSharedFlow<Unit>(
        extraBufferCapacity = 8,
    )
    val listeningStatsUpdated = _listeningStatsUpdated.asSharedFlow()

    suspend fun emitInAppNotification(event: RealtimeNotificationEvent) {
        _inAppNotificationEvents.emit(event)
    }

    suspend fun requestNavigation(route: String) {
        _pendingNavigation.emit(NavigationRequest(route))
    }

    fun notifyNotificationsUpdated() {
        _notificationsUpdated.tryEmit(Unit)
    }

    fun notifyListeningStatsUpdated() {
        _listeningStatsUpdated.tryEmit(Unit)
    }

    private val _plazaPostsUpdated = MutableSharedFlow<Unit>(
        extraBufferCapacity = 8,
    )
    val plazaPostsUpdated = _plazaPostsUpdated.asSharedFlow()

    fun notifyPlazaPostsUpdated() {
        _plazaPostsUpdated.tryEmit(Unit)
    }

    private val _postPublishCompleted = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
    )
    val postPublishCompleted = _postPublishCompleted.asSharedFlow()

    fun notifyPostPublishCompleted(
        isEdit: Boolean,
        skippedCount: Int,
        skippedHasImage: Boolean,
        skippedHasVideo: Boolean,
    ) {
        val message = PostPublishMessages.successMessage(
            isEdit = isEdit,
            skippedCount = skippedCount,
            skippedHasImage = skippedHasImage,
            skippedHasVideo = skippedHasVideo,
        )
        _postPublishCompleted.tryEmit(message)
    }
}
