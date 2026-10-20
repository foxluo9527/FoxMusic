package com.fox.music.feature.chat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ProfileRelationshipBottomBar
import com.fox.music.feature.chat.ui.component.UserStatusChip
import com.fox.music.feature.chat.viewmodel.UserProfileState

/**
 * 「用户资料」页 —— 跨端无状态实现。
 *
 * 依赖的两个 core:ui 组件（CachedImage / ProfileRelationshipBottomBar）都已在
 * commonMain，因此 iOS 侧可直接渲染同一份 UI。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileContent(
    state: UserProfileState,
    onBack: () -> Unit,
    onSendMessageClick: () -> Unit,
    onAddFriendClick: () -> Unit,
    onUserHomeClick: () -> Unit,
    onShowRemarkDialog: () -> Unit,
    onDismissRemarkDialog: () -> Unit,
    onSubmitRemark: (String) -> Unit,
    onShareProfile: (userId: Long, nickname: String?, signature: String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("用户资料") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.userId > 0L) {
                        IconButton(
                            onClick = {
                                onShareProfile(state.userId, state.displayName, state.signature)
                            },
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "分享名片")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (!state.isSelf) {
                ProfileRelationshipBottomBar(
                    isFriend = state.isFriend,
                    isRequested = state.isRequested,
                    isPrivateAccount = state.isPrivate,
                    onSendMessage = onSendMessageClick,
                    onAddFriend = onAddFriendClick,
                )
            }
        },
    ) { padding ->
        if (state.isLoading && state.nickname.isBlank()) {
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ProfileHeader(
                nickname = state.displayName,
                avatar = state.avatar,
                relationshipLabel = relationshipLabel(
                    isSelf = state.isSelf,
                    isFriend = state.isFriend,
                    isRequested = state.isRequested,
                ),
                isFriendHighlight = state.isFriend,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileInfoCard {
                    ProfileInfoRow(
                        icon = Icons.Default.Home,
                        label = "主页",
                        value = "查看 TA 的个人主页",
                        onClick = onUserHomeClick,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ProfileInfoRow(
                        icon = Icons.Default.Tag,
                        label = "用户 ID",
                        value = state.userId.toString(),
                    )
                    if (state.isFriend) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ProfileInfoRow(
                            icon = Icons.Default.DriveFileRenameOutline,
                            label = "好友备注",
                            value = state.mark?.takeIf { it.isNotBlank() } ?: "点击设置备注",
                            trailing = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "修改备注",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = onShowRemarkDialog,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ProfileInfoRow(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        label = "个性签名",
                        value = state.signature?.takeIf { it.isNotBlank() } ?: "这个人很懒，什么都没写",
                    )
                }

                if (state.isSelf) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    ) {
                        Text(
                            text = "这是你自己的资料",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (state.showRemarkDialog) {
        RemarkEditDialog(
            initialRemark = state.mark.orEmpty(),
            isSaving = state.isSavingRemark,
            onConfirm = onSubmitRemark,
            onDismiss = onDismissRemarkDialog,
        )
    }
}

@Composable
private fun RemarkEditDialog(
    initialRemark: String,
    isSaving: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxLength = UserProfileState.MAX_REMARK_LENGTH
    var text by remember(initialRemark) { mutableStateOf(initialRemark) }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("修改备注") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= maxLength) text = it },
                singleLine = true,
                label = { Text("好友备注") },
                placeholder = { Text("最多 $maxLength 个字符") },
                supportingText = { Text("${text.length}/$maxLength") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = !isSaving,
            ) {
                Text(if (isSaving) "保存中..." else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ProfileHeader(
    nickname: String,
    avatar: String?,
    relationshipLabel: String?,
    isFriendHighlight: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CachedImage(
                imageUrl = avatar,
                contentDescription = nickname,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape,
                    ),
                shape = CircleShape,
                placeholderIcon = Icons.Default.Person,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = nickname,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            relationshipLabel?.let { label ->
                Spacer(modifier = Modifier.height(8.dp))
                UserStatusChip(
                    text = label,
                    isHighlight = isFriendHighlight,
                )
            }
        }
    }
}

@Composable
private fun ProfileInfoCard(
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

private fun relationshipLabel(
    isSelf: Boolean,
    isFriend: Boolean,
    isRequested: Boolean,
): String? = when {
    isSelf -> "我自己"
    isFriend -> "我的好友"
    isRequested -> "已发送申请"
    else -> "陌生人"
}
