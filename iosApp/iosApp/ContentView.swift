import SwiftUI
import FoxShared

struct ContentView: View {
    @State private var songs: [PlayableSong] = []
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
                    List(songs, id: \.id) { song in
                        Button {
                            PlayerModel.shared.play(song)
                        } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(song.title).font(.headline)
                                    Text(song.artist)
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if player.current?.id == song.id {
                                    Image(systemName: player.isPlaying ? "speaker.wave.2.fill" : "speaker.fill")
                                }
                            }
                        }
                    }
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
            let playable = list.compactMap { FoxIosBridge.shared.toPlayableSong(music: $0) }
            DispatchQueue.main.async {
                self.songs = playable
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
