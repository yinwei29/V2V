package com.yinwei.v2v.session

import com.yinwei.v2v.speech.PcmSpeech
import com.yinwei.v2v.settings.PresentationSettings
import android.content.Context
import android.net.Uri
import com.yinwei.v2v.providers.OpenAiProvider
import com.yinwei.v2v.diagnostics.*
import com.yinwei.v2v.transcript.SubtitlePipeline
import android.media.projection.MediaProjection
import android.os.SystemClock
import com.yinwei.v2v.audio.*
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Service-owned source lifetime; UI only observes repository state. */
class SessionController(private val context: Context, private val repository: SessionRepository, private val diagnostics: Diagnostics, private val history: com.yinwei.v2v.history.HistoryStore) {
    @Volatile private var capture: PlaybackCapture? = null
    @Volatile private var speech: PcmSpeech? = null
    @Volatile private var activeOutput: PcmOutput? = null
    fun resumeOutput() { activeOutput?.resume() }
    fun clearSpeech() { speech?.clear() }
    fun pauseOutput() { speech?.clear(); activeOutput?.pauseAndFlush() }
    fun interrupt() { pauseOutput(); capture?.interrupt() }
    suspend fun run(id: String, source: Source, projection: MediaProjection?, mix: () -> MixSettings,
        outputPaused: () -> Boolean, fileUri: Uri? = null, provider: OpenAiProvider? = null, language: String = "en", presentation: () -> PresentationSettings = { PresentationSettings() }) = coroutineScope {
        val external = source == Source.SYSTEM_PLAYBACK
        val probe = AsrProbe()
        val mixer = StereoMixer()
        val fixtureMode = source == Source.TEST_FILE
        val original = if (fixtureMode) fixture("original.wav") else ShortArray(0)
        val translated = if (fixtureMode) fixture("translation-fixture.wav") else ShortArray(0)
        var offset = 0
        var sequence = 0L
        var samplePosition = 0L
        val began = SystemClock.elapsedRealtime()
        var lastSignal = began
        var report = began
        var verified = false
        var lastRoute = -1
        var output: PcmOutput? = null
        var reader: WavReader? = null
        var subtitles: SubtitlePipeline? = null
        try {
            if (source == Source.OWNED_FILE) {
                val stream = context.contentResolver.openInputStream(checkNotNull(fileUri)) ?: error("無法讀取選取檔案")
                try { reader = WavReader(stream) } catch (e: Exception) { stream.close(); throw e }
            }
            if (provider != null) {
                speech = PcmSpeech(context, id, repository, this, diagnostics.journal, outputPaused, presentation)
                subtitles = SubtitlePipeline(id, language, provider, repository, this, diagnostics.journal) { segment ->
                    speech?.offer(segment)
                    history.record(segment, presentation().saveHistory)
                }
            }
            if (external) {
                capture = PlaybackCapture(checkNotNull(projection))
                checkNotNull(capture).start()
            }
            if (!external || provider != null) { output = PcmOutput(); activeOutput = output }
            repository.update { it.copy(capabilities = it.capabilities.copy(
                captureAvailable = true,
                stereoOutputSupported = output != null, translationOutputAvailable = output != null,
                verificationEvidence = if (external) "AudioRecord initialized; source content and physical device still unverified"
                    else "48k stereo AudioTrack initialized; physical channel isolation still unverified",
            )) }
            while (currentCoroutineContext().isActive) {
                val pcm = when (source) {
                    Source.SYSTEM_PLAYBACK -> checkNotNull(capture).read()
                    Source.OWNED_FILE -> checkNotNull(reader).readFrame() ?: break
                    Source.TEST_FILE -> ShortArray(960) { original[(offset + it) % original.size] }
                }
                val now = SystemClock.elapsedRealtime()
                val frame = AudioFrame(id, source, sequence++, SystemClock.elapsedRealtimeNanos() / 1000, 48000, pcm, samplePosition)
                samplePosition += pcm.size
                probe.consume(frame) // Always before any output gain, including originalGain=0.
                subtitles?.offer(frame) // Ownership shared read-only until bounded queue consumes it.
                val level = frame.rms()
                if (level > 0.001f) { lastSignal = now; verified = true }
                if (output != null) {
                    val t = if (fixtureMode) ShortArray(pcm.size) { translated[(offset + it) % translated.size] } else speech?.take(pcm.size) ?: ShortArray(pcm.size)
                    if (fixtureMode) { offset = (offset + pcm.size) % original.size }
                    if (!outputPaused()) {
                        val mixed = mixer.render(if (external) ShortArray(pcm.size) else pcm, t, if (external) mix().forExternalOutput() else mix())
                        output.write(mixed)
                    } else if (!external) delay(20)
                }
                currentCoroutineContext().ensureActive()
                if (now - report >= 100) {
                    val routeType = output?.routeType ?: 0
                    if (lastRoute != routeType) {
                        lastRoute = routeType
                        diagnostics.journal.record(DiagnosticEvent.ROUTE_CHANGE, id, mapOf(Metric.ROUTE_TYPE to routeType))
                    }
                    val silent = now - lastSignal >= 3000
                    repository.update { previous -> previous.copy(
                        state = if (silent) SessionState.NO_SIGNAL else SessionState.RUNNING,
                        sourceStatus = if (silent) SourceStatus.NO_SIGNAL else if (verified) SourceStatus.AVAILABLE else SourceStatus.UNKNOWN,
                        capabilities = previous.capabilities.copy(inputVerified = verified,
                            reason = if (external && verified) "曾收到非靜音 PCM；來源內容、版本與原聲控制仍未驗證" else previous.capabilities.reason),
                        ttsBufferedMs = speech?.bufferedMs() ?: 0, ttsPending = speech?.pending() ?: 0,
                        level = level, frames = sequence, asrSamples = probe.samples, elapsedMs = now - began,
                        route = output?.route ?: "擷取模式不重播來源副本；語音由 Android 離線引擎合成",
                        underruns = output?.underruns ?: 0,
                        message = if (outputPaused()) previous.message else if (silent) "NO_SIGNAL：可能安靜、暫停或來源政策限制；尚不能判定不支援"
                            else if (!verified) "正在等待有效 PCM；不代表已成功取得來源"
                            else if (provider != null) "雲端字幕模式；音訊與辨識文字會送至 OpenAI，語音由 Android 離線引擎合成"
                            else if (external) "取得非靜音 PCM；雲端字幕未啟用，來源控制不可用"
                            else if (fixtureMode) "測試音檔輸出中；ASR 分支僅量測，660Hz 為合成 fixture，非真實譯音"
                            else "WAV 播放／診斷中；雲端字幕未啟用，無真實 TTS",
                    ) }
                    report = now
                }
                if (pcm.isEmpty()) delay(20)
            }
            if (source == Source.OWNED_FILE) repository.update {
                if (it.id == id) it.copy(level = 0f, message = "音檔已播完，等待最後字幕；可按停止取消") else it
            }
            val finishing = launch { subtitles?.finish(); speech?.finish() }
            while (!finishing.isCompleted || speech?.hasAudio() == true) {
                if (!outputPaused() && output != null) {
                    output.write(mixer.render(ShortArray(960), speech?.take(960) ?: ShortArray(960), mix()))
                } else { speech?.clear(); delay(20) }
            }
            finishing.join()
            output?.drain()
        } finally {
            subtitles?.cancel()
            speech?.close(); speech = null
            activeOutput = null
            capture?.close(); capture = null
            output?.close()
            reader?.close()
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
