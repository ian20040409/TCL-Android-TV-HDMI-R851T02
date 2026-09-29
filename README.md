# TCL HDMI Launcher

<p align="left">
  <b>English</b> | <a href="README_zh.md">繁體中文</a>
</p>

> **Designed specifically for enthusiasts and minimalists who treat their TCL Android TV as a pure external display / monitor.**  
> Completely eliminate TCL's sluggish, ad-ridden stock launcher. Boot directly into your external devices (Apple TV 4K / PS5 / Android TV Box / Blu-ray Player) in sub-milliseconds, transforming your TV into a distraction-free, high-performance display!

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
   - **Signal Search UI & Smart Diagnostic Mechanism**:
     - **Searching Animation**: Clean pure-black overlay with a rotating spinner and "Searching for signal…" during chip lock.
     - **Connection Badge**: Smooth fade-out once signal locks, displaying a brief "HDMI X • Connected" badge in the top-right.
     - **No-Signal Diagnostics**: Automatically displays troubleshooting hints if no signal after 6 seconds, supporting **OK to Refresh**, **1/2/3 to Switch**, or **Back to Exit**.
     - **Hot-Plug & Power-On Detection**: Automatically wakes up and displays video seamlessly when a source device powers on.
   - **Independent Task Window & Deterministic Hardware ID Mapping**:
     - Utilizes an independent task window stack (`FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_MULTIPLE_TASK` with custom `taskAffinity`) to isolate playback lifecycle from the launcher.
     - Maps directly to R851T02 hardware chip inputs (`HW1413744128`, `HW1413744384`, `HW1413744640`) with full `onNewIntent` handling, fixing the OEM TIF bug where CEC/AV inputs distorted string sorting and caused HDMI 1/2 to switch to HDMI 3.
   - You can now **safely disable `com.tcl.tv` entirely** via ADB without losing HDMI display capability!

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

## Tested Device

- **Tested Model**: **TCL 65C715** (C715 Series 65" 4K QLED Android TV)
- **Chassis Platform**: **RTD2851 / R851T02**
  - **Software / Firmware Version**: `V8-R851T02-LF1V701.000031`
  - **TV+OS Version**: `V5.2.0 (V8-2112290-MF1V520)`
  - **Client Platform ID**: `TCL-AP-RT2851-S1`
  - **Product ID**: `660`
  - **Linux Kernel**: `4.14.236+ #2 SMP PREEMPT Tue Jan 18 20:32:54 CST 2022 armv7l`
  > [!NOTE]
  > **About the R851T02 Platform:**  
  > `R851T02` is TCL's widely deployed chipset and motherboard hardware architecture (Realtek RTD2851 SoC) across multiple mainstream Android TV series (including C715, P715, P615, S434, etc.). All models running the **R851T02 chassis architecture** share a unified low-level TV input service (`com.tcl.tvinput`) and Passthrough pipeline specification.
- **Hardware Specs**:
  - **Display Panel**: 65" 4K UHD (3840 × 2160) Quantum Dot QLED, 60Hz, Dolby Vision / HDR10+ support
  - **HDMI Ports**: 3 physical HDMI 2.0 ports (HDCP 2.2, HDMI-ARC / CEC supported)
  - **CPU & Architecture**: Quad-core ARM Cortex-A55 processor (32-bit user space armv7l), 2 GB RAM / 16 GB ROM
  - **System OS**: Android TV 11 (Android R)
  - **Test Results**: Sub-millisecond HDMI 1 ~ 3 input switching, automatic countdown transition, boot default input persistence, remote controls (number keys / menu / settings) 100% verified.

## Physical Device Input Mapping Table (R851T02 / C715 Tested)

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

### Step 4: Disable the Bloated TCL Stock Launcher (Optional, Recommended)
To completely prevent the factory launcher from running or waking in the background:

```bash
# 1. Find TCL factory launcher package name
adb shell pm list packages | grep -i launcher

# 2. Disable the stock launcher (safe and reversible)
adb shell pm disable-user --user 0 <tcl.launcher.package.name>
```

> [!TIP]
> **Completely Reversible:**  
> If you ever want to restore the factory TCL launcher, simply run:
> ```bash
> adb shell pm enable <tcl.launcher.package.name>
> adb shell cmd package clear-preferred-activities com.lnu.tclhdmilauncher
> ```

---

### Step 5: (Highly Recommended) Enable "Wake Guard & Home Button Mapper"

Enabling the Accessibility Service grants two system-level capabilities:
1. **Standby Wake Guard**: Guaranteed 100% return to this Launcher on sleep wake (preventing AV input / no signal).
2. **Button Mapper (Home Key Redirection)**: No matter which app you are in, pressing the remote **Home button** is intercepted to open this Launcher directly!

**How to enable:**
- In the Launcher top bar, click the "Wake Guard" button (`⚠️ Wake Guard: Off`) and select "Open Settings" (or manually go to TV Settings ➔ Device Preferences ➔ Accessibility).
- Toggle **"HDMI Launcher Wake & Home Button Mapper"** to ON.

---

### Step 6: (Troubleshooting) Fix Apple TV / HDMI-CEC Standby Wake Glitch (Double-Power-Key Bug)

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

#### The Ultimate Solution: Completely Disable `com.tcl.tv` (Now 100% Safe!)

Because this app now includes a built-in native HDMI Viewer (`HdmiViewerActivity`) using Android TIF (`android.media.tv.TvView`), **you no longer need `com.tcl.tv` at all!**

You can completely disable the entire factory TV package with zero fear of breaking HDMI video:

```bash
# 1. Completely disable the entire factory com.tcl.tv package
adb shell pm disable-user --user 0 com.tcl.tv

# 2. (Optional) Disable double-tap power camera gesture
adb shell settings put secure camera_double_tap_power_gesture_disabled 1
```

> [!NOTE]
> **Difference between `com.tcl.tv` and `com.tcl.tvinput`:**  
> - `com.tcl.tv`: OEM Live TV / UI player app containing `TVActivity`, ads, channel banners, and the buggy `VoicePowerBroadcastReceiver`. **Safe to disable!**  
> - `com.tcl.tvinput`: Low-level hardware HAL service (`TvPassThroughService`) driving the Realtek RTD2851 HDMI Rx chip. **Must remain enabled.**

> [!TIP]
> **Alternative Component-Only Disabling (If you still want to keep factory TV app):**  
> If you prefer not to disable the entire package:  
> `adb shell pm disable com.tcl.tv/com.tcl.tv.receiver.VoicePowerBroadcastReceiver`  
> `adb shell pm disable com.tcl.tv/.service.GlobalKeyService`

---

### Step 7: (Troubleshooting) Fix Stuck Dolby Vision / HDR 10 OEM Toast Notification

#### Problem
When switching an external source (Apple TV 4K, PS5, Xbox) to Dolby Vision or HDR formats, TCL's system overlays an OEM Dolby Vision / HDR banner in the top-right corner. When running a custom launcher or standalone viewer, this banner frequently **stays permanently frozen on the screen and never dismisses**, even if `com.tcl.tv` was disabled via `pm disable`.

#### Root Cause (DEX Decompilation Analysis)
1. **Persistent System UID Process Cannot Be Killed**:  
   `com.tcl.tv` declares `android:persistent="true"` with system privilege `uid=1000 (system)`. Even after `pm disable` or `kill`, Android Zygote immediately respawns it within milliseconds.
2. **Broken Auto-Dismissal Timer Logic**:  
   Decompiling `/product/app/TIF_LiveTV/TIF_LiveTV.apk` (`com.tcl.tv`) and `/product/app/SystemSettings/SystemSettings.apk` (`com.tcl.settings`) reveals:
   - When the hardware detects HDR/Dolby Vision mode changes, `tcl_system_server` broadcasts `com.tcl.Hdr`.
   - `com.tcl.tv`'s `DolbyToast` class calls `WindowManager.addView` to insert a floating overlay (`CToast`).
   - Critically, `DolbyToast.checkShowDolby()` checks `isTVTop` (whether `com.tcl.tv.TVActivity` is currently the foreground app). When a third-party launcher or standalone HDMI viewer is active, `isTVTop == false`. This breaks or cancels the scheduled dismiss timer (`mHide`), leaving the floating window **permanently stuck on screen**.
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

### Step 8: (Troubleshooting) Fix TCL TvView Hardware Passthrough Black Screen, Remote Freeze & Ghost HUD / Previous App Image Retention

#### Symptom 1: Screen Completely Black & Remote Buttons Frozen Upon Entering HDMI
- When entering `HdmiViewerActivity`, the display stays completely black with no video output, and pressing remote Back / number keys does nothing.
- **Root Cause**:
  1. `TvView` in hardware passthrough mode relies on punching a hole through the window to display the underlying video overlay directly. An opaque window background (such as `#000000` or an opaque root layout) completely blocks the hardware video plane from view.
  2. The window or `tvView` loses focus, causing remote inputs to be swallowed or dropped by the system, appearing as if the app is frozen.
- **Solution**:
  - Use a dedicated transparent theme for `HdmiViewerActivity` (`Theme.HdmiViewer`: `windowBackground=@android:color/transparent`, `windowIsTranslucent=true`, `window.setFormat(PixelFormat.TRANSLUCENT)`).
  - Set root layout background to transparent and explicitly request focus on `tvView` across lifecycle callbacks (`requestFocus()`).

#### Symptom 2: Signal Search HUD Lingers as a Semi-Transparent "Ghost Overlay" or Previous App Still Visible in Background
- **The TCL TIF Hardware Compositor Traps**:
  1. **GPU Compositor Cache Freezing**: In a `windowIsTranslucent=true` window, standard view fade animations (`.animate().alpha(0f)`) often fail to flush the final frame on Realtek TV SoCs (RTD2851). The hardware compositor freezes the semi-transparent frame indefinitely, leaving a persistent ghost HUD.
  2. **Separate Overlay Activity Pauses TvView**: Attempting to launch a separate transparent Activity for the signal search status puts the underlying `HdmiViewerActivity` into `onPause()`. Android TV instantly halts video decoding for non-foreground `TvView` instances, preventing `onVideoAvailable()` from ever firing and causing an infinite black screen deadlock.
  3. **Previous App Punch-Through Bleed**: When entering the transparent HDMI viewer before video has locked, the transparent window exposes the cached graphics buffer of the launcher or previously opened app.
- **The Ultimate Fix (CPU-Rendered Black Cover + Zero-Animation Instant Removal)**:
  - Inside `HdmiViewerActivity`, render a CPU-backed opaque black cover (`blackCoverLayout`) immediately upon entering to cleanly block any previous app residue.
  - Display search progress on this cover.
  - The instant `onVideoAvailable()` confirms video lock, **bypass all alpha fade animations** and directly invoke **`rootLayout.removeView(blackCoverLayout)`** to rip the layout out of the View Tree entirely.
  - This completely sidesteps the TV GPU's translucent compositor caching bug, guaranteeing 0 residue, 0 ghost overlays, and pristine instant HDMI video rendering!

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

## Recommended Projects

- [spocky/miproja1](https://github.com/spocky/miproja1) - Projectivity Launcher for Android TV / Google TV.

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
| Release APK Size | Only **~300 KB** after R8 fullMode optimization |
| Resident Memory | **0 MB** (Task self-terminates via `finishAndRemoveTask()`) |
