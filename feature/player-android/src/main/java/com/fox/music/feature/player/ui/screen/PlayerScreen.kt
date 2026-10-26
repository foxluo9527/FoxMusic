package com.fox.music.feature.player.ui.screen

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.blankj.utilcode.util.ToastUtils
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.component.BlurBgImage
import com.fox.music.feature.player.lyric.manager.LyricStyleManager
import com.fox.music.feature.player.ui.component.CustomLyricSelectorSheet
import com.fox.music.feature.player.ui.component.LyricPage
import com.fox.music.feature.player.ui.component.LyricSettingsSheet
import com.fox.music.feature.player.ui.component.MusicCommentBottomSheet
import com.fox.music.feature.player.ui.component.PlaylistBottomSheet
import com.fox.music.feature.player.ui.component.SongPage
import com.fox.music.feature.player.viewmodel.CommentBubbleIntent
import com.fox.music.feature.player.viewmodel.MusicCommentBubbleViewModel
import com.fox.music.feature.player.viewmodel.PlayerEffect
import com.fox.music.feature.player.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

const val PLAYER_ROUTE = "player"

/**
 * 播放页。
 *
 * 2026-10 改版（原型 03 播放页 / 04 歌词面板）：
 * - 背景走「封面模糊图 + 主题色半透明渐变」：底色与顶光都取当前主题的
 *   background / primary，不再固定纸色，换主题时播放页与应用其它页面一致。
 * - 顶栏从「← 返回 + 两点 Tab 切换 + 队列」换成「⌄ 收起 + 来源胶囊 + 词」：
 *   胶囊本身就是"我在听哪个列表、第几首"，点它开播放列表，信息与入口合一；
 *   原来那颗小小的两点 Tab 切换器既不可点也说不清是什么。
 * - 歌词页的样式色做主题兜底：深色主题下默认配色会切成 onBackground。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    musicController: MusicController,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onBack: () -> Unit = {},
    onPlaylistSourceClick: (PlaybackPlaylistSource) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
    bubbleViewModel: MusicCommentBubbleViewModel = hiltViewModel(),
) {
    val playerState by musicController.playerState.collectAsState(PlayerState())
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val playerBase = scheme.background
    val ink = scheme.onBackground
    // 深色主题下背景与墨色互换，状态栏、歌词与控件都要跟着走。
    val darkPlayer = playerBase.luminance() < 0.5f

    // 歌词页评论冒泡：跟着当前曲目换池子（同一首不重复请求）
    val bubbleState by bubbleViewModel.uiState.collectAsState()
    val currentMusicId = playerState.currentMusic?.id
    LaunchedEffect(currentMusicId) {
        currentMusicId?.let { bubbleViewModel.sendIntent(CommentBubbleIntent.Load(it)) }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PlayerEffect.ShowToast -> ToastUtils.make().show(effect.message)
            }
        }
    }
    // 歌词样式：字号 / 翻译开关沿用用户设置，三个颜色通道换成当前配色方案的解析结果
    // （旧实现拿的是「白字 + 半透明黑底」的遗留值，铺到纸感浅底上根本看不见）。
    val styleManager = remember { LyricStyleManager.getInstance() }
    val currentStyle by styleManager.styleFlow.collectAsState()
    val paletteTick by styleManager.paletteTick.collectAsState()
    val themeAccentArgb = scheme.primary.toArgb()
    val inkArgb = ink.toArgb()
    val lyricStyle = remember(currentStyle, paletteTick, themeAccentArgb, darkPlayer, inkArgb) {
        val resolved = styleManager.resolvedLyricStyle(themeAccentArgb)
        if (darkPlayer) {
            // 精选配色都是按浅底设计的深色字，深色主题下统一换成主题墨色，
            // 高亮沿用 primary，避免歌词在深底上「消失」。
            resolved.copy(textColor = inkArgb, highlightColor = themeAccentArgb)
        } else {
            resolved
        }
    }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { 2 }
    var showLyricSettings by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showCommentSheet by remember { mutableStateOf(false) }
    var showCustomLyricSelector by remember { mutableStateOf(false) }

    // 从桌面歌词悬浮窗点「设置」进来：直接弹歌词设置 Sheet，无需切页。
    val desktopLyricSettingsPending by DesktopLyricSettingsRequest.pending.collectAsState()
    LaunchedEffect(desktopLyricSettingsPending) {
        if (!desktopLyricSettingsPending) return@LaunchedEffect
        showLyricSettings = true
        DesktopLyricSettingsRequest.consume()
    }

    // 获取当前 Window 以控制状态栏
    val view = LocalView.current
    val window = (view.context as? android.app.Activity)?.window

    // 背景是主题底色：浅色主题用深色状态栏图标，深色主题用浅色图标。
    SideEffect {
        window?.let {
            val insetsController = WindowCompat.getInsetsController(it, view)
            insetsController.isAppearanceLightStatusBars = !darkPlayer
        }
    }

    // 退出时恢复默认的状态栏样式（与主题保持一致）
    DisposableEffect(Unit) {
        onDispose {
            window?.let {
                val insetsController = WindowCompat.getInsetsController(it, view)
                insetsController.isAppearanceLightStatusBars = !darkPlayer
            }
        }
    }

    with(sharedTransitionScope) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(playerBase),
        ) {
            // ── 背景：封面模糊图 + 主题色渐变（顶端 primary 顶光，底部回到底色） ──
            Box(
                Modifier
                    .fillMaxSize()
                    .sharedBounds(
                        sharedTransitionScope.rememberSharedContentState(
                            key = "player-blur-bg-${playerState.currentMusic?.id}",
                        ),
                        animatedVisibilityScope = animatedContentScope,
                    ),
            ) {
                BlurBgImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.42f },
                    url = playerState.currentMusic?.coverImage,
                )
                Spacer(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    lerp(playerBase, scheme.primary, 0.14f).copy(alpha = 0.72f),
                                    playerBase.copy(alpha = 0.55f),
                                    playerBase.copy(alpha = 0.86f),
                                    playerBase.copy(alpha = 0.96f),
                                ),
                            ),
                        ),
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                PlayerTopBar(
                    playerState = playerState,
                    isLyricPage = pager.currentPage == 1,
                    onBack = onBack,
                    onSourceClick = { showPlaylistSheet = true },
                    onLyricClick = {
                        scope.launch { pager.animateScrollToPage(1) }
                    },
                    onLyricSettingsClick = { showLyricSettings = !showLyricSettings },
                )
                HorizontalPager(
                    pager,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                ) {
                    if (it == 0) {
                        SongPage(
                            Modifier.fillMaxSize(),
                            playerState,
                            musicController,
                            ink,
                            animatedContentScope,
                            onFavoriteClick = { id -> viewModel.toggleFavorite(id) },
                            onDownloadClick = { viewModel.downloadCurrentMusic() },
                            onClickLyric = {
                                scope.launch { pager.animateScrollToPage(1) }
                            },
                            onCommentClick = { showCommentSheet = true },
                            onShareClick = {
                                val music = playerState.currentMusic
                                if (music != null) {
                                    shareMusic(
                                        context = context,
                                        title = music.title,
                                        artists = music.artists.joinToString(" / ") { a -> a.name },
                                    )
                                }
                            },
                            onQueueClick = { showPlaylistSheet = true },
                        )
                    } else {
                        LyricPage(
                            Modifier.fillMaxSize(),
                            lyricStyle,
                            musicController,
                            playerBase,
                            ink,
                            comments = bubbleState.comments,
                            onCommentClick = { showCommentSheet = true },
                        )
                    }
                }
            }
        }

        // 播放列表底部工作表
        if (showPlaylistSheet) {
            PlaylistBottomSheet(
                playerState = playerState,
                musicController = musicController,
                onDismiss = { showPlaylistSheet = false },
                onSourceClick = { source ->
                    showPlaylistSheet = false
                    onPlaylistSourceClick(source)
                },
            )
        }

        // 评论底部弹窗
        if (showCommentSheet) {
            MusicCommentBottomSheet(
                musicId = playerState.currentMusic?.id ?: 0L,
                onDismiss = {
                    showCommentSheet = false
                },
            )
        }

        // 歌词设置：BottomSheet 化（旧版是嵌在歌词上方的 XML 面板，会挡住内容）
        if (showLyricSettings) {
            LyricSettingsSheet(
                onDismiss = { showLyricSettings = false },
                onLyricCorrectClick = {
                    showLyricSettings = false
                    showCustomLyricSelector = true
                },
            )
        }

        // 自定义歌词选择
        if (showCustomLyricSelector) {
            val music = playerState.currentMusic
            if (music != null) {
                val artistName = music.artists.joinToString(" / ") { it.name }
                CustomLyricSelectorSheet(
                    searchKeyword = music.title,
                    musicTitle = music.title,
                    artistName = artistName,
                    style = lyricStyle,
                    musicController = musicController,
                    onDismiss = { showCustomLyricSelector = false },
                )
            }
        }
    }
}

/**
 * 顶栏：⌄ 收起 / 来源胶囊 / 歌词（歌词页时是设置）。
 *
 * 三个元素都用 34dp 半透明白圆底，浮在模糊背景上不会糊成一片；
 * 胶囊承担"我在听什么列表、第几首"这个信息，点一下开播放列表。
 */
@Composable
private fun PlayerTopBar(
    playerState: PlayerState,
    isLyricPage: Boolean,
    onBack: () -> Unit,
    onSourceClick: () -> Unit,
    onLyricClick: () -> Unit,
    onLyricSettingsClick: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircleGlassButton(
            modifier = Modifier.align(Alignment.CenterStart),
            onClick = onBack,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "收起",
                modifier = Modifier.size(19.dp),
                tint = ink,
            )
        }

        SourceChip(
            playerState = playerState,
            onClick = onSourceClick,
        )

        CircleGlassButton(
            modifier = Modifier.align(Alignment.CenterEnd),
            onClick = { if (isLyricPage) onLyricSettingsClick() else onLyricClick() },
        ) {
            if (isLyricPage) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "歌词设置",
                    modifier = Modifier.size(17.dp),
                    tint = ink,
                )
            } else {
                Text(
                    text = "词",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = ink,
                )
            }
        }
    }
}

/** 来源胶囊：「热歌榜 | 12 / 200」。没有来源信息时退化成「正在播放」。 */
@Composable
private fun SourceChip(
    playerState: PlayerState,
    onClick: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val inkMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val glass = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    val source = playerState.playlistSource
    val label = source?.title?.takeIf { it.isNotBlank() }
        ?: source?.typeLabel
        ?: "正在播放"
    val index = playerState.currentIndex + 1
    val total = playerState.playlist.size
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(glass)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .height(26.dp)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 132.dp),
        )
        if (total > 0) {
            Spacer(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(width = 1.dp, height = 9.dp)
                    .background(ink.copy(alpha = 0.22f)),
            )
            Text(
                text = "$index / $total",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontFeatureSettings = "tnum",
                ),
                color = inkMuted,
                maxLines = 1,
            )
        }
        // 可点暗示：这颗胶囊是「播放列表」的唯一入口（原型 .pl-src 的 ›）
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(12.dp),
            tint = inkMuted,
        )
    }
}

/** 34dp 半透明主题色圆底按钮（浮在模糊背景上）。 */
@Composable
private fun CircleGlassButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 系统分享：曲名 + 艺人。不依赖后端，不起浏览器。 */
private fun shareMusic(context: android.content.Context, title: String, artists: String) {
    val text = if (artists.isBlank()) "我在 FoxMusic 听《$title》" else "我在 FoxMusic 听《$title》— $artists"
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(
        android.content.Intent.createChooser(intent, "分享到").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}
