# TV Screensaver

A full-screen WebView screensaver for **Android TV** and **Amazon Fire TV / Fire Stick**.  
Configure a URL, a sleep timer, and a screen-idle timeout — the app handles the rest, including working around Amazon's locked-down screensaver system.

---

## Features

- Displays any `http://` or `https://` URL full-screen
- JavaScript enabled; all popups, permission dialogs, and JS alerts silently suppressed
- Desktop user-agent so sites render their full layout on the TV screen
- Configurable **screensaver duration** (how long it plays before the device sleeps)
- Configurable **screen idle timeout** (how long before the screensaver starts)
- Works on both **standard Android TV** (via `DreamService`) and **Amazon Fire TV** (via activity overlay + secure settings)
- Survives Amazon resetting its own screensaver — see [Fire TV resilience](#fire-tv-resilience) below

---

## How it works

The app uses two complementary mechanisms depending on the device.

### Standard Android TV (NVIDIA Shield, Chromecast with Google TV, etc.)

On standard Android TV the system honours the [`DreamService`](https://developer.android.com/reference/android/service/dreams/DreamService) API.  
The app registers `ScreensaverDreamService` as a dream service, which the system screensaver picker can select.

```
Settings → Device Preferences → Screen saver → TV Screensaver
```

When the device idles for the configured timeout, the system automatically starts the `DreamService`, which opens a full-screen `WebView` with JavaScript enabled and all dialogs suppressed.  After the configured **sleep duration** the service calls `finish()`, allowing the device to sleep normally.

**The `DreamService` is registered and active on all Android TV devices.** No ADB commands are required for this path.

---

### Amazon Fire TV / Fire Stick

Amazon has replaced the standard Android screensaver system with their own proprietary "Backdrop" screensaver on Fire OS.  The screensaver picker in the Fire TV UI does not expose third-party `DreamService` apps, and Amazon periodically resets the underlying `screensaver_components` secure setting back to their own value.

The app addresses this with three layers.

#### Layer 1 — Direct launch from the home screen

The app tile on the Fire TV home screen launches `ScreensaverActivity` directly — a full-screen `WebView` activity that behaves identically to the `DreamService` screensaver.  This is the simplest way to start the screensaver on demand.

- Press the app tile → screensaver starts immediately
- Press **Back** or **Menu** on the remote → returns to the settings screen
- The sleep timer still operates: after the configured duration `FLAG_KEEP_SCREEN_ON` is cleared and the activity finishes, letting the device sleep

#### Layer 2 — Override the system screensaver via secure settings (one-time ADB setup)

Android's `screensaver_components` secure setting controls which `DreamService` the system uses.  Writing to it requires the `WRITE_SECURE_SETTINGS` permission, which must be granted once via ADB — Amazon does not expose this in their UI.

**Run this once from a PC with ADB installed:**

```bash
adb connect <fire-stick-ip>:5555
adb shell pm grant com.gappleby.androidtvss android.permission.WRITE_SECURE_SETTINGS
```

Then open the app on the Fire Stick and press **"Set as System Screensaver"**.  The app writes:

```
screensaver_components  →  com.gappleby.androidtvss/.ScreensaverDreamService
screensaver_enabled     →  1
screensaver_activate_on_sleep → 1
screensaver_activate_on_dock  → 1
```

This tells the Fire TV system to use the app's `DreamService` when it would normally show Amazon's screensaver.

#### Layer 3 — Automatic resilience (IdleMonitorService)

Amazon can silently reset `screensaver_components` after a system update or OTA.  `IdleMonitorService` runs as a foreground service (started at boot, restarted automatically if killed) and handles two jobs:

| Job | How |
|---|---|
| **Re-apply secure settings** | Every hour the service re-writes the four `screensaver_*` secure settings, keeping the app registered as the system screensaver |
| **Intercept `DREAMING_STARTED`** | If Amazon's screensaver fires before the hourly re-apply runs, the service receives the `android.intent.action.DREAMING_STARTED` broadcast and immediately launches `ScreensaverActivity` on top — the user sees the configured URL, not Amazon's Backdrop |

The result: even if Amazon resets its screensaver settings, the next screensaver event triggers our activity within seconds.

---

## Setup

### Requirements

- Android TV device (API 21+) **or** Amazon Fire Stick / Fire TV (any generation)
- A PC with [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) (for ADB)
- ADB debugging enabled on the device

### Enable ADB on the device

**Amazon Fire Stick:**
1. Settings → My Fire TV → About → tap **Build Number** seven times to unlock Developer Options
2. Settings → My Fire TV → Developer Options → **ADB Debugging: ON**
3. Settings → My Fire TV → Developer Options → **Apps from Unknown Sources: ON**

**Standard Android TV:**
1. Settings → Device Preferences → About → tap **Build** seven times
2. Settings → Device Preferences → Developer Options → **USB Debugging: ON**

### Install

```bash
# Connect (use your device's IP address)
adb connect 10.0.0.67:5555

# Install the APK
adb install -r app-release.apk

# Launch settings
adb shell am start -n com.gappleby.androidtvss/.SettingsActivity
```

### Configure

Open the app on the device and set:

| Field | Description |
|---|---|
| **Screensaver URL** | The page to display (`http://` or `https://`) |
| **Screensaver duration** | Minutes before the screensaver exits and the device sleeps (0 = run indefinitely) |
| **Screen idle timeout** | Minutes of inactivity before the screensaver starts — written to `Settings.System.SCREEN_OFF_TIMEOUT` (requires "Modify system settings" permission) |

Press **Test URL** to preview the page full-screen before saving.

### Fire TV — one-time ADB permission grant

```bash
adb shell pm grant com.gappleby.androidtvss android.permission.WRITE_SECURE_SETTINGS
```

Then press **"Set as System Screensaver"** in the app.  The ADB command survives reboots but must be re-run if the APK is uninstalled and reinstalled.

### Standard Android TV — select the screensaver

```
Settings → Device Preferences → Screen saver → TV Screensaver
```

---

## Permissions

| Permission | Why | How granted |
|---|---|---|
| `INTERNET` | Load the URL in the WebView | Automatic |
| `WRITE_SETTINGS` | Write `SCREEN_OFF_TIMEOUT` to the system | User taps "Grant" in the app |
| `RECEIVE_BOOT_COMPLETED` | Start `IdleMonitorService` after reboot | Automatic |
| `FOREGROUND_SERVICE` | Keep `IdleMonitorService` alive | Automatic |
| `WRITE_SECURE_SETTINGS` | Write `screensaver_components` and related settings | **One-time ADB command** (see above) |

---

## CI / CD — GitHub Actions

Every push automatically builds the app via `.github/workflows/build.yml`.

### What runs and when

| Event | What happens |
|---|---|
| Push / PR to `main` or `master` | Debug APK built and uploaded as a workflow artifact (downloadable from the Actions tab, retained for 30 days) |
| Push a tag `v*.*.*` (e.g. `v1.2.0`) | Signed release APK built, named `tv-screensaver-1.2.0.apk`, and published as a GitHub Release with auto-generated release notes |

### One-time setup — signing secrets

The release build requires a signing keystore stored as GitHub Secrets.

**Step 1 — generate a keystore** (skip if you already have one):
```bash
keytool -genkey -v \
  -keystore release.jks \
  -alias tv-screensaver \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

**Step 2 — base64-encode the keystore:**
```bash
# macOS / Linux
base64 -w 0 release.jks

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))
```

**Step 3 — add four secrets** in your GitHub repo under  
[github.com/gappleby/androidtv-ss-url/settings/secrets/actions](https://github.com/gappleby/androidtv-ss-url/settings/secrets/actions)  
**New repository secret**:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | Output from the base64 command above |
| `KEYSTORE_PASSWORD` | The store password you chose in `keytool` |
| `KEY_ALIAS` | `tv-screensaver` (or whatever alias you used) |
| `KEY_PASSWORD` | The key password you chose in `keytool` |

### Releasing a new version

```bash
git tag v1.0.0
git push origin v1.0.0
```

The Actions workflow will build, sign, and publish the APK to GitHub Releases automatically.

**Download the APK:**
- Releases page: https://github.com/gappleby/androidtv-ss-url/releases
- Direct link pattern: `https://github.com/gappleby/androidtv-ss-url/releases/download/v1.0.0/tv-screensaver-1.0.0.apk`
- Debug builds (every push): https://github.com/gappleby/androidtv-ss-url/actions → click the latest run → **Artifacts**

Sideload with:
```bash
adb install -r tv-screensaver-1.0.0.apk
```

---

## Building from source

```bash
# Prerequisites: JDK 11+, Android SDK

git clone https://github.com/gappleby/androidtv-ss-url.git
cd androidtv-ss-url

# Build debug APK
./gradlew assembleDebug

# Build and deploy in one step
./gradlew assembleDebug && \
  adb install -r app/build/outputs/apk/debug/app-debug.apk && \
  adb shell am start -n com.gappleby.androidtvss/.ScreensaverActivity
```

**Windows:**
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat assembleDebug
```

---

## Architecture

```
com.gappleby.androidtvss
│
├── ScreensaverDreamService   Android TV DreamService — activated by the system
│                             screensaver picker; full-screen WebView with sleep timer
│
├── ScreensaverActivity       Full-screen WebView activity — the Fire TV equivalent;
│                             launched from the home tile or from SettingsActivity;
│                             Back/Menu opens settings
│
├── SettingsActivity          Configuration UI: URL, durations, permissions,
│                             "Set as System Screensaver" button
│
├── TestUrlActivity           Full-screen preview launched from settings;
│                             identical WebView config to the screensaver
│
├── IdleMonitorService        Foreground service (started at boot):
│                             • re-applies screensaver_components hourly
│                             • intercepts DREAMING_STARTED to overlay
│                               ScreensaverActivity if Amazon fires first
│
├── BootReceiver              BOOT_COMPLETED receiver — starts IdleMonitorService
│
└── AppPreferences            SharedPreferences wrapper (URL, durations)
```

---

## WebView behaviour

All WebView instances (screensaver, test preview, dream service) share the same configuration:

- JavaScript **enabled**, DOM storage enabled
- `javaScriptCanOpenWindowsAutomatically = false` — no pop-up windows
- All JS dialogs (`alert`, `confirm`, `prompt`, `beforeunload`) silently cancelled
- All hardware permission requests (camera, microphone, geolocation) denied
- Desktop user-agent string (removes "Mobile" token) so sites render their desktop layout on the TV screen

---

## Troubleshooting

**Screensaver doesn't start automatically on Fire TV**  
Make sure you ran the ADB grant command and pressed "Set as System Screensaver" in the app.  
Check that `IdleMonitorService` is running: Settings → Applications → Running Services.

**"Set as System Screensaver" button shows an error**  
The `WRITE_SECURE_SETTINGS` permission hasn't been granted.  The button shows the ADB command inline — run it from your PC and try again.

**Page shows `ERR_CLEARTEXT_NOT_PERMITTED`**  
The app already sets `android:usesCleartextTraffic="true"` so HTTP URLs are allowed.  If you see this error the URL may be using a mixed-content redirect — try the `https://` version of the URL.

**App disappears from the Fire TV home screen after an update**  
Re-install the APK and re-run the ADB grant command — the `WRITE_SECURE_SETTINGS` permission is cleared on uninstall.

---

## Licence

MIT
