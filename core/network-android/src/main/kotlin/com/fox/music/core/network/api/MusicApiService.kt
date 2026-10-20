package com.fox.music.core.network.api

import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.CommentDto
import com.fox.music.core.network.model.CreateAdminMusicRequest
import com.fox.music.core.network.model.DeleteHistoryRequest
import com.fox.music.core.network.model.FindMusicDto
import com.fox.music.core.network.model.FindOrCreateMusicDto
import com.fox.music.core.network.model.FindOrCreateMusicRequest
import com.fox.music.core.network.model.ListeningStatsDto
import com.fox.music.core.network.model.MusicDto
import com.fox.music.core.network.model.PagedResponse
import com.fox.music.core.network.model.PlayHistoryDto
import com.fox.music.core.network.model.PlayRecordRequest
import com.fox.music.core.network.model.PostCommentRequest
import com.fox.music.core.network.model.ReplaceMusicSourceRequest
import com.fox.music.core.network.model.SubmitMusicFeedbackDto
import com.fox.music.core.network.model.SubmitMusicFeedbackRequest
import com.fox.music.core.network.model.TagDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MusicApiService {

    @GET("api/music")
    suspend fun getMusicList(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("keyword") keyword: String? = null,
        @Query("tag_id") tagId: Long? = null,
        @Query("sort") sort: String? = null,
        @Query("seed") seed: String? = null,
        @Query("exclude_ids") excludeIds: String? = null,
    ): ApiResponse<PagedResponse<MusicDto>>

    @GET("api/music/third-party/search")
    suspend fun searchThirdPartyMusic(
        @Query("platform") platform: String,
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
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


    @POST("api/music-comments/like/{id}")
    suspend fun likeMusicComment(
        @Path("id") commentId: Long
    ): ApiResponse<Unit>

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

    @GET("api/music-history/stats")
    suspend fun getListeningStats(
        @Query("days") days: Int = 7
    ): ApiResponse<ListeningStatsDto>

    @HTTP(method = "DELETE", path = "api/music-history", hasBody = true)
    suspend fun deletePlayHistory(@Body request: DeleteHistoryRequest): ApiResponse<Unit>

    @GET("api/tags/music")
    suspend fun getMusicTags(): ApiResponse<List<TagDto>>

    @GET("api/admin/music")
    suspend fun getAdminMusicList(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("keyword") keyword: String? = null,
    ): ApiResponse<PagedResponse<MusicDto>>

    @DELETE("api/admin/music/{id}")
    suspend fun deleteAdminMusic(@Path("id") id: Long): ApiResponse<Unit>

    @POST("api/admin/music")
    suspend fun createAdminMusic(
        @Body body: CreateAdminMusicRequest
    ): ApiResponse<JsonElement?>

    @GET("api/music/find")
    suspend fun findMusic(
        @Query("title") title: String,
        @Query("artist") artist: String = "",
    ): ApiResponse<FindMusicDto>

    @POST("api/music/find-or-create")
    suspend fun findOrCreateMusic(
        @Body body: FindOrCreateMusicRequest,
    ): ApiResponse<FindOrCreateMusicDto>

    /** 反馈音乐不匹配（音源纠错） */
    @POST("api/music/feedback")
    suspend fun submitMusicFeedback(
        @Body body: SubmitMusicFeedbackRequest,
    ): ApiResponse<SubmitMusicFeedbackDto>

    /** 替换音乐音源（仅管理员） */
    @POST("api/music/{id}/replace-source")
    suspend fun replaceMusicSource(
        @Path("id") id: Long,
        @Body body: ReplaceMusicSourceRequest,
    ): ApiResponse<Unit>
}
