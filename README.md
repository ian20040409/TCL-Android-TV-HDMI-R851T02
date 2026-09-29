# TCL HDMI Launcher

<p align="left">
  <b>English</b> | <a href="README_zh.md">繁體中文</a>
</p>

> **Designed specifically for enthusiasts and minimalists who treat their TCL Android TV as a pure external display / monitor.**  
> Completely eliminate TCL's sluggish, ad-ridden stock launcher. Boot directly into your external devices (Apple TV 4K / PS5 / Android TV Box / Blu-ray Player) in sub-milliseconds, transforming your TV into a distraction-free, high-performance display!

---

## Table of Contents

- [Why This Project? (Core Pain Points & Use Cases)](#why-this-project-core-pain-points--use-cases)
- [Ideal Home Theater Setup Example](#ideal-home-theater-setup-example)
- [Key Highlights & Features](#key-highlights--features)
- [Screenshots](#screenshots)
- [Remote Control Shortcuts](#remote-control-shortcuts)
- [Installation & Setup Guide](#installation--setup-guide)
  - [Step 1: Build the APK](#step-1-build-the-apk)
  - [Step 2: Enable ADB on Your TCL TV & Install](#step-2-enable-adb-on-your-tcl-tv--install)
  - [Step 3: Set as Default TV Launcher](#step-3-set-as-default-tv-launcher)
  - [Step 4: Disable the Bloated TCL Stock Launcher (Recommended)](#step-4-disable-the-bloated-tcl-stock-launcher-recommended)
  - [Step 5: Enable Wake Guard & Home Button Mapper (Highly Recommended)](#step-5-enable-wake-guard--home-button-mapper-highly-recommended)
- [Troubleshooting & In-Depth Fixes](#troubleshooting--in-depth-fixes)
  - [Fix 1: Apple TV / HDMI-CEC Standby Wake Glitch (Double-Power-Key Bug)](#fix-1-apple-tv--hdmi-cec-standby-wake-glitch-double-power-key-bug)
  - [Fix 2: Stuck Dolby Vision / HDR 10 OEM Toast Notification](#fix-2-stuck-dolby-vision--hdr-10-oem-toast-notification)
  - [Fix 3: TvView Hardware Passthrough Black Screen (invalid sideband 0/0) & Compositor Glitch](#fix-3-tvview-hardware-passthrough-black-screen-invalid-sideband-00--compositor-glitch)
- [Tested Device & Input Mapping](#tested-device--input-mapping)
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
- **TCL Android TV / Google TV Users**: You own a TCL Smart TV and want an ultra-fast, bloatware-free launcher tailored specifically to TCL's native display and TV input framework (`com.tcl.tvinput`).
- **External Input Purists**: You rely almost entirely on external high-performance hardware, and your TV's built-in Smart TV OS is merely a "panel driver":
  - 🍏 **Apple TV 4K** — Primary platform for streaming, movies, and TV shows.
  - 🎮 **PlayStation 5 (PS5) / Xbox Series X / Nintendo Switch** — Next-gen 4K HDR gaming.
  - 📺 **Dedicated TV Boxes** (Chromecast with Google TV, NVIDIA Shield, Fire TV, etc.).
  - 💿 **4K UHD Blu-ray / DVD Players** or **AV Receivers (eARC)**.
- **Plagued by TCL's Infamous HDMI-CEC Firmware Flaws**:
  - **Reboot Loops & Standby Glitches**: When putting Apple TV into sleep, TCL's flawed firmware handles CEC standby frames by injecting two power button keycodes within 95ms, immediately waking the TV back up after shutting off.
  - **AVR / Soundbar eARC Handshake Drops**: Bloated OEM background daemons congest CPU and bus cycles, causing CEC timing violations and audio dropouts or lost volume sync on external sound systems.
- **Tired of Bloated & Ad-Heavy Stock Launchers**: The factory TCL launcher is slow to boot, clutters the screen with unwanted video recommendations, and consumes precious RAM and background CPU cycles.
- **Want a True "Instant-On" Display Experience**: When you power on the TV, it should act like a traditional monitor or high-end display—automatically switching to your favorite input (e.g., Apple TV) within seconds without you ever having to touch a remote.
- **Lightning-Fast Multi-Device Switching**: When switching between a gaming console (PS5) and a streaming box (Apple TV), you want instant switching at the press of a single remote number button (`1` / `2` / `3`), without navigating clunky input menus.

---

## Ideal Home Theater Setup Example

```
                 ┌─────────────────────────────────┐
                 │     TCL 4K QLED / Mini-LED TV   │
                 │      (Pure Monitor / Display)   │
                 └────────────────┬────────────────┘
                                  │
      ┌───────────────────────────┼───────────────────────────┐
      │                           │                           │
  [ HDMI 1 ]                  [ HDMI 2 ]                  [ HDMI 3 (eARC) ]
      │                           │                           │
      ▼                           ▼                           ▼
🎮 PlayStation 5            📺 Blu-ray Player / Switch    🍏 Apple TV 4K / AVR
(Press remote "1" to switch) (Press remote "2" to switch)  (Default: Auto-boots in 3s)
```

- **Default Scenario**: On power-up, a 3-second countdown (customizable) automatically transitions straight into **HDMI 3 (Apple TV 4K)** with zero button presses required.
- **Gaming Scenario**: When you're ready to game, press numeric key `1` on your remote to instantly switch to **HDMI 1 (PS5)**.
- **Picture & Sound Calibration**: The top-right pill button takes you straight to native TCL Picture & Sound settings (`com.tcl.settings`) without ever displaying the TCL home screen.

---

## Key Highlights & Features

1. **Auto-Switch Countdown on Boot / Home**:
   - Customizable countdown timer: `Off`, `1s`, `2s`, `3s (Default)`, `5s`, `10s`, `15s`, `30s`.
   - On TV startup or pressing the Home button, automatically switches to your designated default HDMI port once the timer expires.
   - Touching any D-pad direction button during countdown cancels the timer, letting you browse inputs at your leisure.
2. **Instant Remote Number Key Switching**:
   - Press `1`, `2`, or `3` on your remote keypad to jump straight to the respective HDMI port with zero input lag.
3. **Set Default Boot Input (Long-Press OK)**:
   - Long-press the OK button on any HDMI card to set it as your persistent default startup input.
4. **Ultra-Lightweight, Zero Background Services, Zero GC Pressure**:
   - **Only ~9.0 KB** after full R8 minification and resource shrinking.
   - Built 100% in code (0 XML layout inflation overhead, 0 reflection).
   - Hot-path countdown timer achieves **0 heap memory allocations per second (0 GC)**.
   - Immediately calls `finishAndRemoveTask()` upon switching, leaving **zero background resident memory**—giving 100% of TV chipset resources to 4K video and audio decoding.
5. **Pure Black OLED / Dark Room Friendly UI (`#000000`)**:
   - Eliminates blinding white flashes in dark home theater rooms and optimizes local dimming on QLED / Mini-LED panels.
6. **Multi-Language Support**:
   - Fully localized in English, Traditional Chinese (繁體中文), and Simplified Chinese (简体中文) based on your system locale.
7. **Essential TV Features Preserved**:
   - **TCL Settings Shortcut**: Single-click access to native TCL picture/audio adjustment pages.
   - **Lightweight App Drawer**: An ultra-fast, on-demand list for occasional built-in or sideloaded TV apps with recents and long-press uninstall/disable management.
8. **App Mode & Auto-Open App**:
   - Single-click toggle for "App Mode" on the top status bar. When enabled, booting or pressing Home opens the App Drawer directly.
   - Long-press any app in the App Drawer to set it to "Auto-Open". After the countdown timer expires, it automatically launches that app (e.g. YouTube, Netflix, Plex).
9. **Self-Contained Native HDMI Viewer (`TvView`) — Zero Dependency on `com.tcl.tv`**:
   - Includes a built-in fullscreen HDMI Viewer (`HdmiViewerActivity`) powered directly by Android's TV Input Framework (`android.media.tv.TvView`).
   - Completely eliminates dependency on TCL's factory TV player (`com.tcl.tv.TVActivity`).
   - Full 4K @ 60Hz, HDR10, and Dolby Vision passthrough with hardware overlay decoding.
   - **Silent 0 Overlay 0 Toast Hardware Passthrough (Zero Ghosting, Zero Shadow Artifacts)**:
     - **Pristine Direct Output**: `HdmiViewerActivity` displays no Toast and renders no custom overlay views, designating `TvView` as the sole ContentView.
     - **Eliminates TV GPU Cache Glitch**: On Realtek RTD2851 SoCs, window alpha fade-out animations (including Android system Toast fade-outs) freeze the semi-transparent alpha bounding box on top of the video plane, leaving a persistent dark/black rectangular shadow. Completely silencing all toasts and overlays guarantees 100% clean, artifact-free video.
     - **Hot-Plug & Power-On Detection**: When external devices power on or connect, video illuminates instantly and smoothly without obstruction.
   - **Independent Task Window & Deterministic Hardware ID Mapping**:
     - Utilizes an independent task window stack (`FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_MULTIPLE_TASK` with custom `taskAffinity`) to isolate playback lifecycle from the launcher.
     - Maps directly to R851T02 hardware chip inputs (`HW1413744128`, `HW1413744384`, `HW1413744640`) with full `onNewIntent` handling, fixing the OEM TIF bug where CEC/AV inputs distorted string sorting and caused HDMI 1/2 to switch to HDMI 3.
   - You can now **safely uninstall or disable `com.tcl.tv` entirely** via ADB without losing HDMI display capability!

---

## Screenshots

| Main Screen (HDMI Input Selector) | Countdown Timer Settings Dialog |
|:---:|:---:|
| ![Main Screen](readme_pic/Screenshot_20260925_222244.png) | ![Countdown Settings](readme_pic/Screenshot_20260925_222304.png) |
| **Native TCL Settings Shortcut** | **App Drawer** |
| ![TCL Settings](readme_pic/Screenshot_20260925_222322.png) | ![App Drawer](readme_pic/Screenshot_20260925_222344.png) |
| **System App Management (Hold OK)** | **Third-Party App Management (Hold OK)** |
| ![System App Management](readme_pic/Screenshot_20260925_222413.png) | ![Third-Party App Management](readme_pic/Screenshot_20260925_222455.png) |

---

## Remote Control Shortcuts

| Remote Button | Action & Behavior |
|---|---|
| **D-Pad (Arrows)** | Move card focus; pressing any arrow key during countdown **cancels auto-switch** |
| **OK / Enter** | Switch immediately to focused HDMI input (or launch selected app) |
| **Long-Press OK (Main View)** | Set currently focused HDMI port as the **default startup input** |
| **Long-Press OK (App Drawer)** | Open management dialog: **Set as Auto-Open**, Uninstall, Disable, App Info |
| **Number Keys `1` / `2` / `3`** | **Instant Switch**: Jump straight to HDMI 1 / 2 / 3 in both Launcher and Viewer |
| **MENU** | Open countdown settings (Launcher) / Return to Launcher (Viewer) |
| **SETTINGS** | Launch native TCL settings (`com.tcl.settings`) |
| **BACK** | Exit dialogs / return to launcher from Viewer or App Drawer |

---

## Installation & Setup Guide

### Step 1: Build the APK

```bash
# Build Debug APK
./gradlew assembleDebug

# Or build ultra-optimized Release APK (~9.0 KB)
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
*Pressing the remote's "Home" button will now open this clean HDMI launcher.*

### Step 4: Disable the Bloated TCL Stock Launcher (Recommended)

To completely prevent the factory launcher from running or waking in the background:

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
1. **Standby Wake Guard**: Guaranteed 100% return to this Launcher on sleep wake (preventing AV input / no signal).
2. **Button Mapper (Home Key Redirection)**: No matter which app you are in, pressing the remote **Home button** is intercepted to open this Launcher directly!

**How to enable:**
- In the Launcher top bar, click the "Wake Guard" button (`⚠️ Wake Guard: Off`) and select "Open Settings" (or manually go to TV Settings ➔ Device Preferences ➔ Accessibility).
- Toggle **"HDMI Launcher Wake & Home Button Mapper"** to ON.

---

## Troubleshooting & In-Depth Fixes

### Fix 1: Apple TV / HDMI-CEC Standby Wake Glitch (Double-Power-Key Bug)

#### Problem
When putting the TV into standby via Apple TV or other HDMI-CEC connected devices, certain TCL TVs (such as C715 / RTD2851 platforms) may briefly turn off the screen, only to turn right back on within 1 second and launch the TCL TV app, or appear not to turn off at all.

#### Root Cause (Logcat Analysis)
1. **Apple TV Dual CEC Standby Commands**:  
   Per HDMI-CEC specifications, when Apple TV goes to sleep, it sends two consecutive Standby frames within ~79ms (a broadcast frame `4F:36` followed by a unicast frame `40:36`).
2. **TCL Firmware Flaw**:  
   TCL's `TclPowerManagerService` dispatches a `com.tcl.voicestandby` broadcast for every standby command received.  
   The built-in `com.tcl.tv` package has a `VoicePowerBroadcastReceiver` that intercepts this broadcast and **simulates pressing the hardware power button (`keyCode 4000 -> 26 KEYCODE_POWER`)**.
3. **Double Power Key Injection Within 95ms**:  
   - **First Key Press**: The TV screen turns off and enters sleep mode (`interactive=false`).  
   - **Second Key Press** (~95ms later): Arrives while the device is sleeping, which **immediately wakes the TV back up** (and can even trigger Android's double-press power button camera gesture)!

#### The Ultimate Solution: Completely Uninstall for User 0 (Cleanest & Most Effective!)

Because this app now includes a built-in native HDMI Viewer (`HdmiViewerActivity`) using Android TIF (`android.media.tv.TvView`), **you no longer need `com.tcl.tv` at all!**

Empirical tests show that **`pm uninstall -k --user 0 com.tcl.tv` works best**, completely severing `com.tcl.tv` from being awakened or pushed to the foreground by `system_server`, which is much cleaner than just `pm disable-user`:

```bash
# 1. Completely uninstall com.tcl.tv for User 0 (Most effective, cleanest solution!)
adb shell pm uninstall -k --user 0 com.tcl.tv

# 2. (Optional) Disable double-tap power camera gesture
adb shell settings put secure camera_double_tap_power_gesture_disabled 1
```

> [!TIP]
> **Fully Reversible at Any Time:**  
> If you ever wish to restore the factory `com.tcl.tv` package, simply run:
> ```bash
> adb shell cmd package install-existing com.tcl.tv
> ```

> [!NOTE]
> **Crucial Architectural Distinction: `com.tcl.tv` (Signal UI App) vs `com.tcl.tvinput` (Hardware HAL)**  
> - **`com.tcl.tv` (OEM Signal UI Application)**: In essence, this is a **pure frontend UI player**. It is specifically responsible for drawing all "signal status OSD elements" (source switching banners, top-right Dolby Vision / HDR badges, "No Signal" prompt screens, TV channel guides, etc.) and contains the problematic `VoicePowerBroadcastReceiver`. Because its internal UI timers assume it is the sole foreground app, running alongside third-party launchers causes timer freezes, overlay leaks, and black screen glitches. **With our built-in native TIF viewer handling video directly, this UI package can be 100% safely uninstalled without affecting HDMI video passthrough in any way!**  
> - **`com.tcl.tvinput` (Low-Level Hardware HAL Service)**: Low-level hardware abstraction service (`TvPassThroughService`) communicating directly with the Realtek RTD2851 SoC. It manages HDMI Rx physical handshakes, HDCP decryption keys, and hardware sideband punch-hole composition. **This service is the hardware video engine and must remain enabled permanently.**

> [!TIP]
> **Alternative: Disabling package or individual components:**  
> If you prefer not to uninstall:  
> - Disable package: `adb shell pm disable-user --user 0 com.tcl.tv`  
> - Disable component: `adb shell pm disable com.tcl.tv/com.tcl.tv.receiver.VoicePowerBroadcastReceiver`

---

### Fix 2: Stuck Dolby Vision / HDR 10 OEM Toast Notification

#### Problem
When switching an external source (Apple TV 4K, PS5, Xbox) to Dolby Vision or HDR formats, TCL's system overlays an OEM Dolby Vision / HDR banner in the top-right corner. When running a custom launcher or standalone viewer, this banner frequently **stays permanently frozen on the screen and never dismisses**, even if `com.tcl.tv` was disabled via `pm disable`.

#### Root Cause (DEX Decompilation & Logcat Analysis)
1. **Persistent System UID Process Cannot Be Killed**:  
   `com.tcl.tv` declares `android:persistent="true"` with system privilege `uid=1000 (system)`. Even after `pm disable` or `kill`, Android Zygote immediately respawns it within milliseconds.
2. **Broken Auto-Dismissal Timer Logic (Empirically Confirmed in Logcat)**:  
   Decompiling `/product/app/TIF_LiveTV/TIF_LiveTV.apk` (`com.tcl.tv`) and `/product/app/SystemSettings/SystemSettings.apk` (`com.tcl.settings`) reveals:
   - When the hardware detects HDR/Dolby Vision mode changes, `tcl_system_server` broadcasts `com.tcl.Hdr`.
   - `com.tcl.tv`'s `DolbyToast` class calls `WindowManager.addView` to insert a floating overlay (`CToast`).
   - Critically, `DolbyToast.checkShowDolby()` checks `isTVTop` (whether `com.tcl.tv.TVActivity` is currently the foreground app). When a third-party launcher or standalone HDMI viewer is active, real-device Logcat precisely captures:
     ```text
     com.tcl.tv        D  checkShowDolby() isTVTop = false
     system_server     I  mayAddFloatingWindow w = Window{2ad38c3 u0 Toast}
     ```
   - Because `isTVTop == false`, the scheduled dismiss timer (`mHide`) fails, is canceled, or overridden, leaving the floating window **permanently stuck on screen**!
   - Concurrently, `com.tcl.settings.receiver.HdrReceiver` intercepts the broadcast and invokes `Toast.show()`, compounding the visual clutter.

#### The Ultimate Solution: Granular AppOps Permission Configuration

Android features a powerful permission manager (`appops`). Detailed testing reveals:
- **`com.tcl.tv`**: The stuck DolbyToast is a custom overlay window (`CToast`, `SYSTEM_ALERT_WINDOW`). **Both overlay and toast permissions must be denied.**
- **`com.tcl.settings`**: The TCL picture/sound settings menu itself renders as a system overlay (`type:2003`). Therefore, **we must keep `SYSTEM_ALERT_WINDOW allow` and only deny `TOAST_WINDOW`**. This suppresses `HdrReceiver`'s toast while keeping the native picture/audio adjustment dialog 100% functional!

Run the following commands via ADB:

```bash
# 1. Completely block overlay and toast permissions for com.tcl.tv (kills stuck DolbyToast)
adb shell appops set com.tcl.tv SYSTEM_ALERT_WINDOW deny
adb shell appops set com.tcl.tv TOAST_WINDOW deny

# 2. Block only toast permission for com.tcl.settings (preserves native settings menu)
adb shell appops set com.tcl.settings TOAST_WINDOW deny
adb shell appops set com.tcl.settings SYSTEM_ALERT_WINDOW allow
```

> [!NOTE]
> `appops` modifications are written directly to the TV's `/data/system/appops.xml` and **persist across reboots**.

---

### Fix 3: TvView Hardware Passthrough Black Screen (invalid sideband 0/0) & Compositor Glitch

#### Symptom: Screen Completely Stuck on Black Screen When Launching HDMI Viewer
When entering `HdmiViewerActivity`, the low-level hardware decoder successfully locks the stream, but the screen remains stuck on pure black. Logcat reports critical composition errors:
```text
VideoComposer  composer-rtk@2.1-service  E  invalid sideband 0xb6040750, 0/0
TclWinInjector system_server             I  mayAddFloatingWindow w = ...HdmiViewerActivity float
```

#### Low-Level Hardware Output Timeline (Real Device Logcat Trace)
Logcat captures the exact sequence from HDMI handshake to hardware video rendering on the Realtek RTD2851 platform:

1. **Hardware decoder lock & 4K 60Hz HDR handshake:**
   ```text
   2026-09-29 15:57:40.634  sitatvservice   D  SetInputRegion wId=0, x=0, y=0, w=3840, h=2160, HDRType=4
   2026-09-29 15:57:41.476  com.tcl.tvinput D  audioFormat= 0 width = 2160 height = 3840 videoFrameRate = 60.0
   ```
   *External device (Apple TV / PS5) delivers 4K 60Hz HDR signal; TV's low-level chip locks and begins decoding.*

2. **TvView triggers rendering callback:**
   ```text
   2026-09-29 15:57:41.502  com.lnu.tclhdmilauncher I  TvView onVideoAvailable: com.tcl.tvinput/.../HW1413744640 (video rendering active)
   ```
   *Our app precisely detects the exact millisecond video rendering begins.*

3. **Hardware Composer (HWC) invalid sideband glitch:**
   ```text
   2026-09-29 15:57:40.278  composer-rtk@2.1-service  E  invalid sideband 0xb6040750, 0/0
   2026-09-29 15:57:40.311  composer-rtk@2.1-service  E  invalid sideband 0xb6040d30, 0/0
   ```

#### In-Depth Root Cause Analysis
1. **`windowIsTranslucent=true` Double-Window Obstruction & Zero Dimensions**:
   - Setting `android:windowIsTranslucent="true"` causes Android WindowManager to treat the window as non-occluding (`r.occludesParent = false`).
   - **Fatal Occlusion (MainActivity Blocks Video Layer)**: Because the top window is translucent, the underlying `MainActivity` (with an opaque `#000000` black background) **is never hidden or stopped via `onStop()`**. SurfaceFlinger keeps rendering `MainActivity`'s black window directly on top of the bottom hardware video layer, entirely blocking HDMI video from reaching the display!
   - **TCL Floating Window Misdetection**: TCL's proprietary system injector (`TclWinInjector`) interprets translucent windows as floating windows (`float`). This feeds Realtek's display Hardware Composer (`RTKHWC2` / `VideoComposer`) invalid layer dimensions (width and height set to 0, logged as `invalid sideband ..., 0/0`), preventing video output.
2. **Realtek RTD2851 Hardware Composer Glitch: Dirty Rect Alpha Freeze (Affecting Even System Volume Dialogs)**:
   - Rigorous testing on real hardware reveals that during 4K 60Hz HDR / Dolby Vision hardware sideband playback, **not only Toasts, but even the standard Android "System Volume Dialog" leaves a permanent dark rectangular shadow burned into the screen upon fading out!**
   - **Low-Level Mechanism**: Realtek RTD2851's HWC utilizes "Dirty Rect" incremental updates to reduce power and memory bus load. When any UI element (volume bar, Toast, dialog) plays an exit fade animation (`alpha: 1.0 -> 0.0`, captured as `AnimatingExit` in logcat), at the very last moment before opacity reaches zero, the HWC misinterprets the bounding box as having "no update", **freezing the semi-transparent alpha transition buffer permanently in the display composer layer** directly over the hardware video plane!

#### The Ultimate Fix & Comprehensive System Optimizations

1. **App-Side Pure Minimalization (Opaque Fullscreen Theme + 0 View 0 Overlay 0 Toast Direct Passthrough)**:
   - In `Theme.HdmiViewer`, remove `windowIsTranslucent=true` and use a standard fullscreen theme to ensure `MainActivity` immediately halts via `onStop()` and leaves the composition stack.
   - Completely remove `blackCoverLayout`, progress spinners, and text views. `tvView` is set as the sole `ContentView`.
   - `HdmiViewerActivity` never triggers any `Toast` or overlay windows, ensuring 100% silent input switching and video lock.
2. **System-Level Fix: Disable System Animation Scales (Eradicates Volume Bar & Pop-up Shadows Permanently)**:
   - Because this SoC defect is triggered specifically by **alpha fade-out transitions**, setting Android's animation scales to 0 forces all system dialogs and volume bars to appear and disappear instantaneously (0ms, no alpha transition frames). This enables HWC to cleanly switch layer states with zero dirty rect buffer freezes:
   - Execute the following ADB commands:
     ```bash
     # Disable window animations, transition animations, and animator duration scales
     adb shell settings put global window_animation_scale 0
     adb shell settings put global transition_animation_scale 0
     adb shell settings put global animator_duration_scale 0
     ```
3. **Underlying Session Race Condition & Frozen Video Fix (Critical Bugfix)**:
   - **Root Cause**: Calling `tvView.reset()` synchronously right before `tvView.tune()` creates an asynchronous race condition within the HAL (releasing takes ~250ms). This causes `grantMediaResource` to throw a `NullPointerException: getPackageName()`, prompting the HAL decoder to fire `onVideoUnavailable(reason=0)` and freezing the video stream permanently on the last decoded frame!
   - **Solution Architecture**:
     1. Eliminate premature `tvView.reset()` calls inside `tuneToPort()`; let TIF handle session transitions smoothly.
     2. Properly invoke `tvView.reset()` in `onStop()` to completely release the hardware session when leaving the foreground.
     3. Implement a 600ms self-healing auto re-tune mechanism when `onVideoUnavailable(reason=0)` is caught, immediately unfreezing the hardware decoder.
4. **Ultimate Firmware Solution: Downgrade to Android 9 Official Firmware (`V8-R851T02-LF1V662`)**:
   - **Root Cause of Android 11 Issues**: In TCL Android 11 (V7xx / V8xx) firmware builds, TCL ported modern HWC2 drivers onto the aging Realtek RTD2851 SoC. This resulted in `VideoComposer` sideband buffer deadlocks (`invalid sideband ... 0/0`), severe memory leaks, random HDMI frame freezes, eARC audio dropouts, and layer contention with `com.tcl.tv`.
   - **V662 (Android 9) Verified**:
     - `V8-R851T02-LF1V662` is widely recognized by XDA and 4PDA developer communities as the **most stable, rock-solid, and mature firmware** for the R851T02 chassis.
     - **100% Cures HDMI Freeze Bugs**: Android 9 utilizes the mature native SurfaceView rendering pipeline without sideband dirty-rect cache lockups.
     - **Massive RAM & Performance Boost**: Eliminates bloated Android 11 background watchers (`com.tcl.guard`), freeing up ~300MB–500MB of RAM. Wake-up latency and HDMI lock-on are virtually instantaneous.
     - **Flashing Method**: Downgrading from Android 11 requires placing `Update.img` on a FAT32 USB drive and performing a force-flash (holding the hardware power button while plugging in AC power).
5. **Instant Buffer Flush Trick (for Android 11 users)**:
   - If a dark shadow is already frozen on screen before applying the settings, simply press the remote's **Home** button to return to the Launcher (the full-screen standard view tree completely overwrites and flushes the HWC buffer), then press OK to re-enter HDMI. The shadow will be completely gone!

---

## Tested Device & Input Mapping

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

This project supports two methods for switching HDMI ports via ADB:

#### Method 1: Explicit Component Launch (Recommended, Instant Direct Jump)

```bash
# Switch to HDMI 1
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 1

# Switch to HDMI 2
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 2

# Switch to HDMI 3
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 3
```

#### Method 2: Standard Android TV Passthrough Intent (Implicit Intent Resolution)

> [!NOTE]
> `HdmiViewerActivity` declares filters for `android.media.tv` and MIME types `vnd.android.cursor.item/channel` & `vnd.android.cursor.dir/channel`. Even with `com.tcl.tv` fully disabled, these standard Android TV passthrough intents will be automatically resolved and handled by `HdmiViewerActivity`:

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
                │             [Press 1/2/3: Instant Switch]
                │             [Press BACK:  Exit to Launcher]
                │
                └─ Cold boot not ready ──► Retry up to 3 times (1500ms delay)
```

**Architectural Principles:**
- **Zero OEM App Dependency**: Directly binds to Android TIF hardware passthrough pipeline via `TvView.tune()`, eliminating all `com.tcl.tv` bloat, ads, and bugs.
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
| Dependencies | **0 external dependencies** (100% native Android SDK) |
| Theme Design | **Pure Black (`#000000`)**, ForceDark disabled |
| Localization | English, Traditional Chinese (繁體中文), Simplified Chinese (简体中文) |
| Release APK Size | Only **~9.0 KB** after R8 fullMode optimization |
| Resident Memory | **0 MB** (Task self-terminates via `finishAndRemoveTask()`) |

---

## Recommended Projects

- [spocky/miproja1](https://github.com/spocky/miproja1) - Projectivity Launcher for Android TV / Google TV
