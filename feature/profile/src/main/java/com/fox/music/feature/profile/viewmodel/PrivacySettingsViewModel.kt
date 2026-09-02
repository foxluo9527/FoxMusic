package com.fox.music.feature.profile.viewmodel

import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.usecase.GetBlacklistUseCase
import com.fox.music.core.domain.usecase.GetPrivacySettingsUseCase
import com.fox.music.core.domain.usecase.UnblockUserUseCase
import com.fox.music.core.domain.usecase.UpdatePrivacySettingsUseCase
import com.fox.music.core.model.user.BlacklistUser
import com.fox.music.core.model.user.PrivacySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivacySettingsState(
    val settings: PrivacySettings? = null,
    val blacklist: List<BlacklistUser> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val unblockTarget: BlacklistUser? = null,
    val isUnblocking: Boolean = false,
) : UiState

sealed interface PrivacySettingsIntent : UiIntent {
    data object Load : PrivacySettingsIntent
    data class SetPrivateAccount(val enabled: Boolean) : PrivacySettingsIntent
    data class SetSearchable(val enabled: Boolean) : PrivacySettingsIntent
    data class ShowUnblockDialog(val user: BlacklistUser) : PrivacySettingsIntent
    data object DismissUnblockDialog : PrivacySettingsIntent
    data object ConfirmUnblock : PrivacySettingsIntent
}

sealed interface PrivacySettingsEffect : UiEffect {
    data object NavigateBack : PrivacySettingsEffect
    data class ShowMessage(val message: String) : PrivacySettingsEffect
}

@HiltViewModel
class PrivacySettingsViewModel @Inject constructor(
    private val getPrivacySettingsUseCase: GetPrivacySettingsUseCase,
    private val updatePrivacySettingsUseCase: UpdatePrivacySettingsUseCase,
    private val getBlacklistUseCase: GetBlacklistUseCase,
    private val unblockUserUseCase: UnblockUserUseCase,
) : MviViewModel<PrivacySettingsState, PrivacySettingsIntent, PrivacySettingsEffect>(
    PrivacySettingsState(),
) {

    override fun handleIntent(intent: PrivacySettingsIntent) {
        when (intent) {
            PrivacySettingsIntent.Load -> load()
            is PrivacySettingsIntent.SetPrivateAccount -> updatePrivate(intent.enabled)
            is PrivacySettingsIntent.SetSearchable -> updateSearchable(intent.enabled)
            is PrivacySettingsIntent.ShowUnblockDialog -> updateState { copy(unblockTarget = intent.user) }
            PrivacySettingsIntent.DismissUnblockDialog -> updateState { copy(unblockTarget = null) }
            PrivacySettingsIntent.ConfirmUnblock -> unblock()
        }
    }

    fun onBackClick() = sendEffect(PrivacySettingsEffect.NavigateBack)

    private fun load() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            val settingsResult = getPrivacySettingsUseCase()
            val blacklistResult = getBlacklistUseCase()
            when {
                settingsResult is Result.Success && blacklistResult is Result.Success -> {
                    updateState {
                        copy(
                            settings = settingsResult.data,
                            blacklist = blacklistResult.data.list,
                            isLoading = false,
                        )
                    }
                }
                settingsResult is Result.Error -> {
                    updateState {
                        copy(isLoading = false, error = settingsResult.message ?: "加载失败")
                    }
                }
                blacklistResult is Result.Error -> {
                    updateState {
                        copy(isLoading = false, error = blacklistResult.message ?: "加载失败")
                    }
                }
                else -> updateState { copy(isLoading = false) }
            }
        }
    }

    private fun updatePrivate(enabled: Boolean) {
        viewModelScope.launch {
            updateState { copy(isSaving = true) }
            when (val result = updatePrivacySettingsUseCase(isPrivate = enabled)) {
                is Result.Success -> updateState { copy(settings = result.data, isSaving = false) }
                is Result.Error -> {
                    updateState { copy(isSaving = false) }
                    sendEffect(PrivacySettingsEffect.ShowMessage(result.message ?: "保存失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun updateSearchable(enabled: Boolean) {
        viewModelScope.launch {
            updateState { copy(isSaving = true) }
            when (val result = updatePrivacySettingsUseCase(isSearchable = enabled)) {
                is Result.Success -> updateState { copy(settings = result.data, isSaving = false) }
                is Result.Error -> {
                    updateState { copy(isSaving = false) }
                    sendEffect(PrivacySettingsEffect.ShowMessage(result.message ?: "保存失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun unblock() {
        val target = currentState.unblockTarget ?: return
        viewModelScope.launch {
            updateState { copy(isUnblocking = true) }
            when (val result = unblockUserUseCase(target.id)) {
                is Result.Success -> {
                    updateState {
                        copy(
                            isUnblocking = false,
                            unblockTarget = null,
                            blacklist = blacklist.filterNot { it.id == target.id },
                        )
                    }
                    sendEffect(PrivacySettingsEffect.ShowMessage("已解除黑名单"))
                }
                is Result.Error -> {
                    updateState { copy(isUnblocking = false) }
                    sendEffect(PrivacySettingsEffect.ShowMessage(result.message ?: "操作失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }
}
