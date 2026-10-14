# 來源矩陣（待真機填寫）

沒有連接 Android 裝置。UNKNOWN 不推斷來源用途或原聲是否可控。NO_SIGNAL 不能升為 UNSUPPORTED。

| 裝置 | OS | 來源版本 | 日期 | 來源模式 | 耳機與實際路由 | 授權 | 有效 PCM | usage | 外部直出 | 靜音時仍輸入 | 控制能力 | 結果 | 證據 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 未提供 | 未提供 | test WAV 0.1 | 2026-10-14 | TEST_FILE | UNKNOWN | 原創資產 | 软件格式已知；裝置待測 | MEDIA (本 APP 設定) | 無 | 軟體分支測試 | APP_OWNED | NOT_TESTED_ON_DEVICE | StereoMixerTest，僅軟體 |
| 未提供 | 未提供 | YouTube UNKNOWN | 未測 | SYSTEM_PLAYBACK | UNKNOWN | 未在裝置授權 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNAVAILABLE | NOT_TESTED_ON_DEVICE | 無 |
| 未提供 | 未提供 | Zoom UNKNOWN | 未測 | SYSTEM_PLAYBACK | UNKNOWN | 未在裝置授權 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNAVAILABLE | NOT_TESTED_ON_DEVICE | 無 |
| 未提供 | 未提供 | Meet UNKNOWN | 未測 | SYSTEM_PLAYBACK | UNKNOWN | 未在裝置授權 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNAVAILABLE | NOT_TESTED_ON_DEVICE | 無 |

每次測試記錄來源版本、短音檔、電平／路由／frame sample count、授權畫面、静音前後證據；不得把非靜音 PCM 自動解釋為來源內容正確或 TTS 防回灌成功。
