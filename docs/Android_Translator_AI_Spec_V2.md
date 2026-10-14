# Android 即時音訊翻譯 App — AI 開發規格書

版本：2.0｜日期：2026-10-14｜平台：Android｜用途：交付 AI／工程師實作與驗收

本文件取代原 V1.0 規格與其 V2 附錄。里程碑、功能優先序、測試與限制均以本文件為準。

## 0. 給 AI 的最高優先指令

使用者已確認：只做 Android；不要求在原 APP 播放音訊；本 APP 必須能以可拖動小圈圈收合並在背景工作，讓使用者觀看 YouTube 或線上會議畫面；字幕背景透明度須獨立調整，文字保持清楚並支援描邊。

你的任務是做真實可運作的產品，而非假畫面。先驗證音訊來源，再做辨識、翻譯、語音與完整混音。不能以测试音檔成功冒充 YouTube 或會議整合成功。不得推定浮窗、MediaProjection、來源 APP 靜音或內建 WebView 可以解除第三方平台限制。

先讀現有 README、AGENTS.md、PROJECT_HANDOFF.md、Gradle 與程式碼。不要覆寫既有成果。每階段先列檔案與驗收項目，完成實作、適當檢查、文件與交付。無真機、API Key、來源授權或帳號時標記阻擋原因；mock 必須明示，不得報告為實際支援。

本專案不包含 iOS、root、Accessibility 攔截音訊、DRM 繞過或未授權的平台接入。不自行發佈 Google Play，不替使用者購買服務或傳送第三方訊息。

## 1. 產品目標與成功定義

產品暫名：Live Audio Translator。

使用者希望在手機觀看影片／線上會議畫面，同時聽到翻譯，也可查看浮動字幕。主 APP 可收合成小圈圈；翻譯由前景服務持續處理。只聽翻譯、原聲／翻譯獨立音量、左耳翻譯右耳原聲，必須在本 APP 可控制完整音訊輸出時真正有效。

主要語言：英文 en、印尼文 id、繁體中文 zh-TW、簡體中文 zh-CN。首個端到端路徑先做 en/id → zh-TW。介面預設繁體中文，架構支援英文、印尼文、簡體中文本地化。

「即時」指持續輸入、逐段處理與低延遲輸出，不代表零延遲同聲傳譯。TTS 通常晚於原聲；若需原文譯文對齊，必須緩衝延後原聲，並明確顯示。

產品交付成功分成三層：
1. 基礎成功：合法可控的音檔／影片來源，完成辨識、翻譯、字幕、TTS、小圈圈及完整混音。
2. 外部播放成功：指定 Android 裝置及來源版本實測取得有效音訊，完成浮動字幕與翻譯 TTS；不保證原聲可控。
3. 目標服務成功：YouTube／指定會議服務有真實可用的來源整合，完成使用者要求的畫面與音訊分離。必須另有來源接入證據；只完成第一層不能宣稱第三層完成。

## 2. 音訊與畫面：必須分開定義

外部 APP 在前景顯示畫面，不代表本 APP 擁有其音訊。小圈圈只改變操作介面，不會改變來源擷取政策或原聲音量控制權。

完整功能要求同時滿足：
- 來源持續提供合法可用的原始音訊。
- 本 APP 可決定使用者聽到的原聲增益與聲道。
- 外部來源不再額外直接播放未受控制的原聲。
- 原聲靜音不會切斷送往 ASR 的輸入。

來源 APP 的靜音、降音量或音訊焦點處理不一定保留有效擷取音訊，必須實測，不能視為通用解法。僅把擷取副本設為零不能關掉外部 APP 原聲。

### 2.1 三種運作模式

A. OWNED_PLAYBACK：本 APP 讀取使用者選擇的音檔／影片、合法串流或可授權的直接音訊；解碼後先分流給 ASR，再進混音器播放。支援完整雙音量與三種聲道模式。自有影片可提供 APP 內播放器與系統支援時的畫中畫；畫中畫不等於浮動字幕。

B. EXTERNAL_CAPTURE：使用者在 YouTube／其他 APP 看畫面，本 APP 取得 Android 允許擷取的播放音訊。只保證對已驗證来源提供字幕與翻譯 TTS。若原聲仍由外部 APP 直接播放，原聲增益、純翻譯與原聲聲道分配均不可用；避免把副本原聲再播一次造成重音。

C. AUTHORIZED_INTEGRATION：透過來源正式 SDK／API 或其他已授權音訊通道，取得連續音訊並控制音訊輸出。只有在輸入持續、來源直出可關閉且畫面可維持後，才可升級為完整控制模式。

模式切換需停止舊來源並清理資源，不得在背景偷偷改用麥克風。

### 2.2 YouTube／線上會議接入決策

每一來源建立 Source Integration Decision 文件，記錄：服務名稱、接入方式、授權與條款依據、音訊 API 能力、外部畫面是否可同時觀看、是否額外加入會議、來源是否仍直接出聲、靜音是否保留輸入、付費或帳號依賴、已測裝置與版本。

YouTube：先驗證 Android 播放擷取。若需完整控制，另研究正式允許的整合；官方嵌入播放器可控制播放不代表能輸出原始 PCM。不得以擷取未授權串流 URL、下載受保護內容或 WebView 注入作為預設方案。

會議：選定服務後再驗證。一般 AudioPlaybackCapture 不應被當作通話擷取介面。正式 SDK 可能要求會議權限、額外 participant 或在本 APP 內呈現會議，而未必能保留外部 APP 的畫面；必須逐項證明。找不到可行來源時，狀態為 BLOCKED_SOURCE_INTEGRATION，交付證據與可行替代，不得用通用 mock 結案。

替代路徑：使用者授權的音訊檔、另一裝置外放搭配手機麥克風、或未來桌面 Companion。Companion 是後續選項，不是 Android 第一版必需交付。麥克風方案不能提供來源原聲的獨立控制，也不能保證會議品質。

## 3. Android 平台基準

Kotlin + Jetpack Compose + Material 3；minSdk 29。compileSdk、targetSdk 與 AGP/Kotlin/Gradle 使用開發時已驗證的穩定相容版本，記錄版本及當時適用的 Google Play target 要求，不硬寫未查證的最新版本。

音訊擷取使用 AudioPlaybackCaptureConfiguration、MediaProjection、AudioRecord。內部播放擷取也需要 RECORD_AUDIO runtime permission 與使用者 MediaProjection 同意；RECORD_AUDIO 授權不表示一定開啟實體麥克風。

可擷取音訊取決於音訊用途、來源 capture policy 與同一使用者 profile 等條件。公開播放擷取以 MEDIA、GAME、UNKNOWN 等符合規則的用途為限；VOICE_COMMUNICATION 不屬於一般可擷取播放用途。不因來源名稱就推斷其用途或支援狀態。

依 target／OS 實作適用的 foreground service 權限與類型。播放擷取服務須處理 mediaProjection；持續播放處理 mediaPlayback；麥克風來源處理 microphone 與使用中權限限制。通知授權依 OS 處理；通知被拒不代表可隱藏前景服務。開始工作由可見 Activity 中的明確使用者操作觸發，不能從背景任意啟動。

依版本處理 MediaProjection 同意、token 限制及 session 生命週期，registerCallback/onStop、停止、使用者撤銷、螢幕鎖定及系統終止。若採用需建立 virtual display 的方案，遵守一次性 token 規則；不要為純音訊路徑盲目建立不必要的畫面擷取。每次新 session 按實際 API 要求重新授權，不假設舊 token 永久有效。

API 35+ 等新版本的音訊焦點請求條件及背景限制須查官方文件並測試；不以搶焦點迫使來源停止來假裝獨立靜音。

## 4. 操作流程與浮窗

### 4.1 首次與啟動

首次說明：來源限制、雲端資料傳送、錄音／會議同意、前景通知及浮窗權限。使用者選來源、語言、輸出模式、引擎並開始。只要求當前來源必需權限。拒絕浮窗時，仍可使用 APP 內字幕與通知控制。

開始前執行 capability check；顯示能力、缺失條件及可用功能。權限取得後進入來源準備、擷取／解碼、ASR、翻譯與輸出。不要把 AudioRecord 啟動成功當作有效輸入成功。

### 4.2 小圈圈

以 WindowManager TYPE_APPLICATION_OVERLAY 實作，需要 SYSTEM_ALERT_WINDOW 特殊存取權；不能用 Accessibility Service 代替。

預設直徑 56dp，可設 40–80dp；顯示翻譯狀態圖示／簡短電平指示。拖動、靠邊吸附、可保存位置；旋轉、螢幕尺寸與顯示密度改變後重新限制在安全可見範圍。拖動不誤觸為點擊。

點擊展開小型控制面板：暫停／繼續、字幕開關、語音開關、音量、展開主畫面、停止。拖至刪除區或按停止，經明確操作結束 session；不能只隱藏圖示而讓使用者誤以為停止。

小圈圈、字幕與展開面板獨立管理。收合 Activity 不結束服務。禁止透明全螢幕攔截觸控；浮窗以外可正常操作 YouTube／會議。提供暫時隱藏浮窗，可從通知恢復。

背景限制：OEM、省電、記憶體、鎖屏或系統保護可能停止擷取。顯示真實停止原因；不得承諾永不被系統終止。不自動在程序重建或開機後重新錄音。

### 4.3 浮動字幕

字幕窗與小圈圈可同時存在，可各自隱藏。支持拖動、縮放、靠邊、防超出畫面與位置保存。背景、字體、行數及大小即時預覽。

必須使用背景層獨立 alpha；禁止對整個字幕容器使用降低 alpha 的方式，否則文字也變淡。背景透明度 0–100%，0 為完全透明，100 為不透明。文字預設 alpha=1，描邊亦不隨背景透明度變化。

預設外觀：黑底 55% 不透明度、白色粗體字、黑描邊 1.5dp、18sp、最多 4 行、圓角與 8dp padding。字級可設 12–40sp；描邊 0–4dp，0 表示關閉；支援字色、描邊色、背景色、粗體、行距 1.0–1.6。描邊採用外輪廓效果，避免侵蝕中文字筆畫；可用先描邊再填色的同一文字 layout，兩次繪製必須字形與換行一致。

支援原文＋譯文／只譯文；以字級／字色區分，不只依賴透明度。partial 可更新但不閃爍、不重複整段；final 穩定後才進 TTS。

操作模式分為調整模式與閱讀模式：調整模式顯示拖曳及縮放控制；閱讀模式縮小可觸區，窗外觸控不攔截。若提供穿透字幕內容，仍需從小圈圈或通知恢復調整。不得讓用戶失去停止／移動浮窗入口。

系統安全畫面或其他 APP 可隱藏 overlay，尊重系統行為；回退至 APP 內字幕。支援系統字體放大、TalkBack 的調整控制描述及橫直畫面。所有外觀設定透過 DataStore 保存，含設定 schema version、預設值及還原預設。

## 5. 音訊處理、雙音量與聲道

管線：Source Adapter → timestamped PCM → [ASR 分支、可控原聲分支]；ASR → segmenter → MT → 字幕與 TTS → TTS PCM → StereoMixer → AudioTrack。

EXTERNAL_CAPTURE 預設只播放 TTS，禁止將來源副本再次混入原聲。內部來源先解碼再分流，辨識不經輸出增益；不要從已靜音的 AudioTrack 回抓辨識。

### 5.1 三種完整控制模式

原聲 O、翻譯 T、增益 gO/gT 0–1；原聲與 TTS 混音前轉為相同取樣率、聲道布局與 PCM 格式。

SPLIT：L=gT×T；R=gO×O，可左右互換。
TRANSLATION_ONLY：L=R=gT×T，實際原聲增益為 0。
MIX：L=R=limiter(gO×O+gT×T)。

第一版以 mono 原聲分配至模式輸出；若來源為 stereo，明確定義 downmix（例如 L/R 平均並保留 headroom），避免相位抵消與超幅。保留原始 stereo 模式為後續擴充。

定義 TRANSLATION_ONLY 為鎖定 gO=0；切换前保存其他模式的原聲增益。原聲滑桿在此模式禁用並說明，想調原聲須切回 MIX/SPLIT。避免預設與 mode 狀態矛盾。

預設：纯翻譯（TRANSLATION_ONLY、0/100）、背景原聲（MIX、20/100）、雙聲（MIX、100/100）；100/100 必須經 limiter，不能以相加後直接截斷 PCM。

增益變化採 20–100ms ramp；模式切換 30–100ms crossfade。先驗證 PCM 沒有 clipping、click/pop，再主觀試聽。切換不重新提交 TTS、不重播旧句。來源停止後不再輸出舊語音。

### 5.2 TTS 與 mixer 契約

Android TextToSpeech 是預設低成本引擎，但 speak() 直接播放不等於提供可控 PCM。完整混音路徑需 synthesizeToFile 後解析／解碼，或 provider 提供可消費的音訊／PCM；不能以兩個獨立播放器宣稱已經聲道隔離。

區分 DIRECT_TTS（快速基礎播放，聲道控制能力有限）與 PCM_TTS（進單一 mixer，可驗證增益／聲道／路由）。UI 依 capabilities 啟用；PCM 路徑為完整功能必做。合成檔進 app-private cache，完成／取消／過期即清理。Android TTS 可用語言、離線能力、引擎下載與輸出格式按實際裝置驗證。

系統 TTS provider 不一定可取消單一合成或提供 streaming，provider 契約須揭示此限制。雲端 provider 的資料、格式、費用與條款明示。

### 5.3 隊列、同步及效能

PCM frame 建議 20–40ms；ASR 使用 provider 相容格式，例如 16kHz mono PCM16；mixer 以裝置支持的 48kHz stereo 或其他已驗證格式輸出。重採樣包含抗混疊，不是單純丟樣本。

所有 buffer 有上限，記錄 underrun/overflow。建議 ASR backlog 最大 5 秒、TTS queue 最大 3 個片段且待播音訊不超過 10 秒，數值可配置並測試。過載時明示跳過或延遲，不無限排隊。字幕不因丟棄 TTS 而遺失。

同步策略分為即時原聲（允許譯音晚到）與延遲原聲（限定緩衝上限）；來源中斷時清理超時 buffer。第一版預設即時原聲，不宣稱譯音與影片口型同步。若影片畫面在外部 APP，不能任意延後畫面與音訊一起同步。

ASR 去重、翻譯結果排序、TTS 播放均以 sessionId/segmentId/revision 驗證；晚到結果不得覆蓋較新 final，停止／換語言後不再播放舊結果。

## 6. 音訊路由與防回灌

提供系統預設、耳機優先與可用輸出裝置選擇。AudioTrack preferred device 是偏好而非硬保證；以實際 routed device 回報。不要把 setCommunicationDevice 當作所有媒體路由的通用方案。

監測 AudioDeviceCallback、實際路由、AUDIO_BECOMING_NOISY、焦點變化。A2DP 有機會提供 stereo；HFP/SCO、LE Audio 及會議共用路由需按裝置驗證。不能僅按耳機連線推斷 stereo。

預設耳機斷線立即停止 TTS 及本 APP 可控的原聲，避免內容突然外放；字幕可持續。重新連接後由使用者確認恢復。選擇自動喇叭回退須明確 opt-in。測量與記錄斷線處理延遲；不能承諾所有 OEM 在切路由瞬間絕不外洩。

內部擷取排除本 APP UID，配合適當 capture policy，並用實測證明本 APP TTS 不回灌。第三方 TTS 引擎直接播放可能由其他 UID 輸出，不能假設排除本 APP 就有效；PCM_TTS 由本 APP AudioTrack 播放更容易建立一致政策。

麥克風模式無法透過 UID 排除喇叭聲：優先要求耳機；喇叭情境評估 AEC 支援與實際效果。若暫停收音避開 TTS，必須顯示會漏掉來源語音，不可稱連續全雙工。ASR 不得把譯音當成新的原文無限循環。

## 7. ASR、翻譯與語言

Provider 可替換；第一版用一組實際可用的雲端 ASR＋MT 完成 en/id → zh-TW。OpenAI／Gemini 可作候選，不預設所有模型都有串流 ASR 或所有語言組合。記錄 API 版本、模型 ID、輸入格式、語言、速率限制、價格來源與查核日期。

ASR：partial/final、時間戳、信心度（實際有提供才顯示）、手動／自動語言。VAD 與斷句間隔 300–1500ms 可調；連續發言也有最大切段長度，初始 8–12 秒並以 provider 限制調整。不能等整段會議结束再翻譯。

MT：保留原意、術語與說話者信息（來源有提供時），不補充事實；支援術語表、有限上下文窗口、速度／品質模式。語言偵測需避免因單一短詞反覆切換。繁體中文是明确輸出目標，不把 zh 當作已符合 zh-TW；離線引擎若僅輸出簡體，可增加已授權繁簡轉換並說明術語限制。

TTS：只合成穩定 final；語速 0.75–1.5；聲線與語言需 engine 實際支援。標點、數字、英文詞、姓名、印尼語需測試。TTS 設 0 不影響 ASR 或字幕。

網路：HTTPS、合理 timeout、有界重試、429 backoff、401 明確提示、5xx 可切已配置 provider；取消後不得重試舊 session。provider 切換記錄 segment 邊界與重複風險。

## 8. 離線模式與費用

離線不是預設全部支援。ASR 可評估 sherpa-onnx 等實際模型，MT 選可在 Android 部署且授權合適的模型；逐一測 en/id/zh、語言組合、RAM、速度與大小。不得僅列出框架名稱就宣稱印尼語离線可用。

模型管理：下載、SHA-256、版本、來源 URL、授權、空間檢查、取消、可用時的續傳、刪除；下載必須區分 downloading/verified/installed，未下載或初始化失敗不可顯示可用。

離線自動回退仅在 ASR＋MT＋TTS 全鏈路語言支持且初始化成功時可用。若只有字幕鏈路離線可用，明示降級並停 TTS；不可偷偷切回雲端。無離線模型時斷網進明確錯誤。

費用為估算，依供應商模型費率與實際可取得 usage 計算，顯示查核日期與誤差來源。不把累積用量估算稱為正式帳單；provider 不回傳 usage 時明示估算方法。

## 9. 設定、資料及隱私

DataStore 保存非敏感設定；Room 保存 opt-in 的歷史與 segment。API Key 使用 Android Keystore 持有金鑰，加密後存 app-private storage；不得把 API Key 直接當 Keystore 可任意儲存的普通文字，且须排除備份／還原後失效資料並提供重新輸入流程。

Key 可測連線、移除與更新；不進 log、Crash、analytics、診斷、截圖範例、Git 或 CI。測試 Key 不寫入 APK。自用 BYOK 可直接連 provider，但要記錄供應商是否允許此使用方式；若公開發佈需要後端代理，另定範圍。

雲端模式先同意音訊／文字傳送；預設不保存原始錄音，逐字稿保存 opt-in。提供歷史刪除、快取清理、保留期限、TXT/SRT UTF-8 匯出。SRT 使用單調且有效的 start/end，相鄰段排序；partial 不重複輸出。

摘要／待辦是選配，不阻擋核心交付。對可能不准確內容提示，不能代替原文記錄。日誌包含事件與耗時，不包含原文音訊、完整逐字稿、Key；需要敏感診斷時另取得使用者明確同意。

## 10. 架構與契約

建議分層：app/ui、feature/session、feature/overlay、feature/diagnostics、core/audio、core/mixer、core/asr、core/translation、core/tts、core/settings、core/storage、providers/cloud、providers/offline。第一版可先用少量 Gradle module 保持邊界，不為形式過度拆分。DI 選 Hilt 或 Koin 一種；coroutines/Flow；Room、DataStore、OkHttp。

TranslationForegroundService 持有 SessionController；Activity／overlay 僅訂閱狀態與發命令。旋轉或 Activity 被移除不重建擷取；程序被殺不能冒稱 session 仍在运行。

AudioFrame：sessionId、sourceId、sequence、timestampUs（monotonic）、sampleRate、channelCount、encoding、PCM；明訂 buffer ownership 與回收時間。
TranscriptSegment：sessionId、id、revision、startMs、endMs、sourceLanguage、originalText、translatedText、isFinal、provider、ttsState、latencyMetrics。
SourceCapabilities：captureAvailable、inputVerified、originalOutputControlled、externalDirectOutputPresent、stereoOutputSupported、reason、verificationEvidence。
OriginalGainControlCapability：APP_OWNED／PROVIDER_CONTROLLED／PLATFORM_VERIFIED／UNAVAILABLE。PLATFORM_VERIFIED 必須有版本範圍、證據與測試，不是猜測開關。
UiCapability：FULL_MIXER_CONTROL／TRANSLATION_OUTPUT_ONLY／CAPTURE_ONLY／UNSUPPORTED；由來源、provider、輸出路由合成。
AudioSource：OWNED_FILE／OWNED_STREAM／SYSTEM_PLAYBACK／AUTHORIZED_INTEGRATION／MICROPHONE／TEST_FILE。
SessionState：IDLE／REQUESTING_PERMISSION／STARTING／RUNNING／NO_SIGNAL／PAUSED／STOPPING／ERROR／STOPPED。ASR、MT、TTS 各有子狀態；不能把 CAPTURING 與 TRANSLATING 設為互斥，兩者會同時運作。
SourceStatus：UNKNOWN／AVAILABLE／NO_SIGNAL／UNSUPPORTED／BLOCKED_SOURCE_INTEGRATION／ERROR。NO_SIGNAL 不能自动升為 UNSUPPORTED。

所有 provider 提供 capabilities、supportedLanguages、initialize、healthCheck、cancel；ASR 提供 stream events，MT 提供 translate，TTS 提供 direct playback 或 synthesized audio 明確契約。各階段不支持的能力明示，不用空函式假成功。

## 11. 主要畫面

1. 首次說明與按來源要求權限。
2. 主頁：來源、語言、引擎、字幕／語音／兩者、開始／停止、來源能力。
3. 即時頁：原文／譯文、電平、耗時、語音狀態、收合成小圈圈。
4. 小圈圈＋展開控制面板。
5. 字幕外觀：背景透明度、顏色、字級、粗體、描邊、行距、行數、預覽、重設。
6. 音訊頁：mode、原聲／翻譯滑桿、左右交換、預設、路由与能力說明。
7. 引擎與 Key：設定、健康檢查、費用估算、離線可用狀態。
8. 模型管理與下載。
9. 診斷：來源信號、權限、路由、provider、backlog、latency、匿名匯出。
10. 選配歷史與匯出。

UNAVAILABLE 時禁用原聲滑桿及聲道分配，純翻譯預設不可使用。可控制 TTS 自身音量／聲道時仍可保留該功能，但文字說明「只調整翻譯，來源 APP 可能仍有原聲」。來源未经驗證時不顯示全綠成功狀態。

## 12. 效能與測量

良好網路、短句、已驗證來源下，穩定譯字幕目標：片段結束到譯文顯示 P50 ≤2.5秒、P95 ≤5秒；TTS 開始 P50 ≤4秒、P95 ≤8秒。這些是工程目標，非所有手機保證。

另記錄：語音開始到首次 partial／final／譯文／TTS、斷句等待、MT time、TTS 合成及排隊、原聲緩衝、provider RTT。使用 monotonic clock；樣本量、來源音檔、裝置、網路條件寫入報告，建議每基準至少 50 個片段。

至少 30 分鐘持續運作、20 次啟停、30 次聲道切換。量測 RAM、CPU、耗電、溫度、網路、buffer backlog；低階裝置與主力 Samsung 至少各一台。熱降頻時降級／提示，不硬跑造成隊列失控。無真機不填實測數值。

## 13. 單一里程碑（取代舊版兩套定義）

M0 — 工程骨架
建立可建置 Compose APP、DI、服務與狀態骨架、README、CI、設定初版。交付 debug APK；assembleDebug、單元測試與 lint。若 repository／CI 無權限，先交付本機檔案，不假造 commit 或 Actions 結果。

M1 — 來源與混音 POC（完整功能阻擋門檻）
建立 owned test source、MediaProjection 授權與 AudioRecord、PCM 電平、來源診斷；建立 StereoMixer 與可預先生成的 TTS fixture，證明三模式、0% 原聲仍送 ASR 分支、聲道隔離與 limiter。fixture 不是實際 TTS provider 成功。
測 YouTube、指定 Zoom／Meet 版本、耳機、Android 版本；產出 CAPTURE_COMPATIBILITY 與 SOURCE_INTEGRATION_DECISIONS。每項分 AVAILABLE／UNSUPPORTED／UNKNOWN／NOT_TESTED_ON_DEVICE。
Gate：可控測試來源與 mixer 通過，可繼續通用管線；目標外部來源未解決則相關整合保持 blocked，不能宣稱使用者全部功能完成。

M2 — 字幕端到端
至少一個真實 ASR 與 MT，en/id → zh-TW；owned file 跑端到端；partial/final、去重、取消、401/429/斷線提示。無 Key 可交付 mock 及測試，但 M2 真實 provider 驗收未通過。

M3 — 真實 TTS 與完整音訊控制
接 Android TTS，實作 PCM_TTS、格式處理、單一 mixer、mode、gain、左右交換、bounded queue、路由與斷線暫停、防回灌。證明真實翻譯語音的 stereo 隔離，不以 fixture 代替。

M4 — 小圈圈、背景與字幕外觀
完成 foreground service、通知、小圈圈、浮動字幕、獨立背景 alpha、描邊、調整／閱讀模式、設定保存、權限拒絕降級。觀看外部 APP 時浮窗與翻譯正常；需要實測，而非只在本 APP 預覽。

M5 — 來源整合、引擎與恢復
完成已獲授權且可實現的指定來源接入；如仍 blocked 清楚報告原因與替代交付，完整目標不標完成。完成 Key 管理、provider 切換與模型管理；至少驗證一條實際離線路徑，其他語言明示未支援。任何付費／授權依賴列入 handoff。

M6 — 穩定性與交付
30 分鐘、背景／鎖屏、耳機與藍牙、斷網、資源洩漏、隱私、浮窗可讀性測試；修正問題、產出 APK、來源矩陣、測試報告與使用者說明。完整功能只在實測支援組合標為 PASS。

## 14. 驗收測試

T01：權限拒絕／取消／撤銷、旋轉、Activity 移除、程序終止、重啟後不自動錄音。
T02：owned source 有效 PCM、來源時間戳、ASR 分流、原聲靜音仍持續輸入。
T03：外部擷取有效 PCM；安靜／暫停顯示 NO_SIGNAL，未知原因不假判 unsupported。
T04：YouTube／會議來源按版本矩陣實測；未取得音訊不產生虛假字幕，不偷開麥克風。
T05：外部原聲不可控時禁用原聲相關功能，從不改系統媒體音量假裝成功。
T06：SPLIT 的軟體 PCM 左右隔離、左右交換、TRANSLATION_ONLY 兩耳同譯音、MIX limiter 不削波。
T07：gT=0 不影響 ASR／字幕；跨模式保存增益一致，快速切换 30 次無舊句重播。
T08：真實 TTS 進 mixer；內部擷取無譯音回灌；麥克風＋耳機與喇叭風險分開測。
T09：耳機斷線預設停本 APP 音訊、字幕保留；單聲道路由正確降級並提示。
T10：停止、換來源、換語言後舊網路結果不播放；20 次啟停資源釋放。
T11：浮窗移動、吸附、旋轉、靠邊、縮放、不攔截窗外觸控；拒絕／撤銷權限可降級。
T12：背景 alpha=0/0.5/1 时文字與描邊 alpha 不變；白底、黑底、快速變動影片上可讀。
T13：繁中、英、印尼文字描邊與填色 layout 一致；多行、放大字體、原文譯文不截斷。
T14：外觀與位置重啟保留；穿透模式仍可從小圈圈／通知恢復；暫時隱藏不停止翻譯。
T15：401、429、5xx、timeout、斷網、模型不存在、空間不足、下載中斷，無靜默假成功。
T16：30 分鐘背景運作與鎖屏，報告版本對 MediaProjection 的實際停止行為及恢復授權。
T17：Key 不在 Git、log、診斷、backup 明文；取消後 cache 檔刪除；歷史 opt-in 有效。
T18：SRT 時間碼對齊、排序、UTF-8、partial 不重複；费用估算與實際 usage 區分。
T19：完整來源模式必須證明外部無額外直出原聲，不能只靠混音器 PCM 測試。
T20：真實會議情境對音訊焦點、麥克風競用、耳機路由、額外 participant 與授權條件實測。

測試分 unit（DSP、queue、狀態、去重）、instrumentation（服務、UI、權限可自動部分）、真機（第三方來源、藍牙、鎖屏、音訊路由）。不寫只重複實作的形式測試；DSP 採可量測波形與能量、事件測試採明確停止條件。平台／第三方限制不可只靠 emulator 結論。

報告每項列 PASS／FAIL／BLOCKED／NOT_TESTED_ON_DEVICE，附證據。無裝置必須留待驗證，不能把缺測列 PASS。

## 15. 交付文件、CI 與建置

交付 app 原始碼、Gradle wrapper 與版本鎖定、README、PROJECT_HANDOFF；debug APK 位於 app/build/outputs/apk/debug，對外另複製到 deliverables 並附 SHA-256。

必備 docs：ARCHITECTURE.md、ANDROID_LIMITATIONS.md、SOURCE_INTEGRATION_DECISIONS.md、CAPTURE_COMPATIBILITY.md、PROVIDER_MATRIX.md、OVERLAY_AND_SUBTITLE_SPEC.md、AUDIO_MIXER_SPEC.md、TEST_REPORT.md、PRIVACY_AND_KEYS.md。

來源矩陣欄位：裝置／OS／來源版本／日期／來源模式／耳機與實際路由／授權／有效 PCM／外部直出／靜音是否持續輸入／控制能力／結果／證據。音訊 usage 欄位不明時填 UNKNOWN，不猜測。

CI 可用時 GitHub Actions：assembleDebug、testDebugUnitTest、lint；artifact retention 建議 3 天。instrumentation 僅在有 emulator／device 環境時執行並註明範圍。

標準命令：./gradlew assembleDebug；./gradlew testDebugUnitTest；./gradlew lintDebug；有設備時 ./gradlew connectedDebugAndroidTest。依實際 variant 調整並寫 README。

PROJECT_HANDOFF 記錄最後實際 commit（有 Git 才填）、完成／未完成、測試、APK 位置、阻擋條件、下一步與來源可用範圍。禁止把尚未驗證來源寫成支援。

## 16. 可直接交給 AI 的首次任務

請完整閱讀本 V2.0 規格及 repository 指令，只先執行 M0、M1。建立 Android 10+ Kotlin／Compose 專案、合規 foreground service、MediaProjection 音訊授權、AudioRecord、PCM 電平與診斷，以及合法 owned test source。用 stereo PCM mixer 驗證 SPLIT／TRANSLATION_ONLY／MIX、gain ramp、limiter、左右交換、原聲靜音不影響 ASR 分支。外部來源不可控時禁用原聲相關功能，不調系統媒體音量。產出 APK、適當單元測試、lint 結果、README、來源矩陣模板、整合決策與 PROJECT_HANDOFF。若無真機／來源授權，不宣稱 YouTube／Zoom／Meet 成功；若無 Key，不宣稱已完成真實翻譯。後續依 M2–M6 推進，不跳過來源接入阻擋條件。

## 17. 官方文件與查核要求

以下為開發前查核入口，不代表本規格已在線核對所有最新規則：
- https://developer.android.com/media/platform/av-capture
- https://developer.android.com/media/grow/media-projection
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/reference/android/speech/tts/TextToSpeech
- https://developer.android.com/reference/android/media/AudioTrack
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams
- https://developer.android.com/media/optimize/audio-focus

開發者需按選定 targetSdk／OS 與來源服務 SDK 查核官方文件、條款與版本，將查核日期與差異寫入 ANDROID_LIMITATIONS／SOURCE_INTEGRATION_DECISIONS。來源成功與背景持續運作都以真機證據為準。
