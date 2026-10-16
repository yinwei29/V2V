# 測試報告 · 0.6.1-speech-flow

新增4項播報分句回歸測試：英／印尼短句完整合成、長段落保持完整詞與內容、中文日文原策略保持、小數及超長詞／遞迴分段保持有界且無損。程式不記錄原始翻譯內容或Key，原始使用者Log不提交。本機全主程式及所有測試來源直接編譯成功，61項不需網路的JUnit全部通過，輸出見evidence/speech-flow/；完整APK／所有JUnit／lint以本版CircleCI及Artifacts驗證資料為準。實體手機試聽仍待驗收。

---

# 測試報告 · 0.6.0-languages

2026-10-16。本機使用快取Kotlin／Compose compiler直接編譯所有主程式及測試程式成功；57項不需網路JUnit通過（包含恢復、設定、歷史、DSP等）。完整Gradle建置／所有網路測試／Android lint以本版CircleCI為準，CI Artifacts提供實際verification.json、ci-build.log與APK checksum。本機Gradle未成功啟動額外網路權限流程，不沿用舊版本測試數或APK當作本版驗證。

新增範圍：503及timeout可恢復；MT失敗保留原文；400／Key／429不進持續管線重試；連續3段停止及取消／新session隔離；模型路徑、thinking及healthcheck一致；來源／目的保存；15種目標指令、中文ASR ISO提示、auto僅來源；離線聲線選擇；舊紀錄缺targetLanguage與新語言匯出相容。仍無Google真實Key及Android裝置驗收。

---

# 測試報告 · 0.5.2-gemini-fix

2026-10-15。本機assembleDebug / testDebugUnitTest / lintDebug通過，79項測試，0 failure/error/skipped；完整驗證3m 23s，lint0 errors/fatal、10 warnings。原始輸出及JUnit／lint見evidence/gemini-400/。本機checksum：`a7d6b98c25f67647ac6a1c69b63447e2b0033dfd59699b4b04f700af23418f99  V2V-0.5.2-gemini-fix-debug.apk`。

新增8項：獨立Google discovery enum快照驗證實際送出的responseFormat.text.mimeType與thinkingLevel，並確認舊值application/json及low不在對應REST列舉；HTTP400 INVALID_ARGUMENT分類、Google ErrorInfo.API_KEY_INVALID優先辨認且不漏正文；超過64KiB失敗body丟棄並保留HTTP狀態；取消涵蓋失敗body讀取；其他Key限制／帳務／服務／專案／額度分類；任意錯誤message及未知reason不能變成Log字串。原有71項完整回歸，包括實際DataStore持久化、Gemini與OpenAI、語音與紀錄。

新Log已CLOUD_ENABLED=1，ASR首個請求400；不同於第一份Log的未啟用。核對官方機器可讀定義後發現前版REST列舉值有誤並修正。沒有使用真實Key或Google生成服務，不能宣稱使用者帳號可用性及實際翻譯已驗收。首次真機測試仍須更新此版並啟用翻譯；若仍失敗，Log的PROVIDER_REASON有安全分類可供排查。記住勾選及允許截圖已包括在此版。

---

# 測試報告 · 0.5.1-start-fix

2026-10-15。本機 assembleDebug / testDebugUnitTest / lintDebug 通過，71項測試，0 failure/error/skipped；完整驗證3m 24s，lint0 errors/fatal、10 warnings。原始輸出、JUnit與lint見evidence/start-fix/；前版結果保留於下方。APK checksum：`ead9947df54058af86b665d78fbf42353539f0b3617c64732b05832274697332  V2V-0.5.1-start-fix-debug.apk`。

新增2項真實PreferenceDataStore檔案測試：首次預設未授權，勾選後結束scope並重開仍保留，切換Gemini／OpenAI不重設；手動取消後再次重開仍為false。原有69項完整回歸。編譯後MainActivity bytecode不再呼叫Window.addFlags，來源中沒有FLAG_SECURE。截圖及Key欄位遮蔽的實際外觀仍需Android手機驗證；沒有宣稱做過裝置截圖測試。

使用者這份Log最新Gemini工作階段CLOUD_ENABLED=0，沒有HTTP請求；較早的成功翻譯屬舊run，不能當Gemini成功證據。本次修正啟動與授權保存，沒有證據需要更換Gemini API／Key。真實Gemini雲端品質及Key可用性仍待啟用後手機測試。詳見CONSENT_AND_SCREENSHOTS.md。

---

# 測試報告 · 0.5.0-gemini

2026-10-15。本機 assembleDebug / testDebugUnitTest / lintDebug 通過，69 項測試，0 failure/error/skipped；最後完整驗證 2m 57s。lint 0 errors/fatal，10 warnings。原始輸出、JUnit及lint見 evidence/gemini/；前版報告與證據保留於下方與 evidence/studio/。本版 CircleCI 請以 main 的本次提交狀態為準。

新增16項Gemini測試：WAV解碼與原始PCM一致、固定模型路徑與header驗證、台灣繁中與結構化JSON、用量缺失未知、無語音空字串、空譯文錯誤、截斷／安全阻擋／錯誤格式／長度限制、thought不當字幕、拒絕工具輸出、HTTP 400/401/403/404不重試且隱藏body、429僅再試一次、healthcheck不傳音訊或生成、退避與body讀取可取消、完整pipeline回寫revision2及呼叫播報callback、Log不含Key／字幕。原有53項完整回歸，包括OpenAI原路徑與取消、DSP、紀錄、語音等。

沒有真實Gemini Key、Google生成呼叫或Android真機／emulator。模擬成功只證明本機請求／解析與管線邏輯，不證明帳號可用、模型品質、延遲、實際播報、Keystore或UI操作。手機測試按 GEMINI_SETUP.md 操作，既有裝置限制仍適用。

本機交付 checksum：`16bedb4654f25a6d59392835a8b25bd3208fb4649f894c174a05949d1e42fbf1  V2V-0.5.0-gemini-debug.apk`。CircleCI產物各用自己的checksum，避免混淆不同建置／簽章。

---

# 測試報告 · 0.4.0-studio

2026-10-15。OpenJDK17 / Gradle8.13 / SDK35。本機assembleDebug、testDebugUnitTest及lintDebug已通過；53項測試，0 failure/error/skipped。最後完整build/test/lint為2m15s；lint 0 errors, 10 warnings。最後輸出與JUnit、lint見 evidence/studio/。完整CI以本版main commit的CircleCI狀態為準，不以舊版#4冒充本版結果。

## 新增回歸

- HistoryStore6項：final資料持久化與重新載入、revision去重／拒絕回退、關閉保存／partial／空譯文不落盤、清除watermark拒絕晚到舊完成段而接受新段、容量上限移除最舊、損壞檔不覆寫、UTF-8與時區日期／原文譯文匯出（多個斷言合併6方法）。
- SpeechLoudness4項：偏小PCM最多4×補強、近靜音不增強、關閉或已足夠響亮時原樣保留、含大峰值語句與400%混音不溢位／不反轉正負號。
- 其餘43項涵蓋DSP、語音格式、來源能力、分段、有限queue、provider取消／401／429、日誌隱私。新測試使用合成語音／虛構文字，沒有真實會議內容、API Key或使用者log。
- 修正舊provider overload測試時序：舊測試收到首個丟frame後立即要求整秒，可能在剩餘處理完成前誤判。新fixture以NO_RESPONSE保持供應商忙碌，等Flow超載事件，驗證丟棄計數上限及沒有第二請求，去掉任意startup sleep；專用ChunkMailbox測試另驗證精確容量。

## 介面與裝置驗收

四頁籤、固定紀錄工具列、搜尋／原文切換、清除確認、四組動作圖示、兩組浮窗stepper、48dp觸控目標與窄螢幕捲動皆已實作；Compose／View能編譯，lint無阻擋錯誤。沒有可操作Android裝置／emulator，不宣稱已做實際視覺、TalkBack、SAF、摺疊／放大字體／旋轉或OEM浮窗驗證。

錄音來源／YouTube／Zoom／Meet、防回灌、實際2×語速、實際音壓、耳機路由、鎖屏與30分鐘運作仍NOT_TESTED_ON_DEVICE。響度數值測量為合成PCM，不是耳機音壓；最高400%與響度增強不能繞過系統音量／安全限制，外部來源原聲仍不可單獨控制。

測試者：更新同CircleCI簽章APK → 開新翻譯 → 紀錄頁確認保存 → 重開App確認仍在 → 匯出TXT核對繁中與原文 → 清除後確認舊片段不回來 → 背景浮窗測播報／字幕stepper與捲動不重置 → 低音量起逐步測響度。細節見 STUDIO_UI_AND_HISTORY.md。

APK debug v2簽章已通過（1 signer）。本機SHA-256：`4a5817bd347504964af7329ae32ba35ec1ca0bc812b2b343e9abd8c68aa28c2f  V2V-0.4.0-studio-debug.apk`。
