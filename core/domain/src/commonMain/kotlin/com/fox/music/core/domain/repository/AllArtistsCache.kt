package com.fox.music.core.domain.repository

import com.fox.music.core.model.music.Artist

/**
 * 「全部歌手」全量列表的本地快照。
 *
 * 接口放 domain、实现放 data：feature 模块只依赖 :core:domain，不能碰 :core:data-android。
 * 用途是让列表页先渲染快照（秒开），网络回来后再替换，而不是每次进页面都转圈等。
 */
interface AllArtistsCache {

    suspend fun read(): List<Artist>?

    suspend fun write(artists: List<Artist>)

    /** 快照写入时间（毫秒），用于判断要不要重新回源刷新 */
    suspend fun lastWriteAt(): Long
}
