package com.fox.music.feature.chat.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.feature.chat.viewmodel.AddFriendRequestEffect
import com.fox.music.feature.chat.viewmodel.AddFriendRequestIntent
import com.fox.music.feature.chat.viewmodel.AddFriendRequestViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    val title = if (state.nickname.isNotBlank()) {
        "添加 ${state.nickname} 为好友"
    } else {
        "添加好友"
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = viewModel::onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text(
                text = "验证消息",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.message,
                onValueChange = { viewModel.sendIntent(AddFriendRequestIntent.MessageChange(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("我是...") },
                minLines = 3,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "好友备注（选填，最多20字）",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.mark,
                onValueChange = { viewModel.sendIntent(AddFriendRequestIntent.MarkChange(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("给对方的备注") },
                singleLine = true,
                supportingText = {
                    Text("${state.mark.length}/20")
                },
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.sendIntent(AddFriendRequestIntent.Submit) },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSubmitting) "发送中..." else "发送申请")
            }
        }
    }
}
