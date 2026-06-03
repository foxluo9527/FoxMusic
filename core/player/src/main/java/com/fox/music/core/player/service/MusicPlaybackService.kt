package com.fox.music.core.player.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.fox.music.core.datastore.FoxPreferencesDataStore
import com.fox.music.core.player.cache.PlaybackCacheManager
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.player.datasource.RoutingDataSourceFactory
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@UnstableApi
@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    @Inject
    lateinit var musicController: MusicController

    @Inject
    lateinit var playbackCacheManager: PlaybackCacheManager

    @Inject
    lateinit var preferencesDataStore: FoxPreferencesDataStore

    private var mediaSession: MediaSession? = null

    val cache: SimpleCache by lazy {
        val cacheDir = File(cacheDir, PlaybackCacheManager.CACHE_DIR_NAME)
        val maxBytes = runBlocking { preferencesDataStore.getCacheMaxBytes() }
        val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
        val databaseProvider: DatabaseProvider = ExoDatabaseProvider(this)
        SimpleCache(cacheDir, evictor, databaseProvider).also {
            playbackCacheManager.attachCache(it)
        }
    }

    private val playbackDataSourceFactory by lazy {
        val upstreamFactory = DefaultDataSource.Factory(
            this,
            DefaultHttpDataSource.Factory().setUserAgent("FoxMusic"),
        )
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        RoutingDataSourceFactory(
            localFactory = upstreamFactory,
            remoteFactory = cacheDataSourceFactory,
        )
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(playbackDataSourceFactory))
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also {
                it.repeatMode = Player.REPEAT_MODE_OFF
                it.shuffleModeEnabled = false
            }

        val player = PlaylistForwardingPlayer(exoPlayer, musicController)

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(PlaybackMediaSessionCallback())
            .build()
    }

    /**
     * MediaController 传入的 MediaItem 在跨进程后不含 [MediaItem.localConfiguration]，
     * 需在 Session 侧根据 [MediaItem.RequestMetadata.mediaUri] 还原可播放 URI。
     */
    private inner class PlaybackMediaSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<List<MediaItem>> =
            Futures.immediateFuture(mediaItems.map(::toPlayableMediaItem))

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val playableItems = mediaItems.map(::toPlayableMediaItem)
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    playableItems,
                    startIndex,
                    startPositionMs,
                ),
            )
        }

        private fun toPlayableMediaItem(mediaItem: MediaItem): MediaItem {
            val uri = mediaItem.localConfiguration?.uri ?: mediaItem.requestMetadata.mediaUri
                ?: return mediaItem
            return mediaItem.buildUpon().setUri(uri).build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        val hadSession = mediaSession != null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        if (hadSession) {
            playbackCacheManager.detachCache()
            cache.release()
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Music Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Controls for music playback"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "fox_music_playback_channel"
        const val NOTIFICATION_ID = 1001
    }
}
