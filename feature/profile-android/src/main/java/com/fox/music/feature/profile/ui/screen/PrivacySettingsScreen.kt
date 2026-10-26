package com.fox.music.feature.profile.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.user.BlacklistUser
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.FoxDialog
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.feature.profile.viewmodel.PrivacySettingsEffect
import com.fox.music.feature.profile.viewmodel.PrivacySettingsIntent
import com.fox.music.feature.profile.viewmodel.PrivacySettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: PrivacySettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.sendIntent(PrivacySettingsIntent.Load)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PrivacySettingsEffect.NavigateBack -> onBack()
                is PrivacySettingsEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FoxTopBar(
                title = "隐私设置",
                showBackButton = true,
                onBackClick = viewModel::onBackClick,
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.settings == null -> {
                LoadingIndicator(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    useLottie = false,
                )
            }
            state.error != null && state.settings == null -> {
                ErrorView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    message = state.error!!,
                    onRetry = { viewModel.sendIntent(PrivacySettingsIntent.Load) },
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    item {
                        PrivacySwitchItem(
                            title = "私密账号",
                            subtitle = "开启后非好友无法添加你为好友",
                            checked = state.settings?.isPrivate == true,
                            enabled = !state.isSaving,
                            onCheckedChange = {
                                viewModel.sendIntent(PrivacySettingsIntent.SetPrivateAccount(it))
                            },
                        )
                    }
                    item {
                        PrivacySwitchItem(
                            title = "无法被搜索找到",
                            subtitle = "开启后其他用户无法通过搜索找到你",
                            checked = state.settings?.isSearchable == false,
                            enabled = !state.isSaving,
                            onCheckedChange = {
                                viewModel.sendIntent(PrivacySettingsIntent.SetSearchable(!it))
                            },
                        )
                    }
                    item {
                        Text(
                            text = "黑名单",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    if (state.blacklist.isEmpty()) {
                        item {
                            Text(
                                text = "暂无黑名单用户",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 24.dp),
                            )
                        }
                    } else {
                        items(state.blacklist, key = { it.id }) { user ->
                            BlacklistItem(
                                user = user,
                                onUnblock = {
                                    viewModel.sendIntent(PrivacySettingsIntent.ShowUnblockDialog(user))
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    state.unblockTarget?.let { target ->
        FoxDialog(
            title = "解除黑名单",
            text = "确定要将「${target.nickname ?: "用户"}」移出黑名单吗？",
            confirmText = "确定",
            onDismiss = { viewModel.sendIntent(PrivacySettingsIntent.DismissUnblockDialog) },
            onConfirm = { viewModel.sendIntent(PrivacySettingsIntent.ConfirmUnblock) },
            confirmEnabled = !state.isUnblocking,
        )
    }
}

@Composable
private fun PrivacySwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun BlacklistItem(
    user: BlacklistUser,
    onUnblock: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = user.avatar,
            contentDescription = user.nickname,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
            shape = CircleShape,
            placeholderIcon = Icons.Default.Person,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = user.nickname?.takeIf { it.isNotBlank() } ?: "用户",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onUnblock) {
            Text("解除")
        }
    }
}
