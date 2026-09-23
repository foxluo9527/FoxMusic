package com.fox.music.iosshared.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.fox.music.feature.auth.ui.screen.LoginContent
import com.fox.music.feature.auth.viewmodel.AuthIntent
import com.fox.music.feature.auth.viewmodel.AuthState
import com.fox.music.iosshared.AuthUser
import com.fox.music.iosshared.FoxAuthStore
import com.fox.music.iosshared.FoxMusicApi
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS 登录页：复用 `feature:auth` commonMain 的 [LoginContent]（同一份 UI），
 * 状态机在这里用 `AuthState` + [FoxMusicApi] 复刻 Android `AuthViewModel` 的语义。
 *
 * Android 的状态机依赖 Hilt 注入的 UseCase + `viewModelScope`，两端不共享；
 * 但 MVI 契约（[AuthState]/[AuthIntent]）是跨端的，所以行为可以逐条对齐。
 */
object FoxLoginScreen {

    fun makeLoginViewController(
        onLoginSuccess: (AuthUser) -> Unit,
        onBack: () -> Unit,
    ): UIViewController = ComposeUIViewController {
        val holder = remember { mutableStateOf(AuthState()) }
        val api = remember { FoxMusicApi() }
        val scope = rememberCoroutineScope()

        var state by holder

        /** 登录 / 注册 / 重置 的统一提交入口，语义对齐 Android AuthViewModel.submit()。 */
        fun submit() {
            val current = holder.value
            holder.value = current.copy(isLoading = true, error = null)
            scope.launch {
                when {
                    current.isResetMode -> {
                        val result = api.resetPassword(
                            email = current.email,
                            code = current.verifyCode,
                            newPassword = current.password,
                        )
                        if (result.success) {
                            // 重置成功后回到登录态（对齐 Android：isResetMode = false）
                            holder.value = holder.value.copy(isLoading = false, isResetMode = false)
                        } else {
                            holder.value = holder.value.copy(
                                isLoading = false,
                                error = result.message ?: "密码重置失败",
                            )
                        }
                    }

                    current.isLoginMode -> {
                        val result = api.login(current.username, current.password)
                        val user = result.user
                        val token = result.token
                        if (result.success && user != null && !token.isNullOrBlank()) {
                            // 先写内存态（Swift 在 onLoginSuccess 里读 token 落 Keychain）
                            FoxAuthStore.update(
                                token = token,
                                refreshToken = result.refreshToken,
                                user = user,
                            )
                            holder.value = holder.value.copy(isLoading = false)
                            onLoginSuccess(user)
                        } else {
                            holder.value = holder.value.copy(
                                isLoading = false,
                                error = result.message ?: "登录失败",
                            )
                        }
                    }

                    else -> {
                        val result = api.register(
                            username = current.username,
                            password = current.password,
                            email = current.email,
                        )
                        val user = result.user
                        val token = result.token
                        if (result.success && user != null && !token.isNullOrBlank()) {
                            FoxAuthStore.update(
                                token = token,
                                refreshToken = result.refreshToken,
                                user = user,
                            )
                            holder.value = holder.value.copy(isLoading = false)
                            onLoginSuccess(user)
                        } else {
                            holder.value = holder.value.copy(
                                isLoading = false,
                                error = result.message ?: "注册失败",
                            )
                        }
                    }
                }
            }
        }

        fun sendVerify() {
            val email = holder.value.email
            holder.value = holder.value.copy(isLoading = true, error = null)
            scope.launch {
                val result = api.forgetPassword(email)
                holder.value = holder.value.copy(
                    isLoading = false,
                    waitingSecond = 60,
                    error = if (result.success) null else (result.message ?: "验证码发送失败"),
                )
            }
        }

        LoginContent(
            state = state,
            onIntent = { intent ->
                when (intent) {
                    is AuthIntent.UsernameChange ->
                        holder.value = holder.value.copy(username = intent.value)

                    is AuthIntent.PasswordChange ->
                        holder.value = holder.value.copy(password = intent.value)

                    is AuthIntent.EmailChange ->
                        holder.value = holder.value.copy(email = intent.value)

                    is AuthIntent.VerifyChange ->
                        holder.value = holder.value.copy(verifyCode = intent.value)

                    is AuthIntent.UpdateWaiting ->
                        holder.value = holder.value.copy(waitingSecond = intent.value)

                    AuthIntent.Submit -> submit()

                    AuthIntent.SendVerify -> sendVerify()

                    AuthIntent.ToggleToRegisterMode -> holder.value = holder.value.copy(
                        isLoginMode = !holder.value.isLoginMode,
                        isResetMode = false,
                        isLoading = false,
                        error = null,
                    )

                    AuthIntent.ToggleToResetMode -> holder.value = holder.value.copy(
                        isResetMode = !holder.value.isResetMode,
                        isLoading = false,
                        error = null,
                    )
                }
            },
            onNavigateBack = onBack,
            onNavigateToHome = {},
        )
    }
}
