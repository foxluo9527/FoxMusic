import SwiftUI
import PhotosUI
import FoxShared

enum ProfileRoute: Hashable {
    case playlistDetail(id: Int64, title: String)
    case artistDetail(id: Int64)
    case albumDetail(id: Int64)
    /// favorite_music（我的收藏）/ play_history（历史播放）——用列表详情页承载
    case collectionDetail(type: String)
    /// 收藏分区「更多」→ 收藏详情页（playlists / albums / artists）。
    /// 复用共享层 FavoriteCollectionDetailContent（返回栏 + 标题 + 收藏列表项样式）。
    case favoriteDetail(type: String)
    case settings
    /// 歌单编辑页（profile 页歌单 ⋮ → 编辑歌单）
    case playlistEdit(id: Int64)
    /// 下载管理（profile 页「下载管理」行 → Swift 原生 DownloadManagerView）
    case downloadManager
    /// 第三方歌单导入选择页（创建歌单 → 导入第三方歌单）
    case importPlaylist(link: String)
    /// 个人主页（「我的」页头部卡片 → 自己主页；帖子详情作者 → 他人主页）
    case userHome(id: Int64)
    /// 个人主页「动态」Tab → 帖子详情
    case postDetail(id: Int64)
    /// 帖子标签深链 → 搜索页（帖子 Tab + 标签）
    case search(tag: String?)
    /// 帖子详情 → 编辑自己的帖子
    case editPost(id: Int64)
    /// 他人主页关系条 → 聊天 / 加好友
    case chat(userId: Int64, peerNickname: String?, peerAvatar: String?)
    case addFriend(userId: Int64, nickname: String)
    /// 自己主页更多菜单 → 编辑资料 / 隐私设置
    case editProfile
    case privacySettings

    /// 该二级页是否展示迷你播放条。
    ///
    /// 设置页是工具型页面（与 Android 侧一致：`SETTINGS_ROUTE` 是独立路由、外面
    /// 没有 MainScreenWithBottomBar，所以没有 MiniPlayer）。歌单/艺人详情保留迷你条，
    /// 否则这两页隐藏了 Tab 栏后就完全没有播放入口了。
    /// 个人主页/帖子详情/聊天收起：迷你浮条会盖住关系条与评论输入栏。
    var showsMiniPlayer: Bool {
        switch self {
        case .settings, .playlistEdit, .importPlaylist, .downloadManager,
             .userHome, .postDetail, .editPost, .chat, .addFriend, .editProfile, .privacySettings:
            return false
        default:
            return true
        }
    }
}

struct ProfileTabView: View {
    @ObservedObject private var auth = AuthStore.shared
    @State private var path: [ProfileRoute] = []
    @State private var showLogin = false
    @StateObject private var router = AppTabRouter.shared
    // 创建歌单：入口弹窗（导入第三方 / 自定义）+ 二级输入弹窗
    @State private var showCreateMenu = false
    @State private var createInput: CreatePlaylistInput?
    /// 帖子举报目标（个人主页动态 → 帖子详情 → 举报）。
    @State private var reportTarget: ReportTarget?

    /// 迷你条可见性由导航路径推导（栈顶页面说了算），而不是用全局开关：
    /// 全局开关一旦 onAppear/onDisappear 漏配对，就会把迷你条永久藏掉。
    private var showsMiniPlayer: Bool {
        path.last?.showsMiniPlayer ?? true
    }

    /// 消费播放列表「来源」卡片跳转：我的 Tab 可承接帖子/专辑/歌单（榜单回发现 Tab）。
    private func consumePlayerSourceLink() {
        router.consumePlayerSource(in: .profile) { link in
            switch link {
            case .post(let id):
                path.append(.postDetail(id: id))
            case .album(let id):
                path.append(.albumDetail(id: id))
            case .playlist(let id, let title):
                path.append(.playlistDetail(id: id, title: title))
            case .rank:
                break
            }
        }
    }

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
                onAlbumClick: { id in
                    path.append(.albumDetail(id: id.int64Value))
                },
                onFavoriteTracksClick: {
                    path.append(.collectionDetail(type: "favorite_music"))
                },
                onPlayHistoryClick: {
                    path.append(.collectionDetail(type: "play_history"))
                },
                onOpenDrawer: { router.openDrawer() },
                onMoreFavoritePlaylists: {
                    path.append(.favoriteDetail(type: "playlists"))
                },
                onMoreFavoriteAlbums: {
                    path.append(.favoriteDetail(type: "albums"))
                },
                onMoreFavoriteArtists: {
                    path.append(.favoriteDetail(type: "artists"))
                },
                onPostDetail: { id in
                    path.append(.postDetail(id: id))
                },
                onSharePost: { id, text in
                    ShareActionPresenter.present(
                        shareType: "post",
                        shareId: id,
                        displayTitle: "分享动态",
                        systemText: "FoxMusic 动态：\(text ?? "")\nID：\(id)"
                    )
                },
                onReportPost: { id, title in
                    reportTarget = ReportTarget(type: "post", targetId: id, name: title ?? "动态")
                },
                onEditPost: { id in
                    FoxCreatePostStore.shared.clear()
                    path.append(.editPost(id: id))
                },
                onDownloadManagerClick: {
                    path.append(.downloadManager)
                },
                onCreatePlaylist: { showCreateMenu = true },
                onEditPlaylist: { id in
                    path.append(.playlistEdit(id: id.int64Value))
                },
                onPlayAll: { songs in
                    PlayerModel.shared.play(
                        queue: songs,
                        startIndex: 0,
                        source: PlaybackSource(title: "我的歌单", coverUrl: nil, typeLabel: "歌单")
                    )
                },
                onMessage: { ToastCenter.shared.show($0) },
                onUserHome: {
                    // 头部卡片 → 自己的个人主页（对齐 Android 「我的」onUserHomeClick）。
                    if auth.currentUserId > 0 {
                        path.append(.userHome(id: auth.currentUserId))
                    }
                }
            )
            .ignoresSafeArea(edges: [.top, .bottom])
            .navigationBarHidden(true)
            .themedPageBackground()
            .bottomSheet(isPresented: $showCreateMenu) {
                CreatePlaylistMenuSheet(
                    onImport: { selectCreateMode(.importLink) },
                    onCustom: { selectCreateMode(.custom) }
                )
            }
            .bottomSheet(item: $createInput) { mode in
                CreatePlaylistInputSheet(
                    mode: mode,
                    onCancel: {
                        createInput = nil
                        BottomSheet.dismissTop()
                    },
                    onConfirm: { value in
                        // ⚠️ 必须显式关闭弹窗：`BottomSheet` 是命令式呈现
                        //（`BottomSheet.present` 在 onChange 里主动 present），
                        // 把 `createInput` 置 nil **不会**自动关掉已呈现的卡片 ——
                        // 只置 nil 的话二级输入框会一直挂在屏幕上，用户得手动下滑。
                        createInput = nil
                        BottomSheet.dismissTop()
                        switch mode {
                        case .custom:
                            createPlaylist(named: value)
                        case .importLink:
                            path.append(.importPlaylist(link: value))
                        }
                    }
                )
            }
            .navigationDestination(for: ProfileRoute.self) { route in
                switch route {
                case .playlistDetail(let id, let title):
                    PlaylistDetailView(playlistId: id, isRank: false, title: title)
                case .artistDetail(let id):
                    ArtistDetailView(artistId: id, onAlbumClick: { path.append(.albumDetail(id: $0)) })
                case .albumDetail(let id):
                    PlaylistDetailView(playlistId: id, isRank: false, title: "专辑详情", detailType: "album")
                case .search(let tag):
                    SearchView(
                        onArtistClick: { _ in },
                        onAlbumClick: { id in path.append(.albumDetail(id: id)) },
                        onPostClick: { id in path.append(.postDetail(id: id)) },
                        initialTag: tag
                    )
                case .collectionDetail(let type):
                    PlaylistDetailView(
                        playlistId: 0,
                        isRank: false,
                        title: type == "favorite_music" ? "我的收藏" : "历史播放",
                        detailType: type
                    )
                case .favoriteDetail(let type):
                    FavoriteCollectionDetailView(
                        type: type,
                        title: favoriteDetailTitle(type),
                        onBack: { path.removeLast() },
                        onPlaylistClick: { id, title in
                            path.append(.playlistDetail(id: id, title: title))
                        },
                        onAlbumClick: { id in
                            path.append(.albumDetail(id: id))
                        },
                        onArtistClick: { id in
                            path.append(.artistDetail(id: id))
                        }
                    )
                case .settings:
                    SettingsView(onLogin: { showLogin = true })
                case .downloadManager:
                    DownloadManagerView()
                case .playlistEdit(let id):
                    PlaylistEditView(playlistId: id)
                case .importPlaylist(let link):
                    ImportPlaylistSelectView(link: link) { pid, newTitle in
                        // 导入确认页 → 落到新建歌单的详情页（与 Android NavigateToPlaylist 一致）
                        if !path.isEmpty { path.removeLast() }
                        path.append(.playlistDetail(id: pid, title: newTitle))
                    }
                case .userHome(let id):
                    userHomeView(userId: id)
                case .postDetail(let id):
                    PostDetailView(
                        postId: id,
                        onReportPost: { reportId, title in
                            reportTarget = ReportTarget(type: "post", targetId: reportId, name: title)
                        },
                        onTagClick: { tag in
                            path.append(.search(tag: tag))
                        },
                        onUserHome: { userId in
                            path.append(.userHome(id: userId))
                        },
                        onEditPost: { editId in
                            FoxCreatePostStore.shared.clear()
                            path.append(.editPost(id: editId))
                        }
                    )
                case .editPost(let id):
                    CreatePostView(postId: id, onBack: { path.removeLast() })
                case .chat(let userId, let peerNickname, let peerAvatar):
                    ChatDetailView(
                        userId: userId,
                        peerNickname: peerNickname,
                        peerAvatar: peerAvatar,
                        onUserProfile: { uid in
                            path.append(.userHome(id: uid))
                        },
                        onSettings: { settingsUserId, _, _ in
                            path.append(.userHome(id: settingsUserId))
                        },
                        onOpenShare: { shareType, shareId, song in
                            switch shareType {
                            case "music":
                                if let song { PlayerModel.shared.play(song) }
                            case "playlist":
                                path.append(.playlistDetail(id: shareId, title: "歌单"))
                            case "album":
                                path.append(.albumDetail(id: shareId))
                            case "artist":
                                path.append(.artistDetail(id: shareId))
                            case "post":
                                path.append(.postDetail(id: shareId))
                            case "user":
                                path.append(.userHome(id: shareId))
                            default:
                                break
                            }
                        }
                    )
                case .addFriend(let userId, let nickname):
                    AddFriendRequestView(
                        userId: userId,
                        nickname: nickname,
                        onRequestSent: { path.removeLast() }
                    )
                case .editProfile:
                    EditProfileView()
                case .privacySettings:
                    PrivacySettingsView()
                }
            }
        }
        .bottomSheet(item: $reportTarget) { target in
            ReportSheet(targetType: target.type, targetId: target.targetId, targetName: target.name) {}
        }
        // 播放列表来源跳转：已在我的页时即时消费；从别的 Tab 切过来时 .task 兜底。
        .task { consumePlayerSourceLink() }
        .onChange(of: router.pendingPlayerSourceLink) { _ in
            consumePlayerSourceLink()
        }
        // 迷你条挂在 NavigationStack 外层（而不是 ContentView 的 Tab 上），
        // 这样它能感知导航栈、在设置页收起。视觉位置不变：仍在 Tab 栏之上。
        .withMiniPlayer(isEnabled: showsMiniPlayer)
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

    /// 「我的」链路里的个人主页：二级跳转都 append 到本 Tab 导航栈。
    @ViewBuilder
    private func userHomeView(userId: Int64) -> some View {
        UserHomeView(
            userId: userId,
            onChat: { uid, nickname, avatar in
                path.append(.chat(userId: uid, peerNickname: nickname, peerAvatar: avatar))
            },
            onAddFriend: { uid, nickname in
                path.append(.addFriend(userId: uid, nickname: nickname ?? ""))
            },
            onPlaylist: { id in
                path.append(.playlistDetail(id: id, title: "歌单"))
            },
            onPostDetail: { id in
                path.append(.postDetail(id: id))
            },
            onTagSearch: { tag in
                path.append(.search(tag: tag))
            },
            onEditProfile: { path.append(.editProfile) },
            onPrivacySettings: { path.append(.privacySettings) }
        )
    }

    /// 收藏详情页标题（与「我的」页收藏分区标题一致）。
    private func favoriteDetailTitle(_ type: String) -> String {
        switch type {
        case "albums": return "收藏的专辑"
        case "artists": return "收藏的歌手"
        default: return "收藏的歌单"
        }
    }

    /// 创建自定义歌单：POST api/playlists，成功后刷新「我的」页歌单列表。
    private func createPlaylist(named name: String) {
        FoxMusicApi().createPlaylist(
            title: name,
            description: nil,
            coverImage: nil,
            isPublic: true
        ) { _, error in
            DispatchQueue.main.async {
                if let error {
                    ToastCenter.shared.show(error.localizedDescription)
                } else {
                    ToastCenter.shared.show("歌单「\(name)」已创建")
                    FoxPlaylistRefresh.shared.refreshProfile()
                }
            }
        }
    }

    /// 一级「创建歌单」菜单选完方式后：**先关掉菜单**，等关闭动画走完再弹二级输入。
    ///
    /// 为什么不能只 `showCreateMenu = false` + `createInput = mode`：`BottomSheet`
    /// 是命令式呈现（见 `BottomSheetModifier`），`isPresented` 变 false 只是"不再拦"，并不会
    /// 关闭已 present 的卡片；于是二级输入结束后一级菜单还压在屏幕上（截图 46 可见）。
    /// 也不能在同一 runloop 里 dismiss+present（UIKit 会丢弃这次 present），故延后 0.35s。
    private func selectCreateMode(_ mode: CreatePlaylistInput) {
        showCreateMenu = false
        BottomSheet.dismissTop()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
            createInput = mode
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
    let onFavoriteTracksClick: () -> Void
    let onPlayHistoryClick: () -> Void
    let onOpenDrawer: () -> Void
    let onMoreFavoritePlaylists: () -> Void
    let onMoreFavoriteAlbums: () -> Void
    let onMoreFavoriteArtists: () -> Void
    let onPostDetail: (Int64) -> Void
    let onSharePost: (Int64, String?) -> Void
    let onReportPost: (Int64, String?) -> Void
    let onEditPost: (Int64) -> Void
    let onDownloadManagerClick: () -> Void
    let onCreatePlaylist: () -> Void
    let onEditPlaylist: (KotlinLong) -> Void
    let onPlayAll: ([PlayableSong]) -> Void
    let onMessage: (String) -> Void
    let onUserHome: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxProfileScreen.shared.makeProfileViewController(
            onLogin: onLogin,
            onSettings: onSettings,
            onPlaylistClick: onPlaylistClick,
            onArtistClick: onArtistClick,
            onAlbumClick: onAlbumClick,
            onFavoriteTracksClick: onFavoriteTracksClick,
            onPlayHistoryClick: onPlayHistoryClick,
            onOpenDrawer: onOpenDrawer,
            onMoreFavoritePlaylists: onMoreFavoritePlaylists,
            onMoreFavoriteAlbums: onMoreFavoriteAlbums,
            onMoreFavoriteArtists: onMoreFavoriteArtists,
            onPostDetail: { onPostDetail($0.int64Value) },
            onSharePost: { onSharePost($0.int64Value, $1) },
            onReportPost: { onReportPost($0.int64Value, $1) },
            onEditPost: { onEditPost($0.int64Value) },
            onDownloadManagerClick: onDownloadManagerClick,
            onCreatePlaylistClick: onCreatePlaylist,
            onEditPlaylistClick: onEditPlaylist,
            onPlayAll: onPlayAll,
            onMessage: onMessage,
            onUserHome: onUserHome
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        _ = isLoggedIn
    }
}

/// 收藏详情页：共享层 FavoriteCollectionDetailContent（feature:profile commonMain）渲染 ——
/// 通用「返回 + 标题」顶部栏 + 复用「我的」页的收藏歌单/专辑/艺术家列表项样式。
struct FavoriteCollectionDetailView: View {
    let type: String
    let title: String
    let onBack: () -> Void
    let onPlaylistClick: (Int64, String) -> Void
    let onAlbumClick: (Int64) -> Void
    let onArtistClick: (Int64) -> Void

    var body: some View {
        FavoriteCollectionDetailComposeView(
            type: type,
            title: title,
            onBack: onBack,
            onPlaylistClick: onPlaylistClick,
            onAlbumClick: onAlbumClick,
            onArtistClick: onArtistClick
        )
        .ignoresSafeArea(edges: [.top, .bottom])
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
    }
}

struct FavoriteCollectionDetailComposeView: UIViewControllerRepresentable {
    let type: String
    let title: String
    let onBack: () -> Void
    let onPlaylistClick: (Int64, String) -> Void
    let onAlbumClick: (Int64) -> Void
    let onArtistClick: (Int64) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxFavoriteCollectionDetailScreen.shared.makeViewController(
            type: type,
            title: title,
            onBack: onBack,
            onPlaylistClick: { id, title in onPlaylistClick(id.int64Value, title) },
            onAlbumClick: { onAlbumClick($0.int64Value) },
            onArtistClick: { onArtistClick($0.int64Value) }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
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

/// 设置页（iOS 原生 List 风格，分组与 Android `SettingsScreen` 一致）。
///
/// 游客也能进：账号区在未登录时显示「登录 FoxMusic」入口（此前游客点齿轮
/// 被接到登录、已登录点齿轮被直接退出登录，两个都不对）。
///
/// 功能对齐情况（与 Android 逐组核对过）：
/// - 真实可用：循环模式 / 定时关闭（含取消）/ 歌词字号 / 歌词翻译 / 清理缓存
/// - 待迁移（占位标灰，保持入口与 Android 对齐）：编辑资料 / 隐私设置 /
///   深色模式 / 主题设置 / 下载管理 / 举报历史 / 检查更新 / 锁屏灵动岛歌词
struct SettingsView: View {
    @ObservedObject private var auth = AuthStore.shared
    @ObservedObject private var player = PlayerModel.shared
    @ObservedObject private var appearance = AppearanceStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var confirmLogout = false
    @State private var confirmClearCache = false
    @State private var cacheSizeText: String = "计算中..."
    /// 曲库管理仅管理员可见（对齐 Android SettingsScreen 的 user.isAdmin 门控）。
    @State private var isAdmin = false

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
            accountSection
            playbackSection
            lyricSection
            appSection
            aboutSection
        }
        .listStyle(.insetGrouped)
        .navigationTitle("设置")
        .navigationBarTitleDisplayMode(.inline)
        // 设置是二级页：进入时隐藏底部 Tab 栏（与 Android 设置页是独立路由一致）。
        // 迷你条也一并收起，由 ProfileTabView 按 ProfileRoute.showsMiniPlayer 处理。
        .toolbar(.hidden, for: .tabBar)
        // ⚠️ 用 `.alert` 而不是 `.confirmationDialog`：
        // iOS 26 起 confirmationDialog 在 iPhone 上渲染成**锚定在被点行的 popover**，
        // 并**省略 cancel 按钮**（只能点外部关闭）。对「清理缓存 / 退出登录」这类
        // 破坏性操作，看不到明确的「取消」既不符合 Android 端 AlertDialog 的形态，
        // 也让用户难以判断"点了会怎样"。系统 alert 恒有取消 + 破坏性两个按钮。
        .alert("退出登录", isPresented: $confirmLogout) {
            Button("取消", role: .cancel) {}
            Button("退出登录", role: .destructive) {
                AuthStore.shared.logout()
            }
        } message: {
            Text("退出后将无法同步歌单、收藏与听歌记录，确定退出吗？")
        }
        .alert("清理缓存", isPresented: $confirmClearCache) {
            Button("取消", role: .cancel) {}
            Button("清理", role: .destructive) {
                SettingsCache.clean()
                refreshCacheSize()
                ToastCenter.shared.show("缓存已清理")
            }
        } message: {
            Text("将清理已缓存的音频与网络数据（约 \(cacheSizeText)），不会影响账号与本地设置。")
        }
        .onAppear {
            refreshCacheSize()
            FoxManageLibrary.shared.isAdmin { admin in
                self.isAdmin = admin.boolValue
            }
        }
    }

    // MARK: 分组（与 Android SettingsScreen 逐组对齐）

    private var accountSection: some View {
        Section("账号") {
            if auth.isLoggedIn {
                LabeledContent {
                    Text(accountName).foregroundStyle(.secondary).lineLimit(1)
                } label: {
                    Label("账号", systemImage: "person.circle")
                }
                .accessibilityIdentifier("settings.account")

                NavigationLink {
                    EditProfileView()
                } label: {
                    Label("编辑资料", systemImage: "pencil")
                }
                .accessibilityIdentifier("settings.editProfile")

                NavigationLink {
                    PrivacySettingsView()
                } label: {
                    Label("隐私设置", systemImage: "hand.raised")
                }
                .accessibilityIdentifier("settings.privacy")

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
    }

    private var playbackSection: some View {
        Section("播放") {
            Menu {
                ForEach(RepeatMode.allCases, id: \.rawValue) { mode in
                    Button {
                        player.setRepeatMode(mode)
                    } label: {
                        if player.repeatMode == mode {
                            Label(mode.label, systemImage: "checkmark")
                        } else {
                            Text(mode.label)
                        }
                    }
                }
            } label: {
                LabeledContent {
                    Text(player.repeatMode.label).foregroundStyle(.secondary)
                } label: {
                    Label("循环模式", systemImage: "repeat")
                }
            }
            .accessibilityIdentifier("settings.repeatMode")

            Menu {
                ForEach(PlayerModel.sleepTimerOptions, id: \.self) { minutes in
                    Button("\(minutes) 分钟") {
                        player.startSleepTimer(minutes: minutes)
                    }
                }
            } label: {
                LabeledContent {
                    Text(sleepTimerLabel).foregroundStyle(.secondary)
                } label: {
                    Label("定时关闭", systemImage: "moon.zzz")
                }
            }
            .accessibilityIdentifier("settings.sleepTimer")

            if player.sleepTimerEndsAt != nil {
                Button(role: .destructive) {
                    player.cancelSleepTimer()
                } label: {
                    Text("取消定时关闭")
                }
            }
        }
    }

    private var lyricSection: some View {
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
            if #available(iOS 16.2, *) {
                Toggle(isOn: Binding(
                    get: { LiveLyricsManager.shared.enabled },
                    set: { LiveLyricsManager.shared.enabled = $0 }
                )) {
                    Label("锁屏与灵动岛歌词", systemImage: "lock.iphone")
                }
                .accessibilityIdentifier("settings.liveLyrics")
            }
        } header: {
            Text("桌面与歌词")
        } footer: {
            Text("与播放器「歌词设置」面板共用同一份配置，实时联动。")
        }
    }

    /// 通知设置入口。
    ///
    /// 为什么放在设置页：Android 的 `NotificationSettingsScreen` 只有一个入口 ——
    /// `MessagesContent` 在 `showBackButton = true`（「我的消息」二级页）时才渲染的齿轮，
    /// 但两端都只以 `showBackButton = false` 调用（`TabNavGraph:227` / `FoxMessagesScreen:139`），
    /// 也就是说这个页面在**两端都进不去**（`ChatScreen` 也是无人引用的死代码）。
    /// 这里把入口补在设置页。
    ///
    /// ⚠️ 位置：必须作为「应用」组内的**一行**，与 Android `SettingsScreen` 的
    /// 「应用」组（深色模式 / 主题设置 / 通知设置 / 下载管理 / …）同构。
    /// 曾经把它做成**独立的一组**（header「通知」+ footer）插在「桌面与歌词」和「应用」之间 ——
    /// 多出的一整组（≈100pt）把「应用」组整体下推，「主题设置」（该组第 2 行）掉出首屏，
    /// `List` 是懒加载 → 该行根本没被创建 → XCUITest 按 identifier 查不到，
    /// 表现为 test28「设置页缺少主题设置项」**回归**。
    /// 教训：往设置页插内容时，**插在什么位置**和**插什么**一样重要 ——
    /// 懒加载列表里，把后面的关键项推出首屏 = 该行在无障碍树里消失。
    private var notificationRow: some View {
        NavigationLink {
            NotificationSettingsView()
        } label: {
            Label("通知设置", systemImage: "bell.badge")
        }
        .accessibilityIdentifier("settings.notification")
    }

    private var appSection: some View {
        Section("应用") {
            Menu {
                ForEach(AppAppearance.allCases) { mode in
                    Button {
                        appearance.appearance = mode
                    } label: {
                        if appearance.appearance == mode {
                            Label(mode.label, systemImage: "checkmark")
                        } else {
                            Text(mode.label)
                        }
                    }
                }
            } label: {
                LabeledContent {
                    Text(appearance.appearance.label).foregroundStyle(.secondary)
                } label: {
                    Label("深色模式", systemImage: "circle.lefthalf.filled")
                }
            }
            .accessibilityIdentifier("settings.appearance")

            Menu {
                ForEach(AppThemeAccent.allCases) { item in
                    Button {
                        appearance.accent = item
                    } label: {
                        if appearance.accent == item {
                            Label(item.label, systemImage: "checkmark")
                        } else {
                            Text(item.label)
                        }
                    }
                }
            } label: {
                LabeledContent {
                    HStack(spacing: 6) {
                        if let dot = appearance.accent.color {
                            Circle().fill(dot).frame(width: 12, height: 12)
                        }
                        Text(appearance.accent.label).foregroundStyle(.secondary)
                    }
                } label: {
                    Label("主题设置", systemImage: "paintpalette")
                }
            }
            .accessibilityIdentifier("settings.theme")

            notificationRow

            NavigationLink {
                DownloadManagerView()
            } label: {
                Label("下载管理", systemImage: "arrow.down.circle")
            }
            .accessibilityIdentifier("settings.downloads")
            if isAdmin {
                NavigationLink {
                    ManageLibraryView()
                } label: {
                    Label("曲库管理", systemImage: "square.stack.3d.up")
                }
                .accessibilityIdentifier("settings.manageLibrary")
            }
            NavigationLink {
                ReportHistoryView()
            } label: {
                Label("举报历史", systemImage: "flag")
            }
            .accessibilityIdentifier("settings.reportHistory")

            Button {
                confirmClearCache = true
            } label: {
                LabeledContent {
                    Text(cacheSizeText).foregroundStyle(.secondary)
                } label: {
                    Label("清理缓存", systemImage: "trash")
                }
            }
            .accessibilityIdentifier("settings.clearCache")
        }
    }

    private var aboutSection: some View {
        Section {
            LabeledContent {
                Text(appVersion).foregroundStyle(.secondary)
            } label: {
                Label("版本", systemImage: "info.circle")
            }
            .accessibilityIdentifier("settings.version")
        }
    }

    // MARK: 辅助

    private var sleepTimerLabel: String {
        // 文案对齐 Android `SettingsViewModel.sleepTimerLabel`：未开启 / 剩余 mm:ss
        guard let end = player.sleepTimerEndsAt else { return "未开启" }
        let remain = max(0, Int(end.timeIntervalSinceNow))
        return String(format: "剩余 %02d:%02d", remain / 60, remain % 60)
    }

    private func refreshCacheSize() {
        cacheSizeText = SettingsCache.formattedSize()
    }

    private func pendingRow(title: String, icon: String, subtitle: String? = nil) -> some View {
        HStack {
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).foregroundStyle(.primary)
                    if let subtitle {
                        Text(subtitle).font(.caption).foregroundStyle(.secondary)
                    }
                }
            } icon: {
                Image(systemName: icon).foregroundStyle(.secondary)
            }
            Spacer()
            Text("迁移中")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
    }

    /// 安卓端有、iOS 端**没有对应子系统**的条目。
    ///
    /// 为什么不直接删掉：设置页是两端功能对齐的清单，缺项要能一眼看出原因，
    /// 直接消失会被误读成"漏做"。这里保留条目但明确标注，点击不做任何事。
    private func unavailableRow(title: String, icon: String, subtitle: String) -> some View {
        HStack {
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).foregroundStyle(.secondary)
                    Text(subtitle).font(.caption).foregroundStyle(.tertiary)
                }
            } icon: {
                Image(systemName: icon).foregroundStyle(.tertiary)
            }
            Spacer()
            Text("iOS 不适用")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - 缓存统计与清理

/// 清理缓存的真实实现：系统 URLCache（封面/音频请求）+ Caches 目录。
/// 与 Android 的「清理已缓存音频文件，不影响账号与本地设置」同口径。
private enum SettingsCache {

    static var cachesDir: URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
    }

    static func formattedSize() -> String {
        let dirBytes = directorySize(cachesDir)
        let urlBytes = Int64(URLCache.shared.currentDiskUsage) + Int64(URLCache.shared.currentMemoryUsage)
        return ByteCountFormatter.string(fromByteCount: max(0, dirBytes + urlBytes), countStyle: .file)
    }

    static func clean() {
        URLCache.shared.removeAllCachedResponses()
        if let entries = try? FileManager.default.contentsOfDirectory(
            at: cachesDir, includingPropertiesForKeys: nil
        ) {
            for entry in entries {
                try? FileManager.default.removeItem(at: entry)
            }
        }
    }

    private static func directorySize(_ url: URL) -> Int64 {
        guard let entries = FileManager.default.enumerator(
            at: url, includingPropertiesForKeys: [.fileSizeKey]
        ) else { return 0 }
        var total: Int64 = 0
        for case let file as URL in entries {
            let bytes = (try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
            total += Int64(bytes)
        }
        return total
    }
}

// MARK: - 创建歌单（与 Android CreatePlaylistBottomSheet / 输入弹窗同流程）

/// 创建歌单菜单：导入第三方歌单 / 创建自定义歌单。
struct CreatePlaylistMenuSheet: View {
    let onImport: () -> Void
    let onCustom: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            SheetHeader(title: "创建歌单")
            SheetRow(
                icon: "icloud.and.arrow.down",
                title: "导入第三方歌单",
                subtitle: "支持网易云音乐、QQ音乐",
                action: onImport
            )
            SheetRow(
                icon: "plus.circle",
                title: "创建自定义歌单",
                subtitle: "创建一个空白歌单",
                action: onCustom
            )
            .padding(.bottom, 12)
        }
    }
}

/// 创建歌单的二级输入：自定义创建输入名称，导入输入第三方歌单链接。
enum CreatePlaylistInput: String, Identifiable {
    case custom
    case importLink

    var id: String { rawValue }
}

struct CreatePlaylistInputSheet: View {
    let mode: CreatePlaylistInput
    let onCancel: () -> Void
    let onConfirm: (String) -> Void

    @State private var text = ""
    @FocusState private var focused: Bool

    private var titleText: String {
        mode == .custom ? "创建自定义歌单" : "导入第三方歌单"
    }

    private var placeholder: String {
        mode == .custom ? "输入歌单名称" : "粘贴网易云音乐 / QQ音乐歌单链接"
    }

    var body: some View {
        VStack(spacing: 0) {
            SheetHeader(title: titleText)
            TextField(placeholder, text: $text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(mode == .custom ? .default : .URL)
                .padding(12)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .padding(.horizontal, 16)
                .padding(.top, 4)
                .focused($focused)
                .onSubmit(confirm)
            HStack(spacing: 12) {
                Button(action: onCancel) {
                    Text("取消")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.bordered)
                Button(action: confirm) {
                    Text("确定")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(16)
        }
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { focused = true }
        }
    }

    private func confirm() {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }
        onConfirm(value)
    }
}

// MARK: - 导入第三方歌单（与 Android ImportPlaylistSelectScreen 同流程）

/// 解析第三方歌单链接 → 编辑歌单名、勾选曲目 → 确认导入。
/// 确认后：转存封面 → 创建歌单 → 启动 Kotlin 后台导入队列 → 落到新歌单详情页。
struct ImportPlaylistSelectView: View {
    let link: String
    let onCreated: (Int64, String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var loading = true
    @State private var errorMessage: String?
    @State private var parsed: ParsedPlaylistDto?
    @State private var titleText = ""
    @State private var selected: Set<Int> = []
    @State private var confirming = false

    private var tracks: [ParsedTrackDto] {
        (parsed?.tracks as? [ParsedTrackDto]) ?? []
    }

    var body: some View {
        Group {
            if loading {
                VStack(spacing: 12) {
                    ProgressView()
                    Text("正在解析歌单…")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if parsed == nil {
                VStack(spacing: 12) {
                    Text(errorMessage ?? "解析失败")
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                    Button("返回") { dismiss() }
                }
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    Section {
                        TextField("歌单名称", text: $titleText)
                            .textInputAutocapitalization(.never)
                    }
                    Section("歌曲（已选 \(selected.count)/\(tracks.count)）") {
                        ForEach(Array(tracks.enumerated()), id: \.offset) { index, track in
                            trackRow(index: index, track: track)
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .disabled(confirming)
            }
        }
        .navigationTitle("选择导入歌曲")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if parsed != nil {
                ToolbarItem(placement: .topBarLeading) {
                    Button("全选") { selected = Set(tracks.indices) }
                }
                ToolbarItem(placement: .topBarLeading) {
                    Button("清空") { selected = [] }
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            if parsed != nil {
                Button {
                    confirmImport()
                } label: {
                    Text(confirming ? "正在创建歌单…" : "确认导入（\(selected.count) 首）")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                }
                .buttonStyle(.borderedProminent)
                .disabled(confirming || selected.isEmpty)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(.bar)
            }
        }
        .task { parse() }
    }

    private func trackRow(index: Int, track: ParsedTrackDto) -> some View {
        let isSelected = selected.contains(index)
        return Button {
            if isSelected {
                selected.remove(index)
            } else {
                selected.insert(index)
            }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
                coverView(track.cover)
                    .frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                VStack(alignment: .leading, spacing: 2) {
                    Text(track.title)
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Text(artistText(track))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func coverView(_ url: String?) -> some View {
        if let url, !url.isEmpty, let u = URL(string: url) {
            // 用 phase-switch 写法（与 MessagesTabView 一致），
            // 避免 content/placeholder 双闭包重载在新 SDK 下的类型推断问题
            AsyncImage(url: u) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                default:
                    coverPlaceholder
                }
            }
        } else {
            coverPlaceholder
        }
    }

    private var coverPlaceholder: some View {
        ZStack {
            Color(.secondarySystemBackground)
            Image(systemName: "music.note")
                .foregroundStyle(.secondary)
        }
    }

    private func artistText(_ track: ParsedTrackDto) -> String {
        let names = ((track.artists as? [ParsedArtistDto]) ?? []).map { $0.name }
            .filter { !$0.isEmpty }
        if names.isEmpty { return durationText(track) }
        return names.joined(separator: "、") + " · " + durationText(track)
    }

    private func durationText(_ track: ParsedTrackDto) -> String {
        let seconds = Int(Int64(track.duration) / 1000)
        guard seconds > 0 else { return "--:--" }
        return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }

    private func parse() {
        FoxMusicApi().parseImportPlaylist(url: link) { dto, error in
            DispatchQueue.main.async {
                loading = false
                if let error {
                    errorMessage = error.localizedDescription
                } else if let dto {
                    parsed = dto
                    titleText = dto.title
                    let list = (dto.tracks as? [ParsedTrackDto]) ?? []
                    selected = Set(list.indices)
                }
            }
        }
    }

    private func confirmImport() {
        guard let parsed, !confirming else { return }
        confirming = true
        let chosen = selected.sorted().compactMap { tracks.indices.contains($0) ? tracks[$0] : nil }
        guard !chosen.isEmpty else {
            confirming = false
            ToastCenter.shared.show("请至少选择一首歌")
            return
        }
        let playlistTitle = titleText.trimmingCharacters(in: .whitespacesAndNewlines)
        let finalTitle = playlistTitle.isEmpty ? "导入歌单" : playlistTitle

        // ① 封面转存（失败继续无封面创建，与 Android 一致）
        let createPlaylist: (String?) -> Void = { coverRemote in
            FoxMusicApi().createPlaylist(
                title: finalTitle,
                description: parsed.description.isEmpty ? nil : parsed.description,
                coverImage: coverRemote,
                isPublic: true
            ) { dto, error in
                DispatchQueue.main.async {
                    if let error {
                        confirming = false
                        ToastCenter.shared.show(error.localizedDescription)
                        return
                    }
                    guard let dto else {
                        confirming = false
                        ToastCenter.shared.show("创建歌单失败")
                        return
                    }
                    // ② 启动 Kotlin 后台导入队列（队列自己会写 import-session 进度）
                    FoxPlaylistImportQueue.shared.start(
                        api: FoxMusicApi(),
                        playlistId: Int64(dto.id),
                        platform: parsed.platform,
                        tracks: chosen
                    ) { imported, failed in
                        DispatchQueue.main.async {
                            let ok = imported.intValue
                            let bad = failed.intValue
                            ToastCenter.shared.show(
                                bad == 0 ? "导入完成：成功 \(ok) 首" : "导入结束：成功 \(ok) 首，失败 \(bad) 首"
                            )
                            FoxPlaylistRefresh.shared.refreshProfile()
                        }
                    }
                    confirming = false
                    ToastCenter.shared.show("已创建歌单，正在后台导入 \(chosen.count) 首")
                    FoxPlaylistRefresh.shared.refreshProfile()
                    onCreated(Int64(dto.id), dto.title)
                }
            }
        }

        if let cover = parsed.cover, !cover.isEmpty {
            FoxMusicApi().uploadImageByUrl(url: cover, referer: nil) { url, _ in
                DispatchQueue.main.async {
                    createPlaylist(url)
                }
            }
        } else {
            createPlaylist(nil)
        }
    }
}

// MARK: - 歌单编辑页（与 Android PlaylistEditScreen 同字段：封面/名称/描述/公开）

struct PlaylistEditView: View {
    let playlistId: Int64
    var onSaved: () -> Void = {}

    @Environment(\.dismiss) private var dismiss
    @State private var loading = true
    @State private var loadError: String?
    @State private var saving = false
    @State private var title = ""
    @State private var descriptionText = ""
    @State private var isPublic = true
    @State private var remoteCover: String?
    @State private var pendingCoverImage: UIImage?
    @State private var pickerItem: PhotosPickerItem?
    @State private var uploadingCover = false

    var body: some View {
        Form {
            Section {
                VStack(spacing: 8) {
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        coverView
                            .frame(width: 120, height: 120)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .disabled(uploadingCover || saving)
                    Text(uploadingCover ? "封面上传中…" : "点击更换封面")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .onChange(of: pickerItem) { item in
                    loadPickedCover(item)
                }
            }
            Section("歌单名称") {
                TextField("歌单名称", text: $title)
                    .textInputAutocapitalization(.never)
            }
            Section("歌单描述") {
                TextField("介绍一下这个歌单", text: $descriptionText, axis: .vertical)
                    .lineLimit(3...6)
            }
            Section {
                Toggle("公开歌单", isOn: $isPublic)
            } footer: {
                Text("开启后其他用户可搜索并查看此歌单")
            }
        }
        .navigationTitle("编辑歌单")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("取消") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(saving ? "保存中…" : "保存") { save() }
                    .disabled(saving || loading)
            }
        }
        .overlay {
            if loading {
                ProgressView()
            }
        }
        .task { load() }
    }

    @ViewBuilder
    private var coverView: some View {
        if let pendingCoverImage {
            Image(uiImage: pendingCoverImage)
                .resizable()
                .scaledToFill()
        } else if let remoteCover, !remoteCover.isEmpty, let url = URL(string: remoteCover) {
            AsyncImage(url: url) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                Color(.secondarySystemBackground)
            }
        } else {
            ZStack {
                Color(.secondarySystemBackground)
                Image(systemName: "music.note.list")
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func load() {
        FoxMusicApi().fetchPlaylistDetail(playlistId: playlistId, page: 1, limit: 1) { detail, error in
            DispatchQueue.main.async {
                loading = false
                if let d = detail {
                    title = d.title
                    descriptionText = d.description ?? ""
                    isPublic = d.isPublic
                    remoteCover = d.coverImage
                } else {
                    loadError = error?.localizedDescription ?? "加载失败"
                    ToastCenter.shared.show(loadError!)
                }
            }
        }
    }

    private func loadPickedCover(_ item: PhotosPickerItem?) {
        guard let item else { return }
        item.loadTransferable(type: Data.self) { result in
            if case .success(let data?) = result, let image = UIImage(data: data) {
                DispatchQueue.main.async {
                    pendingCoverImage = image
                    uploadCover(data)
                }
            }
        }
    }

    private func uploadCover(_ data: Data) {
        uploadingCover = true
        let dst = FileManager.default.temporaryDirectory
            .appendingPathComponent("playlist_cover_\(UUID().uuidString).jpg")
        do {
            try data.write(to: dst)
        } catch {
            uploadingCover = false
            ToastCenter.shared.show("封面保存失败")
            return
        }
        FoxMusicApi().uploadImage(filePath: dst.path) { remoteUrl, error in
            DispatchQueue.main.async {
                uploadingCover = false
                if let error {
                    ToastCenter.shared.show(error.localizedDescription)
                } else if let remoteUrl {
                    remoteCover = remoteUrl
                    ToastCenter.shared.show("封面已上传")
                }
            }
        }
    }

    private func save() {
        let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedTitle.isEmpty else {
            ToastCenter.shared.show("歌单名称不能为空")
            return
        }
        saving = true
        let trimmedDesc = descriptionText.trimmingCharacters(in: .whitespacesAndNewlines)
        FoxMusicApi().updatePlaylist(
            playlistId: playlistId,
            title: trimmedTitle,
            description: trimmedDesc.isEmpty ? nil : trimmedDesc,
            coverImage: remoteCover,
            isPublic: KotlinBoolean(value: isPublic)
        ) { _, error in
            DispatchQueue.main.async {
                saving = false
                if let error {
                    ToastCenter.shared.show(error.localizedDescription)
                } else {
                    ToastCenter.shared.show("歌单已更新")
                    FoxPlaylistRefresh.shared.refreshProfile()
                    FoxPlaylistDetailScreen.shared.refresh()
                    onSaved()
                    dismiss()
                }
            }
        }
    }
}
