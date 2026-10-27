package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.Music

data class AdminArtistInput(
    val name: String,
    val alias: String = "",
    val avatar: String? = null,
    val coverImage: String? = null,
)

data class CreateAdminMusicInput(
    val title: String,
    val description: String = "",
    val url: String,
    val coverImage: String? = null,
    val duration: Long = 0,
    val language: String = "未知",
    val genre: String = "流行",
    val lyrics: String = "",
    val lyricsType: String = "none",
    val album: String = "",
    val artists: List<AdminArtistInput> = emptyList(),
)

interface AdminMusicRepository {

    suspend fun getAdminMusicList(
        page: Int = 1,
        limit: Int = 20,
        keyword: String? = null,
    ): Result<PagedData<Music>>

    suspend fun deleteAdminMusic(id: Long): Result<Unit>

    suspend fun createAdminMusic(input: CreateAdminMusicInput): Result<Music>

    /**
     * 把歌词写入曲库对应歌曲。
     * [lyricsTrans] 为 null 时不改库内翻译。
     * 非管理员由服务端拒绝，此时 [Result.Error.exception] 为 [LibraryLyricsForbidden]。
     */
    suspend fun updateMusicLyrics(
        id: Long,
        lyrics: String,
        lyricsTrans: String? = null,
    ): Result<Unit>
}

/** 服务端判定当前 token 不是管理员（HTTP 403）。 */
class LibraryLyricsForbidden : Exception("无权限")
