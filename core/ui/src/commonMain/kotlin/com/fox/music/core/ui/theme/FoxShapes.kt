package com.fox.music.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val FoxShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val SearchBarShape = RoundedCornerShape(24.dp)
val CardShape = RoundedCornerShape(16.dp)
val CoverShape = RoundedCornerShape(13.dp)
val PlayerSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
val FloatingNavShape = RoundedCornerShape(28.dp)
val MiniPlayerShape = RoundedCornerShape(15.dp)

/**
 * 封面圆角按"实际边长"分档，不再一律 14dp。
 * 视觉上圆角占比稳定在 10%~15% 才像"实物裁切"，同一个圆角套在 44dp 与 300dp 上
 * 会显得小图太圆、大图太方。
 */
/** 44dp 列表行封面 */
val CoverShapeRow = RoundedCornerShape(9.dp)
/** 74dp 横向轨道小卡 */
val CoverShapeRail = RoundedCornerShape(11.dp)
/** 300dp 播放页大封面 */
val CoverShapeHero = RoundedCornerShape(20.dp)
/** 迷你条 38dp 封面 */
val CoverShapeMini = RoundedCornerShape(8.dp)
/** 搜索框：40dp 高、13dp 圆角（不再是抢眼的胶囊） */
val SearchFieldShape = RoundedCornerShape(13.dp)

