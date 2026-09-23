import SwiftUI
import FoxShared

/// 空状态占位（本项目 deployment target 是 iOS 16，
/// 用不了 iOS 17 才有的 ContentUnavailableView，故自建一个）。
struct EmptyHint: View {
    let icon: String
    let title: String
    var subtitle: String?

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 30))
                .foregroundStyle(.secondary)
            Text(title)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            if let subtitle {
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.tertiary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 32)
    }
}

// MARK: - 播放列表面板

/// 播放列表面板 —— 对应 Android `PlaylistBottomSheet`：
/// 标题栏 + 清空（带二次确认）、来源卡片、共 N 首、当前曲高亮、点击切歌、单曲删除。
struct QueueSheet: View {
    @ObservedObject private var player = PlayerModel.shared
    @Environment(\.dismiss) private var dismiss
    @State private var showClearConfirm = false

    var body: some View {
        NavigationStack {
            Group {
                if player.queue.isEmpty {
                    EmptyHint(
                        icon: "music.note.list",
                        title: "播放列表为空",
                        subtitle: "从首页或发现页点一首歌开始播放"
                    )
                } else {
                    List {
                        if let source = player.source {
                            Section {
                                HStack(spacing: 12) {
                                    AsyncImage(url: source.coverUrl.flatMap(URL.init(string:))) { phase in
                                        if let image = phase.image {
                                            image.resizable().scaledToFill()
                                        } else {
                                            Color.secondary.opacity(0.18)
                                        }
                                    }
                                    .frame(width: 44, height: 44)
                                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(source.title)
                                            .font(.subheadline)
                                            .lineLimit(1)
                                        Text("来自\(source.typeLabel)")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer(minLength: 0)
                                }
                                .padding(.vertical, 2)
                            }
                        }

                        Section {
                            ForEach(Array(player.queue.enumerated()), id: \.offset) { index, song in
                                QueueRow(
                                    song: song,
                                    isCurrent: index == player.currentIndex,
                                    isPlaying: player.isPlaying,
                                    onPlay: { player.playQueueItem(at: index) }
                                )
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        player.removeFromQueue(at: index)
                                    } label: {
                                        Label("删除", systemImage: "trash")
                                    }
                                }
                                .accessibilityIdentifier("queue.row.\(index)")
                            }
                        } header: {
                            Text("共 \(player.queue.count) 首歌曲")
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("播放列表")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("关闭") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    if !player.queue.isEmpty {
                        Button(role: .destructive) {
                            showClearConfirm = true
                        } label: {
                            Image(systemName: "trash")
                        }
                        .accessibilityIdentifier("queue.clear")
                    }
                }
            }
            .confirmationDialog(
                "清空播放列表",
                isPresented: $showClearConfirm,
                titleVisibility: .visible
            ) {
                Button("清空", role: .destructive) {
                    player.clearQueue()
                    dismiss()
                }
                Button("取消", role: .cancel) {}
            } message: {
                Text("确定要清空播放列表吗？此操作不可恢复。")
            }
        }
        .preferredColorScheme(.dark)
        .accessibilityIdentifier("queueSheet")
    }
}

private struct QueueRow: View {
    let song: PlayableSong
    let isCurrent: Bool
    let isPlaying: Bool
    let onPlay: () -> Void

    var body: some View {
        Button(action: onPlay) {
            HStack(spacing: 10) {
                Image(systemName: isCurrent ? (isPlaying ? "speaker.wave.2.fill" : "speaker.fill") : "music.note")
                    .font(.system(size: 13))
                    .foregroundStyle(isCurrent ? Color.accentColor : .secondary)
                    .frame(width: 18)

                VStack(alignment: .leading, spacing: 2) {
                    Text(song.title)
                        .font(.subheadline)
                        .foregroundStyle(isCurrent ? Color.accentColor : .primary)
                        .lineLimit(1)
                    Text(song.artist)
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
}

// MARK: - 评论面板

/// 评论面板 —— 对应 Android `MusicCommentBottomSheet`：
/// 一级评论分页列表（头像/昵称/内容/点赞数/最新回复预览）+ 点赞 + 发表评论。
/// 发表与点赞都需要登录，未登录时后端会拒绝，这里把错误原文透出。
struct CommentSheet: View {
    let musicId: Int64
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model: CommentSheetModel
    @State private var draft = ""

    init(musicId: Int64) {
        self.musicId = musicId
        _model = StateObject(wrappedValue: CommentSheetModel(musicId: musicId))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                content

                Divider()

                HStack(spacing: 10) {
                    TextField("说点什么…", text: $draft, axis: .vertical)
                        .textFieldStyle(.plain)
                        .lineLimit(1...4)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .accessibilityIdentifier("comment.input")

                    Button {
                        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !text.isEmpty else { return }
                        model.post(content: text) { draft = "" }
                    } label: {
                        if model.posting {
                            ProgressView().scaleEffect(0.8)
                        } else {
                            Image(systemName: "paperplane.fill")
                                .font(.system(size: 17))
                        }
                    }
                    .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || model.posting)
                    .accessibilityIdentifier("comment.send")
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
            }
            .navigationTitle("评论")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("关闭") { dismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
        .accessibilityIdentifier("commentSheet")
        .task { model.loadFirstPage() }
    }

    @ViewBuilder
    private var content: some View {
        if model.isLoading && model.comments.isEmpty {
            Spacer()
            ProgressView().tint(.white)
            Spacer()
        } else if model.comments.isEmpty {
            Spacer()
            VStack(spacing: 8) {
                Image(systemName: "bubble.left.and.bubble.right")
                    .font(.system(size: 30))
                    .foregroundStyle(.secondary)
                Text(model.errorMessage ?? "还没有评论，来说两句")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }
            Spacer()
        } else {
            List {
                ForEach(model.comments, id: \.id) { comment in
                    CommentRow(comment: comment) {
                        model.toggleLike(comment)
                    }
                    .listRowSeparator(.hidden)
                    .task {
                        // 滑到最后一条时翻页
                        if comment.id == model.comments.last?.id { model.loadNextPage() }
                    }
                }
                if model.isLoading {
                    HStack { Spacer(); ProgressView(); Spacer() }
                        .listRowSeparator(.hidden)
                }
            }
            .listStyle(.plain)
        }
    }
}

private struct CommentRow: View {
    let comment: FoxComment
    let onLike: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            AsyncImage(url: comment.authorAvatar.flatMap(URL.init(string:))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                } else {
                    ZStack {
                        Color.secondary.opacity(0.2)
                        Image(systemName: "person.fill")
                            .font(.system(size: 15))
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .frame(width: 34, height: 34)
            .clipShape(Circle())

            VStack(alignment: .leading, spacing: 4) {
                Text(comment.authorName)
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                Text(comment.content)
                    .font(.subheadline)

                if let reply = comment.latestReply, !reply.isEmpty {
                    HStack(alignment: .top, spacing: 4) {
                        Image(systemName: "arrow.turn.down.right")
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                        Text("\(comment.latestReplyAuthor ?? "对方")：\(reply)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                    .padding(8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.quaternary.opacity(0.35), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                }

                if let time = CommentDateFormatter.relative(comment.createdAt) {
                    Text(time)
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                }
            }

            Spacer(minLength: 0)

            Button(action: onLike) {
                VStack(spacing: 2) {
                    Image(systemName: comment.isLiked ? "heart.fill" : "heart")
                        .font(.system(size: 15))
                        .foregroundStyle(comment.isLiked ? Color.red : .secondary)
                    Text("\(comment.likeCount)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                .frame(width: 36)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("comment.like.\(comment.id)")
        }
        .padding(.vertical, 6)
    }
}

/// 评论分页 + 发表 + 点赞的视图模型。
final class CommentSheetModel: ObservableObject {
    @Published private(set) var comments: [FoxComment] = []
    @Published private(set) var isLoading = false
    @Published private(set) var posting = false
    @Published private(set) var errorMessage: String?

    private let musicId: Int64
    private var page = 1
    private var hasMore = true

    init(musicId: Int64) {
        self.musicId = musicId
    }

    func loadFirstPage() {
        guard comments.isEmpty else { return }
        page = 1
        hasMore = true
        load()
    }

    func loadNextPage() {
        guard hasMore, !isLoading else { return }
        page += 1
        load()
    }

    private func load() {
        isLoading = true
        let targetPage = page
        FoxPlayerApi.shared.fetchComments(
            musicId: musicId,
            page: Int32(targetPage),
            limit: 20,
            onResult: { [weak self] list, total in
                guard let self else { return }
                if targetPage == 1 {
                    self.comments = list
                } else {
                    self.comments.append(contentsOf: list)
                }
                self.hasMore = self.comments.count < total.intValue
                self.isLoading = false
                self.errorMessage = nil
            },
            onError: { [weak self] message in
                self?.isLoading = false
                self?.hasMore = false
                self?.errorMessage = message
            }
        )
    }

    func post(content: String, onSuccess: @escaping () -> Void) {
        posting = true
        FoxPlayerApi.shared.postComment(
            musicId: musicId,
            content: content,
            parentId: nil,
            onResult: { [weak self] _ in
                guard let self else { return }
                self.posting = false
                onSuccess()
                // 重新拉第一页，让新评论（倒序）出现在最前
                self.comments = []
                self.loadFirstPage()
                ToastCenter.shared.show("评论已发布")
            },
            onError: { [weak self] message in
                self?.posting = false
                ToastCenter.shared.show(message)
            }
        )
    }

    func toggleLike(_ comment: FoxComment) {
        FoxPlayerApi.shared.likeComment(
            commentId: comment.id,
            onResult: { [weak self] _ in
                guard let self else { return }
                // 后端不回传新状态，本地取反（与 Android 同策略）
                if let idx = self.comments.firstIndex(where: { $0.id == comment.id }) {
                    let old = self.comments[idx]
                    self.comments[idx] = FoxComment(
                        id: old.id,
                        content: old.content,
                        authorName: old.authorName,
                        authorAvatar: old.authorAvatar,
                        likeCount: old.isLiked ? max(old.likeCount - 1, 0) : old.likeCount + 1,
                        isLiked: !old.isLiked,
                        replyCount: old.replyCount,
                        createdAt: old.createdAt,
                        replyToName: old.replyToName,
                        latestReply: old.latestReply,
                        latestReplyAuthor: old.latestReplyAuthor
                    )
                }
            },
            onError: { message in
                ToastCenter.shared.show(message)
            }
        )
    }
}

/// 评论时间：ISO8601 → "x 分钟前 / x 小时前 / x 天前 / 具体日期"。
enum CommentDateFormatter {
    private static let iso: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
    private static let isoNoFraction: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()
    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    static func relative(_ raw: String?) -> String? {
        guard let raw, !raw.isEmpty else { return nil }
        guard let date = iso.date(from: raw) ?? isoNoFraction.date(from: raw) else {
            return String(raw.prefix(10))
        }
        let seconds = Date().timeIntervalSince(date)
        switch seconds {
        case ..<60: return "刚刚"
        case ..<3600: return "\(Int(seconds / 60)) 分钟前"
        case ..<86400: return "\(Int(seconds / 3600)) 小时前"
        case ..<(86400 * 30): return "\(Int(seconds / 86400)) 天前"
        default: return dayFormatter.string(from: date)
        }
    }
}

// MARK: - 歌词设置面板

/// 歌词设置 —— 对应 Android `LyricSettings`：
/// 字号、歌词翻译开关、歌词纠正（换成别的歌词源）。
///
/// 说明：Android 那栏还有「桌面歌词」（悬浮窗）。iOS 没有悬浮窗能力，
/// 对应形态是锁屏/灵动岛的 Live Activity，属独立批次，不在本面板内。
struct LyricSettingsSheet: View {
    @ObservedObject private var player = PlayerModel.shared
    @Environment(\.dismiss) private var dismiss
    @State private var showLyricSearch = false

    var body: some View {
        NavigationStack {
            List {
                Section("歌词字号") {
                    HStack(spacing: 12) {
                        Image(systemName: "textformat.size.smaller")
                            .foregroundStyle(.secondary)
                        Slider(value: $player.lyricFontSize, in: 14...30, step: 1)
                            .accessibilityIdentifier("lyricSettings.fontSize")
                        Image(systemName: "textformat.size.larger")
                            .foregroundStyle(.secondary)
                        Text("\(Int(player.lyricFontSize))")
                            .font(.footnote.monospacedDigit())
                            .foregroundStyle(.secondary)
                            .frame(width: 24)
                    }
                }

                Section {
                    Toggle("歌词翻译", isOn: $player.showTranslation)
                        .accessibilityIdentifier("lyricSettings.translation")
                } footer: {
                    Text("翻译歌词来自歌曲的 lyricsTrans 字段，与原文按时间戳对齐。")
                }

                Section {
                    Button {
                        showLyricSearch = true
                    } label: {
                        HStack {
                            Text("歌词纠正")
                                .foregroundStyle(.primary)
                            Spacer()
                            if player.current == nil {
                                Text("未在播放").foregroundStyle(.secondary)
                            }
                            Image(systemName: "chevron.right")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(.tertiary)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(player.current == nil)
                    .accessibilityIdentifier("lyricSettings.correct")
                } footer: {
                    Text("搜同名歌曲，用它的歌词替换当前歌词。")
                }
            }
            .navigationTitle("歌词设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("完成") { dismiss() }
                }
            }
            .sheet(isPresented: $showLyricSearch) {
                // 应用歌词后连设置面板一起收掉，直接回到播放页看新歌词
                LyricCorrectSheet(onApplied: {
                    showLyricSearch = false
                    dismiss()
                })
            }
        }
        .preferredColorScheme(.dark)
        .accessibilityIdentifier("lyricSettingsSheet")
    }
}

/// 歌词纠正：搜同名歌曲 → 点候选**先预览全文** → 右上角确认才替换当前歌词。
/// 与 Android `CustomLyricSelectorSheet` 同一口径（第三方平台 netease + 标题完全匹配）。
private struct LyricCorrectSheet: View {
    @ObservedObject private var player = PlayerModel.shared
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model = LyricSearchModel()

    /// 在预览页点了"使用该歌词"后的回调（由 LyricSettingsSheet 收掉两级面板）。
    var onApplied: () -> Void = {}

    @State private var keyword = ""
    @State private var didSearch = false
    /// 正在预览的候选；非 nil 时 push 歌词预览页。
    @State private var previewing: FoxLyricCandidate?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("搜索歌曲名", text: $keyword)
                        .textFieldStyle(.plain)
                        .submitLabel(.search)
                        .onSubmit { search(reset: true) }
                    if !keyword.isEmpty {
                        Button {
                            keyword = ""
                        } label: {
                            Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                        }
                    }
                }
                .padding(.horizontal, 12)
                .frame(height: 38)
                .background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                .padding(.horizontal, 16)
                .padding(.vertical, 10)

                content
            }
            .navigationDestination(isPresented: Binding(
                get: { previewing != nil },
                set: { if !$0 { previewing = nil } }
            )) {
                if let candidate = previewing {
                    LyricPreviewPage(candidate: candidate) {
                        player.applyLyrics(lyrics: candidate.lyrics, translation: candidate.lyricsTrans)
                        onApplied()
                    }
                }
            }
            .navigationTitle("歌词纠正")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("关闭") { dismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
        .accessibilityIdentifier("lyricCorrectSheet")
        .onAppear {
            if !didSearch, let song = player.current {
                keyword = song.title
                search(reset: true)
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if model.isLoading && model.results.isEmpty {
            Spacer(); ProgressView().tint(.white); Spacer()
        } else if model.results.isEmpty {
            Spacer()
            Text(model.message ?? (didSearch ? "没找到更合适的歌词" : "输入歌名后回车搜索"))
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Spacer()
        } else {
            List {
                ForEach(model.results, id: \.id) { item in
                    // 点候选不直接应用：先进预览页看全文，右上角确认才替换
                    Button {
                        previewing = item
                    } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.title)
                                .font(.subheadline)
                                .foregroundStyle(.primary)
                                .lineLimit(1)
                            HStack(spacing: 6) {
                                Text(item.artist)
                                    .lineLimit(1)
                                Text("·")
                                Text("\(item.lineCount) 行")
                            }
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("lyricCorrect.result.\(item.id)")
                    .task {
                        if item.id == model.results.last?.id { search(reset: false) }
                    }
                }
                if model.isLoading {
                    HStack { Spacer(); ProgressView(); Spacer() }
                }
            }
            .listStyle(.plain)
        }
    }

    private func search(reset: Bool) {
        let text = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        didSearch = true
        model.search(keyword: text, title: player.current?.title ?? text, reset: reset)
    }
}

/// 歌词预览页：展示候选歌词全文（原文 + 翻译），右上角"使用该歌词"确认后才应用。
private struct LyricPreviewPage: View {
    let candidate: FoxLyricCandidate
    let onUse: () -> Void
    @Environment(\.dismiss) private var dismiss

    private var lines: [LyricLine] {
        PlayerModel.parseBilingual(candidate.lyrics, candidate.lyricsTrans)
    }

    var body: some View {
        Group {
            if lines.isEmpty {
                EmptyHint(
                    icon: "text.alignleft",
                    title: "该来源没有返回歌词正文",
                    subtitle: "换一个候选试试"
                )
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(candidate.artist)
                                .font(.footnote.weight(.medium))
                                .foregroundStyle(.secondary)
                            Text("\(lines.count) 行歌词预览")
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }
                        .padding(.bottom, 4)

                        ForEach(lines) { line in
                            VStack(alignment: .leading, spacing: 3) {
                                Text(line.text)
                                    .font(.system(size: 15))
                                if let trans = line.translation, !trans.isEmpty {
                                    Text(trans)
                                        .font(.system(size: 13))
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 16)
                }
            }
        }
        .navigationTitle("歌词预览")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: onUse) {
                    Text("使用该歌词")
                        .font(.system(size: 15, weight: .semibold))
                }
                .disabled(lines.isEmpty)
                .accessibilityIdentifier("lyricPreview.use")
            }
        }
    }
}

/// 歌词搜索结果的状态持有者。
/// 用 class 而不是 struct + `[weak self]`：struct 是值类型，escaping 闭包里
/// 捕获 `self` 既不能 `weak`，写回的也是副本（拿不到更新）。
final class LyricSearchModel: ObservableObject {
    @Published private(set) var results: [FoxLyricCandidate] = []
    @Published private(set) var isLoading = false
    @Published private(set) var hasMore = false
    @Published private(set) var message: String?

    private var page = 1

    func search(keyword: String, title: String, reset: Bool) {
        if reset {
            page = 1
            results = []
            message = nil
        } else {
            guard hasMore, !isLoading else { return }
            page += 1
        }
        isLoading = true
        let targetPage = page
        FoxPlayerApi.shared.searchLyrics(
            keyword: keyword,
            targetTitle: title,
            page: Int32(targetPage),
            onResult: { [weak self] list, more in
                guard let self else { return }
                if targetPage == 1 {
                    self.results = list
                } else {
                    self.results.append(contentsOf: list)
                }
                self.hasMore = more.boolValue
                self.isLoading = false
                self.message = self.results.isEmpty ? "没找到更合适的歌词" : nil
            },
            onError: { [weak self] msg in
                self?.isLoading = false
                self?.hasMore = false
                self?.message = msg
            }
        )
    }
}
