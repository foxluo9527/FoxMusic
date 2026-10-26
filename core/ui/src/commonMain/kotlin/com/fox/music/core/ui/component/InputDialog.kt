package com.fox.music.core.ui.component

import androidx.compose.runtime.Composable

/**
 * 通用单行输入弹窗 —— 已经合并到 [FoxInputDialog]。
 *
 * 之前这里是第二份手写 AlertDialog 实现，样式（M3 默认圆角/按钮排布）与
 * Fox 家族对不上。现在保留函数名作为转发层，真正的外观只有 [FoxInputDialog] 一份。
 */
@Composable
fun InputDialog(
    title: String,
    label: String,
    placeholder: String = "",
    initialValue: String = "",
    confirmText: String = "确定",
    dismissText: String = "取消",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    FoxInputDialog(
        title = title,
        label = label,
        initialValue = initialValue,
        placeholder = placeholder,
        confirmText = confirmText,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
