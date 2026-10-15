# Provider 矩陣 · 2026-10-15

| 階段 | 實作 | 語言 / 格式 | 能力與限制 | 實際驗收 |
|---|---|---|---|---|
| ASR | OpenAI POST /v1/audio/transcriptions，whisper-1 | 48k mono PCM16 WAV；en / id / auto，<=10秒片段 | final only，無 partial、confidence、diarization 或逐字時間戳；時間由輸入樣本推算 | BLOCKED_NO_KEY / NOT_TESTED_ON_DEVICE |
| MT | POST /v1/chat/completions，gpt-4o-mini | 指定台灣繁體中文 zh-TW | 不補事實／保留術語指令，獨立片段；無術語表與跨段上下文 UI；截斷回應不當成功 | BLOCKED_NO_KEY / NOT_TESTED_ON_DEVICE |
| TTS | 尚無真實 provider | test fixture 660Hz 只用於 TEST_FILE | 不是 TTS，其他來源沒有模擬語音輸出 | M3 未完成 |
| 離線 | 無 | 未支援 | 不自動回退或偷偷改供應商 | M5 未完成 |

官方文件已於 2026-10-15 線上讀取：
- https://platform.openai.com/docs/guides/speech-to-text
- https://platform.openai.com/docs/api-reference/chat/create

API Key 由使用者自有帳號在裝置輸入，以 Android Keystore AES-GCM 保護。健康檢查只查 whisper-1 / gpt-4o-mini 的 model metadata；不代表音訊辨識、翻譯品質、計費及指定語言已驗收。測試不使用真實 Key，不呼叫帳號雲端。

個人 BYOK 為本版使用範圍；尚未取得公開發佈的條款／client key 使用允許性結論，公開產品需另查供應商條款與後端代理，不可把自用 Key 打入 APK。價格來源／費率尚未獨立核對，因此不顯示金額估算。只顯示供應商回傳的 MT token usage，不是帳單；ASR 時長不被稱作已回報帳單 usage。

HTTPS 固定 endpoint、15秒 connect / 30秒 read / 40秒 call timeout、不跟 redirect、不自動重試連線。429 / 5xx 最多額外重試一次，Retry-After 秒數 clamp 1–8 秒。401 / 403 / 404 明示；錯誤 body 不記 log。取消含正在讀 response body 的 Call；舊 session 無法回寫目前字幕。

分段：能量門檻 0.005 RMS、600ms 安靜斷句、連續發言最多 10秒；不是神經 VAD，低音量／雜音準確率需實測。音訊 frame queue 上限 250×20ms；chunk 無待處理 backlog，provider 忙碌時明示跳過並計時，尾段正常結束可等待處理。跳過保留來源時間缺口，不壓縮時間戳。字幕最近 200 段僅在記憶體保存。
