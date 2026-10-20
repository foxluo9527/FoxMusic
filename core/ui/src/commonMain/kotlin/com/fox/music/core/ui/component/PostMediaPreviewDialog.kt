package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.PostMediaType

/**
 * 帖子媒体全屏预览（图片/视频/Live）。
 *
 * 视频与 Live 的「实际播放」是平台能力（Android = MediaPlayer/TextureView，
 * iOS 目前只展示封面），所以这两页拆 expect/actual；其余（Pager、顶栏、
 * 缩放容器）都是纯 Compose，两端共用同一份实现。
 */
@Composable
expect fun PlatformVideoPreviewPage(
    media: PostMedia,
    playing: Boolean,
    muted: Boolean,
    onPlaybackEnded: () -> Unit,
    onPlaybackFailed: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier,
)

@Composable
expect fun PlatformLivePreviewPage(
    media: PostMedia,
    playing: Boolean,
    onPlaybackFinished: () -> Unit,
    onPlaybackFailed: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier,
)

internal fun resolvePreviewImageUrl(url: String): String = MediaUrlResolver.resolve(url) ?: url

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostMediaPreviewDialog(
    media: List<PostMedia>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (media.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, media.lastIndex),
        pageCount = { media.size },
    )
    var pagerScrollEnabled by remember { mutableStateOf(true) }
    val currentItem = media[pagerState.currentPage]

    var livePlaying by remember { mutableStateOf(false) }
    var liveShowSlash by remember { mutableStateOf(true) }
    var videoPlaying by remember { mutableStateOf(false) }
    var videoMuted by remember { mutableStateOf(false) }
    val audioCoordinator = LocalTransientMediaAudioCoordinator.current

    DisposableEffect(Unit) {
        onDispose {
            audioCoordinator?.onTransientMediaPlaybackEnded()
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        pagerScrollEnabled = true
        val item = media[pagerState.currentPage]
        when (item.type) {
            PostMediaType.LIVE -> {
                val canPlay = !item.playbackUrl.isNullOrBlank()
                livePlaying = canPlay
                liveShowSlash = !canPlay
            }
            else -> {
                livePlaying = false
                liveShowSlash = true
            }
        }
        videoPlaying = false
        videoMuted = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${pagerState.currentPage + 1}/${media.size}",
                            color = Color.White,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
                        }
                    },
                    actions = {
                        when (currentItem.type) {
                            PostMediaType.LIVE -> {
                                PostLivePreviewBadge(
                                    showSlash = liveShowSlash || !livePlaying,
                                    onClick = {
                                        if (!livePlaying && !currentItem.playbackUrl.isNullOrBlank()) {
                                            livePlaying = true
                                            liveShowSlash = false
                                        }
                                    },
                                )
                            }
                            PostMediaType.VIDEO -> {
                                VideoPreviewTopBarActions(
                                    playing = videoPlaying,
                                    muted = videoMuted,
                                    videoAvailable = !currentItem.playbackUrl.isNullOrBlank(),
                                    onTogglePlay = { videoPlaying = !videoPlaying },
                                    onToggleMute = { videoMuted = !videoMuted },
                                )
                            }
                            else -> Unit
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                        scrolledContainerColor = Color.Black,
                    ),
                    modifier = Modifier.statusBarsPadding(),
                )
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = pagerScrollEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                ) { page ->
                    val item = media[page]
                    val isCurrentPage = page == pagerState.currentPage
                    when (item.type) {
                        PostMediaType.IMAGE -> {
                            ZoomablePreviewImage(
                                imageUrl = resolvePreviewImageUrl(item.url),
                                contentDescription = "帖子图片",
                                onZoomedChange = { zoomed -> pagerScrollEnabled = !zoomed },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        PostMediaType.VIDEO -> {
                            PlatformVideoPreviewPage(
                                media = item,
                                playing = videoPlaying && isCurrentPage,
                                muted = videoMuted,
                                onPlaybackEnded = { videoPlaying = false },
                                onPlaybackFailed = { videoPlaying = false },
                                onZoomedChange = { zoomed -> pagerScrollEnabled = !zoomed },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        PostMediaType.LIVE -> {
                            PlatformLivePreviewPage(
                                media = item,
                                playing = livePlaying && isCurrentPage,
                                onPlaybackFinished = {
                                    livePlaying = false
                                    liveShowSlash = true
                                },
                                onPlaybackFailed = {
                                    livePlaying = false
                                    liveShowSlash = true
                                },
                                onZoomedChange = { zoomed -> pagerScrollEnabled = !zoomed },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewTopBarActions(
    playing: Boolean,
    muted: Boolean,
    videoAvailable: Boolean,
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
) {
    if (!videoAvailable) return
    IconButton(onClick = onTogglePlay) {
        Icon(
            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (playing) "暂停" else "播放",
            tint = Color.White,
        )
    }
    IconButton(onClick = onToggleMute) {
        Icon(
            imageVector = if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            contentDescription = if (muted) "取消静音" else "静音",
            tint = Color.White,
        )
    }
}

/** 兜底页：平台无法播放时至少给出一块黑底，避免空白。 */
@Composable
internal fun PreviewFallbackPage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.Black))
}
