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
    /// 来源类别，对齐 Android `PlaybackSourceKind`。
    /// 可展示并可跳转的类别见 `isDisplayable`。
    enum Kind: String, Equatable {
        case playlist, album, rank, post, recommend, newMusic, other
    }

    var kind: Kind
    /// 来源实体 id（歌单/专辑/榜单/帖子 id；推荐/新歌用虚拟 id）；其它来源或无效 id 时为 nil。
    var id: Int64?
    var title: String
    var coverUrl: String?
    /// 中文类型标签，如「歌单」「榜单」。
    var typeLabel: String

    /// 是否可在播放列表里展示为可点击的来源卡片。
    /// 口径同 Android `PlaybackPlaylistSource.isDisplayable`：
    /// 真实来源与推荐/新歌虚拟来源，id > 0、标题非空才展示；收藏/搜索等其它来源不展示。
    var isDisplayable: Bool {
        switch kind {
        case .playlist, .album, .rank, .post, .recommend, .newMusic:
            return (id ?? 0) > 0 &&
                !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        case .other:
            return false
        }
    }

    init(
        kind: Kind = .other,
        id: Int64? = nil,
        title: String,
        coverUrl: String? = nil,
        typeLabel: String
    ) {
        self.kind = kind
        self.id = id
        self.title = title
        self.coverUrl = coverUrl
        self.typeLabel = typeLabel
    }

    /// 从共享层 `PlaybackPlaylistSource`（帖子音乐等 Kotlin 入口）折成 Swift 来源。
    init(_ source: PlaybackPlaylistSource) {
        switch source.kind {
        case .playlist: kind = .playlist
        case .album: kind = .album
        case .rank: kind = .rank
        case .post: kind = .post
        case .recommend: kind = .recommend
        // Kotlin 的 NEW_MUSIC 导出为 theNewMusic（new 前缀被框架保留）
        case .theNewMusic: kind = .newMusic
        default: kind = .other
        }
        id = source.id?.int64Value
        title = source.title ?? ""
        coverUrl = source.coverUrl
        typeLabel = source.typeLabel
    }
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
    /// 当前歌词是否含可展示的翻译行（歌词纠正后也以解析结果为准）。
    /// 无翻译时歌词设置里不展示「歌词翻译」开关。
    var hasTranslatedLyrics: Bool {
        lyricLines.contains { ($0.translation ?? "").isEmpty == false }
    }
    /// 歌词字号（对应 Android 歌词设置的字体大小）。
    @Published var lyricFontSize: Double = 20

    /// 定时关闭：到点时间（nil = 未开启）。对齐 Android 设置页的「定时关闭」。
    @Published private(set) var sleepTimerEndsAt: Date?
    private var sleepTimer: Timer?

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
    /// 换歌词时另建副本回填队列（applyLyrics），本字段作为会话内的兜底覆盖：
    /// 用户之后用**新的** play(queue:) 重进同一首歌时队列会被整体换掉，
    /// 靠它保证纠正过的歌词仍生效。
    private var lyricOverride: (songId: String, lyrics: String, translation: String?)?

    /// 无歌词歌曲切歌时正在静默拉取歌词的 id（对齐 Android LyricPrefetchCoordinator 的去重）。
    private var lyricFetchInFlight = Set<String>()

    /// 本首已播秒数 / 是否已上报（避免同一首重复上报）。
    private var listenedSeconds: Double = 0
    private var reportedForCurrentSong = false

    /// 恢复播放时要落到的进度点（AVPlayer item 还没 ready 前先记着，ready 后再 seek）。
    private var pendingStartSeconds: Double = 0
    /// 进度节流持久化用：上次落盘的时刻与位置。
    private var lastPositionPersistAt: Date = .distantPast
    private var lastPersistedPosition: Double = -10

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
            // 进后台把播放态全部 flush 一遍（对应 Android FoxMusicApplication 的 flushPlaybackState）
            self?.persistPlaybackState(force: true)
        }
        restorePreferences()
        restorePlayback()
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

    /// 「为你精选 / 新歌速递」详情页整份列表拉回后，把正在播放的同一来源队列
    /// 同步成最新列表（对齐 Android `MusicController.syncPlaylistForSource`）。
    /// 只在来源 kind 匹配时生效；保持当前歌曲与进度、不打断播放，
    /// 当前曲目不在新列表里时保持原队列。
    func syncTopListQueue(kind: PlaybackSource.Kind, songs: [PlayableSong]) {
        guard !songs.isEmpty, source?.kind == kind else { return }
        guard let current,
              let newIndex = songs.firstIndex(where: { $0.id == current.id }) else { return }
        // 列表没变化就不动，避免每次进详情页都重写队列/落盘
        guard queue.map({ $0.id }) != songs.map({ $0.id }) else { return }
        queue = songs
        currentIndex = newIndex
        persistPlaybackState(force: true)
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

    func setRepeatMode(_ mode: RepeatMode) {
        repeatMode = mode
        UserDefaults.standard.set(mode.rawValue, forKey: Self.repeatModeKey)
    }

    // MARK: - 定时关闭（对齐 Android 设置页「定时关闭」）

    /// 可选的定时关闭时长（分钟）；nil = 不限时。
    static let sleepTimerOptions: [Int] = [15, 30, 60, 90]

    func startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        sleepTimerEndsAt = Date().addingTimeInterval(TimeInterval(minutes * 60))
        sleepTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            guard let self, let end = self.sleepTimerEndsAt else { return }
            if Date() >= end {
                self.cancelSleepTimer()
                self.player?.pause()
                ToastCenter.shared.show("定时关闭：已暂停播放")
            }
        }
    }

    func cancelSleepTimer() {
        sleepTimer?.invalidate()
        sleepTimer = nil
        sleepTimerEndsAt = nil
    }

    // MARK: - 传输控制

    func togglePlayPause() {
        // 冷启动恢复时 player 是懒创建的（见 restorePlayback）：首次点播放才建
        // AVPlayer 并从上次位置续播，避免启动即预加载远端 URL 导致的误报失败。
        if player == nil, !queue.isEmpty, current != nil {
            let start = pendingStartSeconds > 0 ? pendingStartSeconds : currentTime
            playAt(index: currentIndex, startAt: start, autoPlay: true)
            return
        }
        guard let player else { return }
        if player.rate != 0 { player.pause() } else { player.play() }
    }

    // MARK: - 瞬态媒体互斥（视频/Live 图带声播放时暂停音乐，结束后恢复）

    private var transientPausedMusic = false

    func beginTransientMediaPlayback() {
        if isPlaying && !transientPausedMusic {
            transientPausedMusic = true
            togglePlayPause()
        }
    }

    func endTransientMediaPlayback() {
        if transientPausedMusic {
            transientPausedMusic = false
            togglePlayPause()
        }
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
        // 队列内容变了，立即重写存档（删的不是当前曲也影响队列签名）
        persistPlaybackState(force: true)
    }

    func clearQueue() {
        stopEverything()
    }

    /// 下一首播放：插入到当前播放位置的下一首。
    func insertNext(_ song: PlayableSong) {
        if queue.isEmpty {
            play(song)
            return
        }
        let insertAt = currentIndex + 1
        queue.insert(song, at: min(insertAt, queue.count))
        persistPlaybackState(force: true)
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
        pendingStartSeconds = 0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        FoxComposeState.shared.currentSongId = nil
        FoxComposeState.shared.isPlaying = false
        // 队列清空 = 播放态作废，把存档一并清掉（对齐 Android 的 replaceAll(空)）
        PlaybackPersistence.clearAll()
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
    /// 除了更新内存态，还必须把纠正后的歌词**写回队列里这首歌的副本**并强制落盘——
    /// 只记内存 override 的话，重启恢复队列后又回到接口原歌词（对齐 Android：队列实体整首重写）。
    func applyLyrics(lyrics: String, translation: String?) {
        guard let song = current else { return }
        lyricOverride = (song.id, lyrics, translation)
        lyricLines = Self.parseBilingual(lyrics, translation)
        if let idx = queue.firstIndex(where: { $0.id == song.id }) {
            let patched = PlayableSong(
                id: song.id,
                title: song.title,
                artist: song.artist,
                audioUrl: song.audioUrl,
                coverUrl: song.coverUrl,
                durationMillis: song.durationMillis,
                lyric: lyrics,
                lyricTrans: translation,
                commentCount: song.commentCount,
                isFavorite: song.isFavorite
            )
            queue[idx] = patched
            // current 还指向旧对象，同步替换，后续读 current.lyric 才是纠正后的内容
            if currentIndex == idx { current = patched }
        }
        // 歌词长度变了 → 队列内容签名变化 → 强制重写队列存档
        persistPlaybackState(force: true)
        ToastCenter.shared.show("歌词已更换")
    }

    /// 下载歌曲歌词离线补全：把随下载持久化的歌词静默写回播放队列。
    /// 与 [applyLyrics] 的区别：这是系统自动补全（无网络时读本地、播中拉到后补上），
    /// 不是用户手动纠正，因此**不弹「歌词已更换」toast**，也不设置 lyricOverride。
    /// 已有歌词（含用户纠正结果）不覆盖。
    func applyDownloadedLyrics(songId: String, lyrics: String, translation: String?, durationMillis: Int64 = 0) {
        guard let idx = queue.firstIndex(where: { $0.id == songId }) else { return }
        let song = queue[idx]
        var changed = false

        // 老下载记录时长初始为 0，补全后立即校正进度条总时长
        if durationMillis > 0 && song.durationMillis <= 0 {
            if current?.id == songId && duration <= 0 {
                duration = Double(durationMillis) / 1000.0
            }
            changed = true
        }

        // 已有歌词（含用户纠正结果）不覆盖
        if !lyrics.isEmpty && (song.lyric ?? "").isEmpty {
            let patched = PlayableSong(
                id: song.id,
                title: song.title,
                artist: song.artist,
                audioUrl: song.audioUrl,
                coverUrl: song.coverUrl,
                durationMillis: durationMillis > 0 ? durationMillis : song.durationMillis,
                lyric: lyrics,
                lyricTrans: translation,
                commentCount: song.commentCount,
                isFavorite: song.isFavorite
            )
            queue[idx] = patched
            if currentIndex == idx { current = patched }
            // 当前正在播这首歌且此前无歌词：立即刷新歌词页
            if current?.id == songId,
               lyricOverride?.songId != songId,
               lyricLines.isEmpty {
                lyricLines = Self.parseBilingual(lyrics, translation)
            }
            changed = true
        }

        if changed { persistPlaybackState(force: true) }
    }

    /// 切到一首队列里没带歌词的歌时，静默拉取歌词并写回队列
    /// （对齐 Android LyricPrefetchCoordinator + FetchMusicLyricsUseCase）。
    /// 搜索结果、第三方导入等入口的列表项只有音频地址，歌词要在播放时补；
    /// 已有歌词（含用户纠正结果）不会被覆盖，全程不弹 toast。
    private func prefetchMissingLyrics(for song: PlayableSong) {
        guard let musicId = Int64(song.id),
              !lyricFetchInFlight.contains(song.id) else { return }
        lyricFetchInFlight.insert(song.id)
        // 先取详情：同时拿到内嵌歌词与时长；内嵌缺失时再走第三方 import/lyrics
        FoxIosBridge.shared.fetchPlayableSong(
            musicId: musicId,
            onResult: { [weak self] detail in
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.lyricFetchInFlight.remove(song.id)
                    let embedded = detail.lyric?
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                    self.applyDownloadedLyrics(
                        songId: song.id,
                        lyrics: embedded ?? "",
                        translation: detail.lyricTrans,
                        durationMillis: detail.durationMillis
                    )
                    if embedded?.isEmpty ?? true {
                        self.fetchImportedLyrics(songId: song.id, musicId: musicId)
                    }
                }
            },
            onError: { [weak self] _ in
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.lyricFetchInFlight.remove(song.id)
                    // 详情失败：第三方导入歌单的歌词仍可能命中 import/lyrics，再兜一次
                    self.fetchImportedLyrics(songId: song.id, musicId: musicId)
                }
            }
        )
    }

    /// 第三方歌词兜底（api/import/lyrics）。
    private func fetchImportedLyrics(songId: String, musicId: Int64) {
        guard !lyricFetchInFlight.contains(songId) else { return }
        lyricFetchInFlight.insert(songId)
        FoxIosBridge.shared.fetchSongLyrics(
            musicId: musicId,
            onResult: { [weak self] lyric, translation in
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.lyricFetchInFlight.remove(songId)
                    self.applyDownloadedLyrics(
                        songId: songId,
                        lyrics: lyric,
                        translation: translation
                    )
                }
            },
            onError: { [weak self] _ in
                // 拉不到歌词不影响播放，静默
                DispatchQueue.main.async { self?.lyricFetchInFlight.remove(songId) }
            }
        )
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

    private func playAt(index: Int, startAt: Double = 0, autoPlay: Bool = true) {
        guard queue.indices.contains(index) else { return }
        // 先摘掉上一首的全部观察者（含向**旧** AVPlayer 注销 timeObserver），再建新实例
        detachObservers()
        reportPlayback()
        let song = queue[index]
        // 已下载完成的歌优先走本地文件（对齐 Android 播放前查 DownloadRecord 的本地路径）
        let url: URL
        if let localPath = MusicDownloadManager.shared.localPath(for: song.id) {
            url = URL(fileURLWithPath: localPath)
        } else if let remote = URL(string: song.audioUrl) {
            url = remote
        } else {
            print("PlayerModel: 非法播放地址: \(song.audioUrl)")
            ToastCenter.shared.show("这首歌暂时无法播放")
            return
        }

        currentIndex = index
        current = song
        currentTime = startAt
        duration = Double(song.durationMillis) / 1000.0
        // 歌词纠正过的歌沿用替换后的歌词，否则用接口自带的
        if let override = lyricOverride, override.songId == song.id {
            lyricLines = Self.parseBilingual(override.lyrics, override.translation)
        } else {
            lyricLines = Self.parseBilingual(song.lyric, song.lyricTrans)
            // 队列条目没带歌词（搜索结果/第三方导入等列表只给音频地址）：
            // 切到它时静默拉取并写回队列，对齐 Android LyricPrefetchCoordinator
            if (song.lyric ?? "").isEmpty {
                prefetchMissingLyrics(for: song)
            }
        }
        isFavorite = song.isFavorite
        isLoading = autoPlay
        listenedSeconds = 0
        reportedForCurrentSong = false
        // 冷启动恢复场景：item 尚未 ready，先记下目标位置，ready 后再精确 seek
        pendingStartSeconds = startAt

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
        updateNowPlayingInfo(song, playing: autoPlay)

        // 回灌给共享 UI（Swift 持有播放状态 → Kotlin state → Compose 重组）
        FoxComposeState.shared.currentSongId = song.id
        FoxComposeState.shared.isPlaying = autoPlay

        if autoPlay {
            newPlayer.play()
        }
        // 切歌即持久化（对齐 Android persistPlaybackState 的触发点之一）
        persistPlaybackState(force: true)
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
            // 进度节流持久化：与 Android POSITION_PERSIST_INTERVAL_MS 同口径
            // （5 秒一次，或位置变化超过 3 秒立即写）
            self.persistPlaybackState()
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
                // 暂停/恢复的瞬间落一次盘（暂停位置是用户最容易下次接着听的位置）
                if p.rate == 0 {
                    self.persistPlaybackState(force: true)
                }
            }
        }

        statusObserver = item.observe(\.status, options: [.new, .initial]) { [weak self] it, _ in
            DispatchQueue.main.async {
                guard let self, self.player?.currentItem === it else { return }
                switch it.status {
                case .readyToPlay:
                    // 冷启动恢复：item ready 后把进度精确落到上次听到的位置
                    if self.pendingStartSeconds > 0 {
                        let target = self.pendingStartSeconds
                        self.pendingStartSeconds = 0
                        self.player?.seek(
                            to: CMTime(seconds: target, preferredTimescale: 1),
                            toleranceBefore: .zero, toleranceAfter: .zero
                        )
                    }
                case .failed:
                    self.isLoading = false
                    ToastCenter.shared.show("播放失败，请检查网络")
                    print("PlayerModel: 播放失败 \(String(describing: it.error))")
                default:
                    break
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
            guard let self else { return .commandFailed }
            // 冷启动恢复态 player 尚未创建（restorePlayback 只恢复元数据）：
            // 灵动岛/锁屏/控制中心/耳机的播放命令走的是 playCommand 而不是
            // togglePlayPauseCommand，旧实现直接 player?.play() 等于空操作，
            // 表现为「没在 App 内点过播放，灵动岛播放键就永远失灵」。
            // 与 minibar 的 togglePlayPause 走同一条懒建+续播路径。
            if self.player == nil, !self.queue.isEmpty, self.current != nil {
                let start = self.pendingStartSeconds > 0 ? self.pendingStartSeconds : self.currentTime
                self.playAt(index: self.currentIndex, startAt: start, autoPlay: true)
            } else {
                self.player?.play()
            }
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

    // MARK: - 播放态持久化（对齐 Android PlaybackStateStoreImpl）

    /// 持久化当前播放态。
    ///
    /// 分两处写（对齐 Android 的 Room + DataStore 分工）：
    /// - 队列内容 → JSON 文件。**全字段**保存（含 `lyric` / `lyricTrans` / 封面 /
    ///   评论数 / 收藏态），恢复出来不会是缺歌词的"残缺歌曲"；
    ///   内容没变时跳过重写（签名比对），避免播放中反复写大文件。
    /// - 标量（index / 进度 / 模式 / 来源）→ UserDefaults，便宜可频繁写。
    ///
    /// `force = false` 时按节流窗口执行（5 秒一次或位移超 3 秒），对应 Android
    /// `POSITION_PERSIST_INTERVAL_MS` 的节流；`force = true` 立即写（切歌、暂停、
    /// 队列编辑、进后台）。
    func persistPlaybackState(force: Bool = false) {
        guard !queue.isEmpty, currentIndex >= 0 else { return }
        if force {
            lastPositionPersistAt = .distantPast
            lastPersistedPosition = -10
        } else {
            let now = Date()
            let movedEnough = abs(currentTime - lastPersistedPosition) > 3
            let intervalElapsed = now.timeIntervalSince(lastPositionPersistAt) >= 5
            guard movedEnough || intervalElapsed else { return }
            lastPositionPersistAt = now
            lastPersistedPosition = currentTime
        }

        let songs = queue.map { StoredPlayableSong($0) }
        if !PlaybackPersistence.queueMatchesLastSaved(songs) {
            PlaybackPersistence.saveQueue(songs)
        }
        PlaybackPersistence.saveScalars(
            PlaybackScalarState(
                index: currentIndex,
                positionMs: Int64((currentTime * 1000).rounded()),
                repeatMode: repeatMode.rawValue,
                source: source.map { StoredPlaybackSource($0) }
            )
        )
    }

    /// 冷启动恢复上次的播放列表与进度（对齐 Android `restoreFromSnapshot`）：
    /// 恢复队列、当前曲目元数据、歌词、进度与循环模式，**停在暂停态**，
    /// 不自动出声 —— 用户点播放才从恢复的位置继续。
    ///
    /// 注意：这里**故意不创建 AVPlayer/AVPlayerItem**。旧实现恢复时就把远端
    /// URL 交给 AVPlayer 预加载，App 刚启动网络/鉴权可能还没就绪，item 直接
    /// `.failed`，于是每次冷启动都弹一次「播放失败，请检查网络」—— 用户根本没
    /// 点播放。改为纯元数据恢复，player 延迟到 [togglePlayPause] 首次按下时再建。
    private func restorePlayback() {
        guard let scalars = PlaybackPersistence.loadScalars(),
              let stored = PlaybackPersistence.loadQueue(),
              !stored.isEmpty else { return }

        let songs = stored.map { $0.toPlayableSong() }
        let index = songs.indices.contains(scalars.index) ? scalars.index : 0
        let song = songs[index]

        queue = songs
        currentIndex = index
        current = song
        duration = Double(song.durationMillis) / 1000.0
        lyricLines = Self.parseBilingual(song.lyric, song.lyricTrans)
        isFavorite = song.isFavorite
        currentTime = Double(scalars.positionMs) / 1000.0
        // 懒建 player 时（用户首次点播放）要 seek 到这个位置
        pendingStartSeconds = currentTime
        if let src = scalars.source { source = src.toSource() }
        if let mode = RepeatMode(rawValue: scalars.repeatMode) { repeatMode = mode }
        // 恢复的这首不触发播放上报（还没真的听）
        reportedForCurrentSong = true

        FoxComposeState.shared.currentSongId = song.id
        FoxComposeState.shared.isPlaying = false
        print("PlayerModel: 恢复播放队列 \(songs.count) 首，第 \(index + 1) 首，位置 \(Int(currentTime))s（已暂停，待用户点播放）")
    }
}
