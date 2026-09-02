package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ImportRepository
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.model.music.Music
import javax.inject.Inject

data class MusicLyrics(
    val lyrics: String,
    val lyricsTrans: String? = null,
)

/**
 * 按曲目来源拉取歌词：平台曲走详情接口，第三方曲走 import/lyrics。
 * 用于播放快照剥离非当前曲歌词后，切歌时补全。
 */
class FetchMusicLyricsUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    private val importRepository: ImportRepository,
) {
    suspend operator fun invoke(music: Music): Result<MusicLyrics> {
        if (music.id > 0L) {
            val detail = musicRepository.getMusicDetail(music.id).getOrNull()?.music
            if (!detail?.lyrics.isNullOrBlank()) {
                return Result.success(
                    MusicLyrics(
                        lyrics = detail.lyrics!!,
                        lyricsTrans = detail.lyricsTrans,
                    ),
                )
            }
        }

        val importParams = resolveImportLyricParams(music)
        if (importParams != null) {
            val (platform, externalId) = importParams
            val imported = importRepository.fetchLyrics(platform, externalId).getOrNull()
            if (imported != null && imported.lyric.isNotBlank()) {
                return Result.success(
                    MusicLyrics(
                        lyrics = imported.lyric,
                        lyricsTrans = imported.lyricTrans.takeIf { it.isNotBlank() },
                    ),
                )
            }
        }

        return Result.error(
            NoSuchElementException("lyrics unavailable"),
            "暂无歌词",
        )
    }

    companion object {
        /** 从 Music 解析 import/lyrics 所需的 platform + externalId。 */
        fun resolveImportLyricParams(music: Music): Pair<String, String>? {
            val platform = music.sourcePlatform
                ?.lowercase()
                ?.takeIf { it == "qq" || it == "netease" }
                ?: music.sourceId
                    .substringBefore('_', missingDelimiterValue = "")
                    .lowercase()
                    .takeIf { it == "qq" || it == "netease" }
                ?: return null

            val externalId = when {
                music.sourceId.contains('_') &&
                    music.sourceId.substringBefore('_').equals(platform, ignoreCase = true) ->
                    music.sourceId.substringAfter('_')
                music.sourceId.isNotBlank() && music.sourceId != music.id.toString() ->
                    music.sourceId
                else -> null
            }?.takeIf { it.isNotBlank() } ?: return null

            return platform to externalId
        }
    }
}
