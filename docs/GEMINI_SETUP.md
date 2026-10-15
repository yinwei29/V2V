# Gemini API · 0.5.2-gemini-fix

## 手機設定

1. 在 https://aistudio.google.com/api-keys 建立自己的 Gemini API Key。這是 Google AI Studio／Gemini Developer API，不是 OpenAI Key、Firebase設定或Vertex服務帳戶。
2. 安裝本版 APK，先結束進行中的翻譯，在「設定 → 引擎與金鑰」選 Google Gemini。
3. 輸入 Key，按儲存，再按測試連線。欄位不回顯舊 Key；更新與移除只作用於目前引擎，另一家仍保留。舊 OpenAI Key 不需重填。
4. 返回翻譯頁，選其他 App 音訊或授權 WAV、語言，首次勾選同意傳送給所選雲端引擎（只需一次，會記住），再開始並完成本次系統擷取授權。
5. 確認原文與繁中譯文、播報、浮窗與紀錄。錯誤時結束、匯出技術 Log 回報操作；不要傳 Key。Log 的 SESSION_START.PROVIDER 可分辨 GEMINI／OPENAI。

400會顯示安全分類：INVALID_REQUEST表示參數格式，INVALID_KEY表示Google明確回傳Key錯誤；PROJECT_SETUP_REQUIRED表示專案資格，不一律誤稱Key錯誤。未知回應僅保留HTTP狀態。

只有使用者手機內的 Key 需要手動設定；CircleCI 不需加環境變數、token或Key。測試連線只查詢固定模型資訊，不傳音訊、不呼叫生成；成功不代表生成額度、品質或收費已驗證。400/401檢查 Key 與帳號；403檢查專案權限／地區；404檢查模型資格；429檢查供應商額度、速率或帳務。不自動購買額度或切換供應商。

## 實作

固定 HTTPS generativelanguage.googleapis.com/v1beta，x-goog-api-key header，不把 Key 放 URL，不跟redirect。使用官方仍支援的 generateContent API，模型 gemini-3.8-flash（官方2026-10文件列為stable，支援音訊與結構化輸出），LOW thinking 減少多餘推理；REST responseFormat.text.mimeType為APPLICATION_JSON（不是application/json字串），以官方discovery定義為準。採既有600ms安靜斷句／10秒最長片段，inline base64 WAV，不建立雲端檔案或對話資源。每段先辨識原文，再以原文請求繁中翻譯；字幕更新與語音佇列沿用既有流程。這不是 Gemini Live 串流辨識，不保證速度快於 OpenAI。

JSON schema 要求單一 text 字串，拒絕格式不符、超過12,000字元、空譯文、被阻擋與非STOP回應；辨識空字串視為無語音，不生成假字幕。只使用非thought文字；MT token為供應商回傳值、缺失顯示未知，不是完整帳單或費用估算。HTTP成功body最多1MiB；Gemini失敗body僅於記憶體讀取最多64KiB，僅解析Google status／ErrorInfo.reason白名單分類，正文、message、metadata與未知字串不顯示、不記錄，429/5xx僅再試一次，停止時取消含body讀取／退避中的請求。共用HTTP實作亦回歸原有OpenAI測試。

辨識與翻譯送 Google；播報仍為 Android 離線台灣中文語音，不使用 Gemini 雲端TTS。雲端資料處理由供應商條款／專案層級規範，沒有宣稱零保留；本App不儲存來源錄音，不記錄字幕／Key／HTTP正文至技術Log。完成翻譯依本機紀錄開關保存。

Key以兩家獨立AES-GCM alias及私有noBackup檔案保存；舊OpenAI檔案和alias相容。選擇引擎存DataStore，啟動畫面等載入後才開放操作，開始時將所選引擎傳給服務固定到該session。選錯provider不嘗試另一家的Key，無Key不啟動擷取；音訊授權依使用者要求永久保存，不因切換引擎或重開App而失效；可以手動取消。未勾選不進入實際擷取模式。

## 驗收範圍

本機測試使用虛構文字／合成WAV及MockWebServer，不使用真實Key或Google生成服務。兩家的Keystore操作、選擇持久化、授權文案與真實Gemini辨識／翻譯／延遲／播報需手機實測。0.5.1允許App內截圖，Key欄位仍遮蔽。YouTube／Zoom／Meet能否擷取仍受來源App政策與Android版本限制；加入Gemini不能解除原聲控制與會議擷取限制。

官方依據：
- https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash
- https://ai.google.dev/gemini-api/docs/generate-content/text-generation
- https://ai.google.dev/gemini-api/docs/generate-content/audio
- https://ai.google.dev/gemini-api/docs/generate-content/structured-output
