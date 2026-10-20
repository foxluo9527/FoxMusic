package com.fox.music.core.common.media

import platform.UIKit.UIView

/**
 * iOS 视频/Live 图播放桥（Swift ↔ Kotlin）。
 *
 * 为什么播放器实现在 Swift 侧：K/N 2.3.21 预置平台库的 AVPlayer 绑定残缺
 * （play/pause/rate 等实例方法在 cinterop 阶段整体丢失，见 FoxIosBridge 注释），
 * Kotlin 侧无法驱动 AVPlayer。所以这里只定义接口：
 *   - Swift 侧实现 [FoxVideoPlayerFactory] 创建 AVPlayerLayer 承载视图，
 *     并在 App 启动时注册到 [FoxVideoPlayerBridge.factory]；
 *   - Compose（core:ui 的 PostVideoIos.kt）通过 UIKitView 互操作把视图嵌进
 *     帖子视频/Live 预览页，用 [FoxVideoPlayerHandle] 控制播放。
 *
 * Android 端对应的播放实现在 core:ui androidMain 的 PostVideoAndroid.kt。
 */
interface FoxVideoPlayerHandle {
    fun setPlaying(playing: Boolean)
    fun setMuted(muted: Boolean)
    fun setLooping(looping: Boolean)

    /** 停止播放并释放底层资源；视图从组合树移除后不允许再复用。 */
    fun releasePlayer()
}

interface FoxVideoPlayerFactory {
    /**
     * 创建一个承载视频画面的 UIView（内部为 AVPlayerLayer，videoGravity = resizeAspect）。
     *
     * @param url       完整可播地址（相对路径由调用方先经 MediaUrlResolver 补全）
     * @param autoPlay  创建后是否立即播放
     * @param muted     是否静音（Live 图/视频预览的静音开关）
     * @param looping   是否循环（Live 图一般 false，播完回调 onCompletion）
     */
    fun createPlayerView(
        url: String,
        autoPlay: Boolean,
        muted: Boolean,
        looping: Boolean,
        onFirstFrame: () -> Unit,
        onCompletion: () -> Unit,
        onError: (reason: String?) -> Unit,
    ): UIView
}

/** Swift 侧在 App 启动时把工厂注册进来；未注册时 Compose 侧退回封面展示。 */
object FoxVideoPlayerBridge {
    var factory: FoxVideoPlayerFactory? = null
}
