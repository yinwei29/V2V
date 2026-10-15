package com.yinwei.v2v.ui

import com.yinwei.v2v.history.*
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.core.content.FileProvider
import com.yinwei.v2v.diagnostics.*
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.lifecycleScope
import com.yinwei.v2v.audio.*
import com.yinwei.v2v.providers.*
import com.yinwei.v2v.security.ApiKeyStore
import com.yinwei.v2v.session.*
import com.yinwei.v2v.settings.SettingsStore
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val repository: SessionRepository by inject()
    private val history: HistoryStore by inject()
    private var historyNotice by mutableStateOf("")
    private var historyBusy by mutableStateOf(false)
    private var pendingHistoryExport: String? = null
    private val exportHistory = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val snapshot = pendingHistoryExport; pendingHistoryExport = null
        if (uri != null && snapshot != null) lifecycleScope.launch {
            historyBusy = true
            try {
                withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(snapshot) } ?: error("Cannot export") }
                historyNotice = "文字檔已匯出"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { historyNotice = "匯出失敗，請重新選擇位置" }
            finally { historyBusy = false }
        }
    }
    private val settings: SettingsStore by inject()
    private val keys: ApiKeyStore by inject()
    private val diagnostics: Diagnostics by inject()
    private var logNotice by mutableStateOf("")
    private var logBusy by mutableStateOf(false)
    private val exportLog = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")) { uri ->
        if (uri != null) lifecycleScope.launch {
            logBusy = true
            try {
                withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri, "wt")?.use { diagnostics.journal.export(it) } ?: error("Cannot open export") }
                logNotice = "已匯出技術 Log；可附在聊天中協助開發"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { logNotice = "Log 匯出失敗，請重新選擇儲存位置" }
            finally { logBusy = false }
        }
    }
    private var fileUri by mutableStateOf<String?>(null)
    private var pendingExternal = false
    private var pendingCloud = false
    private var pendingLanguage = "en"
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            fileUri = uri.toString()
        }
    }
    private val captureConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        diagnostics.journal.record(DiagnosticEvent.PERMISSION, metrics = mapOf(Metric.GRANTED to if (result.resultCode == Activity.RESULT_OK) 1 else 0), tags = mapOf(Tag.PERMISSION to PermissionKind.PROJECTION))
        if (result.resultCode == Activity.RESULT_OK && result.data != null) startSession(true, result.resultCode, result.data)
        else repository.update { it.copy(state = SessionState.IDLE, message = "已取消擷取授權；沒有啟動錄音") }
    }
    private val recordPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        diagnostics.journal.record(DiagnosticEvent.PERMISSION, metrics = mapOf(Metric.GRANTED to if (granted) 1 else 0), tags = mapOf(Tag.PERMISSION to PermissionKind.AUDIO))
        if (granted) askProjection() else repository.update {
            it.copy(state = SessionState.IDLE, message = "未授予音訊擷取權限；沒有啟動，也不會改用麥克風") }
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        diagnostics.journal.record(DiagnosticEvent.PERMISSION, metrics = mapOf(Metric.GRANTED to if (granted) 1 else 0), tags = mapOf(Tag.PERMISSION to PermissionKind.NOTIFICATION))
        continueStart()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileUri = savedInstanceState?.getString("file")
        pendingExternal = savedInstanceState?.getBoolean("external") ?: false
        pendingCloud = savedInstanceState?.getBoolean("cloud") ?: false
        pendingLanguage = savedInstanceState?.getString("language") ?: "en"
        // Includes the credential form: keys never appear in screenshots or app-switcher previews.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF086B63), secondary = Color(0xFF486C7A),
                background = Color(0xFFF4F7F8), surface = Color.White,
                onSurface = Color(0xFF172F37), surfaceVariant = Color(0xFFE7F0F1), secondaryContainer = Color(0xFFDFF2EE))) {
                val state by repository.state.collectAsStateWithLifecycle()
                val mix by settings.settings.collectAsStateWithLifecycle(MixSettings())
                val presentation by settings.presentation.collectAsStateWithLifecycle(com.yinwei.v2v.settings.PresentationSettings())
                val archive by history.entries.collectAsStateWithLifecycle()
                val historyError by history.error.collectAsStateWithLifecycle()
                val logEnabled by settings.diagnosticsEnabled.collectAsStateWithLifecycle(true)
                AppScreen(state, mix, presentation, { changed -> lifecycleScope.launch { settings.savePresentation(changed) } }, fileUri != null, keys, { filePicker.launch(arrayOf("audio/*", "application/octet-stream")) },
                    { source, cloud, language ->
                        pendingExternal = source == Source.SYSTEM_PLAYBACK
                        pendingCloud = cloud && source != Source.TEST_FILE
                        pendingLanguage = language
                        if (source == Source.TEST_FILE) fileUri = null
                        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else continueStart()
                    }, { command ->
                        when (command) {
                            "overlayPermission" -> startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                            "soundSettings" -> startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS))
                            "ttsSettings" -> runCatching { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                            else -> startService(Intent(this, TranslationForegroundService::class.java).setAction(command))
                        }
                    },
                    { changed -> lifecycleScope.launch { settings.save(changed) } }, diagnostics, logEnabled,
                    { enabled -> lifecycleScope.launch { settings.setDiagnosticsEnabled(enabled) } }, logNotice, logBusy,
                    { exportLog.launch("V2V-technical-${System.currentTimeMillis()}.jsonl") }, ::shareLog,
                    archive, historyError, historyNotice, historyBusy,
                    { pendingHistoryExport = HistoryText.export(archive); exportHistory.launch("V2V-翻譯紀錄-${System.currentTimeMillis()}.txt") },
                    { lifecycleScope.launch {
                        historyBusy = true
                        try { withContext(Dispatchers.IO) {
                            val current = repository.state.value
                            history.clear(current.id, current.segments.filter { it.translatedText.isNotBlank() }.maxOfOrNull { it.id } ?: -1)
                        }; historyNotice = "已清除翻譯紀錄" }
                        catch (_: Exception) { historyNotice = "清除失敗，請稍後重試" }
                        finally { historyBusy = false }
                    } })
            }
        }
    }
    private fun shareLog() {
        lifecycleScope.launch {
            logBusy = true
            try {
                val file = withContext(Dispatchers.IO) {
                    val directory = File(cacheDir, "diagnostics").apply { mkdirs() }
                    directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.delete() }
                    File(directory, "V2V-technical-${System.currentTimeMillis()}.jsonl").also { output ->
                        output.outputStream().use { diagnostics.journal.export(it) }
                    }
                }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.diagnostics", file)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/x-ndjson")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "分享 V2V 技術 Log"))
                logNotice = "已開啟系統分享；請自行選擇接收對象"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { logNotice = "無法分享，請改用匯出檔案" }
            finally { logBusy = false }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("file", fileUri); outState.putBoolean("external", pendingExternal)
        outState.putBoolean("cloud", pendingCloud); outState.putString("language", pendingLanguage)
        super.onSaveInstanceState(outState)
    }
    private fun continueStart() {
        if (!pendingExternal) { startSession(false); return }
        repository.update { it.copy(state = SessionState.REQUESTING_PERMISSION, source = Source.SYSTEM_PLAYBACK,
            capabilities = SourceCapabilities(), message = "需要本次音訊／螢幕擷取授權；不啟用麥克風") }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) askProjection()
        else recordPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    private fun askProjection() = captureConsent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    private fun startSession(external: Boolean, result: Int = Activity.RESULT_CANCELED, consent: Intent? = null) {
        try {
            startForegroundService(Intent(this, TranslationForegroundService::class.java)
                .setAction(TranslationForegroundService.START).putExtra("external", external)
                .putExtra("resultCode", result).putExtra("consent", consent)
                .putExtra("fileUri", if (!external) fileUri else null)
                .putExtra("cloudConsent", pendingCloud).putExtra("language", pendingLanguage))
        } catch (_: Exception) { repository.update { it.copy(state = SessionState.ERROR, message = "無法啟動前景服務，請保持主畫面可見後重試") } }
    }
}

@Composable
internal fun KeyPanel(keys: ApiKeyStore, configured: Boolean, enabled: Boolean, journal: DiagnosticJournal, refresh: () -> Unit) {
    var value by remember { mutableStateOf("") } // Deliberately NOT rememberSaveable.
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Text("OpenAI 引擎與 Key", style = MaterialTheme.typography.titleLarge)
    Text(if (configured) "已儲存加密 Key；不顯示原值" else "尚未設定 Key；不會有真實雲端字幕")
    OutlinedTextField(value = value, onValueChange = { value = it.take(1024) }, label = { Text("API Key（僅存此裝置）") },
        visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            busy = true
            val pending = value; value = ""
            scope.launch {
                try { withContext(Dispatchers.IO) { keys.save(pending) }; refresh(); message = "已用 Keystore 加密儲存" }
                catch (_: Exception) { message = "無法儲存，請確認裝置 Keystore 並重新輸入" }
                finally { busy = false }
            }
        }, enabled = enabled && !busy && value.isNotBlank()) { Text("儲存 / 更新") }
        OutlinedButton(onClick = {
            busy = true; value = ""
            scope.launch {
                try { withContext(Dispatchers.IO) { keys.remove() }; refresh(); message = "已移除 Key" }
                catch (_: Exception) { message = "無法移除 Key，請稍後重試" }
                finally { busy = false }
            }
        }, enabled = enabled && !busy && configured) { Text("移除") }
    }
    OutlinedButton(onClick = {
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { OpenAiProvider(checkNotNull(keys.read()), diagnostics = journal).healthCheck() }
                message = "Key 驗證及模型查詢成功；仍需音訊端到端測試，不代表扣款／轉寫已驗收"
            } catch (e: CancellationException) { throw e }
            catch (e: ProviderFailure) { message = e.message.orEmpty() }
            catch (_: Exception) { message = "連線失敗或 Key 已失效，請重新設定" }
            finally { busy = false }
        }
    }, enabled = enabled && !busy && configured) { Text(if (busy) "處理中…" else "測試連線（僅傳 Key，不傳音訊）") }
    if (message.isNotBlank()) Text(message)
    Text("OpenAI audio/transcriptions: whisper-1；chat/completions: gpt-4o-mini。分段 final、600ms 斷句、最長 10 秒。供應商可能收費；本版不報費用估算。", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Choice(title: String, subtitle: String, selected: Boolean, enabled: Boolean, choose: () -> Unit) {
    OutlinedCard(onClick = choose, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            RadioButton(selected = selected, onClick = choose, enabled = enabled)
            Column(Modifier.padding(start = 8.dp, top = 6.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
@Composable
internal fun Detail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary); Text(value) }
}

@Composable
internal fun DiagnosticPanel(diagnostics: Diagnostics, enabled: Boolean, setEnabled: (Boolean) -> Unit,
    notice: String, exportBusy: Boolean, export: () -> Unit, share: () -> Unit) {
    val scope = rememberCoroutineScope()
    var clearing by remember { mutableStateOf(false) }
    var clearNotice by remember { mutableStateOf("") }
    Text("測試 Log", style = MaterialTheme.typography.titleLarge)
    Row { Switch(checked = enabled, onCheckedChange = setEnabled); Text(if (enabled) "技術事件記錄中" else "技術事件記錄已關閉", Modifier.padding(start = 12.dp, top = 12.dp)) }
    Text("記錄啟停、權限、每秒電平、音訊路由、provider 狀態與錯誤類型。不含錄音、字幕、API Key、檔案路徑或裝置識別碼。含型號／Android 版本，僅存本機，最多 3 × 512KiB。", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = export, enabled = !exportBusy && !clearing) { Text("匯出 Log") }
        OutlinedButton(onClick = share, enabled = !exportBusy && !clearing) { Text("分享 Log") }
    }
    OutlinedButton(onClick = {
        clearing = true
        scope.launch {
            try { withContext(Dispatchers.IO) { diagnostics.journal.clear() }; clearNotice = "已清除既有 Log；若記錄開啟，新的測試事件會繼續寫入" }
            catch (_: Exception) { clearNotice = "清除失敗，請稍後重試" }
            finally { clearing = false }
        }
    }, enabled = !exportBusy && !clearing) { Text("清除既有 Log") }
    if (exportBusy || clearing) Text("處理中…")
    if (notice.isNotBlank()) Text(notice)
    if (clearNotice.isNotBlank()) Text(clearNotice)
    Text("測試方式：開啟記錄 → 重現問題 → 停止工作階段 → 匯出／分享 Log，附上發生問題的操作步驟。", style = MaterialTheme.typography.bodySmall)
}
