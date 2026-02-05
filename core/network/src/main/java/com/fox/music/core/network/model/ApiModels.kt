package com.fox.music.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * {
 *   "code" : 500,
 *   "message" : "密码错误",
 *   "data" : null,
 *   "success" : false
 * }
 */
@Serializable
data class ApiResponse<T>(
    val code: Int = 0,
    val message: String = "",
    // 兼容部分接口使用 { success, data, message } 的返回结构
    @SerialName("success")
    val success: Boolean? = null,
    val data: T? = null
) {
    val isSuccess: Boolean get() = success ?: (code == 200)
}

@Serializable
data class PagedResponse<T>(
    val list: List<T> = emptyList(),
    val total: Int = 0,
    val current: Int = 1,
    val pageSize: Int = 20,
    val totalPages: Int = 0
)

@Serializable
data class TokenResponse(
    val token: String
)

@Serializable
data class UnreadCountDto(
    val count: Int = 0
)
