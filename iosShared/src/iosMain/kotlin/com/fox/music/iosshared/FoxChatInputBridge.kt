package com.fox.music.iosshared

/**
 * 聊天输入桥：Swift 侧（PHPicker/AVAudioRecorder/UIDocumentPicker）把选好的媒体
 * 通过此桥交给当前活跃的 Kotlin 聊天页发送。
 *
 * 方向：
 *  - Kotlin 按钮 → Swift（makeChatDetailViewController 的 onPickMedia 等回调）
 *  - Swift 选完/录完 → 调此桥的 [notifyMediaResult] → Kotlin 聊天页发送
 *
 * 活跃聊天页在 LaunchedEffect 里注册 [onMediaResult]，销毁时置空。
 */
object FoxChatInputBridge {

    /** type: text/image/audio/file/video; url: 服务端返回的远程 URL；durationMs 仅语音/视频 */
    var onMediaResult: ((type: String, url: String, durationMs: Long?, fileName: String?) -> Unit)? = null

    fun notifyMediaResult(type: String, url: String, durationMs: Long?, fileName: String?) {
        onMediaResult?.invoke(type, url, durationMs, fileName)
    }
}
