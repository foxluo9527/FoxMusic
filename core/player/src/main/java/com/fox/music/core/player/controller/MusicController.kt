package com.fox.music.core.player.controller

import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.PlaybackSourceKind
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.music.RepeatMode
import com.fox.music.core.model.music.ClipRange
import kotlinx.coroutines.flow.StateFlow

interface MusicController {

    val playerState: StateFlow<PlayerState>

    val currentPosition: StateFlow<Long>

    fun play()

    fun pause()

    fun togglePlay()

    fun stop()

    fun next()

    fun previous()

    fun seekTo(positionMs: Long)

    /**
     * 无感切换歌单列表
     */
    fun updatePlaylist(musics: List<Music>, key: String)

    /**
     * 详情页加载完整列表后，同步「同一板块来源」正在播放的队列。
     * 仅当当前播放队列的来源 kind 与 [kind] 一致时生效；找不到当前曲目时保持原队列。
     */
    fun syncPlaylistForSource(kind: PlaybackSourceKind, musics: List<Music>)

    fun setPlaylist(
        musics: List<Music>,
        startIndex: Int = 0,
        key: String,
        source: PlaybackPlaylistSource? = null,
    )

    fun addToQueue(music: Music)

    fun addAllToQueue(musics: List<Music>)

    fun setRepeatMode(repeatMode: RepeatMode)

    fun setPlaybackSpeed(speed: Float)

    /**
     * 从播放队列中删除指定索引的歌曲
     */
    fun removeFromQueue(index: Int)

    /**
     * 歌单曲目删除成功后，若当前队列就是该歌单（或从它起播），立刻去掉这些曲目。
     * 正在播的那首被删掉时切到下一首；已经是最后一首则回到剩余的最后一首；队列空了则停止。
     * 其它来源的队列不动。
     */
    fun removeTracksFromPlaylistQueue(
        musicIds: Collection<Long>,
        playlistKey: String,
        playlistId: Long,
    )

    /**
     * 清空播放队列
     */
    fun clearQueue()

    /**
     * 跳转到指定索引的歌曲进行播放
     */
    fun seekToQueueItem(index: Int)

    /**
     * 队列内拖动排序：把 [from] 位置的曲目移到 [to]，播放顺序随之改变。
     * 当前播放项会跟随移动（拖动它自己时它仍保持播放态）。
     */
    fun moveQueueItem(from: Int, to: Int)

    /**
     * 音流模式：以「高潮片段」为单位启动刷歌队列。
     * @param musics 服务端的音流队列（已是按偏好/风格排好的歌单）
     * @param clipsByMusic 每首歌的高潮片段（毫秒窗口）；缺省时引擎按曲长兜底 clip
     * @param startIndex 起始歌曲索引
     * @param key 播放队列标识
     * @param source 队列来源（用于决定弹窗是否展示）
     */
    fun startStreamQueue(
        musics: List<Music>,
        clipsByMusic: Map<Long, List<ClipRange>>,
        startIndex: Int = 0,
        key: String,
        source: PlaybackPlaylistSource? = null,
    )

    /**
     * 音流模式：把新一批歌曲追加到当前音流队列**尾部**（「上滑加载更多」）。
     *
     * 与 [startStreamQueue] 的区别：不重建队列、不动 ExoPlayer（音流一次只加载一轨），
     * 因此不会打断正在播放的片段、也不会跳回队首。仅音流会话（由 [startStreamQueue] 建立）
     * 生效——普通歌单队列返回 false，调用方需自行回退到 [addAllToQueue]。
     *
     * 已存在的 id 会被忽略：服务端 exclude 只是「优先排除」，客户端必须自己兜底去重，
     * 否则同一首歌会出现两张卡片、队列里也会撞车。
     *
     * @return 是否真正追加了歌曲（false = 非音流队列或全部重复）
     */
    fun appendStreamQueue(
        musics: List<Music>,
        clipsByMusic: Map<Long, List<ClipRange>>,
    ): Boolean

    /**
     * 音流模式：推进到下一个高潮片段；当前歌还有剩余片段则切片段，否则切下一首。
     * 注意：音流页的「上一首」请用 [seekToQueueItem]（previous() 在 clip 起点会重播当前片段），
     * 「下一首/上滑」用本方法。
     */
    fun advanceStream()

    /**
     * 音流模式：自由进度跳转（允许拖到高潮片段之外）。
     * 语义：跳到片段区间外 → 当前曲目不再在片段终点自动 advance，改为完整播放到媒体自然结束；
     * 跳回片段区间内 → 恢复「只播高潮即切歌」。非音流（无激活片段）时行为等同 [seekTo]。
     */
    fun seekFree(positionMs: Long)

    /**
     * 音流模式：片段播放的页面级开关。
     * 仅当音流页在前台时开启（true → 只播高潮即切歌）；切到其它 Tab 时关闭（false →
     * 当前及后续歌曲都完整播放到自然结束再切歌）。开关状态是「粘性」的，切歌不会自行恢复。
     */
    fun setStreamClipEnabled(enabled: Boolean)

    /**
     * 立即将当前播放状态写入本地存储（供应用进入后台时调用）
     */
    fun flushPlaybackState()

    /**
     * 更新当前播放曲目的收藏状态（同步内存歌单与 UI）
     */
    fun updateCurrentMusicFavorite(isFavorite: Boolean)

    /**
     * 将详情接口拉取到的歌词写回内存歌单与 UI
     */
    fun updateMusicLyrics(musicId: Long, lyrics: String?, lyricsTrans: String?)

    /**
     * 音源替换后更新该歌曲的播放地址（写回内存歌单 + 覆盖表 + 持久化队列）。
     * 若该曲正在播放，会按新地址重建媒体项并尽量保留进度与播放态。
     */
    fun updateMusicUrl(musicId: Long, url: String)

    /** Live/短视频开始有声播放前调用：若正在播音乐则暂停并记住状态 */
    fun beginTransientMediaPlayback()

    /** Live/短视频结束或取消时调用：必要时恢复音乐播放 */
    fun endTransientMediaPlayback()
}
