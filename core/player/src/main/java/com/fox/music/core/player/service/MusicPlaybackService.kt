package com.fox.music.core.player.service

import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.fox.music.core.datastore.FoxPreferencesDataStore
import com.fox.music.core.player.cache.PlaybackCacheManager
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.player.lyric.CarBluetoothLyricMetadataCoordinator
import com.fox.music.core.player.datasource.RoutingDataSourceFactory
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@UnstableApi
@AndroidEntryPoint
class MusicPlaybackService : MediaLibraryService() {

    @Inject
    lateinit var musicController: MusicController

    @Inject
    lateinit var playbackCacheManager: PlaybackCacheManager

    @Inject
    lateinit var preferencesDataStore: FoxPreferencesDataStore

    @Inject
    lateinit var playbackMediaLibraryCallback: PlaybackMediaLibraryCallback

    @Inject
    lateinit var carBluetoothLyricMetadataCoordinator: CarBluetoothLyricMetadataCoordinator

    private var mediaLibrarySession: MediaLibrarySession? = null

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

        carBluetoothLyricMetadataCoordinator.attach(player)

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            player,
            playbackMediaLibraryCallback,
        ).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        val hadSession = mediaLibrarySession != null
        carBluetoothLyricMetadataCoordinator.detach()
        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
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
