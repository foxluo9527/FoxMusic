package com.fox.music.core.common.mvi

/**
 * 列表滚动位置的跨组合存档。
 *
 * 为什么需要它：进详情页时列表页 Composable 会被移出组合，`rememberLazyListState()`
 * 随组合销毁而丢失，返回时重新创建就会滚回顶部。ViewModel 不会随导航重建，
 * 所以把「首个可见项 + 偏移」存在这里，Composable 重新进入组合时用它做初始值即可复位。
 *
 * 这是普通字段而不是 UiState 字段：滚动每帧都在变，进 State 会白白触发整屏重组。
 */
class ListScrollStateHolder {

    private var index = 0
    private var offset = 0

    /** 供 `rememberLazyListState / rememberLazyGridState` 的 initial* 参数使用 */
    fun firstVisibleItemIndex(): Int = index

    fun firstVisibleItemScrollOffset(): Int = offset

    fun save(index: Int, offset: Int) {
        // 已滚动到最顶端时归零，避免记录一个「刚好停在第 0 项偏移 7px」的无意义位置
        if (index <= 0) {
            this.index = 0
            this.offset = 0
            return
        }
        this.index = index
        this.offset = offset
    }
}
