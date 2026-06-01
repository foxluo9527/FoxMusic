package com.fox.music.realtime

import android.content.Context
import com.fox.music.core.domain.repository.RealtimeConnectionLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeConnectionLauncherImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : RealtimeConnectionLauncher {

    override fun startConnectionService() {
        RealtimeConnectionService.start(context)
    }

    override fun stopConnectionService() {
        RealtimeConnectionService.stop(context)
    }
}
