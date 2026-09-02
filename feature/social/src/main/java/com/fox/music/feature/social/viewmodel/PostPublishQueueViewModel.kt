package com.fox.music.feature.social.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PostPublishJob
import com.fox.music.core.domain.repository.PostPublishJobStatus
import com.fox.music.core.domain.repository.PostPublishRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PostPublishQueueState(
    val jobs: List<PostPublishJob> = emptyList(),
    val isLoading: Boolean = true,
) : UiState

sealed interface PostPublishQueueIntent : UiIntent {
    data object Load : PostPublishQueueIntent
    data class Retry(val jobId: String) : PostPublishQueueIntent
    data class Cancel(val jobId: String) : PostPublishQueueIntent
}

sealed interface PostPublishQueueEffect : UiEffect {
    data class ShowToast(val message: String) : PostPublishQueueEffect
}

@HiltViewModel
class PostPublishQueueViewModel @Inject constructor(
    private val postPublishRepository: PostPublishRepository,
) : MviViewModel<PostPublishQueueState, PostPublishQueueIntent, PostPublishQueueEffect>(
    PostPublishQueueState(),
) {

    init {
        viewModelScope.launch {
            postPublishRepository.ensureWorkerRunning()
        }
        postPublishRepository.observeActiveJobs()
            .onEach { jobs ->
                updateState { copy(jobs = jobs, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: PostPublishQueueIntent) {
        when (intent) {
            PostPublishQueueIntent.Load -> updateState { copy(isLoading = false) }
            is PostPublishQueueIntent.Retry -> retry(intent.jobId)
            is PostPublishQueueIntent.Cancel -> cancel(intent.jobId)
        }
    }

    private fun retry(jobId: String) {
        viewModelScope.launch {
            when (val result = postPublishRepository.retryJob(jobId)) {
                is Result.Success -> sendEffect(PostPublishQueueEffect.ShowToast("已开始重试"))
                is Result.Error -> sendEffect(
                    PostPublishQueueEffect.ShowToast(result.exception.message ?: "重试失败"),
                )
                is Result.Loading -> Unit
            }
        }
    }

    private fun cancel(jobId: String) {
        viewModelScope.launch {
            when (val result = postPublishRepository.cancelJob(jobId)) {
                is Result.Success -> sendEffect(PostPublishQueueEffect.ShowToast("已取消"))
                is Result.Error -> sendEffect(
                    PostPublishQueueEffect.ShowToast(result.exception.message ?: "取消失败"),
                )
                is Result.Loading -> Unit
            }
        }
    }

    companion object {
        fun statusLabel(status: PostPublishJobStatus): String = when (status) {
            PostPublishJobStatus.QUEUED -> "排队中"
            PostPublishJobStatus.UPLOADING -> "上传中"
            PostPublishJobStatus.PUBLISHING -> "发布中"
            PostPublishJobStatus.FAILED -> "失败"
        }
    }
}
