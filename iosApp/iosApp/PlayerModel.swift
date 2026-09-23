import AVFoundation
import CoreMedia
import FoxShared
import MediaPlayer
import SwiftUI
import UIKit

// MARK: - 循环模式

/// 播放模式。四项与 Android `RepeatMode`（core:model）一一对应，切换顺序也一致：
/// 顺序 → 列表循环 → 单曲循环 → 随机 → 顺序（对齐 Android SongPage 的 nextRepeatMode）。
///
/// 为什么在 Swift 侧重声明而不是直接用共享枚举：Kotlin 枚举桥到 Swift 后是
/// 类对象，不能 `CaseIterable`、也不方便挂 SF Symbol 与中文名。
enum RepeatMode: Int, CaseIterable {
    case sequential = 0
    case all = 1
    case one = 2
    case random = 3

    var next: RepeatMode {
        switch self {
        case .sequential: return .all
        case .all: return .one
        case .one: return .random
        case .random: return .sequential
        }
    }

    var symbolName: String {
        switch self {
        case .sequential: return "arrow.right"
        case .all: return "repeat"
        case .one: return "repeat.1"
        case .random: return "shuffle"
        }
    }

    var label: String {
        switch self {
        case .sequential: return "顺序播放"
        case .all: return "列表循环"
        case .one: return "单曲循环"
        case .random: return "随机播放"
        }
    }
}

/// 播放来源（队列是从哪个歌单/榜单/歌手点进来的），对应 Android `PlaybackPlaylistSource`。
struct PlaybackSource: Equatable {
    var title: String
    var coverUrl: String?
    /// 中文类型标签，如「歌单」「榜单」。
    var typeLabel: String
}

/// 一行歌词。由共享层 `LyricsParser.parseBilingualLrc` 解析后折成，两端同一份解析逻辑。
struct LyricLine: Identifiable, Equatable {
    let id: Int
    let time: Double
    let text: String
    let translation: String?

    /// 当前时间对应的行索引（二分，与 Android `findCurrentBilingualLyricIndex` 同口径）。
    static func index(for time: Double, in lines: [LyricLine]) -> Int {
        guard !lines.isEmpty else { return -1 }
        var lo = 0, hi = lines.count - 1, ans = -1
        while lo <= hi {
            let mid = (lo + hi) / 2
            if lines[mid].time <= time { ans = mid; lo = mid + 1 } else { hi = mid - 1 }
        }
        return ans
    }
}

/// iOS 端播放器内核（Swift 实现）。
///
/// 为什么不在 Kotlin 共享层做：Kotlin/Native 2.3.21 预置平台库里的 AVPlayer
/// 绑定是残缺的（play/pause/rate 全部缺失，根因是 Xcode 26+ SDK 头文件把
/// 方法声明拆进了 NS_SWIFT_NONISOLATED 条件宏），等上游修复后再评估下沉。
///
/// 与 Android `MusicControllerImpl` 的能力对齐清单：
/// - 播放队列（含来源）+ 播完自动续播
/// - 循环模式四态（顺序/列表循环/单曲循环/随机），next/previous 语义逐条对齐
/// - 删除单曲 / 清空队列 / 点队列切歌
/// - 收藏（走后端，取反式）
/// - 加载/缓冲态
/// - 歌词（双语 + 点击行 seek + 歌词纠正）
/// - 播放上报（"最近在听"的数据来源）
final class PlayerModel: ObservableObject {
    static let shared = PlayerModel()

    // MARK: - 对外状态

    @Published private(set) var queue: [PlayableSong] = []
    @Published private(set) var currentIndex: Int = -1
    @Published private(set) var current: PlayableSong?
    @Published private(set) var isPlaying = false
    @Published private(set) var isLoading = false
    @Published private(set) var currentTime: Double = 0
    @Published private(set) var duration: Double = 0
    @Published private(set) var lyricLines: [LyricLine] = []
    @Published private(set) var repeatMode: RepeatMode = .sequential
    @Published private(set) var isFavorite = false
    @Published private(set) var source: PlaybackSource?
    /// 歌词是否显示翻译（对应 Android 歌词设置的"歌词翻译"开关）。
    @Published var showTranslation = true
    /// 歌词字号（对应 Android 歌词设置的字体大小）。
    @Published var lyricFontSize: Double = 20

    /// 当前歌词行索引；-1 表示还没唱到第一行。
    var currentLyricIndex: Int { LyricLine.index(for: currentTime, in: lyricLines) }

    /// 歌曲页展示的当前歌词行。
    var currentLyricText: String? {
        let idx = currentLyricIndex
        guard lyricLines.indices.contains(idx) else { return lyricLines.first?.text }
        return lyricLines[idx].text
    }

    /// 歌曲页展示的"下一句歌词"。
    var nextLyricText: String? {
        let idx = currentLyricIndex
        guard idx >= 0, idx + 1 < lyricLines.count else { return nil }
        return lyricLines[idx + 1].text
    }

    /// 评论数（评论入口的副标题用）。
    var commentCount: Int { Int(current?.commentCount ?? 0) }

    // MARK: - 内部

    private var player: AVPlayer?
    /// 注册 timeObserver 的那个实例。AVPlayer 的规则是**必须由注册它的同一个实例移除**，
    /// 用新实例移除会抛 ObjC 异常（`cannot remove a time observer that was added by a
    /// different instance`）→ SIGABRT 整个 App 挂掉。所以这里单独记住宿主。
    private weak var observedPlayer: AVPlayer?
    private var timeObserver: Any?
    private var rateObserver: NSKeyValueObservation?
    private var statusObserver: NSKeyValueObservation?
    private var timeControlObserver: NSKeyValueObservation?
    private var endObserver: NSObjectProtocol?

    /// 歌词纠正：替换后的歌词源。PlayableSong 是 Kotlin data class（属性只读），
    /// 所以换歌词只能在本层记一份覆盖，而不是回写对象。
    private var lyricOverride: (songId: String, lyrics: String, translation: String?)?

    /// 本首已播秒数 / 是否已上报（避免同一首重复上报）。
    private var listenedSeconds: Double = 0
    private var reportedForCurrentSong = false

    private init() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback)
        try? session.setActive(true)
        setupRemoteCommands()
        NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.reportPlayback()
        }
        restorePreferences()
    }

    // MARK: - 播放入口

    /// 单曲播放（等价于"队列只有这一首"）。
    func play(_ song: PlayableSong) {
        play(queue: [song], startIndex: 0, source: nil)
    }

    /// 入队并播放。所有列表入口都应走这里，队列才完整 ——
    /// 这也是修掉旧版「上一首/下一首是空实现」的根因。
    func play(queue songs: [PlayableSong], startIndex: Int, source: PlaybackSource? = nil) {
        guard !songs.isEmpty else { return }
        queue = songs
        self.source = source
        playAt(index: min(max(startIndex, 0), songs.count - 1))
    }

    /// 从当前队列某一条开始播（播放列表 Sheet 点击）。
    func playQueueItem(at index: Int) {
        guard queue.indices.contains(index) else { return }
        playAt(index: index)
    }

    // MARK: - 队列内切换

    /// 下一首。`auto` 为 true 表示由"播完自动续播"触发。
    func next(auto: Bool = false) {
        guard !queue.isEmpty else { return }
        // 单曲循环：自动续播时重播本首；手动点"下一首"时 Android 也停在当前
        if repeatMode == .one {
            if auto { restartCurrent() } else { playAt(index: currentIndex) }
            return
        }
        guard let target = nextIndex() else {
            // 顺序播放到底：停在最后一首（与 Android 的 return 一致）
            if auto { finishPlayback() }
            return
        }
        playAt(index: target)
    }

    /// 上一首：播放超过 3 秒先回到本首开头（Android `PREVIOUS_RESTART_THRESHOLD_MS`）。
    func previous() {
        guard !queue.isEmpty else { return }
        if currentTime > 3.0 {
            seek(to: 0)
            if !isPlaying { player?.play() }
            return
        }
        switch repeatMode {
        case .sequential:
            if currentIndex > 0 { playAt(index: currentIndex - 1) }
        case .one:
            playAt(index: currentIndex)
        case .all, .random:
            playAt(index: currentIndex > 0 ? currentIndex - 1 : queue.count - 1)
        }
    }

    private func nextIndex() -> Int? {
        switch repeatMode {
        case .sequential:
            return currentIndex < queue.count - 1 ? currentIndex + 1 : nil
        case .one:
            return currentIndex
        case .all:
            return queue.isEmpty ? nil : (currentIndex + 1) % queue.count
        case .random:
            guard queue.count > 1 else { return currentIndex }
            var pick = currentIndex
            while pick == currentIndex { pick = Int.random(in: 0..<queue.count) }
            return pick
        }
    }

    func cycleRepeatMode() {
        repeatMode = repeatMode.next
        UserDefaults.standard.set(repeatMode.rawValue, forKey: Self.repeatModeKey)
        ToastCenter.shared.show(repeatMode.label)
    }

    // MARK: - 传输控制

    func togglePlayPause() {
        guard let player else { return }
        if player.rate != 0 { player.pause() } else { player.play() }
    }

    func seek(to time: Double) {
        let upper = duration > 0 ? duration : time
        let t = max(0, min(time, upper))
        player?.seek(to: CMTime(seconds: t, preferredTimescale: 1),
                     toleranceBefore: .zero, toleranceAfter: .zero)
        currentTime = t
    }

    /// 点歌词行跳转（Android `onSeek(lyricLine.startTimeMs)`）。
    func seekToLyric(_ line: LyricLine) {
        seek(to: line.time)
        if !isPlaying { player?.play() }
    }

    // MARK: - 队列编辑

    /// 删除队列中某一条，语义与 Android `removeFromQueue` 一致：
    /// 删的是当前曲 → 同 index 顶上来（末尾则回退最后一首）并沿用原播放态。
    func removeFromQueue(at index: Int) {
        guard queue.indices.contains(index) else { return }
        let wasPlaying = isPlaying
        let removingCurrent = index == currentIndex
        queue.remove(at: index)

        if queue.isEmpty {
            stopEverything()
            return
        }

        if removingCurrent {
            let target = currentIndex < queue.count ? currentIndex : queue.count - 1
            playAt(index: target)
            if !wasPlaying { player?.pause() }
            return
        }
        if index < currentIndex { currentIndex -= 1 }
    }

    func clearQueue() {
        stopEverything()
    }

    private func stopEverything() {
        detachObservers()
        player?.pause()
        player = nil
        queue = []
        currentIndex = -1
        current = nil
        source = nil
        lyricLines = []
        lyricOverride = nil
        isPlaying = false
        isLoading = false
        currentTime = 0
        duration = 0
        reportedForCurrentSong = true
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        FoxComposeState.shared.currentSongId = nil
        FoxComposeState.shared.isPlaying = false
    }

    // MARK: - 收藏

    func toggleFavorite() {
        guard let song = current else { return }
        let wasFavorite = isFavorite
        // 乐观更新：收藏要即时反馈，失败再回滚
        isFavorite = !wasFavorite
        guard let musicId = Int64(song.id) else { return }
        let rollback = wasFavorite
        FoxPlayerApi.shared.toggleFavorite(
            musicId: musicId,
            wasFavorite: wasFavorite,
            onResult: { [weak self] newState in
                self?.isFavorite = newState.boolValue
                ToastCenter.shared.show(newState.boolValue ? "已收藏" : "已取消收藏")
            },
            onError: { [weak self] message in
                self?.isFavorite = rollback
                ToastCenter.shared.show(message)
            }
        )
    }

    // MARK: - 歌词

    /// 歌词纠正：用别的歌词源替换当前歌的歌词（Android `updateMusicLyrics` 的等价物）。
    func applyLyrics(lyrics: String, translation: String?) {
        guard let song = current else { return }
        lyricOverride = (song.id, lyrics, translation)
        lyricLines = Self.parseBilingual(lyrics, translation)
        ToastCenter.shared.show("歌词已更换")
    }

    /// 用共享层 LyricsParser 解析双语歌词 —— 与 Android 同一份实现，避免两端解析不一致。
    /// internal：歌词纠正的预览页（PlayerSheets）也用它解析候选歌词全文。
    static func parseBilingual(_ lyrics: String?, _ translation: String?) -> [LyricLine] {
        guard let lyrics, !lyrics.isEmpty else { return [] }
        let parsed = LyricsParser.shared.parseBilingualLrc(
            originalLrc: lyrics,
            translatedLrc: translation
        )
        var lines: [LyricLine] = []
        lines.reserveCapacity(parsed.count)
        for (idx, line) in parsed.enumerated() {
            lines.append(
                LyricLine(
                    id: idx,
                    time: Double(line.startTimeMs) / 1000.0,
                    text: line.originalText,
                    translation: line.translatedText
                )
            )
        }
        return lines
    }

    // MARK: - 播放实现

    private func playAt(index: Int) {
        guard queue.indices.contains(index) else { return }
        // 先摘掉上一首的全部观察者（含向**旧** AVPlayer 注销 timeObserver），再建新实例
        detachObservers()
        reportPlayback()
        let song = queue[index]
        guard let url = URL(string: song.audioUrl) else {
            print("PlayerModel: 非法播放地址: \(song.audioUrl)")
            ToastCenter.shared.show("这首歌暂时无法播放")
            return
        }

        currentIndex = index
        current = song
        currentTime = 0
        duration = Double(song.durationMillis) / 1000.0
        // 歌词纠正过的歌沿用替换后的歌词，否则用接口自带的
        if let override = lyricOverride, override.songId == song.id {
            lyricLines = Self.parseBilingual(override.lyrics, override.translation)
        } else {
            lyricLines = Self.parseBilingual(song.lyric, song.lyricTrans)
        }
        isFavorite = song.isFavorite
        isLoading = true
        listenedSeconds = 0
        reportedForCurrentSong = false

        let item = AVPlayerItem(url: url)
        let newPlayer = AVPlayer(playerItem: item)
        player = newPlayer
        observedPlayer = newPlayer
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            self?.handleItemFinished()
        }

        observe(item: item, player: newPlayer)
        updateNowPlayingInfo(song, playing: true)

        // 回灌给共享 UI（Swift 持有播放状态 → Kotlin state → Compose 重组）
        FoxComposeState.shared.currentSongId = song.id
        FoxComposeState.shared.isPlaying = true

        newPlayer.play()
        print("PlayerModel: 开始播放《\(song.title)》- \(song.artist) [\(index + 1)/\(queue.count)]")
    }

    /// 统一摘除观察者。`timeObserver` 必须还给**注册它的那个 AVPlayer**，
    /// 否则 AVPlayer 会抛 ObjC 异常让进程 abort（批次 28 实测崩溃点）。
    private func detachObservers() {
        if let token = timeObserver, let owner = observedPlayer {
            owner.removeTimeObserver(token)
        }
        timeObserver = nil
        observedPlayer = nil
        rateObserver = nil
        statusObserver = nil
        timeControlObserver = nil
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = nil
    }

    private func observe(item: AVPlayerItem, player: AVPlayer) {
        let interval = CMTime(seconds: 0.25, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] t in
            guard let self, t.seconds.isFinite else { return }
            self.currentTime = t.seconds
            self.listenedSeconds = max(self.listenedSeconds, t.seconds)
            if let d = self.player?.currentItem?.duration.seconds, d.isFinite, d > 0 {
                self.duration = d
            }
            // 播过一半（上限 30 秒）就上报一次，保证"最近在听"有数据，不必等整首播完
            if !self.reportedForCurrentSong, self.duration > 0,
               self.listenedSeconds >= min(30, self.duration / 2) {
                self.reportPlayback()
            }
            FoxComposeState.shared.isPlaying = self.player?.rate != 0
        }

        rateObserver = player.observe(\.rate, options: [.new]) { [weak self] p, _ in
            DispatchQueue.main.async {
                guard let self else { return }
                self.isPlaying = p.rate != 0
                FoxComposeState.shared.isPlaying = p.rate != 0
                if let song = self.current {
                    self.updateNowPlayingInfo(song, playing: p.rate != 0)
                }
            }
        }

        statusObserver = item.observe(\.status, options: [.new, .initial]) { [weak self] it, _ in
            DispatchQueue.main.async {
                guard let self, self.player?.currentItem === it else { return }
                if it.status == .failed {
                    self.isLoading = false
                    ToastCenter.shared.show("播放失败，请检查网络")
                    print("PlayerModel: 播放失败 \(String(describing: it.error))")
                }
            }
        }

        // 缓冲态：waitingToPlayAtSpecifiedRate = 想播但还没缓冲够
        timeControlObserver = player.observe(\.timeControlStatus, options: [.new]) { [weak self] p, _ in
            DispatchQueue.main.async {
                guard let self else { return }
                self.isLoading = p.timeControlStatus == .waitingToPlayAtSpecifiedRate
            }
        }
    }

    private func handleItemFinished() {
        reportPlayback()
        if repeatMode == .one {
            restartCurrent()
        } else {
            next(auto: true)
        }
    }

    private func restartCurrent() {
        seek(to: 0)
        player?.play()
    }

    /// 顺序播放到队尾且不循环：停在最后一首不动。
    private func finishPlayback() {
        player?.pause()
        isPlaying = false
        FoxComposeState.shared.isPlaying = false
    }

    // MARK: - 锁屏 / Now Playing

    private func updateNowPlayingInfo(_ song: PlayableSong, playing: Bool) {
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: song.title,
            MPMediaItemPropertyArtist: song.artist,
            MPNowPlayingInfoPropertyPlaybackRate: playing ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentTime,
        ]
        if duration > 0 { info[MPMediaItemPropertyPlaybackDuration] = duration }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
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
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.togglePlayPause()
            return .success
        }
        center.nextTrackCommand.addTarget { [weak self] _ in
            self?.next()
            return .success
        }
        center.previousTrackCommand.addTarget { [weak self] _ in
            self?.previous()
            return .success
        }
    }

    // MARK: - 播放上报（"最近在听"）

    /// 上报播放时长与进度（POST api/music/{id}/play）。
    private func reportPlayback() {
        guard let song = current, !reportedForCurrentSong, listenedSeconds > 0,
              let musicId = Int64(song.id) else { return }
        reportedForCurrentSong = true
        let progress = duration > 0 ? Int32((listenedSeconds / duration) * 100) : 0
        FoxPlayerApi.shared.recordPlay(
            musicId: musicId,
            durationSeconds: Int32(listenedSeconds.rounded()),
            progressPercent: min(max(progress, 0), 100)
        )
    }

    private static let repeatModeKey = "foxplayer.repeatMode"

    /// 恢复上次的循环模式（与 Android 的 playbackStateStore 持久化对齐）。
    private func restorePreferences() {
        if let saved = UserDefaults.standard.object(forKey: Self.repeatModeKey) as? Int,
           let mode = RepeatMode(rawValue: saved) {
            repeatMode = mode
        }
    }
}
