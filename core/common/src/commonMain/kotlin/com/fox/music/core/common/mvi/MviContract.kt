package com.fox.music.core.common.mvi

// MVI 契约层（跨端）。
//
// UiState / UiIntent / UiEffect 是纯标记接口，LoadState 是纯数据类 —— 都不含
// 平台 API，因此放在 commonMain，让各 feature 的 State/Intent/Effect 也能跨端。
//
// MviViewModel 本身仍留在 androidMain：它继承 androidx.lifecycle.ViewModel 并
// 用 viewModelScope。等引入 lifecycle 的 KMP 变体（或改为 CoroutineScope 实现）
// 后再把状态机也搬上来（见迁移计划批次 12）。
interface UiState
interface UiIntent
interface UiEffect

/**
 * 仅用于单页数据获取，多页数据请使用paging
 */
data class LoadState<T>(
    var page: Int = 1,
    val limit: Int = 20,
    val data: T? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
) {
    fun success(data: T?): LoadState<T> {
        return this.copy(
            data = data,
            isLoading = false,
            error = null,
            isSuccess = true
        )
    }

    fun error(error: String?): LoadState<T> {
        return this.copy(
            data = null,
            isLoading = false,
            error = error,
            isSuccess = false
        )
    }

    fun onLoading() = this.copy(data = null, isLoading = true, error = null, isSuccess = false)
}
