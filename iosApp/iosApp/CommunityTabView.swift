import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import FoxShared

/// 社区 Tab 路由：帖子详情 / 搜索 / 专辑详情 / 发帖 / 发布任务 /
/// 个人主页（帖子详情作者入口）及其二级页。
enum CommunityRoute: Hashable {
    case postDetail(id: Int64)
    case albumDetail(id: Int64)
    case playlistDetail(id: Int64, title: String)
    /// 标签深链时带 tag（进搜索页即落帖子 Tab 出结果）
    case search(tag: String?)
    case createPost
    case editPost(id: Int64)
    case publishQueue
    case userHome(id: Int64)
    case chat(userId: Int64, peerNickname: String?, peerAvatar: String?)
    case addFriend(userId: Int64, nickname: String)
    case editProfile
    case privacySettings
}

/// 社区 Tab：共享层 CommunityPlazaContent（feature:social commonMain）渲染 ——
/// 与 Android 社区广场同一份 UI（瀑布流/列表双布局 + 点赞 + 评论输入条）。
///
/// 数据全在 Kotlin 侧（Ktor + Paging3），Swift 只接导航/播放/提示回调。
struct CommunityTabView: View {
    @ObservedObject private var auth = AuthStore.shared
    @State private var path: [CommunityRoute] = []
    @State private var reportTarget: ReportTarget?
    @StateObject private var router = AppTabRouter.shared

    /// 消费播放列表「来源」卡片跳转：社区可承接帖子/专辑/歌单。
    private func consumePlayerSourceLink() {
        router.consumePlayerSource(in: .community) { link in
            switch link {
            case .post(let id):
                path.append(.postDetail(id: id))
            case .album(let id):
                path.append(.albumDetail(id: id))
            case .playlist(let id, let title):
                path.append(.playlistDetail(id: id, title: title))
            case .rank, .recommend, .newMusic:
                break
            }
        }
    }

    var body: some View {
        NavigationStack(path: $path) {
            CommunityComposeView(
                onPostClick: { id in
                    path.append(.postDetail(id: id))
                },
                onSearchClick: {
                    path.append(.search(tag: nil))
                },
                onTagClick: { tag in
                    path.append(.search(tag: tag))
                },
                onCreatePost: {
                    FoxCreatePostStore.shared.clear()
                    path.append(.createPost)
                },
                onPublishQueue: {
                    path.append(.publishQueue)
                },
                onPlayMusic: { song, source in
                    if let song {
                        PlayerModel.shared.play(
                            queue: [song],
                            startIndex: 0,
                            source: source.map(PlaybackSource.init)
                        )
                    }
                },
                onReportPost: { id, title in
                    reportTarget = ReportTarget(type: "post", targetId: id, name: title)
                },
                onSharePost: { id, text in
                    ShareActionPresenter.present(
                        shareType: "post",
                        shareId: id,
                        displayTitle: "分享动态",
                        systemText: "FoxMusic 动态：\(text)\nID：\(id)"
                    )
                },
                onEditPost: { id in
                    FoxCreatePostStore.shared.clear()
                    path.append(.editPost(id: id))
                },
                onToast: { message in
                    ToastCenter.shared.show(message)
                },
                onOpenDrawer: { router.openDrawer() }
            )
            .ignoresSafeArea(edges: [.top, .bottom])
            .navigationBarHidden(true)
            .themedPageBackground()
            .navigationDestination(for: CommunityRoute.self) { route in
                switch route {
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
                case .albumDetail(let id):
                    PlaylistDetailView(playlistId: id, isRank: false, title: "专辑详情", detailType: "album")
                case .playlistDetail(let id, let title):
                    PlaylistDetailView(playlistId: id, isRank: false, title: title)
                case .search(let tag):
                    SearchView(
                        onArtistClick: { _ in },
                        onAlbumClick: { id in path.append(.albumDetail(id: id)) },
                        onPostClick: { id in path.append(.postDetail(id: id)) },
                        initialTag: tag
                    )
                case .createPost:
                    CreatePostView(onBack: { path.removeLast() })
                case .editPost(let id):
                    CreatePostView(postId: id, onBack: { path.removeLast() })
                case .publishQueue:
                    PublishQueueView(onBack: { path.removeLast() })
                case .userHome(let id):
                    userHomeView(userId: id)
                case .chat(let userId, let peerNickname, let peerAvatar):
                    ChatDetailView(
                        userId: userId,
                        peerNickname: peerNickname,
                        peerAvatar: peerAvatar,
                        onUserProfile: { uid in
                            path.append(.userHome(id: uid))
                        },
                        onSettings: { settingsUserId, nickname, avatar in
                            // 社区链路没有聊天设置场景，给一个回到主页的等价入口
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
                                ToastCenter.shared.show("歌手详情请在消息页打开")
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
        // 二级页收起迷你条，避免浮层盖住个人主页关系条/聊天输入栏（与消息 Tab 同口径）。
        .withMiniPlayer(isEnabled: path.isEmpty)
        // 播放列表来源跳转：已在社区页时即时消费；从别的 Tab 切过来时 .task 兜底。
        .task { consumePlayerSourceLink() }
        .onChange(of: router.pendingPlayerSourceLink) { _ in
            consumePlayerSourceLink()
        }
        .bottomSheet(item: $reportTarget) { target in
            ReportSheet(targetType: target.type, targetId: target.targetId, targetName: target.name) {}
        }
    }

    /// 社区链路里的个人主页：所有二级跳转都 append 到当前 Tab 的导航栈。
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
}

/// 举报目标（供 sheet(item:) 使用）。
struct ReportTarget: Identifiable {
    let id = UUID()
    let type: String
    let targetId: Int64
    let name: String
}

/// 把 Kotlin 侧社区广场 VC 包进 SwiftUI。
/// 滚动/数据状态留在 Kotlin/Compose 内部，updateUIViewController 留空避免重建丢位置。
struct CommunityComposeView: UIViewControllerRepresentable {
    let onPostClick: (Int64) -> Void
    let onSearchClick: () -> Void
    let onTagClick: (String) -> Void
    let onCreatePost: () -> Void
    let onPublishQueue: () -> Void
    let onPlayMusic: (PlayableSong?, PlaybackPlaylistSource?) -> Void
    let onReportPost: (Int64, String) -> Void
    let onSharePost: (Int64, String) -> Void
    let onEditPost: (Int64) -> Void
    let onToast: (String) -> Void
    let onOpenDrawer: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxCommunityScreen.shared.makeCommunityViewController(
            // KotlinLong → Swift Int64 桥接：body 里闭包用的是 Int64，
            // Kotlin 导出的回调签名是 KotlinLong，必须在这里转一手
            onPostClick: { onPostClick($0.int64Value) },
            onSearchClick: onSearchClick,
            onCreatePost: onCreatePost,
            onPublishQueue: onPublishQueue,
            onPlayMusic: onPlayMusic,
            onReportPost: { onReportPost($0.int64Value, $1) },
            onSharePost: { onSharePost($0.int64Value, $1) },
            onEditPost: { onEditPost($0.int64Value) },
            onTagClick: onTagClick,
            onToast: onToast,
            onOpenDrawer: onOpenDrawer
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// 帖子详情页：共享层 PostDetailContent（feature:social commonMain）渲染 ——
/// 与 Android 帖子详情同一份 UI（媒体轮播 + 音乐条 + 正文 + 评论列表 + 回复 + 底栏点赞）。
///
/// 返回：页面导航栏被隐藏（内容里自带返回箭头，与 Android 一致），
/// 所以必须把 Kotlin 的 onBack 接回 SwiftUI 的 dismiss()，否则点箭头无反应。
struct PostDetailView: View {
    let postId: Int64
    let onReportPost: (Int64, String) -> Void
    /// 帖子标签点击 → 宿主推搜索页（默认不处理，由各 Tab 注入自己的栈路由）
    var onTagClick: (String) -> Void = { _ in }
    var onUserHome: (Int64) -> Void = { _ in }
    var onEditPost: (Int64) -> Void = { _ in }
    var onPostDeleted: () -> Void = {}
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var auth = AuthStore.shared
    @State private var commentReport: ReportTarget?

    var body: some View {
        PostDetailComposeView(
            postId: postId,
            currentUserId: auth.currentUserId,
            onBack: { dismiss() },
            onTagClick: onTagClick,
            onPlayMusic: { song, source in
                if let song {
                    PlayerModel.shared.play(
                        queue: [song],
                        startIndex: 0,
                        source: source.map(PlaybackSource.init)
                    )
                }
            },
            onToast: { message in
                ToastCenter.shared.show(message)
            },
            onCommentDeleted: {},
            onReportPost: onReportPost,
            onUserHome: onUserHome,
            onEditPost: onEditPost,
            onSharePost: { id, text in
                ShareActionPresenter.present(
                    shareType: "post",
                    shareId: id,
                    displayTitle: "分享动态",
                    systemText: "FoxMusic 动态：\(text)\nID：\(id)"
                )
            },
            onReportComment: { commentId in
                commentReport = ReportTarget(type: "post-comment", targetId: commentId, name: "评论")
            },
            onPostDeleted: { onPostDeleted(); dismiss() }
        )
        // 整页仍 ignore 安全区（背景渐变要铺到上下边缘）；迷你条会盖住评论输入栏
        // 这件事改用「显式底部内边距」解决 —— 由 PostDetailComposeView 按
        // MiniPlayerBar.contentHeight 传入 bottomBarBottomInsetDp。
        // 不用 safeAreaInset：NavigationStack 的 pushed 页面拿不到外层 safeAreaInset
        // 缩减后的安全区，靠它预留会失效。
        .ignoresSafeArea(edges: [.top, .bottom])
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
        .bottomSheet(item: $commentReport) { target in
            ReportSheet(
                targetType: target.type,
                targetId: target.targetId,
                targetName: target.name
            ) {}
        }
    }
}

/// 把 Kotlin 侧帖子详情 VC 包进 SwiftUI。
struct PostDetailComposeView: UIViewControllerRepresentable {
    let postId: Int64
    let currentUserId: Int64
    let onBack: () -> Void
    let onTagClick: (String) -> Void
    let onPlayMusic: (PlayableSong?, PlaybackPlaylistSource?) -> Void
    let onToast: (String) -> Void
    let onCommentDeleted: () -> Void
    let onReportPost: (Int64, String) -> Void
    let onUserHome: (Int64) -> Void
    let onEditPost: (Int64) -> Void
    let onSharePost: (Int64, String) -> Void
    let onReportComment: (Int64) -> Void
    let onPostDeleted: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxCommunityScreen.shared.makePostDetailViewController(
            postId: postId,
            currentUserId: currentUserId,
            onBack: onBack,
            onPlayMusic: onPlayMusic,
            onToast: onToast,
            onCommentDeleted: onCommentDeleted,
            onReportPost: { onReportPost($0.int64Value, $1) },
            onNavigateToUserHome: { onUserHome($0.int64Value) },
            onEditPost: { onEditPost($0.int64Value) },
            onSharePost: { onSharePost($0.int64Value, $1) },
            onReportComment: { onReportComment($0.int64Value) },
            onPostDeleted: onPostDeleted,
            onTagClick: onTagClick,
            // 帖子详情永远是各 Tab 的二级页，迷你条已按 path 收起，恒传 0。
            bottomBarBottomInsetDp: 0
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - 发布动态页

/// 发布动态页 SwiftUI 外壳：包装 Kotlin `FoxCreatePostScreen`，负责媒体/歌曲/话题选择。
/// postId > 0 时为编辑态（回显原帖，保存走 PUT）。
struct CreatePostView: View {
    var postId: Int64 = -1
    let onBack: () -> Void
    @State private var pickerPresented = false
    @State private var musicPickerPresented = false
    @State private var tagPickerPresented = false

    var body: some View {
        CreatePostComposeView(
            postId: postId,
            onBack: onBack,
            onPublished: {
                // 成功文案由 Kotlin 侧按新建/编辑分别 Toast，这里只负责返回。
                onBack()
            },
            onPickMedia: { pickerPresented = true },
            onSelectMusic: { musicPickerPresented = true },
            onSelectTag: { tagPickerPresented = true },
            onToast: { ToastCenter.shared.show($0) }
        )
        .ignoresSafeArea(edges: [.top, .bottom])
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
        .sheet(isPresented: $pickerPresented) {
            MediaPickerView { path, type in
                FoxCreatePostStore.shared.addMedia(path: path, type: type, videoPath: nil)
            }
        }
        .sheet(isPresented: $musicPickerPresented) {
            MusicPickerView { music in
                if let m = music {
                    FoxCreatePostStore.shared.setMusic(music: m)
                }
            }
        }
        .sheet(isPresented: $tagPickerPresented) {
            TagPickerView { name, id in
                FoxCreatePostStore.shared.addTag(name: name, id: id)
            }
        }
    }
}

struct CreatePostComposeView: UIViewControllerRepresentable {
    var postId: Int64 = -1
    let onBack: () -> Void
    let onPublished: () -> Void
    let onPickMedia: () -> Void
    let onSelectMusic: () -> Void
    let onSelectTag: () -> Void
    let onToast: (String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxCreatePostScreen.shared.makeViewController(
            editPostId: postId,
            onBack: onBack,
            onPublished: onPublished,
            onPickMedia: onPickMedia,
            onSelectMusic: onSelectMusic,
            onSelectTag: onSelectTag,
            onToast: onToast
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - 发布任务状态页

struct PublishQueueView: View {
    let onBack: () -> Void

    var body: some View {
        PublishQueueComposeView(onBack: onBack, onToast: { ToastCenter.shared.show($0) })
            .ignoresSafeArea(edges: [.top, .bottom])
            .navigationBarHidden(true)
            .toolbar(.hidden, for: .tabBar)
            .swipeBackEnabled()
            .themedPageBackground()
    }
}

struct PublishQueueComposeView: UIViewControllerRepresentable {
    let onBack: () -> Void
    let onToast: (String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxPostPublishQueueScreen.shared.makeViewController(onBack: onBack, onToast: onToast)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - 媒体选择（PHPicker，无需权限）

/// PHPicker 包装：选图片/视频，把文件复制到 tmp 后回调本地路径与类型。
struct MediaPickerView: UIViewControllerRepresentable {
    let onPicked: (String, String) -> Void

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration(photoLibrary: .shared())
        config.filter = .any(of: [.images, .videos])
        config.selectionLimit = 9
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPicked: onPicked) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let onPicked: (String, String) -> Void
        init(onPicked: @escaping (String, String) -> Void) { self.onPicked = onPicked }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            picker.dismiss(animated: true)
            for result in results {
                let provider = result.itemProvider
                if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) {
                    provider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { url, _ in
                        guard let url else { return }
                        let dest = FileManager.default.temporaryDirectory
                            .appendingPathComponent(UUID().uuidString + ".\(url.pathExtension)")
                        do {
                            try FileManager.default.copyItem(at: url, to: dest)
                            self.onPicked(dest.path, "image")
                        } catch { NSLog("copy image failed: \(error)") }
                    }
                } else if provider.hasItemConformingToTypeIdentifier(UTType.movie.identifier) {
                    provider.loadFileRepresentation(forTypeIdentifier: UTType.movie.identifier) { url, _ in
                        guard let url else { return }
                        let dest = FileManager.default.temporaryDirectory
                            .appendingPathComponent(UUID().uuidString + ".\(url.pathExtension)")
                        do {
                            try FileManager.default.copyItem(at: url, to: dest)
                            self.onPicked(dest.path, "video")
                        } catch { NSLog("copy video failed: \(error)") }
                    }
                }
            }
        }
    }
}

// MARK: - 歌曲/话题选择（简易实现）

/// 歌曲选择：复用搜索页能力的精简版 —— 直接调用 FoxMusicApi 搜索并展示结果。
struct MusicPickerView: View {
    let onSelected: (FoxSelectedMusic?) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var keyword = ""
    @State private var results: [FoxSelectedMusic] = []
    @State private var loading = false

    var body: some View {
        NavigationStack {
            List {
                TextField("搜索歌曲", text: $keyword)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit { search() }
                if loading { ProgressView() }
                ForEach(results, id: \.id) { song in
                    Button {
                        onSelected(song)
                        dismiss()
                    } label: {
                        VStack(alignment: .leading) {
                            Text(song.title)
                            if let artist = song.artist { Text(artist).font(.caption).foregroundStyle(.secondary) }
                        }
                    }
                }
            }
            .navigationTitle("选择歌曲")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { onSelected(nil); dismiss() } } }
        }
    }

    private func search() {
        guard !keyword.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        loading = true
        FoxMusicApi().searchMusic(keyword: keyword, page: 1, limit: 20) { songs, error in
            DispatchQueue.main.async {
                loading = false
                if let error { ToastCenter.shared.show(error.localizedDescription); return }
                guard let list = songs as? [MusicDto] else { return }
                results = list.map { dto in
                    FoxSelectedMusic(
                        id: Int64(dto.id) ?? 0,
                        title: dto.title,
                        cover: dto.coverUrl,
                        artist: dto.artists.first?.name,
                        isFavorite: false
                    )
                }
            }
        }
    }
}

/// 话题选择：拉取话题列表。
struct TagPickerView: View {
    let onSelected: (String, Int64) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var tags: [(name: String, id: Int64)] = []

    var body: some View {
        NavigationStack {
            List(tags, id: \.name) { tag in
                Button("#\(tag.name)") { onSelected(tag.name, tag.id); dismiss() }
            }
            .navigationTitle("选择话题")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { load() }
        }
    }

    private func load() {
        FoxMusicApi().fetchPostTags(limit: 50, keyword: nil) { list, error in
            DispatchQueue.main.async {
                if let error { ToastCenter.shared.show(error.localizedDescription); return }
                guard let arr = list as? [TagDto] else { return }
                tags = arr.map { ($0.name, $0.id) }
            }
        }
    }
}
