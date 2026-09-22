package com.fox.music.core.ui.component

import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fox.music.core.common.R
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.PostMediaType

@Composable
fun PostLiveBadge(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_post_live_badge),
        contentDescription = "LIVE",
        modifier = modifier,
        tint = Color.Unspecified,
    )
}

/**
 * Live 预览顶栏徽章：未播放时显示斜线，播放时斜线淡出；点击可重新播放。
 */
@Composable
fun PostLivePreviewBadge(
    showSlash: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slashAlpha by animateFloatAsState(
        targetValue = if (showSlash) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "liveSlashAlpha",
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PostLiveBadge(modifier = Modifier.size(24.dp))
        if (slashAlpha > 0.01f) {
            Canvas(
                modifier = Modifier
                    .size(24.dp)
                    .alpha(slashAlpha),
            ) {
                val strokeWidth = (size.minDimension * 0.1f).coerceAtLeast(2f)
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.18f, size.height * 0.82f),
                    end = Offset(size.width * 0.82f, size.height * 0.18f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
fun PostMediaCarousel(
    media: List<PostMedia>,
    onMediaClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    /**
     * 固定容器宽高比。null 时按所有媒体中「同宽最高」的比例（min 宽/高），
     * 最多不超过 [maxHeight]；较矮的 item 在容器内竖向居中。
     */
    containerAspectRatio: Float? = null,
    /** 限制媒体区域最大高度；pager 始终铺满宽度，单图超高时高度顶满、宽度按比例缩小并居中 */
    maxHeight: Dp? = null,
    /** 探测到的宽高比下限（宽/高），默认允许较极端比例 */
    minAspectRatio: Float = 0.45f,
    /** 探测到的宽高比上限（宽/高） */
    maxAspectRatio: Float = 2.2f,
    /** 超出 [minAspectRatio]/[maxAspectRatio] 的容器内裁切展示（广场信息流） */
    cropOverflow: Boolean = false,
) {
    if (media.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { media.size })
    val mediaShape: Shape = if (cornerRadius > 0.dp) {
        RoundedCornerShape(cornerRadius)
    } else {
        RectangleShape
    }
    val (measuredAspect, aspectReady) = if (maxHeight != null) {
        rememberPostMediaListAspectRatioLoaded(
            mediaList = media,
            pickShortest = false,
            fallback = 1f,
            min = minAspectRatio,
            max = maxAspectRatio,
        )
    } else {
        rememberPostMediaListAspectRatioLoaded(
            mediaList = media,
            pickShortest = true,
            fallback = 1f,
            min = minAspectRatio,
            max = maxAspectRatio,
        )
    }
    val pagerAspect = containerAspectRatio ?: measuredAspect

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val safeAspect = pagerAspect.coerceAtLeast(0.01f)
        val heightAtFullWidth = maxWidth / safeAspect
        val outerHeight = when {
            maxHeight != null && (containerAspectRatio != null || aspectReady) -> {
                minOf(heightAtFullWidth, maxHeight)
            }
            maxHeight != null -> {
                minOf(240.dp, maxHeight)
            }
            containerAspectRatio != null || aspectReady -> {
                heightAtFullWidth
            }
            else -> {
                240.dp
            }
        }
        val containerWidth = maxWidth

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(outerHeight),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(outerHeight)
                    .clip(mediaShape),
            ) { page ->
                PostMediaCarouselPage(
                    item = media[page],
                    page = page,
                    containerWidth = containerWidth,
                    containerHeight = outerHeight,
                    onMediaClick = onMediaClick,
                    minAspectRatio = minAspectRatio,
                    maxAspectRatio = maxAspectRatio,
                    cropOverflow = cropOverflow,
                )
            }
        }

        if (media.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(media.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 7.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) Color(0xFFE53935)
                                else Color.White.copy(alpha = 0.7f),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PostMediaCarouselPage(
    item: PostMedia,
    page: Int,
    containerWidth: Dp,
    containerHeight: Dp,
    onMediaClick: (Int) -> Unit,
    minAspectRatio: Float = 0.45f,
    maxAspectRatio: Float = 2.2f,
    cropOverflow: Boolean = false,
) {
    val (itemAspect, aspectReady) = rememberPostMediaAspectRatioLoaded(
        media = item,
        fallback = if (item.type == PostMediaType.VIDEO) 3f / 4f else 1f,
        min = minAspectRatio,
        max = maxAspectRatio,
    )
    val contentScale = when {
        cropOverflow && aspectReady -> ContentScale.Crop
        aspectReady -> ContentScale.Fit
        else -> ContentScale.Crop
    }
    val mediaModifier = if (cropOverflow) {
        Modifier.fillMaxSize()
    } else {
        val safeAspect = itemAspect.coerceAtLeast(0.01f)
        val itemHeightAtContainerWidth = containerWidth / safeAspect
        val itemWidthAtContainerHeight = containerHeight * safeAspect
        when {
            !aspectReady -> Modifier.fillMaxSize()
            itemHeightAtContainerWidth > containerHeight -> {
                Modifier
                    .width(itemWidthAtContainerHeight)
                    .height(containerHeight)
            }
            else -> {
                Modifier
                    .fillMaxWidth()
                    .height(itemHeightAtContainerWidth)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onMediaClick(page) },
        contentAlignment = Alignment.Center,
    ) {
        when (item.type) {
            PostMediaType.IMAGE -> {
                CachedImage(
                    imageUrl = item.url,
                    contentDescription = "帖子图片",
                    modifier = mediaModifier,
                    shape = RectangleShape,
                    contentScale = contentScale,
                )
            }
            PostMediaType.VIDEO -> {
                PostVideoPage(
                    item = item,
                    onOpen = { onMediaClick(page) },
                    mediaModifier = mediaModifier,
                    contentScale = contentScale,
                )
            }
            PostMediaType.LIVE -> {
                Box(modifier = mediaModifier) {
                    CachedImage(
                        imageUrl = item.url,
                        contentDescription = "Live 图",
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape,
                        contentScale = contentScale,
                    )
                    PostLiveBadge(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PostVideoPage(
    item: PostMedia,
    onOpen: () -> Unit,
    mediaModifier: Modifier = Modifier.fillMaxSize(),
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = mediaModifier.clickable(onClick = onOpen),
    ) {
        PostVideoCover(
            media = item,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            showPlayIcon = true,
        )
    }
}

@Composable
fun LiveStillWithMotion(
    stillUrl: String,
    videoUrl: String?,
    playing: Boolean,
    muted: Boolean = true,
    looping: Boolean = false,
    onPlaybackFailed: () -> Unit = {},
    onPlaybackFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var videoReady by remember(videoUrl) { mutableStateOf(false) }
    var videoAspect by remember(videoUrl) { mutableFloatStateOf(0f) }
    val onFailed by rememberUpdatedState(onPlaybackFailed)
    val onFinished by rememberUpdatedState(onPlaybackFinished)

    LaunchedEffect(playing) {
        if (!playing) {
            videoReady = false
            videoAspect = 0f
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // 静帧按真实比例 Fit
        CachedImage(
            imageUrl = stillUrl,
            contentDescription = "Live 图",
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            contentScale = ContentScale.Fit,
        )
        if (playing && !videoUrl.isNullOrBlank()) {
            // 视频也按真实比例 Fit，不裁切到静帧框
            val videoModifier = if (videoAspect > 0f) {
                val frame = fitInside(maxWidth, maxHeight, videoAspect)
                Modifier
                    .size(frame.width, frame.height)
                    .alpha(if (videoReady) 1f else 0f)
            } else {
                Modifier
                    .fillMaxSize()
                    .alpha(0f)
            }
            PostInlineVideo(
                url = videoUrl,
                playing = playing,
                looping = looping,
                muted = muted,
                modifier = videoModifier,
                onVideoSize = { w, h ->
                    if (w > 0 && h > 0) {
                        videoAspect = w.toFloat() / h.toFloat()
                    }
                },
                onPlaying = { videoReady = true },
                onCompletion = {
                    videoReady = false
                    onFinished()
                },
                onError = {
                    videoReady = false
                    onFailed()
                },
            )
        }
    }
}

/** ContentScale.Fit：在容器内完整显示 */
private fun fitInside(maxWidth: Dp, maxHeight: Dp, aspect: Float): DpSize {
    val safe = aspect.coerceAtLeast(0.01f)
    return if (maxWidth / maxHeight > safe) {
        val height = maxHeight
        DpSize(width = height * safe, height = height)
    } else {
        val width = maxWidth
        DpSize(width = width, height = width / safe)
    }
}

private data class DpSize(val width: Dp, val height: Dp)

@Composable
internal fun PostInlineVideo(
    url: String,
    playing: Boolean = true,
    looping: Boolean,
    muted: Boolean,
    showPreviewFrame: Boolean = false,
    retainFrameOnEnd: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier,
    onVideoSize: ((width: Int, height: Int) -> Unit)? = null,
    onPlaying: (() -> Unit)? = null,
    onCompletion: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val resolved = remember(url) { MediaUrlResolver.resolve(url) ?: url }
    val onPlayingState by rememberUpdatedState(onPlaying)
    val onCompletionState by rememberUpdatedState(onCompletion)
    val onErrorState by rememberUpdatedState(onError)
    val onVideoSizeState by rememberUpdatedState(onVideoSize)
    val playingState by rememberUpdatedState(playing)
    val mutedState by rememberUpdatedState(muted)
    val loopingState by rememberUpdatedState(looping)
    val showPreviewFrameState by rememberUpdatedState(showPreviewFrame)
    val retainFrameOnEndState by rememberUpdatedState(retainFrameOnEnd)
    val contentScaleState by rememberUpdatedState(contentScale)
    val audioCoordinator = LocalTransientMediaAudioCoordinator.current
    var mediaPlayer by remember(resolved) { mutableStateOf<MediaPlayer?>(null) }
    var videoSurface by remember(resolved) { mutableStateOf<Surface?>(null) }
    var holdsTransientAudio by remember(resolved) { mutableStateOf(false) }
    var videoAspect by remember(resolved) { mutableFloatStateOf(0f) }

    fun notifyVideoSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            videoAspect = width.toFloat() / height.toFloat()
        }
        onVideoSizeState?.invoke(width, height)
    }

    fun applyVideoScalingMode(mp: MediaPlayer) {
        mp.setVideoScalingMode(
            if (contentScaleState == ContentScale.Fit || contentScaleState == ContentScale.Inside) {
                MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
            } else {
                MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            },
        )
    }

    fun releaseTransientAudio() {
        if (holdsTransientAudio) {
            holdsTransientAudio = false
            audioCoordinator?.onTransientMediaPlaybackEnded()
        }
    }

    fun wantsTransientAudio(): Boolean = playingState && !mutedState

    fun acquireTransientAudioIfNeeded() {
        if (wantsTransientAudio() && !holdsTransientAudio) {
            audioCoordinator?.onTransientMediaPlaybackStarted()
            holdsTransientAudio = true
        }
    }

    fun releasePlayer() {
        runCatching {
            mediaPlayer?.setSurface(null)
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    fun stopPlaybackAndReleaseAudio() {
        releasePlayer()
        releaseTransientAudio()
    }

    fun pauseAndReleaseAudio() {
        mediaPlayer?.pause()
        releaseTransientAudio()
    }

    fun showFrameAtStart() {
        val mp = mediaPlayer ?: return
        mp.setVolume(0f, 0f)
        mp.seekTo(0)
        if (!mp.isPlaying) {
            mp.start()
        }
        mp.pause()
    }

    fun pauseAtPreviewFrame() {
        showFrameAtStart()
        pauseAndReleaseAudio()
    }

    fun finishPlaybackAtPreviewFrame() {
        showFrameAtStart()
        releaseTransientAudio()
    }

    fun onPlayerPrepared(mp: MediaPlayer) {
        mediaPlayer = mp
        mp.isLooping = loopingState
        applyVideoScalingMode(mp)
        if (mp.videoWidth > 0 && mp.videoHeight > 0) {
            notifyVideoSize(mp.videoWidth, mp.videoHeight)
        }
        if (playingState) {
            val vol = if (mutedState) 0f else 1f
            mp.setVolume(vol, vol)
            mp.seekTo(0)
            acquireTransientAudioIfNeeded()
            mp.start()
            onPlayingState?.invoke()
        } else if (showPreviewFrameState) {
            mp.seekTo(0)
            mp.setVolume(0f, 0f)
            mp.start()
            mp.pause()
        } else {
            mp.seekTo(0)
        }
    }

    fun loadVideo(uri: String, surface: Surface?) {
        releasePlayer()
        val mp = MediaPlayer()
        runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            surface?.let { mp.setSurface(it) }
            mp.setOnPreparedListener { prepared -> onPlayerPrepared(prepared) }
            mp.setOnVideoSizeChangedListener { _, width, height ->
                notifyVideoSize(width, height)
            }
            mp.setOnErrorListener { _, _, _ ->
                stopPlaybackAndReleaseAudio()
                onErrorState?.invoke()
                true
            }
            mp.setOnCompletionListener {
                if (!loopingState) {
                    if (retainFrameOnEndState) {
                        finishPlaybackAtPreviewFrame()
                    } else {
                        stopPlaybackAndReleaseAudio()
                    }
                    onCompletionState?.invoke()
                }
            }
            mp.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    onPlayingState?.invoke()
                }
                false
            }
            mp.setDataSource(context, Uri.parse(uri))
            mp.prepareAsync()
        }.onFailure {
            mp.release()
            onErrorState?.invoke()
        }
    }

    LaunchedEffect(playingState, mutedState) {
        if (!playingState) {
            if (showPreviewFrameState) {
                pauseAtPreviewFrame()
            } else {
                pauseAndReleaseAudio()
            }
        } else if (mutedState) {
            releaseTransientAudio()
        }
    }

    @Composable
    fun InlineVideoSurface(viewModifier: Modifier) {
        AndroidView(
            modifier = viewModifier,
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            val surface = Surface(surfaceTexture)
                            videoSurface = surface
                            mediaPlayer?.setSurface(surface) ?: loadVideo(resolved, surface)
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            mediaPlayer?.setSurface(null)
                            videoSurface?.release()
                            videoSurface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                    }
                    tag = resolved
                }
            },
            update = { view ->
                if (view.tag != resolved) {
                    stopPlaybackAndReleaseAudio()
                    view.tag = resolved
                    loadVideo(resolved, videoSurface)
                    return@AndroidView
                }
                mediaPlayer?.isLooping = loopingState
                mediaPlayer?.let(::applyVideoScalingMode)

                if (!playingState) {
                    if (showPreviewFrameState) {
                        if (mediaPlayer != null) {
                            showFrameAtStart()
                        }
                    } else {
                        mediaPlayer?.pause()
                    }
                    releaseTransientAudio()
                } else {
                    mediaPlayer?.setVolume(
                        if (mutedState) 0f else 1f,
                        if (mutedState) 0f else 1f,
                    )
                    if (mutedState) {
                        releaseTransientAudio()
                    } else {
                        acquireTransientAudioIfNeeded()
                    }
                    if (mediaPlayer != null && mediaPlayer?.isPlaying == false) {
                        mediaPlayer?.start()
                    }
                }
            },
        )
    }

    val useFitLayout = contentScale == ContentScale.Fit || contentScale == ContentScale.Inside
    if (useFitLayout) {
        BoxWithConstraints(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            val viewModifier = if (videoAspect > 0f) {
                val frame = fitInside(maxWidth, maxHeight, videoAspect)
                Modifier.size(frame.width, frame.height)
            } else {
                Modifier.fillMaxSize()
            }
            InlineVideoSurface(viewModifier)
        }
    } else {
        InlineVideoSurface(modifier)
    }

    DisposableEffect(resolved) {
        onDispose {
            stopPlaybackAndReleaseAudio()
            videoSurface = null
        }
    }
}
