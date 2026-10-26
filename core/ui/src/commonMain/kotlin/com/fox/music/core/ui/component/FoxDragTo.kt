package com.fox.music.core.ui.component

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize

/**
 * 「按下即生效、随后逐帧跟随、抬手结束」的拖拽/点按手势，取色盘与滑杆共用。
 *
 * 不用 `detectDragGestures` + `detectTapGestures` 叠两层 pointerInput：那两个手势
 * 检测器各自消费事件，叠在一起会出现「点一下没反应 / 拖到一半丢手」。这里直接读
 * 原始指针事件，Tap 与 Drag 是同一条代码路径，天然不会互相抢。
 */
internal fun Modifier.foxDragTo(
    onPosition: (Offset, Size) -> Unit,
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            onPosition(down.position, size.toSize())
            down.consume()
            var event = awaitPointerEvent()
            while (event.changes.any { it.pressed }) {
                event.changes.forEach { change ->
                    if (change.pressed) {
                        onPosition(change.position, size.toSize())
                        change.consume()
                    }
                }
                event = awaitPointerEvent()
            }
        }
    }
}

internal fun IntSize.toSize(): Size = Size(width.toFloat(), height.toFloat())
