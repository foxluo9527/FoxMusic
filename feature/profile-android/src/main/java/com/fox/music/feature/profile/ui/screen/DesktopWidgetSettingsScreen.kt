package com.fox.music.feature.profile.ui.screen

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.WidgetPreviewCard
import com.fox.music.core.ui.theme.FoxSpacing

data class WidgetPinOption(
    val label: String,
    val receiverClassName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopWidgetSettingsScreen(
    modifier: Modifier = Modifier,
    musicController: MusicController,
    widgetPinOptions: List<WidgetPinOption> = emptyList(),
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val playerState by musicController.playerState.collectAsState(PlayerState())
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val canPin = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        appWidgetManager.isRequestPinAppWidgetSupported

    Scaffold(
        modifier = modifier,
        topBar = {
            FoxTopBar(
                title = "桌面小组件",
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
            WidgetPreviewCard(
                playerState = playerState,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "长按桌面 → 小组件 → FoxMusic，可选择迷你 / 标准 / 大卡三种尺寸",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (canPin && widgetPinOptions.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "快速添加",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    widgetPinOptions.forEachIndexed { index, option ->
                        val pinWidget: () -> Unit = {
                            val component = ComponentName(context.packageName, option.receiverClassName)
                            appWidgetManager.requestPinAppWidget(component, null, null)
                        }
                        if (index == 1) {
                            Button(
                                onClick = pinWidget,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("添加${option.label}")
                            }
                        } else {
                            OutlinedButton(
                                onClick = pinWidget,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("添加${option.label}")
                            }
                        }
                    }
                }
            }
        }
    }
}
