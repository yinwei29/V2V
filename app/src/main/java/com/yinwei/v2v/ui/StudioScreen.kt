package com.yinwei.v2v.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yinwei.v2v.languages.*
import com.yinwei.v2v.providers.CloudProvider
import com.yinwei.v2v.providers.GeminiModel
import com.yinwei.v2v.R
import com.yinwei.v2v.audio.*
import com.yinwei.v2v.diagnostics.*
import com.yinwei.v2v.history.HistoryEntry
import com.yinwei.v2v.security.ApiKeyStore
import com.yinwei.v2v.session.*
import com.yinwei.v2v.settings.PresentationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val Ink = Color(0xFF142F36)
private val Muted = Color(0xFF617780)
private val Teal = Color(0xFF087F79)
private val CardShape = RoundedCornerShape(24.dp)

@Composable internal fun Symbol(@DrawableRes icon: Int, description: String? = null, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(painterResource(icon), description, modifier.size(22.dp), tint)
}
@Composable private fun IconAction(@DrawableRes icon: Int, label: String, enabled: Boolean = true, action: () -> Unit) {
    IconButton(onClick = action, enabled = enabled) { Symbol(icon, label) }
}
@Composable private fun Panel(title: String, @DrawableRes icon: Int, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = CardShape, color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Symbol(icon, tint = Teal); Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
            content()
        }
    }
}
@Composable private fun Toggle(title: String, detail: String? = null, checked: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted) }
        Switch(checked, change)
    }
}
@Composable private fun Stepper(title: String, value: String, @DrawableRes icon: Int, canDecrease: Boolean, canIncrease: Boolean,
    decrease: () -> Unit, increase: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Symbol(icon, tint = Muted); Spacer(Modifier.width(10.dp)); Text(title, Modifier.weight(1f))
        IconAction(R.drawable.ic_minus, "降低$title", canDecrease, decrease)
        Text(value, fontWeight = FontWeight.SemiBold, color = Teal, modifier = Modifier.widthIn(min = 52.dp))
        IconAction(R.drawable.ic_plus, "提高$title", canIncrease, increase)
    }
}

@Composable internal fun AppScreen(state: SessionSnapshot, mix: MixSettings, presentation: PresentationSettings,
    savePresentation: (PresentationSettings) -> Unit, hasFile: Boolean, keys: ApiKeyStore, pickFile: () -> Unit,
    start: (Source, Boolean, String, String, CloudProvider, GeminiModel) -> Unit, command: (String) -> Unit, save: (MixSettings) -> Unit,
    diagnostics: Diagnostics, logEnabled: Boolean, setLogEnabled: (Boolean) -> Unit, logNotice: String, logBusy: Boolean,
    exportLog: () -> Unit, shareLog: () -> Unit, archive: List<HistoryEntry>, historyError: String, historyNotice: String,
    historyBusy: Boolean, exportHistory: () -> Unit, clearHistory: () -> Unit,
    selectedProvider: CloudProvider, selectProvider: (CloudProvider) -> Unit,
    cloudConsent: Boolean, saveCloudConsent: (Boolean) -> Unit,
    geminiModel: GeminiModel, selectGeminiModel: (GeminiModel) -> Unit,
    languages: LanguagePair, selectLanguages: (LanguagePair) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var clearHistoryDialog by remember { mutableStateOf(false) }
    var sourceName by rememberSaveable { mutableStateOf(Source.SYSTEM_PLAYBACK.name) }
    val language = languages.source
    val consent = cloudConsent
    var configured by remember(selectedProvider) { mutableStateOf(false) }
    var keysLoaded by remember(selectedProvider) { mutableStateOf(false) }
    var keyRefresh by remember { mutableIntStateOf(0) }
    val active = state.state in setOf(SessionState.STARTING, SessionState.RUNNING, SessionState.NO_SIGNAL, SessionState.PAUSED, SessionState.STOPPING, SessionState.REQUESTING_PERMISSION)
    val source = if (active) state.source else Source.valueOf(sourceName)
    val full = if (active) state.capabilities.ui == UiCapability.FULL_MIXER_CONTROL else source != Source.SYSTEM_PLAYBACK
    LaunchedEffect(keyRefresh, selectedProvider) {
        keysLoaded = false
        configured = withContext(Dispatchers.IO) { keys.configured(selectedProvider) }
        keysLoaded = true
    }
    val names = listOf("翻譯", "紀錄", "聲音", "設定")
    val symbols = listOf(R.drawable.ic_captions, R.drawable.ic_history, R.drawable.ic_sound, R.drawable.ic_settings)
    Scaffold(containerColor = Color(0xFFF3F7F8), topBar = {
        Surface(color = Color(0xFFF3F7F8)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = Teal) { Symbol(R.drawable.ic_captions, modifier = Modifier.padding(10.dp), tint = Color.White) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("V2V", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(if (tab == 0) "聽懂每一句" else names[tab], color = Muted, style = MaterialTheme.typography.labelMedium)
                }
                if (tab == 1) {
                    IconAction(R.drawable.ic_download, "匯出文字檔", !historyBusy && archive.isNotEmpty(), exportHistory)
                    IconAction(R.drawable.ic_delete, "清除翻譯紀錄", !historyBusy && (archive.isNotEmpty() || historyError.isNotBlank())) { clearHistoryDialog = true }
                } else if (active) {
                    val status = when {
                        state.state == SessionState.REQUESTING_PERMISSION -> "授權中"
                        state.state == SessionState.STARTING -> "準備中"
                        state.state == SessionState.STOPPING -> "結束中"
                        state.state == SessionState.NO_SIGNAL -> "等待音訊"
                        state.providerRecovering -> "等待服務恢復"
                        state.providerError.isNotBlank() -> "字幕暫停"
                        !state.cloudEnabled -> "音訊模式"
                        else -> "翻譯中"
                    }
                    Surface(color = Color(0xFFDDF2E9), shape = RoundedCornerShape(30.dp)) { Text(status, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Teal, style = MaterialTheme.typography.labelMedium) }
                    IconAction(R.drawable.ic_expand, "顯示浮動字幕") { command(TranslationForegroundService.SHOW_OVERLAY) }
                }
            }
        }
    }, bottomBar = {
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            names.forEachIndexed { index, name -> NavigationBarItem(selected = tab == index, onClick = { tab = index },
                icon = { Symbol(symbols[index]) }, label = { Text(name) }) }
        }
    }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            if (tab == 1) {
                HistoryPage(archive, historyError, historyNotice, historyBusy, presentation.saveHistory,
                    { savePresentation(presentation.copy(saveHistory = it)) }, clearHistory, clearHistoryDialog, { clearHistoryDialog = it })
            } else {
                Column(Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (tab) {
                        0 -> {
                            CaptionHero(state, diagnostics, if (active) state.targetLanguage else languages.target) { tab = 1 }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = {
                                    when {
                                        active -> command(TranslationForegroundService.STOP)
                                        source == Source.TEST_FILE -> start(source, false, language, languages.target, selectedProvider, geminiModel)
                                        !configured -> tab = 3
                                        !consent -> Unit
                                        else -> start(source, true, language, languages.target, selectedProvider, geminiModel)
                                    }
                                }, enabled = state.state != SessionState.STOPPING && (active ||
                                    ((source != Source.OWNED_FILE || hasFile) && (source == Source.TEST_FILE ||
                                        (keysLoaded && (!configured || consent))))),
                                    shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f).height(56.dp)) {
                                    Symbol(if (active) R.drawable.ic_stop else R.drawable.ic_play); Spacer(Modifier.width(10.dp)); Text(when {
                                        active && !state.cloudEnabled -> "結束音訊測試"
                                        active -> "結束翻譯"
                                        source == Source.TEST_FILE -> "開始音訊測試"
                                        !configured -> "設定 API Key"
                                        else -> "開始翻譯"
                                    })
                                }
                                if (active) FilledTonalIconButton(onClick = { command(if (state.outputPaused) TranslationForegroundService.RESUME else TranslationForegroundService.PAUSE) }, modifier = Modifier.size(56.dp), shape = RoundedCornerShape(18.dp)) {
                                    Symbol(if (state.outputPaused) R.drawable.ic_play else R.drawable.ic_pause, if (state.outputPaused) "恢復播報" else "暫停播報")
                                }
                            }
                            if (!active && source != Source.TEST_FILE && !consent) Text(
                                "請勾選下方音訊授權以啟用翻譯；只需勾選一次，之後開啟 App 會記住。",
                                style = MaterialTheme.typography.bodySmall, color = Muted)
                            Panel("音訊與語言", R.drawable.ic_mic) {
                                var sourceMenu by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(onClick = { sourceMenu = true }, enabled = !active, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                        Symbol(if (source == Source.SYSTEM_PLAYBACK) R.drawable.ic_expand else R.drawable.ic_file)
                                        Text(when (source) { Source.SYSTEM_PLAYBACK -> "手機播放音訊"; Source.OWNED_FILE -> "本機 WAV 音檔"; Source.TEST_FILE -> "測試音訊" }, Modifier.weight(1f).padding(10.dp))
                                        Symbol(R.drawable.ic_more)
                                    }
                                    DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                                        listOf(Source.SYSTEM_PLAYBACK to "手機播放音訊 · 不限 App 名稱", Source.OWNED_FILE to "本機 WAV · 48k mono PCM16", Source.TEST_FILE to "測試音訊 · 440 / 660 Hz").forEach { (value, title) ->
                                            DropdownMenuItem(text = { Text(title) }, onClick = { sourceName = value.name; sourceMenu = false })
                                        }
                                    }
                                }
                                if (source == Source.OWNED_FILE) TextButton(onClick = pickFile, enabled = !active) { Symbol(R.drawable.ic_file); Text(if (hasFile) " 重新選擇音檔" else " 選擇音檔") }
                                LanguagePicker("來源語言", if (active) state.sourceLanguage else language, !active, true) { selectLanguages(languages.copy(source = it)) }
                                LanguagePicker("目的語言", if (active) state.targetLanguage else languages.target, !active, false) { selectLanguages(languages.copy(target = it)) }
                                TextButton(onClick = { tab = 3 }, enabled = !active) { Text("${if (active) state.cloudProvider.label else selectedProvider.label} · 更換引擎") }
                                if (source != Source.TEST_FILE) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(if (active) state.cloudEnabled else consent, saveCloudConsent, enabled = !active)
                                        Text("我有權處理此音訊，並同意將音訊與文字送到所選雲端引擎辨識及翻譯（目前：${if (active) state.cloudProvider.label else selectedProvider.label}，可能收費）。", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("記住此選擇，下次不需重勾；可隨時取消。", style = MaterialTheme.typography.bodySmall, color = Muted)
                                    if (!configured) TextButton(onClick = { tab = 3 }) { Symbol(R.drawable.ic_shield); Text(" 設定 API Key") }
                                    if (source == Source.SYSTEM_PLAYBACK) Text("不需選 App；接收系統允許擷取的播放音訊。部分通話或受保護內容可能無法取得；原聲音量由來源 App 控制。", style = MaterialTheme.typography.bodySmall, color = Muted)
                                } else Text("測試音不是實際翻譯播報，不會傳到雲端。", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                            Panel("浮動字幕", R.drawable.ic_expand) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = { command(TranslationForegroundService.SHOW_OVERLAY) }, enabled = active, modifier = Modifier.weight(1f)) { Symbol(R.drawable.ic_expand); Text(" 顯示") }
                                    OutlinedButton(onClick = { command("overlayPermission") }, modifier = Modifier.weight(1f)) { Symbol(R.drawable.ic_settings); Text(" 授權") }
                                }
                                if (state.overlayError.isNotBlank()) Text(state.overlayError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                Text("切到其他 App 後，從小圈圈調整播報與字幕。", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                        }
                        2 -> SoundPage(state, mix, presentation, full, save, savePresentation, command)
                        3 -> {
                            Panel("引擎與金鑰", R.drawable.ic_shield) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CloudProvider.entries.forEach { provider ->
                                        FilterChip(selected = selectedProvider == provider, enabled = !active,
                                            onClick = { selectProvider(provider) }, label = { Text(provider.label) })
                                    }
                                }
                                if (active) Text("結束本次翻譯後可切換引擎。", style = MaterialTheme.typography.bodySmall, color = Muted)
                                if (selectedProvider == CloudProvider.GEMINI) {
                                    Text("Gemini 模型", style = MaterialTheme.typography.labelLarge)
                                    Column {
                                        GeminiModel.entries.forEach { model -> FilterChip(selected = geminiModel == model, enabled = !active,
                                            onClick = { selectGeminiModel(model) }, label = { Text("${model.label} · ${model.apiId}") }) }
                                    }
                                    Text("服務忙碌時，可結束本次翻譯後切換模型再試；不會自動切換供應商。", style = MaterialTheme.typography.bodySmall, color = Muted)
                                }
                                key(selectedProvider, geminiModel) { KeyPanel(selectedProvider, geminiModel, keys, configured, !active, diagnostics.journal) { keyRefresh++ } }
                            }
                            Panel("語音引擎", R.drawable.ic_sound) {
                                Text("播報使用手機已安裝、符合目的語言的離線語音；辨識與翻譯需要網路。", color = Muted)
                                OutlinedButton(onClick = { command("ttsSettings") }) { Symbol(R.drawable.ic_settings); Text(" Android 語音設定") }
                            }
                            var details by rememberSaveable { mutableStateOf(false) }
                            Panel("診斷與測試", R.drawable.ic_settings) {
                                TextButton(onClick = { details = !details }) { Symbol(R.drawable.ic_more); Text(if (details) " 收合技術資訊" else " 展開技術資訊與 Log") }
                                AnimatedVisibility(details) { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    DiagnosticPanel(diagnostics, logEnabled, setLogEnabled, logNotice, logBusy, exportLog, shareLog)
                                    Detail("辨識 / 翻譯 / 播報", "${state.asrState} / ${state.mtState} / ${state.ttsState}")
                                    Detail("來源", "${state.source} · ${state.sourceStatus}")
                                    Detail("音訊路由", state.route)
                                    Detail("遺漏 / 失敗", "輸入 ${state.skippedSamples} samples · 播報跳過 ${state.ttsDropped} · 合成失敗 ${state.ttsFailures}")
                                    Detail("PCM", "${state.frames} frames · ${state.asrSamples} samples · ${state.underruns} underruns")
                                    Detail("MT 用量", state.providerTokens?.let { "$it tokens（不是帳單）" } ?: "供應商未回傳用量")
                                } }
                            }
                            Text("V2V ${com.yinwei.v2v.BuildConfig.VERSION_NAME} · 本機紀錄不自動上傳；沒有離線辨識／翻譯模型。", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(8.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable private fun CaptionHero(state: SessionSnapshot, diagnostics: Diagnostics, targetLanguage: String, history: () -> Unit) {
    val latest = state.segments.lastOrNull { it.translatedText.isNotBlank() }
    val original = state.segments.lastOrNull()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(state.id, latest?.id, latest?.revision, lifecycle) {
        if (latest != null && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) diagnostics.journal.record(DiagnosticEvent.TRANSLATION_VISIBLE, state.id,
            mapOf(Metric.SEGMENT_ID to latest.id, Metric.REVISION to latest.revision, Metric.CHARACTERS to latest.translatedText.length))
    }
    Surface(shape = RoundedCornerShape(28.dp), color = Ink, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Symbol(R.drawable.ic_captions, tint = Color(0xFF83D6C7)); Text(" ${AppLanguage.label(targetLanguage)}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFFBFE6DF), style = MaterialTheme.typography.labelLarge)
                 Text("${state.segments.count { it.translatedText.isNotBlank() }} 段", color = Color(0xFFACC4CA), style = MaterialTheme.typography.labelMedium)
            }
            SelectionContainer { Text(latest?.translatedText ?: when {
                state.mtState == StageState.RUNNING -> "正在翻譯這一句…"
                state.asrState == StageState.RUNNING -> "正在聆聽與辨識…"
                state.cloudEnabled -> "等待下一句話…"
                else -> "讓語言，不再是距離。"
            }, color = Color.White, fontSize = 25.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, maxLines = 8, overflow = TextOverflow.Ellipsis) }
            val sourceText = latest?.originalText ?: original?.originalText
            if (!sourceText.isNullOrBlank()) Text(sourceText, color = Color(0xFFB5C9CF), fontSize = 14.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(color = Color(0xFF365057))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Symbol(if (state.outputPaused) R.drawable.ic_pause else R.drawable.ic_sound, tint = Color(0xFF8DD8C8))
                Text(if (state.outputPaused) "播報已暫停" else "待播 ${state.ttsPending} 段 · %.1f 秒".format(state.ttsBufferedMs / 1000.0), color = Color(0xFFCEE3E6), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f)); Text("%.2f×".format(state.ttsRate), color = Color(0xFF8DD8C8), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = history, contentPadding = PaddingValues(0.dp)) { Text("查看翻譯紀錄", color = Color(0xFF8DD8C8)); Symbol(R.drawable.ic_chevron, tint = Color(0xFF8DD8C8)) }
            if (state.providerError.isNotBlank()) Text(state.providerError, color = Color(0xFFFFC3BA), style = MaterialTheme.typography.bodySmall)
            if (state.ttsError.isNotBlank()) Text(state.ttsError, color = Color(0xFFFFD29A), style = MaterialTheme.typography.bodySmall)
            if (state.state in setOf(SessionState.IDLE, SessionState.STOPPED, SessionState.ERROR, SessionState.NO_SIGNAL, SessionState.REQUESTING_PERMISSION)) Text(state.message, color = Color(0xFFFFD29A), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun SoundPage(state: SessionSnapshot, mix: MixSettings, p: PresentationSettings, full: Boolean,
    save: (MixSettings) -> Unit, present: (PresentationSettings) -> Unit, command: (String) -> Unit) {
    Panel("播報", R.drawable.ic_sound) {
        Toggle("播放翻譯語音", checked = p.speechEnabled) { present(p.copy(speechEnabled = it)) }
        Stepper("語速", "%.2f×".format(p.speechRate), R.drawable.ic_speed, p.speechRate > 0.75f, p.speechRate < 2f,
            { present(p.copy(speechRate = (p.speechRate - 0.1f).coerceAtLeast(0.75f))) }, { present(p.copy(speechRate = (p.speechRate + 0.1f).coerceAtMost(2f))) })
        Toggle("自動追趕", "積壓時最多使用 2 倍速", p.autoCatchUp) { present(p.copy(autoCatchUp = it)) }
        HorizontalDivider()
        Stepper("譯音增益", "${(mix.translationGain * 100).roundToInt()}%", R.drawable.ic_sound, mix.translationGain > 0f, mix.translationGain < 4f,
            { save(mix.copy(translationGain = (mix.translationGain - 0.1f).coerceAtLeast(0f))) }, { save(mix.copy(translationGain = (mix.translationGain + 0.1f).coerceAtMost(4f))) })
        Slider(mix.translationGain, { save(mix.copy(translationGain = it)) }, valueRange = 0f..4f)
        Toggle("語音響度增強", "自動補強偏小的合成語音，最高約 +12 dB", p.enhanceSpeech) { present(p.copy(enhanceSpeech = it)) }
        Text("增益最高 400%，仍受手機媒體音量、耳機及系統保護限制。先從較低增益調整；這不會改變其他 App 的音量。", color = Muted, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { command("soundSettings") }) { Symbol(R.drawable.ic_settings); Text(" 系統音量設定") }
        if (state.outputPaused) FilledTonalButton(onClick = { command(TranslationForegroundService.RESUME) }) { Symbol(R.drawable.ic_play); Text(" 確認路由並恢復播報") }
    }
    Panel(if (full) "原聲與譯音" else "譯音聲道", R.drawable.ic_settings) {
        if (full) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MixMode.entries.forEach { mode ->
                FilterChip(mix.mode == mode, { save(mix.copy(mode = mode)) }, label = { Text(when (mode) { MixMode.SPLIT -> "左右分離"; MixMode.TRANSLATION_ONLY -> "只聽翻譯"; MixMode.MIX -> "混音" }) })
            } }
            Text("原聲 ${if (mix.mode == MixMode.TRANSLATION_ONLY) 0 else (mix.originalGain * 100).roundToInt()}%")
            Slider(if (mix.mode == MixMode.TRANSLATION_ONLY) 0f else mix.originalGain, { save(mix.copy(originalGain = it)) }, enabled = mix.mode != MixMode.TRANSLATION_ONLY)
            if (mix.mode == MixMode.SPLIT) Toggle("交換左右", checked = mix.swap) { save(mix.copy(swap = it)) }
            TextButton(onClick = { save(mix.copy(mode = MixMode.MIX, originalGain = 0.2f, translationGain = 1f)) }) { Symbol(R.drawable.ic_refresh); Text(" 原聲 20% · 譯音 100%") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TranslationChannel.entries.forEach { channel ->
                FilterChip(mix.translationChannel == channel, { save(mix.copy(translationChannel = channel)) }, label = { Text(when (channel) { TranslationChannel.BOTH -> "雙耳"; TranslationChannel.LEFT -> "左耳"; TranslationChannel.RIGHT -> "右耳" }) })
            } }
            Text("只調整 V2V 的翻譯語音；其他 App 的原聲與左右聲道不變。實體左右隔離仍受耳機／通話路由影響。", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
    Panel("字幕外觀", R.drawable.ic_captions) {
        Stepper("字級", "${p.captionSize.roundToInt()}sp", R.drawable.ic_captions, p.captionSize > 14f, p.captionSize < 30f,
            { present(p.copy(captionSize = (p.captionSize - 1).coerceAtLeast(14f))) }, { present(p.copy(captionSize = (p.captionSize + 1).coerceAtMost(30f))) })
        Stepper("顯示行數", "${p.captionLines}", R.drawable.ic_file, p.captionLines > 2, p.captionLines < 6,
            { present(p.copy(captionLines = p.captionLines - 1)) }, { present(p.copy(captionLines = p.captionLines + 1)) })
        Text("背景 ${(p.backgroundOpacity * 100).roundToInt()}%", color = Muted)
        Slider(p.backgroundOpacity, { present(p.copy(backgroundOpacity = it)) })
        Text("白色文字與黑色描邊不隨背景透明度變化；觸控穿透模式依 Android 安全限制略為透明。", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun HistoryPage(entries: List<HistoryEntry>, error: String, notice: String, busy: Boolean, saveEnabled: Boolean,
    toggleSave: (Boolean) -> Unit, clear: () -> Unit, confirm: Boolean, confirmVisible: (Boolean) -> Unit) {
    var showOriginal by rememberSaveable { mutableStateOf(true) }
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(entries, search) { entries.asReversed().filter { search.isBlank() || it.original.contains(search, true) || it.translation.contains(search, true) } }
    if (confirm) AlertDialog(onDismissRequest = { confirmVisible(false) }, icon = { Symbol(R.drawable.ic_delete) }, title = { Text("清除所有翻譯紀錄？") },
        text = { Text("這會刪除此手機保存的紀錄。正在進行的翻譯不會停止；若自動保存開啟，新的片段仍會保存。已匯出的檔案需自行刪除。") },
        confirmButton = { TextButton(onClick = { confirmVisible(false); clear() }) { Text("清除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmVisible(false) }) { Text("取消") } })
    LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Panel("翻譯紀錄 · ${entries.size}", R.drawable.ic_history) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("原文與譯文，隨時回看。", color = Muted, modifier = Modifier.weight(1f))
                }
                Toggle("自動保存", "只存此手機，最多 1,000 段，或 4 MB，不進技術 Log", saveEnabled, toggleSave)
                Toggle("顯示原文", checked = showOriginal) { showOriginal = it }
                OutlinedTextField(search, { search = it }, placeholder = { Text("搜尋原文或翻譯") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (notice.isNotBlank()) Text(notice, color = Teal, style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (filtered.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Symbol(R.drawable.ic_history, modifier = Modifier.size(42.dp), tint = Muted)
                Text(if (search.isBlank()) "還沒有翻譯紀錄" else "找不到相符紀錄", fontWeight = FontWeight.SemiBold)
                Text(if (search.isBlank()) "完成的翻譯會出現在這裡。" else "試試其他關鍵字。", color = Muted)
            }
        }
        items(filtered, key = { "${it.sessionId}/${it.segmentId}" }) { entry ->
            Surface(shape = CardShape, color = Color.White) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val formatter = remember { DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault()) }
                    Row { Text(formatter.format(Instant.ofEpochMilli(entry.createdAt)), style = MaterialTheme.typography.labelMedium, color = Muted)
                        Spacer(Modifier.weight(1f)); Text("${AppLanguage.label(entry.sourceLanguage)} → ${AppLanguage.label(entry.targetLanguage)}", style = MaterialTheme.typography.labelMedium, color = Teal) }
                    SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (showOriginal) Text(entry.original, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
                        Text(entry.translation, color = Ink, fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
                    } }
                }
            }
        }
    }
}

@Composable private fun LanguagePicker(title: String, selected: String, enabled: Boolean, allowAuto: Boolean, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Muted)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(AppLanguage.label(selected), Modifier.weight(1f)); Symbol(R.drawable.ic_more)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                if (allowAuto) DropdownMenuItem(text = { Text("自動偵測") }, onClick = { choose("auto"); expanded = false })
                AppLanguage.entries.forEach { language -> DropdownMenuItem(text = { Text(language.label) },
                    onClick = { choose(language.code); expanded = false }) }
            }
        }
    }
}
