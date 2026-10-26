package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.ui.theme.FoxMusicTheme

/**
 * 全站顶栏唯一尺寸表（v14）。
 *
 * 改这里就是改全站顶栏 —— 单个页面**不允许**再写自己的 dp 常量。
 * 数值来源：56dp 是这套纸感排版的舒适高度（M3 默认 64 在旁边会显得笨重，
 * 而 app 内已有的手写页头基本都落在 52~56）；返回与右侧图标统一 20dp / 命中盒 44dp；
 * 盒子外距 12dp，使 **图标左边缘正好落到 24dp 内容边距上**（12 + (44-20)/2 = 24）。
 */
object FoxTopBarSpec {
    /** 内容区（状态栏以下）高度。 */
    val Height = 56.dp

    /** 顶栏左右外距。与 [IconSize]/[ActionBox] 配合，让图标边缘对齐 24dp 内容边距。 */
    val HorizontalPadding = 12.dp

    /** 返回键与右侧图标按钮的命中盒。≥44dp 满足无障碍最小触控目标。 */
    val ActionBox = 44.dp

    /** 返回箭头与右侧图标的视觉尺寸。 */
    val IconSize = 20.dp

    /** 右侧文字动作（保存 / 完成 / 全部重试…）的高度与字号。 */
    val TextActionHeight = 32.dp
    val TextActionSize = 13.5.sp

    /** 标题字号。 */
    val TitleSize = 17.sp
    val TitleLineHeight = 22.sp
}

/**
 * 统一返回键（v14）：**全站唯一的返回图标**。
 *
 * 视觉 20dp、命中盒 44dp、图标 `Icons.AutoMirrored.Filled.ArrowBack`（RTL 自动镜像）。
 * 禁止再用 `ArrowBackIos` / 未镜像的 `Icons.Default.ArrowBack` / 文字「返回」/ `KeyboardArrowLeft`。
 *
 * @param onMedia 压在封面图 / 大图上的场景（我的主页、帖子详情、搜索刊头）：垫一层半透明黑圆底
 *   并把图标转白，否则深色图上黑箭头看不见。除此之外一律不垫底。
 */
@Composable
fun FoxBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMedia: Boolean = false,
) {
    val tint = if (onMedia) Color.White else MaterialTheme.colorScheme.onSurface
    val container = if (onMedia) Color(0x4D000000) else Color.Transparent
    Box(
        modifier = modifier
            .size(FoxTopBarSpec.ActionBox)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = tint,
            modifier = Modifier.size(FoxTopBarSpec.IconSize),
        )
    }
}

/**
 * 统一顶栏（v14）：**全站二级页顶栏的唯一实现**。
 *
 * 布局：`[状态栏inset] → 56dp 内容区`，内容区左右各 12dp 外距；
 * 返回键 44dp 命中盒 → 标题吃掉剩余宽度（左对齐，单行省略）→ 右侧动作槽。
 *
 * 为什么不直接用 M3 `TopAppBar`：M3 的 `navigationIcon` 是 48dp 盒、标题是 20sp
 * `titleLarge`、水平内距写死在内部（nav 4dp / title 16dp），三样都不可配；
 * 全站 39 处 `TopAppBar` 就因此长出了 6 档图标尺寸、10 档标题字号、8 档内距。
 * 这里自绘一次，把这几个数固定成 [FoxTopBarSpec]。
 *
 * @param containerColor 默认透明 —— 顶栏底色跟随所在页面的 Scaffold 容器色，
 *   避免「页面是 background、顶栏是 surface」拼出一条接缝。
 * @param centerAligned 标题居中（少数全屏页用），默认左对齐。
 * @param titleContent 标题槽整体替换（搜索框 / 分段 tab / 输入框），传了就不渲染 [title]。
 * @param onMedia 压在图片上时垫深色圆底 + 白图标（配合 [FoxBackButton]）。
 */
@Composable
fun FoxTopBar(
    title: String = "",
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    centerAligned: Boolean = false,
    containerColor: Color = Color.Transparent,
    onTitleClick: (() -> Unit)? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    onMedia: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .statusBarsPadding()
            .height(FoxTopBarSpec.Height)
            .padding(horizontal = FoxTopBarSpec.HorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBackButton) {
            FoxBackButton(onClick = onBackClick, onMedia = onMedia)
        }

        if (titleContent != null) {
            Box(modifier = Modifier.weight(1f)) { titleContent() }
        } else if (title.isNotEmpty()) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (centerAligned) {
                    Arrangement.Center
                } else {
                    Arrangement.Start
                },
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = FoxTopBarSpec.TitleSize,
                        lineHeight = FoxTopBarSpec.TitleLineHeight,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (onMedia) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (centerAligned) TextAlign.Center else TextAlign.Start,
                    modifier = if (onTitleClick != null) {
                        Modifier.clickable(onClick = onTitleClick)
                    } else {
                        Modifier
                    },
                )
                titleTrailing?.invoke()
            }
        } else {
            // 无标题：占位撑开，保证 actions 仍靠右
            Box(modifier = Modifier.weight(1f))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = actions,
        )
    }
}

/**
 * 右侧图标动作（v14）：与返回键同一规格（20dp 图标 / 44dp 命中盒），
 * 保证「左返回、右动作」两端图标一样大、一样高。
 */
@Composable
fun FoxTopBarIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = Modifier
            .size(FoxTopBarSpec.ActionBox)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(FoxTopBarSpec.IconSize),
        )
    }
}

/**
 * 右侧文字动作（v14）：保存 / 完成 / 全部重试 / 全选 这类。
 *
 * 高度 32dp、字号 13.5sp SemiBold、颜色走强调色。右侧补 12dp 内距，
 * 让文字右边缘落在 24dp 内容边距上，和图标动作对齐。
 */
@Composable
fun FoxTopBarTextAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = Modifier
            .height(FoxTopBarSpec.ActionBox)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = FoxTopBarSpec.HorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = FoxTopBarSpec.TextActionSize,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = if (enabled) color else color.copy(alpha = 0.38f),
            maxLines = 1,
        )
    }
}

@Preview
@Composable
private fun FoxTopBarPreview() {
    FoxMusicTheme {
        FoxTopBar(
            title = "Fox Music",
            showBackButton = true,
            actions = { FoxTopBarTextAction(text = "保存", onClick = {}) },
        )
    }
}
