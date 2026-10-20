package com.fox.music.iosshared

import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.user.User
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

/**
 * iOS 本地聊天存储（JSON 文件，Application Support/chat/）。
 *
 * 设计对齐 Android 的 Room ChatRepository，但用 JSON 而非 SQL：
 *  - 每条会话一个文件：chat/u_{ownerId}/messages/{peerUserId}.json → List<Message>
 *  - 会话列表：chat/u_{ownerId}/conversations.json → List<ChatConversation>
 *  - 发送队列（outbox）：chat/u_{ownerId}/outbox.json → List<Message>（状态 SENDING/FAILED）
 *
 * ⚠️ **账号隔离（与 Android Room 的 `ownerUserId` 主键对齐）**：
 * 所有文件都挂在 `chat/u_{当前登录用户id}/` 下。未登录时 [ownerDir] 返回 null
 * → 读恒为空、写恒为 no-op，**绝不会落到公共目录**，因此换账号登录不会看到上一个
 * 账号的会话/消息，登出也不会残留可见数据。
 *
 * 升级兼容：旧版本是扁平目录（chat/conversations.json 等）。首次带登录态访问时，
 * 若 `chat/.owner` 标记不存在，则把旧文件归给**当前**账号并落标记；此后其它账号
 * 登录不再认领（旧文件留在原地但对任何人都不可见）。
 *
 * 文件读写沿用项目约定：NSFileManager.contentsAtPath 读，POSIX fopen/fwrite 写。
 */
/**
 * 服务端未读快照里「某个发送方」的未读聚合结果。
 *
 * 带上发送方资料是因为快照可能要**新建**会话行（对方第一次发消息、本地还没有这一行），
 * 而 MessageDto 里正好有 sender_nickname / sender_avatar。
 */
data class FoxPeerUnread(
    val peer: User,
    val count: Int,
    val lastMessage: Message,
)

@OptIn(ExperimentalForeignApi::class)
object FoxLocalChatStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val chatDir: NSURL? by lazy {
        runCatching {
            val base = NSFileManager.defaultManager.URLForDirectory(
                NSApplicationSupportDirectory, NSUserDomainMask, null, true, null,
            )
            val dir = base?.URLByAppendingPathComponent("chat", isDirectory = true)
            dir?.path?.let { NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null) }
            dir
        }.getOrNull()
    }

    /**
     * 当前登录账号的私有目录 `chat/u_{ownerId}/`。
     *
     * **未登录时返回 null** —— 这是账号隔离的支点：所有读写都经过它，
     * null 意味着「无归属」，读空、写丢弃，绝不落到公共目录。
     */
    private fun ownerDir(): NSURL? {
        val ownerId = FoxAuthStore.user?.id
            ?.takeIf { it > 0L }
            ?: return null
        val dir = chatDir?.URLByAppendingPathComponent("u_$ownerId", isDirectory = true)
            ?: return null
        dir.path?.let { NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null) }
        migrateLegacyFilesIfNeeded(into = dir)
        return dir
    }

    /** 旧版本扁平目录（chat/conversations.json）→ 归给当前账号，仅认领一次。 */
    private var legacyMigrationAttempted = false

    private fun migrateLegacyFilesIfNeeded(into: NSURL) {
        if (legacyMigrationAttempted) return
        legacyMigrationAttempted = true
        val fm = NSFileManager.defaultManager
        val root = chatDir?.path ?: return
        val destRoot = into.path ?: return
        val marker = chatDir?.URLByAppendingPathComponent(".owner", isDirectory = false)?.path
        if (marker != null && fm.fileExistsAtPath(marker)) return
        runCatching {
            listOf("conversations.json", "outbox.json").forEach { name ->
                val src = "$root/$name"
                if (fm.fileExistsAtPath(src) && !fm.fileExistsAtPath("$destRoot/$name")) {
                    fm.moveItemAtPath(src, "$destRoot/$name", null)
                }
            }
            val legacyMessages = "$root/messages"
            if (fm.fileExistsAtPath(legacyMessages) && !fm.fileExistsAtPath("$destRoot/messages")) {
                fm.moveItemAtPath(legacyMessages, "$destRoot/messages", null)
            }
        }
        // 落标记：之后其它账号登录不再认领旧文件（避免把 A 的数据归给 B）
        marker?.let { fm.createFileAtPath(it, contents = null, attributes = null) }
    }

    private fun messagesFile(peerUserId: Long): NSURL? =
        ownerDir()?.URLByAppendingPathComponent("messages", isDirectory = true)?.also {
            it.path?.let { p -> NSFileManager.defaultManager.createDirectoryAtPath(p, true, null, null) }
        }?.URLByAppendingPathComponent("$peerUserId.json", isDirectory = false)

    private fun conversationsFile(): NSURL? =
        ownerDir()?.URLByAppendingPathComponent("conversations.json", isDirectory = false)

    private fun outboxFile(): NSURL? =
        ownerDir()?.URLByAppendingPathComponent("outbox.json", isDirectory = false)

    // ---- 消息 ----

    fun loadMessages(peerUserId: Long): List<Message> =
        readList(messagesFile(peerUserId)) ?: emptyList()

    fun saveMessages(peerUserId: Long, messages: List<Message>) {
        writeList(messagesFile(peerUserId), messages)
    }

    /** 单条消息 upsert（按 localId 优先，其次 server id 去重）。 */
    fun upsertMessage(peerUserId: Long, message: Message) {
        val existing = loadMessages(peerUserId).toMutableList()
        val idx = existing.indexOfFirst { sameMessage(it, message) }
        if (idx >= 0) existing[idx] = message else existing.add(message)
        existing.sortBy { parseTime(it.createdAt) }
        saveMessages(peerUserId, existing)
    }

    fun deleteMessage(peerUserId: Long, localId: String?) {
        if (localId == null) return
        val existing = loadMessages(peerUserId).filterNot { it.localId == localId }
        saveMessages(peerUserId, existing)
    }

    // ---- 会话 ----

    fun loadConversations(): List<ChatConversation> =
        readList(conversationsFile()) ?: emptyList()

    fun saveConversations(conversations: List<ChatConversation>) {
        writeList(conversationsFile(), conversations)
    }

    fun upsertConversation(conversation: ChatConversation) {
        val existing = loadConversations().toMutableList()
        val idx = existing.indexOfFirst { it.user.id == conversation.user.id }
        if (idx >= 0) {
            // 身份字段只增不删：聊天页进页瞬间（资料还没拉到）/快速退出（拉取协程被取消）
            // /发消息成功回写与资料拉取竞态时，都可能带着 null 昵称落盘。整体替换会把
            // 已知的「张河源」抹成兜底「用户」、头像抹成占位图。新值为空时沿用旧值。
            val oldUser = existing[idx].user
            val newUser = oldUser.copy(
                username = conversation.user.username.takeIf { it.isNotBlank() } ?: oldUser.username,
                nickname = conversation.user.nickname?.takeIf { it.isNotBlank() } ?: oldUser.nickname,
                avatar = conversation.user.avatar?.takeIf { it.isNotBlank() } ?: oldUser.avatar,
            )
            existing[idx] = conversation.copy(user = newUser)
        } else {
            existing.add(0, conversation)
        }
        saveConversations(existing)
    }

    // ---- 会话未读 ----

    /**
     * 未读 +N：会话不存在时用 [lastMessage] / [peer] 新建一行（「只有未读、从未聊过」
     * 的人也要出现在会话列表里）。
     *
     * ⚠️ **只能累加，不能覆盖**：服务端未读接口 `api/messages/unread/{id}` 是
     * **一次性消费**（查完立刻 `is_notified=1`），同一条消息第二次拉不到。
     * 因此它拿不到「全量未读快照」—— 用它做绝对值覆盖，会把上一次已经消费掉、
     * 但用户还没读的其它会话误清零（A 读完 → 重算 → B 的未读被抹掉）。
     */
    fun bumpUnread(peer: User, delta: Int, lastMessage: Message) {
        if (delta <= 0) return
        val conversations = loadConversations().toMutableList()
        val idx = conversations.indexOfFirst { it.user.id == peer.id }
        val base = conversations.getOrNull(idx) ?: ChatConversation(
            id = peer.id,
            user = peer,
            lastMessage = lastMessage,
            unreadCount = 0,
            updatedAt = lastMessage.createdAt,
        )
        val updated = base.copy(
            user = mergeUser(base.user, peer),
            unreadCount = base.unreadCount + delta,
            lastMessage = lastMessage,
            updatedAt = lastMessage.createdAt ?: base.updatedAt,
        )
        if (idx >= 0) conversations[idx] = updated else conversations.add(0, updated)
        saveConversations(conversations)
    }

    /** 批量版 [bumpUnread]：服务端补拉回来的一批未读按发送方聚合后累加。 */
    fun accumulateUnread(items: List<FoxPeerUnread>) {
        if (items.isEmpty()) return
        val conversations = loadConversations().toMutableList()
        items.forEach { item ->
            val idx = conversations.indexOfFirst { it.user.id == item.peer.id }
            val base = conversations.getOrNull(idx) ?: ChatConversation(
                id = item.peer.id,
                user = item.peer,
                lastMessage = item.lastMessage,
                unreadCount = 0,
                updatedAt = item.lastMessage.createdAt,
            )
            val updated = base.copy(
                user = mergeUser(base.user, item.peer),
                unreadCount = base.unreadCount + item.count,
                lastMessage = item.lastMessage,
                updatedAt = item.lastMessage.createdAt ?: base.updatedAt,
            )
            if (idx >= 0) conversations[idx] = updated else conversations.add(0, updated)
        }
        saveConversations(conversations)
    }

    /** 各会话未读之和 —— Tab 红点与会话列表用同一个口径，不会互相打架。 */
    fun totalUnread(): Int = loadConversations().sumOf { it.unreadCount }

    /** 进入/读完某个会话：未读清零。返回是否真的改了（避免无谓的会话列表刷新）。 */
    fun markConversationRead(peerUserId: Long): Boolean {
        val conversations = loadConversations().toMutableList()
        val idx = conversations.indexOfFirst { it.user.id == peerUserId }
        if (idx < 0 || conversations[idx].unreadCount == 0) return false
        conversations[idx] = conversations[idx].copy(unreadCount = 0)
        saveConversations(conversations)
        return true
    }

    /** 身份字段只增不删：新值为空时沿用旧值（与 [upsertConversation] 同款保护）。 */
    private fun mergeUser(old: User, incoming: User): User = old.copy(
        username = incoming.username.takeIf { it.isNotBlank() } ?: old.username,
        nickname = incoming.nickname?.takeIf { it.isNotBlank() } ?: old.nickname,
        avatar = incoming.avatar?.takeIf { it.isNotBlank() } ?: old.avatar,
    )

    fun deleteConversation(peerUserId: Long) {
        val existing = loadConversations().filterNot { it.user.id == peerUserId }
        saveConversations(existing)
        // 同时清掉该会话的本地消息
        messagesFile(peerUserId)?.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
    }

    // ---- 发送队列（outbox） ----

    fun loadOutbox(): List<Message> = readList(outboxFile()) ?: emptyList()

    fun saveOutbox(messages: List<Message>) {
        writeList(outboxFile(), messages)
    }

    fun addToOutbox(message: Message) {
        val existing = loadOutbox().toMutableList()
        existing.add(message)
        saveOutbox(existing)
    }

    fun updateOutbox(localId: String, updater: (Message) -> Message) {
        val existing = loadOutbox().map { if (it.localId == localId) updater(it) else it }
        saveOutbox(existing)
    }

    fun removeFromOutbox(localId: String?) {
        if (localId == null) return
        saveOutbox(loadOutbox().filterNot { it.localId == localId })
    }

    // ---- 工具 ----

    private fun sameMessage(a: Message, b: Message): Boolean {
        // ⚠️ 服务端 id 优先：同一条消息在不同阶段 localId 会变（乐观发送是
        // "local_x"，发送成功回写仍保留 localId，历史拉取则是 "server_N"），
        // 若先比 localId 会漏判 → 历史加载后出现重复条目。
        if (a.id > 0L && b.id > 0L) return a.id == b.id
        if (a.localId != null && b.localId != null) return a.localId == b.localId
        return false
    }

    private fun parseTime(s: String?): Long =
        s?.replace("T", " ")?.take(19)?.let {
            runCatching {
                // 简化解析：yyyy-MM-dd HH:mm:ss → 直接比较字符串也可，这里转毫秒粗略排序
                it.filter { c -> c.isDigit() }.toLongOrNull()
            }.getOrNull()
        } ?: 0L

    @OptIn(ExperimentalForeignApi::class)
    private inline fun <reified T> readList(file: NSURL?): List<T>? {
        val path = file?.path ?: return null
        val data = NSFileManager.defaultManager.contentsAtPath(path) ?: return null
        val ptr = data.bytes ?: return null
        val bytes = ptr.reinterpret<ByteVar>().readBytes(data.length.toInt())
        val text = bytes.decodeToString()
        return runCatching { json.decodeFromString<List<T>>(text) }.getOrNull()
    }

    @OptIn(ExperimentalForeignApi::class)
    private inline fun <reified T> writeList(file: NSURL?, items: List<T>) {
        val path = file?.path ?: return
        runCatching {
            val bytes = json.encodeToString(items).encodeToByteArray()
            val f = fopen(path, "w") ?: return@runCatching
            try {
                bytes.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), f)
                }
            } finally {
                fclose(f)
            }
        }
    }
}
