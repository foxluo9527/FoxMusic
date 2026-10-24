package com.fox.music.feature.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.fox.music.core.assistant.AssistantAction
import com.fox.music.core.assistant.AiConversationSummary
import com.fox.music.core.assistant.isPlayback
import com.fox.music.core.ui.component.FoxDialog
import com.fox.music.core.ui.component.FoxEditorialCircleButton
import com.fox.music.core.ui.component.FoxEditorialRow
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.core.ui.theme.FoxEditorial
import com.fox.music.core.ui.theme.foxHeroGradient
import com.fox.music.core.ui.theme.AccentTerracotta
import com.fox.music.core.ui.theme.InkMuted
import com.fox.music.core.ui.theme.InkQuaternary
import com.fox.music.core.ui.theme.InkTertiary
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.feature.assistant.AssistantChatMessage
import com.fox.music.feature.assistant.AssistantState
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * AI 助手对话面板（跨端共用，stateless）。
 *
 * 只吃 [AssistantState] + 回调；Android 薄壳（AssistantScreen）负责
 * hiltViewModel 与 effect → Toast，iOS 壳复用同一份。
 *
 * 视觉（2026-10 纸感改版）：
 *  - 用户气泡 = 强调色实心（陶土红），助手气泡 = 纸面 + 发丝线描边，弃用灰底 surfaceVariant；
 *  - 助手消息带 AI 徽标（淡强调色圆底 + 星形），用户消息右对齐无徽标；
 *  - 顶栏 / 页面底色统一 `background`，与设置等二级页一致；
 *  - 所有弹窗走 core:ui 统一样式（[FoxDialog]），历史会话为定制列表弹窗。
 *
 * 动作分发策略（与服务端约定）：
 *  - 非 requiresConfirm 动作（如 play_music）由 ViewModel 收到即自动执行；
 *  - requiresConfirm 动作（send_message / add_friend）进 [AssistantState.pendingActions]
 *    队列，本面板逐条弹 [FoxDialog] 确认后再执行。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantContent(
    state: AssistantState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: (Long) -> Unit,
    onRunAction: (AssistantAction) -> Unit,
    onConfirmAction: (AssistantAction) -> Unit,
    onDismissAction: () -> Unit,
    onNewConversation: () -> Unit,
    onLoadConversations: () -> Unit = {},
    onOpenConversation: (Long) -> Unit = {},
    onDeleteConversation: (Long) -> Unit = {},
    onToggleHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    /** 点空态示例问法：默认填入输入框（不直接发送，给用户改词的机会） */
    onSuggestionClick: (String) -> Unit = { onInputChange(it) },
    /**
     * 底部悬浮迷你播放器插槽（平台壳注入，可空）。
     *
     * 之所以做成 `() -> Unit` 而不是直接依赖 MiniPlayer：MiniPlayer 是 androidMain
     * 组件且强依赖 sharedTransition，commonMain 不能引用；这里只留插槽，
     * iOS 壳不传即为 null（iOS 播放由 Swift PlayerModel 托管，另有自己的播放条）。
     */
    miniPlayer: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val pending = state.pendingActions.firstOrNull()

    // 新消息（含流式上屏的乐观消息）自动滚到底
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AssistantTopBar(
                onBack = onBack,
                onLoadConversations = onLoadConversations,
                onNewConversation = onNewConversation,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = FoxSpacing.cardPadding,
                    vertical = FoxSpacing.itemSpacing,
                ),
                verticalArrangement = Arrangement.spacedBy(FoxSpacing.itemSpacing),
            ) {
                if (state.isEmpty) {
                    item(key = "welcome") {
                        AssistantWelcome(onSuggestionClick = onSuggestionClick)
                    }
                }
                itemsIndexed(state.messages, key = { _, message -> message.id }) { _, message ->
                    AssistantMessageItem(
                        message = message,
                        onRetry = { onRetry(message.id) },
                        onActionClick = onRunAction,
                    )
                }
            }

            miniPlayer?.invoke()

            AssistantInputBar(
                text = state.inputText,
                canSend = state.canSend,
                onInputChange = onInputChange,
                onSend = onSend,
            )
        }
    }

    // requiresConfirm 动作确认框（统一弹窗样式）
    if (pending != null) {
        FoxDialog(
            title = "确认操作",
            text = pending.label.ifBlank { "确认执行「${pending.type}」？" },
            confirmText = "确认",
            dismissText = "取消",
            onDismiss = onDismissAction,
            onConfirm = { onConfirmAction(pending) },
        )
    }

    // 历史会话面板（FoxDialog 容器 + 定制列表）
    if (state.historyOpen) {
        AssistantHistoryDialog(
            state = state,
            onOpenConversation = onOpenConversation,
            onDeleteConversation = onDeleteConversation,
            onToggleHistory = onToggleHistory,
        )
    }
}

/**
 * v10 顶栏：左返回圆钮 + 中间「ASSISTANT」眼标与衬线 21sp 标题 + 右历史/新建圆钮。
 *
 * 三个圆钮统一走 [FoxEditorialCircleButton]（surface 实底 + hairline 描边，
 * 视觉 32dp / 命中 44dp），图标全部线性 SVG。
 */
@Composable
private fun AssistantTopBar(
    onBack: () -> Unit,
    onLoadConversations: () -> Unit,
    onNewConversation: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoxEditorialCircleButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.size(18.dp),
                tint = InkMuted,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = "ASSISTANT",
                style = FoxEditorial.kicker.copy(fontSize = 9.5.sp),
                color = InkQuaternary,
            )
            Text(
                text = "AI 音乐助手",
                style = FoxEditorial.mastheadTitle.copy(fontSize = 21.sp, lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        FoxEditorialCircleButton(onClick = onLoadConversations) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = "历史会话",
                modifier = Modifier.size(17.dp),
                tint = InkMuted,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        FoxEditorialCircleButton(onClick = onNewConversation) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "新会话",
                modifier = Modifier.size(17.dp),
                tint = InkMuted,
            )
        }
    }
}

/**
 * 历史会话弹窗：复用 [FoxDialog] 的纸面容器 / 圆角 / 按钮排布，
 * 列表区定制——当前会话高亮、行内删除二次确认、发丝线分隔。
 */
@Composable
private fun AssistantHistoryDialog(
    state: AssistantState,
    onOpenConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onToggleHistory: () -> Unit,
) {
    // 行内删除二次确认：点垃圾桶后该项就地变成「确认删除 / 取消」，不叠弹窗
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    val scheme = MaterialTheme.colorScheme

    FoxDialog(
        title = "历史会话",
        text = "",
        confirmText = "关闭",
        dismissText = null,
        onDismiss = onToggleHistory,
        onConfirm = onToggleHistory,
    ) {
        if (state.conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "还没有历史会话",
                    fontSize = 13.5.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                itemsIndexed(
                    items = state.conversations,
                    key = { _, conv -> conv.id },
                ) { index, conv ->
                    val isCurrent = conv.id == state.conversationId
                    val deleteConfirming = pendingDeleteId == conv.id
                    val tint = HISTORY_TINTS[index % HISTORY_TINTS.size]
                    if (deleteConfirming) {
                        // 行内删除二次确认：点垃圾桶后就地变成「确认删除 / 取消」，不叠弹窗
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pendingDeleteId = null }
                                .padding(horizontal = 4.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "删除该会话？",
                                fontSize = 13.5.sp,
                                color = scheme.error,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "取消",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { pendingDeleteId = null }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                            Text(
                                text = "删除",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = scheme.error,
                                modifier = Modifier
                                    .clickable {
                                        pendingDeleteId = null
                                        onDeleteConversation(conv.id)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        if (index < state.conversations.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(PaperHairline),
                            )
                        }
                    } else {
                        // 设计稿⑥：无卡片编辑列表 —— 渐变 icon 容器 + 标题 + 相对时间 + 删除图标，
                        // 发丝线分隔（FoxEditorialRow 自带）；点当前会话关闭面板，点其它打开。
                        FoxEditorialRow(
                            title = conv.title ?: "会话 ${conv.id}",
                            subtitle = buildHistorySubtitle(conv),
                            isPlaying = isCurrent,
                            leading = {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(foxHeroGradient(Color(tint))),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "✦",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                            },
                            trailing = {
                                IconButton(
                                    onClick = { pendingDeleteId = conv.id },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "删除会话",
                                        tint = scheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                            onClick = {
                                if (isCurrent) onToggleHistory() else onOpenConversation(conv.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 单条消息气泡：用户右侧（强调色实心），助手左侧（纸面 + 发丝线）+ AI 徽标 + 动作芯片。 */
@Composable
private fun AssistantMessageItem(
    message: AssistantChatMessage,
    onRetry: () -> Unit,
    onActionClick: (AssistantAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!message.isUser) {
            // AI 身份徽标：28dp、9dp 圆角渐变方块 + 衬线「F」，与气泡顶对齐
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFF3DCD5), Color(0xFFE8C4B8)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "F",
                    style = FoxEditorial.sectionNumber.copy(fontSize = 14.sp),
                    color = AccentTerracotta,
                )
            }
            Spacer(modifier = Modifier.width(9.dp))
        }
        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
        ) {
            // 用户气泡：14/14/4/14（尖角在右下）；AI 气泡：14/14/14/4（尖角在左下）
            val bubbleShape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (message.isUser) 14.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 14.dp,
            )
            Box(
                modifier = Modifier
                    // 用户气泡 = variant 填充；AI 气泡 = surface + 1px hairline 描边
                    .clip(bubbleShape)
                    .background(if (message.isUser) scheme.surfaceVariant else scheme.surface)
                    .then(
                        if (message.isUser) {
                            Modifier
                        } else {
                            Modifier.border(1.dp, PaperHairline, bubbleShape)
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Column {
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.5.sp,
                                lineHeight = 21.6.sp,
                            ),
                            color = scheme.onSurface,
                        )
                    }
                    if (message.isSending) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = scheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "发送中",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (message.failed) {
                        Text(
                            text = "发送失败，点击重试",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = scheme.error,
                            modifier = Modifier
                                .clickable(onClick = onRetry)
                                .padding(vertical = 2.dp),
                        )
                    }
                }
            }

            if (message.actions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    message.actions.forEach { action ->
                        AssistantActionChip(
                            action = action,
                            onClick = { onActionClick(action) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 动作芯片：AI 给出的结果一律渲染成可点击芯片，点了才真的执行。
 *
 * 播放类（play_*）用强调色实心胶囊 + 播放图标，第一眼传达「这是个播放入口」；
 * 其它动作用纸面 + 发丝线描边胶囊，避免 M3 AssistChip 默认描边色与纸感不统一。
 */
@Composable
private fun AssistantActionChip(
    action: AssistantAction,
    onClick: () -> Unit,
) {
    val text = action.label.ifBlank { action.type }
    val scheme = MaterialTheme.colorScheme
    if (action.isPlayback) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = scheme.primary,
            contentColor = scheme.onPrimary,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                )
            }
        }
        return
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = scheme.surface,
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, PaperHairline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            )
        }
    }
}

/**
 * 空态引导（设计稿⑤）：电波徽标 + 衬线问候 + 2×2 渐变大图建议格；输入条由父级保留。
 * CTA / 强调色统一走 AccentTerracotta。
 */
@Composable
private fun AssistantWelcome(
    onSuggestionClick: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    // 电波徽标 + 衬线问候
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistantWaveBadge()
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "晚上好",
                style = FoxEditorial.heroTitle.copy(fontSize = 19.sp, lineHeight = 24.sp),
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "我是 Fox 电台的 AI 灵感",
                style = FoxEditorial.kicker.copy(fontSize = 12.sp, letterSpacing = 1.sp),
                color = InkTertiary,
            )
        }
    }

    // 2×2 大图建议格（设计稿⑤）：每张渐变实底 + 左下角 kicker 标签 + 衬线标题。
    val suggestions = listOf(
        WelcomeSuggestion("MOOD", "放点适合\n现在听的", "播放适合现在听的音乐"),
        WelcomeSuggestion("PLAYLIST", "建一份\n深夜写作歌单", "帮我建一份深夜写作歌单"),
        WelcomeSuggestion("OST", "找某部\n影视的原声", "找一部影视作品的原声"),
        WelcomeSuggestion("CHAT", "这首\n唱的是什么", "这首歌唱的是什么"),
    )
    // 设计稿⑤的四张渐变实底（160° 对角）：MOOD 陶土 / PLAYLIST 苔绿 / OST 黛蓝 / CHAT 琥珀
    val cardBrushes = listOf(
        Brush.linearGradient(
            listOf(Color(0xFFE8B8A2), Color(0xFFC97A5A)),
            start = Offset(0f, 0f),
            end = Offset.Infinite,
        ),
        Brush.linearGradient(
            listOf(Color(0xFF7FAE86), Color(0xFF3E6B4E)),
            start = Offset(0f, 0f),
            end = Offset.Infinite,
        ),
        Brush.linearGradient(
            listOf(Color(0xFF8FA6C4), Color(0xFF4E6B8C)),
            start = Offset(0f, 0f),
            end = Offset.Infinite,
        ),
        Brush.linearGradient(
            listOf(Color(0xFFD4945A), Color(0xFF9C6B2E)),
            start = Offset(0f, 0f),
            end = Offset.Infinite,
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        suggestions.chunked(2).forEachIndexed { rowIndex, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { colIndex, sug ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBrushes[rowIndex * 2 + colIndex])
                            .clickable { onSuggestionClick(sug.query) }
                            .padding(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Text(
                                text = sug.kicker,
                                style = FoxEditorial.kicker.copy(
                                    fontSize = 8.5.sp,
                                    letterSpacing = 1.5.sp,
                                ),
                                color = Color.White.copy(alpha = 0.85f),
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = sug.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 16.5.sp,
                                ),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

/** 电波徽标（设计稿⑤）：圆角方形 + 5 根 equalizer 条轻微起伏动画，accent 实色。 */
@Composable
private fun AssistantWaveBadge() {
    val transition = rememberInfiniteTransition()
    val barCount = 5
    val heights = (0 until barCount).map { i ->
        transition.animateFloat(
            initialValue = 7f,
            targetValue = 22f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(offsetMillis = i * 130),
            ),
        )
    }
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                // 渐变实底（浅色 #F3DCD5 → #E8C4B8）：不用 alpha 叠色，
                // 否则落在不同底色上会得到脏色
                Brush.linearGradient(
                    listOf(Color(0xFFF3DCD5), Color(0xFFE8C4B8)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            heights.forEach { h ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(h.value.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AccentTerracotta),
                )
            }
        }
    }
}

/** 空态建议格数据：kicker 标签 + 标题（可换行）+ 点击后填入输入框的问句。 */
private data class WelcomeSuggestion(
    val kicker: String,
    val title: String,
    val query: String,
)

/** 底部输入栏：多行输入 + 发送按钮（纸面 + 顶发丝线，与页面底色分层）。 */
@Composable
private fun AssistantInputBar(
    text: String,
    canSend: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val pillShape = RoundedCornerShape(20.dp)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PaperHairline),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 只补键盘 inset：底部导航栏 inset 由 Scaffold 的 contentWindowInsets
                    // 统一处理，再叠 navigationBarsPadding 会双重留白。
                    .imePadding()
                    .padding(
                        horizontal = FoxSpacing.cardPadding,
                        vertical = FoxSpacing.itemSpacing,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // pill 输入框：40dp 高、hairline 描边（不填充）
                BasicTextField(
                    value = text,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, PaperHairline, pillShape)
                                .padding(horizontal = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (text.isEmpty()) {
                                Text(
                                    text = "说点什么…",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 12.5.sp,
                                    ),
                                    color = InkQuaternary,
                                    maxLines = 1,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Spacer(modifier = Modifier.width(10.dp))
                // 40dp 强调色圆形发送钮：内含白色线性箭头
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .clickable(enabled = canSend, onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = "发送",
                        modifier = Modifier.size(19.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/** 历史列表渐变 icon 容器的取色（设计稿⑥：4 色循环，与空态建议卡呼应）。 */
private val HISTORY_TINTS = listOf(0xFF9C6B44, 0xFF5F7A4E, 0xFF38598C, 0xFFB07624)

/** 历史行副标题：相对时间（今天 / 昨天 / N天前 / M月D日）+ 消息条数。 */
private fun buildHistorySubtitle(conv: AiConversationSummary): String {
    val rel = relativeTime(conv.updatedAt.ifBlank { conv.createdAt })
    val count = "${conv.messageCount} 条"
    return if (rel.isBlank()) count else "$rel · $count"
}

/** ISO 时间串 → 相对时间描述（commonMain 无 java.time，走 kotlinx-datetime）。 */
private fun relativeTime(iso: String): String {
    if (iso.isBlank()) return ""
    val datePart = iso.take(10) // yyyy-MM-dd
    val parts = datePart.split("-")
    if (parts.size != 3) return datePart
    val y = parts[0].toIntOrNull() ?: return datePart
    val m = parts[1].toIntOrNull() ?: return datePart
    val d = parts[2].toIntOrNull() ?: return datePart
    return try {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val target = LocalDate(y, m, d)
        val days = today.toEpochDays().toLong() - target.toEpochDays().toLong()
        when {
            days <= 0L -> "今天"
            days == 1L -> "昨天"
            days < 7L -> "${days}天前"
            else -> "${m}月${d}日"
        }
    } catch (_: Exception) {
        datePart
    }
}
