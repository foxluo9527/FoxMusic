package com.fox.music.feature.profile.ui.screen

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.LockScreenLyricPreview
import com.fox.music.core.ui.component.LyricHelpDialog
import com.fox.music.core.ui.theme.resolveDarkTheme
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.feature.profile.ui.component.SettingsSwitchItem
import com.fox.music.feature.profile.viewmodel.DesktopLyricSettingsIntent
import com.fox.music.feature.profile.viewmodel.DesktopLyricSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreenLyricSettingsScreen(
    modifier: Modifier = Modifier,
    musicController: MusicController,
    viewModel: DesktopLyricSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onPreviewLockScreenLyric: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val playerState by musicController.playerState.collectAsState(PlayerState())
    val prefs = state.preferences

    if (state.showHelpDialog) {
        LyricHelpDialog(onDismiss = {
            viewModel.sendIntent(DesktopLyricSettingsIntent.DismissHelp)
        })
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            FoxTopBar(
                title = "锁屏歌词",
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                SettingsSwitchItem(
                    title = "锁屏歌词",
                    checked = prefs.lockScreenLyricEnabled,
                    onCheckedChange = {
                        viewModel.sendIntent(DesktopLyricSettingsIntent.UpdateLockScreenLyric(it))
                    },
                )
            }

            LockScreenLyricPreview(
                playerState = playerState,
                appThemeId = prefs.appThemeId,
                customThemeSeedArgb = prefs.customThemeSeedArgb,
                darkTheme = prefs.resolveDarkTheme(isSystemInDarkTheme()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            )

            TextButton(
                onClick = onPreviewLockScreenLyric,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "立即预览")
            }

            TextButton(
                onClick = { viewModel.sendIntent(DesktopLyricSettingsIntent.ShowHelp) },
            ) {
                Text(
                    text = "锁屏歌词不显示？",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "查看帮助",
                    color = Color(0xFFE53935),
                )
            }
        }
    }
}
