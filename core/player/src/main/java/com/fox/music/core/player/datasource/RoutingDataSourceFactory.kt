package com.fox.music.core.player.datasource

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * 本地 file/content 直连上游，避免 [androidx.media3.datasource.cache.CacheDataSource]
 * 与在线播放共用 cache key 时在 seek 处回退 HTTP 导致 404。
 */
@UnstableApi
class RoutingDataSourceFactory(
    private val localFactory: DataSource.Factory,
    private val remoteFactory: DataSource.Factory,
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        RoutingDataSource(localFactory.createDataSource(), remoteFactory.createDataSource())

    private class RoutingDataSource(
        private val local: DataSource,
        private val remote: DataSource,
    ) : DataSource {

        private var active: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            local.addTransferListener(transferListener)
            remote.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            active = if (isLocalUri(dataSpec.uri)) local else remote
            return active!!.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int =
            checkActive().read(buffer, offset, readLength)

        override fun getUri(): Uri? = active?.uri

        override fun close() {
            active?.close()
            active = null
        }

        private fun checkActive(): DataSource =
            active ?: throw IllegalStateException("DataSource not opened")

        private fun isLocalUri(uri: Uri): Boolean {
            val scheme = uri.scheme?.lowercase() ?: return false
            return scheme == "file" || scheme == "content"
        }
    }
}
