import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import FoxShared
import ObjectiveC

// MARK: - 聊天信息页

/// 聊天信息页 —— 对应 Android `ChatSettingsScreen`（聊天页右上角「⋮」进入）。
///
/// 功能逐项对齐安卓：
///  - 对方资料卡 → 用户资料页
///  - 查找聊天记录 → ``ChatSearchView``
///  - 消息免打扰（本地开关，Kotlin `FoxChatSettings` → NSUserDefaults）
///  - 置顶聊天（本地会话表状态，刷新/重进保持，与 Android Room 同语义）
///  - 设置聊天背景（相册选图 → Application Support，聊天页实时渲染）/ 清除背景
///  - 清空聊天记录并删除对话（红色 destructive，确认后删本地消息+会话并退回列表）
struct ChatSettingsView: View {

    @Environment(\.dismiss) private var dismiss

    let userId: Int64
    let peerNickname: String?
    let peerAvatar: String?
    let onUserProfile: () -> Void
    let onOpenSearch: () -> Void
    let onClearedChat: () -> Void

    @State private var isPinned = false
    @State private var isMuted = false
    @State private var backgroundPath: String?
    @State private var showClearConfirm = false

    var body: some View {
        Form {
            // 对方资料卡
            Section {
                Button(action: onUserProfile) {
                    HStack(spacing: 12) {
                        ChatPeerAvatar(url: peerAvatar, size: 52)
                        Text(peerNickname?.isEmpty == false ? peerNickname! : "聊天")
                            .font(.headline)
                            .foregroundStyle(.primary)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.tertiary)
                    }
                    .padding(.vertical, 4)
                }
                .buttonStyle(.plain)
            }

            // 记录查找 / 免打扰 / 置顶 / 背景
            Section {
                Button(action: onOpenSearch) {
                    HStack {
                        Text("查找聊天记录").foregroundStyle(.primary)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.tertiary)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("chatSettings.search")

                Toggle("消息免打扰", isOn: muteBinding)
                Toggle("置顶聊天", isOn: pinBinding)

                Button(action: { ChatBackgroundPicker.present(peerUserId: userId) { path in
                    guard let path else { return }
                    FoxChatSettings.shared.setBackgroundPath(peerUserId: userId, path: path)
                    backgroundPath = path
                } }) {
                    HStack {
                        Text("设置聊天背景").foregroundStyle(.primary)
                        Spacer()
                        if let path = backgroundPath,
                           FileManager.default.fileExists(atPath: path),
                           let uiImage = UIImage(contentsOfFile: path) {
                            Image(uiImage: uiImage)
                                .resizable()
                                .scaledToFill()
                                .frame(width: 44, height: 28)
                                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                        }
                        Image(systemName: "chevron.right")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.tertiary)
                    }
                }
                .buttonStyle(.plain)

                if backgroundPath != nil {
                    Button(role: .destructive) {
                        if let path = backgroundPath {
                            try? FileManager.default.removeItem(atPath: path)
                        }
                        FoxChatSettings.shared.setBackgroundPath(peerUserId: userId, path: nil)
                        backgroundPath = nil
                    } label: {
                        Text("清除背景图片")
                    }
                }
            }

            // 清空聊天记录并删除对话
            Section {
                Button(role: .destructive) {
                    showClearConfirm = true
                } label: {
                    Text("清空聊天记录并删除对话")
                }
            }
        }
        .navigationTitle("聊天信息")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            refreshState()
        }
        .alert("清空聊天记录", isPresented: $showClearConfirm) {
            Button("取消", role: .cancel) {}
            Button("确认清空", role: .destructive) {
                FoxChatSettings.shared.clearChat(peerUserId: userId)
                // 删除后聊天页已无数据，直接退回会话列表（path 重置）
                onClearedChat()
            }
        } message: {
            Text("确定要清空所有聊天记录并删除对话吗？此操作不可恢复。")
        }
    }

    private func refreshState() {
        isMuted = FoxChatSettings.shared.isMuted(peerUserId: userId)
        isPinned = FoxChatSettings.shared.isPinned(peerUserId: userId)
        backgroundPath = FoxChatSettings.shared.backgroundPath(peerUserId: userId)
    }

    private var muteBinding: Binding<Bool> {
        Binding(
            get: { isMuted },
            set: { newValue in
                FoxChatSettings.shared.setMuted(peerUserId: userId, muted: newValue)
                isMuted = newValue
            }
        )
    }

    private var pinBinding: Binding<Bool> {
        Binding(
            get: { isPinned },
            set: { newValue in
                FoxChatSettings.shared.setPinned(peerUserId: userId, pinned: newValue)
                isPinned = newValue
            }
        )
    }
}

// MARK: - 查找聊天记录

/// 进页自动激活搜索栏（展开并聚焦、唤起键盘）的系统搜索栏。
///
/// ⚠️ `searchable(text:isPresented:)` 是 iOS 17.0+ API，而本工程 App target 的
/// 部署目标是 16.0（只有 Widget target 是 17.0）—— 直接写会编译报
/// "'searchable(text:isPresented:placement:prompt:)' is only available in iOS 17.0 or newer"。
/// 所以按可用性分叉：17+ 用 isPresented 自动展开，16 保持系统默认行为
/// （搜索栏常显，用户点一下才进输入态）。
private struct AutoOpenSearchBar: ViewModifier {
    @Binding var text: String
    @Binding var isPresented: Bool
    let prompt: String

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 17.0, *) {
            content.searchable(
                text: $text,
                isPresented: $isPresented,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: prompt
            )
        } else {
            content.searchable(
                text: $text,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: prompt
            )
        }
    }
}

/// 聊天记录搜索页 —— 对应 Android `ChatSearchScreen` / `ChatSearchViewModel`。
///
/// 搜索范围与安卓一致：**全部本地会话**，结果按会话聚合（对方头像/昵称 +
/// 最近一条命中内容 + 命中条数），点击直接进入对应会话。
struct ChatSearchView: View {

    let onOpenChat: (Int64, String?, String?) -> Void

    @State private var query = ""
    @State private var results: [FoxChatSearchResult] = []
    /// 进入页面即展开搜索栏并聚焦输入框（对齐 Android ChatSearchScreen 的
    /// `focusRequester.requestFocus()`）：否则要再点一下搜索框才会弹键盘。
    @State private var searchPresented = false

    var body: some View {
        List {
            ForEach(results, id: \.userId) { item in
                Button {
                    onOpenChat(item.userId, item.nickname, item.avatar)
                } label: {
                    HStack(spacing: 12) {
                        ChatPeerAvatar(url: item.avatar, size: 44)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.nickname?.isEmpty == false ? item.nickname! : "用户")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.primary)
                                .lineLimit(1)
                            Text(snippet(for: item))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                        Spacer()
                        if item.matchCount > 1 {
                            Text("\(Int(item.matchCount))条")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }
                .buttonStyle(.plain)
            }
        }
        .listStyle(.plain)
        .overlay {
            if results.isEmpty {
                Text(isQueryBlank ? "输入关键词搜索聊天记录" : "未找到相关聊天记录")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
        }
        .modifier(AutoOpenSearchBar(text: $query, isPresented: $searchPresented, prompt: "搜索聊天记录"))
        .navigationTitle("查找聊天记录")
        .navigationBarTitleDisplayMode(.inline)
        // 二级页必须隐藏底部 TabBar（与聊天页/聊天信息页一致），否则列表底部被栏切开
        .toolbar(.hidden, for: .tabBar)
        .task(id: query) {
            // 对齐 Android 的 300ms debounce：本地 JSON 扫描虽快，逐字搜也没必要
            let keyword = query
            try? await Task.sleep(nanoseconds: 250_000_000)
            if Task.isCancelled || keyword != query { return }
            let trimmed = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
            results = trimmed.isEmpty ? [] : FoxChatSearch.shared.search(query: trimmed)
        }
        .onAppear { searchPresented = true }
    }

    private var isQueryBlank: Bool {
        query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// 命中文本片段：以命中位置为中心截窗，并用主题色高亮关键词
    /// （对齐 Android ChatSearchScreen 的 ±20 字符窗口 + primary 高亮）。
    private func snippet(for item: FoxChatSearchResult) -> AttributedString {
        let content = item.lastMessageContent
        let keyword = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !keyword.isEmpty,
              let range = content.range(of: keyword, options: .caseInsensitive) else {
            return AttributedString(String(content.prefix(50)))
        }
        let window = 20
        let startOffset = min(window, content.distance(from: content.startIndex, to: range.lowerBound))
        let snippetStart = content.index(range.lowerBound, offsetBy: -startOffset)
        let remaining = content.distance(from: range.upperBound, to: content.endIndex)
        let endOffset = min(window, remaining)
        let snippetEnd = content.index(range.upperBound, offsetBy: endOffset)
        let snippetText = (startOffset < window ? "" : "...") +
            String(content[snippetStart..<snippetEnd]) +
            (endOffset < remaining ? "..." : "")

        var attributed = AttributedString(snippetText)
        if let highlightRange = attributed.range(of: keyword, options: .caseInsensitive) {
            attributed[highlightRange].foregroundColor = .accentColor
        }
        return attributed
    }
}

// MARK: - 会话内查找聊天记录

/// 会话内搜索页 —— 对应 Android `UserChatSearchScreen`（从聊天信息页进入）。
/// 只扫与该用户的本地消息：顶部对方资料卡 + 逐条命中（时间倒序），点击进入会话。
struct UserChatSearchView: View {

    let peerUserId: Int64
    let peerNickname: String?
    let peerAvatar: String?
    let onOpenChat: () -> Void
    let onUserProfile: () -> Void

    @State private var query = ""
    @State private var hits: [FoxUserChatHit] = []
    /// 进入页面即自动激活搜索栏并唤起键盘（对齐 Android UserChatSearchScreen）。
    /// 原先这里是 `@FocusState` + `onAppear { searchFocused = true }`，但 FocusState
    /// 只能绑到自己声明的输入控件，绑不到 `.searchable` 的系统搜索栏 —— 是段死代码。
    @State private var searchPresented = false

    private var displayName: String {
        peerNickname?.isEmpty == false ? peerNickname! : "聊天"
    }

    var body: some View {
        List {
            Section {
                Button(action: onUserProfile) {
                    HStack(spacing: 12) {
                        ChatPeerAvatar(url: peerAvatar, size: 44)
                        Text(displayName)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)
                        Spacer()
                    }
                    .padding(.vertical, 4)
                }
                .buttonStyle(.plain)
            }

            if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Section {
                    Text("共\(hits.count)条相关的聊天记录")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                ForEach(hits, id: \.messageId) { hit in
                    Button(action: onOpenChat) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(hit.content)
                                .font(.subheadline)
                                .foregroundStyle(.primary)
                                .lineLimit(2)
                            if let time = formatTime(hit.createdAt) {
                                Text(time)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .listStyle(.plain)
        .overlay {
            if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && hits.isEmpty {
                Text("未找到相关聊天记录")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
        }
        .modifier(AutoOpenSearchBar(text: $query, isPresented: $searchPresented, prompt: "搜索"))
        .navigationTitle("查找聊天记录")
        .navigationBarTitleDisplayMode(.inline)
        // 二级页必须隐藏底部 TabBar（与聊天页/聊天信息页一致）
        .toolbar(.hidden, for: .tabBar)
        .task(id: query) {
            let keyword = query
            try? await Task.sleep(nanoseconds: 250_000_000)
            if Task.isCancelled || keyword != query { return }
            let trimmed = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
            hits = trimmed.isEmpty
                ? []
                : FoxChatSearch.shared.searchInConversation(peerUserId: peerUserId, query: trimmed)
        }
        .onAppear { searchPresented = true }
    }

    private func formatTime(_ iso: String?) -> String? {
        guard let iso, !iso.isEmpty else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let date = formatter.date(from: iso) else { return nil }
        let out = DateFormatter()
        out.dateFormat = "MM-dd HH:mm"
        return out.string(from: date)
    }
}

// MARK: - 对方头像

/// 聊天信息/搜索页共用的圆形头像：走与聊天页相同的 URL 解析（MediaUrlResolver）。
struct ChatPeerAvatar: View {
    let url: String?
    let size: CGFloat

    var body: some View {
        avatarContent
            .frame(width: size, height: size)
            .clipShape(Circle())
    }

    @ViewBuilder
    private var avatarContent: some View {
        if let url,
           let resolved = MediaUrlResolver.shared.resolve(url: url),
           let imageUrl = URL(string: resolved) {
            AsyncImage(url: imageUrl) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                default:
                    Image(systemName: "person.crop.circle.fill")
                        .resizable()
                        .scaledToFill()
                        .foregroundStyle(.secondary.opacity(0.6))
                }
            }
        } else {
            Image(systemName: "person.crop.circle.fill")
                .resizable()
                .scaledToFill()
                .foregroundStyle(.secondary.opacity(0.6))
        }
    }
}

// MARK: - 聊天背景选图

/// 从相册选一张图作为聊天背景：复制（压缩）到
/// Application Support/chat_backgrounds/bg_{userId}.jpg，回调绝对路径。
///
/// 路径约定与 Android `ChatSettingsViewModel` 的 filesDir/chat_backgrounds 对齐，
/// Kotlin 侧拿到路径后由 PlatformFileImage 直接读文件渲染。
final class ChatBackgroundPicker: NSObject, PHPickerViewControllerDelegate {

    private let peerUserId: Int64
    private let completion: (String?) -> Void

    private init(peerUserId: Int64, completion: @escaping (String?) -> Void) {
        self.peerUserId = peerUserId
        self.completion = completion
    }

    static func present(peerUserId: Int64, completion: @escaping (String?) -> Void) {
        var config = PHPickerConfiguration(photoLibrary: .shared())
        config.filter = .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        // 代理是局部对象，用关联对象挂到 picker 上保活，dismiss 后随 picker 一起释放
        let coordinator = ChatBackgroundPicker(peerUserId: peerUserId, completion: completion)
        objc_setAssociatedObject(picker, &AssociatedKeys.coordinator, coordinator,
                                 .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        picker.delegate = coordinator
        UIApplication.shared.topViewController?.present(picker, animated: true)
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let provider = results.first?.itemProvider else {
            DispatchQueue.main.async { self.completion(nil) }
            return
        }
        provider.loadDataRepresentation(forTypeIdentifier: UTType.image.identifier) { data, _ in
            guard let data,
                  let image = UIImage(data: data),
                  let jpeg = image.jpegData(compressionQuality: 0.85) else {
                DispatchQueue.main.async { self.completion(nil) }
                return
            }
            let saved = self.saveBackground(jpeg)
            DispatchQueue.main.async { self.completion(saved) }
        }
    }

    private func saveBackground(_ data: Data) -> String? {
        do {
            let base = try FileManager.default.url(
                for: .applicationSupportDirectory,
                in: .userDomainMask,
                appropriateFor: nil,
                create: true
            )
            let dir = base.appendingPathComponent("chat_backgrounds", isDirectory: true)
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let fileURL = dir.appendingPathComponent("bg_\(peerUserId).jpg")
            try data.write(to: fileURL, options: .atomic)
            return fileURL.path
        } catch {
            return nil
        }
    }

    private enum AssociatedKeys {
        static var coordinator: UInt8 = 0
    }
}
