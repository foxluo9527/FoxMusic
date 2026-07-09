package com.fox.music.core.model.user

object AppThemeIds {
    const val FOX_PURPLE = "fox_purple"
    const val YOUTHFUL = "youthful"
    const val NOBLE_CLASSICAL = "noble_classical"
    const val OCEAN_BREEZE = "ocean_breeze"
    const val FOREST_MIST = "forest_mist"
    const val SUNSET_WARM = "sunset_warm"
    const val CUSTOM = "custom"

    const val DEFAULT = FOX_PURPLE

    val presetIds: List<String> = listOf(
        FOX_PURPLE,
        YOUTHFUL,
        NOBLE_CLASSICAL,
        OCEAN_BREEZE,
        FOREST_MIST,
        SUNSET_WARM,
    )
}

data class AppThemePreset(
    val id: String,
    val name: String,
    val description: String,
    val previewColors: List<Long>,
)
