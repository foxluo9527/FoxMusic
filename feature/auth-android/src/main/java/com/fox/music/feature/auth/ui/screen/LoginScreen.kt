package com.fox.music.feature.auth.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.feature.auth.viewmodel.AuthEffect
import com.fox.music.feature.auth.viewmodel.AuthViewModel

/**
 * Android 侧外壳：负责 Hilt 取 ViewModel、收集 uiState / effect，
 * 真正的界面在跨端的 [LoginContent] 里。
 *
 * 这是 feature 层迁移的标准模式 ——
 * 「UI 与契约进 commonMain，依赖注入与生命周期留在平台侧」。
 */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
) {
    BackHandler(onBack = onNavigateBack)

    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collect {
            when (it) {
                is AuthEffect.NavigateToHome -> onNavigateToHome()
            }
        }
    }

    LoginContent(
        state = state,
        onIntent = viewModel::sendIntent,
        modifier = modifier,
        onNavigateBack = onNavigateBack,
        onNavigateToHome = onNavigateToHome,
    )
}
