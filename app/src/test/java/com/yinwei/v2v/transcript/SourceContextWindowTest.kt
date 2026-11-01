package com.yinwei.v2v.transcript

import com.yinwei.v2v.providers.TranslationContext
import org.junit.Assert.*
import org.junit.Test

class SourceContextWindowTest {
    private fun chunk(id: Long, start: Long = id * 1000) = AudioChunk(id, start, start + 1000, shortArrayOf(1))
    @Test fun keepsOnlyTwoSuccessfulSourceSegmentsWithoutMutatingPriorSnapshot() {
        val window = SourceContextWindow()
        window.remember(chunk(0), "one")
        val snapshot = window.before(chunk(1))
        window.remember(chunk(1), "two"); window.remember(chunk(2), "three")
        assertEquals(listOf("one"), snapshot.precedingOriginals)
        assertEquals(listOf("two", "three"), window.before(chunk(3)).precedingOriginals)
    }
    @Test fun lostChunkLongSilenceAndReversedTimelineEachDiscardContext() {
        for (next in listOf(chunk(2), chunk(1, 5000), chunk(1, 500))) {
            val window = SourceContextWindow(); window.remember(chunk(0), "old")
            assertTrue(window.before(next).precedingOriginals.isEmpty())
        }
    }
    @Test fun exactSilenceBoundaryIsAllowedButLargerGapIsNot() {
        val window = SourceContextWindow(); window.remember(chunk(0), "source")
        assertEquals(listOf("source"), window.before(chunk(1, 4000)).precedingOriginals)
        assertTrue(window.before(chunk(1, 4001)).precedingOriginals.isEmpty())
    }
    @Test fun emptyOversizedAndExplicitFailureResetCannotPolluteLaterRequest() {
        for (text in listOf("", " ", "x".repeat(1201))) {
            val window = SourceContextWindow(); window.remember(chunk(0), "old")
            window.remember(chunk(1), text)
            assertTrue(window.before(chunk(2)).precedingOriginals.isEmpty())
        }
        val window = SourceContextWindow(); window.remember(chunk(0), "source"); window.clear()
        assertTrue(window.before(chunk(1)).precedingOriginals.isEmpty())
    }
    @Test fun providerBoundaryCapsCountAndSizeWithoutTruncatingUnicodeOrSource() {
        assertEquals(listOf("b", "c"), TranslationContext.from(listOf("a", "b", "c")).precedingOriginals)
        assertEquals(listOf("中文😀"), TranslationContext.from(listOf("x".repeat(1201), "中文😀")).precedingOriginals)
    }
}
