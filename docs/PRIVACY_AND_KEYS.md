# 隱私、Key 與診斷

M2 preview 支援個人 BYOK。Key 只由使用者在 App 輸入，Android Keystore 生成 AES 金鑰，AES-GCM ciphertext + IV 存 noBackupFilesDir；不把普通 API Key 字串當 Keystore entry。每次加密使用新 IV。Key 不寫 log、savedInstanceState、Git、CI、備份或診斷。Key 表單用 password transformation，Activity 設 FLAG_SECURE（包含 app-switcher preview），因此本版無法截取 App 畫面，請以 log 回報。

健康檢查只發送 Authorization 至固定 OpenAI HTTPS /models，不傳音訊；能查詢模型不代表音訊／翻譯功能或扣款已驗收。Key 可更新／移除；無法解密或還原後無有效 AES key，刪除 ciphertext 並要求重新輸入。實際 Keystore / 換機 / 系統還原需真機測試。

只有使用者勾選「有權處理且同意傳送」並開始來源，才會把 audio chunks 及辨識文字送 OpenAI。測試 tone 永不送雲端。不勾選就是播放／診斷；擷取失敗不改用麥克風、不切未配置供應商或離線。固定 HTTPS，不允許 redirect 或 cleartext（JVM unit fixture 的 local MockWebServer 除外，不是 App 流程）。

PCM / ASR uploads 全在記憶體，沒有原始錄音或雲端 payload 落盤；字幕僅最近200段保留在工作階段記憶體，沒有 Room 歷史、TXT/SRT 或持久逐字稿。App allowBackup=false + fullBackupContent=false，Android12+ dataExtractionRules 排除 cloud / device transfer 所有資料域。卸載移除 Key、log、設定。

技術 Log 為新功能，不含音訊／字幕／Key／檔案 URI／response body。只保存 enum、計數、RMS、權限與路由 type、錯誤 class，加上裝置廠牌型號／SDK／App版本；不是裝置識別碼。DataStore 記住記錄開關，可清除，最多3×512KiB；啟用後在 noBackupFilesDir 輪替，離開 App／程序重啟後已寫事件仍可匯出。沒有自動傳送。SAF 匯出與 FileProvider 分享必須由使用者按鈕操作，系統 chooser 由使用者選對象；分享快取最多3份，僅暴露 cache/diagnostics/。詳見 DIAGNOSTIC_LOGS。

公眾發佈或後端代理、provider 資料保留／帳號條款尚待確認，本版不宣稱已完成公開產品合規。不要把真實 Key 貼聊天或放 CircleCI。
