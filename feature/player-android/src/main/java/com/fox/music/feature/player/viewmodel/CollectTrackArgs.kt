package com.fox.music.feature.player.viewmodel

import android.net.Uri

/**
 * 收录/导入单曲二级页入参。
 *
 * 入口有两种：
 * - 添加单曲（管理员）：一级页选了网易云/QQ 的某首歌，把它的元数据带进来，
 *   进入本页后可编辑名称/艺人、宽松匹配、多源 tab、单选+试听、收录到平台/歌单。
 * - 搜索（普通用户）：同上，但侧重「这首第三方歌是否已被平台收录」。
 *
 * @param mode "add_single" | "search"
 */
data class CollectTrackArgs(
    val title: String,
    val artist: String = "",
    val platform: String,          // netease / qq
    val externalId: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0,
    val mode: String = "search",
)

const val COLLECT_TRACK_ROUTE =
    "collect_track?title={title}&artist={artist}&platform={platform}" +
        "&externalId={externalId}&coverUrl={coverUrl}&durationMs={durationMs}&mode={mode}"

fun collectTrackRoute(args: CollectTrackArgs): String =
    "collect_track?title=${Uri.encode(args.title)}" +
        "&artist=${Uri.encode(args.artist)}" +
        "&platform=${Uri.encode(args.platform)}" +
        "&externalId=${Uri.encode(args.externalId)}" +
        "&coverUrl=${Uri.encode(args.coverUrl)}" +
        "&durationMs=${args.durationMs}" +
        "&mode=${Uri.encode(args.mode)}"

/** 从 SavedStateHandle（NavBackStackEntry 参数）还原入参。 */
fun collectTrackArgsFrom(savedStateHandle: androidx.lifecycle.SavedStateHandle): CollectTrackArgs? {
    val title = savedStateHandle.get<String>("title")?.takeIf { it.isNotBlank() } ?: return null
    return CollectTrackArgs(
        title = title,
        artist = savedStateHandle.get<String>("artist").orEmpty(),
        platform = savedStateHandle.get<String>("platform").orEmpty(),
        externalId = savedStateHandle.get<String>("externalId").orEmpty(),
        coverUrl = savedStateHandle.get<String>("coverUrl").orEmpty(),
        durationMs = savedStateHandle.get<String>("durationMs")?.toLongOrNull() ?: 0L,
        mode = savedStateHandle.get<String>("mode")?.takeIf { it.isNotBlank() } ?: "search",
    )
}
