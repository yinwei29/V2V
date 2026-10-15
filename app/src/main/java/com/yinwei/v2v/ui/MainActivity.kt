package com.yinwei.v2v.ui

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
                background = Color(0xFFF4F7F8), surface = Color.White)) {
                val state by repository.state.collectAsStateWithLifecycle()
                val mix by settings.settings.collectAsStateWithLifecycle(MixSettings())
                val presentation by settings.presentation.collectAsStateWithLifecycle(com.yinwei.v2v.settings.PresentationSettings())
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
                            "ttsSettings" -> runCatching { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                            else -> startService(Intent(this, TranslationForegroundService::class.java).setAction(command))
                        }
                    },
                    { changed -> lifecycleScope.launch { settings.save(changed) } }, diagnostics, logEnabled,
                    { enabled -> lifecycleScope.launch { settings.setDiagnosticsEnabled(enabled) } }, logNotice, logBusy,
                    { exportLog.launch("V2V-technical-${System.currentTimeMillis()}.jsonl") }, ::shareLog)
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
private fun AppScreen(state: SessionSnapshot, mix: MixSettings, presentation: com.yinwei.v2v.settings.PresentationSettings, savePresentation: (com.yinwei.v2v.settings.PresentationSettings) -> Unit, hasFile: Boolean, keys: ApiKeyStore,
    pickFile: () -> Unit, start: (Source, Boolean, String) -> Unit, command: (String) -> Unit, save: (MixSettings) -> Unit,
    diagnostics: Diagnostics, logEnabled: Boolean, setLogEnabled: (Boolean) -> Unit, logNotice: String, logBusy: Boolean,
    exportLog: () -> Unit, shareLog: () -> Unit) {
    var sourceName by rememberSaveable { mutableStateOf(Source.SYSTEM_PLAYBACK.name) }
    val active = state.state in setOf(SessionState.STARTING, SessionState.RUNNING, SessionState.NO_SIGNAL, SessionState.PAUSED, SessionState.STOPPING, SessionState.REQUESTING_PERMISSION)
    val source = if (active) state.source else Source.valueOf(sourceName)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var language by rememberSaveable { mutableStateOf("en") }
    var consent by rememberSaveable { mutableStateOf(false) }
    var configured by remember { mutableStateOf(false) }
    var keyRefresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(keyRefresh) { configured = withContext(Dispatchers.IO) { keys.configured() } }
    val full = if (active) state.capabilities.ui == UiCapability.FULL_MIXER_CONTROL else source != Source.SYSTEM_PLAYBACK
    Scaffold(topBar = {
        if (tab == 0) LatestCaption(state, diagnostics)
    }, bottomBar = {
        NavigationBar {
            listOf("字幕工作台", "混音控制", "引擎與診斷").forEachIndexed { i, label ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(listOf("◉", "≋", "ⓘ")[i], fontSize = 24.sp) }, label = { Text(label) })
            }
        }
    }) { inset ->
        Column(Modifier.padding(inset).fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (tab != 0) Text("V2V", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            if (tab != 0) Text("即時音訊翻譯 · M3 / M4 工程預覽", color = MaterialTheme.colorScheme.secondary)
            Surface(color = Color(0xFFE1EFEA), shape = MaterialTheme.shapes.medium) {
                Text("分段辨識 → 繁中字幕\n離線中文播報＋浮動字幕預覽；需真機驗收。", Modifier.padding(16.dp))
            }
            when (tab) {
                0 -> {
                    Text("選擇音訊來源", style = MaterialTheme.typography.titleLarge)
                    Choice("自有測試音檔", "440Hz 原聲 / 660Hz 模擬譯音，無雲端傳送", source == Source.TEST_FILE, !active) { sourceName = Source.TEST_FILE.name }
                    Choice("授權 WAV 音檔", "48kHz · mono · PCM16；不支援其他格式", source == Source.OWNED_FILE, !active) { sourceName = Source.OWNED_FILE.name }
                    if (source == Source.OWNED_FILE) OutlinedButton(onClick = pickFile, enabled = !active) { Text(if (hasFile) "已選取音檔 · 重新選擇" else "選擇使用者授權的 WAV") }
                    Choice("其他 APP 播放音訊", "系統授權擷取 · 原聲不可控 · 第三方來源待真機驗證", source == Source.SYSTEM_PLAYBACK, !active) { sourceName = Source.SYSTEM_PLAYBACK.name }
                    if (source != Source.TEST_FILE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("en" to "英文", "id" to "印尼文", "auto" to "自動").forEach { (code, label) ->
                                FilterChip(selected = (if (active) state.sourceLanguage else language) == code, onClick = { language = code }, enabled = !active, label = { Text(label) })
                            }
                        }
                        Text("目標：繁體中文（台灣）；whisper-1 只回傳 final，沒有串流 partial。")
                        Row {
                            Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !active)
                            Text("我有權處理此音訊，並同意將片段音訊與辨識文字傳送 OpenAI（可能收費）。關閉時僅播放／診斷。", Modifier.weight(1f))
                        }
                        if (consent && !configured) Text("請先到「引擎與診斷」儲存自己的 API Key。", color = MaterialTheme.colorScheme.error)
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.state.name, style = MaterialTheme.typography.titleLarge)
                            LinearProgressIndicator(progress = { (state.level * 3).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text("RMS %.4f · %.1f 秒".format(state.level, state.elapsedMs / 1000.0))
                            Text(state.message)
                            if (state.providerError.isNotBlank()) Text(state.providerError, color = MaterialTheme.colorScheme.error)
                            if (state.skippedSamples > 0) Text("過載：已跳過 %.1f 秒音訊，不會假造字幕。".format(state.skippedSamples / 48000.0), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Button(onClick = { if (active) command(TranslationForegroundService.STOP) else start(source, consent, language) },
                        enabled = state.state != SessionState.STOPPING && (active || ((source != Source.OWNED_FILE || hasFile) && (!consent || configured || source == Source.TEST_FILE))),
                        modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (active) "停止工作階段" else "開始") }
                    if (active && !state.outputPaused) OutlinedButton(onClick = { command(TranslationForegroundService.PAUSE) }) { Text("暫停本 APP 播報（字幕持續）") }
                    Text("播報：${state.ttsState} · 過載跳過 ${state.ttsDropped} 段 · 合成失敗 ${state.ttsFailures} 段")
                    Text("待播 ${state.ttsPending} 段 · 已合成音訊 %.1f 秒 · 本句語速 %.2f×".format(state.ttsBufferedMs / 1000.0, state.ttsRate))
                    if (state.ttsError.isNotBlank()) Text(state.ttsError, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { command("ttsSettings") }) { Text("Android 文字轉語音設定") }
                    Text("首次浮窗設定：開啟上層顯示權限 → 返回本 App → 開始後按顯示浮窗。")
                    OutlinedButton(onClick = { command("overlayPermission") }) { Text("設定浮動字幕權限") }
                    OutlinedButton(onClick = { command(TranslationForegroundService.SHOW_OVERLAY) }, enabled = active) { Text("顯示浮動字幕／小圈圈") }
                    if (state.overlayError.isNotBlank()) Text(state.overlayError, color = MaterialTheme.colorScheme.error)
                    if (state.outputPaused) OutlinedButton(onClick = { command(TranslationForegroundService.RESUME) }) { Text("確認路由並恢復本 APP 音訊") }
                    Text("字幕僅保留本次最近 200 段於記憶體，無自動歷史。停止／換語言後不接受舊 session 結果。", style = MaterialTheme.typography.bodySmall)
                    if (state.segments.isEmpty()) Text("尚無辨識結果；沒有使用 mock 字幕。", color = MaterialTheme.colorScheme.secondary)
                    state.segments.takeLast(30).reversed().forEach { segment ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("%.1f–%.1f 秒".format(segment.startMs / 1000.0, segment.endMs / 1000.0), style = MaterialTheme.typography.labelMedium)
                                Text(segment.originalText)
                                Text(segment.translatedText.ifBlank { "等待繁中翻譯…" }, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                        }
                    }
                }
                1 -> {
                    Row { Switch(checked = presentation.speechEnabled, onCheckedChange = { savePresentation(presentation.copy(speechEnabled = it)) }); Text("播報翻譯", Modifier.padding(12.dp)) }
                    Row { Switch(checked = presentation.autoCatchUp, onCheckedChange = { savePresentation(presentation.copy(autoCatchUp = it)) }); Text("自動追趕（最多 2×）", Modifier.padding(12.dp)) }
                    Text("長譯文會分短句接續播報；追趕不會摘要或改寫字幕。字幕先顯示，播報仍需朗讀時間。", style = MaterialTheme.typography.bodySmall)
                    Text("基準語速 %.2f×".format(presentation.speechRate))
                    Slider(value = presentation.speechRate, onValueChange = { savePresentation(presentation.copy(speechRate = it)) }, valueRange = 0.75f..2f)
                    Text("浮動字幕字體 ${presentation.captionSize.toInt()}sp")
                    Slider(value = presentation.captionSize, onValueChange = { savePresentation(presentation.copy(captionSize = it)) }, valueRange = 14f..30f)
                    Text("字幕背景 ${(presentation.backgroundOpacity * 100).toInt()}%（文字保持不透明）")
                    Slider(value = presentation.backgroundOpacity, onValueChange = { savePresentation(presentation.copy(backgroundOpacity = it)) }, valueRange = 0f..1f)
                    Text("字幕最多 ${presentation.captionLines} 行")
                    Slider(value = presentation.captionLines.toFloat(), onValueChange = { savePresentation(presentation.copy(captionLines = it.toInt())) }, valueRange = 2f..6f, steps = 3)
                    Text("音訊混音器", style = MaterialTheme.typography.titleLarge)
                    Text(if (full) "可控原聲進單一 stereo AudioTrack。只有測試音檔有 660Hz 譯音 fixture；雲端翻譯使用 Android 離線語音引擎。"
                        else "其他 App 的原聲仍由該 App 直接播放，V2V 無法靜音或重新分配它的左右聲道。下方只控制 V2V 譯音；完整混音／只聽翻譯請改用由 V2V 播放的授權 WAV。")
                    if (full) {
                        MixMode.entries.forEach { mode -> Choice(when (mode) {
                            MixMode.SPLIT -> "左譯音 / 右原聲"
                            MixMode.TRANSLATION_ONLY -> "只聽譯音"
                            MixMode.MIX -> "雙聲混音"
                        }, "50ms ramp / crossfade", mix.mode == mode, full) { save(mix.copy(mode = mode)) } }
                        Text("原聲 ${(if (mix.mode == MixMode.TRANSLATION_ONLY) 0 else (mix.originalGain * 100).toInt())}%")
                        Slider(value = if (mix.mode == MixMode.TRANSLATION_ONLY) 0f else mix.originalGain,
                            onValueChange = { save(mix.copy(originalGain = it)) }, enabled = full && mix.mode != MixMode.TRANSLATION_ONLY)
                        if (mix.mode == MixMode.TRANSLATION_ONLY) Text("此模式原聲鎖定 0；切回 MIX / SPLIT 保留先前增益。辨識輸入不受音量影響。")
                    } else {
                        Text("譯音輸出聲道（來源 App 原聲不變）")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TranslationChannel.entries.forEach { channel ->
                                FilterChip(selected = mix.translationChannel == channel, onClick = { save(mix.copy(translationChannel = channel)) },
                                    label = { Text(when (channel) { TranslationChannel.BOTH -> "雙耳"; TranslationChannel.LEFT -> "左耳"; TranslationChannel.RIGHT -> "右耳" }) })
                            }
                        }
                    }
                    Text("譯音 ${(mix.translationGain * 100).toInt()}%")
                    Slider(value = mix.translationGain, onValueChange = { save(mix.copy(translationGain = it)) }, valueRange = 0f..2f)
                    Text("譯音增益可達 200%；超過 100% 會限幅。其他 App 原聲無法由 V2V 單獨調低；可手動降低手機媒體音量，再補強譯音。", style = MaterialTheme.typography.bodySmall)
                    if (full) {
                        Row { Checkbox(checked = mix.swap, onCheckedChange = { save(mix.copy(swap = it)) }, enabled = full && mix.mode == MixMode.SPLIT); Text("交換左右", Modifier.padding(top = 12.dp)) }
                        OutlinedButton(onClick = { save(mix.copy(mode = MixMode.MIX, originalGain = 0.2f, translationGain = 1f)) }, enabled = full) { Text("背景原聲 20 / 100") }
                        OutlinedButton(onClick = { save(mix.copy(mode = MixMode.MIX, originalGain = 1f, translationGain = 1f)) }, enabled = full) { Text("雙聲 100 / 100（limiter）") }
                    }
                    Text("軟體 stereo PCM 不保證 Bluetooth/HFP 的實際左右隔離。", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    DiagnosticPanel(diagnostics, logEnabled, setLogEnabled, logNotice, logBusy, exportLog, shareLog)
                    HorizontalDivider()
                    KeyPanel(keys, configured, !active, diagnostics.journal) { keyRefresh++ }
                    HorizontalDivider()
                    Detail("ASR / MT / TTS", "${state.asrState} / ${state.mtState} / ${state.ttsState}")
                    Detail("來源與有效訊號", "${state.source} · ${state.sourceStatus} · 曾驗證=${state.capabilities.inputVerified}")
                    Detail("原聲控制", state.capabilities.originalControl.name)
                    Detail("驗證證據", state.capabilities.verificationEvidence)
                    Detail("PCM / ASR 量測樣本", "${state.frames} frames / ${state.asrSamples} samples")
                    Detail("實際輸出路由", state.route)
                    Detail("AudioTrack underruns", state.underruns.toString())
                    Detail("MT provider usage", state.providerTokens?.let { "$it tokens（僅累計收到 usage 的片段；不是帳單）" } ?: "尚未取得 usage；不當作零用量或正式帳單")
                    Text("YouTube / Zoom / Meet：NOT_TESTED_ON_DEVICE；完整外部來源整合 BLOCKED。一般播放擷取不等於通話擷取。")
                    Text("浮窗與離線 TTS 為工程預覽；離線辨識模型、字幕歷史與字幕匯出尚未交付。無離線回退；不會偷開麥克風。")
                }
            }
        }
    }
}

@Composable
private fun KeyPanel(keys: ApiKeyStore, configured: Boolean, enabled: Boolean, journal: DiagnosticJournal, refresh: () -> Unit) {
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
private fun Detail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary); Text(value) }
}

@Composable
private fun DiagnosticPanel(diagnostics: Diagnostics, enabled: Boolean, setEnabled: (Boolean) -> Unit,
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


@Composable
private fun LatestCaption(state: SessionSnapshot, diagnostics: Diagnostics) {
    val latest = state.segments.lastOrNull { it.translatedText.isNotBlank() }
    val original = state.segments.lastOrNull()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(state.id, latest?.id, latest?.revision, lifecycle) {
        if (latest != null && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
            diagnostics.journal.record(DiagnosticEvent.TRANSLATION_VISIBLE, state.id,
                metrics = mapOf(Metric.SEGMENT_ID to latest.id, Metric.REVISION to latest.revision,
                    Metric.CHARACTERS to latest.translatedText.length))
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("V2V · 即時繁中字幕", fontWeight = FontWeight.Bold)
            Text("完成 ${state.segments.count { it.translatedText.isNotBlank() }} 段 · Android 語音＋浮窗預覽",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(latest?.translatedText ?: when {
                state.providerError.isNotBlank() -> state.providerError
                state.mtState == StageState.RUNNING -> "已辨識原文，正在翻譯…"
                state.asrState == StageState.RUNNING -> "正在辨識音訊…"
                state.cloudEnabled -> "等待可辨識語音與翻譯結果…"
                else -> "尚無繁中字幕；請選語音來源並啟用雲端翻譯"
            }, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (latest != null) Text(latest.originalText, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall)
            else if (original != null) Text(original.originalText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("字幕固定顯示於此；完整紀錄可往下捲動。可授權浮窗，在其他 App 上方顯示。", style = MaterialTheme.typography.labelSmall)
        }
    }
}
