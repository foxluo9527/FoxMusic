package com.fox.music.iosshared

import com.fox.music.core.network.FoxNetworkConfig
import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.MusicDto
import com.fox.music.core.network.model.PagedResponse
import com.fox.music.core.network.model.PlaylistDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * iOS 端最小 API 客户端：Ktor Darwin 引擎（底层是 NSURLSession）。
 *
 * 只实现批次 7 验收所需的"拉歌曲列表"一个接口；DTO 直接复用
 * :core:network commonMain 里的 @Serializable 类，与 Android 端完全同一份契约。
 *
 * 注意：后端是纯 http://，iOS ATS 默认拦截明文请求 —— 例外在 iosApp 的
 * Info.plist（NSAllowsArbitraryLoads，与 Android 的全局明文放行对齐）。
 */
class FoxMusicApi {

    private val client = HttpClient(Darwin) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }

    /** GET /api/music —— 后端 noAuth，无需 token。 */
    suspend fun fetchMusicList(page: Int = 1, limit: Int = 20): List<MusicDto> {
        val response: ApiResponse<PagedResponse<MusicDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/music") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data?.list.orEmpty()
    }

    /**
     * GET /api/playlists/recommended —— 推荐歌单（与 Android 端 PlaylistApiService 同一路径）。
     */
    suspend fun fetchRecommendedPlaylists(page: Int = 1, limit: Int = 20): List<PlaylistDto> {
        val response: ApiResponse<List<PlaylistDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/playlists/recommended") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data.orEmpty()
    }
}
