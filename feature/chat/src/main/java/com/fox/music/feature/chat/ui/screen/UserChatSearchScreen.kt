package com.fox.music.feature.chat.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.chat.Message
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.feature.chat.viewmodel.UserChatSearchEffect
import com.fox.music.feature.chat.viewmodel.UserChatSearchIntent
import com.fox.music.feature.chat.viewmodel.UserChatSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserChatSearchScreen(
    modifier: Modifier = Modifier,
    viewModel: UserChatSearchViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToChatDetail: (Long, Long?) -> Unit = { _, _ -> },
    onNavigateToUserProfile: (Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (state.query.isBlank()) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is UserChatSearchEffect.NavigateToChatDetail -> {
                    onNavigateToChatDetail(effect.userId, effect.messageId)
                }
                is UserChatSearchEffect.NavigateToUserProfile -> {
                    onNavigateToUserProfile(effect.userId)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.userNickname,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        TextField(
                            value = state.query,
                            onValueChange = { viewModel.sendIntent(UserChatSearchIntent.UpdateQuery(it)) },
                            placeholder = { Text("搜索") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.sendIntent(UserChatSearchIntent.UpdateQuery("")) }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                UserProfileHeader(
                    nickname = state.userNickname,
                    avatar = state.userAvatar,
                    onClick = { viewModel.onUserProfileClick() },
                )
            }

            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (state.matchCount > 0) {
                item {
                    Text(
                        text = "共${state.matchCount}条相关的聊天记录",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }

                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                items(state.messages, key = { it.id }) { message ->
                    SearchMessageItem(
                        message = message,
                        query = state.query,
                        currentUserId = state.currentUserId,
                        peerNickname = state.userNickname,
                        peerAvatar = state.userAvatar,
                        onClick = { viewModel.onMessageClick(message) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            } else if (state.query.isNotBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "未找到相关聊天记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserProfileHeader(
    nickname: String,
    avatar: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = avatar,
            contentDescription = "头像",
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            placeholderIcon = Icons.Default.Person,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = nickname,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "查看资料",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchMessageItem(
    message: Message,
    query: String,
    currentUserId: Long,
    peerNickname: String,
    peerAvatar: String?,
    onClick: () -> Unit,
) {
    val isSentByMe = message.senderId == currentUserId

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CachedImage(
            imageUrl = if (isSentByMe) null else peerAvatar,
            contentDescription = "头像",
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            placeholderIcon = Icons.Default.Person,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isSentByMe) "我" else peerNickname,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = buildAnnotatedString {
                    val content = message.content
                    val index = content.indexOf(query, ignoreCase = true)
                    if (index >= 0) {
                        val start = maxOf(0, index - 30)
                        val end = minOf(content.length, index + query.length + 30)
                        val prefix = if (start > 0) "..." else ""
                        val suffix = if (end < content.length) "..." else ""
                        append(prefix)
                        append(content.substring(start, index))
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append(content.substring(index, index + query.length))
                        }
                        append(content.substring(index + query.length, end))
                        append(suffix)
                    } else {
                        append(content.take(60))
                        if (content.length > 60) append("...")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
