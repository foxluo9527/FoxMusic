package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.ui.theme.FoxSpacing

/**
 * 全站统一的圆形图标按钮（抽屉入口 / 顶栏动作）。
 *
 * 旧做法是 `surfaceVariant 50%` 或 `Color.Black 35%` 的圆底：前者在纸感底上几乎看不见边界，
 * 后者在浅底上像一块脏斑。改成 surface 实底 + 1dp 发丝描边，同一颗按钮在任何底色上都成立。
 */
@Composable
fun FoxCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(36.dp)
            .background(scheme.surface, CircleShape)
            .border(1.dp, scheme.onSurface.copy(alpha = 0.07f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: scheme.onSurface,
            modifier = Modifier.size(19.dp),
        )
    }
}

/**
 * 主 Tab 页统一头部：左上角抽屉入口 + 右侧动作，标题**左对齐**并带一行眉标（eyebrow）。
 *
 * 与旧的 [TabPageTopBar]（居中标题 + 三横）相比，左对齐头部让标题与下方列表共享同一条
 * 左侧对齐轴（24dp），页面不再"上方居中、下方左对齐"地分裂成两套栅格。
 */
@Composable
fun FoxPageHeader(
    title: String,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = FoxSpacing.pagePadding,
                end = FoxSpacing.pagePadding,
                // 顶部 4dp：让 36dp 圆钮中心与发现页头部（44dp 行内居中）的圆钮中心同高，
                // 切 Tab 时左上角入口不产生纵向跳变。
                top = 4.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FoxCircleIconButton(
                icon = Icons.Filled.Menu,
                contentDescription = "更多",
                onClick = onOpenDrawer,
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        eyebrow?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                fontSize = 10.5.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(5.dp))
        }
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onBackground,
        )
    }
}
