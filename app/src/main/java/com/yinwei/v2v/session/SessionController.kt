package com.yinwei.v2v.session

import android.content.Context
import android.media.projection.MediaProjection
import android.os.SystemClock
import com.yinwei.v2v.audio.*
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Service-owned source lifetime; UI only observes repository state. */
class SessionController(private val context: Context, private val repository: SessionRepository) {
    @Volatile private var capture: PlaybackCapture? = null
    fun interrupt() { capture?.interrupt() }
    suspend fun run(id: String, source: Source, projection: MediaProjection?, mix: () -> MixSettings, outputPaused: () -> Boolean) {
        val external = source == Source.SYSTEM_PLAYBACK
        val probe = AsrProbe()
        val mixer = StereoMixer()
        val original = if (!external) fixture("original.wav") else ShortArray(0)
        val translated = if (!external) fixture("translation-fixture.wav") else ShortArray(0)
        var offset = 0
        var sequence = 0L
        val began = SystemClock.elapsedRealtime()
        var lastSignal = began
        var report = began
        var verified = false
        var output: PcmOutput? = null
        try {
            if (external) {
                capture = PlaybackCapture(checkNotNull(projection))
                checkNotNull(capture).start()
            }
            else output = PcmOutput()
            repository.update { it.copy(capabilities = it.capabilities.copy(
                captureAvailable = true,
                stereoOutputSupported = !external,
                verificationEvidence = if (external) "AudioRecord initialized; source content and physical device still unverified"
                    else "48k stereo AudioTrack initialized; physical channel isolation still unverified",
            )) }
            while (currentCoroutineContext().isActive) {
                val pcm = if (external) checkNotNull(capture).read() else ShortArray(960) { original[(offset + it) % original.size] }
                val now = SystemClock.elapsedRealtime()
                val frame = AudioFrame(id, source, sequence++, SystemClock.elapsedRealtimeNanos() / 1000, 48000, pcm)
                probe.consume(frame) // Always before any output gain, including originalGain=0.
                val level = frame.rms()
                if (level > 0.001f) { lastSignal = now; verified = true }
                if (!external) {
                    val t = ShortArray(pcm.size) { translated[(offset + it) % translated.size] }
                    offset = (offset + pcm.size) % original.size
                    val mixed = mixer.render(pcm, t, mix())
                    output!!.write(if (outputPaused()) ShortArray(mixed.size) else mixed)
                }
                currentCoroutineContext().ensureActive()
                if (now - report >= 100) {
                    val silent = now - lastSignal >= 3000
                    repository.update { previous -> previous.copy(
                        state = if (silent) SessionState.NO_SIGNAL else SessionState.RUNNING,
                        sourceStatus = if (silent) SourceStatus.NO_SIGNAL else if (verified) SourceStatus.AVAILABLE else SourceStatus.UNKNOWN,
                        capabilities = previous.capabilities.copy(inputVerified = verified),
                        level = level, frames = sequence, asrSamples = probe.samples, elapsedMs = now - began,
                        route = output?.route ?: "擷取模式不重播來源副本；真實 TTS 尚未接入",
                        underruns = output?.underruns ?: 0,
                        message = if (outputPaused()) previous.message else if (silent) "NO_SIGNAL：可能安靜、暫停或來源政策限制；尚不能判定不支援"
                            else if (!verified) "正在等待有效 PCM；不代表已成功取得來源"
                            else if (external) "取得非靜音 PCM；尚未接入辨識與翻譯，來源控制不可用"
                            else "測試音檔輸出中；ASR 分支僅量測，660Hz 為合成 fixture，非真實譯音",
                    ) }
                    report = now
                }
                if (pcm.isEmpty()) delay(20)
            }
        } finally {
            capture?.close(); capture = null
            output?.close()
        }
    }
    private fun fixture(name: String): ShortArray {
        val bytes = context.assets.open(name).use { it.readBytes() }
        // Repository-owned canonical WAV: 44-byte header, 48k mono PCM16, 2s maximum.
        check(bytes.size == 192044)
        return ShortArray(96000).also { ByteBuffer.wrap(bytes, 44, bytes.size - 44)
            .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(it) }
    }
}
