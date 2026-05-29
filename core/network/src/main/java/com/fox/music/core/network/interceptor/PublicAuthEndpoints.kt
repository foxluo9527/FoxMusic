package com.fox.music.core.network.interceptor

/** 无需携带 token 的认证接口（401 表示凭据错误，而非会话过期） */
internal fun isPublicAuthPath(path: String): Boolean =
    path.contains("login") || path.contains("register") ||
        path.contains("forgot-password") || path.contains("reset-password") ||
        path.contains("app/update")
