import SwiftUI
import FoxShared

/// 歌单广场 Tab：共享层 PlaylistListContent（feature:playlist commonMain）渲染，
/// 分页数据在 Kotlin 侧（Ktor + Paging）自管理，Swift 只接导航回调。
///
/// 用 NavigationStack(path:) + navigationDestination(for:) 而非 item: 版本，
/// 因为部署目标是 iOS 16.0，navigationDestination(item:) 需要 iOS 17+。
struct PlaylistTabView: View {
    @State private var path: [String] = []

    var body: some View {
        NavigationStack(path: $path) {
            PlaylistComposeView { playlist in
                // 详情页后续批次接入 PlaylistDetailContent；本轮先确认导航回调打通
                path.append(playlist.title)
            }
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle("歌单广场")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: String.self) { title in
                VStack(spacing: 12) {
                    Image(systemName: "music.note.list")
                        .font(.system(size: 48))
                        .foregroundStyle(.secondary)
                    Text(title)
                        .font(.headline)
                    Text("歌单详情页迁移中")
                        .foregroundStyle(.secondary)
                }
                .padding()
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.inline)
            }
        }
    }
}

/// 把 Kotlin 侧的歌单广场 VC 包进 SwiftUI。
/// 滚动状态留在 Kotlin/Compose 内部，updateUIViewController 留空避免重建丢位置。
struct PlaylistComposeView: UIViewControllerRepresentable {
    let onPlaylistClick: (Playlist) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxPlaylistScreen.shared.makePlaylistListViewController(
            onPlaylistClick: onPlaylistClick,
            onBackClick: {}
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
