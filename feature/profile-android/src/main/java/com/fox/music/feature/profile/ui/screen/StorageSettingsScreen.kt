package com.fox.music.feature.profile.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.ui.component.FoxChoiceDialog
import com.fox.music.core.ui.component.FoxDialog
import com.fox.music.core.ui.component.FoxLoadingDialog
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.feature.profile.ui.component.SettingsDivider
import com.fox.music.feature.profile.ui.component.SettingsGroup
import com.fox.music.feature.profile.ui.component.SettingsNavigationItem
import com.fox.music.feature.profile.viewmodel.PrefChoiceKind
import com.fox.music.feature.profile.viewmodel.PreferencesSettingsEffect
import com.fox.music.feature.profile.viewmodel.PreferencesSettingsIntent
import com.fox.music.feature.profile.viewmodel.PreferencesSettingsViewModel

const val STORAGE_SETTINGS_ROUTE = "settings/storage"

/**
 * 「存储空间」二级页（渐进式披露重构）。
 *
 * 为什么要单独一页：主页原来把「缓存大小限制」和「清理缓存」当两条平级设置项铺在
 * 「应用」组里，用户要读两行才能拼出「我占了多少 / 上限多少」。合并成一页后，
 * 主页只需要一行「存储空间 · 已用 320 MB / 2.0 GB」，进来看才是容量条 + 两个动作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: PreferencesSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefs = state.preferences

    LaunchedEffect(Unit) {
        viewModel.sendIntent(PreferencesSettingsIntent.RefreshCacheUsage)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PreferencesSettingsEffect.ShowMessage ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (state.showClearCacheDialog) {
        FoxDialog(
            title = "清理缓存",
            text = "将清理已缓存的音频文件（约 ${viewModel.formatBytes(state.cacheUsedBytes)}），" +
                "不会影响账号与本地设置。",
            confirmText = "清理",
            destructive = true,
            confirmEnabled = !state.isClearingCache,
            onDismiss = { viewModel.sendIntent(PreferencesSettingsIntent.DismissClearCacheDialog) },
            onConfirm = { viewModel.sendIntent(PreferencesSettingsIntent.ConfirmClearCache) },
        )
    }

    if (state.isClearingCache) {
        FoxLoadingDialog(text = "正在清理缓存...")
    }

    state.choice?.let { dialog ->
        FoxChoiceDialog(
            title = when (dialog.kind) {
                PrefChoiceKind.CACHE_LIMIT -> "缓存上限"
                PrefChoiceKind.DOWNLOAD_QUALITY -> "下载音质"
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
                title = "存储空间",
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
                SettingsGroup(title = "缓存占用") {
                    CacheUsageBlock(
                        usedText = viewModel.formatBytes(state.cacheUsedBytes),
                        maxText = viewModel.cacheLimitLabel(prefs.cacheMaxBytes),
                        usedRatio = if (prefs.cacheMaxBytes > 0L) {
                            (state.cacheUsedBytes.toFloat() / prefs.cacheMaxBytes).coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                    )
                }
            }

            item {
                SettingsGroup(title = "管理") {
                    SettingsNavigationItem(
                        title = "缓存上限",
                        subtitle = viewModel.cacheLimitLabel(prefs.cacheMaxBytes),
                        onClick = { viewModel.sendIntent(PreferencesSettingsIntent.ShowCacheLimitPicker) },
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = "清理缓存",
                        subtitle = "已使用 ${viewModel.formatBytes(state.cacheUsedBytes)}",
                        onClick = { viewModel.sendIntent(PreferencesSettingsIntent.OnClearCacheClick) },
                        showChevron = false,
                    )
                }
            }

            item {
                Text(
                    text = "缓存上限调整后，超出部分会在下次启动时随播放缓存策略一起回收。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = FoxSpacing.pagePadding + 4.dp),
                )
            }
        }
    }
}

/** 容量条：已用 / 上限，条子吃强调色，和主题页预览条同一套画法。 */
@Composable
private fun CacheUsageBlock(
    usedText: String,
    maxText: String,
    usedRatio: Float,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = usedText,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = " / $maxText",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(scheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(usedRatio)
                    .fillMaxHeight()
                    .background(scheme.primary),
            )
        }
    }
}
