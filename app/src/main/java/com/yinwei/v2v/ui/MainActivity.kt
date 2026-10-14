package com.yinwei.v2v.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.yinwei.v2v.audio.*
import com.yinwei.v2v.session.*
import com.yinwei.v2v.settings.SettingsStore
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val repository: SessionRepository by inject()
    private val settings: SettingsStore by inject()
    private val captureConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startSession(true, result.resultCode, result.data)
        } else repository.update { it.copy(state = SessionState.IDLE, message = "已取消擷取授權；沒有啟動錄音") }
    }
    private val recordPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) askProjection() else repository.update {
            it.copy(state = SessionState.IDLE, message = "未授予音訊擷取權限；未啟動，也不會改用麥克風") }
    }
    private var pendingExternal = false
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        continueStart(pendingExternal) // Denial does not suppress the mandatory foreground service.
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF086B63), secondary = Color(0xFF486C7A),
                background = Color(0xFFF4F7F8), surface = Color.White,
            )) {
                val state by repository.state.collectAsStateWithLifecycle()
                val mix by settings.settings.collectAsStateWithLifecycle(MixSettings())
                AppScreen(state, mix, ::requestStart, {
                    startService(Intent(this, TranslationForegroundService::class.java).setAction(TranslationForegroundService.STOP))
                }, { changed -> lifecycleScope.launch { settings.save(changed) } }, {
                    startService(Intent(this, TranslationForegroundService::class.java).setAction(TranslationForegroundService.RESUME))
                })
            }
        }
    }
    private fun requestStart(external: Boolean) {
        pendingExternal = external
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else continueStart(external)
    }
    private fun continueStart(external: Boolean) {
        if (!external) { startSession(false); return }
        repository.update { it.copy(state = SessionState.REQUESTING_PERMISSION, source = Source.SYSTEM_PLAYBACK,
            capabilities = SourceCapabilities(), message = "需要本次音訊／螢幕擷取授權；不啟用麥克風") }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) askProjection()
        else recordPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    private fun askProjection() {
        captureConsent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }
    private fun startSession(external: Boolean, result: Int = Activity.RESULT_CANCELED, consent: Intent? = null) {
        try {
            startForegroundService(Intent(this, TranslationForegroundService::class.java)
                .setAction(TranslationForegroundService.START).putExtra("external", external)
                .putExtra("resultCode", result).putExtra("consent", consent))
        } catch (_: Exception) {
            repository.update { it.copy(state = SessionState.ERROR, message = "無法啟動前景服務，請保持主畫面可見後重試") }
        }
    }
}

@Composable
private fun AppScreen(
    state: SessionSnapshot, mix: MixSettings, start: (Boolean) -> Unit, stop: () -> Unit,
    save: (MixSettings) -> Unit, resume: () -> Unit,
) {
    var external by rememberSaveableSource()
    var tab by remember { mutableIntStateOf(0) }
    val active = state.state in setOf(SessionState.STARTING, SessionState.RUNNING, SessionState.NO_SIGNAL, SessionState.PAUSED, SessionState.STOPPING, SessionState.REQUESTING_PERMISSION)
    val full = if (active) state.capabilities.ui == UiCapability.FULL_MIXER_CONTROL else !external
    Scaffold(bottomBar = {
        NavigationBar {
            listOf("音訊工作台", "混音控制", "診斷與說明").forEachIndexed { i, label ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(listOf("◉", "≋", "ⓘ")[i], fontSize = 24.sp) }, label = { Text(label) })
            }
        }
    }) { inset ->
        Column(Modifier.padding(inset).fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("V2V", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("即時音訊翻譯 · 工程驗證版", color = MaterialTheme.colorScheme.secondary)
            Surface(color = Color(0xFFE1EFEA), shape = MaterialTheme.shapes.medium) {
                Text("M0 / M1 · 音訊来源與混音 POC\n本版尚無語音辨識、翻譯或真實 TTS。", Modifier.padding(16.dp))
            }
            when (tab) {
                0 -> {
                    Text("選擇音訊來源", style = MaterialTheme.typography.titleLarge)
                    Choice("自有測試音檔", "440Hz 原聲 / 660Hz 模擬譯音 · 可完整控制", !external, !active) { external = false }
                    Choice("其他 APP 播放音訊", "需系統授權 · 原聲控制不可用 · 支援待真機驗證", external, !active) { external = true }
                    Text("英文 / 印尼文 → 繁體中文", fontWeight = FontWeight.SemiBold)
                    Text("ASR / MT 未配置；沒有雲端傳送，也不產生虛假字幕。", color = MaterialTheme.colorScheme.secondary)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(state.state.name, style = MaterialTheme.typography.titleLarge)
                            LinearProgressIndicator(progress = { (state.level * 3).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text("PCM RMS：%.4f  ·  %.1f 秒".format(state.level, state.elapsedMs / 1000.0))
                            Text(state.message)
                        }
                    }
                    Button(onClick = { if (active) stop() else start(external) }, enabled = state.state != SessionState.STOPPING,
                        modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (active) "停止工作階段" else "開始音訊驗證") }
                    if (state.outputPaused) OutlinedButton(onClick = resume) { Text("確認路由並恢復本 APP 音訊") }
                    Text("離開畫面仍由前景服務執行；通知可停止。小圈圈與浮動字幕屬 M4，尚未實作。", style = MaterialTheme.typography.bodySmall)
                }
                1 -> {
                    Text("音訊混音器", style = MaterialTheme.typography.titleLarge)
                    Text(if (full) "自有來源：單一 AudioTrack 輸出 stereo PCM。譯音為測試 fixture。"
                        else "外部來源：只擷取，不重播原聲副本。來源 APP 可能仍有原聲；本版無 TTS 輸出。")
                    MixMode.entries.forEach { mode -> Choice(when (mode) {
                        MixMode.SPLIT -> "左譯音 / 右原聲"
                        MixMode.TRANSLATION_ONLY -> "只聽模擬譯音"
                        MixMode.MIX -> "雙聲混音"
                    }, "50ms ramp / crossfade", mix.mode == mode, full) { save(mix.copy(mode = mode)) } }
                    Text("原聲 ${(if (mix.mode == MixMode.TRANSLATION_ONLY) 0 else (mix.originalGain * 100).toInt())}%")
                    Slider(value = if (mix.mode == MixMode.TRANSLATION_ONLY) 0f else mix.originalGain,
                        onValueChange = { save(mix.copy(originalGain = it)) }, enabled = full && mix.mode != MixMode.TRANSLATION_ONLY)
                    if (mix.mode == MixMode.TRANSLATION_ONLY) Text("此模式原聲鎖定為 0；切回 MIX / SPLIT 會保留先前增益。", style = MaterialTheme.typography.bodySmall)
                    Text("模擬譯音 ${(mix.translationGain * 100).toInt()}%")
                    Slider(value = mix.translationGain, onValueChange = { save(mix.copy(translationGain = it)) }, enabled = full)
                    Row { Checkbox(checked = mix.swap, onCheckedChange = { save(mix.copy(swap = it)) }, enabled = full && mix.mode == MixMode.SPLIT); Text("交換左右", Modifier.padding(top = 12.dp)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { save(mix.copy(mode = MixMode.MIX, originalGain = 0.2f, translationGain = 1f)) }, enabled = full) { Text("背景原聲") }
                        OutlinedButton(onClick = { save(mix.copy(mode = MixMode.MIX, originalGain = 1f, translationGain = 1f)) }, enabled = full) { Text("雙聲") }
                    }
                    Text("立體聲 PCM 不保證藍牙或耳機實際左右隔離。100/100 經 soft-knee limiter，不直接相加截斷。", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    Text("來源診斷", style = MaterialTheme.typography.titleLarge)
                    Detail("來源", state.source.name)
                    Detail("ASR / MT / TTS", "${state.asrState} / ${state.mtState} / ${state.ttsState}")
                    Detail("輸入", state.sourceStatus.name)
                    Detail("有效 PCM", if (state.capabilities.inputVerified) "曾量測到非靜音；未驗證內容" else "尚未驗證")
                    Detail("原聲控制", state.capabilities.originalControl.name)
                    Detail("控制能力", state.capabilities.ui.name)
                    Detail("驗證證據", state.capabilities.verificationEvidence)
                    Detail("輸入片段", state.frames.toString())
                    Detail("ASR 量測樣本", "${state.asrSamples}（僅量測，未辨識）")
                    Detail("實際輸出路由", state.route)
                    Detail("AudioTrack underruns", state.underruns.toString())
                    Text(state.capabilities.reason)
                    HorizontalDivider()
                    Text("YouTube / Zoom / Meet：NOT_TESTED_ON_DEVICE；完整來源整合 BLOCKED。一般播放擷取不等於通話擷取。")
                    Text("權限拒絕或來源靜音不會改用麥克風。不修改系統媒體音量。不會在程序重啟或開機後自動錄音。")
                    Text("本版不存錄音、逐字稿或 API Key；只有音量與模式設定會保存在裝置。離線翻譯尚不可用。")
                    Text("下一階段 M2：真實 ASR / MT；M3：PCM TTS；M4：小圈圈與浮動字幕。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
@Composable
private fun rememberSaveableSource() = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
@Composable
private fun Choice(title: String, subtitle: String, selected: Boolean, enabled: Boolean, choose: () -> Unit) {
    OutlinedCard(onClick = choose, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            RadioButton(selected = selected, onClick = choose, enabled = enabled)
            Column(Modifier.padding(start = 8.dp, top = 6.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
@Composable
private fun Detail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(value)
    }
}
