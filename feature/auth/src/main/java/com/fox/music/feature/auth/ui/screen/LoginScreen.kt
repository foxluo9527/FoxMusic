package com.fox.music.feature.auth.ui.screen

import androidx.compose.foundation.layout.Column
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
            state.password.length in 1 until  6
        }
    }

    val emailHasErrors by remember {
        derivedStateOf {
            if (state.email.isNotEmpty()) {
                // Email is considered erroneous until it completely matches EMAIL_ADDRESS.
                ! android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()
            } else {
                false
            }
        }
    }

    val usernameError by remember {
        derivedStateOf {
            state.username.isEmpty()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { when (it) {
            is AuthEffect.NavigateToHome -> onNavigateToHome()
        } }
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
        if (!state.isLoginMode) {
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
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { viewModel.sendIntent(AuthIntent.Submit) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading && ((!emailHasErrors && state.email.isNotEmpty())  || state.isLoginMode) && !passwordError && state.password.isNotEmpty() && !usernameError
        ) {
            if (state.isLoading) CircularProgressIndicator(Modifier.height(24.dp))
            else Text(if (state.isLoginMode) "登录" else "注册")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.sendIntent(AuthIntent.ToggleToRegisterMode) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isLoginMode) "去注册" else "去登录")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.sendIntent(AuthIntent.ToggleToRegisterMode) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("忘记密码")
        }
    }
}
