package com.fox.music.core.network.api

import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.AppUpdateDto
import retrofit2.http.GET
import retrofit2.http.Query

interface AppUpdateApiService {

    @GET("api/v1/app/update/check")
    suspend fun checkUpdate(
        @Query("platform") platform: String = "android",
        @Query("versionCode") versionCode: Int,
        @Query("channel") channel: String = "official",
    ): ApiResponse<AppUpdateDto>
}
