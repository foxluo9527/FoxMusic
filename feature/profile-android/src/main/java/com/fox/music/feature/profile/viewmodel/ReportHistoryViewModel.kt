package com.fox.music.feature.profile.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.GetReportHistoryUseCase
import com.fox.music.core.model.report.Report
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportHistoryState(
    val reports: List<Report> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val canLoadMore: Boolean = false,
) : UiState

sealed interface ReportHistoryIntent : UiIntent {
    data object Load : ReportHistoryIntent
    data object Refresh : ReportHistoryIntent
    data object LoadMore : ReportHistoryIntent
}

sealed interface ReportHistoryEffect : UiEffect {
    data class ShowMessage(val message: String) : ReportHistoryEffect
}

@HiltViewModel
class ReportHistoryViewModel @Inject constructor(
    private val getReportHistoryUseCase: GetReportHistoryUseCase,
) : MviViewModel<ReportHistoryState, ReportHistoryIntent, ReportHistoryEffect>(ReportHistoryState()) {

    private var nextPage = 1

    override fun handleIntent(intent: ReportHistoryIntent) {
        when (intent) {
            ReportHistoryIntent.Load -> loadFirstPage(isRefresh = false)
            ReportHistoryIntent.Refresh -> loadFirstPage(isRefresh = true)
            ReportHistoryIntent.LoadMore -> loadMore()
        }
    }

    private fun loadFirstPage(isRefresh: Boolean) {
        if (currentState.isLoading || currentState.isLoadingMore) return
        viewModelScope.launch {
            updateState {
                copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    error = null,
                )
            }
            getReportHistoryUseCase(page = 1, limit = PAGE_SIZE)
                .onSuccess { paged ->
                    nextPage = 2
                    updateState {
                        copy(
                            reports = paged.list,
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                            canLoadMore = paged.hasMore,
                        )
                    }
                }
                .onError { _, msg ->
                    updateState {
                        copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = if (reports.isEmpty()) (msg ?: "加载失败") else null,
                        )
                    }
                    if (currentState.reports.isNotEmpty()) {
                        sendEffect(ReportHistoryEffect.ShowMessage(msg ?: "加载失败"))
                    }
                }
        }
    }

    private fun loadMore() {
        if (currentState.isLoading || currentState.isLoadingMore || !currentState.canLoadMore) return
        viewModelScope.launch {
            updateState { copy(isLoadingMore = true) }
            getReportHistoryUseCase(page = nextPage, limit = PAGE_SIZE)
                .onSuccess { paged ->
                    nextPage += 1
                    updateState {
                        copy(
                            reports = reports + paged.list,
                            isLoadingMore = false,
                            canLoadMore = paged.hasMore,
                        )
                    }
                }
                .onError { _, msg ->
                    updateState { copy(isLoadingMore = false) }
                    sendEffect(ReportHistoryEffect.ShowMessage(msg ?: "加载更多失败"))
                }
        }
    }

    companion object {
        private const val PAGE_SIZE = 20
    }
}
