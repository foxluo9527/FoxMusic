package com.fox.music.feature.profile.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.ui.component.FoxChoiceDialog
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.feature.profile.ui.component.SettingsDivider
import com.fox.music.feature.profile.ui.component.SettingsGroup
import com.fox.music.feature.profile.ui.component.SettingsNavigationItem
import com.fox.music.feature.profile.ui.component.SettingsSwitchItem
import com.fox.music.feature.profile.viewmodel.PrefChoiceKind
import com.fox.music.feature.profile.viewmodel.PreferencesSettingsEffect
import com.fox.music.feature.profile.viewmodel.PreferencesSettingsIntent
import com.fox.music.feature.profile.viewmodel.PreferencesSettingsViewModel
import androidx.compose.runtime.LaunchedEffect

const val DOWNLOAD_SETTINGS_ROUTE = "settings/download"

/**
 * 「下载」二级页（渐进式披露重构）。
 *
 * 合并来源：主页「应用」组里散着的「仅 WiFi 下载」（开关）、「下载管理」（跳转），
 * 以及一个**从来没有入口**的「下载音质」（SettingsViewModel 里
 * ShowDownloadQualityPicker + DOWNLOAD_QUALITY 分支都写好了，但 UI 从没调用过）。
 * 三件事同属「下载」一个心智模型，现在收到一页里，顺带把死功能接上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: PreferencesSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenDownloadManager: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefs = state.preferences

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PreferencesSettingsEffect.ShowMessage ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    state.choice?.let { dialog ->
        FoxChoiceDialog(
            title = when (dialog.kind) {
                PrefChoiceKind.DOWNLOAD_QUALITY -> "下载音质"
                PrefChoiceKind.CACHE_LIMIT -> "缓存上限"
            },
            options = dialog.options,
            selectedIndex = dialog.selectedIndex,
            onSelect = { index ->
                viewModel.sendIntent(PreferencesSettingsIntent.OnChoiceSelected(index))
            },
            onDismiss = { viewModel.sendIntent(PreferencesSettingsIntent.DismissChoiceDialog) },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            FoxTopBar(
                title = "下载",
                showBackButton = true,
                onBackClick = onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsGroup(title = "网络") {
                    SettingsSwitchItem(
                        title = "仅 WiFi 下载",
                        subtitle = "移动网络下暂停下载任务",
                        checked = prefs.downloadOnWifiOnly,
                        onCheckedChange = {
                            viewModel.sendIntent(PreferencesSettingsIntent.UpdateDownloadOnWifiOnly(it))
                        },
                    )
                }
            }

            item {
                SettingsGroup(title = "音质") {
                    SettingsNavigationItem(
                        title = "下载音质",
                        subtitle = viewModel.downloadQualityLabel(prefs.downloadQuality),
                        onClick = {
                            viewModel.sendIntent(PreferencesSettingsIntent.ShowDownloadQualityPicker)
                        },
                    )
                }
            }

            item {
                SettingsGroup(title = "已下载") {
                    SettingsNavigationItem(
                        title = "下载管理",
                        subtitle = "查看与管理已下载歌曲",
                        onClick = onOpenDownloadManager,
                    )
                }
            }
        }
    }
}
