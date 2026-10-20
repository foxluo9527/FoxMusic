package com.fox.music.feature.profile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.ui.component.AlbumListItem
import com.fox.music.core.ui.component.ArtistListItem
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator

/** 收藏详情页类型。 */
enum class FavoriteCollectionType { PLAYLISTS, ALBUMS, ARTISTS }

/**
 * 收藏详情页（共享）：通用「返回 + 标题」顶部栏，下方复用「我的」页的收藏列表项样式。
 */
@Composable
fun FavoriteCollectionDetailContent(
    title: String,
    type: FavoriteCollectionType,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    playlists: List<Playlist> = emptyList(),
    albums: List<Album> = emptyList(),
    artists: List<Artist> = emptyList(),
    isLoading: Boolean = false,
    error: String? = null,
    onPlaylistClick: (Playlist) -> Unit = {},
    onAlbumClick: (Long) -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        FavoriteDetailTopBar(title = title, onBack = onBack)

        val isEmpty = when (type) {
            FavoriteCollectionType.PLAYLISTS -> playlists.isEmpty()
            FavoriteCollectionType.ALBUMS -> albums.isEmpty()
            FavoriteCollectionType.ARTISTS -> artists.isEmpty()
        }

        when {
            isLoading && isEmpty -> LoadingIndicator(modifier = Modifier.fillMaxSize(), useLottie = false)
            error != null && isEmpty -> ErrorView(modifier = Modifier.fillMaxSize(), message = error)
            isEmpty -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyMessage(type),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 24.dp,
                    ),
                ) {
                    when (type) {
                        FavoriteCollectionType.PLAYLISTS -> items(playlists, key = { it.id }) { playlist ->
                            ProfilePlaylistListItem(
                                title = playlist.title,
                                trackCount = playlist.trackCount,
                                coverUrl = playlist.coverImage,
                                showMoreButton = false,
                                placeholderIcon = Icons.AutoMirrored.Filled.QueueMusic,
                                onClick = { onPlaylistClick(playlist) },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
                            )
                        }

                        FavoriteCollectionType.ALBUMS -> items(albums, key = { it.id }) { album ->
                            AlbumListItem(
                                album = album,
                                onClick = { onAlbumClick(album.id) },
                            )
                        }

                        FavoriteCollectionType.ARTISTS -> items(artists, key = { it.id }) { artist ->
                            ArtistListItem(
                                artist = artist,
                                onClick = { onArtistClick(artist.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteDetailTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.width(40.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

private fun emptyMessage(type: FavoriteCollectionType): String = when (type) {
    FavoriteCollectionType.PLAYLISTS -> "暂无收藏歌单"
    FavoriteCollectionType.ALBUMS -> "暂无收藏专辑"
    FavoriteCollectionType.ARTISTS -> "暂无收藏艺术家"
}
