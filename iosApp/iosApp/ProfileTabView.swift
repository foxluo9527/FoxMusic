import SwiftUI
import FoxShared

enum ProfileRoute: Hashable {
    case playlistDetail(id: Int64, title: String)
    case artistDetail(id: Int64)
    case settings
}

struct ProfileTabView: View {
    @ObservedObject private var auth = AuthStore.shared
    @State private var path: [ProfileRoute] = []
    @State private var showLogin = false

    var body: some View {
        NavigationStack(path: $path) {
            ProfileComposeView(
                isLoggedIn: auth.isLoggedIn,
                onLogin: { showLogin = true },
                onSettings: { path.append(ProfileRoute.settings) },
                onPlaylistClick: { id, title in
                    path.append(.playlistDetail(id: id.int64Value, title: title))
                },
                onArtistClick: { id in
                    path.append(.artistDetail(id: id.int64Value))
                },
                onAlbumClick: { _ in },
                onMusicClick: { song in
                    if let song { PlayerModel.shared.play(song) }
                }
            )
            .ignoresSafeArea(edges: [.top, .bottom])
            .navigationBarHidden(true)
            .themedPageBackground()
            .navigationDestination(for: ProfileRoute.self) { route in
                switch route {
                case .playlistDetail(let id, let title):
                    PlaylistDetailView(playlistId: id, isRank: false, title: title)
                case .artistDetail(let id):
                    ArtistDetailView(artistId: id)
                case .settings:
                    SettingsView(onLogin: { showLogin = true })
                }
            }
        }
        .fullScreenCover(isPresented: $showLogin) {
            LoginComposeView(
                onSuccess: {
                    AuthStore.shared.persistFromKotlin()
                    showLogin = false
                },
                onBack: { showLogin = false }
            )
            .ignoresSafeArea()
        }
    }
}

struct ProfileComposeView: UIViewControllerRepresentable {
    var isLoggedIn: Bool
    let onLogin: () -> Void
    let onSettings: () -> Void
    let onPlaylistClick: (KotlinLong, String) -> Void
    let onArtistClick: (KotlinLong) -> Void
    let onAlbumClick: (KotlinLong) -> Void
    let onMusicClick: (PlayableSong?) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxProfileScreen.shared.makeProfileViewController(
            onLogin: onLogin,
            onSettings: onSettings,
            onPlaylistClick: onPlaylistClick,
            onArtistClick: onArtistClick,
            onAlbumClick: onAlbumClick,
            onMusicClick: onMusicClick
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        _ = isLoggedIn
    }
}

struct LoginComposeView: UIViewControllerRepresentable {
    let onSuccess: () -> Void
    let onBack: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxLoginScreen.shared.makeLoginViewController(
            onLoginSuccess: { _ in onSuccess() },
            onBack: onBack
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - 设置页

/// 设置页（iOS 原生 List 风格，对齐 Android SettingsScreen 的能力范围）。
///
/// 游客也能进：账号区在未登录时显示「登录 FoxMusic」入口（此前游客点齿轮
/// 被接到登录、已登录点齿轮被直接退出登录，两个都不对）。
/// 已迁移的真实能力：歌词字号 / 歌词翻译（与播放器歌词设置共用 PlayerModel，
/// 两处联动）。主题 / 隐私 / 锁屏歌词尚未迁移，占位标灰。
struct SettingsView: View {
    @ObservedObject private var auth = AuthStore.shared
    @ObservedObject private var player = PlayerModel.shared
    @Environment(\.dismiss) private var dismiss
    @State private var confirmLogout = false

    var onLogin: () -> Void

    private var accountName: String {
        let user = FoxAuthStore.shared.user
        return user?.nickname ?? user?.username ?? ""
    }

    private var appVersion: String {
        let v = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let b = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "\(v) (\(b))"
    }

    var body: some View {
        List {
            Section("账号") {
                if auth.isLoggedIn {
                    LabeledContent {
                        Text(accountName).foregroundStyle(.secondary).lineLimit(1)
                    } label: {
                        Label("账号", systemImage: "person.circle")
                    }
                    .accessibilityIdentifier("settings.account")

                    Button(role: .destructive) {
                        confirmLogout = true
                    } label: {
                        Text("退出登录")
                    }
                    .accessibilityIdentifier("settings.logout")
                } else {
                    Button {
                        onLogin()
                    } label: {
                        Label("登录 FoxMusic", systemImage: "person.crop.circle.badge.plus")
                    }
                    .accessibilityIdentifier("settings.login")

                    Text("登录后可同步歌单、收藏与听歌记录")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Section {
                HStack(spacing: 12) {
                    Image(systemName: "textformat.size").foregroundStyle(.secondary)
                    Slider(value: $player.lyricFontSize, in: 14...30, step: 1)
                        .accessibilityIdentifier("settings.fontSize")
                    Text("\(Int(player.lyricFontSize))")
                        .font(.footnote.monospacedDigit())
                        .foregroundStyle(.secondary)
                        .frame(width: 24)
                }
                Toggle(isOn: $player.showTranslation) {
                    Label("歌词翻译", systemImage: "character.bubble")
                }
                .accessibilityIdentifier("settings.translation")
            } header: {
                Text("歌词")
            } footer: {
                Text("与播放器「歌词设置」面板共用同一份配置，两端实时联动。")
            }

            Section("通用") {
                pendingRow(title: "主题设置", icon: "paintpalette")
                pendingRow(title: "隐私设置", icon: "hand.raised")
                pendingRow(title: "锁屏与灵动岛歌词", icon: "lock.iphone")
            }

            Section {
                LabeledContent {
                    Text(appVersion).foregroundStyle(.secondary)
                } label: {
                    Label("版本", systemImage: "info.circle")
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("设置")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("退出登录", isPresented: $confirmLogout, titleVisibility: .visible) {
            Button("退出登录", role: .destructive) {
                AuthStore.shared.logout()
            }
            Button("取消", role: .cancel) {}
        }
    }

    private func pendingRow(title: String, icon: String) -> some View {
        HStack {
            Label(title, systemImage: icon)
            Spacer()
            Text("迁移中")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
    }
}
