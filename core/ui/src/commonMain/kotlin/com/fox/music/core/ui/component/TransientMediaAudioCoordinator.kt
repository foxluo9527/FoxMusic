package com.fox.music.core.ui.component

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Live/短视频等临时占用音频时，协调主音乐播放器暂停与恢复。
 */
interface TransientMediaAudioCoordinator {
    fun onTransientMediaPlaybackStarted()
    fun onTransientMediaPlaybackEnded()
}

val LocalTransientMediaAudioCoordinator = staticCompositionLocalOf<TransientMediaAudioCoordinator?> { null }
