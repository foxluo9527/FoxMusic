package com.fox.music.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 *    Author : 罗福林
 *    Date   : 2026/2/4
 *    Desc   :
 */
@Composable
fun TitleSwitch(
    modifier: Modifier = Modifier,
    titles: List<String>,
    currentIndex: Int,
    onTitleClick: (Int) -> Unit,
) {
    val indicatorWidth = 1f / titles.size
    val targetPosition = currentIndex * indicatorWidth

    val indicatorOffset = remember {Animatable(targetPosition)}

    LaunchedEffect(targetPosition) {
        indicatorOffset.animateTo(
            targetPosition,
            animationSpec = tween(durationMillis = 300)
        )
    }

    BoxWithConstraints(modifier) {
        val maxWidth = maxWidth

        Box(Modifier.fillMaxSize()) {
            // 指示器背景
            Box(
                Modifier
                    .fillMaxWidth(indicatorWidth)
                    .fillMaxHeight()
                    .offset(x = maxWidth * indicatorOffset.value)
                    .background(Color.White, CircleShape)
            )

            // 文字选项
            Row(Modifier.fillMaxSize()) {
                titles.forEachIndexed {index, title ->
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clickable(
                                indication = null,
                                interactionSource = remember {MutableInteractionSource()}
                            ) {
                                onTitleClick(index)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF202122),
                            fontWeight = FontWeight(if (index == currentIndex) 700 else 510)
                        )
                    }
                }
            }
        }
    }
}
