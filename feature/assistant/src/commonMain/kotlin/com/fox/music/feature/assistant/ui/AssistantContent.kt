package com.fox.music.feature.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fox.music.core.assistant.AssistantAction
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.feature.assistant.AssistantChatMessage
import com.fox.music.feature.assistant.AssistantState

/**
 * AI 助手对话面板（跨端共用，stateless）。
 *
 * 只吃 [AssistantState] + 回调；Android 薄壳（AssistantScreen）负责
 * hiltViewModel 与 effect → Toast，iOS 壳（Increment 3）复用同一份。
 *
 * 动作分发策略（与服务端约定）：
 *  - 非 requiresConfirm 动作（如 play_music）由 ViewModel 收到即自动执行；
 *  - requiresConfirm 动作（send_message / add_friend）进 [AssistantState.pendingActions]
 *    队列，本面板逐条弹 [AlertDialog] 确认后再执行。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantContent(
    state: AssistantState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: (Long) -> Unit,
    onRunAction: (AssistantAction) -> Unit,
    onConfirmAction: (AssistantAction) -> Unit,
    onDismissAction: () -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val pending = state.pendingActions.firstOrNull()

    // 新消息（含流式上屏的乐观消息）自动滚到底
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("AI 音乐助手") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onNewConversation) {
                        Icon(Icons.Default.Add, contentDescription = "新会话")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = FoxSpacing.cardPadding,
                    vertical = FoxSpacing.itemSpacing,
                ),
                verticalArrangement = Arrangement.spacedBy(FoxSpacing.itemSpacing),
            ) {
                if (state.isEmpty) {
                    item(key = "welcome") { AssistantWelcome() }
                }
                items(state.messages, key = { it.id }) { message ->
                    AssistantMessageItem(
                        message = message,
                        onRetry = { onRetry(message.id) },
                        onActionClick = onRunAction,
                    )
                }
            }

            AssistantInputBar(
                text = state.inputText,
                canSend = state.canSend,
                onInputChange = onInputChange,
                onSend = onSend,
            )
        }
    }

    if (pending != null) {
        AlertDialog(
            onDismissRequest = onDismissAction,
            title = { Text("确认操作") },
            text = {
                Text(pending.label.ifBlank { "确认执行「${pending.type}」？" })
            },
            confirmButton = {
                TextButton(onClick = { onConfirmAction(pending) }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = onDismissAction) { Text("取消") }
            },
        )
    }
}

/** 单条消息气泡：用户右侧（primaryContainer），助手左侧（surfaceVariant）+ 动作芯片。 */
@Composable
private fun AssistantMessageItem(
    message: AssistantChatMessage,
    onRetry: () -> Unit,
    onActionClick: (AssistantAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
        ) {
            Surface(
                color = if (message.isUser) scheme.primaryContainer else scheme.surfaceVariant,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (message.isUser) 18.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 18.dp,
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (message.isUser) {
                                scheme.onPrimaryContainer
                            } else {
                                scheme.onSurfaceVariant
                            },
                        )
                    }
                    if (message.isSending) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "发送中",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (message.failed) {
                        TextButton(onClick = onRetry) { Text("发送失败，点击重试") }
                    }
                }
            }

            if (message.actions.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    message.actions.forEach { action ->
                        AssistChip(
                            onClick = { onActionClick(action) },
                            label = {
                                Text(
                                    text = action.label.ifBlank { action.type },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 空态引导：品牌 + 三个示例问法。 */
@Composable
private fun AssistantWelcome() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 72.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.size(FoxSpacing.itemSpacing))
        Text(
            text = "我是 FoxMusic AI 助手",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onBackground,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "试试这样问我：",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(8.dp))
        listOf(
            "播放周杰伦的《晴天》",
            "推荐适合学习的音乐",
            "搜索包含「海」的歌单",
        ).forEach { hint ->
            Surface(
                color = scheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}

/** 底部输入栏：多行输入 + 发送按钮（占位文案给出能力示例）。 */
@Composable
private fun AssistantInputBar(
    text: String,
    canSend: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 只补键盘 inset：底部导航栏 inset 由 Scaffold 的 contentWindowInsets
                // 统一处理，再叠 navigationBarsPadding 会双重留白。
                .imePadding()
                .padding(horizontal = FoxSpacing.cardPadding, vertical = FoxSpacing.itemSpacing),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "问点什么…比如「播放轻松的音乐」",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}
