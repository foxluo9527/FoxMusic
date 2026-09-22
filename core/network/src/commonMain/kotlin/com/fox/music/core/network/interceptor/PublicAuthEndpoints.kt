package com.fox.music.core.network.interceptor

/**
 * 无需携带 token 的认证接口（401 表示凭据错误，而非会话过期）。
 *
 * 放在 commonMain 是为了让 Android/iOS 两端共用同一份"哪些接口不该带 token"的判定，
 * 因此是 public：Android 侧的 SafeHttpLoggingInterceptor 在 :core:network-android，属另一模块。
 */
fun isPublicAuthPath(path: String): Boolean = path in PUBLIC_AUTH_PATHS

private val PUBLIC_AUTH_PATHS = setOf(
    "/api/auth/login",
    "/api/auth/register",
    "/api/auth/forgot-password",
    "/api/auth/reset-password",
    "/api/auth/refresh-token",
    "/api/app/update",
)
