# V2V · 0.8.1-install · 2026-11-01

使用者回報「未安裝應用程式」。已驗證#17的0.7.1與#19/#20的0.8個別APK簽章有效，但憑證不同；#19快取缺失後換簽，不能直接覆蓋舊套件。詳見 [安裝修復](docs/INSTALLATION.md)，舊私鑰不能從公開憑證恢復，沒有自動移除舊版或搬移資料。

debug現在為V2V Preview /com.yinwei.v2v.preview，Code15/0.8.1-install，與舊V2V並存；release applicationId與namespace不變，翻譯核心仍0.8。Preview需重新填Key／授權，舊歷史保留於舊App。CI先比對固定公開憑證指紋，缺key／換簽就停止；可由保護變數恢復原key，但永久加密備份尚未設定，不能以快取當長期保證。簽章私鑰不進Git／附件／Log／聊天。Ju未修改。

[CircleCI #21](https://circleci.com/gh/yinwei29/V2V/21)來源49597b0cd5cebbd43efa9a1d53ce6400bb773caa通過assembleDebug、123/123 JVM（0failed/errors/skipped）、lint0 Error/Fatal/219 Warning；四語系204標籤與shell/Python檢查通過。SDK apksigner/aapt驗證實際APK簽章、Preview套件／版本、min29/target35及啟動入口；實際下載APK SHA256及ZIP完整性再次通過。詳見 [metadata](docs/validation/circleci-21-installation.json)。實機安裝及真實模型品質尚未驗收。

English: Installation recovery uses a parallel Preview debug package, preserving legacy data. Actual matching-source APK signature/package/version and all123 JVM tests verified; no physical device install claim. Runtime unchanged, no Ju changes or Play release. Persistent signing backup still needs secure CircleCI administration; fail-closed pinning prevents silently publishing rotated identities. Later evidence-only documentation does not change app/build/CI/scripts.

---

# V2V · 0.8.0-core · 2026-11-01

僅V2V翻譯核心。生產管線改為600ms停頓斷句／連續語音最多4秒；移除Gemini固定4秒最低等待。OpenAI MT及Gemini combined request加入最近兩段成功原文（每段最多1,200 UTF-16 units）參考，只翻譯目前輸入，失敗／漏段／長停頓／新session清除；不用前次譯文強化錯誤。原有有界佇列、取消、供應商、單一字幕／譯音結果與資料格式保留。新增數值PIPELINE_TIMING診斷，不保存原文／音訊／Key。版本Code14，Ju沒有修改。

[CircleCI #19](https://circleci.com/gh/yinwei29/V2V/19)來源68455308d8d923a5f91081cfb09ba2a7b0a0f86b：assembleDebug成功、123/123 JVM tests（0 failed/errors/skipped），lint0 Error/Fatal、219 Warning。四語系204標籤驗證通過；下載APK校驗碼及ZIP完整性核對通過，APK含新版核心標記。詳見 [核心調整／操作／限制](docs/TRANSLATION_CORE.md) 與 [驗證metadata](docs/validation/circleci-19-translation-core.json)。後續只新增本交接與驗證文件，app/Gradle/CI未變。

English: V2V0.8.0-core reduces collection delay and adds bounded source continuity; matching-source CircleCI passes123 tests/build/lint with219 warnings. This is segmented final translation, not streaming or a4s end-to-end guarantee. More requests/context may increase cost. No live API accuracy/throughput or Android listening acceptance: test the same authorized audio before/after and export technical logs. Offline ASR/MT, streaming and device/source acceptance remain open. No Ju changes or store release.

---

# V2V · 0.7.1-srt · 2026-11-01

本輪按照V2V自己的規格§9/T18完成工作階段SRT匯出。紀錄→匯出→SRT→選工作階段→純譯文或雙語；原有TXT及歷史資料保留。使用來源相對時間碼、最新修訂、UTF-8與無效紀錄略過提示；檔案選擇前的快照保留於記憶體ViewModel，非Bundle。詳見 [SRT操作及限制](docs/SRT_EXPORT.md)。版本Code13，沒有修改Ju。

[CircleCI #17](https://circleci.com/gh/yinwei29/V2V/17)驗證來源ec9f121a75cb5635007274897957d93affaf661a：assembleDebug成功，112/112 JVM tests（0 failures/errors/skipped），lint 0 Error/Fatal、219 Warning；四語系204標籤檢查通過。警告仍需後續處理。APK為V2V-0.7.1-srt-debug.apk，未上架；真機SAF／旋轉畫面／播放器相容性及真實模型測試未執行。離線ASR/MT、串流及來源／裝置驗收未宣告完成。

English: V2V-only SRT export, matching-source CircleCI build/unit/lint passed (112 tests; lint 219 warnings, no errors). Existing TXT/history/audio/provider behavior retained. Device export/player and live API acceptance remain pending. See linked design/operation/rollback and validation metadata.

---

# V2V · 2026-11-01 功能歸屬核對

依使用者澄清：V2V為手機播放音訊及音訊檔轉錄／即時翻譯，Ju／LinguaBridge為獨立的對話／文件翻譯專案。以 [PRODUCT_SCOPE](docs/PRODUCT_SCOPE.md) 分離；本repo自己的Android音訊V2規格與M0–M6保留，不改套Ju混合規劃。外部會議聲音擷取不等於Ju AI會議紀要；音訊檔匯入不等於Office/PDF；系統離線TTS不等於Ju離線ASR/MT。PR#1沒有引入Ju runtime。此次只修正文檔、不改APP、不移植程式／keys、不新增功能驗收聲明。

English: Scope correction only. V2V and Ju retain independent requirements, implementations and evidence; see product scope audit.

---

# V2V · 2026-10-31 CircleCI #11→#12 回歸修正

本repo是yinwei29/V2V（com.yinwei.v2v／Kotlin Compose），與yinwei29/Ju／LinguaBridge獨立維護；不可混用來源、分支、PR、版本、APK及驗證數量。repo AGENTS.md已明訂。

CircleCI #11在5cf817e為106 tests／1 timeout：provider blocked測試仍以舊小佇列為前提，只送四段短音訊，無法使目前30秒／12段FIFO超載。改成14段額外句子並每frame讓出1ms消化ingress，保留丟棄上下界／一次request／finally cancel，不擴timeout、不skip。正式engine／FIFO不變，README版本與APK檔名更新為現有0.7.0-global。

[CircleCI #12](https://circleci.com/gh/yinwei29/V2V/12) source8e7e82d通過build、106/106 unit（0failed/errors/skipped）、lint（0Error/Fatal，208Warning）。實際下載APK33,179,757 bytes與checksum核對通過；artifact-only，未正式上架／未真機／未呼叫真實API。後續文件不變更app/與Gradle。詳見 [回歸報告](docs/CIRCLECI_11_BACKLOG_REGRESSION.md) 與 [稽核metadata](docs/validation/circleci-12-backlog-regression.json)。舊失敗及產品功能限制保留於下文。

English: V2V and Ju are distinct projects. V2V #12 validates only the V2V source8e7e82d. The fix updates an obsolete overload fixture without changing production behavior; actual APK digest verified. Device/API quality and lint warnings remain outside this pass.

---

# PROJECT_HANDOFF · 0.7.0-global

本版處理使用者Gemini漏句、播報停頓、離線語音下載入口、多國語言介面，以及最新字幕範圍要求。Gemini一次音訊請求取得原文與譯文、短段合併4秒、等待FIFO最多30秒／12段，仍有界並保留丟棄計數；TTS裁切頭尾靜音但保留句內停頓。介面繁中／英文／印尼文／日文與跟隨系統，新增語音資料安裝入口。字幕6–30sp／2–10行並保存。詳見 docs/GLOBAL_AND_AUDIO_FLOW.md；原始使用者Log不提交。

APK由CircleCI本版Artifacts交付。裝置仍須安裝目的語言離線語音；Samsung／Google設定選單不同，App提供安裝與語音設定兩個入口及說明。不需要新增API Key或CircleCI手動設定。持續慢於即時的供應商仍可能延遲或漏段，需新版真機Log驗證。部分後端技術錯誤訊息仍保留原診斷文字。

---

# PROJECT_HANDOFF · 0.6.1-speech-flow

修正英文／印尼文被空格切成短詞而反覆合成的問題。目的語言中文及日文維持原策略，其餘語言以句子與完整詞優先、最多120 code points。過長PCM遞迴縮短及有界佇列保留。詳見 docs/SPEECH_FLOW.md；真機流暢度仍須使用者試聽。本版APK由CircleCI提供，不沿用舊APK。無新增手動設定要求。

---

# PROJECT_HANDOFF · 0.6.0-languages

使用者最新0.5.2Log為Gemini503後重試SocketTimeout，ASR錯誤後管線永久停止。新增暫時失敗恢復及模型選擇，同時按使用者要求完成來源／目的多語言（15種＋來源auto）、目的語言離線播報／紀錄匯出、手機播放音訊不限App名稱。既有勾一次記住及允許截圖保留。

詳見docs/LANGUAGES_AND_RECOVERY.md。Gemini新預設3.5 Flash-Lite／MINIMAL，另可選3.8 Flash／LOW；模型與語言保存DataStore並在服務開始時固定。服務503／5xx／408／網路故障可恢復，HTTP重試界限保留，管線對後續片段退避2／4秒，連續3段失敗明示暫停，不無限排隊、不切其他供應商。MT失敗保留原文；成功時清除故障。

AppLanguage固定15種語言，來源auto、目標不可auto。OpenAI中文hint映射zh，provider翻譯指令及TranscriptSegment傳目標；歷史JSON新增targetLanguage，舊資料缺欄預設繁中。TTS只用目的語言離線聲線，優先同地區、中文不跨繁簡；缺語音提示保留字幕。手機播放音訊沒有App UID白名單，仍受Android MEDIA/GAME/UNKNOWN及capture policy約束，不能保證通話／受保護內容，沒有麥克風fallback。

驗證：本機用快取Kotlin2.1.20／Compose編譯器直接編譯全主程式及全部測試程式已成功，執行57項不需網路的JUnit均通過。本機Gradle額外網路權限流程未成功啟動，不把舊build輸出當本版結果。完整Android assembleDebug、所有unit tests及lint由CircleCI完成，以本版commit狀態及Artifacts的verification.json／ci-build.log為準。沒有真實Gemini Key或Android裝置，語言品質、實際API、離線語音及OEM擷取待手機測試。沒有虛構本機新版APK或checksum。

交付：CircleCI產生V2V-0.6.0-languages-debug.apk／SHA256SUMS／verification.json。移除repo追蹤的舊checksum，避免與CI環境混用，歷史報告仍保留舊證據。Key不需重填或放CI，手機需選來源／目的及有離線聲音的目的語言；系統擷取同意照Android要求。

Git base本機98e0bba2d937b39bfe261dd82304ab614af50166，遠端68ffdcb109343aebdb6777314ff1adb186df05d0，tree同為063be9e80bc890aa68853d37fa9cf2cc3df0ddb9。用遠端parent加本機同tree建立commit、expected SHA推main，勿強推舊本機history。CI狀態另記/workspace/tooling/languages-github-upload.json；原始使用者Log不提交Git。
