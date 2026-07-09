package com.fox.music.widget

import android.content.Context
import android.content.Intent
import com.fox.music.MainActivity
import com.fox.music.core.domain.usecase.ToggleMusicFavoriteUseCase
import com.fox.music.core.model.music.RepeatMode
import com.fox.music.core.network.token.TokenManager
import com.fox.music.core.player.controller.MusicController
import com.fox.music.feature.auth.ui.screen.LOGIN_ROUTE
import com.fox.music.notification.FoxNotificationManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

object WidgetActionHandler {
    fun togglePlay(context: Context) {
        musicController(context).togglePlay()
    }

    fun previous(context: Context) {
        musicController(context).previous()
    }

    fun next(context: Context) {
        musicController(context).next()
    }

    fun cycleRepeatMode(context: Context) {
        val controller = musicController(context)
        val nextMode = when (controller.playerState.value.repeatMode) {
            RepeatMode.SEQUENTIAL -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.RANDOM
            RepeatMode.RANDOM -> RepeatMode.SEQUENTIAL
        }
        controller.setRepeatMode(nextMode)
    }

    suspend fun toggleFavorite(context: Context) {
        if (!entryPoint(context).tokenManager().isLoggedIn.first()) {
            openLogin(context)
            return
        }
        val controller = musicController(context)
        val music = controller.playerState.value.currentMusic ?: return
        val wasFavorite = controller.playerState.value.let { state ->
            state.currentMusic?.takeIf { it.id == music.id }?.isFavorite ?: state.isFavorite
        }
        entryPoint(context).toggleMusicFavoriteUseCase().invoke(music.id)
            .onSuccess {
                controller.updateCurrentMusicFavorite(!wasFavorite)
                refreshWidget(context)
            }
    }

    suspend fun refreshWidget(context: Context) {
        val controller = musicController(context)
        val state = controller.playerState.value
        val position = controller.currentPosition.value
        val snapshot = WidgetLyricHelper.buildSnapshot(state, position) ?: return
        WidgetStateWriter.write(context, snapshot)
    }

    fun openLogin(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(FoxNotificationManager.EXTRA_START_ROUTE, LOGIN_ROUTE)
        }
        context.startActivity(intent)
    }

    private fun musicController(context: Context): MusicController =
        entryPoint(context).musicController()

    private fun entryPoint(context: Context): WidgetMusicEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetMusicEntryPoint::class.java,
        )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetMusicEntryPoint {
    fun musicController(): MusicController
    fun tokenManager(): TokenManager
    fun toggleMusicFavoriteUseCase(): ToggleMusicFavoriteUseCase
}
