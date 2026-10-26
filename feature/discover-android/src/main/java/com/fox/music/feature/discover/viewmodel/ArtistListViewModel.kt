package com.fox.music.feature.discover

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.ListScrollStateHolder
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AllArtistsCache
import com.fox.music.core.domain.usecase.GetAllArtistsUseCase
import com.fox.music.core.domain.util.ArtistGrouping
import com.fox.music.core.model.music.Artist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ArtistListViewModel @Inject constructor(
    private val getAllArtistsUseCase: GetAllArtistsUseCase,
    private val allArtistsCache: AllArtistsCache,
) : MviViewModel<ArtistListState, ArtistListIntent, ArtistListEffect>(ArtistListState()) {

    /** 详情页返回时 Composable 会重新组合，但 ViewModel 不会重建 —— 滚动位置存在这里才保得住 */
    val scrollState = ListScrollStateHolder()

    private var loadJob: Job? = null
    private var lastLoadedAtMs = 0L

    init {
        loadArtists(force = false)
    }

    override fun handleIntent(intent: ArtistListIntent) {
        when (intent) {
            ArtistListIntent.Load -> loadArtists(force = false)
            ArtistListIntent.Refresh -> loadArtists(force = true)
        }
    }

    fun onArtistClick(artistId: Long) {
        sendEffect(ArtistListEffect.NavigateToArtist(artistId))
    }

    fun onBackClick() {
        sendEffect(ArtistListEffect.NavigateBack)
    }

    /**
     * @param force true 时忽略节流强制回源。
     *
     * 幂等是硬要求：Screen 每次进入组合都会发 Load，若不去重，从详情页返回就会把
     * 5000+ 位歌手重新拉一遍并清空列表（原本就是这个 bug）。
     */
    private fun loadArtists(force: Boolean) {
        if (loadJob?.isActive == true && !force) return

        // 缓存快照与网络回源各会算一次分组；旧任务不取消的话，两份结果会互相覆盖，
        // 退出页面后也还在后台白算 5000+ 位歌手。
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // 1) 先渲染本地快照：有缓存就立刻出列表，用户不再对着转圈等
            val cached = allArtistsCache.read()
            if (cached != null) {
                applyArtists(cached)
            } else {
                updateState { copy(isLoading = true, error = null) }
            }

            // 2) 节流：短时间内反复进出详情页没必要每次都回源
            val lastWriteAt = allArtistsCache.lastWriteAt()
            val freshAt = maxOf(lastLoadedAtMs, lastWriteAt)
            if (!force && cached != null && System.currentTimeMillis() - freshAt < REFRESH_MIN_INTERVAL_MS) {
                return@launch
            }

            // 3) 回源；成功的快照落盘，失败的保留旧列表（不把「有内容」刷成「加载失败」）
            when (val result = getAllArtistsUseCase()) {
                is Result.Success -> {
                    lastLoadedAtMs = System.currentTimeMillis()
                    allArtistsCache.write(result.data)
                    applyArtists(result.data)
                }

                is Result.Error -> {
                    if (cached == null) {
                        updateState {
                            copy(
                                isLoading = false,
                                error = result.message ?: "加载失败",
                            )
                        }
                    }
                }

                is Result.Loading -> Unit
            }
        }
    }

    /**
     * 分组 + 拼音排序是 CPU 重活（5000+ 条），必须在 Default 上算，不能占着 Main.immediate。
     * 耗时日志保留：logcat 过滤 `ArtistPerf` 可看到真实毫秒数。
     */
    private suspend fun applyArtists(artists: List<Artist>) {
        val startNs = System.nanoTime()
        val (sections, index) = withContext(Dispatchers.Default) {
            val grouped = ArtistGrouping.groupArtistsByInitial(artists)
            grouped to ArtistGrouping.buildIndexMap(grouped)
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        Log.d("ArtistPerf", "sort ${artists.size} in ${elapsedMs}ms")

        val (letters, indexMap) = index
        updateState {
            copy(
                sections = sections,
                availableLetters = letters,
                indexMap = indexMap,
                isLoading = false,
                error = null,
            )
        }
    }

    private companion object {
        const val REFRESH_MIN_INTERVAL_MS = 60_000L
    }
}
