# 測試 Log（M2 preview）

使用者要求：測試 App 後可以提供 log 協助開發。診斷頁最上方提供記錄開關、匯出、系統分享、清除。開關透過 DataStore 保存；首次預設記錄一般技術事件，UI 明示內容與限制。沒有自動上傳。

## 記錄內容

- App 版本、Android SDK、廠牌／型號；不記 serial、Android ID、帳號、fingerprint 或耳機名稱。
- 程序隨機 runId、session UUID、wallTimeMs 與 monotonicMs。
- 來源與 session 狀態、ASR/MT/TTS 子狀態、權限同意／拒絕、停止原因。
- 約每秒 PCM RMS、frame / sample 計數、工作時間、underrun / 過載跳過樣本。
- 路由僅記 Android 裝置 type 數字，音量／模式只記數值與 enum。
- provider HTTP status、耗時；未捕捉 Java 例外只記 class，不記 message／stack／request／response body。

## 隱私邊界

事件 API 只接受 enum、numeric、內部 UUID 與 exception class，不提供任意文字欄位。音訊、原文、譯文、Key、Authorization header、URL、來源 URI／檔案路徑不進 log。手機型號為診斷所需資訊，匯出前由使用者決定是否分享。

這不是 Android 系統 logcat，不保證能記錄 native crash、ANR 或系統強制殺程序。程序忽然終止時，最後仍為 RUNNING 而沒有 STOP 可能是線索，不能直接判斷根因。Java uncaught handler 不改原本系統終止處理。

## 儲存與有界行為

app noBackupFilesDir，JSON Lines UTF-8；最多 3 份 × 512KiB（輪替時包含小量 header 邊界）。單一背景 writer，待寫 event 上限 128；滿時丟事件並記 logEventsDropped，不阻塞音訊。寫入錯誤計入 writeFailures。無磁碟上傳或原始錄音。

匯出先建立 barrier 等前序事件寫完，IO thread 在鎖內取得快照；使用 Android CreateDocument 儲存，不需廣泛儲存空間權限。分享使用限定 cache/diagnostics/ 的 FileProvider 和單次 URI read grant，開啟系統 chooser，由使用者挑選對象。分享 cache 最多 3 份，不含 Key storage。清除會移除輪替 log；仍啟用時後續事件繼續產生。

## 給測試者

1. 安裝 0.2.0-m2-preview，進「引擎與診斷」確認技術事件記錄開啟。
2. 重現問題；記下來源 APP 名稱／版本、耳機與操作步驟（log 不會自行讀其他 APP 資訊）。
3. 按停止，回診斷頁按「匯出 Log」或「分享 Log」。App 若崩潰，重新開啟後再匯出即可保留已寫入內容。
4. 把 JSONL 檔附回聊天，附上預期和實際行為。Key 只輸入 App，勿貼聊天。

自動測試：JSON 可解析、無 exception message／Key／逐字稿、輪替容量、順序、跨 journal instance 保留、清除、停用與 NaN 防護。Android SAF／分享 chooser／裝置崩潰重啟仍待真機驗證。


## 0.3 預覽新增

ASR_EMPTY、TRANSCRIPT_PUBLISHED、TRANSLATION_PUBLISHED、TRANSLATION_VISIBLE 記錄段號／revision／字數，沒有文字；VISIBLE 表示前景 Activity 已綁定該段，不是螢幕像素或使用者已讀證明。TTS_PCM_READY 表示引擎成功回傳 PCM，不能單獨當作耳機有聲的證據；TTS_DROPPED 記錄超長／過載捨棄。PROVIDER_STATE 含 TTS 狀態，PCM_SAMPLE 含累計 TTS_DROPPED。浮窗顯示／權限仍需操作驗證。
