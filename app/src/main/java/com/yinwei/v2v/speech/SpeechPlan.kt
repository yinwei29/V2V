package com.yinwei.v2v.speech

/** Lossless short phrases; boundary detection works on code points, including emoji. */
object SpeechPlan {
    fun phrases(text: String, limit: Int = 18): List<String> {
        require(limit >= 2)
        val points = text.codePoints().toArray()
        val result = mutableListOf<String>()
        var start = 0
        while (start < points.size) {
            val maximum = minOf(start + limit, points.size)
            var end = maximum
            // Prefer punctuation/whitespace, without breaking an ordinary short Latin word.
            for (i in start until maximum) {
                if (points[i].toChar() in "。！？；，、.!?;,\n " && i - start >= limit / 3) { end = i + 1; break }
            }
            if (end == maximum && end < points.size && Character.isLetterOrDigit(points[end]) && points[end] < 128) {
                val boundary = (start until end).lastOrNull { points[it] == 32 }
                if (boundary != null && boundary > start) end = boundary + 1
            }
            result += String(points, start, end - start)
            start = end
        }
        return result
    }
    fun rate(base: Float, automatic: Boolean, text: String, sourceDurationMs: Long,
        pending: Int, bufferedMs: Long): Float {
        val preferred = base.coerceIn(0.75f, 2f)
        if (!automatic) return preferred
        val units = text.codePoints().toArray().sumOf { p ->
            when { Character.isWhitespace(p) -> 0.0; p < 128 && Character.isLetter(p) -> 0.25
                Character.isDigit(p) -> 0.5; Character.isLetter(p) -> 1.0; else -> 0.15 }
        }
        val durationRate = if (sourceDurationMs > 0) (units / 4.6 / (sourceDurationMs / 1000.0)).toFloat() else preferred
        val backlogRate = if (pending > 1 || bufferedMs > 4000) 2f else preferred
        return maxOf(preferred, durationRate, backlogRate).coerceAtMost(2f)
    }
}

data class SpeechRequest(val key: Long, val segmentId: Long, val revision: Int, val text: String,
    val sourceDurationMs: Long)

/** Three translated segments total. Each may produce many phrases; PCM remains <=10 seconds. */
class SpeechQueue(private val maxSegments: Int = 3, private val maxSamples: Int = 480000) {
    private data class Record(val request: SpeechRequest, var producing: Boolean = true, var chunks: Int = 0)
    private data class Chunk(val key: Long, val pcm: ShortArray, var offset: Int = 0)
    private val records = LinkedHashMap<Long, Record>()
    private val waiting = ArrayDeque<SpeechRequest>()
    private val audio = ArrayDeque<Chunk>()
    private var samples = 0
    @Synchronized fun enqueue(request: SpeechRequest): Boolean {
        if (records.size >= maxSegments || records.containsKey(request.key)) return false
        records[request.key] = Record(request); waiting.addLast(request); return true
    }
    @Synchronized fun next(): SpeechRequest? = if (waiting.isEmpty()) null else waiting.removeFirst()
    @Synchronized fun active(key: Long) = records.containsKey(key)
    @Synchronized fun append(key: Long, pcm: ShortArray): Boolean {
        val record = records[key] ?: return false
        if (!record.producing || pcm.isEmpty() || pcm.size > maxSamples - samples) return false
        record.chunks++; audio.addLast(Chunk(key, pcm)); samples += pcm.size
        return true
    }
    @Synchronized fun complete(key: Long) {
        val record = records[key] ?: return
        record.producing = false
        if (record.chunks == 0) records.remove(key)
    }
    @Synchronized fun take(size: Int): ShortArray = ShortArray(size).also { result ->
        var offset = 0
        while (offset < size && audio.isNotEmpty()) {
            val chunk = audio.first()
            val count = minOf(size - offset, chunk.pcm.size - chunk.offset)
            chunk.pcm.copyInto(result, offset, chunk.offset, chunk.offset + count)
            chunk.offset += count; offset += count; samples -= count
            if (chunk.offset == chunk.pcm.size) {
                audio.removeFirst()
                records[chunk.key]?.let { record ->
                    record.chunks--
                    if (!record.producing && record.chunks == 0) records.remove(chunk.key)
                }
            }
        }
    }
    @Synchronized fun clear() { records.clear(); waiting.clear(); audio.clear(); samples = 0 }
    @Synchronized fun pending() = records.size
    @Synchronized fun bufferedMs() = samples * 1000L / 48000
    @Synchronized fun hasAudio() = samples > 0
}
