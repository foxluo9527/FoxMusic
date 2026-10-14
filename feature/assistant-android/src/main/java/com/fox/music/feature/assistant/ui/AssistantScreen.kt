package com.fox.music.feature.assistant.ui.screen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.feature.assistant.AssistantEffect
import com.fox.music.feature.assistant.AssistantIntent
import com.fox.music.feature.assistant.ui.AssistantContent
import com.fox.music.feature.assistant.viewmodel.AssistantViewModel

/**
 * AI 助手对话面板（Android 薄壳）。
 *
 * UI 本体在与 :feature:assistant commonMain 的 [AssistantContent]（iOS 复用）；
 * 这里只留 Android 专属三件事：hiltViewModel 注入、effect → Toast。
 */
@Composable
fun AssistantScreen(
    modifier: Modifier = Modifier,
    viewModel: AssistantViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AssistantEffect.ShowMessage ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    AssistantContent(
        state = state,
        onInputChange = { viewModel.sendIntent(AssistantIntent.InputChanged(it)) },
        onSend = { viewModel.sendIntent(AssistantIntent.Send) },
        onRetry = { viewModel.sendIntent(AssistantIntent.Retry(it)) },
        onRunAction = { viewModel.sendIntent(AssistantIntent.RunAction(it)) },
        onConfirmAction = { viewModel.sendIntent(AssistantIntent.ConfirmAction(it)) },
        onDismissAction = { viewModel.sendIntent(AssistantIntent.DismissAction) },
        onNewConversation = { viewModel.sendIntent(AssistantIntent.NewConversation) },
        modifier = modifier,
        onBack = onBack,
    )
}
