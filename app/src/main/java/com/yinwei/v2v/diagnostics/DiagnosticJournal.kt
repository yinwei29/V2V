package com.yinwei.v2v.diagnostics

import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.FutureTask

enum class DiagnosticEvent { PROVIDER_RECOVERY, PROVIDER_RECOVERED, APP_START, SESSION_START, SESSION_STOP, PERMISSION, PCM_SAMPLE, SESSION_STATE, MIX_CHANGE, ROUTE_CHANGE, OUTPUT_PAUSE, PROVIDER_STATE, PROVIDER_FAILURE, OVERLOAD, CRASH, LOG_EXPORT, ASR_EMPTY, TRANSCRIPT_PUBLISHED, TRANSLATION_PUBLISHED, TRANSLATION_VISIBLE, TTS_FAILURE, TTS_PHRASE_SPLIT, TTS_PCM_READY, TTS_DROPPED, OVERLAY_STATE }
enum class Metric { TRIMMED_SAMPLES, CONSECUTIVE_FAILURES, RETRY_DELAY_MS, RMS, FRAMES, SAMPLES, ELAPSED_MS, UNDERRUNS, ROUTE_TYPE, ORIGINAL_GAIN, TRANSLATION_GAIN, SWAP, GRANTED, DROPPED_SAMPLES, HTTP_STATUS, LATENCY_MS, CLOUD_ENABLED, SEGMENT_ID, REVISION, CHARACTERS, PUBLISHED_SEGMENTS, TTS_DROPPED, VISIBLE, TTS_FAILURES, PHRASE_INDEX, SPEECH_RATE, BUFFERED_MS, PENDING_SEGMENTS, INPUT_RMS, OUTPUT_RMS, NORMALIZATION_GAIN }
enum class Tag { TARGET_LANGUAGE, GEMINI_MODEL, PROVIDER_REASON, PROVIDER, SOURCE, STATE, SOURCE_STATUS, ASR_STATE, MT_STATE, TTS_STATE, MIX_MODE, PERMISSION, STOP_REASON, LANGUAGE, TTS_REASON }
enum class SpeechFailureReason { TOO_LONG, FORMAT, EMPTY_AUDIO, ENGINE_ERROR, TIMEOUT, INTERRUPTED }
enum class DiagnosticLanguage { EN, ID, AUTO }
enum class StopReason { USER, SOURCE_FINISHED, SYSTEM_REVOKED, AUDIO_ERROR, SERVICE_DESTROYED }
enum class PermissionKind { AUDIO, PROJECTION, NOTIFICATION }
data class DiagnosticEnvironment(val version: String, val sdk: Int, val manufacturer: String, val model: String)

/** Typed events accept no arbitrary messages, transcript, URL, headers, files or keys. */
class DiagnosticJournal(
    private val directory: File, private val environment: DiagnosticEnvironment,
    private val maxFileBytes: Long = 512 * 1024, private val fileCount: Int = 3,
) {
    @Volatile var enabled = true
    private val guard = Any()
    private val start = System.nanoTime()
    private val runId = java.util.UUID.randomUUID().toString()
    private val dropped = AtomicLong()
    private val writeFailures = AtomicLong()
    private val writer = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(128), java.util.concurrent.ThreadFactory { task -> Thread(task, "v2v-diagnostics").apply { isDaemon = true } })
        .apply { rejectedExecutionHandler = java.util.concurrent.RejectedExecutionHandler { _, _ -> dropped.incrementAndGet() } }
    init { require(fileCount in 1..5 && maxFileBytes >= 1024); directory.mkdirs(); writer.prestartCoreThread() }
    fun record(event: DiagnosticEvent, sessionId: String = "", metrics: Map<Metric, Number> = emptyMap(),
        tags: Map<Tag, Enum<*>> = emptyMap(), error: Class<out Throwable>? = null) {
        if (!enabled) return
        val objectValue = JSONObject().put("event", event.name).put("wallTimeMs", System.currentTimeMillis())
            .put("runId", runId).put("monotonicMs", (System.nanoTime() - start) / 1_000_000)
        // Only internally generated UUIDs are accepted, never caller text.
        if (sessionId.matches(Regex("[a-fA-F0-9-]{1,36}"))) objectValue.put("session", sessionId)
        metrics.forEach { (key, value) -> if (value.toDouble().isFinite()) objectValue.put(key.name, value) }
        tags.forEach { (key, value) -> objectValue.put(key.name, value.name) }
        if (error != null) objectValue.put("errorClass", error.simpleName.replace(Regex("[^A-Za-z0-9_$]"), "").take(80))
        val missed = dropped.getAndSet(0)
        if (missed > 0) objectValue.put("logEventsDropped", missed)
        val line = objectValue.toString()
        writer.execute { runCatching { append(line) }.onFailure { writeFailures.incrementAndGet() } }
    }
    private fun append(line: String) = synchronized(guard) {
        directory.mkdirs()
        val current = File(directory, "technical-0.jsonl")
        if (current.exists() && current.length() + line.toByteArray().size + 1 > maxFileBytes) {
            File(directory, "technical-${fileCount - 1}.jsonl").delete()
            for (i in fileCount - 2 downTo 0) File(directory, "technical-$i.jsonl").renameTo(File(directory, "technical-${i + 1}.jsonl"))
        }
        if (!current.exists()) current.appendText(header() + "\n", Charsets.UTF_8)
        current.appendText(line + "\n", Charsets.UTF_8)
    }
    private fun header(): String = JSONObject().put("schema", 1).put("privacy", "technical_only_no_audio_no_transcript_no_keys")
        .put("writeFailures", writeFailures.get()).put("appVersion", safeDeviceText(environment.version)).put("androidSdk", environment.sdk)
        .put("manufacturer", safeDeviceText(environment.manufacturer)).put("model", safeDeviceText(environment.model)).toString()
    /** Runs behind queued events; caller uses an IO thread, never an audio/UI callback. */
    fun export(output: OutputStream) {
        record(DiagnosticEvent.LOG_EXPORT)
        flush()
        synchronized(guard) {
            output.write((header() + "\n").toByteArray(Charsets.UTF_8))
            for (i in fileCount - 1 downTo 0) {
                val file = File(directory, "technical-$i.jsonl")
                if (file.exists()) file.inputStream().use { it.copyTo(output) }
            }
        }
    }
    fun clear() { flush(); synchronized(guard) { directory.listFiles()?.forEach { it.delete() } } }
    fun sizeBytes(): Long = synchronized(guard) { directory.listFiles()?.sumOf { it.length() } ?: 0 }
    fun flush() {
        val barrier = FutureTask<Unit> { }
        check(writer.queue.offer(barrier, 5, TimeUnit.SECONDS)) { "Log writer busy" }
        barrier.get(5, TimeUnit.SECONDS)
    }
    fun close() { writer.shutdown(); writer.awaitTermination(5, TimeUnit.SECONDS) }
    private fun safeDeviceText(value: String) = value.replace(Regex("[^A-Za-z0-9 ._()/-]"), "_").take(80)
}
