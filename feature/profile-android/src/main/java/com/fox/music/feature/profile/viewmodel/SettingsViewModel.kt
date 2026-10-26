package com.fox.music.feature.profile.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.repository.AppUpdateRepository
import com.fox.music.core.domain.repository.PlaybackCacheRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.domain.usecase.CheckAppUpdateUseCase
import com.fox.music.core.domain.usecase.GetProfileUseCase
import com.fox.music.core.domain.usecase.LogoutUseCase
import com.fox.music.core.model.app.ApkDownloadState
import com.fox.music.core.model.app.AppUpdateInfo
import com.fox.music.core.model.music.RepeatMode
import com.fox.music.core.model.music.SleepTimerState
import com.fox.music.core.model.user.DarkMode
import com.fox.music.core.model.user.User
import com.fox.music.core.model.user.UserPreferences
import com.fox.music.core.model.user.isAdmin
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.player.timer.SleepTimerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SettingsState(
    val user: User? = null,
    val preferences: UserPreferences = UserPreferences(),
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val cacheUsedBytes: Long = 0L,
    val isLoading: Boolean = false,
    val showLogoutDialog: Boolean = false,
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val choiceDialog: ChoiceDialog? = null,
    val versionName: String = "",
    val isCheckingUpdate: Boolean = false,
    val showUpdateDialog: Boolean = false,
    val updateInfo: AppUpdateInfo? = null,
    val forceUpdate: Boolean = false,
    val isDownloadingApk: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadIndeterminate: Boolean = false,
    val downloadStatusText: String? = null,
    val updateError: String? = null,
) : UiState

data class ChoiceDialog(
    val type: ChoiceType,
    val options: List<String>,
    val selectedIndex: Int,
)

/**
 * 主页只剩「即时操作类」的单选弹窗。
 *
 * 原来还有 DARK_MODE / CACHE_LIMIT / DOWNLOAD_QUALITY 三项：外观、存储、下载都已下沉为
 * 二级页，弹窗跟着搬去了 ThemeSettingsScreen / PreferencesSettingsViewModel。
 */
enum class ChoiceType {
    REPEAT_MODE,
    SLEEP_TIMER,
}

sealed interface SettingsIntent : UiIntent {
    data class Load(val fetchProfile: Boolean = true) : SettingsIntent
    /** 只重算缓存占用量（从存储页返回时用），不重打 profile 接口。 */
    data object RefreshCacheUsage : SettingsIntent
    data object OnEditProfileClick : SettingsIntent
    data object OnManageLibraryClick : SettingsIntent
    data object OnLogoutClick : SettingsIntent
    data object ConfirmLogout : SettingsIntent
    data object DismissLogoutDialog : SettingsIntent
    data object ShowRepeatModePicker : SettingsIntent
    data object ShowSleepTimerPicker : SettingsIntent
    data object OnCheckUpdateClick : SettingsIntent
    data object DismissUpdateDialog : SettingsIntent
    data object ConfirmUpdate : SettingsIntent
    data object RetryUpdateDownload : SettingsIntent
    data class OnChoiceSelected(val type: ChoiceType, val index: Int) : SettingsIntent
    data object DismissChoiceDialog : SettingsIntent
}

sealed interface SettingsEffect : UiEffect {
    data object NavigateToEditProfile : SettingsEffect
    data object NavigateToManageLibrary : SettingsEffect
    data object NavigateToLogin : SettingsEffect
    data class ShowMessage(val message: String) : SettingsEffect
    data class LaunchInstall(val file: File) : SettingsEffect
    data object RecreateActivity : SettingsEffect
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getProfileUseCase: GetProfileUseCase,
    private val checkAppUpdateUseCase: CheckAppUpdateUseCase,
    private val appUpdateRepository: AppUpdateRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playbackCacheRepository: PlaybackCacheRepository,
    private val logoutUseCase: LogoutUseCase,
    private val musicController: MusicController,
    private val sleepTimerManager: SleepTimerManager,
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {

    private val sleepTimerOptionsMinutes = listOf(0L, 15L, 30L, 45L, 60L, 90L, 120L)

    private var downloadJob: Job? = null

    init {
        updateState { copy(versionName = appVersionName()) }
        userPreferencesRepository.userPreferences
            .onEach { prefs ->
                updateState { copy(preferences = prefs) }
            }
            .launchIn(viewModelScope)

        musicController.playerState
            .onEach { playerState ->
                updateState { copy(repeatMode = playerState.repeatMode) }
            }
            .launchIn(viewModelScope)

        sleepTimerManager.state
            .onEach { timer ->
                updateState { copy(sleepTimer = timer) }
            }
            .launchIn(viewModelScope)

        sleepTimerManager.finished
            .onEach {
                sendEffect(SettingsEffect.ShowMessage("定时关闭，播放已停止"))
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.Load -> load(intent.fetchProfile)
            SettingsIntent.RefreshCacheUsage -> refreshCacheUsage()
            SettingsIntent.OnEditProfileClick -> sendEffect(SettingsEffect.NavigateToEditProfile)
            SettingsIntent.OnManageLibraryClick -> {
                if (currentState.user?.isAdmin == true) {
                    sendEffect(SettingsEffect.NavigateToManageLibrary)
                }
            }
            SettingsIntent.OnLogoutClick -> updateState { copy(showLogoutDialog = true) }
            SettingsIntent.DismissLogoutDialog -> updateState { copy(showLogoutDialog = false) }
            SettingsIntent.ConfirmLogout -> logout()
            SettingsIntent.ShowRepeatModePicker -> showRepeatModePicker()
            SettingsIntent.ShowSleepTimerPicker -> showSleepTimerPicker()
            SettingsIntent.OnCheckUpdateClick -> checkForUpdate()
            SettingsIntent.DismissUpdateDialog -> {
                if (!currentState.forceUpdate) {
                    updateState { copy(showUpdateDialog = false, updateError = null) }
                }
            }
            SettingsIntent.ConfirmUpdate -> confirmUpdate()
            SettingsIntent.RetryUpdateDownload -> confirmUpdate()
            is SettingsIntent.OnChoiceSelected -> onChoiceSelected(intent.type, intent.index)
            SettingsIntent.DismissChoiceDialog -> updateState { copy(choiceDialog = null) }
        }
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            updateState { copy(isCheckingUpdate = true, updateError = null) }
            checkAppUpdateUseCase(
                versionCode = appVersionCode(),
                channel = updateChannel(),
            ).onSuccess { info ->
                updateState { copy(isCheckingUpdate = false) }
                if (info.hasUpdate) {
                    updateState {
                        copy(
                            showUpdateDialog = true,
                            updateInfo = info,
                            forceUpdate = info.shouldForceUpdate(appVersionCode()),
                        )
                    }
                } else {
                    sendEffect(SettingsEffect.ShowMessage("已是最新版本"))
                }
            }.onError { _, msg ->
                updateState { copy(isCheckingUpdate = false) }
                sendEffect(SettingsEffect.ShowMessage(msg ?: "检查更新失败"))
            }
        }
    }

    private fun confirmUpdate() {
        val info = currentState.updateInfo ?: return
        if (info.apkUrl.isBlank()) {
            updateState { copy(updateError = "下载地址无效") }
            return
        }
        appUpdateRepository.getCachedApkFile(info.latestVersionCode)?.let { cached ->
            updateState { copy(showUpdateDialog = false, updateError = null) }
            sendEffect(SettingsEffect.LaunchInstall(cached))
            return
        }
        startDownload(info)
    }

    private fun startDownload(info: AppUpdateInfo) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            updateState {
                copy(
                    isDownloadingApk = true,
                    downloadProgress = 0,
                    downloadIndeterminate = info.apkSize <= 0L,
                    downloadStatusText = "准备下载...",
                    updateError = null,
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
                        updateState {
                            copy(
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
                        updateState {
                            copy(
                                isDownloadingApk = false,
                                downloadProgress = 100,
                                downloadIndeterminate = false,
                                downloadStatusText = null,
                                showUpdateDialog = false,
                            )
                        }
                        sendEffect(SettingsEffect.LaunchInstall(state.file))
                    }
                    is ApkDownloadState.Failed -> {
                        updateState {
                            copy(
                                isDownloadingApk = false,
                                updateError = state.message,
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

    private fun appVersionCode(): Int {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    }

    private fun appVersionName(): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    private fun updateChannel(): String {
        return if (context.packageName.endsWith(".debug")) "debug" else "official"
    }

    private fun load(fetchProfile: Boolean) {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            refreshCacheUsageBlocking()
            if (!fetchProfile) {
                updateState { copy(isLoading = false) }
                return@launch
            }
            getProfileUseCase()
                .onSuccess { user -> updateState { copy(user = user, isLoading = false) } }
                .onError { _, msg ->
                    updateState { copy(isLoading = false) }
                    sendEffect(SettingsEffect.ShowMessage(msg ?: "加载失败"))
                }
        }
    }

    /** 主页「存储空间」这行摘要的用量刷新（公开给 intent，从存储页返回时用）。 */
    private fun refreshCacheUsage() {
        viewModelScope.launch { refreshCacheUsageBlocking() }
    }

    private suspend fun refreshCacheUsageBlocking() {
        val used = playbackCacheRepository.getCacheUsedBytes()
        updateState { copy(cacheUsedBytes = used) }
    }

    private fun logout() {
        viewModelScope.launch {
            updateState { copy(showLogoutDialog = false, isLoading = true) }
            logoutUseCase()
                .onSuccess {
                    updateState { copy(isLoading = false) }
                    sendEffect(SettingsEffect.NavigateToLogin)
                }
                .onError { _, msg ->
                    updateState { copy(isLoading = false) }
                    sendEffect(SettingsEffect.ShowMessage(msg ?: "退出失败"))
                }
        }
    }

    private fun showRepeatModePicker() {
        val modes = listOf(RepeatMode.SEQUENTIAL, RepeatMode.ALL, RepeatMode.ONE, RepeatMode.RANDOM)
        val labels = listOf("顺序播放", "列表循环", "单曲循环", "随机播放")
        updateState {
            copy(
                choiceDialog = ChoiceDialog(
                    type = ChoiceType.REPEAT_MODE,
                    options = labels,
                    selectedIndex = modes.indexOf(repeatMode).coerceAtLeast(0),
                ),
            )
        }
    }

    private fun showSleepTimerPicker() {
        val labels = sleepTimerOptionsMinutes.map { minutes ->
            if (minutes == 0L) "关闭" else "${minutes} 分钟"
        }
        val timer = currentState.sleepTimer
        val currentTotalMinutes = timer.totalMs / 60_000L
        val selectedIndex = if (!timer.isActive) {
            0
        } else {
            sleepTimerOptionsMinutes.indexOf(currentTotalMinutes).let {
                if (it >= 0) it else 0
            }
        }
        updateState {
            copy(
                choiceDialog = ChoiceDialog(
                    type = ChoiceType.SLEEP_TIMER,
                    options = labels,
                    selectedIndex = selectedIndex,
                ),
            )
        }
    }

    private fun cancelSleepTimer() {
        sleepTimerManager.cancel()
        sendEffect(SettingsEffect.ShowMessage("已取消定时关闭"))
    }

    private fun startSleepTimer(minutes: Long) {
        sleepTimerManager.start(minutes * 60_000L)
        sendEffect(SettingsEffect.ShowMessage("将在 ${minutes} 分钟后停止播放"))
    }

    private fun onChoiceSelected(type: ChoiceType, index: Int) {
        viewModelScope.launch {
            when (type) {
                ChoiceType.REPEAT_MODE -> {
                    val modes = listOf(RepeatMode.SEQUENTIAL, RepeatMode.ALL, RepeatMode.ONE, RepeatMode.RANDOM)
                    musicController.setRepeatMode(modes.getOrElse(index) { RepeatMode.ALL })
                }
                ChoiceType.SLEEP_TIMER -> {
                    val minutes = sleepTimerOptionsMinutes.getOrElse(index) { 0L }
                    if (minutes == 0L) {
                        if (currentState.sleepTimer.isActive) {
                            cancelSleepTimer()
                        }
                    } else {
                        startSleepTimer(minutes)
                    }
                }
            }
            updateState { copy(choiceDialog = null) }
        }
    }

    fun repeatModeLabel(mode: RepeatMode): String = when (mode) {
        RepeatMode.SEQUENTIAL -> "顺序播放"
        RepeatMode.ALL -> "列表循环"
        RepeatMode.ONE -> "单曲循环"
        RepeatMode.RANDOM -> "随机播放"
    }

    fun darkModeLabel(mode: DarkMode): String = when (mode) {
        DarkMode.FOLLOW_SYSTEM -> "跟随系统"
        DarkMode.LIGHT -> "浅色"
        DarkMode.DARK -> "深色"
    }

    fun cacheLimitLabel(maxBytes: Long): String = formatBytes(maxBytes)

    fun sleepTimerLabel(timer: SleepTimerState): String {
        if (!timer.isActive) return "未开启"
        return "剩余 ${formatDuration(timer.remainingMs)}"
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        if (bytes < 1024 * 1024 * 1024) return "${bytes / (1024 * 1024)} MB"
        return String.format("%.1f GB", bytes.toDouble() / (1024 * 1024 * 1024))
    }
}
