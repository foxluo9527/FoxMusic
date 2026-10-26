package com.fox.music.feature.player.lyric.manager

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.blankj.utilcode.util.Utils
import com.fox.music.core.common.lyric.LyricStyleChangeNotifier
import com.fox.music.feature.player.lyric.data.LyricCustomColors
import com.fox.music.feature.player.lyric.data.LyricPalette
import com.fox.music.feature.player.lyric.data.LyricPaletteColors
import com.fox.music.feature.player.lyric.data.LyricPalettes
import com.fox.music.feature.player.lyric.data.LyricStyle
import com.fox.music.feature.player.lyric.data.resolve
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
        private const val KEY_SHOW_TRANSLATION = "show_translation"

        // 2026-10 v8 第二批：歌词配色方案（8 组精选 + 自定义三通道）
        private const val KEY_PALETTE_ID = "lyric_palette_id"
        private const val KEY_CUSTOM_BG = "lyric_custom_bg"
        private const val KEY_CUSTOM_NORMAL = "lyric_custom_normal"
        private const val KEY_CUSTOM_HIGHLIGHT = "lyric_custom_highlight"
        /**
         * 自定义通道的「未设置」哨兵值。
         * 必须是 0 而不是 -1 —— ARGB 的 Int 值（0xFFxxxxxx）本身就是负数，
         * 用 -1 做哨兵会导致所有合法深色被当成"未设置"。
         */
        private const val NO_CUSTOM_COLOR = LyricCustomColors.UNSET

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

    /**
     * 配色方案的变更计数。
     *
     * 方案/自定义色存在独立的 SharedPreferences key 里，不会改变 [styleFlow] 的值，
     * 所以需要一个「版本号」让 Compose 侧 recompose —— 否则选完配色歌词不刷新。
     */
    private val _paletteTick by lazy { MutableStateFlow(0) }
    val paletteTick: StateFlow<Int> by lazy { _paletteTick.asStateFlow() }

    /** 当前选中的配色方案 id（默认「纸上陶土」= 跟随主题）。 */
    val paletteId: String
        get() = prefs.getString(KEY_PALETTE_ID, null) ?: LyricPalettes.default.id

    /** 当前选中的配色方案。 */
    val currentPalette: LyricPalette
        get() = LyricPalettes.byId(paletteId)

    /** 用户自定义的三通道（-1 = 未自定义，沿用方案色）。 */
    val customColors: LyricCustomColors
        get() = LyricCustomColors(
            background = prefs.getInt(KEY_CUSTOM_BG, NO_CUSTOM_COLOR),
            normal = prefs.getInt(KEY_CUSTOM_NORMAL, NO_CUSTOM_COLOR),
            highlight = prefs.getInt(KEY_CUSTOM_HIGHLIGHT, NO_CUSTOM_COLOR),
        )

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
            lineSpacing = prefs.getFloat(KEY_LINE_SPACING, 1.5f),
            showTranslation = prefs.getBoolean(KEY_SHOW_TRANSLATION, true)
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
                putBoolean(KEY_SHOW_TRANSLATION, style.showTranslation)
            }
            _styleFlow.value = style
            LyricStyleChangeNotifier.notifyChanged(Utils.getApp())
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

    fun setShowTranslation(show: Boolean) {
        updateStyle(currentStyle.copy(showTranslation = show))
    }

    // ── 配色方案（2026-10 v8 第二批） ──────────────────────────────────────

    /**
     * 选中一组配色方案。
     *
     * 选方案即代表「整体换肤」，所以同时清掉三通道的自定义覆盖 ——
     * 否则用户先自定义了高亮色、再切方案，会发现高亮色纹丝不动（看起来像 bug）。
     */
    fun setPaletteId(paletteId: String) {
        prefs.edit {
            putString(KEY_PALETTE_ID, paletteId)
            remove(KEY_CUSTOM_BG)
            remove(KEY_CUSTOM_NORMAL)
            remove(KEY_CUSTOM_HIGHLIGHT)
        }
        notifyPaletteChanged()
    }

    /** 自定义「高亮 · 当前行」色。 */
    fun setCustomHighlight(argb: Int) {
        prefs.edit { putInt(KEY_CUSTOM_HIGHLIGHT, argb) }
        notifyPaletteChanged()
    }

    /** 自定义「普通 · 其他行」色。 */
    fun setCustomNormal(argb: Int) {
        prefs.edit { putInt(KEY_CUSTOM_NORMAL, argb) }
        notifyPaletteChanged()
    }

    /** 自定义背景色（上下渐变取同色）。 */
    fun setCustomBackground(argb: Int) {
        prefs.edit { putInt(KEY_CUSTOM_BG, argb) }
        notifyPaletteChanged()
    }

    /**
     * 一次性写回设置弹层的草稿（v12）。
     *
     * 弹层里的所有控件都只改本地 Compose state，**只有点「应用」时**才走这里落盘；
     * 取消 / 侧滑关闭不调用，等于天然回滚 —— 这是「没点应用就保存」的修法。
     *
     * 三通道里的 [LyricCustomColors.UNSET] 会 remove 掉对应 key（而不是写入 0），
     * 否则「跟随主题」清空覆盖后重启会读回一个透明色。
     */
    fun applyDraft(
        fontSize: Int,
        showTranslation: Boolean,
        paletteId: String,
        custom: LyricCustomColors,
    ) {
        updateStyle(
            currentStyle.copy(
                fontSize = fontSize.coerceIn(10, 48),
                showTranslation = showTranslation,
            )
        )
        prefs.edit {
            putString(KEY_PALETTE_ID, paletteId)
            if (custom.background != NO_CUSTOM_COLOR) putInt(KEY_CUSTOM_BG, custom.background) else remove(KEY_CUSTOM_BG)
            if (custom.normal != NO_CUSTOM_COLOR) putInt(KEY_CUSTOM_NORMAL, custom.normal) else remove(KEY_CUSTOM_NORMAL)
            if (custom.highlight != NO_CUSTOM_COLOR) putInt(KEY_CUSTOM_HIGHLIGHT, custom.highlight) else remove(KEY_CUSTOM_HIGHLIGHT)
        }
        notifyPaletteChanged()
    }

    /** 一键回到「跟随主题」：方案回到默认，同时清掉所有自定义覆盖。 */
    fun followThemePalette() {
        prefs.edit {
            putString(KEY_PALETTE_ID, LyricPalettes.default.id)
            remove(KEY_CUSTOM_BG)
            remove(KEY_CUSTOM_NORMAL)
            remove(KEY_CUSTOM_HIGHLIGHT)
        }
        notifyPaletteChanged()
    }

    /** 当前方案 + 自定义覆盖解析出的最终颜色。 */
    fun resolvePaletteColors(themeAccentArgb: Int): LyricPaletteColors =
        currentPalette.resolve(themeAccentArgb, customColors)

    /**
     * 供歌词渲染使用的最终样式：字号 / 翻译开关沿用用户设置，
     * 三个颜色通道换成当前方案的解析结果。
     *
     * 旧代码里 [currentStyle] 存的是「白字 + 半透明黑底」，那是给旧版深底播放器设计的，
     * 直接铺到纸感浅底上会看不见，所以渲染侧一律走这里拿色。
     */
    fun resolvedLyricStyle(themeAccentArgb: Int): LyricStyle {
        val colors = resolvePaletteColors(themeAccentArgb)
        return currentStyle.copy(
            textColor = colors.normal,
            highlightColor = colors.highlight,
            backgroundColor = colors.bgTop,
        )
    }

    private fun notifyPaletteChanged() {
        _paletteTick.value = _paletteTick.value + 1
        LyricStyleChangeNotifier.notifyChanged(Utils.getApp())
    }

    fun resetToDefault() {
        updateStyle(getDefaultStyle())
        followThemePalette()
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
            textColor = "#FFB3B3".toColorInt(),
            highlightColor = "#FF6B6B".toColorInt(),
            backgroundColor = "#99000000".toColorInt()
        )),
        NEON("霓虹", LyricStyle(
            fontSize = 18,
            textColor = "#D98CD9".toColorInt(),
            highlightColor = "#FF00FF".toColorInt(),
            backgroundColor = "#CC000000".toColorInt()
        )),
        WARM("暖阳", LyricStyle(
            fontSize = 18,
            textColor = "#FFD699".toColorInt(),
            highlightColor = "#FFA500".toColorInt(),
            backgroundColor = "#B0843C30".toColorInt()
        )),
        COOL("清凉", LyricStyle(
            fontSize = 18,
            textColor = "#80E8EA".toColorInt(),
            highlightColor = "#00CED1".toColorInt(),
            backgroundColor = "#B03A4A4E".toColorInt()
        )),
        DARK("暗黑", LyricStyle(
            fontSize = 18,
            textColor = "#C8C8C8".toColorInt(),
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
