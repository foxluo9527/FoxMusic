package com.fox.music.core.assistant.android

import com.fox.music.core.assistant.AssistantAction
import com.fox.music.core.assistant.AssistantActionDispatcher
import com.fox.music.core.assistant.AssistantActionTypes
import com.fox.music.core.assistant.DispatchResult
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.domain.usecase.AddTrackToPlaylistUseCase
import com.fox.music.core.domain.usecase.CreatePlaylistUseCase
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
 * 动作意图 → 端侧执行（方案 4.1 铁律：不裸调 API，走既有播放器/UseCase/仓库通道）。
 *
 * 已接通（12 个动作类型全覆盖）：
 *   play_music        —— GetMusicDetailUseCase 拉详情 → MusicController.setPlaylist 即播
 *   play_playlist     —— GetPlaylistDetailUseCase 拉歌单曲目 → setPlaylist 即播
 *   play_album        —— GetAlbumDetailUseCase 拉专辑曲目 → setPlaylist 即播
 *   play_music_list   —— music_ids 逐个 GetMusicDetailUseCase → 临时队列即播（不建歌单）
 *   favorite_music    —— ToggleMusicFavoriteUseCase
 *   favorite_playlist —— TogglePlaylistFavoriteUseCase
 *   favorite_album    —— ToggleAlbumFavoriteUseCase
 *   create_playlist   —— CreatePlaylistUseCase（name）
 *   add_to_playlist   —— AddTrackToPlaylistUseCase（playlist_id + music_id）
 *   send_message      —— ChatRepository.sendMessage（to_user_id + text，服务端确认后执行）
 *   add_friend        —— SocialRepository.sendFriendRequest（user_id，服务端确认后执行）
 * 导航类动作（open_screen / search_in_app）不走这里——core 层拿不到 NavController，
 * 由 feature:assistant-android 的 ViewModel 直接发 AssistantEffect 上浮给 UI（见 2b）。
 *
 * 说明：setPlaylist 的 key 带 assistant: 前缀，与首页/详情等入口的播放队列隔离，
 * 避免"继续播刚才那个歌单"类指令误覆盖其它入口的队列。
 */
/** 助手临时队列一次最多入队的曲目数（与服务端 MAX_PLAYLIST_TRACKS=20 对齐） */
private const val MAX_ASSISTANT_QUEUE = 100

@Singleton
class AssistantActionDispatcherImpl @Inject constructor(
    private val getMusicDetailUseCase: GetMusicDetailUseCase,
    private val getPlaylistDetailUseCase: GetPlaylistDetailUseCase,
    private val getAlbumDetailUseCase: GetAlbumDetailUseCase,
    private val toggleMusicFavoriteUseCase: ToggleMusicFavoriteUseCase,
    private val togglePlaylistFavoriteUseCase: TogglePlaylistFavoriteUseCase,
    private val toggleAlbumFavoriteUseCase: ToggleAlbumFavoriteUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val addTrackToPlaylistUseCase: AddTrackToPlaylistUseCase,
    private val chatRepository: ChatRepository,
    private val socialRepository: SocialRepository,
    private val musicController: MusicController,
) : AssistantActionDispatcher {

    override suspend fun dispatch(action: AssistantAction): DispatchResult =
        when (action.type) {
            AssistantActionTypes.PLAY_MUSIC -> dispatchPlayMusic(action)
            AssistantActionTypes.PLAY_PLAYLIST -> dispatchPlayPlaylist(action)
            AssistantActionTypes.PLAY_ALBUM -> dispatchPlayAlbum(action)
            AssistantActionTypes.PLAY_MUSIC_LIST -> dispatchPlayMusicList(action)
            AssistantActionTypes.FAVORITE_MUSIC -> dispatchFavoriteMusic(action)
            AssistantActionTypes.FAVORITE_PLAYLIST -> dispatchFavoritePlaylist(action)
            AssistantActionTypes.FAVORITE_ALBUM -> dispatchFavoriteAlbum(action)
            AssistantActionTypes.CREATE_PLAYLIST -> dispatchCreatePlaylist(action)
            AssistantActionTypes.ADD_TO_PLAYLIST -> dispatchAddToPlaylist(action)
            AssistantActionTypes.SEND_MESSAGE -> dispatchSendMessage(action)
            AssistantActionTypes.ADD_FRIEND -> dispatchAddFriend(action)
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
        // 平台上有一批 music.url 为空白的曲目：能被搜到、能进歌单，但入队后播放器拿不到音源，
        // 表现是「点了播放键、界面停在播放态、却一点声音都没有且没有任何提示」。
        // 与 iOS 端对齐（AssistantDispatcherIos.loadPlayback → FoxIosBridge.toPlayableSong）：
        // 先把没有音源的曲目挡在播放器之外，明确告诉用户为什么没播。
        if (music.url.isBlank()) {
            return DispatchResult.Failed("《${music.title}》暂无播放地址，换一首试试")
        }
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
            getPlaylistDetailUseCase(playlistId, limit = MAX_ASSISTANT_QUEUE)
        } catch (e: Exception) {
            return DispatchResult.Failed(e.message ?: "获取歌单详情失败")
        }
        val playlistDetail = detail.getOrNull()
            ?: return DispatchResult.Failed("歌单不存在或已下架")
        // 按 MAX_ASSISTANT_QUEUE 拉取整单曲目（服务端组装歌单最多 100 首），不再只入队前 20 首，
        // 让助手推荐的歌单内容尽可能完整地播出来
        // 服务端组装歌单时已剔除无音源曲目，但历史歌单里仍可能有，这里再挡一层
        val musics = playlistDetail.tracks.list.filter { it.url.isNotBlank() }
        if (musics.isEmpty()) {
            return DispatchResult.Failed(
                if (playlistDetail.tracks.list.isEmpty()) "歌单《${playlistDetail.playlist.title}》暂无歌曲"
                else "歌单《${playlistDetail.playlist.title}》里的歌曲都没有播放地址"
            )
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
        // 同上：剔除没有音源的曲目，避免整专只有哑曲时进了一个播不出声的队列
        val musics = albumDetail.tracks.list.filter { it.url.isNotBlank() }
        if (musics.isEmpty()) {
            return DispatchResult.Failed(
                if (albumDetail.tracks.list.isEmpty()) "专辑《${albumDetail.album.title}》暂无歌曲"
                else "专辑《${albumDetail.album.title}》里的歌曲都没有播放地址"
            )
        }
        musicController.setPlaylist(
            musics = musics,
            startIndex = 0,
            key = "assistant:album:$albumId",
        )
        return DispatchResult.Success("正在播放专辑《${albumDetail.album.title}》")
    }

    /**
     * 临时队列播放：服务端给的 music_ids 逐个取详情后直接入队，不建歌单、不落库。
     * 用于纯询问/推荐场景（「周杰伦有什么歌」），避免污染用户的歌单列表。
     */
    private suspend fun dispatchPlayMusicList(action: AssistantAction): DispatchResult {
        val ids = action.paramLongList("music_ids").distinct().take(MAX_ASSISTANT_QUEUE)
        if (ids.isEmpty()) return DispatchResult.Failed("动作缺少 music_ids")
        val fetched = ids.mapNotNull { id ->
            runCatching { getMusicDetailUseCase(id) }.getOrNull()?.getOrNull()?.music
        }
        if (fetched.isEmpty()) return DispatchResult.Failed("这些歌曲都不存在或已下架")
        val musics = fetched.filter { it.url.isNotBlank() }
        if (musics.isEmpty()) return DispatchResult.Failed("这些歌曲暂无播放地址")
        musicController.setPlaylist(
            musics = musics,
            startIndex = 0,
            key = "assistant:list:${ids.joinToString("_")}",
        )
        val title = action.paramString("title")
        return DispatchResult.Success(
            if (title.isNullOrBlank()) "正在播放这 ${musics.size} 首歌"
            else "正在播放《$title》"
        )
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

    private suspend fun dispatchCreatePlaylist(action: AssistantAction): DispatchResult {
        val name = action.paramString("name")?.trim()
        if (name.isNullOrBlank()) return DispatchResult.Failed("动作缺少 name（歌单名称）")
        val result = try {
            createPlaylistUseCase(name)
        } catch (e: Exception) {
            return DispatchResult.Failed(e.message ?: "创建歌单失败")
        }
        return when (result) {
            is Result.Success -> DispatchResult.Success("已创建歌单《${result.data.title}》")
            is Result.Error -> DispatchResult.Failed(result.message ?: "创建歌单失败")
            Result.Loading -> DispatchResult.Failed("操作未完成，请重试")
        }
    }

    private suspend fun dispatchAddToPlaylist(action: AssistantAction): DispatchResult {
        val playlistId = action.paramLong("playlist_id")
            ?: return DispatchResult.Failed("动作缺少 playlist_id")
        val musicId = action.paramLong("music_id")
            ?: return DispatchResult.Failed("动作缺少 music_id")
        val result = try {
            addTrackToPlaylistUseCase(playlistId, listOf(musicId))
        } catch (e: Exception) {
            return DispatchResult.Failed(e.message ?: "加入歌单失败")
        }
        return when (result) {
            is Result.Success -> DispatchResult.Success("已加入歌单")
            is Result.Error -> DispatchResult.Failed(result.message ?: "加入歌单失败")
            Result.Loading -> DispatchResult.Failed("操作未完成，请重试")
        }
    }

    private suspend fun dispatchSendMessage(action: AssistantAction): DispatchResult {
        val toUserId = action.paramLong("to_user_id")
            ?: return DispatchResult.Failed("动作缺少 to_user_id")
        val text = action.paramString("text")?.trim()
        if (text.isNullOrBlank()) return DispatchResult.Failed("动作缺少 text（消息内容）")
        val result = try {
            chatRepository.sendMessage(toUserId, text)
        } catch (e: Exception) {
            return DispatchResult.Failed(e.message ?: "发送私信失败")
        }
        return when (result) {
            is Result.Success -> DispatchResult.Success("已发送私信")
            is Result.Error -> DispatchResult.Failed(result.message ?: "发送私信失败")
            Result.Loading -> DispatchResult.Failed("操作未完成，请重试")
        }
    }

    private suspend fun dispatchAddFriend(action: AssistantAction): DispatchResult {
        val userId = action.paramLong("user_id")
            ?: return DispatchResult.Failed("动作缺少 user_id")
        val result = try {
            socialRepository.sendFriendRequest(userId, "")
        } catch (e: Exception) {
            return DispatchResult.Failed(e.message ?: "发送好友申请失败")
        }
        return when (result) {
            is Result.Success -> DispatchResult.Success("已发送好友申请")
            is Result.Error -> DispatchResult.Failed(result.message ?: "发送好友申请失败")
            Result.Loading -> DispatchResult.Failed("操作未完成，请重试")
        }
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
