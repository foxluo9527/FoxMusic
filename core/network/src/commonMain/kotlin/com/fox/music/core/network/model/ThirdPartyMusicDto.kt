package com.fox.music.core.network.model

import kotlinx.serialization.Serializable

/**
 * 多源元数据搜索结果（/api/music/third-party/search-providers）。
 * 只回元数据（封面/标题/艺人/时长/源ID），不解析音频直链。
 * 客户端按 provider 分 tab 展示，用户点选后再调 resolve 解析直链。
 *
 * 注意：本 DTO 引用 MusicDto，故保留在 core:network（不放入 core:model，
 * 以免 core:model 反向依赖 core:network）。请求/响应类在 core:model.music。
 */
@Serializable
data class ProviderSearchResultsDto(
    val netease: List<MusicDto> = emptyList(),
    val qq: List<MusicDto> = emptyList(),
    val kuwo: List<MusicDto> = emptyList(),
) {
    /** 仅保留有候选的 provider，保持服务端返回顺序：网易云 → QQ → 酷我 */
    fun nonEmptyGroups(): List<Pair<String, List<MusicDto>>> =
        buildList {
            if (netease.isNotEmpty()) add("netease" to netease)
            if (qq.isNotEmpty()) add("qq" to qq)
            if (kuwo.isNotEmpty()) add("kuwo" to kuwo)
        }
}
