import SwiftUI
import FoxShared

// MARK: - 选择好友（分享/转发）

/// 选人结果：userId + 展示名 + 头像。
typealias FriendPickHandler = (Int64, String, String?) -> Void

/// 全局选人弹窗入口：歌曲菜单/资料页/聊天转发等任意位置都可直接弹出，
/// 不必层层透传 sheet 状态（内部用 UIHostingController 盖在最顶层 VC 上）。
enum FriendPickerPresenter {

    static func present(title: String, onPicked: @escaping FriendPickHandler) {
        guard AuthStore.shared.isLoggedIn else {
            ToastCenter.shared.show("请先登录")
            return
        }
        // 注意：不要在外面套 UINavigationController + SwiftUI .toolbar/.searchable。
        // 实测该组合在 UIKit 模态 UIHostingController 里，searchable 的搜索容器会
        // 截获整页触摸（取消/好友行都点不动），环境 dismiss 也失效。
        // 这里直接用裸 UIHostingController，头部与搜索框全部自绘，关闭走显式闭包。
        let sheet = FriendPickerSheet(title: title) {
            weakHost?.dismiss(animated: true)
        } onPicked: { id, name, avatar in
            weakHost?.dismiss(animated: true)
            onPicked(id, name, avatar)
        }
        let host = UIHostingController(rootView: sheet)
        weakHost = host
        host.modalPresentationStyle = .pageSheet
        if let sheetCtrl = host.sheetPresentationController {
            sheetCtrl.detents = [.large()]
            sheetCtrl.prefersGrabberVisible = true
            sheetCtrl.preferredCornerRadius = 16
        }
        // 若顶层是 UIAlert（系统分享动作表点进来的链式场景），等它先消失再 present，
        // 否则会盖在 alert 上、alert 关闭后弹窗被孤立。
        guard var presenter = UIApplication.shared.topViewController else { return }
        if presenter is UIAlertController {
            let alert = presenter
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                let p = UIApplication.shared.topViewController ?? alert.presentingViewController
                p?.present(host, animated: true)
            }
        } else {
            presenter.present(host, animated: true)
        }
    }

    private static weak var weakHost: UIViewController?
}

/// 全局「分享给好友 / 系统分享」动作表：帖子详情、广场等任意位置共用，
/// 不必在每个 View 里重复 confirmationDialog 状态。
enum ShareActionPresenter {

    static func present(
        shareType: String,
        shareId: Int64,
        displayTitle: String,
        systemText: String,
    ) {
        let alert = UIAlertController(title: displayTitle, message: nil, preferredStyle: .actionSheet)
        alert.addAction(UIAlertAction(title: "分享给好友", style: .default) { _ in
            // alert 动作回调时 alert 仍在顶层，延迟到它消失后再弹选人页
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                FriendPickerPresenter.present(title: "分享给好友") { receiverId, name, avatar in
                    FoxShareSender.shared.send(
                        receiverId: receiverId,
                        shareType: shareType,
                        shareId: shareId,
                        content: displayTitle,
                        receiverNickname: name,
                        receiverAvatar: avatar,
                        onSuccess: { ToastCenter.shared.show("已分享") },
                        onError: { ToastCenter.shared.show($0) }
                    )
                }
            }
        })
        alert.addAction(UIAlertAction(title: "系统分享", style: .default) { _ in
            let av = UIActivityViewController(activityItems: [systemText], applicationActivities: nil)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                UIApplication.shared.topViewController?.present(av, animated: true)
            }
        })
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))

        // 从 Compose Multiplatform 的 ModalBottomSheet（详情「更多」/卡片面板）点分享时，
        // 回调里先 showMoreSheet=false 再立即触发本方法：sheet 的 dismiss 动画还在进行，
        // 此时在顶层（正在被 dismiss 的 sheet 宿主）上 present 会被 UIKit 静默丢弃
        // （表现为「点分享没反应」）。延迟到 dismiss 落地后重新解析顶层 VC，
        // 并对「仍在转场」的情况做有限次重试。
        presentWhenReady(alert, attempt: 0)
    }

    private static func presentWhenReady(_ alert: UIAlertController, attempt: Int) {
        DispatchQueue.main.asyncAfter(deadline: .now() + (attempt == 0 ? 0.35 : 0.15)) {
            guard let presenter = UIApplication.shared.topViewController,
                  presenter.presentedViewController == nil,
                  !presenter.isBeingDismissed,
                  presenter.view.window != nil else {
                if attempt < 6 { presentWhenReady(alert, attempt: attempt + 1) }
                return
            }
            if let pop = alert.popoverPresentationController {
                pop.sourceView = presenter.view
                pop.sourceRect = CGRect(x: presenter.view.bounds.midX, y: presenter.view.bounds.midY, width: 0, height: 0)
                pop.permittedArrowDirections = []
            }
            presenter.present(alert, animated: true)
        }
    }
}

/// 选择好友页 —— 对应 Android `SelectFriendScreen`（分享名片/转发消息共用）。
/// 数据走 api/friends；关键词为本地昵称过滤（与安卓仅展示好友列表的口径一致）。
///
/// 自绘头部与搜索框：本页由 [FriendPickerPresenter] 以裸 UIHostingController 模态弹出，
/// 不依赖 SwiftUI NavigationStack / .toolbar / .searchable（见 Presenter 注释里的坑）。
struct FriendPickerSheet: View {

    let title: String
    let onClose: () -> Void
    let onPicked: FriendPickHandler

    @State private var friends: [FoxPickFriend] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var query = ""

    private var filtered: [FoxPickFriend] {
        let keyword = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !keyword.isEmpty else { return friends }
        return friends.filter { $0.name.localizedCaseInsensitiveContains(keyword) }
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            searchBar
            Divider()
            content
        }
        .background(Color(uiColor: .systemBackground))
        .task { load() }
    }

    // MARK: 头部（取消 / 居中标题）

    private var header: some View {
        ZStack {
            Text(title)
                .font(.headline)
                .foregroundStyle(.primary)
            HStack {
                Button(action: onClose) {
                    Text("取消")
                        .font(.body)
                        .foregroundStyle(Color.accentColor)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(Color.primary.opacity(0.08), in: Capsule())
                }
                .accessibilityIdentifier("selectFriend.cancel")
                Spacer()
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 10)
    }

    // MARK: 搜索框（替代系统 .searchable）

    private var searchBar: some View {
        HStack(spacing: 6) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 14))
                .foregroundStyle(.secondary)
            TextField("搜索好友", text: $query)
                .font(.subheadline)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .accessibilityIdentifier("selectFriend.search")
            if !query.isEmpty {
                Button {
                    query = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 15))
                        .foregroundStyle(.secondary)
                }
                .accessibilityIdentifier("selectFriend.clearSearch")
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .padding(.horizontal, 16)
        .padding(.bottom, 10)
    }

    // MARK: 内容

    @ViewBuilder
    private var content: some View {
        if isLoading {
            Spacer()
            ProgressView()
            Spacer()
        } else if let loadError, friends.isEmpty {
            Spacer()
            VStack(spacing: 12) {
                Text(loadError).font(.footnote).foregroundStyle(.secondary)
                Button("重试") { load() }
            }
            Spacer()
        } else if filtered.isEmpty {
            Spacer()
            Text(friends.isEmpty ? "还没有好友，先去添加好友吧" : "未找到匹配的好友")
                .font(.callout)
                .foregroundStyle(.secondary)
            Spacer()
        } else {
            List {
                ForEach(filtered, id: \.id) { friend in
                    Button {
                        onPicked(friend.id, friend.name, friend.avatar)
                    } label: {
                        HStack(spacing: 12) {
                            ChatPeerAvatar(url: friend.avatar, size: 42)
                            Text(friend.name)
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(.primary)
                                .lineLimit(1)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.tertiary)
                        }
                        .padding(.vertical, 4)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    .listRowSeparator(.hidden)
                    .accessibilityIdentifier("selectFriend.row.\(friend.id)")
                }
            }
            .listStyle(.plain)
            .accessibilityIdentifier("selectFriend.list")
        }
    }

    private func load() {
        isLoading = true
        loadError = nil
        FoxFriendPicker.shared.loadFriends(
            onSuccess: { list in
                friends = list
                isLoading = false
            },
            onError: { message in
                loadError = message
                isLoading = false
            }
        )
    }
}
