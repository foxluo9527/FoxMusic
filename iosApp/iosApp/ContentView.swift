import SwiftUI
import FoxShared

/// 应用根视图：SwiftUI TabView 外壳。
///
/// 注意：迷你播放条不能挂在 TabView 上（safeAreaInset(edge: .bottom) 挂在
/// TabView 上会盖住 Tab 栏，Tab 按钮全部点不到）——必须挂在每个 Tab 内容
/// 内部（withMiniPlayer），让迷你条渲染在 Tab 栏之上、与 Tab 栏共存。
/// 内容屏逐步迁移到共享 Compose（feature:x commonMain 的 Content 组件），
/// 导航与播放器这类平台特性保持 SwiftUI/Swift 原生 —— 与"UI 两端共用结构、
/// 平台分支细节"的既定路线一致。
struct ContentView: View {
    var body: some View {
        TabView {
            HomeTabView()
                .withMiniPlayer()
                .tabItem { Label("首页", systemImage: "music.note.house") }
            PlaylistTabView()
                .withMiniPlayer()
                .tabItem { Label("歌单", systemImage: "music.note.list") }
            PlaceholderTabView(title: "搜索", icon: "magnifyingglass")
                .withMiniPlayer()
                .tabItem { Label("搜索", systemImage: "magnifyingglass") }
            PlaceholderTabView(title: "我的", icon: "person.crop.circle")
                .withMiniPlayer()
                .tabItem { Label("我的", systemImage: "person.crop.circle") }
        }
    }
}

/// 迷你播放条修饰器：挂在每个 Tab 的根视图上（而非 TabView），
/// 这样迷你条位于 Tab 栏上方，两者互不遮挡。
struct MiniPlayerModifier: ViewModifier {
    @ObservedObject private var player = PlayerModel.shared

    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .bottom) {
            if let current = player.current {
                MiniPlayerBar(current: current, isPlaying: player.isPlaying)
            }
        }
    }
}

extension View {
    func withMiniPlayer() -> some View { modifier(MiniPlayerModifier()) }
}

/// 全局迷你播放条（从旧 ContentView 抽成组件，跨 Tab 常驻）。
struct MiniPlayerBar: View {
    let current: PlayableSong
    let isPlaying: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(current.title).font(.headline).lineLimit(1)
                    .accessibilityIdentifier("miniPlayer.title")
                Text(current.artist)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            Button {
                PlayerModel.shared.togglePlayPause()
            } label: {
                Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                    .font(.system(size: 36))
            }
            .accessibilityIdentifier("miniPlayer.playPause")
            .accessibilityLabel(isPlaying ? "pause.circle.fill" : "play.circle.fill")
        }
        .padding()
        .background(.bar)
    }
}

/// 占位 Tab（搜索/我的）：对应共享 Content 尚未接入，后续批次替换。
struct PlaceholderTabView: View {
    let title: String
    let icon: String

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 48))
                    .foregroundStyle(.secondary)
                Text("\(title)页迁移中")
                    .foregroundStyle(.secondary)
            }
            .navigationTitle(title)
        }
    }
}
