package com.fox.music.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateAdminMusicRequest(
    val title: String,
    val description: String = "",
    val url: String,
    @SerialName("cover_image")
    val coverImage: String? = null,
    val duration: Long = 0,
    val language: String = "未知",
    val genre: String = "流行",
    val lyrics: String = "",
    @SerialName("lyrics_type")
    val lyricsType: String = "none",
    val artists: List<CreateAdminArtistRequest> = emptyList(),
)

@Serializable
data class CreateAdminArtistRequest(
    val name: String,
    val alias: String = "",
    val avatar: String? = null,
    @SerialName("cover_image")
    val coverImage: String? = null,
)

@Serializable
data class UploadByUrlRequest(
    val url: String,
    val filename: String? = null,
    val referer: String? = null,
)
