# TCL HDMI Launcher

<p align="left">
  <b>English</b> | <a href="README_zh.md">繁體中文</a>
</p>

[![Latest Release](https://img.shields.io/github/v/release/ian20040409/TCL-Android-TV-HDMI-R851T02?style=flat-square&color=blue)](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/ian20040409/TCL-Android-TV-HDMI-R851T02/total?style=flat-square)](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases)
[![License](https://img.shields.io/github/license/ian20040409/TCL-Android-TV-HDMI-R851T02?style=flat-square)](LICENSE)

> **Turn your TCL Android TV into a pure, high-performance display.**  
> Bypass TCL's sluggish, ad-ridden stock launcher. Boot directly into your external devices (Apple TV 4K, PS5, Switch, TV Box) with instant switching and zero clutter.

👉 **[Download Latest Release APK](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases/latest)**

---

## Highlights

- ⏱️ **Auto-Boot Countdown**: Automatically tunes to your default HDMI input on boot or Home press (any D-Pad key cancels auto-switch).
- 🔢 **Numeric Key Jump**: Press `1`, `2`, or `3` on your remote to jump straight to HDMI 1 / 2 / 3 (works in both Launcher & Viewer).
- 📺 **Native TvView Engine**: Built-in player directly powered by Android TV Input Framework (`TvView`), eliminating reliance on buggy `com.tcl.tv`.
- 🎮 **4K HDR & Dolby Vision**: Pristine 4K @ 60Hz passthrough without floating overlays or toast artifacts.
- 🧠 **Source-Aware Smart CEC Routing & Standby**:
  - Automatically matches standby signals with the active viewing port: only puts the TV to sleep if the standby request originates from the currently viewed input, preventing background devices (e.g. docked Switch) from turning off your TV.
  - Suppresses rogue `com.tcl.voicestandby` loops caused by TCL's firmware.
- 📱 **Revamped TV App Drawer**: Grid-based card UI tailored for TV remotes with category chips (All, Recent, TV, Mobile, System, Frozen) and one-click app freezing via Shizuku.
- 🚀 **Flexible App Mode**: Boot straight into a designated streaming app (e.g. YouTube, Netflix) or use the App Drawer as the default home screen.
- ⚡ **One-Click Shizuku Integration**: Built-in [Shizuku TV build](https://github.com/thedjchi/Shizuku/releases) APK downloader, effortlessly grant `READ_LOGS` and **automatically enable the Wake Guard Accessibility Service** via shell without manual navigation in system settings.
- 🖤 **Pure Black UI (`#000000`)**: Designed for dark home theater rooms and OLED/QLED local dimming.
- 🛠️ **Leanback Settings & Diagnostics**: Native 10-foot TV settings menu, remote button remapping (Button Mapper), direct shortcut to TCL picture/sound settings (`com.tcl.settings`), and built-in on-screen CEC live event inspector.

---

## Screenshots

| Main Screen (Input Selector) | Countdown Dialog | Native Picture Settings | App Drawer |
|:---:|:---:|:---:|:---:|
| ![Main](readme_pic/Screenshot_20260925_222244.png) | ![Countdown](readme_pic/Screenshot_20260925_222304.png) | ![Settings](readme_pic/Screenshot_20260925_222322.png) | ![Apps](readme_pic/Screenshot_20260925_222344.png) |

---

## Quick Start (4 Steps)

### 1. Install the APK
Download the APK from [GitHub Releases](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases/latest) and install via ADB:
```bash
adb connect <TV-IP>:5555
adb install -r app-release.apk
```

### 2. Set as Default TV Launcher
```bash
adb shell cmd package set-home-activity com.lnu.tclhdmilauncher/.MainActivity
```

### 3. Disable Stock Launcher (Recommended)
Prevent the factory launcher from running or waking in the background:
```bash
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell am force-stop com.google.android.tvlauncher
```
*(Reversible anytime via `adb shell pm enable com.google.android.tvlauncher`)*

### 4. Enable Wake Guard & CEC Auto-Switch (Recommended)

Wake Guard (Accessibility Service) prevents standby black-screen glitches and handles Home button redirection, while logcat inspection enables instant CEC auto-switching.

- **Option A: One-click via Shizuku (Recommended)**  
  If [Shizuku (Recommended TV build: thedjchi/Shizuku)](https://github.com/thedjchi/Shizuku/releases) is running on your TV (you can also download and install it directly within the launcher's settings), simply authorize Shizuku in launcher settings:
  - Automatically grants `READ_LOGS` permission.
  - **Automatically enables the Wake Guard Accessibility Service** via shell without digging through system menus.
- **Option B: Manual ADB Setup**  
  1. Grant log reading permission:
     ```bash
     adb shell pm grant com.lnu.tclhdmilauncher android.permission.READ_LOGS
     adb shell am force-stop com.lnu.tclhdmilauncher
     ```
  2. Click **Wake Guard** on the top status bar to enable the service in Android Accessibility settings (or enable directly via ADB):
     ```bash
     adb shell settings put secure enabled_accessibility_services com.lnu.tclhdmilauncher/.WakeAccessibilityService
     ```

---

## Remote Control Shortcuts

| Context | Button | Action |
|---|---|---|
| **Global** | **Number `1` / `2` / `3`** | Jump directly to HDMI 1 / 2 / 3 (in both Launcher & Viewer) |
| **Global** | **INPUT / SOURCE** | Cycle through HDMI 1 ➔ HDMI 2 ➔ HDMI 3 |
| **Launcher** | **D-Pad Arrows** | Move focus; pressing any arrow during countdown **cancels auto-switch** |
| **Launcher** | **OK / Enter** | Launch selected HDMI input or app (Long-press on HDMI card to set as default) |
| **Launcher** | **SETTINGS / MENU** | Open launcher settings menu |
| **HDMI Viewer** | **SETTINGS** | Open native TCL picture / sound settings (`com.tcl.settings`) |
| **HDMI Viewer** | **BACK / MENU** | Exit viewer and return to Launcher |
| **App Drawer** | **OK / Enter** | Launch selected app |
| **App Drawer** | **MENU / Long-press OK** | Open app options (App info, uninstall, freeze/unfreeze via Shizuku) |

---

## Verified Device & Input Mapping

Tested and verified on **TCL 65C715** (Chassis: RTD2851 / R851T02, Firmware: `V8-R851T02-LF1V662`, Android 9):

| Input | Port | TvInput ID |
|---|---|---|
| **HDMI 1** | 1 | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744128` |
| **HDMI 2** | 2 | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744384` |
| **HDMI 3** | 3 | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744640` |

---

## Privacy & Permissions

This project is open-source, non-commercial, and respects your privacy:
- **`READ_LOGS`**: Strictly filtered in code to inspect `HdmiCecController` tags for active source routing. Zero data stored or transmitted.
- **Accessibility Service (`WakeAccessibilityService`)**: Monitors window state changes and key events to prevent standby black-screen glitches. Does not collect user input or personal data.
- **Shizuku API**: Used exclusively for local IPC privileged commands (permission grant, auto-enabling accessibility, app freezing). No external network communication.

---

## Documentation & Troubleshooting Index

For deep technical analysis, root-cause teardowns, and firmware guides, explore:

- 📖 **[Troubleshooting Guide (docs/troubleshooting.md)](docs/troubleshooting.md)** ([繁體中文](docs/troubleshooting_zh.md))
  - *Fix 1*: Apple TV dual-standby power loop (safely remove `com.tcl.tv`)
  - *Fix 2*: Stuck Dolby Vision / HDR10 banner (AppOps permission solution)
  - *Fix 3*: TvView black screen & Realtek HWC compositor glitches
  - *Fix 4*: HDMI-CEC auto-switching via background logcat listener
- 📑 **[ADB Cheatsheet (docs/adb-cheatsheet.md)](docs/adb-cheatsheet.md)**: Ready-to-copy ADB commands.
- ⚙️ **[Firmware & Compatibility Guide (docs/firmware-guide.md)](docs/firmware-guide.md)**: Notes on Android 9 V662 downgrade and Android 11 status.
