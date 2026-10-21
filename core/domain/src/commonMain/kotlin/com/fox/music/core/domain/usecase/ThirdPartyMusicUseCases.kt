package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.model.music.ImportSingleRequest
import com.fox.music.core.model.music.ImportSingleResult
import com.fox.music.core.model.music.MusicExists
import com.fox.music.core.model.music.ProviderCandidateGroup
import com.fox.music.core.model.music.ProviderSearchResults
import com.fox.music.core.model.music.ResolveThirdPartyDto
import com.fox.music.core.model.music.ResolveThirdPartyRequest

/** 按精准歌名+艺人拉取多 provider 元数据（不解析音频直链） */
class SearchProvidersByTrackUseCase constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(
        title: String,
        artist: String? = null,
        platforms: List<String>? = null,
        limit: Int = 8,
    ): Result<ProviderSearchResults> = searchRepository.searchProvidersByTrack(
        title = title,
        artist = artist,
        platforms = platforms,
        limit = limit,
    )
}

/** 按需解析第三方单曲音源（点选某首后才触发） */
class ResolveThirdPartyAudioUseCase constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(req: ResolveThirdPartyRequest): Result<ResolveThirdPartyDto> =
        searchRepository.resolveThirdPartyAudio(req)
}

/** 查询某首歌是否已收录到平台 */
class CheckMusicExistsUseCase constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(title: String, artist: String? = null): Result<MusicExists> =
        searchRepository.checkMusicExists(title = title, artist = artist)
}

/** 收录单曲到平台（普通用户也可用） */
class ImportSingleTrackUseCase constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(req: ImportSingleRequest): Result<ImportSingleResult> =
        searchRepository.importSingleTrack(req)
}

/**
 * 按音源渠道分组搜索候选（纠错/收录二级页「音源 tab」用）。
 * 宽松匹配不过滤：hifini 四站为站点主题列表（RESOLVE），
 * 其余为官方 API 直链候选（DIRECT）。
 */
class SearchProviderCandidatesUseCase constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(
        title: String,
        artist: String? = null,
        durationMs: Long? = null,
        groups: List<String>? = null,
    ): Result<List<ProviderCandidateGroup>> = searchRepository.searchProviderCandidates(
        title = title,
        artist = artist,
        durationMs = durationMs,
        groups = groups,
    )
}

/**
 * 预拉刮削歌词（LRCLIB 优先 + lyrics.ovh 兜底）。
 * 二级页打开即调：用户浏览/试听期间后台拉好，收录时随 import-single 落库，
 * 避免收录流程卡在歌词上（国际链路 4~12s，平台歌词才是主力）。
 */
class FetchScrapedLyricsUseCase constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(
        title: String,
        artist: String,
        durationMs: Long? = null,
    ): Result<String?> = searchRepository.fetchScrapedLyrics(
        title = title,
        artist = artist,
        durationMs = durationMs,
    )
}
