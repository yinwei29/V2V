# Android 限制與查核

查核日期：2026-10-14。選用已知相容基線 min 29 / target 35 / compile 35，AGP 8.9.2、Kotlin 2.1.20、Gradle 8.13。這不是最新 SDK 宣稱。此版自用 debug，不做 Play 發佈；當日 Play target 政策尚未獨立核對，公開發佈前必須重新查核。

已線上讀取官方文件：
- https://developer.android.com/media/platform/av-capture
- https://developer.android.com/develop/background-work/services/fgs/service-types

官方播放擷取條件：RECORD_AUDIO、使用者同意 MediaProjection、相同 profile；來源 usage 為 MEDIA / GAME / UNKNOWN 且允許 capture。VOICE_COMMUNICATION 不能推定可抓。實作匹配三種 usage，excludeUid 自身；不調來源或系統媒體音量。

官方 FGS mediaProjection 需對應權限並先由使用者授權；mediaPlayback 需相應 FGS 權限。Android 15+ 不允許由 BOOT_COMPLETED 啟動這兩類服務。本專案沒有 boot receiver，只在可見 Activity 操作後 startForegroundService，再依來源傳入單一適當 service type。

純音訊路徑不建立 VirtualDisplay，不擷取或保存螢幕影像。每次開始都重新要求授權，不持久化 token，先註冊 callback，再建立 AudioRecord；onStop 取消 session。Android 版本／OEM 何時在鎖屏或背景撤銷投影，需真機測試，不保證永續擷取。

Owned 模式僅要求媒體音訊焦點，失去焦點停止 APP 音訊直到使用者恢復；外部擷取不搶焦點。耳機裝置移除或 AUDIO_BECOMING_NOISY 會停 APP 音訊；系統可能在 callback 前已換路由，無零外洩保證。本版不宣稱 stereo 耳機能力，routedDevice 僅為裝置回報，HFP/SCO/LE Audio 待驗證。

已知待驗證：Android 10–16 實際授權與服務行為、API35+ 焦點限制、YouTube capture policy、Zoom/Meet 授權來源、藍牙／Samsung OEM、省電與鎖屏、30 分鐘負載。沒有真機數據，不填延遲／耗電／RAM 實測數字。

後續 M3 的 Android TTS 必須 synthesizeToFile/解碼到單一 PCM mixer；不可用 speak() 加獨立播放器冒充完整混音。
