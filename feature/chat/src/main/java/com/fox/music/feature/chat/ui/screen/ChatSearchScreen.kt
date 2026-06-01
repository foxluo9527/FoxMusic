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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.chat.SearchResultItem
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.feature.chat.viewmodel.ChatSearchEffect
import com.fox.music.feature.chat.viewmodel.ChatSearchIntent
import com.fox.music.feature.chat.viewmodel.ChatSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSearchScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatSearchViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUserSearch: (Long, String, String?, String) -> Unit = { _, _, _, _ -> },
    onNavigateToChatDetail: (Long, Long?) -> Unit = { _, _ -> },
    onNavigateToUserProfile: (Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ChatSearchEffect.NavigateToUserSearch -> {
                    onNavigateToUserSearch(
                        effect.userId,
                        effect.nickname,
                        effect.avatar,
                        effect.query
                    )
                }

                is ChatSearchEffect.NavigateToChatDetail -> {
                    onNavigateToChatDetail(effect.userId, effect.messageId)
                }

                is ChatSearchEffect.NavigateToUserProfile -> {
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
                    TextField(
                        value = state.query,
                        onValueChange = { viewModel.sendIntent(ChatSearchIntent.UpdateQuery(it)) },
                        placeholder = { Text("搜索聊天记录") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.sendIntent(ChatSearchIntent.UpdateQuery("")) }) {
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

        if (state.query.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "输入关键词搜索聊天记录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        if (state.results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "未找到相关聊天记录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(state.results, key = { it.user.id }) { item ->
                SearchResultItem(
                    item = item,
                    query = state.query,
                    onClick = { viewModel.onResultClick(item) },
                    onUserProfileClick = { viewModel.onUserProfileClick(item.user.id) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    item: SearchResultItem,
    query: String,
    onClick: () -> Unit,
    onUserProfileClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = item.user.avatar,
            contentDescription = "头像",
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onUserProfileClick),
            shape = CircleShape,
            placeholderIcon = Icons.Default.Person,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.user.nickname ?: "",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = buildAnnotatedString {
                    val content = item.lastMessage.content
                    val index = content.indexOf(query, ignoreCase = true)
                    if (index >= 0) {
                        val start = maxOf(0, index - 20)
                        val end = minOf(content.length, index + query.length + 20)
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
                        append(content.take(50))
                        if (content.length > 50) append("...")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (item.matchCount > 1) {
            Text(
                text = "${item.matchCount}条",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
