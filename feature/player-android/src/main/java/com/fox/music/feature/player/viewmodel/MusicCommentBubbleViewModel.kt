package com.fox.music.feature.player.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.usecase.GetMusicCommentsUseCase
import com.fox.music.core.model.social.Comment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 歌词页「评论冒泡」的数据：只取一小撮热评做循环轮播，不做分页。 */
data class CommentBubbleState(
    val musicId: Long = 0L,
    val comments: List<Comment> = emptyList(),
) : UiState

sealed interface CommentBubbleIntent : UiIntent {
    data class Load(val musicId: Long) : CommentBubbleIntent
}

sealed interface CommentBubbleEffect : UiEffect

/**
 * 评论冒泡的数据源。
 *
 * 和「评论详情」分开：详情页要分页、要点赞、要发评论，这里只要一页文本拿去轮播，
 * 失败就静默（歌词页不该因为评论拉不到而报错打扰听歌）。
 */
@HiltViewModel
class MusicCommentBubbleViewModel @Inject constructor(
    private val getMusicComments: GetMusicCommentsUseCase,
) : MviViewModel<CommentBubbleState, CommentBubbleIntent, CommentBubbleEffect>(CommentBubbleState()) {

    /** 已经加载过的曲目，避免每次重组/回前台都重新打接口。 */
    private var loadedMusicId: Long? = null

    override fun handleIntent(intent: CommentBubbleIntent) {
        when (intent) {
            is CommentBubbleIntent.Load -> load(intent.musicId)
        }
    }

    private fun load(musicId: Long) {
        if (musicId <= 0L || musicId == loadedMusicId) return
        loadedMusicId = musicId
        updateState { copy(musicId = musicId, comments = emptyList()) }
        viewModelScope.launch {
            when (val res = getMusicComments(musicId, page = 1, limit = FETCH_LIMIT)) {
                is Result.Success -> {
                    // 期间又切歌了：丢弃这批，避免 A 的评论飘在 B 的歌词上
                    if (currentState.musicId != musicId) return@launch
                    updateState {
                        copy(
                            comments = res.data.list
                                .filter { it.content.isNotBlank() }
                                .take(POOL_LIMIT),
                        )
                    }
                }

                is Result.Error -> {
                    if (currentState.musicId == musicId) loadedMusicId = null
                }

                is Result.Loading -> Unit
            }
        }
    }

    private companion object {
        /** 一页取 12 条：足够轮播很久，又不至于一次拉太多。 */
        const val FETCH_LIMIT = 12

        /** 参与轮播的上限。 */
        const val POOL_LIMIT = 12
    }
}
