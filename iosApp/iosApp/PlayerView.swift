import SwiftUI
import FoxShared

/// 全屏播放器（批次 23）：复用 Swift 侧 PlayerModel 的播放状态与歌词。
/// 封面 / 标题 / 艺术家 / 进度条 / 逐行高亮歌词。点击迷你条进入，下拉关闭。
struct PlayerView: View {
    @ObservedObject private var player = PlayerModel.shared
    @Environment(\.dismiss) private var dismiss

    private func format(_ t: Double) -> String {
        guard t.isFinite, t > 0 else { return "0:00" }
        let total = Int(t)
        return "\(total / 60):\(String(format: "%02d", total % 60))"
    }

    var body: some View {
        ZStack {
            // 背景：随封面取色的暗色渐变
            LinearGradient(
                colors: [Color(white: 0.12), Color(white: 0.04)],
                startPoint: .top, endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // 顶部栏
                HStack {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "chevron.down")
                            .font(.system(size: 22, weight: .semibold))
                    }
                    Spacer()
                    Text("正在播放")
                        .font(.headline)
                    Spacer()
                    // 占位保持对称
                    Image(systemName: "chevron.down").opacity(0)
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 20)
                .padding(.top, 12)

                ScrollView {
                    VStack(spacing: 24) {
                        // 封面
                        if let urlStr = player.current?.coverUrl, let url = URL(string: urlStr) {
                            AsyncImage(url: url) { phase in
                                if let image = phase.image {
                                    image.resizable().scaledToFill()
                                } else {
                                    Color(white: 0.2)
                                }
                            }
                            .frame(width: 260, height: 260)
                            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                            .shadow(radius: 20)
                            .accessibilityIdentifier("player.cover")
                        } else {
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .fill(Color(white: 0.2))
                                .frame(width: 260, height: 260)
                                .accessibilityIdentifier("player.cover")
                        }

                        // 标题 / 艺术家
                        VStack(spacing: 6) {
                            Text(player.current?.title ?? "")
                                .font(.title2.weight(.bold))
                                .foregroundStyle(.white)
                                .multilineTextAlignment(.center)
                                .accessibilityIdentifier("fullPlayer.title")
                            Text(player.current?.artist ?? "")
                                .font(.subheadline)
                                .foregroundStyle(.white.opacity(0.6))
                        }

                        // 歌词
                        LyricsView()
                            .frame(minHeight: 220)
                    }
                    .padding(.horizontal, 24)
                    .padding(.top, 16)
                }

                // 进度条 + 控制
                VStack(spacing: 12) {
                    Slider(
                        value: Binding(
                            get: { player.currentTime },
                            set: { player.seek(to: $0) }
                        ),
                        in: 0...max(player.duration, 1)
                    ) {
                        Text("进度")
                    } minimumValueLabel: {
                        Text(format(player.currentTime)).font(.caption2).foregroundStyle(.white.opacity(0.6))
                    } maximumValueLabel: {
                        Text(format(player.duration)).font(.caption2).foregroundStyle(.white.opacity(0.6))
                    }
                    .tint(.white)

                    HStack(spacing: 48) {
                        Button {
                            // 上一首：当前无队列，回到 0
                            player.seek(to: 0)
                        } label: {
                            Image(systemName: "backward.fill").font(.system(size: 26))
                        }
                        Button {
                            player.togglePlayPause()
                        } label: {
                            Image(systemName: player.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                                .font(.system(size: 52))
                        }
                        .accessibilityIdentifier("player.playPause")
                        .accessibilityLabel(player.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        Button {
                            // 下一首：当前无队列，保持
                        } label: {
                            Image(systemName: "forward.fill").font(.system(size: 26))
                        }
                    }
                    .foregroundStyle(.white)
                }
                .padding(.horizontal, 28)
                .padding(.bottom, 28)
            }
        }
        .preferredColorScheme(.dark)
    }
}

/// 歌词区：逐行高亮 + 自动滚到当前行。
private struct LyricsView: View {
    @ObservedObject private var player = PlayerModel.shared

    var body: some View {
        if player.lyricLines.isEmpty {
            Text("暂无歌词")
                .font(.callout)
                .foregroundStyle(.white.opacity(0.5))
                .frame(maxWidth: .infinity, alignment: .center)
        } else {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 16) {
                        ForEach(Array(player.lyricLines.enumerated()), id: \.element.id) { idx, line in
                            Text(line.text)
                                .id(idx)
                                .font(idx == player.currentLyricIndex ? .title3.weight(.semibold) : .body)
                                .foregroundStyle(idx == player.currentLyricIndex ? .white : .white.opacity(0.45))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .animation(.easeInOut(duration: 0.2), value: idx == player.currentLyricIndex)
                        }
                    }
                    .padding(.vertical, 40)
                }
                .onChange(of: player.currentLyricIndex) { _ in
                    withAnimation { proxy.scrollTo(player.currentLyricIndex, anchor: .center) }
                }
            }
        }
    }
}
