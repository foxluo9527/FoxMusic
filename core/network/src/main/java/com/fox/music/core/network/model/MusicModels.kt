package com.fox.music.core.network.model

import com.fox.music.core.model.ZeroOneBooleanSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MusicDto(
    val id: Long,
    val title: String,
    val description: String? = null,
    val url: String,
    @SerialName("cover_image")
    val coverImage: String? = null,
    val duration: Long = 0,
    @SerialName("track_number")
    val trackNumber: Int? = null,
    @SerialName("disc_number")
    val discNumber: Int? = null,
    val genre: String? = null,
    val language: String? = null,
    val lyrics: String? = null,
    @SerialName("lyrics_trans")
    val lyricsTrans: String? = null,
    @SerialName("lyrics_url")
    val lyricsUrl: String? = null,
    @SerialName("play_count")
    val playCount: Int = 0,
    @SerialName("like_count")
    val likeCount: Int = 0,
    @SerialName("comment_count")
    val commentCount: Int = 0,
    @SerialName("collection_count")
    val collectionCount: Int = 0,
    @SerialName("avg_play_progress")
    val avgPlayProgress: Float = 0f,
    @SerialName("completion_rate")
    val completionRate: Float = 0f,
    @SerialName("is_explicit")
    @Serializable(with = ZeroOneBooleanSerializer::class)
    val isExplicit: Boolean = false,
    @SerialName("is_featured")
    @Serializable(with = ZeroOneBooleanSerializer::class)
    val isFeatured: Boolean = false,
    @SerialName("created_by")
    val createdBy: Long? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val isFavorite: Boolean = false,
    val tags: List<TagDto> = emptyList(),
    val artists: List<ArtistDto> = emptyList(),
    val album: AlbumDto? = null
)

@Serializable
data class TagDto(
    val id: Long,
    val name: String,
    val type: String? = null,
    val category: String? = null
)

@Serializable
data class PlayRecordRequest(
    val duration: Int? = null,
    val progress: Int? = null
)

@Serializable
data class PostCommentRequest(
    @SerialName("music_id")
    val musicId: Long? = null,
    val content: String,
    @SerialName("parent_id")
    val parentId: Long? = null
)

@Serializable
data class CommentDto(
    val id: Long,
    @SerialName("user_id")
    val userId: Long,
    val content: String,
    @SerialName("parent_id")
    val parentId: Long? = null,
    @SerialName("like_count")
    val likeCount: Int = 0,
    @SerialName("reply_count")
    val replyCount: Int = 0,
    val isLiked: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    val user: UserDto? = null,
    val replies: List<CommentDto> = emptyList()
)

@Serializable
data class PlayHistoryDto(
    val id: Long,
    val music: MusicDto,
    @SerialName("play_time")
    val playTime: String? = null,
    val progress: Long = 0,
    val duration: Long = 0
)

@Serializable
data class DeleteHistoryRequest(
    val musicIds: List<Long>
)
