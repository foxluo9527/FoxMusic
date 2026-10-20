package com.fox.music.iosshared

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.model.music.Music
import com.fox.music.core.network.model.MusicDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
// NSDate 的 timeIntervalSince1970 在 ObjC 里是 category 成员，K/N 映射成**扩展属性**，
// 必须显式 import 才可见（否则 Unresolved reference）。
import platform.Foundation.timeIntervalSince1970

/**
 * 「为你精选(sort=recommend) / 新歌速递(sort=latest)」的 **100 条快照**唯一数据源。
 *
 * 对齐 Android 的两层实现：
 * - `RecommendedMusicCacheManagerImpl`：进首页时后台翻 `MAX_ITEMS=100 / PAGE_LIMIT=20` 共 5 页，
 *   带当天固定的 seed 落盘（当天有效），详情页与首页行共用这一份。
 * - `MusicPagingSource.loadFromCache`：拿到快照后**只做切片**，不再各页各发一次请求
 *   （首页那一行 pageSize=5 → 前 5 条；详情页 pageSize=100 → 全部 100 条）。
 *
 * 修掉的 iOS 老坑：首页「为你精选」行的分页源（pageSize=5）与详情页共用同一个
 * `FoxFirstPageCache` key（`__music_recommend_v1`），首页写进去的是 5 条小快照，
 * 详情页读到它之后 `nextKey` 必为 null（5 < 30）→ 详情页永远只有 5 条
 * （模拟器缓存文件实测就是 5 条，与截图一致）。现在两边都只认这份 100 条快照，
 * 从根上消除「小快照喂大页面」。
 *
 * 另外两条与 Android 同口径的硬约束：
 * 1. sort=recommend 翻页必须带**同一个 seed**：后端 ORDER BY 第三项是 `MD5(id + seed)`，
 *    不传时服务端每次请求随机生成新 seed → 各页顺序错位、必然重复。
 * 2. 账号维度：磁盘 key 前缀带 ownerUserId（未登录 anon），内存 memo 也带账号前缀，
 *    避免 A 账号的快照被 B 账号命中。
 */
internal object FoxMusicTopListCache {

    /** 与 Android `RecommendedMusicCacheManagerImpl.MAX_ITEMS` 对齐。 */
    const val MAX_ITEMS = 100
    private const val PAGE_LIMIT = 20
    private const val PAGE_COUNT = MAX_ITEMS / PAGE_LIMIT

    const val SORT_RECOMMEND = "recommend"
    const val SORT_LATEST = "latest"

    /**
     * 新歌不做磁盘缓存（今天刚上传的歌会被「缓存一天」挡住），只做进程内短期记忆：
     * 同一会话里反复进出「新歌速递」详情页不再重复打 5 个请求。
     */
    private const val LATEST_MEMO_TTL_MS = 5 * 60 * 1000L

    private val mutex = Mutex()
    private val memory = mutableMapOf<String, Entry>()

    private class Entry(val items: List<MusicDto>, val cacheDate: String?, val stampMs: Long)

    private class Fetched(val seed: String, val items: List<MusicDto>)

    /**
     * 取「最多 100 条」快照。命中内存/磁盘直接返回；否则现拉（5 页）。
     * [force] 用于需要忽略缓存时（例如跨天后刷新）。
     */
    suspend fun load(api: FoxMusicApi, sort: String, force: Boolean = false): List<MusicDto> {
        val owner = FoxFirstPageCache.ownerPrefix()
        val memoKey = "${owner}__$sort"
        val today = todayString()
        val now = nowMs()

        mutex.withLock {
            val cached = memory[memoKey]
            if (!force && cached != null && !isStale(sort, cached, today, now)) {
                return cached.items
            }

            val disk = if (sort == SORT_RECOMMEND) loadFromDisk(owner, today) else null
            if (!force && disk != null) {
                memory[memoKey] = Entry(disk.items, today, now)
                return disk.items
            }

            val fetched = runCatching { fetchAll(api, sort) }.getOrNull()
            if (fetched == null || fetched.items.isEmpty()) {
                // 网络失败：有旧数据就先给旧数据，别把整页打成错误态
                return disk?.items ?: cached?.items.orEmpty()
            }
            memory[memoKey] = Entry(
                items = fetched.items,
                cacheDate = if (sort == SORT_RECOMMEND) today else null,
                stampMs = now,
            )
            if (sort == SORT_RECOMMEND) saveToDisk(owner, fetched.seed, fetched.items)
            return fetched.items
        }
    }

    private fun isStale(sort: String, entry: Entry, today: String, now: Long): Boolean =
        if (sort == SORT_RECOMMEND) {
            entry.cacheDate != today
        } else {
            now - entry.stampMs > LATEST_MEMO_TTL_MS
        }

    /**
     * 现拉满 100 条：第 1 页先拿 recommendSeed（服务端不传 seed 时会随机生成并回传），
     * 之后各页**并发**拉（顺序由 seed 决定，与请求先后无关）。
     * 最后按 id 去重并截断到 [MAX_ITEMS]，即便某页失败也不会污染整份快照。
     */
    private suspend fun fetchAll(api: FoxMusicApi, sort: String): Fetched {
        val first = api.fetchMusicListPage(page = 1, limit = PAGE_LIMIT, sort = sort)
        val seed = first.recommendSeed.orEmpty()
        val tail = coroutineScope {
            (2..PAGE_COUNT).map { page ->
                async {
                    runCatching {
                        api.fetchMusicListPage(
                            page = page,
                            limit = PAGE_LIMIT,
                            sort = sort,
                            seed = seed.ifEmpty { null },
                        ).items
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }
        val items = (listOf(first.items) + tail)
            .flatten()
            .distinctBy { it.id }
            .take(MAX_ITEMS)
        return Fetched(seed, items)
    }

    private suspend fun loadFromDisk(owner: String, today: String): Entry? {
        val dto = FoxFirstPageCache.load(cacheKey(owner), MusicTopListDto.serializer()) ?: return null
        if (dto.cacheDate != today || dto.items.isEmpty()) return null
        return Entry(dto.items.take(MAX_ITEMS), today, nowMs())
    }

    private suspend fun saveToDisk(owner: String, seed: String, items: List<MusicDto>) {
        FoxFirstPageCache.save(
            cacheKey(owner),
            MusicTopListDto.serializer(),
            MusicTopListDto(cacheDate = todayString(), seed = seed, items = items),
        )
    }

    private fun cacheKey(owner: String) = "${owner}__music_top100_recommend_v1"

    private fun nowMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

    private fun todayString(): String = NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd"
    }.stringFromDate(NSDate())
}

/** 每日推荐 100 条磁盘快照（DTO，全部可序列化）。 */
@Serializable
internal data class MusicTopListDto(
    val cacheDate: String = "",
    val seed: String = "",
    val items: List<MusicDto> = emptyList(),
)

/**
 * 「为你精选 / 新歌速递」分页源：数据来自 [FoxMusicTopListCache]，**只做切片**不再逐页发请求
 * —— 与 Android `MusicPagingSource.loadFromCache` 同语义。
 *
 * 首页那一行用 pageSize=5（拿前 5 条），详情页用 pageSize=100（一次拿满 100 条，
 * 于是「播放全部(100)」与整份播放队列都到位）。
 */
internal class MusicTopListPagingSource(
    private val api: FoxMusicApi,
    private val sort: String,
    private val maxItems: Int = FoxMusicTopListCache.MAX_ITEMS,
) : PagingSource<Int, Music>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Music> = try {
        val all = FoxMusicTopListCache.load(api, sort).take(maxItems)
        val page = params.key ?: 1
        val start = (page - 1) * params.loadSize
        val slice = if (start >= all.size) {
            emptyList()
        } else {
            all.subList(start, minOf(start + params.loadSize, all.size))
        }
        LoadResult.Page(
            data = slice.map { it.toMusic() },
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (start + slice.size < all.size) page + 1 else null,
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, Music>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}
