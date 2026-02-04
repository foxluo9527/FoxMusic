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
import androidx.compose.runtime.getValue
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
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = { viewModel.sendIntent(AuthIntent.PasswordChange(it)) },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth()
        )
        if (!state.isLoginMode) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.email,
                onValueChange = { viewModel.sendIntent(AuthIntent.EmailChange(it)) },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { viewModel.sendIntent(AuthIntent.Submit) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading
        ) {
            if (state.isLoading) CircularProgressIndicator(Modifier.height(24.dp))
            else Text(if (state.isLoginMode) "Login" else "Register")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.sendIntent(AuthIntent.ToggleMode) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isLoginMode) "Switch to Register" else "Switch to Login")
        }
    }
}
