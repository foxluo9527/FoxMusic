package com.fox.music.core.network.api

import com.fox.music.core.network.model.ApiResponse
import retrofit2.http.*

interface SearchApiService {

    @GET("api/search/hot-keywords")
    suspend fun getHotKeywords(
        @Query("type") type: String? = null,
        @Query("limit") limit: Int = 10
    ): ApiResponse<List<String>>

    @GET("api/search/history")
    suspend fun getSearchHistory(
        @Query("type") type: String? = null,
        @Query("limit") limit: Int = 20
    ): ApiResponse<List<String>>

    @DELETE("api/search/history")
    suspend fun clearSearchHistory(
        @Query("type") type: String? = null
    ): ApiResponse<Unit>
}
