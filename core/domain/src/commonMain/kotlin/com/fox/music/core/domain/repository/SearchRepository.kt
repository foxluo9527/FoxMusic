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
}
