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

/**
 * 举报历史记录。GET /api/reports 的响应字段在 API 文档中未明确给出，
 * 这里按 POST 提交字段 + 常见列表字段反推，并全部使用可空/默认值以保证容错。
 */
@Serializable
data class ReportDto(
    val id: Long = 0L,
    @SerialName("target_type")
    val targetType: String? = null,
    @SerialName("target_id")
    val targetId: Long = 0L,
    val reason: String? = null,
    val description: String? = null,
    val status: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
)
