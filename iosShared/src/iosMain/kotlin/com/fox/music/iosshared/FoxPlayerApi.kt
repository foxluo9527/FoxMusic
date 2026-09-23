package com.fox.music.iosshared

import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.model.music.LyricsParser
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * 播放器相关的跨语言桥：收藏 / 评论 / 歌词纠正 / 播放上报。
 *
 * 为什么单独一个 object（而不是塞进 [FoxIosBridge]）：播放本身在 Swift（AVPlayer），
 * 这些是 Swift 侧要调的「服务端能力」，与 [FoxIosBridge] 负责的「把数据折成可渲染结构」
 * 职责不同，分开后也便于后续把接口下沉到 :core:network。
 *
 * 约定与 [FoxIosBridge] 一致：**不出现 suspend / Flow**，全部回调风格 ——
 * Kotlin 函数类型直接映射成 Swift 闭包，框架无需导出协程库。
 */
object FoxPlayerApi {

    private val scope = MainScope()
    private val api = FoxMusicApi()

    // ---------- 收藏 ----------

    /**
     * 切换歌曲收藏（取反式）。
     *
     * @param wasFavorite 调用前的收藏态（后端只返回"操作成功"，不回传新状态）
     * @param onResult 参数为**调用后**是否已收藏
     */
    fun toggleFavorite(
        musicId: Long,
        wasFavorite: Boolean,
        onResult: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                onResult(api.toggleFavorite(musicId, wasFavorite))
            } catch (e: Exception) {
                println("FoxPlayerApi: 收藏失败 ${e.message}")
                onError(e.message ?: "操作失败")
            }
        }
    }

    // ---------- 评论 ----------

    /** 拉一级评论分页。onResult(list, total)。 */
    fun fetchComments(
        musicId: Long,
        page: Int,
        limit: Int,
        onResult: (List<FoxComment>, Int) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                val paged = api.fetchMusicComments(musicId, page, limit)
                onResult(
                    paged.list.map { it.toFoxComment() },
                    paged.total,
                )
            } catch (e: Exception) {
                println("FoxPlayerApi: 拉评论失败 ${e.message}")
                onError(e.message ?: "加载评论失败")
            }
        }
    }

    /** 发表评论（parentId 非空即回复）。 */
    fun postComment(
        musicId: Long,
        content: String,
        parentId: Long?,
        onResult: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                onResult(api.postMusicComment(musicId, content, parentId))
            } catch (e: Exception) {
                println("FoxPlayerApi: 发评论失败 ${e.message}")
                onError(e.message ?: "发布失败")
            }
        }
    }

    /** 点赞/取消点赞（取反式，后端不回传新状态）。 */
    fun likeComment(
        commentId: Long,
        onResult: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                onResult(api.likeMusicComment(commentId))
            } catch (e: Exception) {
                println("FoxPlayerApi: 点赞失败 ${e.message}")
                onError(e.message ?: "操作失败")
            }
        }
    }

    // ---------- 歌词纠正（自定义歌词源）----------

    /**
     * 搜"同名歌曲的歌词"作为当前歌的替代歌词源。
     *
     * 与 Android `CustomLyricSearchPagingSource` 完全同一口径：
     * 走第三方平台（netease）搜关键词，只保留**标题完全一致**且带歌词的结果，
     * 否则用户会拿到一堆不相关的歌。
     */
    fun searchLyrics(
        keyword: String,
        targetTitle: String,
        page: Int,
        onResult: (List<FoxLyricCandidate>, Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                val normalized = targetTitle.trim().lowercase()
                val list = api.searchThirdPartyMusic("netease", keyword, page, 20)
                val matched = list
                    .filter {
                        it.title.trim().lowercase() == normalized &&
                            !it.lyrics.isNullOrBlank()
                    }
                    .map { dto ->
                        val lyric = dto.lyrics.orEmpty()
                        FoxLyricCandidate(
                            id = dto.id,
                            title = dto.title,
                            artist = dto.artists.joinToString(" / ") { it.name }.ifBlank { "未知艺术家" },
                            lyrics = lyric,
                            lyricsTrans = dto.lyricsTrans?.takeIf { it.isNotBlank() },
                            lineCount = LyricsParser.parseLrc(lyric).size,
                        )
                    }
                // 末条命中说明后面还有同名结果，可以继续翻页
                val lastMatches = list.lastOrNull()
                    ?.let { it.title.trim().lowercase() == normalized } ?: false
                onResult(matched, lastMatches && matched.isNotEmpty())
            } catch (e: Exception) {
                println("FoxPlayerApi: 搜歌词失败 ${e.message}")
                onError(e.message ?: "搜索失败")
            }
        }
    }

    // ---------- 播放上报（"最近在听"的数据来源）----------

    /**
     * 上报播放。Android 端由播放器内部上报；iOS 走同一接口。
     * 未登录时后端只累加播放量、不写历史，所以不必判断登录态。
     */
    fun recordPlay(musicId: Long, durationSeconds: Int, progressPercent: Int) {
        scope.launch {
            runCatching { api.recordPlay(musicId, durationSeconds, progressPercent) }
                .onFailure { println("FoxPlayerApi: 上报播放失败 ${it.message}") }
        }
    }
}

/** 评论：Swift 可直接消费的精简结构（不含 Kotlin 侧 DTO 的完整字段）。 */
data class FoxComment(
    val id: Long,
    val content: String,
    val authorName: String,
    val authorAvatar: String?,
    val likeCount: Int,
    val isLiked: Boolean,
    val replyCount: Int,
    val createdAt: String?,
    /** 回复的目标昵称（一级评论下挂的最新一条回复用到）。 */
    val replyToName: String?,
    /** 该一级评论下挂的最新一条回复内容（后端只带 1 条）。 */
    val latestReply: String?,
    val latestReplyAuthor: String?,
)

/** 歌词候选（歌词纠正）。 */
data class FoxLyricCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val lyrics: String,
    val lyricsTrans: String?,
    val lineCount: Int,
)

/** 昵称优先级与 Android 的 displayAuthorName 一致：nickname → username → 匿名。 */
private fun MusicCommentDto.toFoxComment(): FoxComment {
    val latest = replies.firstOrNull()
    return FoxComment(
        id = id,
        content = content,
        authorName = userNickname?.takeIf { it.isNotBlank() }
            ?: userName?.takeIf { it.isNotBlank() }
            ?: "匿名用户",
        authorAvatar = userAvatar?.let { MediaUrlResolver.resolve(it) },
        likeCount = likeCount,
        isLiked = isLike,
        replyCount = replyCount,
        createdAt = createdAt,
        replyToName = latest?.replyTo?.let {
            it.nickname?.takeIf { n -> n.isNotBlank() } ?: it.username
        },
        latestReply = latest?.content,
        latestReplyAuthor = latest?.let {
            it.userNickname?.takeIf { n -> n.isNotBlank() } ?: it.userName
        },
    )
}
