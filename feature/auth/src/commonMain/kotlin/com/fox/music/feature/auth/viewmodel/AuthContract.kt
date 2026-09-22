package com.fox.music.feature.auth.viewmodel

import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState

// 登录/注册/重置的 MVI 契约。
//
// 跨平台共享：这里是纯数据（sealed interface / data class），不含任何平台 API，
// 所以 UI（LoginContent）与状态机可以分别在各端实现，契约保持一致。

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
