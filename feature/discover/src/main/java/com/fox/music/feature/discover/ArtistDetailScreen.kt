package com.fox.music.feature.discover

import android.widget.Toast
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.MusicListItem
import com.fox.music.core.ui.component.SectionHeader

const val ARTIST_DETAIL_ROUTE = "artist_detail/{artistId}"

fun artistDetailRoute(artistId: Long) = "artist_detail/$artistId"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
    onMusicClick: (Music, List<Music>, String, PlaybackPlaylistSource?) -> Unit = { _, _, _, _ -> },
    updateMusicList: (List<Music>, String) -> Unit = { _, _ -> },
    onAlbumClick: (Album) -> Unit = {},
    onBack: () -> Unit = {},
    onMusicMoreClick: (Music) -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val artist = state.artist
    val playlistKey = "artist_detail/${artist?.id ?: 0}"
    val listState = rememberLazyListState()
    var headerHeightPx by remember { mutableStateOf(0f) }
    val showCollapsedTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > headerHeightPx
        }
    }

    LaunchedEffect(state.hotMusics, artist?.id) {
        if (state.hotMusics.isNotEmpty()) {
            updateMusicList(state.hotMusics, playlistKey)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ArtistDetailEffect.NavigateToMusic -> {
                    onMusicClick(effect.music, effect.musicList, playlistKey, null)
                }

                is ArtistDetailEffect.NavigateToAlbum -> onAlbumClick(effect.album)
                is ArtistDetailEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (artist != null && showCollapsedTitle) {
                        Row(
                            Modifier.height(60.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CachedImage(
                                imageUrl = artist.avatar,
                                errorImageUrl = artist.coverImage,
                                contentDescription = artist.name,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                placeholderIcon = Icons.Filled.Person,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = artist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (artist != null) {
                        IconButton(
                            onClick = { viewModel.sendIntent(ArtistDetailIntent.ToggleFavorite) },
                            enabled = !state.isFavoriteLoading,
                        ) {
                            if (showCollapsedTitle) {
                                if (state.isFavoriteLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(
                                        imageVector = if (artist.isFavorite) {
                                            Icons.Filled.Favorite
                                        } else {
                                            Icons.Filled.FavoriteBorder
                                        },
                                        contentDescription = "收藏",
                                        tint = if (artist.isFavorite) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading && artist == null -> {
                LoadingIndicator(useLottie = false)
            }

            state.error != null && artist == null -> {
                ErrorView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    message = state.error ?: "加载失败",
                    onRetry = { viewModel.sendIntent(ArtistDetailIntent.Load) },
                )
            }

            artist != null -> {
                ArtistDetailBody(
                    artist = artist,
                    hotMusics = state.hotMusics,
                    albums = state.albums,
                    listState = listState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    onMusicClick = viewModel::onMusicClick,
                    onAlbumClick = viewModel::onAlbumClick,
                    onMusicMoreClick = onMusicMoreClick,
                    onFavoriteClick = {
                        viewModel.sendIntent(ArtistDetailIntent.ToggleFavorite)
                    },
                    onHeaderHeightMeasured = { headerHeightPx = it },
                    isFavoriteLoading = state.isFavoriteLoading,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun ArtistDetailBody(
    artist: Artist,
    hotMusics: List<Music>,
    albums: List<Album>,
    listState: LazyListState,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onMusicMoreClick: (Music) -> Unit,
    onFavoriteClick: () -> Unit,
    onHeaderHeightMeasured: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isFavoriteLoading: Boolean,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "header") {
            ArtistDetailHeader(
                artist = artist,
                onFavoriteClick = onFavoriteClick,
                isFavoriteLoading = isFavoriteLoading,
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    onHeaderHeightMeasured(coordinates.size.height.toFloat())
                }
            )
        }

        if (hotMusics.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "热门歌曲",
                    onMoreClick = null,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(hotMusics, key = { it.id }) { music ->
                MusicListItem(
                    music = music,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = { onMusicClick(music) },
                    onMoreClick = { onMusicMoreClick(music) },
                )
            }
        }

        if (albums.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "专辑",
                    onMoreClick = null,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(albums, key = { it.id }) { album ->
                        ArtistAlbumItem(
                            album = album,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope,
                            onClick = { onAlbumClick(album) },
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(68.dp))
        }
    }
}

@Composable
private fun ArtistDetailHeader(
    artist: Artist,
    modifier: Modifier = Modifier,
    onFavoriteClick: () -> Unit,
    isFavoriteLoading: Boolean
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = artist.avatar,
            errorImageUrl = artist.coverImage,
            contentDescription = artist.name,
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            placeholderIcon = Icons.Filled.Person,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            artist.alias?.takeIf { it.isNotBlank() }?.let { alias ->
                Text(
                    text = alias,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            val meta = buildList {
                artist.region?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (artist.musicCount > 0) add("${artist.musicCount} 首歌曲")
                if (artist.albumCount > 0) add("${artist.albumCount} 张专辑")
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (artist.tags.isNotEmpty()) {
                Text(
                    text = artist.tags.joinToString(" · ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            artist.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        IconButton(
            onClick = { onFavoriteClick() },
            enabled = !isFavoriteLoading,
        ) {
            if (isFavoriteLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    imageVector = if (artist.isFavorite) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Filled.FavoriteBorder
                    },
                    contentDescription = "收藏",
                    tint = if (artist.isFavorite) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun ArtistAlbumItem(
    album: Album,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    with(sharedTransitionScope) {
        Column(
            modifier = modifier
                .width(120.dp)
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = MaterialTheme.shapes.medium,
            ) {
                CachedImage(
                    imageUrl = album.coverImage,
                    contentDescription = album.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .sharedElement(
                            sharedTransitionScope.rememberSharedContentState("album-cover-${album.id}"),
                            animatedContentScope,
                        ),
                    shape = MaterialTheme.shapes.medium,
                    placeholderIcon = Icons.Filled.Album,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
