import SwiftUI
import UIKit
import FoxShared

/// 全屏播放器展示协调器（单例）：迷你条点击 → 置 true；ContentView 监听后弹全屏。
final class PlayerUICoordinator: ObservableObject {
    static let shared = PlayerUICoordinator()
    @Published var isFullPlayerPresented = false

    /// 音乐评论通知定位：非 nil 时，播放器弹出后自动打开评论面板并定位楼中楼。
    struct MusicCommentAnchor: Equatable {
        let commentId: Int64
        let rootCommentId: Int64
    }
    @Published var commentAnchor: MusicCommentAnchor?

    /// 通知点击音乐评论：拉歌曲详情 → 起播 → 打开全屏播放器（PlayerView 观察 anchor 自动弹评论面板）。
    func openMusicComment(musicId: Int64, commentId: Int64, rootCommentId: Int64) {
        FoxIosBridge.shared.fetchPlayableSong(
            musicId: musicId,
            onResult: { song in
                DispatchQueue.main.async {
                    PlayerModel.shared.play(song)
                    self.commentAnchor = MusicCommentAnchor(commentId: commentId, rootCommentId: rootCommentId)
                    self.isFullPlayerPresented = true
                }
            },
            onError: { message in
                DispatchQueue.main.async { ToastCenter.shared.show(message) }
            }
        )
    }

    /// 播放列表面板「来源」卡片点击：请求 Tab 路由跳到对应详情页，
    /// 再关掉全屏播放器（队列 sheet 已由其自身 dismiss）。
    /// 延后关播放器：sheet dismiss 动画进行中立刻收 fullScreenCover 会有两层竞态。
    func openSource(_ source: PlaybackSource) {
        AppTabRouter.shared.requestPlayerSource(
            kind: source.kind,
            id: source.id,
            title: source.title
        )
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            self.isFullPlayerPresented = false
        }
    }
}

/// 五个底部 Tab（与 Android `AppDestinations` 一一对应）。
enum AppTab: Hashable {
    case home, discover, community, messages, profile
}

/// 跨 Tab 跳转总线。
///
/// 存在理由：应用内通知横幅（`InAppNotificationOverlay`）挂在根视图上，
/// 它知道"该跳到哪"，但**够不着**消息 Tab 内部的 NavigationStack。
/// 这里做一层最小总线：横幅写 `selectedTab` + `pendingMessagesLink`，
/// ContentView 切 Tab、MessagesTabView 消费并清空。
///
/// 在此之前横幅点击只做「收起 + 打日志」（而且注释还写着"消息页还是占位"，
/// 早已过时）—— 用户点了通知什么都没发生。
final class AppTabRouter: ObservableObject {
    static let shared = AppTabRouter()

    /// 消息 Tab 内的二级目标（够用的最小集合：聊天详情 / 通知分类）。
    enum MessagesLink: Equatable {
        case chat(peerUserId: Int64, nickname: String?)
        case category(String)
    }

    /// 播放列表「来源」卡片的跳转目标（对齐 Android PlayerNavGraph 的来源导航）。
    enum PlayerSourceLink: Equatable {
        case post(id: Int64)
        case playlist(id: Int64, title: String)
        case rank(id: Int64, title: String)
        case album(id: Int64)
    }

    @Published var selectedTab: AppTab = .home
    /// 待消费的消息页面跳转。消息页（根级模态）消费后置回 nil。
    @Published var pendingMessagesLink: MessagesLink?
    /// 待消费的播放来源跳转。由当前选中（或被切到）的可承接 Tab 消费后置回 nil。
    @Published var pendingPlayerSourceLink: PlayerSourceLink?

    /// 左侧抽屉（左上角三横「更多」）开合。
    @Published var isDrawerOpen = false
    /// 根级模态：消息二级页 / 音流页 / 抽屉里的工具页。
    /// 用**单一** `.fullScreenCover(item:)` 承载，避免多个 fullScreenCover 互相抢占。
    @Published var rootModal: RootModal?
    /// AI 音乐助手模态（由 HomeTabView 呈现；入口从搜索框移到抽屉）。
    @Published var isAssistantPresented = false

    private init() {}

    /// 打开左侧抽屉。
    func openDrawer() { isDrawerOpen = true }

    /// 消息不再是底部 Tab，改为根级模态页；入口在抽屉 / 应用内通知横幅。
    func openMessages(_ link: MessagesLink?) {
        pendingMessagesLink = link
        isDrawerOpen = false
        rootModal = .messages
    }

    /// 播放列表来源点击：选一个能承接该来源的 Tab（优先当前 Tab），再挂起跳转请求。
    /// 注意必须先切 Tab 再置 pending：已存活 Tab 的 onChange 会按 selectedTab 门控消费，
    /// 新出现的 Tab 在 .task 里兜底消费。
    func requestPlayerSource(kind: PlaybackSource.Kind, id: Int64?, title: String) {
        guard let id, id > 0 else { return }
        let link: PlayerSourceLink
        switch kind {
        case .post: link = .post(id: id)
        case .playlist: link = .playlist(id: id, title: title)
        case .rank: link = .rank(id: id, title: title)
        case .album: link = .album(id: id)
        case .other: return
        }
        if !Self.canOpen(link, in: selectedTab) {
            selectedTab = Self.fallbackTab(for: link)
        }
        pendingPlayerSourceLink = link
    }

    /// 各 Tab 在 .task / onChange 里调用：只有当前选中且该 Tab 能承接时才消费。
    func consumePlayerSource(in tab: AppTab, _ handle: (PlayerSourceLink) -> Void) {
        guard let link = pendingPlayerSourceLink,
              selectedTab == tab,
              Self.canOpen(link, in: tab) else { return }
        pendingPlayerSourceLink = nil
        handle(link)
    }

    /// 各 Tab 已有的详情路由能力（与各 Tab 的 Route 枚举一一对应）。
    static func canOpen(_ link: PlayerSourceLink, in tab: AppTab) -> Bool {
        switch link {
        case .post:
            return tab == .discover || tab == .community || tab == .profile
        case .playlist:
            return tab == .home || tab == .community || tab == .profile
        case .rank:
            return tab == .home || tab == .discover
        case .album:
            return true
        }
    }

    static func fallbackTab(for link: PlayerSourceLink) -> AppTab {
        switch link {
        case .post: return .community
        case .playlist, .album: return .home
        case .rank: return .discover
        }
    }
}

/// 应用根视图：SwiftUI TabView 外壳 —— 与 Android 端 AppDestinations 对齐的 5 个 Tab：
/// 首页 / 发现 / 社区 / 消息 / 我的。
///
/// 注意：迷你播放条不能挂在 TabView 上（safeAreaInset(edge: .bottom) 挂在
/// TabView 上会盖住 Tab 栏，Tab 按钮全部点不到）——必须挂在每个 Tab 内容
/// 内部（withMiniPlayer），让迷你条渲染在 Tab 栏之上、与 Tab 栏共存。
///
/// 「我的」和「消息」两个 Tab 例外：它们的迷你条由各自的 TabView 自己挂 ——
/// 只有那里才知道导航栈里当前是哪个二级页，而二级页不展示迷你条
/// （我的-设置页、消息-聊天/好友/资料/通知页）。这里再挂一次会重复。
struct ContentView: View {
    @StateObject private var playerUI = PlayerUICoordinator.shared
    /// Tab 选中态由 `AppTabRouter` 托管：应用内通知横幅要能跨 Tab 跳转。
    @StateObject private var router = AppTabRouter.shared

    var body: some View {
        ZStack {
            TabView(selection: $router.selectedTab) {
                HomeTabView()
                    .withMiniPlayer()
                    .tabItem { Label("首页", systemImage: "house.fill") }
                    .tag(AppTab.home)
                DiscoverTabView()
                    .withMiniPlayer()
                    .tabItem { Label("发现", systemImage: "safari.fill") }
                    .tag(AppTab.discover)
                CommunityTabView()
                    // 迷你条挂在 CommunityTabView 内部并按导航 path 门控：
                    // 二级页（帖子详情/个人主页）收起，避免浮层盖住评论栏/关系条。
                    .tabItem { Label("社区", systemImage: "bubble.left.and.bubble.right") }
                    .tag(AppTab.community)
                ProfileTabView()
                    .tabItem { Label("我的", systemImage: "person.crop.circle") }
                    .tag(AppTab.profile)
            }
            // 窗口/Tab 未覆盖区用渐变顶色，与 Compose foxTabPageBackground 对齐。
            // 背景视图必须观察 AppearanceStore —— 否则切主题后这里不会重组（FoxTheme 是静态快照）。
            .background(FoxThemedBackgroundView())
            // 监听系统外观运行期变化（深色/浅色切换），跟随系统模式下重新推送宿主补色。
            .overlay(SystemAppearanceObserver().frame(width: 0, height: 0).allowsHitTesting(false))
            // 中央「音流模式」圆钮：圆形突出于底部 Tab 栏之上（消息 Tab 已移入抽屉）。
            .overlay(alignment: .bottom) {
                if !router.isDrawerOpen {
                    StreamEntryButton()
                }
            }
            .fullScreenCover(isPresented: $playerUI.isFullPlayerPresented) {
                PlayerView()
            }

            // 仿网易云左侧抽屉（左上角三横「更多」入口）。
            MineDrawerHost()
        }
        // 根级模态：消息二级页 / 音流页 / 抽屉里的工具页（单一 cover 承载）。
        .fullScreenCover(item: $router.rootModal) { modal in
            rootModalView(modal)
        }
        .onAppear {
            // UI 测试专用登录态重置：模拟器上残留的登录会让「游客态」用例
            // （test19/20）不稳定。必须在运行时就绪后执行（App.init 里做会崩，
            // 见 FoxMusicApp.init 的注释）。
            if ProcessInfo.processInfo.arguments.contains("-UITestResetAuth") {
                AuthStore.shared.logout()
            }
            // UI 测试专用自动登录：依赖登录态的用例（test25 好友页等）不再依赖
            // 「上次手动登过 root」这种残留状态。注册失败（账号已存在）时兜底走登录。
            // 与 ResetAuth 组合使用（Reset 必须先执行，见上）。
            if ProcessInfo.processInfo.arguments.contains("-UITestAutoLogin"),
               !AuthStore.shared.isLoggedIn {
                FoxPlayerApi.shared.autoLoginForUiTest(
                    username: "foxuitest",
                    password: "123456",
                    email: "foxuitest@test.local",
                    onResult: { _ in
                        AuthStore.shared.persistFromKotlin()
                    },
                    onError: { message in
                        NSLog("FoxUITest autoLogin failed: \(message)")
                    }
                )
            }
            FoxTheme.applyToWindowBackground()
        }
        .withInAppNotificationBanner()
        .withToast()
    }

    /// 根级模态内容：抽屉入口 + 消息二级页 + 音流页。
    @ViewBuilder
    private func rootModalView(_ modal: RootModal) -> some View {
        switch modal {
        case .messages:
            MessagesTabView(onClose: { router.rootModal = nil })
        case .stream:
            StreamView()
        case .editProfile:
            NavigationStack { EditProfileView() }
        case .downloadManager:
            NavigationStack { DownloadManagerView() }
        case .theme:
            NavigationStack { ThemeSettingsView() }
        case .manageLibrary:
            NavigationStack { ManageLibraryView() }
        case .settings:
            NavigationStack { SettingsView(onLogin: { router.rootModal = .login }) }
        case .login:
            LoginComposeView(
                onSuccess: {
                    AuthStore.shared.persistFromKotlin()
                    router.rootModal = nil
                },
                onBack: { router.rootModal = nil }
            )
            .ignoresSafeArea()
        }
    }
}

/// 迷你播放条修饰器：挂在每个 Tab 的根视图上（而非 TabView），
/// 这样迷你条位于 Tab 栏上方，两者互不遮挡。
struct MiniPlayerModifier: ViewModifier {
    @ObservedObject private var player = PlayerModel.shared

    /// 当前页面是否要展示迷你条。false 时连安全区占位一起去掉，
    /// 页面内容可以一直铺到 Tab 栏（设置页等工具页用）。
    var isEnabled: Bool = true

    /// 点击迷你条打开全屏播放器的行为。nil 时走默认（全局协调器）。
    /// 助手模态（fullScreenCover）内需先关模态再打开，由调用方注入。
    var onOpenFullPlayer: (() -> Void)? = nil

    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .bottom) {
            if isEnabled, let current = player.current {
                MiniPlayerBar(
                    current: current,
                    onOpenFullPlayer: onOpenFullPlayer
                        ?? { PlayerUICoordinator.shared.isFullPlayerPresented = true }
                )
            }
        }
    }
}

extension View {
    func withMiniPlayer(
        isEnabled: Bool = true,
        onOpenFullPlayer: (() -> Void)? = nil
    ) -> some View {
        modifier(MiniPlayerModifier(isEnabled: isEnabled, onOpenFullPlayer: onOpenFullPlayer))
    }
}

/// 全局迷你播放条（跨 Tab 常驻）。
/// 与 Android `core:ui` 的 MiniPlayer 对齐：封面缩略图 + 标题歌手 + 播放暂停（缓冲转圈）
/// + 下一首 + 底部线性进度条。
struct MiniPlayerBar: View {
    @ObservedObject private var player = PlayerModel.shared
    let current: PlayableSong

    /// 点击整条 → 打开全屏播放器。默认走全局协调器；助手模态内由调用方注入
    /// 「先关模态再打开」，避免 fullScreenCover 叠加被系统排队而失效。
    var onOpenFullPlayer: () -> Void = { PlayerUICoordinator.shared.isFullPlayerPresented = true }

    /// 迷你条高度（不含底部安全区）。
    ///
    /// 与 `CommunityTabView` 给 Compose 帖子详情页预留的底部内边距**共用同一个常量**：
    /// iOS 的迷你条是常驻浮层、画在页面之外，不预留就会盖住评论输入栏。
    /// 改迷你条版式（下面的 padding / 封面尺寸 / 进度条高度）时必须同步这里。
    static let contentHeight: CGFloat = 8 + 44 + 6 + 2

    private var progress: Double {
        guard player.duration > 0 else { return 0 }
        return min(max(player.currentTime / player.duration, 0), 1)
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                // 整条可点区域（封面 + 标题歌手 + 右侧空白）统一接收点击。
                // 只把 onTapGesture 挂在文字 VStack 上时，可点区域只剩文字宽度，
                // 点封面或空白处都没反应（用户反馈："只有点击图像和文字才有反应"）。
                HStack(spacing: 12) {
                    AsyncImage(url: current.coverUrl.flatMap(URL.init(string:))) { phase in
                        if let image = phase.image {
                            image.resizable().scaledToFill()
                        } else {
                            ZStack {
                                Color.secondary.opacity(0.2)
                                Image(systemName: "music.note")
                                    .font(.system(size: 18))
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))

                    VStack(alignment: .leading, spacing: 2) {
                        Text(current.title).font(.system(size: 15, weight: .medium)).lineLimit(1)
                            .accessibilityIdentifier("miniPlayer.title")
                        // 与 Android MiniPlayer 对齐：第二行 = 当前歌词（随播放逐行刷新），
                        // 没有歌词时回退歌手名。currentTime 是 @Published，歌词行会跟着刷。
                        Text(player.currentLyricText ?? current.artist)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 0)
                }
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
                .onTapGesture { onOpenFullPlayer() }
                // 必须 combine：容器不合并子元素时，identifier 会挂到子树多个元素上，
                // XCUITest 查 miniPlayer.bar 会因"匹配到多个"而失败
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isButton)
                .accessibilityIdentifier("miniPlayer.bar")

                Button {
                    player.togglePlayPause()
                } label: {
                    ZStack {
                        if player.isLoading {
                            ProgressView().scaleEffect(0.85)
                        } else {
                            Image(systemName: player.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                                .font(.system(size: 32))
                        }
                    }
                    .frame(width: 40, height: 40)
                    .contentShape(Rectangle())
                }
                .accessibilityIdentifier("miniPlayer.playPause")
                .accessibilityLabel(player.isPlaying ? "暂停" : "播放")

                Button {
                    player.next()
                } label: {
                    Image(systemName: "forward.fill")
                        .font(.system(size: 19))
                        .frame(width: 38, height: 40)
                        .contentShape(Rectangle())
                }
                .accessibilityIdentifier("miniPlayer.next")
                .accessibilityLabel("下一首")
            }
            .padding(.horizontal, 12)
            .padding(.top, 8)
            .padding(.bottom, 6)

            // 线性进度：与 Android MiniPlayer 底部的 FoxLinearProgress 对应
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Rectangle().fill(Color.secondary.opacity(0.22))
                    Rectangle().fill(Color.accentColor)
                        .frame(width: geo.size.width * progress)
                }
            }
            .frame(height: 2)
            .accessibilityIdentifier("miniPlayer.progress")
        }
        .background(.bar)
    }
}

// MARK: - 发现 Tab

/// 发现页路由：榜单详情 / 艺人详情 / 搜索 / 专辑详情 / 新歌速递落地页 / 帖子详情。
enum DiscoverRoute: Hashable {
    case rankDetail(id: Int64, title: String)
    case artistDetail(id: Int64)
    case albumDetail(id: Int64)
    /// 标签深链时带 tag（进搜索页即落帖子 Tab 出结果）
    case search(tag: String?)
    /// 新歌速递「更多」→ 虚拟歌单详情页（DetailType.NEW_MUSIC，sort=latest）
    case newMusicList
    /// 热门歌手「更多」→ 热门歌手网格（含「全部歌手」入口）
    case hotArtistList
    /// 全部歌手（字母分组索引）
    case allArtistList
    /// 搜索页帖子 Tab 点结果 → 帖子详情
    case postDetail(id: Int64)
    /// 帖子详情 → 编辑自己的帖子
    case editPost(id: Int64)
}

/// 发现 Tab：共享层 DiscoverContent（feature:discover commonMain）渲染 ——
/// 与 Android 发现页同一份 UI（热搜词+热门榜单+新歌速递+热门歌手）。
struct DiscoverTabView: View {
    @State private var path: [DiscoverRoute] = []
    @State private var reportTarget: ReportTarget?
    @State private var moreSong: Music?
    @StateObject private var router = AppTabRouter.shared

    /// 消费播放列表「来源」卡片跳转：发现页可承接榜单/专辑/帖子。
    private func consumePlayerSourceLink() {
        router.consumePlayerSource(in: .discover) { link in
            switch link {
            case .rank(let id, let title):
                path.append(.rankDetail(id: id, title: title))
            case .album(let id):
                path.append(.albumDetail(id: id))
            case .post(let id):
                path.append(.postDetail(id: id))
            case .playlist:
                break
            }
        }
    }

    var body: some View {
        NavigationStack(path: $path) {
            DiscoverComposeView(
                onMusicClickFromList: { songs, index in
                    PlayerModel.shared.play(
                        queue: songs,
                        startIndex: Int(index.int32Value),
                        source: PlaybackSource(title: "新歌速递", coverUrl: nil, typeLabel: "发现页")
                    )
                },
                onRankClick: { id, title in
                    path.append(.rankDetail(id: id, title: title))
                },
                onArtistClick: { id in
                    path.append(.artistDetail(id: id))
                },
                onSearchClick: {
                    path.append(.search(tag: nil))
                },
                onNewMusicMore: {
                    path.append(.newMusicList)
                },
                onHotArtistMore: {
                    path.append(.hotArtistList)
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
            .navigationDestination(for: DiscoverRoute.self) { route in
                switch route {
                case .rankDetail(let id, let title):
                    PlaylistDetailView(playlistId: id, isRank: true, title: title)
                case .artistDetail(let id):
                    ArtistDetailView(artistId: id, onAlbumClick: { path.append(.albumDetail(id: $0)) })
                case .albumDetail(let id):
                    PlaylistDetailView(playlistId: id, isRank: false, title: "专辑详情", detailType: "album")
                case .search(let tag):
                    SearchView(
                        onArtistClick: { id in path.append(.artistDetail(id: id)) },
                        onAlbumClick: { id in path.append(.albumDetail(id: id)) },
                        onPostClick: { id in path.append(.postDetail(id: id)) },
                        initialTag: tag
                    )
                case .newMusicList:
                    // 与 Android onNewMusicMore 一致：虚拟 id = Long.MAX_VALUE - 1
                    PlaylistDetailView(playlistId: Int64.max - 1, isRank: false, title: "新歌速递", detailType: "new_music")
                case .hotArtistList:
                    // Compose VC 自带顶栏（pageOwnsTopBar=true）：必须隐藏 SwiftUI 导航栏，
                    // 否则出现双返回栏；同时隐藏底部 TabBar（与 PlaylistDetailView 同处理）。
                    HotArtistListComposeView(
                        onArtistClick: { id in path.append(.artistDetail(id: id)) },
                        onAllArtistsClick: { path.append(.allArtistList) },
                        onBack: { path.removeLast() }
                    )
                    .ignoresSafeArea(edges: [.top, .bottom])
                    .navigationBarHidden(true)
                    .toolbar(.hidden, for: .tabBar)
                    .swipeBackEnabled()
                case .allArtistList:
                    ArtistListComposeView(
                        onArtistClick: { id in path.append(.artistDetail(id: id)) },
                        onBack: { path.removeLast() }
                    )
                    .ignoresSafeArea(edges: [.top, .bottom])
                    .navigationBarHidden(true)
                    .toolbar(.hidden, for: .tabBar)
                    .swipeBackEnabled()
                case .postDetail(let id):
                    PostDetailView(
                        postId: id,
                        onReportPost: { reportId, title in
                            reportTarget = ReportTarget(type: "post", targetId: reportId, name: title)
                        },
                        onTagClick: { tag in
                            path.append(.search(tag: tag))
                        },
                        onEditPost: { editId in
                            FoxCreatePostStore.shared.clear()
                            path.append(.editPost(id: editId))
                        }
                    )
                case .editPost(let id):
                    CreatePostView(postId: id, onBack: { path.removeLast() })
                }
            }
        }
        // 播放列表来源跳转：已在发现页时即时消费；从别的 Tab 切过来时 .task 兜底。
        .task { consumePlayerSourceLink() }
        .onChange(of: router.pendingPlayerSourceLink) { _ in
            consumePlayerSourceLink()
        }
        .bottomSheet(item: $reportTarget) { target in
            ReportSheet(targetType: target.type, targetId: target.targetId, targetName: target.name) {}
        }
    }
}

/// 把 Kotlin 侧发现页 VC 包进 SwiftUI。
struct DiscoverComposeView: UIViewControllerRepresentable {
    let onMusicClickFromList: ([PlayableSong], KotlinInt) -> Void
    let onRankClick: (Int64, String) -> Void
    let onArtistClick: (Int64) -> Void
    let onSearchClick: () -> Void
    let onNewMusicMore: () -> Void
    let onHotArtistMore: () -> Void
    let onMusicMoreClick: (Music) -> Void
    /// 左上角三横「更多」→ 打开左侧抽屉。
    let onOpenDrawer: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxDiscoverScreen.shared.makeDiscoverViewController(
            onMusicClickFromList: onMusicClickFromList,
            onRankClick: { id, title in
                onRankClick(id.int64Value, title)
            },
            onArtistClick: { id in
                onArtistClick(id.int64Value)
            },
            onSearchClick: onSearchClick,
            onNewMusicMore: onNewMusicMore,
            onHotArtistMore: onHotArtistMore,
            onMusicMoreClick: onMusicMoreClick,
            onOpenDrawer: onOpenDrawer
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// 艺人详情页：共享层 ArtistDetailContent（feature:discover commonMain）渲染 ——
/// 与 Android 歌手详情页同一份 UI（头像头部 + 热门歌曲 + 专辑）。
struct ArtistDetailView: View {
    let artistId: Int64
    /// 点歌手页里的专辑 → 由宿主推专辑详情页
    var onAlbumClick: (Int64) -> Void = { _ in }
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ArtistDetailComposeView(
            artistId: artistId,
            onMusicClick: { song in
                if let song { PlayerModel.shared.play(song) }
            },
            onAlbumClick: onAlbumClick,
            onBack: { dismiss() }
        )
            .ignoresSafeArea(edges: [.top, .bottom])
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

/// 热门歌手页包装：共享层 HotArtistListContent（4 列圆形头像网格 + 全部歌手入口）。
struct HotArtistListComposeView: UIViewControllerRepresentable {
    let onArtistClick: (Int64) -> Void
    let onAllArtistsClick: () -> Void
    let onBack: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxHotArtistListScreen.shared.makeHotArtistListViewController(
            onArtistClick: { onArtistClick($0.int64Value) },
            onAllArtistsClick: onAllArtistsClick,
            onBack: onBack
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// 全部歌手页包装：共享层 ArtistListContent（字母分组 + 索引条）。
struct ArtistListComposeView: UIViewControllerRepresentable {
    let onArtistClick: (Int64) -> Void
    let onBack: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxArtistListScreen.shared.makeArtistListViewController(
            onArtistClick: { onArtistClick($0.int64Value) },
            onBack: onBack
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// 搜索页：共享层 SearchContent（feature:search commonMain）渲染 ——
/// 与 Android 搜索页同一份 UI（搜索框+单曲/歌手/专辑/帖子 Tab+平台切换+热词面板）。
struct SearchView: View {
    var onArtistClick: (Int64) -> Void = { _ in }
    var onAlbumClick: (Int64) -> Void = { _ in }
    /// 帖子 Tab 点结果 → 由宿主推帖子详情页
    var onPostClick: (Int64) -> Void = { _ in }
    /// 帖子标签深链：非空时进入即「帖子」Tab 并按该标签出结果
    var initialTag: String? = nil
    @Environment(\.dismiss) private var dismiss
    @State private var moreSong: Music?

    var body: some View {
        SearchComposeView(
            initialTag: initialTag,
            onBack: { dismiss() },
            onMusicClick: { song in
                if let song { PlayerModel.shared.play(song) }
            },
            onArtistClick: onArtistClick,
            onAlbumClick: onAlbumClick,
            onPostClick: onPostClick,
            onMusicMoreClick: { music in
                moreSong = music
            }
        )
            .ignoresSafeArea(edges: [.top, .bottom])
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
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
    }
}

/// 把 Kotlin 侧搜索 VC 包进 SwiftUI。
struct SearchComposeView: UIViewControllerRepresentable {
    let initialTag: String?
    let onBack: () -> Void
    let onMusicClick: (PlayableSong?) -> Void
    let onArtistClick: (Int64) -> Void
    let onAlbumClick: (Int64) -> Void
    let onPostClick: (Int64) -> Void
    let onMusicMoreClick: (Music) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxSearchScreen.shared.makeSearchViewController(
            onBack: onBack,
            onMusicClick: onMusicClick,
            onArtistClick: { onArtistClick($0.int64Value) },
            onAlbumClick: { onAlbumClick($0.int64Value) },
            onPostClick: { onPostClick($0.int64Value) },
            onToast: { message in ToastCenter.shared.show(message) },
            onMusicMoreClick: onMusicMoreClick,
            initialTag: initialTag
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
    /// 列表详情的数据源类型：playlist / rank / album / favorite_music / play_history
    /// （与安卓 DetailType 同语义；专辑/收藏/历史共用 PlaylistDetailContent 承载）
    var detailType: String = "playlist"
    @Environment(\.dismiss) private var dismiss
    @State private var moreSong: Music?
    // 歌单管理（Kotlin 侧仅自己的歌单才会触发这两个回调）：编辑 / 删除
    @State private var showEdit = false
    @State private var showDeleteConfirm = false
    @State private var isDeleting = false

    /// 播放入口的来源标签（显示在播放列表"来自xx"）
    private var sourceLabel: String {
        switch detailType {
        case "rank": return "榜单"
        case "album": return "专辑"
        case "favorite_music": return "我的收藏"
        case "play_history": return "历史播放"
        default: return "歌单"
        }
    }

    /// 当前列表对应的可播放来源。仅真实歌单/专辑/榜单可跳转，
    /// 推荐/收藏/历史等虚拟列表为 other（播放列表不展示来源卡片，对齐 Android）。
    private var playbackSource: PlaybackSource {
        let kind: PlaybackSource.Kind
        switch detailType {
        case "album": kind = .album
        case "rank": kind = .rank
        case "playlist":
            // 榜单详情默认 detailType 仍是 "playlist"，靠 isRank 区分（见 DiscoverRoute.rankDetail）
            kind = isRank ? .rank : .playlist
        default: kind = .other
        }
        return PlaybackSource(
            kind: kind,
            id: playlistId > 0 ? playlistId : nil,
            title: title,
            coverUrl: nil,
            typeLabel: sourceLabel
        )
    }

    var body: some View {
        PlaylistDetailComposeView(
            playlistId: playlistId,
            isRank: isRank,
            detailType: detailType,
            onMusicClickFromList: { songs, index in
                PlayerModel.shared.play(
                    queue: songs,
                    startIndex: Int(index.int32Value),
                    source: playbackSource
                )
            },
            onPlayAll: { songs in
                // 播放全部：整个列表入队，从第一首开始（带来源，播放列表里能显示"来自xx"）
                PlayerModel.shared.play(
                    queue: songs,
                    startIndex: 0,
                    source: playbackSource
                )
            },
            onBack: { dismiss() },
            onMusicMoreClick: { music in
                moreSong = music
            },
            onEditPlaylist: { _ in showEdit = true },
            onDeletePlaylist: { showDeleteConfirm = true }
        )
            .ignoresSafeArea(edges: [.top, .bottom])
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
        // 编辑歌单（管理菜单进入；PlaylistEditView 在 ProfileTabView.swift）
        .fullScreenCover(isPresented: $showEdit) {
            NavigationStack {
                PlaylistEditView(playlistId: playlistId)
            }
        }
        // 删除歌单确认（与 Android DeletePlaylistConfirmDialog 同语义）
        .confirmationDialog(
            "删除歌单",
            isPresented: $showDeleteConfirm,
            titleVisibility: .visible
        ) {
            Button(isDeleting ? "删除中…" : "删除", role: .destructive) {
                deletePlaylist()
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("确定要删除「\(title)」吗？此操作不可恢复。")
        }
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
    }

    /// 删除歌单：DELETE api/playlists/{id}，成功后刷新「我的」页并退出详情页。
    private func deletePlaylist() {
        guard !isDeleting else { return }
        isDeleting = true
        FoxMusicApi().deletePlaylist(playlistId: playlistId) { error in
            DispatchQueue.main.async {
                isDeleting = false
                if let error {
                    ToastCenter.shared.show(error.localizedDescription)
                } else {
                    ToastCenter.shared.show("歌单已删除")
                    FoxPlaylistRefresh.shared.refreshProfile()
                    dismiss()
                }
            }
        }
    }
}

/// 把 Kotlin 侧歌单详情 VC 包进 SwiftUI。
struct PlaylistDetailComposeView: UIViewControllerRepresentable {
    let playlistId: Int64
    let isRank: Bool
    let detailType: String
    let onMusicClickFromList: ([PlayableSong], KotlinInt) -> Void
    let onPlayAll: ([PlayableSong]) -> Void
    let onBack: () -> Void
    let onMusicMoreClick: (Music) -> Void
    // 歌单管理（仅 type=playlist 且自己创建时，Kotlin 侧才会触发）
    var onEditPlaylist: (KotlinLong) -> Void = { _ in }
    var onDeletePlaylist: () -> Void = {}

    func makeUIViewController(context: Context) -> UIViewController {
        FoxPlaylistDetailScreen.shared.makePlaylistDetailViewController(
            playlistId: playlistId,
            useRankEndpoint: isRank,
            detailType: detailType,
            onMusicClickFromList: onMusicClickFromList,
            onPlayAll: onPlayAll,
            onBack: onBack,
            onMusicMoreClick: onMusicMoreClick,
            onEditPlaylist: onEditPlaylist,
            onDeletePlaylist: onDeletePlaylist
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

/// 与 FoxMusicTheme 默认「黑胶夜场」对齐：窗口补渐变顶色（primary 14% 叠在 background 上）。
enum FoxTheme {
    static var pageBackground: Color {
        Color(argb: UInt32(truncatingIfNeeded: FoxIosTheme.shared.pageBackgroundArgb()))
    }

    static var tabGradientTop: Color {
        Color(argb: UInt32(truncatingIfNeeded: FoxIosTheme.shared.tabGradientTopArgb()))
    }

    static func applyToWindowBackground() {
        let color = UIColor(tabGradientTop)
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

/// 主题背景视图：观察 AppearanceStore，外观/主题色变化时即时重组取色。
private struct FoxThemedBackgroundView: View {
    @ObservedObject private var appearance = AppearanceStore.shared

    var body: some View {
        FoxTheme.tabGradientTop.ignoresSafeArea()
    }
}

/// 系统外观变化监听：环境 colorScheme 变化（系统深浅色切换）时通知 AppearanceStore。
private struct SystemAppearanceObserver: View {
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Color.clear
            .onAppear { AppearanceStore.shared.syncSystemDark() }
            .onChange(of: colorScheme) { _ in AppearanceStore.shared.syncSystemDark() }
    }
}

extension View {
    /// 给被 SwiftUI 按安全区缩进的 Compose 页面补一层**全屏底色**（含状态栏那条）。
    ///
    /// 注意：不能只挂在 TabView 上 —— TabView 自己会画一层不透明的系统底色，
    /// 把 `.background()` 盖掉，所以必须画在每个 Tab 内容（即 Compose 页面）这一层。
    /// 修饰器内部观察 AppearanceStore，切换主题/深色模式后背景即时跟随。
    func themedPageBackground() -> some View {
        modifier(ThemedPageBackgroundModifier())
    }
}

private struct ThemedPageBackgroundModifier: ViewModifier {
    @ObservedObject private var appearance = AppearanceStore.shared

    func body(content: Content) -> some View {
        content.background(FoxTheme.tabGradientTop.ignoresSafeArea())
    }
}
