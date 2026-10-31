# V2V CircleCI #11：有界佇列回歸 / Bounded backlog regression

本文件只適用於 **yinwei29/V2V**（Kotlin/Compose、0.7.0-global），不是 yinwei29/Ju／LinguaBridge；兩個專案不混用程式、測試數量或CI證據。

## 實際失敗 / Observed failure

來源 `5cf817efcde30da68571ebdda60150fbaaa44bb6`，CircleCI [#11](https://circleci.com/gh/yinwei29/V2V/11)：106 tests／1 failed；ProviderTest.busyProviderDropsAreCountedAndDoNotGrowAnUnboundedQueue 在等待skippedSamples>0時TimeoutCancellationException。這是測試步驟失敗，不能宣稱整輪build/lint通過或本版APK已交付。

Production SubtitlePipeline uses 30 seconds / 12 chunks of pending backlog. The old test only sent four extra 1.6-second sentences: they fit inside both current limits, so waiting for an overload was obsolete. This failure alone is not evidence of an actual provider/translation stall.

## 修正 / Change

保留NO_RESPONSE local MockWebServer、真實OpenAiProvider、真實SubtitlePipeline。輸入改為14段額外完整句，使超過12段容量；每frame讓出1ms讓segmenter消化ingress，減少只測到突發producer overflow。保留skippedSamples>0、丟棄不超過實際額外輸入、blocked provider只有一次request及finally取消清理；不改正式佇列、不放大timeout、不刪除／skip／弱化原有斷言。106 cases數量不變。

No production engine, key, locale, schema, Android package or version changes. README's stale version heading is corrected to the existing 0.7.0-global. Ju remains untouched.

## 驗證與回退 / Verification and rollback

新分支push後由V2V既有CircleCI verify pipeline自動build/test/lint，結果待本輪來源完成才追加。沒有真實API key／音訊／真機，不能聲稱語意品質或耳機驗收。回退用git revert此修正，不改production backlog或使用者資料。

## 本輪執行與專案識別 / Execution and project identity

修正source `8e7e82d3349d4b78c6b921339dd2dbb863f6027d` 已啟動 [V2V CircleCI #12](https://circleci.com/gh/yinwei29/V2V/12)。該結果只屬V2V，不能算Ju驗證。新增本repo AGENTS.md明訂origin／package／CI及證據分離；沒有複製Ju私人程式碼。

Local locale verification passed (193 labels/four locales), as did git diff --check. Local Gradle could not initialize its default home; workspace GRADLE_USER_HOME retry then failed to connect while downloading Gradle. No local Gradle build/unit pass is claimed. CircleCI remains the full Gradle verification environment. Following documentation-only changes do not alter app/src or Gradle inputs.

## 已驗證結果 / Verified result

V2V CircleCI #12 source8e7e82d：**106 tests、0 failures、0 errors、0 skipped**；assembleDebug及lintDebug成功，lint Error/Fatal=0、Warning=208（未宣稱清零）。產物V2V-0.7.0-global-debug.apk，33,179,757 bytes；實際下載並核對SHA-256 `ea66c44e99d21ff4c59f422ff4f7ee09c306cc0f200a51bc3ce550ab7d6868be` 與同次SHA256SUMS／verification.json一致。完整metadata在 [validation JSON](validation/circleci-12-backlog-regression.json)。本次後續文件提交不變更app/或Gradle inputs。

The former overload case now passes with the existing production capacity and all assertions retained. No real provider or physical Android device testing occurred. CircleCI artifacts are temporary; no store release or permanent APK publication is claimed. These results never validate Ju.
