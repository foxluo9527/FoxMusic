package com.fox.music.iosshared.realtime

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.session.TokenManager
import com.fox.music.core.common.util.FoxLog
import com.fox.music.core.data.realtime.NotificationPeerResolver
import com.fox.music.core.data.realtime.RealtimeNotificationRouting
import com.fox.music.core.domain.repository.RealtimeConnectionLauncher
import com.fox.music.core.domain.repository.RealtimeNotificationDispatcher
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.NotificationType
import com.fox.music.core.model.user.DarkMode
import com.fox.music.core.model.user.PlayQuality
import com.fox.music.core.model.user.UserPreferences
import com.fox.music.core.network.websocket.WebSocketManager
import com.fox.music.iosshared.FoxAuthStore
import com.fox.music.iosshared.foxBackgroundScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * iOS 侧的 TokenManager：读 [FoxAuthStore]，不自己持有第二份登录态。
 *
 * 为什么需要它：跨端后的 RealtimeMessageCoordinator 依赖
 * [com.fox.music.core.common.session.TokenManager] 的 `isLoggedIn` 流来决定
 * 连/断 WebSocket，而 iOS 的登录态在 Swift 的 Keychain + Kotlin 的 FoxAuthStore 里，
 * 不是 Flow。这里做一层「可推送的适配器」：Swift 在登录/登出后调
 * [FoxRealtimeService.onAuthChanged]，由它把 FoxAuthStore 的现状同步成 Flow 事件。
 *
 * 注意 peekXxx 是**同步读 FoxAuthStore 现值**（不走缓存），因为 WebSocket 建连时
 * 需要的是当下的最新 token，缓存多一份只会带来不一致。
 */
class IosTokenManager : TokenManager {

    private val _accessToken = MutableStateFlow<String?>(null)
    override val accessToken: Flow<String?> = _accessToken.asStateFlow()

    private val _refreshToken = MutableStateFlow<String?>(null)
    override val refreshToken: Flow<String?> = _refreshToken.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    override val isLoggedIn: Flow<Boolean> = _isLoggedIn.asStateFlow()

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    /** 由 [FoxRealtimeService] 在启动 / 登录态变化时调用，把 FoxAuthStore 的现值推成 Flow 事件。 */
    fun syncFromAuthStore() {
        _accessToken.value = FoxAuthStore.token
        _refreshToken.value = FoxAuthStore.refreshToken
        _isLoggedIn.value = FoxAuthStore.isLoggedIn
    }

    override fun peekAccessToken(): String? = FoxAuthStore.token

    override fun peekRefreshToken(): String? = FoxAuthStore.refreshToken

    // 以下写方法在 iOS 上是空实现：令牌的**持久化**归 Swift 的 Keychain（AuthStore.swift），
    // Kotlin 侧只读不写，避免出现两个写入方。写入后由 Swift 调 syncFromAuthStore() 同步内存态。
    override suspend fun saveAccessToken(token: String) = Unit

    override suspend fun saveTokens(accessToken: String, refreshToken: String?) = Unit

    override suspend fun clearTokens() = Unit

    override suspend fun invalidateSession() {
        _sessionExpired.tryEmit(Unit)
    }
}

/**
 * iOS 侧的用户偏好：**内存态**。
 *
 * 为什么不做持久化：iOS 端目前没有设置页（通知偏好 / 主题 / 音质都还没有 UI），
 * 而 Coordinator 只用到两处 —— `userPreferences.first().userId`（判断消息是否自己发的）
 * 与 `notificationSettings.isEnabled(type)`（用户是否关闭了某类提醒）。这两处用默认值
 * 就能得到正确行为，做成 DataStore/SQLDelight 落地是纯粹的过度设计。
 *
 * 将来 iOS 有设置页时，把这里换成持久化实现即可，上层（Coordinator）零改动。
 */
class IosUserPreferencesRepository : UserPreferencesRepository {

    private val _prefs = MutableStateFlow(UserPreferences())
    override val userPreferences: Flow<UserPreferences> = _prefs.asStateFlow()

    /** 登录态变化时把用户信息同步进来 —— `userId` 是 Coordinator 判断「消息是不是自己发的」的依据。 */
    fun syncUser() {
        val user = FoxAuthStore.user
        _prefs.value = _prefs.value.copy(
            isLoggedIn = FoxAuthStore.isLoggedIn,
            token = FoxAuthStore.token,
            userId = user?.id,
            username = user?.username,
        )
    }

    override suspend fun updateDarkMode(darkMode: DarkMode): Result<Unit> {
        _prefs.value = _prefs.value.copy(darkMode = darkMode)
        return Result.Success(Unit)
    }

    override suspend fun updateAutoPlay(enabled: Boolean): Result<Unit> {
        _prefs.value = _prefs.value.copy(autoPlay = enabled)
        return Result.Success(Unit)
    }

    override suspend fun updatePlayQuality(quality: PlayQuality): Result<Unit> {
        _prefs.value = _prefs.value.copy(playQuality = quality)
        return Result.Success(Unit)
    }

    override suspend fun updateDownloadQuality(quality: PlayQuality): Result<Unit> {
        _prefs.value = _prefs.value.copy(downloadQuality = quality)
        return Result.Success(Unit)
    }

    override suspend fun updateShowLyrics(enabled: Boolean): Result<Unit> {
        _prefs.value = _prefs.value.copy(showLyrics = enabled)
        return Result.Success(Unit)
    }

    override suspend fun updateNotificationEnabled(
        type: NotificationType,
        enabled: Boolean,
    ): Result<Unit> {
        val s = _prefs.value.notificationSettings
        val next = when (type) {
            NotificationType.COMMENT -> s.copy(commentEnabled = enabled)
            NotificationType.LIKE -> s.copy(likeEnabled = enabled)
            NotificationType.FOLLOW -> s.copy(followEnabled = enabled)
            NotificationType.MENTION -> s.copy(mentionEnabled = enabled)
            NotificationType.MESSAGE -> s.copy(messageEnabled = enabled)
            NotificationType.FRIEND_REQUEST -> s.copy(friendRequestEnabled = enabled)
            NotificationType.SYSTEM -> s.copy(systemEnabled = enabled)
            NotificationType.MUSIC -> s.copy(musicEnabled = enabled)
        }
        _prefs.value = _prefs.value.copy(notificationSettings = next)
        return Result.Success(Unit)
    }

    override suspend fun updateLanguage(language: String): Result<Unit> {
        _prefs.value = _prefs.value.copy(language = language)
        return Result.Success(Unit)
    }

    override suspend fun updateDownloadOnWifiOnly(enabled: Boolean): Result<Unit> {
        _prefs.value = _prefs.value.copy(downloadOnWifiOnly = enabled)
        return Result.Success(Unit)
    }

    override suspend fun updateCacheMaxBytes(maxBytes: Long): Result<Unit> {
        _prefs.value = _prefs.value.copy(cacheMaxBytes = maxBytes)
        return Result.Success(Unit)
    }

    override suspend fun updateAppTheme(themeId: String, customSeedArgb: Long?): Result<Unit> {
        _prefs.value = _prefs.value.copy(
            appThemeId = themeId,
            customThemeSeedArgb = customSeedArgb ?: _prefs.value.customThemeSeedArgb,
        )
        return Result.Success(Unit)
    }

    override suspend fun updateDynamicColor(enabled: Boolean): Result<Unit> = Result.Success(Unit)

    override suspend fun updateDesktopLyricEnabled(enabled: Boolean): Result<Unit> {
        _prefs.value = _prefs.value.copy(desktopLyricEnabled = enabled)
        return Result.Success(Unit)
    }

    override suspend fun updateLockScreenLyricEnabled(enabled: Boolean): Result<Unit> {
        _prefs.value = _prefs.value.copy(lockScreenLyricEnabled = enabled)
        return Result.Success(Unit)
    }

    override suspend fun updateCarBluetoothLyricEnabled(enabled: Boolean): Result<Unit> {
        _prefs.value = _prefs.value.copy(carBluetoothLyricEnabled = enabled)
        return Result.Success(Unit)
    }
}

/**
 * iOS 的「连接保活」：Android 是拉起前台 Service，iOS 没有这个概念 ——
 * 前台时 WebSocket 本身就活着，后台会被系统挂起（那是平台行为，不是保活能解决的）。
 * 所以这里只做一件有意义的事：登录后把连接拉起来。
 */
class IosRealtimeConnectionLauncher(
    private val webSocketManager: WebSocketManager,
) : RealtimeConnectionLauncher {

    private val scope = foxBackgroundScope()

    override fun startConnectionService() {
        // 建连失败只影响实时消息（回前台还有 syncUnread 兜底），绝不能让未捕获
        // 异常沿 DarwinMainDispatcher/Default 线程走到 terminateWithUnhandledException。
        scope.launch { runCatching { webSocketManager.ensureConnected() } }
    }

    override fun stopConnectionService() {
        // 登出时 Coordinator 已负责 disconnect()，这里无需额外动作。
    }
}

/**
 * iOS 的系统通知出口：**空实现**。
 *
 * 这是本方案的关键设计点 —— APNs 被隔离在这一个接口后面。
 * iOS 不接 APNs 时（本项目明确不做，见下），后台期间本来就收不到任何消息
 * （App 被挂起 → socket 断开），所以这个出口在 iOS 上永远不会被触发；
 * 一旦将来要做 APNs，只需把这里换成「上报设备 token / 触发服务端推送」的实现，
 * Coordinator 与业务代码一行都不用改。
 */
class IosRealtimeNotificationDispatcher : RealtimeNotificationDispatcher {

    override fun showNotification(
        notification: Notification,
        route: String,
        peerUserId: Long?,
    ) {
        FoxLog.i(TAG, "后台通知被丢弃（iOS 未接 APNs）: type=${notification.type}")
    }

    private companion object {
        const val TAG = "IosNotificationDispatcher"
    }
}

/**
 * iOS 的通知路由：产出与 Android 语义一致的 route 字符串，供 Swift 侧决定点开横幅后跳哪。
 *
 * 目前 iOS 只有首页 / 搜索 / 社区 / 我的四个 Tab，「消息」页尚未迁移，
 * 所以 route 先原样透传给 Swift（[FoxInAppNotification.route]），
 * 等消息页落地后在这里映射到具体 Tab 或 SwiftUI NavigationPath。
 */
class IosRealtimeNotificationRouting : RealtimeNotificationRouting {

    override fun routeFor(notification: Notification): String = when (notification.type) {
        NotificationType.MESSAGE -> {
            val peerId = NotificationPeerResolver.resolvePeerUserId(notification)
            if (peerId > 0L) chatRoute(peerId) else MESSAGES_ROUTE
        }
        NotificationType.COMMENT, NotificationType.MENTION -> "$NOTIFICATION_CATEGORY_ROUTE/comment"
        NotificationType.LIKE, NotificationType.MUSIC -> "$NOTIFICATION_CATEGORY_ROUTE/like"
        NotificationType.FOLLOW -> MESSAGES_ROUTE
        NotificationType.FRIEND_REQUEST -> FRIENDS_ROUTE
        NotificationType.SYSTEM -> "$NOTIFICATION_CATEGORY_ROUTE/system"
    }

    override fun chatRoute(peerUserId: Long): String = "$CHAT_ROUTE/$peerUserId"

    private companion object {
        const val CHAT_ROUTE = "chat"
        const val MESSAGES_ROUTE = "messages"
        const val FRIENDS_ROUTE = "friends"
        const val NOTIFICATION_CATEGORY_ROUTE = "notifications"
    }
}
