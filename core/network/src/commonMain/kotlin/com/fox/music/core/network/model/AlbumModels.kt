package com.fox.music.core.network.model

import com.fox.music.core.model.NullAsZeroIntSerializer
import com.fox.music.core.model.NullAsZeroLongSerializer
import com.fox.music.core.model.ZeroOneBooleanSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlbumDto(
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
    @Serializable(with = ZeroOneBooleanSerializer::class)
    val isPublic: Boolean = true,
    @Serializable(with = NullAsZeroLongSerializer::class)
    val duration: Long = 0,
    @SerialName("track_count")
    @Serializable(with = NullAsZeroIntSerializer::class)
    val trackCount: Int = 0,
    @SerialName("view_count")
    @Serializable(with = NullAsZeroIntSerializer::class)
    val viewCount: Int = 0,
    @SerialName("like_count")
    @Serializable(with = NullAsZeroIntSerializer::class)
    val likeCount: Int = 0,
    @SerialName("collection_count")
    @Serializable(with = NullAsZeroIntSerializer::class)
    val collectionCount: Int = 0,
    @SerialName("is_featured")
    @Serializable(with = ZeroOneBooleanSerializer::class)
    val isFeatured: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("favorite_count")
    @Serializable(with = NullAsZeroIntSerializer::class)
    val favoriteCount: Int = 0,
    @SerialName("play_count")
    @Serializable(with = NullAsZeroIntSerializer::class)
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    @SerialName("favorited_at")
    val favoritedAt: String? = null,
    val artists: List<ArtistDto> = emptyList()
)

@Serializable
data class AlbumDetailDto(
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
    @Serializable(with = ZeroOneBooleanSerializer::class)
    val isPublic: Boolean = true,
    @Serializable(with = NullAsZeroLongSerializer::class)
    val duration: Long = 0,
    @SerialName("track_count")
    @Serializable(with = NullAsZeroIntSerializer::class)
    val trackCount: Int = 0,
    @SerialName("view_count")
    @Serializable(with = NullAsZeroIntSerializer::class)
    val viewCount: Int = 0,
    @SerialName("like_count")
    @Serializable(with = NullAsZeroIntSerializer::class)
    val likeCount: Int = 0,
    @SerialName("collection_count")
    @Serializable(with = NullAsZeroIntSerializer::class)
    val collectionCount: Int = 0,
    @SerialName("is_featured")
    @Serializable(with = ZeroOneBooleanSerializer::class)
    val isFeatured: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("favorite_count")
    @Serializable(with = NullAsZeroIntSerializer::class)
    val favoriteCount: Int = 0,
    @SerialName("play_count")
    @Serializable(with = NullAsZeroIntSerializer::class)
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val artists: List<ArtistDto> = emptyList(),
    val tracks: PagedResponse<MusicDto> = PagedResponse()
)
