import SwiftUI
import FoxShared

/// 首页 Tab：共享层 HomeContent（feature:home commonMain）渲染 ——
/// 与 Android 首页同一份 UI（品牌头+搜索框+最近在听+推荐歌单+为你精选+热门专辑）。
/// 分页与数据全部在 Kotlin 侧（Ktor + Paging3），Swift 只接导航/播放回调。
enum HomeRoute: Hashable {
    case playlistDetail(id: Int64, title: String, isRank: Bool)
    case artistDetail(id: Int64)
    case search
}

struct HomeTabView: View {
    @State private var path: [HomeRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            HomeComposeView(
                onMusicClickFromList: { songs, index in
                    // 整列表入队并定位到点中的那首 —— 这样上一首/下一首才有东西可切
                    PlayerModel.shared.play(queue: songs, startIndex: Int(index.int32Value))
                },
                onPlaylistClick: { id, title, isRank in
                    path.append(.playlistDetail(id: id.int64Value, title: title, isRank: isRank.boolValue))
                },
                onSearchClick: {
                    path.append(.search)
                }
            )
            .ignoresSafeArea(edges: [.top, .bottom])
            .navigationBarHidden(true)
            .themedPageBackground()
            .navigationDestination(for: HomeRoute.self) { route in
                switch route {
                case .playlistDetail(let id, let title, let isRank):
                    PlaylistDetailView(playlistId: id, isRank: isRank, title: title)
                case .artistDetail(let id):
                    ArtistDetailView(artistId: id)
                case .search:
                    SearchView(onArtistClick: { id in path.append(.artistDetail(id: id)) })
                }
            }
        }
    }
}

/// 把 Kotlin 侧首页 VC 包进 SwiftUI。
/// 滚动/数据状态留在 Kotlin/Compose 内部，updateUIViewController 留空避免重建丢位置。
struct HomeComposeView: UIViewControllerRepresentable {
    let onMusicClickFromList: ([PlayableSong], KotlinInt) -> Void
    let onPlaylistClick: (KotlinLong, String, KotlinBoolean) -> Void
    let onSearchClick: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxHomeScreen.shared.makeHomeViewController(
            onMusicClickFromList: onMusicClickFromList,
            onPlaylistClick: onPlaylistClick,
            onSearchClick: onSearchClick
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
