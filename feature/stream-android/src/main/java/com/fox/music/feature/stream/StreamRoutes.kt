package com.fox.music.feature.stream

/**
 * 音流模式路由常量。在 App 的 NavGraph 中加一行即可接入：
 *   composable(StreamRoutes.STREAM) { StreamRoute() }
 * 入口按钮（如首页「音流」Tab）跳转 Navigation.findNavController().navigate(StreamRoutes.STREAM)
 */
object StreamRoutes {
    const val STREAM = "stream"
}
