package com.fox.music.feature.profile.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.repository.PlaybackCacheRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.model.user.PlayQuality
import com.fox.music.core.model.user.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 「通用」二级页（下载 / 存储空间）共用的偏好设置 ViewModel。
 *
 * 为什么从 [SettingsViewModel] 拆出来：这两页原来把「缓存上限」「清理缓存」「仅 WiFi 下载」
 * 「下载音质」四件事平铺在设置主页的「应用」组里，和外观、通知、版本、记录混成 11 行。
 * 拆成二级页后它们只关心 prefs + 缓存用量，不再需要主页那套账号 / 更新 / 定时关闭的状态，
 * 因此单独一份状态机，主页那边的 [SettingsState] 也能瘦身。
 */
data class PreferencesSettingsState(
    val preferences: UserPreferences = UserPreferences(),
    val cacheUsedBytes: Long = 0L,
    val isClearingCache: Boolean = false,
    val showClearCacheDialog: Boolean = false,
    val choice: PrefChoiceDialog? = null,
) : UiState

/** 二级页里的单选弹窗（缓存上限 / 下载音质）。 */
data class PrefChoiceDialog(
    val kind: PrefChoiceKind,
    val options: List<String>,
    val selectedIndex: Int,
)

enum class PrefChoiceKind {
    CACHE_LIMIT,
    DOWNLOAD_QUALITY,
}

sealed interface PreferencesSettingsIntent : UiIntent {
    /** 进入页面时刷新一次缓存占用量（幂等，返回本页也会重跑）。 */
    data object RefreshCacheUsage : PreferencesSettingsIntent
    data class UpdateDownloadOnWifiOnly(val enabled: Boolean) : PreferencesSettingsIntent
    data object ShowCacheLimitPicker : PreferencesSettingsIntent
    data object ShowDownloadQualityPicker : PreferencesSettingsIntent
    data class OnChoiceSelected(val index: Int) : PreferencesSettingsIntent
    data object DismissChoiceDialog : PreferencesSettingsIntent
    data object OnClearCacheClick : PreferencesSettingsIntent
    data object ConfirmClearCache : PreferencesSettingsIntent
    data object DismissClearCacheDialog : PreferencesSettingsIntent
}

sealed interface PreferencesSettingsEffect : UiEffect {
    data class ShowMessage(val message: String) : PreferencesSettingsEffect
}

@HiltViewModel
class PreferencesSettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playbackCacheRepository: PlaybackCacheRepository,
) : MviViewModel<PreferencesSettingsState, PreferencesSettingsIntent, PreferencesSettingsEffect>(
    initialState = PreferencesSettingsState(),
) {

    private val cacheLimitOptions = listOf(
        256L * 1024 * 1024,
        512L * 1024 * 1024,
        1024L * 1024 * 1024,
        2L * 1024 * 1024 * 1024,
        4L * 1024 * 1024 * 1024,
    )

    private val downloadQualities = listOf(PlayQuality.STANDARD, PlayQuality.HIGH, PlayQuality.LOSSLESS)

    init {
        userPreferencesRepository.userPreferences
            .onEach { prefs -> updateState { copy(preferences = prefs) } }
            .launchIn(viewModelScope)
        refreshCacheUsage()
    }

    override fun handleIntent(intent: PreferencesSettingsIntent) {
        when (intent) {
            PreferencesSettingsIntent.RefreshCacheUsage -> refreshCacheUsage()
            is PreferencesSettingsIntent.UpdateDownloadOnWifiOnly -> updateWifiOnly(intent.enabled)
            PreferencesSettingsIntent.ShowCacheLimitPicker -> showCacheLimitPicker()
            PreferencesSettingsIntent.ShowDownloadQualityPicker -> showDownloadQualityPicker()
            is PreferencesSettingsIntent.OnChoiceSelected -> onChoiceSelected(intent.index)
            PreferencesSettingsIntent.DismissChoiceDialog -> updateState { copy(choice = null) }
            PreferencesSettingsIntent.OnClearCacheClick -> updateState { copy(showClearCacheDialog = true) }
            PreferencesSettingsIntent.ConfirmClearCache -> clearCache()
            PreferencesSettingsIntent.DismissClearCacheDialog ->
                updateState { copy(showClearCacheDialog = false) }
        }
    }

    private fun refreshCacheUsage() {
        viewModelScope.launch {
            val used = playbackCacheRepository.getCacheUsedBytes()
            updateState { copy(cacheUsedBytes = used) }
        }
    }

    private fun updateWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateDownloadOnWifiOnly(enabled)
                .onError { _, msg -> sendEffect(PreferencesSettingsEffect.ShowMessage(msg ?: "保存失败")) }
        }
    }

    private fun showCacheLimitPicker() {
        val labels = cacheLimitOptions.map { formatBytes(it) }
        val currentIndex = cacheLimitOptions.indexOf(currentState.preferences.cacheMaxBytes).let {
            if (it >= 0) it else cacheLimitOptions.indexOf(2L * 1024 * 1024 * 1024)
        }
        updateState {
            copy(
                choice = PrefChoiceDialog(
                    kind = PrefChoiceKind.CACHE_LIMIT,
                    options = labels,
                    selectedIndex = currentIndex.coerceAtLeast(0),
                ),
            )
        }
    }

    private fun showDownloadQualityPicker() {
        updateState {
            copy(
                choice = PrefChoiceDialog(
                    kind = PrefChoiceKind.DOWNLOAD_QUALITY,
                    options = downloadQualities.map { downloadQualityLabel(it) },
                    selectedIndex = downloadQualities
                        .indexOf(currentState.preferences.downloadQuality)
                        .coerceAtLeast(0),
                ),
            )
        }
    }

    private fun onChoiceSelected(index: Int) {
        val dialog = currentState.choice ?: return
        viewModelScope.launch {
            when (dialog.kind) {
                PrefChoiceKind.CACHE_LIMIT -> {
                    val maxBytes = cacheLimitOptions.getOrElse(index) { cacheLimitOptions[3] }
                    playbackCacheRepository.updateCacheMaxBytes(maxBytes)
                        .onSuccess {
                            sendEffect(
                                PreferencesSettingsEffect.ShowMessage("缓存上限已更新，重启应用后完全生效"),
                            )
                        }
                        .onError { _, msg ->
                            sendEffect(PreferencesSettingsEffect.ShowMessage(msg ?: "保存失败"))
                        }
                }
                PrefChoiceKind.DOWNLOAD_QUALITY -> {
                    val quality = downloadQualities.getOrElse(index) { PlayQuality.LOSSLESS }
                    userPreferencesRepository.updateDownloadQuality(quality)
                        .onError { _, msg ->
                            sendEffect(PreferencesSettingsEffect.ShowMessage(msg ?: "保存失败"))
                        }
                }
            }
            updateState { copy(choice = null) }
        }
    }

    private fun clearCache() {
        viewModelScope.launch {
            updateState { copy(showClearCacheDialog = false, isClearingCache = true) }
            playbackCacheRepository.clearCache()
                .onSuccess {
                    val used = playbackCacheRepository.getCacheUsedBytes()
                    updateState { copy(cacheUsedBytes = used, isClearingCache = false) }
                    sendEffect(PreferencesSettingsEffect.ShowMessage("缓存已清理"))
                }
                .onError { _, msg ->
                    updateState { copy(isClearingCache = false) }
                    sendEffect(PreferencesSettingsEffect.ShowMessage(msg ?: "清理失败"))
                }
        }
    }

    fun cacheLimitLabel(maxBytes: Long): String = formatBytes(maxBytes)

    fun downloadQualityLabel(quality: PlayQuality): String = when (quality) {
        PlayQuality.STANDARD -> "标准"
        PlayQuality.HIGH -> "高品质"
        PlayQuality.LOSSLESS -> "无损"
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        if (bytes < 1024 * 1024 * 1024) return "${bytes / (1024 * 1024)} MB"
        return String.format("%.1f GB", bytes.toDouble() / (1024 * 1024 * 1024))
    }
}
