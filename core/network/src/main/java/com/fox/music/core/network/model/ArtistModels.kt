package com.fox.music.core.network.model

import com.fox.music.core.model.ZeroOneBooleanSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArtistDto(
    val id: Long,
    val name: String,
    val alias: String? = null,
    val avatar: String? = null,
    @SerialName("cover_image")
    val coverImage: String? = null,
    val description: String? = null,
    val region: String? = null,
    @SerialName("birth_date")
    val birthDate: String? = null,
    val gender: String? = null,
    @SerialName("debut_date")
    val debutDate: String? = null,
    @SerialName("view_count")
    val viewCount: Int = 0,
    @SerialName("favorite_count")
    val favoriteCount: Int = 0,
    @SerialName("is_verified")
    @Serializable(with = ZeroOneBooleanSerializer::class)
    val isVerified: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("music_count")
    val musicCount: Int = 0,
    @SerialName("album_count")
    val albumCount: Int = 0,
    val isFavorite: Boolean = false,
    val tags: List<TagDto> = emptyList()
)

@Serializable
data class ArtistDetailDto(
    val id: Long,
    val name: String,
    val alias: String? = null,
    val avatar: String? = null,
    @SerialName("cover_image")
    val coverImage: String? = null,
    val description: String? = null,
    val region: String? = null,
    @SerialName("birth_date")
    val birthDate: String? = null,
    val gender: String? = null,
    @SerialName("debut_date")
    val debutDate: String? = null,
    @SerialName("view_count")
    val viewCount: Int = 0,
    @SerialName("favorite_count")
    val favoriteCount: Int = 0,
    @SerialName("is_verified")
    val isVerified: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("music_count")
    val musicCount: Int = 0,
    @SerialName("album_count")
    val albumCount: Int = 0,
    val isFavorite: Boolean = false,
    val tags: List<TagDto> = emptyList(),
    val hotMusics: List<MusicDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList()
)
