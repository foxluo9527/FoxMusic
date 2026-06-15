package com.fox.music.core.model.social

import kotlinx.serialization.Serializable

@Serializable
data class PostDraft(
    val content: String = "",
    val localImagePaths: List<String> = emptyList(),
    val updatedAt: Long = 0L,
)
