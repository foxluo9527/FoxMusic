package com.fox.music.feature.profile.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.model.user.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DesktopLyricSettingsState(
    val preferences: UserPreferences = UserPreferences(),
    val showHelpDialog: Boolean = false,
    val showCarHelpDialog: Boolean = false,
) : UiState

sealed interface DesktopLyricSettingsIntent : UiIntent {
    data object Load : DesktopLyricSettingsIntent
    data class UpdateDesktopLyric(val enabled: Boolean) : DesktopLyricSettingsIntent
    data class UpdateLockScreenLyric(val enabled: Boolean) : DesktopLyricSettingsIntent
    data class UpdateCarBluetoothLyric(val enabled: Boolean) : DesktopLyricSettingsIntent
    data object ShowHelp : DesktopLyricSettingsIntent
    data object DismissHelp : DesktopLyricSettingsIntent
    data object ShowCarHelp : DesktopLyricSettingsIntent
    data object DismissCarHelp : DesktopLyricSettingsIntent
    data object ConfirmDesktopLyricEnabled : DesktopLyricSettingsIntent
}

sealed interface DesktopLyricSettingsEffect : UiEffect {
    data object RequestOverlayPermission : DesktopLyricSettingsEffect
}

@HiltViewModel
class DesktopLyricSettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : MviViewModel<DesktopLyricSettingsState, DesktopLyricSettingsIntent, DesktopLyricSettingsEffect>(
    DesktopLyricSettingsState(),
) {

    init {
        userPreferencesRepository.userPreferences
            .onEach { prefs -> updateState { copy(preferences = prefs) } }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: DesktopLyricSettingsIntent) {
        when (intent) {
            DesktopLyricSettingsIntent.Load -> Unit
            is DesktopLyricSettingsIntent.UpdateDesktopLyric -> {
                if (intent.enabled) {
                    sendEffect(DesktopLyricSettingsEffect.RequestOverlayPermission)
                } else {
                    updatePreference(userPreferencesRepository::updateDesktopLyricEnabled)(false)
                }
            }
            is DesktopLyricSettingsIntent.UpdateLockScreenLyric ->
                updatePreference(userPreferencesRepository::updateLockScreenLyricEnabled)(intent.enabled)
            is DesktopLyricSettingsIntent.UpdateCarBluetoothLyric ->
                updatePreference(userPreferencesRepository::updateCarBluetoothLyricEnabled)(intent.enabled)
            DesktopLyricSettingsIntent.ShowHelp -> updateState { copy(showHelpDialog = true) }
            DesktopLyricSettingsIntent.DismissHelp -> updateState { copy(showHelpDialog = false) }
            DesktopLyricSettingsIntent.ShowCarHelp -> updateState { copy(showCarHelpDialog = true) }
            DesktopLyricSettingsIntent.DismissCarHelp -> updateState { copy(showCarHelpDialog = false) }
            DesktopLyricSettingsIntent.ConfirmDesktopLyricEnabled ->
                updatePreference(userPreferencesRepository::updateDesktopLyricEnabled)(true)
        }
    }

    private fun updatePreference(
        block: suspend (Boolean) -> com.fox.music.core.common.result.Result<Unit>,
    ): (Boolean) -> Unit = { enabled ->
        viewModelScope.launch { block(enabled) }
    }
}
