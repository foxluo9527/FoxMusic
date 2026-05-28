package com.fox.music.core.network.api

import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.SubmitReportRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface ReportApiService {

    @POST("api/reports")
    suspend fun submitReport(@Body request: SubmitReportRequest): ApiResponse<Unit>
}
