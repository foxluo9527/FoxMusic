// xcode: set sdk=iOS

import SwiftUI

// MARK: - 通知类型

/// 通知分类 —— 逐字对齐 Android `NotificationSettingsViewModel.notificationTypes`
/// 与 `typeLabel()` 的文案，以及 Kotlin `NotificationType` 枚举的小写名。
///
/// `rawValue` 必须与 Kotlin 侧 `RealtimeNotificationEvent.type.name.lowercase()`
/// 产物一致（`FoxRealtimeService.toFoxPayload()`），否则横幅的开关匹配不上：
/// 这条字符串是两端唯一的契约。
enum FoxNotificationCategory: String, CaseIterable, Identifiable {
    case message
    case comment
    case like
    case follow
    case mention
    case friendRequest = "friend_request"
    case system
    case music

    var id: String { rawValue }

    /// 与 Android `typeLabel()` 完全一致。
    var label: String {
        switch self {
        case .comment: return "评论提醒"
        case .like: return "点赞提醒"
        case .follow: return "关注提醒"
        case .mention: return "提及提醒"
        case .message: return "聊天消息"
        case .friendRequest: return "好友请求"
        case .system: return "系统通知"
        case .music: return "音乐提醒"
        }
    }
}

// MARK: - 存储

/// 通知开关（本地偏好）。
///
/// 与 Android 的关系：安卓侧同一份开关存在 DataStore，并被
/// `RealtimeMessageCoordinator:211` 的 `notificationSettings.isEnabled(type)` 消费
/// —— 也就是「关掉某类通知 → 不再弹横幅」。
/// iOS 的实时链路走 `IosRealtimeMessageGateway`（不经过那个 coordinator），
/// 所以这里用 Swift 侧 UserDefaults 存一份，并在 `InAppNotificationCenter.present`
/// 入口做同样的过滤，行为与安卓对齐。
///
/// 默认值全开：未配置过的用户不应因为缺省而被静默屏蔽通知。
final class NotificationPreferenceStore: ObservableObject {

    static let shared = NotificationPreferenceStore()

    private static let prefix = "fox_notif_enabled_"

    /// 观察用。`UserDefaults` 不是可观察的，所以开关变化时手动 `objectWillChange`。
    @Published private(set) var revision: Int = 0

    private init() {
        // UI 测试：清掉本机残留开关，保证「默认全开」的前提成立。
        if CommandLine.arguments.contains("-UITestResetNotificationPrefs") {
            for category in FoxNotificationCategory.allCases {
                UserDefaults.standard.removeObject(forKey: Self.prefix + category.rawValue)
            }
        }
    }

    func isEnabled(_ category: FoxNotificationCategory) -> Bool {
        let key = Self.prefix + category.rawValue
        guard UserDefaults.standard.object(forKey: key) != nil else { return true }
        return UserDefaults.standard.bool(forKey: key)
    }

    /// 横幅过滤入口：Kotlin 推来的 `type` 字符串直接查开关。
    /// 未知类型（后端新增分类而客户端还没跟上）一律放行，避免静默丢通知。
    func isEnabled(type: String) -> Bool {
        guard let category = FoxNotificationCategory(rawValue: type) else { return true }
        return isEnabled(category)
    }

    func setEnabled(_ category: FoxNotificationCategory, _ enabled: Bool) {
        UserDefaults.standard.set(enabled, forKey: Self.prefix + category.rawValue)
        revision &+= 1
    }
}

// MARK: - 页面

/// 通知设置页 —— 对应 Android `NotificationSettingsScreen`。
///
/// 结构对齐：「消息提醒」分组标题 + 8 个开关，副标题同为「前台横幅与后台系统通知」。
/// 关掉某类后，该类通知的**应用内横幅不再弹出**（见 `InAppNotificationCenter.present`）；
/// 消息本身仍会进会话与未读角标 —— 关的是"提醒"，不是"消息"。
struct NotificationSettingsView: View {

    @ObservedObject private var store = NotificationPreferenceStore.shared

    var body: some View {
        List {
            Section {
                ForEach(FoxNotificationCategory.allCases) { category in
                    Toggle(isOn: Binding(
                        get: { store.isEnabled(category) },
                        set: { store.setEnabled(category, $0) }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(category.label)
                            Text("前台横幅与后台系统通知")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .accessibilityIdentifier("notification.toggle.\(category.rawValue)")
                }
            } header: {
                Text("消息提醒")
            } footer: {
                Text("关闭后，该类通知在应用内不再弹出提醒横幅；消息与未读数不受影响。")
            }
        }
        .navigationTitle("通知设置")
        .navigationBarTitleDisplayMode(.inline)
    }
}
