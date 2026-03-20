package com.fox.music.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.Favorite
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.SectionHeader
import kotlinx.coroutines.launch

const val PROFILE_ROUTE = "profile"

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: ProfileViewModel = hiltViewModel(),
    isLogin: Boolean,
    onLogin: () -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
    onCreatePlaylistClick: () -> Unit = {},
    manageMusics: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val collectState = rememberPagerState {4}
    val scope = rememberCoroutineScope()

    LaunchedEffect(isLogin) {
        if (isLogin) {
            viewModel.sendIntent(ProfileIntent.Load)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect {effect ->
            when(effect) {
                is ProfileEffect.NavigateToPlaylist -> onPlaylistClick(effect.playlistId)
                ProfileEffect.NavigateToCreatePlaylist -> onCreatePlaylistClick()
                else -> {}
            }
        }
    }

    when {
        ! isLogin -> ErrorView(
            Modifier.fillMaxSize(),
            "请先登录",
            retryText = "登录",
            onRetry = onLogin
        )

        state.isLoading && state.user == null -> LoadingIndicator(useLottie = false)
        state.error != null && state.user == null -> ErrorView(message = state.error!!)
        else -> state.user?.let { user ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CachedImage(
                        imageUrl = user.avatar,
                        contentDescription = user.username,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                        placeholderIcon = Icons.Default.Person
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = user.nickname ?: user.username,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    user.signature?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(label = "歌单", count = state.playlists.size)
                    StatItem(label = "收藏", count = state.favoritePlaylists.size)
                    StatItem(label = "专辑", count = state.favoriteAlbums.size)
                }


                // Manage Music Button

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = manageMusics,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "曲库管理"
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "曲库管理",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                PrimaryTabRow(collectState.currentPage, divider = {}) {
                    Tab(
                        selected = collectState.currentPage == 0,
                        onClick = {
                            scope.launch {
                                collectState.animateScrollToPage(0)
                            }
                        },
                        text = {Text("我的")}
                    )
                    Tab(
                        selected = collectState.currentPage == 1,
                        onClick = {
                            scope.launch {
                                collectState.animateScrollToPage(1)
                            }
                        },
                        text = {Text("歌单")}
                    )
                    Tab(
                        selected = collectState.currentPage == 2,
                        onClick = {
                            scope.launch {
                                collectState.animateScrollToPage(2)
                            }
                        },
                        text = {Text("专辑")}
                    )
                    Tab(
                        selected = collectState.currentPage == 3,
                        onClick = {
                            scope.launch {
                                collectState.animateScrollToPage(3)
                            }
                        },
                        text = {Text("艺术家")}
                    )
                }

                HorizontalPager(
                    collectState,
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) {tabIndex ->
                    when(tabIndex) {
                        0 -> {
                            LazyColumn {
                                item {
                                    SectionHeader(
                                        title = "我的歌单",
                                        modifier = Modifier.padding(top = 8.dp),
                                        moreText = "创建歌单",
                                        moreIcon = Icons.Default.Add,
                                        onMoreClick = {viewModel.onCreatePlaylistClick()}
                                    )
                                }

                                // User's Playlists
                                items(state.playlists, key = {it.id}) {playlist ->
                                    PlaylistCard(
                                        playlist = playlist,
                                        onClick = {
                                            viewModel.sendIntent(
                                                ProfileIntent.OnPlaylistClick(
                                                    playlist
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        1 -> {
                            LazyColumn {
                                item {
                                    SectionHeader(
                                        title = "收藏的歌单",
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                if (state.favoritePlaylists.isNotEmpty()) {
                                    items(state.favoritePlaylists, key = {it.id}) {favorite ->
                                        FavoriteCard(
                                            favorite = favorite,
                                            onClick = {onPlaylistClick(favorite.targetId)}
                                        )
                                    }
                                } else {
                                    item {
                                        ErrorView(
                                            Modifier
                                                .fillMaxSize()
                                                .padding(16.dp)
                                                .weight(1f),
                                            message = "暂无任何收藏歌单",
                                            showIcon = false
                                        )
                                    }
                                }
                            }
                        }

                        2 -> {
                            LazyColumn {
                                item {
                                    SectionHeader(
                                        title = "收藏的专辑",
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                if (state.favoriteAlbums.isNotEmpty()) {
                                    items(state.favoriteAlbums, key = {it.id}) {favorite ->
                                        FavoriteCard(
                                            favorite = favorite,
                                            onClick = { /* Navigate to album */}
                                        )
                                    }
                                } else {
                                    item {
                                        ErrorView(
                                            Modifier
                                                .fillMaxSize()
                                                .padding(16.dp)
                                                .weight(1f),
                                            message = "暂无任何收藏专辑",
                                            showIcon = false
                                        )
                                    }
                                }
                            }
                        }

                        3 -> {
                            LazyColumn {
                                item {
                                    SectionHeader(
                                        title = "收藏的艺术家",
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                if (state.favoriteArtists.isNotEmpty()) {
                                    items(state.favoriteArtists, key = {it.id}) {favorite ->
                                        FavoriteCard(
                                            favorite = favorite,
                                            isCircle = true,
                                            onClick = { /* Navigate to artist */}
                                        )
                                    }
                                } else {
                                    item {
                                        ErrorView(
                                            Modifier
                                                .fillMaxSize()
                                                .padding(16.dp)
                                                .weight(1f),
                                            message = "暂无任何收藏艺术家",
                                            showIcon = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun StatItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .width(60.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            CachedImage(
                imageUrl = playlist.coverImage,
                contentDescription = playlist.title,
                modifier = Modifier
                    .size(50.dp),
                shape = MaterialTheme.shapes.medium,
                placeholderIcon = Icons.AutoMirrored.Filled.QueueMusic
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${playlist.trackCount}首歌曲",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FavoriteCard(
    favorite: Favorite,
    isCircle: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .height(60.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = if (isCircle) CircleShape else MaterialTheme.shapes.medium
        ) {
            CachedImage(
                imageUrl = null, // Will need to fetch actual images
                contentDescription = favorite.title,
                modifier = Modifier
                    .size(50.dp)
                    .then(
                        if (! isCircle) Modifier.aspectRatio(1f)
                        else Modifier
                    ),
                shape = if (isCircle) CircleShape else MaterialTheme.shapes.medium,
                placeholderIcon = when {
                    isCircle -> Icons.Default.Person
                    else -> Icons.Default.Album
                }
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = favorite.title ?: "未知",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

