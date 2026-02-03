package com.fox.music.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Music(
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
    val isExplicit: Boolean = false,
    @SerialName("is_featured")
    val isFeatured: Boolean = false,
    @SerialName("created_by")
    val createdBy: Long? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("is_favorite")
    val isFavorite: Boolean = false,
    val tags: List<Tag> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val album: Album? = null
) {
    val lyricLines: List<LyricsParser.LyricLine> = LyricsParser.parseLrc(lyrics ?: "")

    fun getCurrentLyric(currentPosition: Long): String? =
        LyricsParser.findCurrentLyric(lyricLines, currentPosition)?.text

    fun getNextLyric(currentPosition: Long): String? =
        LyricsParser.findNextLyric(lyricLines, currentPosition)?.text

}

@Serializable
data class MusicDetail(
    val music: Music,
    val relatedMusics: List<Music> = emptyList()
)
