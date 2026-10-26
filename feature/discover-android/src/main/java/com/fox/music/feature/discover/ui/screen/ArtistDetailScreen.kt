package com.fox.music.feature.discover

import android.widget.Toast
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.PlaybackSourceKind

/**
 * 歌手详情页 Android 外壳。
 *
 * Toast 只能在这里弹（LocalContext 是 Android 专属），commonMain 的 [ArtistDetailContent]
 * 只把 ShowToast 当一条 effect 吐出来。
 */
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
    onViewAllArtistSongs: (Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val artist = state.artist
    val playlistKey = "artist_detail/${artist?.id ?: 0}"

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ArtistDetailEffect.NavigateToMusic -> {
                    onMusicClick(
                        effect.music,
                        effect.musicList,
                        playlistKey,
                        PlaybackPlaylistSource(
                            PlaybackSourceKind.ARTIST,
                            id = artist?.id ?: 0,
                            title = artist?.name,
                        ),
                    )
                }

                is ArtistDetailEffect.NavigateToAlbum -> onAlbumClick(effect.album)
                is ArtistDetailEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ArtistDetailContent(
        state = state,
        onIntent = viewModel::sendIntent,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        modifier = modifier,
        onMusicClick = viewModel::onMusicClick,
        onAlbumClick = viewModel::onAlbumClick,
        onBack = onBack,
        onMusicMoreClick = onMusicMoreClick,
        updateMusicList = updateMusicList,
        onViewAllArtistSongs = onViewAllArtistSongs,
    )
}
