package com.fox.music.feature.player.lyric.manager

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.blankj.utilcode.util.Utils
import com.fox.music.feature.player.lyric.data.LyricStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LyricStyleManager private constructor() {

    companion object {
        private const val PREFS_NAME = "lyric_style_prefs"

        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_TEXT_COLOR = "text_color"
        private const val KEY_HIGHLIGHT_COLOR = "highlight_color"
        private const val KEY_BACKGROUND_COLOR = "background_color"
        private const val KEY_IS_BOLD = "is_bold"
        private const val KEY_ALIGNMENT = "alignment"
        private const val KEY_SCROLL_ENABLED = "scroll_enabled"
        private const val KEY_FADE_DURATION = "fade_duration"
        private const val KEY_LINE_SPACING = "line_spacing"

        private const val DEFAULT_FONT_SIZE = 18
        private const val DEFAULT_TEXT_COLOR = Color.WHITE
        private val DEFAULT_HIGHLIGHT_COLOR = "#FF6B6B".toColorInt()
        private val DEFAULT_BACKGROUND_COLOR = "#99000000".toColorInt()

        @Volatile
        private var instance: LyricStyleManager? = null

        fun getInstance(): LyricStyleManager {
            return instance ?: synchronized(this) {
                instance ?: LyricStyleManager().also { instance = it }
            }
        }

        fun getDefaultStyle(): LyricStyle = LyricStyle()
    }

    private val prefs: SharedPreferences by lazy {
        Utils.getApp().getSharedPreferences("LyricSettings", Context.MODE_PRIVATE)
    }

    private val _styleFlow by lazy {
        MutableStateFlow(loadStyle())
    }
    val styleFlow: StateFlow<LyricStyle> by lazy {
        _styleFlow.asStateFlow()
    }

    val currentStyle: LyricStyle
        get() = _styleFlow.value

    private fun loadStyle(): LyricStyle {
        return LyricStyle(
            fontSize = prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE),
            textColor = prefs.getInt(KEY_TEXT_COLOR, DEFAULT_TEXT_COLOR),
            highlightColor = prefs.getInt(KEY_HIGHLIGHT_COLOR, DEFAULT_HIGHLIGHT_COLOR),
            backgroundColor = prefs.getInt(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR),
            isBold = prefs.getBoolean(KEY_IS_BOLD, false),
            alignment = prefs.getInt(KEY_ALIGNMENT, Gravity.CENTER),
            isScrollEnabled = prefs.getBoolean(KEY_SCROLL_ENABLED, true),
            fadeDuration = prefs.getInt(KEY_FADE_DURATION, 300),
            lineSpacing = prefs.getFloat(KEY_LINE_SPACING, 1.5f)
        )
    }

    fun updateStyle(style: LyricStyle, keepFontSize: Boolean = false) {
        if (keepFontSize) {
            updateStyle(style.copy(fontSize = _styleFlow.value.fontSize))
        } else {
            prefs.edit {
                putInt(KEY_FONT_SIZE, style.fontSize)
                putInt(KEY_TEXT_COLOR, style.textColor)
                putInt(KEY_HIGHLIGHT_COLOR, style.highlightColor)
                putInt(KEY_BACKGROUND_COLOR, style.backgroundColor)
                putBoolean(KEY_IS_BOLD, style.isBold)
                putInt(KEY_ALIGNMENT, style.alignment)
                putBoolean(KEY_SCROLL_ENABLED, style.isScrollEnabled)
                putInt(KEY_FADE_DURATION, style.fadeDuration)
                putFloat(KEY_LINE_SPACING, style.lineSpacing)
            }
            _styleFlow.value = style
        }
    }

    fun setFontSize(size: Int) {
        updateStyle(currentStyle.copy(fontSize = size.coerceIn(10, 48)))
    }

    fun setTextColor(color: Int) {
        updateStyle(currentStyle.copy(textColor = color))
    }

    fun setHighlightColor(color: Int) {
        updateStyle(currentStyle.copy(highlightColor = color))
    }

    fun setBackgroundColor(color: Int) {
        updateStyle(currentStyle.copy(backgroundColor = color))
    }

    fun setBold(isBold: Boolean) {
        updateStyle(currentStyle.copy(isBold = isBold))
    }

    fun setAlignment(alignment: Int) {
        updateStyle(currentStyle.copy(alignment = alignment))
    }

    fun setScrollEnabled(enabled: Boolean) {
        updateStyle(currentStyle.copy(isScrollEnabled = enabled))
    }

    fun setFadeDuration(duration: Int) {
        updateStyle(currentStyle.copy(fadeDuration = duration.coerceIn(0, 1000)))
    }

    fun setLineSpacing(spacing: Float) {
        updateStyle(currentStyle.copy(lineSpacing = spacing.coerceIn(1.0f, 3.0f)))
    }

    fun resetToDefault() {
        updateStyle(getDefaultStyle())
    }

    fun applyPreset(preset: LyricPreset) {
        updateStyle(preset.style)
    }

    fun getFontTypeface(context: Context): Typeface {
        return if (currentStyle.isBold) {
            Typeface.DEFAULT_BOLD
        } else {
            Typeface.DEFAULT
        }
    }

    enum class LyricPreset(val displayName: String, val style: LyricStyle) {
        CLASSIC("经典", LyricStyle(
            fontSize = 18,
            textColor = Color.WHITE,
            highlightColor = "#FF6B6B".toColorInt(),
            backgroundColor = "#99000000".toColorInt()
        )),
        NEON("霓虹", LyricStyle(
            fontSize = 18,
            textColor = "#00FFFF".toColorInt(),
            highlightColor = "#FF00FF".toColorInt(),
            backgroundColor = "#CC000000".toColorInt()
        )),
        WARM("暖阳", LyricStyle(
            fontSize = 18,
            textColor = "#FFF8DC".toColorInt(),
            highlightColor = "#FFA500".toColorInt(),
            backgroundColor = "#B0843C30".toColorInt()
        )),
        COOL("清凉", LyricStyle(
            fontSize = 18,
            textColor = "#E0FFFF".toColorInt(),
            highlightColor = "#00CED1".toColorInt(),
            backgroundColor = "#B03A4A4E".toColorInt()
        )),
        DARK("暗黑", LyricStyle(
            fontSize = 18,
            textColor = "#CCCCCC".toColorInt(),
            highlightColor = "#FFFFFF".toColorInt(),
            backgroundColor = "#FF1A1A1A".toColorInt()
        ))
    }

    fun getPresets(): List<LyricPreset> = LyricPreset.entries

    fun saveCurrentStyleAsPreset(name: String): Boolean {
        if (name.isBlank()) return false
        val presets = getPresets()
        if (presets.any { it.displayName == name }) return false

        prefs.edit {
            putString("custom_preset_$name", "${currentStyle.fontSize},${currentStyle.textColor},${currentStyle.highlightColor},${currentStyle.backgroundColor}")
        }
        return true
    }

    fun loadCustomPresets(): Map<String, LyricStyle> {
        val customPresets = mutableMapOf<String, LyricStyle>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("custom_preset_") && value is String) {
                val name = key.removePrefix("custom_preset_")
                try {
                    val parts = value.split(",")
                    if (parts.size >= 4) {
                        customPresets[name] = LyricStyle(
                            fontSize = parts[0].toInt(),
                            textColor = parts[1].toInt(),
                            highlightColor = parts[2].toInt(),
                            backgroundColor = parts[3].toInt()
                        )
                    }
                } catch (_: Exception) {}
            }
        }
        return customPresets
    }
}
