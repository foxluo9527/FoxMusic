package com.fox.music.iosshared

import com.fox.music.core.network.FoxNetworkConfig
import com.fox.music.core.network.model.AlbumDto
import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.ArtistDto
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.network.model.MusicDto
import com.fox.music.core.network.model.PagedResponse
import com.fox.music.core.network.model.PlaylistDetailDto
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
 * iOS 端 API 客户端：Ktor Darwin 引擎（底层是 NSURLSession）。
 *
 * DTO 直接复用 :core:network commonMain 里的 @Serializable 类，与 Android 端完全同一份契约
 * （路径/参数与 core:network-android 的各 *ApiService 声明一一对应）。
 *
 * 注意：后端是纯 http://，iOS ATS 默认拦截明文请求 —— 例外在 iosApp 的
 * Info.plist（NSAllowsArbitraryLoads，与 Android 的全局明文放行对齐）。
 *
 * 未登录：最近在听（/api/music-history 系列接口）后端强制 req.user，iOS 侧暂不可用，
 * HomeState.recentPlays 置空即可（HomeContent 自带空态）；其余接口后端均为 noAuth
 * 或 optionalAuth。
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

    /** GET /api/music —— 新歌/推荐歌曲分页（后端 noAuth）。 */
    suspend fun fetchMusicList(page: Int = 1, limit: Int = 20, sort: String? = null): List<MusicDto> {
        val response: ApiResponse<PagedResponse<MusicDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/music") {
                parameter("page", page)
                parameter("limit", limit)
                if (sort != null) parameter("sort", sort)
            }
            .body()
        return response.data?.list.orEmpty()
    }

    /** GET /api/playlists/recommended —— 推荐歌单（与 Android PlaylistApiService 同契约）。 */
    suspend fun fetchRecommendedPlaylists(page: Int = 1, limit: Int = 20): List<PlaylistDto> {
        val response: ApiResponse<List<PlaylistDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/playlists/recommended") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data.orEmpty()
    }

    /** GET /api/search/hot-keywords —— 热门搜索词（后端无鉴权）。 */
    suspend fun fetchHotKeywords(limit: Int = 10): List<HotKeyword> {
        val response: ApiResponse<List<HotKeyword>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/search/hot-keywords") {
                parameter("limit", limit)
            }
            .body()
        return response.data.orEmpty()
    }

    /** GET /api/albums —— 专辑列表分页（后端 noAuth）。 */
    suspend fun fetchAlbums(page: Int = 1, limit: Int = 20): List<AlbumDto> {
        val response: ApiResponse<PagedResponse<AlbumDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/albums") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data?.list.orEmpty()
    }

    /** GET /api/artists —— 热门艺人列表分页（后端 noAuth）。 */
    suspend fun fetchArtists(page: Int = 1, limit: Int = 20): List<ArtistDto> {
        val response: ApiResponse<PagedResponse<ArtistDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/artists") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data?.list.orEmpty()
    }

    /** GET /api/playlists/ranks —— 排行榜列表（后端 noAuth，data 为数组）。 */
    suspend fun fetchRanks(page: Int = 1, limit: Int = 20): List<PlaylistDto> {
        val response: ApiResponse<List<PlaylistDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/playlists/ranks") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data.orEmpty()
    }

    /** GET /api/playlists/ranks/{id} —— 排行榜详情含 tracks 分页（后端 noAuth）。 */
    suspend fun fetchRankDetail(rankId: Long, page: Int = 1, limit: Int = 20): PlaylistDetailDto? {
        val response: ApiResponse<PlaylistDetailDto> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/playlists/ranks/$rankId") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data
    }

    /** GET /api/playlists/{id} —— 歌单详情含 tracks 分页。 */
    suspend fun fetchPlaylistDetail(playlistId: Long, page: Int = 1, limit: Int = 20): PlaylistDetailDto? {
        val response: ApiResponse<PlaylistDetailDto> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/playlists/$playlistId") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data
    }

    /** GET /api/music?keyword= —— 平台曲库搜索（noAuth）。 */
    suspend fun searchMusic(keyword: String, page: Int = 1, limit: Int = 20): List<MusicDto> {
        val response: ApiResponse<PagedResponse<MusicDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/music") {
                parameter("keyword", keyword)
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data?.list.orEmpty()
    }

    /** GET /api/music/third-party/search?platform=&keyword= —— 第三方平台搜索（noAuth）。 */
    suspend fun searchThirdPartyMusic(
        platform: String,
        keyword: String,
        page: Int = 1,
        limit: Int = 20,
    ): List<MusicDto> {
        val response: ApiResponse<PagedResponse<MusicDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/music/third-party/search") {
                parameter("platform", platform)
                parameter("keyword", keyword)
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data?.list.orEmpty()
    }

    /** GET /api/artists?keyword= —— 歌手搜索（noAuth）。 */
    suspend fun searchArtists(keyword: String, page: Int = 1, limit: Int = 20): List<ArtistDto> {
        val response: ApiResponse<PagedResponse<ArtistDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/artists") {
                parameter("keyword", keyword)
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data?.list.orEmpty()
    }

    /** GET /api/albums?keyword= —— 专辑搜索（noAuth）。 */
    suspend fun searchAlbums(keyword: String, page: Int = 1, limit: Int = 20): List<AlbumDto> {
        val response: ApiResponse<PagedResponse<AlbumDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/albums") {
                parameter("keyword", keyword)
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data?.list.orEmpty()
    }
}
