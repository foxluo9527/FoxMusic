package com.fox.music.iosshared

import com.fox.music.core.network.FoxNetworkConfig
import com.fox.music.core.network.model.AlbumDto
import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.ArtistDetailDto
import com.fox.music.core.network.model.ArtistDto
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.network.model.ForgotPasswordRequest
import com.fox.music.core.network.model.ListeningStatsDto
import com.fox.music.core.network.model.LoginRequest
import com.fox.music.core.network.model.LoginResponse
import com.fox.music.core.network.model.MusicDto
import com.fox.music.core.network.model.PagedResponse
import com.fox.music.core.network.model.PlayHistoryDto
import com.fox.music.core.network.model.PlaylistDetailDto
import com.fox.music.core.network.model.PlaylistDto
import com.fox.music.core.network.model.RegisterRequest
import com.fox.music.core.network.model.ResetPasswordRequest
import com.fox.music.core.network.model.PlayRecordRequest
import com.fox.music.core.network.model.PostCommentRequest
import com.fox.music.core.network.model.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
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
 * 登录态：`Authorization: Bearer <token>` 由 [defaultRequest] 在每次请求时从
 * [FoxAuthStore] 现取，因此登录后无需重建 HttpClient。最近在听
 * （api/music-history 系列）后端强制 req.user，故仅在已登录时有数据。
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
        // defaultRequest 的 lambda 是「每次请求」求值，所以能读到运行期变化的令牌。
        defaultRequest {
            FoxAuthStore.token?.takeIf { it.isNotBlank() }?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }
    }

    // ---------- 登录 / 注册 / 找回密码 ----------

    /** POST api/auth/login —— 成功即写入 [FoxAuthStore]，失败返回后端 message。 */
    suspend fun login(username: String, password: String): AuthResult = try {
        val response: ApiResponse<LoginResponse> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username = username, password = password))
            }
            .body()
        val data = response.data
        if (response.isSuccess && data != null) {
            AuthResult(
                success = true,
                message = null,
                user = data.user.toAuthUser(),
                token = data.token,
                refreshToken = data.refreshToken,
            )
        } else {
            AuthResult(
                success = false,
                message = response.message.ifBlank { "登录失败，请检查账号或密码" },
                user = null,
            )
        }
    } catch (e: Exception) {
        AuthResult(success = false, message = e.readableMessage("登录失败，请稍后重试"), user = null)
    }

    /** POST api/auth/register —— 成功后后端同样返回 token + user。 */
    suspend fun register(username: String, password: String, email: String): AuthResult = try {
        val response: ApiResponse<LoginResponse> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(username = username, password = password, email = email))
            }
            .body()
        val data = response.data
        if (response.isSuccess && data != null) {
            AuthResult(
                success = true,
                message = null,
                user = data.user.toAuthUser(),
                token = data.token,
                refreshToken = data.refreshToken,
            )
        } else {
            AuthResult(
                success = false,
                message = response.message.ifBlank { "注册失败，请检查填写内容" },
                user = null,
            )
        }
    } catch (e: Exception) {
        AuthResult(success = false, message = e.readableMessage("注册失败，请稍后重试"), user = null)
    }

    /** POST api/auth/forgot-password —— 发送邮箱验证码。 */
    suspend fun forgetPassword(email: String): AuthResult = try {
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordRequest(email = email))
            }
            .body()
        if (response.isSuccess) {
            AuthResult(success = true, message = null, user = null)
        } else {
            AuthResult(
                success = false,
                message = response.message.ifBlank { "验证码发送失败" },
                user = null,
            )
        }
    } catch (e: Exception) {
        AuthResult(success = false, message = e.readableMessage("验证码发送失败"), user = null)
    }

    /** POST api/auth/reset-password —— 校验码 + 新密码。 */
    suspend fun resetPassword(email: String, code: String, newPassword: String): AuthResult = try {
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/auth/reset-password") {
                contentType(ContentType.Application.Json)
                setBody(
                    ResetPasswordRequest(
                        email = email,
                        code = code,
                        newPassword = newPassword,
                    )
                )
            }
            .body()
        if (response.isSuccess) {
            AuthResult(success = true, message = null, user = null)
        } else {
            AuthResult(
                success = false,
                message = response.message.ifBlank { "密码重置失败" },
                user = null,
            )
        }
    } catch (e: Exception) {
        AuthResult(success = false, message = e.readableMessage("密码重置失败"), user = null)
    }

    /** GET api/auth/profile —— 用当前令牌换取用户资料（判断令牌是否仍有效）。 */
    suspend fun fetchProfile(): UserDto? = try {
        val response: ApiResponse<UserDto> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/auth/profile")
            .body()
        if (response.isSuccess) response.data else null
    } catch (e: Exception) {
        null
    }

    // ---------- 需登录的内容 ----------

    suspend fun fetchPlayHistoryPage(page: Int = 1, limit: Int = 20): PagedResponse<PlayHistoryDto> {
        val response: ApiResponse<PagedResponse<PlayHistoryDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/music-history") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data ?: PagedResponse()
    }

    /** GET api/music-history —— 最近在听（后端强制 req.user）。 */
    suspend fun fetchPlayHistory(page: Int = 1, limit: Int = 20): List<PlayHistoryDto> =
        fetchPlayHistoryPage(page, limit).list

    /** GET api/music-history/stats */
    suspend fun fetchListeningStats(days: Int = 7): ListeningStatsDto? {
        val response: ApiResponse<ListeningStatsDto> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/music-history/stats") {
                parameter("days", days)
            }
            .body()
        return if (response.isSuccess) response.data else null
    }

    /** GET api/playlists —— 当前用户歌单（需登录）。 */
    suspend fun fetchUserPlaylists(userId: Long? = null): List<PlaylistDto> {
        val response: ApiResponse<List<PlaylistDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/playlists") {
                if (userId != null) parameter("user_id", userId)
            }
            .body()
        return response.data.orEmpty()
    }

    suspend fun fetchFavoriteMusics(page: Int = 1, limit: Int = 20): PagedResponse<MusicDto> {
        val response: ApiResponse<PagedResponse<MusicDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/favorites/music") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data ?: PagedResponse()
    }

    suspend fun fetchFavoritePlaylists(page: Int = 1, limit: Int = 20): PagedResponse<PlaylistDto> {
        val response: ApiResponse<PagedResponse<PlaylistDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/favorites/playlists") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data ?: PagedResponse()
    }

    suspend fun fetchFavoriteAlbums(page: Int = 1, limit: Int = 20): PagedResponse<AlbumDto> {
        val response: ApiResponse<PagedResponse<AlbumDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/favorites/albums") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data ?: PagedResponse()
    }

    suspend fun fetchFavoriteArtists(page: Int = 1, limit: Int = 20): PagedResponse<ArtistDto> {
        val response: ApiResponse<PagedResponse<ArtistDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/favorites/artists") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data ?: PagedResponse()
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

    /** GET /api/artists/{id} —— 歌手详情（含热门歌曲 + 专辑，后端 noAuth）。 */
    suspend fun fetchArtistDetail(id: Long): ArtistDetailDto? {
        val response: ApiResponse<ArtistDetailDto> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/artists/$id")
            .body()
        return response.data
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

    // ---------- 播放器：收藏 / 评论 / 播放记录 ----------

    /**
     * POST api/music/{id}/favorite —— **取反式**切换收藏（auth）。
     *
     * 后端语义：已收藏就删、未收藏就插，返回的 data 恒为 null，只能用 message 区分。
     * 所以这里返回布尔「调用后是否已收藏」，由调用方给出调用前的状态。
     */
    suspend fun toggleFavorite(musicId: Long, wasFavorite: Boolean): Boolean {
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/music/$musicId/favorite")
            .body()
        if (response.code != 200) throw IllegalStateException(response.message.ifBlank { "操作失败" })
        return !wasFavorite
    }

    /**
     * GET api/music-comments?music_id= —— 一级评论分页（noAuth；带 token 时返回 is_like）。
     *
     * internal：返回类型 MusicCommentDto 是模块私有 DTO，不对外暴露给 Swift，
     * 对外统一走 [FoxPlayerApi] 的 FoxComment。
     */
    internal suspend fun fetchMusicComments(
        musicId: Long,
        page: Int = 1,
        limit: Int = 20,
    ): PagedResponse<MusicCommentDto> {
        val response: ApiResponse<PagedResponse<MusicCommentDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/music-comments") {
                parameter("music_id", musicId)
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data ?: PagedResponse()
    }

    /** POST api/music-comments —— 发表评论/回复（auth）。parentId 非空即回复。 */
    suspend fun postMusicComment(musicId: Long, content: String, parentId: Long? = null): Boolean {
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/music-comments") {
                contentType(ContentType.Application.Json)
                setBody(PostCommentRequest(musicId = musicId, content = content, parentId = parentId))
            }
            .body()
        if (response.code != 200) throw IllegalStateException(response.message.ifBlank { "发布失败" })
        return true
    }

    /** POST api/music-comments/like/{id} —— 点赞/取消点赞（auth，取反式）。 */
    suspend fun likeMusicComment(commentId: Long): Boolean {
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/music-comments/like/$commentId")
            .body()
        if (response.code != 200) throw IllegalStateException(response.message.ifBlank { "操作失败" })
        return true
    }

    /**
     * POST api/music/{id}/play —— 上报播放（noAuth，但只有带 token 才会写进
     * music_play_history，"最近在听"才有数据）。
     */
    suspend fun recordPlay(musicId: Long, durationSeconds: Int = 0, progressPercent: Int = 0) {
        client.post("${FoxNetworkConfig.API_BASE_URL}api/music/$musicId/play") {
            contentType(ContentType.Application.Json)
            setBody(PlayRecordRequest(duration = durationSeconds, progress = progressPercent))
        }
    }
}

/** 网络层异常在 UI 上要可读：优先异常自带信息，缺省用兜底文案。 */
private fun Exception.readableMessage(fallback: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallback

/** UserDto → Swift 可直接消费的精简用户结构。 */
fun UserDto.toAuthUser(): AuthUser = AuthUser(
    id = id,
    username = username,
    nickname = nickname?.takeIf { it.isNotBlank() } ?: username,
    avatarUrl = avatar,
    email = email,
)
