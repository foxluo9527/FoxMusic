import Foundation
import Network
import FoxShared

// MARK: - 下载任务模型

enum DownloadStatus: String, Codable {
    case waiting, downloading, paused, completed, failed
}

struct DownloadTask: Codable, Identifiable {
    let id: String          // 用 musicId 字符串
    let musicId: String
    let title: String
    let artist: String
    let coverUrl: String?
    let sourceUrl: String
    var localPath: String?
    var status: DownloadStatus
    var progress: Double       // 0...1
    var totalBytes: Int64
    var downloadedBytes: Int64
    var errorMessage: String?
    var createdAt: Date
    /// 随歌曲一起本地持久化的歌词（对齐 Android DownloadEntity.lyrics/lyricsTrans），
    /// 离线播放下载歌曲时直接读这里，不再联网。
    var lyric: String?
    var lyricTrans: String?
    var durationMillis: Int64

    enum CodingKeys: String, CodingKey {
        case id, musicId, title, artist, coverUrl, sourceUrl, localPath, status,
             progress, totalBytes, downloadedBytes, errorMessage, createdAt,
             lyric, lyricTrans, durationMillis
    }

    init(id: String,
         musicId: String,
         title: String,
         artist: String,
         coverUrl: String?,
         sourceUrl: String,
         localPath: String?,
         status: DownloadStatus,
         progress: Double,
         totalBytes: Int64,
         downloadedBytes: Int64,
         errorMessage: String?,
         createdAt: Date,
         lyric: String? = nil,
         lyricTrans: String? = nil,
         durationMillis: Int64 = 0) {
        self.id = id
        self.musicId = musicId
        self.title = title
        self.artist = artist
        self.coverUrl = coverUrl
        self.sourceUrl = sourceUrl
        self.localPath = localPath
        self.status = status
        self.progress = progress
        self.totalBytes = totalBytes
        self.downloadedBytes = downloadedBytes
        self.errorMessage = errorMessage
        self.createdAt = createdAt
        self.lyric = lyric
        self.lyricTrans = lyricTrans
        self.durationMillis = durationMillis
    }

    // 旧 tasks.json 没有歌词/时长字段：缺省解为 nil/0，播放/补全逻辑会自动回填。
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        musicId = try c.decode(String.self, forKey: .musicId)
        title = try c.decode(String.self, forKey: .title)
        artist = try c.decode(String.self, forKey: .artist)
        coverUrl = try c.decodeIfPresent(String.self, forKey: .coverUrl)
        sourceUrl = try c.decode(String.self, forKey: .sourceUrl)
        localPath = try c.decodeIfPresent(String.self, forKey: .localPath)
        status = try c.decode(DownloadStatus.self, forKey: .status)
        progress = try c.decode(Double.self, forKey: .progress)
        totalBytes = try c.decodeIfPresent(Int64.self, forKey: .totalBytes) ?? 0
        downloadedBytes = try c.decodeIfPresent(Int64.self, forKey: .downloadedBytes) ?? 0
        errorMessage = try c.decodeIfPresent(String.self, forKey: .errorMessage)
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date()
        lyric = try c.decodeIfPresent(String.self, forKey: .lyric)
        lyricTrans = try c.decodeIfPresent(String.self, forKey: .lyricTrans)
        durationMillis = try c.decodeIfPresent(Int64.self, forKey: .durationMillis) ?? 0
    }
}

// MARK: - 下载引擎

/// 音乐下载引擎：URLSession 后台下载 + JSON 持久化 + 仅 Wi-Fi 开关。
/// 对齐安卓 MusicDownloadManager：直接下载 sourceUrl，不走转码。
final class MusicDownloadManager: NSObject, ObservableObject {
    static let shared = MusicDownloadManager()

    @Published private(set) var tasks: [DownloadTask] = []
    /// 仅 Wi-Fi 下载（持久化到 UserDefaults；关闭后允许蜂窝）。
    @Published var wifiOnly: Bool {
        didSet {
            UserDefaults.standard.set(wifiOnly, forKey: Self.wifiOnlyKey)
            // 放宽限制时把因蜂窝被拦的等待/失败任务重新拉起
            if !wifiOnly { kickWaitingTasks() }
        }
    }
    /// 当前是否处于 Wi-Fi（或有线等非蜂窝网络）。NWPathMonitor 实时更新。
    @Published private(set) var isOnWifi = true
    /// 当前网络是否可用。
    @Published private(set) var isNetworkAvailable = true

    private let fileManager = FileManager.default
    private var session: URLSession!
    private var activeTasks: [String: URLSessionDownloadTask] = [:]
    private var resumeDataMap: [String: Data] = [:]
    /// 正在补全元数据（歌词/时长）的任务，避免重复请求。
    private var metadataInFlight = Set<String>()
    private let monitor = NWPathMonitor()
    private let monitorQueue = DispatchQueue(label: "com.fox.music.download.network")

    private static let wifiOnlyKey = "foxdownload.wifiOnly"

    private var persistenceURL: URL {
        let dir = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("downloads", isDirectory: true)
        try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("tasks.json")
    }

    private var downloadsDir: URL {
        let dir = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("downloads/music", isDirectory: true)
        try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private override init() {
        // @Published 初值必须在 super.init 前给定；wifiOnly 从 UserDefaults 读历史设置
        let savedWifiOnly = UserDefaults.standard.object(forKey: Self.wifiOnlyKey) as? Bool ?? true
        wifiOnly = savedWifiOnly
        super.init()
        let config = URLSessionConfiguration.background(withIdentifier: "com.fox.music.download")
        config.sessionSendsLaunchEvents = true
        config.isDiscretionary = false
        session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        loadTasks()
        startNetworkMonitor()
    }

    // MARK: - 持久化

    private func loadTasks() {
        guard let data = try? Data(contentsOf: persistenceURL) else { return }
        if let decoded = try? JSONDecoder().decode([DownloadTask].self, from: data) {
            // 恢复：downloading 状态重置为 waiting（应用重启后重新下载）
            tasks = decoded.map {
                $0.status == .downloading ? DownloadTask(
                    id: $0.id, musicId: $0.musicId, title: $0.title, artist: $0.artist,
                    coverUrl: $0.coverUrl, sourceUrl: $0.sourceUrl, localPath: $0.localPath,
                    status: .waiting, progress: $0.progress, totalBytes: $0.totalBytes,
                    downloadedBytes: $0.downloadedBytes, errorMessage: nil, createdAt: $0.createdAt,
                    lyric: $0.lyric, lyricTrans: $0.lyricTrans, durationMillis: $0.durationMillis
                ) : $0
            }
        }
    }

    private func saveTasks() {
        if let data = try? JSONEncoder().encode(tasks) {
            try? data.write(to: persistenceURL)
        }
    }

    private func updateTask(id: String, _ transform: (inout DownloadTask) -> Void) {
        guard let idx = tasks.firstIndex(where: { $0.id == id }) else { return }
        transform(&tasks[idx])
        saveTasks()
    }

    // MARK: - 公共 API

    func startDownload(musicId: String, title: String, artist: String, coverUrl: String?, sourceUrl: String) {
        if let existing = tasks.first(where: { $0.musicId == musicId }) {
            if existing.status == .completed { return }
            if existing.status == .downloading { return }
            // 重新下载失败/暂停的
            updateTask(id: existing.id) { $0.status = .waiting; $0.errorMessage = nil }
            beginDownload(taskId: existing.id)
            // 老任务可能也没存歌词，重新下载时一并补全
            resolveAndStoreMetadata(taskId: existing.id)
            return
        }
        let task = DownloadTask(
            id: musicId, musicId: musicId, title: title, artist: artist, coverUrl: coverUrl,
            sourceUrl: sourceUrl, localPath: nil, status: .waiting, progress: 0,
            totalBytes: 0, downloadedBytes: 0, errorMessage: nil, createdAt: Date()
        )
        tasks.insert(task, at: 0)
        saveTasks()
        beginDownload(taskId: task.id)
        // 歌词/时长随下载一起本地持久化（对齐 Android enqueue 时 resolveLyrics 落库）
        resolveAndStoreMetadata(taskId: task.id)
    }

    // MARK: - 歌词元数据本地持久化

    /// 为所有已完成但缺歌词的下载记录补全歌词（老版本下载的歌曲用）。
    /// 播放下载列表时触发，纯后台静默执行：拉到后落 tasks.json 并同步内存播放队列。
    func backfillCompletedMetadata() {
        for task in tasks where task.status == .completed {
            if (task.lyric ?? "").isEmpty || task.durationMillis <= 0 {
                resolveAndStoreMetadata(taskId: task.id)
            }
        }
    }

    /// 拉取歌曲详情/歌词并写入下载记录。音频文件与歌词各自独立落盘，
    /// 完成后离线播放直接读本地记录。
    private func resolveAndStoreMetadata(taskId: String) {
        guard !metadataInFlight.contains(taskId),
              let musicId = Int64(taskId) else { return }
        metadataInFlight.insert(taskId)
        // 先取详情拿时长与内嵌歌词；内嵌歌词缺失时再走第三方 import/lyrics。
        FoxIosBridge.shared.fetchPlayableSong(
            musicId: musicId,
            onResult: { [weak self] song in
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.metadataInFlight.remove(taskId)
                    guard self.tasks.contains(where: { $0.id == taskId }) else { return }
                    let embeddedLyric = song.lyric?.trimmingCharacters(in: .whitespacesAndNewlines)
                    self.updateTask(id: taskId) { task in
                        if task.durationMillis <= 0 { task.durationMillis = song.durationMillis }
                        if (task.lyric ?? "").isEmpty,
                           let lyric = embeddedLyric, !lyric.isEmpty {
                            task.lyric = song.lyric
                        }
                        if (task.lyricTrans ?? "").isEmpty,
                           let trans = song.lyricTrans?.trimmingCharacters(in: .whitespacesAndNewlines),
                           !trans.isEmpty {
                            task.lyricTrans = song.lyricTrans
                        }
                    }
                    if embeddedLyric == nil || embeddedLyric?.isEmpty == true {
                        self.fetchImportedLyrics(taskId: taskId)
                    } else {
                        self.syncLyricsToPlayer(taskId: taskId)
                    }
                }
            },
            onError: { [weak self] _ in
                // 详情也失败：离线/接口异常，静默（歌词拉不到不影响音频下载与播放）
                DispatchQueue.main.async { self?.metadataInFlight.remove(taskId) }
            }
        )
    }

    private func fetchImportedLyrics(taskId: String) {
        guard let musicId = Int64(taskId) else { return }
        FoxIosBridge.shared.fetchSongLyrics(
            musicId: musicId,
            onResult: { [weak self] lyric, translation in
                self?.storeMetadata(taskId: taskId, lyric: lyric, translation: translation)
            },
            onError: { [weak self] _ in
                DispatchQueue.main.async { self?.metadataInFlight.remove(taskId) }
            }
        )
    }

    private func storeMetadata(taskId: String, lyric: String, translation: String?) {
        DispatchQueue.main.async {
            self.metadataInFlight.remove(taskId)
            guard self.tasks.contains(where: { $0.id == taskId }) else { return }
            self.updateTask(id: taskId) { task in
                if (task.lyric ?? "").isEmpty { task.lyric = lyric }
                if (task.lyricTrans ?? "").isEmpty, let translation {
                    task.lyricTrans = translation
                }
            }
            self.syncLyricsToPlayer(taskId: taskId)
        }
    }

    private func syncLyricsToPlayer(taskId: String) {
        guard let task = tasks.first(where: { $0.id == taskId }) else { return }
        // 若这首歌正在内存播放队列里（下载列表已起播），静默补上歌词/时长 ——
        // 不走 applyLyrics：那是用户手动纠正入口，会弹「歌词已更换」。
        PlayerModel.shared.applyDownloadedLyrics(
            songId: taskId,
            lyrics: task.lyric ?? "",
            translation: task.lyricTrans,
            durationMillis: task.durationMillis
        )
    }

    func pauseDownload(id: String) {
        guard let task = activeTasks[id] else { return }
        task.cancel { [weak self] resumeData in
            self?.resumeDataMap[id] = resumeData
            self?.updateTask(id: id) { $0.status = .paused }
            self?.activeTasks.removeValue(forKey: id)
        }
    }

    /// 继续/重试：暂停、失败、等待中的任务都可重新拉起。
    func resumeDownload(id: String) {
        guard let status = tasks.first(where: { $0.id == id })?.status,
              status == .paused || status == .failed || status == .waiting else { return }
        if status != .paused { resumeDataMap.removeValue(forKey: id) }
        updateTask(id: id) { $0.errorMessage = nil }
        beginDownload(taskId: id)
    }

    func cancelDownload(id: String) {
        activeTasks[id]?.cancel()
        activeTasks.removeValue(forKey: id)
        resumeDataMap.removeValue(forKey: id)
        if let idx = tasks.firstIndex(where: { $0.id == id }) {
            let task = tasks[idx]
            if let path = resolvedLocalPath(for: task) { try? fileManager.removeItem(atPath: path) }
            tasks.remove(at: idx)
            saveTasks()
        }
    }

    func removeCompleted(id: String) {
        guard let idx = tasks.firstIndex(where: { $0.id == id }) else { return }
        let task = tasks[idx]
        if task.status == .completed, let path = resolvedLocalPath(for: task) {
            try? fileManager.removeItem(atPath: path)
        }
        tasks.remove(at: idx)
        saveTasks()
    }

    /// 已下载单曲的本地路径（文件存在才返回，避免记录指向被系统清掉的文件）。
    func localPath(for musicId: String) -> String? {
        guard let task = tasks.first(where: { $0.musicId == musicId && $0.status == .completed }) else {
            return nil
        }
        return resolvedLocalPath(for: task)
    }

    /// 解析任务对应文件的当前绝对路径。
    ///
    /// 历史上 localPath 存的是绝对路径，但 App 重装（尤其模拟器 Debug 重装）后
    /// 沙盒容器 UUID 会变，旧绝对路径全部失效 —— 已下载 8.5MB 的歌在 UI 上显示
    /// 占用 0KB。现在持久化只存文件名；同时兼容旧记录：绝对路径不存在时，
    /// 回退到「当前下载目录 + 原文件名」。
    private func resolvedLocalPath(for task: DownloadTask) -> String? {
        guard let stored = task.localPath else { return nil }
        // 1) 存的就是文件名（新格式）或旧绝对路径仍有效
        let direct = stored.hasPrefix("/")
            ? stored
            : downloadsDir.appendingPathComponent(stored).path
        if fileManager.fileExists(atPath: direct) { return direct }
        // 2) 旧记录绝对路径已随容器更换失效：用文件名在当前容器下载目录里找
        let fileName = (stored as NSString).lastPathComponent
        let migrated = downloadsDir.appendingPathComponent(fileName).path
        return fileManager.fileExists(atPath: migrated) ? migrated : nil
    }

    /// 已下载文件总占用（字节）。文件丢失（如旧容器路径失效）时回退用持久化的
    /// downloadedBytes 计入，避免「歌还在列表里、占用却是 0」。
    var totalUsageBytes: Int64 {
        tasks.reduce(into: Int64(0)) { acc, task in
            guard task.status == .completed else { return }
            if let path = resolvedLocalPath(for: task),
               let attrs = try? fileManager.attributesOfItem(atPath: path),
               let size = attrs[.size] as? NSNumber {
                acc += size.int64Value
            } else {
                acc += max(task.downloadedBytes, task.totalBytes)
            }
        }
    }

    // MARK: - 网络监听（NWPathMonitor）

    private func startNetworkMonitor() {
        monitor.pathUpdateHandler = { [weak self] path in
            DispatchQueue.main.async {
                guard let self else { return }
                let available = path.status == .satisfied
                // Wi-Fi / 有线视为非蜂窝；其余接口（蜂窝）在仅 Wi-Fi 模式下拦截
                let wifiLike = available && !path.availableInterfaces.contains {
                    $0.type == .cellular
                }
                self.isNetworkAvailable = available
                self.isOnWifi = wifiLike
                if available && (!self.wifiOnly || wifiLike) {
                    self.kickWaitingTasks()
                }
            }
        }
        monitor.start(queue: monitorQueue)
    }

    /// 当前策略下是否允许发起下载。
    private var downloadAllowedNow: Bool {
        guard isNetworkAvailable else { return false }
        return !wifiOnly || isOnWifi
    }

    private func policyErrorMessage() -> String {
        if !isNetworkAvailable { return "当前无网络连接，下载已等待" }
        return "当前为移动网络，已暂停下载（可在下载页关闭「仅 Wi-Fi 下载」）"
    }

    /// 把等待中的任务按当前网络策略拉起（重启恢复 / 网络恢复 / 关闭仅 Wi-Fi 时调用）。
    private func kickWaitingTasks() {
        guard downloadAllowedNow else { return }
        for task in tasks where task.status == .waiting {
            beginDownload(taskId: task.id)
        }
    }

    // MARK: - 下载执行

    private func beginDownload(taskId: String) {
        guard let task = tasks.first(where: { $0.id == taskId }) else { return }
        // 网络策略拦截：保持等待态并提示（不标失败 —— Wi-Fi 恢复后 monitor 会自动拉起）
        guard downloadAllowedNow else {
            updateTask(id: taskId) {
                $0.status = .waiting
                $0.errorMessage = policyErrorMessage()
            }
            if wifiOnly && !isOnWifi {
                ToastCenter.shared.show(policyErrorMessage())
            }
            return
        }
        // 对齐安卓 MusicDownloadManager.downloadFile：接口返回的 /uploads/... 是相对路径，
        // 必须经 MediaUrlResolver 拼上 MEDIA_BASE_URL，否则 URLSession 报"不支持的 URL"。
        // 在执行时（而非入队时）解析，已入队的旧任务重试时也能自动修复。
        guard let resolved = MediaUrlResolver.shared.resolve(url: task.sourceUrl),
              let url = URL(string: resolved) else {
            updateTask(id: taskId) { $0.status = .failed; $0.errorMessage = "无效的下载地址" }
            return
        }
        let request = URLRequest(url: url)
        let downloadTask: URLSessionDownloadTask
        if let resumeData = resumeDataMap[taskId] {
            downloadTask = session.downloadTask(withResumeData: resumeData)
            resumeDataMap.removeValue(forKey: taskId)
        } else {
            downloadTask = session.downloadTask(with: request)
        }
        activeTasks[taskId] = downloadTask
        updateTask(id: taskId) { $0.status = .downloading; $0.errorMessage = nil }
        downloadTask.resume()
    }
}

// MARK: - URLSession Download Delegate

extension MusicDownloadManager: URLSessionDownloadDelegate {
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        guard let taskId = activeTasks.first(where: { $0.value == downloadTask })?.key else { return }
        guard let task = tasks.first(where: { $0.id == taskId }) else { return }
        let ext = (task.sourceUrl as NSString).pathExtension.isEmpty ? "mp3" : (task.sourceUrl as NSString).pathExtension
        let filename = "\(task.musicId).\(ext)"
        let destURL = downloadsDir.appendingPathComponent(filename)
        do {
            if fileManager.fileExists(atPath: destURL.path) { try fileManager.removeItem(at: destURL) }
            try fileManager.moveItem(at: location, to: destURL)
            DispatchQueue.main.async {
                self.updateTask(id: taskId) {
                    $0.status = .completed
                    // 只持久化文件名：沙盒容器 UUID 会随重装变化，
                    // 绝对路径会在下次启动后失效（读取时由当前下载目录拼回）。
                    $0.localPath = filename
                    $0.progress = 1.0
                    $0.downloadedBytes = $0.totalBytes
                }
                self.activeTasks.removeValue(forKey: taskId)
                // 入队时若离线导致歌词没拉到，完成时（此刻通常已联网）再补一次
                self.resolveAndStoreMetadata(taskId: taskId)
                ToastCenter.shared.show("下载完成：\(task.title)")
            }
        } catch {
            DispatchQueue.main.async {
                self.updateTask(id: taskId) { $0.status = .failed; $0.errorMessage = error.localizedDescription }
                self.activeTasks.removeValue(forKey: taskId)
            }
        }
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        guard let taskId = activeTasks.first(where: { $0.value == downloadTask })?.key else { return }
        let progress = totalBytesExpectedToWrite > 0 ? Double(totalBytesWritten) / Double(totalBytesExpectedToWrite) : 0
        DispatchQueue.main.async {
            self.updateTask(id: taskId) {
                $0.progress = progress
                $0.downloadedBytes = totalBytesWritten
                $0.totalBytes = totalBytesExpectedToWrite
            }
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let downloadTask = task as? URLSessionDownloadTask,
              let taskId = activeTasks.first(where: { $0.value == downloadTask })?.key else { return }
        if let error {
            // 断点续传数据
            if let resumeData = (error as NSError).userInfo[NSURLSessionDownloadTaskResumeData] as? Data {
                resumeDataMap[taskId] = resumeData
            }
            DispatchQueue.main.async {
                self.updateTask(id: taskId) { $0.status = .failed; $0.errorMessage = error.localizedDescription }
                self.activeTasks.removeValue(forKey: taskId)
            }
        }
    }
}
