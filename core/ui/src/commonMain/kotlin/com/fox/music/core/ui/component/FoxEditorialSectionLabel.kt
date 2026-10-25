package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.ui.theme.FoxEditorial
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.core.ui.theme.foxEditorialAccent

/**
 * 编辑杂志风 · 分区标签（v10）：`01 ── 热搜榜 ─────────────`。
 *
 * 编号用衬线强调色，标题用 13sp 半粗正文，右侧一条 1px 发丝线填满剩余宽度。
 * 与 [FoxEditorialRow] 配套：上分区标签、下无边框编辑列表。
 *
 * 内部已固定左右 20dp 页边距，调用方只需传 `Modifier.padding(top = …)` 之类。
 *
 * @param number 分区编号（"01" / "02"），衬线强调色。
 * @param title 分区名（"热搜榜" / "评论"）。
 * @param trailing 末尾的可选操作（如热搜榜的「换一换」）。
 */
@Composable
fun FoxEditorialSectionLabel(
    number: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number,
            style = FoxEditorial.sectionNumber.copy(fontSize = 12.5.sp),
            color = foxEditorialAccent,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                letterSpacing = (-0.3).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
                .height(1.dp)
                .background(PaperHairline),
        )
        if (trailing != null) {
            Row(modifier = Modifier.padding(start = 10.dp)) {
                trailing()
            }
        }
    }
}
