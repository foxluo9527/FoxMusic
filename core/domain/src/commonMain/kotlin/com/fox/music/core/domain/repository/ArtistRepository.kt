package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.ArtistDetail
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.PagedData

interface ArtistRepository {

    /**
     * @param slim 只回列表页用得到的字段（id/名称/头像/封面/歌曲数）。
     *   「全部歌手」要翻完 5000+ 行，开 slim 能把传输量砍掉大半；服务端只在
     *   sort 为 id/latest 时接受 slim，其他排序会自动按全字段返回。
     */
    suspend fun getArtistList(
        page: Int = 1,
        limit: Int = 20,
        keyword: String? = null,
        tagId: Long? = null,
        sort: String? = null,
        slim: Boolean = false,
    ): Result<PagedData<Artist>>

    suspend fun getArtistDetail(id: Long): Result<ArtistDetail>

    suspend fun getArtistMusics(
        id: Long,
        page: Int = 1,
        limit: Int = 20,
        sort: String? = null
    ): Result<PagedData<Music>>

    suspend fun toggleFavorite(id: Long): Result<Unit>
}
