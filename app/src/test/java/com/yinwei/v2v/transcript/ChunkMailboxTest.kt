package com.yinwei.v2v.transcript

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ChunkMailboxTest {
    private fun chunk(id: Long, seconds: Int) = AudioChunk(id, id * 10000, id * 10000 + seconds * 1000, ShortArray(seconds * 48000))
    @Test fun productionBacklogKeepsThreeLongChunksInOrderAndRejectsOverflow() = runBlocking {
        val mailbox = ChunkMailbox(maxQueuedSamples = 30 * 48000, maxChunks = 12)
        repeat(3) { assertTrue(mailbox.tryOffer(chunk(it.toLong(), 10))) }
        assertFalse(mailbox.tryOffer(chunk(3, 1)))
        assertEquals(30 * 48000, mailbox.queuedSamples)
        repeat(3) { assertEquals(it.toLong(), mailbox.receive()!!.id) }
        assertTrue(mailbox.tryOffer(chunk(3, 10)))
        mailbox.cancel(); assertEquals(0, mailbox.queuedSamples)
    }
    @Test fun shortChunksWaitWithinFiveSecondBudgetInsteadOfBeingDroppedImmediately() = runBlocking {
        val mailbox = ChunkMailbox()
        assertTrue(mailbox.tryOffer(chunk(1, 2)))
        assertTrue(mailbox.tryOffer(chunk(2, 2)))
        assertFalse(mailbox.tryOffer(chunk(3, 2)))
        assertEquals(192000, mailbox.queuedSamples)
        assertEquals(1L, mailbox.receive()!!.id)
        assertTrue(mailbox.tryOffer(chunk(3, 2)))
        assertEquals(2L, mailbox.receive()!!.id)
        assertEquals(3L, mailbox.receive()!!.id)
        assertEquals(0, mailbox.queuedSamples)
        mailbox.close(); assertNull(mailbox.receive())
    }
    @Test fun longContinuousSpeechIsHandedToWaitingWorkerWithoutExpandingBacklog() = runBlocking {
        val mailbox = ChunkMailbox()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { mailbox.receive() }
        assertTrue(mailbox.tryOffer(chunk(0, 10)))
        assertEquals(10 * 48000, waiting.await()!!.pcm.size)
        assertEquals(0, mailbox.queuedSamples)
        assertFalse(mailbox.tryOffer(chunk(1, 10)))
        mailbox.cancel()
    }
    @Test fun normalCloseDrainsWhileCancelClearsPendingAudio() = runBlocking {
        val normal = ChunkMailbox()
        normal.tryOffer(chunk(0, 1)); normal.close()
        assertEquals(0L, normal.receive()!!.id); assertNull(normal.receive())
        val cancelled = ChunkMailbox()
        cancelled.tryOffer(chunk(0, 1)); cancelled.cancel()
        assertEquals(0, cancelled.queuedSamples); assertNull(cancelled.receive())
        assertFalse(cancelled.tryOffer(chunk(1, 1)))
    }
}
