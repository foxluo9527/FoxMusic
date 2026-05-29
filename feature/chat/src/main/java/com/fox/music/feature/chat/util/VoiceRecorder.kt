package com.fox.music.feature.chat.util

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): Boolean = try {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file
        recorder = createRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(96_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        true
    } catch (_: Exception) {
        cancel()
        false
    }

    fun stop(): Uri? {
        return try {
            recorder?.stop()
            outputFile?.let { Uri.fromFile(it) }
        } catch (_: Exception) {
            null
        } finally {
            release()
        }
    }

    fun cancel() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        } finally {
            release()
            outputFile?.delete()
            outputFile = null
        }
    }

    private fun release() {
        recorder?.release()
        recorder = null
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
}
