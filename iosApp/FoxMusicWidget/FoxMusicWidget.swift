import WidgetKit
import SwiftUI
import ActivityKit
import UIKit

// MARK: - Widget & Live Activity 配置

@main
struct FoxMusicWidgetBundle: WidgetBundle {
    var body: some Widget {
        FoxMusicLiveActivity()
    }
}

struct FoxMusicLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: FoxMusicActivityAttributes.self) { context in
            // 锁屏实时活动卡片
            LockScreenLyricsView(state: context.state)
        } dynamicIsland: { context in
            DynamicIsland {
                // 展开态（长按灵动岛）：Apple Music 风格 —— 封面+歌名/歌手+声波，进度行（含剩余时间），五键控制行
                DynamicIslandExpandedRegion(.leading) {
                    LiveArtworkView(data: context.state.artwork, size: 56, cornerRadius: 14)
                        .padding(.leading, 2)
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(alignment: .leading, spacing: 3) {
                        // 第一行：歌名 - 歌手；第二行：当前歌词（无歌词回退歌手名）
                        Text("\(context.state.title) - \(context.state.artist)")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        Text(context.state.lyric.isEmpty ? context.state.artist : context.state.lyric)
                            .font(.system(size: 12))
                            .foregroundStyle(.white.opacity(0.75))
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 10)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Image(systemName: "waveform")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.trailing, 2)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 14) {
                        // 进度行：已播 + 进度条 + 剩余（负号）
                        LiveProgressBar(state: context.state, height: 5, showRemaining: true,
                                        font: .system(size: 13, weight: .medium))

                        // 控制行：收藏（红心）· 上一首 · 播放/暂停 · 下一首 · 耳机（装饰）
                        HStack(spacing: 0) {
                            Button(intent: ToggleFavoriteIntent()) {
                                Image(systemName: context.state.isFavorite ? "heart.fill" : "heart")
                                    .font(.system(size: 22, weight: .medium))
                                    .foregroundStyle(context.state.isFavorite ? Color.red : .white)
                            }
                            .buttonStyle(.plain)

                            Spacer()

                            Button(intent: PreviousTrackIntent()) {
                                Image(systemName: "backward.fill")
                                    .font(.system(size: 22, weight: .bold))
                                    .foregroundStyle(.white)
                            }
                            .buttonStyle(.plain)

                            Spacer()

                            LivePlaybackButton(isPlaying: context.state.isPlaying) {
                                Image(systemName: context.state.isPlaying ? "pause.fill" : "play.fill")
                                    .font(.system(size: 30, weight: .bold))
                                    .foregroundStyle(.white)
                            }

                            Spacer()

                            Button(intent: NextTrackIntent()) {
                                Image(systemName: "forward.fill")
                                    .font(.system(size: 22, weight: .bold))
                                    .foregroundStyle(.white)
                            }
                            .buttonStyle(.plain)

                            Spacer()

                            Image(systemName: "headphones")
                                .font(.system(size: 22, weight: .medium))
                                .foregroundStyle(.white.opacity(0.85))
                        }
                        .padding(.horizontal, 8)
                    }
                    .padding(.horizontal, 2)
                }
            } compactLeading: {
                // 压缩态左侧：单行跑马灯歌词（约 6.5 个字宽，长句自动滚动）
                CompactLyrics(state: context.state)
            } compactTrailing: {
                // 压缩态右侧：圆形封面填满右插槽（点封面即可播放/暂停，不损失控制能力）
                LivePlaybackButton(isPlaying: context.state.isPlaying) {
                    LiveArtworkView(data: context.state.artwork, size: 24, cornerRadius: 12)
                        .clipShape(Circle())
                }
            } minimal: {
                Image(systemName: context.state.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(.white)
            }
        }
    }
}

// MARK: - 封面

struct LiveArtworkView: View {
    let data: Data?
    let size: CGFloat
    var cornerRadius: CGFloat

    var body: some View {
        Group {
            if let data, let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else {
                ZStack {
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .fill(.white.opacity(0.14))
                    Image(systemName: "music.note")
                        .font(.system(size: size * 0.46, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.85))
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

// MARK: - 压缩态歌词（左侧单行跑马灯，容纳约 6 个半字，放不下滚动；参考「灵动歌词」形态）

struct CompactLyrics: View {
    let state: FoxMusicActivityAttributes.ContentState

    private static let font = UIFont.systemFont(ofSize: 10.5, weight: .medium)
    /// 插槽宽度：约 6.5 个中文字（10.5pt 字宽 ≈ 10.5pt），长句自动跑马灯
    private static let slotWidth: CGFloat = 70

    var body: some View {
        let text = state.lyric.isEmpty ? state.title : state.lyric
        MarqueeText(text: text, font: Self.font, isPlaying: state.isPlaying, anchor: state.updatedAt,
                    width: Self.slotWidth, height: 15, opacity: 0.95)
    }
}

/// Widget 内可用的跑马灯文本：TimelineView 驱动（系统在岛不可见时自动挂起重绘），
/// Canvas 双份文本循环左移；文本放得下时静态展示。
/// 相位锚定本句歌词下发时刻 anchor：先停留 hold 秒便于读句首，再匀速滚完；暂停时停住。
struct MarqueeText: View {
    let text: String
    let font: UIFont
    let isPlaying: Bool
    let anchor: Date
    var width: CGFloat = 70
    var height: CGFloat = 15
    var opacity: Double = 0.92
    var speed: CGFloat = 32
    var gap: CGFloat = 16
    var hold: Double = 1.6

    private var textWidth: CGFloat {
        (text as NSString).size(withAttributes: [.font: font]).width
    }

    var body: some View {
        // 固定宽度槽位（compact leading 插槽按内容 ideal 宽度分配，柔性 Canvas 会被收成 0）。
        TimelineView(.periodic(from: anchor, by: 1.0 / 20.0)) { ctx in
            Canvas { context, _ in
                guard !text.isEmpty else { return }
                let tw = textWidth
                let resolved = context.resolve(
                    Text(text).font(Font(font)).foregroundStyle(.white.opacity(opacity))
                )
                let y: CGFloat = height / 2
                let elapsed = ctx.date.timeIntervalSince(anchor)

                if tw <= width {
                    // 放得下：水平居中静态显示
                    let x = (width - tw) / 2
                    context.draw(resolved, at: CGPoint(x: x, y: y), anchor: UnitPoint(x: 0, y: 0.5))
                } else {
                    // 放不下：先停留 hold 秒读句首，再匀速循环左移；暂停时停住
                    let cycle = tw + gap
                    let scrollDur = max(Double(cycle / speed), 0.1)
                    var x: CGFloat = 0
                    if isPlaying, elapsed > hold {
                        let phase = (elapsed - hold).truncatingRemainder(dividingBy: hold + scrollDur)
                        let p = min(max(phase / scrollDur, 0), 1)
                        x = -CGFloat(p) * cycle
                    }
                    context.draw(resolved, at: CGPoint(x: x, y: y), anchor: UnitPoint(x: 0, y: 0.5))
                    context.draw(resolved, at: CGPoint(x: x + cycle, y: y), anchor: UnitPoint(x: 0, y: 0.5))
                }
            }
            .frame(width: width, height: height)
            .clipped()
            .overlay(alignment: .trailing) {
                // 仅长句滚动时右缘渐隐；短句不遮字
                LinearGradient(
                    colors: [.black.opacity(0), .black.opacity(0.9)],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .frame(width: 8)
                .opacity(textWidth > width ? 1 : 0)
                .allowsHitTesting(false)
            }
        }
        .frame(width: width, height: 15)
        .fixedSize()
    }
}

// MARK: - 播放状态指示（圆形按钮样式，仅展示不可点，与网易云/QQ 一致）

struct PlayStateIndicator: View {
    let isPlaying: Bool
    let diameter: CGFloat

    var body: some View {
        ZStack {
            Circle()
                .fill(.white.opacity(0.16))
            Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                .font(.system(size: diameter * 0.38, weight: .bold))
                .foregroundStyle(.white)
                .padding(.leading, isPlaying ? 0 : diameter * 0.06)
        }
        .frame(width: diameter, height: diameter)
    }
}

// MARK: - 播放/暂停交互按钮（iOS 17+，点击就地执行，不跳转 App）

struct LivePlaybackButton<Label: View>: View {
    let isPlaying: Bool
    @ViewBuilder let label: () -> Label

    var body: some View {
        if isPlaying {
            Button(intent: PausePlaybackIntent()) { label() }
                .buttonStyle(.plain)
        } else {
            Button(intent: ResumePlaybackIntent()) { label() }
                .buttonStyle(.plain)
        }
    }
}

// MARK: - 实时进度条（本地每秒推进，不消耗 Activity 更新预算）

struct LiveProgressBar: View {
    let state: FoxMusicActivityAttributes.ContentState
    let height: CGFloat
    var showTimes: Bool = false
    /// true：已播时间 + 进度条 + 剩余时间（-m:ss）同行，Apple Music 展开态样式
    var showRemaining: Bool = false
    var font: Font = .caption2

    var body: some View {
        TimelineView(.periodic(from: state.updatedAt, by: 1)) { context in
            let duration = max(state.duration, 0.1)
            let elapsed: Double = {
                guard state.isPlaying else { return state.currentTime }
                return min(state.currentTime + context.date.timeIntervalSince(state.updatedAt), duration)
            }()
            let progress = min(max(elapsed / duration, 0), 1)

            if showRemaining {
                HStack(spacing: 10) {
                    Text(Self.timeString(elapsed))
                        .font(font)
                        .foregroundStyle(.white.opacity(0.9))
                        .monospacedDigit()
                    bar(progress: progress)
                    Text("-" + Self.timeString(max(duration - elapsed, 0)))
                        .font(font)
                        .foregroundStyle(.white.opacity(0.9))
                        .monospacedDigit()
                }
            } else {
                VStack(spacing: 3) {
                    bar(progress: progress)

                    if showTimes {
                        HStack {
                            Text(Self.timeString(elapsed))
                            Spacer()
                            Text(Self.timeString(duration))
                        }
                        .font(font)
                        .foregroundStyle(.white.opacity(0.5))
                        .monospacedDigit()
                    }
                }
            }
        }
    }

    private func bar(progress: Double) -> some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(.white.opacity(0.22))
                Capsule()
                    .fill(.white)
                    .frame(width: geo.size.width * progress)
            }
        }
        .frame(height: height)
    }

    static func timeString(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "00:00" }
        let total = Int(seconds)
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}

// MARK: - 锁屏实时活动卡片（参考网易云/QQ音乐：黑底 + 封面 + 文案 + 状态 + 进度）

struct LockScreenLyricsView: View {
    let state: FoxMusicActivityAttributes.ContentState

    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                LiveArtworkView(data: state.artwork, size: 52, cornerRadius: 12)

                VStack(alignment: .leading, spacing: 2) {
                    Text(state.title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Text(state.artist)
                        .font(.system(size: 13))
                        .foregroundStyle(.white.opacity(0.55))
                        .lineLimit(1)
                    HStack(spacing: 5) {
                        Image(systemName: "music.quarternote.3")
                            .font(.system(size: 9))
                            .foregroundStyle(.white.opacity(0.45))
                        Text(state.lyric.isEmpty ? "FoxMusic" : state.lyric)
                            .font(.system(size: 12))
                            .foregroundStyle(.white.opacity(0.75))
                            .lineLimit(1)
                    }
                    .padding(.top, 1)
                }

                Spacer(minLength: 0)

                LivePlaybackButton(isPlaying: state.isPlaying) {
                    PlayStateIndicator(isPlaying: state.isPlaying, diameter: 40)
                }
            }

            LiveProgressBar(state: state, height: 3.5, showTimes: true, font: .system(size: 10, weight: .medium))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity)
        .background(Color.black)
    }
}
