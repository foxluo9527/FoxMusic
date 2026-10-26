package com.fox.music.feature.discover

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.Menu
import com.fox.music.core.ui.component.FoxCircleIconButton
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.music.PlaylistDetail
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.FoxPageHeader
import com.fox.music.core.ui.component.HotKeywordsCarousel
import com.fox.music.core.ui.component.MusicListItem
import com.fox.music.core.ui.component.SectionHeader
import com.fox.music.core.ui.theme.CoverShapeHero
import com.fox.music.core.ui.theme.FoxSpacing

/**
 * 发现页纯界面：只依赖 [DiscoverState] 与 [DiscoverIntent]，不碰 Hilt / ViewModel。
 * Android 外壳（:feature:discover-android 的 DiscoverScreen）负责 hiltViewModel() 与 effect 收集。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverContent(
    state: DiscoverState,
    onIntent: (DiscoverIntent) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNewMusicMore: () -> Unit = {},
    onHotArtistMore: () -> Unit = {},
    onMusicMoreClick: (Music) -> Unit = {},
) {
    with(sharedTransitionScope) {
        // 顶部头部与搜索框固定在滚动区域之外：发现页的动作入口（搜索）不能滚走。
        // 头部改用左对齐 + 眉标的 FoxPageHeader，与首页 / 广场共用同一条对齐轴（24dp）。
        val gridState = rememberLazyGridState()
        // 头部随列表滚走后，右上角浮出一颗抽屉入口——否则滑到下面就没有任何"回首页/消息"的入口。
        val headerCollapsed by remember {
            derivedStateOf {
                gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 72
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onIntent(DiscoverIntent.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 0.dp,
                    bottom = FoxSpacing.tabChromeBottom, // 悬浮 MiniPlayer + 底栏
                    start = FoxSpacing.pagePadding,
                    end = FoxSpacing.pagePadding,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 头部与搜索框随列表滚动：固定住的话，滚动内容会贴着搜索框下沿被硬切一刀。
                // 设计稿 b3：紧凑顶栏（☰ 左 + 标题居中同一行）+ 搜索框，
                // 不再是「☰ 独占一行 + 眉标 + 大标题」的堆叠头部。
                item(
                    span = { GridItemSpan(4) },
                    key = "discover_header",
                    contentType = "header",
                ) {
                    Column(modifier = Modifier.padding(bottom = 6.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                        ) {
                            FoxCircleIconButton(
                                icon = Icons.Filled.Menu,
                                contentDescription = "更多",
                                onClick = onOpenDrawer,
                                modifier = Modifier.align(Alignment.CenterStart),
                            )
                            Text(
                                text = "发现",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        HotKeywordsCarousel(
                            keywords = state.hotKeywords,
                            onSearchClick = onSearchClick,
                        )
                    }
                }

                // 热门榜单
                if (state.ranks.isNotEmpty()) {
                    // 标题 - 占据整行
                    item(
                        span = { GridItemSpan(4) },
                        key = "rank_header",
                        contentType = "section_header",
                    ) {
                        SectionHeader(title = "热门榜单")
                    }

                    // 榜单Pager - 占据整行
                    item(
                        span = { GridItemSpan(4) },
                        key = "rank_pager",
                        contentType = "rank_pager",
                    ) {
                        RankPager(
                            ranks = state.ranks,
                            rankDetails = state.rankDetails,
                            onRankClick = { rank ->
                                onIntent(DiscoverIntent.OnRankClick(rank))
                            },
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }

                // 新歌速递
                if (state.newMusic.isNotEmpty()) {
                    // 标题 - 占据整行
                    item(
                        span = { GridItemSpan(4) },
                        key = "new_music_header",
                        contentType = "section_header",
                    ) {
                        SectionHeader(title = "新歌速递", moreText = "", onMoreClick = onNewMusicMore)
                    }

                    // 新歌列表 - 每个占据整行
                    items(
                        count = minOf(state.newMusic.size, 5),
                        key = { index -> "new_music_${state.newMusic[index].id}" },
                        span = { GridItemSpan(4) },
                        contentType = { "music_item" },
                    ) { index ->
                        val music = state.newMusic[index]
                        MusicListItem(
                            music = music,
                            sharedTransitionScope = this@with,
                            animatedContentScope = animatedContentScope,
                            onClick = { onIntent(DiscoverIntent.OnMusicClick(music)) },
                            onMoreClick = { onMusicMoreClick(music) },
                            showDivider = index > 0,
                        )
                    }
                }

                // 热门歌手
                if (state.hotArtists.isNotEmpty()) {
                    // 标题 - 占据整行
                    item(
                        span = { GridItemSpan(4) },
                        key = "artist_header",
                        contentType = "section_header",
                    ) {
                        SectionHeader(title = "热门歌手", onMoreClick = onHotArtistMore, moreText = "")
                    }

                    // 歌手网格项 - 每个占1列
                    items(
                        count = state.hotArtists.size,
                        key = { index -> "artist_${state.hotArtists[index].id}" },
                        contentType = { "artist_item" },
                    ) { index ->
                        val artist = state.hotArtists[index]
                        ArtistGridItem(
                            artist = artist,
                            onClick = { onIntent(DiscoverIntent.OnArtistClick(artist)) },
                        )
                    }
                }
            }
        }

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RankPager(
    ranks: List<Playlist>,
    rankDetails: Map<Long, PlaylistDetail>,
    onRankClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardWidth = 280.dp
    val lazyListState = rememberLazyListState()

    LazyRow(
        state = lazyListState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        flingBehavior = rememberSnapFlingBehavior(lazyListState),
    ) {
        itemsIndexed(
            items = ranks,
            key = { _, rank -> rank.id },
        ) { _, rank ->
            RankCard(
                rank = rank,
                detail = rankDetails[rank.id],
                onClick = { onRankClick(rank) },
                modifier = Modifier.width(cardWidth),
            )
        }
    }
}

@Composable
private fun RankCard(
    rank: Playlist,
    detail: PlaylistDetail?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val hairline = scheme.onSurface.copy(alpha = 0.07f)
    val tracks = detail?.tracks?.list.orEmpty()

    Column(
        modifier = modifier
            .clip(CoverShapeHero)
            .background(scheme.surface)
            .border(1.dp, hairline, CoverShapeHero)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CachedImage(
                imageUrl = rank.coverImage,
                contentDescription = rank.title,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rank.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${rank.trackCount} 首",
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        if (tracks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(13.dp))
            HorizontalDivider(color = hairline, thickness = 1.dp)
            tracks.take(4).forEachIndexed { index, music ->
                RankMusicItem(
                    index = index + 1,
                    music = music,
                    showDivider = index > 0,
                )
            }
        }
    }
}

/**
 * 榜单内的曲目行：序号用等宽数字压到 20dp 固定宽，标题一行、艺人一行但整体 36dp 高。
 * 旧版序号借用 titleMedium（Fraunces 显示体）且带 8dp 行距，四行排下来把卡片撑得很散。
 */
@Composable
private fun RankMusicItem(
    index: Int,
    music: Music,
    showDivider: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (showDivider) {
                    drawLine(
                        color = scheme.onSurface.copy(alpha = 0.07f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            color = if (index <= 3) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(22.dp),
            textAlign = TextAlign.Start,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = music.title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                ),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = music.artists.joinToString(", ") { it.name },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                ),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArtistGridItem(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 头像
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            CachedImage(
                imageUrl = artist.avatar,
                errorImageUrl = artist.coverImage,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                placeholderIcon = Icons.Filled.Person,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 歌手名称
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
