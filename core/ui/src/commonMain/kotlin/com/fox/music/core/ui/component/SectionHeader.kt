package com.fox.music.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.ui.theme.FoxSpacing

/**
 * 板块标题行。
 *
 * 2026-10 改版要点（对应原型 01 首页 .sec-hd2）：
 * - 标题 16sp / 600，不再是 20sp Bold —— 板块标题是"路标"不是"标题"，压低字号后
 *   内容才是主角，页面也不会被一堆大标题切成碎块。
 * - 右侧入口文案收短成「全部」，字号 12sp、颜色降到 ink-3：可点但退到背景里去。
 * - 上下留白走 4pt 基线：上 18 / 下 10（[FoxSpacing.headerGap] 由调用方决定是否叠加）。
 */
@Composable
fun SectionHeader(
    modifier: Modifier = Modifier,
    title: String,
    moreText: String = "全部",
    moreIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    iconRotate: Float = 180f,
    onMoreClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = FoxSpacing.headerGap),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            fontWeight = FontWeight.SemiBold,
            color = scheme.onBackground,
            maxLines = 1,
        )

        if (onMoreClick != null) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onMoreClick)
                    // 无障碍语义：可点行必须以「按钮 + moreText」暴露（iOS XCUITest 才能以
                    // buttons[moreText] 命中；读屏用户也能听到这是可点入口）。
                    // moreText 为空时视觉上只留箭头，语义仍以「全部」暴露。
                    // 注意 Icon 的 contentDescription 要去掉，否则合并语义后出现重复标签。
                    .semantics {
                        role = Role.Button
                        contentDescription = moreText.ifBlank { "全部" }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (moreText.isNotBlank()) {
                    Text(
                        text = moreText,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = scheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = moreIcon,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(14.dp)
                        .rotate(iconRotate)
                )
            }
        }
    }
}
