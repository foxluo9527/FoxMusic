package com.fox.music.core.model.report

data class Report(
    val id: Long,
    val targetType: String,
    val targetId: Long,
    val reason: String,
    val description: String?,
    val status: String,
    val createdAt: String?,
)
