# V2V 0.8.1-install · 安裝與簽章 / Installation and signing

## Confirmed issue / 已確認問題

User reports Android「未安裝應用程式」. Official Google apksig8.9.2 verifies both downloaded APKs individually, but their signer certificates differ:

- CircleCI#17 /0.7.1-srt /com.yinwei.v2v: SHA-256`342b715027a9a5f5afb23044c914b45bc6141a0735b64a3895bf41a554ca47de`.
- CircleCI#19 and#20 /0.8.0-core /com.yinwei.v2v: SHA-256`fa0d822eeb06566185983e0bb02cdf516bfdad5925a5665b0f294985be475e32`.
- #17/#18 restored the October15 debug-keystore cache; #19 reported no matching signing/Gradle cache, generated a new key and saved it; #20 restored#19's key. Cache disappearance caused identity rotation. Cache presence is not durable key backup.

Android rejects an update with the same applicationId and a different signing identity. This is a verified APK compatibility defect, though the phone's exact package-manager failure code is unavailable. The old private key cannot be reconstructed from an APK/public certificate. No keystore/private material was downloaded, committed or published.

## Install without removing existing data / 保留舊資料安裝

1. Download`V2V-0.8.1-install-preview.apk` from this version's GitHub prerelease or matching CircleCI artifacts. Do not open a ZIP/log/HTML file as an APK.
2. Open it in Android's package installer; if requested allow this browser/file manager to install unknown apps. Android10+ required.
3. Launcher shows **V2V Preview**. Package`com.yinwei.v2v.preview` is separate from old`com.yinwei.v2v`, so it does not overwrite/uninstall old V2V. Only one instance should capture/play translated audio at a time.
4. Set the API key, language, cloud consent and overlay/capture permissions in Preview. Old keys/settings/history do not automatically migrate because Android isolates each installation. Existing Android TTS voices are system resources and can be reused.
5. Keep old V2V to view/export its history. Do not uninstall it without your own backup decision; uninstall removes its private data. No automatic migration/restore is promised.

若仍出現「未安裝」，提供完整訊息及Android版本；若能使用ADB，`adb install -r PATH_TO_PREVIEW.apk`會顯示更精確代碼。不要移除舊版來猜測修正，也不要提供API Key或簽章私鑰。

This is a parallel test installation, not a signed in-place update for legacy0.7.1. Release applicationId/namespace remains unchanged; only the debug build has the Preview suffix and launcher-label override. The runtime is the same V2V0.8 translation core, not Ju.

## CI safeguards and durable signing / 簽章管理

- `scripts/prepare-debug-signing.sh` runs after cache restore and before Gradle. Missing key or public certificate mismatch fails closed. Never auto-generate a replacement or change the fingerprint merely to make CI green.
- `scripts/debug-signer.sha256` pins the0.8 signer, not a private key. Actual APK is verified with Android SDK35.0.0`apksigner verify`, including v2 signatures, then`aapt dump badging` checks Preview package, version, min29/target35 and existing launcher class. `installation.json` and verification summary contain public metadata only. No physical installation acceptance is claimed from these checks.
- To avoid a future expired-cache block, a CircleCI project administrator must securely back up the existing pinned debug keystore and configure protected project variable`V2V_DEBUG_KEYSTORE_BASE64`. It is the existing Android debug keystore (alias`androiddebugkey`, conventional debug password), not a new arbitrary signing key. The script decodes it privately at`~/.android/debug.keystore`, mode0600, then verifies its certificate before use. Never put it in Git, public artifacts, command logs or chat; use the CI settings/secure administration channel. Do not expose protected variables to untrusted fork builds.
- This task does not have a CircleCI administration credential or the original old private key. A durable encrypted backup has **not** been configured automatically. Current pinned cache can build Preview; missing it later requires administrator restoration. Release/store signing is separate and never uses an intentionally public test key.

## Verification and rollback / 驗證與回退

Run locale verification, bash syntax/Python compilation, then existing V2V CircleCI assembleDebug/testDebugUnitTest/lintDebug and APK installation-metadata checks. Existing123 JVM tests retained;123/123 passed in [CircleCI#21](https://circleci.com/gh/yinwei29/V2V/21), source49597b0cd5cebbd43efa9a1d53ce6400bb773caa. Build passed; lint0 Error/Fatal with219 warnings. New signing and APK install-metadata checks passed; downloaded APK digest/ZIP/signature reverified. See [metadata](validation/circleci-21-installation.json). Physical install/update, Samsung source-policy restrictions and live-model quality remain device acceptance items.

Rollback this install-fix commit to restore prior debug identity/packaging. It does not delete either installed app, alter runtime translation or change old saved data. Do not re-publish a legacy-package APK with the mismatched new signer as an in-place update. Ju unchanged; no Play publication.
