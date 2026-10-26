package com.fox.music.feature.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.ui.component.FoxColorPicker
import com.fox.music.core.ui.component.FoxColorPickerPresets
import com.fox.music.core.ui.component.FoxEditorialRow
import com.fox.music.core.ui.component.FoxSheet
import com.fox.music.core.ui.component.FoxSlider
import com.fox.music.core.ui.component.FoxSwitch
import com.fox.music.core.ui.theme.FoxEditorial
import com.fox.music.core.ui.theme.InkQuaternary
import com.fox.music.core.ui.theme.InkTertiary
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.feature.player.lyric.data.LyricCustomColors
import com.fox.music.feature.player.lyric.data.LyricPalette
import com.fox.music.feature.player.lyric.data.LyricPaletteColors
import com.fox.music.feature.player.lyric.data.LyricPalettes
import com.fox.music.feature.player.lyric.data.resolve
import com.fox.music.feature.player.lyric.manager.LyricStyleManager
import com.fox.music.feature.player.lyric.manager.LyricSyncManager
import kotlin.math.roundToInt

/** 字号滑杆范围：低于 16 在歌词页基本读不出来，高于 34 会顶掉整屏。 */
private const val FONT_SIZE_MIN = 16f
private const val FONT_SIZE_MAX = 34f

/** 重置时的字号（与 [LyricStyle] 默认值一致）。 */
private const val FONT_SIZE_DEFAULT = 18

/**
 * 歌词设置 BottomSheet（设计稿② / v12 验收返工）。
 *
 * 旧实现是 [LyricSettings] —— 一块 XML binding 面板直接叠在歌词上，既挡内容
 * 又和全站的纸感语言对不上。改版后走标准底部弹层：22dp 顶圆角 + drag handle +
 * 语义 surface 底，内容包括 8 组精选配色方案 / 字号整宽区块 / 翻译与桌面歌词开关 /
 * 歌词纠正入口 / 自定义配色（二级页，取色盘）。
 *
 * **应用 / 取消语义（v12）**：进入时把 [LyricStyleManager] 的当前值快照成草稿
 * （[LyricSettingsDraft]），所有控件只改草稿；点「应用」才通过
 * [LyricStyleManager.applyDraft] 一次性落盘，点「取消」/ 侧滑关闭直接丢弃草稿。
 * 弹层内**不允许**再出现任何 `styleManager.setXxx()` 调用。
 *
 * 桌面歌词那条路径仍然用旧的 XML 面板（DesktopLyricContainer 依赖它），这里不动。
 *
 * @param onDismiss 收起弹层
 * @param onLyricCorrectClick 「歌词纠正」入口（跳自定义歌词选择）
 */
// FoxSheet 的 sheetState 参数把实验性的 SheetState 带进了它的签名，调用侧需要显式 opt-in。
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricSettingsSheet(
    onDismiss: () -> Unit,
    onLyricCorrectClick: () -> Unit = {},
) {
    val styleManager = remember { LyricStyleManager.getInstance() }
    val syncManager = remember { LyricSyncManager.getInstance() }
    val context = LocalContext.current

    var showCustomPage by remember { mutableStateOf(false) }

    // 进二级页时的草稿快照：页头 ‹ 返回 = 撤销本页改的配色后回主页面（字号等其它项保留），
    // 底部「取消」= 整层丢弃（与主页面取消、侧滑关闭同语义）。
    var customEntryDraft by remember { mutableStateOf<LyricSettingsDraft?>(null) }

    // 草稿只在这里初始化一次（remember 无 key → 弹层每次进入都是新快照）。
    // 之后所有控件都只改它，绝不直接写 styleManager。
    var draft by remember {
        mutableStateOf(
            LyricSettingsDraft(
                fontSize = styleManager.currentStyle.fontSize,
                showTranslation = styleManager.currentStyle.showTranslation,
                desktopLyric = syncManager.isDesktopLyricEnabled.value,
                paletteId = styleManager.paletteId,
                custom = styleManager.customColors,
            )
        )
    }

    // 「应用」两条路径（主页面 / 自定义配色页底部）共用同一份落盘逻辑，
    // 保证二级页点应用和主页面点应用效果完全一致（含桌面歌词的系统副作用）。
    val applyAll: () -> Unit = {
        styleManager.applyDraft(
            fontSize = draft.fontSize,
            showTranslation = draft.showTranslation,
            paletteId = draft.paletteId,
            custom = draft.custom,
        )
        // 桌面歌词是系统级副作用（要悬浮窗权限），只在这里执行一次。
        if (draft.desktopLyric != syncManager.isDesktopLyricEnabled.value) {
            if (draft.desktopLyric) {
                syncManager.openDesktopLyric(context) {
                    syncManager.enableDesktopLyric()
                }
            } else {
                syncManager.disableDesktopLyric()
            }
        }
        onDismiss()
    }

    // 统一走 FoxSheet：22dp 顶圆角 + drag handle + 语义 surface 底。
    FoxSheet(onDismissRequest = onDismiss) {
        // 内容比屏幕高时可滚动（小屏 / 大字号下不会被裁掉最后几行）。
        // 注意：这里**不加** horizontal padding —— FoxEditorialRow 自带 20dp 页面边距，
        // 再叠一层会变成 40dp；非编辑行的块（预览卡 / thumbnail 条 / 按钮）各自加。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
        ) {
            if (showCustomPage) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    LyricCustomColorPage(
                        draft = draft,
                        onDraftChange = { draft = it },
                        // 恢复默认只作用于配色：方案回「跟随主题」+ 清空自定义覆盖，
                        // 字号 / 翻译 / 桌面歌词都不动。
                        onReset = {
                            draft = draft.copy(
                                paletteId = LyricPalettes.default.id,
                                custom = LyricCustomColors(),
                            )
                        },
                        onCancel = onDismiss,
                        onApply = applyAll,
                        onBack = {
                            customEntryDraft?.let { draft = it }
                            showCustomPage = false
                        },
                    )
                }
            } else {
                LyricSettingsMainPage(
                    draft = draft,
                    onDraftChange = { draft = it },
                    onReset = {
                        // 只重置草稿：字号 / 翻译 / 方案 / 自定义色，桌面歌词开关不动。
                        draft = draft.copy(
                            fontSize = FONT_SIZE_DEFAULT,
                            showTranslation = true,
                            paletteId = LyricPalettes.default.id,
                            custom = LyricCustomColors(),
                        )
                    },
                    onLyricCorrectClick = onLyricCorrectClick,
                    onCustomColorClick = {
                        customEntryDraft = draft
                        showCustomPage = true
                    },
                    onCancel = onDismiss,
                    onApply = applyAll,
                )
            }
        }
    }
}

@Composable
private fun LyricSettingsMainPage(
    draft: LyricSettingsDraft,
    onDraftChange: (LyricSettingsDraft) -> Unit,
    onReset: () -> Unit,
    onLyricCorrectClick: () -> Unit,
    onCustomColorClick: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    val themeAccent = MaterialTheme.colorScheme.primary

    // 预览与缩略图全部读草稿，实时跟随拖动/选择。
    val colors = remember(draft.paletteId, draft.custom, themeAccent) {
        LyricPalettes.byId(draft.paletteId).resolve(themeAccent.toArgb(), draft.custom)
    }

    Column {
        // 头部（设计稿②）：眼标 LYRICS + 衬线 19sp「歌词」+ 右侧「重置」
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LYRICS",
                    style = FoxEditorial.kicker.copy(
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp,
                        letterSpacing = 2.6.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = themeAccent,
                )
                Text(
                    text = "歌词",
                    style = FoxEditorial.mastheadTitle.copy(
                        fontSize = 21.sp,
                        lineHeight = 24.sp,
                        letterSpacing = (-0.4).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text(
                text = "重置",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                ),
                color = foxInkMuted(),
                modifier = Modifier.clickable(onClick = onReset),
            )
        }

        // 实拍预览（设计稿②）：占满宽的大卡，底色统一走语义 surface ——
        // 背景跟随应用主题，不再展示方案自带的渐变底（那是 v12 被驳回的逻辑错误）。
        LyricPreviewCard(
            colors = colors,
            fontSize = draft.fontSize,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(12.dp))

        // 方案 thumbnail 条：96×62，卡内两个「词」迷你预览（高亮 + 普通），
        // 选中 2dp accent 描边。整条横向可滑，一眼扫过 8 组方案。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            LyricPalettes.all.forEach { palette ->
                LyricPaletteThumb(
                    palette = palette,
                    // 「跟随主题」方案的高亮色实时取主题强调色，缩略图才不会和大卡对不上。
                    highlightColor = if (palette.followTheme) {
                        themeAccent
                    } else {
                        Color(palette.highlightColor)
                    },
                    selected = palette.id == draft.paletteId,
                    onClick = {
                        // 选方案即整体换肤：和 LyricStyleManager.setPaletteId 同语义，
                        // 顺手清掉草稿里的自定义覆盖，否则预览会「切了方案但颜色不动」。
                        onDraftChange(
                            draft.copy(
                                paletteId = palette.id,
                                custom = LyricCustomColors(),
                            )
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(thickness = 1.dp, color = foxHairline())

        // 字号：独立整宽区块（头部读数 + A−/滑杆/A+ + 刻度）。
        // v12 之前把 Slider 塞进编辑行 trailing，和 A−/A+ 挤在 120dp 里被裁，
        // 这就是「拖动栏展示异常」的根因 —— 这里不再用编辑行。
        FontSizeBlock(
            fontSize = draft.fontSize,
            onFontSizeChange = { onDraftChange(draft.copy(fontSize = it)) },
        )

        HorizontalDivider(thickness = 1.dp, color = foxHairline())

        FoxEditorialRow(
            title = "歌词翻译",
            trailing = {
                FoxSwitch(
                    checked = draft.showTranslation,
                    onCheckedChange = { onDraftChange(draft.copy(showTranslation = it)) },
                )
            },
        )
        FoxEditorialRow(
            title = "桌面歌词",
            trailing = {
                FoxSwitch(
                    checked = draft.desktopLyric,
                    onCheckedChange = { onDraftChange(draft.copy(desktopLyric = it)) },
                )
            },
        )
        FoxEditorialRow(
            title = "歌词纠正",
            onClick = onLyricCorrectClick,
            trailing = { ChevronText() },
        )
        FoxEditorialRow(
            title = "自定义配色",
            onClick = onCustomColorClick,
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SwatchDot(Color(colors.normal), null)
                    SwatchDot(Color(colors.highlight), null)
                }
                Spacer(Modifier.width(9.dp))
                ChevronText()
            },
        )

        Spacer(Modifier.height(14.dp))

        // 应用 / 取消：两个等宽按钮。取消 = 丢弃草稿（从未写回过，天然回滚）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(width = 1.dp, color = foxHairline(), shape = RoundedCornerShape(12.dp))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = foxInkMuted(),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onApply),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "应用",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * 字号区块（v13）：**单行** —— 左「字号」标签，右 A− / 滑杆 / A+。
 *
 * 去掉行尾「24 sp」读数与 16/22/28/34 刻度行：滑杆本身就能感知大小，多一行数字只
 * 让这一块显得笨重；标签已说明调的是什么，具体数值属于「能看见效果还非要读一遍」的
 * 冗余信息。
 */
@Composable
private fun FontSizeBlock(
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "字号",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(4.dp))
        FontStepButton(
            label = "A−",
            enabled = fontSize > FONT_SIZE_MIN.toInt(),
            onClick = { onFontSizeChange((fontSize - 1).coerceAtLeast(FONT_SIZE_MIN.toInt())) },
        )
        FoxSlider(
            value = fontSize.coerceIn(
                FONT_SIZE_MIN.toInt(),
                FONT_SIZE_MAX.toInt(),
            ).toFloat(),
            onValueChange = { onFontSizeChange(it.roundToInt()) },
            valueRange = FONT_SIZE_MIN..FONT_SIZE_MAX,
            // 字号必须是整数档，连续拖会得到 23.7sp 这种值
            step = 1f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        FontStepButton(
            label = "A+",
            enabled = fontSize < FONT_SIZE_MAX.toInt(),
            onClick = { onFontSizeChange((fontSize + 1).coerceAtMost(FONT_SIZE_MAX.toInt())) },
        )
    }
}

/**
 * A− / A+ 步进按钮。
 *
 * 视觉是 32dp 方形描边，但**触控盒是 44dp** —— 32dp 直接可点会低于无障碍最小触控目标。
 */
@Composable
private fun FontStepButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = foxHairline(), shape = RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                ),
                fontWeight = if (label == "A+") FontWeight.SemiBold else FontWeight.Normal,
                color = if (enabled) foxInkMuted() else foxInkFaint(),
            )
        }
    }
}

/** 编辑行右侧的 › 指示（不用图标字体，走普通文本避免 emoji 化）。 */
@Composable
private fun ChevronText() {
    Text(
        text = "›",
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 16.sp,
            lineHeight = 18.sp,
        ),
        color = foxInkFaint(),
    )
}

/**
 * 实拍预览大卡（设计稿②）：占满宽圆角卡，底色固定为语义 surface（背景跟随应用主题），
 * 只靠 1dp 发丝线收边；居中三行歌词 —— 普通 / 高亮 / 普通。
 *
 * 字号按 [fontSize] 等比缩放，拖动滑杆能立刻看到大小变化。
 *
 * @param colors 草稿解析出的三通道色
 */
@Composable
private fun LyricPreviewCard(
    colors: LyricPaletteColors,
    fontSize: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val normalSize = (fontSize * 0.72f).coerceIn(11f, 24f)
    val highlightSize = (normalSize * 1.15f).coerceIn(12f, 28f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(width = 1.dp, color = foxHairline(), shape = shape)
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        PreviewLine(
            text = "沿着晚风走了很久",
            color = Color(colors.normal),
            fontSize = normalSize.sp,
            bold = false,
        )
        PreviewLine(
            text = "把心事都交给霓虹",
            color = Color(colors.highlight),
            fontSize = highlightSize.sp,
            bold = true,
        )
        PreviewLine(
            text = "城市在身后慢慢安静",
            color = Color(colors.normal),
            fontSize = normalSize.sp,
            bold = false,
        )
    }
}

// ── 深色感知的语义色（v10 验收返工） ─────────────────────────────────────
/** 发丝线：深色下取设计稿的 #3A322A，浅色取 PaperHairline #E4DDD1。 */
@Composable
private fun foxHairline(): Color =
    if (isSystemInDarkTheme()) Color(0xFF3A322A) else PaperHairline

/**
 * 次级文字（眼标 / A−A+ / 元信息）色。
 *
 * 深色下上修到 #A99F92：设计稿深色 ink4 = #7E7568 在 #17140F 上只有 4.05:1，
 * 不过 WCAG AA；#A99F92 是 7.0:1。**这是对设计稿做的可访问性上修**，不是风格改动。
 */
@Composable
private fun foxInkMuted(): Color =
    if (isSystemInDarkTheme()) Color(0xFFA99F92) else InkTertiary

/** 占位级文字（› 指示）：深色下 #8A8175（≈5.3:1）。 */
@Composable
private fun foxInkFaint(): Color =
    if (isSystemInDarkTheme()) Color(0xFF8A8175) else InkQuaternary

@Composable
private fun PreviewLine(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    bold: Boolean,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = fontSize,
            lineHeight = fontSize * 2.05f,
        ),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
    )
}

/**
 * 方案 thumbnail（设计稿② / v12）：52dp 方形小卡 + 下方方案名。
 *
 * 底色同样是语义 surface + 发丝线，卡内只放两个「词」（高亮色 15sp 粗 / 普通色 9sp 细），
 * 选中时换成 2dp 强调色描边、方案名同步转强调色 —— 一眼看出点名了哪一组。
 *
 * 不再展示方案自带渐变底：设置页看到的底就是应用主题的底，所见即所得。
 */
@Composable
private fun LyricPaletteThumb(
    palette: LyricPalette,
    highlightColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val tileShape = RoundedCornerShape(13.dp)
    Column(
        modifier = Modifier
            .width(52.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(tileShape)
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (selected) {
                        Modifier.border(width = 2.dp, color = accent, shape = tileShape)
                    } else {
                        Modifier.border(width = 1.dp, color = foxHairline(), shape = tileShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "词",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = highlightColor,
                    maxLines = 1,
                )
                Text(
                    text = "词",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                    ),
                    color = Color(palette.normalColor),
                    maxLines = 1,
                )
            }
        }
        Text(
            text = palette.name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
            ),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else InkQuaternary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun SwatchDot(color: Color, borderColor: Color?) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (borderColor != null) {
                    Modifier.border(width = 1.dp, color = borderColor, shape = CircleShape)
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * 自定义配色二级页（设计稿③ / v12 二轮返工）。
 *
 * 一条竖向动线：**刊头 → 实时预览 → 普通词/高亮词分段控件 → 取色盘 → 取消/应用**。
 *
 * 为什么是分段控件而不是两行通道：v12 第一版做成「高亮·当前行 / 普通·其他行」两行，
 * 每行右侧挂一个色点，用户看不出「我现在在改哪一种」，实际点预设点也一直是改高亮色。
 * 分段控件把「当前编辑对象」做成唯一显式状态，取色盘、预设点、HEX 读数全部只服务于它。
 *
 * 背景通道整体删除（背景跟随应用主题），[LyricCustomColors] 的 background 字段不再有新写入。
 *
 * @param onBack 页头 ‹ —— 撤销本页配色改动并回主页面（字号等其它草稿项保留）
 * @param onCancel 底部取消 —— 丢弃整层草稿并关闭弹层
 * @param onApply 底部应用 —— 整份草稿落盘（与主页面「应用」同一路径）
 * @param onReset 页头「恢复默认」—— 方案回「跟随主题」+ 清空自定义覆盖
 */
@Composable
private fun LyricCustomColorPage(
    draft: LyricSettingsDraft,
    onDraftChange: (LyricSettingsDraft) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit,
) {
    val themeAccent = MaterialTheme.colorScheme.primary
    // 设计稿默认停在「普通词」：普通行字最多，先调它更符合直觉。
    var activeChannel by remember { mutableStateOf(LyricColorChannel.NORMAL) }

    val colors = remember(draft.paletteId, draft.custom, themeAccent) {
        LyricPalettes.byId(draft.paletteId).resolve(themeAccent.toArgb(), draft.custom)
    }
    val activeArgb = when (activeChannel) {
        LyricColorChannel.HIGHLIGHT -> colors.highlight
        LyricColorChannel.NORMAL -> colors.normal
    }
    // 预览字号与主页面预览同一套换算，两页看到的大小一致。
    val normalSize = (draft.fontSize * 0.72f).coerceIn(11f, 24f)
    val highlightSize = (normalSize * 1.15f).coerceIn(12f, 28f)

    Column {
        // ── 刊头：‹ + CUSTOM / 自定义配色 + 恢复默认 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f).padding(start = 2.dp)) {
                Text(
                    text = "CUSTOM",
                    style = FoxEditorial.kicker.copy(
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp,
                        letterSpacing = 2.6.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = themeAccent,
                )
                Text(
                    text = "自定义配色",
                    style = FoxEditorial.mastheadTitle.copy(
                        fontSize = 19.sp,
                        lineHeight = 22.sp,
                        letterSpacing = (-0.6).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text(
                text = "恢复默认",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                ),
                color = foxInkMuted(),
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable(onClick = onReset),
            )
        }

        // ── 实时预览：底固定语义 surface（背景跟随应用主题），只让字色变化 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = foxHairline(), shape = RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            PreviewLine(
                text = "沿着晚风走了很久",
                color = Color(colors.normal),
                fontSize = normalSize.sp,
                bold = false,
            )
            PreviewLine(
                text = "把心事都交给霓虹",
                color = Color(colors.highlight),
                fontSize = highlightSize.sp,
                bold = true,
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── 通道分段控件：唯一决定「取色盘改谁」的状态 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LyricColorChannel.entries.forEach { channel ->
                val selected = channel == activeChannel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
                        )
                        .clickable { activeChannel = channel },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = channel.title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp,
                        ),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            foxInkMuted()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── 取色盘：面板 / 色相条 / 预设点全部按 activeChannel 分流 ──
        FoxColorPicker(
            color = Color(activeArgb),
            onColorChange = { picked ->
                val argb = picked.toArgb()
                val nextCustom = when (activeChannel) {
                    LyricColorChannel.HIGHLIGHT -> draft.custom.copy(highlight = argb)
                    LyricColorChannel.NORMAL -> draft.custom.copy(normal = argb)
                }
                onDraftChange(draft.copy(custom = nextCustom))
            },
            caption = when (activeChannel) {
                LyricColorChannel.HIGHLIGHT -> "已应用到「高亮词」"
                LyricColorChannel.NORMAL -> "已应用到「普通词」"
            },
            presets = FoxColorPickerPresets,
            // 设计稿：预置颜色在上，自定义色盘在下（点预设比拖面板更快）
            presetsOnTop = true,
        )

        Spacer(Modifier.height(16.dp))

        // ── 取消 / 应用（设计稿：40dp 高、12dp 圆角、13.5sp SemiBold） ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(width = 1.dp, color = foxHairline(), shape = RoundedCornerShape(12.dp))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = foxInkMuted(),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onApply),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "应用",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** 取色通道：只剩「普通词」与「高亮词」；背景不再可调（跟随应用主题）。 */
private enum class LyricColorChannel(val title: String) {
    NORMAL("普通词"),
    HIGHLIGHT("高亮词"),
}

/**
 * 设置弹层的草稿快照（v12）。
 *
 * 弹层打开时从 [LyricStyleManager] 抄一份，控件只读改写这里；「应用」时整份交给
 * [LyricStyleManager.applyDraft]，「取消」直接丢弃。桌面歌词开关也进草稿 ——
 * 它原来一点就调 `syncManager.disableDesktopLyric()`，同样属于「没点应用就保存」。
 */
private data class LyricSettingsDraft(
    val fontSize: Int,
    val showTranslation: Boolean,
    val desktopLyric: Boolean,
    val paletteId: String,
    val custom: LyricCustomColors,
)
