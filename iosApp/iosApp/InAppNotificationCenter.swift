import SwiftUI
import FoxShared

/// 一条应用内通知（由 Kotlin 的 [FoxRealtimeService.onInAppNotification] 推来）。
///
/// Kotlin 侧刻意把它拍平成 String + 基本类型（`type` 传小写字符串而不是枚举），
/// Swift 这里直接 `switch` 即可，不用比较 Kotlin 枚举实例。
struct InAppNotificationItem: Identifiable, Equatable {
    let id: Int64
    let type: String
    let title: String
    let content: String
    let route: String
    let peerUserId: Int64?

    /// 图标与配色：与 Android 端 FoxNotificationManager 的分类保持同一套语义。
    var iconName: String {
        switch type {
        case "message": return "bubble.left.fill"
        case "comment": return "text.bubble.fill"
        case "like": return "heart.fill"
        case "follow": return "person.badge.plus"
        case "mention": return "at"
        case "friend_request": return "person.2.fill"
        case "music": return "music.note"
        default: return "bell.fill"
        }
    }

    var tint: Color {
        switch type {
        case "message": return .accentColor
        case "like": return .pink
        case "comment", "mention": return .orange
        case "follow", "friend_request": return .green
        case "music": return .purple
        default: return .gray
        }
    }
}

/// 应用内通知横幅的展示中心。
///
/// 存在的理由：iOS 进后台会被系统挂起、WebSocket 断开，本项目明确不接 APNs
/// （非公开分发、少数人用，$99 账号 + 后端推送链路不划算），
/// 因此**提醒只在应用内发生** —— 前台收到实时消息/通知，顶部浮出横幅。
///
/// 与 ToastCenter 同一套路（单例 + ObservableObject + 自动消失），
/// 但横幅挂在顶部且停留更久（通知需要看清发信人与内容）。
final class InAppNotificationCenter: ObservableObject {
    static let shared = InAppNotificationCenter()

    @Published private(set) var current: InAppNotificationItem?

    /// 自动消失时长：比 Toast（2s）长，通知内容更需要读清。
    private let autoDismissSeconds: TimeInterval = 4.0
    private var dismissWork: DispatchWorkItem?

    private init() {}

    func present(_ item: InAppNotificationItem) {
        // 通知设置过滤：与 Android `RealtimeMessageCoordinator:211` 的
        // `notificationSettings.isEnabled(type)` 同语义 —— 关掉某类通知就不再弹横幅。
        // 放在 present 内部而不是调用方（FoxRealtimeBridge），保证任何未来新增的
        // 调用点都自动受开关约束，不会被绕过。
        guard NotificationPreferenceStore.shared.isEnabled(type: item.type) else {
            RealtimeDebugLog.record("banner suppressed by settings: type=\(item.type)")
            return
        }
        // 会话级「消息免打扰」：与 Android 的会话 mute 同语义，只压制该会话的前台
        // 横幅，消息本身与未读角标不受影响（设置存在 Kotlin FoxChatSettings）。
        if item.type == "message",
           let peer = item.peerUserId,
           peer > 0,
           FoxChatSettings.shared.isMuted(peerUserId: peer) {
            RealtimeDebugLog.record("banner suppressed by conversation mute: peer=\(peer)")
            return
        }

        dismissWork?.cancel()
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            current = item
        }

        let work = DispatchWorkItem { [weak self] in
            self?.dismiss()
        }
        dismissWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + autoDismissSeconds, execute: work)
    }

    func dismiss() {
        dismissWork?.cancel()
        dismissWork = nil
        withAnimation(.easeInOut(duration: 0.25)) { current = nil }
    }
}

/// 顶部横幅：毛玻璃卡片 + 分类图标，符合 iOS HIG（材质、圆角、安全区）。
struct InAppNotificationBanner: View {
    let item: InAppNotificationItem
    let onTap: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            ZStack {
                Circle()
                    .fill(item.tint.opacity(0.16))
                    .frame(width: 36, height: 36)
                Image(systemName: item.iconName)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(item.tint)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(item.title)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Text(item.content)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Color.primary.opacity(0.06), lineWidth: 0.5)
        )
        .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
        .padding(.horizontal, 12)
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        .accessibilityIdentifier("inapp.banner")
    }
}

/// 横幅修饰器：挂在根视图（ContentView）上全局生效。
struct InAppNotificationOverlay: ViewModifier {
    @ObservedObject private var center = InAppNotificationCenter.shared

    func body(content: Content) -> some View {
        content.overlay(alignment: .top) {
            if let item = center.current {
                InAppNotificationBanner(item: item) {
                    handleTap(item)
                }
                .padding(.top, 8)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
    }

    /// 点横幅：先收起，再按通知类型跳到对应页面。
    ///
    /// 路由能力（2026-09-27 补）：此前这里只 `dismiss()` + 打日志 —— 注释还写着
    /// "iOS 消息页还是占位"，而消息页早已迁移完成，结果就是**点了通知毫无反应**。
    /// 现在：
    ///   - 聊天类（有 peerUserId）→ 消息 Tab → 对应会话
    ///   - 评论/点赞/系统 → 消息 Tab → 对应通知分类列表
    ///   - 其它（关注/音乐等，iOS 无对应二级页）→ 只切到消息 Tab，不做二级跳转
    private func handleTap(_ item: InAppNotificationItem) {
        InAppNotificationCenter.shared.dismiss()
        RealtimeDebugLog.record("banner tapped: type=\(item.type) route=\(item.route)")

        if let peer = item.peerUserId, peer > 0 {
            AppTabRouter.shared.openMessages(.chat(peerUserId: peer, nickname: item.title))
            return
        }
        switch item.type {
        case "comment", "like", "system":
            AppTabRouter.shared.openMessages(.category(item.type))
        default:
            AppTabRouter.shared.selectedTab = .messages
        }
    }
}

extension View {
    /// 挂在根视图上，让应用内通知横幅生效。
    func withInAppNotificationBanner() -> some View {
        modifier(InAppNotificationOverlay())
    }
}

/// 未读消息数（由 Kotlin 的 IosRealtimeMessageGateway 经 REST 补拉得到）。
///
/// 这是「退出 App 期间消息不丢」在 UI 上的体现：回前台补拉一次，
/// 服务端未读数就反映到「消息」Tab 的角标上。
final class RealtimeBadgeCenter: ObservableObject {
    static let shared = RealtimeBadgeCenter()
    @Published private(set) var unreadCount: Int = 0

    private init() {}

    func update(_ count: Int) {
        guard count != unreadCount else { return }
        unreadCount = max(0, count)
    }
}

/// 轻量调试日志：把实时链路的关键事件打到 Xcode 控制台，便于真机/模拟器排查。
enum RealtimeDebugLog {
    static func record(_ message: String) {
        print("[FoxRealtime] \(message)")
    }
}
