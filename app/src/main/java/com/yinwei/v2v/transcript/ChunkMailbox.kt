package com.yinwei.v2v.transcript

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.util.ArrayDeque

/** Direct handoff for long chunks; at most 5s / 3 short chunks wait behind an active request. */
class ChunkMailbox(private val maxQueuedSamples: Int = 240000, private val maxChunks: Int = 3) {
    private val direct = Channel<AudioChunk>(Channel.RENDEZVOUS)
    private val changed = Channel<Unit>(Channel.CONFLATED)
    private val queue = ArrayDeque<AudioChunk>()
    private val guard = Any()
    @Volatile private var closed = false
    private var samples = 0
    val queuedSamples: Int get() = synchronized(guard) { samples }
    fun tryOffer(chunk: AudioChunk): Boolean {
        val accepted = synchronized(guard) {
            if (closed) return false
            if (queue.isEmpty() && direct.trySend(chunk).isSuccess) return true
            if (queue.size >= maxChunks || chunk.pcm.size > maxQueuedSamples - samples) false
            else { queue.addLast(chunk); samples += chunk.pcm.size; true }
        }
        if (accepted) changed.trySend(Unit)
        return accepted
    }
    suspend fun receive(): AudioChunk? {
        while (true) {
            val pending = synchronized(guard) { queue.pollFirst()?.also { samples -= it.pcm.size } }
            if (pending != null) return pending
            if (closed) return null
            val incoming = select<AudioChunk?> {
                direct.onReceiveCatching { it.getOrNull() }
                changed.onReceiveCatching { null }
            }
            if (incoming != null) return incoming
        }
    }
    suspend fun sendTail(chunk: AudioChunk) = direct.send(chunk)
    fun close() { synchronized(guard) { closed = true }; direct.close(); changed.close() }
    fun cancel() {
        synchronized(guard) { closed = true; queue.clear(); samples = 0 }
        direct.cancel(); changed.cancel()
    }
}
