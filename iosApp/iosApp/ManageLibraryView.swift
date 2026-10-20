import SwiftUI
import FoxShared

// MARK: - 曲库管理

/// 曲库管理页（仅管理员可见入口；后端 403 时原文 Toast）。
/// 对齐 Android ManageScreen + ManageViewModel：
/// 曲库分页搜索 / 删除单曲 / 添加音乐（第三方搜索后入库）/ 更新排行榜 / 更新热门歌单。
struct ManageLibraryView: View {
    @StateObject private var model = ManageLibraryModel()
    @State private var showAddMusic = false
    @State private var pendingDelete: PlayableSong?
    @State private var showChartsConfirm = false
    @State private var showHotConfirm = false

    var body: some View {
        Group {
            if !model.loggedIn {
                VStack(spacing: 12) {
                    Image(systemName: "person.crop.circle.badge.exclamationmark")
                        .font(.system(size: 34))
                        .foregroundStyle(.secondary)
                    Text("请先登录后再使用曲库管理")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                listContent
            }
        }
        .navigationTitle("曲库管理")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $model.keyword, placement: .navigationBarDrawer(displayMode: .always),
                    prompt: "搜索曲库")
        .onSubmit(of: .search) { model.reload() }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Menu {
                    Button {
                        showAddMusic = true
                    } label: {
                        Label("添加音乐", systemImage: "plus.circle")
                    }
                    Button {
                        showChartsConfirm = true
                    } label: {
                        Label("更新排行榜", systemImage: "chart.bar")
                    }
                    Button {
                        showHotConfirm = true
                    } label: {
                        Label("更新热门歌单", systemImage: "flame")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .accessibilityIdentifier("manageLibrary.menu")
            }
        }
        .sheet(isPresented: $showAddMusic) {
            ManageSearchView()
        }
        .confirmationDialog(
            "确认删除？",
            isPresented: Binding(
                get: { pendingDelete != nil },
                set: { if !$0 { pendingDelete = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("删除「\(pendingDelete?.title ?? "")」", role: .destructive) {
                if let song = pendingDelete {
                    model.delete(song) { pendingDelete = nil }
                } else {
                    pendingDelete = nil
                }
            }
            Button("取消", role: .cancel) { pendingDelete = nil }
        } message: {
            Text("删除后不可恢复，确定要删除这首歌曲吗？")
        }
        .confirmationDialog("更新排行榜？", isPresented: $showChartsConfirm, titleVisibility: .visible) {
            Button("立即更新") { model.updateCharts() }
            Button("取消", role: .cancel) {}
        } message: {
            Text("将从网易云同步排行榜数据，可能需要一些时间。")
        }
        .confirmationDialog("更新热门歌单？", isPresented: $showHotConfirm, titleVisibility: .visible) {
            Button("立即更新") { model.updateHotPlaylists() }
            Button("取消", role: .cancel) {}
        } message: {
            Text("将触发热门歌单后台同步任务。")
        }
        .overlay {
            if model.busy {
                ZStack {
                    Color.black.opacity(0.35).ignoresSafeArea()
                    VStack(spacing: 10) {
                        ProgressView().tint(.white)
                        Text(model.busyText).font(.caption).foregroundStyle(.white)
                    }
                    .padding(20)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
                }
            }
        }
        .task {
            if model.songs.isEmpty && model.loggedIn { model.reload() }
        }
    }

    private var listContent: some View {
        List {
            ForEach(model.songs, id: \.id) { song in
                ManageMusicRow(song: song)
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            pendingDelete = song
                        } label: {
                            Label("删除", systemImage: "trash")
                        }
                    }
                    .task {
                        if song.id == model.songs.last?.id { model.loadNextIfNeeded() }
                    }
            }
            if model.loadingMore {
                HStack { Spacer(); ProgressView(); Spacer() }
            }
        }
        .overlay {
            if model.loading && model.songs.isEmpty {
                ProgressView()
            } else if !model.loading && model.songs.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "tray")
                        .font(.system(size: 30))
                        .foregroundStyle(.secondary)
                    Text(model.errorText ?? "曲库为空，点右上角添加音乐")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .refreshable { model.reload() }
    }
}

private struct ManageMusicRow: View {
    let song: PlayableSong

    var body: some View {
        HStack(spacing: 10) {
            AsyncImage(url: song.coverUrl.flatMap(URL.init(string:))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                } else {
                    ZStack {
                        Color.secondary.opacity(0.2)
                        Image(systemName: "music.note")
                            .font(.system(size: 14))
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .frame(width: 40, height: 40)
            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                Text(song.title).font(.subheadline).lineLimit(1)
                Text(song.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 2)
        .accessibilityIdentifier("manageLibrary.row.\(song.id)")
    }
}

/// 曲库分页/删除/批量维护的状态持有者。
@MainActor
final class ManageLibraryModel: ObservableObject {
    @Published private(set) var songs: [PlayableSong] = []
    @Published var keyword = ""
    @Published private(set) var loading = false
    @Published private(set) var loadingMore = false
    @Published private(set) var busy = false
    @Published var busyText = "处理中…"
    @Published var errorText: String?
    @Published private(set) var loggedIn = AuthStore.shared.isLoggedIn

    private var page = 1
    private var hasMore = true

    func reload() {
        page = 1
        hasMore = true
        loading = true
        errorText = nil
        FoxManageLibrary.shared.fetchPage(
            page: 1,
            limit: 20,
            keyword: keyword.trimmingCharacters(in: .whitespacesAndNewlines),
            onResult: { [weak self] list, total in
                guard let self else { return }
                self.songs = list
                self.hasMore = list.count < total.intValue
                self.loading = false
            },
            onError: { [weak self] message in
                self?.loading = false
                self?.errorText = message
                ToastCenter.shared.show(message)
            }
        )
    }

    func loadNextIfNeeded() {
        guard hasMore, !loading, !loadingMore else { return }
        loadingMore = true
        let next = page + 1
        FoxManageLibrary.shared.fetchPage(
            page: Int32(next),
            limit: 20,
            keyword: keyword.trimmingCharacters(in: .whitespacesAndNewlines),
            onResult: { [weak self] list, total in
                guard let self else { return }
                self.page = next
                self.songs.append(contentsOf: list)
                self.hasMore = self.songs.count < total.intValue
                self.loadingMore = false
            },
            onError: { [weak self] message in
                self?.loadingMore = false
                ToastCenter.shared.show(message)
            }
        )
    }

    func delete(_ song: PlayableSong, onDone: @escaping () -> Void) {
        guard let musicId = Int64(song.id) else {
            ToastCenter.shared.show("歌曲 ID 异常，无法删除")
            onDone()
            return
        }
        FoxManageLibrary.shared.deleteMusic(
            musicId: musicId,
            onResult: {
                ToastCenter.shared.show("已删除「\(song.title)」")
                onDone()
                self.reload()
            },
            onError: { message in
                ToastCenter.shared.show(message)
                onDone()
            }
        )
    }

    func updateCharts() {
        busy = true
        busyText = "正在更新排行榜…"
        FoxManageLibrary.shared.updateCharts(
            onResult: { total, success, failed in
                self.busy = false
                ToastCenter.shared.show("排行榜更新完成：成功 \(success)，失败 \(failed)（共 \(total)）")
            },
            onError: { message in
                self.busy = false
                ToastCenter.shared.show(message)
            }
        )
    }

    func updateHotPlaylists() {
        busy = true
        busyText = "正在更新热门歌单…"
        FoxManageLibrary.shared.updateHotPlaylists(
            onResult: { message in
                self.busy = false
                ToastCenter.shared.show(message)
            },
            onError: { message in
                self.busy = false
                ToastCenter.shared.show(message)
            }
        )
    }
}

// MARK: - 添加音乐（第三方搜索 → 选择 → 入库）

/// 对齐 Android ManageSearchScreen：网易云/QQ 平台切换、搜索结果试听、多选导入。
struct ManageSearchView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model = ManageSearchModel()
    @ObservedObject private var voice = VoiceMessagePlayer.shared

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("平台", selection: $model.platform) {
                    Text("网易云").tag("netease")
                    Text("QQ 音乐").tag("qq")
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .onChange(of: model.platform) { _ in
                    if model.hasSearched && !model.keyword.isEmpty { model.search() }
                }
                List {
                    ForEach(model.results, id: \.id) { song in
                        HStack(spacing: 10) {
                            Button {
                                model.toggleSelect(song)
                            } label: {
                                Image(systemName: model.selectedIds.contains(song.id)
                                      ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 20))
                                .foregroundStyle(model.selectedIds.contains(song.id) ? Color.accentColor : .secondary)
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("manageSearch.select.\(song.id)")

                            Button {
                                togglePreview(song)
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(song.title).font(.subheadline).lineLimit(1)
                                    Text(song.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)

                            if voice.playingLocalId == "manage_preview_\(song.id)" {
                                Image(systemName: "speaker.wave.2.fill")
                                    .font(.system(size: 14))
                                    .foregroundStyle(Color.accentColor)
                            }
                        }
                        .task {
                            if song.id == model.results.last?.id { model.loadNextIfNeeded() }
                        }
                    }
                    if model.loadingMore {
                        HStack { Spacer(); ProgressView(); Spacer() }
                    }
                }
                .listStyle(.plain)
                .overlay {
                    if model.loading {
                        ProgressView()
                    } else if model.hasSearched && model.results.isEmpty {
                        Text("没有搜索到相关歌曲")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    } else if !model.hasSearched {
                        Text("输入歌名或歌手，搜索后勾选要入库的歌曲")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                }
            }
            .navigationTitle("添加音乐")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $model.keyword, placement: .navigationBarDrawer(displayMode: .always),
                        prompt: "搜索第三方曲库")
            .onSubmit(of: .search) { model.search() }
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("导入 \(model.selectedIds.isEmpty ? "" : "(\(model.selectedIds.count))")") {
                        Task { await model.importSelected() }
                    }
                    .disabled(model.selectedIds.isEmpty || model.importing)
                    .accessibilityIdentifier("manageSearch.import")
                }
            }
            .overlay {
                if model.importing {
                    ZStack {
                        Color.black.opacity(0.35).ignoresSafeArea()
                        VStack(spacing: 10) {
                            ProgressView().tint(.white)
                            Text(model.importText).font(.caption).foregroundStyle(.white)
                        }
                        .padding(20)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
                    }
                }
            }
            .onDisappear { VoiceMessagePlayer.shared.stop() }
        }
    }

    private func togglePreview(_ song: PlayableSong) {
        let localId = "manage_preview_\(song.id)"
        if voice.playingLocalId == localId {
            voice.stop()
        } else {
            if song.audioUrl.isEmpty {
                ToastCenter.shared.show("当前歌曲无法试听")
                return
            }
            voice.play(urlString: song.audioUrl, localId: localId)
        }
    }
}

/// 第三方搜索 + 多选入库的状态持有者。
@MainActor
final class ManageSearchModel: ObservableObject {
    @Published var keyword = ""
    @Published var platform = "netease"
    @Published private(set) var results: [PlayableSong] = []
    @Published private(set) var hasSearched = false
    @Published private(set) var loading = false
    @Published private(set) var loadingMore = false
    @Published private(set) var selectedIds = Set<String>()
    @Published private(set) var importing = false
    @Published var importText = "正在导入…"

    private var page = 1
    private var hasMore = false

    func toggleSelect(_ song: PlayableSong) {
        if selectedIds.contains(song.id) {
            selectedIds.remove(song.id)
        } else {
            selectedIds.insert(song.id)
        }
    }

    func search() {
        let kw = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !kw.isEmpty else {
            ToastCenter.shared.show("请输入搜索关键词")
            return
        }
        page = 1
        hasMore = false
        loading = true
        hasSearched = true
        FoxManageLibrary.shared.searchPlatform(
            platform: platform,
            keyword: kw,
            page: 1,
            limit: 20,
            onResult: { [weak self] list, more in
                self?.results = list
                self?.hasMore = more.boolValue
                self?.loading = false
            },
            onError: { [weak self] message in
                self?.loading = false
                ToastCenter.shared.show(message)
            }
        )
    }

    func loadNextIfNeeded() {
        guard hasMore, !loading, !loadingMore else { return }
        loadingMore = true
        let next = page + 1
        let kw = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
        FoxManageLibrary.shared.searchPlatform(
            platform: platform,
            keyword: kw,
            page: Int32(next),
            limit: 20,
            onResult: { [weak self] list, more in
                guard let self else { return }
                self.page = next
                self.results.append(contentsOf: list)
                self.hasMore = more.boolValue
                self.loadingMore = false
            },
            onError: { [weak self] message in
                self?.loadingMore = false
                ToastCenter.shared.show(message)
            }
        )
    }

    func importSelected() async {
        let items = results.filter { selectedIds.contains($0.id) }
        guard !items.isEmpty else { return }
        importing = true
        var success = 0
        var failed = 0
        for (index, song) in items.enumerated() {
            importText = "正在导入「\(song.title)」（\(index + 1)/\(items.count)）"
            let ok = await importOne(song)
            if ok { success += 1 } else { failed += 1 }
        }
        importing = false
        selectedIds.removeAll()
        ToastCenter.shared.show("导入结果：成功 \(success) 首，失败 \(failed) 首")
    }

    private func importOne(_ song: PlayableSong) async -> Bool {
        await withCheckedContinuation { continuation in
            FoxManageLibrary.shared.importMusic(
                song: song,
                onResult: { continuation.resume(returning: true) },
                onError: { _ in continuation.resume(returning: false) }
            )
        }
    }
}
