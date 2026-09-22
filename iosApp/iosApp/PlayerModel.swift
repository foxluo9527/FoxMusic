import AVFoundation
import MediaPlayer
import FoxShared

/// iOS 端最小播放器（Swift 实现）。
///
/// 为什么不在 Kotlin 共享层做：Kotlin/Native 2.3.21 预置平台库里的 AVPlayer
/// 绑定是残缺的（play/pause/rate 全部缺失，根因是 Xcode 26+ SDK 头文件把
/// 方法声明拆进了 NS_SWIFT_NONISOLATED 条件宏），等上游修复后再评估下沉。
///
/// 批次 7 验收三要素：
/// - 播放：AVPlayer 直接流式拉远端 mp3；
/// - 后台不断播：AVAudioSession.category = .playback + Info.plist 的
///   UIBackgroundModes: audio；
/// - 锁屏歌名：MPNowPlayingInfoCenter + MPRemoteCommandCenter。
final class PlayerModel: ObservableObject {
    static let shared = PlayerModel()

    @Published private(set) var current: PlayableSong?
    @Published private(set) var isPlaying = false

    private var player: AVPlayer?
    private var rateObserver: NSKeyValueObservation?

    private init() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback)
        try? session.setActive(true)
        setupRemoteCommands()
    }

    func play(_ song: PlayableSong) {
        guard let url = URL(string: song.audioUrl) else {
            print("PlayerModel: 非法播放地址: \(song.audioUrl)")
            return
        }
        player = AVPlayer(url: url)
        current = song

        var info: [String: Any] = [
            MPMediaItemPropertyTitle: song.title,
            MPMediaItemPropertyArtist: song.artist,
            MPNowPlayingInfoPropertyPlaybackRate: 1.0,
        ]
        if song.durationMillis > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = Double(song.durationMillis) / 1000.0
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info

        rateObserver = player?.observe(\.rate, options: [.new]) { [weak self] p, _ in
            DispatchQueue.main.async { self?.isPlaying = p.rate != 0 }
        }

        player?.play()
        print("PlayerModel: 开始播放《\(song.title)》- \(song.artist)\n  url=\(song.audioUrl)")
    }

    func togglePlayPause() {
        guard let player else { return }
        if player.rate != 0 { player.pause() } else { player.play() }
    }

    private func setupRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.addTarget { [weak self] _ in
            self?.player?.play()
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            self?.player?.pause()
            return .success
        }
    }
}
