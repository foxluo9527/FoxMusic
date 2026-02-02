package com.fox.music.core.network.model

import com.fox.music.core.model.ZeroOneBooleanSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistDto(
    val id: Long,
    val title: String,
    val description: String? = null,
    @SerialName("cover_image")
    val coverImage: String? = null,
    @SerialName("is_public")
    @Serializable(with = ZeroOneBooleanSerializer::class)
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
    val tags: List<TagDto> = emptyList(),
    val creator: UserDto? = null
)

@Serializable
data class PlaylistDetailDto(
    val id: Long,
    val title: String,
    val description: String? = null,
    @SerialName("cover_image")
    val coverImage: String? = null,
    @SerialName("is_public")
    @Serializable(with = ZeroOneBooleanSerializer::class)
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
    val tags: List<TagDto> = emptyList(),
    val creator: UserDto? = null,
    val tracks: PagedResponse<MusicDto> = PagedResponse()
)

@Serializable
data class PlaylistCategoryDto(
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

@Serializable
data class CreatePlaylistRequest(
    val title: String,
    val description: String? = null,
    @SerialName("cover_image")
    val coverImage: String? = null,
    @SerialName("is_public")
    val isPublic: Boolean = true,
    val tags: List<Long>? = null
)

@Serializable
data class UpdatePlaylistRequest(
    val title: String? = null,
    val description: String? = null,
    @SerialName("cover_image")
    val coverImage: String? = null,
    @SerialName("is_public")
    val isPublic: Boolean? = null,
    val tags: List<Long>? = null
)

@Serializable
data class AddTracksRequest(
    val musicIds: List<Long>
)

@Serializable
data class RemoveTracksRequest(
    val musicIds: List<Long>
)
