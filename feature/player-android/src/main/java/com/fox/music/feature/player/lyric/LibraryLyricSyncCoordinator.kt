package com.fox.music.feature.player.lyric

import com.blankj.utilcode.util.ToastUtils
import com.fox.music.core.domain.usecase.LibraryLyricSyncOutcome
import com.fox.music.core.domain.usecase.SyncLibraryLyricsUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 歌词纠正页关掉之后，同步请求还要继续，所以不挂在页面的 viewModelScope 上。
 * 本机歌词已经先写进播放器；这里只负责管理员写回曲库，失败再补一条提示。
 */
@Singleton
class LibraryLyricSyncCoordinator @Inject constructor(
    private val syncLibraryLyricsUseCase: SyncLibraryLyricsUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enqueue(
        musicId: Long,
        isThirdParty: Boolean,
        lyrics: String,
        lyricsTrans: String?,
    ) {
        scope.launch {
            try {
                when (val outcome = syncLibraryLyricsUseCase(musicId, isThirdParty, lyrics, lyricsTrans)) {
                    LibraryLyricSyncOutcome.SkippedNotLibrary ->
                        Timber.d("library lyrics sync skipped, not a library track musicId=%s", musicId)
                    LibraryLyricSyncOutcome.SkippedNotAdmin ->
                        Timber.d("library lyrics sync skipped, not admin musicId=%s", musicId)
                    LibraryLyricSyncOutcome.Synced -> {
                        Timber.i("library lyrics synced musicId=%s", musicId)
                        toast("已同步到曲库")
                    }
                    is LibraryLyricSyncOutcome.Failed -> {
                        Timber.w("library lyrics sync failed musicId=%s: %s", musicId, outcome.message)
                        toast("曲库歌词同步失败：${outcome.message}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "library lyrics sync crashed musicId=%s", musicId)
                toast("曲库歌词同步失败")
            }
        }
    }

    private suspend fun toast(message: String) {
        withContext(Dispatchers.Main) {
            ToastUtils.make().show(message)
        }
    }
}
