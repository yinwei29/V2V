package com.yinwei.v2v.diagnostics

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class DiagnosticJournalTest {
    private class SensitiveFailure(message: String) : RuntimeException(message)
    private fun environment() = DiagnosticEnvironment("test", 35, "TestMaker", "TestPhone")
    @Test fun exportedEventsContainMetricsAndExceptionTypeButNeverExceptionMessage() {
        val dir = Files.createTempDirectory("diagnostics-test").toFile()
        val log = DiagnosticJournal(dir, environment())
        try {
            val exception = SensitiveFailure("sk-private-key private-transcript /private/source.wav")
            log.record(DiagnosticEvent.CRASH, error = exception.javaClass)
            log.record(DiagnosticEvent.PCM_SAMPLE, "12345678-abcd-abcd-abcd-123456789abc", mapOf(Metric.RMS to 0.25f, Metric.SAMPLES to 960))
            val result = ByteArrayOutputStream().also(log::export).toString("UTF-8")
            assertTrue(result.contains("SensitiveFailure")); assertTrue(result.contains("PCM_SAMPLE")); assertTrue(result.contains("0.25"))
            assertFalse(result.contains("sk-private-key")); assertFalse(result.contains("private-transcript")); assertFalse(result.contains("source.wav"))
            result.lines().filter { it.isNotBlank() }.forEach { JSONObject(it) }
        } finally { log.close(); dir.deleteRecursively() }
    }
    @Test fun rotationCapsDiskUseAndPreservesNewestEvents() {
        val dir = Files.createTempDirectory("diagnostics-test").toFile()
        val log = DiagnosticJournal(dir, environment(), maxFileBytes = 1024, fileCount = 3)
        try {
            repeat(80) { index ->
                log.record(DiagnosticEvent.PCM_SAMPLE, metrics = mapOf(Metric.FRAMES to index))
                log.flush()
            }
            assertTrue(dir.listFiles()!!.size <= 3)
            assertTrue(log.sizeBytes() <= 3 * (1024 + 512))
            val lines = ByteArrayOutputStream().also(log::export).toString("UTF-8").lines().filter { it.isNotBlank() }.map(::JSONObject)
            val frames = lines.filter { it.optString("event") == "PCM_SAMPLE" }.map { it.getInt("FRAMES") }
            assertEquals(79, frames.last()); assertEquals(frames.sorted(), frames)
        } finally { log.close(); dir.deleteRecursively() }
    }
    @Test fun logsSurviveProcessRecreationAndClearRemovesStoredEvents() {
        val dir = Files.createTempDirectory("diagnostics-test").toFile()
        DiagnosticJournal(dir, environment()).let { log -> log.record(DiagnosticEvent.SESSION_START); log.flush(); log.close() }
        val next = DiagnosticJournal(dir, environment())
        try {
            assertTrue(ByteArrayOutputStream().also(next::export).toString("UTF-8").contains("SESSION_START"))
            next.clear()
            assertEquals(0L, next.sizeBytes())
            assertFalse(ByteArrayOutputStream().also(next::export).toString("UTF-8").contains("SESSION_START"))
        } finally { next.close(); dir.deleteRecursively() }
    }
    @Test fun disabledLoggingAndNonFiniteMetricsDoNotProduceInvalidJson() {
        val dir = Files.createTempDirectory("diagnostics-test").toFile()
        val log = DiagnosticJournal(dir, environment())
        try {
            log.enabled = false; log.record(DiagnosticEvent.CRASH)
            assertFalse(ByteArrayOutputStream().also(log::export).toString("UTF-8").contains("CRASH"))
            log.enabled = true
            log.record(DiagnosticEvent.PCM_SAMPLE, metrics = mapOf(Metric.RMS to Float.NaN))
            val result = ByteArrayOutputStream().also(log::export).toString("UTF-8")
            assertFalse(result.contains("NaN"))
            result.lines().filter { it.isNotBlank() }.forEach { JSONObject(it) }
        } finally { log.close(); dir.deleteRecursively() }
    }
}
