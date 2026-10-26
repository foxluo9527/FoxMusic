package com.fox.music.core.domain.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.paging.MusicPagingSource
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.domain.repository.RecommendedMusicCacheManager
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.PagedData

class GetMusicListUseCase constructor(
    private val musicRepository: MusicRepository,
    private val recommendedMusicCacheManager: RecommendedMusicCacheManager,
) {
    suspend operator fun invoke(
        page: Int = 1,
        limit: Int = 20,
        keyword: String? = null,
        tagId: Long? = null,
        sort: String? = null
    ): Result<PagedData<Music>> = musicRepository.getMusicList(page, limit, keyword, tagId, sort)

    fun getPagingSource(
        limit: Int = 20,
        keyword: String? = null,
        tagId: Long? = null,
        sort: String? = null,
        maxTotalItems: Int? = null,
        onRecommendSeedLoaded: ((String) -> Unit)? = null,
        /** 第一页加载成功后回调，参数为该页数据（详情页 100 条场景即整份列表）。 */
        onFirstPageLoaded: ((List<Music>) -> Unit)? = null,
    ) = Pager(
        config = PagingConfig(
            pageSize = limit,
            prefetchDistance = 20,
            initialLoadSize = limit
        ),
        pagingSourceFactory = {
            MusicPagingSource(
                musicRepository,
                keyword,
                tagId,
                sort,
                maxTotalItems = maxTotalItems,
                onRecommendSeedLoaded = onRecommendSeedLoaded,
                onFirstPageLoaded = onFirstPageLoaded,
                recommendedMusicCacheManager = sort?.takeIf { it == "recommend" }
                    ?.let { recommendedMusicCacheManager },
            )
        }
    )
}
