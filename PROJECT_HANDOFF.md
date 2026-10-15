# PROJECT_HANDOFF · 2026-10-15

版本 **0.3.0-speech-overlay-preview / versionCode4**。使用者已完成 CircleCI 設定；M2 bdfdcc1 的 [CircleCI #2](https://circleci.com/gh/yinwei29/V2V/2) 已success。使用者已確認原文與翻譯字幕，本次新增播報與浮窗，不將此回報誤判為ASR/MT完全失敗。

## 本次交付

- Android離線zh-TW TTS，以synthesizeToFile的PCM callbacks取得音訊，PCM16 8–48k mono/stereo轉mono48k，進既有單一mixer/AudioTrack；缺語音／格式／超長明示錯誤並保留字幕。
- 語音等待、合成中、PCM待播合計上限3段，PCM最多10秒；暫停／關閉清除舊語音，停止取消合成、刪私有暫存；程序被殺後下次啟動清殘留。
- 服務持有浮動小圈圈與字幕，移動／吸邊／寬度／閱讀與調整模式，隱藏／通知恢復／開主畫面／停止。上層顯示權限有明確入口與拒絕降級。
- DataStore保存播報開關、語速、字幕字級／行數／背景alpha；座標與寬度私有保存。普通模式黑描邊白字不隨背景alpha變；Android12+閱讀模式因系統觸控安全上限而降低整窗alpha。
- 耳機移除pause/flush輸出、清queue，ASR/MT繼續，使用者確認恢復；未宣稱OEM零瞬間外洩。
- 原有主畫面字幕固定顯示、前景可見metadata事件、短chunk有限backlog、TTS PCM ready與drop事件。log不含音訊／字幕／Key。

## 驗證與APK

本機 build / 37 tests / lint通過，0 errors / 7 warnings；debug v2簽章通過。證據 docs/evidence/m3/；詳細 docs/TEST_REPORT.md。

APK `deliverables/V2V-0.3.0-speech-overlay-preview-debug.apk`，本機 SHA-256 `e2878e75b8199fe3ae0f5498344a1348e7bb445692e226e1e503f9d2b030dd98`。CircleCI會使用CI自己的debug key與建置環境，下載時以其Artifacts內SHA256SUMS為準，不比較本機APK checksum。

## 使用者手動設定（已提早告知）

1. CircleCI不用重設，也不放OpenAI Key。新版推送main後自動建置，Artifacts下載同來源APK以延續簽章；卸載會失去Key與設定，先匯出Log。
2. 透過「設定浮動字幕權限」開啟顯示在其他應用程式上層，再回App開始／顯示浮窗。
3. 若顯示沒有離線台灣中文聲線，進「Android文字轉語音設定」安裝語音包／選引擎，再停止並重啟工作階段。無聲先看TTS錯誤與譯音音量，不重設已成功的OpenAI Key。
4. 測短句播報與切換其他App浮窗，再測耳機拔除／接回、停止／重啟與匯出Log。操作細節 docs/SPEECH_AND_OVERLAY.md。

## 尚待驗收與後續

M3/M4為工程預覽，Android TTS、浮窗、實際聲道／防回灌、Bluetooth、旋轉、鎖屏與30分鐘均NOT_TESTED_ON_DEVICE。完整色票／行距／描邊設定、來源授權整合、離線辨識與翻譯、模型管理、術語／上下文、歷史與SRT、穩定性未完成。外部原聲無控制證據，完整目標仍blocked，不以字幕與合成API接上便宣稱全功能完成。

本機前次M2文件commit c212d10 與遠端 bdfdcc1 具有相同tree，但commit不同；本次以遠端main為parent使用GitHub API fast-forward，禁止用本機舊history強推覆蓋。
