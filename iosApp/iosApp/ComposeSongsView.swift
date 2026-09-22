import SwiftUI
import FoxShared

/// 把共享层（Kotlin / Compose Multiplatform）写的歌曲列表接进 SwiftUI 层级。
///
/// 这是"共享 UI + 平台原生外壳"的标准接法：Kotlin 侧产出 UIViewController，
/// Swift 侧只负责把它放进自己的视图树。外壳（导航栏、迷你播放条）仍用 SwiftUI，
/// 列表内容则由两端同一份 Kotlin 代码渲染。
///
/// 播放状态不经过这里：PlayerModel 直接写 FoxComposeState，Compose 内部重组。
/// 因此 updateUIViewController 无需做任何事——重建 controller 反而会丢失滚动位置。
struct ComposeSongsView: UIViewControllerRepresentable {
    let songs: [Music]
    let onPlaySong: (PlayableSong) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        FoxIosBridge.shared.makeSongsViewController(songs: songs, onPlaySong: onPlaySong)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // 状态回灌走 FoxComposeState，这里刻意留空
    }
}
