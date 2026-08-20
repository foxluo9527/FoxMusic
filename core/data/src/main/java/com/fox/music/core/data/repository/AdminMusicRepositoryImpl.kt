package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.domain.repository.AdminMusicRepository
import com.fox.music.core.domain.repository.CreateAdminMusicInput
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.Music
import com.fox.music.core.network.api.MusicApiService
import com.fox.music.core.network.model.CreateAdminArtistRequest
import com.fox.music.core.network.model.CreateAdminMusicRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminMusicRepositoryImpl @Inject constructor(
    private val musicApi: MusicApiService,
) : AdminMusicRepository {

    override suspend fun getAdminMusicList(
        page: Int,
        limit: Int,
        keyword: String?,
    ): Result<PagedData<Music>> = suspendRunCatching {
        val response = musicApi.getAdminMusicList(page, limit, keyword?.takeIf { it.isNotBlank() })
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toMusic() }
        } else {
            throw Exception(response.message.ifBlank { "获取曲库失败" })
        }
    }

    override suspend fun deleteAdminMusic(id: Long): Result<Unit> = suspendRunCatching {
        val response = musicApi.deleteAdminMusic(id)
        if (response.isSuccess) Unit else throw Exception(response.message.ifBlank { "删除失败" })
    }

    override suspend fun createAdminMusic(input: CreateAdminMusicInput): Result<Music> =
        suspendRunCatching {
            val response = musicApi.createAdminMusic(
                CreateAdminMusicRequest(
                    title = input.title,
                    description = input.description,
                    url = input.url,
                    coverImage = input.coverImage,
                    duration = input.duration,
                    language = input.language,
                    genre = input.genre,
                    lyrics = input.lyrics,
                    lyricsType = input.lyricsType,
                    artists = input.artists.map {
                        CreateAdminArtistRequest(
                            name = it.name,
                            alias = it.alias,
                            avatar = it.avatar,
                            coverImage = it.coverImage,
                        )
                    },
                )
            )
            // Server may return a partial/non-MusicDto payload in data; treat isSuccess like the HTML client.
            if (response.isSuccess) {
                Music(
                    id = 0L,
                    title = input.title,
                    url = input.url,
                    coverImage = input.coverImage,
                    duration = input.duration,
                    language = input.language,
                    genre = input.genre,
                    lyrics = input.lyrics.takeIf { it.isNotBlank() },
                )
            } else {
                throw Exception(response.message.ifBlank { "创建音乐失败" })
            }
        }
}
