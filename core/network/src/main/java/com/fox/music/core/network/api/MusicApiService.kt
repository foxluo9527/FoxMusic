package com.fox.music.core.network.api

import com.fox.music.core.network.model.*
import retrofit2.http.*

interface MusicApiService {

    @GET("api/music")
    suspend fun getMusicList(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("keyword") keyword: String? = null,
        @Query("tag_id") tagId: Long? = null,
        @Query("sort") sort: String? = null
    ): ApiResponse<PagedResponse<MusicDto>>

    @GET("api/music/{id}")
    suspend fun getMusicDetail(@Path("id") id: Long): ApiResponse<MusicDto>

    @POST("api/music/{id}/favorite")
    suspend fun toggleFavorite(@Path("id") id: Long): ApiResponse<Unit>

    @POST("api/music/{id}/play")
    suspend fun recordPlay(
        @Path("id") id: Long,
        @Body request: PlayRecordRequest
    ): ApiResponse<Unit>

    @GET("api/music-comments")
    suspend fun getMusicComments(
        @Query("music_id") musicId: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<PagedResponse<CommentDto>>

    @POST("api/music-comments")
    suspend fun postComment(@Body request: PostCommentRequest): ApiResponse<CommentDto>

    @DELETE("api/music-comments/{id}")
    suspend fun deleteComment(@Path("id") id: Long): ApiResponse<Unit>

    @GET("api/music-comments/{id}/replies")
    suspend fun getCommentReplies(
        @Path("id") id: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<PagedResponse<CommentDto>>

    @GET("api/music-history")
    suspend fun getPlayHistory(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<PagedResponse<PlayHistoryDto>>

    @HTTP(method = "DELETE", path = "api/music-history", hasBody = true)
    suspend fun deletePlayHistory(@Body request: DeleteHistoryRequest): ApiResponse<Unit>

    @GET("api/tags/music")
    suspend fun getMusicTags(): ApiResponse<List<TagDto>>
}
