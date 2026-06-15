package com.fox.music.core.domain.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.fox.music.core.domain.paging.PostPagingSource
import com.fox.music.core.domain.repository.SocialRepository
import javax.inject.Inject

class GetPostsPagingUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
) {
    operator fun invoke(
        limit: Int = 20,
        sort: String = "latest",
        keyword: String? = null,
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
                keyword = keyword,
            )
        },
    )
}
