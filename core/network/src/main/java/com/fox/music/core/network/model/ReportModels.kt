package com.fox.music.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubmitReportRequest(
    @SerialName("target_type")
    val targetType: String,
    @SerialName("target_id")
    val targetId: Long,
    val reason: String,
    val description: String? = null,
    val evidence: Map<String, String>? = null,
)
