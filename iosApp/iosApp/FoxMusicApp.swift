import SwiftUI
import ActivityKit
import Foundation
import FoxShared

/// App 生命周期兜底：从多任务视图划掉 App 时，系统会回调 applicationWillTerminate
///（App 在前台运行或后台存活时划掉都会触发；已被系统挂起(suspend)后划掉不保证触发）。
/// Live Activity 生命周期独立于 App 进程（Apple 官方行为），不主动结束就会一直留在灵动岛。
final class AppDelegate: NSObject, UIApplicationDelegate {
    func applicationWillTerminate(_ application: UIApplication) {
        guard #available(iOS 16.2, *) else { return }
        let activities = Activity<FoxMusicActivityAttributes>.activities
        guard !activities.isEmpty else { return }
        // end 是 async：必须用信号量阻塞等它跑完再让进程退出，
        // 否则 Task 还没执行进程就被杀了，活动照样残留（社区验证过的唯一可靠写法）。
        let semaphore = DispatchSemaphore(value: 0)
        Task.detached {
            for activity in activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            semaphore.signal()
        }
        semaphore.wait()
    }
}

@main
struct FoxMusicApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase
    /// 外观（深色模式）+ 主题色，对应设置页「应用」分组的对应两项。
    /// 挂在根上是为了让 `.preferredColorScheme` / `.tint` 覆盖到整个窗口（含各 sheet）。
    @StateObject private var appearance = AppearanceStore.shared

    init() {
        // 图片磁盘缓存：进程级共享 256MB（覆盖 iOS 默认 ~4MB 磁盘短板）。
        // 必须在这里（App 启动期）就设好 —— 否则 Ktor Darwin 引擎懒初始化之前，
        // 纯 SwiftUI 的 AsyncImage 会先用到系统默认的小缓存，冷启动封面照样全重下。
        // 与 Ktor 端设置的参数一致（diskPath=nil 走同一默认目录，数据互通）。
        URLCache.shared = URLCache(
            memoryCapacity: 64 * 1024 * 1024,
            diskCapacity: 256 * 1024 * 1024,
            diskPath: nil
        )
        // 最先安装未捕获异常落盘：K/N 上协程漏网异常默认直接 abort（表现为卡死/闪退），
        // hook 后异常类型+堆栈会写到 Application Support/fox_crash.log，
        // 下次启动可通过 FoxCrashLog.shared.crashLog() 读出定位。
        FoxCrashLog.shared.install()
        // 先把 Keychain 里的登录态推回 Kotlin（FoxAuthStore），再启动实时链路 ——
        // 顺序不能反：实时服务的 start() 会读 FoxAuthStore 判断是否需要建连。
        //
        // ⚠️ UI 测试的登录态重置（-UITestResetAuth）**不能在这里做**：App.init 阶段
        // Kotlin 运行时还没完全就绪，logout() → FoxAuthStore.clear() → 实时服务
        // onAuthChanged → FoxLog(NSLog) 会拿到未初始化的桥接对象直接 SIGSEGV
        //（实测崩溃栈：objc_opt_respondsToSelector 访问 0x6c616552786f5e）。
        // 挪到 ContentView.onAppear（见 ContentView）执行，与手动退出登录同路径。
        AuthStore.shared.restoreToKotlin()
        FoxRealtimeBridge.install()
        // 注册视频/Live 图播放器工厂（Compose 侧经 FoxVideoPlayerBridge 使用）
        FoxVideoPlayback.install()
        FoxChatOutboxSender.shared.start()
        if #available(iOS 16.2, *) { _ = LiveLyricsManager.shared }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // 深色模式：`.system` 时不覆盖（nil），浅色/深色则锁整个窗口。
                .preferredColorScheme(appearance.preferredColorScheme)
                // 主题色：与 Android AppThemePresets 同色值。
                .tint(appearance.tintColor)
        }
        // 应用生命周期 → Kotlin 的实时链路。
        //
        // iOS 进后台会被系统挂起、WebSocket 随之断开（平台行为，不是 bug），
        // 所以 onAppForeground 里做的是「重连 + 补拉未读」——这是退出期间消息不丢的兜底。
        // 部署目标是 iOS 16，这里用单参数版 onChange（双参数版要 17+）。
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .active:
                FoxRealtimeService.shared.onAppForeground()
            case .background:
                FoxRealtimeService.shared.onAppBackground()
            default:
                break
            }
        }
    }
}

/// Kotlin 实时服务 ↔ SwiftUI 的接线处。
///
/// 三件事：
///   1. 把 Kotlin 推来的应用内通知事件交给 [InAppNotificationCenter] 弹横幅
///   2. 把未读数交给 [RealtimeBadgeCenter] 更新「消息」Tab 角标
///   3. 启动实时链路（幂等）
///
/// 为什么横幅画在 Swift 而不是复用 Compose 的 InAppNotificationHost：
/// 通知横幅属于「平台细节」——iOS 要用 SwiftUI 的 .ultraThinMaterial / 安全区 /
/// 手势才拿得到 HIG 观感；Android 那份 Compose 实现在 app 模块继续用。
/// Kotlin 侧只负责把事件推出来（FoxRealtimeService.onInAppNotification），不碰渲染。
enum FoxRealtimeBridge {

    static func install() {
        FoxRealtimeService.shared.onInAppNotification = { payload in
            InAppNotificationCenter.shared.present(payload.toItem())
        }
        FoxRealtimeService.shared.onUnreadCountChanged = { badge in
            RealtimeBadgeCenter.shared.update(Int(badge.count))
        }
        FoxRealtimeService.shared.start()
    }
}

private extension FoxInAppNotification {
    func toItem() -> InAppNotificationItem {
        InAppNotificationItem(
            id: id,
            type: type,
            title: title,
            content: content,
            route: route,
            peerUserId: peerUserId?.int64Value
        )
    }
}
