package com.fox.music.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class UploadFileDto(
    val url: String = "",
    val filename: String? = null,
    val size: Long? = null,
)
