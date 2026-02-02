package com.fox.music.core.network.api

import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.ImportChartsBatchDto
import com.fox.music.core.network.model.ImportChartsRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface ImportApiService {

    /**
     * 导入所有网易云排行榜
     * POST /api/import/netease/charts
     */
    @POST("/api/import/netease/charts")
    suspend fun importNeteaseCharts(
        @Body body: ImportChartsRequest
    ): ApiResponse<ImportChartsBatchDto>
}

