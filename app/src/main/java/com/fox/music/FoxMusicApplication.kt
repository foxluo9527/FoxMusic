package com.fox.music

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.fox.music.core.common.EventViewModel
import com.fox.music.core.player.controller.MusicController
import com.fox.music.crash.BuglyInitializer
import com.fox.music.feature.player.lyric.manager.LyricSyncManager
import com.tencent.bugly.crashreport.CrashReport
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FoxMusicApplication : Application() {

    @Inject
    lateinit var musicController: MusicController

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        BuglyInitializer.init(this)
        LyricSyncManager.getInstance().init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                EventViewModel.appInForeground.value = true
            }

            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                EventViewModel.appInForeground.value = false
                musicController.flushPlaybackState()
            }
        })
    }
}
