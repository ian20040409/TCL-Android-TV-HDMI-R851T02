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

- ⏱️ **開機自動倒數**：電視開機或按下 Home 鍵，倒數結束自動切換至預設 HDMI 埠。
- 🔢 **遙控器數字鍵直達**：按下 `1`、`2`、`3` 立即跳轉至 HDMI 1 / 2 / 3。
- 📺 **自建原生 TvView 播放器**：基於 Android TIF（`TvView`）硬體直通，徹底終結對臃腫 `com.tcl.tv` 的依賴。
- 🎮 **4K HDR & Dolby Vision**：支援 4K @ 60Hz 直通，無多餘浮層與 Toast 殘影瑕疵。
- 🖤 **純黑介面設計 (`#000000`)**：專為暗室家庭劇院與 OLED / QLED 局部控光最佳化。
- 🛠️ **原生 Leanback 設定與導覽精靈**：直覺 10 呎電視介面，並提供一鍵呼叫 TCL 原廠畫質/音效設定 (`com.tcl.settings`)。

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

GitHub Actions 產生的是啟用 R8 的 Release APK。若要安裝，APK 必須有簽章；若要直接覆蓋升級並保留 App 資料，簽章憑證必須與目前安裝版本完全相同。未設定 CI 簽章金鑰時，產物會是未簽名 APK，Android 無法直接安裝。GitHub 不會自動提供 Android 簽章憑證；請由維護者將既有 `.jks` 與密碼設為 Repository Actions Secrets，詳見 [建置與簽章說明](docs/compose-tv-migration.md)。

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
- **待機喚醒守護 (無障礙服務)**：可點擊狀態列的「喚醒守護」前往系統設定手動開啟；若已授予 App Shizuku 權限，App 會透過 shell 自動啟用此服務。可避免待機喚醒黑屏並提供 Home 鍵重定向。
- **CEC 自動切換**：授予讀取日誌權限，外接設備開機時自動跳轉對應端口：
  ```bash
  adb shell pm grant com.lnu.tclhdmilauncher android.permission.READ_LOGS
  adb shell am force-stop com.lnu.tclhdmilauncher
  ```
  *(亦可透過內建整合之 **Shizuku** 自動授權)*

---

## 遙控器操作指南

| 按鍵 | 操作說明 |
|---|---|
| **方向鍵 (D-Pad)** | 移動焦點；在倒數進行時按任意方向鍵**取消自動跳轉** |
| **OK / 確認鍵** | 進入選取之 HDMI 埠或 App（在 HDMI 卡片上**長按 OK** 設為開機預設） |
| **數字鍵 `1` / `2` / `3`** | 秒切 HDMI 1 / 2 / 3（桌面與播放器內皆有效） |
| **輸入源鍵 (INPUT)** | 循環切換 HDMI 1 ➔ HDMI 2 ➔ HDMI 3 |
| **設定鍵 (SETTINGS)** | 開啟啟動器設定 / 一鍵開啟 TCL 原廠畫質選單 (`com.tcl.settings`) |
| **返回 / 選單 (BACK/MENU)** | 離開對話框或自播放器返回桌面 |

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
- **Shizuku API**：僅用於本機 IPC 調用指令（自動授權、凍結背景 App），無任何外部通訊。

---

## 技術文件與排查指引 (Docs Index)

深入技術分析、Logcat 追蹤與韌體說明已整理至專屬文件庫：

- 🏗️ **[架構說明 (docs/architecture.md)](docs/architecture.md)**：CEC log 讀取、無障礙服務與 Shizuku 的分工與互動流程。
- **[Compose for TV 遷移紀錄](docs/compose-tv-migration.md)**：主畫面與 App 清單已採用官方 TV Material 3 預設元件；設定與 OOBE 尚未遷移。包含測試方式與 TCL 實機驗收清單。

- 📖 **[完整排查手冊 (docs/troubleshooting_zh.md)](docs/troubleshooting_zh.md)** ([English](docs/troubleshooting.md))
  - *排查 1*：Apple TV 雙重休眠 CEC 循環開關機（安全卸載 `com.tcl.tv`）
  - *排查 2*：Dolby Vision / HDR10 橫幅永久卡死（AppOps 權限治本解法）
  - *排查 3*：TvView 畫面穿透黑屏與 Realtek HWC 圖層合成殘影
  - *排查 4*：透過前台服務 Logcat 攔截實現真正的 CEC 自動切換
- 📑 **[常用 ADB 指令速查表 (docs/adb-cheatsheet.md)](docs/adb-cheatsheet.md)**
- ⚙️ **[韌體相容性與 Android 9 降級指南 (docs/firmware-guide.md)](docs/firmware-guide.md)**
