package com.fox.music

import android.app.Application
import androidx.annotation.OptIn
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.work.Configuration
import com.fox.music.core.common.EventViewModel
import com.fox.music.core.player.controller.MusicController
import com.fox.music.crash.BuglyInitializer
import com.fox.music.core.data.recovery.ChatMessageRecovery
import com.fox.music.core.data.realtime.RealtimeMessageCoordinator
import com.fox.music.core.domain.repository.RecommendedMusicCacheManager
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.player.lyric.LyricPrefetchCoordinator
import com.fox.music.feature.player.lyric.manager.LyricSyncManager
import com.fox.music.lyric.UnlockLyricCoordinator
import com.fox.music.notification.FoxNotificationManager
import com.fox.music.core.common.lyric.LyricStyleChangeNotifier
import com.fox.music.widget.FoxMusicWidgetUpdater
import com.fox.music.widget.WidgetBootstrapper
import com.fox.music.widget.WidgetLyricHelper
import com.fox.music.widget.WidgetStateWriter
import com.fox.music.widget.WidgetThemeStore
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class FoxMusicApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var musicController: MusicController

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var chatMessageRecovery: ChatMessageRecovery

    @Inject
    lateinit var realtimeMessageCoordinator: RealtimeMessageCoordinator

    @Inject
    lateinit var foxNotificationManager: FoxNotificationManager

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var lyricPrefetchCoordinator: LyricPrefetchCoordinator

    @Inject
    lateinit var unlockLyricCoordinator: UnlockLyricCoordinator

    @Inject
    lateinit var recommendedMusicCacheManager: RecommendedMusicCacheManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        BuglyInitializer.init(this)
        LyricStyleChangeNotifier.onLyricStyleChanged = { context ->
            appScope.launch {
                if (FoxMusicWidgetUpdater.isAnyInstalled(context)) {
                    FoxMusicWidgetUpdater.updateAll(context)
                }
            }
        }
        LyricSyncManager.getInstance().init(this)
        lyricPrefetchCoordinator.start()
        unlockLyricCoordinator.start(this)
        appScope.launch {
            recommendedMusicCacheManager.prefetchIfNeeded()
        }
        syncDesktopLyricPreference()
        observeWidgetPlaybackState()
        observeWidgetTheme()
        appScope.launch {
            WidgetBootstrapper.refreshIfInstalled(this@FoxMusicApplication)
        }
        foxNotificationManager.createChannels()
        chatMessageRecovery.recoverPendingMessages()
        realtimeMessageCoordinator.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                EventViewModel.appInForeground.value = true
                realtimeMessageCoordinator.onAppForeground()
                appScope.launch {
                    recommendedMusicCacheManager.prefetchIfNeeded()
                    WidgetBootstrapper.refreshIfInstalled(this@FoxMusicApplication)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                EventViewModel.appInForeground.value = false
                realtimeMessageCoordinator.onAppBackground()
                musicController.flushPlaybackState()
                appScope.launch {
                    WidgetBootstrapper.refreshIfInstalled(this@FoxMusicApplication)
                }
            }
        })
    }

    private fun syncDesktopLyricPreference() {
        appScope.launch {
            val prefs = userPreferencesRepository.userPreferences.first()
            LyricSyncManager.getInstance().syncDesktopLyricEnabled(prefs.desktopLyricEnabled)
        }
    }

    private fun observeWidgetTheme() {
        appScope.launch {
            userPreferencesRepository.userPreferences
                .distinctUntilChanged { old, new ->
                    old.appThemeId == new.appThemeId &&
                        old.customThemeSeedArgb == new.customThemeSeedArgb &&
                        old.darkMode == new.darkMode
                }
                .collect { prefs ->
                    val isSystemDark = (
                        resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                        ) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    WidgetThemeStore.sync(this@FoxMusicApplication, prefs, isSystemDark)
                    if (FoxMusicWidgetUpdater.isAnyInstalled(this@FoxMusicApplication)) {
                        FoxMusicWidgetUpdater.updateAll(this@FoxMusicApplication)
                    }
                }
        }
    }

    private fun observeWidgetPlaybackState() {
        appScope.launch {
            combine(
                musicController.playerState,
                musicController.currentPosition,
            ) { state, position -> state to position }
                .map { (state, position) -> WidgetLyricHelper.buildSnapshot(state, position) }
                .distinctUntilChanged { old, new ->
                    old?.musicId == new?.musicId &&
                        old?.title == new?.title &&
                        old?.artist == new?.artist &&
                        old?.isPlaying == new?.isPlaying &&
                        old?.currentLyric == new?.currentLyric &&
                        old?.lyricsRaw == new?.lyricsRaw &&
                        old?.largeLyricLines == new?.largeLyricLines &&
                        old?.largeLyricCurrentIndex == new?.largeLyricCurrentIndex &&
                        old?.repeatMode == new?.repeatMode &&
                        old?.isFavorite == new?.isFavorite &&
                        old?.coverUrl == new?.coverUrl
                }
                .collect { snapshot ->
                    snapshot ?: return@collect
                    WidgetStateWriter.write(
                        context = this@FoxMusicApplication,
                        snapshot = snapshot,
                    )
                }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        val imageCacheDir = File(cacheDir, "image_cache")

        val okHttpClient = OkHttpClient.Builder()
            .cache(Cache(imageCacheDir.resolve("http_cache"), 2L * 1024 * 1024 * 1024))
            .addInterceptor(ForceCacheInterceptor())
            .build()

        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDir.resolve("coil_cache"))
                    .maxSizeBytes(2L * 1024 * 1024 * 1024) // 2GB
                    .build()
            }
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()
    }
}

/**
 * 强制缓存拦截器：覆盖服务器返回的禁止缓存头，确保图片能被缓存
 */
private class ForceCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val cacheControl = CacheControl.Builder()
            .maxAge(30, TimeUnit.DAYS)
            .build()
        return response.newBuilder()
            .removeHeader("Pragma")
            .removeHeader("Cache-Control")
            .header("Cache-Control", cacheControl.toString())
            .build()
    }
}
