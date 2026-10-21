package com.fox.music.core.model.music

/**
 * 多源元数据搜索结果（按 provider 分组的候选列表）。
 * 仅含元数据（封面/标题/艺人/时长/源ID），不含音频直链。
 */
data class ProviderSearchResults(
    val groups: Map<String, List<Music>> = emptyMap(),
) {
    val orderedProviders: List<String> get() = groups.keys.toList()
    val totalCount: Int get() = groups.values.sumOf { it.size }
}

/** 平台是否已收录某首歌 */
data class MusicExists(
    val exists: Boolean,
    val musicId: Long? = null,
)

/** 收录单曲结果 */
data class ImportSingleResult(
    val musicId: Long,
    val isNew: Boolean,
    val playlistId: Long? = null,
    val alreadyInPlaylist: Boolean = false,
)

/** 候选播放类型：DIRECT=官方 API 已带直链；RESOLVE=需逆向解析 */
enum class CandidatePlayType { DIRECT, RESOLVE;

    companion object {
        fun from(raw: String?): CandidatePlayType =
            if (raw.equals("DIRECT", ignoreCase = true)) DIRECT else RESOLVE
    }
}

/** 候选解析参数（勾选 RESOLVE 候选时回传服务端） */
data class CandidateResolveInfo(
    val site: String? = null,
    val detailUrl: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
)

/** 渠道候选（领域模型） */
data class ProviderCandidate(
    val id: String,
    val title: String,
    val artist: String = "",
    val durationMs: Long? = null,
    val playType: CandidatePlayType = CandidatePlayType.RESOLVE,
    /** DIRECT 型官方直链（可直接播放） */
    val directUrl: String? = null,
    val referer: String? = null,
    val score: Double? = null,
    val exact: Boolean = false,
    /** RESOLVE 型：解析方式（hifini_site / musicFreePlugins / provider 名） */
    val resolveProvider: String? = null,
    val resolveParams: CandidateResolveInfo? = null,
) {
    /** 已拿到可播直链（DIRECT 或解析成功） */
    val isPlayable: Boolean get() = !directUrl.isNullOrBlank()
    val canSelect: Boolean get() = isPlayable || playType == CandidatePlayType.RESOLVE
}

/** 一个音源渠道分组（客户端一个 tab） */
data class ProviderCandidateGroup(
    val key: String,
    val label: String,
    val playType: CandidatePlayType = CandidatePlayType.RESOLVE,
    val items: List<ProviderCandidate> = emptyList(),
) {
    val size: Int get() = items.size
}
