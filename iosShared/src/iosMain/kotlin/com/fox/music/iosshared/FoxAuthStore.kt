package com.fox.music.iosshared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Swift 侧可直接消费的登录用户精简结构（只带 UI 需要的字段）。
 */
data class AuthUser(
    val id: Long,
    val username: String,
    val nickname: String,
    val avatarUrl: String?,
    val email: String?,
)

/**
 * 登录结果：成功带 user + token，失败带后端 message（可直接展示给用户）。
 *
 * 不用异常传递失败 —— Kotlin 抛出的异常在 Swift 侧是 `NSError`/崩溃边界，
 * 而登录失败（密码错误、账号不存在）是**业务常态**，用返回值更稳。
 */
data class AuthResult(
    val success: Boolean,
    val message: String?,
    val user: AuthUser?,
    val token: String? = null,
    val refreshToken: String? = null,
)

/**
 * iOS 登录态持有者（Kotlin 侧内存态）。
 *
 * 职责划分（与 PlayerModel 在 Swift 侧调 AVPlayer 的思路一致）：
 * - Kotlin 只持有内存态，供 Ktor 注入 `Authorization: Bearer` 头；
 * - **持久化交给 Swift 的 Keychain**（`AuthStore.swift`）——启动时调 [restore] 把
 *   令牌推回来，登录成功/登出时由 Swift 落盘或清除。
 * 这样避免在 Kotlin/Native 里手写 `SecItemAdd` 的 CFDictionary interop。
 */
/**
 * 把令牌归一成「恰好一个 `Bearer ` 前缀」的规范形式，且**幂等**。
 *
 * ⚠️ 必须归一化，否则 iOS 端登录后拿不到任何需鉴权的数据：
 * 后端 `routes/client/auth.js` 的 `issueTokenPair()` 返回的就是
 * `token: \`Bearer ${accessToken}\``（**自带前缀**），而 Ktor 侧再拼一次
 * `Bearer $token` 会得到 `Authorization: Bearer Bearer eyJ...`。
 * 后端中间件做的是 `authHeader.split(' ')[1]` —— 取到的是第二个 "Bearer",
 * `jwt.verify("Bearer", secret)` 必然抛错 → 恒定 401「认证失败」。
 * 表象是登录成功了，但「我的」页空空如也（fetchProfile / 歌单 / 收藏 / 听歌
 * 记录全是 null，而 FoxProfileScreen 又把异常吞成空列表，没有报错提示）。
 *
 * Android 靠 `core:datastore` 的 `TokenManagerImpl.normalizeBearer` +
 * `AuthInterceptor`（直接把已带前缀的 token 塞进 header）避免了这个问题，
 * iOS 这里必须与它等价。
 */
internal fun normalizeBearerToken(token: String?): String? {
    val raw = token?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val body = if (raw.startsWith("Bearer", ignoreCase = true)) {
        raw.removePrefix("Bearer").trim()
    } else {
        raw
    }
    return if (body.isBlank()) null else "Bearer $body"
}

/**
 * [normalizeBearerToken] 的逆操作：剥掉 `Bearer ` 前缀。
 *
 * Ktor `Auth` 插件的 `BearerTokens` 会自己拼 `Bearer `，所以传进去之前必须剥掉
 * （否则又是 `Bearer Bearer ...` → 恒定 401，见上面注释）。
 */
internal fun stripBearerPrefix(token: String?): String? {
    val raw = token?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val body = if (raw.startsWith("Bearer", ignoreCase = true)) {
        raw.removePrefix("Bearer").trim()
    } else {
        raw
    }
    return body.takeIf { it.isNotBlank() }
}

object FoxAuthStore {

    var token: String? = null
        private set

    var refreshToken: String? = null
        private set

    var user: AuthUser? = null
        private set

    val isLoggedIn: Boolean get() = !token.isNullOrEmpty()

    /** Compose 观察此值即可在登录/登出后重组（首页最近在听、「我的」）。 */
    var revision: Int by mutableIntStateOf(0)
        private set

    /**
     * Swift 侧注册的令牌回写钩子：Kotlin 侧 401 刷新成功（[updateTokens]）后通知 Swift
     * 把新令牌落 Keychain —— iOS 的持久化归 Swift，Kotlin 只维护内存态。
     * 不回写会导致下次冷启动仍用旧的、已被服务端轮换作废的 refreshToken → 必须重登。
     */
    var onTokensRefreshed: ((String?, String?) -> Unit)? = null

    /**
     * 会话真正结束（401 刷新时服务端明确拒绝 refreshToken）时通知 Swift：
     * 清 Keychain、把 `isLoggedIn` 置 false 并回登录页 —— 对齐 Android
     * TokenAuthenticator 刷新失败时的 `tokenManager.invalidateSession()`。
     *
     * 只在「服务端拒绝」时触发；网络异常/超时不会触发（那只是断网，不能把人踢下线）。
     * 调用发生在 Ktor 的后台线程，Swift 侧必须切回主线程再改 UI 状态。
     */
    var onSessionExpired: (() -> Unit)? = null

    private fun bump() {
        revision += 1
    }

    fun restore(token: String?, refreshToken: String?, user: AuthUser?) {
        this.token = normalizeBearerToken(token)
        this.refreshToken = refreshToken
        this.user = user
        bump()
    }

    fun update(token: String, refreshToken: String?, user: AuthUser) {
        this.token = normalizeBearerToken(token)
        this.refreshToken = refreshToken
        this.user = user
        bump()
    }

    /** 仅更新令牌（401 自动续期成功时调用），用户资料不变，并通知 Swift 回写 Keychain。 */
    fun updateTokens(token: String, refreshToken: String?) {
        this.token = normalizeBearerToken(token)
        this.refreshToken = refreshToken
        bump()
        onTokensRefreshed?.invoke(this.token, refreshToken)
    }

    /** 仅更新内存中的用户资料（昵称/头像/签名等），不改动令牌。 */
    fun updateUser(user: AuthUser) {
        this.user = user
        bump()
    }

    fun clear() {
        token = null
        refreshToken = null
        user = null
        bump()
    }
}
