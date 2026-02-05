package com.fox.music.feature.auth.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.feature.auth.viewmodel.AuthEffect
import com.fox.music.feature.auth.viewmodel.AuthIntent
import com.fox.music.feature.auth.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

const val LOGIN_ROUTE = "login"

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val passwordError by remember {
        derivedStateOf {
            state.password.length in 1 until 6
        }
    }
    LaunchedEffect(state.waitingSecond) {
        if (state.waitingSecond == 60) {
            while (state.waitingSecond > 0 && isActive) {
                viewModel.sendIntent(AuthIntent.UpdateWaiting(state.waitingSecond - 1))
                delay(1000)
            }
        }
    }

    val emailHasErrors by remember {
        derivedStateOf {
            if (state.email.isNotEmpty()) {
                // Email is considered erroneous until it completely matches EMAIL_ADDRESS.
                !android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()
            } else {
                false
            }
        }
    }

    val verifyError by remember {
        derivedStateOf {
            state.verifyCode.length != 6
        }
    }

    val usernameError by remember {
        derivedStateOf {
            state.username.isEmpty()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect {
            when (it) {
                is AuthEffect.NavigateToHome -> onNavigateToHome()
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (state.isLoginMode) "Login" else "Register",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 48.dp, bottom = 24.dp)
        )
        if (!state.isLoginMode || state.isResetMode) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.email,
                onValueChange = { viewModel.sendIntent(AuthIntent.EmailChange(it)) },
                label = { Text("邮箱") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    if (emailHasErrors) {
                        Text("邮箱格式错误")
                    }
                },
                isError = emailHasErrors
            )
        }
        if (state.isResetMode) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.verifyCode,
                    onValueChange = { viewModel.sendIntent(AuthIntent.VerifyChange(it)) },
                    label = { Text("邮箱验证码") },
                    modifier = Modifier.weight(1f),
                    supportingText = {
                        if (verifyError) {
                            Text("请输入6位数验证码")
                        }
                    },
                    isError = verifyError
                )

                TextButton(onClick = {
                    viewModel.sendIntent(AuthIntent.SendVerify)
                }) {
                    Text("发送")
                }
            }

        }
        if (!state.isResetMode) {
            OutlinedTextField(
                value = state.username,
                onValueChange = { viewModel.sendIntent(AuthIntent.UsernameChange(it)) },
                label = { Text(if (state.isLoginMode) "用户名或密码" else "用户名") },
                modifier = Modifier.fillMaxWidth(),
                isError = usernameError,
                supportingText = {
                    if (usernameError) {
                        Text("用户名不能为空")
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = { viewModel.sendIntent(AuthIntent.PasswordChange(it)) },
            label = { Text("密码") },
            modifier = Modifier.fillMaxWidth(),
            isError = passwordError,
            supportingText = {
                if (passwordError) {
                    Text("密码至少6位数")
                }
            }
        )
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { viewModel.sendIntent(AuthIntent.Submit) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading && ((!emailHasErrors && state.email.isNotEmpty()) || state.isLoginMode) && !passwordError && state.password.isNotEmpty() && !usernameError
        ) {
            Text(if (state.isLoginMode) "登录" else "注册")
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (!state.isResetMode) {
            Button(
                onClick = { viewModel.sendIntent(AuthIntent.ToggleToRegisterMode) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isLoginMode) "去注册" else "返回登录")
            }
        }
        if (state.isLoginMode || state.isResetMode) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.sendIntent(AuthIntent.ToggleToResetMode) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isResetMode) "返回登录" else "忘记密码")
            }
        }
        if (state.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(Modifier.height(24.dp))
        }
    }
}
