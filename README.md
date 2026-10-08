# TCL HDMI Launcher

<p align="left">
  <b>English</b> | <a href="README_zh.md">繁體中文</a>
</p>

[![Latest Release](https://img.shields.io/github/v/release/ian20040409/TCL-Android-TV-HDMI-R851T02?style=flat-square&color=blue)](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/ian20040409/TCL-Android-TV-HDMI-R851T02/total?style=flat-square)](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases)
[![License](https://img.shields.io/github/license/ian20040409/TCL-Android-TV-HDMI-R851T02?style=flat-square)](LICENSE)

> **Designed specifically for enthusiasts and minimalists who treat their TCL Android TV as a pure external display / monitor.**  
> Bypass TCL's sluggish, ad-ridden stock launcher. Boot directly into your external devices (Apple TV 4K / PS5 / Android TV Box / Blu-ray Player) rapidly, transforming your TV into a distraction-free, dedicated display!

👉 **[Download Latest Release APK](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases/latest)**

---

## Table of Contents

- [Why This Project? (Core Pain Points & Use Cases)](#why-this-project-core-pain-points--use-cases)
- [Ideal Home Theater Setup Example](#ideal-home-theater-setup-example)
- [Key Highlights & Features](#key-highlights--features)
- [Screenshots](#screenshots)
- [Remote Control Shortcuts](#remote-control-shortcuts)
- [Installation & Setup Guide](#installation--setup-guide)
  - [Step 1: Build or Download the APK](#step-1-build-or-download-the-apk)
  - [Step 2: Enable ADB on Your TCL TV & Install](#step-2-enable-adb-on-your-tcl-tv--install)
  - [Step 3: Set as Default TV Launcher](#step-3-set-as-default-tv-launcher)
  - [Step 4: Disable the Bloated TCL Stock Launcher (Recommended)](#step-4-disable-the-bloated-tcl-stock-launcher-recommended)
  - [Step 5: Enable Wake Guard & Home Button Mapper (Highly Recommended)](#step-5-enable-wake-guard--home-button-mapper-highly-recommended)
  - [Step 6: Enable Shizuku for Privileged Operations & CEC Permissions (Optional)](#step-6-enable-shizuku-for-privileged-operations--cec-permissions-optional)
- [Privacy & Permissions](#privacy--permissions)
- [Troubleshooting & In-Depth Fixes](#troubleshooting--in-depth-fixes)
  - [Fix 1: Apple TV / HDMI-CEC Standby Wake Glitch (Double-Power-Key Bug)](#fix-1-apple-tv--hdmi-cec-standby-wake-glitch-double-power-key-bug)
  - [Fix 2: Stuck Dolby Vision / HDR 10 OEM Toast Notification](#fix-2-stuck-dolby-vision--hdr-10-oem-toast-notification)
  - [Fix 3: TvView Hardware Passthrough Black Screen & Compositor Glitch](#fix-3-tvview-hardware-passthrough-black-screen--compositor-glitch)
  - [Fix 4: True HDMI-CEC Auto-Switching via Logcat Monitoring](#fix-4-true-hdmi-cec-auto-switching-via-logcat-monitoring)
- [Tested / Untested Hardware Compatibility Matrix](#tested--untested-hardware-compatibility-matrix)
  - [Tested Model Information](#tested-model-information)
  - [Physical Device Input Mapping Table (R851T02 / C715 Tested)](#physical-device-input-mapping-table-r851t02--c715-tested)
  - [ADB Testing for Input Switching](#adb-testing-for-input-switching)
- [How It Works](#how-it-works)
- [Technical Specifications](#technical-specifications)
- [Recommended Projects](#recommended-projects)

---

## Why This Project? (Core Pain Points & Use Cases)

### Who Is This For?
If any of the following describes your home theater setup, this launcher was built for you:
- **TCL Android TV / Google TV Users**: You own a TCL Smart TV and want a streamlined launcher tailored specifically to TCL's native display and TV input framework (`com.tcl.tvinput`).
- **External Input Purists**: You rely almost entirely on external high-performance hardware, and your TV's built-in Smart TV OS is merely a "panel driver":
  - 🍏 **Apple TV 4K** — Primary platform for streaming, movies, and TV shows.
  - 🎮 **PlayStation 5 (PS5) / Xbox Series X / Nintendo Switch** — Next-gen 4K HDR gaming.
  - 📺 **Dedicated TV Boxes** (Chromecast with Google TV, NVIDIA Shield, Fire TV, etc.).
  - 💿 **4K UHD Blu-ray / DVD Players** or **AV Receivers (eARC)**.
- **Plagued by TCL's Infamous HDMI-CEC Firmware Flaws**:
  - **Reboot Loops & Standby Glitches**: When putting Apple TV into sleep, TCL's stock firmware handles CEC standby frames by injecting two power button keycodes within 95ms, immediately waking the TV back up after shutting off.
  - **AVR / Soundbar eARC Handshake Drops**: Bloated OEM background daemons congest CPU and bus cycles, causing CEC timing violations and audio dropouts or lost volume sync on external sound systems.
- **Tired of Bloated & Ad-Heavy Stock Launchers**: The factory TCL launcher is slow to boot, clutters the screen with unwanted recommendations, and consumes precious RAM and CPU cycles.
- **Want an "Instant-On" Display Experience**: When you power on the TV, it acts like a traditional monitor or high-end display—automatically switching to your favorite input (e.g., Apple TV) within seconds without navigating menus.
- **Quick Multi-Device Switching**: When switching between a gaming console (PS5) and a streaming box (Apple TV), switch quickly at the press of a single remote number button (`1` / `2` / `3`), without navigating clunky input menus.

---

## Ideal Home Theater Setup Example

- **Default Scenario**: On power-up, a 3-second countdown (customizable) automatically transitions straight into **HDMI 3 (Apple TV 4K)** with zero button presses required.
- **Gaming Scenario**: When you're ready to game, press numeric key `1` on your remote to jump to **HDMI 1 (PS5)**.
- **Picture & Sound Calibration**: The top-right pill button takes you straight to native TCL Picture & Sound settings (`com.tcl.settings`) without ever displaying the TCL home screen.

---

## Key Highlights & Features

1. **Auto-Switch Countdown on Boot / Home**:
   - Customizable countdown timer: `Off`, `1s`, `2s`, `3s (Default)`, `5s`, `10s`, `15s`, `30s`.
   - On TV startup or pressing the Home button, automatically switches to your designated default HDMI port once the timer expires.
   - Touching any D-pad direction button during countdown cancels the timer, letting you browse inputs at your leisure.
2. **Instant Remote Number Key Switching**:
   - Press `1`, `2`, or `3` on your remote keypad to jump straight to the respective HDMI port.
3. **Set Default Boot Input (Long-Press OK)**:
   - Long-press the OK button on any HDMI card to set it as your persistent default startup input.
4. **Lightweight Architecture & Memory Profile**:
   - Optimized with full R8 minification, dead-code stripping, and resource shrinking.
   - Built with pure programmatic view trees (0 XML layout inflation overhead, 0 reflection).
   - Hot-path countdown timer avoids heap memory allocations during countdown.
   - **Background Lifecycle**: When Wake Guard and CEC monitoring are inactive, the app runs as a pure launcher without resident background services. When CEC Auto-Switch or Wake Guard is enabled, a low-overhead foreground service (`CecLogReaderService`) or Accessibility Service (`WakeAccessibilityService`) runs in the background to ensure reliable wake detection and input routing.
5. **Pure Black OLED / Dark Room Friendly UI (`#000000`)**:
   - Eliminates blinding white flashes in dark home theater rooms and optimizes local dimming on QLED / Mini-LED panels.
6. **Multi-Language Support**:
   - Fully localized in English, Traditional Chinese (繁體中文), and Simplified Chinese (简体中文) based on your system locale.
7. **Native Leanback Settings & OOBE Setup Wizard**:
   - Built on Android TV's official **Leanback `GuidedStepSupportFragment`** for 10-foot TV UI ergonomics and D-pad remote navigation.
   - **Out-of-the-Box Setup Wizard (OOBE)**: Automatically guides first-time users through choosing Monitor vs. App Mode, picking a default boot port, and enabling Accessibility Guard.
   - **App Mode Settings**: Dedicated sub-settings activity (`AppModeSettingsActivity`) with asynchronous installed app scanning, app picker dialog, and configurable auto-launch delay timer.
   - **Reset Preferences**: Safely restore all launcher settings to defaults and restart the setup wizard with one click.
   - Single-click access to native TCL picture/audio adjustment pages (`com.tcl.settings`) and Android system preferences.
8. **App Mode & Auto-Open App**:
   - Single-click toggle for "App Mode" on the top status bar or via Settings. When enabled, booting or pressing Home opens the App Drawer directly.
   - Pick an auto-launch app either from the App Drawer (hold OK) or in **App Mode Settings**, complete with configurable boot/wake delay seconds (e.g. YouTube, Netflix, Plex).
9. **Lightweight App Drawer**:
   - An on-demand list for occasional built-in or sideloaded TV apps with recents and long-press uninstall/disable management.
10. **Self-Contained Native HDMI Viewer (`TvView`) — Zero Dependency on `com.tcl.tv`**:
    - Includes a built-in fullscreen HDMI Viewer (`HdmiViewerActivity`) powered directly by Android's TV Input Framework (`android.media.tv.TvView`).
    - Eliminates dependency on TCL's factory TV player (`com.tcl.tv.TVActivity`).
    - Supports 4K @ 60Hz, HDR10, and Dolby Vision passthrough with hardware overlay decoding.
    - **Silent Passthrough (Zero Overlay / Zero Toast)**:
      - `HdmiViewerActivity` renders no custom toast or overlay views, designating `TvView` as the sole ContentView.
      - **Mitigates TV GPU Cache Glitch**: On Realtek RTD2851 SoCs, window alpha fade-out animations (including system toast fade-outs) can freeze semi-transparent bounding boxes over the video plane, leaving a dark shadow. Silencing all toasts in the viewer avoids triggering this compositor issue.
      - **Hot-Plug & Power-On Detection**: When external devices power on or connect, video illuminates smoothly without obstruction.
    - **Independent Task Window & Deterministic Hardware ID Mapping**:
      - Utilizes an independent task window stack (`FLAG_ACTIVITY_NEW_TASK` with custom `taskAffinity`) to isolate playback lifecycle from the launcher.
      - Maps directly to R851T02 hardware chip inputs (`HW1413744128`, `HW1413744384`, `HW1413744640`) with full `onNewIntent` handling.
    - You can **safely uninstall or disable `com.tcl.tv` entirely** via ADB without losing HDMI display capability (tested on TCL 65C715).
11. **Shizuku API Integration & In-App APK Download Management (`ShizukuSettingsActivity`)**:
    - **Real-Time Service Monitoring**: Automatically detects Shizuku service binder, permission status (`PERMISSION_GRANTED`), and server version.
    - **One-Click Shizuku Permission Request**: Triggers Shizuku's native API permit dialog window directly on the TV interface (`Shizuku.requestPermission()`).
    - **Automatic Shizuku APK Downloader**: Connects to GitHub Releases API, fetches the latest Shizuku APK, downloads in background with real-time decimal progress (`X.X MB / Y.Y MB (Z%)`), and opens package installer via `FileProvider`.
    - **Local APK Integrity Validation & Cache Reuse**: Validates local APK files with `packageManager.getPackageArchiveInfo()`. Reuses previously downloaded APK files so returning users can install instantly without re-downloading.
    - **Direct App Freeze / Unfreeze**: Freeze (disable) or unfreeze (enable) background TV or mobile apps directly from the App Drawer (`AppListActivity`) using Shizuku permissions.
12. **HDMI-CEC Wake-Up / Standby Auto-Switching & System Logcat Interception (`CecLogReaderService` + Shizuku-API Integration)**:
    - **Smart CEC Wake-Up Interception**: Bypasses TCL firmware limitations by monitoring system logcat (`HdmiCecController`) for `<Active Source>` and `MSG_VIEW_ON` broadcasts to auto-switch to the correct HDMI port on wake.
    - **Automatic Permission Granting via Shizuku API**: Integrates [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) (`ShizukuHelper.tryGrantPermissions`) to automatically grant `READ_LOGS` and `DUMP` permissions without manual ADB commands.
    - **Foreground Service Wake Guard**: Runs a foreground service (`CecLogReaderService`) that ensures reliable standby wake detection and port tracking across TV sleep/wake cycles.

---

## Screenshots

| Main Screen (HDMI Input Selector) | Countdown Timer Settings Dialog |
|:---:|:---:|
| ![Main Screen](readme_pic/Screenshot_20260925_222244.png) | ![Countdown Settings](readme_pic/Screenshot_20260925_222304.png) |
| **Native TCL Settings Shortcut** | **App Drawer** |
| ![TCL Settings](readme_pic/Screenshot_20260925_222322.png) | ![App Drawer](readme_pic/Screenshot_20260925_222344.png) |

---

## Remote Control Shortcuts

| Remote Button | Action & Behavior |
|---|---|
| **D-Pad (Arrows)** | Move card focus; pressing any arrow key during countdown **cancels auto-switch** |
| **OK / Enter** | Switch immediately to focused HDMI input (or launch selected app) |
| **Long-Press OK (Main View)** | Set currently focused HDMI port as the **default startup input** |
| **Long-Press OK (App Drawer)** | Open management dialog: **Set as Auto-Open**, Uninstall, Disable, App Info |
| **Number Keys `1` / `2` / `3`** | Jump straight to HDMI 1 / 2 / 3 in both Launcher and Viewer |
| **INPUT / SOURCE** | Cycle through HDMI 1 ➔ HDMI 2 ➔ HDMI 3 in both Launcher and Viewer |
| **MENU** | Open Settings menu (Launcher) / Return to Launcher (Viewer) |
| **SETTINGS** | Open Settings menu (Launcher) / Launch native TCL settings (`com.tcl.settings`) |
| **BACK** | Exit dialogs / return to launcher from Viewer, Settings, or App Drawer |

---

## Installation & Setup Guide

### Step 1: Build or Download the APK

You can download pre-built APKs from [GitHub Releases](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases/latest), or build it locally:

```bash
# Build Debug APK
./gradlew assembleDebug

# Or build R8-optimized Release APK
./gradlew assembleRelease
```

### Step 2: Enable ADB on Your TCL TV & Install

1. On your TCL TV, open System Settings -> "About" -> click "Build Number" 7 times to enable Developer Options.
2. In "Developer Options", enable "USB Debugging" or "Network Debugging".
3. Connect your computer to your TV over ADB:

```bash
# Connect to your TV's IP address
adb connect 192.168.1.xxx:5555

# Install the APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Set as Default TV Launcher

```bash
# Set this app as the default Home launcher
adb shell cmd package set-home-activity com.lnu.tclhdmilauncher/.MainActivity
```
*Pressing the remote's "Home" button will now open this HDMI launcher.*

### Step 4: Disable the Bloated TCL Stock Launcher (Recommended)

To prevent the factory launcher from running or waking in the background:

```bash
# Disable the stock Android TV / Google TV Launcher (safe and reversible)
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell am force-stop com.google.android.tvlauncher
```

> [!TIP]
> **Completely Reversible:**  
> If you ever want to restore the factory launcher, simply run:
> ```bash
> adb shell pm enable com.google.android.tvlauncher
> adb shell cmd package clear-preferred-activities com.lnu.tclhdmilauncher
> ```

### Step 5: Enable Wake Guard & Home Button Mapper (Highly Recommended)

Enabling the Accessibility Service grants two system-level capabilities:
1. **Standby Wake Guard**: Monitors screen wake events to pull the display back to the Launcher or active input (preventing AV input / no signal bugs).
2. **Button Mapper (Home Key Redirection)**: Intercepts the remote **Home button** to open this Launcher directly.

**How to enable:**
- In the Launcher top bar, click the "Wake Guard" button (`⚠️ Wake Guard: Off`) and select "Open Settings" (or manually navigate to TV Settings ➔ Device Preferences ➔ Accessibility).
- Toggle **"HDMI Launcher Wake & Home Button Mapper"** to ON.

### Step 6: Enable Shizuku for Privileged Operations & CEC Permissions (Optional)

1. Open **Settings** ➔ **Shizuku API Settings & Status** in the Launcher.
2. If Shizuku is not installed, click **Download & Install Latest Shizuku** directly within the Launcher.
3. Open Shizuku on your TV and start the service following Shizuku's standard methods:
   - **Wireless Debugging**: Start directly within the Shizuku App on Android 11+.
   - **ADB Command**: Use the command provided inside the official Shizuku app, or launch it via your computer terminal:
     ```bash
     # Execute the official startup script provided by the Shizuku app
     adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/files/start.sh
     ```
4. Return to **Shizuku API Settings** in this launcher and click **Request Shizuku Permission**.
5. Once granted, the app can automatically configure `READ_LOGS` and manage background apps.

---

## Privacy & Permissions

This project is open-source and respects user privacy. Below is an overview of sensitive permissions used by the application, their runtime purposes, and revocation methods:

| Permission / Service | Purpose | Data Handling & Scope | Revocation Method |
|---|---|---|---|
| **`READ_LOGS`** | Used by `CecLogReaderService` to intercept HDMI-CEC messages (`<Active Source>`, `<Routing Change>`). | **Strictly filtered in code**: Only reads log entries tagged with `HdmiCecController`, `HdmiCecLocalDeviceTv`, `HdmiCecLocalDevice`, `HdmiControlService`, `HdmiCecNetwork`, and `HdmiCecMessage`. **Zero data is written to disk or transmitted over the network.** | `adb shell pm revoke com.lnu.tclhdmilauncher android.permission.READ_LOGS` |
| **Accessibility Service (`WakeAccessibilityService`)** | Intercepts `KEYCODE_HOME` key events and listens for `ACTION_SCREEN_ON` wake events to prevent boot-to-black or AV bugs. | Processes only window state change events from stock launchers and key events. **Does not collect user input, credentials, or personal information.** | TV Settings ➔ Device Preferences ➔ Accessibility ➔ Toggle OFF |
| **Shizuku API (`moe.shizuku.manager.permission.API_V23`)** | Automates granting `READ_LOGS` / `DUMP` and allows freezing unused background TV apps from the App Drawer. | Executes standard Android package management commands locally via IPC binder. **No telemetry or remote commands.** | Revoke permission inside the Shizuku Manager app at any time. |

---

## Troubleshooting & In-Depth Fixes

> [!NOTE]
> Detailed technical analysis, logcat traces, and low-level root causes have been organized into a dedicated document:  
> 📖 **[Read the Full Troubleshooting Guide (docs/troubleshooting.md)](docs/troubleshooting.md)** (or [繁體中文版本](docs/troubleshooting_zh.md))

### Quick Summary of Key Fixes:

- **Fix 1: Apple TV / HDMI-CEC Standby Wake Glitch (Double-Power-Key Bug)**:  
  TCL's `com.tcl.tv` receives dual CEC standby frames and sends two power-key presses, waking the TV up immediately.  
  *Solution*: Completely uninstall `com.tcl.tv` for user 0 (`adb shell pm uninstall -k --user 0 com.tcl.tv`). Verified effective on TCL 65C715.
- **Fix 2: Stuck Dolby Vision / HDR 10 OEM Toast Notification**:  
  When running third-party launchers, `com.tcl.tv`'s DolbyToast timer fails and freezes on screen.  
  *Solution*: Deny overlay and toast permissions via AppOps (`adb shell appops set com.tcl.tv SYSTEM_ALERT_WINDOW deny` and `adb shell appops set com.tcl.tv TOAST_WINDOW deny`).
- **Fix 3: TvView Hardware Passthrough Black Screen & Compositor Glitch**:  
  Translucent window themes and Realtek RTD2851 HWC dirty-rect alpha animations can cause black screen or frozen dark shadows.  
  *Solution*: Use an opaque fullscreen theme, eliminate overlay toasts, and optionally set animation scales to 0 (`adb shell settings put global window_animation_scale 0`).  
  *Side Effect Warning*: Setting animation scale to 0 affects all apps across the TV. To revert: set scales back to 1.
- **Fix 4: True HDMI-CEC Auto-Switching via Logcat Monitoring**:  
  Bypasses TCL's broken auto-switch logic by monitoring `HdmiCecController` active source packets via `CecLogReaderService`. Requires `READ_LOGS` permission.

---

## Tested / Untested Hardware Compatibility Matrix

| Category | Device / Environment | Status | Verification Notes |
|---|---|---|---|
| **Verified Working** | TCL 65C715 (BeyondTV2) | ✅ **Tested & Verified** | Chassis: RTD2851 / R851T02; Kernel 4.14.76+ |
| **Verified Working** | Firmware `V8-R851T02-LF1V662.019382` | ✅ **Tested & Verified** | Android 9; TV+OS V5.2.0; Client ID `TCL-AP-RT2851-S1` |
| **Verified Working** | Apple TV 4K, PS5, Nintendo Switch | ✅ **Tested & Verified** | HDR10, Dolby Vision 4K 60Hz passthrough |
| **Untested** | TCL Android 11 Firmware (V7xx / V8xx) | ⚠️ **Untested in this project** | Community reports mention HWC2 composition issues, but not directly tested in this repo. <!-- TODO: 需作者確認 --> |
| **Untested** | Other TCL Chassis Platforms (RTD2885, T972, MT9615, etc.) | ⚠️ **Untested** | Hardware input IDs and CEC broadcast behaviors may differ. |
| **Untested** | Non-TCL Android TV Brands (Sony, Philips, Xiaomi) | ⚠️ **Untested** | Hardware passthrough IDs and OEM packages (`com.tcl.tvinput`) are TCL-specific. |

### Tested Model Information

- **Tested Model**: **TCL 65C715** (BeyondTV2)
- **Chassis Platform**: **RTD2851 / R851T02**
  - **Firmware Version**: `V8-R851T02-LF1V662.019382`
  - **TV+OS Version**: `V5.2.0 (V8-2204190-MF1V520)`
  - **Client ID**: `TCL-AP-RT2851-S1`
  - **Product ID**: `660`
  - **Android OS**: Android 9 (Kernel `4.14.76+`)

### Physical Device Input Mapping Table (R851T02 / C715 Tested)

| Input Source | Port | Hardware ID | Full TvInput ID |
|---|---|---|---|
| **HDMI 1** | 1 | `1413744128` | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744128` |
| **HDMI 2** | 2 | `1413744384` | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744384` |
| **HDMI 3** | 3 | `1413744640` | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744640` |

### ADB Testing for Input Switching

#### Method 1: Explicit Component Launch (Recommended)

```bash
# Switch to HDMI 1
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 1

# Switch to HDMI 2
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 2

# Switch to HDMI 3
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 3
```

#### Method 2: Standard Android TV Passthrough Intent

```bash
# Switch to HDMI 1
adb shell am start -a android.intent.action.VIEW \
  -d "content://android.media.tv/passthrough/com.tcl.tvinput%2F.passthroughinput.TvPassThroughService%2FHW1413744128"

# Switch to HDMI 2
adb shell am start -a android.intent.action.VIEW \
  -d "content://android.media.tv/passthrough/com.tcl.tvinput%2F.passthroughinput.TvPassThroughService%2FHW1413744384"

# Switch to HDMI 3
adb shell am start -a android.intent.action.VIEW \
  -d "content://android.media.tv/passthrough/com.tcl.tvinput%2F.passthroughinput.TvPassThroughService%2FHW1413744640"
```

---

## How It Works

```
TV Power-On / Sleep Wake / Home Button
                │
                ▼
       MainActivity.onCreate()
                │
        ┌───────┴───────┐
        ▼               ▼
   [Unattended]    [User Action]
        │               │
  Timer expires    Press 1/2/3 or OK
        │               │
        └───────┬───────┘
                │
                ▼
    HdmiViewerActivity (TvView)
                │
                ├─ Direct Tune ──► TvView.tune(inputId, channelUri)
                │                         │
                │                         ▼
                │                  Hardware Overlay
                │            (4K 60Hz, HDR10, Dolby Vision)
                │                         │
                │             [Press 1/2/3: Direct Switch]
                │             [Press BACK:  Exit to Launcher]
                │
                └─ Cold boot not ready ──► Retry up to 3 times (1500ms delay)
```

**Architectural Principles:**
- **Zero OEM App Dependency**: Directly binds to Android TIF hardware passthrough pipeline via `TvView.tune()`, bypassing `com.tcl.tv`.
- `excludeFromRecents="true"` — Prevents cluttering recent apps.
- `singleTask` — Avoids duplicate Activity stack creation.
- Non-blocking Handler retry — Built-in fault tolerance while system TV input services initialize on cold boot.

---

## Technical Specifications

| Parameter | Value |
|---|---|
| `minSdk` | 25 (Android 7.1) |
| `targetSdk` | 36 |
| `compileSdk` | 37 |
| AGP | 9.2.1 |
| Gradle | 9.4.1 (Java 25 JBR / Android Studio Ladybug+) |
| Dependencies | AndroidX Leanback (`androidx.leanback:leanback:1.0.0`), AndroidX AppCompat |
| Theme Design | **Pure Black (`#000000`)**, ForceDark disabled |
| Localization | English, Traditional Chinese (繁體中文), Simplified Chinese (简体中文) |
| Optimization | R8 fullMode minification, dead-code stripping & resource shrinking |
| Resident Memory | Minimal footprint; runs without background services when Wake Guard & CEC are disabled. Low-overhead foreground service when enabled. |

---

## Recommended Projects

- [spocky/miproja1](https://github.com/spocky/miproja1) - Projectivity Launcher for Android TV / Google TV
