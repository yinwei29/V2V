package com.yinwei.v2v.history

import com.yinwei.v2v.transcript.TranscriptSegment
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.time.ZoneId

class HistoryStoreTest {
    private fun segment(id: Long = 1, revision: Int = 2) = TranscriptSegment("test-session", id, revision, 0, 2000,
        "Hello\n世界 😀", "你好，世界 😀", "en")
    private fun file() = Files.createTempDirectory("history-test").resolve("archive.json").toFile()
    @Test fun finalRecordsPersistAcrossRestartAndDoNotDuplicateOrRegress() {
        val file = file(); val store = HistoryStore(file)
        store.record(segment(), true, 1000); store.record(segment(), true, 2000)
        store.record(segment(revision = 1).copy(translatedText = "舊版本"), true, 3000)
        store.record(segment(revision = 3).copy(translatedText = "更新譯文"), true, 4000)
        val reloaded = HistoryStore(file).apply { load() }
        assertEquals(1, reloaded.entries.value.size)
        assertEquals("更新譯文", reloaded.entries.value.single().translation)
        assertEquals(1000L, reloaded.entries.value.single().createdAt)
    }
    @Test fun disabledPartialAndUntranslatedTextNeverSave() {
        val file = file(); val store = HistoryStore(file)
        store.record(segment(), false)
        store.record(segment().copy(isFinal = false), true)
        store.record(segment().copy(translatedText = ""), true)
        assertTrue(store.entries.value.isEmpty()); assertFalse(file.exists())
    }
    @Test fun clearDoesNotResurrectAlreadyPublishedCurrentSessionFinals() {
        val file = file(); val store = HistoryStore(file)
        store.record(segment(), true)
        store.clear("test-session", 2)
        store.record(segment(2), true)
        assertTrue(store.entries.value.isEmpty()); assertFalse(file.exists())
        store.record(segment(3), true)
        assertEquals(3L, store.entries.value.single().segmentId)
        assertEquals(1, HistoryStore(file).apply { load() }.entries.value.size)
        store.clear(); assertTrue(HistoryStore(file).apply { load() }.entries.value.isEmpty())
    }
    @Test fun oldestEntriesExpireWithinCountAndByteBounds() {
        val file = file(); val store = HistoryStore(file, maxEntries = 2, maxBytes = 2048)
        repeat(5) { store.record(segment(it.toLong()), true, it.toLong()) }
        assertEquals(listOf(3L, 4L), store.entries.value.map { it.segmentId })
        assertTrue(file.length() <= 2048)
    }
    @Test fun corruptedHistoryIsNotOverwrittenAndCanBeCleared() {
        val file = file().apply { writeText("broken-existing-data") }; val store = HistoryStore(file)
        store.load(); store.record(segment(), true)
        assertTrue(store.error.value.isNotBlank()); assertEquals("broken-existing-data", file.readText())
        store.clear(); store.record(segment(), true)
        assertEquals(1, store.entries.value.size); assertTrue(store.error.value.isBlank())
    }
    @Test fun exportIsReadableUtf8AndPreservesOriginalTranslationAndChronology() {
        val store = HistoryStore(file())
        store.record(segment(), true, 0)
        val text = HistoryText.export(store.entries.value, ZoneId.of("Asia/Jakarta"))
        assertTrue(text.contains("1970-01-01 07:00:00"))
        assertTrue(text.contains("原文：Hello\n世界 😀"))
        assertTrue(text.contains("譯文：你好，世界 😀"))
        assertEquals(text, text.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
    }
}
