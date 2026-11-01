package com.yinwei.v2v.transcript

import com.yinwei.v2v.providers.TranslationContext

/** Worker-owned, per-session source context. No old translations to reinforce mistakes. */
internal class SourceContextWindow {
    private val originals = ArrayDeque<String>()
    private var lastEndMs: Long? = null
    private var lastId: Long? = null
    fun before(chunk: AudioChunk): TranslationContext {
        val end = lastEndMs
        if (end != null && (chunk.startMs < end || chunk.startMs - end > 3000 || chunk.id != lastId?.plus(1))) clear()
        return TranslationContext.from(originals.toList())
    }
    fun remember(chunk: AudioChunk, original: String) {
        if (original.isBlank() || original.length > 1200) { clear(); return }
        originals.addLast(original)
        while (originals.size > 2) originals.removeFirst()
        lastEndMs = chunk.endMs
        lastId = chunk.id
    }
    fun clear() { originals.clear(); lastEndMs = null; lastId = null }
}
