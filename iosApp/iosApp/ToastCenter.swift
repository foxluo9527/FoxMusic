import SwiftUI

/// 轻量 Toast：Android 端用 `ToastUtils`（收藏成功/歌词已更换/播放失败等都会提示），
/// iOS 没有对应的系统级 Toast，这里做一个最小实现，保持两端反馈一致。
///
/// 挂在根视图上（ContentView）即可全局生效，包括全屏播放器之上。
final class ToastCenter: ObservableObject {
    static let shared = ToastCenter()

    @Published private(set) var message: String?

    private var dismissWork: DispatchWorkItem?

    private init() {}

    /// 显示一条短提示，2 秒后自动消失（新消息会顶掉旧的）。
    func show(_ text: String) {
        guard !text.isEmpty else { return }
        dismissWork?.cancel()
        withAnimation(.easeOut(duration: 0.2)) { message = text }

        let work = DispatchWorkItem { [weak self] in
            withAnimation(.easeIn(duration: 0.25)) { self?.message = nil }
        }
        dismissWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0, execute: work)
    }
}

/// Toast 视图修饰器：底部浮出一条胶囊提示。
struct ToastOverlay: ViewModifier {
    @ObservedObject private var center = ToastCenter.shared

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if let message = center.message {
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
                    .background(.black.opacity(0.78), in: Capsule())
                    .padding(.bottom, 64)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
                    .accessibilityIdentifier("toast.message")
            }
        }
    }
}

extension View {
    /// 挂在根视图上，让全局 Toast 生效。
    func withToast() -> some View { modifier(ToastOverlay()) }
}
