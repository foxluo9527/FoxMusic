package com.fox.music.feature.player.lyric.data

/**
 * 歌词配色方案（设计稿②）。
 *
 * 一组方案同时决定「背景渐变（上/下）+ 普通行 + 高亮行 + 翻译行」四个通道，
 * 取代旧版那 5 个刺眼的独立圆点（旧版只能改单色，改出来不是脏就是瞎）。
 *
 * 颜色统一用 0xAARRGGBB 的 Long 存储，便于直接落 SharedPreferences 与渲染。
 */
data class LyricPalette(
    val id: String,
    val name: String,
    /** 背景渐变起始色（顶部） */
    val bgTop: Long,
    /** 背景渐变结束色（底部） */
    val bgBottom: Long,
    /** 非当前行文字色 */
    val normalColor: Long,
    /** 当前高亮行文字色；[followTheme] 为 true 时该值只是兜底，实际取主题 primary */
    val highlightColor: Long,
    /** 翻译行文字色 */
    val translationColor: Long,
) {
    /** 是否「跟随主题」：高亮色实时跟随当前主题强调色。 */
    val followTheme: Boolean
        get() = id == FOLLOW_THEME_ID

    companion object {
        const val FOLLOW_THEME_ID = "paper_terracotta"
    }
}

/** 方案解析后的最终四通道颜色（已应用用户的自定义覆盖）。 */
data class LyricPaletteColors(
    val bgTop: Int,
    val bgBottom: Int,
    val normal: Int,
    val highlight: Int,
    val translation: Int,
)

/**
 * 用户在「自定义配色」里覆盖的通道。
 *
 * 未设置用 `0`（全透明）做哨兵，**不能用 -1**：ARGB 的 Int 值高位是 alpha=0xFF，
 * 转成 Int 后本身就是负数，用 `< 0` 判断会把每一个合法颜色都当成"未设置"。
 */
data class LyricCustomColors(
    val background: Int = UNSET,
    val normal: Int = UNSET,
    val highlight: Int = UNSET,
) {
    val isEmpty: Boolean
        get() = background == UNSET && normal == UNSET && highlight == UNSET

    companion object {
        /** 未自定义哨兵值（透明色，正常配色不会出现） */
        const val UNSET = 0
    }
}

object LyricPalettes {

    val all: List<LyricPalette> = listOf(
        LyricPalette(
            id = LyricPalette.FOLLOW_THEME_ID,
            name = "纸上陶土",
            bgTop = 0xFFF7F5F0,
            bgBottom = 0xFFEFE9DF,
            normalColor = 0xFF4A443E,
            highlightColor = 0xFFB8513A,
            translationColor = 0xFF8A8378,
        ),
        LyricPalette(
            id = "ink_amber",
            name = "墨夜琥珀",
            bgTop = 0xFF17140F,
            bgBottom = 0xFF241E17,
            normalColor = 0xFF7E776C,
            highlightColor = 0xFFE8A838,
            translationColor = 0xFF5C554A,
        ),
        LyricPalette(
            id = "vinyl_latte",
            name = "黑胶奶咖",
            bgTop = 0xFFEFE6DA,
            bgBottom = 0xFFE2D5C4,
            normalColor = 0xFF5C5248,
            highlightColor = 0xFF8C5A3C,
            translationColor = 0xFF96897B,
        ),
        LyricPalette(
            id = "mist_blue",
            name = "雾蓝清晨",
            bgTop = 0xFFEEF2F6,
            bgBottom = 0xFFDDE6EF,
            normalColor = 0xFF4E5A66,
            highlightColor = 0xFF38708C,
            translationColor = 0xFF8C99A6,
        ),
        LyricPalette(
            id = "moss_rain",
            name = "雨后苔原",
            bgTop = 0xFFEEF2EA,
            bgBottom = 0xFFDFE8D8,
            normalColor = 0xFF525C48,
            highlightColor = 0xFF4E7A3C,
            translationColor = 0xFF8C997E,
        ),
        LyricPalette(
            id = "plum_dusk",
            name = "绛紫暮色",
            bgTop = 0xFFF2ECF2,
            bgBottom = 0xFFE4D8E6,
            normalColor = 0xFF5C4E5C,
            highlightColor = 0xFF8C4E6E,
            translationColor = 0xFF96899A,
        ),
        LyricPalette(
            id = "coral_sea",
            name = "珊瑚海",
            bgTop = 0xFFF7EFEA,
            bgBottom = 0xFFF0DDD4,
            normalColor = 0xFF665048,
            highlightColor = 0xFFC25E42,
            translationColor = 0xFFA38F84,
        ),
        LyricPalette(
            id = "graphite",
            name = "石墨",
            bgTop = 0xFFEDEDEB,
            bgBottom = 0xFFDFDFDC,
            normalColor = 0xFF4A4A48,
            highlightColor = 0xFF2E2E2C,
            translationColor = 0xFF8A8A86,
        ),
    )

    val default: LyricPalette
        get() = all.first()

    fun byId(id: String?): LyricPalette =
        all.firstOrNull { it.id == id } ?: default

    /** 「高亮色预设」8 格（设计稿②自定义二级页）。 */
    val highlightPresets: List<Long> = listOf(
        0xFFB8513A,
        0xFFC25E42,
        0xFFB07624,
        0xFF4E7A3C,
        0xFF38708C,
        0xFF38598C,
        0xFF8C4E6E,
        0xFF2E2E2C,
    )
}

/**
 * 解析一组方案的最终颜色。
 *
 * @param themeAccentArgb 当前主题的强调色（「跟随主题」方案的高亮色取它）
 * @param custom 用户在自定义页覆盖的三通道（0 = 未自定义）
 */
fun LyricPalette.resolve(
    themeAccentArgb: Int,
    custom: LyricCustomColors = LyricCustomColors(),
): LyricPaletteColors {
    val unset = LyricCustomColors.UNSET
    return LyricPaletteColors(
        bgTop = custom.background.takeIf { it != unset } ?: bgTop.toInt(),
        bgBottom = custom.background.takeIf { it != unset } ?: bgBottom.toInt(),
        normal = custom.normal.takeIf { it != unset } ?: normalColor.toInt(),
        highlight = custom.highlight.takeIf { it != unset }
            ?: if (followTheme) themeAccentArgb else highlightColor.toInt(),
        translation = translationColor.toInt(),
    )
}
