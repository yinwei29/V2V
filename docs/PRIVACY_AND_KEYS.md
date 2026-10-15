# 隱私、Key 與診斷

0.5 支援 OpenAI 與 Google Gemini 個人 BYOK。兩家分開使用 ciphertext 檔案與 Keystore alias；既有 OpenAI 檔名與 alias 不變。依使用者要求，音訊授權存DataStore：首次預設false，勾選一次後重開／切換引擎仍保留，手動取消即保存false。文案明示音訊與文字會送目前所選雲端引擎，工作階段固定該引擎；不在開App時自動開始擷取。Key 只由使用者在 App 輸入，Android Keystore 生成 AES 金鑰，AES-GCM ciphertext + IV 存 noBackupFilesDir；不把普通 API Key 字串當 Keystore entry。每次加密使用新 IV。Key 不寫 log、savedInstanceState、Git、CI、備份或診斷。Key 表單用 password transformation。0.5.1依使用者要求取消Activity的FLAG_SECURE，允許截圖與app-switcher preview；不回顯已儲存Key，輸入仍遮蔽。

健康檢查僅以 Authorization（OpenAI）或 x-goog-api-key（Gemini）送至各自固定 HTTPS /models，不傳音訊；能查詢模型不代表音訊／翻譯功能或扣款已驗收。Key 可更新／移除；無法解密或還原後無有效 AES key，刪除 ciphertext 並要求重新輸入。實際 Keystore / 換機 / 系統還原需真機測試。

只有使用者勾選「有權處理且同意傳送」並開始來源，才會把 audio chunks 及辨識文字送使用者選定的 OpenAI 或 Google Gemini。測試 tone 永不送雲端。未勾選時，其他App與授權WAV無法啟動翻譯；純測試音訊仍可診斷且不送雲端。擷取失敗不改用麥克風、不切未配置供應商或離線。固定 HTTPS，不允許 redirect 或 cleartext（JVM unit fixture 的 local MockWebServer 除外，不是 App 流程）。

來源 PCM / ASR uploads 全在記憶體，不保存來源錄音或完整 HTTP response；原文／譯文依下述本機紀錄設定保存；即時字幕最近200段保留在工作階段記憶體。0.4依使用者要求，另將完成原文／譯文保存在手機私有noBackup目錄，最多1,000段／4MB，可關閉自動保存、清除或經SAF匯出TXT；不自動上傳、不進技術Log，沒有Room或SRT。App allowBackup=false + fullBackupContent=false，Android12+ dataExtractionRules 排除 cloud / device transfer 所有資料域。卸載移除 Key、log、設定。

技術 Log 不含音訊／字幕／Key／檔案 URI／response body。0.5.2的Gemini錯誤body只在記憶體讀取最多64KiB，僅解析已知status及ErrorInfo.reason為App自有enum，Log只保存該PROVIDER_REASON enum，不記錄或顯示Google message、metadata、任意body字串。OpenAI仍不讀取失敗body。只保存 enum、計數、RMS、權限與路由 type、錯誤 class，加上裝置廠牌型號／SDK／App版本；不是裝置識別碼。DataStore 記住記錄開關，可清除，最多3×512KiB；啟用後在 noBackupFilesDir 輪替，離開 App／程序重啟後已寫事件仍可匯出。沒有自動傳送。SAF 匯出與 FileProvider 分享必須由使用者按鈕操作，系統 chooser 由使用者選對象；分享快取最多3份，僅暴露 cache/diagnostics/。詳見 DIAGNOSTIC_LOGS。

公眾發佈或後端代理、provider 資料保留／帳號條款尚待確認，本版不宣稱已完成公開產品合規。不要把真實 Key 貼聊天或放 CircleCI。


0.3 語音預覽：Android TTS 僅選引擎回報不需網路的台灣中文聲線。合成API需建立私有cache的tts-*.wav；檔案不分享、不備份，完成／取消即刪；若程序強制終止，於下次啟動刪除殘留。這是短暫譯音合成檔，不是來源錄音或歷史。浮窗只顯示本次字幕，不擷取螢幕內容；拒絕上層顯示權限仍可使用主畫面字幕。停止服務移除視窗。
