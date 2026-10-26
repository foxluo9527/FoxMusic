package com.fox.music.feature.player.ui.component

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.music.RepeatMode
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.PostImagePreviewDialog
import com.fox.music.core.ui.theme.CoverShapeHero
import com.fox.music.feature.player.R

/**
 * 播放页「封面页」。
 *
 * 2026-10 改版（原型 03 播放页）：把原来"零件摆好了但没装"的竖向堆叠重排成有主次的四段：
 * 1. **歌名先于封面**（25sp / 600）——先告诉你在听什么，再给你看画面。
 * 2. **封面 300dp / 20dp 圆角 + 落地投影**，是全屏唯一的"重物"，周围留白给它。
 * 3. **歌词当前行 16.5 / 500、下一句 13 / 34%**，第三行是所属专辑信息（10.5 / 38% 弱化）。
 * 4. **控制与内容操作分家**：主控 5 键（循环/上一首/播放 64/下一首/队列）一行，
 *    收藏/评论/分享另一行。原来 5 个键同权重 SpaceEvenly，播放键完全不突出，
 *    收藏混在传输控制里，语义是乱的。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SharedTransitionScope.SongPage(
    modifier: Modifier,
    playerState: PlayerState,
    musicController: MusicController,
    contrastColor: Color,
    animatedContentScope: AnimatedContentScope,
    onFavoriteClick: (Long?) -> Unit = {},
    onClickLyric: () -> Unit = {},
    onCommentClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onQueueClick: () -> Unit = {},
) {
    var previewCoverUrl by remember { mutableStateOf<String?>(null) }

    previewCoverUrl?.let { url ->
        PostImagePreviewDialog(
            images = listOf(url),
            initialIndex = 0,
            onDismiss = { previewCoverUrl = null },
        )
    }

    val ink = contrastColor
    val inkMuted = contrastColor.copy(alpha = 0.62f)
    val inkFaint = contrastColor.copy(alpha = 0.38f)
    // 播放键是「实心墨色圆」：图标取页面底色，深浅主题都能保证对比。
    val onInk = MaterialTheme.colorScheme.background

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        playerState.currentMusic?.let { music ->
            // ── 1. 歌名 / 艺人 ────────────────────────────────────────────
            Column(
                modifier = Modifier.sharedElement(
                    rememberSharedContentState(key = "music-title-${music.id}"),
                    animatedVisibilityScope = animatedContentScope,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = music.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 25.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                    ),
                    maxLines = 1,
                    color = ink,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = music.artists.joinToString(" / ") { it.name }
                        .ifBlank { "未知艺人" },
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                    color = inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── 2. 封面（弹性区，全屏唯一重物） ─────────────────────────────
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 14.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                // 边长取「可用宽度的 88%」与「可用高度」的较小者：
                // 只看宽度会让封面在矮屏上撑出弹性区，压到歌名和歌词上。
                val side = minOf(maxWidth * 0.88f, maxHeight)
                // 阴影随进出场动画 0→22dp：hero 过渡期间封面还在飞，
                // 满强度阴影会先一步落在终点上，看起来像一块空白和封面没对上。
                val coverShadowDp by animatedContentScope.transition.animateFloat(
                    transitionSpec = { tween(220) },
                    label = "coverShadow",
                ) { state ->
                    if (state == EnterExitState.Visible) 22f else 0f
                }
                CachedImage(
                    imageUrl = music.coverImage,
                    contentDescription = music.title,
                    modifier = Modifier
                        .size(side)
                        .shadow(
                            elevation = coverShadowDp.dp,
                            shape = CoverShapeHero,
                            ambientColor = Color(0x66C8845A),
                            spotColor = Color(0x88B06040),
                        )
                        .sharedElement(
                            rememberSharedContentState(key = "music-image-${music.id}"),
                            animatedVisibilityScope = animatedContentScope,
                        )
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = {
                                music.coverImage.takeIf { it?.isNotBlank() == true }?.let {
                                    previewCoverUrl = it
                                }
                            },
                        ),
                    shape = CoverShapeHero,
                    // 纸感渐变上的大图：占位必须透明。默认 surfaceVariant 实心块在
                    // 封面加载间隙（进场转场/弱网）会露出「白色块」（用户反馈）。
                    placeholderColor = Color.Transparent,
                )
            }

            // ── 3. 歌词当前行 / 下一句 / 专辑信息 ───────────────────────────
            Column(
                modifier = Modifier
                    .clickable { onClickLyric() }
                    .sharedElement(
                        rememberSharedContentState(key = "music-lyric-${music.id}"),
                        animatedVisibilityScope = animatedContentScope,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                music.getCurrentLyric(playerState.position)?.let {
                    Text(
                        text = it,
                        maxLines = 1,
                        fontWeight = FontWeight(500),
                        fontSize = 16.5.sp,
                        overflow = TextOverflow.Ellipsis,
                        color = ink,
                    )
                }
                music.getNextLyric(playerState.position)?.let {
                    Text(
                        text = it,
                        color = contrastColor.copy(alpha = 0.34f),
                        fontWeight = FontWeight(400),
                        maxLines = 1,
                        fontSize = 13.sp,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }

            // 第三行：所属专辑 / 曲风 —— 弱到只看轮廓，但不能是空的（空位最显廉价）
            val credit = buildString {
                val album = music.album?.title?.takeIf { it.isNotBlank() }
                if (album != null) append("专辑 $album")
                val genre = music.genre?.takeIf { it.isNotBlank() }
                if (genre != null) {
                    if (isNotEmpty()) append("  ·  ")
                    append(genre)
                }
            }
            if (credit.isNotEmpty()) {
                Text(
                    text = credit,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        letterSpacing = 0.4.sp,
                    ),
                    color = inkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── 4. 进度（时间标签在轨道下方两端） ──────────────────────────
            SongProgress(
                modifier = Modifier.fillMaxWidth(),
                playerState = playerState,
                musicController = musicController,
                contrastColor = ink,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // ── 5. 主控 5 键 ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    musicController.setRepeatMode(nextRepeatMode(playerState.repeatMode))
                }) {
                    Icon(
                        painter = painterResource(
                            when (playerState.repeatMode) {
                                RepeatMode.SEQUENTIAL -> R.drawable.ic_sequential
                                RepeatMode.ALL -> R.drawable.ic_cycle
                                RepeatMode.ONE -> R.drawable.ic_single
                                RepeatMode.RANDOM -> R.drawable.ic_random
                            }
                        ),
                        contentDescription = "RepeatModel",
                        modifier = Modifier.size(22.dp),
                        tint = ink.copy(alpha = 0.78f),
                    )
                }
                IconButton(onClick = { musicController.previous() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_previous),
                        contentDescription = "Previous",
                        modifier = Modifier.size(28.dp),
                        tint = ink,
                    )
                }
                // 播放键：64dp 实心圆，是全屏唯一的"实心块" —— 视觉权重即功能权重
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(ink)
                        .sharedElement(
                            rememberSharedContentState(
                                key = "music-toggle-${playerState.currentMusic?.id}",
                            ),
                            animatedVisibilityScope = animatedContentScope,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { musicController.togglePlay() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (playerState.isLoading) {
                        CircularProgressIndicator(
                            color = onInk,
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                if (playerState.isPlaying) {
                                    com.fox.music.core.common.R.drawable.iv_pause
                                } else {
                                    com.fox.music.core.common.R.drawable.iv_play
                                }
                            ),
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(26.dp),
                            tint = onInk,
                        )
                    }
                }
                IconButton(
                    onClick = { musicController.next() },
                    modifier = Modifier.sharedElement(
                        rememberSharedContentState(
                            key = "music-next-${playerState.currentMusic?.id}",
                        ),
                        animatedVisibilityScope = animatedContentScope,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_next),
                        contentDescription = "Next",
                        modifier = Modifier.size(28.dp),
                        tint = ink,
                    )
                }
                IconButton(onClick = onQueueClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "播放列表",
                        modifier = Modifier.size(22.dp),
                        tint = ink.copy(alpha = 0.78f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── 6. 内容操作：收藏 / 评论 / 下载 / 分享 ────────────────────────
            // 原型 .pl-acts：4 项 space-between 铺在 20dp 内距里，图标 19 / 标签 9.5sp。
            // 3 项挤中间会读成"另一组按钮"，补上下载（与列表操作面板同仓库）后语义也齐了。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerAction(
                    icon = {
                        Icon(
                            imageVector = if (playerState.isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Filled.FavoriteBorder
                            },
                            contentDescription = if (playerState.isFavorite) {
                                "取消收藏"
                            } else {
                                "收藏"
                            },
                            modifier = Modifier
                                .size(19.dp)
                                .sharedElement(
                                    rememberSharedContentState(
                                        key = "music-like-${playerState.currentMusic?.id}",
                                    ),
                                    animatedVisibilityScope = animatedContentScope,
                                ),
                            tint = if (playerState.isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                ink.copy(alpha = 0.62f)
                            },
                        )
                    },
                    label = if (playerState.isFavorite) "已收藏" else "收藏",
                    tint = if (playerState.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        ink.copy(alpha = 0.5f)
                    },
                    onClick = { onFavoriteClick(playerState.currentMusic?.id) },
                )
                PlayerAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_chat),
                            contentDescription = "评论",
                            modifier = Modifier.size(19.dp),
                            tint = ink.copy(alpha = 0.62f),
                        )
                    },
                    label = if (music.commentCount > 0) "评论 ${music.commentCount}" else "评论",
                    tint = ink.copy(alpha = 0.45f),
                    onClick = onCommentClick,
                )
                PlayerAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = "下载",
                            modifier = Modifier.size(19.dp),
                            tint = ink.copy(alpha = 0.62f),
                        )
                    },
                    label = "下载",
                    tint = ink.copy(alpha = 0.45f),
                    onClick = onDownloadClick,
                )
                PlayerAction(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "分享",
                            modifier = Modifier.size(19.dp),
                            tint = ink.copy(alpha = 0.62f),
                        )
                    },
                    label = "分享",
                    tint = ink.copy(alpha = 0.45f),
                    onClick = onShareClick,
                )
            }
        }
        if (playerState.currentMusic == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无播放", color = inkMuted)
            }
        }
    }
}

/** 内容操作按钮：图标 + 10sp 标签，命中区 56x44。 */
@Composable
private fun PlayerAction(
    icon: @Composable () -> Unit,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = tint,
            maxLines = 1,
        )
    }
}

private fun nextRepeatMode(current: RepeatMode): RepeatMode = when (current) {
    RepeatMode.SEQUENTIAL -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.RANDOM
    RepeatMode.RANDOM -> RepeatMode.SEQUENTIAL
}
