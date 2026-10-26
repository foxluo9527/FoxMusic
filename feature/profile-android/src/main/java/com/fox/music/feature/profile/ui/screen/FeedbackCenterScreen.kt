package com.fox.music.feature.profile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.feature.profile.ui.component.SettingsDivider
import com.fox.music.feature.profile.ui.component.SettingsGroup
import com.fox.music.feature.profile.ui.component.SettingsNavigationItem

const val FEEDBACK_CENTER_ROUTE = "settings/feedback"

/**
 * 「我的反馈」二级页（渐进式披露重构）。
 *
 * 主页原来把「举报历史」和「音源反馈记录」当两条平级设置项，夹在「检查更新」「缓存」之间。
 * 两者其实是同一件事的两条来路（我提交过的纠错记录），合并成一个入口，主页的
 * 「应用」组才收得住。两页本身完全复用，不新建内容页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackCenterScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onReportHistory: () -> Unit = {},
    onFeedbackHistory: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            FoxTopBar(
                title = "我的反馈",
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
                SettingsGroup(title = "提交记录") {
                    SettingsNavigationItem(
                        title = "举报记录",
                        subtitle = "查看你提交的内容举报与处理进度",
                        onClick = onReportHistory,
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = "音源纠错记录",
                        subtitle = "查看音源纠错进度与自动匹配结果",
                        onClick = onFeedbackHistory,
                    )
                }
            }
        }
    }
}
