# Codex Wake

Personal fork of [suddenBook/GPTWake](https://github.com/suddenBook/GPTWake) with a plain Android screen and the fixed English wake phrase **Hey Codex**.

## Install on your Android phone

Download the APK from [this fork’s releases](https://github.com/Drhmmm/CodexWake/releases). Open the downloaded APK on your phone and install it. This fork uses the original package name, so remove the original GPTWake first if Android reports a signature conflict.

Open **Codex Wake**, tap **Enable Codex Wake**, and complete the microphone, notification, and appear-on-top prompts. ChatGPT should remain your default digital assistant, with Background conversations enabled in ChatGPT. Settings already granted are skipped.

When the status says **Listening for Hey Codex**, say “Hey Codex.” **Stop listening** disables listening, including after the next restart. After enabling, the original boot receiver resumes listening after a restart.

Android 12L or later and an ARM64 phone are required. This first personal test build still needs to be tried on the target phone; automated checks do not establish wake-word accuracy or lock-screen behavior on Samsung devices.

## Scope of this fork

The Jetpack Compose configuration screen and its dependencies have been removed. A native Activity provides Enable, Stop, permission setup, and live status. English uses GPTWake’s existing Chinese/English model with a fixed English phoneme line. The screen also replaces any previously saved custom phrase with Hey Codex.

WakeService, WakeController, KwsEngine, GptLauncher, ShimActivity, BootReceiver, AudioProbe, and AudioStateMonitor retain their upstream implementations. The original model assets, Japanese support code, and third-party notices remain included. No new AI service, account, or API key is required.

## Build and verification

Use JDK 21, Android SDK platform 37 and build tools 37.0.0, and the checked-in Gradle wrapper:

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

The release APK is unsigned until signed with an Android keystore. Keep the same private signing key for future updates; never commit it. The personal test workflow builds and signs an APK without repository secrets and attaches it to a GitHub pre-release. It generates a temporary test signing key, so later test builds can require uninstalling the previous version first. The inherited production release workflow still requires signing secrets.

On Windows, point the `BASH` environment variable at Git Bash, for example `C:/Program Files/Git/bin/bash.exe`. Model scripts and checksum files use LF line endings. The first build downloads and verifies the existing pinned Japanese model dependencies.

The regression suite covers the native permission flow, Start and Stop, fixed phrase persistence, model token compatibility, and the existing engine and launcher tests. Phone testing remains necessary for the actual microphone, ChatGPT handoff, screen-off wake, and restarting behavior.

## License

Apache-2.0. Bundled third-party components: [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
v1.13.4 (Apache-2.0) and the `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20` model; the pinyin
dictionary is generated at build time by [pypinyin](https://github.com/mozillazg/python-pinyin).
The model weights and the `en.phone` dictionary redistributed here come from upstream releases;
confirm their terms before redistributing further.

Japanese recognition is **Powered by Moonshine AI**. Its Japanese model weights use the
[Moonshine AI Community License](app/src/main/assets/ja/LICENSE), with separate terms for
commercial use; they are not Apache-2.0 licensed. The APK includes that license and its
[attribution notice](app/src/main/assets/ja/Notice.txt). Silero VAD uses the MIT license.
Kuromoji 0.9.0 uses Apache-2.0 and includes IPADIC dictionary notices in the APK.
