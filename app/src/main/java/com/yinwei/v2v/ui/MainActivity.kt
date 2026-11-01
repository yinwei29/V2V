package com.yinwei.v2v.ui

import com.yinwei.v2v.history.*
import com.yinwei.v2v.languages.*
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalContext
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
    private val context get() = this
    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(UiLocale.wrap(base))
    private fun openVoiceSettings(install: Boolean) {
        val intents = if (install) listOf(Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent("com.android.settings.TTS_SETTINGS")) else listOf(Intent("com.android.settings.TTS_SETTINGS"))
        for (intent in intents) if (runCatching { startActivity(intent); true }.getOrDefault(false)) return
        android.widget.Toast.makeText(this, uiText(context.uiText("找不到語音安裝程式，請在系統設定搜尋「文字轉語音」。")), android.widget.Toast.LENGTH_LONG).show()
    }

    private val repository: SessionRepository by inject()
    private val history: HistoryStore by inject()
    private var historyNotice by mutableStateOf("")
    private var historyBusy by mutableStateOf(false)
    private val historyExportState by lazy { androidx.lifecycle.ViewModelProvider(this)[HistoryExportState::class.java] }
    private var pendingHistoryExport: String?
        get() = historyExportState.pending
        set(value) { historyExportState.pending = value }
    private fun completeHistoryExport(uri: Uri?) {
        val snapshot = pendingHistoryExport; pendingHistoryExport = null
        if (uri != null && snapshot == null) { historyNotice = uiText("匯出已中斷，請重新匯出"); return }
        if (uri != null && snapshot != null) lifecycleScope.launch {
            historyBusy = true
            try {
                withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(snapshot) } ?: error("Cannot export") }
                historyNotice = context.uiText("檔案已匯出")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { historyNotice = context.uiText("匯出失敗，請重新選擇位置") }
            finally { historyBusy = false }
        }
    }
    private val exportHistory = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain"), ::completeHistoryExport)
    private val exportSrt = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip"), ::completeHistoryExport)
    private fun prepareHistoryExport(archive: List<HistoryEntry>, sessionId: String? = null, bilingual: Boolean = false) {
        if (historyBusy) return
        historyBusy = true
        lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.Default) {
                    if (sessionId == null) HistoryText.export(archive) else HistorySrt.export(archive, sessionId, bilingual)
                }
                if (snapshot.isBlank()) historyNotice = uiText("沒有可匯出的有效字幕")
                else {
                    pendingHistoryExport = snapshot
                    if (sessionId == null) exportHistory.launch("V2V-history-${System.currentTimeMillis()}.txt")
                    else exportSrt.launch("V2V-subtitles-${System.currentTimeMillis()}.srt")
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { pendingHistoryExport = null; historyNotice = uiText("匯出失敗，請重新選擇位置") }
            finally { historyBusy = false }
        }
    }
    private fun chooseHistoryExport(archive: List<com.yinwei.v2v.history.HistoryEntry>) {
        android.app.AlertDialog.Builder(this).setTitle(uiText("匯出翻譯紀錄"))
            .setItems(arrayOf(uiText("文字紀錄（TXT）"), uiText("字幕（SRT）"))) { _, format ->
                if (format == 0) {
                    prepareHistoryExport(archive)
                } else {
                    val sessions = archive.groupBy { it.sessionId }.entries.sortedByDescending { row -> row.value.maxOf { it.createdAt } }
                    val labels = sessions.mapIndexed { index, row ->
                        "${index + 1}. ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(row.value.minOf { it.createdAt }))} · ${row.value.size}"
                    }.toTypedArray()
                    android.app.AlertDialog.Builder(this).setTitle(uiText("選擇工作階段（各自獨立時間軸）"))
                        .setItems(labels) { _, index ->
                            android.app.AlertDialog.Builder(this).setTitle(uiText("字幕內容"))
                                .setItems(arrayOf(uiText("只匯出譯文"),uiText("原文與譯文"))) { _, mode ->
                                    prepareHistoryExport(sessions[index].value, sessions[index].key, mode == 1)
                                }.setNegativeButton(uiText("取消"),null).show()
                        }.setNegativeButton(uiText("取消"),null).show()
                }
            }.setNegativeButton(uiText("取消"),null).show()
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
                logNotice = context.uiText("已匯出技術 Log；可附在聊天中協助開發")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { logNotice = context.uiText("Log 匯出失敗，請重新選擇儲存位置") }
            finally { logBusy = false }
        }
    }
    private var fileUri by mutableStateOf<String?>(null)
    private var pendingExternal = false
    private var pendingCloud = false
    private var pendingLanguage = "en"
    private var pendingTargetLanguage = "zh-TW"
    private var pendingProvider = CloudProvider.OPENAI
    private var pendingGeminiModel = GeminiModel.DEFAULT
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            fileUri = uri.toString()
        }
    }
    private val captureConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        diagnostics.journal.record(DiagnosticEvent.PERMISSION, metrics = mapOf(Metric.GRANTED to if (result.resultCode == Activity.RESULT_OK) 1 else 0), tags = mapOf(Tag.PERMISSION to PermissionKind.PROJECTION))
        if (result.resultCode == Activity.RESULT_OK && result.data != null) startSession(true, result.resultCode, result.data)
        else repository.update { it.copy(state = SessionState.IDLE, message = context.uiText("已取消擷取授權；沒有啟動錄音")) }
    }
    private val recordPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        diagnostics.journal.record(DiagnosticEvent.PERMISSION, metrics = mapOf(Metric.GRANTED to if (granted) 1 else 0), tags = mapOf(Tag.PERMISSION to PermissionKind.AUDIO))
        if (granted) askProjection() else repository.update {
            it.copy(state = SessionState.IDLE, message = context.uiText("未授予音訊擷取權限；沒有啟動，也不會改用麥克風")) }
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
        pendingTargetLanguage = savedInstanceState?.getString("targetLanguage") ?: "zh-TW"
        pendingProvider = CloudProvider.parse(savedInstanceState?.getString("provider")) ?: CloudProvider.OPENAI
        pendingGeminiModel = GeminiModel.parse(savedInstanceState?.getString("geminiModel")) ?: GeminiModel.DEFAULT
        // Screenshots are enabled at the user's request; credentials remain masked in the form.
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF086B63), secondary = Color(0xFF486C7A),
                background = Color(0xFFF4F7F8), surface = Color.White,
                onSurface = Color(0xFF172F37), surfaceVariant = Color(0xFFE7F0F1), secondaryContainer = Color(0xFFDFF2EE))) {
                val state by repository.state.collectAsStateWithLifecycle()
                val mix by settings.settings.collectAsStateWithLifecycle(MixSettings())
                val presentation by settings.presentation.collectAsStateWithLifecycle(com.yinwei.v2v.settings.PresentationSettings())
                val storedProvider by settings.cloudProvider.collectAsStateWithLifecycle<CloudProvider?>(null)
                val selectedProvider = storedProvider ?: return@MaterialTheme
                val storedConsent by settings.cloudConsent.collectAsStateWithLifecycle<Boolean?>(null)
                val cloudConsent = storedConsent ?: return@MaterialTheme
                val storedModel by settings.geminiModel.collectAsStateWithLifecycle<GeminiModel?>(null)
                val geminiModel = storedModel ?: return@MaterialTheme
                val storedLanguages by settings.languages.collectAsStateWithLifecycle<LanguagePair?>(null)
                val languages = storedLanguages ?: return@MaterialTheme
                val archive by history.entries.collectAsStateWithLifecycle()
                val historyError by history.error.collectAsStateWithLifecycle()
                val logEnabled by settings.diagnosticsEnabled.collectAsStateWithLifecycle(true)
                AppScreen(state, mix, presentation, { changed -> lifecycleScope.launch { settings.savePresentation(changed) } }, fileUri != null, keys, { filePicker.launch(arrayOf("audio/*", "application/octet-stream")) },
                    { source, cloud, language, targetLanguage, provider, model ->
                        pendingExternal = source == Source.SYSTEM_PLAYBACK
                        pendingCloud = cloud && source != Source.TEST_FILE
                        pendingLanguage = language
                        pendingTargetLanguage = targetLanguage
                        pendingProvider = provider
                        pendingGeminiModel = model
                        if (source == Source.TEST_FILE) fileUri = null
                        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else continueStart()
                    }, { command ->
                        when (command) {
                            "overlayPermission" -> startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                            "soundSettings" -> startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS))
                            "ttsSettings" -> openVoiceSettings(false)
                            "ttsInstall" -> openVoiceSettings(true)
                            else -> if (command.startsWith("uiLocale:")) {
                                UiLocale.set(this, command.substringAfter(':')); recreate()
                            } else startService(Intent(this, TranslationForegroundService::class.java).setAction(command))
                        }
                    },
                    { changed -> lifecycleScope.launch { settings.save(changed) } }, diagnostics, logEnabled,
                    { enabled -> lifecycleScope.launch { settings.setDiagnosticsEnabled(enabled) } }, logNotice, logBusy,
                    { exportLog.launch("V2V-technical-${System.currentTimeMillis()}.jsonl") }, ::shareLog,
                    archive, historyError, historyNotice, historyBusy,
                    { chooseHistoryExport(archive) },
                    { lifecycleScope.launch {
                        historyBusy = true
                        try { withContext(Dispatchers.IO) {
                            val current = repository.state.value
                            history.clear(current.id, current.segments.filter { it.translatedText.isNotBlank() }.maxOfOrNull { it.id } ?: -1)
                        }; historyNotice = context.uiText("已清除翻譯紀錄") }
                        catch (_: Exception) { historyNotice = context.uiText("清除失敗，請稍後重試") }
                        finally { historyBusy = false }
                    } }, selectedProvider, { provider -> lifecycleScope.launch { settings.saveCloudProvider(provider) } },
                    cloudConsent, { enabled -> lifecycleScope.launch { settings.saveCloudConsent(enabled) } },
                    geminiModel, { model -> lifecycleScope.launch { settings.saveGeminiModel(model) } },
                    languages, { pair -> lifecycleScope.launch { settings.saveLanguages(pair) } })
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
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), context.uiText("分享 V2V 技術 Log")))
                logNotice = context.uiText("已開啟系統分享；請自行選擇接收對象")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { logNotice = context.uiText("無法分享，請改用匯出檔案") }
            finally { logBusy = false }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("file", fileUri); outState.putBoolean("external", pendingExternal)
        outState.putString("targetLanguage", pendingTargetLanguage)
        outState.putString("geminiModel", pendingGeminiModel.name)
        outState.putString("provider", pendingProvider.name)
        outState.putBoolean("cloud", pendingCloud); outState.putString("language", pendingLanguage)
        super.onSaveInstanceState(outState)
    }
    private fun continueStart() {
        if (!pendingExternal) { startSession(false); return }
        repository.update { it.copy(state = SessionState.REQUESTING_PERMISSION, source = Source.SYSTEM_PLAYBACK,
            capabilities = SourceCapabilities(), message = context.uiText("需要本次音訊／螢幕擷取授權；不啟用麥克風")) }
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
                .putExtra("targetLanguage", pendingTargetLanguage).putExtra("geminiModel", pendingGeminiModel.name).putExtra("provider", pendingProvider.name).putExtra("cloudConsent", pendingCloud).putExtra("language", pendingLanguage))
        } catch (_: Exception) { repository.update { it.copy(state = SessionState.ERROR, message = context.uiText("無法啟動前景服務，請保持主畫面可見後重試")) } }
    }
}

@Composable
internal fun KeyPanel(provider: CloudProvider, model: GeminiModel, keys: ApiKeyStore, configured: Boolean, enabled: Boolean, journal: DiagnosticJournal, refresh: () -> Unit) {
    val context = LocalContext.current
    var value by remember(provider) { mutableStateOf("") } // Deliberately NOT rememberSaveable.
    var busy by remember(provider) { mutableStateOf(false) }
    var message by remember(provider) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Text(context.uiText("%s 引擎與 Key").format(provider.label), style = MaterialTheme.typography.titleLarge)
    Text(if (configured) context.uiText("已儲存加密 Key；不顯示原值") else context.uiText("尚未設定 Key；不會有真實雲端字幕"))
    OutlinedTextField(value = value, onValueChange = { value = it.take(1024) }, label = { Text(context.uiText("API Key（僅存此裝置）")) },
        visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            busy = true
            val pending = value; value = ""
            scope.launch {
                try { withContext(Dispatchers.IO) { keys.save(pending, provider) }; refresh(); message = context.uiText("已用 Keystore 加密儲存") }
                catch (_: Exception) { message = context.uiText("無法儲存，請確認裝置 Keystore 並重新輸入") }
                finally { busy = false }
            }
        }, enabled = enabled && !busy && value.isNotBlank()) { Text(context.uiText("儲存 / 更新")) }
        OutlinedButton(onClick = {
            busy = true; value = ""
            scope.launch {
                try { withContext(Dispatchers.IO) { keys.remove(provider) }; refresh(); message = context.uiText("已移除 Key") }
                catch (_: Exception) { message = context.uiText("無法移除 Key，請稍後重試") }
                finally { busy = false }
            }
        }, enabled = enabled && !busy && configured) { Text(context.uiText("移除")) }
    }
    OutlinedButton(onClick = {
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { provider.create(checkNotNull(keys.read(provider)), journal, model).healthCheck() }
                message = context.uiText("Key 驗證及模型查詢成功；仍需音訊端到端測試，不代表扣款／轉寫已驗收")
            } catch (e: CancellationException) { throw e }
            catch (e: ProviderFailure) { message = e.message.orEmpty() }
            catch (_: Exception) { message = context.uiText("連線失敗或 Key 已失效，請重新設定") }
            finally { busy = false }
        }
    }, enabled = enabled && !busy && configured) { Text(if (busy) context.uiText("處理中…") else context.uiText("測試連線（僅傳 Key，不傳音訊）")) }
    if (message.isNotBlank()) Text(message)
    Text(if (provider == CloudProvider.OPENAI) context.uiText("OpenAI · whisper-1 辨識 / gpt-4o-mini 翻譯。") else context.uiText("Google Gemini · %s，請使用 Google AI Studio 的 API Key。").format(model.apiId), style = MaterialTheme.typography.bodySmall)
    Text(context.uiText("分段完成後更新字幕，最長 10 秒。播報沿用手機離線語音。供應商可能收費；沒有額度時請在供應商後台調整專案／帳務，不需設定 CircleCI。"), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Choice(title: String, subtitle: String, selected: Boolean, enabled: Boolean, choose: () -> Unit) {
    val context = LocalContext.current
    OutlinedCard(onClick = choose, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            RadioButton(selected = selected, onClick = choose, enabled = enabled)
            Column(Modifier.padding(start = 8.dp, top = 6.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
@Composable
internal fun Detail(label: String, value: String) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary); Text(value) }
}

@Composable
internal fun DiagnosticPanel(diagnostics: Diagnostics, enabled: Boolean, setEnabled: (Boolean) -> Unit,
    notice: String, exportBusy: Boolean, export: () -> Unit, share: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var clearing by remember { mutableStateOf(false) }
    var clearNotice by remember { mutableStateOf("") }
    Text(context.uiText("測試 Log"), style = MaterialTheme.typography.titleLarge)
    Row { Switch(checked = enabled, onCheckedChange = setEnabled); Text(if (enabled) context.uiText("技術事件記錄中") else context.uiText("技術事件記錄已關閉"), Modifier.padding(start = 12.dp, top = 12.dp)) }
    Text(context.uiText("記錄啟停、權限、每秒電平、音訊路由、provider 狀態與錯誤類型。不含錄音、字幕、API Key、檔案路徑或裝置識別碼。含型號／Android 版本，僅存本機，最多 3 × 512KiB。"), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = export, enabled = !exportBusy && !clearing) { Text(context.uiText("匯出 Log")) }
        OutlinedButton(onClick = share, enabled = !exportBusy && !clearing) { Text(context.uiText("分享 Log")) }
    }
    OutlinedButton(onClick = {
        clearing = true
        scope.launch {
            try { withContext(Dispatchers.IO) { diagnostics.journal.clear() }; clearNotice = context.uiText("已清除既有 Log；若記錄開啟，新的測試事件會繼續寫入") }
            catch (_: Exception) { clearNotice = context.uiText("清除失敗，請稍後重試") }
            finally { clearing = false }
        }
    }, enabled = !exportBusy && !clearing) { Text(context.uiText("清除既有 Log")) }
    if (exportBusy || clearing) Text(context.uiText("處理中…"))
    if (notice.isNotBlank()) Text(notice)
    if (clearNotice.isNotBlank()) Text(clearNotice)
    Text(context.uiText("測試方式：開啟記錄 → 重現問題 → 停止工作階段 → 匯出／分享 Log，附上發生問題的操作步驟。"), style = MaterialTheme.typography.bodySmall)
}
