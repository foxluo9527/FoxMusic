package com.fox.music.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PagedData<T>(
    val list: List<T> = emptyList(),
    val total: Int = 0,
    val current: Int = 1,
    val pageSize: Int = 20,
    val totalPages: Int = 0
) {
    val hasMore: Boolean get() = current < totalPages
    val isEmpty: Boolean get() = list.isEmpty()
}

@Serializable
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null
) {
    val isSuccess: Boolean get() = code == 200
}
