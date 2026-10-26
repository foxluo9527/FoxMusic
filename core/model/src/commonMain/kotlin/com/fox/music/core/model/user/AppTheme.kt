package com.fox.music.core.model.user

object AppThemeIds {
    const val VINYL_NIGHT = "vinyl_night"
    const val FOX_PURPLE = "fox_purple"
    const val YOUTHFUL = "youthful"
    const val NOBLE_CLASSICAL = "noble_classical"
    const val OCEAN_BREEZE = "ocean_breeze"
    const val FOREST_MIST = "forest_mist"
    const val SUNSET_WARM = "sunset_warm"
    const val CUSTOM = "custom"

    /** 默认主题：黑胶夜场 */
    const val DEFAULT = VINYL_NIGHT

    val presetIds: List<String> = listOf(
        VINYL_NIGHT,
        FOX_PURPLE,
        YOUTHFUL,
        NOBLE_CLASSICAL,
        OCEAN_BREEZE,
        FOREST_MIST,
        SUNSET_WARM,
    )
}

/**
 * 「氛围」三档：决定派生容器色里种子色的占比（设计稿①）。
 *
 * v12 验收返工：旧三档是 8% / 12% / 18%，落进 `colorSchemeFromSeed` 只影响
 * primaryContainer 的混合比例，三档在同一屏上肉眼几乎无差（18% - 8% = 10 个百分点，
 * 纸底上就是两三个色阶），用户反馈"选了浓度预览一点没变"。
 * 现在拉开到 6% / 22% / 48%：柔和几乎回到纸面，浓郁是明确的实色块。
 * 与 themeId / customThemeSeed 存在同一处 DataStore（fox_preferences）。
 */
object AppThemeIntensity {
    const val SOFT = 0.06f
    const val BALANCED = 0.22f
    const val RICH = 0.48f
    const val DEFAULT = BALANCED

    /** 把任意 float 收敛到最近的一档，避免脏值写进 DataStore。 */
    fun normalize(value: Float): Float = when {
        value <= (SOFT + BALANCED) / 2f -> SOFT
        value <= (BALANCED + RICH) / 2f -> BALANCED
        else -> RICH
    }

    fun label(value: Float): String = when (normalize(value)) {
        SOFT -> "柔和"
        RICH -> "浓郁"
        else -> "均衡"
    }
}

data class AppThemePreset(
    val id: String,
    val name: String,
    val description: String,
    val previewColors: List<Long>,
)
