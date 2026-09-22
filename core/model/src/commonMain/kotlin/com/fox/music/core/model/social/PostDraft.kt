package com.fox.music.core.model.social

import kotlinx.serialization.Serializable

@Serializable
data class DraftMediaItem(
    val localPath: String,
    val type: PostMediaType = PostMediaType.IMAGE,
    val videoLocalPath: String? = null,
)

@Serializable
data class DraftTagItem(
    val id: Long = 0L,
    val name: String,
    val checked: Boolean = true,
    val operatedAt: Long = 0L,
)

@Serializable
data class PostDraft(
    val title: String = "",
    val content: String = "",
    val localImagePaths: List<String> = emptyList(),
    val localMedia: List<DraftMediaItem> = emptyList(),
    val tags: List<String> = emptyList(),
    val tagItems: List<DraftTagItem> = emptyList(),
    val musicId: Long? = null,
    val musicTitle: String? = null,
    val musicCover: String? = null,
    val musicArtist: String? = null,
    val updatedAt: Long = 0L,
)
