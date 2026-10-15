# PROJECT_HANDOFF · 0.5.1-start-fix

本次使用者回報Gemini無法翻譯並提供Log，接著明確要求音訊授權勾一次就記住、App內可以截圖。這三項要求均納入本版。CircleCI沿用android-verify，不需新手動設定。

## 發現與修改

最新0.5.0的Gemini session有非靜音PCM，但CLOUD_ENABLED=0，沒有HTTP／字幕發布；Log混有舊版成功工作階段，已按runId分辨。原因為未勾選仍可按開始翻譯，實際只做擷取，且同意僅存Compose狀態。本次沒有把這份Log當作API認證失敗，沒有更換模型或偷偷送測試音訊。原始使用者Log不提交Git。

cloud_consent現保存於DataStore，首次false；勾選一次後重開App／重開翻譯／切換provider皆保留，手動取消亦持久化。Activity等儲存狀態載入；未勾選停用開始並明示提示，沒有每次確認框。Key缺失可直接按主按鈕前往設定。服務拒絕未同意的非測試音訊啟動，測試tone仍可單獨驗證，按鈕標為音訊測試。Android系統本身的擷取同意不由此設定略過。

依使用者要求移除Activity FLAG_SECURE，各頁允許截圖／app preview。已儲存Key不回顯，輸入仍密碼遮蔽；Key的AES-GCM／Keystore與Log不含字幕／Key等保護保留。

## 驗證與交付

本機71項測試、build/lint通過，完整3m 24s；lint0 errors/fatal、10 warnings。新增2項實際DataStore關閉重開／撤銷／provider切換測試。編譯bytecode檢查沒有Window.addFlags。docs/evidence/start-fix/及docs/TEST_REPORT.md為原始證據。

APK deliverables/V2V-0.5.1-start-fix-debug.apk，checksum `ead9947df54058af86b665d78fbf42353539f0b3617c64732b05832274697332  V2V-0.5.1-start-fix-debug.apk`。CI結果以本次main提交為準，不能沿用舊#6；實際提交與狀態記 /workspace/tooling/start-fix-github-upload.json。

## 手機驗收

更新後主頁勾選音訊授權一次→開始→Log應CLOUD_ENABLED=1並出現HTTP事件；結束重開App應維持勾選，取消後重開應維持未勾，各頁可截圖。沒有Android真機／emulator，未證明實際Gemini Key／品質／延遲或截圖效果。既有其他App擷取政策、原聲不能獨立控制及裝置待測限制不變。詳細操作見docs/CONSENT_AND_SCREENSHOTS.md及docs/GEMINI_SETUP.md。

## Git

本機base d1ef69e403cbca2c46686c26d8518ff61289736a，遠端base332d09b9e0d427fb087b1658a7f64084ba7fb437，tree同為4d7f15366508a72d1536d4eb03aab06d42f1b539。本次以遠端parent建立與本機一致tree，expected SHA推main；不要用舊本機history強推。
