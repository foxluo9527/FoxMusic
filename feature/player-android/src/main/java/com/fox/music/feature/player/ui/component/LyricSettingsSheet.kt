package com.fox.music.feature.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.ui.component.FoxEditorialRow
import com.fox.music.core.ui.theme.FoxEditorial
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.core.ui.theme.PaperSurface
import com.fox.music.core.ui.theme.PaperVariant
import com.fox.music.core.ui.theme.InkQuaternary
import com.fox.music.core.ui.theme.InkTertiary
import com.fox.music.feature.player.lyric.data.LyricCustomColors
import com.fox.music.feature.player.lyric.data.LyricPalette
import com.fox.music.feature.player.lyric.data.LyricPaletteColors
import com.fox.music.feature.player.lyric.data.LyricPalettes
import com.fox.music.feature.player.lyric.data.resolve
import com.fox.music.feature.player.lyric.manager.LyricStyleManager
import com.fox.music.feature.player.lyric.manager.LyricSyncManager

/** 字号滑杆范围：低于 12 在歌词页基本不可读，高于 32 会顶掉整屏。 */
private const val FONT_SIZE_MIN = 12f
private const val FONT_SIZE_MAX = 32f

private val SheetTopShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
private val HandleColor = Color(0xFFD5CEC2)

/**
 * 歌词设置 BottomSheet（设计稿②）。
 *
 * 旧实现是 [LyricSettings] —— 一块 XML binding 面板直接叠在歌词上，既挡内容
 * 又和全站的纸感语言对不上。改版后走标准底部弹层：20dp 顶圆角 + drag handle +
 * PaperSurface 底，内容包括 8 组精选配色方案 / 字号滑杆 / 翻译与桌面歌词开关 /
 * 歌词纠正入口 / 自定义配色（二级页，支持 HEX）。
 *
 * 桌面歌词那条路径仍然用旧的 XML 面板（DesktopLyricContainer 依赖它），这里不动。
 *
 * @param onDismiss 收起弹层
 * @param onLyricCorrectClick 「歌词纠正」入口（跳自定义歌词选择）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricSettingsSheet(
    onDismiss: () -> Unit,
    onLyricCorrectClick: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val styleManager = remember { LyricStyleManager.getInstance() }
    val syncManager = remember { LyricSyncManager.getInstance() }
    val context = LocalContext.current

    var showCustomPage by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetTopShape,
        containerColor = PaperSurface,
        dragHandle = { FoxSheetDragHandle() },
    ) {
        // 内容比屏幕高时可滚动（小屏 / 大字号下不会被裁掉最后几行）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp),
        ) {
            if (showCustomPage) {
                LyricCustomColorPage(
                    styleManager = styleManager,
                    onBack = { showCustomPage = false },
                    onFinish = { showCustomPage = false },
                )
            } else {
                LyricSettingsMainPage(
                    styleManager = styleManager,
                    syncManager = syncManager,
                    context = context,
                    onReset = {
                        styleManager.resetToDefault()
                    },
                    onLyricCorrectClick = onLyricCorrectClick,
                    onCustomColorClick = { showCustomPage = true },
                    onFinish = onDismiss,
                )
            }
        }
    }
}

/** 32×4 圆角 drag handle（与全局 sheet 语言一致）。 */
@Composable
private fun FoxSheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(CircleShape)
                .background(HandleColor),
        )
    }
}

@Composable
private fun LyricSettingsMainPage(
    styleManager: LyricStyleManager,
    syncManager: LyricSyncManager,
    context: android.content.Context,
    onReset: () -> Unit,
    onLyricCorrectClick: () -> Unit,
    onCustomColorClick: () -> Unit,
    onFinish: () -> Unit,
) {
    val style by styleManager.styleFlow.collectAsState()
    val paletteTick by styleManager.paletteTick.collectAsState()
    val isDesktopLyricEnabled by syncManager.isDesktopLyricEnabled.collectAsState()
    val themeAccent = MaterialTheme.colorScheme.primary

    // paletteTick 参与计算：配色方案变了要重新解析出新的三色
    val paletteColors = remember(paletteTick, themeAccent) {
        styleManager.resolvePaletteColors(themeAccent.toArgb())
    }
    val selectedPaletteId = remember(paletteTick) { styleManager.paletteId }

    Column {
        // 头部（设计稿②）：眼标 LYRICS + 衬线 19sp「歌词」+ 右侧「重置」
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LYRICS",
                    style = FoxEditorial.kicker.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
                    color = InkQuaternary,
                )
                Text(
                    text = "歌词",
                    style = FoxEditorial.mastheadTitle.copy(
                        fontSize = 19.sp,
                        lineHeight = 22.sp,
                        letterSpacing = (-0.6).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = "重置",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onReset),
            )
        }

        // 实拍预览（设计稿②）：占满宽的大卡，用当前方案渐变底 + 真实字号渲染三行歌词
        LyricPreviewCard(colors = paletteColors)

        Spacer(Modifier.height(12.dp))

        // 方案 thumbnail 条：96×62，卡内两行迷你预览（普通 + 高亮），选中 2dp accent 描边
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            LyricPalettes.all.forEach { palette ->
                LyricPaletteThumb(
                    palette = palette,
                    selected = palette.id == selectedPaletteId,
                    onClick = { styleManager.setPaletteId(palette.id) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(thickness = 1.dp, color = PaperHairline)

        // 字号：A− / 滑杆 / A+（无容器编辑行）
        FoxEditorialRow(
            title = "字号",
            trailing = {
                Text(
                    text = "A−",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                    color = InkTertiary,
                    modifier = Modifier.clickable {
                        styleManager.setFontSize(style.fontSize - 1)
                    },
                )
                Spacer(Modifier.width(9.dp))
                Slider(
                    value = style.fontSize.coerceIn(
                        FONT_SIZE_MIN.toInt(),
                        FONT_SIZE_MAX.toInt(),
                    ).toFloat(),
                    onValueChange = { styleManager.setFontSize(it.toInt()) },
                    valueRange = FONT_SIZE_MIN..FONT_SIZE_MAX,
                    // 不画刻度点：设计稿要求一条干净的轨道 + 单颗圆钮
                    steps = 0,
                    modifier = Modifier.width(120.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = PaperVariant,
                    ),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "A+",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = InkTertiary,
                    modifier = Modifier.clickable {
                        styleManager.setFontSize(style.fontSize + 1)
                    },
                )
            },
        )

        FoxEditorialRow(
            title = "歌词翻译",
            trailing = {
                Switch(
                    checked = style.showTranslation,
                    onCheckedChange = { styleManager.setShowTranslation(it) },
                    colors = switchColors(),
                )
            },
        )
        FoxEditorialRow(
            title = "桌面歌词",
            subtitle = "在桌面悬浮显示",
            trailing = {
                Switch(
                    checked = isDesktopLyricEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            syncManager.openDesktopLyric(context) { syncManager.enableDesktopLyric() }
                        } else {
                            syncManager.disableDesktopLyric()
                        }
                    },
                    colors = switchColors(),
                )
            },
        )
        FoxEditorialRow(
            title = "歌词纠正",
            subtitle = "发现词错？帮助我们修正",
            onClick = onLyricCorrectClick,
            trailing = { ChevronText() },
        )
        FoxEditorialRow(
            title = "自定义配色",
            subtitle = "背景 / 普通 / 高亮 三通道",
            onClick = onCustomColorClick,
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SwatchDot(Color(paletteColors.bgTop), PaperHairline)
                    SwatchDot(Color(paletteColors.normal), null)
                    SwatchDot(Color(paletteColors.highlight), null)
                }
                Spacer(Modifier.width(9.dp))
                ChevronText()
            },
        )

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onFinish)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "完成",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                ),
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** 开关配色：选中轨道 accent + 白钮；未选中轨道 variant + 白钮。 */
@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = PaperVariant,
)

/** 编辑行右侧的 › 指示（不用图标字体，走普通文本避免 emoji 化）。 */
@Composable
private fun ChevronText() {
    Text(
        text = "›",
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 16.sp,
            lineHeight = 18.sp,
        ),
        color = InkQuaternary,
    )
}

/**
 * 实拍预览大卡（设计稿②）：占满宽的圆角卡，底色就是当前方案的渐变，
 * 居中三行歌词 —— 普通 / 高亮 / 普通，字号即真实字号（13sp / 15sp）。
 */
@Composable
private fun LyricPreviewCard(colors: LyricPaletteColors) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(width = 1.dp, color = PaperHairline, shape = shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(colors.bgTop), Color(colors.bgBottom)),
                ),
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PreviewLine(
            text = "你说已分开",
            color = Color(colors.normal),
            fontSize = 13.sp,
            bold = false,
        )
        PreviewLine(
            text = "为什么还要帮你撑伞",
            color = Color(colors.highlight),
            fontSize = 15.sp,
            bold = true,
        )
        PreviewLine(
            text = "你说已分开",
            color = Color(colors.normal),
            fontSize = 13.sp,
            bold = false,
        )
    }
}

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
            lineHeight = fontSize * 2.1f,
        ),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
        textAlign = TextAlign.Center,
    )
}

/**
 * 方案 thumbnail（设计稿②）：96×62 小卡，卡内两行迷你预览（普通 + 高亮），
 * 选中卡 2dp accent 描边。整条横向可滑，一眼扫过 8 组方案。
 */
@Composable
private fun LyricPaletteThumb(
    palette: LyricPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val cardShape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(62.dp)
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(palette.bgTop), Color(palette.bgBottom)),
                ),
            )
            .then(
                if (selected) {
                    Modifier.border(width = 2.dp, color = accent, shape = cardShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "你说已分开",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                ),
                color = Color(palette.normalColor),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = "为什么还要帮你撑伞",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                ),
                fontWeight = FontWeight.Bold,
                color = Color(palette.highlightColor),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
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
 * 自定义配色二级页（设计稿②）：实时预览 → 三通道行 → 8 高亮预设 + HEX → 跟随主题 / 完成。
 */
@Composable
private fun LyricCustomColorPage(
    styleManager: LyricStyleManager,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    val paletteTick by styleManager.paletteTick.collectAsState()
    val themeAccent = MaterialTheme.colorScheme.primary
    var hexInput by remember { mutableStateOf("") }
    var activeChannel by remember { mutableStateOf(LyricColorChannel.HIGHLIGHT) }

    val custom = remember(paletteTick) { styleManager.customColors }
    val palette = remember(paletteTick) { styleManager.currentPalette }
    val colors = remember(paletteTick, themeAccent) {
        styleManager.resolvePaletteColors(themeAccent.toArgb())
    }
    val parsedHex = remember(hexInput) { parseHexArgb(hexInput) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PaperVariant)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "自定义配色",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // 实时预览：渐变底 + 三行示例字（高亮 / 普通 / 翻译）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(width = 1.dp, color = PaperHairline, shape = RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(colors.bgTop), Color(colors.bgBottom)),
                    ),
                )
                .padding(vertical = 22.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "你说已分开 为什么还要帮你撑伞",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                ),
                fontWeight = FontWeight.Bold,
                color = Color(colors.highlight),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Cuz baby you are mine mine",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                ),
                color = Color(colors.normal),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "翻译：因为宝贝 你本属于我",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                ),
                color = Color(colors.translation),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(10.dp))

        CustomChannelRow(
            title = "高亮 · 当前行",
            value = hexString(colors.highlight),
            color = Color(colors.highlight),
            selected = activeChannel == LyricColorChannel.HIGHLIGHT,
            onClick = { activeChannel = LyricColorChannel.HIGHLIGHT },
        )
        CustomChannelRow(
            title = "普通 · 其他行",
            value = hexString(colors.normal),
            color = Color(colors.normal),
            selected = activeChannel == LyricColorChannel.NORMAL,
            onClick = { activeChannel = LyricColorChannel.NORMAL },
        )
        CustomChannelRow(
            title = "背景",
            value = if (custom.background != LyricCustomColors.UNSET) {
                hexString(colors.bgTop)
            } else {
                "跟随主题 · ${palette.name}"
            },
            color = Color(colors.bgTop),
            borderColor = PaperHairline,
            selected = activeChannel == LyricColorChannel.BACKGROUND,
            onClick = { activeChannel = LyricColorChannel.BACKGROUND },
        )

        Spacer(Modifier.height(10.dp))
        Text(
            text = "高亮色预设",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            LyricPalettes.highlightPresets.forEach { argb ->
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .then(
                            if (colors.highlight == argb.toInt()) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clickable {
                            activeChannel = LyricColorChannel.HIGHLIGHT
                            styleManager.setCustomHighlight(argb.toInt())
                        },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // HEX 输入：6 位 hex，非法不生效（按钮禁用）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = hexInput,
                onValueChange = { raw ->
                    hexInput = raw
                        .uppercase()
                        .filter { it.isDigit() || it in 'A'..'F' }
                        .take(6)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
                placeholder = {
                    Text(
                        text = "6 位 HEX，如 B8513A",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PaperVariant,
                    unfocusedContainerColor = PaperVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = PaperHairline,
                ),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (parsedHex != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            PaperVariant
                        },
                    )
                    .clickable(enabled = parsedHex != null) {
                        val argb = parsedHex ?: return@clickable
                        when (activeChannel) {
                            LyricColorChannel.HIGHLIGHT -> styleManager.setCustomHighlight(argb)
                            LyricColorChannel.NORMAL -> styleManager.setCustomNormal(argb)
                            LyricColorChannel.BACKGROUND -> styleManager.setCustomBackground(argb)
                        }
                        hexInput = ""
                    }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "应用",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = if (parsedHex != null) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(PaperSurface)
                    .border(
                        width = 1.dp,
                        color = PaperHairline,
                        shape = RoundedCornerShape(22.dp),
                    )
                    .clickable { styleManager.followThemePalette() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "跟随主题",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onFinish)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "完成",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

private enum class LyricColorChannel {
    HIGHLIGHT,
    NORMAL,
    BACKGROUND,
}

/** 三通道行：标题 + 当前 HEX（或「跟随主题 · 方案名」）+ 右侧当前色 swatch。 */
@Composable
private fun CustomChannelRow(
    title: String,
    value: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    borderColor: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (borderColor != null) {
                        Modifier.border(width = 1.dp, color = borderColor, shape = CircleShape)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

/** 0xAARRGGBB → "#RRGGBB"（大写）。 */
private fun hexString(argb: Int): String =
    "#" + (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** 解析 6 位 hex → 0xAARRGGBB；非法返回 null。 */
private fun parseHexArgb(raw: String): Int? {
    val text = raw.trim().removePrefix("#")
    if (text.length != 6) return null
    if (text.any { !(it.isDigit() || it in 'A'..'F' || it in 'a'..'f') }) return null
    val rgb = text.toLongOrNull(16) ?: return null
    return (0xFF000000L or rgb).toInt()
}
