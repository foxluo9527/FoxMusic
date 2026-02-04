package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 *    Author : 罗福林
 *    Date   : 2026/2/4
 *    Desc   :
 */
@Composable
fun TabSwitch(
    modifier: Modifier = Modifier,
    length: Int,
    currentIndex: Int,
    color: Color = Color.Black,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        repeat(length) {index ->
            val isCurrent = index == currentIndex
            Spacer(Modifier
                .width(if (isCurrent) 7.dp else 3.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(color)
            )
            if (index != length - 1){
                Spacer(Modifier.width(3.dp))
            }
        }
    }
}
