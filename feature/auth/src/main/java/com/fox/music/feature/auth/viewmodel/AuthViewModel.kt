package com.fox.music.feature.auth.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.ForgotPasswordUseCase
import com.fox.music.core.domain.usecase.LoginUseCase
import com.fox.music.core.domain.usecase.RegisterUseCase
import com.fox.music.core.domain.usecase.ResetPasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val username: String = "",
    val password: String = "",
    val email: String = "",
    val verifyCode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoginMode: Boolean = true,
    val isResetMode: Boolean = false,
    val waitingSecond: Int = 0
) : UiState

sealed interface AuthIntent : UiIntent {
    data class UsernameChange(val value: String) : AuthIntent
    data class PasswordChange(val value: String) : AuthIntent
    data class EmailChange(val value: String) : AuthIntent
    data class VerifyChange(val value: String) : AuthIntent
    data class UpdateWaiting(val value: Int) : AuthIntent
    data object Submit : AuthIntent
    data object SendVerify : AuthIntent
    data object ToggleToRegisterMode : AuthIntent
    data object ToggleToResetMode : AuthIntent
}

sealed interface AuthEffect : UiEffect {
    data object NavigateToHome : AuthEffect
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val forgotPasswordUseCase: ForgotPasswordUseCase,
    private val resetPasswordUseCase: ResetPasswordUseCase
) : MviViewModel<AuthState, AuthIntent, AuthEffect>(AuthState()) {

    override fun handleIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.UsernameChange -> updateState { copy(username = intent.value) }
            is AuthIntent.PasswordChange -> updateState { copy(password = intent.value) }
            is AuthIntent.EmailChange -> updateState { copy(email = intent.value) }
            is AuthIntent.UpdateWaiting -> updateState { copy(waitingSecond = intent.value) }
            AuthIntent.Submit -> submit()
            AuthIntent.ToggleToRegisterMode -> updateState {
                copy(
                    isLoginMode = !isLoginMode,
                    isResetMode = false
                )
            }

            AuthIntent.ToggleToResetMode -> updateState { copy(isResetMode = !isResetMode) }
            is AuthIntent.VerifyChange -> updateState { copy(verifyCode = intent.value) }
            AuthIntent.SendVerify -> sendCode()
        }
    }

    private fun sendCode() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            forgotPasswordUseCase(currentState.email)
                .onSuccess {
                    updateState { copy(isLoading = false, waitingSecond = 60) }
                }
                .onError { _, msg ->
                    updateState {
                        copy(
                            isLoading = false,
                            error = msg ?: "Send failed"
                        )
                    }
                }
        }
    }

    private fun submit() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            if (currentState.isResetMode) {
                resetPasswordUseCase(
                    currentState.verifyCode,
                    currentState.email,
                    currentState.password
                )
                    .onSuccess {
                        updateState { copy(isLoading = false, isResetMode = false) }
                    }
                    .onError { _, msg ->
                        updateState {
                            copy(
                                isLoading = false,
                                error = msg ?: "Reset failed"
                            )
                        }
                    }
            } else if (currentState.isLoginMode) {
                loginUseCase(currentState.username, currentState.password)
                    .onSuccess { updateState { copy(isLoading = false) }; sendEffect(AuthEffect.NavigateToHome) }
                    .onError { _, msg ->
                        updateState {
                            copy(
                                isLoading = false,
                                error = msg ?: "Login failed"
                            )
                        }
                    }
            } else {
                registerUseCase(currentState.username, currentState.password, currentState.email)
                    .onSuccess { updateState { copy(isLoading = false) }; sendEffect(AuthEffect.NavigateToHome) }
                    .onError { _, msg ->
                        updateState {
                            copy(
                                isLoading = false,
                                error = msg ?: "Register failed"
                            )
                        }
                    }
            }
        }
    }
}
