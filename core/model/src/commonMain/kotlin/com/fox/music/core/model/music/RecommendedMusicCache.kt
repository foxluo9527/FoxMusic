package com.fox.music.core.model.music

import kotlinx.serialization.Serializable

@Serializable
data class RecommendedMusicCache(
    val seed: String,
    val cacheDate: String,
    val items: List<Music>,
)
