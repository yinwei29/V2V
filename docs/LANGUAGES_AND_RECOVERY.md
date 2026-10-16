# 多語言、Gemini恢復與手機播放音訊 · 0.6.0-languages

本次收到0.5.2使用者Log：雲端已啟用，Gemini第一個辨識請求503約3.35秒，HTTP層再試一次後SocketTimeoutException約33.7秒，ASR變ERROR，MT未執行。這不是前次400，也不能由503認定Key錯誤；舊管線catch後結束worker而前景擷取仍繼續，造成之後都沒有字幕。原始使用者Log不提交Git。

## 恢復與模型

ProviderFailure明確區分retryable。HTTP408／5xx及IOException可恢復，格式／400／401／403／404／429等需要使用者處理的錯誤不進入持續重試。HTTP內原有429／5xx最多再試一次的界限保留。管線對暫時失敗略過未完成段並計入skippedSamples，第一、二次分別等2及4秒，繼續有界佇列中的後續片段；連續3個失敗片段後明示暫停。成功後清空故障狀態及計數，Log有PROVIDER_RECOVERY／PROVIDER_RECOVERED。取消／換session不允許舊回應改寫新字幕；不重送已完成譯文或補造遺失字幕。

若MT失敗，已發布的原文保留；未完成的譯文不會送TTS／歷史。佇列仍250frames及最多5秒／3待處理段，沒有無限積壓。重試可能增加供應商請求次數，有限策略不保證持續服務故障時仍能翻譯。

設定頁可選gemini-3.5-flash-lite（預設，MINIMAL thinking）及gemini-3.8-flash（LOW）。模型選擇存DataStore，healthcheck及ASR／MT都使用所選模型；開始時固定並記入SESSION_START.GEMINI_MODEL，工作中不切換。相同Google endpoint／Key，不自動切換模型、供應商、雲端TTS或麥克風。官方列為支援音訊與結構化輸出，但本工作環境沒有真實Google Key，不保證帳號配額／品質／延遲已實測。

## 語言

來源及目的共用15種明確語言：zh-TW、zh-CN、en、id、ja、ko、vi、th、ms、es、fr、de、pt、hi、ar；來源額外auto，目的不接受auto。語言僅從固定目錄選擇，不將任意使用者字串插入系統指令。OpenAI ASR中文使用ISO zh提示，Gemini接收明確語言名稱；兩者翻譯提示均使用所選目的語言。預設英文→繁中，兩端選擇一起保存。工作階段固定來源、目的語言，需結束後變更。

TranscriptSegment及HistoryEntry新增targetLanguage，舊JSON紀錄缺欄位時預設zh-TW。新紀錄／字幕／匯出使用實際語言，不把英文譯文標為繁中。Android離線TTS只挑符合目的語言且不需網路的聲線，優先相同地區；繁／簡中文不跨腳本誤用，其他同語言地區可降級。缺離線語音僅提示並保留字幕。實際聲線及各語言品質仍需真機安裝語音包後確認。

## 所有允許擷取的播放App

來源叫「手機播放音訊」，沒有YouTube／Zoom／Meet套件白名單或addMatchingUid；沿用MEDIA／GAME／UNKNOWN，排除自己的UID。這已涵蓋Android允許一般App取得的播放類型，不是只服務特定App。Android會拒絕未允許擷取的內容、通話usage、不同使用者profile等；不能宣稱手機發聲就一定能取得。無可用PCM時UI提示正在播放／來源政策限制，不改用麥克風，不以NO_SIGNAL直接判定永久不支援。

## 驗證與手機步驟

本機採既有Kotlin／Compose compiler直接編譯主程式及測試程式（不啟動Gradle網路daemon），非網路JUnit組已執行；完整Gradle assembleDebug、testDebugUnitTest、lintDebug及APK由CircleCI驗證，請看本版提交的pipeline及Artifacts。不能把0.5.2的79測試或舊APK checksum當作0.6驗證。

更新後選Gemini模型、來源／目的語言；音訊授權沿用已記住設定→開始手機播放音訊→切換任意允許擷取的App→確認字幕／離線目的語言播報→停止、重開後確認設定保留。測原文／譯文歷史與TXT語言標示，使用已安裝離線聲線及缺語音兩種情況；503／逾時時觀察恢復中狀態與Log。截圖仍開放，Key仍遮蔽。外部原聲不可獨立控制、藍牙／鎖屏／長時間仍需真機驗證。

官方來源：
- https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite
- https://ai.google.dev/gemini-api/docs/thinking
- https://ai.google.dev/gemini-api/docs/troubleshooting
- https://developer.android.com/media/platform/av-capture
