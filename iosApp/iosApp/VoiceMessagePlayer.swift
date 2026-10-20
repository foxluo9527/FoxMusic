import AVFoundation
import FoxShared

// MARK: - 语音消息播放

/// 聊天语音消息播放器（AVAudioPlayer 单例）。
///
/// 对齐 Android ChatDetailViewModel 的 MediaPlayer 语音播放：
///  - 同一气泡再点一次停止；切到另一条语音自动替换；
///  - 播放期间暂停主播放器（若正在放歌），播完/停止后恢复；
///  - 语音文件下载到 tmp 缓存（按 URL 哈希命名），二次播放不重复下载；
///  - 自然播完时通过 `endHandler` 通知 Compose 侧复位气泡播放态
///    （FoxChatVoiceController）。
final class VoiceMessagePlayer: NSObject, ObservableObject {

    static let shared = VoiceMessagePlayer()

    /// 当前播放中语音对应的消息 localId（nil = 未播放）。
    @Published private(set) var playingLocalId: String?

    private var player: AVAudioPlayer?
    private var currentLocalId: String?
    private var currentURL: URL?
    private var loadTask: URLSessionDataTask?

    /// 自然播放结束（或失败）时回调：Compose 侧用来清 playingVoiceLocalId。
    var endHandler: (() -> Void)?

    private override init() {
        super.init()
    }

    // MARK: API

    /// - Parameters:
    ///   - urlString: 服务端语音地址（先走 MediaUrlResolver 补全）
    ///   - localId: 气泡 localId，用作播放态标识
    func play(urlString: String, localId: String) {
        // 同一条：外部已先做停播判断，走到这里一定是新条目；先清旧播放
        stop(resetKotlinState: false)

        let resolved = MediaUrlResolver.shared.resolve(url: urlString) ?? urlString
        guard let url = URL(string: resolved) else {
            finish()
            ToastCenter.shared.show("语音地址无效")
            return
        }

        currentLocalId = localId
        if let cached = cachedFileURL(for: url), FileManager.default.fileExists(atPath: cached.path) {
            startPlayback(fileURL: cached, localId: localId)
            return
        }

        var request = URLRequest(url: url)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        loadTask = URLSession.shared.dataTask(with: request) { [weak self] data, _, error in
            DispatchQueue.main.async {
                guard let self, self.currentLocalId == localId else { return }
                if let error {
                    self.finish()
                    ToastCenter.shared.show("语音下载失败：\(error.localizedDescription)")
                    return
                }
                guard let data, let saved = self.saveToCache(data: data, remote: url) else {
                    self.finish()
                    ToastCenter.shared.show("语音缓存失败")
                    return
                }
                self.startPlayback(fileURL: saved, localId: localId)
            }
        }
        loadTask?.resume()
    }

    /// 停止播放。
    ///
    /// ⚠️ 不要在这里清 `endHandler`：开始播放的 `play()` 会先调一次 `stop()` 清旧播放，
    /// 若连回调一起抹掉，语音自然播完时 Compose 侧就收不到通知 —— 气泡会永远停在
    /// 「播放中」样式。回调的生命周期归调用方（聊天页），由它在页面消失时清理。
    func stop(resetKotlinState: Bool = false) {
        loadTask?.cancel()
        loadTask = nil
        player?.stop()
        player = nil
        currentURL = nil
        currentLocalId = nil
        playingLocalId = nil
        // 恢复被语音暂停的主播放器（与视频/Live 预览同一套互斥机制）
        PlayerModel.shared.endTransientMediaPlayback()
        if resetKotlinState {
            endHandler?()
        }
    }

    // MARK: Private

    private func startPlayback(fileURL: URL, localId: String) {
        do {
            // 播放语音前暂停主播放器（对齐 Android 语音与音乐互斥）
            PlayerModel.shared.beginTransientMediaPlayback()
            let audioPlayer = try AVAudioPlayer(contentsOf: fileURL)
            audioPlayer.delegate = self
            audioPlayer.prepareToPlay()
            player = audioPlayer
            currentURL = fileURL
            playingLocalId = localId
            audioPlayer.play()
        } catch {
            finish()
            ToastCenter.shared.show("语音播放失败")
        }
    }

    /// 自然结束/失败：复位 UI（Compose 气泡播放态）+ 恢复音乐。
    private func finish() {
        stop(resetKotlinState: true)
    }

    private func cachedFileURL(for remote: URL) -> URL? {
        let dir = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("voice_msgs", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let hash = abs(remote.absoluteString.hashValue)
        let ext = (remote.pathExtension.isEmpty ? "m4a" : remote.pathExtension)
        return dir.appendingPathComponent("\(hash).\(ext)")
    }

    private func saveToCache(data: Data, remote: URL) -> URL? {
        guard let dest = cachedFileURL(for: remote) else { return nil }
        do {
            try data.write(to: dest, options: .atomic)
            return dest
        } catch {
            return nil
        }
    }
}

extension VoiceMessagePlayer: AVAudioPlayerDelegate {
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        finish()
    }

    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        finish()
    }
}
