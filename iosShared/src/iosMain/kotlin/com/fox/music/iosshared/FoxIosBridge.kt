package com.fox.music.iosshared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Music
import com.fox.music.core.network.model.MusicDto
import com.fox.music.iosshared.ui.FoxSongsScreen
import kotlinx.coroutines.MainScope
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

    private val scope = MainScope()
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
     * 把一首 MusicDto 折成 Swift 可直接播放的最小结构：
     * 用 commonMain 的 MediaUrlResolver 把相对路径补全成完整 URL（跨端复用点）。
     */
    /**
     * 把 DTO 折成领域模型，供共享 UI 组件（core:ui）渲染。
     *
     * 注意：Android 侧同名映射在 core:data 的 DtoMappers.kt（含第三方平台 ID 解析等
     * Android 逻辑），那份还没跨端化。这里只填 UI 需要的字段，
     * 等 core:data 迁移到 KMP 后应合并成一份。
     */
    fun toMusic(music: MusicDto): Music = Music(
        id = stableId(music.id),
        sourceId = music.id,
        title = music.title,
        url = music.audioUrl?.takeIf { it.isNotBlank() } ?: music.url.orEmpty(),
        coverImage = music.coverUrl ?: music.coverImage,
        duration = music.duration,
        artists = music.artists.map { Artist(id = it.id ?: 0L, name = it.name) },
    )

    fun toPlayableSong(music: MusicDto): PlayableSong? {
        val url = MediaUrlResolver.resolve(music.audioUrl ?: music.url) ?: return null
        return PlayableSong(
            id = music.id,
            title = music.title,
            artist = music.artists.joinToString(" / ") { it.name }.ifBlank { "未知艺术家" },
            audioUrl = url,
            durationMillis = music.duration,
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
    ): UIViewController = ComposeUIViewController {
        FoxSongsScreen(
            songs = songs,
            playingId = FoxComposeState.currentSongId,
            isPlaying = FoxComposeState.isPlaying,
            onPlaySong = onPlaySong,
            playableOf = { music -> toPlayableSongFromMusic(music) },
        )
    }

    /** MusicListItem 点击后要把领域模型折回可播放结构交给 Swift 的 AVPlayer。 */
    private fun toPlayableSongFromMusic(music: Music): PlayableSong? {
        val url = MediaUrlResolver.resolve(music.url) ?: return null
        return PlayableSong(
            id = music.sourceId,
            title = music.title,
            artist = music.artists.joinToString(" / ") { it.name }.ifBlank { "未知艺术家" },
            audioUrl = url,
            durationMillis = music.duration,
        )
    }
}

/** 后端 id 是字符串（如 "1789673428265_6tem71"），领域模型要 Long —— 取稳定哈希。 */
private fun stableId(raw: String?): Long {
    val s = raw?.takeIf { it.isNotBlank() } ?: return 0L
    return s.fold(0L) { acc, c -> acc * 31 + c.code }
}

/**
 * 播放状态由 Swift 的 PlayerModel 持有，通过这里回灌给 Compose（Swift → Kotlin → 重组）。
 * 单向数据流，避免两边各持一份状态打架。
 */
object FoxComposeState {
    var currentSongId: String? by mutableStateOf(null)
    var isPlaying: Boolean by mutableStateOf(false)
}

/** Swift 侧直接消费的可播放条目（比 MusicDto 小，避免 Swift 感知完整 DTO 结构）。 */
data class PlayableSong(
    val id: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val durationMillis: Long,
)
