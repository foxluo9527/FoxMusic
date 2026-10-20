package com.fox.music.core.ui.component

import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import com.fox.music.core.common.R
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.common.util.extractVideoFrameBitmap
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.PostMediaType
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.runtime.mutableIntStateOf

@Composable
actual fun PostLiveBadge(modifier: Modifier) {
    androidx.compose.material3.Icon(
        painter = painterResource(R.drawable.ic_post_live_badge),
        contentDescription = "LIVE",
        modifier = modifier,
        tint = Color.Unspecified,
    )
}

@Composable
actual fun PostVideoCover(
    media: PostMedia,
    modifier: Modifier,
    contentScale: ContentScale,
    showPlayIcon: Boolean,
) {
    val context = LocalContext.current
    val imageCover = media.imageCoverUrl?.takeIf { it.isNotBlank() }
    val videoUrl = media.playbackUrl
    val resolvedVideo = remember(videoUrl) {
        videoUrl?.let { MediaUrlResolver.resolve(it) ?: it }
    }

    if (!imageCover.isNullOrBlank()) {
        Box(modifier = modifier) {
            CachedImage(
                imageUrl = imageCover,
                contentDescription = "视频封面",
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                contentScale = contentScale,
            )
            if (showPlayIcon) {
                PostVideoPlayIcon(modifier = Modifier.align(Alignment.Center))
            }
        }
        return
    }

    val frameBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, resolvedVideo) {
        value = resolvedVideo?.let { extractVideoFrameBitmap(context, it) }
    }

    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.08f)),
    ) {
        if (frameBitmap != null) {
            Image(
                bitmap = frameBitmap!!.asImageBitmap(),
                contentDescription = "视频封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
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
    val context = LocalContext.current

    var zoomResetKey by remember(media) { mutableIntStateOf(0) }
    LaunchedEffect(playing) {
        if (!playing) {
            zoomResetKey++
        }
    }

    if (videoUrl.isNullOrBlank()) {
        PreviewFallbackPage(modifier)
        return
    }

    if (playing) {
        PostInlineVideo(
            url = videoUrl,
            playing = true,
            looping = false,
            muted = muted,
            contentScale = ContentScale.Fit,
            modifier = modifier,
            onCompletion = onPlaybackEnded,
            onError = onPlaybackFailed,
        )
        return
    }

    val hasDedicatedCover = !media.imageCoverUrl.isNullOrBlank()
    val resolvedVideo = remember(videoUrl) {
        MediaUrlResolver.resolve(videoUrl) ?: videoUrl
    }
    val coverModel = remember(media, context) {
        if (hasDedicatedCover) {
            resolvePreviewImageUrl(media.imageCoverUrl!!)
        } else {
            ImageRequest.Builder(context)
                .data(resolvedVideo)
                .setParameter("coil#video_frame_millis", 0L)
                .crossfade(true)
                .build()
        }
    }
    val frameBitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = media,
        key2 = resolvedVideo,
    ) {
        if (!hasDedicatedCover) {
            value = extractVideoFrameBitmap(context, resolvedVideo)
        }
    }
    val previewModel: Any? = frameBitmap ?: coverModel

    ZoomablePreviewContainer(
        resetKey = zoomResetKey,
        enabled = true,
        onZoomedChange = onZoomedChange,
        modifier = modifier,
    ) { contentModifier ->
        SubcomposeAsyncImage(
            model = previewModel,
            contentDescription = "视频预览",
            modifier = contentModifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * Live 预览：默认自动有声播放；顶栏 Live 徽章控制重播，未播放时显示斜线。
 */
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

    Box(modifier = modifier) {
        if (!playing || videoUrl.isNullOrBlank()) {
            ZoomablePreviewImage(
                imageUrl = resolvePreviewImageUrl(stillUrl),
                contentDescription = "Live 图",
                onZoomedChange = onZoomedChange,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LiveStillWithMotion(
                stillUrl = stillUrl,
                videoUrl = videoUrl,
                playing = true,
                muted = false,
                looping = false,
                onPlaybackFailed = onPlaybackFailed,
                onPlaybackFinished = onPlaybackFinished,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
