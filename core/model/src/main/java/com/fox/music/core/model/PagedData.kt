package com.fox.music.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PagedData<T>(
    val list: List<T> = emptyList(),
    val total: Int = 0,
    val current: Int = 1,
    val pageSize: Int = 20,
    val totalPages: Int = 0,
    @SerialName("recommend_seed")
    val recommendSeed: String? = null,
) {
    private val resolvedTotalPages: Int
        get() = when {
            totalPages > 0 -> totalPages
            total > 0 && pageSize > 0 -> (total + pageSize - 1) / pageSize
            else -> 0
        }

    val hasMore: Boolean
        get() = when {
            resolvedTotalPages > 0 -> current < resolvedTotalPages
            list.size >= pageSize && pageSize > 0 -> true
            else -> false
        }
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
