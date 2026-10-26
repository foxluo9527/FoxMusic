package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 全站底部弹层的**唯一**公共容器。
 *
 * 建立原因（2026-10 全局弹窗体系收口）：在此之前全仓 15+ 个 sheet 各自手写
 * ModalBottomSheet，顶圆角实测 16 / 20 / 22 / 28dp 四种并存，多数连 drag handle
 * 都没有，容器色也各写各的。唯一一份写得比较完整的 handle 还被锁在
 * `LyricSettingsSheet` 里当 private，别的模块拿不到，只能复制。
 *
 * 现在统一由这里出口：
 * - **22dp 顶圆角**（不是 16 / 20 / 28）；
 * - drag handle 32×4 居中，默认开启；
 * - 容器底走语义 [MaterialTheme.colorScheme].surface —— 浅色 #FFFDF9、深色 #1E1A15，
 *   深色模式自动对齐，**不再写死浅纸面色**；
 * - 拖拽下拉关闭由 ModalBottomSheet 原生提供。
 *
 * 迁移姿势：把原来的 `ModalBottomSheet(onDismissRequest=..., sheetState=...) { ... }`
 * 整段替换成 `FoxSheet(onDismissRequest = ...) { ... }`，内部 lambda 内容一行不用改
 * （content 同样是 [ColumnScope]，Column API 全部可照搬）。已经在壳外面套了
 * `navigationBarsPadding()` 的页面照旧保留即可。
 *
 * @param showDragHandle 关掉的情况仅限 sheet 内部自己画了标题栏 handle 的旧页面过渡期
 * @param containerColor 容器底，默认就是 semantic surface。**一般不该改**：只有沉浸式深色页
 *   （音流那种整屏走 #1C1C1E 的场景）才需要显式传，放在这里让「偏离令牌」这件事在调用点一眼可见。
 * @param scrimColor 遮罩色。带自定义手柄 animation / predictive back 的页面会调它，其余用 M3 默认
 * @param properties 透传给 ModalBottomSheet；典型用途是 shouldDismissOnBackPress = false 让页面自己接管返回手势
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoxSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    showDragHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dragHandle: (@Composable () -> Unit)? = if (showDragHandle) {
        { FoxSheetDragHandle() }
    } else {
        null
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = FoxSheetTopShape,
        // 浮层底默认走语义 surface：浅色 #FFFDF9、深色 #1E1A15，深色模式自动对齐
        containerColor = containerColor,
        scrimColor = scrimColor,
        properties = properties,
        dragHandle = dragHandle,
        modifier = modifier,
        content = content,
    )
}

/** sheet 顶圆角常量，22dp。需要单独拿 shape 拼 Border/Painter 时用这个。 */
val FoxSheetTopShape: RoundedCornerShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

/**
 * 32×4 圆角 drag handle，居中。
 *
 * 深色下用 #4A4239：#D5CEC2 在 #1E1A15 底上对比过强，会像一条高亮条。
 */
@Composable
fun FoxSheetDragHandle() {
    val handleColor = if (isSystemInDarkTheme()) Color(0xFF4A4239) else Color(0xFFD5CEC2)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(CircleShape)
                .background(handleColor),
        )
    }
}
