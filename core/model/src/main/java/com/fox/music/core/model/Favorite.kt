package com.fox.music.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Favorite(
    val id: Long,
    @SerialName("user_id")
    val userId: Long,
    val type: FavoriteType,
    @SerialName("target_id")
    val targetId: Long,
    val title: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
enum class FavoriteType {
    @SerialName("music")
    MUSIC,
    @SerialName("video")
    VIDEO,
    @SerialName("novel")
    NOVEL,
    @SerialName("post")
    POST,
    @SerialName("artist")
    ARTIST,
    @SerialName("album")
    ALBUM,
    @SerialName("playlist")
    PLAYLIST
}
