package com.fox.music.core.network.interceptor

/** 无需携带 token 的认证接口（401 表示凭据错误，而非会话过期） */
internal fun isPublicAuthPath(path: String): Boolean = path in PUBLIC_AUTH_PATHS

private val PUBLIC_AUTH_PATHS = setOf(
    "/api/auth/login",
    "/api/auth/register",
    "/api/auth/forgot-password",
    "/api/auth/reset-password",
    "/api/auth/refresh-token",
    "/api/app/update",
)
