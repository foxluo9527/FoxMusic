package com.fox.music.feature.stream

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.fox.music.core.ui.component.FoxSheet
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.core.ui.theme.InkGlass
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicVideo
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TempleBuddhist
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fox.music.core.model.music.ClipRange
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.widget.Toast

/**
 * 音流模式入口屏幕：汽水音乐卡片流，上下滑刷歌。
 *
 * 布局口径（卡片式，非沉浸全屏）：
 * - 页面底色为「封面主色 → 黑」的纵向渐变，随当前页 300ms 平滑过渡；
 * - 顶栏无底色，内容铺到状态栏下方：左三横（打开听歌场景侧边栏）+ 歌名（不加粗）；
 * - 封面为 16dp 圆角卡片，宽 = 屏宽 − 40dp（左右 20dp 边距），不再是满屏出血；
 * - 封面下方依次为歌词（3 行居中）、歌名/歌手 + 关注、收藏/评论/分享/更多操作行、整曲进度条；
 * - 听歌模式为自左滑入的侧边栏（覆盖约 86% 屏宽），播放控制只在底部 Tab 栏中央圆钮；
 * - 底部让开浮岛 Tab 栏（本页已关 MiniPlayer）。
 */
@Composable
fun StreamRoute(
    modifier: Modifier = Modifier,
    viewModel: StreamViewModel = hiltViewModel(),
    /** 打开「听歌模式」侧边栏：抽屉由外层 Tab 宿主渲染，才能盖在底部 Tab 栏之上。 */
    onOpenScenePanel: () -> Unit = {},
    /** App 内歌曲分享（走全局音乐操作 → 好友选择流程）。 */
    onShareMusic: (Music) -> Unit = {},
    /** 管理员「音源纠错」：跳转纠错页（传音乐 id）。 */
    onNavigateToSourceCorrect: (Long) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val position by viewModel.currentPosition.collectAsStateWithLifecycle()
    val followedArtists by viewModel.followedArtists.collectAsStateWithLifecycle()
    val mood by viewModel.mood.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    // 上次听到的卡片索引：回到音流页时按它续播（记忆收听位置）
    val anchorIndex by viewModel.anchorIndex.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 收藏/操作结果以系统 Toast 反馈（与其它页面的 effect → Toast 惯例一致）
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is StreamUiEffect.Toast ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when (val s = uiState) {
            is StreamUiState.Loading -> FullScreenHint("正在为你加载音流…")
            is StreamUiState.Error -> FullScreenHint(s.message) {
                viewModel.sendIntent(StreamUiIntent.Load(null))
            }
            is StreamUiState.Ready -> StreamContent(
                items = s.items,
                playerState = playerState,
                position = position,
                followedArtists = followedArtists,
                sceneTitle = streamSceneTitle(mood),
                isLoadingMore = s.isLoadingMore,
                canLoadMore = s.canLoadMore,
                loadMoreError = s.loadMoreError,
                onLoadMore = { viewModel.sendIntent(StreamUiIntent.LoadMore) },
                onSeekTo = { viewModel.sendIntent(StreamUiIntent.SeekTo(it)) },
                onSeekFree = { viewModel.sendIntent(StreamUiIntent.SeekFree(it)) },
                onToggleFavorite = { id, favorite ->
                    viewModel.sendIntent(StreamUiIntent.ToggleFavorite(id, favorite))
                },
                onToggleArtistFavorite = { artist ->
                    viewModel.sendIntent(StreamUiIntent.ToggleArtistFavorite(artist))
                },
                onEnsureQueue = { index ->
                    viewModel.sendIntent(StreamUiIntent.EnsureQueue(index))
                },
                fallbackInitialPage = anchorIndex,
                onOpenScenePanel = onOpenScenePanel,
                onShareMusic = onShareMusic,
                onDownload = { viewModel.downloadMusic(it) },
                onReport = { viewModel.reportMusic(it) },
                isAdmin = isAdmin,
                onFeedbackMismatch = { music, content ->
                    viewModel.submitFeedback(music, content)
                },
                onCorrectSource = { music ->
                    onNavigateToSourceCorrect(music.id)
                },
                onMoreSheetOpened = { viewModel.ensureAdminStatus() },
            )
        }
    }
}

@Composable
private fun StreamContent(
    items: List<StreamItemUi>,
    playerState: PlayerState,
    position: Long,
    /** 「关注」已成功的歌手名（音流原始名），用于回显已关注态 */
    followedArtists: Set<String>,
    /** 顶栏标题：当前听歌模式选择（不再展示歌曲名，歌名在封面下方已有展示） */
    sceneTitle: String,
    /** 分页：正在加载下一批 / 还能继续加载 / 上一批失败的文案 */
    isLoadingMore: Boolean = false,
    canLoadMore: Boolean = true,
    loadMoreError: String? = null,
    /** 分页：上滑接近队尾时追加下一批（幂等，内部防重入） */
    onLoadMore: () -> Unit = {},
    onSeekTo: (Int) -> Unit,
    onSeekFree: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    /** 「关注」= 收藏歌手，参数为歌手名 */
    onToggleArtistFavorite: (String?) -> Unit,
    /** 当前播放队列已不是本页音流队列时，按该页码重新挂载音流队列 */
    onEnsureQueue: (Int) -> Unit,
    /**
     * 队列不是音流队列时的起始页码：**上次听到的卡片索引**（记忆收听位置）。
     * 不传则回到第 0 张，用户会被拉回这批歌的开头。
     */
    fallbackInitialPage: Int = 0,
    onOpenScenePanel: () -> Unit,
    onShareMusic: (Music) -> Unit,
    onDownload: (Music) -> Unit = {},
    onReport: (Music) -> Unit = {},
    isAdmin: Boolean = false,
    onFeedbackMismatch: (Music, String?) -> Unit = { _, _ -> },
    onCorrectSource: (Music) -> Unit = {},
    onMoreSheetOpened: () -> Unit = {},
) {
    // 当前播放队列是否就是本页音流队列（按 id 序列比对）。
    // 在其它 Tab（如搜索页）点播过歌曲后队列会被替换，此时本页必须重新挂载音流队列，
    // 否则会出现「卡片显示音流页、实际播放别的歌、划动也切不动」。
    // 正在播放音流时为真 —— 此时回到音流页不应重新加载，且 pager 应直接定位到正在播放的那一页。
    val onStreamQueue = playerState.playlist.size == items.size &&
        items.withIndex().all { (i, item) -> item.music.id == playerState.playlist[i].id }

    // 回到音流页：若正在播放音流，pager 直接定位到当前正在播放的卡片，避免从头开始或误跳歌；
    // 否则从第 0 页开始（后续由 ensureQueue 决定是否重新挂载队列）。
    val pagerState = rememberPagerState(
        initialPage = if (onStreamQueue) {
            playerState.currentIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        } else {
            // 队列被别的页面顶掉了：从「上次听到的那一张」接上，而不是退回第 0 张重听
            fallbackInitialPage.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        },
    ) { items.size }
    var programmatic by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // 封面 URL → 主色；供当前页渐变与后两页预取共用
    val coverColors = remember { mutableStateMapOf<String, Color>() }
    // 收藏：接口未回填时以本地状态兜底，保证图标即时切换
    val favoriteIds = remember { mutableStateMapOf<Long, Boolean>() }

    // 播放队列不是音流队列 → 以 pager 当前页为起点重新起播音流
    LaunchedEffect(onStreamQueue, items) {
        if (!onStreamQueue) onEnsureQueue(pagerState.currentPage)
    }
    // 播放器切歌（含片段自动 advance）→ 同步 pager（仅在音流队列播放时，避免被别的队列带走）
    LaunchedEffect(playerState.currentIndex, onStreamQueue) {
        if (!onStreamQueue) return@LaunchedEffect
        if (playerState.currentIndex != pagerState.currentPage) {
            programmatic = true
            pagerState.scrollToPage(playerState.currentIndex)
            programmatic = false
        }
    }
    // 用户滑动 → 跳到对应歌曲（挂载该歌首片段）；非音流队列时不发 seek，防止误切别的队列
    LaunchedEffect(pagerState.currentPage, onStreamQueue) {
        if (!onStreamQueue) return@LaunchedEffect
        if (!programmatic && pagerState.currentPage != playerState.currentIndex) {
            onSeekTo(pagerState.currentPage)
        }
    }
    // 上滑接近队尾 → 预拉下一批。提前 STREAM_LOAD_MORE_PREFETCH 张触发，
    // 等用户真的滑到最后一张时新卡片已经在队列里，不会出现「滑到底空一片」。
    // 幂等：VM 内部有 job + 状态位双重防重入，这里只负责在正确时机喊一嗓子。
    // 失败后不再自动重试（否则会无限打接口），只在用户点「点击重试」时再来一次
    LaunchedEffect(pagerState.currentPage, items.size, canLoadMore, isLoadingMore, loadMoreError) {
        if (!canLoadMore || isLoadingMore || loadMoreError != null) return@LaunchedEffect
        if (items.isEmpty()) return@LaunchedEffect
        if (pagerState.currentPage >= items.size - STREAM_LOAD_MORE_PREFETCH) onLoadMore()
    }
    // 预取：当前页补算主色，后两页预热封面图 + 计算主色，翻到该页时底色已就绪
    LaunchedEffect(pagerState.currentPage, items) {
        ensureCoverColor(context, items.getOrNull(pagerState.currentPage)?.music?.coverImage, coverColors)
        for (offset in 1..2) {
            val next = items.getOrNull(pagerState.currentPage + offset) ?: continue
            preloadStreamCover(context, next.music.coverImage, StreamCoverSizePx)
            ensureCoverColor(context, next.music.coverImage, coverColors)
        }
    }

    val currentItem = items.getOrNull(pagerState.currentPage)
    val fallbackBg = MaterialTheme.colorScheme.primary
    val targetBg = currentItem?.music?.coverImage?.let { coverColors[it] } ?: fallbackBg
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 300),
        label = "streamBg",
    )
    // 汽水式底色：整页保持封面主色，底部只做「同色相压暗」，而不是渐变到纯黑（否则整页发灰发暗）
    val bgBottom = remember(bgColor) { bgColor.scaleRgb(0.5f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgColor, bgBottom))),
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items.getOrNull(page) ?: return@VerticalPager
            StreamCard(
                item = item,
                playerState = playerState,
                position = position,
                isCurrent = page == playerState.currentIndex,
                isFavorite = favoriteIds[item.music.id] ?: item.music.isFavorite,
                isFollowed = item.artist != null && followedArtists.contains(item.artist),
                onSeekFree = onSeekFree,
                onFavoriteClick = { next ->
                    favoriteIds[item.music.id] = next
                    onToggleFavorite(item.music.id, next)
                },
                onFollowClick = { onToggleArtistFavorite(item.artist) },
                onShareMusic = { onShareMusic(item.music) },
                onDownload = onDownload,
                onReport = onReport,
                isAdmin = isAdmin,
                onFeedbackMismatch = onFeedbackMismatch,
                onCorrectSource = onCorrectSource,
                onMoreSheetOpened = onMoreSheetOpened,
            )
        }

        StreamTopBar(
            title = sceneTitle,
            onOpenPanel = onOpenScenePanel,
            modifier = Modifier.align(Alignment.TopStart),
        )

        // 分页状态：加载中在底部转圈；失败给一条可点重试的提示（不盖住卡片，不打断播放）
        StreamLoadMoreHint(
            isLoadingMore = isLoadingMore,
            canLoadMore = canLoadMore,
            loadMoreError = loadMoreError,
            onRetry = onLoadMore,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 让开底部浮岛 Tab 栏（与卡片底部操作区保持距离）
                .padding(bottom = 132.dp)
                .navigationBarsPadding(),
        )
    }
}

/**
 * 上滑加载更多的状态提示：
 * - 加载中：小号转圈（汽水式的「正在找更多歌」）；
 * - 加载失败：一行「加载失败，点击重试」，点它再拉一次；
 * - 已经没有更多：静默（不打扰），滑到最后一张就是最后一张。
 */
@Composable
private fun StreamLoadMoreHint(
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    loadMoreError: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isLoadingMore && (loadMoreError == null || !canLoadMore)) return
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isLoadingMore) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp),
            )
        } else if (loadMoreError != null) {
            Text(
                text = "加载失败，点击重试",
                color = Color.White,
                fontSize = 13.sp,
                style = TextStyle(shadow = LyricShadow),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x33000000))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/** 顶栏：左侧三横打开场景面板，右侧当前「听歌模式」标题（不加粗、单行省略），无底色块。 */
@Composable
private fun StreamTopBar(
    title: String,
    onOpenPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            // 三横视觉中心对齐其它 Tab 顶栏（40dp 按钮 + 10dp 起始内边距 = 中心 30dp）
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onOpenPanel,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x33000000)),
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "听歌场景",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = LyricShadow),
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamCard(
    item: StreamItemUi,
    playerState: PlayerState,
    position: Long,
    isCurrent: Boolean,
    isFavorite: Boolean,
    isFollowed: Boolean,
    isAdmin: Boolean = false,
    onSeekFree: (Long) -> Unit,
    onFavoriteClick: (Boolean) -> Unit,
    onFollowClick: () -> Unit,
    onShareMusic: () -> Unit,
    onDownload: (Music) -> Unit = {},
    onReport: (Music) -> Unit = {},
    /** 「反馈音乐不匹配」：content 为用户填写的问题说明（可为空） */
    onFeedbackMismatch: (Music, String?) -> Unit = { _, _ -> },
    /** 「音源纠错」（仅管理员）：跳转纠错页替换当前歌曲音源 */
    onCorrectSource: (Music) -> Unit = {},
    /** 「更多」菜单打开：用于按需拉取管理员身份 */
    onMoreSheetOpened: () -> Unit = {},
) {
    var showMoreSheet by remember { mutableStateOf(false) }
    var showFeedbackSheet by remember { mutableStateOf(false) }
    val clip = if (isCurrent) playerState.clip else null
    val duration = if (isCurrent) playerState.duration else 0L
    // 歌词来自播放器当前曲目（预取协程会把歌词写回当前曲目），非当前卡片不展示歌词。
    val lyricMusic: Music? = if (isCurrent) playerState.currentMusic ?: item.music else null
    val context = LocalContext.current
    // 进度条拖拽预览位置：拖动中把它当作播放位置 → 时间与歌词都跟着走；松手才真正 seek
    var previewMs by remember { mutableStateOf<Long?>(null) }
    val shownPosition = previewMs ?: position
    // 双击点赞：抖音式漂浮爱心
    val heartProgress = remember { Animatable(0f) }
    var heartOffset by remember { mutableStateOf<Offset?>(null) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        // 封面：宽 = 屏宽 − 40dp；高度上限屏高 42%，保证上半屏为主、下方留足信息区
        val coverSize = minOf(maxWidth - 40.dp, maxHeight * 0.42f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = StreamTopBarHeight,
                    bottom = StreamChromeBottom,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 双击封面 = 点赞收藏（抖音语义：只加不取消）。手势与漂浮爱心都限定在封面区内，
            // 避免吞掉下方歌曲信息区 / 操作行的点击。
            Box(
                modifier = Modifier
                    .size(coverSize)
                    .pointerInput(isFavorite) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                heartOffset = offset
                                if (!isFavorite) onFavoriteClick(true)
                                scope.launch {
                                    heartProgress.snapTo(0f)
                                    heartProgress.animateTo(1f, animationSpec = tween(durationMillis = 850))
                                }
                            },
                        )
                    },
            ) {
                CoverCard(item = item, clip = clip, size = coverSize)
                // 漂浮爱心绘制在封面之上，按封面内坐标定位
                heartOffset?.let { origin ->
                    FloatingHeart(progress = heartProgress.value, origin = origin)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 拖动进度条时歌词跟着预览位置跳行（汽水同款交互）
            LyricsBlock(
                music = lyricMusic,
                position = if (isCurrent) shownPosition else position,
            )

            Spacer(modifier = Modifier.weight(1f))

            StreamSongInfo(
                item = item,
                isFollowed = isFollowed,
                onFollowClick = onFollowClick,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 汽水音乐式操作行：收藏/评论/分享/更多（播放控制已交给底部 Tab 栏中央圆钮）
            StreamActionRow(
                isFavorite = isFavorite,
                likeCount = item.music.likeCount,
                commentCount = item.music.commentCount,
                onFavoriteClick = { onFavoriteClick(!isFavorite) },
                onCommentClick = { Toast.makeText(context, "评论功能开发中", Toast.LENGTH_SHORT).show() },
                onShareClick = onShareMusic,
                onMoreClick = {
                    onMoreSheetOpened()
                    showMoreSheet = true
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 时间行：始终展示「当前 / 总时长」；拖动中高亮并随拖动实时变化
            StreamTimeRow(
                positionMs = if (isCurrent) shownPosition else 0L,
                durationMs = duration,
                dragging = previewMs != null,
            )

            Spacer(modifier = Modifier.height(6.dp))

            ClipProgressBar(
                durationMs = duration,
                positionMs = if (isCurrent) position else 0L,
                clip = clip,
                enabled = isCurrent,
                onPreviewPositionChange = { previewMs = it },
                onSeek = onSeekFree,
            )
        }
    }

    if (showMoreSheet) {
        // 原来是裸 ModalBottomSheet（无 shape / 无 handle），现统一 22dp 顶圆角 + drag handle。
        // containerColor 显式保留沉浸式的 #1C1C1E —— 这个页面整屏是深色 EV 风格，不走纸面令牌。
        FoxSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color(0xFF1C1C1E),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                StreamSheetItem(
                    icon = Icons.Filled.FileDownload,
                    label = "下载",
                    onClick = {
                        showMoreSheet = false
                        onDownload(item.music)
                    },
                )
                StreamSheetItem(
                    icon = Icons.Filled.Flag,
                    label = "举报",
                    onClick = {
                        showMoreSheet = false
                        onReport(item.music)
                    },
                )
                StreamSheetItem(
                    icon = Icons.Filled.Warning,
                    label = "反馈音乐不匹配",
                    onClick = {
                        showMoreSheet = false
                        showFeedbackSheet = true
                    },
                )
                if (isAdmin) {
                    StreamSheetItem(
                        icon = Icons.Filled.Build,
                        label = "音源纠错",
                        onClick = {
                            showMoreSheet = false
                            onCorrectSource(item.music)
                        },
                    )
                }
            }
        }
    }

    if (showFeedbackSheet) {
        StreamFeedbackSheet(
            targetTitle = item.music.title,
            onDismiss = { showFeedbackSheet = false },
            onSubmit = { content ->
                showFeedbackSheet = false
                onFeedbackMismatch(item.music, content)
            },
        )
    }
}

/**
 * 音流页「反馈音乐不匹配」弹窗：深色容器与「更多」菜单保持一致
 * （音流页是沉浸式深色页，不能直接用 core:ui 的浅色主题弹窗）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamFeedbackSheet(
    targetTitle: String,
    onDismiss: () -> Unit,
    onSubmit: (content: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var content by remember { mutableStateOf("") }
    val hintColor = Color.White.copy(alpha = 0.6f)

    // 统一 22dp 顶圆角 + drag handle；containerColor 保留本页面沉浸式的 #1C1C1E
    FoxSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = "反馈音乐不匹配",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = targetTitle,
                color = hintColor,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "如果播放的内容与歌名/歌手不符，或音质异常，请告诉我们，我们会尽快核实修正。",
                color = hintColor,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it.take(2000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("问题说明（可选）") },
                placeholder = { Text("例如：播放的是另一首歌、歌词与音频不同步等", color = hintColor) },
                minLines = 2,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = hintColor,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = hintColor,
                    cursorColor = Color.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onSubmit(content.takeIf { it.isNotBlank() }) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("提交反馈")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** 底部弹窗里的单行操作项（下载 / 举报）。 */
@Composable
private fun StreamSheetItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp,
        )
    }
}

/** 进度时间行：`当前 / 总时长`，拖动中改用主色以提示「预览中」。 */
@Composable
private fun StreamTimeRow(
    positionMs: Long,
    durationMs: Long,
    dragging: Boolean,
) {
    // 固定占位高度，避免时间行出现/消失时进度条上下跳动；文字只在拖动预览时展示
    Box(
        modifier = Modifier.fillMaxWidth().height(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (dragging) {
            Text(
                text = "${formatStreamTime(positionMs)} / ${formatStreamTime(durationMs)}",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                style = TextStyle(shadow = LyricShadow),
            )
        }
    }
}

/** 毫秒 → `m:ss`。 */
private fun formatStreamTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/**
 * 抖音式漂浮爱心：以双击点为中心弹出放大 → 回弹 → 上浮淡出。
 * progress 由 0 → 1 驱动（见 [StreamCard] 的 Animatable）。
 */
@Composable
private fun FloatingHeart(progress: Float, origin: Offset) {
    if (progress <= 0f || progress >= 1f) return
    val density = LocalDensity.current
    val risePx = with(density) { 80.dp.toPx() }
    // 0→0.3 弹出到 1.25 倍；0.3→0.55 回弹到 1 倍；之后保持
    val scale = when {
        progress < 0.3f -> (progress / 0.3f) * 1.25f
        progress < 0.55f -> 1.25f - ((progress - 0.3f) / 0.25f) * 0.25f
        else -> 1f
    }
    // 0.7 之后淡出
    val alpha = if (progress < 0.7f) 1f else (1f - (progress - 0.7f) / 0.3f).coerceIn(0f, 1f)
    // 0.55 之后缓慢上浮
    val rise = if (progress < 0.55f) 0f else ((progress - 0.55f) / 0.45f) * risePx
    val iconSize = with(density) { HeartSize.toPx() }

    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = HeartColor,
        modifier = Modifier
            .offset {
                IntOffset(
                    (origin.x - iconSize / 2f).roundToInt(),
                    (origin.y - iconSize / 2f - rise).roundToInt(),
                )
            }
            .size(HeartSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
    )
}

/** 圆角封面卡片：轻微压暗 + 左下角片段标签药丸。 */
@Composable
private fun CoverCard(item: StreamItemUi, clip: ClipRange?, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = item.music.coverImage,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // 轻微压暗：让白字/片段标签在花哨封面图上始终读得清
        Box(modifier = Modifier.fillMaxSize().background(Color(0x1A000000)))
        clip?.label?.takeIf { it.isNotBlank() }?.let { label ->
            Surface(
                color = Color(0x99000000),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            ) {
                Text(
                    text = "♪ $label",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StreamSongInfo(
    item: StreamItemUi,
    isFollowed: Boolean,
    onFollowClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = item.music.title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = LyricShadow),
        )
        item.artist?.takeIf { it.isNotBlank() }?.let { artist ->
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = artist,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = LyricShadow),
                    modifier = Modifier.weight(1f),
                )
                // 未关注时展示「关注」药丸；歌手无 id 数据，故仅本地置为已关注
                if (!isFollowed) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onFollowClick),
                    ) {
                        Text(
                            text = "关注",
                            color = Color(0xFF111111),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        // 片段/限免类标签（模型暴露时才展示）
        val tags = item.music.tags.map { it.name }.filter { it.isNotBlank() } +
            listOfNotNull("限免".takeIf { item.music.isFeatured })
        if (tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.take(3).forEach { tag ->
                    Surface(
                        color = Color(0x33FFFFFF),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = tag,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 整曲进度条：整曲 0..duration 为底，高潮片段渲染为高亮带 + 两端刻度；可拖动 seek。
 * 拖到片段外由播放层放开限制，「只播高潮」转为整曲播放（见 MusicController.seekFree）。
 *
 * 绘制约定（两处曾经出过问题，别再改回去）：
 * 1) 所有「胶囊」都用**显式半圆弧 + 直边拼 Path**，不用 drawRoundRect 的圆角推算：
 *    曾出现同一帧里已播段右端是圆的、左端却是直角的（左端圆角丢失）；
 * 2) 片段两端节点画成**白色圆点**且最后画（在已播段之上）：既要保证播放头经过起点时
 *    节点不被白色已播段吃掉，又不能做成「等高竖条 + 描边」（那样是个带暗边的白方块）。
 */
@Composable
private fun ClipProgressBar(
    durationMs: Long,
    positionMs: Long,
    clip: ClipRange?,
    enabled: Boolean,
    /** 拖动中回调预览位置（供时间行/歌词跟随），松手回调 null */
    onPreviewPositionChange: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
) {
    var dragMs by remember { mutableStateOf<Long?>(null) }
    val safeDuration = durationMs.coerceAtLeast(0L)
    val shownMs = (dragMs ?: positionMs).coerceIn(0L, safeDuration)
    val fraction = if (safeDuration > 0L) shownMs.toFloat() / safeDuration else 0f
    val accent = MaterialTheme.colorScheme.primary
    val trackColor = Color.White.copy(alpha = 0.24f)
    val dragging = dragMs != null

    fun updateDrag(ms: Long) {
        dragMs = ms
        onPreviewPositionChange(ms)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 拖动中放大触控/视觉高度，靠近汽水的手感
            .height(if (dragging) 36.dp else 30.dp)
            .then(
                if (enabled && safeDuration > 0L) {
                    Modifier.pointerInput(safeDuration) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                updateDrag(offset.x.toSeekMs(size.width, safeDuration))
                            },
                            onDragEnd = {
                                // 松手才真正落地 seek（拖动中只做预览）
                                dragMs?.let(onSeek)
                                dragMs = null
                                onPreviewPositionChange(null)
                            },
                            onDragCancel = {
                                dragMs = null
                                onPreviewPositionChange(null)
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                updateDrag(change.position.x.toSeekMs(size.width, safeDuration))
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val trackH = 4.dp.toPx()
            val capR = trackH / 2f
            val w = size.width
            val top = cy - capR
            val bottom = cy + capR

            /** 胶囊路径：[fromX]..[toX]，两端必为半圆（宽度不足一个圆时半径自动收敛）。 */
            fun pillPath(fromX: Float, toX: Float): Path? {
                val left = fromX.coerceIn(0f, w)
                val right = toX.coerceIn(0f, w)
                val length = right - left
                if (length <= 0.5f) return null
                val r = minOf(capR, length / 2f)
                return Path().apply {
                    moveTo(left + r, top)
                    lineTo(right - r, top)
                    // 右端半圆：-90° 顺时针扫 180°
                    arcTo(Rect(right - 2 * r, top, right, bottom), -90f, 180f, false)
                    lineTo(left + r, bottom)
                    // 左端半圆：90° 顺时针扫 180°
                    arcTo(Rect(left, top, left + 2 * r, bottom), 90f, 180f, false)
                    close()
                }
            }

            fun pill(fromX: Float, toX: Float, color: Color) {
                pillPath(fromX, toX)?.let { drawPath(it, color) }
            }

            // ① 整曲轨道
            pill(0f, w, trackColor)

            val clipStartX = if (clip != null && safeDuration > 0L) {
                (clip.startMs.toFloat() / safeDuration).coerceIn(0f, 1f) * w
            } else {
                null
            }
            val clipEndX = if (clip != null && safeDuration > 0L) {
                ((clip.startMs + clip.durationMs).toFloat() / safeDuration).coerceIn(0f, 1f) * w
            } else {
                null
            }
            // ② 高潮片段高亮带（与轨道等高，不冒头）
            if (clipStartX != null && clipEndX != null && clipEndX > clipStartX) {
                pill(clipStartX, clipEndX, accent.copy(alpha = 0.35f))
            }

            // ③ 已播进度：拖动中改主色，明确「正在预览」
            pill(0f, fraction * w, if (dragging) accent else Color.White)

            // ④ 片段两端节点：**白色圆点**（略大于轨道直径，压在进度条之上）。
            // 不要再画成「等高白色竖条 + 深色描边」——4dp 的轨道上那样会显示成一个
            // 带两条暗竖线的白方块，用户明确否决。
            if (clipStartX != null && clipEndX != null) {
                // 圆点直径 = 轨道高度（dotR = capR = trackH/2），不超出进度条
                listOf(clipStartX, clipEndX).forEach { x ->
                    drawCircle(color = Color.White, radius = capR, center = Offset(x, cy))
                }
            }

            // ⑤ 拖动手柄：与进度条等高，拖动中进度条转主色，白色手柄浮在其上可见
            drawCircle(
                color = Color.White,
                radius = capR,
                center = Offset(fraction * w, cy),
            )
        }
    }
}

private fun Float.toSeekMs(widthPx: Int, durationMs: Long): Long {
    if (widthPx <= 0) return 0L
    val f = (this / widthPx).coerceIn(0f, 1f)
    return (f * durationMs).toLong()
}

/**
 * 操作行：收藏(喜欢) / 评论 / 分享 / 更多，图标 + 可选计数（无计数时只显示图标）。
 * 播放控制不在此处，已由底部 Tab 栏中央圆钮承担。
 */
@Composable
private fun StreamActionRow(
    isFavorite: Boolean,
    likeCount: Int,
    commentCount: Int,
    onFavoriteClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StreamActionItem(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isFavorite) "取消收藏" else "收藏",
            tint = if (isFavorite) AccentColor else Color.White,
            count = formatStreamCount(likeCount),
            onClick = onFavoriteClick,
        )
        Spacer(modifier = Modifier.width(22.dp))
        StreamActionItem(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = "评论",
            count = formatStreamCount(commentCount),
            onClick = onCommentClick,
        )
        Spacer(modifier = Modifier.width(22.dp))
        StreamActionItem(
            imageVector = Icons.Filled.Share,
            contentDescription = "分享",
            onClick = onShareClick,
        )
        Spacer(modifier = Modifier.weight(1f))
        StreamActionItem(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "更多",
            onClick = onMoreClick,
        )
    }
}

@Composable
private fun StreamActionItem(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    count: String? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        if (count != null) {
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = count,
                color = tint.copy(alpha = 0.9f),
                fontSize = 12.sp,
                style = TextStyle(shadow = LyricShadow),
            )
        }
    }
}

/**
 * 计数展示口径：万以下原样；万级保留一位小数（1.2w）；百万级取整并带 +（330w+）。
 * 无计数（<=0）时返回 null，UI 只显示图标。
 */
private fun formatStreamCount(count: Int): String? {
    if (count <= 0) return null
    return when {
        count < 10_000 -> count.toString()
        count < 1_000_000 -> "${count / 10_000}.${(count % 10_000) / 1_000}w"
        else -> "${count / 10_000}w+"
    }
}

/**
 * 「听歌模式」弹层宿主（BottomSheet，按用户反馈从侧滑抽屉改回底部弹层）。
 *
 * 由外层 Tab 宿主渲染在**底部 Tab 栏之后**，弹层与遮罩能盖住底栏。
 * 内容沿用原型 06「按心情听」的层级：hero 卡 → 输入行 → 分组 chips。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSceneDrawerHost(
    open: Boolean,
    onClose: () -> Unit,
    currentMood: String?,
    onSubmit: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 统一走 FoxSheet：22dp 顶圆角 + drag handle。原本用 colorScheme.background，
    // 这里显式沿用，避免深浅主题下与页面底色脱节。
    FoxSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) {
        StreamScenePanel(
            currentMood = currentMood,
            onSubmit = onSubmit,
        )
    }
}

/**
 * 听歌模式侧边栏内容（原型 06「按心情听」）：
 * 深色 hero 卡（当前场景 + 跳动柱 + 换一首）→ 输入行（46dp 输入 + 黑圆提交）→
 * 按「情绪 / 场景 / 能量 / 曲风与语言」分组的 chips，选中态只有一处（实心墨底）。
 * 旧版是「默认模式横条 + 一整面 3 列图标格子」，没有信息层级，选择成本高。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamScenePanel(
    currentMood: String?,
    onSubmit: (String?) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 弹层限高：chips 全展开内容很长，超出部分弹层内滚动
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FoxSpacing.pagePadding)
            .navigationBarsPadding()
            .padding(bottom = 24.dp, top = 4.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "按心情听",
            color = scheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "选一个场景，为你刷更合拍的片段",
            color = scheme.onSurfaceVariant,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(modifier = Modifier.height(18.dp))

        SceneHeroCard(
            currentMood = currentMood,
            onReshuffle = { onSubmit(currentMood) },
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ── 自由输入：46dp 输入框 + 46dp 墨色圆提交键，一条轴 ───────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    // 白底 + 发丝描边：设计稿输入框是白底轻描边（米色 sheet 上
                    // surfaceVariant 底分离度不足，输入感偏弱）。
                    .background(scheme.surface)
                    .border(1.dp, scheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(13.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(MoodMaxLength) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(
                                text = "输入风格或心情，例如「雨天 通勤」",
                                color = scheme.onSurface.copy(alpha = 0.4f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(scheme.onSurface)
                    .clickable { onSubmit(text.trim().ifBlank { null }) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = "提交",
                    tint = scheme.background,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = "${text.length} / $MoodMaxLength",
            color = scheme.onSurface.copy(alpha = 0.38f),
            fontSize = 10.5.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            textAlign = TextAlign.End,
        )

        // ── 分组 chips ──────────────────────────────────────────────────
        SceneGroups.forEach { group ->
            Text(
                text = group.label,
                color = scheme.onSurface.copy(alpha = 0.38f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 9.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.scenes.forEach { scene ->
                    val isDefault = scene.mood == null
                    SceneChip(
                        label = scene.label,
                        selected = scene.mood == currentMood || (isDefault && currentMood == null),
                        warm = scene.mood in WarmMoods,
                        onClick = { onSubmit(scene.mood) },
                    )
                }
            }
        }
    }
}

/** 当前场景 hero 卡：深底 + 跳动柱 + 换一首。点「换一首」按当前场景重刷一批片段。 */
@Composable
private fun SceneHeroCard(
    currentMood: String?,
    onReshuffle: () -> Unit,
) {
    val label = moodSceneLabel(currentMood) ?: "默认模式"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(InkGlass)
            .clickable(onClick = onReshuffle)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SceneEqBars()
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = Color(0xFFFBF8F4),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "正在按场景连播高潮片段",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = "换一首",
                color = Color(0xFFFBF8F4),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.26f), RoundedCornerShape(999.dp))
                    .clickable(onClick = onReshuffle)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** 三根错峰跳动的柱子（与 MusicListItem 的 NowPlayingBars 同款语言，尺寸放大）。 */
@Composable
private fun SceneEqBars() {
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = listOf(7.dp, 14.dp, 10.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(18.dp),
    ) {
        heights.forEachIndexed { index, h ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 170),
                ),
                label = "eq$index",
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h * (0.45f + 0.55f * phase))
                    .background(Color(0xFFE2563C), RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun SceneChip(
    label: String,
    selected: Boolean,
    warm: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val background = when {
        selected -> scheme.onSurface
        warm -> Color(0xFFF1E4DC)
        else -> scheme.surfaceVariant
    }
    val content = when {
        selected -> scheme.background
        warm -> Color(0xFF8E4130)
        else -> scheme.onSurface.copy(alpha = 0.85f)
    }
    Text(
        text = label,
        color = content,
        fontSize = 12.5.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** 场景分组：mood=null 表示「回到默认推荐」。 */
private data class SceneGroup(val label: String, val scenes: List<SceneChipItem>)
private data class SceneChipItem(val label: String, val mood: String?)

private val SceneGroups = listOf(
    SceneGroup("情绪", listOf(
        SceneChipItem("默认推荐", null),
        SceneChipItem("专注模式", "专注"),
        SceneChipItem("摸鱼", "摸鱼"),
        SceneChipItem("深夜 EMO", "深夜EMO"),
        SceneChipItem("快乐时光", "快乐"),
        SceneChipItem("好运", "好运"),
        SceneChipItem("失恋必听", "失恋"),
        SceneChipItem("躺平", "躺平"),
    )),
    SceneGroup("场景", listOf(
        SceneChipItem("助眠模式", "助眠"),
        SceneChipItem("洗澡", "洗澡"),
        SceneChipItem("通勤必听", "通勤"),
        SceneChipItem("Chill 放松", "Chill"),
        SceneChipItem("打扫", "打扫"),
        SceneChipItem("抖音漫游", "抖音"),
    )),
    SceneGroup("能量", listOf(
        SceneChipItem("动感健身", "健身"),
        SceneChipItem("电音", "电音"),
        SceneChipItem("打游戏", "游戏"),
    )),
    SceneGroup("曲风与语言", listOf(
        SceneChipItem("华语", "华语"),
        SceneChipItem("粤语", "粤语"),
        SceneChipItem("日韩", "日韩"),
        SceneChipItem("欧美", "欧美"),
        SceneChipItem("国风", "国风"),
    )),
)

/** 高意图场景给暖色 chip（设计稿 accentSoft），给静态列表一点温度差。 */
private val WarmMoods = setOf("助眠")

private fun moodSceneLabel(mood: String?): String? {
    val value = mood?.takeIf { it.isNotBlank() } ?: return null
    return SceneGroups.asSequence()
        .flatMap { it.scenes }
        .firstOrNull { it.mood == value }
        ?.label
        ?: value
}

/**
 * 歌词展示：以当前播放行为中心，展示上一/当前/下一共 3 行，当前行高亮放大，其余淡出。
 * 无歌词时不占位。
 */
@Composable
private fun LyricsBlock(music: Music?, position: Long) {
    val lines = remember(music?.lyrics) {
        music?.lyricLines?.filter { it.text.isNotBlank() }.orEmpty()
    }
    if (lines.isEmpty()) return

    val currentIndex = remember(lines, position) {
        lines.indexOfLast { position >= it.startTimeMs }.coerceAtLeast(0)
    }
    val start = (currentIndex - 1).coerceAtLeast(0)
    val end = (currentIndex + 2).coerceAtMost(lines.size)
    val visible = lines.subList(start, end)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        visible.forEachIndexed { offset, line ->
            val isCurrentLine = (start + offset) == currentIndex
            Text(
                text = line.text,
                color = if (isCurrentLine) Color.White else Color.White.copy(alpha = 0.45f),
                fontSize = if (isCurrentLine) 18.sp else 14.sp,
                fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = LyricShadow),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun FullScreenHint(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = Color.White)
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = AccentColor,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable { onRetry() },
                ) {
                    Text("重试", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
        }
    }
}

/** 音流心情自由输入的字数上限 */
private const val MoodMaxLength = 20

/** 预热封面时用的近似展示尺寸（像素）；实际 AsyncImage 由布局约束决定，此值仅用于命中共通缓存 */
private const val StreamCoverSizePx = 720

private val AccentColor = Color(0xFFFF4081)

/** 压在花哨封面/渐变上的文字统一加暗色阴影，保证可读性。 */
private val LyricShadow = Shadow(
    color = Color(0xCC000000),
    offset = Offset(0f, 1.5f),
    blurRadius = 10f,
)

/** 顶栏（不含状态栏）高度：与其它 Tab 顶栏一致（40dp 按钮 + 上下 8dp 内边距） */
private val StreamTopBarHeight = 56.dp

/** 音流页已隐藏 MiniPlayer，底部只需让开浮岛 Tab 栏（64dp 高 + 上下 8dp 外边距 + 余量） */
private val StreamChromeBottom = 96.dp

/** 双击点赞漂浮爱心：尺寸与配色（抖音式亮红，压在深色渐变上足够醒目） */
private val HeartSize = 104.dp
private val HeartColor = Color(0xFFFF2D55)

/** 「听歌模式」面板宽度占屏比：右侧留出被遮罩压暗的音流页 */
private const val PanelWidthFraction = 0.86f

/** 同色相压暗（保持 RGB 比例，只降亮度），用于「封面主色 → 底部同色更深」的渐变 */
private fun Color.scaleRgb(factor: Float): Color =
    Color(red * factor, green * factor, blue * factor, alpha)

/** 将封面主色写入状态表（已缓存则跳过）；取色失败静默忽略，由当前页回落主题色。 */
private suspend fun ensureCoverColor(
    context: android.content.Context,
    url: String?,
    colors: SnapshotStateMap<String, Color>,
) {
    val key = url?.takeIf { it.isNotBlank() } ?: return
    if (colors.containsKey(key)) return
    val argb = streamCoverColor(context, key) ?: return
    colors[key] = Color(argb)
}


/** 顶栏标题：默认「听歌模式」，选中场景后展示「听歌模式 · {场景名}」。 */
private fun streamSceneTitle(mood: String?): String {
    val label = moodSceneLabel(mood)
    return if (label.isNullOrBlank()) "听歌模式" else "听歌模式 · $label"
}
