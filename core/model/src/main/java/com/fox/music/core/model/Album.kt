package com.fox.music.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Album(
    val id: Long,
    val title: String,
    @SerialName("cover_image")
    val coverImage: String? = null,
    val description: String? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
    val type: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    @SerialName("creator_id")
    val creatorId: Long? = null,
    @SerialName("is_public")
    val isPublic: Boolean = true,
    val duration: Long = 0,
    @SerialName("track_count")
    val trackCount: Int = 0,
    @SerialName("view_count")
    val viewCount: Int = 0,
    @SerialName("like_count")
    val likeCount: Int = 0,
    @SerialName("collection_count")
    val collectionCount: Int = 0,
    @SerialName("is_featured")
    val isFeatured: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("favorite_count")
    val favoriteCount: Int = 0,
    @SerialName("play_count")
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val artists: List<Artist> = emptyList()
)

@Serializable
data class AlbumDetail(
    val album: Album,
    val tracks: PagedData<Music> = PagedData()
)
