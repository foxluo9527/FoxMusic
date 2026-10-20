package com.fox.music.core.network.api

import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.HighlightSegmentsData
import com.fox.music.core.network.model.HighlightStreamData
import retrofit2.http.GET
import retrofit2.http.Query

interface HighlightApiService {

    /**
     * 音流队列：
     * - 带 mood：按风格/心情语义近邻召回（services/highlightSearch.recommendByText）
     * - 不带 mood：默认队列（热门 + 登录用户偏好加权）
     * 每首歌附 clips（高潮片段）；clips 为空时客户端用兜底 clip。
     */
    @GET("api/highlight/stream")
    suspend fun getStream(
        @Query("mood") mood: String? = null,
        @Query("userId") userId: Long? = null,
        @Query("limit") limit: Int = 30,
        @Query("seed") seed: String? = null,
        @Query("exclude") exclude: String? = null,
    ): ApiResponse<HighlightStreamData>

    /** 批量取多首歌的原始高潮片段（客户端按需补充） */
    @GET("api/highlight/segments")
    suspend fun getSegments(
        @Query("musicIds") musicIds: String,
    ): ApiResponse<HighlightSegmentsData>
}
