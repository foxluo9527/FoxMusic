package com.fox.music.feature.profile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.domain.repository.DownloadRepository
import com.fox.music.core.model.download.DownloadStatus
import com.fox.music.core.model.download.DownloadTask
import com.fox.music.core.model.music.Music
import com.fox.music.core.player.controller.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DownloadEffect {
    data object NavigateToPlayer : DownloadEffect
    data class ShowMessage(val message: String) : DownloadEffect
}

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val musicController: MusicController,
) : ViewModel() {

    val downloads = downloadRepository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val completedDownloads = downloadRepository.downloads
        .map { tasks -> tasks.filter { it.status == DownloadStatus.COMPLETED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _effect = MutableSharedFlow<DownloadEffect>()
    val effect: SharedFlow<DownloadEffect> = _effect.asSharedFlow()

    var totalBytes: Long = 0L
        private set

    init {
        viewModelScope.launch {
            totalBytes = downloadRepository.getTotalDownloadedBytes()
        }
    }

    fun refreshTotalBytes() {
        viewModelScope.launch {
            totalBytes = downloadRepository.getTotalDownloadedBytes()
        }
    }

    fun pause(musicId: Long) {
        viewModelScope.launch { downloadRepository.pause(musicId) }
    }

    fun resume(musicId: Long) {
        viewModelScope.launch { downloadRepository.resume(musicId) }
    }

    fun delete(musicId: Long) {
        viewModelScope.launch {
            downloadRepository.delete(musicId)
            refreshTotalBytes()
        }
    }

    fun pauseAll() {
        viewModelScope.launch { downloadRepository.pauseAll() }
    }

    fun resumeAll() {
        viewModelScope.launch { downloadRepository.resumeAll() }
    }

    fun playDownload(task: DownloadTask) {
        val playlist = buildPlayablePlaylist()
        val music = task.toMusic()
        if (music == null || playlist.isEmpty()) {
            viewModelScope.launch {
                _effect.emit(DownloadEffect.ShowMessage("文件不存在或尚未下载完成"))
            }
            return
        }
        val startIndex = playlist.indexOfFirst { it.id == music.id }.coerceAtLeast(0)
        musicController.setPlaylist(playlist, startIndex, PLAYLIST_KEY)
        viewModelScope.launch {
            _effect.emit(DownloadEffect.NavigateToPlayer)
        }
    }

    fun playAllCompleted() {
        val playlist = buildPlayablePlaylist()
        if (playlist.isEmpty()) {
            viewModelScope.launch {
                _effect.emit(DownloadEffect.ShowMessage("暂无可播放的已下载歌曲"))
            }
            return
        }
        musicController.setPlaylist(playlist, 0, PLAYLIST_KEY)
        viewModelScope.launch {
            _effect.emit(DownloadEffect.NavigateToPlayer)
        }
    }

    private fun buildPlayablePlaylist(): List<Music> {
        return downloads.value.mapNotNull { it.toMusic() }
    }

    fun statusLabel(task: DownloadTask): String = when (task.status) {
        DownloadStatus.PENDING -> "等待中"
        DownloadStatus.DOWNLOADING -> when {
            task.totalBytes > 0L -> "下载中 ${task.progress}%"
            task.downloadedBytes > 0L -> "下载中 ${formatBytes(task.downloadedBytes)}"
            else -> "下载中"
        }
        DownloadStatus.COMPLETED -> "已完成"
        DownloadStatus.FAILED -> "下载失败"
        DownloadStatus.PAUSED -> "已暂停"
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        if (bytes < 1024 * 1024 * 1024) return "${bytes / (1024 * 1024)} MB"
        return String.format("%.1f GB", bytes.toDouble() / (1024 * 1024 * 1024))
    }

    companion object {
        private const val PLAYLIST_KEY = "downloads"
    }
}
