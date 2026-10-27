package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AdminMusicRepository
import com.fox.music.core.domain.repository.LibraryLyricsForbidden
import com.fox.music.core.model.user.isAdmin

sealed interface LibraryLyricSyncOutcome {
    /** 第三方曲或没有曲库 id，不写库。 */
    data object SkippedNotLibrary : LibraryLyricSyncOutcome

    /** 当前用户不是管理员，或身份不明且服务端拒绝。 */
    data object SkippedNotAdmin : LibraryLyricSyncOutcome

    data object Synced : LibraryLyricSyncOutcome

    data class Failed(val message: String) : LibraryLyricSyncOutcome
}

/**
 * 管理员应用歌词后，把同一份歌词写入曲库。
 * 身份沿用 [com.fox.music.core.model.user.User.isAdmin]（profile.role 含 admin）；
 * 服务端再用既有 admin 中间件复核。资料拉取失败时仍尝试写入，403 视为非管理员。
 */
class SyncLibraryLyricsUseCase constructor(
    private val getProfileUseCase: GetProfileUseCase,
    private val adminMusicRepository: AdminMusicRepository,
) {
    suspend operator fun invoke(
        musicId: Long,
        isThirdParty: Boolean,
        lyrics: String,
        lyricsTrans: String?,
    ): LibraryLyricSyncOutcome {
        if (isThirdParty || musicId <= 0L || lyrics.isBlank()) {
            return LibraryLyricSyncOutcome.SkippedNotLibrary
        }
        val profile = getProfileUseCase()
        val knownAdmin = (profile as? Result.Success)?.data?.isAdmin
        if (knownAdmin == false) return LibraryLyricSyncOutcome.SkippedNotAdmin

        return when (val result = adminMusicRepository.updateMusicLyrics(musicId, lyrics, lyricsTrans)) {
            is Result.Success -> LibraryLyricSyncOutcome.Synced
            is Result.Error -> {
                if (knownAdmin != true && result.exception is LibraryLyricsForbidden) {
                    LibraryLyricSyncOutcome.SkippedNotAdmin
                } else {
                    LibraryLyricSyncOutcome.Failed(
                        result.message?.takeIf { it.isNotBlank() } ?: "曲库歌词同步失败",
                    )
                }
            }
            is Result.Loading -> LibraryLyricSyncOutcome.Failed("曲库歌词同步失败")
        }
    }
}
