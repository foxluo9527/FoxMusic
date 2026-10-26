package com.fox.music.feature.stream

import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.cache.TabPageCache
import com.fox.music.core.common.cache.TabPageKeys
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
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.PlaybackSourceKind
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
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject

@Serializable
data class StreamItemUi(
    val music: Music,
    val clips: List<com.fox.music.core.model.music.ClipRange>,
    val artist: String? = null,
)

/** 每批音流的歌曲数（服务端 /api/highlight/stream 的 limit 上限为 60，30 一批较稳） */
internal const val STREAM_PAGE_SIZE = 30

/**
 * 收听锚点落盘节流间隔：整批卡片 JSON 不便宜（最多 10 批），
 * 所以内存快照每次更新、磁盘最多每 30s 写一次（换歌时立即写）。
 */
internal const val ANCHOR_DISK_WRITE_INTERVAL_MS = 30_000L

/** 上滑到距队尾还剩几张卡片时预拉下一批（提前量，避免滑到底干等） */
internal const val STREAM_LOAD_MORE_PREFETCH = 3

/**
 * 单个音流会话的卡片上限（10 批）：再往下拉体验也不会更好，
 * 而队列要常驻内存并随播放状态持久化，无限增长会拖慢冷启动。
 */
internal const val MAX_STREAM_ITEMS = STREAM_PAGE_SIZE * 10

sealed interface StreamUiState : UiState {
    data object Loading : StreamUiState
    data class Ready(
        val items: List<StreamItemUi>,
        val mood: String? = null,
        /** 正在加载下一批（底部转圈） */
        val isLoadingMore: Boolean = false,
        /** 服务端还能给出新歌；false = 这批已经是全部（或反复拿到重复） */
        val canLoadMore: Boolean = true,
        /** 加载更多失败的文案（可重试） */
        val loadMoreError: String? = null,
    ) : StreamUiState
    data class Error(val message: String) : StreamUiState
}

sealed interface StreamUiIntent : UiIntent {
    data class Load(val mood: String? = null) : StreamUiIntent
    /** 上滑接近队尾：追加下一批（幂等，内部防重入） */
    data object LoadMore : StreamUiIntent
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

    /**
     * 上次听到的卡片索引：回到音流页、且播放队列已被其它页面顶掉时，
     * 页面按这个索引重新挂载队列（而不是从第 0 张重来），实现「记忆上次听到的位置」。
     */
    private val _anchorIndex = MutableStateFlow(0)
    val anchorIndex: StateFlow<Int> = _anchorIndex.asStateFlow()

    /** 上次听到的歌曲内位置（整曲绝对毫秒），与 [_anchorIndex] 配对使用 */
    private var anchorPositionMs = 0L

    /** 本次会话解析出的 userId：快照归属校验与写快照都用它 */
    private var sessionUserId: Long? = null

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
        // 先注册快照的磁盘编解码器，随后的 restoreFromCache 才能在冷启动时
        // 解析上一进程落盘的整批卡片（不打接口、不进 Loading）
        registerStreamPageCacheCodec()
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
            val userId = runCatching { preferences.userId.first()?.toLongOrNull() }.getOrNull()
            sessionUserId = userId
            // ① 进程内 / 磁盘已有上一批数据 → 直接复用，不打接口、不进 Loading
            val snapshot = TabPageCache.readAsync<StreamPageSnapshot>(TabPageKeys.STREAM)
            if (snapshot != null && restoreFromCache(snapshot, userId)) return@launch
            // ② 否则用会话快照（只有 id 序列）同 seed 重拉复现上一批并续播；失败/批次漂移则换一批
            val session = StreamSession.decode(
                runCatching { preferences.getStreamSessionJson() }.getOrNull()
            )
            if (session == null || !restoreFromSession(savedMood, session)) {
                load(savedMood)
            }
        }
        // 音流进度记忆：索引变化即时落库，播放位置节流落库（仅当前队列仍是本批音流时）
        viewModelScope.launch {
            player.playerState.collect { ps ->
                val items = (currentState as? StreamUiState.Ready)?.items ?: return@collect
                if (!isSameStreamQueue(items, ps.playlist)) return@collect
                // 记住「听到第几张 + 听到哪儿」：回到音流页时按这里续播
                _anchorIndex.value = ps.currentIndex
                anchorPositionMs = ps.position
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
                _anchorIndex.value = ps.currentIndex
                anchorPositionMs = positionMs
                persistSession(index = ps.currentIndex, positionMs = positionMs)
                // 同步进程内快照的收听锚点（页面重建后能接着上次的位置）
                refreshCacheAnchor(index = ps.currentIndex, positionMs = positionMs)
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
            StreamUiIntent.LoadMore -> loadMore()
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

    /** 当前批次的 seed：换一批 / 切场景时才变，加载更多沿用同一个，保证同一次刷歌的口径一致 */
    private var currentSeed: String? = null
    /** 首屏请求时用的 exclude：会话快照重放分页时必须原样复用，否则拉回来的不是同一批 */
    private var baseExcludeIds: List<Long> = emptyList()
    /** 加载更多的协程：用于防重入（滑到队尾会连续触发多次） */
    private var loadMoreJob: Job? = null

    private fun load(mood: String?) {
        _mood.value = mood
        // 换一批 / 切场景：作废上一批的分页状态
        loadMoreJob?.cancel()
        loadMoreJob = null
        // 记忆听歌模式：把本次选择（含「默认模式」=null）写入本地，下次冷启动沿用
        viewModelScope.launch { runCatching { preferences.saveStreamMood(mood) } }
        viewModelScope.launch {
            updateState { StreamUiState.Loading }
            runCatching {
                // 真实 userId：个性化与去重都依赖它（此前一直传 null，导致永远是全局热门榜）
                // 每次 load 生成新 seed：每次打开 / 换风格都会换一批，体验不重样
                val userId = sessionUserId
                    ?: runCatching { preferences.userId.first()?.toLongOrNull() }.getOrNull()
                sessionUserId = userId
                val seed = UUID.randomUUID().toString()
                val exclude = heardIds.value.toList()
                currentSeed = seed
                baseExcludeIds = exclude
                val items = repository.getStream(
                    mood = mood,
                    userId = userId,
                    limit = STREAM_PAGE_SIZE,
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
                    PlaybackPlaylistSource(PlaybackSourceKind.STREAM, title = "音流"),
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
                _anchorIndex.value = 0
                anchorPositionMs = 0L
                writeCache(uiItems, index = 0, positionMs = 0L, canLoadMore = true)
                updateState { StreamUiState.Ready(uiItems, mood) }
            }.onFailure { e ->
                updateState { StreamUiState.Error(e.message ?: "音流加载失败") }
            }
        }
    }

    /**
     * 上滑加载更多：沿用在用的 seed，以「已加载的全部歌曲」为 exclude 拉下一批并追加到队尾。
     *
     * 三个必须做对的点：
     * 1) **不重建播放队列**：走 MusicController.appendStreamQueue，只把新歌挂到内存队列尾部，
     *    不走 startStreamQueue（那会从头起播，把用户正在听的片段打断）；
     * 2) **客户端自己去重**：服务端 exclude 只是「优先排除」，mood 走语义召回的兜底分支
     *    甚至不带 exclude，重复 id 会导致同一首歌出两张卡片、队列里也撞车；
     * 3) **幂等 + 防重入**：滑到队尾会连续触发多次，靠 job + 状态位各挡一层。
     */
    private fun loadMore() {
        val ready = currentState as? StreamUiState.Ready ?: return
        if (ready.isLoadingMore || !ready.canLoadMore) return
        val seed = currentSeed ?: return
        if (loadMoreJob?.isActive == true) return
        val mood = _mood.value
        loadMoreJob = viewModelScope.launch {
            updateState {
                (currentState as? StreamUiState.Ready)?.copy(isLoadingMore = true, loadMoreError = null)
                    ?: currentState
            }
            val userId = runCatching { preferences.userId.first()?.toLongOrNull() }.getOrNull()
            runCatching {
                repository.getStream(
                    mood = mood,
                    userId = userId,
                    limit = STREAM_PAGE_SIZE,
                    seed = seed,
                    exclude = heardIds.value.toList(),
                )
            }.onSuccess { raw ->
                val existingIds = ready.items.mapTo(HashSet()) { it.music.id }
                val fresh = StreamClipPolicy.buildPlaylist(raw)
                    .filter { it.music.id !in existingIds }
                    .distinctBy { it.music.id }
                if (fresh.isEmpty()) {
                    // 服务端翻不出新歌了（或整批都是重复）：关掉加载更多的入口，不再空转
                    updateState {
                        (currentState as? StreamUiState.Ready)?.copy(isLoadingMore = false, canLoadMore = false)
                            ?: currentState
                    }
                    return@launch
                }
                val merged = ready.items + fresh
                // 只追加到内存队列尾部，当前片段不受影响。
                // 返回 false = 当前队列已不是音流队列（用户在别的 Tab 点播过歌）：
                // 卡片照常加（还能继续滑着看），但不写会话快照——那已不是可续播的音流队列。
                val appended = player.appendStreamQueue(
                    fresh.map { it.music },
                    fresh.associate { it.music.id to it.clips },
                )
                heardIds.value = heardIds.value + fresh.map { it.music.id }
                if (appended) {
                    val (index, positionMs) = currentPlaybackAnchor(merged)
                    writeSession(
                        mood = mood,
                        userId = userId,
                        seed = seed,
                        index = index,
                        positionMs = positionMs,
                        items = merged,
                        excludeIds = baseExcludeIds,
                    )
                }
                // 到底的判据只有「一条新歌都没拿到」：服务端会剔除没有音源的歌，
                // 单批不满 30 是常态（语义召回尤甚），拿「不满一批」当到底会误伤、
                // 让用户明明还有歌却滑不动。上限兜住无限增长。
                val exhausted = merged.size >= MAX_STREAM_ITEMS
                val (anchorIdx, anchorPos) = currentPlaybackAnchor(merged)
                writeCache(merged, anchorIdx, anchorPos, canLoadMore = !exhausted)
                updateState {
                    (currentState as? StreamUiState.Ready)?.copy(
                        items = merged,
                        isLoadingMore = false,
                        canLoadMore = !exhausted,
                        loadMoreError = null,
                    ) ?: currentState
                }
            }.onFailure { e ->
                // 失败保留入口，用户再滑一次即重试
                updateState {
                    (currentState as? StreamUiState.Ready)?.copy(
                        isLoadingMore = false,
                        loadMoreError = e.message ?: "加载更多失败",
                    ) ?: currentState
                }
            }
        }
    }

    /**
     * 当前播放锚点（索引 + 位置）：仅在「播放器里的队列仍是本页音流队列」时可信，
     * 否则（用户在别的 Tab 点播过歌）返回队首，避免把别人的播放位置写进音流快照。
     */
    private fun currentPlaybackAnchor(items: List<StreamItemUi>): Pair<Int, Long> {
        val ps = player.playerState.value
        return if (isSameStreamQueue(items, ps.playlist)) {
            ps.currentIndex.coerceIn(0, items.lastIndex) to ps.position
        } else {
            0 to 0L
        }
    }

    /**
     * 复用进程内上一批数据：不进 Loading、不打接口，直接把上一批卡片铺上去，
     * 并按快照里的锚点续上上次听到的位置。
     *
     * 只在「播放器里的队列已经不是这批音流」时重新挂载（挂载后补一次 seek 回到上次位置）；
     * 正在播这批时什么都不做，避免打断当前片段。
     */
    private suspend fun restoreFromCache(snapshot: StreamPageSnapshot, userId: Long?): Boolean {
        val items = snapshot.items
        if (items.isEmpty()) return false
        if (snapshot.ownerUserId != userId) return false
        _mood.value = snapshot.mood
        // 恢复即续上这批的分页状态：后续上滑加载更多沿用同一个 seed 与首屏 exclude
        currentSeed = snapshot.seed
        baseExcludeIds = snapshot.baseExcludeIds
        heardIds.value = snapshot.heardIds
        val safeIndex = snapshot.anchorIndex.coerceIn(0, items.lastIndex)
        _anchorIndex.value = safeIndex
        anchorPositionMs = snapshot.anchorPositionMs
        if (!isSameStreamQueue(items, player.playerState.value.playlist)) {
            player.startStreamQueue(
                items.map { it.music },
                items.associate { it.music.id to it.clips },
                safeIndex,
                "stream",
                PlaybackPlaylistSource(PlaybackSourceKind.STREAM, title = "音流"),
            )
            if (snapshot.anchorPositionMs > 0) {
                viewModelScope.launch {
                    delay(400)
                    runCatching { player.seekFree(snapshot.anchorPositionMs) }
                }
            }
        }
        lastPersistedIndex = safeIndex
        // 保持 DataStore 会话同步：进程被杀后冷启动仍能靠它复现这一批
        writeSession(
            mood = snapshot.mood,
            userId = userId,
            seed = snapshot.seed,
            index = safeIndex,
            positionMs = snapshot.anchorPositionMs,
            items = items,
            excludeIds = snapshot.baseExcludeIds,
        )
        updateState { StreamUiState.Ready(items, snapshot.mood, canLoadMore = snapshot.canLoadMore) }
        return true
    }

    /** 写进程内快照：下次进音流页直接复用（只更新锚点时也走这里） */
    private fun writeCache(
        items: List<StreamItemUi>,
        index: Int,
        positionMs: Long,
        canLoadMore: Boolean,
        persistToDisk: Boolean = true,
    ) {
        val seed = currentSeed ?: return
        TabPageCache.put(
            key = TabPageKeys.STREAM,
            value = StreamPageSnapshot(
                ownerUserId = sessionUserId,
                mood = _mood.value,
                items = items,
                seed = seed,
                baseExcludeIds = baseExcludeIds,
                heardIds = heardIds.value,
                anchorIndex = index.coerceAtLeast(0),
                anchorPositionMs = positionMs.coerceAtLeast(0L),
                canLoadMore = canLoadMore,
            ),
            persistToDisk = persistToDisk,
        )
    }

    /** 上一次锚点落盘的索引 / 时间，用于节流（内存里随时最新，磁盘可以懒一点） */
    private var lastAnchorDiskIndex = -2
    private var lastAnchorDiskWriteAtMs = 0L

    /** 只更新快照里的收听锚点（歌曲列表不变），播放位置节流回调用 */
    private fun refreshCacheAnchor(index: Int, positionMs: Long) {
        val items = (currentState as? StreamUiState.Ready)?.items ?: return
        val canLoadMore = (currentState as? StreamUiState.Ready)?.canLoadMore ?: true
        // 整批卡片 JSON 落盘不便宜（最多 10 批），所以锚点只按「换歌立即写 +
        // 每 30s 一次」的节流写盘；内存快照仍然每次都最新。
        val now = System.currentTimeMillis()
        val persist = index != lastAnchorDiskIndex ||
            now - lastAnchorDiskWriteAtMs >= ANCHOR_DISK_WRITE_INTERVAL_MS
        if (persist) {
            lastAnchorDiskIndex = index
            lastAnchorDiskWriteAtMs = now
        }
        writeCache(items, index, positionMs, canLoadMore, persistToDisk = persist)
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
        // 会话快照可能记录的是「多批拼接」后的长队列（上滑加载过更多）：
        // 必须按页原样重放（每页的 exclude = 之前所有页的 id），一次拉全部拉不出同样的序列——
        // 服务端的选取是「利用档 + 探索档」再按 exclude 过滤，一次取 60 与「先 30 再 30」结果不同。
        val pages = session.ids.chunked(STREAM_PAGE_SIZE)
        val uiItems = mutableListOf<StreamItemUi>()
        val rollingExclude = session.excludeIds.toMutableList()
        for (page in pages) {
            // 请求量固定按整批下发：最后一页可能不满（服务端剔掉了无音源的歌），
            // 若按实际条数去请求，语义召回分支会被「翻页上限」判定拦掉，反而重放不出来。
            // 多返回的那些一律截掉，只比对本页应有的一段。
            val got = runCatching {
                repository.getStream(
                    mood = session.mood,
                    userId = userId,
                    limit = STREAM_PAGE_SIZE,
                    seed = session.seed,
                    exclude = rollingExclude,
                )
            }.getOrElse { return false }
            val built = StreamClipPolicy.buildPlaylist(got).take(page.size)
            // 批次校验：曲库变动可能导致漂移，宁换一批不错位续播
            if (built.map { it.music.id } != page) return false
            uiItems += built
            rollingExclude += page
        }
        if (uiItems.isEmpty()) return false
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
        // 恢复成功即续上这一批的分页状态：后续上滑加载更多沿用同一个 seed 与首屏 exclude
        currentSeed = session.seed
        baseExcludeIds = session.excludeIds
        val safeIndex = session.index.coerceIn(0, merged.lastIndex)
        player.startStreamQueue(
            merged.map { it.music },
            merged.associate { it.music.id to it.clips },
            safeIndex,
            "stream",
            PlaybackPlaylistSource(PlaybackSourceKind.STREAM, title = "音流"),
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
        _anchorIndex.value = safeIndex
        anchorPositionMs = session.positionMs
        writeCache(merged, safeIndex, session.positionMs, canLoadMore = true)
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
        val safeIndex = index.coerceIn(0, items.lastIndex)
        player.startStreamQueue(
            items.map { it.music },
            items.associate { it.music.id to it.clips },
            safeIndex,
            "stream",
            PlaybackPlaylistSource(PlaybackSourceKind.STREAM, title = "音流"),
        )
        // 记忆上一次听到的位置：重新挂载队列时接着上次的进度播，而不是从片段头重来
        if (safeIndex == _anchorIndex.value && anchorPositionMs > 0) {
            val positionMs = anchorPositionMs
            viewModelScope.launch {
                delay(400)
                runCatching { player.seekFree(positionMs) }
            }
        }
    }

    private companion object {
        /** 管理员身份判定失败后的重试冷却，避免每次打开菜单都打接口 */
        const val ADMIN_CHECK_RETRY_MS = 30_000L
    }
}
