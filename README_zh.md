# TCL HDMI Launcher

<p align="left">
  <a href="README.md">English</a> | <b>繁體中文</b>
</p>

[![Latest Release](https://img.shields.io/github/v/release/ian20040409/TCL-Android-TV-HDMI-R851T02?style=flat-square&color=blue)](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/ian20040409/TCL-Android-TV-HDMI-R851T02/total?style=flat-square)](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases)
[![License](https://img.shields.io/github/license/ian20040409/TCL-Android-TV-HDMI-R851T02?style=flat-square)](LICENSE)

> **把 TCL Android 電視打造成純粹、高效能的外接顯示器。**  
> 徹底擺脫原廠臃腫慢速、充斥推薦廣告的系統首頁；開機迅速直達外接裝置（Apple TV 4K、PS5、Switch、電視盒），極速切換且零干擾。

👉 **[下載最新版 Release APK](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases/latest)**

---

## 核心特色 (Highlights)

- ⏱️ **開機自動倒數**：電視開機或按下 Home 鍵，倒數結束自動切換至預設 HDMI 埠（方向鍵任意鍵可中斷倒數）。
- 🔢 **遙控器數字鍵直達**：按下 `1`、`2`、`3` 立即秒切 HDMI 1 / 2 / 3（無論在桌面或播放器中皆有效）。
- 📺 **自建原生 TvView 播放器**：基於 Android TIF（`TvView`）硬體直通，徹底終結對臃腫 `com.tcl.tv` 的依賴。
- 🎮 **4K HDR & Dolby Vision**：支援 4K @ 60Hz 直通，無多餘浮層與 Toast 殘影瑕疵。
- 🧠 **來源感知 CEC 智慧休眠與切換**：
  - 智慧判斷僅在「當前觀看中的 HDMI 裝置」發出休眠時才連動電視關機，徹底解決背景裝置（如 Dock 中的 Switch）休眠誤關電視的問題。
  - 主動攔截並壓制 TCL 韌體 `com.tcl.voicestandby` 導致的異常關機。
- 📱 **全新卡片式 TV 應用程式庫**：專為電視遙控器最佳化的格狀抽屜，支援分類標籤（全部 / 最近 / TV 版 / 手機版 / 系統 / 已凍結），長按可管理或凍結應用程式。
- 🚀 **靈活啟動模式 (App Mode)**：除了 HDMI 直通模式，亦可設定開機自動直達指定串流 App（YouTube / Netflix 等）或以 App 抽屜為主畫面。
- ⚡ **Shizuku 一鍵無痛配置**：內建 [Shizuku TV 版](https://github.com/thedjchi/Shizuku/releases) APK 下載，一鍵自動授予 `READ_LOGS` 權限並**自動開啟無障礙待機守護**，免去手動翻找電視系統選單。
- 🖤 **純黑介面設計 (`#000000`)**：專為暗室家庭劇院與 OLED / QLED 局部控光最佳化。
- 🛠️ **原生 Leanback 設定與除錯**：完整 10 呎電視設定選單、按鍵重定向 (Button Mapper)、一鍵呼叫 TCL 原廠畫質選單 (`com.tcl.settings`)，以及內建即時 CEC 事件除錯視窗。

---

## 畫面截圖 (Screenshots)

| 主畫面（HDMI 訊號源切換） | 倒數秒數設定 | TCL 原生設定捷徑 | 應用程式列表 |
|:---:|:---:|:---:|:---:|
| ![Main](readme_pic/Screenshot_20260925_222244.png) | ![Countdown](readme_pic/Screenshot_20260925_222304.png) | ![Settings](readme_pic/Screenshot_20260925_222322.png) | ![Apps](readme_pic/Screenshot_20260925_222344.png) |

---

## 快速安裝指南 (4 個步驟)

### 1. 安裝 APK
至 [GitHub Releases](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases/latest) 下載 APK 並透過 ADB 安裝：
```bash
adb connect <電視IP>:5555
adb install -r app-release.apk
```

### 2. 設為預設 TV Launcher
```bash
adb shell cmd package set-home-activity com.lnu.tclhdmilauncher/.MainActivity
```

### 3. 停用原廠臃腫桌面（建議）
杜絕原廠首頁的背景佔用與自動喚醒：
```bash
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell am force-stop com.google.android.tvlauncher
```
*(隨時可透過 `adb shell pm enable com.google.android.tvlauncher` 還原)*

### 4. 啟用待機守護與 CEC 自動切換（建議）

本 App 需無障礙服務確保待機喚醒穩定與 Home 鍵重定向，並透過日誌監聽實現 CEC 外接設備開機自動換源。

- **方法 A：透過 Shizuku 一鍵自動啟用（最推薦，最省事）**  
  若電視已運行 [Shizuku（推薦電視適配版：thedjchi/Shizuku）](https://github.com/thedjchi/Shizuku/releases)（亦可直接在 Launcher 設定內一鍵下載安裝），在 Launcher 設定中點擊 Shizuku 授權：
  - 自動授予 `READ_LOGS` 日誌讀取權限
  - **自動透過 Shell 啟用「喚醒守護 (Wake Guard)」無障礙服務**，無需手動翻找電視無障礙設定
- **方法 B：手動 ADB 指令與設定**  
  1. 授予 CEC 日誌讀取權限：
     ```bash
     adb shell pm grant com.lnu.tclhdmilauncher android.permission.READ_LOGS
     adb shell am force-stop com.lnu.tclhdmilauncher
     ```
  2. 點擊狀態列的「喚醒守護」，於系統設定中開啟「TCL HDMI 喚醒守護」無障礙服務（或透過 ADB 一鍵啟用）：
     ```bash
     adb shell settings put secure enabled_accessibility_services com.lnu.tclhdmilauncher/.WakeAccessibilityService
     ```

---

## 遙控器操作指南

| 介面 | 按鍵 | 操作說明 |
|---|---|---|
| **全部介面** | **數字鍵 `1` / `2` / `3`** | 秒切 HDMI 1 / 2 / 3（無論在桌面或播放器中皆有效） |
| **全部介面** | **輸入源鍵 (INPUT)** | 依序循環切換 HDMI 1 ➔ HDMI 2 ➔ HDMI 3 |
| **主畫面** | **方向鍵 (D-Pad)** | 移動焦點；在倒數進行時按任意方向鍵**取消自動跳轉** |
| **主畫面** | **OK / 確認鍵** | 進入選取之 HDMI 埠或 App；在 HDMI 卡片上**長按 OK** 設為預設開機埠 |
| **主畫面** | **設定鍵 (SETTINGS / MENU)** | 開啟啟動器設定選單 |
| **HDMI 播放器** | **設定鍵 (SETTINGS)** | 一鍵呼叫 TCL 原廠畫質 / 音效設定選單 (`com.tcl.settings`) |
| **HDMI 播放器** | **返回鍵 (BACK / MENU)** | 退出播放畫面，返回桌面 |
| **應用程式庫** | **OK / 確認鍵** | 開啟選取之應用程式 |
| **應用程式庫** | **選單鍵 (MENU) / 長按 OK** | 呼叫 App 操作選單（應用程式資訊、解除安裝、透過 Shizuku 凍結/解凍） |

---

## 實測機型與訊號源對照

本專案在 **TCL 65C715** 實測驗證（平台：RTD2851 / R851T02，韌體：`V8-R851T02-LF1V662`，Android 9）：

| 訊號來源 | 端口 | TvInput ID |
|---|---|---|
| **HDMI 1** | 1 | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744128` |
| **HDMI 2** | 2 | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744384` |
| **HDMI 3** | 3 | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744640` |

---

## 隱私與權限說明 (Privacy & Permissions)

本專案完全開源無商業行為，尊重用戶隱私：
- **`READ_LOGS`**：程式碼內僅過濾 `HdmiCecController` 相關標籤以識別 CEC 訊號埠，絕無資料寫入或網路傳輸。
- **無障礙服務 (`WakeAccessibilityService`)**：僅監聽螢幕點亮與視窗狀態以防假死，絕不蒐集密碼或個人隱私。
- **Shizuku API**：僅用於本機 IPC 調用指令（自動授權、自動啟用無障礙、凍結背景 App），無任何外部通訊。

---

## 技術文件與排查指引 (Docs Index)

深入技術分析、Logcat 追蹤與韌體說明已整理至專屬文件庫：

- 📖 **[完整排查手冊 (docs/troubleshooting_zh.md)](docs/troubleshooting_zh.md)** ([English](docs/troubleshooting.md))
  - *排查 1*：Apple TV 雙重休眠 CEC 循環開關機（安全卸載 `com.tcl.tv`）
  - *排查 2*：Dolby Vision / HDR10 橫幅永久卡死（AppOps 權限治本解法）
  - *排查 3*：TvView 畫面穿透黑屏與 Realtek HWC 圖層合成殘影
  - *排查 4*：透過前台服務 Logcat 攔截實現真正的 CEC 自動切換
- 📑 **[常用 ADB 指令速查表 (docs/adb-cheatsheet.md)](docs/adb-cheatsheet.md)**
- ⚙️ **[韌體相容性與 Android 9 降級指南 (docs/firmware-guide.md)](docs/firmware-guide.md)**
