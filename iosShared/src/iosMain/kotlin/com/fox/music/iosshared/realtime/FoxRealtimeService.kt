package com.fox.music.iosshared.realtime

import com.fox.music.core.common.EventViewModel
import com.fox.music.core.common.realtime.RealtimeNotificationEvent
import com.fox.music.core.common.util.FoxLog
import com.fox.music.core.data.realtime.ActiveChatTracker
import com.fox.music.core.data.realtime.RealtimeMessageCoordinator
import com.fox.music.core.network.websocket.WebSocketManagerIos
import com.fox.music.iosshared.FoxAuthStore
import com.fox.music.iosshared.foxMainScope
import com.fox.music.iosshared.safeLaunch

/**
 * iOS 端实时消息总装 + 对 Swift 的门面。
 *
 * 这里把跨端的 [RealtimeMessageCoordinator] 用 iOS 的实现拼起来，形成与 Android
 * **完全同一条逻辑**的实时链路：WebSocket 建连 → 报文解析 → 分流（前台应用内横幅 /
 * 后台交给平台通知出口）→ 回前台补拉未读。
 *
 * ## iOS 的平台事实（决定了这套方案长什么样）
 *
 * - 前台：WebSocket 正常工作，消息实时到达，走应用内横幅。
 * - 后台：App 被系统挂起，socket 随即断开，**收不到任何消息**。唯一后台通道是 APNs，
 *   本项目明确不做（非公开分发、少数人用，$99 账号 + 后端推送链路不划算）。
 * - 因此「退出 App 期间的消息」靠回前台的 [onAppForeground]（重连 + 补拉未读）兜底，
 *   消息不丢，只是延迟到下次打开。
 *
 * ## 为什么横幅由 Swift 画而不是共享 Compose
 *
 * UI 方向是「两端共用结构、平台分支细节」，通知横幅属于**平台细节**：
 * iOS 用 SwiftUI overlay 才拿得到 HIG 的观感（模糊材质、圆角、安全区、手势）。
 * Kotlin 侧只负责把事件推给 Swift（[onInAppNotification]），不碰渲染。
 *
 * ## Swift 侧的接入点（三个，都在 App 生命周期里）
 *
 * ```
 * .onAppear        { FoxRealtimeService.shared.start() }
 * .onChange(of: scenePhase) { ... 前台 onAppForeground() / 后台 onAppBackground() }
 * 登录/登出后        { onAuthChanged() }
 * ```
 */
object FoxRealtimeService {

    private const val TAG = "FoxRealtime"

    private val scope = foxMainScope()

    private val tokenManager = IosTokenManager()
    private val userPreferences = IosUserPreferencesRepository()
    private val messageGateway = IosRealtimeMessageGateway()
    private val activeChatTracker = ActiveChatTracker()

    private val webSocketManager: WebSocketManagerIos = WebSocketManagerIos(tokenManager)

    private val coordinator = RealtimeMessageCoordinator(
        webSocketManager = webSocketManager,
        tokenManager = tokenManager,
        messageGateway = messageGateway,
        userPreferencesRepository = userPreferences,
        notificationDispatcher = IosRealtimeNotificationDispatcher(),
        activeChatTracker = activeChatTracker,
        notificationRouting = IosRealtimeNotificationRouting(),
        connectionLauncher = IosRealtimeConnectionLauncher(webSocketManager),
        // 网络恢复自动重连：iOS 由 Ktor Darwin 自己处理 NSURLSession 的等待队列，
        // 且回前台本来就会重连，故不注入 NetworkMonitor。
        networkMonitor = null,
    )

    private var started = false
    private var collecting = false

    /** 应用内通知事件（Swift 侧弹横幅）。 */
    var onInAppNotification: ((FoxInAppNotification) -> Unit)? = null

    /** 未读消息数变化（Swift 侧更新角标）。 */
    var onUnreadCountChanged: ((FoxUnreadBadge) -> Unit)? = null

    /**
     * 启动实时链路。幂等 —— 可以放心在 `.onAppear` / 每次回到前台时调用。
     *
     * 顺序很重要：先把 FoxAuthStore 的现状同步成 Flow（登录态决定要不要连），
     * 再 start() 协调器，否则 `isLoggedIn` 初值为 false 会漏掉首次连接。
     */
    fun start() {
        if (started) return
        started = true

        tokenManager.syncFromAuthStore()
        userPreferences.syncUser()
        coordinator.start()
        observeInAppNotifications()
        observeUnreadCount()

        // 已登录则立即建连（未登录时协调器会在 isLoggedIn=false 时保持断开）
        if (FoxAuthStore.isLoggedIn) {
            coordinator.onAppForeground()
        }
        FoxLog.i(TAG, "started, loggedIn=${FoxAuthStore.isLoggedIn}")
    }

    /**
     * 登录态变化后调用（Swift 在登录成功 / 登出后各调一次）。
     *
     * 登出时 Coordinator 会收到 isLoggedIn=false 并主动 disconnect + 停保活，
     * 这里只需把新的登录态推进 Flow。
     */
    fun onAuthChanged() {
        tokenManager.syncFromAuthStore()
        userPreferences.syncUser()
        if (FoxAuthStore.isLoggedIn) {
            // 登录后补拉一次未读，避免「登录瞬间对方已发过消息」漏掉
            coordinator.onAppForeground()
        }
        FoxLog.i(TAG, "auth changed, loggedIn=${FoxAuthStore.isLoggedIn}")
    }

    /**
     * 回前台：重连 + 补拉未读。
     *
     * 这是 iOS「退出期间消息不丢」的唯一保障 —— 挂起期间收到的消息，
     * 靠这里一次性从服务端拉回来（见 [IosRealtimeMessageGateway.syncUnread]）。
     */
    fun onAppForeground() {
        EventViewModel.appInForeground.value = true
        coordinator.onAppForeground()
    }

    fun onAppBackground() {
        // 置 false：若后台宽限期内还收到消息，走的是平台通知出口而不是应用内横幅。
        EventViewModel.appInForeground.value = false
        coordinator.onAppBackground()
    }

    /** 进入 / 离开某个私聊会话时设置，用于抑制「正在聊的那个人」的横幅。 */
    fun setActiveChat(peerUserId: Long) {
        activeChatTracker.setActivePeer(peerUserId)
    }

    fun clearActiveChat() {
        activeChatTracker.setActivePeer(null)
    }

    /** 当前是否已连上（供 Swift 做调试展示 / 断线提示）。 */
    fun isConnected(): Boolean = webSocketManager.isConnected()

    /** 主动补拉未读（下拉刷新消息列表时可用）。 */
    fun refreshUnread() {
        // syncUnread 内部已自兜网络异常，这里再包一层防止 Swift 回调链路上的意外
        scope.safeLaunch { messageGateway.syncUnread() }
    }

    /**
     * 进入/读完某个会话：消费掉该会话未读并重算「消息」Tab 角标（红点）。
     *
     * 由聊天详情页在标记已读后调用 —— 否则服务端未读（按 `is_notified` 过滤）
     * 不降，红点会一直挂着直到下次回前台补拉。
     */
    fun markPeerRead(peerUserId: Long) {
        scope.safeLaunch { messageGateway.markPeerRead(peerUserId) }
    }

    private fun observeInAppNotifications() {
        if (collecting) return
        collecting = true
        // 长 collect：异常（上游/ Swift 横幅回调）被 safeLaunch 兜住，仅记录不杀进程。
        // 正常情况下 SharedFlow 永不结束，这里不需要重启。
        scope.safeLaunch {
            EventViewModel.inAppNotificationEvents.collect { event ->
                runCatching { onInAppNotification?.invoke(event.toFoxPayload()) }
                    .onFailure { FoxLog.w(TAG, "in-app notification 回调失败: ${it.message}") }
            }
        }
    }

    private fun observeUnreadCount() {
        scope.safeLaunch {
            messageGateway.unreadCount.collect { count ->
                runCatching { onUnreadCountChanged?.invoke(FoxUnreadBadge(count)) }
                    .onFailure { FoxLog.w(TAG, "未读角标回调失败: ${it.message}") }
            }
        }
    }
}

/**
 * 未读角标载荷。
 *
 * 为什么包一层而不是直接传 `Int`：Kotlin 的原始类型出现在**函数类型参数**里，
 * 跨到 Swift 会变成装箱的 `KotlinInt`，取值要写 `count.intValue` 且容易写错；
 * 包成 data class 后 Swift 侧就是普通的 `Int` 属性，两边都清楚。
 */
data class FoxUnreadBadge(val count: Int)

/**
 * 传给 Swift 的应用内通知载荷。
 *
 * 刻意不进领域模型 [RealtimeNotificationEvent] 本身：那个类的 `type` 是 Kotlin 枚举，
 * 跨到 Swift 是 `NotificationType` 类实例，判断要走 `==` 比较类对象，不顺手；
 * 这里拍平成 String + 基本类型，Swift 侧直接 switch 字符串即可。
 */
data class FoxInAppNotification(
    val id: Long,
    val type: String,
    val title: String,
    val content: String,
    val route: String,
    val peerUserId: Long?,
)

private fun RealtimeNotificationEvent.toFoxPayload(): FoxInAppNotification =
    FoxInAppNotification(
        id = id,
        type = type.name.lowercase(),
        title = title,
        content = content,
        route = route,
        peerUserId = peerUserId,
    )
