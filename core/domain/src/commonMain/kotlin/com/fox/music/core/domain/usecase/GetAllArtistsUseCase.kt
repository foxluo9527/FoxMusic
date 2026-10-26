package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.model.music.Artist
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 拉全量歌手（供「全部歌手」页按首字母分组）。
 *
 * 两条关键约束：
 * 1. `sort=id` —— 主键升序是唯一不会因新数据入库而漂移的序。用 latest（created_at）翻页时，
 *    新歌插到首页会把后续页整体挤走，客户端就会漏人或重复。
 * 2. 并发 4 路分页 —— 库里 5000+ 歌手要翻 11 页，串行累加网络往返会明显可感；
 *    按 chunk 并发后整体耗时约等于单页耗时。
 */
class GetAllArtistsUseCase constructor(
    private val artistRepository: ArtistRepository,
) {

    companion object {
        /** 与服务端 ARTIST_LIST_MAX_LIMIT 对齐 */
        private const val PAGE_SIZE = 500
        /** 兜底上限：库里约 11 页，40 页足够覆盖相当长时间的增长 */
        private const val MAX_PAGES = 40
        private const val CONCURRENCY = 4
        private const val SORT_BY_ID = "id"
    }

    suspend operator fun invoke(): Result<List<Artist>> {
        val first = artistRepository.getArtistList(
            page = 1,
            limit = PAGE_SIZE,
            sort = SORT_BY_ID,
            slim = true,
        )
        val firstPage = first.getOrNull()
            ?: return Result.error(
                first.exceptionOrNull() ?: IllegalStateException("加载歌手列表失败")
            )

        val all = ArrayList<Artist>(firstPage.total.coerceAtLeast(firstPage.list.size))
        all += firstPage.list
        if (!firstPage.hasMore) return Result.success(all.distinctBy { it.id })

        val totalPages = if (firstPage.totalPages > 0) {
            firstPage.totalPages.coerceAtMost(MAX_PAGES)
        } else {
            MAX_PAGES
        }
        if (totalPages <= 1) return Result.success(all.distinctBy { it.id })

        return try {
            all += fetchPages((2..totalPages).toList())
            Result.success(all.distinctBy { it.id })
        } catch (e: Exception) {
            Result.error(e, e.message ?: "加载歌手列表失败")
        }
    }

    /** 按 [CONCURRENCY] 路并发翻页；任一页失败都会让整批失败，避免「半张歌手列表」 */
    private suspend fun fetchPages(pages: List<Int>): List<Artist> = coroutineScope {
        pages.chunked(CONCURRENCY).flatMap { chunk ->
            chunk
                .map { page ->
                    async {
                        artistRepository.getArtistList(
                            page = page,
                            limit = PAGE_SIZE,
                            sort = SORT_BY_ID,
                            slim = true,
                        )
                    }
                }
                .awaitAll()
                .flatMap { result ->
                    result.getOrNull()?.list
                        ?: throw result.exceptionOrNull()
                            ?: IllegalStateException("加载歌手列表失败")
                }
        }
    }
}
