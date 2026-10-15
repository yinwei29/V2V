package com.yinwei.v2v.speech

import com.yinwei.v2v.diagnostics.*
import com.yinwei.v2v.settings.PresentationSettings
import android.content.Context
import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.yinwei.v2v.audio.*
import com.yinwei.v2v.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Synthesis only. PCM is rendered through the session's single mixer/AudioTrack. */
class PcmSpeech(private val context: Context, private val id: String, private val repository: SessionRepository,
    scope: CoroutineScope, private val journal: DiagnosticJournal, private val paused: () -> Boolean, private val options: () -> PresentationSettings) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val queue = SpeechQueue()
    private var requestSequence = 0L
    private var lastSegmentId = -1L
    private val guard = Any()
    @Volatile private var finishing = false
    private var engine: TextToSpeech? = null
    @Volatile private var generation = 0L
    @Volatile private var currentSynthesis: CompletableDeferred<ShortArray>? = null
    private val worker = scope.launch(Dispatchers.Main.immediate) {
        try {
            val ready = CompletableDeferred<Int>()
            val tts = TextToSpeech(context.applicationContext) { ready.complete(it) }
            engine = tts
            check(withTimeout(15000) { ready.await() } == TextToSpeech.SUCCESS)
            // Offline voice only: no extra text is silently sent to another cloud service.
            val voice = tts.voices?.filter { it.locale.language == "zh" && it.locale.country == "TW" && !it.isNetworkConnectionRequired }
                ?.sortedBy { it.name }?.firstOrNull()
            if (voice == null) {
                update(StageState.ERROR, "請在 Android 文字轉語音設定安裝離線繁體中文（台灣）語音，再重新開始")
                return@launch
            }
            check(tts.setVoice(voice) == TextToSpeech.SUCCESS)
            tts.setSpeechRate(1f)
            update(StageState.IDLE)
            while (true) {
                val request = queue.next()
                if (request == null) {
                    if (finishing) break
                    requests.receiveCatching().getOrNull() ?: break
                    continue
                }
                val epoch = generation
                try {
                    val phrases = SpeechPlan.phrases(request.text)
                    for ((index, phrase) in phrases.withIndex()) {
                        if (epoch != generation || !queue.active(request.key) || paused() || !options().speechEnabled) break
                        val rate = SpeechPlan.rate(options().speechRate, options().autoCatchUp, request.text,
                            request.sourceDurationMs, queue.pending(), queue.bufferedMs())
                        tts.setSpeechRate(rate)
                        repository.update { if (it.id == id) it.copy(ttsRate = rate) else it }
                        update(StageState.RUNNING)
                        val began = System.nanoTime()
                        // Retry oversized engine output as smaller phrases; never discard the whole paragraph.
                        synthesizePhrase(tts, phrase) { audio ->
                            while (epoch == generation && queue.active(request.key) && !paused() && options().speechEnabled) {
                                if (queue.append(request.key, audio)) {
                                    journal.record(DiagnosticEvent.TTS_PCM_READY, id, mapOf(Metric.SAMPLES to audio.size,
                                        Metric.SEGMENT_ID to request.segmentId, Metric.PHRASE_INDEX to index,
                                        Metric.LATENCY_MS to (System.nanoTime() - began) / 1_000_000, Metric.SPEECH_RATE to rate))
                                    break
                                }
                                delay(20) // Playback frees space; do not throw away valid speech because PCM is temporarily full.
                            }
                        }
                    }
                    if (epoch == generation) update(StageState.IDLE)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (epoch == generation) {
                        val reason = (e as? SpeechFailure)?.reason ?: SpeechFailureReason.ENGINE_ERROR
                        journal.record(DiagnosticEvent.TTS_FAILURE, id, mapOf(Metric.SEGMENT_ID to request.segmentId),
                            mapOf(Tag.TTS_REASON to reason), e.javaClass)
                        repository.update { if (it.id == id) it.copy(ttsFailures = it.ttsFailures + 1) else it }
                        update(StageState.ERROR, "語音合成失敗（${reason.name}）；本段部分語音可能缺失，字幕仍保留")
                    }
                } finally { queue.complete(request.key) }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { update(StageState.ERROR, "無法初始化 Android 語音引擎；請檢查文字轉語音設定") }
        finally { engine?.stop(); engine?.shutdown(); engine = null }
    }
    fun offer(segment: com.yinwei.v2v.transcript.TranscriptSegment) {
        if (segment.translatedText.isBlank() || paused() || !options().speechEnabled || !worker.isActive) return
        synchronized(guard) {
            if (segment.sessionId != id || segment.id <= lastSegmentId) return
            lastSegmentId = segment.id
            val request = SpeechRequest(requestSequence++, segment.id, segment.revision, segment.translatedText, segment.endMs - segment.startMs)
            if (segment.translatedText.length > 4000 || !queue.enqueue(request)) { drop(); return }
            requests.trySend(Unit)
        }
    }
    fun take(size: Int): ShortArray = queue.take(size)
    fun hasAudio() = queue.hasAudio()
    fun bufferedMs() = queue.bufferedMs()
    fun pending() = queue.pending()
    fun clear() = synchronized(guard) {
        generation++
        queue.clear()
        repository.update { if (it.id == id) it.copy(ttsPending = 0, ttsBufferedMs = 0) else it }
        val previous = currentSynthesis
        if (previous != null) android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (currentSynthesis === previous) {
                engine?.stop()
                previous.completeExceptionally(SpeechFailure(SpeechFailureReason.INTERRUPTED))
            }
        }
    }
    suspend fun finish() { finishing = true; requests.trySend(Unit); worker.join() }
    fun close() { clear(); requests.cancel(); worker.cancel() }
    private fun drop() {
        journal.record(DiagnosticEvent.TTS_DROPPED, id)
        repository.update { if (it.id == id) it.copy(ttsDropped = it.ttsDropped + 1) else it }
    }
    private fun update(state: StageState, error: String = "") = repository.update {
        if (it.id == id) it.copy(ttsState = state, ttsError = error) else it
    }
    private suspend fun synthesizePhrase(tts: TextToSpeech, phrase: String, emit: suspend (ShortArray) -> Unit) {
        val audio = try { synthesize(tts, phrase) }
        catch (e: SpeechFailure) {
            if (e.reason != SpeechFailureReason.TOO_LONG || phrase.codePointCount(0, phrase.length) <= 2) throw e
            journal.record(DiagnosticEvent.TTS_PHRASE_SPLIT, id, mapOf(Metric.CHARACTERS to phrase.length))
            for (part in SpeechPlan.phrases(phrase, maxOf(2, phrase.codePointCount(0, phrase.length) / 2))) {
                synthesizePhrase(tts, part, emit)
            }
            return
        }
        emit(audio)
    }
    private suspend fun synthesize(tts: TextToSpeech, text: String): ShortArray {
        val done = CompletableDeferred<ShortArray>()
        currentSynthesis = done
        val token = UUID.randomUUID().toString()
        val file = File.createTempFile("tts-", ".wav", context.cacheDir)
        val bytes = ByteArrayOutputStream()
        var rate = 0; var channels = 0; var format = 0
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onBeginSynthesis(utteranceId: String?, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
                if (utteranceId == token) { rate = sampleRateInHz; channels = channelCount; format = audioFormat }
            }
            override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                if (utteranceId != token || audio == null || done.isCompleted) return
                synchronized(bytes) {
                    if (rate !in 8000..48000 || channels !in 1..2 || bytes.size() + audio.size > rate * channels * 2 * 10) {
                        done.completeExceptionally(SpeechFailure(if (rate !in 8000..48000 || channels !in 1..2) SpeechFailureReason.FORMAT else SpeechFailureReason.TOO_LONG))
                    } else bytes.write(audio)
                }
            }
            override fun onDone(utteranceId: String?) {
                if (utteranceId != token || done.isCompleted) return
                runCatching {
                    if (format != AudioFormat.ENCODING_PCM_16BIT) throw SpeechFailure(SpeechFailureReason.FORMAT)
                    synchronized(bytes) { if (bytes.size() == 0) throw SpeechFailure(SpeechFailureReason.EMPTY_AUDIO); SpeechPcm.decode(bytes.toByteArray(), rate, channels) }
                }.fold({ done.complete(it) }, { done.completeExceptionally(it) })
            }
            @Deprecated("Android callback") override fun onError(utteranceId: String?) {
                if (utteranceId == token) done.completeExceptionally(SpeechFailure(SpeechFailureReason.ENGINE_ERROR))
            }
            override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
        })
        var successful = false
        try {
            check(tts.synthesizeToFile(text, Bundle(), file, token) == TextToSpeech.SUCCESS)
            return try { withTimeout(20000) { done.await() }.also { successful = true } }
            catch (_: TimeoutCancellationException) { throw SpeechFailure(SpeechFailureReason.TIMEOUT) }
        } finally {
            currentSynthesis = null
            if (!successful) tts.stop()
            file.delete()
        }
    }
}

private class SpeechFailure(val reason: SpeechFailureReason) : Exception()
