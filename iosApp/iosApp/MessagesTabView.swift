import SwiftUI
import UIKit
import FoxShared

/// 用户资料页的路由参数（Kotlin 回调一次给全，装进 Hashable 结构体便于 NavigationStack 传递）。
/// 可选字段带默认值：从聊天页点对方头像进来时只会带 userId。
struct UserProfileArgs: Hashable {
    var userId: Int64
    var nickname: String? = nil
    var avatar: String? = nil
    var signature: String? = nil
    var isFriend: Bool = false
    var isRequested: Bool = false
}

/// 消息 Tab 路由。二级页面全部由共享层 Compose 渲染（feature:chat commonMain），
/// Swift 只负责「往哪儿去、回来时退到哪儿」。
///
/// 聊天信息 / 聊天记录搜索用 SwiftUI 原生页（对齐 Android ChatSettingsScreen /
/// ChatSearchScreen / UserChatSearchScreen）。
enum MessagesRoute: Hashable {
    /// 标签深链时带 tag（进搜索页即落帖子 Tab 出结果）
    case search(tag: String?)
    case friends
    case searchUser
    case addFriend(userId: Int64, nickname: String)
    case userProfile(UserProfileArgs)
    case chat(userId: Int64, peerNickname: String?, peerAvatar: String?)
    /// 聊天信息（聊天页右上角「⋮」进入）
    case chatSettings(userId: Int64, peerNickname: String?, peerAvatar: String?)
    /// 查找聊天记录（本地全局搜索）
    case chatSearch
    /// 会话内查找聊天记录（从聊天信息页进入，只搜该会话）
    case userChatSearch(userId: Int64, peerNickname: String?, peerAvatar: String?)
    /// type 取值与后端/两端一致：comment / like / system
    case notificationCategory(type: String)
    /// 通知设置（本地开关，SwiftUI 原生页 —— 与 Android NotificationSettingsScreen 对齐）
    case notificationSettings
    case albumDetail(id: Int64)
    /// 聊天卡片消息 → 歌手详情
    case artistDetail(id: Int64)
    /// 个人主页（用户资料卡「查看主页」、帖子详情作者入口）
    case userHome(id: Int64)
    /// 个人主页「动态」Tab → 帖子详情
    case postDetail(id: Int64)
    /// 帖子详情 → 编辑自己的帖子
    case editPost(id: Int64)
    /// 个人主页「歌单」Tab → 歌单详情
    case playlistDetail(id: Int64, title: String)
    /// 个人主页（自己）更多菜单 → 编辑资料 / 隐私设置
    case editProfile
    case privacySettings
}

/// 消息 Tab：共享层 MessagesContent（feature:chat commonMain）渲染 ——
/// 与 Android 消息页同一份 UI（好友/评论和@/赞和通知/系统公告 四个入口 + 会话列表）。
///
/// 数据全在 Kotlin 侧（Ktor REST：api/messages/conversations、api/friends/requests、
/// api/notifications），Swift 只接导航回调。
///
/// 未读角标由 ContentView 挂在 Tab 上（RealtimeBadgeCenter，来自服务端补拉）。
struct MessagesTabView: View {
    /// 作为根级模态（抽屉入口）呈现时传入：非 nil 时顶部栏出现返回箭头，点击关闭模态。
    /// 作为底部 Tab 时保持 nil（与旧行为一致）。
    var onClose: (() -> Void)? = nil

    @State private var path: [MessagesRoute] = []
    @ObservedObject private var auth = AuthStore.shared
    @StateObject private var router = AppTabRouter.shared
    @State private var showLogin = false
    /// 帖子举报目标（个人主页动态 → 帖子详情 → 举报）。
    @State private var reportTarget: ReportTarget?

    /// 消费 `AppTabRouter` 里待处理的通知跳转（应用内横幅点击）。
    ///
    /// 未登录时不跳二级页 —— 与 gated 入口同口径：二级页都依赖鉴权接口，
    /// 直接把登录页弹出来，登录成功后用户自己回到消息 Tab 即可。
    private func consumePendingLink() {
        guard let link = router.pendingMessagesLink else { return }
        router.pendingMessagesLink = nil
        guard auth.isLoggedIn else {
            showLogin = true
            return
        }
        switch link {
        case .chat(let peerUserId, let nickname):
            path.append(.chat(userId: peerUserId, peerNickname: nickname, peerAvatar: nil))
        case .category(let type):
            path.append(.notificationCategory(type: type))
        }
    }

    /// 消费播放列表「来源」卡片跳转：消息 Tab 可承接帖子/专辑/歌单（榜单回发现 Tab）。
    private func consumePlayerSourceLink() {
        router.consumePlayerSource(in: .messages) { link in
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

    /// 未登录门控：消息 Tab 所有功能入口（会话/好友/通知分类/搜索/通知设置）
    /// 都依赖鉴权接口，未登录点击统一弹全屏登录页（与「我的」Tab 同一套路，
    /// 对齐 Android 社区 Tab 的 `navigate(LOGIN_ROUTE)` 惯例）。
    /// 两个重载分别覆盖无参回调（好友/通知入口）与带参回调（会话点击带 KotlinLong）。
    private func gated(_ action: @escaping () -> Void) -> () -> Void {
        return {
            if auth.isLoggedIn {
                action()
            } else {
                showLogin = true
            }
        }
    }

    private func gated<T>(_ action: @escaping (T) -> Void) -> (T) -> Void {
        return { value in
            if auth.isLoggedIn {
                action(value)
            } else {
                showLogin = true
            }
        }
    }

    /// UI 测试钩子：`-UITestChatUserId <userId>` 启动即直达聊天详情页。
    ///
    /// 会话列表依赖真实账号的后端数据，测试无法预知对方的昵称/头像，也就无法
    /// 从「点某一行会话」走进去。这里给一条直达路由，让 XCUITest 能验证
    /// 聊天详情页（共享 ChatThreadContent）的骨架确实渲染出来。
    /// 与既有的 `-UITestResetAuth`（ContentView.swift）同一套路。
    ///
    /// ⚠️ 不能用 `@State path = initialPath()` 的写法：登录态下 AuthStore 异步
    /// 加载完成会触发 `.id(auth.isLoggedIn)` 重建根内容，重建瞬间 NavigationStack
    /// 里还没有注册 chat 目的地，SwiftUI 会把无法解析的 path 值直接丢弃 → 弹回列表。
    /// 改成视图就绪后（.task，此时 navigationDestination 已注册）再推入。
    private static func initialRoute() -> MessagesRoute? {
        guard let raw = UserDefaults.standard.string(forKey: "UITestChatUserId"),
              let userId = Int64(raw) else { return nil }
        return .chat(userId: userId, peerNickname: nil, peerAvatar: nil)
    }

    var body: some View {
        NavigationStack(path: $path) {
            MessagesComposeView(
                onConversationClick: gated { userId in
                    path.append(.chat(userId: userId, peerNickname: nil, peerAvatar: nil))
                },
                onFriendsClick: gated {
                    path.append(.friends)
                },
                onCommentNotificationsClick: gated {
                    path.append(.notificationCategory(type: "comment"))
                },
                onLikeNotificationsClick: gated {
                    path.append(.notificationCategory(type: "like"))
                },
                onSystemAnnouncementsClick: gated {
                    path.append(.notificationCategory(type: "system"))
                },
                onNotificationSettingsClick: gated {
                    path.append(.notificationSettings)
                },
                onSearchClick: gated {
                    // 对齐 Android：消息页搜索 = 查找聊天记录（ChatSearchScreen，本地全局搜索），
                    // 不是歌曲搜索（旧实现错接成 SearchView）。
                    path.append(.chatSearch)
                },
                // 模态呈现（抽屉入口）时根页展示返回箭头 → 关闭模态。
                showBackButton: onClose != nil && path.isEmpty,
                onBack: { onClose?() }
            )
            // id 绑定登录态：登录成功后重建 Compose VC → 重新拉会话/通知数据
            // （MessagesContent 的数据在 VC 创建时取一次，不重建不会自动刷新）。
            .id(auth.isLoggedIn)
            .task {
                // UI 测试直达路由：等目的地注册完成后再推入（见 initialRoute 注释）
                if path.isEmpty, let route = MessagesTabView.initialRoute() {
                    path = [route]
                }
                consumePendingLink()
                consumePlayerSourceLink()
            }
            // 跨 Tab 跳转（应用内通知横幅点击）：消息 Tab 被切到前台后消费一次。
            .onChange(of: router.pendingMessagesLink) { _ in
                consumePendingLink()
            }
            // 播放列表来源跳转：已在消息页时即时消费；从别的 Tab 切过来时 .task 兜底。
            .onChange(of: router.pendingPlayerSourceLink) { _ in
                consumePlayerSourceLink()
            }
            .ignoresSafeArea(edges: [.top, .bottom])
            .navigationBarHidden(true)
            .themedPageBackground()
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
            .navigationDestination(for: MessagesRoute.self) { route in
                switch route {
                case .search(let tag):
                    SearchView(
                        onArtistClick: { _ in },
                        onAlbumClick: { id in path.append(.albumDetail(id: id)) },
                        onPostClick: { id in path.append(.postDetail(id: id)) },
                        initialTag: tag
                    )
                case .friends:
                    FriendsView(
                        onAddFriend: { path.append(.searchUser) },
                        onUserProfile: { path.append(.userProfile($0)) }
                    )
                case .searchUser:
                    SearchUserView(
                        onUserProfile: { path.append(.userProfile($0)) }
                    )
                case .addFriend(let userId, let nickname):
                    AddFriendRequestView(
                        userId: userId,
                        nickname: nickname,
                        onRequestSent: {
                            // 申请成功后把资料页的「已发送申请」状态刷新一遍：
                            // 直接退回上一页（资料页），它会在 onAppear 之外靠
                            // SwiftUI 重建 Compose VC 时重新拉一次服务端数据。
                            path.removeLast()
                        }
                    )
                case .userProfile(let args):
                    UserProfileView(
                        args: args,
                        onSendMessage: { userId, nickname, avatar in
                            path.append(.chat(userId: userId, peerNickname: nickname, peerAvatar: avatar))
                        },
                        onAddFriend: { userId, nickname in
                            path.append(.addFriend(userId: userId, nickname: nickname ?? ""))
                        },
                        onUserHome: { userId in
                            path.append(.userHome(id: userId))
                        }
                    )
                case .chat(let userId, let peerNickname, let peerAvatar):
                    ChatDetailView(
                        userId: userId,
                        peerNickname: peerNickname,
                        peerAvatar: peerAvatar,
                        onUserProfile: { userId in
                            path.append(.userProfile(UserProfileArgs(userId: userId)))
                        },
                        onSettings: { settingsUserId, nickname, avatar in
                            path.append(.chatSettings(
                                userId: settingsUserId,
                                peerNickname: nickname,
                                peerAvatar: avatar
                            ))
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
                case .chatSettings(let userId, let peerNickname, let peerAvatar):
                    ChatSettingsView(
                        userId: userId,
                        peerNickname: peerNickname,
                        peerAvatar: peerAvatar,
                        onUserProfile: {
                            path.append(.userProfile(
                                UserProfileArgs(userId: userId, nickname: peerNickname, avatar: peerAvatar)
                            ))
                        },
                        onOpenSearch: {
                            path.append(.userChatSearch(
                                userId: userId,
                                peerNickname: peerNickname,
                                peerAvatar: peerAvatar
                            ))
                        },
                        onClearedChat: {
                            // 清空并删除对话后，聊天页/信息页都失去意义，重置回会话列表
                            path = []
                        }
                    )
                case .chatSearch:
                    ChatSearchView(
                        onOpenChat: { userId, nickname, avatar in
                            path = [.chat(userId: userId, peerNickname: nickname, peerAvatar: avatar)]
                        }
                    )
                case .userChatSearch(let userId, let peerNickname, let peerAvatar):
                    UserChatSearchView(
                        peerUserId: userId,
                        peerNickname: peerNickname,
                        peerAvatar: peerAvatar,
                        onOpenChat: {
                            path = [.chat(userId: userId, peerNickname: peerNickname, peerAvatar: peerAvatar)]
                        },
                        onUserProfile: {
                            path.append(.userProfile(
                                UserProfileArgs(userId: userId, nickname: peerNickname, avatar: peerAvatar)
                            ))
                        }
                    )
                case .notificationCategory(let type):
                    NotificationCategoryView(
                        type: type,
                        onOpenPost: { postId in
                            // 消息 Tab 栈内已有 postDetail 路由，直接 push（用户主页/举报/编辑均可继续下钻）
                            path.append(.postDetail(id: postId))
                        }
                    )
                case .notificationSettings:
                    NotificationSettingsView()
                case .albumDetail(let id):
                    PlaylistDetailView(playlistId: id, isRank: false, title: "专辑详情", detailType: "album")
                case .artistDetail(let id):
                    ArtistDetailView(
                        artistId: id,
                        onAlbumClick: { albumId in path.append(.albumDetail(id: albumId)) }
                    )
                case .userHome(let id):
                    UserHomeView(
                        userId: id,
                        onChat: { userId, nickname, avatar in
                            path.append(.chat(userId: userId, peerNickname: nickname, peerAvatar: avatar))
                        },
                        onAddFriend: { userId, nickname in
                            path.append(.addFriend(userId: userId, nickname: nickname ?? ""))
                        },
                        onPlaylist: { playlistId in
                            path.append(.playlistDetail(id: playlistId, title: "歌单"))
                        },
                        onPostDetail: { postId in
                            path.append(.postDetail(id: postId))
                        },
                        onTagSearch: { tag in
                            path.append(.search(tag: tag))
                        },
                        onEditProfile: { path.append(.editProfile) },
                        onPrivacySettings: { path.append(.privacySettings) }
                    )
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
                case .playlistDetail(let id, let title):
                    PlaylistDetailView(playlistId: id, isRank: false, title: title)
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
        // 迷你条挂在 NavigationStack 外层（而不是 ContentView 的 Tab 上），这样才能感知导航栈。
        // 二级页一律不展示：Android 侧聊天页走 ChatNavGraph，压根没包 MainScreenWithTabBar
        // （showMiniPlayer 只在 Tab 根页为 true），而且聊天页底部就是输入栏 ——
        // 迷你条会直接把它盖住，导致「打不了字」。与 ProfileTabView 的处理保持一致。
        .withMiniPlayer(isEnabled: path.isEmpty)
    }
}

// MARK: - 好友

/// 我的好友页：共享层 FriendsContent 渲染（api/friends + api/friends/requests）。
struct FriendsView: View {
    @Environment(\.dismiss) private var dismiss
    let onAddFriend: () -> Void
    let onUserProfile: (UserProfileArgs) -> Void

    var body: some View {
        FriendsComposeView(
            onBack: { dismiss() },
            onAddFriendClick: onAddFriend,
            onUserProfileClick: { userId, nickname, avatar, signature, isFriend, isRequested in
                onUserProfile(
                    UserProfileArgs(
                        userId: userId,
                        nickname: nickname,
                        avatar: avatar,
                        signature: signature,
                        isFriend: isFriend,
                        isRequested: isRequested
                    )
                )
            },
            onToast: { ToastCenter.shared.show($0) }
        )
        .ignoresSafeArea(edges: [.top, .bottom])
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
    }
}

/// 把 Kotlin 侧好友页 VC 包进 SwiftUI。
struct FriendsComposeView: UIViewControllerRepresentable {
    let onBack: () -> Void
    let onAddFriendClick: () -> Void
    let onUserProfileClick: (Int64, String?, String?, String?, Bool, Bool) -> Void
    let onToast: (String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxFriendsScreen.shared.makeFriendsViewController(
            onBack: onBack,
            onAddFriendClick: onAddFriendClick,
            onUserProfileClick: { userId, nickname, avatar, signature, isFriend, isRequested in
                // KotlinLong / KotlinBoolean → Swift Int64 / Bool
                onUserProfileClick(
                    userId.int64Value,
                    nickname,
                    avatar,
                    signature,
                    isFriend.boolValue,
                    isRequested.boolValue
                )
            },
            onToast: onToast
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - 搜索用户 / 加好友

/// 搜索用户页（api/friends/search）。
struct SearchUserView: View {
    @Environment(\.dismiss) private var dismiss
    let onUserProfile: (UserProfileArgs) -> Void

    var body: some View {
        SearchUserComposeView(
            onBack: { dismiss() },
            onUserProfileClick: { userId, nickname, avatar, signature, isFriend, isRequested in
                onUserProfile(
                    UserProfileArgs(
                        userId: userId,
                        nickname: nickname,
                        avatar: avatar,
                        signature: signature,
                        isFriend: isFriend,
                        isRequested: isRequested
                    )
                )
            },
            onToast: { ToastCenter.shared.show($0) }
        )
        .ignoresSafeArea(edges: [.top, .bottom])
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
    }
}

struct SearchUserComposeView: UIViewControllerRepresentable {
    let onBack: () -> Void
    let onUserProfileClick: (Int64, String?, String?, String?, Bool, Bool) -> Void
    let onToast: (String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxSearchUserScreen.shared.makeSearchUserViewController(
            onBack: onBack,
            onUserProfileClick: { userId, nickname, avatar, signature, isFriend, isRequested in
                onUserProfileClick(
                    userId.int64Value,
                    nickname,
                    avatar,
                    signature,
                    isFriend.boolValue,
                    isRequested.boolValue
                )
            },
            onToast: onToast
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// 发送好友申请页（api/friends/request）。
struct AddFriendRequestView: View {
    @Environment(\.dismiss) private var dismiss
    let userId: Int64
    let nickname: String
    let onRequestSent: () -> Void

    var body: some View {
        AddFriendRequestComposeView(
            userId: userId,
            nickname: nickname,
            onBack: { dismiss() },
            onRequestSent: onRequestSent,
            onToast: { ToastCenter.shared.show($0) }
        )
        .ignoresSafeArea(edges: [.top, .bottom])
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
    }
}

struct AddFriendRequestComposeView: UIViewControllerRepresentable {
    let userId: Int64
    let nickname: String
    let onBack: () -> Void
    let onRequestSent: () -> Void
    let onToast: (String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxAddFriendRequestScreen.shared.makeAddFriendViewController(
            userId: userId,
            nickname: nickname,
            onBack: onBack,
            onRequestSent: onRequestSent,
            onToast: onToast
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - 用户资料

/// 用户资料页（api/users/{userId}；好友备注按需再查 api/friends）。
struct UserProfileView: View {
    @Environment(\.dismiss) private var dismiss
    let args: UserProfileArgs
    let onSendMessage: (Int64, String?, String?) -> Void
    let onAddFriend: (Int64, String?) -> Void
    let onUserHome: (Int64) -> Void

    @State private var shareItem: ShareItem?
    /// 待分享的名片（点击「分享名片」后弹二选一：站内好友 / 系统分享）。
    @State private var pendingProfileShare: PendingProfileShare?

    private struct PendingProfileShare: Identifiable {
        let id = UUID()
        let userId: Int64
        let nickname: String?
        let signature: String?
    }

    var body: some View {
        UserProfileComposeView(
            args: args,
            onBack: { dismiss() },
            onSendMessage: onSendMessage,
            onAddFriend: onAddFriend,
            onUserHome: onUserHome,
            onShare: { userId, nickname, signature in
                pendingProfileShare = PendingProfileShare(
                    userId: userId, nickname: nickname, signature: signature
                )
            },
            onToast: { ToastCenter.shared.show($0) }
        )
        .ignoresSafeArea(edges: [.top, .bottom])
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
        .sheet(item: $shareItem) { item in
            ActivityView(activityItems: [item.text])
        }
        .confirmationDialog(
            "分享名片",
            isPresented: Binding(
                get: { pendingProfileShare != nil },
                set: { if !$0 { pendingProfileShare = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("分享给好友") {
                guard let pending = pendingProfileShare else { return }
                pendingProfileShare = nil
                let share = pending
                FriendPickerPresenter.present(title: "分享给好友") { receiverId, name, avatar in
                    FoxShareSender.shared.send(
                        receiverId: receiverId,
                        shareType: "user",
                        shareId: share.userId,
                        content: share.nickname ?? "",
                        receiverNickname: name,
                        receiverAvatar: avatar,
                        onSuccess: { ToastCenter.shared.show("已分享") },
                        onError: { ToastCenter.shared.show($0) }
                    )
                }
            }
            Button("系统分享") {
                guard let pending = pendingProfileShare else { return }
                shareItem = ShareItem(text: Self.shareText(
                    userId: pending.userId,
                    nickname: pending.nickname,
                    signature: pending.signature
                ))
                pendingProfileShare = nil
            }
            Button("取消", role: .cancel) { pendingProfileShare = nil }
        }
    }

    /// 与 Android 分享名片语义对齐：一段可读的名片文案（安卓侧由 profileShareActionsViewModel 拼装）。
    static func shareText(userId: Int64, nickname: String?, signature: String?) -> String {
        var lines = ["FoxMusic 用户：\(nickname ?? "用户")"]
        if let signature, !signature.isEmpty {
            lines.append(signature)
        }
        lines.append("ID：\(userId)")
        return lines.joined(separator: "\n")
    }
}

struct UserProfileComposeView: UIViewControllerRepresentable {
    let args: UserProfileArgs
    let onBack: () -> Void
    let onSendMessage: (Int64, String?, String?) -> Void
    let onAddFriend: (Int64, String?) -> Void
    let onUserHome: (Int64) -> Void
    let onShare: (Int64, String?, String?) -> Void
    let onToast: (String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxUserProfileScreen.shared.makeUserProfileViewController(
            userId: args.userId,
            nickname: args.nickname,
            avatar: args.avatar,
            signature: args.signature,
            isFriend: args.isFriend,
            isRequested: args.isRequested,
            onBack: onBack,
            onSendMessage: { userId, peerNickname, peerAvatar in
                onSendMessage(userId.int64Value, peerNickname, peerAvatar)
            },
            onAddFriend: { userId, nickname in
                onAddFriend(userId.int64Value, nickname)
            },
            onUserHome: { userId in
                onUserHome(userId.int64Value)
            },
            onShare: { userId, nickname, signature in
                onShare(userId.int64Value, nickname, signature)
            },
            onToast: onToast
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - 聊天详情

/// 聊天详情页：共享层 ChatThreadContent 渲染。
///
/// 数据源是 Ktor REST（api/messages/history + POST api/messages），页面可见期间
/// 每 4 秒轮询 api/messages/unread 合并新消息（iOS 无本地库，见 FoxChatDetailScreen 注释）。
struct ChatDetailView: View {
    @Environment(\.dismiss) private var dismiss
    let userId: Int64
    let peerNickname: String?
    let peerAvatar: String?
    let onUserProfile: (Int64) -> Void
    let onSettings: (Int64, String?, String?) -> Void
    /// 卡片消息点击：music 时 song 非空直接起播，其余类型按 shareId 导航。
    let onOpenShare: (String, Int64, PlayableSong?) -> Void

    @State private var preview: MediaPreview?
    /// 语音播放态回写 Compose 的控制器（与本页 Compose VC 同生命周期）。
    @State private var voiceController = FoxChatVoiceController()

    var body: some View {
        ChatDetailComposeView(
            userId: userId,
            peerNickname: peerNickname,
            peerAvatar: peerAvatar,
            voiceController: voiceController,
            onBack: { dismiss() },
            onSettings: onSettings,
            onUserProfile: onUserProfile,
            onMediaPreview: { url, videoUrl, type in
                preview = MediaPreview(url: url, videoUrl: videoUrl, type: type)
            },
            onOpenShare: onOpenShare,
            onToast: { ToastCenter.shared.show($0) }
        )
        .ignoresSafeArea(edges: [.top, .bottom])
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
        .sheet(item: $preview) { item in
            MediaPreviewView(preview: item)
        }
        .onDisappear {
            // 退出聊天页：停播 + 把 Compose 气泡的播放态一并复位（resetKotlinState，
            // 否则气泡喇叭的波纹动效会一直转下去），并解绑本页的回调。
            VoiceMessagePlayer.shared.stop(resetKotlinState: true)
            VoiceMessagePlayer.shared.endHandler = nil
        }
    }
}

struct ChatDetailComposeView: UIViewControllerRepresentable {
    let userId: Int64
    let peerNickname: String?
    let peerAvatar: String?
    let voiceController: FoxChatVoiceController
    let onBack: () -> Void
    let onSettings: (Int64, String?, String?) -> Void
    let onUserProfile: (Int64) -> Void
    // 对齐 Kotlin 签名：mediaUrl / videoUrl(可空) / previewType
    let onMediaPreview: (String, String?, String) -> Void
    let onOpenShare: (String, Int64, PlayableSong?) -> Void
    let onToast: (String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let vc = FoxChatDetailScreen.shared.makeChatDetailViewController(
            userId: userId,
            peerNickname: peerNickname,
            peerAvatar: peerAvatar,
            onBack: onBack,
            onSettings: { userId, nickname, avatar in
                onSettings(userId.int64Value, nickname, avatar)
            },
            onUserProfile: { userId in
                onUserProfile(userId.int64Value)
            },
            onMediaPreview: { url, videoUrl, type in
                onMediaPreview(url, videoUrl, type)
            },
            onToast: onToast,
            onPickImage: {
                if let presenter = UIApplication.shared.topViewController {
                    ChatMediaPicker.shared.pickImage(presenter: presenter)
                }
            },
            onTakePhoto: {
                if let presenter = UIApplication.shared.topViewController {
                    ChatMediaPicker.shared.takePhoto(presenter: presenter)
                }
            },
            onCaptureVideo: {
                if let presenter = UIApplication.shared.topViewController {
                    ChatMediaPicker.shared.captureVideo(presenter: presenter)
                }
            },
            onPickFile: {
                if let presenter = UIApplication.shared.topViewController {
                    ChatMediaPicker.shared.pickFile(presenter: presenter)
                }
            },
            onVoiceStart: { ChatMediaPicker.shared.startVoiceRecording() },
            onVoiceEnd: { ChatMediaPicker.shared.endVoiceRecording() },
            onVoiceCancel: { ChatMediaPicker.shared.cancelVoiceRecording() },
            onEnterVoiceMode: { ChatMediaPicker.shared.requestMicPermissionIfNeeded() },
            onForwardMessage: { message in
                FriendPickerPresenter.present(title: "转发给") { targetId, nickname, avatar in
                    FoxMessageForwarder.shared.forward(
                        message: message,
                        targetUserId: targetId,
                        targetNickname: nickname,
                        targetAvatar: avatar,
                        onSuccess: { ToastCenter.shared.show("转发成功") },
                        onError: { ToastCenter.shared.show($0) }
                    )
                }
            },
            onOpenShare: { shareType, shareId, title, url, cover, duration, artists in
                let song: PlayableSong? = {
                    guard shareType == "music", let url, !url.isEmpty else { return nil }
                    return PlayableSong(
                        id: String(shareId.int64Value),
                        title: title ?? "未知歌曲",
                        artist: artists.joined(separator: "、"),
                        audioUrl: url,
                        coverUrl: cover,
                        durationMillis: duration.int64Value,
                        lyric: nil,
                        lyricTrans: nil,
                        commentCount: 0,
                        isFavorite: false
                    )
                }()
                onOpenShare(shareType, shareId.int64Value, song)
            },
            onPlayVoice: { [voiceController] voiceUrl, localId, _, isPlaying in
                if isPlaying.boolValue {
                    VoiceMessagePlayer.shared.endHandler = {
                        voiceController.stopPlaybackState()
                    }
                    VoiceMessagePlayer.shared.play(urlString: voiceUrl, localId: localId)
                } else {
                    VoiceMessagePlayer.shared.stop()
                }
            },
            voiceController: voiceController
        )
        return vc
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// 聊天里的图片/视频/Live 图预览。视频与 Live 的播放统一走
/// FoxVideoPlayerView（与帖子视频/Live 预览同一套 AVPlayer 封装）。
/// 行为对齐 Android PostMediaPreviewDialog：视频等用户点播，Live 打开即有声播放。
struct MediaPreview: Identifiable {
    let id = UUID()
    let url: String
    /// Live 图的实况视频地址（IMAGE/VIDEO 为 nil）
    let videoUrl: String?
    /// ChatMediaPreviewType.name：IMAGE / VIDEO / LIVE
    let type: String

    enum Kind: String { case image, video, live }
    var kind: Kind {
        switch type {
        case "VIDEO": return .video
        case "LIVE": return .live
        default: return .image
        }
    }
}

/// 与 Android / Compose 侧 `PostLiveBadge` 同款的 Live 徽章（同心圆环 + 圆心点）。
/// 未播放时叠一条斜线（对齐 Android PostLivePreviewBadge：斜线 = 当前是静帧，可点播）。
/// 工程里没有对应图片资源，这里用 SwiftUI 图形画，避免退化成「LIVE」文字。
struct LiveBadgeView: View {
    let showSlash: Bool

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.white, lineWidth: 1.6)
            Circle()
                .stroke(Color.white, lineWidth: 1.6)
                .padding(4.5)
            Circle()
                .fill(Color.white)
                .frame(width: 4, height: 4)
            if showSlash {
                Capsule()
                    .fill(Color.white)
                    .frame(width: 22, height: 2.2)
                    .rotationEffect(.degrees(-45))
            }
        }
        .frame(width: 24, height: 24)
    }
}

struct MediaPreviewView: View {
    let preview: MediaPreview
    @Environment(\.dismiss) private var dismiss
    @State private var playing = false
    @State private var muted = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            mediaContent
                .onTapGesture {
                    guard preview.kind != .image else { return }
                    playing.toggle()
                }
            VStack {
                HStack(spacing: 4) {
                    Spacer()
                    if preview.kind == .live {
                        // Live：顶栏挂 LIVE 徽章（播完带斜线，点它重播），
                        // 不再用「中央大播放键」——那是视频的待播样式。
                        Button {
                            playing = true
                        } label: {
                            LiveBadgeView(showSlash: !playing)
                                .frame(width: 40, height: 40)
                        }
                        .accessibilityLabel("LIVE")
                        .accessibilityIdentifier("chatPreview.liveBadge")
                    }
                    if preview.kind != .image {
                        Button {
                            muted.toggle()
                        } label: {
                            Image(systemName: muted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(width: 40, height: 40)
                        }
                        .accessibilityLabel(muted ? "取消静音" : "静音")
                        // ⚠️ 开关类控件的 label 会随状态翻转（静音 ⇄ 取消静音），
                        // UI 测试若用 `label == "静音"` 的 firstMatch 缓存元素，切换后再 tap 必然
                        // 「No matches found」。identifier 恒定，切两次都用它。
                        .accessibilityIdentifier("chatPreview.mute")
                    }
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 40, height: 40)
                    }
                    .accessibilityLabel("关闭预览")
                    .accessibilityIdentifier("chatPreview.close")
                }
                .padding(.horizontal, 12)
                Spacer()
            }
            if preview.kind == .video && !playing {
                // 视频：待点播（Live 的重播入口在顶栏的 LIVE 徽章上）
                Button {
                    playing = true
                } label: {
                    Image(systemName: "play.circle.fill")
                        .font(.system(size: 64))
                        .foregroundColor(.white.opacity(0.9))
                }
                .accessibilityLabel("播放")
                // ⚠️ 唯一的 accessibilityIdentifier 是 UI 测试判定「待播 / 播放中」的**唯一可靠依据**。
                // 不能只用 label == "播放"：预览弹出后底层聊天页的气泡角标也是「播放」，
                // 且气泡（180×180）的圆心距窗口中心仅约 50pt —— 任何"取最靠近窗口中心的
                // 播放按钮"的几何启发式都会命中**底层气泡**（它永不消失），
                // 导致「播放态 = 中央按钮消失」恒为假（test32 曾因此假失败 103s）。
                .accessibilityIdentifier("chatPreview.centerPlay")
            }
            // 播放态探针：供 UI 测试读取「当前是否真的在播」的正向证据
            //（1×1 不可见、不可命中、不参与视觉），避免只能靠"按钮消失"这种反证。
            Color.clear
                .frame(width: 2, height: 2)
                .accessibilityElement()
                .accessibilityIdentifier("chatPreview.probe")
                .accessibilityLabel(preview.kind.rawValue)
                .accessibilityValue(playing ? "playing" : "paused")
                .allowsHitTesting(false)
        }
        .onAppear {
            // 对齐 Android：Live 打开即自动有声播放；视频等待用户点击
            if preview.kind == .live { playing = true }
        }
    }

    @ViewBuilder
    private var mediaContent: some View {
        switch preview.kind {
        case .image:
            if let url = URL(string: preview.url) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFit()
                    case .failure:
                        Text("图片加载失败").foregroundColor(.white)
                    default:
                        ProgressView().tint(.white)
                    }
                }
            } else {
                Text("无法解析的媒体地址").foregroundColor(.white)
            }
        case .video:
            if let url = MediaUrlResolver.shared.resolve(url: preview.url).flatMap({ URL(string: $0) }) {
                FoxVideoPlayerView(
                    url: url,
                    autoPlay: playing,
                    muted: muted,
                    looping: false,
                    onCompletion: { playing = false }
                )
                .ignoresSafeArea()
            } else {
                Text("无法解析的媒体地址").foregroundColor(.white)
            }
        case .live:
            ZStack {
                if let still = URL(string: preview.url) {
                    AsyncImage(url: still) { phase in
                        switch phase {
                        case .success(let image):
                            image.resizable().scaledToFit()
                        default:
                            Color.black
                        }
                    }
                    .ignoresSafeArea()
                }
                if playing,
                   let videoUrl = preview.videoUrl
                       .flatMap({ MediaUrlResolver.shared.resolve(url: $0) })
                       .flatMap({ URL(string: $0) }) {
                    FoxVideoPlayerView(
                        url: videoUrl,
                        autoPlay: true,
                        muted: muted,
                        looping: false,
                        onCompletion: { playing = false },
                        transparentBackground: true
                    )
                    .ignoresSafeArea()
                }
            }
        }
    }
}

// MARK: - 通知分类

/// 通知分类页（评论和@ / 赞和通知 / 系统公告）：共享层 NotificationCategoryContent 渲染。
struct NotificationCategoryView: View {
    @Environment(\.dismiss) private var dismiss
    let type: String
    let onOpenPost: (Int64) -> Void

    var body: some View {
        NotificationCategoryComposeView(
            type: type,
            onBack: { dismiss() },
            onOpenPost: onOpenPost,
            onToast: { ToastCenter.shared.show($0) }
        )
        .ignoresSafeArea(edges: [.top, .bottom])
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
    }
}

struct NotificationCategoryComposeView: UIViewControllerRepresentable {
    let type: String
    let onBack: () -> Void
    let onOpenPost: (Int64) -> Void
    let onToast: (String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxNotificationCategoryScreen.shared.makeNotificationCategoryViewController(
            type: type,
            onBack: onBack,
            onOpenPost: { postId in
                onOpenPost(postId.int64Value)
            },
            onOpenMusicComment: { musicId, commentId, rootCommentId in
                // 拉歌曲详情 → 起播 → 打开全屏播放器并自动展开评论面板定位楼中楼
                PlayerUICoordinator.shared.openMusicComment(
                    musicId: musicId.int64Value,
                    commentId: commentId.int64Value,
                    rootCommentId: rootCommentId.int64Value
                )
            },
            onToast: onToast
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - 系统分享

/// 系统分享面板的 SwiftUI 包装（项目里此前没有，用户资料页分享名片要用）。
struct ActivityView: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

/// Identifiable 包装，供 `.sheet(item:)` 使用。
struct ShareItem: Identifiable {
    let id = UUID()
    let text: String
}

// MARK: - 把 Kotlin 侧消息页 VC 包进 SwiftUI

/// 滚动/数据状态留在 Kotlin/Compose 内部，updateUIViewController 留空避免重建丢位置。
struct MessagesComposeView: UIViewControllerRepresentable {
    let onConversationClick: (Int64) -> Void
    let onFriendsClick: () -> Void
    let onCommentNotificationsClick: () -> Void
    let onLikeNotificationsClick: () -> Void
    let onSystemAnnouncementsClick: () -> Void
    let onNotificationSettingsClick: () -> Void
    let onSearchClick: () -> Void
    /// 模态呈现时顶部栏展示返回箭头。
    let showBackButton: Bool
    let onBack: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxMessagesScreen.shared.makeMessagesViewController(
            // KotlinLong → Swift Int64 桥接（与 CommunityComposeView 同一处理）
            onConversationClick: { onConversationClick($0.int64Value) },
            onFriendsClick: onFriendsClick,
            onCommentNotificationsClick: onCommentNotificationsClick,
            onLikeNotificationsClick: onLikeNotificationsClick,
            onSystemAnnouncementsClick: onSystemAnnouncementsClick,
            onNotificationSettingsClick: onNotificationSettingsClick,
            onSearchClick: onSearchClick,
            showBackButton: showBackButton,
            onBack: onBack
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
