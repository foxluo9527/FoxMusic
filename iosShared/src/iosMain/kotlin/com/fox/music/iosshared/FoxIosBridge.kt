package com.fox.music.iosshared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.model.music.Music
import com.fox.music.core.network.model.MusicDto
import com.fox.music.iosshared.ui.FoxSongsScreen
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * 暴露给 SwiftUI 外壳的门面（纯数据链路）。
 *
 * 刻意不用 suspend/Flow 跨语言（K/N 导出到 Swift 需要额外桥接），
 * 统一回调风格：Kotlin 函数类型会映射成 Swift 闭包，零额外依赖。
 * 签名里不出现 kotlinx.coroutines 类型，所以框架无需 export 协程库。
 *
 * 注意职责边界：播放（AVPlayer）在 Swift 侧实现 —— 因为 Kotlin/Native 2.3.21
 * 预置平台库里的 AVPlayer 绑定是残缺的（play/pause/rate 等实例方法全部缺失，
 * 根因是 Xcode 26+ SDK 头文件把方法声明拆进了
 * `#if AVF_DEPLOYING_TO_2022_RELEASES_AND_LATER / NS_SWIFT_NONISOLATED` 宏里，
 * cinterop 解析后整个丢失）。本层只负责把"能直接播的数据"递给 Swift。
 */
object FoxIosBridge {

    private val scope = foxMainScope()
    private val api = FoxMusicApi()

    /** 拉取第一页歌曲列表，成功/失败各走一个回调。 */
    fun fetchMusicList(onResult: (List<MusicDto>) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val list = api.fetchMusicList()
                println("FoxIosBridge: 拉到 ${list.size} 首歌")
                onResult(list)
            } catch (e: Exception) {
                println("FoxIosBridge: 拉取歌曲列表失败: ${e.message}")
                onError(e.message ?: "unknown error")
            }
        }
    }

    /**
     * 把 DTO 折成领域模型，供共享 UI 组件（core:ui）渲染。
     *
     * 复用 :core:mapper 的 DtoMappers.toMusic() —— 批次 10 已把它从 :core:data 抽出
     * 成跨端模块，因此这里与 Android 端是**完全同一份映射**（含第三方平台 ID 解析），
     * 不再需要 iOS 侧维护简化版。
     */
    fun toMusic(music: MusicDto): Music = music.toMusic()

    /**
     * 按 ID 拉单曲详情并折成可播放结构（通知跳转音乐评论用）。
     */
    fun fetchPlayableSong(musicId: Long, onResult: (PlayableSong) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val song = toPlayableSong(api.fetchMusicDetail(musicId))
                if (song != null) onResult(song) else onError("该歌曲暂无播放地址")
            } catch (e: Exception) {
                println("FoxIosBridge: 拉取歌曲详情失败: ${e.message}")
                onError(e.message ?: "歌曲加载失败")
            }
        }
    }

    /**
     * 播放本地下载歌曲时补歌词（对齐 Android `FetchMusicLyricsUseCase`）：
     * 详情接口内嵌歌词就直接用；否则按第三方平台来源走 `api/import/lyrics`。
     * 成功回调 onResult(原始LRC, 翻译LRC?)；两首源都没有走 onError。
     */
    fun fetchSongLyrics(musicId: Long, onResult: (String, String?) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val dto = api.fetchMusicDetail(musicId)
                val embedded = dto.lyrics?.takeIf { it.isNotBlank() }
                if (embedded != null) {
                    onResult(embedded, dto.lyricsTrans?.takeIf { it.isNotBlank() })
                    return@launch
                }
                val params = resolveImportLyricParams(dto.toMusic())
                if (params == null) {
                    onError("暂无歌词")
                    return@launch
                }
                val imported = api.fetchImportLyricsAnon(params.first, params.second)
                if (imported.lyric.isNotBlank()) {
                    onResult(imported.lyric, imported.lyricTrans.takeIf { it.isNotBlank() })
                } else {
                    onError("暂无歌词")
                }
            } catch (e: Exception) {
                println("FoxIosBridge: 拉取歌词失败: ${e.message}")
                onError(e.message ?: "歌词加载失败")
            }
        }
    }

    /** 与 Android `FetchMusicLyricsUseCase.resolveImportLyricParams` 同口径。 */
    private fun resolveImportLyricParams(music: Music): Pair<String, String>? {
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

    /**
     * 把一首 MusicDto 折成 Swift 可直接播放的最小结构：
     * 用 commonMain 的 MediaUrlResolver 把相对路径补全成完整 URL（跨端复用点）。
     */

    fun toPlayableSong(music: MusicDto): PlayableSong? {
        val url = MediaUrlResolver.resolve(music.audioUrl ?: music.url) ?: return null
        val cover = music.coverImage ?: music.coverUrl
        return PlayableSong(
            id = music.id,
            title = music.title,
            artist = music.artists.joinToString(" / ") { it.name }.ifBlank { "未知艺术家" },
            audioUrl = url,
            coverUrl = cover?.let { MediaUrlResolver.resolve(it) },
            durationMillis = music.duration,
            lyric = music.lyrics,
            lyricTrans = music.lyricsTrans,
            commentCount = music.commentCount,
            isFavorite = music.isFavorite ?: music.isFavoriteCompat ?: false,
        )
    }

    // ---------- 共享 UI（Compose Multiplatform）----------

    /**
     * 把 Compose 写的歌曲列表包成 UIViewController 交给 SwiftUI 外壳展示。
     *
     * Swift 侧用 UIViewControllerRepresentable 包一层就能放进 SwiftUI 层级——
     * 这是 KMP 项目里"共享 UI + 平台原生外壳"的标准接法。
     */
    fun makeSongsViewController(
        songs: List<Music>,
        onPlaySong: (PlayableSong) -> Unit,
    ): UIViewController = foxComposeViewController {
        FoxSongsScreen(
            songs = songs,
            playingId = FoxComposeState.currentSongId,
            isPlaying = FoxComposeState.isPlaying,
            onPlaySong = onPlaySong,
            playableOf = { music -> playableSong(music) },
        )
    }

    /** MusicListItem 点击后要把领域模型折回可播放结构交给 Swift 的 AVPlayer。 */
    fun playableSong(music: Music): PlayableSong? {
        val url = MediaUrlResolver.resolve(music.url) ?: return null
        return PlayableSong(
            id = music.sourceId,
            title = music.title,
            artist = music.artists.joinToString(" / ") { it.name }.ifBlank { "未知艺术家" },
            audioUrl = url,
            coverUrl = music.coverImage?.let { MediaUrlResolver.resolve(it) },
            durationMillis = music.duration,
            lyric = music.lyrics,
            lyricTrans = music.lyricsTrans,
            commentCount = music.commentCount,
        )
    }
}

/**
 * 播放状态由 Swift 的 PlayerModel 持有，通过这里回灌给 Compose（Swift → Kotlin → 重组）。
 * 单向数据流，避免两边各持一份状态打架。
 */
object FoxComposeState {
    var currentSongId: String? by mutableStateOf(null)
    var isPlaying: Boolean by mutableStateOf(false)
}

/** Swift 侧直接消费的可播放条目（比 MusicDto 小，避免 Swift 感知完整 DTO 结构）。
 * coverUrl / lyric / lyricTrans 用于全屏播放器与歌词展示（Kotlin 侧只透传，解析在 Swift）。
 * commentCount / isFavorite 供播放器展示评论数与收藏态。 */
data class PlayableSong(
    val id: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val coverUrl: String? = null,
    val durationMillis: Long,
    val lyric: String? = null,
    val lyricTrans: String? = null,
    val commentCount: Int = 0,
    val isFavorite: Boolean = false,
)
