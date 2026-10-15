V2V 0.2.0-m2-preview · 技術 Log 與 BYOK 字幕工程預覽

新增：技術事件記錄開關、啟停/權限/電平/路由/錯誤 class、JSONL 容量輪替、匯出、系統分享與清除。無錄音、字幕或 API Key，不自動上傳。測試完成後可匯出 log 回報開發。

並接入：授權48k mono PCM16 WAV、OpenAI whisper-1分段ASR、gpt-4o-mini繁中字幕、Keystore加密Key、雲端同意、有界佇列、過載與取消處理。沒有真實Key/語言品質/裝置端到端驗收；這是工程預覽，不能宣稱真實翻譯已PASS。

本機APK建置、28項測試、lint通過（0 errors / 4項版本更新提示），debug v2簽章驗證；SHA-256參閱附件。

Android10+。TTS、小圈圈、浮動字幕、離線尚未交付；第三方來源未驗證。不同debug簽章可能需移除舊版再裝，先匯出既有log。
