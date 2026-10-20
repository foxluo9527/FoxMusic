package com.fox.music.iosshared

import com.fox.music.core.model.chat.Message
import com.fox.music.feature.chat.util.parseTimestampMillis

/**
 * 「查找聊天记录」的单条结果：按会话（对方用户）聚合。
 *
 * 字段刻意拍平成基本类型 + 可空字符串，方便导出到 Swift（FoxShared 框架）直接渲染。
 */
data class FoxChatSearchResult(
    val userId: Long,
    val nickname: String?,
    val avatar: String?,
    val lastMessageContent: String,
    val matchCount: Int,
)

/**
 * 会话内搜索的单条命中（对齐 Android UserChatSearchScreen 的消息级列表）。
 */
data class FoxUserChatHit(
    val messageId: Long,
    val content: String,
    val createdAt: String?,
)

/**
 * iOS 端聊天记录本地搜索 —— 对齐 Android ChatRepository.searchMessages：
 *
 *  - 数据源同样是**本地消息**（Android 查 Room，iOS 查 [FoxLocalChatStore] JSON）；
 *  - 匹配规则同样是消息内容 `contains(query)`（Room 的 `content LIKE '%q%''`，
 *    媒体消息的内容是文件名，同样能被搜到）；
 *  - 结果同样按会话聚合：对方用户 + 最近一条命中 + 命中条数；
 *  - 结果按「最近命中时间」倒序，与 Android DAO 按消息 id 倒序列出的体感一致。
 *
 * 注意：Android 入口虽然从某个聊天的设置页进来，但搜索范围是**全部会话**，
 * iOS 保持这一行为。
 */
object FoxChatSearch {

    fun search(query: String): List<FoxChatSearchResult> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return emptyList()

        data class Hit(val timestamp: Long, val result: FoxChatSearchResult)

        return FoxLocalChatStore.loadConversations().mapNotNull { conversation ->
            val peer = conversation.user
            val matched: List<Message> = FoxLocalChatStore.loadMessages(peer.id)
                .filter { it.content.contains(keyword, ignoreCase = true) }
            if (matched.isEmpty()) return@mapNotNull null

            val latest = matched.maxByOrNull { parseTimestampMillis(it.createdAt) ?: 0L }
                ?: return@mapNotNull null
            Hit(
                timestamp = parseTimestampMillis(latest.createdAt) ?: 0L,
                result = FoxChatSearchResult(
                    userId = peer.id,
                    nickname = peer.nickname ?: peer.username,
                    avatar = peer.avatar,
                    lastMessageContent = latest.content,
                    matchCount = matched.size,
                ),
            )
        }.sortedByDescending { it.timestamp }.map { it.result }
    }

    /**
     * 会话内搜索 —— 对齐 Android UserChatSearchViewModel：
     * 只扫指定会话的本地消息，返回逐条命中（按时间倒序）。
     */
    fun searchInConversation(peerUserId: Long, query: String): List<FoxUserChatHit> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return emptyList()
        return FoxLocalChatStore.loadMessages(peerUserId)
            .filter { it.content.contains(keyword, ignoreCase = true) }
            .sortedByDescending { parseTimestampMillis(it.createdAt) ?: 0L }
            .map {
                FoxUserChatHit(
                    messageId = it.id,
                    content = it.content,
                    createdAt = it.createdAt,
                )
            }
    }
}
