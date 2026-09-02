package com.fox.music.core.network.api

import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.UserHomeProfileDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface UserApiService {

    @GET("api/users/{userId}")
    suspend fun getUserHomeProfile(
        @Path("userId") userId: Long,
    ): ApiResponse<UserHomeProfileDto>

    @POST("api/users/{userId}/block")
    suspend fun blockUser(
        @Path("userId") userId: Long,
    ): ApiResponse<Unit?>
}
