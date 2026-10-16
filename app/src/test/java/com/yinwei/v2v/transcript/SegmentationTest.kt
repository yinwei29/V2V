package com.yinwei.v2v.transcript

import com.yinwei.v2v.audio.WavReader
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SegmentationTest {
    private fun speech(ms: Int) = ShortArray(48 * ms) { 3000 }
    private fun silence(ms: Int) = ShortArray(48 * ms)
    @Test fun geminiGroupsShortSpeechWithoutLosingTailOrChangingTimeline() {
        val segmenter = SpeechSegmenter(minMs = 4000)
        assertTrue(segmenter.offer(speech(1000)).isEmpty())
        assertTrue(segmenter.offer(silence(600)).isEmpty())
        assertTrue(segmenter.offer(speech(1800)).isEmpty())
        val chunk = segmenter.offer(silence(600)).single()
        assertEquals(0L, chunk.startMs); assertEquals(4000L, chunk.endMs)
        assertArrayEquals(speech(1000) + silence(600) + speech(1800) + silence(600), chunk.pcm)
        segmenter.offer(speech(1000))
        assertEquals(5000L, segmenter.flush()!!.endMs)
        assertNull(segmenter.flush())
    }
    @Test fun silenceDoesNotCreateHallucinatedInputAndSpeechEndpointsBeforeWholeFileEnds() {
        val s = SpeechSegmenter()
        assertTrue(s.offer(silence(1000)).isEmpty())
        assertTrue(s.offer(speech(1000)).isEmpty())
        assertTrue(s.offer(silence(580)).isEmpty())
        val chunk = s.offer(silence(20)).single()
        assertEquals(1000L, chunk.startMs)
        assertEquals(2600L, chunk.endMs)
        assertNull(s.flush())
    }
    @Test fun continuousSpeechSplitsAtTenSecondsAndNeverDuplicatesTail() {
        val s = SpeechSegmenter()
        val chunks = s.offer(speech(21000))
        assertEquals(listOf(10000L, 20000L), chunks.map { it.endMs })
        val tail = s.flush()!!
        assertEquals(20000L, tail.startMs)
        assertEquals(21000L, tail.endMs)
        assertEquals(2L, tail.id)
        assertNull(s.flush())
    }
    @Test fun droppedFramesPreserveSourceTimelineInsteadOfCompressingTime() {
        val s = SpeechSegmenter()
        s.offer(speech(1000))
        val prior = s.offer(speech(1000), sourcePosition = 48L * 5000).single()
        assertEquals(1000L, prior.endMs)
        val after = s.flush()!!
        assertEquals(5000L, after.startMs)
        assertEquals(6000L, after.endMs)
    }
    @Test fun ledgerRejectsOldSessionAndRevisionAndKeepsBoundedSortedFinals() {
        val ledger = TranscriptLedger("new", 2)
        fun item(session: String, id: Long, rev: Int = 1) = TranscriptSegment(session, id, rev, id * 1000, id * 1000 + 999, "speech", sourceLanguage = "en")
        assertFalse(ledger.accept(item("old", 1)))
        assertTrue(ledger.accept(item("new", 2, 2)))
        assertFalse(ledger.accept(item("new", 2, 1)))
        assertFalse(ledger.accept(item("new", 2, 2)))
        assertTrue(ledger.accept(item("new", 1)))
        assertTrue(ledger.accept(item("new", 3)))
        assertEquals(listOf(2L, 3L), ledger.snapshot().map { it.id })
        assertFalse(ledger.accept(item("new", 0)))
        assertFalse(ledger.accept(item("new", 4).copy(isFinal = false)))
    }
    @Test fun wavEncoderAndReaderPreserveSamplesAndLastShortFrame() {
        val pcm = speech(21)
        val wav = AudioChunk(0, 0, 21, pcm).wavBytes()
        WavReader(ByteArrayInputStream(wav)).use { reader ->
            assertArrayEquals(pcm.copyOfRange(0, 960), reader.readFrame())
            assertArrayEquals(pcm.copyOfRange(960, pcm.size), reader.readFrame())
            assertNull(reader.readFrame())
        }
        assertEquals(pcm.size * 2, ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int)
    }
    @Test fun unsupportedRateRejectedRatherThanPretendingToResample() {
        val wav = AudioChunk(0, 0, 100, speech(100)).wavBytes(rate = 16000)
        try { WavReader(ByteArrayInputStream(wav)); fail("Unsupported rate accepted") }
        catch (e: IllegalArgumentException) { assertEquals(WavReader.FORMAT, e.message) }
    }
    @Test fun truncatedDataFailsRatherThanReturningUninitializedPcm() {
        val wav = AudioChunk(0, 0, 100, speech(100)).wavBytes().dropLast(200).toByteArray()
        WavReader(ByteArrayInputStream(wav)).use { reader ->
            try { repeat(10) { reader.readFrame() }; fail("Truncation accepted") } catch (_: EOFException) { }
        }
    }
}
