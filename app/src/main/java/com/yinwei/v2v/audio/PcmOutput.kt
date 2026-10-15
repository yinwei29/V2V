package com.yinwei.v2v.audio

import android.media.*

class PcmOutput : AutoCloseable {
    private val track: AudioTrack
    init {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE).build()
        val min = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        check(min > 0) { "裝置不支援 stereo PCM 輸出" }
        track = AudioTrack.Builder().setAudioAttributes(attrs)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min * 2, 19200)).setTransferMode(AudioTrack.MODE_STREAM).build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            error("AudioTrack 初始化失敗")
        }
        try { track.play() } catch (e: Exception) { track.release(); throw e }
    }
    @Volatile private var paused = false
    fun pauseAndFlush() { paused = true; runCatching { track.pause(); track.flush() } }
    fun resume() { if (paused) { track.play(); paused = false } }
    fun write(pcm: ShortArray) {
        var offset = 0
        while (offset < pcm.size && !paused) {
            val count = track.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
            if (paused) return
            check(count > 0) { "AudioTrack 輸出失敗 ($count)" }
            offset += count
        }
    }
    suspend fun drain() {
        // Wait for at most one hardware buffer; cancellation/route pause still takes effect.
        if (!paused) kotlinx.coroutines.delay((track.bufferSizeInFrames * 1000L / 48000 + 40).coerceAtMost(1000))
    }
    val route: String get() = track.routedDevice?.let {
        "${it.productName} · type=${it.type} · stereo 實體隔離待驗證"
    } ?: "系統路由尚未回報"
    val routeType: Int get() = track.routedDevice?.type ?: 0
    val underruns get() = track.underrunCount
    override fun close() { runCatching { track.pause(); track.flush(); track.stop() }; track.release() }
}
