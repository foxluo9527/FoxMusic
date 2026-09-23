import AVFoundation
import MediaPlayer
import CoreMedia
import FoxShared
import SwiftUI

/// 一行歌词（已解析时间戳）。
struct LyricLine: Identifiable, Equatable {
    let id = UUID()
    let time: Double
    let text: String

    /// 解析标准 LRC：每行 `[mm:ss.xx] 文本`（支持多个时间标签叠在同一行）。
    static func parseLRC(_ raw: String?) -> [LyricLine] {
        guard let raw, !raw.isEmpty else { return [] }
        let pattern = #"\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?\]"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        var lines: [LyricLine] = []
        for line in raw.components(separatedBy: "\n") {
            let ns = line as NSString
            let range = NSRange(location: 0, length: ns.length)
            let matches = regex.matches(in: line, range: range)
            guard !matches.isEmpty else { continue }
            var text = regex.stringByReplacingMatches(in: line, range: range, withTemplate: "")
                .trimmingCharacters(in: .whitespaces)
            if text.isEmpty { text = "♪" }
            for m in matches {
                guard m.numberOfRanges >= 3 else { continue }
                let min = Double(ns.substring(with: m.range(at: 1))) ?? 0
                let sec = Double(ns.substring(with: m.range(at: 2))) ?? 0
                let msStr = m.numberOfRanges > 3 ? ns.substring(with: m.range(at: 3)) : ""
                let ms = msStr.isEmpty ? 0 : (Double(msStr) ?? 0) / (msStr.count == 2 ? 100 : 1000)
                let t = min * 60 + sec + ms
                lines.append(LyricLine(time: t, text: text))
            }
        }
        return lines.sorted { $0.time < $1.time }
    }

    /// 当前播放时间对应的歌词行索引（二分）。
    static func index(for time: Double, in lines: [LyricLine]) -> Int {
        guard !lines.isEmpty else { return 0 }
        var lo = 0, hi = lines.count - 1, ans = 0
        while lo <= hi {
            let mid = (lo + hi) / 2
            if lines[mid].time <= time { ans = mid; lo = mid + 1 } else { hi = mid - 1 }
        }
        return ans
    }
}

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
/// - 锁屏歌名/歌词：MPNowPlayingInfoCenter + MPRemoteCommandCenter。
final class PlayerModel: ObservableObject {
    static let shared = PlayerModel()

    @Published private(set) var current: PlayableSong?
    @Published private(set) var isPlaying = false
    @Published private(set) var currentTime: Double = 0
    @Published private(set) var duration: Double = 0
    @Published private(set) var lyricLines: [LyricLine] = []

    private var player: AVPlayer?
    private var rateObserver: NSKeyValueObservation?
    private var timeObserver: Any?

    private init() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback)
        try? session.setActive(true)
        setupRemoteCommands()
    }

    /// 当前歌词行索引（供 UI 高亮 / 自动滚动）。
    var currentLyricIndex: Int {
        LyricLine.index(for: currentTime, in: lyricLines)
    }

    func play(_ song: PlayableSong) {
        guard let url = URL(string: song.audioUrl) else {
            print("PlayerModel: 非法播放地址: \(song.audioUrl)")
            return
        }
        // 清理旧观察者，避免叠加
        if let token = timeObserver { player?.removeTimeObserver(token); timeObserver = nil }

        player = AVPlayer(url: url)
        current = song
        lyricLines = LyricLine.parseLRC(song.lyric)
        currentTime = 0
        duration = Double(song.durationMillis) / 1000.0

        var info: [String: Any] = [
            MPMediaItemPropertyTitle: song.title,
            MPMediaItemPropertyArtist: song.artist,
            MPNowPlayingInfoPropertyPlaybackRate: 1.0,
        ]
        if song.durationMillis > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = Double(song.durationMillis) / 1000.0
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info

        let interval = CMTime(seconds: 0.25, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        timeObserver = player?.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] t in
            guard let self else { return }
            self.currentTime = t.seconds
            if let d = self.player?.currentItem?.duration.seconds, d.isFinite, d > 0 {
                self.duration = d
            }
            FoxComposeState.shared.isPlaying = self.player?.rate != 0
        }

        rateObserver = player?.observe(\.rate, options: [.new]) { [weak self] p, _ in
            DispatchQueue.main.async {
                self?.isPlaying = p.rate != 0
                FoxComposeState.shared.isPlaying = p.rate != 0
            }
        }

        // 回灌给共享 UI（Swift 持有播放状态 → Kotlin state → Compose 重组）
        FoxComposeState.shared.currentSongId = song.id
        FoxComposeState.shared.isPlaying = true

        player?.play()
        print("PlayerModel: 开始播放《\(song.title)》- \(song.artist)\n  url=\(song.audioUrl)")
    }

    func seek(to time: Double) {
        let t = max(0, time)
        player?.seek(to: CMTime(seconds: t, preferredTimescale: 1))
        currentTime = t
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
