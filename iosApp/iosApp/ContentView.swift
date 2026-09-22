import SwiftUI
import FoxShared

struct ContentView: View {
    @State private var songs: [Music] = []
    @State private var errorMessage: String?
    @ObservedObject private var player = PlayerModel.shared

    var body: some View {
        NavigationStack {
            Group {
                if let errorMessage {
                    Text("加载失败：\(errorMessage)")
                        .foregroundStyle(.secondary)
                        .padding()
                } else if songs.isEmpty {
                    ProgressView("加载歌曲中…")
                } else {
                    // 列表本体由共享层 Compose 渲染（批次 8 验证点），
                    // 点击回调回到 Swift 播放，播放状态再回灌给 Compose。
                    ComposeSongsView(songs: songs) { song in
                        PlayerModel.shared.play(song)
                    }
                    .ignoresSafeArea(edges: .bottom)
                }
            }
            .navigationTitle("FoxMusic")
            .safeAreaInset(edge: .bottom) {
                if let current = player.current {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(current.title).font(.headline).lineLimit(1)
                            Text(current.artist)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                        Spacer()
                        Button {
                            PlayerModel.shared.togglePlayPause()
                        } label: {
                            Image(systemName: player.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                                .font(.system(size: 36))
                        }
                    }
                    .padding()
                    .background(.bar)
                }
            }
            .onAppear(perform: loadSongs)
        }
    }

    private func loadSongs() {
        FoxIosBridge.shared.fetchMusicList { list in
            // 领域模型交给共享 UI（core:ui 的 MusicListItem），可播放结构交给 AVPlayer
            let musics = list.map { FoxIosBridge.shared.toMusic(music: $0) }
            let playable = list.compactMap { FoxIosBridge.shared.toPlayableSong(music: $0) }
            DispatchQueue.main.async {
                self.songs = musics
                // 里程碑演示：列表加载后自动播放第一首，便于验证后台播放与锁屏显示
                if let first = playable.first {
                    PlayerModel.shared.play(first)
                }
            }
        } onError: { message in
            DispatchQueue.main.async { self.errorMessage = message }
        }
    }
}
