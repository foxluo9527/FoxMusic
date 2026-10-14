package com.fox.music.core.assistant.android

import com.fox.music.core.assistant.AssistantAction
import com.fox.music.core.assistant.AssistantActionDispatcher
import com.fox.music.core.assistant.AssistantActionTypes
import com.fox.music.core.assistant.DispatchResult
import com.fox.music.core.domain.usecase.GetMusicDetailUseCase
import com.fox.music.core.player.controller.MusicController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 动作意图 → 端侧执行（方案 4.1 铁律：不裸调 API，走既有播放器/UseCase 通道）。
 *
 * 已接通：
 *   play_music —— GetMusicDetailUseCase 拉详情 → MusicController.setPlaylist 即播
 * 其余动作暂返回 Unsupported（UI 提示"暂不支持"），按增量补齐：
 *   play_playlist / play_album / create_playlist / add_to_playlist / favorite_* ...
 *
 * 说明：setPlaylist 的 key 带 assistant: 前缀，与首页/详情等入口的播放队列隔离，
 * 避免"继续播刚才那个歌单"类指令误覆盖其它入口的队列。
 */
@Singleton
class AssistantActionDispatcherImpl @Inject constructor(
    private val getMusicDetailUseCase: GetMusicDetailUseCase,
    private val musicController: MusicController,
) : AssistantActionDispatcher {

    override suspend fun dispatch(action: AssistantAction): DispatchResult =
        when (action.type) {
            AssistantActionTypes.PLAY_MUSIC -> dispatchPlayMusic(action)
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
}
