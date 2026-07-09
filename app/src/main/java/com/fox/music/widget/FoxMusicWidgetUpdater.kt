package com.fox.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.fox.music.MainActivity
import com.fox.music.R
import com.fox.music.core.common.R as CommonR
import com.fox.music.core.common.lyric.LyricColorPreferences
import com.fox.music.core.model.music.RepeatMode
import com.fox.music.core.ui.theme.FoxWidgetColors
import java.io.File

private fun FoxWidgetColors.withLyricColors(
    lyricColors: com.fox.music.core.common.lyric.LyricColors,
): FoxWidgetColors = copy(
    lyricMuted = lyricColors.textColor,
    lyricActive = lyricColors.highlightColor,
)

enum class WidgetSize {
    SMALL,
    MEDIUM,
    LARGE,
}

object FoxMusicWidgetUpdater {
    private val lyricLineIds = listOf(
        R.id.widget_lyric_line_0,
        R.id.widget_lyric_line_1,
        R.id.widget_lyric_line_2,
        R.id.widget_lyric_line_3,
        R.id.widget_lyric_line_4,
    )

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        updateInstalled(context, manager, WidgetSize.SMALL, FoxMusicWidgetSmallReceiver::class.java)
        updateInstalled(context, manager, WidgetSize.MEDIUM, FoxMusicWidgetReceiver::class.java)
        updateInstalled(context, manager, WidgetSize.LARGE, FoxMusicWidgetLargeReceiver::class.java)
    }

    fun updateWidgets(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        size: WidgetSize,
    ) {
        if (appWidgetIds.isEmpty()) return
        val state = WidgetStateStore.read(context)
        val layoutId = when (size) {
            WidgetSize.SMALL -> R.layout.widget_small
            WidgetSize.MEDIUM -> R.layout.widget_medium
            WidgetSize.LARGE -> R.layout.widget_large
        }
        appWidgetIds.forEach { widgetId ->
            val views = buildRemoteViews(context, layoutId, state, size)
            manager.updateAppWidget(widgetId, views)
        }
    }

    fun isAnyInstalled(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return WidgetSize.entries.any { size ->
            manager.getAppWidgetIds(componentName(context, size)).isNotEmpty()
        }
    }

    private fun updateInstalled(
        context: Context,
        manager: AppWidgetManager,
        size: WidgetSize,
        receiverClass: Class<*>,
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(context, receiverClass))
        updateWidgets(context, manager, ids, size)
    }

    private fun componentName(context: Context, size: WidgetSize): ComponentName =
        when (size) {
            WidgetSize.SMALL -> ComponentName(context, FoxMusicWidgetSmallReceiver::class.java)
            WidgetSize.MEDIUM -> ComponentName(context, FoxMusicWidgetReceiver::class.java)
            WidgetSize.LARGE -> ComponentName(context, FoxMusicWidgetLargeReceiver::class.java)
        }

    private fun buildRemoteViews(
        context: Context,
        layoutId: Int,
        state: WidgetPlaybackState,
        size: WidgetSize,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutId)
        val themeColors = WidgetThemeStore.read(context)
        val colors = themeColors.withLyricColors(LyricColorPreferences.read(context))
        bindTheme(views, themeColors)
        bindCover(views, state.coverLocalPath, themeColors)
        bindInfoSection(views, state, size, themeColors, colors)
        bindControls(context, views, state, size, themeColors)
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        return views
    }

    private fun bindTheme(
        views: RemoteViews,
        themeColors: FoxWidgetColors,
    ) {
        views.setImageViewBitmap(R.id.widget_background, createColorBitmap(themeColors.panelBackground))
    }

    private fun createColorBitmap(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }

    private fun bindInfoSection(
        views: RemoteViews,
        state: WidgetPlaybackState,
        size: WidgetSize,
        themeColors: FoxWidgetColors,
        lyricColors: FoxWidgetColors,
    ) {
        when (size) {
            WidgetSize.SMALL -> {
                views.setTextViewText(R.id.widget_title, state.title)
                views.setTextViewText(R.id.widget_artist, state.artist.ifBlank { "暂无播放" })
                views.setTextColor(R.id.widget_title, themeColors.titleText)
                views.setTextColor(R.id.widget_artist, themeColors.titleText)
            }
            WidgetSize.MEDIUM -> {
                views.setTextViewText(
                    R.id.widget_title_meta,
                    WidgetLyricHelper.titleMeta(state.title, state.artist),
                )
                views.setTextViewText(R.id.widget_current_lyric, state.currentLyric)
                views.setTextColor(R.id.widget_title_meta, themeColors.titleText)
                views.setTextColor(R.id.widget_current_lyric, lyricColors.lyricActive)
            }
            WidgetSize.LARGE -> {
                views.setTextViewText(R.id.widget_title_meta, state.title)
                views.setTextViewText(R.id.widget_artist, state.artist.ifBlank { "暂无播放" })
                views.setTextColor(R.id.widget_title_meta, themeColors.titleText)
                views.setTextColor(R.id.widget_artist, themeColors.titleText)
                val lyricWindow = WidgetLyricHelper.resolveLargeLyricWindow(state)
                lyricLineIds.forEachIndexed { index, viewId ->
                    val line = lyricWindow.lines.getOrNull(index).orEmpty()
                    if (line.isBlank()) {
                        views.setViewVisibility(viewId, View.GONE)
                    } else {
                        views.setViewVisibility(viewId, View.VISIBLE)
                        views.setTextViewText(viewId, line)
                        val isCurrent = index == lyricWindow.currentLineIndex
                        views.setTextColor(
                            viewId,
                            if (isCurrent) lyricColors.lyricActive else lyricColors.lyricMuted,
                        )
                        views.setTextViewTextSize(
                            viewId,
                            TypedValue.COMPLEX_UNIT_SP,
                            if (isCurrent) 15f else 13f,
                        )
                    }
                }
            }
        }
    }

    private fun bindControls(
        context: Context,
        views: RemoteViews,
        state: WidgetPlaybackState,
        size: WidgetSize,
        colors: FoxWidgetColors,
    ) {
        if (size != WidgetSize.SMALL) {
            views.setImageViewResource(R.id.widget_btn_repeat, repeatModeIcon(state.repeatMode))
            views.setImageColorFilter(R.id.widget_btn_repeat, colors.controlTint)
            views.setOnClickPendingIntent(
                R.id.widget_btn_repeat,
                WidgetActionReceiver.repeatPendingIntent(context),
            )
        }
        views.setOnClickPendingIntent(
            R.id.widget_btn_prev,
            WidgetActionReceiver.previousPendingIntent(context),
        )
        views.setImageColorFilter(R.id.widget_btn_prev, colors.controlTint)
        views.setImageViewResource(
            R.id.widget_btn_play,
            if (state.isPlaying) CommonR.drawable.iv_pause else CommonR.drawable.iv_play,
        )
        views.setImageColorFilter(R.id.widget_btn_play, colors.controlTint)
        views.setOnClickPendingIntent(
            R.id.widget_btn_play,
            WidgetActionReceiver.togglePendingIntent(context),
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_next,
            WidgetActionReceiver.nextPendingIntent(context),
        )
        views.setImageColorFilter(R.id.widget_btn_next, colors.controlTint)
        if (size != WidgetSize.SMALL) {
            views.setImageViewResource(
                R.id.widget_btn_favorite,
                if (state.isFavorite) {
                    R.drawable.widget_ic_favorite_filled
                } else {
                    R.drawable.widget_ic_favorite_outline
                },
            )
            views.setImageColorFilter(
                R.id.widget_btn_favorite,
                if (state.isFavorite) colors.favoriteTint else colors.controlTint,
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_favorite,
                WidgetActionReceiver.favoritePendingIntent(context),
            )
        }
    }

    private fun bindCover(views: RemoteViews, coverPath: String?, colors: FoxWidgetColors) {
        val bitmap = coverPath
            ?.let(::File)
            ?.takeIf { it.exists() && it.length() > 0L }
            ?.let { decodeScaledBitmap(it.absolutePath, 64) }
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widget_cover, bitmap)
        } else {
            views.setImageViewResource(R.id.widget_cover, R.drawable.widget_cover_placeholder)
            views.setInt(R.id.widget_cover, "setBackgroundColor", colors.placeholderBackground)
            views.setImageColorFilter(R.id.widget_cover, colors.placeholderText)
        }
    }

    private fun RemoteViews.setImageColorFilter(viewId: Int, color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setColorInt(viewId, "setColorFilter", color, PorterDuff.Mode.SRC_IN.ordinal)
        }
    }

    private fun repeatModeIcon(mode: RepeatMode): Int = when (mode) {
        RepeatMode.SEQUENTIAL -> CommonR.drawable.ic_sequential
        RepeatMode.ALL -> CommonR.drawable.ic_cycle
        RepeatMode.ONE -> CommonR.drawable.ic_single
        RepeatMode.RANDOM -> CommonR.drawable.ic_random
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun decodeScaledBitmap(path: String, maxSize: Int) =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                },
            )
        }.getOrNull()

    private fun calculateSampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > maxSize || height / sampleSize > maxSize) {
            sampleSize *= 2
        }
        return sampleSize
    }
}

object WidgetStateStore {
    fun read(context: Context): WidgetPlaybackState {
        val prefs = context.getSharedPreferences(WidgetStateKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val repeatMode = runCatching {
            RepeatMode.valueOf(
                prefs.getString(WidgetStateKeys.REPEAT_MODE, RepeatMode.ALL.name) ?: RepeatMode.ALL.name,
            )
        }.getOrDefault(RepeatMode.ALL)
        val largeLyricLines = List(5) { index ->
            prefs.getString("${WidgetStateKeys.LYRIC_LINE_PREFIX}$index", "").orEmpty()
        }
        return WidgetPlaybackState(
            title = prefs.getString(WidgetStateKeys.TITLE, "FoxMusic") ?: "FoxMusic",
            artist = prefs.getString(WidgetStateKeys.ARTIST, "暂无播放")?.ifBlank { "暂无播放" }
                ?: "暂无播放",
            coverUrl = prefs.getString(WidgetStateKeys.COVER_URL, null),
            coverLocalPath = prefs.getString(WidgetStateKeys.COVER_LOCAL_PATH, null)
                ?.takeIf { File(it).exists() },
            isPlaying = prefs.getBoolean(WidgetStateKeys.IS_PLAYING, false),
            currentLyric = prefs.getString(WidgetStateKeys.CURRENT_LYRIC, "暂无歌词") ?: "暂无歌词",
            lyricsRaw = prefs.getString(WidgetStateKeys.LYRICS_RAW, null),
            positionMs = prefs.getLong(WidgetStateKeys.POSITION_MS, 0L),
            largeLyricLines = largeLyricLines,
            largeLyricCurrentIndex = prefs.getInt(WidgetStateKeys.LYRIC_CURRENT_INDEX, -1),
            repeatMode = repeatMode,
            isFavorite = prefs.getBoolean(WidgetStateKeys.IS_FAVORITE, false),
            musicId = prefs.getLong(WidgetStateKeys.MUSIC_ID, -1L).takeIf { it >= 0L },
        )
    }

    fun write(
        context: Context,
        snapshot: WidgetPlaybackState,
        coverLocalPath: String?,
    ) {
        context.getSharedPreferences(WidgetStateKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(WidgetStateKeys.TITLE, snapshot.title.ifBlank { "FoxMusic" })
            .putString(WidgetStateKeys.ARTIST, snapshot.artist.ifBlank { "未知歌手" })
            .putBoolean(WidgetStateKeys.IS_PLAYING, snapshot.isPlaying)
            .putString(WidgetStateKeys.CURRENT_LYRIC, snapshot.currentLyric)
            .putLong(WidgetStateKeys.POSITION_MS, snapshot.positionMs.coerceAtLeast(0L))
            .putString(WidgetStateKeys.REPEAT_MODE, snapshot.repeatMode.name)
            .putBoolean(WidgetStateKeys.IS_FAVORITE, snapshot.isFavorite)
            .apply {
                if (snapshot.musicId != null) {
                    putLong(WidgetStateKeys.MUSIC_ID, snapshot.musicId)
                } else {
                    remove(WidgetStateKeys.MUSIC_ID)
                }
                if (snapshot.coverUrl != null) putString(WidgetStateKeys.COVER_URL, snapshot.coverUrl)
                else remove(WidgetStateKeys.COVER_URL)
                if (coverLocalPath != null) putString(WidgetStateKeys.COVER_LOCAL_PATH, coverLocalPath)
                else remove(WidgetStateKeys.COVER_LOCAL_PATH)
                if (snapshot.lyricsRaw != null) {
                    putString(WidgetStateKeys.LYRICS_RAW, snapshot.lyricsRaw)
                } else {
                    remove(WidgetStateKeys.LYRICS_RAW)
                }
                (0 until 5).forEach { index ->
                    putString(
                        "${WidgetStateKeys.LYRIC_LINE_PREFIX}$index",
                        snapshot.largeLyricLines.getOrNull(index).orEmpty(),
                    )
                }
                putInt(WidgetStateKeys.LYRIC_CURRENT_INDEX, snapshot.largeLyricCurrentIndex)
            }
            .apply()
    }
}
