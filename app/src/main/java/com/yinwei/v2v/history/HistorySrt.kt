package com.yinwei.v2v.history

import java.util.Locale

/** SRT uses source-relative time within ONE session, never archive wall-clock time. */
object HistorySrt {
    const val MAX_TIMESTAMP_MS = 359_999_999L // 99:59:59,999, compatible two-digit SRT hours.
    data class Result(val text: String, val skippedInvalid: Int)
    fun export(entries: List<HistoryEntry>, sessionId: String, bilingual: Boolean = false): String =
        prepare(entries, sessionId, bilingual).text
    fun prepare(entries: List<HistoryEntry>, sessionId: String, bilingual: Boolean = false): Result {
        require(sessionId.isNotBlank()) { "Session required" }
        val latest = entries.filter { it.sessionId == sessionId }
            .groupBy { it.segmentId }.values.map { versions -> versions.maxBy { it.revision } }
        val cues = latest.filter { it.revision > 0 && text(it.translation).isNotBlank() && it.startMs >= 0 &&
                it.endMs > it.startMs && it.endMs <= MAX_TIMESTAMP_MS }
            .sortedWith(compareBy<HistoryEntry> { it.startMs }.thenBy { it.segmentId })
        val rendered = buildString {
            cues.forEachIndexed { index, entry ->
                append(index + 1).append('\n')
                append(timestamp(entry.startMs)).append(" --> ").append(timestamp(entry.endMs)).append('\n')
                if (bilingual && entry.original.isNotBlank()) append(text(entry.original)).append('\n')
                append(text(entry.translation)).append("\n\n")
            }
        }
        return Result(rendered, latest.size - cues.size)
    }
    private fun timestamp(ms: Long): String = String.format(Locale.ROOT, "%02d:%02d:%02d,%03d",
        ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
    private fun text(value: String): String = value.replace("\r\n", "\n").replace('\r', '\n')
        .filter { it == '\n' || it == '\t' || !it.isISOControl() }
        .lines().map(String::trim).filter(String::isNotBlank).joinToString("\n")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
