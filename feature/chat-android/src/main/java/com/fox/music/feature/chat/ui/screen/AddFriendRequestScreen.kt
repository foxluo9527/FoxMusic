package com.fox.music.feature.chat.ui.screen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.feature.chat.viewmodel.AddFriendRequestEffect
import com.fox.music.feature.chat.viewmodel.AddFriendRequestIntent
import com.fox.music.feature.chat.viewmodel.AddFriendRequestViewModel

/**
 * Android 侧「发送好友申请」入口：只做 ViewModel/Effect 接线，
 * 渲染交给 :feature:chat commonMain 的 [AddFriendRequestContent]。
 */
@Composable
fun AddFriendRequestScreen(
    modifier: Modifier = Modifier,
    viewModel: AddFriendRequestViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onRequestSent: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                AddFriendRequestEffect.NavigateBack -> onBack()
                AddFriendRequestEffect.RequestSent -> onRequestSent()
                is AddFriendRequestEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AddFriendRequestContent(
        state = state,
        onMessageChange = { viewModel.sendIntent(AddFriendRequestIntent.MessageChange(it)) },
        onMarkChange = { viewModel.sendIntent(AddFriendRequestIntent.MarkChange(it)) },
        onSubmit = { viewModel.sendIntent(AddFriendRequestIntent.Submit) },
        onBack = viewModel::onBackClick,
        modifier = modifier,
    )
}
