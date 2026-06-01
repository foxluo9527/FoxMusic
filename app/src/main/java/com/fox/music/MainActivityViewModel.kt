package com.fox.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.EventViewModel
import com.fox.music.core.domain.repository.AppUpdateRepository
import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.domain.usecase.CheckAppUpdateUseCase
import com.fox.music.core.model.app.ApkDownloadState
import com.fox.music.core.model.app.AppUpdateInfo
import com.fox.music.core.model.user.UserPreferences
import com.fox.music.core.network.token.TokenManager
import com.fox.music.core.player.controller.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    val musicController: MusicController,
    private val tokenManager: TokenManager,
    userPreferencesRepository: UserPreferencesRepository,
    private val chatRepository: ChatRepository,
    private val socialRepository: SocialRepository,
    private val playlistRepository: com.fox.music.core.domain.repository.PlaylistRepository,
    private val importRepository: com.fox.music.core.domain.repository.ImportRepository,
    private val checkAppUpdateUseCase: CheckAppUpdateUseCase,
    private val appUpdateRepository: AppUpdateRepository,
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState())
    val authState = _authState.asStateFlow()

    val userPreferences = userPreferencesRepository.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _playlistState = MutableStateFlow(PlaylistState())
    val playlistState = _playlistState.asStateFlow()

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState = _updateState.asStateFlow()

    private var downloadJob: Job? = null
    private var cachedSocialUnread = 0

    private val _chatUnreadCount = MutableStateFlow(0)
    val chatUnreadCount = _chatUnreadCount.asStateFlow()

    private val _totalUnreadCount = MutableStateFlow(0)
    val totalUnreadCount = _totalUnreadCount.asStateFlow()

    init {
        observeAuthState()
        observeSessionExpired()
        observeInboxUnread()
        checkForUpdate()
    }

    private fun observeInboxUnread() {
        chatRepository.observeConversations()
            .onEach { conversations ->
                val chatUnread = conversations.sumOf { it.unreadCount }
                _chatUnreadCount.value = chatUnread
                _totalUnreadCount.value = cachedSocialUnread + chatUnread
            }
            .launchIn(viewModelScope)

        EventViewModel.notificationsUpdated
            .onEach { refreshSocialUnreadForBadge() }
            .launchIn(viewModelScope)
    }

    private fun refreshSocialUnreadForBadge() {
        viewModelScope.launch {
            when (val result = socialRepository.getUnreadNotificationCount()) {
                is com.fox.music.core.common.result.Result.Success -> {
                    cachedSocialUnread = result.data
                    _totalUnreadCount.value = cachedSocialUnread + _chatUnreadCount.value
                }
                else -> Unit
            }
        }
    }

    private fun observeAuthState() {
        tokenManager.isLoggedIn
            .onEach { isLoggedIn ->
                _authState.update { it.copy(isLoggedIn = isLoggedIn) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeSessionExpired() {
        tokenManager.sessionExpired
            .onEach {
                _authState.update { it.copy(requireReLogin = true) }
            }
            .launchIn(viewModelScope)
    }

    fun onRequireReLoginHandled() {
        _authState.update { it.copy(requireReLogin = false) }
    }

    fun checkForUpdate(showWhenNoUpdate: Boolean = false) {
        viewModelScope.launch {
            checkAppUpdateUseCase(
                versionCode = BuildConfig.VERSION_CODE,
                channel = updateChannel,
            ).onSuccess { info ->
                if (info.hasUpdate) {
                    _updateState.update {
                        UpdateState(
                            showDialog = true,
                            updateInfo = info,
                            forceUpdate = info.shouldForceUpdate(BuildConfig.VERSION_CODE),
                        )
                    }
                } else if (showWhenNoUpdate) {
                    _updateState.update { it.copy(noUpdateMessage = "已是最新版本") }
                }
            }
        }
    }

    fun onNoUpdateMessageHandled() {
        _updateState.update { it.copy(noUpdateMessage = null) }
    }

    fun onDismissUpdate() {
        if (!_updateState.value.forceUpdate) {
            _updateState.update { it.copy(showDialog = false) }
        }
    }

    fun onConfirmUpdate() {
        val info = _updateState.value.updateInfo ?: return
        if (info.apkUrl.isBlank()) {
            _updateState.update { it.copy(error = "下载地址无效") }
            return
        }
        appUpdateRepository.getCachedApkFile(info.latestVersionCode)?.let { cached ->
            _updateState.update { it.copy(installFile = cached) }
            return
        }
        startDownload(info)
    }

    fun retryDownload() {
        val info = _updateState.value.updateInfo ?: return
        appUpdateRepository.getCachedApkFile(info.latestVersionCode)?.let { cached ->
            _updateState.update { it.copy(installFile = cached, error = null) }
            return
        }
        startDownload(info)
    }

    fun requestInstall(file: File) {
        _updateState.update { it.copy(installFile = file) }
    }

    fun onInstallAttemptFinished(launched: Boolean, file: File) {
        if (launched) {
            _updateState.update { it.copy(installFile = null, pendingInstallApk = null) }
        } else {
            _updateState.update { it.copy(installFile = null, pendingInstallApk = file) }
        }
    }

    private fun startDownload(info: AppUpdateInfo) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _updateState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0,
                    downloadIndeterminate = info.apkSize <= 0L,
                    downloadStatusText = "准备下载...",
                    error = null,
                    installFile = null,
                )
            }
            appUpdateRepository.downloadApk(
                url = info.apkUrl,
                versionCode = info.latestVersionCode,
                expectedSha256 = info.apkSha256,
                expectedSize = info.apkSize,
            ).collect { state ->
                when (state) {
                    is ApkDownloadState.Downloading -> {
                        val hasTotal = state.totalBytes > 0L
                        _updateState.update {
                            it.copy(
                                downloadProgress = if (hasTotal) state.progress else 0,
                                downloadIndeterminate = !hasTotal,
                                downloadStatusText = if (hasTotal) {
                                    "下载中 ${state.progress}%"
                                } else {
                                    "已下载 ${formatDownloadedSize(state.downloadedBytes)}"
                                },
                            )
                        }
                    }
                    is ApkDownloadState.Completed -> {
                        _updateState.update {
                            it.copy(
                                isDownloading = false,
                                downloadProgress = 100,
                                downloadIndeterminate = false,
                                downloadStatusText = null,
                                installFile = state.file,
                            )
                        }
                    }
                    is ApkDownloadState.Failed -> {
                        _updateState.update {
                            it.copy(
                                isDownloading = false,
                                error = state.message,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun formatDownloadedSize(bytes: Long): String {
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        return String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
    }

    data class AuthState(
        val isLoggedIn: Boolean = true,
        val requireReLogin: Boolean = false,
    )

    data class PlaylistState(
        val isPlaylistCreated: Boolean = false,
        val isPlaylistImported: Boolean = false,
        val error: String? = null,
    )

    data class UpdateState(
        val showDialog: Boolean = false,
        val updateInfo: AppUpdateInfo? = null,
        val forceUpdate: Boolean = false,
        val isDownloading: Boolean = false,
        val downloadProgress: Int = 0,
        val downloadIndeterminate: Boolean = false,
        val downloadStatusText: String? = null,
        val error: String? = null,
        val installFile: File? = null,
        val pendingInstallApk: File? = null,
        val noUpdateMessage: String? = null,
    )

    fun createPlaylist(title: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(
                title = title,
                description = null,
                coverImage = null,
                isPublic = true,
                tagIds = null,
            ).onSuccess {
                _playlistState.update { it.copy(isPlaylistCreated = true) }
            }.onError { _, msg ->
                _playlistState.update { it.copy(error = msg ?: "创建歌单失败") }
            }
        }
    }

    fun importPlaylist(url: String) {
        viewModelScope.launch {
            importRepository.importMusic(url)
                .onSuccess {
                    _playlistState.update { it.copy(isPlaylistImported = true) }
                }
                .onError { _, msg ->
                    _playlistState.update { it.copy(error = msg ?: "导入歌单失败") }
                }
        }
    }

    fun resetPlaylistState() {
        _playlistState.update { PlaylistState() }
    }

    private val updateChannel: String
        get() = if (BuildConfig.DEBUG) "debug" else "official"
}
