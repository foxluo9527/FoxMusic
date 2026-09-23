package com.fox.music.iosshared

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
object FoxAuthStore {

    var token: String? = null
        private set

    var refreshToken: String? = null
        private set

    var user: AuthUser? = null
        private set

    val isLoggedIn: Boolean get() = !token.isNullOrEmpty()

    /** Swift 启动时从 Keychain 恢复登录态；无令牌时传 null 即可。 */
    fun restore(token: String?, refreshToken: String?, user: AuthUser?) {
        this.token = token
        this.refreshToken = refreshToken
        this.user = user
    }

    /** 登录/注册成功后写入内存态（Swift 负责把它落到 Keychain）。 */
    fun update(token: String, refreshToken: String?, user: AuthUser) {
        this.token = token
        this.refreshToken = refreshToken
        this.user = user
    }

    /** 登出：清空内存态（Swift 负责清 Keychain）。 */
    fun clear() {
        token = null
        refreshToken = null
        user = null
    }
}
