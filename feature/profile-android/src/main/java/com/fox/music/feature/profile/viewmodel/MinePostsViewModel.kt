package com.fox.music.feature.profile.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.domain.usecase.GetUserPostsPagingUseCase
import com.fox.music.core.model.social.Post
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 「我的」页「动态」子 tab 的数据源：当前用户自己的帖子分页。
 */
@HiltViewModel
class MinePostsViewModel @Inject constructor(
    private val getUserPostsPagingUseCase: GetUserPostsPagingUseCase,
) : ViewModel() {

    var postsFlow by mutableStateOf<Flow<PagingData<Post>>?>(null)
        private set

    private var boundUserId: Long? = null

    fun bind(userId: Long?) {
        if (userId == null || userId <= 0L) return
        if (boundUserId == userId && postsFlow != null) return
        boundUserId = userId
        postsFlow = getUserPostsPagingUseCase(userId).flow.cachedIn(viewModelScope)
    }
}
