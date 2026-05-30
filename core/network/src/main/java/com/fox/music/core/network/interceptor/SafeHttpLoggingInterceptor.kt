package com.fox.music.core.network.interceptor

import com.fox.music.core.network.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Debug 日志拦截器：multipart / 上传接口仅记录 HEADERS，避免大文件 BODY 日志导致 OOM。
 */
class SafeHttpLoggingInterceptor : Interceptor {

    private val bodyLogger = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val headersLogger = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
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
        return request.url.encodedPath.contains("/upload/")
    }
}
