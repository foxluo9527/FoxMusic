package com.fox.music.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.ui.theme.foxEditorialAccent

/**
 * 话题标签行。
 *
 * v11：默认色从硬编码蓝 `0xFF507DAF` 改成强调色令牌 [foxEditorialAccent]
 * （`colorScheme.primary`，跟随用户在主题设置里选的配色，深色下自动提亮）。
 * 因为强调色要在 Composable 内取，所以默认值用 [Color.Unspecified] 占位再解析。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostTagsRow(
    tags: List<String>,
    onTagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    tagColor: Color = Color.Unspecified,
) {
    if (tags.isEmpty()) return
    val resolvedColor = if (tagColor == Color.Unspecified) foxEditorialAccent else tagColor
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            val label = if (tag.startsWith("#")) tag else "#$tag"
            Text(
                text = label,
                // 设计稿：11.5sp / 半粗 / 强调色
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                ),
                color = resolvedColor,
                modifier = Modifier.clickable { onTagClick(tag.removePrefix("#")) },
            )
        }
    }
}
