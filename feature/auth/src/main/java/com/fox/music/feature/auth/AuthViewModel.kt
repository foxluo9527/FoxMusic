package com.fox.music.feature.auth

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.LoginUseCase
import com.fox.music.core.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val username: String = "",
    val password: String = "",
    val email: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoginMode: Boolean = true
) : UiState

sealed interface AuthIntent : UiIntent {
    data class UsernameChange(val value: String) : AuthIntent
    data class PasswordChange(val value: String) : AuthIntent
    data class EmailChange(val value: String) : AuthIntent
    data object Submit : AuthIntent
    data object ToggleMode : AuthIntent
}

sealed interface AuthEffect : UiEffect {
    data object NavigateToHome : AuthEffect
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase
) : MviViewModel<AuthState, AuthIntent, AuthEffect>(AuthState()) {

    override fun handleIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.UsernameChange -> updateState { copy(username = intent.value) }
            is AuthIntent.PasswordChange -> updateState { copy(password = intent.value) }
            is AuthIntent.EmailChange -> updateState { copy(email = intent.value) }
            AuthIntent.Submit -> submit()
            AuthIntent.ToggleMode -> updateState { copy(isLoginMode = !isLoginMode) }
        }
    }

    private fun submit() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            if (currentState.isLoginMode) {
                loginUseCase(currentState.username, currentState.password)
                    .onSuccess { updateState { copy(isLoading = false) }; sendEffect(AuthEffect.NavigateToHome) }
                    .onError { _, msg -> updateState { copy(isLoading = false, error = msg ?: "Login failed") } }
            } else {
                registerUseCase(currentState.username, currentState.password, currentState.email)
                    .onSuccess { updateState { copy(isLoading = false) }; sendEffect(AuthEffect.NavigateToHome) }
                    .onError { _, msg -> updateState { copy(isLoading = false, error = msg ?: "Register failed") } }
            }
        }
    }
}
