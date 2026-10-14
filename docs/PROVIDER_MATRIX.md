# Provider 與語言

| 階段 | M1 實作 | 語言 / 輸入 | 狀態 | 成本 / 資料 |
|---|---|---|---|---|
| ASR | AsrProbe 只計樣本、RMS | 48k mono PCM16；無語言識別 | 未接 provider，不是 mock ASR 成功 | 無網路傳送 |
| MT | 無 | en/id → zh-TW 目標，未驗證 | M2 BLOCKED 尚未實作／無 Key | 無費率估算 |
| TTS | 660Hz fixture | 非語音、沒有 supportedLanguages | M3 未實作 | 原創音調 |
| 離線 | 無模型 | 所有語言未支援 | 不提供自動離線回退 | 無下載 |

M2 需選一組實際 ASR+MT、記錄模型 ID/API 版本/格式/語言/價格來源與查核日期，以真實 en/id 音檔完成繁中驗收。無 Key 不可標 M2 PASS。
不要求使用者把 Key 寫入 CircleCI，本版 APK 不含 Key。BYOK 允許性與公開發佈 backend proxy 為後續決策。
