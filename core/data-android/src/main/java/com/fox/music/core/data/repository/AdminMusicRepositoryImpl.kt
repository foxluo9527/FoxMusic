package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.domain.repository.AdminMusicRepository
import com.fox.music.core.domain.repository.CreateAdminMusicInput
import com.fox.music.core.domain.repository.LibraryLyricsForbidden
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.Music
import com.fox.music.core.network.api.MusicApiService
import com.fox.music.core.network.model.CreateAdminArtistRequest
import com.fox.music.core.network.model.CreateAdminMusicRequest
import com.fox.music.core.network.model.UpdateAdminMusicLyricsRequest
import com.fox.music.core.network.util.ErrorParser
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
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
                    album = input.album,
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

    override suspend fun updateMusicLyrics(
        id: Long,
        lyrics: String,
        lyricsTrans: String?,
    ): Result<Unit> = try {
        val response = musicApi.updateAdminMusicLyrics(
            id = id,
            body = UpdateAdminMusicLyricsRequest(lyrics = lyrics, lyricsTrans = lyricsTrans),
        )
        if (response.isSuccess) {
            Result.success(Unit)
        } else {
            val message = response.message.ifBlank { "曲库歌词同步失败" }
            Result.error(Exception(message), message)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        val message = ErrorParser.parseError(e).ifBlank { "曲库歌词同步失败" }
        if (e.code() == 403) {
            Result.error(LibraryLyricsForbidden(), message.ifBlank { "无权限" })
        } else {
            Result.error(e, message)
        }
    } catch (e: Exception) {
        Result.error(e, e.message ?: "曲库歌词同步失败")
    }
}
