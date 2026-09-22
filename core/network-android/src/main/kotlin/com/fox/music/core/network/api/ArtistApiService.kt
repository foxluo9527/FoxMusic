package com.fox.music.core.network.api

import com.fox.music.core.network.model.*
import retrofit2.http.*

interface ArtistApiService {

    @GET("api/artists")
    suspend fun getArtistList(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("keyword") keyword: String? = null,
        @Query("tag_id") tagId: Long? = null,
        @Query("sort") sort: String? = null
    ): ApiResponse<PagedResponse<ArtistDto>>

    @GET("api/artists/{id}")
    suspend fun getArtistDetail(@Path("id") id: Long): ApiResponse<ArtistDetailDto>

    @GET("api/artists/{id}/musics")
    suspend fun getArtistMusics(
        @Path("id") id: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String? = null
    ): ApiResponse<PagedResponse<MusicDto>>

    @POST("api/artists/{id}/favorite")
    suspend fun toggleFavorite(@Path("id") id: Long): ApiResponse<Unit>
}
