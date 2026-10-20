import Foundation
import ActivityKit
import Combine
import OSLog
import UIKit
import FoxShared

private let liveLog = Logger(subsystem: "com.fox.music.iosapp", category: "LiveLyrics")

/// 驱动锁屏歌词 / 灵动岛 Live Activity 的生命周期。
/// 监听 PlayerModel 的当前歌曲、播放状态与当前歌词行，
/// 通过 ActivityKit 启动/更新/结束 FoxMusicActivityAttributes Live Activity。
///
/// 设计要点（对齐网易云/QQ音乐行为）：
/// - 整个播放会话只维护 **一个** Activity，切歌走 update 而不是反复 end/request；
/// - App 每次启动先结束系统中可能残留的同类型 Activity（重装/被杀后旧活动会滞留最多 12h，
///   否则会出现锁屏同时挂两个实时活动、灵动岛显示旧播放状态的问题）；
/// - 歌词/状态变化才下发更新，进度条由 Widget 端按 updatedAt 本地平滑推进，不做秒级刷新。
@available(iOS 16.2, *)
@MainActor
final class LiveLyricsManager: ObservableObject {
    static let shared = LiveLyricsManager()

    @Published private(set) var isLiveActivityActive = false
    /// 用户开关：关闭时立即结束 Live Activity 且不再启动。
    @Published var enabled: Bool {
        didSet {
            UserDefaults.standard.set(enabled, forKey: "liveLyricsEnabled")
            liveLog.log("toggle: enabled=\(self.enabled, privacy: .public)")
            if !enabled {
                endActivity()
            } else if let song = PlayerModel.shared.current {
                sync(song: song, isPlaying: PlayerModel.shared.isPlaying, forceStart: true)
            }
        }
    }

    private var activity: Activity<FoxMusicActivityAttributes>?
    private var cancellables = Set<AnyCancellable>()
    private var lastMusicId: String?
    private var lastLyric: String?
    private var lastIsPlaying: Bool?
    private var lastIsFavorite: Bool?
    private var lastDuration: Double = -1
    /// 清理完历史遗留 Activity 前不允许启动新的，避免又叠一个。
    private var isReady = false
    /// 同一 runloop 内的多次状态变化合并成一帧更新（播放抖动 / seek / 歌词连续行）。
    private var updateScheduled = false

    /// 封面缩略图缓存：musicId -> JPEG Data
    private var artworkCache: [String: Data] = [:]

    private init() {
        enabled = UserDefaults.standard.object(forKey: "liveLyricsEnabled") as? Bool ?? true
        liveLog.log("init: enabled=\(self.enabled, privacy: .public) systemEnabled=\(ActivityAuthorizationInfo().areActivitiesEnabled, privacy: .public)")
        observePlayer()
        observeDarwinSignals()
        Task { await boot() }
    }

    /// 监听灵动岛 Widget 扩展发来的 Darwin 通知（收藏按钮）。
    /// App 后台播放时进程存活，必然能收到；收到即切换收藏。
    private func observeDarwinSignals() {
        CFNotificationCenterAddObserver(
            CFNotificationCenterGetDarwinNotifyCenter(),
            nil,
            { _, _, _, _, _ in
                Task { @MainActor in
                    PlayerModel.shared.toggleFavorite()
                }
            },
            LiveActivityDarwinSignal.toggleFavoriteName,
            nil,
            .deliverImmediately
        )
    }

    // MARK: - 启动引导

    private func boot() async {
        // 结束上次 App 生命周期遗留在系统里的同类活动（可能是旧版本、被杀死进程留下的）
        let stale = Activity<FoxMusicActivityAttributes>.activities
        if !stale.isEmpty {
            liveLog.log("boot: ending \(stale.count, privacy: .public) stale activities")
            for existing in stale {
                await existing.end(nil, dismissalPolicy: .immediate)
            }
        }
        isReady = true
        if let song = PlayerModel.shared.current {
            sync(song: song, isPlaying: PlayerModel.shared.isPlaying, forceStart: true)
        }
    }

    // MARK: - 监听播放器

    private func observePlayer() {
        let pm = PlayerModel.shared
        // 歌曲切换 / 播放状态变化
        pm.$current
            .combineLatest(pm.$isPlaying)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] song, isPlaying in
                guard let self, self.isReady else { return }
                if let song {
                    self.sync(song: song, isPlaying: isPlaying)
                } else {
                    self.endActivity()
                }
            }
            .store(in: &cancellables)

        // 歌词行随播放进度变化：sync 内部去重，仅在歌词变化时刷新
        pm.$currentTime
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self, self.isReady, let song = PlayerModel.shared.current else { return }
                self.sync(song: song, isPlaying: PlayerModel.shared.isPlaying)
            }
            .store(in: &cancellables)

        // 时长就绪后补一次更新（进度条需要总时长）
        pm.$duration
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self, self.isReady, let song = PlayerModel.shared.current else { return }
                self.sync(song: song, isPlaying: PlayerModel.shared.isPlaying)
            }
            .store(in: &cancellables)

        // 收藏状态变化（App 内或灵动岛红心）→ 刷新 Widget 红心
        pm.$isFavorite
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self, self.isReady, PlayerModel.shared.current != nil else { return }
                self.scheduleUpdate()
            }
            .store(in: &cancellables)
    }

    // MARK: - 同步状态

    private func sync(song: PlayableSong, isPlaying: Bool, forceStart: Bool = false) {
        guard enabled else { return }

        if forceStart || activity == nil {
            let lyric = PlayerModel.shared.currentLyricText ?? ""
            let duration = PlayerModel.shared.duration
            startActivity(song: song, isPlaying: isPlaying, lyric: lyric, duration: duration)
            lastMusicId = song.id
            lastLyric = lyric
            lastIsPlaying = isPlaying
            lastIsFavorite = PlayerModel.shared.isFavorite
            lastDuration = duration
            return
        }

        // 所有变化（歌词行 / 播放状态 / 时长 / 切歌）合并到下一个 runloop 统一比对下发，
        // 避免 seek、播放状态抖动、连续歌词行产生重复帧消耗更新预算。
        scheduleUpdate()
    }

    private func scheduleUpdate() {
        guard !updateScheduled else { return }
        updateScheduled = true
        DispatchQueue.main.async { [weak self] in
            self?.updateScheduled = false
            self?.flushPendingUpdate()
        }
    }

    private func flushPendingUpdate() {
        guard let activity else { return }
        guard let song = PlayerModel.shared.current else {
            endActivity()
            return
        }
        let lyric = PlayerModel.shared.currentLyricText ?? ""
        let isPlaying = PlayerModel.shared.isPlaying
        let duration = PlayerModel.shared.duration

        // 同一首：歌词 / 播放状态 / 时长 / 收藏变化才下发
        // 切歌：复用同一 Activity（网易云、QQ 音乐都是单活动切歌），只更新内容
        let favorite = PlayerModel.shared.isFavorite
        let songChanged = lastMusicId != song.id
        guard songChanged || lyric != lastLyric || isPlaying != lastIsPlaying
                || duration != lastDuration || favorite != lastIsFavorite else { return }

        pushUpdate(song: song, lyric: lyric, isPlaying: isPlaying, duration: duration, to: activity)
        lastMusicId = song.id
        lastLyric = lyric
        lastIsPlaying = isPlaying
        lastIsFavorite = favorite
        lastDuration = duration

        if songChanged {
            // 新封面异步到位后再补一帧
            requestArtwork(for: song)
        }
    }

    private func makeState(song: PlayableSong, lyric: String, isPlaying: Bool, duration: Double) -> FoxMusicActivityAttributes.ContentState {
        FoxMusicActivityAttributes.ContentState(
            title: song.title,
            artist: song.artist,
            lyric: lyric,
            isPlaying: isPlaying,
            artwork: artworkCache[song.id],
            currentTime: PlayerModel.shared.currentTime,
            duration: duration,
            updatedAt: Date(),
            isFavorite: PlayerModel.shared.isFavorite
        )
    }

    /// ActivityKit 对单次 content state 有 ~4KB 硬限制（超出整帧被系统丢弃，
    /// 日志为 "Payload maximum size exceeded"），编码后必须留足余量。
    private static let maxPayloadBytes = 3800

    private func payloadSize(_ state: FoxMusicActivityAttributes.ContentState) -> Int {
        (try? JSONEncoder().encode(state))?.count ?? Int.max
    }

    // MARK: - ActivityKit 操作

    private func startActivity(song: PlayableSong, isPlaying: Bool, lyric: String, duration: Double) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            liveLog.error("start: skipped — system Live Activities disabled")
            return
        }
        let attributes = FoxMusicActivityAttributes(musicId: song.id)
        let state = makeState(song: song, lyric: lyric, isPlaying: isPlaying, duration: duration)
        do {
            activity = try Activity<FoxMusicActivityAttributes>.request(
                attributes: attributes,
                content: .init(state: state, staleDate: nil),
                pushType: nil
            )
            isLiveActivityActive = true
            liveLog.log("start: SUCCESS id=\(self.activity?.id ?? "nil", privacy: .public) title=\(song.title, privacy: .public) playing=\(isPlaying, privacy: .public)")
        } catch {
            liveLog.error("start: Activity.request threw — \(String(describing: error), privacy: .public)")
        }
        requestArtwork(for: song)
    }

    private func pushUpdate(song: PlayableSong, lyric: String, isPlaying: Bool, duration: Double, to target: Activity<FoxMusicActivityAttributes>) {
        var state = makeState(song: song, lyric: lyric, isPlaying: isPlaying, duration: duration)
        var payload = payloadSize(state)
        // 兜底：超长歌词行可能把整帧顶过 4KB 限制 —— 先截歌词，再不行丢封面，保证播放状态帧永远能送达。
        if payload > Self.maxPayloadBytes && !lyric.isEmpty {
            let truncated = String(lyric.prefix(24))
            state = makeState(song: song, lyric: truncated, isPlaying: isPlaying, duration: duration)
            payload = payloadSize(state)
        }
        if payload > Self.maxPayloadBytes {
            liveLog.fault("update: payload \(payload) over limit even with short lyric; dropping artwork song=\(song.id, privacy: .public)")
            state.artwork = nil
            payload = payloadSize(state)
        }
        liveLog.log("update: song=\(song.id, privacy: .public) playing=\(isPlaying, privacy: .public) duration=\(duration, privacy: .public) artwork=\(state.artwork?.count ?? -1, privacy: .public) payload=\(payload, privacy: .public)")
        Task {
            await target.update(.init(state: state, staleDate: nil))
        }
    }

    private func endActivity() {
        guard let activity else { return }
        let target = activity
        liveLog.log("end: \(target.id, privacy: .public)")
        Task {
            await target.end(nil, dismissalPolicy: .immediate)
        }
        self.activity = nil
        lastMusicId = nil
        lastLyric = nil
        lastIsPlaying = nil
        lastIsFavorite = nil
        lastDuration = -1
        isLiveActivityActive = false
    }

    // MARK: - 封面

    private func requestArtwork(for forSong: PlayableSong) {
        let songId = forSong.id
        guard artworkCache[songId] == nil else { return }
        guard let urlString = forSong.coverUrl, !urlString.isEmpty else { return }
        Task { [weak self] in
            guard let self else { return }
            // 逐档压缩，直到整帧 state 编码后低于 4KB 硬限制；任何一档失败都不影响播放状态帧。
            let configs: [(CGFloat, CGFloat)] = [(116, 0.38), (100, 0.32), (88, 0.28), (72, 0.24)]
            var picked: Data?
            for (maxPixel, quality) in configs {
                guard let data = await LiveArtwork.thumbnail(for: urlString, maxPixel: maxPixel, quality: quality) else { break }
                let probe = FoxMusicActivityAttributes.ContentState(
                    title: forSong.title, artist: forSong.artist, lyric: "",
                    isPlaying: false, artwork: data, currentTime: 0, duration: 200, updatedAt: Date()
                )
                let size = self.payloadSize(probe)
                liveLog.log("artwork: candidate \(maxPixel)px q\(quality) img=\(data.count) payload=\(size)")
                picked = data
                if size <= Self.maxPayloadBytes { break }
            }
            guard let data = picked else {
                liveLog.error("artwork: nil for song=\(songId, privacy: .public)")
                return
            }
            await MainActor.run {
                self.artworkCache[songId] = data
                // 封面到位：若活动还在且仍是同一首歌，补一帧带封面的状态
                guard let activity = self.activity,
                      self.lastMusicId == songId,
                      let current = PlayerModel.shared.current,
                      current.id == songId else {
                    liveLog.log("artwork: cached but skip push (activity/current mismatch) song=\(songId, privacy: .public)")
                    return
                }
                liveLog.log("artwork: ready img=\(data.count, privacy: .public) song=\(songId, privacy: .public), pushing frame")
                self.pushUpdate(
                    song: current,
                    lyric: PlayerModel.shared.currentLyricText ?? "",
                    isPlaying: PlayerModel.shared.isPlaying,
                    duration: PlayerModel.shared.duration,
                    to: activity
                )
            }
        }
    }
}

// MARK: - 封面缩略图下载

enum LiveArtwork {
    /// 下载封面并压成适合 Live Activity 传输的小体积 JPEG（像素最长边 maxPixel）。
    /// 注意必须让 renderer 以 scale=1 输出，否则会按屏幕 3x 放大，体积膨胀十几倍。
    /// ActivityKit content state 硬上限 ~4KB，故默认 120px，配合 Manager 的 payload 探测逐档降级。
    static func thumbnail(for urlString: String, maxPixel: CGFloat = 120, quality: CGFloat = 0.4) async -> Data? {
        guard let url = URL(string: urlString) else { return nil }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let image = UIImage(data: data) else {
                liveLog.error("artwork: source decode failed for \(urlString, privacy: .public)")
                return nil
            }
            let result: Data? = await Task.detached(priority: .utility) {
                let scale = maxPixel / max(image.size.width, image.size.height)
                let targetSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
                let format = UIGraphicsImageRendererFormat()
                format.scale = 1
                format.opaque = true
                let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
                return renderer.jpegData(withCompressionQuality: quality) { _ in
                    UIColor.black.setFill()
                    UIRectFill(CGRect(origin: .zero, size: targetSize))
                    image.draw(in: CGRect(origin: .zero, size: targetSize))
                }
            }.value
            guard let result, UIImage(data: result) != nil else {
                liveLog.error("artwork: thumbnail render/decode failed for \(urlString, privacy: .public)")
                return nil
            }
            #if targetEnvironment(simulator)
            if let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
                try? result.write(to: docs.appendingPathComponent("live_thumb_\(Int(maxPixel)).jpg"))
            }
            #endif
            return result
        } catch {
            liveLog.error("artwork: download failed — \(String(describing: error), privacy: .public)")
            return nil
        }
    }
}
