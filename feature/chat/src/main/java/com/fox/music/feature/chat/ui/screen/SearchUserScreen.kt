package com.fox.music.feature.chat.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ui.components.SearchBar
import com.fox.music.feature.chat.ui.component.UserListItem
import com.fox.music.feature.chat.ui.component.UserStatusChip
import com.fox.music.feature.chat.util.displayName
import com.fox.music.feature.chat.viewmodel.SearchUserEffect
import com.fox.music.feature.chat.viewmodel.SearchUserIntent
import com.fox.music.feature.chat.viewmodel.SearchUserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchUserScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchUserViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUserProfile: (
        userId: Long,
        nickname: String?,
        avatar: String?,
        signature: String?,
        isFriend: Boolean,
        isRequested: Boolean,
    ) -> Unit = { _, _, _, _, _, _ -> },
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SearchUserEffect.NavigateBack -> onBack()
                is SearchUserEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is SearchUserEffect.NavigateToUserProfile -> {
                    onNavigateToUserProfile(
                        effect.userId,
                        effect.nickname,
                        effect.avatar,
                        effect.signature,
                        effect.isFriend,
                        effect.isRequested,
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    SearchBar(
                        query = state.query,
                        onQueryChange = { viewModel.sendIntent(SearchUserIntent.QueryChange(it)) },
                        onSearch = { viewModel.sendIntent(SearchUserIntent.Search) },
                        placeholder = "输入用户名或昵称",
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> {
                    SearchStatusContent(
                        icon = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 3.dp,
                            )
                        },
                        title = "正在搜索",
                        subtitle = "请稍候...",
                    )
                }
                state.error != null -> {
                    SearchStatusContent(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            )
                        },
                        title = "搜索失败",
                        subtitle = state.error.orEmpty(),
                    )
                }
                !state.hasSearched -> {
                    SearchStatusContent(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.PersonSearch,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            )
                        },
                        title = "搜索好友",
                        subtitle = "输入用户名或昵称，按回车开始搜索",
                    )
                }
                state.results.isEmpty() -> {
                    SearchStatusContent(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            )
                        },
                        title = "未找到相关用户",
                        subtitle = "换个关键词试试吧",
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        item {
                            Text(
                                text = "找到 ${state.results.size} 个用户",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                        items(state.results, key = { it.id }) { user ->
                            UserListItem(
                                avatar = user.avatar,
                                name = user.displayName(),
                                subtitle = user.signature,
                                onClick = { viewModel.onUserClick(user) },
                                trailing = {
                                    when {
                                        user.isFriend -> UserStatusChip(
                                            text = "好友",
                                            isHighlight = true,
                                        )
                                        user.isRequested -> UserStatusChip(text = "已申请")
                                    }
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 76.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchStatusContent(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 20.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )
    }
}
