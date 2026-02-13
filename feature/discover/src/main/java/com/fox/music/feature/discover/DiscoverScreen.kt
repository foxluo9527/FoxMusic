package com.fox.music.feature.discover

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.Artist
import com.fox.music.core.model.Music
import com.fox.music.core.model.Playlist
import com.fox.music.core.model.PlaylistDetail
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.HotKeywordsCarousel
import com.fox.music.core.ui.component.MusicListItem
import com.fox.music.core.ui.component.SectionHeader

const val DISCOVER_ROUTE = "discover"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music, List<Music>, String) -> Unit = { _, _, _ -> },
    updateMusicList: (List<Music>, String) -> Unit = { _, _ -> },
    onRankClick: (Long) -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNewMusicMore:()->Unit={}
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is DiscoverEffect.NavigateToMusic -> onMusicClick(
                    effect.music,
                    state.newMusic,
                    DISCOVER_ROUTE
                )
                is DiscoverEffect.NavigateToRank -> onRankClick(effect.rankId)
                is DiscoverEffect.NavigateToArtist -> onArtistClick(effect.artistId)
                is DiscoverEffect.NavigateToSearch -> { onSearchClick() }
            }
        }
    }

    LaunchedEffect(state.newMusic) {
        updateMusicList(state.newMusic, DISCOVER_ROUTE)
    }

    with(sharedTransitionScope) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.sendIntent(DiscoverIntent.Refresh) },
            modifier = modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 搜索热词轮播 - 占据整行
                item(
                    span = { GridItemSpan(4) },
                    key = "hot_keywords",
                    contentType = "search_bar"
                ) {
                    HotKeywordsCarousel(
                        keywords = state.hotKeywords,
                        onSearchClick = { viewModel.onSearchClick() },
                    )
                }

                // 热门榜单
                if (state.ranks.isNotEmpty()) {
                    // 标题 - 占据整行
                    item(
                        span = { GridItemSpan(4) },
                        key = "rank_header",
                        contentType = "section_header"
                    ) {
                        SectionHeader(
                            title = "热门榜单",
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }

                    // 榜单Pager - 占据整行
                    item(
                        span = { GridItemSpan(4) },
                        key = "rank_pager",
                        contentType = "rank_pager"
                    ) {
                        RankPager(
                            ranks = state.ranks,
                            rankDetails = state.rankDetails,
                            onRankClick = { rank ->
                                viewModel.sendIntent(DiscoverIntent.OnRankClick(rank))
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
                        contentType = "section_header"
                    ) {
                        SectionHeader(
                            title = "新歌速递",
                            onMoreClick = onNewMusicMore,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }

                    // 新歌列表 - 每个占据整行
                    items(
                        count = minOf(state.newMusic.size, 10),
                        key = { index -> "new_music_${state.newMusic[index].id}" },
                        span = { GridItemSpan(4) },
                        contentType = { "music_item" }
                    ) { index ->
                        val music = state.newMusic[index]
                        MusicListItem(
                            music = music,
                            sharedTransitionScope = this@with,
                            animatedContentScope = animatedContentScope,
                            onClick = { viewModel.sendIntent(DiscoverIntent.OnMusicClick(music)) },
                        )
                    }
                }

                // 热门歌手
                if (state.hotArtists.isNotEmpty()) {
                    // 标题 - 占据整行
                    item(
                        span = { GridItemSpan(4) },
                        key = "artist_header",
                        contentType = "section_header"
                    ) {
                        SectionHeader(
                            title = "热门歌手",
                            onMoreClick = {},
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }

                    // 歌手网格项 - 每个占1列
                    items(
                        count = state.hotArtists.size,
                        key = { index -> "artist_${state.hotArtists[index].id}" },
                        contentType = { "artist_item" }
                    ) { index ->
                        val artist = state.hotArtists[index]
                        ArtistGridItem(
                            artist = artist,
                            onClick = { viewModel.sendIntent(DiscoverIntent.OnArtistClick(artist)) },
                        )
                    }
                }
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
    val pagerState = rememberPagerState(pageCount = { ranks.size })

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        contentPadding = PaddingValues(end = 100.dp), // 右侧显示下一页的1/3
        pageSpacing = 12.dp,
    ) { page ->
        val rank = ranks[page]
        val detail = rankDetails[rank.id]

        RankCard(
            rank = rank,
            detail = detail,
            onClick = { onRankClick(rank) },
        )
    }
}

@Composable
private fun RankCard(
    rank: Playlist,
    detail: PlaylistDetail?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .width(280.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // 封面和信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 封面
                Card(
                    modifier = Modifier.size(80.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    CachedImage(
                        imageUrl = rank.coverImage,
                        contentDescription = rank.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // 榜单名称和歌曲数
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = rank.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${rank.trackCount} 首歌曲",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 前4首歌曲
            if (detail != null && detail.tracks.list.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                detail.tracks.list.take(4).forEachIndexed { index, music ->
                    RankMusicItem(
                        index = index + 1,
                        music = music,
                    )
                    if (index < 3 && index < detail.tracks.list.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                if (detail.tracks.list.size < 4) {
                    repeat(4-detail.tracks.list.size){
                        Spacer(modifier = Modifier.height(46.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RankMusicItem(
    index: Int,
    music: Music,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 序号
        Text(
            text = index.toString().padStart(2, '0'),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (index <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center,
        )

        // 歌曲信息
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = music.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = music.artists.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .padding(horizontal = 4.dp)
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
