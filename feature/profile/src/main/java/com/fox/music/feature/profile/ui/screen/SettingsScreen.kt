package com.fox.music.feature.profile.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.UpdateDialog
import com.fox.music.feature.profile.ui.component.SettingsDivider
import com.fox.music.feature.profile.ui.component.SettingsGroup
import com.fox.music.feature.profile.ui.component.SettingsNavigationItem
import com.fox.music.feature.profile.ui.component.SettingsSwitchItem
import com.fox.music.feature.profile.viewmodel.ChoiceType
import com.fox.music.feature.profile.viewmodel.SettingsEffect
import com.fox.music.feature.profile.viewmodel.SettingsIntent
import com.fox.music.core.model.user.isAdmin
import com.fox.music.core.model.chat.NotificationType
import com.fox.music.feature.profile.viewmodel.SettingsViewModel
import java.io.File

const val SETTINGS_ROUTE = "settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    isDesktopLyricEnabled: Boolean = false,
    onDesktopLyricChange: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onManageLibrary: () -> Unit = {},
    onDownloadManager: () -> Unit = {},
    onReportHistory: () -> Unit = {},
    onInstallApk: (File) -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.sendIntent(SettingsIntent.Load)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SettingsEffect.NavigateToEditProfile -> onEditProfile()
                SettingsEffect.NavigateToManageLibrary -> onManageLibrary()
                SettingsEffect.NavigateToDownloadManager -> onDownloadManager()
                SettingsEffect.NavigateToReportHistory -> onReportHistory()
                SettingsEffect.NavigateToLogin -> onLogout()
                is SettingsEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is SettingsEffect.LaunchInstall -> onInstallApk(effect.file)
            }
        }
    }

    val updateInfo = state.updateInfo
    if (state.showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo,
            forceUpdate = state.forceUpdate,
            isDownloading = state.isDownloadingApk,
            downloadProgress = state.downloadProgress,
            downloadIndeterminate = state.downloadIndeterminate,
            downloadStatusText = state.downloadStatusText,
            error = state.updateError,
            onDismiss = { viewModel.sendIntent(SettingsIntent.DismissUpdateDialog) },
            onConfirmUpdate = { viewModel.sendIntent(SettingsIntent.ConfirmUpdate) },
            onRetry = { viewModel.sendIntent(SettingsIntent.RetryUpdateDownload) },
        )
    }

    if (state.showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.sendIntent(SettingsIntent.DismissLogoutDialog) },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.sendIntent(SettingsIntent.ConfirmLogout) }) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.sendIntent(SettingsIntent.DismissLogoutDialog) }) {
                    Text("取消")
                }
            },
        )
    }

    if (state.showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.sendIntent(SettingsIntent.DismissClearCacheDialog) },
            title = { Text("清理缓存") },
            text = {
                Text(
                    "将清理已缓存的音频文件（约 ${viewModel.formatBytes(state.cacheUsedBytes)}），" +
                        "不会影响账号与本地设置。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.sendIntent(SettingsIntent.ConfirmClearCache) },
                    enabled = !state.isClearingCache,
                ) {
                    Text("清理")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.sendIntent(SettingsIntent.DismissClearCacheDialog) }) {
                    Text("取消")
                }
            },
        )
    }

    state.choiceDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { viewModel.sendIntent(SettingsIntent.DismissChoiceDialog) },
            title = {
                Text(
                    when (dialog.type) {
                        ChoiceType.REPEAT_MODE -> "循环模式"
                        ChoiceType.DARK_MODE -> "深色模式"
                        ChoiceType.CACHE_LIMIT -> "缓存大小限制"
                        ChoiceType.SLEEP_TIMER -> "定时关闭"
                        ChoiceType.DOWNLOAD_QUALITY -> "下载音质"
                    },
                )
            },
            text = {
                LazyColumn {
                    dialog.options.forEachIndexed { index, label ->
                        item {
                            TextButton(
                                onClick = {
                                    viewModel.sendIntent(
                                        SettingsIntent.OnChoiceSelected(dialog.type, index),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = if (index == dialog.selectedIndex) "✓ $label" else label,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.sendIntent(SettingsIntent.DismissChoiceDialog) }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading && state.user == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(useLottie = false)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                item {
                    SettingsGroup(title = "账号") {
                        SettingsNavigationItem(
                            title = "编辑资料",
                            subtitle = "昵称、签名、邮箱、头像",
                            onClick = { viewModel.sendIntent(SettingsIntent.OnEditProfileClick) },
                        )
                        SettingsDivider()
                        SettingsNavigationItem(
                            title = "退出登录",
                            onClick = { viewModel.sendIntent(SettingsIntent.OnLogoutClick) },
                        )
                    }
                }

                item {
                    SettingsGroup(title = "播放") {
                        SettingsNavigationItem(
                            title = "循环模式",
                            subtitle = viewModel.repeatModeLabel(state.repeatMode),
                            onClick = { viewModel.sendIntent(SettingsIntent.ShowRepeatModePicker) },
                        )
                        SettingsDivider()
                        SettingsSwitchItem(
                            title = "自动播放",
                            checked = state.preferences.autoPlay,
                            onCheckedChange = {
                                viewModel.sendIntent(SettingsIntent.UpdateAutoPlay(it))
                            },
                        )
                        SettingsDivider()
                        SettingsSwitchItem(
                            title = "桌面歌词",
                            checked = isDesktopLyricEnabled,
                            onCheckedChange = onDesktopLyricChange,
                        )
                        SettingsDivider()
                        SettingsNavigationItem(
                            title = "定时关闭",
                            subtitle = viewModel.sleepTimerLabel(state.sleepTimer),
                            onClick = { viewModel.sendIntent(SettingsIntent.ShowSleepTimerPicker) },
                        )
                        if (state.sleepTimer.isActive) {
                            SettingsDivider()
                            SettingsNavigationItem(
                                title = "取消定时关闭",
                                onClick = { viewModel.sendIntent(SettingsIntent.CancelSleepTimer) },
                            )
                        }
                    }
                }

                item {
                    SettingsGroup(title = "通知") {
                        val settings = state.preferences.notificationSettings
                        val notificationTypes = listOf(
                            NotificationType.MESSAGE,
                            NotificationType.COMMENT,
                            NotificationType.LIKE,
                            NotificationType.FOLLOW,
                            NotificationType.MENTION,
                            NotificationType.FRIEND_REQUEST,
                            NotificationType.SYSTEM,
                            NotificationType.MUSIC,
                        )
                        notificationTypes.forEachIndexed { index, type ->
                            SettingsSwitchItem(
                                title = viewModel.notificationTypeLabel(type),
                                subtitle = viewModel.notificationTypeSubtitle(type),
                                checked = settings.isEnabled(type),
                                onCheckedChange = {
                                    viewModel.sendIntent(
                                        SettingsIntent.UpdateNotificationSetting(type, it),
                                    )
                                },
                            )
                            if (index < notificationTypes.lastIndex) {
                                SettingsDivider()
                            }
                        }
                    }
                }

                item {
                    SettingsGroup(title = "应用") {
                        SettingsNavigationItem(
                            title = "深色模式",
                            subtitle = viewModel.darkModeLabel(state.preferences.darkMode),
                            onClick = { viewModel.sendIntent(SettingsIntent.ShowDarkModePicker) },
                        )
                        SettingsDivider()
                        SettingsSwitchItem(
                            title = "仅 WiFi 下载",
                            checked = state.preferences.downloadOnWifiOnly,
                            onCheckedChange = {
                                viewModel.sendIntent(SettingsIntent.UpdateDownloadOnWifiOnly(it))
                            },
                        )
                        SettingsDivider()
                        SettingsNavigationItem(
                            title = "下载管理",
                            subtitle = "查看与管理已下载歌曲",
                            onClick = { viewModel.sendIntent(SettingsIntent.OnDownloadManagerClick) },
                        )
                        SettingsDivider()
                        SettingsNavigationItem(
                            title = "举报历史",
                            subtitle = "查看你提交的举报记录",
                            onClick = { viewModel.sendIntent(SettingsIntent.OnReportHistoryClick) },
                        )
                        SettingsDivider()
                        SettingsNavigationItem(
                            title = "检查更新",
                            subtitle = if (state.isCheckingUpdate) {
                                "检查中..."
                            } else {
                                "当前版本 ${state.versionName}"
                            },
                            onClick = { viewModel.sendIntent(SettingsIntent.OnCheckUpdateClick) },
                        )
                        SettingsDivider()
                        SettingsNavigationItem(
                            title = "缓存大小限制",
                            subtitle = viewModel.cacheLimitLabel(state.preferences.cacheMaxBytes),
                            onClick = { viewModel.sendIntent(SettingsIntent.ShowCacheLimitPicker) },
                        )
                        SettingsDivider()
                        SettingsNavigationItem(
                            title = "清理缓存",
                            subtitle = "已使用 ${viewModel.formatBytes(state.cacheUsedBytes)}",
                            onClick = { viewModel.sendIntent(SettingsIntent.OnClearCacheClick) },
                        )
                        if (state.user?.isAdmin == true) {
                            SettingsDivider()
                            SettingsNavigationItem(
                                title = "曲库管理",
                                onClick = { viewModel.sendIntent(SettingsIntent.OnManageLibraryClick) },
                            )
                        }
                    }
                }
            }
        }

        if (state.isLoading || state.isClearingCache) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
