package com.fox.music.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.fox.music.feature.discover.ALL_ARTIST_LIST_ROUTE
import com.fox.music.feature.discover.ARTIST_DETAIL_ROUTE
import com.fox.music.feature.discover.ArtistDetailScreen
import com.fox.music.feature.discover.ArtistListScreen
import com.fox.music.feature.discover.HOT_ARTIST_LIST_ROUTE
import com.fox.music.feature.discover.HotArtistListScreen
import com.fox.music.feature.playlist.ui.component.PLAYLIST_LIST_ROUTE
import com.fox.music.feature.playlist.ui.component.PlaylistDetailScreen
import com.fox.music.feature.playlist.ui.component.PlaylistListScreen
import com.fox.music.feature.playlist.ui.screen.ALBUM_LIST_ROUTE
import com.fox.music.feature.playlist.ui.screen.AlbumListScreen
import com.fox.music.feature.playlist.ui.screen.PLAYLIST_EDIT_ROUTE
import com.fox.music.feature.playlist.ui.screen.PlaylistEditScreen
import com.fox.music.feature.playlist.ui.screen.playlistEditRoute
import com.fox.music.feature.search.SEARCH_ROUTE
import com.fox.music.feature.search.SearchScreen
import com.fox.music.core.model.music.DetailType

fun NavGraphBuilder.musicNavGraph(
    context: NavGraphContext,
    sharedTransitionScope: SharedTransitionScope,
) {
    val navController = context.navController
    val musicController = context.musicController
    val playerState = context.playerState
    val showBottomBar = context.showBottomBar

    composableStack(SEARCH_ROUTE) {
        val animatedScope = this
        with(sharedTransitionScope) {
            MainScreenWithBottomBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                showMiniPlayer = showBottomBar,
                sharedTransitionScope = this@with,
            ) {
                SearchScreen(
                    modifier = it,
                    sharedTransitionScope = this@with,
                    animatedContentScope = animatedScope,
                    onMusicClick = context.onMusicClick,
                    updateMusicList = context.onUpdateMusicList,
                    onArtistClick = context.onArtistClick,
                    onAlbumClick = context.onAlbumClick,
                    onMusicMoreClick = context.onMusicMoreClick,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

    composableStack(PLAYLIST_LIST_ROUTE) {
        val animatedScope = this
        with(sharedTransitionScope) {
            PlaylistListScreen(
                modifier = Modifier.fillMaxSize(),
                sharedTransitionScope = this@with,
                animatedContentScope = animatedScope,
                onPlaylistClick = context.onPlaylistClick,
                onBackClick = { navController.popBackStack() }
            )
        }
    }

    composableStack(ALBUM_LIST_ROUTE) {
        val animatedScope = this
        with(sharedTransitionScope) {
            AlbumListScreen(
                modifier = Modifier.fillMaxSize(),
                sharedTransitionScope = this@with,
                animatedContentScope = animatedScope,
                onAlbumClick = context.onAlbumClick,
                onBackClick = { navController.popBackStack() }
            )
        }
    }

    composableStack(HOT_ARTIST_LIST_ROUTE) {
        HotArtistListScreen(
            modifier = Modifier.fillMaxSize(),
            onArtistClick = context.onArtistClick,
            onAllArtistsClick = {
                navController.navigate(ALL_ARTIST_LIST_ROUTE)
            },
            onBackClick = { navController.popBackStack() },
        )
    }

    composableStack(ALL_ARTIST_LIST_ROUTE) {
        ArtistListScreen(
            modifier = Modifier.fillMaxSize(),
            onArtistClick = context.onArtistClick,
            onBackClick = { navController.popBackStack() },
        )
    }

    composableStack(
        route = ARTIST_DETAIL_ROUTE,
        arguments = listOf(
            navArgument("artistId") { type = NavType.LongType },
        ),
    ) {
        val animatedScope = this
        with(sharedTransitionScope) {
            MainScreenWithBottomBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                showMiniPlayer = showBottomBar,
                sharedTransitionScope = this@with,
            ) {
                ArtistDetailScreen(
                    modifier = it,
                    sharedTransitionScope = this@with,
                    animatedContentScope = animatedScope,
                    onMusicClick = context.onMusicClick,
                    updateMusicList = context.onUpdateMusicList,
                    onAlbumClick = context.onAlbumClick,
                    onBack = { navController.popBackStack() },
                    onMusicMoreClick = context.onMusicMoreClick,
                    onArtistClick = context.onArtistClick,
                )
            }
        }
    }

    composableStack(
        route = "playlist_detail/{playlistId}/{type}",
        arguments = listOf(
            navArgument("playlistId") { type = NavType.StringType },
            navArgument("type") {
                type = NavType.StringType
                defaultValue = DetailType.PLAYLIST.name
            }
        ),
    ) {
        val animatedScope = this
        with(sharedTransitionScope) {
            MainScreenWithBottomBar(
                navController = navController,
                musicController = musicController,
                playerState = playerState,
                showMiniPlayer = showBottomBar && !context.hideMiniPlayerInSelection,
                sharedTransitionScope = this@with,
            ) {
                PlaylistDetailScreen(
                    it,
                    sharedTransitionScope = this@with,
                    animatedContentScope = animatedScope,
                    onMusicClick = context.onMusicClick,
                    updateMusicList = context.onUpdateMusicList,
                    onBack = { navController.popBackStack() },
                    onPlaylistDeleted = context.onProfileRefreshKeyIncrement,
                    onEditPlaylist = { id ->
                        navController.navigate(playlistEditRoute(id))
                    },
                    onMusicMoreClick = context.onMusicMoreClick,
                    onArtistClick = context.onArtistClick,
                    onAddSelectedToQueue = { musics ->
                        context.musicActionsViewModel.addAllToQueue(musics)
                    },
                    onAddSelectedToPlaylist = { musicIds ->
                        context.musicActionsViewModel.showAddToPlaylist(musicIds)
                    },
                    onDownloadSelected = { musics ->
                        context.musicActionsViewModel.downloadMusics(musics)
                    },
                    onSelectionModeChanged = context.onHideMiniPlayerInSelectionChange,
                )
            }
        }
    }

    composableStack(
        route = PLAYLIST_EDIT_ROUTE,
        arguments = listOf(
            navArgument("playlistId") { type = NavType.StringType },
        ),
    ) {
        PlaylistEditScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onSaved = context.onProfileRefreshKeyIncrement,
        )
    }
}
