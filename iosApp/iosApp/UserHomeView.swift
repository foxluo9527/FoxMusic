import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import FoxShared

// MARK: - 个人主页

/// 个人主页：共享层 FoxUserHomeScreen（对齐 Android UserHomeScreen）渲染 ——
/// 顶部背景 + 头像资料、歌单/动态两个 Tab、他人主页的关系底栏、
/// 更多菜单（分享名片 / 换背景 / 编辑资料 / 隐私 / 举报 / 拉黑）。
///
/// 三个 Tab（消息 / 社区 / 我的）都会推这个页，但各自持有自己的 NavigationStack，
/// 所以页面内所有跳转都以闭包注入，由各 Tab 决定往哪个路由 append。
struct UserHomeView: View {
    let userId: Int64
    let onChat: (Int64, String?, String?) -> Void
    let onAddFriend: (Int64, String?) -> Void
    let onPlaylist: (Int64) -> Void
    let onPostDetail: (Int64) -> Void
    /// 动态列表帖子标签点击 → 宿主推标签搜索（默认不处理，由各 Tab 注入）
    var onTagSearch: (String) -> Void = { _ in }
    let onEditProfile: () -> Void
    let onPrivacySettings: () -> Void

    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var auth = AuthStore.shared

    @State private var pickerPresented = false
    @State private var shareItem: ShareItem?
    @State private var reportTarget: ReportTarget?
    /// 待站内/系统分享的目标（名片 user / 动态 post）。
    @State private var pendingShare: PendingShare?

    private struct PendingShare: Identifiable {
        let id = UUID()
        let shareType: String
        let shareId: Int64
        let displayTitle: String
        let systemText: String
    }

    var body: some View {
        UserHomeComposeView(
            userId: userId,
            currentUserId: auth.currentUserId,
            onBack: { dismiss() },
            onChat: onChat,
            onAddFriend: onAddFriend,
            onPlaylist: onPlaylist,
            onPostDetail: onPostDetail,
            onTagSearch: onTagSearch,
            onEditProfile: onEditProfile,
            onPrivacySettings: onPrivacySettings,
            onPickBackground: { pickerPresented = true },
            onPlayMusic: { song in
                if let song { PlayerModel.shared.play(song) }
            },
            onShareProfile: { id, nickname, signature in
                var lines = ["FoxMusic 用户：\(nickname ?? "用户")"]
                if let signature, !signature.isEmpty { lines.append(signature) }
                lines.append("ID：\(id)")
                pendingShare = PendingShare(
                    shareType: "user",
                    shareId: id,
                    displayTitle: "分享名片",
                    systemText: lines.joined(separator: "\n")
                )
            },
            onSharePost: { id, title in
                let name = title ?? "未命名帖子"
                pendingShare = PendingShare(
                    shareType: "post",
                    shareId: id,
                    displayTitle: "分享动态",
                    systemText: "FoxMusic 动态：\(name)\nID：\(id)"
                )
            },
            onReportPost: { id, title in
                reportTarget = ReportTarget(type: "post", targetId: id, name: title ?? "未命名帖子")
            },
            onToast: { ToastCenter.shared.show($0) }
        )
        .ignoresSafeArea(edges: [.top, .bottom])
        .navigationBarHidden(true)
        .toolbar(.hidden, for: .tabBar)
        .swipeBackEnabled()
        .themedPageBackground()
        // 替换背景图：PHPicker 单选（无需相册权限）。uCrop 是 Android 独有的裁剪库，
        // iOS 不做裁剪，直接上传原图（后端按封面比例拉取展示）。
        .sheet(isPresented: $pickerPresented) {
            SingleImagePickerView { path in
                uploadBackground(path)
            }
        }
        .sheet(item: $shareItem) { item in
            ActivityView(activityItems: [item.text])
        }
        .confirmationDialog(
            pendingShare?.displayTitle ?? "分享",
            isPresented: Binding(
                get: { pendingShare != nil },
                set: { if !$0 { pendingShare = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("分享给好友") {
                guard let share = pendingShare else { return }
                pendingShare = nil
                FriendPickerPresenter.present(title: "分享给好友") { receiverId, name, avatar in
                    FoxShareSender.shared.send(
                        receiverId: receiverId,
                        shareType: share.shareType,
                        shareId: share.shareId,
                        content: share.displayTitle,
                        receiverNickname: name,
                        receiverAvatar: avatar,
                        onSuccess: { ToastCenter.shared.show("已分享") },
                        onError: { ToastCenter.shared.show($0) }
                    )
                }
            }
            Button("系统分享") {
                if let share = pendingShare {
                    shareItem = ShareItem(text: share.systemText)
                }
                pendingShare = nil
            }
            Button("取消", role: .cancel) { pendingShare = nil }
        }
        .bottomSheet(item: $reportTarget) { target in
            ReportSheet(
                targetType: target.type,
                targetId: target.targetId,
                targetName: target.name
            ) {}
        }
    }

    /// 上传 + 保存背景全在 Kotlin 侧（api/upload/image → PUT api/auth/profile），
    /// 成功后共享页自己监听 revision 重拉资料，这里只负责提示。
    private func uploadBackground(_ path: String) {
        FoxUserHomeScreen.shared.uploadBackgroundImage(filePath: path) { error in
            DispatchQueue.main.async {
                if let error {
                    ToastCenter.shared.show(error)
                } else {
                    ToastCenter.shared.show("背景图已更新")
                }
            }
        }
    }
}

/// 把 Kotlin 侧个人主页 VC 包进 SwiftUI。
struct UserHomeComposeView: UIViewControllerRepresentable {
    let userId: Int64
    let currentUserId: Int64
    let onBack: () -> Void
    let onChat: (Int64, String?, String?) -> Void
    let onAddFriend: (Int64, String?) -> Void
    let onPlaylist: (Int64) -> Void
    let onPostDetail: (Int64) -> Void
    let onTagSearch: (String) -> Void
    let onEditProfile: () -> Void
    let onPrivacySettings: () -> Void
    let onPickBackground: () -> Void
    let onPlayMusic: (PlayableSong?) -> Void
    let onShareProfile: (Int64, String?, String?) -> Void
    let onSharePost: (Int64, String?) -> Void
    let onReportPost: (Int64, String?) -> Void
    let onToast: (String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxUserHomeScreen.shared.makeUserHomeViewController(
            userId: userId,
            currentUserId: currentUserId,
            onBack: onBack,
            onNavigateToChat: { id, nickname, avatar in
                onChat(id.int64Value, nickname, avatar)
            },
            onNavigateToAddFriend: { id, nickname in
                onAddFriend(id.int64Value, nickname)
            },
            onNavigateToPlaylist: { id in
                onPlaylist(id.int64Value)
            },
            onNavigateToPostDetail: { id in
                onPostDetail(id.int64Value)
            },
            onNavigateToTagSearch: { tag in
                onTagSearch(tag)
            },
            onNavigateToEditProfile: onEditProfile,
            onNavigateToPrivacySettings: onPrivacySettings,
            onPickBackgroundImage: onPickBackground,
            onPlayMusic: onPlayMusic,
            onShareProfile: { id, nickname, signature in
                onShareProfile(id.int64Value, nickname, signature)
            },
            onSharePost: { id, title in
                onSharePost(id.int64Value, title)
            },
            onReportPost: { id, title in
                onReportPost(id.int64Value, title)
            },
            onToast: onToast
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - 单图选择（个人主页背景）

/// PHPicker 单选图片：选中后把文件复制进 tmp，回调本地路径（供 Kotlin 上传）。
struct SingleImagePickerView: UIViewControllerRepresentable {
    let onPicked: (String) -> Void

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration(photoLibrary: .shared())
        config.filter = .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPicked: onPicked) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let onPicked: (String) -> Void
        init(onPicked: @escaping (String) -> Void) { self.onPicked = onPicked }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            picker.dismiss(animated: true)
            guard let result = results.first else { return }
            guard result.itemProvider.hasItemConformingToTypeIdentifier(UTType.image.identifier) else { return }
            result.itemProvider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { url, _ in
                guard let url else { return }
                let dest = FileManager.default.temporaryDirectory
                    .appendingPathComponent("home_bg_\(UUID().uuidString).\(url.pathExtension)")
                do {
                    // 必须复制：原 url 是 picker 的临时文件，回调结束就失效。
                    try FileManager.default.copyItem(at: url, to: dest)
                    DispatchQueue.main.async { self.onPicked(dest.path) }
                } catch {
                    NSLog("copy home background failed: \(error)")
                }
            }
        }
    }
}
