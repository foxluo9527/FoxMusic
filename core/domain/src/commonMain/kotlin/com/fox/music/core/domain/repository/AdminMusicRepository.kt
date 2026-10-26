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
}
