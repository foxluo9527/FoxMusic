package com.fox.music.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayHistory(
    val id: Long,
    val music: Music,
    @SerialName("play_time")
    val playTime: String? = null,
    val progress: Long = 0,
    val duration: Long = 0
)

@Serializable
data class SearchHistory(
    val id: Long,
    val keyword: String,
    @SerialName("search_time")
    val searchTime: String? = null
)
