package com.fox.music.iosshared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import platform.Foundation.NSUserDefaults

/**
 * 单聊维度的本地设置（iOS 专用）—— 对齐 Android 聊天信息页
 * （ChatSettingsScreen + ChatSettingsViewModel）的五类能力：
 *
 *  1. 消息免打扰：本地开关。Android 存在 DataStore；iOS 存 NSUserDefaults。
 *     被 `InAppNotificationCenter.present` 消费 —— 被免打扰的会话不再弹前台横幅。
 *  2. 置顶聊天：纯本地状态（Android 也只写 Room，后端无接口），落到
 *     [FoxLocalChatStore] 的会话表，并 [FoxChatRefresh.bumpConversations] 让列表重排。
 *  3. 聊天背景：相册图片由 Swift 侧复制进 Application Support 后把**绝对路径**存这里；
 *     聊天页（FoxChatDetailScreen）观察 [revision] 把路径喂给 ChatThreadContent 渲染。
 *  4. 清空聊天记录并删除对话：本地删除会话 + 消息（同 Android clearChatHistory 语义）。
 *
 * [revision] 是设置变更总线：聊天页 Compose VC 常驻，push 设置页再返回不会重建，
 * 必须靠它把背景等变化推回聊天页。
 */
object FoxChatSettings {

    private const val MUTE_KEY_PREFIX = "fox_chat_muted_"
    private const val BACKGROUND_KEY_PREFIX = "fox_chat_bg_"

    private val defaults: NSUserDefaults get() = NSUserDefaults.standardUserDefaults

    var revision by mutableIntStateOf(0)
        private set

    fun bump() {
        revision++
    }

    // ---- 消息免打扰 ----

    fun isMuted(peerUserId: Long): Boolean =
        defaults.boolForKey(MUTE_KEY_PREFIX + peerUserId)

    fun setMuted(peerUserId: Long, muted: Boolean) {
        defaults.setBool(muted, forKey = MUTE_KEY_PREFIX + peerUserId)
        bump()
    }

    // ---- 聊天背景 ----

    fun backgroundPath(peerUserId: Long): String? =
        defaults.stringForKey(BACKGROUND_KEY_PREFIX + peerUserId)
            ?.takeIf { it.isNotBlank() }

    fun setBackgroundPath(peerUserId: Long, path: String?) {
        val key = BACKGROUND_KEY_PREFIX + peerUserId
        if (path.isNullOrBlank()) {
            defaults.removeObjectForKey(key)
        } else {
            defaults.setObject(path, forKey = key)
        }
        bump()
    }

    // ---- 置顶（本地会话表） ----

    fun isPinned(peerUserId: Long): Boolean =
        FoxLocalChatStore.loadConversations().any { it.user.id == peerUserId && it.isPinned }

    fun setPinned(peerUserId: Long, pinned: Boolean) {
        val updated = FoxLocalChatStore.loadConversations().map { conversation ->
            if (conversation.user.id == peerUserId) conversation.copy(isPinned = pinned) else conversation
        }
        FoxLocalChatStore.saveConversations(updated)
        FoxChatRefresh.bumpConversations()
        bump()
    }

    // ---- 清空聊天记录并删除对话 ----

    fun clearChat(peerUserId: Long) {
        FoxLocalChatStore.deleteConversation(peerUserId)
        FoxChatRefresh.bumpConversations()
        bump()
    }
}
