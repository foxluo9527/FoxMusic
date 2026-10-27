package com.fox.music.iosshared

import com.fox.music.core.common.cache.ListCacheKeys
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

/**
 * iOS 列表 / 详情磁盘快照。key 规则与 Android [ListCacheKeys] 一致（账号 + 场景 + id + 分页），
 * 文件仍走 [FoxFirstPageCache]。
 */
internal object FoxSceneCache {
    private fun key(vararg parts: String): String =
        ListCacheKeys.key(FoxAuthStore.user?.id, *parts)

    suspend fun <T> load(
        deserializer: DeserializationStrategy<T>,
        vararg parts: String,
    ): T? = FoxFirstPageCache.load(key(*parts), deserializer)

    suspend fun <T> save(
        serializer: SerializationStrategy<T>,
        value: T,
        vararg parts: String,
    ) {
        FoxFirstPageCache.save(key(*parts), serializer, value)
    }

    fun remove(vararg parts: String) {
        FoxFirstPageCache.remove(key(*parts))
    }
}
