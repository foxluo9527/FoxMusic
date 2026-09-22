package com.fox.music.iosshared

import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.network.model.MusicDto
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

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
}

/** Swift 侧直接消费的可播放条目（比 MusicDto 小，避免 Swift 感知完整 DTO 结构）。 */
data class PlayableSong(
    val id: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val durationMillis: Long,
)
