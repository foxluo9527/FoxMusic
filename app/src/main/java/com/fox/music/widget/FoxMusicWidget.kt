package com.fox.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FoxMusicWidgetSmallReceiver : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        FoxMusicWidgetUpdater.updateWidgets(context, appWidgetManager, appWidgetIds, WidgetSize.SMALL)
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshScope.launch { WidgetBootstrapper.refreshIfInstalled(context) }
    }
}

class FoxMusicWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        FoxMusicWidgetUpdater.updateWidgets(context, appWidgetManager, appWidgetIds, WidgetSize.MEDIUM)
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshScope.launch { WidgetBootstrapper.refreshIfInstalled(context) }
    }
}

class FoxMusicWidgetLargeReceiver : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        FoxMusicWidgetUpdater.updateWidgets(context, appWidgetManager, appWidgetIds, WidgetSize.LARGE)
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshScope.launch { WidgetBootstrapper.refreshIfInstalled(context) }
    }
}

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> WidgetActionHandler.togglePlay(context)
            ACTION_PREVIOUS -> WidgetActionHandler.previous(context)
            ACTION_NEXT -> WidgetActionHandler.next(context)
            ACTION_REPEAT -> WidgetActionHandler.cycleRepeatMode(context)
            ACTION_FAVORITE -> WidgetRefreshScope.launch { WidgetActionHandler.toggleFavorite(context) }
        }
        if (intent.action != ACTION_FAVORITE) {
            WidgetRefreshScope.launch { WidgetActionHandler.refreshWidget(context) }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.fox.music.widget.TOGGLE"
        const val ACTION_PREVIOUS = "com.fox.music.widget.PREVIOUS"
        const val ACTION_NEXT = "com.fox.music.widget.NEXT"
        const val ACTION_REPEAT = "com.fox.music.widget.REPEAT"
        const val ACTION_FAVORITE = "com.fox.music.widget.FAVORITE"

        fun togglePendingIntent(context: Context): PendingIntent =
            actionPendingIntent(context, ACTION_TOGGLE, 1)

        fun previousPendingIntent(context: Context): PendingIntent =
            actionPendingIntent(context, ACTION_PREVIOUS, 2)

        fun nextPendingIntent(context: Context): PendingIntent =
            actionPendingIntent(context, ACTION_NEXT, 3)

        fun repeatPendingIntent(context: Context): PendingIntent =
            actionPendingIntent(context, ACTION_REPEAT, 4)

        fun favoritePendingIntent(context: Context): PendingIntent =
            actionPendingIntent(context, ACTION_FAVORITE, 5)

        private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

private val WidgetRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

typealias FoxMusicWidget = FoxMusicWidgetReceiver
