package com.fox.music.core.ui.component

import androidx.compose.runtime.Composable
import com.fox.music.core.model.app.AppUpdateInfo

/**
 * 版本升级弹窗 —— 三种态分别落到 Fox 家族，取代原来那套手写 AlertDialog：
 *
 * - **下载中** → [FoxProgressDialog]（不可取消，进度确定/不确定两态）；
 * - **出错** → [FoxDialog] 主钮「重试」；
 * - **常态** → [FoxDialog] 主钮「立即更新」+ 次钮「稍后」（强制升级时不给次钮且不可取消）。
 *
 * 旧实现把三种态塞进同一个 AlertDialog 的 confirmButton/dismissButton 分支里，
 * 圆角与按钮排布都是 M3 默认值，和全站 20dp 纸感容器对不上。
 */
@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    forceUpdate: Boolean,
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadIndeterminate: Boolean = false,
    downloadStatusText: String? = null,
    error: String?,
    onDismiss: () -> Unit,
    onConfirmUpdate: () -> Unit,
    onRetry: () -> Unit,
) {
    val title = updateInfo.upgradeTitle
        ?: "发现新版本 ${updateInfo.latestVersionName}"
    val upgradeContent = updateInfo.upgradeContent

    when {
        isDownloading -> {
            FoxProgressDialog(
                title = title,
                text = downloadStatusText ?: "下载中 $downloadProgress%",
                progress = if (downloadIndeterminate) null else downloadProgress / 100f,
                // 下载中必须锁死，不允许返回键/点遮罩把弹窗关掉
                onDismiss = null,
            )
        }

        !error.isNullOrBlank() -> {
            FoxDialog(
                title = title,
                text = error,
                confirmText = "重试",
                dismissText = if (forceUpdate) null else "稍后",
                cancelable = !forceUpdate,
                onDismiss = onDismiss,
                onConfirm = onRetry,
            )
        }

        else -> {
            val parts = buildList {
                if (!upgradeContent.isNullOrBlank()) add(upgradeContent)
                updateInfo.changelog.forEach { add("• $it") }
            }
            FoxDialog(
                title = title,
                text = parts.joinToString("\n"),
                confirmText = "立即更新",
                dismissText = if (forceUpdate) null else "稍后",
                cancelable = !forceUpdate,
                onDismiss = onDismiss,
                onConfirm = onConfirmUpdate,
            )
        }
    }
}
