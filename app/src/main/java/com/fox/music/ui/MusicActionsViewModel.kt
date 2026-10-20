package com.fox.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.session.TokenManager
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.domain.repository.DownloadRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.domain.usecase.AddTrackToPlaylistUseCase
import com.fox.music.core.domain.usecase.GetMusicDetailUseCase
import com.fox.music.core.domain.usecase.GetPlaylistListUseCase
import com.fox.music.core.domain.usecase.GetProfileUseCase
import com.fox.music.core.domain.usecase.SubmitMusicFeedbackUseCase
import com.fox.music.core.domain.usecase.SubmitReportUseCase
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.user.isAdmin
import com.fox.music.core.player.controller.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MusicActionsUiState(
    val actionMusic: Music? = null,
    val showActionSheet: Boolean = false,
    val showPlaylistPicker: Boolean = false,
    val showReportSheet: Boolean = false,
    val showFeedbackSheet: Boolean = false,
    val showSelectFriend: Boolean = false,
    val pendingMusicIds: List<Long> = emptyList(),
    val pendingMusics: List<Music> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isPlaylistsLoading: Boolean = false,
    val currentQueueSize: Int = 0,
    val currentQueueTitle: String? = null,
    val isPendingInCurrentQueue: Boolean = false,
    val isReportSubmitting: Boolean = false,
    val isFeedbackSubmitting: Boolean = false,
    val isAdmin: Boolean = false,
)

sealed interface MusicActionsEffect {
    data class ShowToast(val message: String) : MusicActionsEffect
    data class NavigateToArtist(val artistId: Long) : MusicActionsEffect
    data class NavigateToSourceCorrect(val musicId: Long) : MusicActionsEffect
    data object RequestCreatePlaylist : MusicActionsEffect
    data object NavigateToSelectFriend : MusicActionsEffect
}

@HiltViewModel
class MusicActionsViewModel @Inject constructor(
    private val musicController: MusicController,
    private val addTrackToPlaylistUseCase: AddTrackToPlaylistUseCase,
    private val getMusicDetailUseCase: GetMusicDetailUseCase,
    private val submitReportUseCase: SubmitReportUseCase,
    private val submitMusicFeedbackUseCase: SubmitMusicFeedbackUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val getPlaylistListUseCase: GetPlaylistListUseCase,
    private val downloadRepository: DownloadRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val chatRepository: ChatRepository,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicActionsUiState())
    val uiState = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<MusicActionsEffect>()
    val effect: SharedFlow<MusicActionsEffect> = _effect.asSharedFlow()

    /**
     * 管理员身份判定状态。
     * 成功判定过一次就不再请求；失败（未登录 / 网络异常）只在冷却后重试。
     */
    private var adminStatusConfirmed = false
    private var adminCheckJob: Job? = null
    private var lastAdminCheckAtMs = 0L

    init {
        // 登录 / 切换账号 / 退出登录都必须重新判定管理员身份。
        // 旧实现只在首次打开菜单时判定一次、且失败不重试，于是「登录前打开过菜单」
        // （或冷启动时 token 还没从 DataStore 读出）的会话会一直看不到「音源纠错」入口，
        // 只有重启 App 让 ViewModel 重建才恢复。
        viewModelScope.launch {
            tokenManager.isLoggedIn
                .distinctUntilChanged()
                .collect { loggedIn ->
                    resetAdminStatus()
                    if (loggedIn) loadAdminStatus(force = true)
                }
        }
    }

    private fun resetAdminStatus() {
        adminCheckJob?.cancel()
        adminCheckJob = null
        adminStatusConfirmed = false
        lastAdminCheckAtMs = 0L
        _uiState.update { it.copy(isAdmin = false) }
    }

    fun showMusicActions(music: Music) {
        _uiState.update {
            it.copy(actionMusic = music, showActionSheet = true)
        }
        loadAdminStatus()
    }

    /** 拉取当前用户资料，判断是否管理员（决定是否显示「音源纠错」入口） */
    private fun loadAdminStatus(force: Boolean = false) {
        if (!force) {
            if (adminStatusConfirmed) return
            if (adminCheckJob?.isActive == true) return
            val last = lastAdminCheckAtMs
            if (last != 0L && System.currentTimeMillis() - last < ADMIN_CHECK_RETRY_MS) return
        }
        adminCheckJob?.cancel()
        adminCheckJob = viewModelScope.launch {
            try {
                getProfileUseCase()
                    .onSuccess { user ->
                        adminStatusConfirmed = true
                        _uiState.update { it.copy(isAdmin = user.isAdmin) }
                    }
                    .onError { _, _ ->
                        // 失败保持「未确认」，冷却时间过后下次打开菜单再试
                    }
            } finally {
                lastAdminCheckAtMs = System.currentTimeMillis()
            }
        }
    }

    fun dismissActionSheet() {
        _uiState.update { it.copy(showActionSheet = false) }
    }

    fun playNext() {
        val music = _uiState.value.actionMusic ?: return
        musicController.addToQueue(music)
        viewModelScope.launch {
            _effect.emit(MusicActionsEffect.ShowToast("已添加到下一首播放"))
        }
        dismissActionSheet()
    }

    fun showAddToPlaylist(musics: List<Music>) {
        if (musics.isEmpty()) return
        val queueState = musicController.playerState.value
        val queueMusicIds = queueState.playlist.map { it.id }.toSet()
        _uiState.update {
            it.copy(
                pendingMusicIds = musics.map { music -> music.id },
                pendingMusics = musics,
                showPlaylistPicker = true,
                isPlaylistsLoading = true,
                currentQueueSize = queueState.playlist.size,
                currentQueueTitle = queueState.playlistSource?.title,
                isPendingInCurrentQueue = musics.all { music -> music.id in queueMusicIds },
            )
        }
        loadPlaylists()
    }

    fun showAddToPlaylistForCurrentMusic() {
        val music = _uiState.value.actionMusic ?: return
        if (music.isThirdParty) {
            viewModelScope.launch {
                _effect.emit(MusicActionsEffect.ShowToast("第三方歌曲暂不支持添加到平台歌单"))
            }
            return
        }
        _uiState.update { it.copy(showActionSheet = false) }
        showAddToPlaylist(listOf(music))
    }

    fun dismissPlaylistPicker() {
        _uiState.update {
            it.copy(
                showPlaylistPicker = false,
                pendingMusicIds = emptyList(),
                pendingMusics = emptyList(),
                currentQueueSize = 0,
                currentQueueTitle = null,
                isPendingInCurrentQueue = false,
            )
        }
    }

    fun showReportSheet() {
        _uiState.update {
            it.copy(showActionSheet = false, showReportSheet = true)
        }
    }

    fun dismissReportSheet() {
        _uiState.update { it.copy(showReportSheet = false) }
    }

    /** 打开「反馈音乐不匹配」弹窗 */
    fun showFeedbackSheet() {
        _uiState.update {
            it.copy(showActionSheet = false, showFeedbackSheet = true)
        }
    }

    fun dismissFeedbackSheet() {
        _uiState.update { it.copy(showFeedbackSheet = false) }
    }

    /** 提交音源不匹配反馈 */
    fun submitFeedback(content: String?) {
        val music = _uiState.value.actionMusic ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isFeedbackSubmitting = true) }
            submitMusicFeedbackUseCase(musicId = music.id, content = content)
                .onSuccess {
                    dismissFeedbackSheet()
                    _effect.emit(MusicActionsEffect.ShowToast("反馈已提交，感谢你的纠音"))
                }
                .onError { _, msg ->
                    _effect.emit(MusicActionsEffect.ShowToast(msg ?: "反馈提交失败"))
                }
            _uiState.update { it.copy(isFeedbackSubmitting = false) }
        }
    }

    /** 管理员：跳转音源纠错页 */
    fun showSourceCorrect() {
        val music = _uiState.value.actionMusic ?: return
        if (music.isThirdParty) {
            viewModelScope.launch {
                _effect.emit(MusicActionsEffect.ShowToast("第三方歌曲不支持音源纠错"))
            }
            return
        }
        dismissActionSheet()
        viewModelScope.launch {
            _effect.emit(MusicActionsEffect.NavigateToSourceCorrect(music.id))
        }
    }

    fun submitReport(reason: String, description: String?) {
        val music = _uiState.value.actionMusic ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isReportSubmitting = true) }
            submitReportUseCase(
                targetType = "music",
                targetId = music.id,
                reason = reason,
                description = description,
            ).onSuccess {
                dismissReportSheet()
                _effect.emit(MusicActionsEffect.ShowToast("举报已提交"))
            }.onError { _, msg ->
                _effect.emit(MusicActionsEffect.ShowToast(msg ?: "举报提交失败"))
            }
            _uiState.update { it.copy(isReportSubmitting = false) }
        }
    }

    fun addPendingToCurrentQueue() {
        val state = _uiState.value
        if (state.pendingMusicIds.isEmpty()) return
        viewModelScope.launch {
            val musics = resolvePendingMusics(state)
            if (musics.isEmpty()) {
                _effect.emit(MusicActionsEffect.ShowToast("无法获取歌曲信息"))
                return@launch
            }
            musicController.addAllToQueue(musics)
            dismissPlaylistPicker()
            val message = if (musics.size == 1) {
                "已添加到播放列表"
            } else {
                "已添加 ${musics.size} 首到播放列表"
            }
            _effect.emit(MusicActionsEffect.ShowToast(message))
        }
    }

    fun addTracksToPlaylist(playlistId: Long) {
        val musicIds = _uiState.value.pendingMusicIds
        if (musicIds.isEmpty()) return
        viewModelScope.launch {
            addTrackToPlaylistUseCase(playlistId, musicIds)
                .onSuccess {
                    dismissPlaylistPicker()
                    _effect.emit(MusicActionsEffect.ShowToast("已添加到歌单"))
                }
                .onError { _, msg ->
                    _effect.emit(MusicActionsEffect.ShowToast(msg ?: "添加失败"))
                }
        }
    }

    fun requestCreatePlaylist() {
        dismissPlaylistPicker()
        viewModelScope.launch {
            _effect.emit(MusicActionsEffect.RequestCreatePlaylist)
        }
    }

    fun addAllToQueue(musics: List<Music>) {
        if (musics.isEmpty()) return
        musicController.addAllToQueue(musics)
        viewModelScope.launch {
            _effect.emit(MusicActionsEffect.ShowToast("已添加 ${musics.size} 首到播放列表"))
        }
    }

    fun downloadMusics(musics: List<Music>) {
        val downloadable = musics.filterNot { MediaUrlResolver.isLocalMedia(it.url) }
        if (downloadable.isEmpty()) return
        viewModelScope.launch {
            downloadRepository.enqueue(downloadable)
            _effect.emit(MusicActionsEffect.ShowToast("已加入下载队列"))
        }
    }

    fun downloadCurrentMusic() {
        val music = _uiState.value.actionMusic ?: return
        downloadMusics(listOf(music))
        dismissActionSheet()
    }

    fun onArtistClick(artistId: Long) {
        val music = _uiState.value.actionMusic
        if (music?.isThirdParty == true) {
            viewModelScope.launch {
                _effect.emit(MusicActionsEffect.ShowToast("第三方歌曲暂不支持跳转艺人详情"))
            }
            return
        }
        dismissActionSheet()
        viewModelScope.launch {
            _effect.emit(MusicActionsEffect.NavigateToArtist(artistId))
        }
    }

    /** 打开好友选择页面，用于分享当前音乐 */
    fun shareCurrentMusic() {
        val music = _uiState.value.actionMusic ?: return
        if (music.isThirdParty) {
            viewModelScope.launch {
                _effect.emit(MusicActionsEffect.ShowToast("第三方歌曲暂不支持分享"))
            }
            return
        }
        _uiState.update { it.copy(showActionSheet = false, showSelectFriend = true) }
        viewModelScope.launch {
            _effect.emit(MusicActionsEffect.NavigateToSelectFriend)
        }
    }

    fun dismissSelectFriend() {
        _uiState.update { it.copy(showSelectFriend = false) }
    }

    /** 选择好友后发送分享消息 */
    fun sendShareMusic(friendId: Long) {
        val music = _uiState.value.actionMusic ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showSelectFriend = false) }
            chatRepository.sendShareMessage(
                receiverId = friendId,
                shareType = "music",
                shareId = music.id,
                content = "",
            ).onSuccess {
                _effect.emit(MusicActionsEffect.ShowToast("分享成功"))
            }.onError { _, msg ->
                _effect.emit(MusicActionsEffect.ShowToast(msg ?: "分享失败"))
            }
        }
    }

    private suspend fun resolvePendingMusics(state: MusicActionsUiState): List<Music> {
        val knownById = buildMap {
            state.actionMusic?.let { put(it.id, it) }
            state.pendingMusics.forEach { put(it.id, it) }
        }
        val resolved = mutableListOf<Music>()
        for (id in state.pendingMusicIds) {
            val known = knownById[id]
            if (known != null) {
                resolved.add(known)
                continue
            }
            getMusicDetailUseCase(id).onSuccess { resolved.add(it.music) }
        }
        return resolved
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            val userId = userPreferencesRepository.userPreferences.first().userId
            getPlaylistListUseCase(userId)
                .onSuccess { playlists ->
                    _uiState.update {
                        it.copy(playlists = playlists, isPlaylistsLoading = false)
                    }
                }
                .onError { _, _ ->
                    _uiState.update {
                        it.copy(playlists = emptyList(), isPlaylistsLoading = false)
                    }
                }
        }
    }

    private companion object {
        /** 管理员身份判定失败后的重试冷却，避免每次打开菜单都打接口 */
        const val ADMIN_CHECK_RETRY_MS = 30_000L
    }
}
