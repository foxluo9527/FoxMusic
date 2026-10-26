package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.ui.theme.FoxEditorial

/**
 * 编辑杂志风 · 实底色卡（v9 主题设置用）。
 *
 * 每张卡用该主色铺满、白字写「名称 + 用途标签」，一眼看出这套配色"铺开后长什么样"，
 * 替换 v8 的 4 列小圆点（小圆点无法预判铺开效果）。选中态浮起 + 2dp 内描边。
 *
 * @param name 主色名称（如 焦糖 / 苔绿）。
 * @param tag 顶部小标签（如 推荐 / 已选 / 默认 / 暖）。
 * @param containerColor 卡面主色。
 * @param contentColor 卡面文字色，默认白。
 * @param selected 选中态（内描边 + 抬升）。
 */
@Composable
fun FoxColorCard(
    name: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    tag: String? = null,
    contentColor: Color = Color.White,
    selected: Boolean = false,
    height: Dp = 84.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = contentColor,
                        shape = RoundedCornerShape(12.dp),
                    )
                } else {
                    Modifier
                },
            )
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (tag != null) {
                Text(
                    text = tag,
                    style = FoxEditorial.kicker.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                    color = contentColor.copy(alpha = 0.85f),
                )
            }
            Text(
                text = name,
                style = FoxEditorial.kicker.copy(
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                ),
                color = contentColor,
                modifier = Modifier.align(Alignment.Start),
            )
        }
    }
}
