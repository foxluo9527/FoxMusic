package com.fox.music.core.domain.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.fox.music.core.domain.paging.PostCommentPagingSource
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.social.Comment
import kotlinx.coroutines.flow.Flow

class GetPostCommentsPagingUseCase constructor(
    private val socialRepository: SocialRepository,
) {
    operator fun invoke(postId: Long): Flow<PagingData<Comment>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false, initialLoadSize = 20),
            pagingSourceFactory = {
                PostCommentPagingSource(socialRepository, postId)
            },
        ).flow
    }
}
