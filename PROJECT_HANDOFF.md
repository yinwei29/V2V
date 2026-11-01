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
