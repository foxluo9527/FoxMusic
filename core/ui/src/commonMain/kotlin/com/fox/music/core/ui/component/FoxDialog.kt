package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.fox.music.core.ui.theme.ErrorRedLight
import com.fox.music.core.ui.theme.FoxDisplayFont
import com.fox.music.core.ui.theme.InkQuaternary
import com.fox.music.core.ui.theme.InkTertiary
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.core.ui.theme.PaperVariant
import com.fox.music.core.ui.theme.foxEditorialAccent
import com.fox.music.core.ui.theme.foxHairline

// ── 统一容器规格 ──────────────────────────────────────────────────────────
// 20dp 圆角 / surface 实底 / 无投影 / 1px 发丝描边 / 最宽 320dp / 内边距 22dp。
// 家族里每一个组件都建在 [FoxDialogShell] 之上，不许再各自写一层容器——
// 之前就是因为各写各的，圆角 16~28dp、有无投影、按钮左右不一全都冒出来了。

internal val FoxDialogShape: RoundedCornerShape = RoundedCornerShape(20.dp)

/** 发丝线：深色下取设计稿的 #3A322A。 */
@Composable
internal fun foxDialogHairline(): Color = foxHairline()

/** 正文弱化色（ink3）：深色下 #A99F92 才有足够对比。 */
@Composable
internal fun foxDialogInkSecondary(): Color =
    if (isSystemInDarkTheme()) Color(0xFFA99F92) else InkTertiary

/** 占位/计税等极弱信息色（ink4）。 */
@Composable
internal fun foxDialogInkFaint(): Color =
    if (isSystemInDarkTheme()) Color(0xFF8A8175) else InkQuaternary

/**
 * 全App弹窗的唯一外壳。
 *
 * 刻意用 [BasicAlertDialog] 而不是 `AlertDialog`：后者自带 24dp 内边距和一套
 * title/text/confirmButton 的槽位布局，塞自定义内容时会多垫一层，撑不到设计稿的
 * 「22dp 内边距 + 最宽 320dp」。这里把容器交回自己手里，转场/遮罩行为仍由 M3 提供。
 *
 * 强调色统一取 [foxEditorialAccent]（即 colorScheme.primary），跟随用户主题，
 * **不许硬编码陶土 #B8513A**；容器色用实底 surface，**不许 copy(alpha=) 叠加**。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoxDialogShell(
    onDismissRequest: () -> Unit,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = dismissOnClickOutside,
            dismissOnBackPress = dismissOnBackPress,
        ),
        modifier = Modifier.widthIn(max = 320.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(FoxDialogShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = foxDialogHairline(), shape = FoxDialogShape)
                .padding(22.dp),
        ) {
            content()
        }
    }
}

// ── 排版块 ────────────────────────────────────────────────────────────────

/** 弹窗标题：Fraunces 衬线 18sp Bold，紧排 -0.4sp。 */
@Composable
internal fun FoxDialogTitle(
    text: String,
    centered: Boolean = false,
) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = FoxDisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            letterSpacing = (-0.4).sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 弹窗正文：13sp、行高 1.6（20.8sp），弱化色。 */
@Composable
internal fun FoxDialogBody(
    text: String,
    centered: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = TextStyle(fontSize = 13.sp, lineHeight = 20.8.sp),
        color = foxDialogInkSecondary(),
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = modifier.fillMaxWidth(),
    )
}

// ── 按钮 ──────────────────────────────────────────────────────────────────
// 主=accent 实心 / 次=ghost 描边 / 破坏=error 红。一律 12dp 圆角，触控区 ≥44dp。

@Composable
internal fun FoxPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val accent = foxEditorialAccent
    val fillColor = if (destructive) ErrorRedLight else accent
    FoxDialogButtonBox(
        text = text,
        textColor = Color.White,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        drawBackground = { Modifier.background(if (enabled) fillColor else fillColor.copy(alpha = 0.35f)) },
    )
}

@Composable
internal fun FoxGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FoxDialogButtonBox(
        text = text,
        textColor = MaterialTheme.colorScheme.onSurface,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        drawBackground = {
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = foxDialogHairline(), shape = FoxButtonShape)
        },
    )
}

private val FoxButtonShape: RoundedCornerShape = RoundedCornerShape(12.dp)

@Composable
private fun FoxDialogButtonBox(
    text: String,
    textColor: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    drawBackground: @Composable () -> Modifier,
) {
    Box(
        modifier = modifier
            .clip(FoxButtonShape)
            .then(drawBackground())
            .clickable(enabled = enabled, onClick = onClick)
            // 触控目标 ≥44dp：短文案按钮也要撑够高度
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 13.5.sp, lineHeight = 18.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) textColor else textColor.copy(alpha = 0.4f),
            maxLines = 1,
        )
    }
}

// ── ① FoxDialog：参数化确认组件（原 FoxDialog + FoxConfirmDialog 合并） ────

/**
 * 通用确认弹窗。两套旧 Sell 合并后的唯一入口：
 *
 * - **不传 [icon]** → 标准版式：标题左对齐，正文在下，按钮右下角排布（ghost 次 / accent 主）；
 * - **传 [icon]** → 图标居中版式：40dp 圆底 icon + 居中标题/正文 + 等宽双钮，
 *   对应设计稿「图标确认」，用于删除 / 清空这类"问一句再干"的场景。
 *
 * 破坏性操作传 [destructive] = true，主钮走 error 红 —— 光靠文案区分「确定 / 删除」
 * 不够，颜色必须先把风险传出去。
 *
 * @param icon 传了就走居中图标版式；null 走标准左对齐版式
 * @param cancelable false = 点遮罩与返回键都不关闭（强制更新这类场景用）
 */
@Composable
fun FoxDialog(
    title: String,
    text: String,
    confirmText: String = "确定",
    dismissText: String? = "取消",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    cancelable: Boolean = true,
    icon: ImageVector? = null,
    content: (@Composable () -> Unit)? = null,
) {
    FoxDialogShell(
        onDismissRequest = { if (cancelable) onDismiss() },
        dismissOnClickOutside = cancelable,
        dismissOnBackPress = cancelable,
    ) {
        if (icon != null) {
            FoxCenteredConfirmContent(
                title = title,
                text = text,
                confirmText = confirmText,
                dismissText = dismissText,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
                destructive = destructive,
                icon = icon,
            )
        } else {
            FoxStandardConfirmContent(
                title = title,
                text = text,
                confirmText = confirmText,
                dismissText = dismissText,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
                destructive = destructive,
                confirmEnabled = confirmEnabled,
                content = content,
            )
        }
    }
}

@Composable
private fun FoxStandardConfirmContent(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean,
    confirmEnabled: Boolean,
    content: (@Composable () -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FoxDialogTitle(title)
        if (text.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            FoxDialogBody(text)
        }
        if (content != null) {
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!dismissText.isNullOrBlank()) {
                FoxGhostButton(text = dismissText, onClick = onDismiss)
                Spacer(modifier = Modifier.size(8.dp))
            }
            FoxPrimaryButton(
                text = confirmText,
                enabled = confirmEnabled,
                destructive = destructive,
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun FoxCenteredConfirmContent(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean,
    icon: ImageVector,
) {
    val accent = foxEditorialAccent
    val resolvedTint = if (destructive) ErrorRedLight else accent
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                // 图标底板允许轻微 tint —— 装饰层不是容器，不违反"容器禁用 alpha 叠加"
                .background(resolvedTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = resolvedTint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        FoxDialogTitle(text = title, centered = true)
        if (text.isNotBlank()) {
            Spacer(modifier = Modifier.height(7.dp))
            FoxDialogBody(text = text, centered = true)
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!dismissText.isNullOrBlank()) {
                FoxGhostButton(text = dismissText, onClick = onDismiss, modifier = Modifier.weight(1f))
            }
            FoxPrimaryButton(
                text = confirmText,
                destructive = destructive,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 图标居中确认弹窗（设计稿「图标确认」）—— [FoxDialog] 的 [icon] 版式快捷写法。
 *
 * 保留这个函数名是为了不让已经调用它的地方全量改名；实现上是同一套参数化组件，
 * 不存在两份互不通用的实现了。
 */
@Composable
fun FoxConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
    icon: ImageVector = Icons.Default.HelpOutline,
) {
    FoxDialog(
        title = title,
        text = text,
        confirmText = confirmText,
        dismissText = dismissText,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        destructive = destructive,
        icon = icon,
    )
}

// ── ② 单选 / 多选列表 ─────────────────────────────────────────────────────

/**
 * 单选弹窗：选项列表 + 当前项 ✓ 标记。
 * 取代 SettingsScreen 原来那种「LazyColumn 塞 text 按钮」的选择器。
 */
@Composable
fun FoxChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = foxEditorialAccent
    FoxDialogShell(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FoxDialogTitle(title)
            Spacer(modifier = Modifier.height(14.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(options) { index, label ->
                    val selected = index == selectedIndex
                    val rowModifier = if (selected) {
                        // 选中底走语义 surfaceVariant：立体 cue 靠实色，不用 alpha 叠加
                        Modifier.background(MaterialTheme.colorScheme.surfaceVariant, FoxOptionShape)
                    } else {
                        Modifier
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(FoxOptionShape)
                                .then(rowModifier)
                                .clickable { onSelect(index) }
                                .padding(horizontal = 12.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                style = TextStyle(fontSize = 14.5.sp, lineHeight = 20.sp),
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Text(
                                    text = "✓",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                FoxGhostButton(text = "取消", onClick = onDismiss)
            }
        }
    }
}

private val FoxOptionShape: RoundedCornerShape = RoundedCornerShape(10.dp)

/**
 * 多选弹窗（新增）：方框勾选 + 确认。
 *
 * 与 [FoxChoiceDialog] 同构，只是每行左侧换成方框勾选（未选=发丝描边空心，
 * 已选=accent 实心 + 白 ✓），底部是「取消 / 确定」双钮——选择先在内部草稿态里改，
 * 点确定才回传，避免每勾一下就把外部状态冲掉。
 *
 * @param initialSelected 初始选中项下标
 * @param onConfirm 回传最终选中下标（升序、去重）
 */
@Composable
fun FoxMultiChoiceDialog(
    title: String,
    options: List<String>,
    initialSelected: List<Int> = emptyList(),
    confirmText: String = "确定",
    dismissText: String = "取消",
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit,
) {
    val accent = foxEditorialAccent
    val selected = remember {
        mutableStateListOf<Int>().also { it.addAll(initialSelected.distinct().sorted()) }
    }
    FoxDialogShell(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FoxDialogTitle(title)
            Spacer(modifier = Modifier.height(14.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(options) { index, label ->
                    val checked = selected.contains(index)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(FoxOptionShape)
                                .clickable {
                                    if (checked) selected.remove(index) else selected.add(index)
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FoxCheckSquare(checked = checked)
                            Spacer(modifier = Modifier.size(10.dp))
                            Text(
                                text = label,
                                style = TextStyle(fontSize = 14.5.sp, lineHeight = 20.sp),
                                fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (index != options.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(foxDialogHairline()),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "已选 ${selected.size} / ${options.size}",
                style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
                color = foxDialogInkFaint(),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FoxGhostButton(text = dismissText, onClick = onDismiss, modifier = Modifier.weight(1f))
                FoxPrimaryButton(
                    text = confirmText,
                    onClick = { onConfirm(selected.distinct().sorted()) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val FoxCheckSquareShape: RoundedCornerShape = RoundedCornerShape(5.dp)

/** 18dp 方框勾选：勾选态 accent 实心 + 白 ✓。 */
@Composable
private fun FoxCheckSquare(checked: Boolean) {
    val accent = foxEditorialAccent
    val shape = FoxCheckSquareShape
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(shape)
            .then(
                if (checked) {
                    Modifier.background(accent)
                } else {
                    Modifier.border(width = 1.dp, color = foxDialogInkFaint(), shape = shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(
                text = "✓",
                style = TextStyle(fontSize = 11.sp, lineHeight = 12.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

// ── ③ 输入类 ──────────────────────────────────────────────────────────────

/**
 * 单行输入弹窗：标签 + 单行输入 + 确认。
 *
 * `InputDialog.kt` 那份重复实现已经合并到这里。
 */
@Composable
fun FoxInputDialog(
    title: String,
    label: String,
    initialValue: String = "",
    placeholder: String = "",
    confirmText: String = "确定",
    /** 字数上限：非 null 时输入会被截断，并在输入框下方显示 已输入/上限 计数。 */
    maxLength: Int? = null,
    /** 外部禁用（例如保存中），与「内容非空」共同决定确认按钮是否可用。 */
    confirmEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    FoxDialogShell(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FoxDialogTitle(title)
            Spacer(modifier = Modifier.height(14.dp))
            FoxDialogTextField(
                value = text,
                onValueChange = { next ->
                    text = if (maxLength != null && next.length > maxLength) {
                        next.take(maxLength)
                    } else {
                        next
                    }
                },
                label = label,
                placeholder = placeholder,
                singleLine = true,
            )
            if (maxLength != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${text.length}/$maxLength",
                    style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
                    color = foxDialogInkFaint(),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FoxGhostButton(text = "取消", onClick = onDismiss, enabled = confirmEnabled)
                Spacer(modifier = Modifier.size(8.dp))
                FoxPrimaryButton(
                    text = confirmText,
                    enabled = confirmEnabled && text.isNotBlank(),
                    onClick = { onConfirm(text.trim()) },
                )
            }
        }
    }
}

/**
 * 多行文本域弹窗（新增）：替换原来 MusicCommentBottomSheet 里那个手写 AlertDialog。
 *
 * 带字数上限与计数（超限计数变红 + 不可确认），高度默认 96dp（约 4 行），
 * 适用于评论、回复、好友备注这类"要写一段话"的场景。
 */
@Composable
fun FoxTextAreaDialog(
    title: String,
    initialValue: String = "",
    placeholder: String = "",
    maxLength: Int = 200,
    areaHeight: Dp = 96.dp,
    confirmText: String = "发布",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    val trimmed = text.trim()
    val overflow = trimmed.length > maxLength
    FoxDialogShell(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FoxDialogTitle(title)
            Spacer(modifier = Modifier.height(14.dp))
            FoxDialogTextField(
                value = text,
                onValueChange = { text = it },
                label = null,
                placeholder = placeholder,
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(areaHeight),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${trimmed.length} / $maxLength",
                style = TextStyle(fontSize = 10.5.sp, lineHeight = 14.sp),
                color = if (overflow) ErrorRedLight else foxDialogInkFaint(),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FoxGhostButton(text = "取消", onClick = onDismiss)
                Spacer(modifier = Modifier.size(8.dp))
                FoxPrimaryButton(
                    text = confirmText,
                    enabled = trimmed.isNotBlank() && !overflow,
                    onClick = { onConfirm(trimmed) },
                )
            }
        }
    }
}

/** 多字段输入的一个字段描述。 */
data class FoxDialogField(
    val label: String,
    val initialValue: String = "",
    val placeholder: String = "",
    val singleLine: Boolean = true,
)

/**
 * 多字段输入弹窗（新增）：≥2 个输入框。
 *
 * 收编搜索条件编辑这类场景（SourceCorrectScreen:523 / PlaylistImportDetailScreen:624），
 * 原来每处都手写一个 AlertDialog 塞两个 OutlinedTextField。
 *
 * @param requireAllFields true = 所有字段都非空才可确认；false = 允许留空（搜索条件通常只要填一个）
 * @param onConfirm 按 [fields] 顺序回传每个字段的值
 */
@Composable
fun FoxMultiInputDialog(
    title: String,
    fields: List<FoxDialogField>,
    confirmText: String = "确定",
    dismissText: String = "取消",
    requireAllFields: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val values = remember(fields) { mutableStateListOf<String>().also { it.addAll(fields.map { it.initialValue }) } }
    val canConfirm = if (requireAllFields) values.all { it.isNotBlank() } else values.any { it.isNotBlank() }
    FoxDialogShell(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FoxDialogTitle(title)
            Spacer(modifier = Modifier.height(14.dp))
            fields.forEachIndexed { index, field ->
                FoxDialogTextField(
                    value = values[index],
                    onValueChange = { values[index] = it },
                    label = field.label,
                    placeholder = field.placeholder,
                    singleLine = field.singleLine,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FoxGhostButton(text = dismissText, onClick = onDismiss)
                Spacer(modifier = Modifier.size(8.dp))
                FoxPrimaryButton(
                    text = confirmText,
                    enabled = canConfirm,
                    onClick = { onConfirm(values.map { it.trim() }) },
                )
            }
        }
    }
}

/** 弹窗内的输入框：12dp 圆角、发丝描边、聚焦 accent。 */
@Composable
private fun FoxDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    placeholder: String,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = foxEditorialAccent
    val hairline = foxDialogHairline()
    val shape = RoundedCornerShape(12.dp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = if (label.isNullOrBlank()) null else ({ Text(label) }),
        placeholder = if (placeholder.isBlank()) null else ({ Text(placeholder) }),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        textStyle = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp),
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = hairline,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

// ── ④ 长文 / 进度 / 加载 ──────────────────────────────────────────────────

/**
 * 长文告知弹窗（新增）：正文可滚动 + 单个主钮。
 *
 * 收编帮助说明 / 公告正文这类"字多但不重要"的场景（LyricHelpDialog /
 * CarLyricHelpDialog / NotificationCategoryContent），原来它们是用 AlertDialog
 * 直接把长文本塞进 text 槽，超长就被裁掉。
 */
@Composable
fun FoxInfoDialog(
    title: String,
    text: String,
    confirmText: String = "我知道了",
    onDismiss: () -> Unit,
) {
    FoxDialogShell(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FoxDialogTitle(title)
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                FoxDialogBody(text)
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                FoxPrimaryButton(text = confirmText, onClick = onDismiss)
            }
        }
    }
}

/**
 * 进度弹窗（新增）：确定百分比 + 默认不可取消 + 错误重试。
 *
 * 收编版本升级下载（UpdateDialog）与歌单导入进度。
 *
 * @param progress 0f~1f；传 null 表示不确定进度，退化为一颗转圈
 * @param errorMessage 非空时切到错误态：正文换成错误色，并给一颗「重试」
 * @param onRetry 错误态下的重试回调
 * @param onDismiss 传 null 表示强制、不可取消（点外面与返回键都不关）
 */
@Composable
fun FoxProgressDialog(
    title: String,
    text: String = "",
    progress: Float? = null,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val accent = foxEditorialAccent
    val cancelable = onDismiss != null
    FoxDialogShell(
        onDismissRequest = { onDismiss?.invoke() },
        dismissOnClickOutside = cancelable,
        dismissOnBackPress = cancelable,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FoxDialogTitle(text = title, centered = true)
            val body = errorMessage ?: text
            if (body.isNotBlank()) {
                Spacer(modifier = Modifier.height(7.dp))
                FoxDialogBody(text = body, centered = true)
            }
            Spacer(modifier = Modifier.height(14.dp))
            if (progress != null) {
                val fraction = progress.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(PaperVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(accent),
                    )
                }
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
                    color = foxDialogInkFaint(),
                    modifier = Modifier.padding(top = 7.dp),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = accent,
                )
            }
            if (errorMessage != null && onRetry != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    FoxPrimaryButton(text = "重试", onClick = onRetry, modifier = Modifier.weight(1f))
                }
            } else if (cancelable) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FoxGhostButton(text = "取消", onClick = { onDismiss?.invoke() })
                }
            } else {
                // 不可取消时给一句提示，避免用户以为卡死
                Text(
                    text = "请勿退出",
                    style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
                    color = foxDialogInkFaint(),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/**
 * 加载中弹窗：转圈 + 文案（用于清理缓存等「点了要等一会」的操作）。
 */
@Composable
fun FoxLoadingDialog(text: String) {
    val accent = foxEditorialAccent
    FoxDialogShell(
        onDismissRequest = {},
        dismissOnClickOutside = false,
        dismissOnBackPress = false,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = accent,
            )
            Spacer(modifier = Modifier.size(14.dp))
            Text(
                text = text,
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                color = foxDialogInkSecondary(),
            )
        }
    }
}

// ── ⑤ 三按钮 / 双主钮 ─────────────────────────────────────────────────────

/**
 * 三按钮弹窗（新增）：主 / 次 / 取消，右对齐排布。
 * 收编后台权限引导这类"要不要去设置、还是以后再说"的场景。
 */
@Composable
fun FoxTripleActionDialog(
    title: String,
    text: String = "",
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit,
    tertiaryText: String = "取消",
    onTertiary: () -> Unit,
    onDismiss: () -> Unit,
) {
    FoxDialogShell(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FoxDialogTitle(title)
            if (text.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                FoxDialogBody(text)
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FoxGhostButton(text = tertiaryText, onClick = onTertiary)
                Spacer(modifier = Modifier.size(8.dp))
                FoxGhostButton(text = secondaryText, onClick = onSecondary)
                Spacer(modifier = Modifier.size(8.dp))
                FoxPrimaryButton(text = primaryText, onClick = onPrimary)
            }
        }
    }
}

/**
 * 双主钮二选一弹窗（新增）：两个等宽主钮并列，下方右对齐一个 ghost 取消。
 * 收编「收录到平台 / 收录到歌单」这类必须让用户先做一次选择的场景。
 */
@Composable
fun FoxDualPrimaryDialog(
    title: String,
    text: String = "",
    firstText: String,
    onFirst: () -> Unit,
    secondText: String,
    onSecond: () -> Unit,
    dismissText: String = "取消",
    onDismiss: () -> Unit,
) {
    FoxDialogShell(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FoxDialogTitle(title)
            if (text.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                FoxDialogBody(text)
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FoxPrimaryButton(text = firstText, onClick = onFirst, modifier = Modifier.weight(1f))
                FoxPrimaryButton(text = secondText, onClick = onSecond, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                FoxGhostButton(text = dismissText, onClick = onDismiss)
            }
        }
    }
}
