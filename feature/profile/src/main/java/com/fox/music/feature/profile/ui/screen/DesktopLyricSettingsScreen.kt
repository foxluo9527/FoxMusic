package com.fox.music.feature.profile.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.ui.component.CarLyricHelpDialog
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.feature.profile.ui.component.SettingsDivider
import com.fox.music.feature.profile.ui.component.SettingsNavigationItem
import com.fox.music.feature.profile.ui.component.SettingsSwitchItem
import com.fox.music.feature.profile.viewmodel.DesktopLyricSettingsEffect
import com.fox.music.feature.profile.viewmodel.DesktopLyricSettingsIntent
import com.fox.music.feature.profile.viewmodel.DesktopLyricSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopLyricSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: DesktopLyricSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onLockScreenLyric: () -> Unit = {},
    onDesktopWidget: () -> Unit = {},
    onCarMode: () -> Unit = {},
    onRequestOverlayPermission: (onGranted: () -> Unit) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val prefs = state.preferences

    if (state.showCarHelpDialog) {
        CarLyricHelpDialog(onDismiss = {
            viewModel.sendIntent(DesktopLyricSettingsIntent.DismissCarHelp)
        })
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                DesktopLyricSettingsEffect.RequestOverlayPermission -> {
                    onRequestOverlayPermission {
                        viewModel.sendIntent(DesktopLyricSettingsIntent.ConfirmDesktopLyricEnabled)
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            FoxTopBar(
                title = "桌面与歌词",
                showBackButton = true,
                onBackClick = onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(FoxSpacing.pagePadding),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column {
                    SettingsSwitchItem(
                        title = "桌面歌词",
                        checked = prefs.desktopLyricEnabled,
                        onCheckedChange = {
                            viewModel.sendIntent(DesktopLyricSettingsIntent.UpdateDesktopLyric(it))
                        },
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = "桌面小组件",
                        onClick = onDesktopWidget,
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = "车载模式",
                        subtitle = "横屏预览完整车载应用",
                        onClick = onCarMode,
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        title = "车载蓝牙歌词",
                        subtitle = "蓝牙设备显示动态歌词",
                        checked = prefs.carBluetoothLyricEnabled,
                        onCheckedChange = {
                            viewModel.sendIntent(DesktopLyricSettingsIntent.UpdateCarBluetoothLyric(it))
                        },
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = "锁屏歌词",
                        onClick = onLockScreenLyric,
                    )
                }
            }

            TextButton(
                onClick = { viewModel.sendIntent(DesktopLyricSettingsIntent.ShowCarHelp) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "车载模式使用说明",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
