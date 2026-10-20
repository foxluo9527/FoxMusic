package com.fox.music.iosshared.assistant

import com.fox.music.core.assistant.AssistantAction
import com.fox.music.core.assistant.AssistantActionDispatcher
import com.fox.music.core.assistant.AssistantActionTypes
import com.fox.music.core.assistant.DispatchResult
import com.fox.music.iosshared.FoxIosBridge
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.FoxPlayerApi
import com.fox.music.iosshared.PlayableSong
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * iOS 端动作执行器（实现 core:assistant 的 AssistantActionDispatcher）。
 *
 * 与服务端 ACTION_TOOLS 对齐；当前接通 iOS 侧已有能力的「纯数据」动作：
 *   create_playlist —— FoxMusicApi.createPlaylist
 *   add_friend     —— FoxMusicApi.sendFriendRequest
 *   favorite_music —— FoxPlayerApi.toggleFavorite（回调风格包成挂起）
 * 其余非播放动作（add_to_playlist、send_message 等）暂返回 Unsupported：需补 iOS 端
 * UseCase 映射，这与端侧「未实现该动作」契约一致，不会阻塞其它动作。
 *
 * 播放类动作（play_music、play_playlist、play_album）不走 [dispatch] —— iOS 播放器在
 * Swift（AVPlayer / PlayerModel），Kotlin 只负责取数据：见 [loadPlayback]，由
 * FoxAssistantScreen 把折好的 [PlayableSong] 队列经 onPlaySongs 回调交给 Swift 播放。
 * 导航类（open_screen、search_in_app）同样由 FoxAssistantScreen 直接经 Swift 回调上浮。
 */
object AssistantDispatcherIos : AssistantActionDispatcher {
    override suspend fun dispatch(action: AssistantAction): DispatchResult =
        when (action.type) {
            AssistantActionTypes.CREATE_PLAYLIST -> dispatchCreatePlaylist(action)
            AssistantActionTypes.ADD_FRIEND -> dispatchAddFriend(action)
            AssistantActionTypes.FAVORITE_MUSIC -> dispatchFavoriteMusic(action)
            else -> DispatchResult.Unsupported(action.type)
        }

    /**
     * 把 play 类动作解析成「可播放队列 + 展示标题」，供 Swift 播放器入队。
     *
     * - play_music    —— fetchMusicDetail(music_id) 取单曲
     * - play_playlist —— fetchPlaylistDetail(playlist_id) 取整单（默认 limit=20，取 tracks.list）
     * - play_album    —— fetchAlbumDetail(album_id) 取整专（同上）
 * - play_music_list —— music_ids 逐个 fetchMusicDetail 折成临时队列（不建歌单、不落库）
     *
     * 只取数据、不播放：真正的播放由 FoxAssistantScreen 经 onPlaySongs 交给 Swift。
     */
    suspend fun loadPlayback(action: AssistantAction): AssistantPlayback {
        return try {
            when (action.type) {
                AssistantActionTypes.PLAY_MUSIC -> {
                    val musicId = action.paramLong("music_id")
                        ?: return AssistantPlayback.Failed("动作缺少 music_id")
                    val dto = FoxMusicApi().fetchMusicDetail(musicId)
                    val song = FoxIosBridge.toPlayableSong(dto)
                        ?: return AssistantPlayback.Failed("该歌曲暂无播放地址")
                    AssistantPlayback.Ready(listOf(song), "《${song.title}》")
                }
                AssistantActionTypes.PLAY_PLAYLIST -> {
                    val playlistId = action.paramLong("playlist_id")
                        ?: return AssistantPlayback.Failed("动作缺少 playlist_id")
                    val detail = FoxMusicApi().fetchPlaylistDetail(playlistId)
                        ?: return AssistantPlayback.Failed("歌单不存在或已下架")
                    val songs = detail.tracks.list.mapNotNull { FoxIosBridge.toPlayableSong(it) }
                    if (songs.isEmpty()) {
                        return AssistantPlayback.Failed("歌单《${detail.title}》暂无歌曲")
                    }
                    AssistantPlayback.Ready(songs, "歌单《${detail.title}》")
                }
                AssistantActionTypes.PLAY_ALBUM -> {
                    val albumId = action.paramLong("album_id")
                        ?: return AssistantPlayback.Failed("动作缺少 album_id")
                    val detail = FoxMusicApi().fetchAlbumDetail(albumId)
                        ?: return AssistantPlayback.Failed("专辑不存在或已下架")
                    val songs = detail.tracks.list.mapNotNull { FoxIosBridge.toPlayableSong(it) }
                    if (songs.isEmpty()) {
                        return AssistantPlayback.Failed("专辑《${detail.title}》暂无歌曲")
                    }
                    AssistantPlayback.Ready(songs, "专辑《${detail.title}》")
                }
                AssistantActionTypes.PLAY_MUSIC_LIST -> {
                    val ids = action.paramLongList("music_ids").distinct().take(20)
                    if (ids.isEmpty()) {
                        return AssistantPlayback.Failed("动作缺少 music_ids")
                    }
                    val songs = ids.mapNotNull { id ->
                        runCatching { FoxMusicApi().fetchMusicDetail(id) }.getOrNull()
                            ?.let { FoxIosBridge.toPlayableSong(it) }
                    }
                    if (songs.isEmpty()) {
                        return AssistantPlayback.Failed("这些歌曲暂无播放地址")
                    }
                    val title = action.paramString("title")
                    AssistantPlayback.Ready(
                        songs,
                        if (title.isNullOrBlank()) "这 ${songs.size} 首歌" else "《$title》",
                    )
                }
                else -> AssistantPlayback.Failed("非播放动作：${action.type}")
            }
        } catch (e: Exception) {
            AssistantPlayback.Failed(e.message ?: "播放失败，请重试")
        }
    }

    private suspend fun dispatchCreatePlaylist(action: AssistantAction): DispatchResult {
        val name = action.paramString("name")?.trim()
        if (name.isNullOrBlank()) return DispatchResult.Failed("动作缺少 name")
        return try {
            val dto = FoxMusicApi().createPlaylist(name)
            DispatchResult.Success("已创建歌单《${dto.title}》")
        } catch (e: Exception) {
            DispatchResult.Failed(e.message ?: "创建歌单失败")
        }
    }

    private suspend fun dispatchAddFriend(action: AssistantAction): DispatchResult {
        val userId = action.paramLong("user_id")
            ?: return DispatchResult.Failed("动作缺少 user_id")
        return try {
            FoxMusicApi().sendFriendRequest(userId, "")
            DispatchResult.Success("已发送好友申请")
        } catch (e: Exception) {
            DispatchResult.Failed(e.message ?: "发送好友申请失败")
        }
    }

    private suspend fun dispatchFavoriteMusic(action: AssistantAction): DispatchResult {
        val musicId = action.paramLong("music_id")
            ?: return DispatchResult.Failed("动作缺少 music_id")
        return suspendCancellableCoroutine { cont ->
            FoxPlayerApi.toggleFavorite(
                musicId = musicId,
                wasFavorite = false,
                onResult = { nowFavorite ->
                    cont.resume(
                        DispatchResult.Success(if (nowFavorite) "已收藏歌曲" else "已取消收藏歌曲"),
                    )
                },
                onError = { msg -> cont.resume(DispatchResult.Failed(msg)) },
            )
        }
    }
}

/** play 类动作的解析结果：Ready 携带可播放队列与展示标题，Failed 携带失败原因。 */
sealed interface AssistantPlayback {
    data class Ready(val songs: List<PlayableSong>, val title: String) : AssistantPlayback
    data class Failed(val reason: String) : AssistantPlayback
}
