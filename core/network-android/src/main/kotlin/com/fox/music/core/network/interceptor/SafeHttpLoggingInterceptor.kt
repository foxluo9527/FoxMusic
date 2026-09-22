package com.fox.music.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Debug 日志拦截器：multipart / 上传接口仅记录 HEADERS，避免大文件 BODY 日志导致 OOM。
 *
 * @param isDebug 替代 AGP 的 BuildConfig.DEBUG（KMP 模块不再生成 BuildConfig），
 *                由 NetworkModule 依据 [com.fox.music.core.network.util.isDebuggable] 传入。
 */
class SafeHttpLoggingInterceptor(private val isDebug: Boolean) : Interceptor {

    private val bodyLogger = HttpLoggingInterceptor().apply {
        redactHeader("Authorization")
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
        level = if (isDebug) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val headersLogger = HttpLoggingInterceptor().apply {
        redactHeader("Authorization")
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
        level = if (isDebug) {
            HttpLoggingInterceptor.Level.HEADERS
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val logger = if (shouldSkipBodyLogging(request)) headersLogger else bodyLogger
        return logger.intercept(chain)
    }

    private fun shouldSkipBodyLogging(request: okhttp3.Request): Boolean {
        if (request.body?.contentType()?.type == "multipart") return true
        val path = request.url.encodedPath
        if (path.contains("/upload/")) return true
        // 登录/注册等请求体含密码，仅记录 HEADERS
        return isPublicAuthPath(path) ||
            path.contains("/api/auth/")
    }
}
