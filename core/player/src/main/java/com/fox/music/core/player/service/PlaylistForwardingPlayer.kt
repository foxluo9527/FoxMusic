package com.fox.music.core.player.service

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.fox.music.core.model.music.RepeatMode
import com.fox.music.core.player.controller.MusicController

/**
 * 将系统/通知栏/蓝牙的上一曲、下一曲命令转发到 [MusicController]，
 * 以支持 Exo 仅加载单曲、完整列表在内存中的播放模式。
 */
@UnstableApi
class PlaylistForwardingPlayer(
    exoPlayer: ExoPlayer,
    private val musicController: MusicController,
) : ForwardingPlayer(exoPlayer) {

    private val exoPlayer: ExoPlayer = exoPlayer

    private fun shouldDelegateSkipToApp(): Boolean {
        val playlist = musicController.playerState.value.playlist
        if (playlist.isEmpty()) return false
        return exoPlayer.mediaItemCount <= 1
    }

    override fun seekToNext() {
        if (delegateSkipToNext()) return
        super.seekToNext()
    }

    override fun seekToPrevious() {
        if (delegateSkipToPrevious()) return
        super.seekToPrevious()
    }

    override fun seekToNextMediaItem() {
        if (delegateSkipToNext()) return
        super.seekToNextMediaItem()
    }

    override fun seekToPreviousMediaItem() {
        if (delegateSkipToPrevious()) return
        super.seekToPreviousMediaItem()
    }

    private fun delegateSkipToNext(): Boolean {
        if (!shouldDelegateSkipToApp()) return false
        musicController.next()
        return true
    }

    private fun delegateSkipToPrevious(): Boolean {
        if (!shouldDelegateSkipToApp()) return false
        musicController.previous()
        return true
    }

    override fun hasNextMediaItem(): Boolean {
        if (!shouldDelegateSkipToApp()) return super.hasNextMediaItem()
        return canSkipToNextInApp()
    }

    override fun hasPreviousMediaItem(): Boolean {
        if (!shouldDelegateSkipToApp()) return super.hasPreviousMediaItem()
        return canSkipToPreviousInApp()
    }

    private fun canSkipToNextInApp(): Boolean {
        val state = musicController.playerState.value
        if (state.playlist.isEmpty()) return false
        return when (state.repeatMode) {
            RepeatMode.SEQUENTIAL -> state.currentIndex < state.playlist.lastIndex
            RepeatMode.ONE, RepeatMode.ALL, RepeatMode.RANDOM -> true
        }
    }

    private fun canSkipToPreviousInApp(): Boolean {
        val state = musicController.playerState.value
        if (state.playlist.isEmpty()) return false
        return when (state.repeatMode) {
            RepeatMode.SEQUENTIAL -> state.currentIndex > 0 || state.position > 3_000L
            RepeatMode.ONE, RepeatMode.ALL, RepeatMode.RANDOM -> true
        }
    }

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        if (!shouldDelegateSkipToApp()) return base
        val builder = base.buildUpon()
        if (canSkipToNextInApp()) {
            builder.add(Player.COMMAND_SEEK_TO_NEXT)
            builder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        if (canSkipToPreviousInApp()) {
            builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
            builder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }
        return builder.build()
    }
}
