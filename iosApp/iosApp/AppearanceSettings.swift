// xcode: set sdk=iOS

import SwiftUI
import UIKit
import FoxShared

// MARK: - 深色模式

/// 深色模式 —— 对应 Android `SettingsScreen`「应用 › 深色模式」。
///
/// 取值与文案逐字对齐 Android `SettingsViewModel.darkModeLabel`：
/// `DarkMode.FOLLOW_SYSTEM / LIGHT / DARK` → 跟随系统 / 浅色 / 深色。
enum AppAppearance: String, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: return "跟随系统"
        case .light: return "浅色"
        case .dark: return "深色"
        }
    }

    /// `nil` = 不覆盖，交给系统（等价 Android 的 FOLLOW_SYSTEM）。
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

// MARK: - 主题色

/// 主题色 —— 对应 Android `SettingsScreen`「应用 › 主题设置」+ `AppThemePresets.catalog`。
///
/// 取色直接对齐各预设的 `previewColors[0]`（即该预设主色），保证两端切换后主色一致。
/// 额外提供「跟随系统」（iOS 默认蓝），对 Android 而言没有这个选项 ——
/// 它是平台默认值，不作为品牌预设之一。
enum AppThemeAccent: String, CaseIterable, Identifiable {
    case system
    case vinylNight
    case foxPurple
    case youthful
    case nobleClassical
    case oceanBreeze
    case forestMist
    case sunsetWarm

    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: return "跟随系统"
        case .vinylNight: return "黑胶夜场"
        case .foxPurple: return "紫韵经典"
        case .youthful: return "青春洋溢"
        case .nobleClassical: return "尊贵古典"
        case .oceanBreeze: return "海洋清风"
        case .forestMist: return "森林薄雾"
        case .sunsetWarm: return "暮色暖阳"
        }
    }

    /// `nil` = 用系统 tint（默认蓝）。
    var color: Color? {
        switch self {
        case .system: return nil
        case .vinylNight: return Color(hex: 0xE8A838)
        case .foxPurple: return Color(hex: 0x6C63FF)
        case .youthful: return Color(hex: 0xFF6B6B)
        case .nobleClassical: return Color(hex: 0xC9A227)
        case .oceanBreeze: return Color(hex: 0x2196F3)
        case .forestMist: return Color(hex: 0x43A047)
        case .sunsetWarm: return Color(hex: 0xFF7043)
        }
    }

    /// 对应 Kotlin `AppThemeIds` 的预设 ID（与 Android `AppTheme.kt` 逐字对齐），
    /// 推给 Compose 宿主（FoxIosThemeBridge）后 Compose 页面才能跟随主题换背景。
    /// `.system` 不是 Android 预设 —— 落到默认主题「黑胶夜场」。
    var themeId: String {
        switch self {
        case .system, .vinylNight: return "vinyl_night"
        case .foxPurple: return "fox_purple"
        case .youthful: return "youthful"
        case .nobleClassical: return "noble_classical"
        case .oceanBreeze: return "ocean_breeze"
        case .forestMist: return "forest_mist"
        case .sunsetWarm: return "sunset_warm"
        }
    }
}

// MARK: - 存储

/// 外观偏好（深色模式 + 主题色）。
///
/// 为什么存在 Swift 而不是走 Kotlin（`FoxShared` / DataStore）：
/// 这两项是**纯渲染层**偏好，只被 SwiftUI 的 `.preferredColorScheme` 与 `.tint` 消费，
/// Kotlin 侧没有任何读取方 —— 跨端同步没有收益，反而多一条桥接。
/// 落库方式与 `PlayerModel.repeatMode` 同口径（UserDefaults 标量，便宜可频繁写）。
///
/// ⚠️ `preferredColorScheme` 只在 `.system` 时返回 nil：返回 `.light`/`.dark` 会把
/// **整个窗口**（含 sheet / fullScreenCover 内未自带覆盖的页面）锁定到该外观。
/// 播放器自身刻意硬编码 `.preferredColorScheme(.dark)`（沉浸式深色），不受影响。
final class AppearanceStore: ObservableObject {

    static let shared = AppearanceStore()

    private static let appearanceKey = "fox_appearance_mode"
    private static let accentKey = "fox_theme_accent"

    @Published var appearance: AppAppearance {
        didSet {
            guard appearance != oldValue else { return }
            UserDefaults.standard.set(appearance.rawValue, forKey: Self.appearanceKey)
            pushToKotlin()
        }
    }

    @Published var accent: AppThemeAccent {
        didSet {
            guard accent != oldValue else { return }
            UserDefaults.standard.set(accent.rawValue, forKey: Self.accentKey)
            pushToKotlin()
        }
    }

    private init() {
        // UI 测试用：把外观重置回默认，避免上一轮测试把模拟器留在深色/特定主题色。
        if CommandLine.arguments.contains("-UITestResetAppearance") {
            UserDefaults.standard.removeObject(forKey: Self.appearanceKey)
            UserDefaults.standard.removeObject(forKey: Self.accentKey)
        }
        let rawAppearance = UserDefaults.standard.string(forKey: Self.appearanceKey)
        appearance = rawAppearance.flatMap(AppAppearance.init(rawValue:)) ?? .system
        let rawAccent = UserDefaults.standard.string(forKey: Self.accentKey)
        accent = rawAccent.flatMap(AppThemeAccent.init(rawValue:)) ?? .system
        // 启动时同步一次初始外观，保证 Compose 页面从第一帧就拿到正确的主题/深色模式。
        pushToKotlin()
    }

    /// 把外观推给 Compose 宿主（Kotlin `FoxIosThemeState`）。
    ///
    /// 没有这一步，`foxComposeViewController` 永远用默认主题渲染 —— 表现为
    /// 设置里切换主题后，Compose 页面背景/主色纹丝不动（只 SwiftUI 外壳的 tint 变了）。
    /// darkModeOverride：-1 跟随系统 / 0 浅色 / 1 深色，与 Kotlin 侧注释同口径。
    private func pushToKotlin() {
        let darkOverride: Int
        switch appearance {
        case .system: darkOverride = -1
        case .light: darkOverride = 0
        case .dark: darkOverride = 1
        }
        FoxIosThemeBridge.shared.setAppearance(
            appThemeId: accent.themeId,
            darkModeOverride: Int32(darkOverride),
            systemDark: Self.systemIsDark
        )
        // 窗口补色（状态栏那条渐变顶色）也要跟着主题走。
        FoxTheme.applyToWindowBackground()
    }

    /// 系统当前是否深色。取 UIScreen 的 trait（不被 SwiftUI preferredColorScheme
    /// 锁定影响），供「跟随系统」时推送宿主补色。
    private static var systemIsDark: Bool {
        UIScreen.main.traitCollection.userInterfaceStyle == .dark
    }

    /// 系统外观在运行期间变化时（SystemAppearanceObserver 回调）重新推送一次。
    func syncSystemDark() {
        pushToKotlin()
        // 跟随系统模式下系统深浅切换不会改动 `appearance`/`accent`，
        // 需手动通知观察者重组，让 SwiftUI 背景取到新的宿主补色。
        objectWillChange.send()
    }

    /// 交给根视图 `.preferredColorScheme(...)`。
    var preferredColorScheme: ColorScheme? { appearance.colorScheme }

    /// 交给根视图 `.tint(...)`。默认（跟随系统）时用系统 accent。
    var tintColor: Color { accent.color ?? .accentColor }
}

// MARK: - 工具

extension Color {
    /// 0xRRGGBB → Color。用于把 Android 端 `previewColors` 的十六进制原样搬过来，
    /// 避免用 SwiftUI 语义色（`.blue` 等）导致两端主色对不上。
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: 1.0
        )
    }
}
