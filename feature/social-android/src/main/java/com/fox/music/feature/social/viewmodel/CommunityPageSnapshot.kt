package com.fox.music.feature.social.viewmodel

import com.fox.music.core.common.cache.TabPageCache
import com.fox.music.core.common.cache.TabPageKeys
import com.fox.music.core.model.social.Post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 社区广场的「接口快照」：分页首屏请求回来前先渲染这一批帖子，
 * 避免切回社区 Tab 时整页空一下。
 *
 * 只存首页若干条（够铺满一屏即可），不整批留存，避免内存里堆一长串帖子。
 */
@Serializable
internal data class CommunityPageSnapshot(
    val posts: List<Post>,
)

private val communitySnapshotJson = Json { ignoreUnknownKeys = true }

/** 注册社区广场快照的磁盘编解码器（冷启动 / 无网络时解析上一进程落盘的快照）。 */
@Suppress("UNCHECKED_CAST")
fun registerCommunityPageCacheCodec() {
    TabPageCache.register(
        key = TabPageKeys.COMMUNITY,
        encode = { value ->
            communitySnapshotJson.encodeToString(
                CommunityPageSnapshot.serializer(),
                value as CommunityPageSnapshot,
            )
        },
        decode = { raw ->
            runCatching {
                communitySnapshotJson.decodeFromString(CommunityPageSnapshot.serializer(), raw)
            }.getOrNull()
        },
    )
}
