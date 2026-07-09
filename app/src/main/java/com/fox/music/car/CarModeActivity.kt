package com.fox.music.car

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.component.CarModeScreen
import com.fox.music.core.ui.component.CarModeTab
import com.fox.music.core.ui.component.nextRepeatMode
import com.fox.music.core.ui.theme.FoxMusicTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CarModeActivity : ComponentActivity() {

    @Inject
    lateinit var musicController: MusicController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()
        setContent {
            val viewModel: CarModeViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsState()
            val playerState by musicController.playerState.collectAsState(PlayerState())
            val position by musicController.currentPosition.collectAsState(0L)
            FoxMusicTheme {
                CarModeScreen(
                    selectedTab = state.selectedTab,
                    recommendedPlaylists = state.recommendedPlaylists,
                    recommendedSongs = state.recommendedSongs,
                    hotKeywords = state.hotKeywords,
                    searchQuery = state.searchQuery,
                    searchResults = state.searchResults,
                    isSearching = state.isSearching,
                    playlistDetail = state.playlistDetail,
                    isPlaylistLoading = state.isPlaylistLoading,
                    isLoading = state.isLoading,
                    error = state.error,
                    playerState = playerState,
                    positionMs = position,
                    onTabSelected = { viewModel.sendIntent(CarModeIntent.SelectTab(it)) },
                    onPlaylistClick = { viewModel.sendIntent(CarModeIntent.SelectPlaylist(it)) },
                    onSongClick = { music, list ->
                        viewModel.sendIntent(CarModeIntent.PlaySong(music, list))
                    },
                    onSearchQueryChange = { viewModel.sendIntent(CarModeIntent.UpdateSearchQuery(it)) },
                    onSearchSubmit = { viewModel.sendIntent(CarModeIntent.SubmitSearch(it)) },
                    onHotKeywordClick = { viewModel.sendIntent(CarModeIntent.SelectHotKeyword(it)) },
                    onBackFromPlaylist = { viewModel.sendIntent(CarModeIntent.BackFromPlaylist) },
                    onExit = { finish() },
                    onRepeatClick = {
                        musicController.setRepeatMode(nextRepeatMode(playerState.repeatMode))
                    },
                    onPreviousClick = { musicController.previous() },
                    onTogglePlayClick = { musicController.togglePlay() },
                    onNextClick = { musicController.next() },
                    isPreviewMode = true,
                    modifier = Modifier,
                )
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, CarModeActivity::class.java)
    }
}
