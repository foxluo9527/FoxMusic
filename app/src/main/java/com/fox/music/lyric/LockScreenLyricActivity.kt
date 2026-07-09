package com.fox.music.lyric

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.fox.music.MainActivity
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.domain.usecase.ToggleMusicFavoriteUseCase
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.user.UserPreferences
import com.fox.music.core.ui.theme.resolveDarkTheme
import com.fox.music.core.network.token.TokenManager
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.component.LockScreenLyricScreen
import com.fox.music.core.ui.component.nextRepeatMode
import com.fox.music.core.ui.theme.FoxMusicTheme
import com.fox.music.feature.auth.ui.screen.LOGIN_ROUTE
import com.fox.music.notification.FoxNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LockScreenLyricActivity : ComponentActivity() {

    @Inject
    lateinit var musicController: MusicController

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var toggleMusicFavoriteUseCase: ToggleMusicFavoriteUseCase

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        setContent {
            val playerState by musicController.playerState.collectAsState(PlayerState())
            val position by musicController.currentPosition.collectAsState(0L)
            val preferences by userPreferencesRepository.userPreferences
                .collectAsState(initial = UserPreferences())
            val darkTheme = preferences.resolveDarkTheme(isSystemInDarkTheme())
            LaunchedEffect(Unit) {
                delay(AUTO_DISMISS_MS)
                finish()
            }
            FoxMusicTheme(
                darkTheme = darkTheme,
                appThemeId = preferences.appThemeId,
                customThemeSeedArgb = preferences.customThemeSeedArgb,
            ) {
                LockScreenLyricScreen(
                    playerState = playerState,
                    positionMs = position,
                    onDismiss = { finish() },
                    onRepeatClick = {
                        val nextMode = nextRepeatMode(playerState.repeatMode)
                        musicController.setRepeatMode(nextMode)
                    },
                    onPreviousClick = { musicController.previous() },
                    onTogglePlayClick = { musicController.togglePlay() },
                    onNextClick = { musicController.next() },
                    onFavoriteClick = { handleFavoriteClick(playerState) },
                    enableSwipeToDismiss = true,
                    showSwipeHint = true,
                )
            }
        }
    }

    private fun handleFavoriteClick(playerState: PlayerState) {
        lifecycleScope.launch {
            if (!tokenManager.isLoggedIn.first()) {
                startActivity(
                    Intent(this@LockScreenLyricActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(FoxNotificationManager.EXTRA_START_ROUTE, LOGIN_ROUTE)
                    },
                )
                finish()
                return@launch
            }
            val music = playerState.currentMusic ?: return@launch
            val wasFavorite = playerState.isFavorite || music.isFavorite
            toggleMusicFavoriteUseCase(music.id)
                .onSuccess {
                    musicController.updateCurrentMusicFavorite(!wasFavorite)
                }
        }
    }

    companion object {
        const val EXTRA_MUSIC_ID = "extra_music_id"
        private const val AUTO_DISMISS_MS = 30_000L

        fun intent(context: Context): Intent =
            Intent(context, LockScreenLyricActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
    }
}
