# APK與驗證結果

從目前提交的CircleCI → android-verify → Artifacts下載同次產生的APK、SHA256SUMS及verification.json。這些檔案由CI建立，不把不同環境或舊版的checksum提交成新版證據。APK內versionName以本版app/build.gradle.kts為準。
