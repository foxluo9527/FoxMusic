package com.fox.music.feature.profile

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.GetProfileUseCase
import com.fox.music.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed interface ProfileIntent : UiIntent {
    data object Load : ProfileIntent
}

sealed interface ProfileEffect : UiEffect {
    data object NavigateToLogin : ProfileEffect
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getProfileUseCase: GetProfileUseCase
) : MviViewModel<ProfileState, ProfileIntent, ProfileEffect>(ProfileState()) {

    override fun handleIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Load -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            getProfileUseCase()
                .onSuccess { user -> updateState { copy(user = user, isLoading = false) } }
                .onError { _, msg -> updateState { copy(isLoading = false, error = msg ?: "Failed") } }
        }
    }
}
