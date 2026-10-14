package com.fox.music.core.assistant.android

import com.fox.music.core.assistant.AssistantAction
import com.fox.music.core.assistant.AssistantActionDispatcher
import com.fox.music.core.assistant.AssistantActionTypes
import com.fox.music.core.assistant.DispatchResult
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.usecase.GetAlbumDetailUseCase
import com.fox.music.core.domain.usecase.GetMusicDetailUseCase
import com.fox.music.core.domain.usecase.GetPlaylistDetailUseCase
import com.fox.music.core.domain.usecase.ToggleAlbumFavoriteUseCase
import com.fox.music.core.domain.usecase.ToggleMusicFavoriteUseCase
import com.fox.music.core.domain.usecase.TogglePlaylistFavoriteUseCase
import com.fox.music.core.player.controller.MusicController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 动作意图 → 端侧执行（方案 4.1 铁律：不裸调 API，走既有播放器/UseCase 通道）。
 *
 * 已接通：
 *   play_music        —— GetMusicDetailUseCase 拉详情 → MusicController.setPlaylist 即播
 *   play_playlist     —— GetPlaylistDetailUseCase 拉歌单曲目 → setPlaylist 即播
 *   play_album        —— GetAlbumDetailUseCase 拉专辑曲目 → setPlaylist 即播
 *   favorite_music    —— ToggleMusicFavoriteUseCase
 *   favorite_playlist —— TogglePlaylistFavoriteUseCase
 *   favorite_album    —— ToggleAlbumFavoriteUseCase
 * 导航类动作（open_screen / search_in_app）不走这里——core 层拿不到 NavController，
 * 由 feature:assistant-android 的 ViewModel 直接发 AssistantEffect 上浮给 UI（见 2b）。
 * create_playlist / add_to_playlist / send_message / add_friend 留待后续增量。
 *
 * 说明：setPlaylist 的 key 带 assistant: 前缀，与首页/详情等入口的播放队列隔离，
 * 避免"继续播刚才那个歌单"类指令误覆盖其它入口的队列。
 */
@Singleton
class AssistantActionDispatcherImpl @Inject constructor(
    private val getMusicDetailUseCase: GetMusicDetailUseCase,
    private val getPlaylistDetailUseCase: GetPlaylistDetailUseCase,
    private val getAlbumDetailUseCase: GetAlbumDetailUseCase,
    private val toggleMusicFavoriteUseCase: ToggleMusicFavoriteUseCase,
    private val togglePlaylistFavoriteUseCase: TogglePlaylistFavoriteUseCase,
    private val toggleAlbumFavoriteUseCase: ToggleAlbumFavoriteUseCase,
    private val musicController: MusicController,
) : AssistantActionDispatcher {

    override suspend fun dispatch(action: AssistantAction): DispatchResult =
        when (action.type) {
            AssistantActionTypes.PLAY_MUSIC -> dispatchPlayMusic(action)
            AssistantActionTypes.PLAY_PLAYLIST -> dispatchPlayPlaylist(action)
            AssistantActionTypes.PLAY_ALBUM -> dispatchPlayAlbum(action)
            AssistantActionTypes.FAVORITE_MUSIC -> dispatchFavoriteMusic(action)
            AssistantActionTypes.FAVORITE_PLAYLIST -> dispatchFavoritePlaylist(action)
            AssistantActionTypes.FAVORITE_ALBUM -> dispatchFavoriteAlbum(action)
            else -> DispatchResult.Unsupported(action.type)
        }

    private suspend fun dispatchPlayMusic(action: AssistantAction): DispatchResult {
        val musicId = action.paramLong("music_id")
            ?: return DispatchResult.Failed("动作缺少 music_id")
        val detail = try {
            getMusicDetailUseCase(musicId)
        } catch (e: Exception) {
            return DispatchResult.Failed(e.message ?: "获取歌曲详情失败")
        }
        val music = detail.getOrNull()?.music
            ?: return DispatchResult.Failed("歌曲不存在或已下架")
        musicController.setPlaylist(
            musics = listOf(music),
            startIndex = 0,
            key = "assistant:music:$musicId",
        )
        val artist = music.artists.firstOrNull()?.name
        return DispatchResult.Success(
            if (artist.isNullOrBlank()) "正在播放《${music.title}》"
            else "正在播放${artist}的《${music.title}》"
        )
    }

    private suspend fun dispatchPlayPlaylist(action: AssistantAction): DispatchResult {
        val playlistId = action.paramLong("playlist_id")
            ?: return DispatchResult.Failed("动作缺少 playlist_id")
        val detail = try {
            getPlaylistDetailUseCase(playlistId)
        } catch (e: Exception) {
            return DispatchResult.Failed(e.message ?: "获取歌单详情失败")
        }
        val playlistDetail = detail.getOrNull()
            ?: return DispatchResult.Failed("歌单不存在或已下架")
        // 已知限制：GetPlaylistDetailUseCase 默认分页 limit=20，暂只入队前 20 首，整单入队留待后续增量
        val musics = playlistDetail.tracks.list
        if (musics.isEmpty()) {
            return DispatchResult.Failed("歌单《${playlistDetail.playlist.title}》暂无歌曲")
        }
        musicController.setPlaylist(
            musics = musics,
            startIndex = 0,
            key = "assistant:playlist:$playlistId",
        )
        return DispatchResult.Success("正在播放歌单《${playlistDetail.playlist.title}》")
    }

    private suspend fun dispatchPlayAlbum(action: AssistantAction): DispatchResult {
        val albumId = action.paramLong("album_id")
            ?: return DispatchResult.Failed("动作缺少 album_id")
        val detail = try {
            getAlbumDetailUseCase(albumId)
        } catch (e: Exception) {
            return DispatchResult.Failed(e.message ?: "获取专辑详情失败")
        }
        val albumDetail = detail.getOrNull()
            ?: return DispatchResult.Failed("专辑不存在或已下架")
        // 已知限制：GetAlbumDetailUseCase 默认分页 limit=20，暂只入队前 20 首，整单入队留待后续增量
        val musics = albumDetail.tracks.list
        if (musics.isEmpty()) {
            return DispatchResult.Failed("专辑《${albumDetail.album.title}》暂无歌曲")
        }
        musicController.setPlaylist(
            musics = musics,
            startIndex = 0,
            key = "assistant:album:$albumId",
        )
        return DispatchResult.Success("正在播放专辑《${albumDetail.album.title}》")
    }

    private suspend fun dispatchFavoriteMusic(action: AssistantAction): DispatchResult {
        val musicId = action.paramLong("music_id")
            ?: return DispatchResult.Failed("动作缺少 music_id")
        val result = try {
            toggleMusicFavoriteUseCase(musicId)
        } catch (e: Exception) {
            return DispatchResult.Failed(e.message ?: "收藏操作失败")
        }
        return result.toFavoriteResult("歌曲")
    }

    private suspend fun dispatchFavoritePlaylist(action: AssistantAction): DispatchResult {
        val playlistId = action.paramLong("playlist_id")
            ?: return DispatchResult.Failed("动作缺少 playlist_id")
        val result = try {
            togglePlaylistFavoriteUseCase(playlistId)
        } catch (e: Exception) {
            return DispatchResult.Failed(e.message ?: "收藏操作失败")
        }
        return result.toFavoriteResult("歌单")
    }

    private suspend fun dispatchFavoriteAlbum(action: AssistantAction): DispatchResult {
        val albumId = action.paramLong("album_id")
            ?: return DispatchResult.Failed("动作缺少 album_id")
        val result = try {
            toggleAlbumFavoriteUseCase(albumId)
        } catch (e: Exception) {
            return DispatchResult.Failed(e.message ?: "收藏操作失败")
        }
        return result.toFavoriteResult("专辑")
    }

    /**
     * ToggleFavorite 系列只回 Result<Unit>（不区分"加入/取消"收藏），
     * 成功统一按动作意图（收藏）反馈；仓库报错（含目标不存在）映射为 Failed。
     */
    private fun Result<Unit>.toFavoriteResult(label: String): DispatchResult =
        when (this) {
            is Result.Success -> DispatchResult.Success("已收藏$label")
            is Result.Error -> DispatchResult.Failed(message ?: "收藏失败，请稍后重试")
            Result.Loading -> DispatchResult.Failed("操作未完成，请重试")
        }
}
