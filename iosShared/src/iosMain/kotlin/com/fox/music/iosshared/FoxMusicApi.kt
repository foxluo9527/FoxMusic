package com.fox.music.iosshared

import com.fox.music.core.network.FoxNetworkConfig
import com.fox.music.core.network.model.AlbumDetailDto
import com.fox.music.core.network.model.AlbumDto
import com.fox.music.core.network.model.AcceptFriendRequest
import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.ArtistDetailDto
import com.fox.music.core.network.model.ArtistDto
import com.fox.music.core.network.model.CommentDto
import com.fox.music.core.network.model.ConversationDto
import com.fox.music.core.network.model.FriendDto
import com.fox.music.core.network.model.FriendRequestBody
import com.fox.music.core.network.model.FriendRequestDto
import com.fox.music.core.network.model.HighlightQueueItem
import com.fox.music.core.network.model.HighlightStreamData
import com.fox.music.core.network.model.NotificationDto
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.network.model.ForgotPasswordRequest
import com.fox.music.core.network.model.ListeningStatsDto
import com.fox.music.core.network.model.LoginRequest
import com.fox.music.core.network.model.LoginResponse
import com.fox.music.core.network.model.MarkChatReadRequest
import com.fox.music.core.network.model.MarkReadRequest
import com.fox.music.core.network.model.MessageDto
import com.fox.music.core.network.model.MusicDto
import com.fox.music.core.network.model.PagedResponse
import com.fox.music.core.network.model.PlayHistoryDto
import com.fox.music.core.network.model.PlaylistCategoryDto
import com.fox.music.core.network.model.PlaylistDetailDto
import com.fox.music.core.network.model.AddTracksRequest
import com.fox.music.core.network.model.PlaylistDto
import com.fox.music.core.network.model.RegisterRequest
import com.fox.music.core.network.model.RefreshTokenRequest
import com.fox.music.core.network.model.ResetPasswordRequest
import com.fox.music.core.network.model.TokenResponse
import com.fox.music.core.network.model.PlayRecordRequest
import com.fox.music.core.network.model.PostCommentRequest
import com.fox.music.core.network.model.PostDto
import com.fox.music.core.network.model.PostLikerDto
import com.fox.music.core.network.model.SearchedUserDto
import com.fox.music.core.network.model.SendMessageRequest
import com.fox.music.core.network.model.ShareMessageRequest
import com.fox.music.core.network.model.SetRemarkRequest
import com.fox.music.core.network.model.SocialPostCommentRequest
import com.fox.music.core.network.model.CreatePostRequest
import com.fox.music.core.network.model.PostMediaDto
import com.fox.music.core.network.model.UpdatePostRequest
import com.fox.music.core.network.model.CreatePlaylistRequest
import com.fox.music.core.network.model.UpdatePlaylistRequest
import com.fox.music.core.network.model.UploadByUrlRequest
import com.fox.music.core.network.model.ParsedPlaylistDto
import com.fox.music.core.network.model.ParsePlaylistRequest
import com.fox.music.core.network.model.ResolveAudioDto
import com.fox.music.core.network.model.ResolveAudioRequest
import com.fox.music.core.network.model.ImportLyricsDto
import com.fox.music.core.network.model.ImportChartsRequest
import com.fox.music.core.network.model.ImportChartsBatchDto
import com.fox.music.core.network.model.CreateAdminMusicRequest
import com.fox.music.core.network.model.FindMusicDto
import com.fox.music.core.network.model.FindOrCreateMusicDto
import com.fox.music.core.network.model.FindOrCreateMusicRequest
import com.fox.music.core.network.model.FindOrCreateArtistRequest
import com.fox.music.core.network.model.FindOrCreateAlbumRequest
import com.fox.music.core.network.model.ImportSessionRequest
import com.fox.music.core.network.model.ReportDto
import com.fox.music.core.network.model.SubmitReportRequest
import com.fox.music.core.network.model.TagDto
import com.fox.music.core.network.model.UploadFileDto
import com.fox.music.core.network.model.UpdateProfileRequest
import com.fox.music.core.network.model.UpdatePrivacyRequest
import com.fox.music.core.network.model.PrivacySettingsDto
import com.fox.music.core.network.model.BlacklistUserDto
import com.fox.music.core.network.model.UserDto
import com.fox.music.core.network.model.UserHomeProfileDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.serialization.json.Json
import platform.Foundation.NSData
import platform.Foundation.NSFileManager

/**
 * iOS 端 API 客户端：Ktor Darwin 引擎（底层是 NSURLSession）。
 *
 * DTO 直接复用 :core:network commonMain 里的 @Serializable 类，与 Android 端完全同一份契约
 * （路径/参数与 core:network-android 的各 *ApiService 声明一一对应）。
 *
 * 注意：后端是纯 http://，iOS ATS 默认拦截明文请求 —— 例外在 iosApp 的
 * Info.plist（NSAllowsArbitraryLoads，与 Android 的全局明文放行对齐）。
 *
 * 登录态：`Authorization: Bearer <token>` 由 Auth 插件的 BearerAuthProvider 注入，
 * 且 `cacheTokens = false` —— 每次请求都从 [FoxAuthStore] 现取，因此登录/登出后
 * 无需重建 HttpClient。最近在听（api/music-history 系列）后端强制 req.user，
 * 故仅在已登录时有数据。
 */
class FoxMusicApi {

    private val client = HttpClient(Darwin) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    // 与 Android NetworkModule.provideJson 完全对齐：
                    // coerceInputValues —— 可空字段收到 null 时用默认值；
                    // explicitNulls=false —— 编码省略 null 字段；
                    // encodeDefaults —— 请求体带默认值字段（部分后端接口依赖）。
                    coerceInputValues = true
                    encodeDefaults = true
                    explicitNulls = false
                }
            )
        }
        // 401 → 用 refreshToken 续期并自动重发（对齐 Android OkHttp 的 TokenAuthenticator）。
        // 没有这层时：access token 一过期，所有鉴权接口都 401，「我的」页拿不到任何数据，
        // 表现就是「直接退出登录态」。而服务端 token 是短期的，这个续期必须存在。
        //
        // 令牌通过 Auth 插件的 BearerTokens 注入（插件自带 `Bearer ` 前缀），
        // 所以 loadTokens 里必须先 stripBearerPrefix —— 不能再手工拼，否则又是
        // `Bearer Bearer ...` → 恒定 401（见 FoxAuthStore.normalizeBearerToken 注释）。
        install(Auth) {
            bearer {
                // ⚠️ 必须关掉缓存：BearerAuthProvider 默认(cacheTokens=true)只在首次请求调一次
                // loadTokens，之后一直用缓存里的令牌 —— 那样登出（FoxAuthStore.clear()，
                // 令牌持久化在 Swift Keychain，清除发生在 Ktor 之外）后客户端还会继续带旧
                // access token（JWT 7 天有效，服务端照样认），等于没登出。
                // 关掉后每次请求都实时读 FoxAuthStore，与原 defaultRequest 行为一致；
                // 刷新成功后插件仍会把新令牌交给本次重试（见 refreshTokens 返回值）。
                cacheTokens = false
                // 与原 defaultRequest 一致：所有请求都带上令牌（访客时 loadTokens 返回 null）。
                sendWithoutRequest { true }
                loadTokens {
                    val access = stripBearerPrefix(FoxAuthStore.token)
                    if (access.isNullOrBlank()) {
                        null
                    } else {
                        BearerTokens(access, FoxAuthStore.refreshToken.orEmpty())
                    }
                }
                refreshTokens {
                    refreshAccessToken()?.let { (access, refresh) ->
                        BearerTokens(access, refresh ?: oldTokens?.refreshToken.orEmpty())
                    }
                }
            }
        }
    }

    /**
     * 与 [client] ContentNegotiation 同一套配置，供 bodyAsText + 显式 serializer 解码使用。
     */
    private val networkJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * 不带 Authorization 的 client，仅供后端标注 noAuth、但带 token 反而会走鉴权分支
     * 500 的接口使用 —— 实测 GET api/music/{id} 携带任意有效 JWT 必返回
     * 「获取音乐详情失败」（服务端鉴权分支 bug，游客态正常）。Android 侧
     * FetchMusicLyricsUseCase 也是先吃详情失败再走 import/lyrics 兜底。
     */
    private val anonymousClient = HttpClient(Darwin) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                    encodeDefaults = true
                    explicitNulls = false
                }
            )
        }
    }

    /**
     * 401 时用 refreshToken 换新 access/refresh（POST api/auth/refresh-token）。
     *
     * 走 [anonymousClient]（无 Auth 插件）避免刷新请求自身 401 触发递归刷新。
     * 成功后写回 [FoxAuthStore]（并回调 Swift 落 Keychain）—— 服务端会**轮换**
     * refreshToken（旧 token 作废），不回写的话下次冷启动就续不了期、只能重登。
     * 返回「无前缀 access + 新 refresh」。
     *
     * 失败分两类，与 Android TokenAuthenticator 同口径：
     * - 网络/解析异常 → 返回 null，**不动登录态**（可能只是断网，不能因此把人踢下线）；
     * - 服务端明确 401 拒绝 refreshToken（无效/过期/已轮换）→ 会话真正结束，
     *   立即 [FoxAuthStore.clear] 并通知 Swift 清 Keychain + 回登录页
     *   （对齐 Android 的 `tokenManager.invalidateSession()`）。
     *   否则 iOS 会停在「UI 显示已登录、所有鉴权接口却全 401、页面空白」的僵尸态。
     */
    private suspend fun refreshAccessToken(): Pair<String, String?>? {
        val refresh = FoxAuthStore.refreshToken ?: return null
        return try {
            val http = anonymousClient
                .post("${FoxNetworkConfig.API_BASE_URL}api/auth/refresh-token") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequest(refresh))
                }
            val response: ApiResponse<TokenResponse> = http.body()
            val data = response.data
            val access = stripBearerPrefix(data?.token)
            if (response.isSuccess && access != null) {
                FoxAuthStore.updateTokens(access, data?.refreshToken)
                access to data?.refreshToken
            } else {
                if (http.status == HttpStatusCode.Unauthorized) {
                    FoxAuthStore.clear()
                    FoxAuthStore.onSessionExpired?.invoke()
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 评论分页的显式类型别名。
     *
     * 不能让 Ktor 在 Native 上按嵌套泛型
     * `ApiResponse<PagedResponse<MusicCommentDto>>` 运行时反射查找：MusicCommentDto 是
     * iosMain 的 internal 类，K/N 上 PagedResponse 泛型参数的序列化器无法被
     * serializerForType 解析，线上实测报
     * "Serializer for type argument 'PagedResponse' is not found for 'ApiResponse'"。
     * iosShared 已启用 kotlinx.serialization 编译插件，reified 泛型在调用点会被
     * 插件改写成编译期生成的 serializer 链，完全绕开运行时反射。
     */
    private suspend fun HttpResponse.parseMusicCommentPage(): PagedResponse<MusicCommentDto> {
        val response: ApiResponse<PagedResponse<MusicCommentDto>> =
            networkJson.decodeFromString(bodyAsText())
        return response.data ?: PagedResponse()
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

    /** PUT api/auth/profile —— 更新资料（auth）。传 null 的字段不修改。 */
    suspend fun updateProfile(
        nickname: String? = null,
        avatar: String? = null,
        signature: String? = null,
        email: String? = null,
        homepageBackground: String? = null,
    ): UserDto? {
        val response: ApiResponse<UserDto> = client
            .put("${FoxNetworkConfig.API_BASE_URL}api/auth/profile") {
                contentType(ContentType.Application.Json)
                setBody(UpdateProfileRequest(
                    nickname = nickname, avatar = avatar, signature = signature,
                    email = email, homepageBackground = homepageBackground,
                ))
            }
            .body()
        if (!response.isSuccess) throw IllegalStateException(response.message.ifBlank { "更新资料失败" })
        return response.data
    }

    /** GET api/auth/privacy —— 隐私设置（auth）。 */
    suspend fun fetchPrivacySettings(): PrivacySettingsDto = try {
        val response: ApiResponse<PrivacySettingsDto> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/auth/privacy")
            .body()
        response.data ?: PrivacySettingsDto()
    } catch (e: Exception) {
        PrivacySettingsDto()
    }

    /** PUT api/auth/privacy —— 更新隐私设置（auth）。 */
    suspend fun updatePrivacySettings(isPrivate: Boolean? = null, isSearchable: Boolean? = null): Boolean = try {
        val response: ApiResponse<Unit> = client
            .put("${FoxNetworkConfig.API_BASE_URL}api/auth/privacy") {
                contentType(ContentType.Application.Json)
                setBody(UpdatePrivacyRequest(isPrivate = isPrivate, isSearchable = isSearchable))
            }
            .body()
        response.isSuccess
    } catch (e: Exception) {
        false
    }

    /** GET api/auth/blacklist —— 黑名单列表（auth）。后端 data 是分页对象 {list,total,...}。 */
    suspend fun fetchBlacklist(): List<BlacklistUserDto> = try {
        val response: ApiResponse<PagedResponse<BlacklistUserDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/auth/blacklist")
            .body()
        response.data?.list.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    /** DELETE api/auth/blacklist/{userId} —— 取消拉黑（auth）。 */
    suspend fun unblockUser(userId: Long): Boolean = try {
        val response: ApiResponse<Unit> = client
            .delete("${FoxNetworkConfig.API_BASE_URL}api/auth/blacklist/$userId")
            .body()
        response.isSuccess
    } catch (e: Exception) {
        false
    }

    /**
     * POST api/users/{userId}/block —— 拉黑用户（auth）。
     * 对齐 Android UserApiService.blockUser；后端会自动解除好友关系。
     * 失败时抛异常带后端 message，由调用方提示。
     */
    suspend fun blockUser(userId: Long) {
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/users/$userId/block")
            .body()
        if (!response.isSuccess) {
            throw IllegalStateException(response.message.ifBlank { "操作失败" })
        }
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

    /** POST api/playlists/{id}/tracks —— 添加歌曲到歌单（auth）。 */
    suspend fun addMusicToPlaylist(playlistId: Long, musicId: Long): Boolean {
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/playlists/$playlistId/tracks") {
                contentType(ContentType.Application.Json)
                setBody(AddTracksRequest(musicIds = listOf(musicId)))
            }
            .body()
        return response.isSuccess
    }

    // ---------- 歌单管理：创建 / 编辑 / 删除 / 导入 ----------
    // 与 Android PlaylistApiService、ImportApiService、UploadApiService 同契约。

    /** POST api/playlists —— 创建歌单（auth）。 */
    suspend fun createPlaylist(
        title: String,
        description: String? = null,
        coverImage: String? = null,
        isPublic: Boolean = true,
    ): PlaylistDto {
        val response: ApiResponse<PlaylistDto> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/playlists") {
                contentType(ContentType.Application.Json)
                setBody(
                    CreatePlaylistRequest(
                        title = title,
                        description = description,
                        coverImage = coverImage,
                        isPublic = isPublic,
                    )
                )
            }
            .body()
        if (!response.isSuccess) throw IllegalStateException(response.message.ifBlank { "创建歌单失败" })
        return response.data ?: throw IllegalStateException("创建歌单结果缺失")
    }

    /** PUT api/playlists/{id} —— 编辑歌单（auth）。传 null 的字段不修改。 */
    suspend fun updatePlaylist(
        playlistId: Long,
        title: String? = null,
        description: String? = null,
        coverImage: String? = null,
        isPublic: Boolean? = null,
    ): PlaylistDto {
        val response: ApiResponse<PlaylistDto> = client
            .put("${FoxNetworkConfig.API_BASE_URL}api/playlists/$playlistId") {
                contentType(ContentType.Application.Json)
                setBody(
                    UpdatePlaylistRequest(
                        title = title,
                        description = description,
                        coverImage = coverImage,
                        isPublic = isPublic,
                    )
                )
            }
            .body()
        if (!response.isSuccess) throw IllegalStateException(response.message.ifBlank { "保存失败" })
        return response.data ?: throw IllegalStateException("保存结果缺失")
    }

    /** DELETE api/playlists/{id} —— 删除歌单（auth）。 */
    suspend fun deletePlaylist(playlistId: Long) {
        val response: ApiResponse<Unit> = client
            .delete("${FoxNetworkConfig.API_BASE_URL}api/playlists/$playlistId")
            .body()
        if (!response.isSuccess) throw IllegalStateException(response.message.ifBlank { "删除歌单失败" })
    }

    /** POST api/playlists/{id}/import-session —— 更新导入进度（auth）。 */
    suspend fun updateImportSession(
        playlistId: Long,
        totalTracks: Int? = null,
        importedTracks: Int? = null,
        status: String? = null,
        error: String? = null,
    ): Boolean {
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/playlists/$playlistId/import-session") {
                contentType(ContentType.Application.Json)
                setBody(
                    ImportSessionRequest(
                        totalTracks = totalTracks,
                        importedTracks = importedTracks,
                        status = status,
                        error = error,
                    )
                )
            }
            .body()
        return response.isSuccess
    }

    /** POST api/import/parse —— 解析第三方歌单链接（网易云音乐/QQ音乐）。 */
    suspend fun parseImportPlaylist(url: String): ParsedPlaylistDto {
        val response: ApiResponse<ParsedPlaylistDto> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/import/parse") {
                contentType(ContentType.Application.Json)
                setBody(ParsePlaylistRequest(url = url))
            }
            .body()
        if (!response.isSuccess) throw IllegalStateException(response.message.ifBlank { "解析失败" })
        return response.data ?: throw IllegalStateException("解析结果缺失")
    }

    /** POST api/upload/image —— 按 URL 转存图片到自有存储（auth），返回新 URL。 */
    suspend fun uploadImageByUrl(url: String, referer: String? = null): String {
        val response: ApiResponse<UploadFileDto> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/upload/image") {
                contentType(ContentType.Application.Json)
                setBody(UploadByUrlRequest(url = url, referer = referer))
            }
            .body()
        if (!response.isSuccess) throw IllegalStateException(response.message.ifBlank { "图片转存失败" })
        return response.data?.url ?: throw IllegalStateException("图片转存结果缺失")
    }

    /** POST api/upload/audio —— 按 URL 转存音频到自有存储（auth），导入曲目时绕开第三方防盗链。 */
    suspend fun uploadAudioByUrl(url: String, referer: String? = null): String {
        val response: ApiResponse<UploadFileDto> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/upload/audio") {
                contentType(ContentType.Application.Json)
                setBody(UploadByUrlRequest(url = url, referer = referer))
            }
            .body()
        if (!response.isSuccess) throw IllegalStateException(response.message.ifBlank { "音频转存失败" })
        return response.data?.url ?: throw IllegalStateException("音频转存结果缺失")
    }

    // region 曲库管理（admin）

    /** GET api/admin/music —— 曲库分页（auth，需管理员；keyword 非空时服务端过滤）。 */
    suspend fun fetchAdminMusicPage(
        page: Int = 1,
        limit: Int = 20,
        keyword: String? = null,
    ): PagedResponse<MusicDto> {
        val response: ApiResponse<PagedResponse<MusicDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/admin/music") {
                parameter("page", page)
                parameter("limit", limit)
                if (!keyword.isNullOrBlank()) parameter("keyword", keyword)
            }
            .body()
        if (response.code != 200) {
            throw IllegalStateException(response.message.ifBlank { "获取曲库失败" })
        }
        return response.data ?: PagedResponse()
    }

    /** DELETE api/admin/music/{id} —— 删除单曲（auth，需管理员）。 */
    suspend fun deleteAdminMusic(musicId: Long) {
        val response: ApiResponse<Unit> = client
            .delete("${FoxNetworkConfig.API_BASE_URL}api/admin/music/$musicId")
            .body()
        if (response.code != 200) {
            throw IllegalStateException(response.message.ifBlank { "删除失败" })
        }
    }

    /** POST api/admin/music —— 直接创建单曲（第三方搜索结果入库主链路，auth，需管理员）。 */
    suspend fun createAdminMusic(request: CreateAdminMusicRequest) {
        val response: ApiResponse<kotlinx.serialization.json.JsonElement> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/admin/music") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .body()
        if (response.code != 200) {
            throw IllegalStateException(response.message.ifBlank { "创建音乐失败" })
        }
    }

    /**
     * POST api/import/netease/charts —— 批量更新网易云排行榜（platform=mobile）。
     * 返回成功/失败计数。
     */
    suspend fun importNeteaseCharts(platform: String = "mobile"): ImportChartsBatchDto {
        val response: ApiResponse<ImportChartsBatchDto> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/import/netease/charts") {
                contentType(ContentType.Application.Json)
                setBody(ImportChartsRequest(platform = platform))
            }
            .body()
        val data = response.data
        if (response.code != 200 || data == null) {
            throw IllegalStateException(response.message.ifBlank { "更新排行榜失败" })
        }
        return data
    }

    /** POST api/import/hot-playlists —— 触发热门歌单同步（platform=mobile），成功提示取服务端 message。 */
    suspend fun importHotPlaylists(platform: String = "mobile"): String {
        val response: ApiResponse<kotlinx.serialization.json.JsonElement> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/import/hot-playlists") {
                contentType(ContentType.Application.Json)
                setBody(ImportChartsRequest(platform = platform))
            }
            .body()
        if (response.code != 200) {
            throw IllegalStateException(response.message.ifBlank { "更新热门歌单失败" })
        }
        return response.message.ifBlank { "热门歌单同步任务已启动，正在后台处理" }
    }

    // endregion

    /** GET api/music/find —— 曲库查找（title+artist），未命中或出错返回 null。 */
    suspend fun findMusicId(title: String, artist: String): Long? = try {
        val response: ApiResponse<FindMusicDto> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/music/find") {
                parameter("title", title)
                if (artist.isNotBlank()) parameter("artist", artist)
            }
            .body()
        if (response.isSuccess) response.data?.musicId else null
    } catch (_: Exception) {
        null
    }

    /**
     * POST api/import/resolve-audio —— 服务端解析可用音源（iOS 导入的主链路）。
     * [durationMs] 有值时服务端优先走 API 多源（网易云/酷我/插件源，自带官方时长），
     * 并按原曲时长过滤 30s 试听片段/错版本。
     */
    suspend fun resolveAudio(title: String, artist: String, durationMs: Long? = null): ResolveAudioDto {
        val response: ApiResponse<ResolveAudioDto> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/import/resolve-audio") {
                contentType(ContentType.Application.Json)
                setBody(
                    ResolveAudioRequest(
                        title = title,
                        artist = artist,
                        durationMs = durationMs?.takeIf { it > 0 },
                    ),
                )
            }
            .body()
        val data = response.data
        if (!response.isSuccess || data == null || data.url.isBlank()) {
            throw IllegalStateException(response.message.ifBlank { "未解析到可用音频" })
        }
        return data
    }

    /** GET api/import/lyrics —— 按 platform+externalId 拉歌词（失败由调用方兜底）。 */
    suspend fun fetchImportLyrics(platform: String, externalId: String): ImportLyricsDto {
        val response: ApiResponse<ImportLyricsDto> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/import/lyrics") {
                parameter("platform", platform)
                parameter("id", externalId)
            }
            .body()
        if (!response.isSuccess) throw IllegalStateException(response.message.ifBlank { "歌词拉取失败" })
        return response.data ?: ImportLyricsDto()
    }

    /** POST api/music/find-or-create —— 歌曲入库（auth），返回 musicId。 */
    suspend fun findOrCreateMusic(
        title: String,
        url: String,
        coverImage: String? = null,
        durationMs: Long = 0,
        artists: List<String> = emptyList(),
        lyrics: String = "",
        lyricsTrans: String = "",
        albumName: String? = null,
        albumCover: String? = null,
        trackNumber: Int = 1,
        platform: String? = null,
        externalId: String? = null,
    ): Long {
        val response: ApiResponse<FindOrCreateMusicDto> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/music/find-or-create") {
                contentType(ContentType.Application.Json)
                setBody(
                    FindOrCreateMusicRequest(
                        title = title,
                        url = url,
                        coverImage = coverImage,
                        duration = durationMs.coerceAtLeast(0),
                        lyrics = lyrics,
                        lyricsTrans = lyricsTrans,
                        artists = artists.filter { it.isNotBlank() }.map { FindOrCreateArtistRequest(it) },
                        album = albumName?.takeIf { it.isNotBlank() }?.let {
                            FindOrCreateAlbumRequest(title = it, coverImage = albumCover)
                        },
                        trackNumber = trackNumber,
                        platform = platform,
                        externalId = externalId,
                    )
                )
            }
            .body()
        if (!response.isSuccess) throw IllegalStateException(response.message.ifBlank { "歌曲入库失败" })
        return response.data?.musicId ?: throw IllegalStateException("歌曲入库结果缺失")
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

    /** GET /api/music 的单页结果（除列表外还要拿到 recommendSeed）。 */
    data class MusicListPage(
        val items: List<MusicDto> = emptyList(),
        val recommendSeed: String? = null,
        val total: Int = 0,
    )

    /**
     * GET /api/music —— 新歌/推荐歌曲分页（后端 noAuth）。
     *
     * [seed] / [excludeIds] 与 Android MusicApiService 同名参数对齐，**翻页必须带**：
     * 后端 sort=recommend 的 ORDER BY 是 `推荐分 DESC, MD5(id + seed) ASC`，
     * 不传 seed 时服务端每次请求都新生成一个 UUID → 各页顺序互相错位、结果重复。
     */
    suspend fun fetchMusicListPage(
        page: Int = 1,
        limit: Int = 20,
        sort: String? = null,
        seed: String? = null,
        excludeIds: String? = null,
    ): MusicListPage {
        val response: ApiResponse<PagedResponse<MusicDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/music") {
                parameter("page", page)
                parameter("limit", limit)
                if (sort != null) parameter("sort", sort)
                if (seed != null) parameter("seed", seed)
                if (excludeIds != null) parameter("exclude_ids", excludeIds)
            }
            .body()
        val data = response.data
        return MusicListPage(
            items = data?.list.orEmpty(),
            recommendSeed = data?.recommendSeed,
            total = data?.total ?: 0,
        )
    }

    /** 只要列表、不要 seed 的简写（发现页第一页/预取池用）。 */
    suspend fun fetchMusicList(page: Int = 1, limit: Int = 20, sort: String? = null): List<MusicDto> =
        fetchMusicListPage(page, limit, sort).items

    /** GET api/music/{id} —— 单曲详情（noAuth）。通知跳转音乐评论时用它换歌曲信息。
     *  必须走 [anonymousClient]：带有效 JWT 时服务端鉴权分支恒定 500（见其注释）。 */
    suspend fun fetchMusicDetail(musicId: Long): MusicDto {
        val response: ApiResponse<MusicDto> = anonymousClient
            .get("${FoxNetworkConfig.API_BASE_URL}api/music/$musicId")
            .body()
        return response.data ?: throw IllegalStateException(response.message.ifBlank { "歌曲不存在" })
    }

    /** GET api/import/lyrics —— noAuth，同样走 [anonymousClient] 规避鉴权分支 500。 */
    suspend fun fetchImportLyricsAnon(platform: String, externalId: String): ImportLyricsDto {
        val response: ApiResponse<ImportLyricsDto> = anonymousClient
            .get("${FoxNetworkConfig.API_BASE_URL}api/import/lyrics") {
                parameter("platform", platform)
                parameter("id", externalId)
            }
            .body()
        if (!response.isSuccess) throw IllegalStateException(response.message.ifBlank { "歌词拉取失败" })
        return response.data ?: ImportLyricsDto()
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

    /** GET /api/playlist-categories —— 歌单分类（全部歌单页 Tab，与 Android getCategories 同契约）。 */
    suspend fun fetchPlaylistCategories(): List<PlaylistCategoryDto> {
        val response: ApiResponse<List<PlaylistCategoryDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/playlist-categories")
            .body()
        return response.data.orEmpty()
    }

    /** GET /api/playlist-categories/{id}/playlists —— 分类下歌单分页。 */
    suspend fun fetchCategoryPlaylists(categoryId: Long, page: Int = 1, limit: Int = 20): PagedResponse<PlaylistDto> {
        val response: ApiResponse<PagedResponse<PlaylistDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/playlist-categories/$categoryId/playlists") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data ?: PagedResponse()
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
    suspend fun fetchAlbums(page: Int = 1, limit: Int = 20, sort: String? = null): List<AlbumDto> {
        val response: ApiResponse<PagedResponse<AlbumDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/albums") {
                parameter("page", page)
                parameter("limit", limit)
                if (sort != null) parameter("sort", sort)
            }
            .body()
        return response.data?.list.orEmpty()
    }

    /** GET /api/albums/{id} —— 专辑详情含 tracks 分页（后端 noAuth）。 */
    suspend fun fetchAlbumDetail(albumId: Long, page: Int = 1, limit: Int = 20): AlbumDetailDto? {
        val response: ApiResponse<AlbumDetailDto> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/albums/$albumId") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data
    }

    /** GET /api/artists —— 歌手列表分页（后端 noAuth）。sort="hot" 为热门歌手。 */
    suspend fun fetchArtists(page: Int = 1, limit: Int = 20, sort: String? = null): List<ArtistDto> =
        fetchArtistsPaged(page, limit, sort).list

    /** 分页版（全部歌手页循环翻页需要 hasMore/total）。 */
    internal suspend fun fetchArtistsPaged(
        page: Int = 1,
        limit: Int = 20,
        sort: String? = null,
    ): PagedResponse<ArtistDto> {
        val response: ApiResponse<PagedResponse<ArtistDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/artists") {
                parameter("page", page)
                parameter("limit", limit)
                if (sort != null) parameter("sort", sort)
            }
            .body()
        return response.data ?: PagedResponse()
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
        return client
            .get("${FoxNetworkConfig.API_BASE_URL}api/music-comments") {
                parameter("music_id", musicId)
                parameter("page", page)
                parameter("limit", limit)
            }
            .parseMusicCommentPage()
    }

    /**
     * GET api/music-comments/{id}/replies —— 某条一级评论的回复分页（noAuth）。
     * 返回项带 music_id / parent_id，通知跳转也靠它反查歌曲。
     */
    internal suspend fun fetchMusicCommentReplies(
        commentId: Long,
        page: Int = 1,
        limit: Int = 20,
    ): PagedResponse<MusicCommentDto> {
        return client
            .get("${FoxNetworkConfig.API_BASE_URL}api/music-comments/$commentId/replies") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .parseMusicCommentPage()
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

    // ---------- 消息 / 通知 / 好友申请 ----------
    // 路径与参数对齐 :core:network-android 的 ChatApiService 与 SocialApiService。
    // 这三者都是 auth 接口，登录态由 defaultRequest 的 Bearer 头带上。

    /** GET api/messages/conversations —— 会话列表（含最后一条消息与未读数）。 */
    suspend fun fetchConversations(): List<ConversationDto> {
        val response: ApiResponse<List<ConversationDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/messages/conversations")
            .body()
        return if (response.isSuccess) response.data.orEmpty() else emptyList()
    }

    /** GET api/friends/requests —— 收到的好友申请（消息页「我的好友」入口的角标来源）。 */
    suspend fun fetchFriendRequests(): List<FriendRequestDto> {
        val response: ApiResponse<List<FriendRequestDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/friends/requests")
            .body()
        return if (response.isSuccess) response.data.orEmpty() else emptyList()
    }

    /** GET api/friends —— 好友列表（含备注 mark）。 */
    suspend fun fetchFriends(): List<FriendDto> {
        val response: ApiResponse<List<FriendDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/friends")
            .body()
        return if (response.isSuccess) response.data.orEmpty() else emptyList()
    }

    /** GET api/friends/search?keyword= —— 按用户名/昵称搜人（加好友用）。 */
    suspend fun searchUsers(keyword: String): List<SearchedUserDto> {
        val response: ApiResponse<List<SearchedUserDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/friends/search") {
                parameter("keyword", keyword)
            }
            .body()
        if (!response.isSuccess) {
            throw IllegalStateException(response.message.ifBlank { "搜索失败" })
        }
        return response.data.orEmpty()
    }

    /** POST api/friends/request —— 发送好友申请；失败抛错交给调用方出提示。 */
    suspend fun sendFriendRequest(friendId: Long, message: String, mark: String? = null) {
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/friends/request") {
                contentType(ContentType.Application.Json)
                setBody(FriendRequestBody(friendId = friendId, message = message, mark = mark))
            }
            .body()
        if (!response.isSuccess) {
            throw IllegalStateException(response.message.ifBlank { "发送失败" })
        }
    }

    /** POST api/friends/accept —— 接受好友申请（requestId 是申请记录 id，非用户 id）。 */
    suspend fun acceptFriendRequest(requestId: Long) {
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/friends/accept") {
                contentType(ContentType.Application.Json)
                setBody(AcceptFriendRequest(requestId = requestId))
            }
            .body()
        if (!response.isSuccess) {
            throw IllegalStateException(response.message.ifBlank { "接受申请失败" })
        }
    }

    /** POST api/friends/remark —— 设置好友备注。 */
    suspend fun setFriendRemark(friendId: Long, remark: String) {
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/friends/remark") {
                contentType(ContentType.Application.Json)
                setBody(SetRemarkRequest(friendId = friendId, remark = remark))
            }
            .body()
        if (!response.isSuccess) {
            throw IllegalStateException(response.message.ifBlank { "备注更新失败" })
        }
    }

    /**
     * GET api/users/{userId} —— 用户主页资料（后端已算好 is_friend / is_requested /
     * is_private / is_self / can_view_content），是用户资料页的主数据源。
     */
    suspend fun fetchUserHomeProfile(userId: Long): UserHomeProfileDto? {
        val response: ApiResponse<UserHomeProfileDto> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/users/$userId")
            .body()
        if (!response.isSuccess) {
            throw IllegalStateException(response.message.ifBlank { "加载失败" })
        }
        return response.data
    }

    // ---------- 聊天（会话详情） ----------
    // 路径与参数对齐 :core:network-android 的 ChatApiService。

    /**
     * GET api/messages/history/{userId} —— 与某人的聊天记录（按页倒序返回）。
     *
     * 注意：Android 侧的消息主源是本地 Room（`ChatRepository.observeMessages`），
     * 这个接口在 Android 上被标记为 Deprecated；iOS 没有本地库，只能以它为准。
     */
    suspend fun fetchChatHistory(
        userId: Long,
        page: Int = 1,
        limit: Int = 50,
    ): PagedResponse<MessageDto> {
        val response: ApiResponse<PagedResponse<MessageDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/messages/history/$userId") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        if (!response.isSuccess) {
            throw IllegalStateException(response.message.ifBlank { "聊天记录加载失败" })
        }
        return response.data ?: PagedResponse()
    }

    /** GET api/messages/unread/{peerUserId} —— 定向补拉未读（回前台/进会话时兜底）。 */
    suspend fun fetchUnreadMessages(peerUserId: Long): List<MessageDto> {
        val response: ApiResponse<List<MessageDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/messages/unread/$peerUserId")
            .body()
        return if (response.isSuccess) response.data.orEmpty() else emptyList()
    }

    /** POST api/messages —— 发送文本消息（auth）。返回落库后的消息体。 */
    suspend fun sendTextMessage(receiverId: Long, content: String): MessageDto? {
        val response: ApiResponse<MessageDto> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/messages") {
                contentType(ContentType.Application.Json)
                setBody(
                    SendMessageRequest(
                        receiverId = receiverId,
                        content = content,
                        type = "text",
                    ),
                )
            }
            .body()
        if (!response.isSuccess) {
            throw IllegalStateException(response.message.ifBlank { "发送失败" })
        }
        return response.data
    }

    /**
     * POST api/messages/ —— 发送媒体消息（image/video/audio/file）。
     * 对齐 Android SendMessageWorker：
     *  - image/video/file → type=file, content="[file]name", file_url, file_type, file_name, video_url
     *  - audio → type=voice, content="/", voice_url, voice_duration(秒)
     *
     * ⚠️ 路径必须带**结尾斜杠**（与 Android ChatApiService 里 Map 版 sendMessage 的
     * `api/messages/` 一致）。服务端 fileConfirm 的配置 key 是 '/messages/'，
     * 而 getBasePath 对 `/api/messages` 解析出的是 '/messages'，匹配不上 →
     * 上传的媒体不会从 temp 确认入库到 OSS，返回的 `/uploads/oss/...` 会 404
     * （表现为语音气泡能显示、一点播放就"语音下载失败"）。
     *
     * ⚠️ 语音必须发 type=voice，**不能**用 Android 传输层的 type=text：
     * 服务端只在 type=voice 时才回吐 voice_url / voice_duration（实测 send /
     * unread / history 三个接口都如此）。发 type=text 时服务端把语音列丢掉，
     * 回包被 toMessage() 降级成 content="/" 的 TEXT 气泡（发送方看到的就是一个斜杠）。
     */
    suspend fun sendMediaMessage(
        receiverId: Long,
        type: String,
        fileUrl: String,
        fileName: String? = null,
        fileType: String? = null,
        durationMs: Long? = null,
        videoUrl: String? = null,
    ): MessageDto? {
        val request = when (type) {
            "audio", "voice" -> SendMessageRequest(
                receiverId = receiverId,
                content = "/",
                type = "voice",
                voiceUrl = fileUrl,
                voiceDuration = ((durationMs ?: 0L) / 1000L).coerceAtLeast(1L),
            )
            else -> SendMessageRequest(
                receiverId = receiverId,
                content = "[file]${fileName ?: "file"}",
                type = "file",
                fileUrl = fileUrl,
                // ⚠️ 这三个字段**必须非 null**：Ktor 的 Json 默认 encodeDefaults=false，
                // 值为 null（等于默认值）的字段会被整个从 JSON 里丢掉，后端解构出
                // undefined → mysql2 抛 "Bind parameters must not contain undefined"。
                // Android 能发是因为 Worker 总是填真实文件名/大小；iOS 这里没有真实值时
                // 也给兜底值，保证字段一定出现在请求体里。
                fileType = fileType ?: when (type) {
                    "image" -> "image"
                    "video" -> "video"
                    else -> "other"
                },
                fileName = fileName ?: when (type) {
                    "image" -> "image.jpg"
                    "video" -> "video.mp4"
                    else -> "file"
                },
                fileSize = "0",
                videoUrl = videoUrl,
            )
        }
        val response: ApiResponse<MessageDto> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/messages/") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .body()
        if (!response.isSuccess) {
            throw IllegalStateException(response.message.ifBlank { "发送失败" })
        }
        return response.data
    }

    /**
     * POST api/messages/share —— 发送名片/卡片消息（音乐/歌单/专辑/歌手/帖子/用户）。
     * 对齐 Android ChatApiService.sendShareMessage / ChatRepository.sendShareMessage。
     */
    suspend fun sendShareMessage(
        receiverId: Long,
        shareType: String,
        shareId: Long,
        content: String = "",
    ): MessageDto? {
        val response: ApiResponse<MessageDto> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/messages/share") {
                contentType(ContentType.Application.Json)
                setBody(
                    ShareMessageRequest(
                        receiverId = receiverId,
                        shareType = shareType,
                        shareId = shareId,
                        content = content,
                    ),
                )
            }
            .body()
        if (!response.isSuccess) {
            throw IllegalStateException(response.message.ifBlank { "分享失败" })
        }
        return response.data
    }

    /** POST api/messages/read —— 标记与某人的会话已读。 */
    suspend fun markChatAsRead(targetId: Long) {
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/messages/read") {
                contentType(ContentType.Application.Json)
                setBody(MarkChatReadRequest(targetId = targetId))
            }
            .body()
        if (!response.isSuccess) {
            throw IllegalStateException(response.message.ifBlank { "标记已读失败" })
        }
    }

    /** POST api/messages/{id}/recall —— 撤回消息（auth，限本人且在两分钟内）。 */
    suspend fun recallMessage(messageId: Long) {
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/messages/$messageId/recall")
            .body()
        if (!response.isSuccess) {
            throw IllegalStateException(response.message.ifBlank { "撤回失败" })
        }
    }

    /** GET api/notifications —— 通知分页（type 留空即全部，客户端再按类型归类）。 */
    suspend fun fetchNotifications(
        page: Int = 1,
        limit: Int = 50,
        type: String? = null,
    ): PagedResponse<NotificationDto> {
        val response: ApiResponse<PagedResponse<NotificationDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/notifications") {
                parameter("page", page)
                parameter("limit", limit)
                if (!type.isNullOrBlank()) parameter("type", type)
            }
            .body()
        return response.data ?: PagedResponse()
    }

    /** POST api/notifications/read —— 标记若干条通知已读。 */
    suspend fun markNotificationsRead(ids: List<Long>): Boolean {
        if (ids.isEmpty()) return true
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/notifications/read") {
                contentType(ContentType.Application.Json)
                setBody(MarkReadRequest(ids = ids))
            }
            .body()
        return response.isSuccess
    }

    // ---------- 社区（帖子 / 评论 / 点赞） ----------
    // 路径与参数对齐 :core:network-android 的 SocialApiService。

    /**
     * GET api/posts —— 帖子分页（noAuth；sort 枚举 latest/hot/recommend）。
     * [authorId] 非空时按作者过滤（个人主页「动态」Tab，参数 author_id）。
     */
    suspend fun fetchPostPage(
        page: Int = 1,
        limit: Int = 20,
        sort: String = "latest",
        tag: String? = null,
        keyword: String? = null,
        authorId: Long? = null,
    ): PagedResponse<PostDto> {
        val response: ApiResponse<PagedResponse<PostDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/posts") {
                parameter("page", page)
                parameter("limit", limit)
                parameter("sort", sort)
                if (!tag.isNullOrBlank()) parameter("tag", tag)
                if (!keyword.isNullOrBlank()) parameter("keyword", keyword)
                if (authorId != null) parameter("author_id", authorId)
            }
            .body()
        if (!response.isSuccess) {
            throw IllegalStateException(response.message.ifBlank { "动态加载失败" })
        }
        return response.data ?: PagedResponse()
    }

    /** GET api/posts/{id} —— 帖子详情（noAuth）。 */
    suspend fun fetchPostDetail(postId: Long): PostDto? {
        val response: ApiResponse<PostDto> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/posts/$postId")
            .body()
        return if (response.isSuccess) response.data else null
    }

    /** POST api/posts/{id}/like —— 取反式点赞（auth）。 */
    suspend fun togglePostLike(postId: Long): Boolean {
        val response: ApiResponse<Unit> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/posts/$postId/like")
            .body()
        return response.code == 200
    }

    /** DELETE api/posts/{id} —— 删除自己的动态（auth）。失败抛异常带后端 message。 */
    suspend fun deletePost(postId: Long) {
        val response: ApiResponse<Unit> = client
            .delete("${FoxNetworkConfig.API_BASE_URL}api/posts/$postId")
            .body()
        if (!response.isSuccess) {
            throw IllegalStateException(response.message.ifBlank { "删除失败" })
        }
    }

    /**
     * GET api/highlight/stream —— 音流模式队列（每首歌附高潮片段 clips）。
     * 契约与 Android HighlightApiService.getStream 一致
     * （query: mood/userId/limit/seed/exclude）。
     */
    suspend fun fetchHighlightStream(
        mood: String? = null,
        userId: Long? = null,
        limit: Int = 30,
        seed: String? = null,
        exclude: List<Long> = emptyList(),
    ): List<HighlightQueueItem> {
        val response: ApiResponse<HighlightStreamData> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/highlight/stream") {
                if (!mood.isNullOrBlank()) parameter("mood", mood)
                if (userId != null) parameter("userId", userId)
                parameter("limit", limit)
                if (!seed.isNullOrBlank()) parameter("seed", seed)
                if (exclude.isNotEmpty()) parameter("exclude", exclude.joinToString(","))
            }
            .body()
        if (response.code != 200) {
            throw IllegalStateException(response.message.ifBlank { "音流队列获取失败" })
        }
        return response.data?.queue ?: emptyList()
    }

    /** GET api/posts/{id}/likes —— 点赞者分页（noAuth）。 */
    suspend fun fetchPostLikes(
        postId: Long,
        page: Int = 1,
        limit: Int = 20,
    ): PagedResponse<PostLikerDto> {
        val response: ApiResponse<PagedResponse<PostLikerDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/posts/$postId/likes") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data ?: PagedResponse()
    }

    /** GET api/posts/{id}/comments —— 一级评论分页（noAuth）。 */
    suspend fun fetchPostComments(
        postId: Long,
        page: Int = 1,
        limit: Int = 20,
    ): PagedResponse<CommentDto> {
        val response: ApiResponse<PagedResponse<CommentDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/posts/$postId/comments") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data ?: PagedResponse()
    }

    /** GET api/posts/comments/{id}/replies —— 某条评论的回复分页（noAuth）。 */
    suspend fun fetchPostCommentReplies(
        commentId: Long,
        page: Int = 1,
        limit: Int = 20,
    ): PagedResponse<CommentDto> {
        val response: ApiResponse<PagedResponse<CommentDto>> = client
            .get("${FoxNetworkConfig.API_BASE_URL}api/posts/comments/$commentId/replies") {
                parameter("page", page)
                parameter("limit", limit)
            }
            .body()
        return response.data ?: PagedResponse()
    }

    /** POST api/posts/{id}/comments —— 发表评论/回复（auth，parentId 非空即回复）。 */
    suspend fun postComment(postId: Long, content: String, parentId: Long? = null): Boolean {
        val response: ApiResponse<CommentDto> = client
            .post("${FoxNetworkConfig.API_BASE_URL}api/posts/$postId/comments") {
                contentType(ContentType.Application.Json)
                setBody(SocialPostCommentRequest(content = content, parentId = parentId))
            }
            .body()
        if (response.code != 200) throw IllegalStateException(response.message.ifBlank { "评论失败" })
        return true
    }

    /** DELETE api/posts/comments/{id} —— 删除自己的评论（auth）。 */
    suspend fun deletePostComment(commentId: Long): Boolean {
        val response: ApiResponse<Unit> = client
            .delete("${FoxNetworkConfig.API_BASE_URL}api/posts/comments/$commentId")
            .body()
        if (response.code != 200) throw IllegalStateException(response.message.ifBlank { "删除失败" })
        return true
    }

    // ---- 发布动态：媒体上传 + 创建帖子（与 Android CreatePostUseCase / SocialApiService 同契约） ----

    /** POST api/upload/image —— 上传图片，返回可访问 URL（auth）。 */
    suspend fun uploadImage(filePath: String): String = uploadMediaFile(filePath, "image/*", "api/upload/image")

    /** POST api/upload/video —— 上传视频，返回可访问 URL（auth）。 */
    suspend fun uploadVideo(filePath: String): String = uploadMediaFile(filePath, "video/*", "api/upload/video")

    /** POST api/upload/audio —— 上传语音，返回可访问 URL（auth）。 */
    suspend fun uploadAudio(filePath: String): String = uploadMediaFile(filePath, "audio/*", "api/upload/audio")

    /** POST api/upload/file —— 上传通用文件，返回可访问 URL（auth）。 */
    suspend fun uploadFile(filePath: String): String = uploadMediaFile(filePath, "application/octet-stream", "api/upload/file")

    private suspend fun uploadMediaFile(filePath: String, mime: String, endpoint: String): String {
        val bytes = readFileBytes(filePath)
        val name = filePath.substringAfterLast('/').ifBlank { "file.bin" }
        val response: ApiResponse<UploadFileDto> = client.post("${FoxNetworkConfig.API_BASE_URL}$endpoint") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, mime)
                                append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                            },
                        )
                    },
                ),
            )
        }.body()
        if (response.code != 200) throw IllegalStateException(response.message.ifBlank { "上传失败" })
        return response.data?.url ?: throw IllegalStateException("上传结果缺少 URL")
    }

    /** POST api/posts —— 创建帖子（auth）。media 为已上传的远程媒体；images 为纯图片 URL 列表。 */
    suspend fun createPost(
        title: String,
        content: String,
        images: List<String>? = null,
        media: List<PostMediaDto>? = null,
        tagNames: List<String>? = null,
        tagIds: List<Long>? = null,
        musicId: Long? = null,
    ): PostDto {
        val response: ApiResponse<PostDto> = client.post("${FoxNetworkConfig.API_BASE_URL}api/posts") {
            contentType(ContentType.Application.Json)
            setBody(
                CreatePostRequest(
                    title = title,
                    content = content,
                    images = images,
                    media = media,
                    tags = tagNames,
                    tagIds = tagIds,
                    musicId = musicId,
                    status = "published",
                ),
            )
        }.body()
        if (response.code != 200) throw IllegalStateException(response.message.ifBlank { "发布失败" })
        return response.data ?: throw IllegalStateException("发布结果缺失")
    }

    /** PUT api/posts/{id} —— 编辑自己的帖子（auth，字段对齐 Android SocialApiService.updatePost）。 */
    suspend fun updatePost(
        postId: Long,
        title: String? = null,
        content: String? = null,
        images: List<String>? = null,
        media: List<PostMediaDto>? = null,
        tagNames: List<String>? = null,
        tagIds: List<Long>? = null,
        musicId: Long? = null,
    ): PostDto {
        val response: ApiResponse<PostDto> = client.put("${FoxNetworkConfig.API_BASE_URL}api/posts/$postId") {
            contentType(ContentType.Application.Json)
            setBody(
                UpdatePostRequest(
                    title = title,
                    content = content,
                    // media 与 images 二选一（与 Android SocialRepositoryImpl 同口径）
                    images = if (media == null) images else null,
                    media = media,
                    tags = tagNames,
                    tagIds = tagIds,
                    musicId = musicId,
                    status = "published",
                ),
            )
        }.body()
        if (response.code != 200) throw IllegalStateException(response.message.ifBlank { "更新失败" })
        return response.data ?: throw IllegalStateException("更新结果缺失")
    }

    /** 读取本地文件字节（Kotlin/Native 走 NSFileManager + NSData）。 */
    @OptIn(ExperimentalForeignApi::class)
    private fun readFileBytes(path: String): ByteArray {
        val data = NSFileManager.defaultManager.contentsAtPath(path)
            ?: throw IllegalStateException("无法读取文件: $path")
        val ptr = data.bytes ?: throw IllegalStateException("文件内容为空: $path")
        return ptr.reinterpret<ByteVar>().readBytes(data.length.toInt())
    }

    /** GET api/tags/post —— 帖子话题列表（noAuth）。 */
    suspend fun fetchPostTags(limit: Int = 30, keyword: String? = null): List<TagDto> {
        val response: ApiResponse<List<TagDto>> = client.get("${FoxNetworkConfig.API_BASE_URL}api/tags/post") {
            parameter("limit", limit)
            if (!keyword.isNullOrBlank()) parameter("keyword", keyword)
        }.body()
        if (response.code != 200) throw IllegalStateException(response.message.ifBlank { "加载话题失败" })
        return response.data ?: emptyList()
    }

    /** GET api/tags/post/recommend —— 推荐话题（noAuth）。 */
    suspend fun fetchRecommendedPostTags(
        content: String? = null,
        musicId: Long? = null,
        limit: Int = 20,
    ): List<TagDto> {
        val response: ApiResponse<List<TagDto>> = client.get("${FoxNetworkConfig.API_BASE_URL}api/tags/post/recommend") {
            if (!content.isNullOrBlank()) parameter("content", content)
            if (musicId != null) parameter("music_id", musicId)
            parameter("limit", limit)
        }.body()
        if (response.code != 200) throw IllegalStateException(response.message.ifBlank { "加载推荐话题失败" })
        return response.data ?: emptyList()
    }

    // ---- 举报（与 Android ReportRepository 同契约） ----

    /** POST api/reports —— 提交举报（auth）。target_type: music/post/comment/user。 */
    suspend fun submitReport(
        targetType: String,
        targetId: Long,
        reason: String,
        description: String? = null,
    ): Boolean {
        val response: ApiResponse<Unit> = client.post("${FoxNetworkConfig.API_BASE_URL}api/reports") {
            contentType(ContentType.Application.Json)
            setBody(SubmitReportRequest(targetType = targetType, targetId = targetId, reason = reason, description = description))
        }.body()
        if (response.code != 200) throw IllegalStateException(response.message.ifBlank { "举报失败" })
        return true
    }

    /** GET api/reports —— 举报历史（auth，分页）。status 可选过滤：pending/processing/resolved/rejected。 */
    suspend fun fetchReports(page: Int = 1, limit: Int = 20, status: String? = null): PagedResponse<ReportDto> {
        val response: ApiResponse<PagedResponse<ReportDto>> = client.get("${FoxNetworkConfig.API_BASE_URL}api/reports") {
            parameter("page", page)
            parameter("limit", limit)
            if (!status.isNullOrBlank()) parameter("status", status)
        }.body()
        if (response.code != 200) throw IllegalStateException(response.message.ifBlank { "加载举报历史失败" })
        return response.data ?: PagedResponse()
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
