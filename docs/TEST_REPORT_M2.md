# 測試報告 · 0.2.0-m2-preview + 技術 Log

日期：2026-10-15；Linux / OpenJDK17 / Gradle8.13 / SDK35。無可操作Android裝置；收到使用者M1截圖（局部擷取證據），不等於我們執行完整真機測試。

## 自動測試

最終 `./gradlew --no-daemon --write-locks assembleDebug testDebugUnitTest lintDebug` **BUILD SUCCESSFUL**（2m06s）。

| 檢查 | 結果 |
|---|---|
| assembleDebug | PASS，0.2.0-m2-preview debug APK |
| testDebugUnitTest | PASS，28 tests / 0 failure / 0 error / 0 skipped |
| lintDebug | PASS，0 errors / 4 warnings（既有版本更新提示） |
| APK signature | PASS，debug v2 scheme，1 signer |
| CircleCI YAML / XML / packaging script | 靜態解析與執行 PASS |

原始JUnit、lint、build文字證據在 docs/evidence/m2/。Provider測試只用local MockWebServer、固定假Key及合成字串，不存取真實帳號，不是實際ASR/MT品質驗收。

- DSP8項：SPLIT頻率能量隔離／交換、雙耳純譯音、ASR分流與增益獨立、limiter、30次切換、downmix與增益恢復、外部控制能力。
- Segmentation7項：安靜不送辨識、600ms斷句、10秒長發言切段、尾段一次、時間缺口、ledger session/revision/容量、WAV編解碼／拒絕不支援格式與截斷。
- Provider9項：真實WAV multipart、id語言hint、zh-TW MT request、usage未知、401不重試、429一次重試、backoff/body-read取消、busy跳過計數、截斷譯文不成功、舊session無法回寫（相關斷言合併於9個方法）。
- Log4項：JSON可解析、RMS/計數保留、不洩露exception message/Key/逐字稿/路徑、容量輪替、排序、重建保留、清除、停用／NaN。

## 裝置／實際供應商

| 驗收 | 結果 | 證據／限制 |
|---|---|---|
| M1 CircleCI build/test/lint | PASS | ci/circleci android-verify success，d3fef3a，#1 |
| M2本機 build/test/lint | PASS | 不以M1 CI冒充本版CI |
| M2最新CircleCI | 待推送後執行 | 不以M1 CI冒充本版結果 |
| 這次M1擷取 | USER_REPORTED_PARTIAL_EVIDENCE | 使用者截圖：34.8秒、1727 frames、1657104 samples，曾非靜音，source/OS版本未知 |
| Log SAF匯出／share／重啟 | NOT_TESTED_ON_DEVICE | JVM可驗證journal，不等於Android chooser或程序崩潰實測 |
| Keystore / restore / screenshot防護 | NOT_TESTED_ON_DEVICE | 程式實作，無裝置驗證 |
| 真實en/id→zh-TW | BLOCKED_NO_KEY | 沒有真實ASR/MT測試，沒有latency/準確率數字 |
| T01–T10裝置權限／音訊／20次啟停 | NOT_TESTED_ON_DEVICE | 僅局部M1截圖，不升級全部PASS |
| 真實TTS / 防回灌 | BLOCKED | M3未實作 |
| T11–T14浮窗與字幕外觀 | BLOCKED | M4未實作 |
| 30分鐘／鎖屏／Bluetooth／會議 | NOT_TESTED_ON_DEVICE | 不虛構RAM/CPU/耗電/洩漏/路由數據 |
| 歷史／SRT／離線／模型 | BLOCKED | 尚未交付 |

Log欄位與測試者步驟見DIAGNOSTIC_LOGS。錯誤／Key測試不將真實秘密寫入Git/CI/APK。

已驗證程式碼 commit：`d03a3dba8a6ab122983759b83faf4ea242793dce`；後續僅文件更新不改程式。
