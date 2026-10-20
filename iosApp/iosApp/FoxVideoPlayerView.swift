import AVFoundation
import SwiftUI
import UIKit
import FoxShared

/// 统一的视频播放视图：帖子视频 / 帖子 Live 图 / 聊天视频、Live 图共用。
///
/// 为什么放在 Swift：K/N 2.3.21 的 AVPlayer 绑定残缺（play/pause/rate 丢失，
/// 见 FoxIosBridge 注释），只能由 Swift 驱动 AVPlayer。
///  - Compose 侧（core:ui PostVideoIos.kt）经 FoxVideoPlayerBridge 工厂 + UIKitView 使用；
///  - SwiftUI 侧（聊天媒体预览）经 [FoxVideoPlayerView] 包装使用。
///
/// 音乐互斥对齐 Android 的 TransientMediaAudioCoordinator：带声播放时暂停
/// PlayerModel 的音乐，结束/暂停/静音/释放后恢复。
final class FoxVideoPlayerUIView: UIView, FoxVideoPlayerHandle {

    // MARK: - 回调（供工厂/SwiftUI 注入）

    var onFirstFrame: (() -> Void)?
    var onCompletion: (() -> Void)?
    var onError: ((String?) -> Void)?

    // MARK: - 内部状态

    private let player = AVPlayer()
    private let videoLayer = AVPlayerLayer()
    private var looping: Bool
    private var firstFrameReported = false
    private var playedToEnd = false
    private var holdingTransientAudio = false
    private var released = false

    private var statusObservation: NSKeyValueObservation?
    private var readyObservation: NSKeyValueObservation?
    private var timeControlObservation: NSKeyValueObservation?
    private var endObserver: NSObjectProtocol?

    init(url: URL, autoPlay: Bool, muted: Bool, looping: Bool) {
        self.looping = looping
        super.init(frame: .zero)
        backgroundColor = .black
        videoLayer.player = player
        videoLayer.videoGravity = .resizeAspect
        layer.addSublayer(videoLayer)
        player.isMuted = muted
        load(url: url, autoPlay: autoPlay)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        videoLayer.frame = bounds
        CATransaction.commit()
    }

    // MARK: - 装载

    private func load(url: URL, autoPlay: Bool) {
        let item = AVPlayerItem(url: url)
        player.replaceCurrentItem(with: item)

        statusObservation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            DispatchQueue.main.async {
                guard let self, !self.released else { return }
                switch item.status {
                case .readyToPlay:
                    if autoPlay { self.player.play(); self.updateTransientAudio() }
                case .failed:
                    self.onError?(item.error?.localizedDescription ?? "视频加载失败")
                default:
                    break
                }
            }
        }
        // isReadyForDisplay 是 AVPlayerLayer 的属性（不是 AVPlayerItem 的），
        // KVO 挂在 videoLayer 上：true 表示已渲染出首帧
        readyObservation = videoLayer.observe(\.isReadyForDisplay, options: [.new]) { [weak self] layer, _ in
            guard layer.isReadyForDisplay else { return }
            DispatchQueue.main.async {
                guard let self, !self.firstFrameReported, !self.released else { return }
                self.firstFrameReported = true
                self.onFirstFrame?()
            }
        }
        timeControlObservation = player.observe(\.timeControlStatus, options: [.new]) { [weak self] player, _ in
            guard player.timeControlStatus == .playing else { return }
            DispatchQueue.main.async {
                guard let self, !self.released else { return }
                self.updateTransientAudio()
            }
        }
        endObserver = NotificationCenter.default.addObserver(
            forName: AVPlayerItem.didPlayToEndTimeNotification,
            object: item,
            queue: .main
        ) { [weak self] _ in
            guard let self, !self.released else { return }
            if self.looping {
                self.player.seek(to: .zero)
                self.player.play()
            } else {
                self.playedToEnd = true
                self.player.pause()
                self.endTransientAudio()
                self.onCompletion?()
            }
        }
    }

    // MARK: - FoxVideoPlayerHandle（Kotlin 导出协议）

    func setPlaying(playing: Bool) {
        guard !released else { return }
        if playing {
            // 播完后的重播：从 0 开始（AVPlayer 在结尾处 play() 不会自动重播）
            if playedToEnd {
                playedToEnd = false
                player.seek(to: .zero)
            }
            player.play()
        } else {
            player.pause()
        }
        updateTransientAudio()
    }

    func setMuted(muted: Bool) {
        guard !released else { return }
        player.isMuted = muted
        updateTransientAudio()
    }

    func setLooping(looping: Bool) {
        self.looping = looping
    }

    func releasePlayer() {
        guard !released else { return }
        released = true
        endTransientAudio()
        statusObservation?.invalidate()
        readyObservation?.invalidate()
        timeControlObservation?.invalidate()
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = nil
        player.pause()
        player.replaceCurrentItem(with: nil)
        videoLayer.player = nil
    }

    deinit {
        statusObservation?.invalidate()
        readyObservation?.invalidate()
        timeControlObservation?.invalidate()
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
    }

    // MARK: - 音乐互斥（对齐 Android TransientMediaAudioCoordinator）

    private func wantsTransientAudio() -> Bool {
        !player.isMuted && player.rate != 0
    }

    private func updateTransientAudio() {
        if wantsTransientAudio() && !holdingTransientAudio {
            holdingTransientAudio = true
            PlayerModel.shared.beginTransientMediaPlayback()
        } else if !wantsTransientAudio() && holdingTransientAudio {
            holdingTransientAudio = false
            PlayerModel.shared.endTransientMediaPlayback()
        }
    }

    private func endTransientAudio() {
        if holdingTransientAudio {
            holdingTransientAudio = false
            PlayerModel.shared.endTransientMediaPlayback()
        }
    }
}

// MARK: - Compose 侧工厂

/// 注册进 Kotlin 的 FoxVideoPlayerBridge，Compose 的 UIKitView 经此创建播放视图。
final class FoxSwiftVideoPlayerFactory: NSObject, FoxVideoPlayerFactory {

    func createPlayerView(
        url: String,
        autoPlay: Bool,
        muted: Bool,
        looping: Bool,
        onFirstFrame: @escaping () -> Void,
        onCompletion: @escaping () -> Void,
        onError: @escaping (String?) -> Void
    ) -> UIView {
        guard let parsed = Self.parseUrl(url) else {
            onError("无法解析的视频地址: \(url)")
            return UIView()
        }
        let view = FoxVideoPlayerUIView(url: parsed, autoPlay: autoPlay, muted: muted, looping: looping)
        view.onFirstFrame = onFirstFrame
        view.onCompletion = onCompletion
        view.onError = onError
        return view
    }

    /// 服务端路径可能含未转义字符（如聊天文件名），先做一次宽松转义。
    static func parseUrl(_ raw: String) -> URL? {
        if let url = URL(string: raw), url.scheme != nil { return url }
        if let encoded = raw.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) {
            return URL(string: encoded).flatMap { $0.scheme == nil ? nil : $0 }
        }
        return nil
    }
}

// MARK: - SwiftUI 包装（聊天媒体预览用）

struct FoxVideoPlayerView: UIViewRepresentable {
    var url: URL
    var autoPlay = true
    var muted = false
    var looping = false
    var onFirstFrame: (() -> Void)? = nil
    var onCompletion: (() -> Void)? = nil
    var onError: ((String?) -> Void)? = nil
    /// Live 图专用：底下的静帧已经按同样比例铺好了，播放器背景若还是黑色会把
    /// 静帧整块盖掉 —— 观感就变成「在看视频」。透明后视频画在静帧之上，两者同框。
    var transparentBackground = false

    func makeUIView(context: Context) -> FoxVideoPlayerUIView {
        let view = FoxVideoPlayerUIView(url: url, autoPlay: autoPlay, muted: muted, looping: looping)
        view.onFirstFrame = onFirstFrame
        view.onCompletion = onCompletion
        view.onError = onError
        view.backgroundColor = transparentBackground ? .clear : .black
        return view
    }

    func updateUIView(_ uiView: FoxVideoPlayerUIView, context: Context) {
        uiView.setPlaying(playing: autoPlay)
        uiView.setMuted(muted: muted)
        uiView.setLooping(looping: looping)
        uiView.backgroundColor = transparentBackground ? .clear : .black
    }

    static func dismantleUIView(_ uiView: FoxVideoPlayerUIView, coordinator: ()) {
        uiView.releasePlayer()
    }
}

// MARK: - 启动注册

enum FoxVideoPlayback {

    /// App 启动时调用一次：把 Swift 播放器工厂注册给 Compose 侧。
    static func install() {
        FoxVideoPlayerBridge.shared.factory = FoxSwiftVideoPlayerFactory()
    }
}
