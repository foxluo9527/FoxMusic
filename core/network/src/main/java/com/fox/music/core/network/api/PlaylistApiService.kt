package com.fox.music.core.network.api

import com.fox.music.core.network.model.*
import retrofit2.http.*

interface PlaylistApiService {

    @GET("api/playlists")
    suspend fun getPlaylists(
        @Query("user_id") userId: Long? = null
    ): ApiResponse<List<PlaylistDto>>

    @POST("api/playlists")
    suspend fun createPlaylist(@Body request: CreatePlaylistRequest): ApiResponse<PlaylistDto>

    @GET("api/playlists/{id}")
    suspend fun getPlaylistDetail(
        @Path("id") id: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<PlaylistDetailDto>

    @PUT("api/playlists/{id}")
    suspend fun updatePlaylist(
        @Path("id") id: Long,
        @Body request: UpdatePlaylistRequest
    ): ApiResponse<PlaylistDto>

    @DELETE("api/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: Long): ApiResponse<Unit>

    @GET("api/playlists/recommended")
    suspend fun getRecommendedPlaylists(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<PagedResponse<PlaylistDto>>

    @POST("api/playlists/{id}/tracks")
    suspend fun addTracks(
        @Path("id") id: Long,
        @Body request: AddTracksRequest
    ): ApiResponse<Unit>

    @DELETE("api/playlists/{id}/tracks/{musicId}")
    suspend fun removeTrack(
        @Path("id") id: Long,
        @Path("musicId") musicId: Long
    ): ApiResponse<Unit>

    @HTTP(method = "DELETE", path = "api/playlists/{id}/batch/tracks", hasBody = true)
    suspend fun removeTracks(
        @Path("id") id: Long,
        @Body request: RemoveTracksRequest
    ): ApiResponse<Unit>

    @GET("api/playlist-categories")
    suspend fun getCategories(
        @Query("categoryType") categoryType: String? = null
    ): ApiResponse<List<PlaylistCategoryDto>>

    @GET("api/playlist-categories/recommended")
    suspend fun getRecommendedCategories(): ApiResponse<List<PlaylistCategoryDto>>

    @GET("api/playlist-categories/fixed")
    suspend fun getFixedCategories(): ApiResponse<List<PlaylistCategoryDto>>

    @GET("api/playlist-categories/{id}")
    suspend fun getCategoryDetail(@Path("id") id: Long): ApiResponse<PlaylistCategoryDto>

    @GET("api/playlist-categories/{id}/playlists")
    suspend fun getCategoryPlaylists(
        @Path("id") id: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<PagedResponse<PlaylistDto>>
}
