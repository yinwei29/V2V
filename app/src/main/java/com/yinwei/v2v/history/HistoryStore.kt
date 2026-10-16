package com.yinwei.v2v.history

import com.yinwei.v2v.transcript.TranscriptSegment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class HistoryEntry(val sessionId: String, val segmentId: Long, val revision: Int, val createdAt: Long,
    val startMs: Long, val endMs: Long, val sourceLanguage: String, val original: String, val translation: String, val targetLanguage: String = "zh-TW")

/** Private, bounded transcript archive. Only explicit final publications are appended, never UI snapshots. */
class HistoryStore(private val file: File, private val maxEntries: Int = 1000, private val maxBytes: Int = 4 * 1024 * 1024) {
    private val mutable = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries = mutable.asStateFlow()
    private val problem = MutableStateFlow("")
    val error = problem.asStateFlow()
    private var loaded = false
    private var clearedSession = ""
    private var clearedThrough = -1L
    @Synchronized fun load() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        try {
            check(file.length() <= maxBytes)
            val data = JSONArray(file.readText(Charsets.UTF_8))
            mutable.value = (0 until data.length()).map { i -> data.getJSONObject(i).let { r ->
                HistoryEntry(r.getString("session"), r.getLong("id"), r.getInt("revision"), r.getLong("createdAt"),
                    r.getLong("startMs"), r.getLong("endMs"), r.getString("language"), r.getString("original"), r.getString("translation"), r.optString("targetLanguage", "zh-TW"))
            } }.takeLast(maxEntries)
        } catch (_: Exception) { problem.value = "紀錄檔無法讀取；既有檔案未被覆寫，可清除後重新保存" }
    }
    @Synchronized fun record(segment: TranscriptSegment, enabled: Boolean, now: Long = System.currentTimeMillis()) {
        if (segment.sessionId == clearedSession && segment.id <= clearedThrough) return
        if (!enabled || !segment.isFinal || segment.translatedText.isBlank()) return
        load()
        if (problem.value.isNotEmpty()) return
        val previous = mutable.value
        val matching = previous.firstOrNull { it.sessionId == segment.sessionId && it.segmentId == segment.id }
        if (matching != null && matching.revision >= segment.revision) return
        val entry = HistoryEntry(segment.sessionId, segment.id, segment.revision, matching?.createdAt ?: now,
            segment.startMs, segment.endMs, segment.sourceLanguage, segment.originalText, segment.translatedText, segment.targetLanguage)
        val next = (previous.filterNot { it.sessionId == entry.sessionId && it.segmentId == entry.segmentId } + entry)
            .sortedWith(compareBy<HistoryEntry> { it.createdAt }.thenBy { it.segmentId }).takeLast(maxEntries).toMutableList()
        try {
            var encoded = encode(next)
            while (encoded.toByteArray(Charsets.UTF_8).size > maxBytes && next.isNotEmpty()) { next.removeAt(0); encoded = encode(next) }
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, file.name + ".tmp")
            try {
                temporary.outputStream().use { stream -> stream.write(encoded.toByteArray(Charsets.UTF_8)); stream.fd.sync() }
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally { temporary.delete() }
            mutable.value = next.toList()
        } catch (_: Exception) { problem.value = "紀錄保存失敗；字幕與播報仍可使用，請檢查儲存空間" }
    }
    @Synchronized fun clear(sessionId: String = "", throughSegmentId: Long = -1) {
        if (file.exists() && !file.delete()) error("Cannot clear history")
        File(file.parentFile, file.name + ".tmp").delete()
        clearedSession = sessionId; clearedThrough = throughSegmentId
        loaded = true; mutable.value = emptyList(); problem.value = ""
    }
    private fun encode(entries: List<HistoryEntry>): String = JSONArray().apply { entries.forEach { e ->
        put(JSONObject().put("session", e.sessionId).put("id", e.segmentId).put("revision", e.revision).put("createdAt", e.createdAt)
            .put("startMs", e.startMs).put("endMs", e.endMs).put("language", e.sourceLanguage).put("original", e.original).put("translation", e.translation).put("targetLanguage", e.targetLanguage))
    } }.toString()
}

object HistoryText {
    fun export(entries: List<HistoryEntry>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        append("V2V 翻譯紀錄\n共 ${entries.size} 段\n\n")
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone)
        entries.forEachIndexed { index, e ->
            append("${index + 1}. ${format.format(Instant.ofEpochMilli(e.createdAt))}  ${e.sourceLanguage} → ${e.targetLanguage}\n")
            append("原文：${e.original}\n譯文：${e.translation}\n\n")
        }
    }
}
