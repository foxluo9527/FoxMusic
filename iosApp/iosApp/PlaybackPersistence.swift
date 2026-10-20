import Foundation
import FoxShared

// MARK: - 持久化结构

/// `PlayableSong`（Kotlin data class，Swift 侧只读）的可编码镜像。
///
/// 字段与 Kotlin 端 `PlayableSong` **一一对应且一个都不落**——尤其 `lyric` /
/// `lyricTrans`，丢了它们恢复出来的歌就是没有歌词的"残缺歌曲"。
/// 这与 Android `PlaybackQueueEntity.musicJson`（整首 `Music` 序列化）同粒度。
struct StoredPlayableSong: Codable, Equatable {
    var id: String
    var title: String
    var artist: String
    var audioUrl: String
    var coverUrl: String?
    var durationMillis: Int64
    var lyric: String?
    var lyricTrans: String?
    var commentCount: Int
    var isFavorite: Bool
}

/// 播放来源的可编码镜像（`PlaybackSource` 是 Swift struct，直接 Codable 也行，
/// 但拆开存可以让旧版本存档缺字段时仍能解出来）。
struct StoredPlaybackSource: Codable {
    var title: String
    var coverUrl: String?
    var typeLabel: String
    /// PlaybackSource.Kind 的 rawValue（旧存档缺字段时解为 "other"）。
    var kindRaw: String
    var sourceId: Int64?

    init(
        title: String,
        coverUrl: String?,
        typeLabel: String,
        kindRaw: String = "other",
        sourceId: Int64? = nil
    ) {
        self.title = title
        self.coverUrl = coverUrl
        self.typeLabel = typeLabel
        self.kindRaw = kindRaw
        self.sourceId = sourceId
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        title = try c.decode(String.self, forKey: .title)
        coverUrl = try c.decodeIfPresent(String.self, forKey: .coverUrl)
        typeLabel = try c.decode(String.self, forKey: .typeLabel)
        kindRaw = try c.decodeIfPresent(String.self, forKey: .kindRaw) ?? "other"
        sourceId = try c.decodeIfPresent(Int64.self, forKey: .sourceId)
    }
}

/// 恢复播放所需的全部标量（对应 Android DataStore 里的 `fox_preferences`）。
struct PlaybackScalarState: Codable {
    var index: Int
    var positionMs: Int64
    var repeatMode: Int
    var source: StoredPlaybackSource?
}

// MARK: - 桥接转换

extension StoredPlayableSong {
    init(_ song: PlayableSong) {
        self.init(
            id: song.id,
            title: song.title,
            artist: song.artist,
            audioUrl: song.audioUrl,
            coverUrl: song.coverUrl,
            durationMillis: song.durationMillis,
            lyric: song.lyric,
            lyricTrans: song.lyricTrans,
            commentCount: Int(song.commentCount),
            isFavorite: song.isFavorite
        )
    }

    /// 反序列化回 `PlayableSong`。所有字段原样回填，**不会少任何一项**。
    func toPlayableSong() -> PlayableSong {
        PlayableSong(
            id: id,
            title: title,
            artist: artist,
            audioUrl: audioUrl,
            coverUrl: coverUrl,
            durationMillis: durationMillis,
            lyric: lyric,
            lyricTrans: lyricTrans,
            commentCount: Int32(commentCount),
            isFavorite: isFavorite
        )
    }
}

extension StoredPlaybackSource {
    init(_ source: PlaybackSource) {
        self.init(
            title: source.title,
            coverUrl: source.coverUrl,
            typeLabel: source.typeLabel,
            kindRaw: source.kind.rawValue,
            sourceId: source.id
        )
    }

    func toSource() -> PlaybackSource {
        PlaybackSource(
            kind: PlaybackSource.Kind(rawValue: kindRaw) ?? .other,
            id: sourceId,
            title: title,
            coverUrl: coverUrl,
            typeLabel: typeLabel
        )
    }
}

// MARK: - 存档读写

/// iOS 端播放态持久化。
///
/// 对齐 Android `MusicControllerImpl` + `PlaybackStateStoreImpl` 的做法，同样分两处存：
/// - **队列内容**（可能几百首、含歌词，体积大）→ Application Support 下的 JSON 文件，
///   且只在**队列内容真的变了**时才重写（用内容签名判断，避免播放中反复写大文件）；
/// - **标量**（当前 index / 播放位置 / 循环模式 / 来源）→ UserDefaults，便宜可频繁写。
///
/// 存储粒度刻意做成"整首歌全字段"，而不是"只存 id 再重新拉详情"：后者在网络不通
/// 或歌曲下架时会恢复不出歌，而且重新拉的详情还可能缺歌词字段。
enum PlaybackPersistence {

    private static let queueFileName = "fox_playback_queue.json"
    private static let scalarKey = "foxplayer.playbackState"

    private static var queueFileURL: URL? {
        guard let dir = FileManager.default.urls(
            for: .applicationSupportDirectory, in: .userDomainMask
        ).first else { return nil }
        // 目录不一定存在（首次安装），自己建出来，否则写入会静默失败
        try? FileManager.default.createDirectory(
            at: dir, withIntermediateDirectories: true
        )
        return dir.appendingPathComponent(queueFileName)
    }

    // MARK: 队列

    static func saveQueue(_ songs: [StoredPlayableSong]) {
        guard let url = queueFileURL else { return }
        do {
            let data = try JSONEncoder().encode(songs)
            try data.write(to: url, options: .atomic)
            lastQueueSignature = signature(of: songs)
            print("PlaybackPersistence: 队列已落盘 \(songs.count) 首")
        } catch {
            print("PlaybackPersistence: 队列写入失败 \(error)")
        }
    }

    static func loadQueue() -> [StoredPlayableSong]? {
        guard let url = queueFileURL,
              let data = try? Data(contentsOf: url) else { return nil }
        do {
            let songs = try JSONDecoder().decode([StoredPlayableSong].self, from: data)
            lastQueueSignature = signature(of: songs)
            return songs.isEmpty ? nil : songs
        } catch {
            print("PlaybackPersistence: 队列解析失败，已忽略旧存档 \(error)")
            return nil
        }
    }

    /// 队列内容是否与上次落盘的一致（用于跳过无谓的大文件重写）。
    static func queueMatchesLastSaved(_ songs: [StoredPlayableSong]) -> Bool {
        lastQueueSignature == signature(of: songs)
    }

    // MARK: 标量

    static func saveScalars(_ state: PlaybackScalarState) {
        guard let data = try? JSONEncoder().encode(state) else { return }
        UserDefaults.standard.set(data, forKey: scalarKey)
    }

    static func loadScalars() -> PlaybackScalarState? {
        guard let data = UserDefaults.standard.data(forKey: scalarKey) else { return nil }
        return try? JSONDecoder().decode(PlaybackScalarState.self, from: data)
    }

    static func clearAll() {
        if let url = queueFileURL { try? FileManager.default.removeItem(at: url) }
        UserDefaults.standard.removeObject(forKey: scalarKey)
        lastQueueSignature = nil
    }

    // MARK: 内部

    private static var lastQueueSignature: String?

    /// 队列内容签名。**刻意把歌词长度纳入**——换过歌词纠正的歌即使 id 相同也要重写。
    /// 对应 Android `queueSignature()` 把 `lyrics?.length` 计入的做法。
    private static func signature(of songs: [StoredPlayableSong]) -> String {
        var hasher = Hasher()
        for s in songs {
            hasher.combine(s.id)
            hasher.combine(s.lyric?.count ?? 0)
            hasher.combine(s.lyricTrans?.count ?? 0)
            hasher.combine(s.isFavorite)
        }
        return String(hasher.finalize())
    }
}
