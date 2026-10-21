package com.fox.music.feature.profile.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.GetMyMusicFeedbacksUseCase
import com.fox.music.core.model.feedback.MusicFeedbackRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 反馈记录筛选（apiValue 为服务端 status 参数，null = 全部） */
enum class FeedbackFilter(val label: String, val apiValue: String?) {
    All("全部", null),
    Pending("待处理", "pending"),
    Resolved("已处理", "resolved"),
    Rejected("已忽略", "rejected"),
}

data class FeedbackHistoryState(
    val records: List<MusicFeedbackRecord> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val canLoadMore: Boolean = false,
    val filter: FeedbackFilter = FeedbackFilter.All,
    /** 展开详情的记录 id（同屏只展开一条，避免长列表抖动） */
    val expandedId: Long? = null,
) : UiState

sealed interface FeedbackHistoryIntent : UiIntent {
    data object Load : FeedbackHistoryIntent
    data object Refresh : FeedbackHistoryIntent
    data object LoadMore : FeedbackHistoryIntent
    data class ChangeFilter(val filter: FeedbackFilter) : FeedbackHistoryIntent
    data class ToggleExpand(val id: Long) : FeedbackHistoryIntent
}

sealed interface FeedbackHistoryEffect : UiEffect {
    data class ShowMessage(val message: String) : FeedbackHistoryEffect
}

@HiltViewModel
class FeedbackHistoryViewModel @Inject constructor(
    private val getMyMusicFeedbacksUseCase: GetMyMusicFeedbacksUseCase,
) : MviViewModel<FeedbackHistoryState, FeedbackHistoryIntent, FeedbackHistoryEffect>(
    FeedbackHistoryState()
) {

    private var nextPage = 1

    /** 切换筛选时作废旧请求，避免慢响应把旧筛选的数据覆盖回来 */
    private var requestSeq = 0

    override fun handleIntent(intent: FeedbackHistoryIntent) {
        when (intent) {
            FeedbackHistoryIntent.Load -> loadFirstPage(isRefresh = false)
            FeedbackHistoryIntent.Refresh -> loadFirstPage(isRefresh = true)
            FeedbackHistoryIntent.LoadMore -> loadMore()
            is FeedbackHistoryIntent.ChangeFilter -> {
                if (currentState.filter == intent.filter) return
                updateState { copy(filter = intent.filter, expandedId = null) }
                loadFirstPage(isRefresh = false)
            }
            is FeedbackHistoryIntent.ToggleExpand -> {
                updateState {
                    copy(expandedId = if (expandedId == intent.id) null else intent.id)
                }
            }
        }
    }

    private fun loadFirstPage(isRefresh: Boolean) {
        if (currentState.isLoading || currentState.isLoadingMore) return
        val seq = ++requestSeq
        val filter = currentState.filter
        viewModelScope.launch {
            updateState {
                copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    error = null,
                )
            }
            getMyMusicFeedbacksUseCase(
                page = 1,
                limit = PAGE_SIZE,
                status = filter.apiValue,
            )
                .onSuccess { paged ->
                    if (seq == requestSeq) {
                        nextPage = 2
                        updateState {
                            copy(
                                records = paged.list,
                                isLoading = false,
                                isRefreshing = false,
                                error = null,
                                canLoadMore = paged.hasMore,
                                expandedId = expandedId?.takeIf { id ->
                                    paged.list.any { it.id == id }
                                },
                            )
                        }
                    }
                }
                .onError { _, msg ->
                    if (seq == requestSeq) {
                        updateState {
                            copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = if (records.isEmpty()) (msg ?: "加载失败") else null,
                            )
                        }
                        if (currentState.records.isNotEmpty()) {
                            sendEffect(FeedbackHistoryEffect.ShowMessage(msg ?: "加载失败"))
                        }
                    }
                }
        }
    }

    private fun loadMore() {
        if (currentState.isLoading || currentState.isLoadingMore || !currentState.canLoadMore) return
        val seq = requestSeq
        val filter = currentState.filter
        viewModelScope.launch {
            updateState { copy(isLoadingMore = true) }
            getMyMusicFeedbacksUseCase(
                page = nextPage,
                limit = PAGE_SIZE,
                status = filter.apiValue,
            )
                .onSuccess { paged ->
                    if (seq == requestSeq) {
                        nextPage += 1
                        updateState {
                            copy(
                                records = records + paged.list,
                                isLoadingMore = false,
                                canLoadMore = paged.hasMore,
                            )
                        }
                    }
                }
                .onError { _, msg ->
                    if (seq == requestSeq) {
                        updateState { copy(isLoadingMore = false) }
                        sendEffect(FeedbackHistoryEffect.ShowMessage(msg ?: "加载更多失败"))
                    }
                }
        }
    }

    companion object {
        private const val PAGE_SIZE = 20
    }
}
