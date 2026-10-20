package com.fox.music.feature.chat.ui.screen

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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fox.music.feature.chat.viewmodel.AddFriendRequestState
import com.fox.music.feature.chat.viewmodel.MAX_MARK_LENGTH

/**
 * 「发送好友申请」页 —— 跨端无状态实现。
 * 备注上限走 [MAX_MARK_LENGTH]，两端计数文案一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendRequestContent(
    state: AddFriendRequestState,
    onMessageChange: (String) -> Unit,
    onMarkChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
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
                onValueChange = onMessageChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("我是...") },
                minLines = 3,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "好友备注（选填，最多 $MAX_MARK_LENGTH 字）",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.mark,
                onValueChange = onMarkChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("给对方的备注") },
                singleLine = true,
                supportingText = {
                    Text("${state.mark.length}/$MAX_MARK_LENGTH")
                },
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onSubmit,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSubmitting) "发送中..." else "发送申请")
            }
        }
    }
}
