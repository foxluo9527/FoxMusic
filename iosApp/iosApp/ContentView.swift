import SwiftUI
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
            }
        )
        .ignoresSafeArea(edges: .bottom)
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
    }
}

/// 把 Kotlin 侧歌单详情 VC 包进 SwiftUI。
struct PlaylistDetailComposeView: UIViewControllerRepresentable {
    let playlistId: Int64
    let isRank: Bool
    let onMusicClick: (PlayableSong?) -> Void
    let onPlayAll: ([PlayableSong]) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxPlaylistDetailScreen.shared.makePlaylistDetailViewController(
            playlistId: playlistId,
            useRankEndpoint: isRank,
            onMusicClick: onMusicClick,
            onPlayAll: onPlayAll,
            onBack: {}
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
