package com.fox.music.feature.assistant.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.ui.component.MiniPlayer
import com.fox.music.feature.assistant.AssistantEffect
import com.fox.music.feature.assistant.AssistantIntent
import com.fox.music.feature.assistant.ui.AssistantContent
import com.fox.music.feature.assistant.viewmodel.AssistantViewModel

/**
 * AI 助手对话面板（Android 薄壳）。
 *
 * UI 本体在与 :feature:assistant commonMain 的 [AssistantContent]（iOS 复用）；
 * 这里只留 Android 专属四件事：hiltViewModel 注入、effect → Toast、
 * MiniPlayer 装配（依赖 sharedTransition）、播放控制回调转发。
 *
 * 为什么要有 MiniPlayer：助手的核心动作就是「替你放歌」，没有播放条用户看不到
 * 当前在播什么、也点不到暂停/下一首，反馈闭环是断的。
 */
@Composable
fun AssistantScreen(
    modifier: Modifier = Modifier,
    viewModel: AssistantViewModel = hiltViewModel(),
    playerState: PlayerState = PlayerState(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    onBack: () -> Unit = {},
    onOpenScreen: (String, Long?) -> Unit = { _, _ -> },
    onSearchInApp: (String) -> Unit = { _ -> },
    onNavigateToPlayer: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onNextTrack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AssistantEffect.ShowMessage ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is AssistantEffect.OpenScreen -> onOpenScreen(effect.screen, effect.targetId)
                is AssistantEffect.SearchInApp -> onSearchInApp(effect.query)
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
        onLoadConversations = { viewModel.sendIntent(AssistantIntent.LoadConversations) },
        onOpenConversation = { viewModel.sendIntent(AssistantIntent.OpenConversation(it)) },
        onDeleteConversation = { viewModel.sendIntent(AssistantIntent.DeleteConversation(it)) },
        onToggleHistory = { viewModel.sendIntent(AssistantIntent.ToggleHistory) },
        modifier = modifier,
        onBack = onBack,
        miniPlayer = if (playerState.currentMusic != null &&
            sharedTransitionScope != null &&
            animatedContentScope != null
        ) {
            {
                with(sharedTransitionScope) {
                    MiniPlayer(
                        playerState = playerState,
                        sharedTransitionScope = this@with,
                        animatedContentScope = animatedContentScope,
                        onPlayPauseClick = onTogglePlay,
                        onNextClick = onNextTrack,
                        onClick = onNavigateToPlayer,
                        modifier = Modifier,
                    )
                }
            }
        } else {
            null
        },
    )
}
