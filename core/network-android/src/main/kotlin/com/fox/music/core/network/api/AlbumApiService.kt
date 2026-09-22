package com.fox.music.core.network.api

import com.fox.music.core.network.model.*
import retrofit2.http.*

interface AlbumApiService {

    @GET("api/albums")
    suspend fun getAlbumList(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("keyword") keyword: String? = null,
        @Query("artist_id") artistId: Long? = null,
        @Query("sort") sort: String? = null
    ): ApiResponse<PagedResponse<AlbumDto>>

    @GET("api/albums/{id}")
    suspend fun getAlbumDetail(
        @Path("id") id: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<AlbumDetailDto>

    @POST("api/albums/{id}/favorite")
    suspend fun toggleFavorite(@Path("id") id: Long): ApiResponse<Unit>
}
