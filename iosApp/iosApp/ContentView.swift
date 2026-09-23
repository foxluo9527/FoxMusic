import SwiftUI
import UIKit
import FoxShared

/// 全屏播放器展示协调器（单例）：迷你条点击 → 置 true；ContentView 监听后弹全屏。
final class PlayerUICoordinator: ObservableObject {
    static let shared = PlayerUICoordinator()
    @Published var isFullPlayerPresented = false
}

/// 应用根视图：SwiftUI TabView 外壳 —— 与 Android 端 AppDestinations 对齐的 5 个 Tab：
/// 首页 / 发现 / 社区 / 消息 / 我的。
///
/// 注意：迷你播放条不能挂在 TabView 上（safeAreaInset(edge: .bottom) 挂在
/// TabView 上会盖住 Tab 栏，Tab 按钮全部点不到）——必须挂在每个 Tab 内容
/// 内部（withMiniPlayer），让迷你条渲染在 Tab 栏之上、与 Tab 栏共存。
struct ContentView: View {
    @StateObject private var playerUI = PlayerUICoordinator.shared

    var body: some View {
        TabView {
            HomeTabView()
                .withMiniPlayer()
                .tabItem { Label("首页", systemImage: "house.fill") }
            DiscoverTabView()
                .withMiniPlayer()
                .tabItem { Label("发现", systemImage: "safari.fill") }
            PlaceholderTabView(title: "社区", icon: "bubble.left.and.bubble.right.fill")
                .withMiniPlayer()
                .tabItem { Label("社区", systemImage: "bubble.left.and.bubble.right") }
            PlaceholderTabView(title: "消息", icon: "envelope.fill")
                .withMiniPlayer()
                .tabItem { Label("消息", systemImage: "envelope") }
            PlaceholderTabView(title: "我的", icon: "person.crop.circle.fill")
                .withMiniPlayer()
                .tabItem { Label("我的", systemImage: "person.crop.circle") }
        }
        // 顶部安全区（状态栏）补同色底：Compose 页面被 SwiftUI 按安全区缩进，
        // 那条区域默认是系统白，会与页面浅紫底色割裂。两层都补，哪层生效都行。
        .background(FoxTheme.pageBackground.ignoresSafeArea())
        .onAppear(perform: FoxTheme.applyToWindowBackground)
        .fullScreenCover(isPresented: $playerUI.isFullPlayerPresented) {
            PlayerView()
        }
    }
}

/// 迷你播放条修饰器：挂在每个 Tab 的根视图上（而非 TabView），
/// 这样迷你条位于 Tab 栏上方，两者互不遮挡。
struct MiniPlayerModifier: ViewModifier {
    @ObservedObject private var player = PlayerModel.shared

    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .bottom) {
            if let current = player.current {
                MiniPlayerBar(current: current, isPlaying: player.isPlaying)
            }
        }
    }
}

extension View {
    func withMiniPlayer() -> some View { modifier(MiniPlayerModifier()) }
}

/// 全局迷你播放条（从旧 ContentView 抽成组件，跨 Tab 常驻）。
struct MiniPlayerBar: View {
    let current: PlayableSong
    let isPlaying: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(current.title).font(.headline).lineLimit(1)
                    .accessibilityIdentifier("miniPlayer.title")
                Text(current.artist)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .contentShape(Rectangle())
            .onTapGesture { PlayerUICoordinator.shared.isFullPlayerPresented = true }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isButton)
            .accessibilityIdentifier("miniPlayer.bar")
            Spacer()
            Button {
                PlayerModel.shared.togglePlayPause()
            } label: {
                Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                    .font(.system(size: 36))
            }
            .accessibilityIdentifier("miniPlayer.playPause")
            .accessibilityLabel(isPlaying ? "pause.circle.fill" : "play.circle.fill")
        }
        .padding()
        .background(.bar)
    }
}

/// 占位 Tab（社区/消息/我的）：对应共享 Content 尚未接入，后续批次替换。
struct PlaceholderTabView: View {
    let title: String
    let icon: String

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 48))
                    .foregroundStyle(.secondary)
                Text("\(title)页迁移中")
                    .foregroundStyle(.secondary)
            }
            .navigationTitle(title)
        }
    }
}

// MARK: - 发现 Tab

/// 发现页路由：榜单详情 / 艺人详情 / 搜索。
enum DiscoverRoute: Hashable {
    case rankDetail(id: Int64, title: String)
    case artistDetail(id: Int64)
    case search
}

/// 发现 Tab：共享层 DiscoverContent（feature:discover commonMain）渲染 ——
/// 与 Android 发现页同一份 UI（热搜词+热门榜单+新歌速递+热门歌手）。
struct DiscoverTabView: View {
    @State private var path: [DiscoverRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            DiscoverComposeView(
                onMusicClick: { song in
                    if let song { PlayerModel.shared.play(song) }
                },
                onRankClick: { id, title in
                    path.append(.rankDetail(id: id, title: title))
                },
                onArtistClick: { id in
                    path.append(.artistDetail(id: id))
                },
                onSearchClick: {
                    path.append(.search)
                }
            )
        .ignoresSafeArea(edges: .bottom)
        .navigationBarHidden(true)
        .themedPageBackground()
        .navigationDestination(for: DiscoverRoute.self) { route in
                switch route {
                case .rankDetail(let id, let title):
                    PlaylistDetailView(playlistId: id, isRank: true, title: title)
                case .artistDetail(let id):
                    ArtistDetailView(artistId: id)
                case .search:
                    SearchView(onArtistClick: { id in path.append(.artistDetail(id: id)) })
                }
            }
        }
    }
}

/// 把 Kotlin 侧发现页 VC 包进 SwiftUI。
struct DiscoverComposeView: UIViewControllerRepresentable {
    let onMusicClick: (PlayableSong?) -> Void
    let onRankClick: (Int64, String) -> Void
    let onArtistClick: (Int64) -> Void
    let onSearchClick: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxDiscoverScreen.shared.makeDiscoverViewController(
            onMusicClick: onMusicClick,
            onRankClick: { id, title in
                onRankClick(id.int64Value, title)
            },
            onArtistClick: { id in
                onArtistClick(id.int64Value)
            },
            onSearchClick: onSearchClick
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// 艺人详情页：共享层 ArtistDetailContent（feature:discover commonMain）渲染 ——
/// 与 Android 歌手详情页同一份 UI（头像头部 + 热门歌曲 + 专辑）。
struct ArtistDetailView: View {
    let artistId: Int64
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ArtistDetailComposeView(
            artistId: artistId,
            onMusicClick: { song in
                if let song { PlayerModel.shared.play(song) }
            },
            onAlbumClick: { _ in },
            onBack: { dismiss() }
        )
        .ignoresSafeArea(edges: .bottom)
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
    }
}

/// 把 Kotlin 侧歌手详情 VC 包进 SwiftUI。
struct ArtistDetailComposeView: UIViewControllerRepresentable {
    let artistId: Int64
    let onMusicClick: (PlayableSong?) -> Void
    let onAlbumClick: (Int64) -> Void
    let onBack: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxArtistDetailScreen.shared.makeArtistDetailViewController(
            artistId: artistId,
            onMusicClick: onMusicClick,
            onAlbumClick: { onAlbumClick($0.int64Value) },
            onBack: onBack
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// 搜索页：共享层 SearchContent（feature:search commonMain）渲染 ——
/// 与 Android 搜索页同一份 UI（搜索框+单曲/歌手/专辑/帖子 Tab+平台切换+热词面板）。
struct SearchView: View {
    var onArtistClick: (Int64) -> Void = { _ in }
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SearchComposeView(
            onBack: { dismiss() },
            onMusicClick: { song in
                if let song { PlayerModel.shared.play(song) }
            },
            onArtistClick: onArtistClick,
            onAlbumClick: { _ in }
        )
        .ignoresSafeArea(edges: .bottom)
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
    }
}

/// 把 Kotlin 侧搜索 VC 包进 SwiftUI。
struct SearchComposeView: UIViewControllerRepresentable {
    let onBack: () -> Void
    let onMusicClick: (PlayableSong?) -> Void
    let onArtistClick: (Int64) -> Void
    let onAlbumClick: (Int64) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxSearchScreen.shared.makeSearchViewController(
            onBack: onBack,
            onMusicClick: onMusicClick,
            onArtistClick: { onArtistClick($0.int64Value) },
            onAlbumClick: { onAlbumClick($0.int64Value) }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - 歌单/榜单详情页

/// 歌单/榜单详情页：共享层 PlaylistDetailContent（feature:playlist commonMain）渲染 ——
/// 与 Android 歌单详情页同一份 UI（大封面头部+播放全部/收藏+分页曲目）。
///
/// 返回：页面导航栏被隐藏（内容里自带返回箭头，与 Android 一致），
/// 所以必须把 Kotlin 的 onBack 接回 SwiftUI 的 dismiss()，否则点箭头无反应。
struct PlaylistDetailView: View {
    let playlistId: Int64
    let isRank: Bool
    let title: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        PlaylistDetailComposeView(
            playlistId: playlistId,
            isRank: isRank,
            onMusicClick: { song in
                if let song { PlayerModel.shared.play(song) }
            },
            onPlayAll: { songs in
                // 播放全部：从第一页曲目开始顺序播放
                if let first = songs.first {
                    PlayerModel.shared.play(first)
                }
            },
            onBack: { dismiss() }
        )
        .ignoresSafeArea(edges: .bottom)
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
    }
}

/// 把 Kotlin 侧歌单详情 VC 包进 SwiftUI。
struct PlaylistDetailComposeView: UIViewControllerRepresentable {
    let playlistId: Int64
    let isRank: Bool
    let onMusicClick: (PlayableSong?) -> Void
    let onPlayAll: ([PlayableSong]) -> Void
    let onBack: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxPlaylistDetailScreen.shared.makePlaylistDetailViewController(
            playlistId: playlistId,
            useRankEndpoint: isRank,
            onMusicClick: onMusicClick,
            onPlayAll: onPlayAll,
            onBack: onBack
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - 侧滑返回支持

/// 详情页用了 `.navigationBarHidden(true)`（页面内自带返回箭头，与 Android 一致），
/// 而 UIKit 在导航栏隐藏时会把 `interactivePopGestureRecognizer` 关掉 —— 表现为
/// 边缘侧滑无反应。这里把它重新打开。
///
/// 注意：直接把 delegate 置 nil 是常见写法，但在**根页面**侧滑会因为没有可弹出的
/// 页面而卡死。所以换成带数量守卫的 delegate。
final class PopGestureDelegate: NSObject, UIGestureRecognizerDelegate {
    static let shared = PopGestureDelegate()

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard let nav = gestureRecognizer.view as? UINavigationController else { return false }
        return nav.viewControllers.count > 1
    }
}

/// 零尺寸、不接收点击的子 VC：出现时恢复侧滑返回手势。
final class SwipeBackRestorer: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard let gesture = navigationController?.interactivePopGestureRecognizer else { return }
        gesture.isEnabled = true
        gesture.delegate = PopGestureDelegate.shared
    }
}

struct SwipeBackRestore: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { SwipeBackRestorer() }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

extension View {
    /// 挂在隐藏了导航栏的详情页上，恢复 iOS 边缘侧滑返回。
    func swipeBackEnabled() -> some View {
        background(
            SwipeBackRestore()
                .frame(width: 0, height: 0)
                .allowsHitTesting(false)
        )
    }
}

// MARK: - 主题底色补色

/// 页面主色（与 Compose 页面底色 = MaterialTheme.colorScheme.background 一致）。
///
/// 存在的理由：共享 Compose 页面被 SwiftUI 按安全区缩进，**状态栏那条区域**
/// 不归 Compose 管，默认是系统白 —— 页面是浅紫(#FEF7FF)、上面却是白，就有割裂感。
/// 这里把这个区域也补成同色。
enum FoxTheme {
    static var pageBackground: Color {
        // 注意：直接调用 Kotlin 函数时，Kotlin 的 Long 在 Swift 侧就是 Int64（不是 KotlinLong），
        // 无需 .int64Value —— 只有传给 Kotlin 函数类型的闭包参数才需要 KotlinLong。
        Color(argb: UInt32(truncatingIfNeeded: FoxIosTheme.shared.pageBackgroundArgb()))
    }

    /// 兜底：UIWindow 底色也一起改 —— 状态栏区域在部分层级下不由 SwiftUI 绘制。
    static func applyToWindowBackground() {
        let color = UIColor(pageBackground)
        for scene in UIApplication.shared.connectedScenes {
            guard let windowScene = scene as? UIWindowScene else { continue }
            for window in windowScene.windows {
                window.backgroundColor = color
            }
        }
    }
}

extension Color {
    /// 0xAARRGGBB → Color
    init(argb: UInt32) {
        self.init(
            .sRGB,
            red: Double((argb >> 16) & 0xFF) / 255.0,
            green: Double((argb >> 8) & 0xFF) / 255.0,
            blue: Double(argb & 0xFF) / 255.0,
            opacity: Double((argb >> 24) & 0xFF) / 255.0
        )
    }
}

extension View {
    /// 给被 SwiftUI 按安全区缩进的 Compose 页面补一层**全屏底色**（含状态栏那条）。
    ///
    /// 注意：不能只挂在 TabView 上 —— TabView 自己会画一层不透明的系统底色，
    /// 把 `.background()` 盖掉，所以必须画在每个 Tab 内容（即 Compose 页面）这一层。
    func themedPageBackground() -> some View {
        background(FoxTheme.pageBackground.ignoresSafeArea())
    }
}
