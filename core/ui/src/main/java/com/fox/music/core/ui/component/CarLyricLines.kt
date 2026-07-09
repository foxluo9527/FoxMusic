package com.fox.music.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.model.music.Music

@Composable
fun CarLyricLines(
    music: Music,
    positionMs: Long,
    modifier: Modifier = Modifier,
    maxVisibleLines: Int = 4,
) {
    val lines = music.lyricLines
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (lines.isEmpty()) {
            Text(
                text = "暂无歌词",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            return
        }
        val currentIndex = lines.indexOfLast { it.startTimeMs <= positionMs }.coerceAtLeast(0)
        val start = (currentIndex - 1).coerceAtLeast(0)
        lines.drop(start).take(maxVisibleLines).forEachIndexed { offset, line ->
            val lineIndex = start + offset
            val isCurrent = lineIndex == currentIndex
            Text(
                text = if (isCurrent) "▶ ${line.text}" else line.text,
                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.38f),
                fontSize = if (isCurrent) 20.sp else 16.sp,
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = if (isCurrent) 30.sp else 24.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            )
        }
    }
}
