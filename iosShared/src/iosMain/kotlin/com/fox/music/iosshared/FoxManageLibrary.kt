package com.fox.music.iosshared

import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.data.mapper.toUser
import com.fox.music.core.network.model.CreateAdminArtistRequest
import com.fox.music.core.network.model.CreateAdminMusicRequest
import kotlinx.coroutines.launch

/**
 * 曲库管理桥（iOS 专用）：对齐 Android ManageViewModel / ManageSearchViewModel 的网络链路。
 *
 * - 曲库分页/删除：GET/DELETE `api/admin/music`（需管理员，403 等错误原文透传给 Swift Toast）
 * - 添加单曲：第三方平台搜索 → 选中项先经 api/upload/{audio,image} 转存防盗链资源 → POST api/admin/music
 * - 批量维护：POST api/import/netease/charts、api/import/hot-playlists（platform=mobile）
 *
 * 跨语言仍只用回调，不暴露 suspend。
 */
object FoxManageLibrary {

    private val scope = foxMainScope()
    private val api = FoxMusicApi()

    /** 当前登录用户是否管理员（role 含 admin，与 Android User.isAdmin 同口径）。未登录/失败 → false。 */
    fun isAdmin(onResult: (Boolean) -> Unit) {
        scope.launch {
            val admin = try {
                api.fetchProfile()?.toUser()?.role?.contains("admin", ignoreCase = true) == true
            } catch (_: Exception) {
                false
            }
            onResult(admin)
        }
    }

    /** 曲库分页：onResult(列表, total)。 */
    fun fetchPage(
        page: Int,
        limit: Int,
        keyword: String?,
        onResult: (List<PlayableSong>, Int) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                val paged = api.fetchAdminMusicPage(page, limit, keyword?.takeIf { it.isNotBlank() })
                onResult(paged.list.mapNotNull { it.toAdminPlayable() }, paged.total)
            } catch (e: Exception) {
                println("FoxManageLibrary: 曲库分页失败: ${e.message}")
                onError(e.message ?: "获取曲库失败")
            }
        }
    }

    /** 删除单曲。 */
    fun deleteMusic(
        musicId: Long,
        onResult: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                api.deleteAdminMusic(musicId)
                onResult()
            } catch (e: Exception) {
                onError(e.message ?: "删除失败")
            }
        }
    }

    /**
     * 第三方平台搜索（netease / qq）：onResult(列表, hasMore)。
     * 复用 GET api/music/third-party/search，与全局搜索页的单曲 Tab 同口径。
     */
    fun searchPlatform(
        platform: String,
        keyword: String,
        page: Int,
        limit: Int,
        onResult: (List<PlayableSong>, Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                val list = api.searchThirdPartyMusic(platform, keyword, page, limit)
                val songs = list.mapNotNull { FoxIosBridge.toPlayableSong(it) }
                onResult(songs, list.size >= limit)
            } catch (e: Exception) {
                onError(e.message ?: "搜索失败")
            }
        }
    }

    /**
     * 把第三方搜索到的单曲入库（对齐 Android ManageSearchViewModel.importOne）：
     * 音频/封面先转存自有存储（失败回退原 URL），再 POST api/admin/music。
     */
    fun importMusic(
        song: PlayableSong,
        onResult: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                val audioUrl = song.audioUrl
                if (audioUrl.isBlank()) {
                    onError("当前歌曲无法导入")
                    return@launch
                }
                val convertedAudio = try {
                    api.uploadAudioByUrl(audioUrl)
                } catch (_: Exception) {
                    audioUrl
                }
                val convertedCover = song.coverUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { url -> try { api.uploadImageByUrl(url) } catch (_: Exception) { url } }

                val artistNames = song.artist
                    .split("、", "/", ",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it != "未知艺术家" }
                    .distinct()
                val artists = if (artistNames.isEmpty()) {
                    listOf(CreateAdminArtistRequest(name = "未知歌手"))
                } else {
                    artistNames.map { CreateAdminArtistRequest(name = it) }
                }
                val lyrics = song.lyric.orEmpty()
                api.createAdminMusic(
                    CreateAdminMusicRequest(
                        title = song.title.ifBlank { "未知歌曲" },
                        url = convertedAudio,
                        coverImage = convertedCover,
                        duration = song.durationMillis,
                        language = "未知",
                        genre = "流行",
                        lyrics = lyrics,
                        lyricsType = if (lyrics.isNotBlank()) "embedded" else "none",
                        artists = artists,
                    ),
                )
                onResult()
            } catch (e: Exception) {
                println("FoxManageLibrary: 入库失败: ${e.message}")
                onError(e.message ?: "导入失败")
            }
        }
    }

    /** 更新网易云排行榜：onResult(total, success, failed)。 */
    fun updateCharts(
        onResult: (Int, Int, Int) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                val r = api.importNeteaseCharts("mobile")
                onResult(r.total, r.successCount, r.failedCount)
            } catch (e: Exception) {
                onError(e.message ?: "更新排行榜失败")
            }
        }
    }

    /** 更新热门歌单：onResult(提示文案)。 */
    fun updateHotPlaylists(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                onResult(api.importHotPlaylists("mobile"))
            } catch (e: Exception) {
                onError(e.message ?: "更新热门歌单失败")
            }
        }
    }

    /** 管理列表也允许展示暂无播放地址的歌（与后台数据一致），URL 缺失时给空串占位。 */
    private fun com.fox.music.core.network.model.MusicDto.toAdminPlayable(): PlayableSong? {
        val title = this.title.takeIf { it.isNotBlank() } ?: return null
        val resolvedUrl = (this.audioUrl ?: this.url)?.let { MediaUrlResolver.resolve(it) } ?: ""
        val cover = this.coverImage ?: this.coverUrl
        return PlayableSong(
            id = this.id,
            title = title,
            artist = this.artists.joinToString("、") { it.name }.ifBlank { "未知艺术家" },
            audioUrl = resolvedUrl,
            coverUrl = cover?.let { MediaUrlResolver.resolve(it) },
            durationMillis = this.duration,
            lyric = this.lyrics,
            lyricTrans = this.lyricsTrans,
            commentCount = this.commentCount,
            isFavorite = this.isFavorite ?: this.isFavoriteCompat ?: false,
        )
    }
}
