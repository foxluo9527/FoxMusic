package com.fox.music.feature.auth.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.domain.usecase.ForgotPasswordUseCase
import com.fox.music.core.domain.usecase.LoginUseCase
import com.fox.music.core.domain.usecase.RegisterUseCase
import com.fox.music.core.domain.usecase.ResetPasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// 状态机与异步逻辑留在 Android 侧：它依赖 Hilt 注入的 UseCase 与
// androidx.lifecycle 的 viewModelScope。MVI 契约本身在 AuthContract.kt（跨端）。
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
            is AuthIntent.VerifyChange -> updateState {copy(verifyCode = intent.value)}
            AuthIntent.Submit -> submit()
            AuthIntent.ToggleToRegisterMode -> updateState {
                copy(
                    isLoginMode = !isLoginMode,
                    isResetMode = false,
                    isLoading = false,
                    error = null
                )
            }

            AuthIntent.ToggleToResetMode -> updateState {
                copy(
                    isResetMode = ! isResetMode,
                    isLoading = false,
                    error = null
                )
            }
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
                            error = msg ?: "Send failed",
                            waitingSecond = 60
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
