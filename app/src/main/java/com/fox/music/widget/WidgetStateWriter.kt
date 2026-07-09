package com.fox.music.widget

import android.content.Context
import com.fox.music.core.model.music.RepeatMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WidgetStateWriter {
    private val persistMutex = Mutex()

    suspend fun write(context: Context, snapshot: WidgetPlaybackState) {
        persistState(context, snapshot, triggerUpdate = true)
    }

    suspend fun persistState(
        context: Context,
        snapshot: WidgetPlaybackState,
        triggerUpdate: Boolean,
    ) {
        persistMutex.withLock {
            if (!FoxMusicWidgetUpdater.isAnyInstalled(context)) return@withLock

            val coverLocalPath = WidgetCoverCache.resolveLocalPath(context, snapshot.coverUrl)
            WidgetStateStore.write(
                context = context,
                snapshot = snapshot,
                coverLocalPath = coverLocalPath,
            )
            if (triggerUpdate) {
                FoxMusicWidgetUpdater.updateAll(context)
            }
        }
    }

    suspend fun hydrateFromSnapshot(context: Context, snapshot: WidgetPlaybackState) {
        write(context, snapshot)
    }
}
