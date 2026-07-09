package com.fox.music.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val FoxShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val SearchBarShape = RoundedCornerShape(28.dp)
val CardShape = RoundedCornerShape(12.dp)
val PlayerSheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
