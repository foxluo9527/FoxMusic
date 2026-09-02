package com.fox.music.core.domain.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.fox.music.core.domain.paging.PostPagingSource
import com.fox.music.core.domain.repository.SocialRepository
import javax.inject.Inject

class GetUserPostsPagingUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
) {
    operator fun invoke(
        authorId: Long,
        limit: Int = 20,
        sort: String = "latest",
    ) = Pager(
        config = PagingConfig(
            pageSize = limit,
            prefetchDistance = limit,
            initialLoadSize = limit,
        ),
        pagingSourceFactory = {
            PostPagingSource(
                socialRepository = socialRepository,
                sort = sort,
                authorId = authorId,
            )
        },
    )
}
