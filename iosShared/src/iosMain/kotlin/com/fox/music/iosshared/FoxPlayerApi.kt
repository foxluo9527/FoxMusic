package com.fox.music.iosshared

import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.domain.lyric.OpenLyricHttp
import com.fox.music.core.domain.lyric.OpenLyricResponse
import com.fox.music.core.domain.lyric.OpenLyricSources
import com.fox.music.core.model.music.LyricCandidateRanker
import com.fox.music.core.model.music.LyricHit
import com.fox.music.core.model.music.LyricQuery
import com.fox.music.core.model.music.LyricTextNormalizer
import com.fox.music.core.model.music.LyricsParser
import com.fox.music.core.model.music.ScoredLyricHit
import com.fox.music.core.network.model.MusicDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    private val scope = foxMainScope()
    private val api = FoxMusicApi()
    private val openLyrics by lazy {
        OpenLyricSources(object : OpenLyricHttp {
            override suspend fun getText(url: String, headers: Map<String, String>): OpenLyricResponse {
                val (code, body) = api.getExternalText(url, headers)
                return OpenLyricResponse(code, body)
            }
        })
    }

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

    /** 拉某条一级评论下的回复分页。onResult(list, total)。 */
    fun fetchCommentReplies(
        commentId: Long,
        page: Int,
        limit: Int,
        onResult: (List<FoxComment>, Int) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                val paged = api.fetchMusicCommentReplies(commentId, page, limit)
                onResult(paged.list.map { it.toFoxComment() }, paged.total)
            } catch (e: Exception) {
                println("FoxPlayerApi: 拉回复失败 ${e.message}")
                onError(e.message ?: "加载回复失败")
            }
        }
    }

    /**
     * 通知跳转：把音乐评论 ID 解析成歌曲 ID（对齐 Android ResolveMusicCommentNavigationUseCase）。
     * 后端通知 targetId 取根评论 ID，replies 项里的 music_id 即所属歌曲。
     */
    fun resolveMusicComment(
        commentId: Long,
        onResult: (FoxMusicCommentNavigation) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                val paged = api.fetchMusicCommentReplies(commentId, page = 1, limit = 20)
                val musicId = paged.list.firstNotNullOfOrNull { it.musicId }
                if (musicId != null) {
                    onResult(FoxMusicCommentNavigation(musicId = musicId, commentId = commentId, rootCommentId = commentId))
                } else {
                    onError("无法定位音乐评论")
                }
            } catch (e: Exception) {
                println("FoxPlayerApi: 解析音乐评论失败 ${e.message}")
                onError(e.message ?: "无法定位音乐评论")
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

    // ---------- UI 测试自动登录 ----------

    /**
     * UI 测试专用：保证固定测试账号处于已登录态，成功后写入 [FoxAuthStore]。
     *
     * 依赖链：先注册（账号已存在时后端返回 success=false，属预期，忽略），
     * 再登录 —— 这样无论模拟器是全新的还是残留了别的登录态，依赖登录的用例
     * （好友 / 社区等）都能拿到确定性状态，不再依赖"上次手动登过 root"。
     * Swift 侧在收到 onResult(true) 后调用 AuthStore.persistFromKotlin() 落 Keychain。
     */
    fun autoLoginForUiTest(
        username: String,
        password: String,
        email: String,
        onResult: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val registered = try {
                api.register(username, password, email)
            } catch (e: Exception) {
                println("FoxPlayerApi: UI 测试注册失败(忽略) ${e.message}")
                null
            }
            val result = if (registered?.success == true) {
                registered
            } else {
                try {
                    api.login(username, password)
                } catch (e: Exception) {
                    println("FoxPlayerApi: UI 测试登录失败 ${e.message}")
                    onError(e.message ?: "自动登录失败")
                    return@launch
                }
            }
            val user = result.user
            val token = result.token
            if (result.success && user != null && !token.isNullOrBlank()) {
                FoxAuthStore.update(token = token, refreshToken = result.refreshToken, user = user)
                onResult(true)
            } else {
                onError(result.message ?: "自动登录失败")
            }
        }
    }

    // ---------- 歌词纠正（自定义歌词源）----------

    /**
     * 歌词纠正候选：网易云只留相似度最高的前几条，再并上 LRCLIB / Megalobiz / lyrics.ovh。
     * 每个来源截断后按歌词正文去重。[note] 非空表示有来源暂时失败。
     */
    fun searchLyrics(
        keyword: String,
        artist: String,
        durationMs: Long,
        onResult: (List<FoxLyricCandidate>, String?) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                val query = LyricQuery(
                    title = keyword.trim(),
                    artist = artist,
                    durationMs = durationMs,
                )
                val failed = mutableListOf<String>()
                val groups = mutableListOf<List<ScoredLyricHit>>()
                suspend fun capture(name: String, block: suspend () -> List<ScoredLyricHit>) {
                    runCatching { block() }
                        .onSuccess { groups += listOf(it) }
                        .onFailure { failed += name }
                }
                coroutineScope {
                    listOf(
                        async { capture("网易云") { neteaseCandidates(query) } },
                        async { capture("LRCLIB") { openLyrics.lrclib(query) } },
                        async { capture("Megalobiz") { openLyrics.megalobiz(query) } },
                        async { capture("lyrics.ovh") { openLyrics.lyricsOvh(query) } },
                    ).awaitAll()
                }
                val note = failed.takeIf { it.isNotEmpty() }
                    ?.joinToString("、")
                    ?.let { "部分来源暂不可用：$it" }
                onResult(LyricCandidateRanker.merge(groups).map { it.toFoxCandidate() }, note)
            } catch (e: Exception) {
                println("FoxPlayerApi: 搜歌词失败 ${e.message}")
                onError(e.message ?: "搜索失败")
            }
        }
    }

    private suspend fun neteaseCandidates(query: LyricQuery): List<ScoredLyricHit> {
        if (query.title.isBlank()) return emptyList()
        val hits = api.searchThirdPartyMusic(
            platform = "netease",
            keyword = query.title,
            page = 1,
            limit = LyricCandidateRanker.NETEASE_FETCH,
        ).mapNotNull { it.toLyricHit() }
        return LyricCandidateRanker.select(query, hits, LyricCandidateRanker.NETEASE_LIMIT)
    }

    private fun MusicDto.toLyricHit(): LyricHit? {
        val raw = lyrics?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val prepared = LyricTextNormalizer.prepare(raw, duration)
        if (!prepared.ok) return null
        return LyricHit(
            source = "netease",
            sourceId = sourceId ?: id,
            title = title,
            artist = artists.joinToString(" / ") { it.name },
            album = album?.title,
            durationMs = duration,
            lyrics = prepared.lrc,
            lyricsTrans = lyricsTrans?.takeIf { it.isNotBlank() },
            synced = prepared.synced,
        )
    }

    private fun ScoredLyricHit.toFoxCandidate(): FoxLyricCandidate = FoxLyricCandidate(
        id = listOf(hit.source, hit.sourceId ?: hit.title, hit.artist).joinToString(":"),
        title = hit.title.ifBlank { "未知歌曲" },
        artist = hit.artist.ifBlank { "未知艺术家" },
        lyrics = hit.lyrics,
        lyricsTrans = hit.lyricsTrans,
        lineCount = LyricsParser.parseLrc(hit.lyrics).size,
        source = hit.source,
        attributionUrl = hit.attributionUrl,
        synced = hit.synced,
    )

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
    /** 非空 = 这条是回复，值为根评论 ID；null = 一级评论。 */
    val parentId: Long? = null,
)

/** 音乐评论通知解析结果：评论 → 所属歌曲 + 根评论（用于打开评论面板并定位楼中楼）。 */
data class FoxMusicCommentNavigation(
    val musicId: Long,
    val commentId: Long,
    val rootCommentId: Long,
)

/** 歌词候选（歌词纠正）。 */
data class FoxLyricCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val lyrics: String,
    val lyricsTrans: String?,
    val lineCount: Int,
    val source: String = "",
    val attributionUrl: String? = null,
    val synced: Boolean = true,
)

/** 昵称优先级与 Android 的 displayAuthorName 一致：nickname → username → 匿名。 */
private fun MusicCommentDto.toFoxComment(): FoxComment {
    val latest = replies.firstOrNull()
    // 自身就是回复（在楼中楼列表里）：reply_to 是它直接回复的对象；
    // 一级评论：展示后端内嵌的最新一条回复。
    val selfReplyTo = replyTo?.let {
        it.nickname?.takeIf { n -> n.isNotBlank() } ?: it.username
    }
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
        replyToName = selfReplyTo ?: latest?.replyTo?.let {
            it.nickname?.takeIf { n -> n.isNotBlank() } ?: it.username
        },
        latestReply = if (parentId != null) null else latest?.content,
        latestReplyAuthor = if (parentId != null) null else latest?.let {
            it.userNickname?.takeIf { n -> n.isNotBlank() } ?: it.userName
        },
        parentId = parentId,
    )
}
