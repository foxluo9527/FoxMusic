// xcode: set sdk=iOS

import SwiftUI
import AVFoundation
import FoxShared

/// 音流播放引擎：独立 AVPlayer，只播服务端给的高潮片段窗口（startMs → endMs），
/// 到点自动切下一首。刻意不复用全局 PlayerModel —— 音流不进入常规播放队列。
final class StreamPlaybackModel: ObservableObject {
    static let shared = StreamPlaybackModel()

    @Published private(set) var items: [FoxStreamItem] = []
    @Published private(set) var index: Int = 0
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?
    @Published private(set) var isPlaying = false
    @Published private(set) var positionMs: Int64 = 0

    private var player: AVPlayer?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    /// 防止「到点自动切歌」在 seek 过程中被重复触发。
    private var advancing = false

    var current: FoxStreamItem? {
        items.indices.contains(index) ? items[index] : nil
    }

    /// 片段进度 0...1（按 startMs→endMs 归一）。
    var clipProgress: Double {
        guard let item = current, item.endMs > item.startMs else { return 0 }
        let span = Double(item.endMs - item.startMs)
        return min(max(Double(positionMs - item.startMs) / span, 0), 1)
    }

    private init() {}

    func load(mood: String?) {
        isLoading = true
        errorMessage = nil
        FoxStreamApi.shared.fetchStream(
            mood: mood,
            onResult: { [weak self] items in
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.isLoading = false
                    self.items = items
                    if items.isEmpty {
                        self.errorMessage = "暂无可播放的音流内容，换个心情试试"
                        self.stop()
                    } else {
                        self.index = 0
                        self.playCurrent()
                    }
                }
            },
            onError: { [weak self] message in
                DispatchQueue.main.async {
                    self?.isLoading = false
                    self?.errorMessage = message
                }
            }
        )
    }

    func advance() {
        guard !items.isEmpty else { return }
        index = (index + 1) % items.count
        playCurrent()
    }

    func togglePlay() {
        guard let player else {
            playCurrent()
            return
        }
        if isPlaying {
            player.pause()
            isPlaying = false
        } else {
            player.play()
            isPlaying = true
        }
    }

    func pause() {
        player?.pause()
        isPlaying = false
    }

    func resume() {
        if player != nil {
            player?.play()
            isPlaying = true
        } else {
            playCurrent()
        }
    }

    func stop() {
        if let timeObserver, let player { player.removeTimeObserver(timeObserver) }
        timeObserver = nil
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = nil
        player?.pause()
        player = nil
        isPlaying = false
        positionMs = 0
    }

    private func playCurrent() {
        guard let item = current else { return }
        stop()
        guard !item.audioUrl.isEmpty, let url = URL(string: item.audioUrl) else {
            errorMessage = "音频地址无效"
            return
        }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        try? AVAudioSession.sharedInstance().setActive(true)

        let itemToPlay = AVPlayerItem(url: url)
        let av = AVPlayer(playerItem: itemToPlay)
        player = av
        positionMs = item.startMs
        av.seek(
            to: CMTime(value: item.startMs, timescale: 1000),
            toleranceBefore: .zero,
            toleranceAfter: .zero
        )
        av.play()
        isPlaying = true

        let interval = CMTime(seconds: 0.2, preferredTimescale: 600)
        timeObserver = av.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self, let currentItem = self.current else { return }
            let ms = Int64((CMTimeGetSeconds(time) * 1000).rounded())
            self.positionMs = max(ms, currentItem.startMs)
            if ms >= currentItem.endMs, !self.advancing {
                self.advancing = true
                DispatchQueue.main.async { self.advance(); self.advancing = false }
            }
        }
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: itemToPlay,
            queue: .main
        ) { [weak self] _ in
            self?.advance()
        }
    }
}

/// 音流模式页（底部 Tab 栏中央圆钮进入）：竖滑切歌 + 心情换一批。
struct StreamView: View {
    @ObservedObject private var model = StreamPlaybackModel.shared
    @Environment(\.dismiss) private var dismiss

    @State private var moodInput = ""
    @FocusState private var moodFocused: Bool

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color.black, Color(hex: 0x1B1030)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                topBar
                moodBar
                Spacer(minLength: 0)
                content
                Spacer(minLength: 0)
                controls
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            if model.items.isEmpty {
                model.load(mood: nil)
            } else {
                model.resume()
            }
        }
        .onDisappear { model.pause() }
    }

    private var topBar: some View {
        HStack {
            Button {
                model.pause()
                dismiss()
            } label: {
                Image(systemName: "chevron.down")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 40, height: 40)
                    .contentShape(Rectangle())
            }
            .accessibilityIdentifier("stream.close")

            Spacer()

            Text("音流模式")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)

            Spacer()

            Color.clear.frame(width: 40, height: 40)
        }
        .padding(.horizontal, 8)
        .padding(.top, 8)
    }

    private var moodBar: some View {
        HStack(spacing: 10) {
            Image(systemName: "sparkles").foregroundStyle(.white.opacity(0.7))
            TextField("此刻心情…", text: $moodInput)
                .focused($moodFocused)
                .submitLabel(.search)
                .onSubmit { reload() }
                .foregroundStyle(.white)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Button("换一批") { reload() }
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Capsule().fill(.white.opacity(0.16)))
            .accessibilityIdentifier("stream.reload")
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(Capsule().fill(.white.opacity(0.08)))
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    @ViewBuilder
    private var content: some View {
        if model.isLoading {
            ProgressView().tint(.white)
        } else if let message = model.errorMessage, model.items.isEmpty {
            VStack(spacing: 12) {
                Image(systemName: "waveform.slash")
                    .font(.system(size: 40))
                    .foregroundStyle(.white.opacity(0.6))
                Text(message)
                    .font(.callout)
                    .foregroundStyle(.white.opacity(0.8))
                    .multilineTextAlignment(.center)
                Button("重新加载") { model.load(mood: moodInput.isEmpty ? nil : moodInput) }
                    .foregroundStyle(.white)
            }
            .padding(.horizontal, 32)
        } else if let item = model.current {
            card(item)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 40)
                        .onEnded { value in
                            if value.translation.height < -40 { model.advance() }
                        }
                )
        } else {
            EmptyView()
        }
    }

    private func card(_ item: FoxStreamItem) -> some View {
        VStack(spacing: 20) {
            AsyncImage(url: item.coverUrl.flatMap(URL.init(string:))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                } else {
                    ZStack {
                        Color.white.opacity(0.08)
                        Image(systemName: "music.note")
                            .font(.system(size: 46))
                            .foregroundStyle(.white.opacity(0.5))
                    }
                }
            }
            .frame(width: 260, height: 260)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .shadow(color: .black.opacity(0.5), radius: 24, y: 12)

            VStack(spacing: 8) {
                if let label = item.label, !label.isEmpty {
                    Text(label)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 4)
                        .background(Capsule().fill(.white.opacity(0.16)))
                }
                Text(item.title)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(item.artist ?? "")
                    .font(.system(size: 14))
                    .foregroundStyle(.white.opacity(0.7))
                    .lineLimit(1)
            }
            .padding(.horizontal, 24)
        }
    }

    private var controls: some View {
        HStack(spacing: 44) {
            Button { model.togglePlay() } label: {
                Image(systemName: model.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                    .font(.system(size: 62))
                    .foregroundStyle(.white)
            }
            .accessibilityIdentifier("stream.toggle")

            Button { model.advance() } label: {
                Image(systemName: "forward.end.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 56)
                    .contentShape(Rectangle())
            }
            .accessibilityIdentifier("stream.next")
        }
        .padding(.bottom, 8)

        .overlay(alignment: .bottom) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(.white.opacity(0.2))
                    Capsule().fill(.white)
                        .frame(width: geo.size.width * model.clipProgress)
                }
            }
            .frame(height: 3)
            .padding(.horizontal, 32)
            .padding(.bottom, -18)
        }
        .padding(.bottom, 40)
    }

    private func reload() {
        moodFocused = false
        model.load(mood: moodInput.isEmpty ? nil : moodInput)
    }
}

/// 底部 Tab 栏中央的「音流模式」圆钮：圆形突起于 Tab 栏之上。
struct StreamEntryButton: View {
    @ObservedObject private var router = AppTabRouter.shared

    private var bottomInset: CGFloat {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }?
            .safeAreaInsets.bottom ?? 0
    }

    var body: some View {
        Button {
            router.rootModal = .stream
        } label: {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [.accentColor, .accentColor.opacity(0.72)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .overlay(Circle().stroke(Color(uiColor: .systemBackground), lineWidth: 3))
                Image(systemName: "waveform")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(.white)
            }
            .frame(width: 54, height: 54)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("tabbar.stream")
        .accessibilityLabel("音流模式")
        .padding(.bottom, max(bottomInset - 1, 0))
    }
}
