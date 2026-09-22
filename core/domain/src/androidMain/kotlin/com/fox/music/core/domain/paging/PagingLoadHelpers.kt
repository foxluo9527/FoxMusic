package com.fox.music.core.domain.paging

import com.fox.music.core.model.PagedData

/**
 * 首页加载时若能拿到列表总长度且当前未拉全，则用该长度再请求一次，尽量一次加载完整列表。
 *
 * @param page 当前页码（仅对首页做全量补拉）
 * @param requestedLimit 本次请求期望的 limit
 * @param knownTotal 已知的列表长度（如详情头里的 trackCount），优先用于首次请求
 * @param fetch 实际分页请求
 */
suspend fun <T> loadPagedPreferFull(
    page: Int,
    requestedLimit: Int,
    knownTotal: Int = 0,
    fetch: suspend (page: Int, limit: Int) -> PagedData<T>,
): PagedData<T> {
    val initialLimit = when {
        page == 1 && knownTotal > 0 -> knownTotal
        else -> requestedLimit
    }
    val first = fetch(page, initialLimit)
    if (page != 1) return first

    val resolvedTotal = maxOf(first.total, knownTotal)
    return if (resolvedTotal > first.list.size) {
        fetch(1, resolvedTotal)
    } else {
        first
    }
}

/** 当分页体未带 total 时，用实体上的 trackCount 等作为 fallback。 */
fun <T> PagedData<T>.withTotalFallback(fallbackTotal: Int): PagedData<T> =
    if (total > 0 || fallbackTotal <= 0) this else copy(total = fallbackTotal)
