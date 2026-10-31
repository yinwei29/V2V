# V2V repository instructions

- This repository is **yinwei29/V2V**, Android package `com.yinwei.v2v`, Kotlin/Compose. Confirm `git remote -v` before every push.
- **yinwei29/Ju (LinguaBridge) is a separate project**, with separate branches, PRs, architecture and test evidence. Do not copy its private source/configuration here or apply its test counts/CI results to V2V.
- Use V2V's existing `.circleci/config.yml` and the CircleCI project `https://app.circleci.com/projects/github/yinwei29/V2V`. Pushes run the existing verify pipeline. Do not assume a Ju CircleCI/GitHub workflow validates this repository.
- Preserve existing bounded audio/translation queues, cancellation, privacy/consent, key storage and history. Real provider keys, user audio/transcripts and raw private logs must not enter commits or CI artifacts.
- Use local synthetic/MockWebServer tests for automatic validation; never claim real provider, Bluetooth, capture policy or device listening acceptance from JVM tests.
- Derive version from app/build.gradle.kts and test/build outcome from the matching source commit's CircleCI reports. Do not label an old APK or another repository's artifact as the current build.
