package com.fox.music.core.network.api

import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.PagedResponse
import com.fox.music.core.network.model.ReportDto
import com.fox.music.core.network.model.SubmitReportRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ReportApiService {

    @POST("api/reports")
    suspend fun submitReport(@Body request: SubmitReportRequest): ApiResponse<Unit>

    @GET("api/reports")
    suspend fun getReports(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("status") status: String? = null,
    ): ApiResponse<PagedResponse<ReportDto>>
}
