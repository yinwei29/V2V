# 隱私與 Key

M1 無 INTERNET permission、網路 SDK、錄音落盤、逐字稿、analytics 或原文 logs。PCM 僅在記憶體內同步量測，停止釋放；通知顯示工作階段。
只在 DataStore 保存 mode / gains / swap / schema version。allowBackup=false，Android 12+ dataExtractionRules 排除 cloud / device transfer 所有資料域；不加入秘密檔案。M1 不提供 API Key 欄位，無測試 Key 打入 APK 或 CI。

MediaProjection 音訊需 RECORD_AUDIO runtime permission，這不等於啟用麥克風。本專案沒有 microphone AudioRecord source，不在擷取失敗時偷換來源。
雲端模式要在 M2 加音訊與文字傳送同意、BYOK Keystore AES-GCM 加密 storage、排除 backup、移除與測連線。Room 歷史 opt-in、UTF-8 TXT/SRT 匯出、保留期限及 TTS cache 清理尚未實作。
