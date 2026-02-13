package com.fox.music.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImportMusicRequest(
    val url: String,
    val platform: String? = null
)

@Serializable
data class ImportMusicResponse(
    @SerialName("albumId")
    val albumId: Long? = null,
    @SerialName("taskId")
    val taskId: String? = null,
    @SerialName("isImporting")
    val isImporting: Boolean = false
)
