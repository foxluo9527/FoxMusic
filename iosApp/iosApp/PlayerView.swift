import SwiftUI
import FoxShared
import AVKit

/// 全屏播放器（批次 28）：与 Android PlayerScreen 功能对齐，视觉走 iOS 风格。
///
/// 对齐 Android 的能力清单：
/// 1. 双页结构：歌曲页 / 歌词页（点页签切换，歌曲页点歌词区也能跳到歌词页）
/// 2. 歌曲页：标题歌手 → 封面（长按看大图）→ 当前/下一句歌词 → 进度条 → 控制行
/// 3. 控制行：循环模式（顺序/列表循环/单曲循环/随机）、上一首、播放暂停（缓冲转圈）、下一首、收藏
/// 4. 歌词页：双语歌词、当前行居中高亮、点击行跳转、底部评论入口 + 播放键
/// 5. 三个面板：播放列表（来源/切歌/删单曲/清空）、评论（列表/点赞/发表）、歌词设置（字号/翻译/歌词纠正）
/// 6. 背景：随封面取色的动态背景（Android 是封面取色 + 白色蒙版，这里用封面上糊化 +
///    暗色蒙版，同一能力、iOS 观感）
struct PlayerView: View {
    @ObservedObject private var player = PlayerModel.shared
    @Environment(\.dismiss) private var dismiss

    @State private var stage: PlayerStage = .song
    @State private var activeSheet: PlayerSheet?
    @State private var coverPreview = false

    var body: some View {
        ZStack {
            PlayerBackground(coverUrl: player.current?.coverUrl)

            VStack(spacing: 0) {
                topBar

                TabView(selection: $stage) {
                    SongStageView(
                        onOpenLyric: { withAnimation { stage = .lyric } },
                        onCoverLongPress: {
                            if player.current?.coverUrl != nil { coverPreview = true }
                        }
                    )
                    .tag(PlayerStage.song)

                    LyricStageView(onOpenComments: { activeSheet = .comments })
                        .tag(PlayerStage.lyric)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .animation(.easeInOut(duration: 0.22), value: stage)
            }
            // 显式撑满：不写的话宽度会被子视图的 ideal size 决定（见 PlayerBackground 注释）
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .preferredColorScheme(.dark)
        // 整页控件用白色描边：深色播放页里默认的 tint（系统蓝）会破坏暗色调，
        // 收藏激活态仍单独用红色（与 Android SongPage 的 Color.Red 一致）
        .tint(.white)
        .fullScreenCover(isPresented: $coverPreview) {
            CoverPreviewView(url: player.current?.coverUrl)
        }
        // 三个面板合成**一个** sheet(item:)：同一视图挂多个 .sheet 时呈现顺序不保证，
        // 实测会出现"点了设置但面板没弹"的偶发失败（UI 测试 test13 抓到的），
        // 用单一 item 驱动是 SwiftUI 推荐写法。
        .sheet(item: $activeSheet) { sheet in
            switch sheet {
            case .queue:
                QueueSheet()
            case .comments:
                CommentSheet(musicId: Int64(player.current?.id ?? "") ?? 0)
            case .lyrics:
                LyricSettingsSheet()
            }
        }
        .onDisappear {
            activeSheet = nil
        }
    }

    private var topBar: some View {
        HStack(spacing: 12) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.down")
                    .font(.system(size: 18, weight: .semibold))
                    .frame(width: 34, height: 34)
                    .contentShape(Rectangle())
            }
            .accessibilityIdentifier("player.close")
            .accessibilityLabel("关闭播放器")

            Spacer(minLength: 0)

            StageSwitch(stage: $stage)

            Spacer(minLength: 0)

            // 右侧按页签切换：歌曲页 = 播放列表，歌词页 = 歌词设置（与 Android 一致）
            if stage == .song {
                Button {
                    activeSheet = .queue
                } label: {
                    Image(systemName: "list.bullet")
                        .font(.system(size: 18, weight: .medium))
                        .frame(width: 34, height: 34)
                        .contentShape(Rectangle())
                }
                .accessibilityIdentifier("player.queueButton")
                .accessibilityLabel("播放列表")
            } else {
                Button {
                    activeSheet = .lyrics
                } label: {
                    Image(systemName: "textformat.size")
                        .font(.system(size: 18, weight: .medium))
                        .frame(width: 34, height: 34)
                        .contentShape(Rectangle())
                }
                .accessibilityIdentifier("player.settingsButton")
                .accessibilityLabel("歌词设置")
            }
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }
}

private enum PlayerStage: Int, Hashable {
    case song = 0
    case lyric = 1
}

/// 播放器内的三个弹出面板（同一时刻只会有一个）。
private enum PlayerSheet: String, Identifiable {
    case queue
    case comments
    case lyrics

    var id: String { rawValue }
}

/// 页签切换：iOS 风格的两个文字标签 + 选中胶囊。
private struct StageSwitch: View {
    @Binding var stage: PlayerStage

    var body: some View {
        HStack(spacing: 2) {
            segment("歌曲", .song, id: "player.stage.song")
            segment("歌词", .lyric, id: "player.stage.lyric")
        }
        .padding(2)
        .background(.white.opacity(0.14), in: Capsule())
    }

    private func segment(_ title: String, _ value: PlayerStage, id: String) -> some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) { stage = value }
        } label: {
            Text(title)
                .font(.system(size: 14, weight: stage == value ? .semibold : .regular))
                .foregroundStyle(stage == value ? Color.black.opacity(0.85) : .white.opacity(0.75))
                .frame(width: 58, height: 28)
                .background(
                    Capsule().fill(stage == value ? Color.white : Color.clear)
                )
        }
        .accessibilityIdentifier(id)
    }
}

// MARK: - 动态背景

/// 随封面变化的全屏背景：封面糊化 + 暗色蒙版。
/// 封面缺失时退化为深色渐变，保证任何情况下文字都可读。
///
/// ⚠️ 必须用 GeometryReader 给图片**显式尺寸**再 clipped。
/// 坑在哪：`resizable().scaledToFill()`（= aspectRatio(contentMode: .fill)）会返回一个
/// **在两个方向上都 ≥ 提案**的尺寸。本项目封面是 1425×1426 的正方形，在 402×778 的
/// 提案（778 = 屏高 874 − 顶部安全区 62 − 底部安全区 34）下，按高度撑满会得到 778×778。
/// 这个 778 就是 PlayerBackground 内层 ZStack 的布局尺寸，再往上传给 body 的根 ZStack；
/// 根 ZStack 被撑到 778 宽后在 402 宽的屏上居中，左右各溢出 188pt ——
/// 实测顶部关闭键落在 x=-172、列表键 x=540、循环键 x=-170、收藏键 x=528，
/// 全在屏幕外，页面只剩中间的歌名+封面+进度+播放键，看着就是个"非常简易的播放页"。
/// 触发时机是**封面异步加载完成那一刻**（加载中只有渐变背景，尺寸正常），
/// 于是表现为"进播放页后立即跳成一个简单页面"。修法就是这里：显式 frame + clipped。
///
/// 同项目其余 4 处 `scaledToFill`（迷你播放器 44×44、播放列表来源 44×44、
/// 评论头像 34×34、封面页 268×268）都已带显式 frame，不受影响。
private struct PlayerBackground: View {
    let coverUrl: String?

    var body: some View {
        GeometryReader { geo in
            ZStack {
                LinearGradient(
                    colors: [Color(white: 0.16), Color(white: 0.05)],
                    startPoint: .top,
                    endPoint: .bottom
                )

                if let urlStr = coverUrl, let url = URL(string: urlStr) {
                    AsyncImage(url: url) { phase in
                        if let image = phase.image {
                            image
                                .resizable()
                                .scaledToFill()
                                .frame(width: geo.size.width, height: geo.size.height)
                                .clipped()
                                .blur(radius: 64, opaque: true)
                                .overlay(Color.black.opacity(0.42))
                        }
                    }
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .clipped()
        }
        .ignoresSafeArea()
        .animation(.easeInOut(duration: 0.35), value: coverUrl)
    }
}

// MARK: - 歌曲页

/// 歌曲页：标题歌手 → 封面 → 当前/下一句歌词 → 进度条 → 控制行。
/// 顺序与 Android SongPage 保持一致（Android 就是标题在封面上方）。
private struct SongStageView: View {
    @ObservedObject private var player = PlayerModel.shared
    let onOpenLyric: () -> Void
    let onCoverLongPress: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 8)

            VStack(spacing: 6) {
                Text(player.current?.title ?? "未在播放")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .accessibilityIdentifier("fullPlayer.title")
                Text(player.current?.artist ?? "")
                    .font(.system(size: 14))
                    .foregroundStyle(.white.opacity(0.62))
                    .lineLimit(1)
            }
            .padding(.horizontal, 32)

            Spacer(minLength: 16).frame(maxHeight: 26)

            CoverArtView(coverUrl: player.current?.coverUrl)
                .onLongPressGesture(minimumDuration: 0.35) { onCoverLongPress() }
                .accessibilityIdentifier("player.cover")

            // 当前句 / 下一句歌词（点一下切到歌词页，与 Android 一致）
            Button(action: onOpenLyric) {
                VStack(spacing: 6) {
                    Text(player.currentLyricText ?? " ")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    Text(player.nextLyricText ?? " ")
                        .font(.system(size: 15))
                        .foregroundStyle(.white.opacity(0.55))
                        .lineLimit(1)
                        .truncationMode(.tail)
                }
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 32)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("player.lyricPeek")
            .padding(.top, 18)

            Spacer(minLength: 12)

            ProgressBar(
                value: player.currentTime,
                total: max(player.duration, 0.1),
                onSeek: { player.seek(to: $0) }
            )
            .padding(.horizontal, 22)
            .padding(.top, 14)

            PlaybackControls()
                .padding(.horizontal, 18)
                .padding(.top, 10)
                .padding(.bottom, 22)
        }
    }
}

/// 封面：大圆角 + 阴影，可复用于预览。
private struct CoverArtView: View {
    let coverUrl: String?
    var size: CGFloat = 268

    var body: some View {
        Group {
            if let urlStr = coverUrl, let url = URL(string: urlStr) {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    } else {
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .shadow(color: .black.opacity(0.45), radius: 22, y: 10)
        .overlay {
            // 换封面时轻微淡入，避免闪白
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(.white.opacity(0.08), lineWidth: 1)
        }
    }

    private var placeholder: some View {
        ZStack {
            Color(white: 0.22)
            Image(systemName: "music.note")
                .font(.system(size: 44))
                .foregroundStyle(.white.opacity(0.35))
        }
    }
}

// MARK: - 进度条

/// 进度条：iOS 风格细轨 + 圆点把手，拖动时实时更新、松手 seek。
/// Android 用 Media3 的 Slider（带时间标签），这里补上左右时间，能力对齐。
private struct ProgressBar: View {
    let value: Double
    let total: Double
    let onSeek: (Double) -> Void

    @State private var dragging: Double?

    private var display: Double { dragging ?? value }
    private var ratio: Double {
        guard total > 0 else { return 0 }
        return min(max(display / total, 0), 1)
    }

    var body: some View {
        VStack(spacing: 6) {
            GeometryReader { geo in
                let width = geo.size.width
                let filled = width * ratio

                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(.white.opacity(0.22))
                        .frame(height: dragging == nil ? 4 : 6)
                    Capsule()
                        .fill(.white)
                        .frame(width: max(filled, 0), height: dragging == nil ? 4 : 6)
                    Circle()
                        .fill(.white)
                        .frame(width: dragging == nil ? 10 : 14, height: dragging == nil ? 10 : 14)
                        .shadow(color: .black.opacity(0.3), radius: 3)
                        .offset(x: max(filled - (dragging == nil ? 5 : 7), 0))
                }
                .frame(height: 22)
                .contentShape(Rectangle())
                // accessibilityElement() 让它成为**单一**元素：不加的话 identifier 会
                // 挂到子树多个元素上，UI 测试查询会报"匹配到多个"
                .accessibilityElement()
                .accessibilityIdentifier("player.progress")
                .accessibilityValue("\(Int(ratio * 100))%")
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { g in
                            let r = min(max(g.location.x / max(width, 1), 0), 1)
                            dragging = r * total
                        }
                        .onEnded { _ in
                            if let target = dragging { onSeek(target) }
                            dragging = nil
                        }
                )
            }
            .frame(height: 22)

            HStack {
                Text(Self.format(display))
                    .accessibilityIdentifier("player.progress.current")
                Spacer()
                Text(Self.format(total))
                    .accessibilityIdentifier("player.progress.total")
            }
            .font(.system(size: 11).monospacedDigit())
            .foregroundStyle(.white.opacity(0.6))
        }
        .animation(.easeOut(duration: 0.15), value: dragging == nil)
    }

    static func format(_ t: Double) -> String {
        guard t.isFinite, t > 0 else { return "0:00" }
        let total = Int(t)
        return "\(total / 60):\(String(format: "%02d", total % 60))"
    }
}

// MARK: - 控制行

/// 控制行：循环模式 / 上一首 / 播放暂停 / 下一首 / 收藏。
/// 按钮排布与 Android SongPage 完全一致（5 个），播放键在正中并在缓冲时转圈。
private struct PlaybackControls: View {
    @ObservedObject private var player = PlayerModel.shared

    var body: some View {
        HStack {
            Button {
                player.cycleRepeatMode()
            } label: {
                Image(systemName: player.repeatMode.symbolName)
                    .font(.system(size: 19, weight: .medium))
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityIdentifier("player.repeat")
            .accessibilityLabel(player.repeatMode.label)

            Spacer(minLength: 0)

            Button {
                player.previous()
            } label: {
                Image(systemName: "backward.fill")
                    .font(.system(size: 25))
                    .frame(width: 52, height: 52)
                    .contentShape(Rectangle())
            }
            .accessibilityIdentifier("player.previous")
            .accessibilityLabel("上一首")

            Spacer(minLength: 0)

            Button {
                player.togglePlayPause()
            } label: {
                ZStack {
                    if player.isLoading {
                        ProgressView()
                            .progressViewStyle(.circular)
                            .tint(.white)
                            .scaleEffect(1.25)
                    } else {
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 30))
                    }
                }
                .frame(width: 66, height: 66)
                .background(.white.opacity(0.14), in: Circle())
                .contentShape(Circle())
            }
            .accessibilityIdentifier("player.playPause")
            .accessibilityLabel(player.isPlaying ? "暂停" : "播放")

            Spacer(minLength: 0)

            Button {
                player.next()
            } label: {
                Image(systemName: "forward.fill")
                    .font(.system(size: 25))
                    .frame(width: 52, height: 52)
                    .contentShape(Rectangle())
            }
            .accessibilityIdentifier("player.next")
            .accessibilityLabel("下一首")

            Spacer(minLength: 0)

            Button {
                player.toggleFavorite()
            } label: {
                Image(systemName: player.isFavorite ? "heart.fill" : "heart")
                    .font(.system(size: 21, weight: .medium))
                    .foregroundStyle(player.isFavorite ? Color.red : .white)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityIdentifier("player.favorite")
            .accessibilityLabel(player.isFavorite ? "取消收藏" : "收藏")
        }
    }
}

// MARK: - 歌词页

/// 歌词页：双语歌词（当前行居中高亮）+ 点击行跳转 + 底部评论入口与播放键。
private struct LyricStageView: View {
    @ObservedObject private var player = PlayerModel.shared
    let onOpenComments: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            if player.lyricLines.isEmpty {
                Spacer()
                VStack(spacing: 8) {
                    Image(systemName: "text.alignleft")
                        .font(.system(size: 30))
                        .foregroundStyle(.white.opacity(0.4))
                    Text("暂无歌词")
                        .font(.system(size: 15))
                        .foregroundStyle(.white.opacity(0.55))
                    Text("可在右上角「歌词设置」里纠正歌词")
                        .font(.system(size: 12))
                        .foregroundStyle(.white.opacity(0.35))
                }
                Spacer()
            } else {
                LyricsScrollView()
            }

            bottomBar
        }
    }

    private var bottomBar: some View {
        HStack(spacing: 12) {
            Button(action: onOpenComments) {
                HStack(spacing: 8) {
                    Image(systemName: "bubble.left")
                        .font(.system(size: 15))
                    Text(player.commentCount > 0 ? "\(player.commentCount) 条评论" : "暂无评论")
                        .font(.system(size: 14, weight: .medium))
                        .lineLimit(1)
                    Spacer(minLength: 0)
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .frame(height: 40)
                .background(.white.opacity(0.14), in: Capsule())
                .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("player.commentEntry")

            Button {
                player.togglePlayPause()
            } label: {
                ZStack {
                    if player.isLoading {
                        ProgressView().tint(.white).scaleEffect(0.9)
                    } else {
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 16))
                    }
                }
                .foregroundStyle(.white)
                .frame(width: 40, height: 40)
                .background(.white.opacity(0.14), in: Circle())
                .contentShape(Circle())
            }
            .accessibilityIdentifier("player.lyricPlayPause")
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 12)
    }
}

/// 歌词滚动区：当前行居中高亮、自动跟随、点击行跳转。
private struct LyricsScrollView: View {
    @ObservedObject private var player = PlayerModel.shared
    /// 用户手动滚动后暂停自动跟随 2 秒（Android 的 nextAutoScrollableTime 同款策略）。
    @State private var autoScrollPausedUntil = Date.distantPast

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 14) {
                    // 上下留白，让首尾行也能滚到屏幕中央
                    Color.clear.frame(height: 120)

                    ForEach(player.lyricLines) { line in
                        lyricRow(line)
                            .id(line.id)
                    }

                    Color.clear.frame(height: 120)
                }
                .padding(.horizontal, 24)
            }
            .accessibilityIdentifier("player.lyrics")
            .onChange(of: player.currentLyricIndex) { idx in
                guard idx >= 0, Date() >= autoScrollPausedUntil else { return }
                withAnimation(.easeInOut(duration: 0.3)) {
                    proxy.scrollTo(idx, anchor: .center)
                }
            }
            // 用户手动滚动时暂停自动跟随 2 秒。
            // ⚠️ 不能用 .simultaneousGesture(DragGesture()) 实现：SwiftUI 的 DragGesture
            // 会截走 TabView(.page) 的横向滑动，歌词页就滑不回歌曲页了（实测）。
            // iOS 18+ 用 onScrollPhaseChange（程序化 scrollTo 是 .animating，不会误触发）；
            // 更低版本退化为不暂停，仅影响边角体验，不能因此牺牲双页滑动。
            .onInteractingScroll {
                autoScrollPausedUntil = Date().addingTimeInterval(2)
            }
            .onAppear {
                guard player.currentLyricIndex >= 0 else { return }
                proxy.scrollTo(player.currentLyricIndex, anchor: .center)
            }
        }
    }

    private func lyricRow(_ line: LyricLine) -> some View {
        let isActive = line.id == player.currentLyricIndex
        return Button {
            player.seekToLyric(line)
        } label: {
            VStack(spacing: 3) {
                Text(line.text)
                    .font(.system(size: isActive ? player.lyricFontSize + 4 : player.lyricFontSize,
                                  weight: isActive ? .bold : .regular))
                    .foregroundStyle(isActive ? Color.white : .white.opacity(0.45))
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)

                if player.showTranslation, let trans = line.translation, !trans.isEmpty {
                    Text(trans)
                        .font(.system(size: isActive ? player.lyricFontSize - 1 : player.lyricFontSize - 3))
                        .foregroundStyle(isActive ? .white.opacity(0.85) : .white.opacity(0.32))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - 滚动辅助

extension View {
    /// 检测用户正在手动滚动（仅 iOS 18+ 生效，低版本是 no-op）。
    @ViewBuilder
    func onInteractingScroll(_ action: @escaping () -> Void) -> some View {
        if #available(iOS 18.0, *) {
            onScrollPhaseChange { _, newPhase in
                if newPhase == .interacting { action() }
            }
        } else {
            self
        }
    }
}

// MARK: - 封面大图预览

/// 封面长按预览（Android 是 PostImagePreviewDialog，支持缩放）。
private struct CoverPreviewView: View {
    let url: String?
    @Environment(\.dismiss) private var dismiss

    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let urlStr = url, let u = URL(string: urlStr) {
                AsyncImage(url: u) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFit()
                            .scaleEffect(scale)
                            .gesture(
                                MagnificationGesture()
                                    .onChanged { scale = lastScale * $0 }
                                    .onEnded { _ in lastScale = min(max(scale, 1), 4) }
                            )
                    } else {
                        ProgressView().tint(.white)
                    }
                }
            }
            VStack {
                HStack {
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 36, height: 36)
                            .background(.white.opacity(0.16), in: Circle())
                    }
                    .accessibilityIdentifier("coverPreview.close")
                }
                .padding(16)
                Spacer()
            }
        }
    }
}
