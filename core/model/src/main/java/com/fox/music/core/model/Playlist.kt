package com.fox.music.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: Long,
    val title: String,
    val description: String? = null,
    @SerialName("cover_image")
    val coverImage: String? = null,
    @SerialName("is_public")
    val isPublic: Boolean = true,
    @SerialName("creator_id")
    val creatorId: Long? = null,
    @SerialName("track_count")
    val trackCount: Int = 0,
    @SerialName("play_count")
    val playCount: Int = 0,
    @SerialName("favorite_count")
    val favoriteCount: Int = 0,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val isFavorite: Boolean = false,
    val tags: List<Tag> = emptyList(),
    val creator: User? = null
)

@Serializable
data class PlaylistDetail(
    val playlist: Playlist,
    val tracks: PagedData<Music> = PagedData()
)

@Serializable
data class PlaylistCategory(
    val id: Long,
    val name: String,
    val description: String? = null,
    @SerialName("cover_image")
    val coverImage: String? = null,
    @SerialName("category_type")
    val categoryType: String? = null,
    @SerialName("playlist_count")
    val playlistCount: Int = 0
)
