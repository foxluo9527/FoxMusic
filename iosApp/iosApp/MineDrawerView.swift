// xcode: set sdk=iOS

import SwiftUI
import FoxShared

/// 根级模态目标（消息二级页 / 音流 / 抽屉里的工具页）。
/// 用**单一** `.fullScreenCover(item:)` 覆盖，避免多个 fullScreenCover 互相抢占。
enum RootModal: String, Identifiable {
    case messages
    case stream
    case editProfile
    case downloadManager
    case theme
    case manageLibrary
    case settings
    case login

    var id: String { rawValue }
}

/// 仿网易云左侧抽屉：资料卡 + 常用功能入口 + 底部「设置」。
struct MineDrawerView: View {
    @ObservedObject private var auth = AuthStore.shared
    @ObservedObject private var badges = RealtimeBadgeCenter.shared
    @ObservedObject private var player = PlayerModel.shared

    let onClose: () -> Void

    @State private var showSleepPicker = false

    private let router = AppTabRouter.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header

            ScrollView {
                VStack(alignment: .leading, spacing: 2) {
                    row(icon: "envelope", title: "我的消息", badge: badges.unreadCount) {
                        router.openMessages(nil)
                    }
                    row(icon: "sparkles", title: "AI 音乐助手") {
                        onClose()
                        router.selectedTab = .home
                        router.isAssistantPresented = true
                    }
                    row(icon: "person.text.rectangle", title: "资料编辑") {
                        onClose()
                        router.rootModal = .editProfile
                    }
                    row(icon: "timer", title: "定时关闭") {
                        showSleepPicker = true
                    }
                    row(icon: "arrow.down.circle", title: "下载管理") {
                        onClose()
                        router.rootModal = .downloadManager
                    }
                    row(icon: "paintpalette", title: "主题设置") {
                        onClose()
                        router.rootModal = .theme
                    }
                    row(icon: "music.note.list", title: "曲库管理") {
                        onClose()
                        router.rootModal = .manageLibrary
                    }
                }
                .padding(.horizontal, 8)
                .padding(.top, 8)
            }

            Divider()

            Button {
                onClose()
                router.rootModal = .settings
            } label: {
                HStack(spacing: 14) {
                    Image(systemName: "gearshape").font(.system(size: 18)).frame(width: 24)
                    Text("设置").font(.system(size: 16, weight: .medium))
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 18)
                .frame(height: 58)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("drawer.settings")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color(uiColor: .systemBackground))
        .confirmationDialog("定时关闭", isPresented: $showSleepPicker, titleVisibility: .visible) {
            ForEach(PlayerModel.sleepTimerOptions, id: \.self) { minutes in
                Button("\(minutes) 分钟后停止播放") {
                    player.startSleepTimer(minutes: minutes)
                    ToastCenter.shared.show("将在 \(minutes) 分钟后停止播放")
                    onClose()
                }
            }
            if player.sleepTimerEndsAt != nil {
                Button("取消定时关闭", role: .destructive) {
                    player.cancelSleepTimer()
                    ToastCenter.shared.show("已取消定时关闭")
                    onClose()
                }
            }
            Button("取消", role: .cancel) {}
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            AsyncImage(url: auth.avatarUrl.flatMap(URL.init(string:))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                } else {
                    ZStack {
                        Color.secondary.opacity(0.2)
                        Image(systemName: "person.fill").foregroundStyle(.secondary)
                    }
                }
            }
            .frame(width: 52, height: 52)
            .clipShape(Circle())

            VStack(alignment: .leading, spacing: 4) {
                Text(displayName)
                    .font(.system(size: 17, weight: .semibold))
                    .lineLimit(1)
                Text(auth.isLoggedIn ? "查看我的音乐" : "登录后同步歌单与收藏")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 18)
        .padding(.top, 20)
        .padding(.bottom, 16)
        .contentShape(Rectangle())
        .onTapGesture {
            onClose()
            router.selectedTab = .profile
        }
        .accessibilityIdentifier("drawer.header")
    }

    private var displayName: String {
        guard auth.isLoggedIn else { return "点击登录" }
        return auth.nickname.isEmpty ? "音乐爱好者" : auth.nickname
    }

    @ViewBuilder
    private func row(
        icon: String,
        title: String,
        badge: Int = 0,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon).font(.system(size: 18)).frame(width: 24)
                Text(title).font(.system(size: 16))
                Spacer(minLength: 0)
                if badge > 0 {
                    let label = badge > 99 ? "99+" : "\(badge)"
                    Text(label)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.white)
                        // 高度固定 18，单数字宽度也固定 18 => 标准圆形；
                        // 多位数放开宽度、横向补 5pt => 胶囊形。
                        .frame(width: label.count > 1 ? nil : 18, height: 18)
                        .padding(.horizontal, label.count > 1 ? 5 : 0)
                        .background(Capsule().fill(.red))
                }
            }
            .padding(.horizontal, 10)
            .frame(height: 50)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("drawer.row.\(title)")
    }
}

/// 抽屉宿主：遮罩 + 从左滑入的面板。挂在 ContentView 的 ZStack 最上层。
struct MineDrawerHost: View {
    @ObservedObject private var router = AppTabRouter.shared

    var body: some View {
        ZStack(alignment: .leading) {
            if router.isDrawerOpen {
                Color.black.opacity(0.35)
                    .ignoresSafeArea()
                    .transition(.opacity)
                    .onTapGesture { router.isDrawerOpen = false }
                MineDrawerView(onClose: { router.isDrawerOpen = false })
                    .frame(width: 300)
                    .ignoresSafeArea(edges: .bottom)
                    .transition(.move(edge: .leading))
            }
        }
        .animation(.easeInOut(duration: 0.25), value: router.isDrawerOpen)
    }
}

/// 主题设置页（抽屉入口）：深色模式 + 主题色，与「设置」里的同名菜单同源。
struct ThemeSettingsView: View {
    @ObservedObject private var appearance = AppearanceStore.shared

    var body: some View {
        Form {
            Section("深色模式") {
                ForEach(AppAppearance.allCases) { mode in
                    Button {
                        appearance.appearance = mode
                    } label: {
                        HStack {
                            Text(mode.label).foregroundStyle(.primary)
                            Spacer()
                            if appearance.appearance == mode {
                                Image(systemName: "checkmark").foregroundStyle(.tint)
                            }
                        }
                    }
                }
            }
            Section("主题色") {
                ForEach(AppThemeAccent.allCases) { accent in
                    Button {
                        appearance.accent = accent
                    } label: {
                        HStack {
                            Text(accent.label).foregroundStyle(.primary)
                            Spacer()
                            if appearance.accent == accent {
                                Image(systemName: "checkmark").foregroundStyle(.tint)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("主题设置")
        .navigationBarTitleDisplayMode(.inline)
    }
}
