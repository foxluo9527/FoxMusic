package com.fox.music.widget

import android.content.Context
import android.content.res.Configuration
import com.fox.music.core.common.player.PlaybackStateStore
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.player.controller.MusicController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

object WidgetBootstrapper {
    suspend fun refreshFromPlaybackStore(context: Context, triggerUpdate: Boolean = true) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetBootstrapEntryPoint::class.java,
        )

        val controller = runCatching { entryPoint.musicController() }.getOrNull()
        val liveMusic = controller?.playerState?.value?.currentMusic
        val snapshot = entryPoint.playbackStateStore().loadSnapshot()
        if (controller != null && liveMusic != null) {
            val state = controller.playerState.value
            val position = controller.currentPosition.value
            WidgetLyricHelper.buildSnapshot(state, position)?.let { widgetSnapshot ->
                WidgetStateWriter.persistState(
                    context = context,
                    snapshot = widgetSnapshot.copy(coverUrl = liveMusic.coverImage),
                    triggerUpdate = triggerUpdate,
                )
            }
            return
        }

        val cachedSnapshot = snapshot ?: return
        val music = cachedSnapshot.playlist.getOrNull(cachedSnapshot.currentIndex) ?: return
        val lyricWindow = WidgetLyricHelper.largeLyricWindow(music, 0L)
        val bootstrapState = WidgetPlaybackState(
            title = music.title,
            artist = music.artists.joinToString { it.name },
            coverUrl = music.coverImage,
            isPlaying = cachedSnapshot.playingKey != null,
            currentLyric = music.getCurrentLyric(0L) ?: "暂无歌词",
            lyricsRaw = music.lyrics,
            positionMs = cachedSnapshot.positionMs.coerceAtLeast(0L),
            largeLyricLines = lyricWindow.lines,
            largeLyricCurrentIndex = lyricWindow.currentLineIndex,
            isFavorite = music.isFavorite,
            musicId = music.id,
        )
        WidgetStateWriter.persistState(
            context = context,
            snapshot = bootstrapState,
            triggerUpdate = triggerUpdate,
        )
    }

    suspend fun refreshIfInstalled(context: Context) {
        if (!FoxMusicWidgetUpdater.isAnyInstalled(context)) return
        syncTheme(context)
        refreshFromPlaybackStore(context)
    }

    private fun syncTheme(context: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetBootstrapEntryPoint::class.java,
        )
        val prefs = runBlocking {
            entryPoint.userPreferencesRepository().userPreferences.first()
        }
        val isSystemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WidgetThemeStore.sync(context, prefs, isSystemDark)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetBootstrapEntryPoint {
    fun playbackStateStore(): PlaybackStateStore
    fun musicController(): MusicController
    fun userPreferencesRepository(): UserPreferencesRepository
}
