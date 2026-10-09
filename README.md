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

- ⏱️ **Auto-Boot Countdown**: Automatically tunes to your default HDMI input on boot or Home press.
- 🔢 **Numeric Key Jump**: Press `1`, `2`, or `3` on your remote to jump straight to HDMI 1 / 2 / 3.
- 📺 **Native TvView Engine**: Built-in player directly powered by Android TV Input Framework (`TvView`), eliminating reliance on buggy `com.tcl.tv`.
- 🎮 **4K HDR & Dolby Vision**: Pristine 4K @ 60Hz passthrough without floating overlays or toast artifacts.
- 🖤 **Pure Black UI (`#000000`)**: Designed for dark home theater rooms and OLED/QLED local dimming.
- 🛠️ **Leanback Settings & OOBE**: Native 10-foot TV UI setup wizard with direct shortcuts to TCL picture/sound settings (`com.tcl.settings`).

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

GitHub Actions builds the R8-optimized Release APK. Android requires APKs to be signed before installation; an in-place update that preserves app data also requires the exact signing key used by the installed version. Without CI signing secrets, the workflow artifact is unsigned and cannot be installed directly. GitHub does not automatically provide an Android signing identity. The maintainer must store the existing `.jks` and passwords as repository Actions Secrets; see [build and signing instructions](docs/compose-tv-migration.md).

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
- **Wake Guard (Accessibility Service)**: Enable it manually from system settings via **Wake Guard** on the top status bar, or grant the app Shizuku permission to enable the service automatically through shell. It helps ensure reliable wakeups and Home button redirection.
- **CEC Auto-Switch**: Grant log reading permission so the launcher can auto-route active HDMI devices on wake:
  ```bash
  adb shell pm grant com.lnu.tclhdmilauncher android.permission.READ_LOGS
  adb shell am force-stop com.lnu.tclhdmilauncher
  ```
  *(Or authorize automatically via the in-app **Shizuku** integration)*

---

## Remote Control Shortcuts

| Button | Action |
|---|---|
| **D-Pad Arrows** | Move focus; pressing any arrow during countdown **cancels auto-switch** |
| **OK / Enter** | Launch selected HDMI input or app (Long-press on HDMI card to set as default) |
| **Number `1` / `2` / `3`** | Jump directly to HDMI 1 / 2 / 3 (in both Launcher & Viewer) |
| **INPUT / SOURCE** | Cycle through HDMI 1 ➔ HDMI 2 ➔ HDMI 3 |
| **SETTINGS** | Open launcher settings / open native TCL picture settings (`com.tcl.settings`) |
| **BACK / MENU** | Exit current view or return to Launcher |

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
- **Shizuku API**: Used exclusively for local IPC privileged commands (permission grant, app freeze). No external network communication.

---

## Documentation & Troubleshooting Index

For deep technical analysis, root-cause teardowns, and firmware guides, explore:

- 🏗️ **[Architecture (docs/architecture.md)](docs/architecture.md)**: How the CEC log reader, Accessibility service and Shizuku work together (written in Traditional Chinese).
- **[Compose for TV migration](docs/compose-tv-migration.md)**: The home screen and App list now use stock TV Material 3; settings/OOBE migration is deferred. Includes validation and TCL device checks.

- 📖 **[Troubleshooting Guide (docs/troubleshooting.md)](docs/troubleshooting.md)**
  - *Fix 1*: Apple TV dual-standby power loop (safely remove `com.tcl.tv`)
  - *Fix 2*: Stuck Dolby Vision / HDR10 banner (AppOps permission solution)
  - *Fix 3*: TvView black screen & Realtek HWC compositor glitches
  - *Fix 4*: HDMI-CEC auto-switching via background logcat listener
- 📑 **[ADB Cheatsheet (docs/adb-cheatsheet.md)](docs/adb-cheatsheet.md)**: Ready-to-copy ADB commands.
- ⚙️ **[Firmware & Compatibility Guide (docs/firmware-guide.md)](docs/firmware-guide.md)**: Notes on Android 9 V662 downgrade and Android 11 status.
