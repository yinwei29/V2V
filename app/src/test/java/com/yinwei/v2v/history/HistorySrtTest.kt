package com.yinwei.v2v.history

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class HistorySrtTest {
    private fun entry(id: Long = 0, session: String = "a", start: Long = 1000, end: Long = 2500,
        revision: Int = 2, text: String = "你好 😀") = HistoryEntry(session, id, revision, 123456789,
            start, end, "en", "Hello", text, "zh-TW")
    @Test fun exactSrtUsesSourceTimeAndPreservesUtf8() {
        val result = HistorySrt.export(listOf(entry()), "a")
        assertEquals("1\n00:00:01,000 --> 00:00:02,500\n你好 😀\n\n", result)
        assertEquals(result, result.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
    }
    @Test fun sessionIsolationKeepsIndependentTimelinesSeparate() {
        val result = HistorySrt.export(listOf(entry(), entry(session = "b", text = "other")), "a")
        assertFalse(result.contains("other")); assertEquals(1, result.split(" --> ").size - 1)
        assertEquals("", HistorySrt.export(listOf(entry()), "unknown"))
    }
    @Test fun newestRevisionOnlyAndSourceOrderRetainRealGaps() {
        val result = HistorySrt.export(listOf(entry(9, start=10000, end=12000), entry(1),
            entry(1, revision=3, text="Corrected"), entry(1, revision=2, text="Old")), "a")
        assertFalse(result.contains("Old")); assertEquals(1, result.split("Corrected").size-1)
        assertTrue(result.indexOf("Corrected") < result.indexOf("00:00:10,000"))
        assertTrue(result.contains("2\n00:00:10,000 --> 00:00:12,000"))
    }
    @Test fun invalidOrUntranslatedTimesNeverBecomeFabricatedCues() {
        val result = HistorySrt.export(listOf(entry(1, start=-1), entry(2, end=1000),
            entry(3, end=Long.MAX_VALUE), entry(4, text=""), entry(5, revision=0), entry(6, text="\u0000")), "a")
        assertEquals("",result)
        val mixed = HistorySrt.prepare(listOf(entry(), entry(1, start=-1)), "a")
        assertEquals(1, mixed.skippedInvalid); assertTrue(mixed.text.contains("你好"))
        assertTrue(HistorySrt.export(listOf(entry(end=HistorySrt.MAX_TIMESTAMP_MS)), "a").contains("99:59:59,999"))
    }
    @Test fun bilingualTextNormalizesCueSeparatorsAndEscapesMarkup() {
        val result = HistorySrt.export(listOf(entry(text="<b>A&B</b>\r\n\r\n第二行\u0000").copy(original="Original\r\n\r\nline")),"a",true)
        assertTrue(result.contains("Original\nline\n&lt;b&gt;A&amp;B&lt;/b&gt;\n第二行\n\n"))
        assertFalse(result.contains("\r"));assertFalse(result.contains("\u0000"))
    }
    @Test fun timestampsUseAsciiDigitsRegardlessOfDeviceLocale() {
        val previous=Locale.getDefault()
        try {Locale.setDefault(Locale.forLanguageTag("ar"));assertTrue(HistorySrt.export(listOf(entry()),"a").contains("00:00:01,000"))}
        finally {Locale.setDefault(previous)}
    }
}
