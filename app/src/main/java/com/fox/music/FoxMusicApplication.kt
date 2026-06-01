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
import com.fox.music.feature.player.lyric.manager.LyricSyncManager
import com.fox.music.notification.FoxNotificationManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FoxMusicApplication : Application(), Configuration.Provider {

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

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        BuglyInitializer.init(this)
        LyricSyncManager.getInstance().init(this)
        foxNotificationManager.createChannels()
        chatMessageRecovery.recoverPendingMessages()
        realtimeMessageCoordinator.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                EventViewModel.appInForeground.value = true
                realtimeMessageCoordinator.onAppForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                EventViewModel.appInForeground.value = false
                realtimeMessageCoordinator.onAppBackground()
                musicController.flushPlaybackState()
            }
        })
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
