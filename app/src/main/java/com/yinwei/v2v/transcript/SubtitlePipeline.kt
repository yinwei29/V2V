package com.yinwei.v2v.transcript

import com.yinwei.v2v.audio.AudioFrame
import com.yinwei.v2v.diagnostics.*
import com.yinwei.v2v.providers.*
import com.yinwei.v2v.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** 250 × 20ms bounded ingress. No waiting chunk backlog: busy provider drops are counted and shown. */
class SubtitlePipeline(
    private val sessionId: String, private val language: String, private val provider: TranslationProvider,
    private val repository: SessionRepository, scope: CoroutineScope,
    private val diagnostics: DiagnosticJournal? = null,
    private val translated: (TranscriptSegment) -> Unit = {},
    private val targetLanguage: String = "zh-TW",
) {
    private val frames = Channel<AudioFrame>(250)
    private val chunks = ChunkMailbox()
    private val ledger = TranscriptLedger(sessionId)
    private val worker = scope.launch(Dispatchers.IO) {
        var consecutiveFailures = 0
        try {
            while (true) {
                val chunk = chunks.receive() ?: break
                if (repository.state.value.id != sessionId) break
                try {
                    process(chunk)
                    if (consecutiveFailures > 0) diagnostics?.record(DiagnosticEvent.PROVIDER_RECOVERED, sessionId)
                    consecutiveFailures = 0
                    repository.update { if (it.id == sessionId) it.copy(providerError = "", providerRecovering = false) else it }
                } catch (e: CancellationException) { throw e }
                catch (e: ProviderFailure) {
                    if (!e.retryable) { fail(e.message.orEmpty()); break }
                    consecutiveFailures++
                    dropped(chunk.pcm.size.toLong())
                    if (consecutiveFailures >= 3) {
                        fail("服務連續 3 段失敗，字幕已暫停。請停止後重試或在設定更換 Gemini 模型。最後錯誤：${e.message.orEmpty()}")
                        break
                    }
                    val waitMs = 2000L shl (consecutiveFailures - 1)
                    repository.update { if (it.id == sessionId) it.copy(asrState = if (it.asrState == StageState.RUNNING) StageState.ERROR else it.asrState,
                        mtState = if (it.mtState == StageState.RUNNING) StageState.ERROR else it.mtState,
                        providerRecovering = true, providerError = "${e.message.orEmpty()}；${waitMs / 1000} 秒後繼續後續音訊，本段未完成") else it }
                    diagnostics?.record(DiagnosticEvent.PROVIDER_RECOVERY, sessionId,
                        mapOf(Metric.CONSECUTIVE_FAILURES to consecutiveFailures, Metric.RETRY_DELAY_MS to waitMs,
                            Metric.HTTP_STATUS to e.status))
                    delay(waitMs)
                    repository.update { if (it.id == sessionId) it.copy(providerError = "等待後續音訊後重新嘗試辨識與翻譯") else it }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { fail("供應商處理失敗；請停止並檢查連線與設定，原文會保留在本次工作階段") }
        finally { chunks.close() }
    }
    private suspend fun process(chunk: AudioChunk) {
        stage(asr = StageState.RUNNING, mt = StageState.IDLE)
        val original = provider.transcribe(chunk, language)
        currentCoroutineContext().ensureActive()
        if (original.isBlank()) {
            diagnostics?.record(DiagnosticEvent.ASR_EMPTY, sessionId, mapOf(Metric.SEGMENT_ID to chunk.id))
            stage(StageState.IDLE, StageState.IDLE); return
        }
        var segment = TranscriptSegment(sessionId, chunk.id, startMs = chunk.startMs, endMs = chunk.endMs,
            originalText = original, sourceLanguage = language, provider = provider.description, targetLanguage = targetLanguage)
        publish(segment)
        stage(StageState.IDLE, StageState.RUNNING)
        val began = System.nanoTime()
        val result = provider.translate(original, language, targetLanguage)
        currentCoroutineContext().ensureActive()
        segment = segment.copy(revision = 2, translatedText = result.text, translationLatencyMs = (System.nanoTime() - began) / 1_000_000)
        publish(segment)
        if (repository.state.value.id == sessionId) translated(segment)
        repository.update { if (it.id == sessionId) it.copy(providerTokens = result.tokens?.let { tokens -> (it.providerTokens ?: 0) + tokens } ?: it.providerTokens) else it }
        stage(StageState.IDLE, StageState.IDLE)
    }
    private val segmenter = scope.launch(Dispatchers.IO) {
        val splitter = SpeechSegmenter()
        try {
            for (frame in frames) {
                if (frame.sessionId != sessionId) continue
                for (chunk in splitter.offer(frame.pcm, frame.sourcePositionSamples)) {
                    if (!chunks.tryOffer(chunk)) dropped(chunk.pcm.size.toLong())
                }
            }
            val tail = splitter.flush()
            if (tail != null) chunks.sendTail(tail)
        } catch (e: CancellationException) { throw e }
        catch (_: kotlinx.coroutines.channels.ClosedSendChannelException) { /* Provider has failed, already reported. */ }
        finally { chunks.close() }
    }
    fun offer(frame: AudioFrame) {
        if (frames.trySend(frame).isFailure) dropped(frame.pcm.size.toLong())
    }
    suspend fun finish() { frames.close(); segmenter.join(); worker.join() }
    fun cancel() { frames.cancel(); chunks.cancel(); segmenter.cancel(); worker.cancel() }
    private fun dropped(samples: Long) = repository.update {
        if (it.id == sessionId) it.copy(skippedSamples = it.skippedSamples + samples) else it
    }
    private fun publish(segment: TranscriptSegment) {
        if (repository.state.value.id != sessionId) return
        if (ledger.accept(segment)) {
            val snapshot = ledger.snapshot()
            repository.update { if (it.id == sessionId) it.copy(segments = snapshot) else it }
            diagnostics?.record(if (segment.translatedText.isNotBlank()) DiagnosticEvent.TRANSLATION_PUBLISHED else DiagnosticEvent.TRANSCRIPT_PUBLISHED,
                sessionId, mapOf(Metric.SEGMENT_ID to segment.id, Metric.REVISION to segment.revision,
                    Metric.CHARACTERS to if (segment.translatedText.isNotBlank()) segment.translatedText.length else segment.originalText.length,
                    Metric.PUBLISHED_SEGMENTS to snapshot.size, Metric.LATENCY_MS to segment.translationLatencyMs))
        }
    }
    private fun stage(asr: StageState, mt: StageState) = repository.update {
        if (it.id == sessionId) it.copy(asrState = asr, mtState = mt) else it
    }
    private fun fail(message: String) = repository.update {
        if (it.id == sessionId) it.copy(asrState = if (it.asrState == StageState.RUNNING) StageState.ERROR else it.asrState,
            mtState = if (it.mtState == StageState.RUNNING) StageState.ERROR else it.mtState, providerError = message, providerRecovering = false) else it
    }
}
