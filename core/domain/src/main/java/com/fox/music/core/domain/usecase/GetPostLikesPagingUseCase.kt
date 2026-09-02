package com.fox.music.core.domain.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.fox.music.core.domain.paging.PostLikerPagingSource
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.user.User
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPostLikesPagingUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
) {
    operator fun invoke(postId: Long): Flow<PagingData<User>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false, initialLoadSize = 20),
            pagingSourceFactory = {
                PostLikerPagingSource(socialRepository, postId)
            },
        ).flow
    }
}
