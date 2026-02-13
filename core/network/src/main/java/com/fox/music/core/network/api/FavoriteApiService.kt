package com.fox.music.core.network.api

import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.FavoriteDto
import com.fox.music.core.network.model.PagedResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface FavoriteApiService {

    @GET("api/favorites")
    suspend fun getFavorites(
        @Query("type") type: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<PagedResponse<FavoriteDto>>
}
