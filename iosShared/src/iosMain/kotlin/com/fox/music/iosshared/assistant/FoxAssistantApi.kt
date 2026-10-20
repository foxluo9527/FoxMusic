package com.fox.music.iosshared.assistant

import com.fox.music.core.assistant.AiChatRequest
import com.fox.music.core.assistant.AiChatResult
import com.fox.music.core.assistant.AiConversationDetail
import com.fox.music.core.assistant.AiConversationPage
import com.fox.music.core.assistant.AssistantApiException
import com.fox.music.core.network.FoxNetworkConfig
import com.fox.music.core.network.model.ApiResponse
import com.fox.music.iosshared.FoxAuthStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.call.body
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * iOS 端 AI 助手数据入口（实现 core:assistant 的 AssistantRepository 契约）。
 *
 * 独立 Ktor Darwin 客户端，Bearer 鉴权与 FoxMusicApi 同款（令牌实时从 FoxAuthStore 取，
 * 登出无需重建客户端）。后端 /api/ai 接口与 Android 端同一份契约（routes/client/ai.js）。
 *
 * 说明：本客户端未接 token 自动续期（refreshTokens=null）。iOS 上其它接口（FoxMusicApi）
 * 会在令牌过期时续期并回写 FoxAuthStore，本客户端每次请求都现取最新令牌，因此只要
 * 会话内发生过其它鉴权请求即可正常工作；纯 AI 会话且令牌恰逢过期的边界情况由 FoxMusicApi
 * 的续期兜底。后续可下沉到共享 client 以复用续期逻辑。
 */
object FoxAssistantApi {

    private fun stripBearer(token: String?): String? {
        if (token.isNullOrBlank()) return null
        return token.removePrefix("Bearer ").removePrefix("Bearer")
    }

    private val client = HttpClient(Darwin) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                    encodeDefaults = true
                    explicitNulls = false
                },
            )
        }
        install(Auth) {
            bearer {
                cacheTokens = false
                sendWithoutRequest { true }
                loadTokens {
                    val access = stripBearer(FoxAuthStore.token)
                    if (access.isNullOrBlank()) null
                    else BearerTokens(access, FoxAuthStore.refreshToken.orEmpty())
                }
                refreshTokens { null }
            }
        }
    }

    suspend fun chat(conversationId: Long?, message: String): AiChatResult {
        val resp = client.post("${FoxNetworkConfig.API_BASE_URL}api/ai/chat") {
            contentType(ContentType.Application.Json)
            setBody(AiChatRequest(conversationId, message))
        }.body<ApiResponse<AiChatResult>>()
        if (!resp.isSuccess || resp.data == null) {
            throw AssistantApiException(resp.message.ifBlank { "对话失败" })
        }
        return resp.data!!
    }

    suspend fun listConversations(page: Int = 1, limit: Int = 20): AiConversationPage {
        val resp = client.get("${FoxNetworkConfig.API_BASE_URL}api/ai/conversations") {
            parameter("page", page)
            parameter("limit", limit)
        }.body<ApiResponse<AiConversationPage>>()
        if (!resp.isSuccess || resp.data == null) {
            throw AssistantApiException(resp.message.ifBlank { "加载会话列表失败" })
        }
        return resp.data!!
    }

    suspend fun getConversation(conversationId: Long): AiConversationDetail {
        val resp = client.get(
            "${FoxNetworkConfig.API_BASE_URL}api/ai/conversations/$conversationId",
        ).body<ApiResponse<AiConversationDetail>>()
        if (!resp.isSuccess || resp.data == null) {
            throw AssistantApiException(resp.message.ifBlank { "加载会话失败" })
        }
        return resp.data!!
    }

    suspend fun deleteConversation(conversationId: Long) {
        val resp = client.delete(
            "${FoxNetworkConfig.API_BASE_URL}api/ai/conversations/$conversationId",
        ).body<ApiResponse<Unit>>()
        if (!resp.isSuccess) {
            throw AssistantApiException(resp.message.ifBlank { "删除会话失败" })
        }
    }
}
