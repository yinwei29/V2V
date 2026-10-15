# Provider 矩陣 · 2026-10-15

| 階段 | 實作 | 語言 / 格式 | 能力與限制 | 實際驗收 |
|---|---|---|---|---|
| ASR | OpenAI POST /v1/audio/transcriptions，whisper-1 | 48k mono PCM16 WAV；en / id / auto，<=10秒片段 | final only，無 partial、confidence、diarization 或逐字時間戳；時間由輸入樣本推算 | USER_REPORTED_SUBTITLES；語言品質與受控測試待驗收 |
| MT | POST /v1/chat/completions，gpt-4o-mini | 指定台灣繁體中文 zh-TW | 不補事實／保留術語指令，獨立片段；無術語表與跨段上下文 UI；截斷回應不當成功 | USER_REPORTED_SUBTITLES；語言品質與受控測試待驗收 |
| TTS | Android TextToSpeech.synthesizeToFile + PCM callbacks | 已安裝、非網路 zh-TW voice；PCM16 mono/stereo 8–48kHz → mono48k | 單一 mixer / AudioTrack，語速0.75–2；缺語音降級字幕，格式不支援明示錯誤，不降採樣 | M3 工程預覽；NOT_TESTED_ON_DEVICE |
| 離線 | 無 | 未支援 | 不自動回退或偷偷改供應商 | M5 未完成 |

官方文件已於 2026-10-15 線上讀取：
- https://platform.openai.com/docs/guides/speech-to-text
- https://platform.openai.com/docs/api-reference/chat/create

API Key 由使用者自有帳號在裝置輸入，以 Android Keystore AES-GCM 保護。健康檢查只查 whisper-1 / gpt-4o-mini 的 model metadata；不代表音訊辨識、翻譯品質、計費及指定語言已驗收。測試不使用真實 Key，不呼叫帳號雲端。

個人 BYOK 為本版使用範圍；尚未取得公開發佈的條款／client key 使用允許性結論，公開產品需另查供應商條款與後端代理，不可把自用 Key 打入 APK。價格來源／費率尚未獨立核對，因此不顯示金額估算。只顯示供應商回傳的 MT token usage，不是帳單；ASR 時長不被稱作已回報帳單 usage。

HTTPS 固定 endpoint、15秒 connect / 30秒 read / 40秒 call timeout、不跟 redirect、不自動重試連線。429 / 5xx 最多額外重試一次，Retry-After 秒數 clamp 1–8 秒。401 / 403 / 404 明示；錯誤 body 不記 log。取消含正在讀 response body 的 Call；舊 session 無法回寫目前字幕。

分段：能量門檻 0.005 RMS、600ms 安靜斷句、連續發言最多 10秒；不是神經 VAD，低音量／雜音準確率需實測。音訊 frame queue 上限 250×20ms；長 chunk 可直接交給空閒 worker；短 chunk 等待最多 5 秒／3 段，超過時明示跳過並計時，尾段正常結束可等待處理。跳過保留來源時間缺口，不壓縮時間戳。字幕最近 200 段僅在記憶體保存。


TTS 僅接收已完成 final 翻譯。合成中、等候合成與 PCM 待播合計最多3段，已產生待播PCM最多10秒；不截斷一段填滿queue，過長或超載捨棄譯音而保留字幕。採線性插值上採樣；48k直接通過，超過48k的引擎格式明確拒絕，沒有未驗證的降採樣支援。預設只選離線 zh-TW voice，語音包由使用者在系統設定安裝。引擎需要的暫存WAV位於私有cache，完成／取消刪除，程序被殺後下次啟動清理。沒有直接呼叫 speak()，不建立第二個播放器。


0.3.1：一個final段拆多個短句，順序合成與有界PCM等待，容量上限以原始翻譯段計數，短句數不占額外翻譯段名額。聲線仍為Android離線zh-TW，未新增雲端TTS。外部來源新增譯音聲道與200%增益，來源App直出原聲不受控制；不能因此解除完整來源整合BLOCKED狀態。
