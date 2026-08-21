package com.fox.music.feature.player.lyric.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.model.music.LyricsParser
import com.fox.music.feature.player.lyric.data.CustomLyricItem

class CustomLyricSearchPagingSource(
    private val repo: SearchRepository,
    private val keyword: String,
    private val targetTitle: String,
) : PagingSource<Int, CustomLyricItem>() {

    private val normalizedTarget = targetTitle.trim().lowercase()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CustomLyricItem> {
        return try {
            val page = params.key ?: 1
            val result = repo.searchMusic(
                keyword = keyword,
                page = page,
                limit = params.loadSize,
                platform = "netease"
            )
            val pagedData = result.getOrNull()
            result.exceptionOrNull()?.let { throw it }

            val musicList = pagedData?.list ?: emptyList()

            if (musicList.isEmpty()) {
                return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
            }

            val lastItemMatches = musicList.last().title.trim().lowercase() == normalizedTarget

            val items = musicList
                .filter {
                    it.title.trim().lowercase() == normalizedTarget
                            && !it.lyrics.isNullOrBlank()
                }
                .map { music ->
                    CustomLyricItem(
                        music = music,
                        lyrics = music.lyrics!!,
                        lyricsTrans = music.lyricsTrans?.takeIf { t -> t.isNotBlank() },
                        lyricLineCount = LyricsParser.parseLrc(music.lyrics!!).size
                    )
                }

            LoadResult.Page(
                data = items,
                prevKey = if (page > 1) page - 1 else null,
                nextKey = if (lastItemMatches && items.isNotEmpty()) page + 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, CustomLyricItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
