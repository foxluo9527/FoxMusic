package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.ImportSingleRequest
import com.fox.music.core.model.music.ImportSingleResult
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.MusicExists
import com.fox.music.core.model.music.ProviderCandidateGroup
import com.fox.music.core.model.music.ProviderSearchResults
import com.fox.music.core.model.music.ResolveThirdPartyDto
import com.fox.music.core.model.music.ResolveThirdPartyRequest
import com.fox.music.core.model.music.ScrapedLyric
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.SearchHistory
import com.fox.music.core.model.music.SearchSuggest
import kotlinx.coroutines.flow.Flow

interface SearchRepository {

    suspend fun searchMusic(
        keyword: String,
        page: Int = 1,
        limit: Int = 20,
        platform: String? = null
    ): Result<PagedData<Music>>

    suspend fun getHotKeywords(type: String? = null, limit: Int = 10): Result<List<HotKeyword>>

    suspend fun getSuggest(keyword: String, limit: Int = 8): Result<List<SearchSuggest>>

    fun getSearchHistory(limit: Int = 20): Flow<List<SearchHistory>>

    suspend fun saveSearchHistory(keyword: String): Result<Unit>

    suspend fun clearSearchHistory(): Result<Unit>

    suspend fun removeSearchHistoryItem(keyword: String): Result<Unit>

    /** 按精准歌名+艺人拉取多 provider 元数据（不解析音频直链） */
    suspend fun searchProvidersByTrack(
        title: String,
        artist: String? = null,
        platforms: List<String>? = null,
        limit: Int = 8,
    ): Result<ProviderSearchResults>

    /** 按需解析第三方单曲音源（点选某首后才触发） */
    suspend fun resolveThirdPartyAudio(req: ResolveThirdPartyRequest): Result<ResolveThirdPartyDto>

    /** 按音源渠道分组搜索候选（宽松匹配，纠错/收录二级页音源 tab 用） */
    suspend fun searchProviderCandidates(
        title: String,
        artist: String? = null,
        durationMs: Long? = null,
        groups: List<String>? = null,
    ): Result<List<ProviderCandidateGroup>>

    /** 查询某首歌是否已收录到平台 */
    suspend fun checkMusicExists(title: String, artist: String? = null): Result<MusicExists>

    /** 收录单曲到平台（普通用户也可用） */
    suspend fun importSingleTrack(req: ImportSingleRequest): Result<ImportSingleResult>

    /**
     * 按 歌名+艺人 刮削歌词（LRCLIB 优先 + lyrics.ovh 兜底，服务端免费无 key 源）。
     * 二级页打开时预拉，收录时随 import-single 落库；失败返回 success(null) 不阻塞。
     */
    suspend fun fetchScrapedLyrics(
        title: String,
        artist: String,
        durationMs: Long? = null,
    ): Result<String?>

    /**
     * 同 fetchScrapedLyrics，但保留「命中了哪首歌」的元信息。
     * 歌词纠错用它做「最佳匹配」推荐项（清楚告知用户匹配到的版本）；
     * 失败返回 success(null)，不阻塞上层。
     */
    suspend fun fetchScrapedLyric(
        title: String,
        artist: String,
        durationMs: Long? = null,
    ): Result<ScrapedLyric?>
}
