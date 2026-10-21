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
import com.fox.music.core.network.model.ProviderSearchResultsDto
import com.fox.music.core.model.music.ResolveThirdPartyRequest
import com.fox.music.core.model.music.ResolveThirdPartyDto
import com.fox.music.core.model.music.MusicExistsDto
import com.fox.music.core.model.music.ImportSingleRequest
import com.fox.music.core.model.music.ImportSingleResponseDto
import com.fox.music.core.model.music.ProviderCandidateGroupDto
import com.fox.music.core.model.music.ScrapedLyricsDto
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

    /** 按精准歌名+艺人拉取多 provider 元数据（纠错/添加单曲/搜索 二级多源 tab 用） */
    @GET("api/music/third-party/search-providers")
    suspend fun searchProvidersByTrack(
        @Query("title") title: String,
        @Query("artist") artist: String? = null,
        @Query("platforms") platforms: String? = null,
        @Query("limit") limit: Int = 8,
    ): ApiResponse<ProviderSearchResultsDto>

    /**
     * 按音源渠道分组拉取候选（纠错/收录二级页「音源 tab」用）。
     * 宽松匹配不过滤；hifini 四站为站点主题列表（RESOLVE），
     * 其余为官方 API 直链候选（DIRECT）。
     */
    @GET("api/music/third-party/provider-candidates")
    suspend fun getProviderCandidates(
        @Query("title") title: String,
        @Query("artist") artist: String? = null,
        @Query("durationMs") durationMs: Long? = null,
        @Query("groups") groups: String? = null,
    ): ApiResponse<List<ProviderCandidateGroupDto>>

    /** 按需解析第三方单曲音源（点选某首后才触发，避免列表整体超时） */
    @POST("api/music/third-party/resolve")
    suspend fun resolveThirdPartyAudio(
        @Body body: ResolveThirdPartyRequest,
    ): ApiResponse<ResolveThirdPartyDto>

    /** 查询某首歌是否已收录到平台（收录/立即播放判定用） */
    @GET("api/music/exists")
    suspend fun checkMusicExists(
        @Query("title") title: String,
        @Query("artist") artist: String? = null,
    ): ApiResponse<MusicExistsDto>

    /** 收录单曲到平台（普通用户也可用，等价于 Hifi 收录） */
    @POST("api/music/import-single")
    suspend fun importSingleTrack(
        @Body body: ImportSingleRequest,
    ): ApiResponse<ImportSingleResponseDto>

    /** 按 歌名+艺人 刮削歌词（LRCLIB 优先 + lyrics.ovh 兜底） */
    @GET("api/music/lyrics/fetch")
    suspend fun fetchScrapedLyrics(
        @Query("title") title: String,
        @Query("artist") artist: String,
        @Query("durationMs") durationMs: Long? = null,
    ): ApiResponse<ScrapedLyricsDto>
}
