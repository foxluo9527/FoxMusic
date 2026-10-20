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
    @State private var moreSong: PlayableSong?

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
                        // 仅可展示来源（歌单/专辑/榜单/帖子）才渲染卡片，点击跳对应详情页。
                        // 对齐 Android PlaylistBottomSheet 的 PlaylistSourceHeader。
                        if let source = player.source, source.isDisplayable {
                            Section {
                                Button {
                                    // 先关播放列表面板，再关全屏播放器并跳来源页
                                    // （关播放器延后到 coordinator，避免两层 dismiss 竞态）。
                                    dismiss()
                                    PlayerUICoordinator.shared.openSource(source)
                                } label: {
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
                                                .foregroundStyle(.primary)
                                            Text("来自\(source.typeLabel)")
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        }
                                        Spacer(minLength: 0)
                                        Image(systemName: "chevron.right")
                                            .font(.system(size: 13, weight: .semibold))
                                            .foregroundStyle(.tertiary)
                                    }
                                    .padding(.vertical, 2)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .accessibilityIdentifier("queue.sourceRow")
                            }
                        }

                        Section {
                            ForEach(Array(player.queue.enumerated()), id: \.offset) { index, song in
                                QueueRow(
                                    song: song,
                                    isCurrent: index == player.currentIndex,
                                    isPlaying: player.isPlaying,
                                    onPlay: { player.playQueueItem(at: index) },
                                    onMore: { moreSong = song }
                                )
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        player.removeFromQueue(at: index)
                                    } label: {
                                        Label("删除", systemImage: "trash")
                                    }
                                }
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
        .bottomSheet(isPresented: Binding(
            get: { moreSong != nil },
            set: { if !$0 { moreSong = nil } }
        )) {
            if let song = moreSong {
                SongMoreMenu(
                    musicId: song.id,
                    title: song.title,
                    artist: song.artist,
                    coverUrl: song.coverUrl,
                    sourceUrl: song.audioUrl
                )
            }
        }
    }
}

private struct QueueRow: View {
    let song: PlayableSong
    let isCurrent: Bool
    let isPlaying: Bool
    let onPlay: () -> Void
    let onMore: () -> Void

    var body: some View {
        HStack(spacing: 10) {
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

            Button(action: onMore) {
                Image(systemName: "ellipsis")
                    .font(.system(size: 16))
                    .foregroundStyle(.secondary)
                    .frame(width: 32, height: 32)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("queue.row.more")
        }
    }
}

// MARK: - 评论面板

/// 评论面板 —— 对应 Android `MusicCommentBottomSheet` + 评论楼中楼：
/// 一级评论分页列表（头像/昵称/内容/点赞数/最新回复预览）+ 点赞 + 发表评论；
/// 每条一级评论可「展开 N 条回复」内联翻页，可点「回复」把 parentId 钉到该根评论。
/// 发表与点赞都需要登录，未登录时后端会拒绝，这里把错误原文透出。
struct CommentSheet: View {
    let musicId: Int64
    /// 通知跳转：要定位的评论 ID（可能是根评论本身，也可能在 rootCommentId 的楼里）。
    private let highlightCommentId: Int64?
    private let rootCommentId: Int64?

    @Environment(\.dismiss) private var dismiss
    @StateObject private var model: CommentSheetModel
    @State private var draft = ""

    init(
        musicId: Int64,
        highlightCommentId: Int64? = nil,
        rootCommentId: Int64? = nil,
    ) {
        self.musicId = musicId
        self.highlightCommentId = highlightCommentId
        self.rootCommentId = rootCommentId
        _model = StateObject(wrappedValue: CommentSheetModel(musicId: musicId))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                content

                Divider()

                VStack(spacing: 6) {
                    if let target = model.replyTarget {
                        HStack(spacing: 6) {
                            Text("回复 \(target.name)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Spacer()
                            Button {
                                model.cancelReply()
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 15))
                                    .foregroundStyle(.secondary)
                            }
                            .accessibilityIdentifier("comment.reply.cancel")
                        }
                        .padding(.horizontal, 4)
                    }

                    HStack(spacing: 10) {
                        TextField(
                            model.replyTarget == nil ? "说点什么…" : "写下你的回复…",
                            text: $draft,
                            axis: .vertical
                        )
                        .textFieldStyle(.plain)
                        .lineLimit(1...4)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .accessibilityIdentifier("comment.input")

                        Button {
                            let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
                            guard !text.isEmpty else { return }
                            model.post(content: text) {
                                draft = ""
                            }
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
        .task {
            model.loadFirstPage {
                guard let root = rootCommentId else { return }
                // 通知跳转：自动展开根评论楼，再滚到目标评论。
                model.expandThread(rootId: root, autoScrollTo: highlightCommentId ?? root)
            }
        }
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
            ScrollViewReader { proxy in
                List {
                    ForEach(model.comments, id: \.id) { comment in
                        CommentRow(
                            comment: comment,
                            replies: model.replies[comment.id] ?? [],
                            replyTotal: model.effectiveReplyCount(comment),
                            isExpanded: model.expandedThreads.contains(comment.id),
                            isLoadingReplies: model.loadingReplies.contains(comment.id),
                            replyHasMore: model.replyHasMore[comment.id] ?? false,
                            highlightedId: highlightCommentId,
                            onLike: { model.toggleLike(comment, rootId: nil) },
                            onReply: {
                                model.setReplyTarget(rootId: comment.id, name: comment.authorName)
                            },
                            onToggleExpand: { model.toggleThread(comment) },
                            onLoadMoreReplies: { model.loadMoreReplies(rootId: comment.id) },
                            onReplyToReply: { reply in
                                // 楼中楼里的回复也钉到根评论（与 Android 同口径）
                                model.setReplyTarget(rootId: comment.id, name: reply.authorName)
                            },
                            onLikeReply: { reply in model.toggleLike(reply, rootId: comment.id) }
                        )
                        .listRowSeparator(.hidden)
                        .id(comment.id)
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
                .onReceive(model.$scrollToId) { target in
                    guard let target else { return }
                    // 等回复插入后再定位
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            proxy.scrollTo(target, anchor: .center)
                        }
                    }
                }
            }
        }
    }
}

private struct CommentRow: View {
    let comment: FoxComment
    /// 已展开的楼中楼回复（未展开为空）。
    let replies: [FoxComment]
    let replyTotal: Int32
    let isExpanded: Bool
    let isLoadingReplies: Bool
    let replyHasMore: Bool
    let highlightedId: Int64?
    let onLike: () -> Void
    let onReply: () -> Void
    let onToggleExpand: () -> Void
    let onLoadMoreReplies: () -> Void
    let onReplyToReply: (FoxComment) -> Void
    let onLikeReply: (FoxComment) -> Void

    private var isHighlighted: Bool {
        highlightedId == comment.id
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 10) {
                avatar

                VStack(alignment: .leading, spacing: 4) {
                    Text(comment.authorName)
                        .font(.footnote)
                        .foregroundStyle(.secondary)

                    Text(comment.content)
                        .font(.subheadline)

                    HStack(spacing: 14) {
                        if let time = CommentDateFormatter.relative(comment.createdAt) {
                            Text(time)
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }
                        Button("回复") { onReply() }
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .accessibilityIdentifier("comment.reply.\(comment.id)")
                    }
                }

                Spacer(minLength: 0)

                likeButton(isLiked: comment.isLiked, count: comment.likeCount, action: onLike, id: comment.id)
            }

            if replyTotal > 0 && replies.isEmpty && !isExpanded {
                // 折叠态：沿用后端内嵌的最新一条做预览
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
                Button {
                    onToggleExpand()
                } label: {
                    Text("展开 \(replyTotal) 条回复")
                        .font(.caption)
                        .foregroundStyle(Color.accentColor)
                }
                .accessibilityIdentifier("comment.expand.\(comment.id)")
            }

            if isExpanded || !replies.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(replies, id: \.id) { reply in
                        ReplyRow(
                            reply: reply,
                            isHighlighted: highlightedId == reply.id,
                            onLike: { onLikeReply(reply) },
                            onReply: { onReplyToReply(reply) }
                        )
                        .id(reply.id)
                    }
                    if isLoadingReplies {
                        ProgressView().scaleEffect(0.8)
                    } else {
                        if replyHasMore {
                            Button("查看更多回复") { onLoadMoreReplies() }
                                .font(.caption)
                                .foregroundStyle(Color.accentColor)
                                .accessibilityIdentifier("comment.moreReplies.\(comment.id)")
                        } else if replies.count > 1 {
                            Text("没有更多回复了")
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }
                        Button("收起回复") { onToggleExpand() }
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.leading, 44)
                .padding(.top, 2)
            }
        }
        .padding(.vertical, 6)
        .padding(.horizontal, isHighlighted ? 8 : 0)
        .background {
            if isHighlighted {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(Color.accentColor.opacity(0.14))
            }
        }
    }

    private var avatar: some View {
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
    }

    private func likeButton(
        isLiked: Bool,
        count: Int32,
        action: @escaping () -> Void,
        id: Int64
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Image(systemName: isLiked ? "heart.fill" : "heart")
                    .font(.system(size: 15))
                    .foregroundStyle(isLiked ? Color.red : .secondary)
                Text("\(count)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            .frame(width: 36)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("comment.like.\(id)")
    }
}

/// 楼中楼单条回复：「A 回复 B：内容」+ 时间/回复按钮 + 点赞。
private struct ReplyRow: View {
    let reply: FoxComment
    let isHighlighted: Bool
    let onLike: () -> Void
    let onReply: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 0) {
                    Text(reply.authorName)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.secondary)
                    if let to = reply.replyToName, !to.isEmpty {
                        Text(" 回复 ")
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                        Text(to)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.secondary)
                    }
                    Text("：")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Text(reply.content)
                    .font(.subheadline)

                HStack(spacing: 12) {
                    if let time = CommentDateFormatter.relative(reply.createdAt) {
                        Text(time)
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                    Button("回复") { onReply() }
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .accessibilityIdentifier("comment.reply.\(reply.id)")
                }
            }

            Spacer(minLength: 0)

            Button(action: onLike) {
                HStack(spacing: 3) {
                    Image(systemName: reply.isLiked ? "heart.fill" : "heart")
                        .font(.system(size: 12))
                        .foregroundStyle(reply.isLiked ? Color.red : .secondary)
                    Text("\(reply.likeCount)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                .frame(width: 40, height: 32)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("comment.like.\(reply.id)")
        }
        .padding(8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(isHighlighted ? Color.accentColor.opacity(0.14) : Color.primary.opacity(0.08))
        }
    }
}

/// 回复目标：钉住根评论 ID（楼内回复也用根 ID，与 Android PostReply 同口径）。
struct CommentReplyTarget: Equatable {
    let rootId: Int64
    let name: String
}

/// 评论分页 + 楼中楼回复 + 发表 + 点赞的视图模型。
final class CommentSheetModel: ObservableObject {
    @Published private(set) var comments: [FoxComment] = []
    @Published private(set) var isLoading = false
    @Published private(set) var posting = false
    @Published private(set) var errorMessage: String?

    /// 当前回复目标；nil = 发一级评论。
    @Published var replyTarget: CommentReplyTarget?
    /// 已展开的根评论 ID。
    @Published private(set) var expandedThreads: Set<Int64> = []
    /// 根评论 ID → 已加载回复。
    @Published private(set) var replies: [Int64: [FoxComment]] = [:]
    @Published private(set) var loadingReplies: Set<Int64> = []
    @Published private(set) var replyHasMore: [Int64: Bool] = [:]
    /// 本地自增的回复数（发表回复后立即 +1，不必重拉一级列表）。
    @Published private(set) var localReplyBump: [Int64: Int32] = [:]
    /// 请求列表滚动到某条评论（通知定位用）。
    @Published var scrollToId: Int64?

    private let musicId: Int64
    private var page = 1
    private var hasMore = true
    private var replyPages: [Int64: Int] = [:]

    init(musicId: Int64) {
        self.musicId = musicId
    }

    /// 展示用回复数：服务端值 + 本地刚发的增量。
    func effectiveReplyCount(_ comment: FoxComment) -> Int32 {
        comment.replyCount + (localReplyBump[comment.id] ?? 0)
    }

    func loadFirstPage(onComplete: (() -> Void)? = nil) {
        guard comments.isEmpty else {
            onComplete?()
            return
        }
        page = 1
        hasMore = true
        load(onComplete: onComplete)
    }

    func loadNextPage() {
        guard hasMore, !isLoading else { return }
        page += 1
        load()
    }

    private func load(onComplete: (() -> Void)? = nil) {
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
                onComplete?()
            },
            onError: { [weak self] message in
                self?.isLoading = false
                self?.hasMore = false
                self?.errorMessage = message
                onComplete?()
            }
        )
    }

    // MARK: 回复目标

    func setReplyTarget(rootId: Int64, name: String) {
        replyTarget = CommentReplyTarget(rootId: rootId, name: name)
    }

    func cancelReply() {
        replyTarget = nil
    }

    // MARK: 楼中楼

    func toggleThread(_ root: FoxComment) {
        if expandedThreads.contains(root.id) {
            // 收起：连同已加载回复一起丢弃，回到「最新回复预览 + 展开 N 条」折叠态
            expandedThreads.remove(root.id)
            replies[root.id] = nil
            replyPages[root.id] = nil
            replyHasMore[root.id] = nil
        } else {
            expandThread(rootId: root.id, autoScrollTo: nil)
        }
    }

    /// 展开根评论楼并拉第一页（已展开则只翻页状态切换）。
    func expandThread(rootId: Int64, autoScrollTo: Int64?) {
        if !expandedThreads.contains(rootId) {
            expandedThreads.insert(rootId)
        }
        guard replies[rootId] == nil else {
            if let autoScrollTo { scrollToId = autoScrollTo }
            return
        }
        fetchReplies(rootId: rootId, page: 1) {
            if let autoScrollTo { self.scrollToId = autoScrollTo }
        }
    }

    func loadMoreReplies(rootId: Int64) {
        guard !loadingReplies.contains(rootId) else { return }
        let next = (replyPages[rootId] ?? 0) + 1
        fetchReplies(rootId: rootId, page: next)
    }

    private func fetchReplies(rootId: Int64, page: Int, onDone: (() -> Void)? = nil) {
        loadingReplies.insert(rootId)
        FoxPlayerApi.shared.fetchCommentReplies(
            commentId: rootId,
            page: Int32(page),
            limit: 20,
            onResult: { [weak self] list, total in
                guard let self else { return }
                self.loadingReplies.remove(rootId)
                self.replyPages[rootId] = page
                let existing = self.replies[rootId] ?? []
                let existingIds = Set(existing.map { $0.id })
                let merged = existing + list.filter { !existingIds.contains($0.id) }
                self.replies[rootId] = merged
                self.replyHasMore[rootId] = merged.count < total.intValue
                onDone?()
            },
            onError: { [weak self] message in
                self?.loadingReplies.remove(rootId)
                ToastCenter.shared.show(message)
                onDone?()
            }
        )
    }

    // MARK: 发表

    func post(content: String, onSuccess: @escaping () -> Void) {
        posting = true
        let target = replyTarget
        FoxPlayerApi.shared.postComment(
            musicId: musicId,
            content: content,
            parentId: target.map { KotlinLong(value: $0.rootId) },
            onResult: { [weak self] _ in
                guard let self else { return }
                self.posting = false
                onSuccess()
                if let target {
                    // 回复：清掉目标 + 展开并刷新该楼 + 根评论回复数本地 +1
                    self.replyTarget = nil
                    self.expandedThreads.insert(target.rootId)
                    self.localReplyBump[target.rootId] = (self.localReplyBump[target.rootId] ?? 0) + 1
                    self.replies[target.rootId] = nil
                    self.replyPages[target.rootId] = 0
                    self.replyHasMore[target.rootId] = nil
                    self.fetchReplies(rootId: target.rootId, page: 1)
                    ToastCenter.shared.show("回复已发布")
                } else {
                    // 重新拉第一页，让新评论（倒序）出现在最前
                    self.comments = []
                    self.loadFirstPage()
                    ToastCenter.shared.show("评论已发布")
                }
            },
            onError: { [weak self] message in
                self?.posting = false
                ToastCenter.shared.show(message)
            }
        )
    }

    // MARK: 点赞

    func toggleLike(_ comment: FoxComment, rootId: Int64?) {
        FoxPlayerApi.shared.likeComment(
            commentId: comment.id,
            onResult: { [weak self] _ in
                guard let self else { return }
                // 后端不回传新状态，本地取反（与 Android 同策略）
                let updated = Self.toggled(comment)
                if let rootId {
                    guard let list = self.replies[rootId],
                          let idx = list.firstIndex(where: { $0.id == comment.id }) else { return }
                    var next = list
                    next[idx] = updated
                    self.replies[rootId] = next
                } else if let idx = self.comments.firstIndex(where: { $0.id == comment.id }) {
                    self.comments[idx] = updated
                }
            },
            onError: { message in
                ToastCenter.shared.show(message)
            }
        )
    }

    private static func toggled(_ old: FoxComment) -> FoxComment {
        FoxComment(
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
            latestReplyAuthor: old.latestReplyAuthor,
            parentId: old.parentId
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

                // 无翻译歌词时不展示「歌词翻译」开关（没有可切换的内容）
                if player.hasTranslatedLyrics {
                    Section {
                        Toggle("歌词翻译", isOn: $player.showTranslation)
                            .accessibilityIdentifier("lyricSettings.translation")
                    } footer: {

                    }
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

/// 歌词纠正的导航路由：用 `NavigationStack(path:)` + `navigationDestination(for:)` 做程序化 push。
/// 之所以不直接用 `FoxLyricCandidate`（Kotlin data class 在 Swift 里不遵守 Hashable），
/// 而是包一层，是为了拿到稳定的 Hashable 身份，避免 `navigationDestination(isPresented:)`
/// 在 List 分页刷新 `results` 时偶发丢失 push（test18 因此偶发失败）。
private struct LyricCandidateRoute: Hashable {
    let id: String
    let candidate: FoxLyricCandidate
    static func == (lhs: LyricCandidateRoute, rhs: LyricCandidateRoute) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
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
    /// 导航栈：点候选 push 预览页，由稳定的 path 驱动（不随 results 刷新而丢失）。
    @State private var path = NavigationPath()

    var body: some View {
        NavigationStack(path: $path) {
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
            .navigationDestination(for: LyricCandidateRoute.self) { route in
                LyricPreviewPage(candidate: route.candidate) {
                    player.applyLyrics(lyrics: route.candidate.lyrics, translation: route.candidate.lyricsTrans)
                    onApplied()
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
                    NavigationLink(value: LyricCandidateRoute(id: item.id, candidate: item)) {
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
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
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
