package com.fox.music.feature.home

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayHistory
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.FoxCircleIconButton
import com.fox.music.core.ui.component.MusicListItem
import com.fox.music.core.ui.component.SectionHeader
import com.fox.music.core.ui.theme.CoverShapeRail
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.core.ui.theme.SearchFieldShape
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

const val HOME_ROUTE = "home"

/** 首页「为你精选」展示条数（与 HomeViewModel 落快照时的截断保持一致） */
private const val HOME_RECOMMEND_MUSIC_COUNT = 5

private val CHINESE_WEEKDAYS = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 首页纯界面：state + onIntent 驱动。
 *
 * 2026-10 改版（原型 01 首页）——三处结构性改动：
 * 1. **顺序即态度**：最近在听 → 为你精选 → 精选歌单 → 热门专辑。个人化内容先给，编辑内容后给；
 *    原来「推荐歌单」四列大图放在第一屏，把真正属于"我"的东西挤到了折叠线以下。
 * 2. **建立竖向节奏**：板块间距走 [FoxSpacing.sectionGap]，标题到内容走 [FoxSpacing.headerGap]，
 *    列表行高锁 [FoxSpacing.rowHeight]；不再是均质的 8/12 堆叠。
 * 3. **顶部换成"人"**：日期 + 时段问候 + 右上角头像（点头像开抽屉）。原来左上那颗
 *    带圆形底色的三横按钮既抢眼又和「我的」页不一致。
 *
 * 分页列表仍由 [HomeState.recommendedMusic] 的 Flow<PagingData<Music>> 驱动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
    onMusicClick: (Music) -> Unit = {},
    // 「最近在听」单独回调：与「为你精选」不同源，队列和来源都不能按歌曲 id 混判
    onRecentMusicClick: (Music) -> Unit = onMusicClick,
    onSearchClick: () -> Unit = {},
    // 客户端 AI 音乐助手入口：已迁至全局左侧抽屉，此处保留参数仅为兼容旧调用方。
    onAssistantClick: (() -> Unit)? = null,
    onPlaylistMoreClick: () -> Unit = {},
    onAlbumMoreClick: () -> Unit = {},
    onRecommendClick: () -> Unit = {},
    onRecentMoreClick: () -> Unit = {},
    onMusicMoreClick: (Music) -> Unit = {},
    updateMusicList: (List<Music>, String) -> Unit = { _, _ -> },
    onRecommendedMusicChanged: (List<Music>) -> Unit = {},
) {
    val recommendedMusicPagingItems = state.recommendedMusic.collectAsLazyPagingItems()
    // 缓存优先：分页空窗期用上一次的首批顶上
    val musicCache = state.recommendedMusicCache
    val recommendedMusicCount = recommendedMusicPagingItems.itemCount
        .takeIf { it > 0 }
        ?.let { minOf(it, HOME_RECOMMEND_MUSIC_COUNT) }
        ?: minOf(musicCache.size, HOME_RECOMMEND_MUSIC_COUNT)

    // 分页有条目时以分页为准，空窗期回落到快照。
    // 必须现读 itemCount 再取：LazyPagingItems.get(index) 在 index >= itemCount 时
    // 直接抛 IndexOutOfBoundsException（刷新会换新的 PagingData，首屏未回时 itemCount 为 0，
    // 而 count 可能还是按缓存算出来的 5 → get(0) 崩）。
    fun musicAt(index: Int): Music? =
        if (index < recommendedMusicPagingItems.itemCount) {
            recommendedMusicPagingItems[index]
        } else {
            musicCache.getOrNull(index)
        }

    // key 带来源前缀：缓存条目与分页条目 id 相同时也不会撞 key
    fun musicKeyAt(index: Int): String {
        val fromPaging = index < recommendedMusicPagingItems.itemCount
        val id = musicAt(index)?.id ?: index
        return if (fromPaging) "music_$id" else "music_cache_$id"
    }

    LaunchedEffect(recommendedMusicPagingItems.itemSnapshotList.items) {
        val items = recommendedMusicPagingItems.itemSnapshotList.items
        onRecommendedMusicChanged(items)
        if (items.isNotEmpty()) {
            updateMusicList(items, HOME_ROUTE)
        }
    }

    // 顶部渐变是 Tab 根页统一铺的（foxTabPageBackground），头部不能盖成实心块；
    // 也不能固定悬浮——那样滚动上来的内容会从透明头部底下穿过去。
    // 所以头部随列表一起滚，滚走后在右上角浮出「更多」圆钮（与发现页同款），保证抽屉入口always可达。
    val gridState = rememberLazyGridState()
    val headerCollapsed by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 56
        }
    }

    with(sharedTransitionScope) {
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { onIntent(HomeIntent.Refresh) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(1),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 0.dp,
                            bottom = FoxSpacing.tabChromeBottom, // 悬浮 MiniPlayer + 底栏
                            start = FoxSpacing.pagePadding,
                            end = FoxSpacing.pagePadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        item(key = "greeting", contentType = "greeting") {
                            HomeGreetingHeader(
                                userName = state.userName,
                                avatarUrl = state.avatarUrl,
                                onOpenDrawer = onOpenDrawer,
                            )
                        }
                        item(key = "search", contentType = "search") {
                            HomeSearchField(
                                onSearchClick = onSearchClick,
                                hotHint = state.hotKeywords.firstOrNull()?.keyword,
                            )
                        }

                    if (state.recentPlays.isNotEmpty()) {
                        item(key = "recent_header", contentType = "section_header") {
                            SectionHeader(
                                title = "最近在听",
                                moreText = "",
                                onMoreClick = onRecentMoreClick,
                            )
                        }
                        item(key = "recent_rail", contentType = "recent_rail") {
                            RecentPlaysRail(
                                recentPlays = state.recentPlays,
                                onMusicClick = onRecentMusicClick,
                                onMoreClick = onRecentMoreClick,
                            )
                        }
                    }

                    item(key = "music_header", contentType = "section_header") {
                        SectionHeader(
                            title = "为你精选",
                            moreText = "",
                            onMoreClick = onRecommendClick,
                        )
                    }

                    // 分页首屏还没回来时，先用快照里的「为你精选」顶上（缓存优先）；
                    // 分页一旦有数据就以分页为准（新数据替换旧数据）。
                    items(
                        count = recommendedMusicCount,
                        key = { index -> musicKeyAt(index) },
                        contentType = { "music_item" },
                    ) { index ->
                        musicAt(index)?.let { music ->
                            MusicListItem(
                                music = music,
                                sharedTransitionScope = this@with,
                                animatedContentScope = animatedContentScope,
                                onClick = { onMusicClick(music) },
                                onMoreClick = { onMusicMoreClick(music) },
                                showDivider = index > 0,
                            )
                        }
                    }

                    if (state.recommendedPlaylists.isNotEmpty()) {
                        item(key = "playlist_header", contentType = "section_header") {
                            SectionHeader(
                                title = "精选歌单",
                                moreText = "",
                                onMoreClick = onPlaylistMoreClick,
                            )
                        }
                        item(key = "playlist_rail", contentType = "playlist_rail") {
                            PlaylistRail(
                                playlists = state.recommendedPlaylists,
                                onPlaylistClick = { onIntent(HomeIntent.OnPlaylistClick(it)) },
                            )
                        }
                    }

                        if (state.recommendedAlbums.isNotEmpty()) {
                            item(key = "album_header", contentType = "section_header") {
                                SectionHeader(
                                    title = "热门专辑",
                                    moreText = "",
                                    onMoreClick = onAlbumMoreClick,
                                )
                            }
                            item(key = "album_rail", contentType = "album_rail") {
                                AlbumRail(
                                    albums = state.recommendedAlbums,
                                    onAlbumClick = { onIntent(HomeIntent.OnAlbumClick(it)) },
                                )
                            }
                        }
                    }
                }
            }

            // 滚过头部后浮出的「更多」按钮：抽屉入口不能因为滚动而消失。
            // 与发现页同款（Menu 圆钮 + 同一悬浮位），切 Tab 时右上角入口不换脸、不跳位。
            AnimatedVisibility(
                visible = headerCollapsed,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = FoxSpacing.pagePadding, top = 8.dp),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                FoxCircleIconButton(
                    icon = Icons.Filled.Menu,
                    contentDescription = "更多",
                    onClick = onOpenDrawer,
                    modifier = Modifier.shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    ),
                )
            }
        }
    }
}

/**
 * 首页头部：日期（eyebrow）+ 时段问候 + 右上角头像。
 * 头像即抽屉入口，与「我的」页的头部图标位置一致。
 */
@Composable
private fun HomeGreetingHeader(
    userName: String?,
    avatarUrl: String?,
    onOpenDrawer: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    val eyebrow = remember(now) {
        "${now.monthNumber}月${now.dayOfMonth}日 · ${CHINESE_WEEKDAYS[now.dayOfWeek.isoDayNumber]}"
    }
    val greeting = remember(now, userName) {
        val prefix = when (now.hour) {
            in 5..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..17 -> "下午好"
            in 18..22 -> "晚上好"
            else -> "夜深了"
        }
        val name = userName?.takeIf { it.isNotBlank() }
        if (name != null) "$prefix，$name" else prefix
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 水平内边距由 LazyVerticalGrid 的 contentPadding 统一给，这里只补上下
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    letterSpacing = 1.4.sp,
                ),
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = greeting,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = scheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        DrawerAvatarButton(userName = userName, avatarUrl = avatarUrl, onOpenDrawer = onOpenDrawer)
    }
}

/** 头像式抽屉入口：36dp 圆，有头像 URL 展示图片，否则取用户名首字母；未登录用人形图标。 */
@Composable
private fun DrawerAvatarButton(
    userName: String?,
    avatarUrl: String?,
    onOpenDrawer: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            // 用 primaryContainer 底 + onPrimaryContainer 字：原来 surfaceVariant + ink-3 的字母
            // 在纸感底上糊成一团，看着像"禁用按钮"。柔陶土圆点才读得出是"我的头像"。
            .background(scheme.primaryContainer)
            .border(1.dp, scheme.onSurface.copy(alpha = 0.06f), CircleShape)
            .clickable(onClick = onOpenDrawer),
        contentAlignment = Alignment.Center,
    ) {
        val avatar = avatarUrl?.takeIf { it.isNotBlank() }
        if (avatar != null) {
            // 有头像 URL：直接展示头像图（加载失败时 CachedImage 回落占位图标）
            CachedImage(
                imageUrl = avatar,
                contentDescription = "我的头像",
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                placeholderIcon = Icons.Outlined.Person,
            )
        } else {
            val initial = userName?.trim()?.firstOrNull()?.toString()
            if (initial != null) {
                Text(
                    text = initial.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = scheme.onPrimaryContainer,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "更多",
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** kotlinx-datetime 的 DayOfWeek 用 [isoDayNumber] 取 1..7（周一=1）。 */

/**
 * 搜索入口：白底 + 1dp 轻描边 + 13dp 圆角。
 * 原来是通栏白胶囊 + 投影 + 22dp 强调色放大镜，第一眼全被它抢走。
 */
@Composable
private fun HomeSearchField(
    onSearchClick: () -> Unit,
    hotHint: String?,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
            .clip(SearchFieldShape)
            .background(scheme.surface)
            .border(1.dp, scheme.onSurface.copy(alpha = 0.05f), SearchFieldShape)
            .clickable(onClick = onSearchClick)
            .height(40.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "搜索",
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = hotHint?.let { "试试「$it」" } ?: "搜索歌曲、艺人、歌单",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = scheme.onSurfaceVariant.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 最近在听：74dp 小卡横滑，标题 12.5 / 艺人 10.5。 */
@Composable
private fun RecentPlaysRail(
    recentPlays: List<PlayHistory>,
    onMusicClick: (Music) -> Unit,
    onMoreClick: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        contentPadding = PaddingValues(bottom = 2.dp),
    ) {
        items(recentPlays, key = { it.id }) { item ->
            Column(
                modifier = Modifier
                    .width(FoxSpacing.railItemSmall)
                    .clickable { onMusicClick(item.music) },
            ) {
                CachedImage(
                    imageUrl = item.music.coverImage,
                    contentDescription = item.music.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = CoverShapeRail,
                )
                Spacer(modifier = Modifier.height(9.dp))
                Text(
                    text = item.music.title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = scheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.music.artists.joinToString(", ") { it.name }
                        .ifBlank { "未知艺人" },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 精选歌单：108dp 大卡横滑，标题 12 / 副标题「N 首」压在下面一行。 */
@Composable
private fun PlaylistRail(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        contentPadding = PaddingValues(bottom = 2.dp),
    ) {
        items(playlists, key = { it.id }) { playlist ->
            Column(
                modifier = Modifier
                    .width(FoxSpacing.railItemLarge)
                    .clickable { onPlaylistClick(playlist) },
            ) {
                CachedImage(
                    imageUrl = playlist.coverImage,
                    contentDescription = playlist.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = CoverShapeRail,
                    placeholderIcon = Icons.AutoMirrored.Filled.QueueMusic,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = scheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val tag = playlist.tags.firstOrNull()?.name
                val subtitle = buildString {
                    if (playlist.trackCount > 0) append("${playlist.trackCount} 首")
                    if (!tag.isNullOrBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(tag)
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 热门专辑：与精选歌单同一套卡位，副标题换成曲目数与类型。 */
@Composable
private fun AlbumRail(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        contentPadding = PaddingValues(bottom = 2.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                modifier = Modifier
                    .width(FoxSpacing.railItemLarge)
                    .clickable { onAlbumClick(album) },
            ) {
                CachedImage(
                    imageUrl = album.coverImage,
                    contentDescription = album.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = CoverShapeRail,
                    placeholderIcon = Icons.Filled.Album,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = scheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    if (album.trackCount > 0) append("${album.trackCount} 首")
                    val type = album.type?.takeIf { it.isNotBlank() }
                    if (type != null) {
                        if (isNotEmpty()) append(" · ")
                        append(type)
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
