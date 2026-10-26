package com.fox.music.core.domain.toplist

import com.fox.music.core.model.music.Music
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 「为你精选 / 新歌速递」整份列表的进程内共享缓存。
 *
 * 首页/发现页只展示前 5 条，详情页进入后一次性加载 100 条；
 * 详情页加载成功后把整份列表回写到这里，首页/发现页据此更新底层列表
 * （点歌入队用整份数据，展示条数仍然保持限制）。
 */
object TopListMusicStore {
    const val SORT_RECOMMEND = "recommend"
    const val SORT_LATEST = "latest"

    private val _lists = MutableStateFlow<Map<String, List<Music>>>(emptyMap())
    val lists: StateFlow<Map<String, List<Music>>> = _lists.asStateFlow()

    /** 详情页成功加载后回写整份列表（空列表忽略，避免把已有数据清空）。 */
    fun publish(sort: String, items: List<Music>) {
        if (items.isEmpty()) return
        _lists.update { it + (sort to items) }
    }

    fun snapshot(sort: String): List<Music> = _lists.value[sort].orEmpty()
}
