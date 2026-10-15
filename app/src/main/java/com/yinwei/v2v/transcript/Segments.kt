package com.yinwei.v2v.transcript

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class AudioChunk(val id: Long, val startMs: Long, val endMs: Long, val pcm: ShortArray)
data class TranscriptSegment(
    val sessionId: String, val id: Long, val revision: Int = 1,
    val startMs: Long, val endMs: Long, val originalText: String,
    val translatedText: String = "", val sourceLanguage: String,
    val isFinal: Boolean = true, val provider: String = "OpenAI whisper-1 / gpt-4o-mini",
    val translationLatencyMs: Long = 0,
)

/** Endpoint on silence; hard ceiling also handles uninterrupted speech. No partial/fake transcripts. */
class SpeechSegmenter(
    private val rate: Int = 48000, private val silenceMs: Int = 600,
    private val maxMs: Int = 10000, private val threshold: Float = 0.005f,
) {
    private var position = 0L
    private var start = 0L
    private var silentSamples = 0
    private var count = 0
    private var nextId = 0L
    private val buffer = ShortArray(rate * maxMs / 1000)
    init { require(rate > 0 && silenceMs in 300..1500 && maxMs in 1000..12000) }
    fun offer(frame: ShortArray, sourcePosition: Long = position): List<AudioChunk> {
        val chunks = mutableListOf<AudioChunk>()
        require(sourcePosition >= position) { "來源時間不可倒退" }
        if (sourcePosition != position) {
            flush()?.let(chunks::add)
            position = sourcePosition
        }
        var offset = 0
        while (offset < frame.size) {
            val take = minOf(frame.size - offset, buffer.size - count, 960)
            val voiced = sqrt((offset until offset + take).sumOf { val v = frame[it] / 32768.0; v * v } / take) >= threshold
            if (count == 0 && !voiced) { position += take; offset += take; continue }
            if (count == 0) start = position
            frame.copyInto(buffer, count, offset, offset + take)
            count += take; position += take; offset += take
            silentSamples = if (voiced) 0 else silentSamples + take
            if (count == buffer.size || silentSamples >= rate * silenceMs / 1000) flush()?.let(chunks::add)
        }
        return chunks
    }
    fun flush(): AudioChunk? {
        if (count == 0) return null
        val result = AudioChunk(nextId++, start * 1000 / rate, position * 1000 / rate, buffer.copyOf(count))
        count = 0; silentSamples = 0
        return result
    }
}

/** In-memory transcript only: bounded and session/revision checked, no disk history by default. */
class TranscriptLedger(private val sessionId: String, private val capacity: Int = 200) {
    private val entries = sortedMapOf<Long, TranscriptSegment>()
    @Synchronized fun accept(segment: TranscriptSegment): Boolean {
        if (segment.sessionId != sessionId || !segment.isFinal || segment.endMs <= segment.startMs) return false
        val old = entries[segment.id]
        if (old != null && segment.revision <= old.revision) return false
        if (entries.size >= capacity && segment.id !in entries && segment.id < entries.firstKey()) return false
        entries[segment.id] = segment
        while (entries.size > capacity) entries.remove(entries.firstKey())
        return true
    }
    @Synchronized fun snapshot(): List<TranscriptSegment> = entries.values.toList()
}

fun AudioChunk.wavBytes(rate: Int = 48000): ByteArray {
    val bytes = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    bytes.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVEfmt ".toByteArray())
        .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        .put("data".toByteArray()).putInt(pcm.size * 2)
    pcm.forEach { bytes.putShort(it) }
    return bytes.array()
}
