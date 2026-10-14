package com.yinwei.v2v.audio

import android.annotation.SuppressLint
import android.media.*
import android.media.projection.MediaProjection
import android.os.Process

/** No microphone fallback. 48k PCM16 mono; never replays the external source copy. */
class PlaybackCapture(projection: MediaProjection) : AutoCloseable {
    private val recorder: AudioRecord
    init {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .excludeUid(Process.myUid()).build()
        val format = AudioFormat.Builder().setSampleRate(48000)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
        val minimum = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "裝置不支援擷取格式" }
        recorder = build(config, format, maxOf(minimum * 2, 9600))
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("AudioRecord 初始化失敗")
        }
    }
    @SuppressLint("MissingPermission") // Activity checks runtime permission; SecurityException handled by service.
    private fun build(config: AudioPlaybackCaptureConfiguration, format: AudioFormat, size: Int) =
        AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(size)
            .setAudioPlaybackCaptureConfig(config).build()
    fun start() = recorder.startRecording()
    fun read(): ShortArray {
        val samples = ShortArray(960)
        val count = recorder.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
        check(count >= 0) { "AudioRecord 讀取失敗 ($count)" }
        return if (count == samples.size) samples else samples.copyOf(count)
    }
    fun interrupt() { runCatching { recorder.stop() } }
    override fun close() { interrupt(); recorder.release() }
}
