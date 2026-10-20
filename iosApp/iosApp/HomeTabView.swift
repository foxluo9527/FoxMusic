import SwiftUI
import FoxShared

/// 首页 Tab：共享层 HomeContent（feature:home commonMain）渲染 ——
/// 与 Android 首页同一份 UI（品牌头+搜索框+最近在听+推荐歌单+为你精选+热门专辑）。
/// 分页与数据全部在 Kotlin 侧（Ktor + Paging3），Swift 只接导航/播放回调。
enum HomeRoute: Hashable {
    case playlistDetail(id: Int64, title: String, isRank: Bool)
    case artistDetail(id: Int64)
    case albumDetail(id: Int64)
    case search
    /// 推荐歌单「查看更多」→ 全部歌单页（歌单广场，分类 Tab）
    case playlistList
    /// 热门专辑「更多」→ 全部专辑页
    case albumList
    /// 为你精选「更多」→ 虚拟歌单详情页（DetailType.RECOMMEND，sort=recommend）
    case recommendList
}

/// 助手 open_screen 动作待消费项。
/// 必须是 Equatable 具体类型（元组不满足 Equatable，无法用于 onChange(of:)）。
private struct AssistantScreenLink: Equatable {
    let screen: String
    let targetId: Int64?
}

struct HomeTabView: View {
    @State private var path: [HomeRoute] = []
    @State private var moreSong: Music?
    @StateObject private var router = AppTabRouter.shared
    /// AI 音乐助手（功能二）入口已从首页搜索框移到左侧抽屉：
    /// 抽屉点「AI 音乐助手」→ 切到首页 Tab + 置 `router.isAssistantPresented`，由本页模态呈现。
    /// open_screen 动作待消费：dismiss 助手后跳转。targetId 来自 Kotlin Long?。
    @State private var pendingAssistantScreen: AssistantScreenLink? = nil
    /// search_in_app 动作待消费：dismiss 助手后跳搜索。
    @State private var pendingAssistantSearch: String? = nil

    /// 消费播放列表「来源」卡片跳转：首页可承接歌单/专辑/榜单。
    private func consumePlayerSourceLink() {
        router.consumePlayerSource(in: .home) { link in
            switch link {
            case .playlist(let id, let title):
                path.append(.playlistDetail(id: id, title: title, isRank: false))
            case .rank(let id, let title):
                path.append(.playlistDetail(id: id, title: title, isRank: true))
            case .album(let id):
                path.append(.albumDetail(id: id))
            case .post:
                break
            }
        }
    }

    var body: some View {
        NavigationStack(path: $path) {
            HomeComposeView(
                onMusicClickFromList: { songs, index in
                    // 整列表入队并定位到点中的那首 —— 这样上一首/下一首才有东西可切
                    PlayerModel.shared.play(queue: songs, startIndex: Int(index.int32Value))
                },
                onPlaylistClick: { id, title, isRank in
                    path.append(.playlistDetail(id: id.int64Value, title: title, isRank: isRank.boolValue))
                },
                onAlbumClick: { id in
                    path.append(.albumDetail(id: id))
                },
                onSearchClick: {
                    path.append(.search)
                },
                onPlaylistCategoryClick: {
                    path.append(.playlistList)
                },
                onAlbumCategoryClick: {
                    path.append(.albumList)
                },
                onRecommendClick: {
                    path.append(.recommendList)
                },
                onMusicMoreClick: { music in
                    moreSong = music
                },
                onOpenDrawer: { router.openDrawer() }
            )
            .ignoresSafeArea(edges: [.top, .bottom])
            .navigationBarHidden(true)
            .themedPageBackground()
            .bottomSheet(isPresented: Binding(
                get: { moreSong != nil },
                set: { if !$0 { moreSong = nil } }
            )) {
                if let music = moreSong {
                    SongMoreMenu(
                        musicId: String(music.id),
                        title: music.title,
                        artist: music.artists.map { $0.name }.joined(separator: "、"),
                        coverUrl: music.coverImage,
                        sourceUrl: music.url
                    )
                }
            }
            .navigationDestination(for: HomeRoute.self) { route in
                switch route {
                case .playlistDetail(let id, let title, let isRank):
                    PlaylistDetailView(playlistId: id, isRank: isRank, title: title)
                case .artistDetail(let id):
                    ArtistDetailView(artistId: id, onAlbumClick: { path.append(.albumDetail(id: $0)) })
                case .albumDetail(let id):
                    PlaylistDetailView(playlistId: id, isRank: false, title: "专辑详情", detailType: "album")
                case .search:
                    SearchView(
                        onArtistClick: { id in path.append(.artistDetail(id: id)) },
                        onAlbumClick: { id in path.append(.albumDetail(id: id)) }
                    )
                case .playlistList:
                    PlaylistListView(onPlaylistClick: { id, title in
                        path.append(.playlistDetail(id: id, title: title, isRank: false))
                    })
                case .albumList:
                    AlbumListView(onAlbumClick: { id in
                        path.append(.albumDetail(id: id))
                    })
                case .recommendList:
                    // 与 Android onRecommendClick 一致：虚拟 id = Long.MAX_VALUE
                    PlaylistDetailView(playlistId: Int64.max, isRank: false, title: "为你精选", detailType: "recommend")
                }
            }
        }
        // 播放列表来源跳转：已在首页时 onChange 即时消费；从别的 Tab 切过来时 .task 兜底。
        .task { consumePlayerSourceLink() }
        .onChange(of: router.pendingPlayerSourceLink) { _ in
            consumePlayerSourceLink()
        }
        // AI 音乐助手（功能二）：模态全屏呈现，dismiss 后由待消费状态驱动跳转。
        .fullScreenCover(isPresented: $router.isAssistantPresented) {
            AssistantComposeView(
                onBack: { router.isAssistantPresented = false },
                onMessage: { ToastCenter.shared.show($0) },
                onOpenScreen: { screen, targetId in
                    router.isAssistantPresented = false
                    pendingAssistantScreen = AssistantScreenLink(screen: screen, targetId: targetId?.int64Value)
                },
                onSearchInApp: { keyword in
                    router.isAssistantPresented = false
                    pendingAssistantSearch = keyword
                },
                onPlaySongs: { songs, _ in
                    // 助手点播：整队列入队并从头播（对齐 Android 助手 setPlaylist 语义）。
                    PlayerModel.shared.play(queue: songs, startIndex: 0)
                }
            )
            .themedPageBackground()
            // 助手是模态全屏，会盖住 Tab 级迷你条 —— 模态内单独挂一条，播放后即出现。
            // 点它先关模态再打开全屏播放器（避免 fullScreenCover 叠加被系统排队而失效）。
            .withMiniPlayer(onOpenFullPlayer: {
                router.isAssistantPresented = false
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    PlayerUICoordinator.shared.isFullPlayerPresented = true
                }
            })
        }
        .onChange(of: pendingAssistantScreen) { _ in
            guard let link = pendingAssistantScreen else { return }
            pendingAssistantScreen = nil
            // 延后 0.3s 等 cover dismiss 动画结束，避免与导航栈竞态（同播放来源总线）。
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                handleAssistantScreen(link.screen, link.targetId)
            }
        }
        .onChange(of: pendingAssistantSearch) { _ in
            guard pendingAssistantSearch != nil else { return }
            pendingAssistantSearch = nil
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                path.append(.search)
            }
        }
    }

    /// 把助手 open_screen 动作的 screen 标识映射到真实跳转（对齐 Android openAssistantScreen）。
    /// Tab 路由切底栏；栈内路由 push 到首页 NavigationStack；缺参/未知值 Toast 兜底。
    private func handleAssistantScreen(_ screen: String, _ targetId: Int64?) {
        switch screen {
        case "home": AppTabRouter.shared.selectedTab = .home
        case "discover": AppTabRouter.shared.selectedTab = .discover
        case "community": AppTabRouter.shared.selectedTab = .community
        case "messages": AppTabRouter.shared.openMessages(nil)
        case "profile": AppTabRouter.shared.selectedTab = .profile
        case "search": path.append(.search)
        case "playlist":
            if let id = targetId { path.append(.playlistDetail(id: id, title: "", isRank: false)) }
            else { ToastCenter.shared.show("缺少歌单 ID") }
        case "album":
            if let id = targetId { path.append(.albumDetail(id: id)) }
            else { ToastCenter.shared.show("缺少专辑 ID") }
        case "artist":
            if let id = targetId { path.append(.artistDetail(id: id)) }
            else { ToastCenter.shared.show("缺少艺人 ID") }
        case "rank":
            if let id = targetId { path.append(.playlistDetail(id: id, title: "", isRank: true)) }
            else { ToastCenter.shared.show("缺少榜单 ID") }
        default:
            ToastCenter.shared.show("该页面暂未接入")
        }
    }
}

/// 把 Kotlin 侧首页 VC 包进 SwiftUI。
/// 滚动/数据状态留在 Kotlin/Compose 内部，updateUIViewController 留空避免重建丢位置。
struct HomeComposeView: UIViewControllerRepresentable {
    let onMusicClickFromList: ([PlayableSong], KotlinInt) -> Void
    let onPlaylistClick: (KotlinLong, String, KotlinBoolean) -> Void
    let onAlbumClick: (Int64) -> Void
    let onSearchClick: () -> Void
    let onPlaylistCategoryClick: () -> Void
    let onAlbumCategoryClick: () -> Void
    let onRecommendClick: () -> Void
    let onMusicMoreClick: (Music) -> Void
    /// 左上角三横「更多」→ 打开左侧抽屉。
    let onOpenDrawer: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxHomeScreen.shared.makeHomeViewController(
            onMusicClickFromList: onMusicClickFromList,
            onPlaylistClick: onPlaylistClick,
            onAlbumClick: { id in
                onAlbumClick(id.int64Value)
            },
            onSearchClick: onSearchClick,
            onPlaylistCategoryClick: onPlaylistCategoryClick,
            onAlbumCategoryClick: onAlbumCategoryClick,
            onRecommendClick: onRecommendClick,
            onMusicMoreClick: onMusicMoreClick,
            // AI 入口已从首页搜索框移到抽屉，不再渲染 pill（传 nil 与 HomeContent 约定一致）。
            // ⚠️ Kotlin 默认参数不导出到 ObjC，所有参数必须显式传入。
            onAssistantClick: nil,
            onOpenDrawer: onOpenDrawer
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// 把 Kotlin 侧 AI 助手 VC 包进 SwiftUI（模态全屏）。
struct AssistantComposeView: UIViewControllerRepresentable {
    let onBack: () -> Void
    let onMessage: (String) -> Void
    let onOpenScreen: (String, KotlinLong?) -> Void
    let onSearchInApp: (String) -> Void
    /// 点播（play_music / play_playlist / play_album）：可播放队列 + 展示标题。
    let onPlaySongs: ([PlayableSong], String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxAssistantScreen.shared.makeAssistantViewController(
            onOpenScreen: onOpenScreen,
            onSearchInApp: onSearchInApp,
            onPlaySongs: onPlaySongs,
            onMessage: onMessage,
            onBack: onBack
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - 全部歌单 / 全部专辑页

/// 全部歌单页：共享层 PlaylistListContent（feature:playlist commonMain）渲染。
struct PlaylistListView: View {
    var onPlaylistClick: (Int64, String) -> Void = { _, _ in }
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        PlaylistListComposeView(
            onPlaylistClick: { id, title in onPlaylistClick(id.int64Value, title) },
            onBack: { dismiss() }
        )
        .ignoresSafeArea(edges: [.top, .bottom])
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
    }
}

struct PlaylistListComposeView: UIViewControllerRepresentable {
    let onPlaylistClick: (KotlinLong, String) -> Void
    let onBack: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxPlaylistListScreen.shared.makePlaylistListViewController(
            onPlaylistClick: onPlaylistClick,
            onBack: onBack
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// 全部专辑页：共享层 AlbumListContent（feature:playlist commonMain）渲染。
struct AlbumListView: View {
    var onAlbumClick: (Int64) -> Void = { _ in }
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        AlbumListComposeView(
            onAlbumClick: { id in onAlbumClick(id.int64Value) },
            onBack: { dismiss() }
        )
        .ignoresSafeArea(edges: [.top, .bottom])
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
    }
}

struct AlbumListComposeView: UIViewControllerRepresentable {
    let onAlbumClick: (KotlinLong) -> Void
    let onBack: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxAlbumListScreen.shared.makeAlbumListViewController(
            onAlbumClick: onAlbumClick,
            onBack: onBack
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
