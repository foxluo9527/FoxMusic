package com.fox.music

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.fox.music.core.common.EventViewModel
import com.fox.music.feature.player.lyric.manager.LyricSyncManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FoxMusicApplication : Application(){
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        LyricSyncManager.getInstance().init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                EventViewModel.appInForeground.value = true
            }

            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                EventViewModel.appInForeground.value= false
            }
        })
    }
}
