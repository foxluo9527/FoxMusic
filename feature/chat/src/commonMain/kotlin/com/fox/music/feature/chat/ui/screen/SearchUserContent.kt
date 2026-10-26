package com.fox.music.feature.chat.ui.screen

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
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.chat.SearchedUser
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.SearchBar
import com.fox.music.feature.chat.ui.component.UserListItem
import com.fox.music.feature.chat.ui.component.UserStatusChip
import com.fox.music.feature.chat.util.displayName
import com.fox.music.feature.chat.viewmodel.SearchUserState

/**
 * 「搜索用户」页 —— 跨端无状态实现。
 *
 * 与 Android 原实现相比，共享版**去掉了内容区多余的 `statusBarsPadding()`**：
 * Scaffold 的 `contentWindowInsets`（systemBars）与 TopAppBar 的 `windowInsets`
 * 已经把状态栏算过一遍，再叠一层会让空态/结果区整体下移一段。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchUserContent(
    state: SearchUserState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onUserClick: (SearchedUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            FoxTopBar(
                showBackButton = true,
                onBackClick = onBack,
                titleContent = {
                    SearchBar(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        // SearchBar 回调带查询串，但本页搜索由 ViewModel 的 Search intent
                        // （自带当前 query 状态）触发，故丢弃该参数。
                        onSearch = { onSearch() },
                        placeholder = "输入用户名或昵称",
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
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
                                onClick = { onUserClick(user) },
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
