package com.fox.music.feature.discover

import android.widget.Toast
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Music
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
    onMusicClick: (Music, List<Music>, String) -> Unit = { _, _, _ -> },
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

    LaunchedEffect(state.hotMusics, artist?.id) {
        if (state.hotMusics.isNotEmpty()) {
            updateMusicList(state.hotMusics, playlistKey)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ArtistDetailEffect.NavigateToMusic -> {
                    onMusicClick(effect.music, effect.musicList, playlistKey)
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
                    Text(
                        text = artist?.name ?: "歌手详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
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
                ArtistDetailContent(
                    artist = artist,
                    hotMusics = state.hotMusics,
                    albums = state.albums,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    onMusicClick = viewModel::onMusicClick,
                    onAlbumClick = viewModel::onAlbumClick,
                    onMusicMoreClick = onMusicMoreClick,
                    onArtistClick = onArtistClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun ArtistDetailContent(
    artist: Artist,
    hotMusics: List<Music>,
    albums: List<Album>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onMusicMoreClick: (Music) -> Unit,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            ArtistDetailHeader(artist = artist)
        }

        if (hotMusics.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "热门歌曲",
                    onMoreClick = null,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            items(hotMusics, key = { it.id }) { music ->
                MusicListItem(
                    music = music,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    modifier = Modifier.padding(horizontal = 8.dp),
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
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
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
    }
}

@Composable
private fun ArtistDetailHeader(
    artist: Artist,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.large),
        ) {
            CachedImage(
                imageUrl = artist.coverImage ?: artist.avatar,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholderIcon = Icons.Filled.Person,
            )
            CachedImage(
                imageUrl = artist.avatar,
                contentDescription = artist.name,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .size(80.dp)
                    .clip(CircleShape),
                shape = CircleShape,
                placeholderIcon = Icons.Filled.Person,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                modifier = Modifier.padding(top = 4.dp),
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
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        artist.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
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
