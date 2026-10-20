package com.fox.music.feature.stream

import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.session.TokenManager
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.datastore.FoxPreferencesDataStore
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.domain.repository.DownloadRepository
import com.fox.music.core.domain.usecase.GetProfileUseCase
import com.fox.music.core.domain.usecase.SubmitMusicFeedbackUseCase
import com.fox.music.core.domain.usecase.SubmitReportUseCase
import com.fox.music.core.domain.usecase.ToggleMusicFavoriteUseCase
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.user.isAdmin
import com.fox.music.core.player.controller.MusicController
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class StreamItemUi(
    val music: Music,
    val clips: List<com.fox.music.core.model.music.ClipRange>,
    val artist: String? = null,
)

sealed interface StreamUiState : UiState {
    data object Loading : StreamUiState
    data class Ready(
        val items: List<StreamItemUi>,
        val mood: String? = null,
    ) : StreamUiState
    data class Error(val message: String) : StreamUiState
}

sealed interface StreamUiIntent : UiIntent {
    data class Load(val mood: String? = null) : StreamUiIntent
    data object Advance : StreamUiIntent
    data class SeekTo(val index: Int) : StreamUiIntent
    /**
     * 确保播放队列是音流队列：从其它 Tab（如在搜索页点播过歌曲）回到音流页时，
     * 以 [index] 为起点重新挂载音流队列；已是音流队列则不做任何事（不打断当前片段）。
     */
    data class EnsureQueue(val index: Int) : StreamUiIntent
    /** 进度条自由拖拽（毫秒，整曲绝对位置；拖到片段外则本曲完整播放） */
    data class SeekFree(val positionMs: Long) : StreamUiIntent
    data object TogglePlay : StreamUiIntent
    /** 收藏/取消收藏当前歌曲；favorite 为点击后 UI 期望的新状态 */
    data class ToggleFavorite(val musicId: Long, val favorite: Boolean) : StreamUiIntent
    /**
     * 「关注」按钮 = 收藏歌手：音流数据里只有歌手名（无 id），
     * 先按名字检索歌手拿到 id，再调艺人收藏接口。
     */
    data class ToggleArtistFavorite(val artistName: String?) : StreamUiIntent
}

sealed interface StreamUiEffect : UiEffect {
    data class Toast(val message: String) : StreamUiEffect
}

@HiltViewModel
class StreamViewModel @Inject constructor(
    private val repository: StreamRepository,
    private val player: MusicController,
    private val toggleMusicFavoriteUseCase: ToggleMusicFavoriteUseCase,
    private val artistRepository: ArtistRepository,
    private val preferences: FoxPreferencesDataStore,
    private val downloadRepository: DownloadRepository,
    private val submitReportUseCase: SubmitReportUseCase,
    private val submitMusicFeedbackUseCase: SubmitMusicFeedbackUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val tokenManager: TokenManager,
) : MviViewModel<StreamUiState, StreamUiIntent, StreamUiEffect>(StreamUiState.Loading) {

    private val _mood = MutableStateFlow<String?>(null)
    val mood: StateFlow<String?> = _mood.asStateFlow()

    /** 当前用户是否管理员：决定「更多」菜单里是否显示「音源纠错」入口 */
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    /** 管理员身份判定状态：成功一次即不再请求，失败只在冷却后重试 */
    private var adminStatusConfirmed = false
    private var adminCheckJob: Job? = null
    private var lastAdminCheckAtMs = 0L

    /** 打开「更多」菜单时调用：幂等拉取用户资料判断管理员身份 */
    fun ensureAdminStatus() {
        if (adminStatusConfirmed) return
        if (adminCheckJob?.isActive == true) return
        val last = lastAdminCheckAtMs
        if (last != 0L && System.currentTimeMillis() - last < ADMIN_CHECK_RETRY_MS) return
        adminCheckJob = viewModelScope.launch {
            try {
                getProfileUseCase()
                    .onSuccess { user ->
                        adminStatusConfirmed = true
                        _isAdmin.value = user.isAdmin
                    }
                    .onError { _, _ ->
                        // 失败保持「未确认」，冷却后下次打开菜单再试
                    }
            } finally {
                lastAdminCheckAtMs = System.currentTimeMillis()
            }
        }
    }

    /** 「关注」收藏成功的歌手名（音流里的原始名，可能是「A/B」拼接串），供卡片回显已关注态 */
    private val _followedArtists = MutableStateFlow<Set<String>>(emptySet())
    val followedArtists: StateFlow<Set<String>> = _followedArtists.asStateFlow()

    /** 透传播放器状态，屏幕只依赖 ViewModel（不直连 MusicController） */
    val playerState: StateFlow<PlayerState> = player.playerState
    val currentPosition: StateFlow<Long> = player.currentPosition

    init {
        // 登录 / 切换账号 / 退出登录后必须重新判定管理员身份，否则登录前判定失败会
        // 永久把「音源纠错」入口藏起来（只能靠重启 App 让 ViewModel 重建恢复）。
        viewModelScope.launch {
            tokenManager.isLoggedIn
                .distinctUntilChanged()
                .collect { loggedIn ->
                    adminCheckJob?.cancel()
                    adminCheckJob = null
                    adminStatusConfirmed = false
                    lastAdminCheckAtMs = 0L
                    _isAdmin.value = false
                    if (loggedIn) ensureAdminStatus()
                }
        }
        // 进入音流 Tab 即拉取并起播：页面没有显式的「开始」入口，
        // 若不在 ViewModel 初始化时触发 Load，UI 会永远停在 Loading 态。
        // 记忆听歌模式：冷启动沿用上次选择的场景（null = 默认模式）。
        viewModelScope.launch {
            val savedMood = runCatching { preferences.getStreamMood() }.getOrNull()
            val session = StreamSession.decode(
                runCatching { preferences.getStreamSessionJson() }.getOrNull()
            )
            // 有音流会话快照 → 同 seed 重拉复现上一批并续播；失败/批次漂移则换一批
            if (session == null || !restoreFromSession(savedMood, session)) {
                load(savedMood)
            }
        }
        // 音流进度记忆：索引变化即时落库，播放位置节流落库（仅当前队列仍是本批音流时）
        viewModelScope.launch {
            player.playerState.collect { ps ->
                val items = (currentState as? StreamUiState.Ready)?.items ?: return@collect
                if (!isSameStreamQueue(items, ps.playlist)) return@collect
                if (ps.currentIndex != lastPersistedIndex) {
                    lastPersistedIndex = ps.currentIndex
                    persistSession(index = ps.currentIndex, positionMs = ps.position)
                }
            }
        }
        viewModelScope.launch {
            player.currentPosition.sample(5_000).collect { positionMs ->
                val items = (currentState as? StreamUiState.Ready)?.items ?: return@collect
                val ps = player.playerState.value
                if (!isSameStreamQueue(items, ps.playlist)) return@collect
                persistSession(index = ps.currentIndex, positionMs = positionMs)
            }
        }
    }

    /** 上一次落库的音流索引，避免重复写 DataStore */
    private var lastPersistedIndex = -1

    private fun isSameStreamQueue(items: List<StreamItemUi>, playlist: List<Music>): Boolean {
        if (items.size != playlist.size) return false
        return items.indices.all { items[it].music.id == playlist[it].id }
    }

    override fun handleIntent(intent: StreamUiIntent) {
        when (intent) {
            is StreamUiIntent.Load -> load(intent.mood)
            StreamUiIntent.Advance -> player.advanceStream()
            is StreamUiIntent.SeekTo -> player.seekToQueueItem(intent.index)
            is StreamUiIntent.EnsureQueue -> ensureQueue(intent.index)
            is StreamUiIntent.SeekFree -> player.seekFree(intent.positionMs)
            StreamUiIntent.TogglePlay -> player.togglePlay()
            is StreamUiIntent.ToggleFavorite -> toggleFavorite(intent.musicId, intent.favorite)
            is StreamUiIntent.ToggleArtistFavorite -> toggleArtistFavorite(intent.artistName)
        }
    }

    /**
     * 「关注」= 收藏歌手：按名字检索歌手 → 调艺人收藏接口 → Toast 反馈。
     * 只有真正收藏成功才把名字放进 [followedArtists]，避免接口失败时 UI 误显示已关注。
     */
    private fun toggleArtistFavorite(artistName: String?) {
        val raw = artistName?.trim().orEmpty()
        if (raw.isBlank()) {
            sendEffect(StreamUiEffect.Toast("未找到歌手信息"))
            return
        }
        // 音流的歌手字段可能是「宝石Gem/my」这种多歌手拼接串，整体检索必不命中，需按分段逐个检索
        val candidates = (listOf(raw) + raw.split('/', '、', '&', ',', '，', ';').map { it.trim() })
            .filter { it.isNotBlank() }
            .distinct()
        viewModelScope.launch {
            val artist = candidates.firstNotNullOfOrNull { candidate ->
                artistRepository.getArtistList(page = 1, limit = 10, keyword = candidate)
                    .getOrNull()
                    ?.list
                    ?.firstOrNull { it.name.equals(candidate, ignoreCase = true) }
            }
            if (artist == null) {
                sendEffect(StreamUiEffect.Toast("未找到歌手「$raw」"))
                return@launch
            }
            if (artist.isFavorite) {
                // 服务端已是收藏态：不再 toggle（否则会取消收藏），直接同步 UI 状态
                _followedArtists.value = _followedArtists.value + raw
                sendEffect(StreamUiEffect.Toast("已收藏歌手 ${artist.name}"))
                return@launch
            }
            artistRepository.toggleFavorite(artist.id)
                .onSuccess {
                    _followedArtists.value = _followedArtists.value + raw
                    sendEffect(StreamUiEffect.Toast("已收藏歌手 ${artist.name}"))
                }
                .onError { _, msg -> sendEffect(StreamUiEffect.Toast(msg ?: "收藏歌手失败")) }
        }
    }

    /** 收藏/取消收藏：调用真实收藏接口并同步播放器内存歌单，结果以 Toast 反馈。 */
    private fun toggleFavorite(musicId: Long, favorite: Boolean) {
        viewModelScope.launch {
            toggleMusicFavoriteUseCase(musicId)
                .onSuccess {
                    player.updateCurrentMusicFavorite(favorite)
                    sendEffect(StreamUiEffect.Toast(if (favorite) "已收藏" else "已取消收藏"))
                }
                .onError { _, msg ->
                    sendEffect(StreamUiEffect.Toast(msg ?: "操作失败"))
                }
        }
    }

    /** 「更多」→ 下载当前音流歌曲：本地音频跳过，其余加入下载队列。 */
    fun downloadMusic(music: Music) {
        if (MediaUrlResolver.isLocalMedia(music.url)) {
            sendEffect(StreamUiEffect.Toast("本地音频无需下载"))
            return
        }
        viewModelScope.launch {
            runCatching { downloadRepository.enqueue(listOf(music)) }
                .onSuccess { sendEffect(StreamUiEffect.Toast("已加入下载队列")) }
                .onFailure { sendEffect(StreamUiEffect.Toast("下载失败，请稍后再试")) }
        }
    }

    /** 「更多」→ 举报当前音流歌曲（reason 取服务端白名单 other）。 */
    fun reportMusic(music: Music) {
        viewModelScope.launch {
            submitReportUseCase(
                targetType = "music",
                targetId = music.id,
                reason = "other",
                description = null,
            ).onSuccess {
                sendEffect(StreamUiEffect.Toast("举报已提交，感谢反馈"))
            }.onError { _, msg ->
                sendEffect(StreamUiEffect.Toast(msg ?: "举报提交失败"))
            }
        }
    }

    /** 「更多」→ 反馈音乐不匹配：提交到后端，管理员处理后自动闭环。 */
    fun submitFeedback(music: Music, content: String?) {
        viewModelScope.launch {
            submitMusicFeedbackUseCase(musicId = music.id, content = content)
                .onSuccess {
                    sendEffect(StreamUiEffect.Toast("反馈已提交，感谢你的纠音"))
                }
                .onError { _, msg ->
                    sendEffect(StreamUiEffect.Toast(msg ?: "反馈提交失败"))
                }
        }
    }

    /** 本会话已听过的 song id，作为 exclude 下发，服务端优先排除，避免「又听一遍一样的歌」 */
    private val heardIds = MutableStateFlow<Set<Long>>(emptySet())

    private fun load(mood: String?) {
        _mood.value = mood
        // 记忆听歌模式：把本次选择（含「默认模式」=null）写入本地，下次冷启动沿用
        viewModelScope.launch { runCatching { preferences.saveStreamMood(mood) } }
        viewModelScope.launch {
            updateState { StreamUiState.Loading }
            runCatching {
                // 真实 userId：个性化与去重都依赖它（此前一直传 null，导致永远是全局热门榜）
                // 每次 load 生成新 seed：每次打开 / 换风格都会换一批，体验不重样
                val userId = runCatching { preferences.userId.first()?.toLongOrNull() }.getOrNull()
                val seed = UUID.randomUUID().toString()
                val exclude = heardIds.value.toList()
                val items = repository.getStream(
                    mood = mood,
                    userId = userId,
                    limit = 30,
                    seed = seed,
                    exclude = exclude,
                )
                val uiItems = StreamClipPolicy.buildPlaylist(items)
                if (uiItems.isEmpty()) {
                    updateState { StreamUiState.Error("暂无可播放的音流内容，换个心情试试") }
                    return@launch
                }
                // 启动音流：服务端队列即播放队列，片段随歌挂载
                player.startStreamQueue(
                    uiItems.map { it.music },
                    uiItems.associate { it.music.id to it.clips },
                    0,
                    "stream",
                    null,
                )
                // 把本批歌曲计入「已听」，下次 load（换一批 / 重开）时服务端优先规避
                heardIds.value = heardIds.value + uiItems.map { it.music.id }
                // 写音流会话快照：冷启动据此复现本批并续播
                writeSession(
                    mood = mood,
                    userId = userId,
                    seed = seed,
                    index = 0,
                    positionMs = 0L,
                    items = uiItems,
                    excludeIds = exclude,
                )
                updateState { StreamUiState.Ready(uiItems, mood) }
            }.onFailure { e ->
                updateState { StreamUiState.Error(e.message ?: "音流加载失败") }
            }
        }
    }

    /**
     * 冷启动恢复：用快照的 seed + exclude 重拉，校验 id 序列与快照完全一致后
     * 从上次的索引/位置续播。任何一步不符都返回 false，由调用方换一批。
     */
    private suspend fun restoreFromSession(
        savedMood: String?,
        session: StreamSession,
    ): Boolean {
        val userId = runCatching { preferences.userId.first()?.toLongOrNull() }.getOrNull()
        // 换账号登录：快照作废
        if (session.userId != userId) return false
        _mood.value = session.mood
        val items = runCatching {
            repository.getStream(
                mood = session.mood,
                userId = userId,
                limit = 30,
                seed = session.seed,
                exclude = session.excludeIds,
            )
        }.getOrElse { return false }
        val uiItems = StreamClipPolicy.buildPlaylist(items)
        // 批次校验：服务端同 seed 理论上同序，曲库变动可能导致漂移，宁换一批不错位续播
        if (uiItems.isEmpty()) return false
        if (uiItems.map { it.music.id } != session.ids) return false
        // 重拉数据不带歌词/收藏态：合并播放器已恢复快照里的歌词，避免恢复后歌词丢失
        val previousById = player.playerState.value.playlist.associateBy { it.id }
        val merged = uiItems.map { item ->
            val previous = previousById[item.music.id] ?: return@map item
            item.copy(
                music = item.music.copy(
                    lyrics = item.music.lyrics ?: previous.lyrics,
                    lyricsTrans = item.music.lyricsTrans ?: previous.lyricsTrans,
                    lyricsUrl = item.music.lyricsUrl ?: previous.lyricsUrl,
                    isFavorite = item.music.isFavorite || previous.isFavorite,
                )
            )
        }
        heardIds.value = (session.excludeIds + session.ids).toSet()
        val safeIndex = session.index.coerceIn(0, merged.lastIndex)
        player.startStreamQueue(
            merged.map { it.music },
            merged.associate { it.music.id to it.clips },
            safeIndex,
            "stream",
            null,
        )
        // startStreamQueue 固定从片段起点起播，恢复播放位置需补一次 seek；
        // 延迟一跳等 setMediaItem 生效，失败（控件未就绪等）最劣从片段起点播，可接受
        if (session.positionMs > 0) {
            viewModelScope.launch {
                delay(400)
                runCatching { player.seekFree(session.positionMs) }
            }
        }
        writeSession(
            mood = session.mood,
            userId = userId,
            seed = session.seed,
            index = safeIndex,
            positionMs = session.positionMs,
            items = merged,
            excludeIds = session.excludeIds,
        )
        lastPersistedIndex = safeIndex
        updateState { StreamUiState.Ready(merged, session.mood) }
        return true
    }

    /** 写音流会话快照（轻量：mood/seed/index/position/id 序列，不含歌曲本体） */
    private suspend fun writeSession(
        mood: String?,
        userId: Long?,
        seed: String,
        index: Int,
        positionMs: Long,
        items: List<StreamItemUi>,
        excludeIds: List<Long>,
    ) {
        val session = StreamSession(
            mood = mood,
            userId = userId,
            seed = seed,
            index = index.coerceAtLeast(0),
            positionMs = positionMs.coerceAtLeast(0L),
            ids = items.map { it.music.id },
            excludeIds = excludeIds,
        )
        runCatching { preferences.saveStreamSessionJson(StreamSession.encode(session)) }
    }

    /** 进度变化时增量更新快照的 index/position（读旧快照改字段，避免依赖内存态） */
    private suspend fun persistSession(index: Int, positionMs: Long) {
        val raw = runCatching { preferences.getStreamSessionJson() }.getOrNull()
        val session = StreamSession.decode(raw) ?: return
        if (session.index == index && session.positionMs == positionMs) return
        runCatching {
            preferences.saveStreamSessionJson(
                StreamSession.encode(
                    session.copy(
                        index = index.coerceIn(0, session.ids.lastIndex.coerceAtLeast(0)),
                        positionMs = positionMs.coerceAtLeast(0L),
                    )
                )
            )
        }
    }

    /**
     * 回到音流页时兜底：其它 Tab（如在搜索页点播歌曲）会把播放队列换成自己的队列，
     * 此时音流页的卡片与播放内容会错位、划动也切不动。这里按 id 序列判断当前队列
     * 是否仍是音流队列，不是则按 [index] 页重新挂载（已是音流队列时不动，避免重播当前片段）。
     */
    private fun ensureQueue(index: Int) {
        val items = (currentState as? StreamUiState.Ready)?.items.orEmpty()
        if (items.isEmpty()) return
        val current = player.playerState.value.playlist
        if (current.size == items.size && current.indices.all { current[it].id == items[it].music.id }) {
            return
        }
        player.startStreamQueue(
            items.map { it.music },
            items.associate { it.music.id to it.clips },
            index,
            "stream",
            null,
        )
    }

    private companion object {
        /** 管理员身份判定失败后的重试冷却，避免每次打开菜单都打接口 */
        const val ADMIN_CHECK_RETRY_MS = 30_000L
    }
}
