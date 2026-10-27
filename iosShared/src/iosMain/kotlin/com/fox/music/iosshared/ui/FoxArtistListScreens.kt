package com.fox.music.iosshared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import com.fox.music.core.data.mapper.toArtist
import com.fox.music.core.domain.util.ArtistGrouping
import com.fox.music.core.model.music.Artist
import com.fox.music.iosshared.FoxSceneCache
import kotlinx.serialization.builtins.ListSerializer
import com.fox.music.feature.discover.ArtistListContent
import com.fox.music.feature.discover.ArtistListState
import com.fox.music.feature.discover.HotArtistListContent
import com.fox.music.feature.discover.HotArtistListState
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.foxComposeViewController
import com.fox.music.iosshared.foxMainScope
import platform.UIKit.UIViewController

/**
 * 热门歌手页：复用 feature:discover commonMain 的 HotArtistListContent。
 * 数据与 Android GetHotArtistPagingUseCase 同口径（sort=hot，pageSize=20）。
 */
object FoxHotArtistListScreen {

    private val scope = foxMainScope()
    private val api = FoxMusicApi()

    fun makeHotArtistListViewController(
        onArtistClick: (Long) -> Unit,
        onAllArtistsClick: () -> Unit,
        onBack: () -> Unit,
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        var cachedArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }
        LaunchedEffect(Unit) {
            cachedArtists = FoxSceneCache.load(
                ListSerializer(Artist.serializer()),
                "artists",
                "hot",
                "p1",
            ).orEmpty()
        }
        val artistsPager = remember {
            Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
                ArtistPagingSource(api, sort = "hot")
            }.flow.cachedIn(scope)
        }

        SharedTransitionLayout {
            AnimatedContent(targetState = true, label = "hot-artist-list") {
                HotArtistListContent(
                    state = HotArtistListState(artists = artistsPager, cachedArtists = cachedArtists),
                    onIntent = {},
                    onArtistClick = onArtistClick,
                    onAllArtistsClick = onAllArtistsClick,
                    onBackClick = onBack,
                )
            }
        }
    }
}

/**
 * 全部歌手页：复用 ArtistListContent（字母分组 + 右侧索引条）。
 * 与 Android GetAllArtistsUseCase 同口径：循环翻页拉满（每页 500），再 ArtistGrouping 分组。
 */
object FoxArtistListScreen {

    private val api = FoxMusicApi()

    fun makeArtistListViewController(
        onArtistClick: (Long) -> Unit,
        onBack: () -> Unit,
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        var state by remember { mutableStateOf(ArtistListState(isLoading = true)) }

        LaunchedEffect(Unit) {
            FoxSceneCache.load(ListSerializer(Artist.serializer()), "artists", "all")
                ?.takeIf { it.isNotEmpty() }
                ?.let { cached ->
                    val sections = ArtistGrouping.groupArtistsByInitial(cached)
                    val (letters, indexMap) = ArtistGrouping.buildIndexMap(sections)
                    state = ArtistListState(
                        sections = sections,
                        availableLetters = letters,
                        indexMap = indexMap,
                        isLoading = false,
                    )
                }
            if (state.sections.isEmpty()) {
                state = state.copy(isLoading = true, error = null)
            }
            try {
                val all = mutableListOf<Artist>()
                var page = 1
                while (true) {
                    val paged = api.fetchArtistsPaged(page = page, limit = 500)
                    all += paged.list.map { it.toArtist() }
                    val hasMore = when {
                        paged.list.isEmpty() -> false
                        paged.totalPages > 0 -> paged.current < paged.totalPages
                        else -> paged.list.size >= 500
                    }
                    if (!hasMore) break
                    page++
                }
                val distinct = all.distinctBy { it.id }
                FoxSceneCache.save(ListSerializer(Artist.serializer()), distinct, "artists", "all")
                val sections = ArtistGrouping.groupArtistsByInitial(distinct)
                val (letters, indexMap) = ArtistGrouping.buildIndexMap(sections)
                state = ArtistListState(
                    sections = sections,
                    availableLetters = letters,
                    indexMap = indexMap,
                    isLoading = false,
                )
            } catch (e: Exception) {
                println("FoxArtistListScreen: 全部歌手加载失败: ${e.message}")
                state = if (state.sections.isEmpty()) {
                    state.copy(isLoading = false, error = e.message ?: "加载失败")
                } else {
                    state.copy(isLoading = false)
                }
            }
        }

        SharedTransitionLayout {
            AnimatedContent(targetState = true, label = "artist-list") {
                ArtistListContent(
                    state = state,
                    onIntent = {},
                    onArtistClick = onArtistClick,
                    onBackClick = onBack,
                )
            }
        }
    }
}

/**
 * 歌手分页源：与 Android ArtistPagingSource 同语义（下一页条件：本页满 loadSize）。
 */
internal class ArtistPagingSource(
    private val api: FoxMusicApi,
    private val sort: String?,
) : PagingSource<Int, Artist>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Artist> = try {
        val page = params.key ?: 1
        val paged = api.fetchArtistsPaged(page = page, limit = params.loadSize, sort = sort)
        val list = paged.list.map { it.toArtist() }
        if (page == 1 && sort == "hot") {
            FoxSceneCache.save(ListSerializer(Artist.serializer()), list, "artists", "hot", "p1")
        }
        LoadResult.Page(
            data = list,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (list.size >= params.loadSize) page + 1 else null,
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, Artist>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}
