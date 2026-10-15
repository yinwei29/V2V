# PROJECT_HANDOFF · 0.3.1-speech-fix

使用者主流程為其他App（YouTube／Zoom／Google Meet）；優先背景浮窗操作，不再以owned WAV為主要建議。M3遠端045f099已通過CircleCI #3。本版修正漏播、2×語速、200%譯音增益與外部譯音聲道控制；沒有宣稱能靜音其他App原聲。

## 變更

舊版完整譯文若超10秒會整段TTS_ERROR，畫面仍顯示0 dropped。現按<=18 code points分短句合成，第一片可先播；PCM滿等待釋放，超長單片再分短重試。三個翻譯段總上限與10秒PCM容量保留，過載與合成失敗分開計數。有界自動追趕依長度／時長／積壓推算，基準0.75–2×，可關閉，無摘要／省略字幕。

外部模式新增V2V譯音雙耳／左／右、0–200%增益；不重播來源PCM，來源App直出原聲仍不受控制。浮動控制中能直接加減語速與譯音增益，控制清單可捲動。初始來源改為SYSTEM_PLAYBACK。原有owned來源完整mixer維持。Android引擎仍只選離線zh-TW，不新增雲端TTS。

SettingsStore flow去重，修正設定改動反覆APP_START的log雜訊。TTS_FAILURE、TTS_PHRASE_SPLIT、逐片PCM ready及queue／rate／failure metrics皆不含字幕、Key或原始音訊。停止／暫停／關閉播報清queue並讓舊callback失效。

## 驗證與交付

本機 build/test/lint成功（2m1s），43項單元測試全過，lint0 errors/8 warnings，debug APK v2簽章通過。詳見 docs/TEST_REPORT.md 與 docs/evidence/speech-fix/。
APK：deliverables/V2V-0.3.1-speech-fix-debug.apk，本機SHA256：149808fef133881780472ec8fa1c99d018b11e1f22a8d88481abee04e3e9fc4f。CircleCI的APK用該次Artifacts內SHA256SUMS；優先使用同CI簽章更新，卸載會刪Key與設定。

## 手動設定與限制

- CircleCI不需重設，main更新自動建置；API Key只留App內。
- 浮窗權限既已授權不需重設；語音引擎不需因本次問題更換，先更新再測。
- Samsung可試官方Sound Assistant的Individual application volume：來源App先調低，V2V維持較高；機型與會議／擷取是否相容需測，未經證實不可升級原聲控制能力。來源調低後若無訊號先恢復。
- 其他App實際可擷取性、2×實際速度、TTS漏播改善、防回灌、Bluetooth、鎖屏、30分鐘均需真機測，Zoom/Meet不得僅因MediaProjection成功就標支援。
- 離線ASR/MT、模型管理、歷史/SRT、來源整合授權、完整外觀設定及穩定性後續；完整產品仍未完成。

遠端main與本機history先前使用不同commit但相同tree。本次以遠端045f099為parent、ad90a5bef6f8a02f04007a5f1cc5cda27599f3fb為base tree fast-forward更新，不強推本機history。
