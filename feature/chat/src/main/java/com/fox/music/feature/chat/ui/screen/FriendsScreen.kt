package com.fox.music.feature.chat.ui.screen

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.chat.Friend
import com.fox.music.core.model.chat.FriendRequest
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.feature.chat.util.displayName
import com.fox.music.feature.chat.viewmodel.FriendsEffect
import com.fox.music.feature.chat.viewmodel.FriendsIntent
import com.fox.music.feature.chat.viewmodel.FriendsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
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
        viewModel.sendIntent(FriendsIntent.Load)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                FriendsEffect.NavigateBack -> onBack()
                FriendsEffect.NavigateToSearch -> onNavigateToSearch()
                is FriendsEffect.NavigateToUserProfile -> {
                    onNavigateToUserProfile(
                        effect.userId,
                        effect.nickname,
                        effect.avatar,
                        effect.signature,
                        effect.isFriend,
                        effect.isRequested,
                    )
                }
                is FriendsEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("我的好友") },
                navigationIcon = {
                    IconButton(onClick = viewModel::onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onAddFriendClick) {
                        Icon(Icons.Default.Add, contentDescription = "搜索用户")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading && state.friends.isEmpty() && state.friendRequests.isEmpty()) {
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
            if (state.friendRequests.isNotEmpty()) {
                item {
                    Text(
                        text = "好友申请 (${state.friendRequests.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(state.friendRequests, key = { it.id }) { request ->
                    FriendRequestItem(
                        request = request,
                        isAccepting = state.isAccepting,
                        onAccept = { viewModel.sendIntent(FriendsIntent.AcceptRequest(request.id)) },
                    )
                }
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }

            if (state.friends.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无好友",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = "好友列表 (${state.friends.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(state.friends, key = { it.id }) { friend ->
                    FriendItem(
                        friend = friend,
                        onClick = { viewModel.onFriendClick(friend) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendRequestItem(
    request: FriendRequest,
    isAccepting: Boolean,
    onAccept: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = request.avatar,
            contentDescription = request.displayName(),
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            placeholderIcon = Icons.Default.Person,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.displayName(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            request.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onAccept,
            enabled = !isAccepting,
        ) {
            Text("接受")
        }
    }
}

@Composable
private fun FriendItem(
    friend: Friend,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = friend.avatar,
            contentDescription = friend.displayName(),
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            placeholderIcon = Icons.Default.Person,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.displayName(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            friend.signature?.takeIf { it.isNotBlank() }?.let { signature ->
                Text(
                    text = signature,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
