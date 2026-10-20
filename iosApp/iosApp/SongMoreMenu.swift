import SwiftUI
import FoxShared

// MARK: - 自定义底部弹窗容器
//
// 根因说明：iOS 26 起，系统对自定义 detent（.height/.medium）的 sheet 采用
// 悬浮卡片样式，左右与底部留白由 UISheetPresentationController 画在 sheet 自身
// 几何上，没有公开 API 去除（清 layoutMargins 无效）。Compose ModalBottomSheet
// 画在 Compose 画布内不走 UIKit presentation，所以没有留白。
//
// 因此这里用标准 UIKit 自定义 presentation 自己掌控卡片形状：
// 贴底全宽卡片 + 圆角 + 蒙层点击关闭 + 下滑手势关闭，高度按 SwiftUI 内容实测自适应。

@MainActor
enum BottomSheet {

    /// 弹出底部卡片（内容高度自适应，最高 90% 屏高）。
    static func present(_ content: some View, onDismiss: @escaping () -> Void = {}) {
        guard let top = topmost() else { onDismiss(); return }
        let container = BottomSheetContainer(rootView: AnyView(content), onDismiss: onDismiss)
        container.modalPresentationStyle = .custom
        container.transitioningDelegate = TransitionDelegate.shared
        top.present(container, animated: true)
    }

    /// 关闭最上层卡片。
    static func dismissTop() {
        topmost()?.dismiss(animated: true)
    }

    /// 关闭整条弹窗链（如"添加到歌单"成功后连歌曲菜单一起关闭）。
    static func dismissAll() {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let root = scene.keyWindow?.rootViewController,
              let first = root.presentedViewController else { return }
        first.dismiss(animated: true)
    }

    private static func topmost() -> UIViewController? {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let root = scene.keyWindow?.rootViewController else { return nil }
        var top = root
        while let next = top.presentedViewController { top = next }
        return top
    }
}

/// 承载 SwiftUI 内容的贴底卡片容器。
final class BottomSheetContainer: UIViewController {
    private let rootView: AnyView
    private let onDismiss: () -> Void
    private var host: UIHostingController<AnyView>?
    private var isClosing = false
    fileprivate weak var dimmingView: UIControl?

    /// 卡片高度（含底部安全区），由内容实测得出。
    private(set) var sheetHeight: CGFloat = 320

    init(rootView: AnyView, onDismiss: @escaping () -> Void) {
        self.rootView = rootView
        self.onDismiss = onDismiss
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        view.layer.cornerRadius = 20
        view.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
        view.clipsToBounds = true

        let host = UIHostingController(rootView: rootView)
        host.view.backgroundColor = .clear
        host.sizingOptions = [.preferredContentSize]
        addChild(host)
        view.addSubview(host.view)
        host.didMove(toParent: self)
        self.host = host

        measure()
        view.addGestureRecognizer(UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:))))
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        host?.view.frame = view.bounds
        // 底部安全区（Home 指示条）：交给 SwiftUI 自动避让
        let safe = presentingViewController?.view.safeAreaInsets.bottom ?? 0
        host?.additionalSafeAreaInsets.bottom = max(0, safe - view.safeAreaInsets.bottom)
    }

    /// 内容变化（如歌单异步加载完成）时跟随调整卡片高度。
    override func preferredContentSizeDidChange(forChildContentContainer container: UIContentContainer) {
        super.preferredContentSizeDidChange(forChildContentContainer: container)
        applyHeight(animated: true)
    }

    private func measure() {
        let width = view.window?.bounds.width ?? UIScreen.main.bounds.width
        let ideal = host?.sizeThatFits(in: CGSize(width: width, height: .greatestFiniteMagnitude)).height ?? 320
        let safe = presentingViewController?.view.safeAreaInsets.bottom ?? 0
        let maxH = (view.window?.bounds.height ?? 800) * 0.9
        sheetHeight = min(ideal + safe, maxH)
    }

    private func applyHeight(animated: Bool) {
        let old = sheetHeight
        measure()
        guard abs(old - sheetHeight) > 0.5, let container = presentationController?.containerView else { return }
        let target = CGRect(x: 0, y: container.bounds.height - sheetHeight,
                            width: container.bounds.width, height: sheetHeight)
        let apply = { self.view.frame = target }
        animated ? UIView.animate(withDuration: 0.25, animations: apply) : apply()
    }

    // MARK: 下滑关闭

    @objc private func handlePan(_ g: UIPanGestureRecognizer) {
        let t = g.translation(in: view).y
        switch g.state {
        case .changed:
            view.transform = CGAffineTransform(translationX: 0, y: max(0, t))
        case .ended, .cancelled:
            let v = g.velocity(in: view).y
            if t > 150 || v > 1000 {
                close()
            } else {
                UIView.animate(withDuration: 0.25, delay: 0, usingSpringWithDamping: 1,
                               initialSpringVelocity: 0, options: [.beginFromCurrentState]) {
                    self.view.transform = .identity
                }
            }
        default:
            break
        }
    }

    fileprivate func close() {
        guard !isClosing else { return }
        isClosing = true
        UIView.animate(withDuration: 0.22, delay: 0, options: [.curveEaseIn, .beginFromCurrentState]) {
            self.view.transform = CGAffineTransform(translationX: 0, y: self.view.bounds.height + 40)
            self.dimmingView?.alpha = 0
        } completion: { _ in
            self.dismiss(animated: false)
        }
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        onDismiss()
    }
}

// MARK: - 过渡与呈现

private final class TransitionDelegate: NSObject, UIViewControllerTransitioningDelegate {
    static let shared = TransitionDelegate()

    func presentationController(forPresented presented: UIViewController,
                                presenting: UIViewController?,
                                source: UIViewController) -> UIPresentationController? {
        PresentationController(presentedViewController: presented, presenting: presenting)
    }
    func animationController(forPresented presented: UIViewController,
                             presenting: UIViewController,
                             source: UIViewController) -> UIViewControllerAnimatedTransitioning? {
        Animator(appearing: true)
    }
    func animationController(forDismissed dismissed: UIViewController) -> UIViewControllerAnimatedTransitioning? {
        Animator(appearing: false)
    }
}

private final class PresentationController: UIPresentationController {
    let dimming = UIControl()

    override init(presentedViewController: UIViewController, presenting presentingViewController: UIViewController?) {
        super.init(presentedViewController: presentedViewController, presenting: presentingViewController)
        dimming.backgroundColor = UIColor.black.withAlphaComponent(0.4)
        dimming.alpha = 0
        dimming.addTarget(self, action: #selector(dimTapped), for: .touchUpInside)
    }

    @objc private func dimTapped() {
        presentedViewController.dismiss(animated: true)
    }

    override var frameOfPresentedViewInContainerView: CGRect {
        guard let container = containerView else { return .zero }
        let h = (presentedViewController as? BottomSheetContainer)?.sheetHeight ?? 320
        return CGRect(x: 0, y: container.bounds.height - h,
                      width: container.bounds.width, height: h)
    }

    override func presentationTransitionWillBegin() {
        guard let container = containerView else { return }
        container.addSubview(dimming)
        dimming.frame = container.bounds
        (presentedViewController as? BottomSheetContainer)?.dimmingView = dimming
        presentingViewController.transitionCoordinator?.animate(alongsideTransition: { _ in
            self.dimming.alpha = 1
        })
    }

    override func dismissalTransitionWillBegin() {
        presentingViewController.transitionCoordinator?.animate(alongsideTransition: { _ in
            self.dimming.alpha = 0
        })
    }

    override func containerViewWillLayoutSubviews() {
        super.containerViewWillLayoutSubviews()
        presentedView?.frame = frameOfPresentedViewInContainerView
        dimming.frame = containerView?.bounds ?? .zero
    }
}

private final class Animator: NSObject, UIViewControllerAnimatedTransitioning {
    let appearing: Bool
    init(appearing: Bool) { self.appearing = appearing }

    func transitionDuration(using ctx: UIViewControllerContextTransitioning?) -> TimeInterval { 0.32 }

    func animateTransition(using ctx: UIViewControllerContextTransitioning) {
        guard let to = ctx.viewController(forKey: .to),
              let from = ctx.viewController(forKey: .from) else {
            ctx.completeTransition(true)
            return
        }
        let container = ctx.containerView
        let duration = transitionDuration(using: ctx)

        if appearing {
            container.addSubview(to.view)
            let h = (to as? BottomSheetContainer)?.sheetHeight ?? to.view.frame.height
            let final = CGRect(x: 0, y: container.bounds.height - h,
                               width: container.bounds.width, height: h)
            to.view.frame = final
            to.view.transform = CGAffineTransform(translationX: 0, y: container.bounds.height - final.origin.y)
            UIView.animate(withDuration: duration, delay: 0, options: [.curveEaseOut, .beginFromCurrentState]) {
                to.view.transform = .identity
            } completion: { _ in ctx.completeTransition(true) }
        } else {
            let drop = container.bounds.height - from.view.frame.origin.y + 40
            UIView.animate(withDuration: duration, delay: 0, options: [.curveEaseIn, .beginFromCurrentState]) {
                from.view.transform = CGAffineTransform(translationX: 0, y: drop)
            } completion: { _ in ctx.completeTransition(true) }
        }
    }
}

// MARK: - SwiftUI 调用入口

/// 以贴底卡片呈现（替代系统 .sheet + presentationDetents）。
struct BottomSheetModifier<Sheet: View>: ViewModifier {
    @Binding var isPresented: Bool
    @ViewBuilder let sheet: () -> Sheet

    func body(content: Content) -> some View {
        content.onChange(of: isPresented) { showing in
            guard showing else { return }
            // 注意：onChange 用 iOS16 单参数签名；present 不用尾闭包形式（swift-frontend 断言崩溃）
            let sheetView = sheet()
            BottomSheet.present(sheetView, onDismiss: { isPresented = false })
        }
    }
}

struct BottomSheetItemModifier<Item: Identifiable, Sheet: View>: ViewModifier {
    @Binding var item: Item?
    @ViewBuilder let sheet: (Item) -> Sheet

    func body(content: Content) -> some View {
        content.onChange(of: item?.id) { showing in
            guard showing != nil, let current = item else { return }
            let sheetView = sheet(current)
            BottomSheet.present(sheetView, onDismiss: { item = nil })
        }
    }
}

extension View {
    /// 与 Compose ModalBottomSheet 一致的贴底弹窗。
    func bottomSheet(isPresented: Binding<Bool>, @ViewBuilder sheet: @escaping () -> some View) -> some View {
        modifier(BottomSheetModifier(isPresented: isPresented, sheet: sheet))
    }

    func bottomSheet<Item: Identifiable>(item: Binding<Item?>, @ViewBuilder sheet: @escaping (Item) -> some View) -> some View {
        modifier(BottomSheetItemModifier(item: item, sheet: sheet))
    }
}

// MARK: - 统一弹窗组件
//
// 所有底部弹窗共用这套组件，与 Compose ModalBottomSheet（歌单更多菜单）同风格：
// 顶部拖动条 + 左对齐标题 + 图标/标题/副标题行，无关闭按钮（点击蒙层或下滑关闭）。

/// 底部弹窗头部：拖动条 + 标题。
struct SheetHeader: View {
    let title: String

    var body: some View {
        VStack(spacing: 0) {
            Capsule()
                .fill(Color.secondary.opacity(0.3))
                .frame(width: 36, height: 4)
                .padding(.top, 8)
            HStack {
                Text(title)
                    .font(.system(size: 17, weight: .semibold))
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .padding(.top, 10)
            .padding(.bottom, 6)
        }
    }
}

/// 底部弹窗单行：图标 + 标题 + 可选副标题 + 右侧可选内容。
struct SheetRow: View {
    let icon: String
    let title: String
    var subtitle: String? = nil
    var isDestructive: Bool = false
    var rightView: AnyView? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 18))
                    .foregroundStyle(isDestructive ? Color.red : Color.accentColor)
                    .frame(width: 24)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 16))
                        .foregroundStyle(isDestructive ? Color.red : Color.primary)
                    if let subtitle {
                        Text(subtitle)
                            .font(.system(size: 13))
                            .foregroundStyle(.secondary)
                    }
                }

                Spacer(minLength: 0)

                rightView
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 11)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - 歌曲更多菜单

/// 歌曲更多操作菜单：下一首播放 / 添加到歌单 / 分享 / 下载 / 举报。
struct SongMoreMenu: View {
    let musicId: String
    let title: String
    let artist: String
    let coverUrl: String?
    let sourceUrl: String?

    var body: some View {
        VStack(spacing: 0) {
            SheetHeader(title: title)

            SheetRow(icon: "text.line.last.and.arrowtriangle.forward",
                     title: "下一首播放", subtitle: "插到当前歌曲之后播放") {
                BottomSheet.dismissTop()
                insertAsNext()
            }
            SheetRow(icon: "plus.circle", title: "添加到歌单", subtitle: "收藏到我的歌单") {
                BottomSheet.present(PlaylistPickerView(musicId: musicId))
            }
            SheetRow(icon: "square.and.arrow.up", title: "分享给好友", subtitle: "以卡片发送给 FoxMusic 好友") {
                shareToFriend()
            }
            SheetRow(icon: "ellipsis.circle", title: "系统分享", subtitle: "拷贝链接 / 更多系统渠道") {
                BottomSheet.dismissTop()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                    shareSong()
                }
            }
            SheetRow(icon: "arrow.down.circle", title: "下载", subtitle: "保存到本地") {
                if let url = sourceUrl, !url.isEmpty {
                    MusicDownloadManager.shared.startDownload(
                        musicId: musicId, title: title, artist: artist,
                        coverUrl: coverUrl, sourceUrl: url
                    )
                    ToastCenter.shared.show("已加入下载队列")
                    BottomSheet.dismissTop()
                } else {
                    ToastCenter.shared.show("该歌曲暂无下载地址")
                }
            }
            SheetRow(icon: "flag", title: "举报", subtitle: "提交举报", isDestructive: true) {
                if let id = Int64(musicId) {
                    BottomSheet.present(
                        ReportSheet(targetType: "music", targetId: id, targetName: title, onSubmitted: {})
                    )
                }
            }
            .padding(.bottom, 8)
        }
        .frame(maxWidth: .infinity, alignment: .top)
    }

    private func buildPlayable() -> PlayableSong? {
        // 列表/歌单传入的 sourceUrl 可能是 /uploads/... 相对路径，需拼上 MEDIA_BASE_URL
        guard let resolved = sourceUrl.flatMap({ MediaUrlResolver.shared.resolve(url: $0) }) else {
            return nil
        }
        return PlayableSong(
            id: musicId,
            title: title,
            artist: artist,
            audioUrl: resolved,
            coverUrl: coverUrl,
            durationMillis: 0,
            lyric: nil,
            lyricTrans: nil,
            commentCount: 0,
            isFavorite: false
        )
    }

    /// 下一首播放：列表数据带播放地址时立即入队；
    /// 地址缺失（如第三方导入歌单、管理库条目）时拉歌曲详情补全后再入队
    /// —— Android 队列直接存 Music 实体、播放时才解析地址，所以不会丢歌，
    /// iOS 这边必须保证入队时有可用 audioUrl。
    private func insertAsNext() {
        if let song = buildPlayable() {
            PlayerModel.shared.insertNext(song)
            ToastCenter.shared.show("已添加到下一首播放")
            return
        }
        guard let id = Int64(musicId) else {
            ToastCenter.shared.show("该歌曲暂不支持播放")
            return
        }
        ToastCenter.shared.show("正在准备播放…")
        FoxIosBridge.shared.fetchPlayableSong(
            musicId: id,
            onResult: { song in
                DispatchQueue.main.async {
                    PlayerModel.shared.insertNext(song)
                    ToastCenter.shared.show("已添加到下一首播放")
                }
            },
            onError: { message in
                DispatchQueue.main.async { ToastCenter.shared.show(message) }
            }
        )
    }

    /// 站内分享：选人 → POST api/messages/share（music 卡片，对齐 Android）。
    private func shareToFriend() {
        guard let id = Int64(musicId) else {
            ToastCenter.shared.show("歌曲信息异常，无法分享")
            return
        }
        BottomSheet.dismissTop()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
            FriendPickerPresenter.present(title: "分享给好友") { receiverId, name, avatar in
                FoxShareSender.shared.send(
                    receiverId: receiverId,
                    shareType: "music",
                    shareId: id,
                    content: "\(title) - \(artist)",
                    receiverNickname: name,
                    receiverAvatar: avatar,
                    onSuccess: { ToastCenter.shared.show("已分享") },
                    onError: { ToastCenter.shared.show($0) }
                )
            }
        }
    }

    private func shareSong() {
        let text = "分享歌曲：\(title) - \(artist)"
        let av = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        if let presenter = UIApplication.shared.topViewController {
            presenter.present(av, animated: true)
        }
    }
}

/// 歌单选择器：从我的歌单里选一个加入歌曲，高度随歌单数量自适应。
struct PlaylistPickerView: View {
    let musicId: String
    var onSelected: ((Int64) -> Void)? = nil

    @State private var playlists: [PlaylistDto] = []
    @State private var loading = true

    var body: some View {
        VStack(spacing: 0) {
            SheetHeader(title: "添加到歌单")

            if loading {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
            } else if playlists.isEmpty {
                Text("暂无歌单")
                    .font(.system(size: 15))
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
                    .padding(.bottom, 8)
            } else {
                playlistRows
                    .padding(.bottom, 8)
            }
        }
        .frame(maxWidth: .infinity, alignment: .top)
        .onAppear { loadPlaylists() }
    }

    /// 数量少直接铺开（容器按内容自适应高度）；超过 8 个才滚动。
    @ViewBuilder
    private var playlistRows: some View {
        let rows = VStack(spacing: 0) {
            ForEach(playlists, id: \.id) { pl in
                Button {
                    addToPlaylist(playlistId: pl.id)
                } label: {
                    HStack {
                        Text(pl.title)
                            .font(.system(size: 16))
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                        Spacer(minLength: 0)
                        Text("\(pl.trackCount) 首")
                            .font(.system(size: 13))
                            .foregroundStyle(.secondary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        if playlists.count > 8 {
            ScrollView { rows }
        } else {
            rows
        }
    }

    private func loadPlaylists() {
        FoxMusicApi().fetchUserPlaylists(userId: nil) { list, _ in
            DispatchQueue.main.async {
                playlists = (list as? [PlaylistDto]) ?? []
                loading = false
            }
        }
    }

    private func addToPlaylist(playlistId: Int64) {
        guard let mid = Int64(musicId) else { return }
        FoxMusicApi().addMusicToPlaylist(playlistId: playlistId, musicId: mid) { _, error in
            DispatchQueue.main.async {
                if let error {
                    ToastCenter.shared.show(error.localizedDescription)
                } else {
                    ToastCenter.shared.show("已添加到歌单")
                    onSelected?(playlistId)
                    BottomSheet.dismissAll()
                }
            }
        }
    }
}
