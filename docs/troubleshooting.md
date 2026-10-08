# Troubleshooting & In-Depth Technical Fixes

This document details deep technical root causes, low-level traces, and workarounds for hardware, CEC, and firmware glitches encountered on TCL Android TV (specifically the RTD2851 / R851T02 chassis platform).

---

## Table of Contents
- [Fix 1: Apple TV / HDMI-CEC Standby Wake Glitch (Double-Power-Key Bug)](#fix-1-apple-tv--hdmi-cec-standby-wake-glitch-double-power-key-bug)
- [Fix 2: Stuck Dolby Vision / HDR 10 OEM Toast Notification](#fix-2-stuck-dolby-vision--hdr-10-oem-toast-notification)
- [Fix 3: TvView Hardware Passthrough Black Screen (invalid sideband 0/0) & Compositor Glitch](#fix-3-tvview-hardware-passthrough-black-screen-invalid-sideband-00--compositor-glitch)
- [Fix 4: True HDMI-CEC Auto-Switching (Bypassing TCL's Broken Auto-Switch)](#fix-4-true-hdmi-cec-auto-switching-bypassing-tcls-broken-auto-switch)

---

### Fix 1: Apple TV / HDMI-CEC Standby Wake Glitch (Double-Power-Key Bug)

#### Problem
When putting the TV into standby via Apple TV or other HDMI-CEC connected devices, certain TCL TVs (such as C715 / RTD2851 platforms) may briefly turn off the screen, only to turn right back on within 1 second and launch the TCL TV app, or appear not to turn off at all.

#### Root Cause (Logcat Analysis)
1. **Apple TV Dual CEC Standby Commands**:  
   Per HDMI-CEC specifications, when Apple TV goes to sleep, it sends two consecutive Standby frames within ~79ms (a broadcast frame `4F:36` followed by a unicast frame `40:36`).
2. **TCL Firmware Behavior**:  
   TCL's `TclPowerManagerService` dispatches a `com.tcl.voicestandby` broadcast for every standby command received.  
   The built-in `com.tcl.tv` package has a `VoicePowerBroadcastReceiver` that intercepts this broadcast and **simulates pressing the hardware power button (`keyCode 4000 -> 26 KEYCODE_POWER`)**.
3. **Double Power Key Injection Within 95ms**:  
   - **First Key Press**: The TV screen turns off and enters sleep mode (`interactive=false`).  
   - **Second Key Press** (~95ms later): Arrives while the device is sleeping, which **immediately wakes the TV back up** (and can even trigger Android's double-press power button camera gesture)!

#### Verified Solution: Completely Uninstall for User 0 (Verified effective on TCL 65C715 + V8-R851T02-LF1V662)

Because this app includes a built-in native HDMI Viewer (`HdmiViewerActivity`) using Android TIF (`android.media.tv.TvView`), **you no longer need `com.tcl.tv` at all!**

Empirical tests show that **`pm uninstall -k --user 0 com.tcl.tv` works best**, completely severing `com.tcl.tv` from being awakened or pushed to the foreground by `system_server`, which is much cleaner than just `pm disable-user`:

```bash
# 1. Completely uninstall com.tcl.tv for User 0 (Verified effective on TCL 65C715)
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
> - **`com.tcl.tv` (OEM Signal UI Application)**: In essence, this is a **pure frontend UI player**. It is specifically responsible for drawing all "signal status OSD elements" (source switching banners, top-right Dolby Vision / HDR badges, "No Signal" prompt screens, TV channel guides, etc.) and contains the problematic `VoicePowerBroadcastReceiver`. Because its internal UI timers assume it is the sole foreground app, running alongside third-party launchers causes timer freezes, overlay leaks, and black screen glitches. With our built-in native TIF viewer handling video directly, this UI package can be uninstalled without affecting HDMI video passthrough (verified on TCL 65C715).  
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

#### Recommended Solution: Granular AppOps Permission Configuration (Verified effective on TCL 65C715)

Android features a permission manager (`appops`). Detailed testing reveals:
- **`com.tcl.tv`**: The stuck DolbyToast is a custom overlay window (`CToast`, `SYSTEM_ALERT_WINDOW`). Both overlay and toast permissions must be denied.
- **`com.tcl.settings`**: The TCL picture/sound settings menu itself renders as a system overlay (`type:2003`). Therefore, we must keep `SYSTEM_ALERT_WINDOW allow` and only deny `TOAST_WINDOW`. This suppresses `HdrReceiver`'s toast while keeping the native picture/audio adjustment dialog functional.

Run the following commands via ADB:

```bash
# 1. Completely block overlay and toast permissions for com.tcl.tv (suppresses stuck DolbyToast)
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

#### Low-Level Hardware Output Timeline (Real Device Logcat Trace on TCL 65C715)
Logcat captures the sequence from HDMI handshake to hardware video rendering on the Realtek RTD2851 platform:

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
   *Our app detects video rendering beginning.*

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
2. **Realtek RTD2851 Hardware Composer Behavior: Dirty Rect Alpha Freeze (Affecting Even System Volume Dialogs)**:
   - Testing on real hardware reveals that during 4K 60Hz HDR / Dolby Vision hardware sideband playback, **not only Toasts, but even the standard Android "System Volume Dialog" leaves a dark rectangular shadow on the screen upon fading out!**
   - **Low-Level Mechanism (Inferred / 推測)**: Realtek RTD2851's HWC utilizes "Dirty Rect" incremental updates to reduce power and memory bus load. When any UI element (volume bar, Toast, dialog) plays an exit fade animation (`alpha: 1.0 -> 0.0`, captured as `AnimatingExit` in logcat), at the very last moment before opacity reaches zero, the HWC is suspected of misinterpreting the bounding box as having "no update", leaving the semi-transparent alpha transition buffer in the display composer layer directly over the hardware video plane.

#### Verified Fix & System Optimizations

1. **App-Side Minimalization (Opaque Fullscreen Theme + 0 View 0 Overlay 0 Toast Direct Passthrough)**:
   - In `Theme.HdmiViewer`, remove `windowIsTranslucent=true` and use a standard fullscreen theme to ensure `MainActivity` immediately halts via `onStop()` and leaves the composition stack.
   - Completely remove `blackCoverLayout`, progress spinners, and text views. `tvView` is set as the sole `ContentView`.
   - `HdmiViewerActivity` avoids triggering Toast or overlay windows for quiet input switching.
2. **System-Level Workaround: Disable Global Animation Scales**:
   - Because this behavior on RTD2851 appears linked to **alpha fade-out transitions**, setting Android's animation scales to 0 forces system dialogs and volume bars to appear and disappear instantaneously (0ms, no alpha transition frames):
     ```bash
     # Disable window animations, transition animations, and animator duration scales
     adb shell settings put global window_animation_scale 0
     adb shell settings put global transition_animation_scale 0
     adb shell settings put global animator_duration_scale 0
     ```
   - **Side Effects**: Setting animation scales to 0 applies globally across the entire Android system. All system dialogs, app transitions, and popup animations across all installed applications will appear instantaneously without smooth animations.
   - **Restoration Command**: To restore standard animations at any time:
     ```bash
     adb shell settings put global window_animation_scale 1
     adb shell settings put global transition_animation_scale 1
     adb shell settings put global animator_duration_scale 1
     ```
3. **Underlying Session Race Condition & Frozen Video Fix**:
   - **Mechanism**: Calling `tvView.reset()` synchronously right before `tvView.tune()` creates an asynchronous race condition within the HAL (releasing takes ~250ms). This causes `grantMediaResource` to throw a `NullPointerException: getPackageName()`, prompting the HAL decoder to fire `onVideoUnavailable(reason=0)` and freezing the video stream on the last decoded frame.
   - **Implementation**:
     1. Eliminate premature `tvView.reset()` calls inside `tuneToPort()`; let TIF handle session transitions.
     2. Properly invoke `tvView.reset()` in `onStop()` to release the hardware session when leaving the foreground.
     3. Implement a 600ms self-healing auto re-tune mechanism when `onVideoUnavailable(reason=0)` is caught.
4. **Firmware Experience: Downgrading to Android 9 Official Firmware (`V8-R851T02-LF1V662`)**:
   - **Android 11 Status (Untested in this project / 未經本專案驗證)**: User reports in community forums suggest that on certain TCL Android 11 (V7xx / V8xx) firmware builds, porting newer HWC2 drivers onto the Realtek RTD2851 platform coincided with `VideoComposer` sideband buffer issues (`invalid sideband ... 0/0`), increased memory pressure, and layer contention. <!-- TODO: 需作者確認：關於 Android 11 HWC2 具體機制的測試與驗證細節 -->
   - **V662 (Android 9) Verified (實測有效)**:
     - On TCL 65C715 with firmware `V8-R851T02-LF1V662`, HDMI passthrough functions stably using native SurfaceView without sideband dirty-rect cache lockups.
     - Reduces background memory overhead compared to heavier builds, providing smooth wake-up latency and stable HDMI locking.
     - *Flashing Note*: Flashing or downgrading firmware requires placing `Update.img` on a FAT32 USB drive and performing a recovery flash (holding the hardware power button while connecting AC power). Proceed with caution.
5. **Buffer Flush Workaround**:
   - If a dark shadow appears on screen, pressing the remote's **Home** button to return to the Launcher (causing a full-screen view redraw to flush the composer buffer) and then pressing OK to re-enter HDMI clears the display buffer.

---

### Fix 4: True HDMI-CEC Auto-Switching (Bypassing TCL's Broken Auto-Switch)

#### Problem
When you wake your Apple TV or PS5, it sends standard HDMI-CEC commands (`<Image View On>` and `<Active Source>`). Standard TV implementations switch automatically to that HDMI port. On tested TCL firmware, the system only wakes the screen (via a proprietary `MSG_VIEW_ON` broadcast) and remains on the Android home screen without switching the active input.

#### Technical Barriers (SELinux & Logcat Filtering)
1. **Empty Broadcasts**: TCL's `MSG_VIEW_ON` broadcast contains no port metadata indicating which HDMI port triggered the wake event.
2. **SELinux Blocks Standard APIs**: Attempts to query the active port via `dumpsys hdmi_control` fail on unprivileged app contexts due to SELinux policy restrictions (`Can't find service: hdmi_control` for `untrusted_app`).
3. **Logcat UID Filtering**: The underlying `system_server` logs the exact CEC command (`HdmiCecController: command:<Active Source> ... params: 10 00`). However, standard Android permissions prevent third-party apps from reading system logs without `READ_LOGS`.

#### Solution: System Logcat Interception via Foreground Service (`CecLogReaderService`)
This launcher includes a foreground service (`CecLogReaderService`) that monitors logcat specifically filtered for `HdmiCecController` tags. When it detects an `<Active Source>` command, it parses the physical address (e.g., `10 00` -> `0x1000` -> HDMI 1) and switches the input accordingly.

To enable this functionality, grant the app `READ_LOGS` permission via ADB or Shizuku:

```bash
# 1. Grant permission to read system logs
adb shell pm grant com.lnu.tclhdmilauncher android.permission.READ_LOGS

# 2. Force stop the app to apply the new permission
adb shell am force-stop com.lnu.tclhdmilauncher
```
Once granted, the foreground service detects CEC active source events and triggers input switching.
