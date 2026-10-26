package com.fox.music.feature.profile.ui.screen

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.user.AppThemeIds
import com.fox.music.core.model.user.DarkMode
import com.fox.music.core.model.user.NotificationPreferenceSettings
import com.fox.music.core.model.user.PlayQuality
import com.fox.music.core.model.user.isAdmin
import com.fox.music.core.ui.component.FoxChoiceDialog
import com.fox.music.core.ui.component.FoxDialog
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.UpdateDialog
import com.fox.music.core.ui.theme.AppThemePresets
import com.fox.music.feature.profile.ui.component.SettingsDivider
import com.fox.music.feature.profile.ui.component.SettingsGroup
import com.fox.music.feature.profile.ui.component.SettingsNavigationItem
import com.fox.music.feature.profile.viewmodel.ChoiceType
import com.fox.music.feature.profile.viewmodel.SettingsEffect
import com.fox.music.feature.profile.viewmodel.SettingsIntent
import com.fox.music.feature.profile.viewmodel.SettingsState
import com.fox.music.feature.profile.viewmodel.SettingsViewModel
import java.io.File

const val SETTINGS_ROUTE = "settings"

/**
 * 设置主页（渐进式披露重构 v2）。
 *
 * 重构前：4 组 17 行，「应用」一组独占 11 行，把外观 / 网络 / 通知 / 下载 / 记录 / 版本 /
 * 存储平均铺在同一层，一屏读不完，且「深色模式」与「主题设置」是同一件事的两个入口
 * （前者立即生效、后者草稿+应用，行为还不一致）。
 *
 * 重构后：每个一级条目只回答"我要去哪"，细节下沉到二级页：
 * - 外观与主题 ← 深色模式 + 主题设置（合并，去重）
 * - 下载 ← 仅 WiFi 下载 + 下载音质 + 下载管理（合并，并救活无入口的下载音质）
 * - 存储空间 ← 缓存大小限制 + 清理缓存（合并）
 * - 我的反馈 ← 举报历史 + 音源反馈记录（合并）
 * - 「取消定时关闭」独立行删除 —— 定时关闭的选择弹窗里本来就有「关闭」这一档。
 *
 * 每组条目数现在都 ≤ 4，符合「一级入口一眼扫完」的信息密度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    isLogin: Boolean = true,
    onThemeSettings: () -> Unit = {},
    onDownloadSettings: () -> Unit = {},
    onStorageSettings: () -> Unit = {},
    onFeedbackCenter: () -> Unit = {},
    onDesktopLyricSettings: () -> Unit = {},
    onNotificationSettings: () -> Unit = {},
    onBack: () -> Unit = {},
    onLogin: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onManageLibrary: () -> Unit = {},
    onPrivacySettings: () -> Unit = {},
    onInstallApk: (File) -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 首载与登录态变化时拉一次账号（幂等）。
    LaunchedEffect(isLogin) {
        viewModel.sendIntent(SettingsIntent.Load(fetchProfile = isLogin))
    }

    // 从「存储空间」清完缓存返回时，主页那行「已用 x MB」必须跟着变 —— 只刷用量，
    // 不重打 profile 接口。
    LaunchedEffect(Unit) {
        viewModel.sendIntent(SettingsIntent.RefreshCacheUsage)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SettingsEffect.NavigateToEditProfile -> onEditProfile()
                SettingsEffect.NavigateToManageLibrary -> onManageLibrary()
                SettingsEffect.NavigateToLogin -> onLogout()
                is SettingsEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is SettingsEffect.LaunchInstall -> onInstallApk(effect.file)
                SettingsEffect.RecreateActivity -> {
                    (context as? Activity)?.recreate()
                }
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
        FoxDialog(
            title = "退出登录",
            text = "确定要退出当前账号吗？",
            confirmText = "退出",
            destructive = true,
            onDismiss = { viewModel.sendIntent(SettingsIntent.DismissLogoutDialog) },
            onConfirm = { viewModel.sendIntent(SettingsIntent.ConfirmLogout) },
        )
    }

    state.choiceDialog?.let { dialog ->
        FoxChoiceDialog(
            title = when (dialog.type) {
                ChoiceType.REPEAT_MODE -> "循环模式"
                ChoiceType.SLEEP_TIMER -> "定时关闭"
            },
            options = dialog.options,
            selectedIndex = dialog.selectedIndex,
            onSelect = { index ->
                viewModel.sendIntent(SettingsIntent.OnChoiceSelected(dialog.type, index))
            },
            onDismiss = { viewModel.sendIntent(SettingsIntent.DismissChoiceDialog) },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            FoxTopBar(
                title = "设置",
                showBackButton = true,
                onBackClick = onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.isLoading && state.user == null && isLogin) {
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
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SettingsGroup(title = "账号") {
                        if (isLogin) {
                            SettingsNavigationItem(
                                title = "编辑资料",
                                subtitle = "昵称、签名、邮箱、头像",
                                onClick = { viewModel.sendIntent(SettingsIntent.OnEditProfileClick) },
                            )
                            SettingsDivider()
                            SettingsNavigationItem(
                                title = "隐私设置",
                                subtitle = "私密账号、搜索可见性、黑名单",
                                onClick = onPrivacySettings,
                            )
                            SettingsDivider()
                            SettingsNavigationItem(
                                title = "退出登录",
                                onClick = { viewModel.sendIntent(SettingsIntent.OnLogoutClick) },
                            )
                        } else {
                            SettingsNavigationItem(
                                title = "点击登录",
                                subtitle = "登录后可同步账号与个性化设置",
                                onClick = onLogin,
                            )
                        }
                    }
                }

                item {
                    SettingsGroup(title = "播放与歌词") {
                        SettingsNavigationItem(
                            title = "循环模式",
                            subtitle = viewModel.repeatModeLabel(state.repeatMode),
                            onClick = { viewModel.sendIntent(SettingsIntent.ShowRepeatModePicker) },
                        )
                        SettingsDivider()
                        SettingsNavigationItem(
                            title = "定时关闭",
                            subtitle = viewModel.sleepTimerLabel(state.sleepTimer),
                            onClick = { viewModel.sendIntent(SettingsIntent.ShowSleepTimerPicker) },
                        )
                        SettingsDivider()
                        SettingsNavigationItem(
                            title = "歌词与桌面",
                            subtitle = "桌面歌词、车载模式、锁屏歌词、小组件",
                            onClick = onDesktopLyricSettings,
                        )
                    }
                }

                item {
                    SettingsGroup(title = "通用") {
                        SettingsNavigationItem(
                            title = "外观与主题",
                            subtitle = appearanceSummary(state, viewModel::darkModeLabel),
                            onClick = onThemeSettings,
                        )
                        SettingsDivider()
                        SettingsNavigationItem(
                            title = "通知与提醒",
                            subtitle = notificationSummary(state.preferences.notificationSettings),
                            onClick = onNotificationSettings,
                        )
                        SettingsDivider()
                        SettingsNavigationItem(
                            title = "下载",
                            subtitle = downloadSummary(state),
                            onClick = onDownloadSettings,
                        )
                        SettingsDivider()
                        SettingsNavigationItem(
                            title = "存储空间",
                            subtitle = "已用 ${viewModel.formatBytes(state.cacheUsedBytes)}" +
                                " / ${viewModel.cacheLimitLabel(state.preferences.cacheMaxBytes)}",
                            onClick = onStorageSettings,
                        )
                    }
                }

                item {
                    SettingsGroup(title = "支持") {
                        SettingsNavigationItem(
                            title = "我的反馈",
                            subtitle = "举报与音源纠错的提交记录",
                            onClick = onFeedbackCenter,
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
                    }
                }

                if (state.user?.isAdmin == true) {
                    item {
                        SettingsGroup(title = "管理") {
                            SettingsNavigationItem(
                                title = "曲库管理",
                                onClick = { viewModel.sendIntent(SettingsIntent.OnManageLibraryClick) },
                            )
                        }
                    }
                }
            }
        }

        if (state.isLoading) {
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

/** 「外观与主题」的一行摘要：色板名 · 深浅档位（原来要靠两行才能拼出来）。 */
private fun appearanceSummary(
    state: SettingsState,
    darkModeLabel: (DarkMode) -> String,
): String {
    val themeName = if (state.preferences.appThemeId == AppThemeIds.CUSTOM) {
        "个性自定义"
    } else {
        AppThemePresets.presetName(state.preferences.appThemeId)
    }
    return "$themeName · ${darkModeLabel(state.preferences.darkMode)}"
}

/** 「下载」的一行摘要：网络策略 · 音质。 */
private fun downloadSummary(state: SettingsState): String {
    val network = if (state.preferences.downloadOnWifiOnly) "仅 WiFi" else "WiFi 与移动网络"
    val quality = when (state.preferences.downloadQuality) {
        PlayQuality.STANDARD -> "标准"
        PlayQuality.HIGH -> "高品质"
        PlayQuality.LOSSLESS -> "无损"
    }
    return "$network · $quality"
}

/** 「通知与提醒」的一行摘要：开了几类。8 个开关全摊在主页会把这一组撑爆。 */
private fun notificationSummary(settings: NotificationPreferenceSettings): String {
    val flags = listOf(
        settings.commentEnabled,
        settings.likeEnabled,
        settings.followEnabled,
        settings.mentionEnabled,
        settings.messageEnabled,
        settings.friendRequestEnabled,
        settings.systemEnabled,
        settings.musicEnabled,
    )
    val enabled = flags.count { it }
    return when (enabled) {
        0 -> "全部关闭"
        flags.size -> "全部开启"
        else -> "已开启 $enabled / ${flags.size} 类"
    }
}
