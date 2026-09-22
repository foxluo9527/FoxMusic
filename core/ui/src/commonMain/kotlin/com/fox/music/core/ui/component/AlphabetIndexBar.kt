package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AlphabetIndexBar(
    letters: List<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = 24.dp)
            .padding(vertical = 8.dp, horizontal = 2.dp)
            .pointerInput(letters) {
                fun resolveLetter(y: Float): Char? {
                    val itemHeight = size.height.toFloat() / letters.size
                    if (itemHeight <= 0f) return null
                    val index = (y / itemHeight).toInt().coerceIn(0, letters.lastIndex)
                    return letters.getOrNull(index)
                }

                detectTapGestures { offset ->
                    resolveLetter(offset.y)?.let(onLetterSelected)
                }
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    resolveLetter(change.position.y)?.let(onLetterSelected)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                )
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            letters.forEach { letter ->
                Text(
                    text = letter.toString(),
                    modifier = Modifier.padding(horizontal = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
