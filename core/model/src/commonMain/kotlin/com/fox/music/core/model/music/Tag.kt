package com.fox.music.core.model.music

import kotlinx.serialization.Serializable

@Serializable
data class Tag(
    val id: Long,
    val name: String,
    val type: String? = null,
    val category: String? = null
)