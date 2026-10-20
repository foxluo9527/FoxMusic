import Foundation
import ActivityKit
import AppIntents

/// 锁屏歌词 / 灵动岛 Live Activity 的属性定义。
/// 此文件同时编进 iosApp 主 target 与 FoxMusicWidget 扩展 target。
@available(iOS 16.1, *)
struct FoxMusicActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        /// 歌名
        var title: String
        /// 歌手
        var artist: String
        /// 当前歌词行
        var lyric: String
        /// 是否正在播放（true 时 UI 展示「暂停」按钮，符合主流音乐 App 习惯）
        var isPlaying: Bool
        /// 封面缩略图（JPEG，约 260px 以内）。
        /// 直接随状态下发，避免引入 App Group 权限配置；仅在切歌时变化，体积可接受。
        var artwork: Data?
        /// 上次状态更新时的播放进度（秒）。Widget 用 updatedAt 自行平滑推进进度条。
        var currentTime: Double
        /// 总时长（秒）
        var duration: Double
        /// currentTime 对应的墙钟时刻，供 Widget 端 TimelineView 计算实时进度
        var updatedAt: Date
        /// 是否已收藏（灵动岛展开态红心状态）
        var isFavorite: Bool

        public init(title: String,
                    artist: String,
                    lyric: String,
                    isPlaying: Bool,
                    artwork: Data? = nil,
                    currentTime: Double = 0,
                    duration: Double = 0,
                    updatedAt: Date = Date(),
                    isFavorite: Bool = false) {
            self.title = title
            self.artist = artist
            self.lyric = lyric
            self.isPlaying = isPlaying
            self.artwork = artwork
            self.currentTime = currentTime
            self.duration = duration
            self.updatedAt = updatedAt
            self.isFavorite = isFavorite
        }
    }

    var musicId: String
}

// MARK: - 实时活动内播放/暂停控制（iOS 17+ 交互式按钮）

/// Live Activity 按钮在 Widget 扩展进程里执行，不能直接调用主 App 的 AVPlayer。
/// 这里走系统媒体远程命令通道（等价于耳机线控/控制中心的播放暂停）：
/// mediaserverd 把命令派发给当前「正在播放」的 App（即本 App），
/// App 被后台唤醒，由 PlayerModel 已注册的 MPRemoteCommandCenter 处理，全程不跳回前台。
/// 按目标状态提供两个具体 Intent（按钮根据当前 isPlaying 显示对应动作）。
@available(iOS 17.0, *)
struct ResumePlaybackIntent: AppIntent {
    static var title: LocalizedStringResource = "播放"
    static var openAppWhenRun: Bool = false

    func perform() async throws -> some IntentResult {
        LiveActivityRemoteCommand.send(.play)
        return .result()
    }
}

@available(iOS 17.0, *)
struct PausePlaybackIntent: AppIntent {
    static var title: LocalizedStringResource = "暂停"
    static var openAppWhenRun: Bool = false

    func perform() async throws -> some IntentResult {
        LiveActivityRemoteCommand.send(.pause)
        return .result()
    }
}

@available(iOS 17.0, *)
struct NextTrackIntent: AppIntent {
    static var title: LocalizedStringResource = "下一首"
    static var openAppWhenRun: Bool = false

    func perform() async throws -> some IntentResult {
        LiveActivityRemoteCommand.send(.nextTrack)
        return .result()
    }
}

@available(iOS 17.0, *)
struct PreviousTrackIntent: AppIntent {
    static var title: LocalizedStringResource = "上一首"
    static var openAppWhenRun: Bool = false

    func perform() async throws -> some IntentResult {
        LiveActivityRemoteCommand.send(.previousTrack)
        return .result()
    }
}

/// 收藏没有对应的系统媒体命令，走 Darwin 通知：
/// Widget 扩展进程 post 系统级广播，后台存活的 App 收到后调用 PlayerModel.toggleFavorite()。
/// Darwin 通知是全局广播，不需要 App Group 权限。
enum LiveActivityDarwinSignal {
    static let toggleFavoriteName = "com.fox.music.liveactivity.toggleFavorite" as CFString
    static var toggleFavoriteNotification: CFNotificationName {
        CFNotificationName(toggleFavoriteName)
    }

    static func postToggleFavorite() {
        CFNotificationCenterPostNotification(
            CFNotificationCenterGetDarwinNotifyCenter(),
            toggleFavoriteNotification,
            nil,
            nil,
            true
        )
    }
}

@available(iOS 17.0, *)
struct ToggleFavoriteIntent: AppIntent {
    static var title: LocalizedStringResource = "收藏"
    static var openAppWhenRun: Bool = false

    func perform() async throws -> some IntentResult {
        LiveActivityDarwinSignal.postToggleFavorite()
        return .result()
    }
}

/// 通过 MediaRemote 下发系统级媒体命令（与锁屏/控制中心/耳机同一通道，可后台唤醒 App）。
/// 该框架为系统私有框架，运行时动态查找符号、无静态链接：
/// 仅限「控制自己 App 的播放」这一场景（命令只派发给当前 now-playing App）。
@available(iOS 17.0, *)
enum LiveActivityRemoteCommand {
    enum Command: UInt32 {
        case play = 0
        case pause = 1
        case nextTrack = 4
        case previousTrack = 5
    }

    private typealias SendCommandFn = @convention(c) (UInt32, CFDictionary?) -> Int32

    static func send(_ command: Command) {
        let path = "/System/Library/PrivateFrameworks/MediaRemote.framework/MediaRemote"
        guard let handle = dlopen(path, RTLD_NOW),
              let symbol = dlsym(handle, "MRMediaRemoteSendCommand") else { return }
        let fn = unsafeBitCast(symbol, to: SendCommandFn.self)
        _ = fn(command.rawValue, nil)
    }
}
