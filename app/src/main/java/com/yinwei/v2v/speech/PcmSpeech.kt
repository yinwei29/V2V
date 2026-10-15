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
    private data class Request(val text: String, val epoch: Long)
    private val requests = Channel<Request>(3)
    private val guard = Any()
    private var pending = 0
    private val pcm = SpeechBuffer()
    private var engine: TextToSpeech? = null
    @Volatile private var generation = 0L
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
            for (request in requests) {
                if (request.epoch != generation) continue
                if (paused() || !options().speechEnabled) { release(request.epoch); continue }
                tts.setSpeechRate(options().speechRate)
                val epoch = request.epoch
                update(StageState.RUNNING)
                try {
                    val audio = synthesize(tts, request.text)
                    synchronized(guard) {
                        if (epoch == generation) {
                            if (paused() || !options().speechEnabled || !pcm.offer(audio)) { release(epoch); drop() }
                            else journal.record(DiagnosticEvent.TTS_PCM_READY, id, mapOf(Metric.SAMPLES to audio.size))
                        }
                    }
                    update(StageState.IDLE)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { release(epoch); update(StageState.ERROR, "語音合成失敗或超過 10 秒限制；字幕仍可使用") }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { update(StageState.ERROR, "無法初始化 Android 語音引擎；請檢查文字轉語音設定") }
        finally { engine?.stop(); engine?.shutdown(); engine = null }
    }
    fun offer(text: String) {
        if (text.isBlank() || paused() || !options().speechEnabled || !worker.isActive) return
        synchronized(guard) {
            if (text.length > TextToSpeech.getMaxSpeechInputLength() || pending >= 3) { drop(); return }
            pending++
            if (requests.trySend(Request(text, generation)).isFailure) { pending--; drop() }
        }
    }
    private fun release(epoch: Long) = synchronized(guard) { if (epoch == generation) pending = (pending - 1).coerceAtLeast(0) }
    fun take(size: Int): ShortArray = synchronized(guard) {
        val before = pcm.utterances()
        pcm.take(size).also { pending = (pending - before + pcm.utterances()).coerceAtLeast(0) }
    }
    fun hasAudio() = pcm.hasAudio()
    fun clear() = synchronized(guard) {
        generation++; pending = 0
        while (requests.tryReceive().isSuccess) { /* discard stale speech */ }
        pcm.clear()
        // Current synthesis may finish, but its generation can no longer enter playback.
    }
    suspend fun finish() { requests.close(); worker.join() }
    fun close() { clear(); requests.cancel(); worker.cancel() }
    private fun drop() {
        journal.record(DiagnosticEvent.TTS_DROPPED, id)
        repository.update { if (it.id == id) it.copy(ttsDropped = it.ttsDropped + 1) else it }
    }
    private fun update(state: StageState, error: String = "") = repository.update {
        if (it.id == id) it.copy(ttsState = state, ttsError = error) else it
    }
    private suspend fun synthesize(tts: TextToSpeech, text: String): ShortArray {
        val done = CompletableDeferred<ShortArray>()
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
                        done.completeExceptionally(IllegalStateException("Unsupported or long speech"))
                    } else bytes.write(audio)
                }
            }
            override fun onDone(utteranceId: String?) {
                if (utteranceId != token || done.isCompleted) return
                runCatching {
                    check(format == AudioFormat.ENCODING_PCM_16BIT)
                    synchronized(bytes) { check(bytes.size() > 0); SpeechPcm.decode(bytes.toByteArray(), rate, channels) }
                }.fold({ done.complete(it) }, { done.completeExceptionally(it) })
            }
            @Deprecated("Android callback") override fun onError(utteranceId: String?) {
                if (utteranceId == token) done.completeExceptionally(IllegalStateException("Synthesis failed"))
            }
            override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
        })
        try {
            check(tts.synthesizeToFile(text, Bundle(), file, token) == TextToSpeech.SUCCESS)
            return withTimeout(20000) { done.await() }
        } finally { tts.stop(); file.delete() }
    }
}
