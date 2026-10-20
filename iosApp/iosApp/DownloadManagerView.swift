import SwiftUI
import FoxShared

/// 下载管理页（对齐 Android DownloadManagerScreen）：
/// 顶部存储概览 + 仅 Wi-Fi 开关；下方分「下载中」「已下载」两组；
/// 支持暂停 / 继续 / 重试 / 删除，点已完成项直接走本地文件播放。
struct DownloadManagerView: View {
    @ObservedObject private var manager = MusicDownloadManager.shared
    @State private var pendingDelete: DownloadTask?

    private var activeTasks: [DownloadTask] {
        manager.tasks.filter { $0.status != .completed }
    }
    private var completedTasks: [DownloadTask] {
        manager.tasks.filter { $0.status == .completed }
    }

    var body: some View {
        List {
            Section {
                overviewRow
                Toggle(isOn: Binding(
                    get: { manager.wifiOnly },
                    set: { manager.wifiOnly = $0 }
                )) {
                    Label("仅 Wi-Fi 下载", systemImage: manager.isOnWifi ? "wifi" : "antenna.radiowaves.left.and.right.slash")
                }
                .accessibilityIdentifier("downloads.wifiOnly")
                if !manager.isNetworkAvailable {
                    Label("当前无网络，任务将在联网后继续", systemImage: "wifi.slash")
                        .font(.caption)
                        .foregroundStyle(.orange)
                } else if manager.wifiOnly && !manager.isOnWifi {
                    Label("当前为移动网络，下载已暂停", systemImage: "antenna.radiowaves.left.and.right")
                        .font(.caption)
                        .foregroundStyle(.orange)
                }
            }

            if manager.tasks.isEmpty {
                emptyState
            } else {
                if !activeTasks.isEmpty {
                    Section {
                        ForEach(activeTasks) { task in
                            DownloadTaskRow(task: task, pendingDelete: $pendingDelete)
                        }
                    } header: {
                        HStack {
                            Text("下载中")
                            Spacer()
                            if activeTasks.contains(where: { $0.status == .downloading }) {
                                Button("全部暂停") {
                                    for task in activeTasks where task.status == .downloading {
                                        manager.pauseDownload(id: task.id)
                                    }
                                }
                                .font(.caption)
                            }
                            if activeTasks.contains(where: { $0.status == .paused || $0.status == .waiting || $0.status == .failed }) {
                                Button("全部继续") {
                                    for task in activeTasks
                                    where task.status == .paused || task.status == .waiting || task.status == .failed {
                                        manager.resumeDownload(id: task.id)
                                    }
                                }
                                .font(.caption)
                            }
                        }
                        .accessibilityIdentifier("downloads.activeSectionHeader")
                    }
                }
                if !completedTasks.isEmpty {
                    Section("已下载") {
                        ForEach(completedTasks) { task in
                            CompletedDownloadRow(task: task, pendingDelete: $pendingDelete)
                        }
                    }
                }
            }
        }
        .navigationTitle("下载管理")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        // 设置页栈内系统 Tab 栏被隐藏，但迷你播放条要保留 ——
        // 否则下载页无法看到正在播放的歌、也进不了播放器详情。
        .withMiniPlayer(isEnabled: true)
        .confirmationDialog(
            "删除下载？",
            isPresented: Binding(
                get: { pendingDelete != nil },
                set: { if !$0 { pendingDelete = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("删除「\(pendingDelete?.title ?? "")」", role: .destructive) {
                if let task = pendingDelete {
                    if task.status == .completed {
                        manager.removeCompleted(id: task.id)
                    } else {
                        manager.cancelDownload(id: task.id)
                    }
                }
                pendingDelete = nil
            }
            Button("取消", role: .cancel) { pendingDelete = nil }
        } message: {
            Text("已下载的本地文件会一并删除，确定吗？")
        }
    }

    private var overviewRow: some View {
        HStack(spacing: 10) {
            Image(systemName: "internaldrive")
                .font(.system(size: 18))
                .foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 2) {
                Text("本地存储")
                    .font(.subheadline)
                Text("已下载 \(completedTasks.count) 首 · 占用 \(Self.formatBytes(manager.totalUsageBytes))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .accessibilityIdentifier("downloads.storageOverview")
        // 让容器本身作为无障碍组元素暴露（否则 identifier 落在无交互 HStack 上，
        // UI 测试的 otherElements 查不到，只能查到内部文本）
        .accessibilityElement(children: .contain)
    }

    private var emptyState: some View {
        Section {
            VStack(spacing: 10) {
                Image(systemName: "arrow.down.circle")
                    .font(.system(size: 36))
                    .foregroundStyle(.secondary)
                Text("暂无下载任务")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text("在歌曲菜单中选择「下载」即可离线播放")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, minHeight: 180)
        }
    }

    static func formatBytes(_ bytes: Int64) -> String {
        if bytes <= 0 { return "0 KB" }
        let kb = Double(bytes) / 1024
        if kb < 1024 { return String(format: "%.0f KB", kb) }
        let mb = kb / 1024
        if mb < 1024 { return String(format: "%.1f MB", mb) }
        return String(format: "%.2f GB", mb / 1024)
    }
}

// MARK: - 进行中任务行

private struct DownloadTaskRow: View {
    let task: DownloadTask
    @Binding var pendingDelete: DownloadTask?
    @ObservedObject private var manager = MusicDownloadManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                coverThumb
                VStack(alignment: .leading, spacing: 2) {
                    Text(task.title).font(.subheadline).lineLimit(1)
                    Text(task.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                Spacer()
                Text(statusLabel).font(.caption2).foregroundStyle(statusColor)
            }
            if task.status == .downloading || task.status == .paused || task.status == .waiting {
                ProgressView(value: task.progress)
                Text("\(DownloadManagerView.formatBytes(task.downloadedBytes)) / \(DownloadManagerView.formatBytes(task.totalBytes))")
                    .font(.caption2).foregroundStyle(.secondary)
            }
            if task.status == .failed, let msg = task.errorMessage {
                Text(msg).font(.caption2).foregroundStyle(.red).lineLimit(2)
            } else if (task.status == .waiting || task.status == .paused), let msg = task.errorMessage {
                Text(msg).font(.caption2).foregroundStyle(.orange).lineLimit(2)
            }
            HStack(spacing: 12) {
                if task.status == .downloading {
                    Button {
                        manager.pauseDownload(id: task.id)
                    } label: {
                        Label("暂停", systemImage: "pause.circle")
                    }
                    .buttonStyle(.bordered).controlSize(.small)
                } else if task.status == .paused || task.status == .waiting {
                    Button {
                        manager.resumeDownload(id: task.id)
                    } label: {
                        Label("继续", systemImage: "play.circle")
                    }
                    .buttonStyle(.bordered).controlSize(.small)
                } else if task.status == .failed {
                    Button {
                        manager.resumeDownload(id: task.id)
                    } label: {
                        Label("重试", systemImage: "arrow.clockwise.circle")
                    }
                    .buttonStyle(.bordered).controlSize(.small)
                }
                Button(role: .destructive) {
                    pendingDelete = task
                } label: {
                    Label("取消", systemImage: "xmark.circle")
                }
                .buttonStyle(.bordered).controlSize(.small)
            }
            .padding(.top, 2)
        }
        .padding(.vertical, 4)
        .accessibilityIdentifier("downloads.activeRow.\(task.musicId)")
    }

    private var coverThumb: some View {
        AsyncImage(url: task.coverUrl.flatMap(URL.init(string:))) { phase in
            if let image = phase.image {
                image.resizable().scaledToFill()
            } else {
                ZStack {
                    Color.secondary.opacity(0.2)
                    Image(systemName: "music.note")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
            }
        }
        .frame(width: 36, height: 36)
        .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
    }

    private var statusLabel: String {
        switch task.status {
        case .waiting: return "等待中"
        case .downloading: return "下载中 \(Int(task.progress * 100))%"
        case .paused: return "已暂停"
        case .completed: return "已完成"
        case .failed: return "失败"
        }
    }

    private var statusColor: Color {
        switch task.status {
        case .downloading: return .blue
        case .completed: return .green
        case .failed: return .red
        case .paused: return .orange
        default: return .secondary
        }
    }
}

// MARK: - 已完成任务行（点击本地播放）

private struct CompletedDownloadRow: View {
    let task: DownloadTask
    @Binding var pendingDelete: DownloadTask?
    @ObservedObject private var manager = MusicDownloadManager.shared
    @ObservedObject private var player = PlayerModel.shared

    var body: some View {
        Button {
            playLocal()
        } label: {
            HStack(spacing: 10) {
                AsyncImage(url: task.coverUrl.flatMap(URL.init(string:))) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    } else {
                        ZStack {
                            Color.secondary.opacity(0.2)
                            Image(systemName: "music.note")
                                .font(.system(size: 12))
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .frame(width: 36, height: 36)
                .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    Text(task.title).font(.subheadline).lineLimit(1).foregroundStyle(.primary)
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.down.circle.fill")
                            .font(.system(size: 9))
                        Text(task.artist).lineLimit(1)
                    }
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                }
                Spacer()
                if player.current?.id == task.musicId {
                    Image(systemName: "speaker.wave.2.fill")
                        .font(.system(size: 13))
                        .foregroundStyle(Color.accentColor)
                }
            }
            .padding(.vertical, 2)
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) {
                pendingDelete = task
            } label: {
                Label("删除", systemImage: "trash")
            }
        }
        .accessibilityIdentifier("downloads.completedRow.\(task.musicId)")
    }

    /// 点击已下载项：把**整个已下载列表**按列表顺序入队，从点中的这首开始播
    /// （对齐 Android DownloadViewModel.playDownload/buildPlayablePlaylist）。
    /// 歌词读随下载持久化的本地记录，离线也能展示；缺歌词的老记录后台静默补全，
    /// 不弹「歌词已更换」。
    private func playLocal() {
        let songs: [PlayableSong] = manager.tasks.compactMap { item in
            guard item.status == .completed,
                  let path = manager.localPath(for: item.musicId) else { return nil }
            return PlayableSong(
                id: item.musicId,
                title: item.title,
                artist: item.artist,
                audioUrl: URL(fileURLWithPath: path).absoluteString,
                coverUrl: item.coverUrl,
                durationMillis: item.durationMillis,
                lyric: item.lyric,
                lyricTrans: item.lyricTrans,
                commentCount: 0,
                isFavorite: false
            )
        }
        guard !songs.isEmpty else {
            ToastCenter.shared.show("本地文件不存在，请重新下载")
            return
        }
        let startIndex = songs.firstIndex { $0.id == task.musicId } ?? 0
        PlayerModel.shared.play(
            queue: songs,
            startIndex: startIndex,
            source: PlaybackSource(title: "我的下载", typeLabel: "我的下载")
        )
        // 老下载记录缺歌词/时长的：后台静默拉取并落库（新下载在入队时已随音频持久化）
        manager.backfillCompletedMetadata()
    }
}
