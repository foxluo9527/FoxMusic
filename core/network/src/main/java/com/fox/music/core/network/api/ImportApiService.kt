package com.fox.music.core.network.api

import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.ImportChartsBatchDto
import com.fox.music.core.network.model.ImportChartsRequest
import com.fox.music.core.network.model.ImportMusicRequest
import com.fox.music.core.network.model.ImportMusicResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ImportApiService {

    /**
     * 导入音乐（歌单/专辑）
     * POST /api/import/music
     */
    @POST("/api/import/music")
    suspend fun importMusic(
        @Body body: ImportMusicRequest
    ): ApiResponse<ImportMusicResponse>

    /**
     * 导入所有网易云排行榜
     * POST /api/import/netease/charts
     */
    @POST("/api/import/netease/charts")
    suspend fun importNeteaseCharts(
        @Body body: ImportChartsRequest
    ): ApiResponse<ImportChartsBatchDto>
}

