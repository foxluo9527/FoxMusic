package com.fox.music.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class ImportChartsRequest(
    val platform: String? = null
)

@Serializable
data class ImportChartResultDto(
    val success: Boolean = false,
    val chartId: Long = 0,
    val chartName: String = "",
    val albumId: Long = 0,
    val error: String? = null
)

@Serializable
data class ImportChartsBatchDto(
    val results: List<ImportChartResultDto> = emptyList(),
    val total: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0
)

