package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.fox.music.core.common.media.FoxVideoPlayerBridge
import com.fox.music.core.common.media.FoxVideoPlayerHandle
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.model.social.PostMedia
import platform.UIKit.UIView

/**
 * iOS 侧 Live 徽章：不再用文字「LIVE」回退，而是用与 Android
 * drawable `ic_post_live_badge` 完全一致的矢量图标（[FoxLiveBadgeVector]），
 * 视觉上与 Android 对齐。
 */
@Composable
actual fun PostLiveBadge(modifier: Modifier) {
    Icon(
        imageVector = FoxLiveBadgeVector,
        contentDescription = "LIVE",
        tint = Color.Unspecified,
        modifier = modifier,
    )
}

/**
 * iOS 侧视频封面：K/N 的 AVFoundation 绑定残缺（AVPlayer 的 play/pause/rate 丢失），
 * 无法在 Kotlin 侧抽帧或播放，因此一律展示后端返回的封面图 + 播放角标。
 * 点击进入预览页后由 Swift 侧播放器（[FoxVideoPlayerSurface]）真正播放。
 */
@Composable
actual fun PostVideoCover(
    media: PostMedia,
    modifier: Modifier,
    contentScale: ContentScale,
    showPlayIcon: Boolean,
) {
    val cover = media.imageCoverUrl?.takeIf { it.isNotBlank() }
        ?: media.displayUrl.takeIf { it.isNotBlank() }
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            CachedImage(
                imageUrl = cover,
                contentDescription = "视频封面",
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                contentScale = contentScale,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        if (showPlayIcon) {
            PostVideoPlayIcon(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
actual fun PlatformVideoPreviewPage(
    media: PostMedia,
    playing: Boolean,
    muted: Boolean,
    onPlaybackEnded: () -> Unit,
    onPlaybackFailed: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val videoUrl = media.playbackUrl
    if (playing && !videoUrl.isNullOrBlank() && FoxVideoPlayerBridge.factory != null) {
        // 与 Android 一致：播放中交给原生播放器（resizeAspect 铺满 + 自动留黑边），不支持缩放
        FoxVideoPlayerSurface(
            url = videoUrl,
            playing = playing,
            muted = muted,
            looping = false,
            onCompletion = onPlaybackEnded,
            onError = onPlaybackFailed,
            modifier = modifier,
        )
        return
    }

    // 暂停/未播放：展示封面（可缩放）+ 播放角标，与 Android 的静止态一致
    val cover = media.imageCoverUrl?.takeIf { it.isNotBlank() }
        ?: media.displayUrl.takeIf { it.isNotBlank() }
    if (cover == null) {
        PreviewFallbackPage(modifier)
        return
    }
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        ZoomablePreviewImage(
            imageUrl = resolvePreviewImageUrl(cover),
            contentDescription = "视频预览",
            onZoomedChange = onZoomedChange,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "播放视频",
            tint = Color.White,
            modifier = Modifier.size(56.dp),
        )
    }
}

@Composable
actual fun PlatformLivePreviewPage(
    media: PostMedia,
    playing: Boolean,
    onPlaybackFinished: () -> Unit,
    onPlaybackFailed: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val stillUrl = media.url
    val videoUrl = media.playbackUrl
    val canPlay = playing && !videoUrl.isNullOrBlank() && FoxVideoPlayerBridge.factory != null

    Box(modifier = modifier) {
        if (!canPlay) {
            // 未播放/无法播放：静帧可缩放
            ZoomablePreviewImage(
                imageUrl = resolvePreviewImageUrl(stillUrl),
                contentDescription = "Live 图",
                onZoomedChange = onZoomedChange,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // 播放中：静帧垫底（Fit），视频首帧渲染后盖上来；
            // AVPlayerLayer resizeAspect 在同一容器内居中，视觉与 Android 的
            // 「静帧/视频各自 Fit 居中」一致。
            CachedImage(
                imageUrl = stillUrl,
                contentDescription = "Live 图",
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                contentScale = ContentScale.Fit,
            )
            FoxVideoPlayerSurface(
                url = videoUrl!!,
                playing = true,
                muted = false,
                looping = false,
                onCompletion = onPlaybackFinished,
                onError = onPlaybackFailed,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Swift 播放器的 Compose 包装：经 [FoxVideoPlayerBridge] 工厂创建 AVPlayerLayer 视图，
 * UIKitView 互操作嵌入 Compose。回调经 rememberUpdatedState 转发，避免工厂闭包捕获旧实例；
 * 视图关闭用户交互（isUserInteractionEnabled = false），触摸事件仍归 Compose（翻页/缩放手势）。
 */
@Composable
internal fun FoxVideoPlayerSurface(
    url: String,
    playing: Boolean,
    muted: Boolean,
    looping: Boolean,
    onCompletion: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = FoxVideoPlayerBridge.factory ?: return
    val resolved = remember(url) { MediaUrlResolver.resolve(url) ?: url }
    val currentPlaying by rememberUpdatedState(playing)
    val currentMuted by rememberUpdatedState(muted)
    val currentLooping by rememberUpdatedState(looping)
    val currentCompletion by rememberUpdatedState(onCompletion)
    val currentError by rememberUpdatedState(onError)

    UIKitView(
        factory = {
            val view = factory.createPlayerView(
                url = resolved,
                autoPlay = currentPlaying,
                muted = currentMuted,
                looping = currentLooping,
                onFirstFrame = {},
                onCompletion = { currentCompletion() },
                onError = { currentError() },
            )
            view.userInteractionEnabled = false
            view
        },
        modifier = modifier,
        update = { view: UIView ->
            (view as? FoxVideoPlayerHandle)?.let { handle ->
                handle.setPlaying(currentPlaying)
                handle.setMuted(currentMuted)
                handle.setLooping(currentLooping)
            }
        },
        onRelease = { view: UIView ->
            (view as? FoxVideoPlayerHandle)?.releasePlayer()
        },
    )
}
