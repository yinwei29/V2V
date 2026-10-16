package com.yinwei.v2v.languages

import com.yinwei.v2v.history.*
import com.yinwei.v2v.transcript.TranscriptSegment
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.ZoneId

class HistoryLanguageTest {
    @Test fun legacyHistoryDefaultsToTraditionalChineseAndNewTargetsRoundTripIntoExport() {
        val directory = Files.createTempDirectory("v2v-language-history").toFile()
        val file = File(directory, "translations.json")
        try {
            file.writeText("""[{"session":"old","id":0,"revision":2,"createdAt":1,"startMs":0,"endMs":1000,"language":"en","original":"hello","translation":"你好"}]""")
            val store = HistoryStore(file); store.load()
            assertEquals("zh-TW", store.entries.value.single().targetLanguage)
            store.record(TranscriptSegment("new", 0, revision = 2, startMs = 0, endMs = 1000,
                originalText = "原文", translatedText = "English translation", sourceLanguage = "zh-TW", targetLanguage = "en"), true, 2)
            val loaded = HistoryStore(file); loaded.load()
            assertEquals("en", loaded.entries.value.last().targetLanguage)
            val text = HistoryText.export(loaded.entries.value, ZoneId.of("UTC"))
            assertTrue(text.contains("en → zh-TW")); assertTrue(text.contains("zh-TW → en"))
            assertTrue(text.contains("English translation"))
        } finally { directory.deleteRecursively() }
    }
}
