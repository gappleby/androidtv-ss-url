# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**URL as TV Screensaver** (short name **UATV**, used for the package `com.gappleby.uatv`, theme `Theme.UATV`, and APK names `uatv-*.apk`). Single-module Android app (`:app`, Kotlin, Groovy Gradle DSL) that shows a configurable URL in a full-screen WebView as a screensaver on Android TV and Amazon Fire TV. No tests, no lint config, no third-party libs beyond AndroidX/Material. minSdk 21, target/compile 34, Java/Kotlin target 1.8.

## Commands

```powershell
# Windows (JAVA_HOME must point at a JDK; CI uses JDK 21)
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat assembleDebug
```

```bash
./gradlew assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # signed only if KEYSTORE_PATH/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD env vars are set
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant <package> android.permission.WRITE_SECURE_SETTINGS   # needed for Fire TV path
```

The debug build type has `applicationIdSuffix ".debug"`, so the debug package is `com.gappleby.uatv.debug` while classes stay in `com.gappleby.uatv`. Use fully-qualified class names in `am start -n` for debug builds (e.g. `com.gappleby.uatv.debug/com.gappleby.uatv.ScreensaverActivity`); the `pkg/.Class` shorthand won't resolve.

CI (`.github/workflows/build.yml`): every push/PR to main builds a debug APK artifact; pushing a `v*.*.*` tag builds a signed release APK `tv-screensaver-<version>.apk` and publishes a GitHub Release. Signing comes from `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` secrets. `versionCode`/`versionName` in `app/build.gradle` are not derived from the tag.

## Architecture

Two parallel display paths exist because Fire OS ignores third-party `DreamService`s:

- **Standard Android TV** — `ScreensaverDreamService` is selected in the system screensaver picker and the system starts it on idle.
- **Fire TV** — the launcher tile opens `MainActivity` (the `LEANBACK_LAUNCHER` entry), a home screen with Back / Show / Config that deliberately shows no configuration details. Show starts `ScreensaverActivity`; Config opens `SettingsActivity`. In `ScreensaverActivity`, Back/Menu are intercepted in `dispatchKeyEvent` (not `onKeyDown`) because the focused WebView otherwise swallows them. Additionally, with `WRITE_SECURE_SETTINGS` granted via ADB, `SettingsActivity` writes `screensaver_components` (+ `screensaver_enabled`, `_activate_on_sleep`, `_activate_on_dock`) to point at the DreamService. `IdleMonitorService` (foreground service, started by `BootReceiver` on boot and by `MainActivity`/`SettingsActivity`; needs `FOREGROUND_SERVICE_DATA_SYNC` on Android 14+) re-writes those settings hourly because Amazon resets them, and listens for `DREAMING_STARTED` to launch `ScreensaverActivity` over Amazon's screensaver.

Key cross-file points:

- **WebView config is duplicated** in `ScreensaverActivity`, `ScreensaverDreamService`, and `TestUrlActivity` (JS on, DOM storage, desktop UA by stripping `"Mobile"`, all JS dialogs cancelled, popups/permissions/geolocation denied). Changes to WebView behaviour must be applied to all three.
- **Secure-settings write logic is duplicated** in `SettingsActivity.applySystemScreensaverComponent()` and `IdleMonitorService.reapplyScreensaverComponent()`; keep them in sync. Both build the component via `ComponentName(this, ScreensaverDreamService::class.java).flattenToString()` — don't use the `"$packageName/.Class"` shorthand, which breaks under the `.debug` applicationId suffix.
- **Sleep timer**: after the configured duration (0 = indefinite), the screensaver clears `FLAG_KEEP_SCREEN_ON` / calls `finish()` so the device can sleep.
- **Screen idle timeout** is written by `SettingsActivity` to `Settings.System.SCREEN_OFF_TIMEOUT`, which requires the user-granted `WRITE_SETTINGS` permission.
- `AppPreferences` wraps SharedPreferences `screensaver_prefs` (URL, sleep duration minutes, screen timeout minutes) and is the only shared state between components.
- `android:usesCleartextTraffic="true"` is set so `http://` URLs load.
