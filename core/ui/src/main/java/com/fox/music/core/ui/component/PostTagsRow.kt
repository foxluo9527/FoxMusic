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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostTagsRow(
    tags: List<String>,
    onTagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    tagColor: Color = Color(0xFF507DAF),
) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            val label = if (tag.startsWith("#")) tag else "#$tag"
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = tagColor,
                modifier = Modifier.clickable { onTagClick(tag.removePrefix("#")) },
            )
        }
    }
}
