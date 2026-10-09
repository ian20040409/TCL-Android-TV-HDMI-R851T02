# ADB Cheatsheet & Command Reference

常用 ADB 指令整理，適用於 TCL Android TV（RTD2851 / R851T02 平台）的安裝、停用原廠應用程式、權限配置與訊號源切換。

---

## 1. 連線與安裝 (Connection & Installation)

```bash
# 透過 Wi-Fi 連線電視（將 IP 替換為電視實際 IP）
adb connect 192.168.1.xxx:5555

# 查看連線裝置
adb devices

# 安裝/覆蓋安裝 APK
adb install -r app/build/outputs/apk/release/app-release.apk

# 設為系統預設 Launcher
adb shell cmd package set-home-activity com.lnu.tclhdmilauncher/.MainActivity
```

---

## 2. 停用與恢復原廠元件 (Stock App Management)

### 停用/恢復 Google TV / Android TV 原廠首頁
```bash
# 停用原生 Launcher (安全可逆)
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell am force-stop com.google.android.tvlauncher

# 還原啟用原生 Launcher
adb shell pm enable com.google.android.tvlauncher
adb shell cmd package clear-preferred-activities com.lnu.tclhdmilauncher
```

### 解除安裝/還原 TCL 原廠電視訊號 UI (`com.tcl.tv`)
*詳細原理見 [Troubleshooting Fix 1](troubleshooting_zh.md#排查-1解決-apple-tv--hdmi-cec-關機後電視自動喚醒或螢幕沒關)*

```bash
# 針對 User 0 解除安裝 com.tcl.tv（徹底解決 CEC 關機喚醒迴圈與 Dolby 橫幅殘留）
adb shell pm uninstall -k --user 0 com.tcl.tv

# （選用）停用連按兩次電源鍵手勢
adb shell settings put secure camera_double_tap_power_gesture_disabled 1

# 還原安裝 com.tcl.tv
adb shell cmd package install-existing com.tcl.tv
```

---

## 3. 權限配置 (AppOps & Permissions)

### 授予本 Launcher 系統層級權限
```bash
# 授予讀取系統 CEC 日誌權限（CEC 自動切換必備）
adb shell pm grant com.lnu.tclhdmilauncher android.permission.READ_LOGS

# 撤銷 READ_LOGS 權限
adb shell pm revoke com.lnu.tclhdmilauncher android.permission.READ_LOGS

# 重新啟動 App 套用權限
adb shell am force-stop com.lnu.tclhdmilauncher
```

### 解決 Dolby Vision / HDR 橫幅殘留 (AppOps)
*詳細原理見 [Troubleshooting Fix 2](troubleshooting_zh.md#排查-2解決-dolby-vision--hdr-10-原廠橫幅殘留與卡死)*

```bash
# 封鎖 com.tcl.tv 的懸浮窗與 Toast
adb shell appops set com.tcl.tv SYSTEM_ALERT_WINDOW deny
adb shell appops set com.tcl.tv TOAST_WINDOW deny

# 封鎖 com.tcl.settings 的 Toast（但保留原生畫質選單 SYSTEM_ALERT_WINDOW）
adb shell appops set com.tcl.settings TOAST_WINDOW deny
adb shell appops set com.tcl.settings SYSTEM_ALERT_WINDOW allow
```

---

## 4. 全域動畫設定 (Display & Animation Optimization)

*詳細原理見 [Troubleshooting Fix 3](troubleshooting_zh.md#排查-3解決-tvview-影像穿透黑屏invalid-sideband-00與殘影)*

```bash
# 關閉全域動畫縮放（防止 Realtek HWC 在淡出過渡時卡死殘影）
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

# 【還原】恢復預設動畫縮放 (改回 1)
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
```

---

## 5. 測試 HDMI 訊號源切換 (Source Switching Testing)

```bash
# 顯式切換至 HDMI 1 / 2 / 3
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 1
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 2
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 3

# 透過 Android TIF 標準 Intent 切換（驗證 TvView 相容性）
adb shell am start -a android.intent.action.VIEW \
  -d "content://android.media.tv/passthrough/com.tcl.tvinput%2F.passthroughinput.TvPassThroughService%2FHW1413744640"
```
