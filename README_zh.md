# TCL HDMI Launcher

<p align="left">
  <a href="README.md">English</a> | <b>繁體中文</b>
</p>

[![Latest Release](https://img.shields.io/github/v/release/ian20040409/TCL-Android-TV-HDMI-R851T02?style=flat-square&color=blue)](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/ian20040409/TCL-Android-TV-HDMI-R851T02/total?style=flat-square)](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases)
[![License](https://img.shields.io/github/license/ian20040409/TCL-Android-TV-HDMI-R851T02?style=flat-square)](LICENSE)

> **專為「把 TCL 電視當純顯示器用」的發燒友與極簡主義者打造。**  
> 徹底擺脫 TCL 原廠臃腫慢速、充斥推薦廣告的系統首頁；開機迅速直達外接裝置（Apple TV / PS5 / 電視盒 / 藍光播放器），讓電視回歸純粹顯示螢幕！

👉 **[下載最新版 Release APK](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases/latest)**

---

## 快速導覽 (Table of Contents)

- [為什麼需要這個專案？（核心痛點與使用場景）](#為什麼需要這個專案核心痛點與使用場景)
- [最佳劇院外接配置範例](#最佳劇院外接配置範例)
- [核心亮點特色](#核心亮點特色)
- [畫面截圖 (Screenshots)](#畫面截圖-screenshots)
- [遙控器操作指南](#遙控器操作指南)
- [快速安裝與設定指南](#快速安裝與設定指南)
  - [步驟 1：建置或下載 APK](#步驟-1建置或下載-apk)
  - [步驟 2：開啟電視 ADB 開發者模式並安裝](#步驟-2開啟電視-adb-開發者模式並安裝)
  - [步驟 3：設為預設 TV Launcher](#步驟-3設為預設-tv-launcher)
  - [步驟 4：停用 TCL 原生臃腫 Launcher（建議）](#步驟-4停用-tcl-原生臃腫-launcher建議)
  - [步驟 5：啟用待機喚醒與 Home 鍵映射（強烈建議）](#步驟-5啟用待機喚醒與-home-鍵映射強烈建議)
  - [步驟 6：啟用 Shizuku 進行應用程式管理與自動授權（選用）](#步驟-6啟用-shizuku-進行應用程式管理與自動授權選用)
- [隱私與權限說明 (Privacy & Permissions)](#隱私與權限說明-privacy--permissions)
- [重要排查與疑難排解 (Troubleshooting)](#重要排查與疑難排解-troubleshooting)
  - [排查 1：解決 Apple TV / HDMI-CEC 關機後電視自動喚醒或螢幕沒關](#排查-1解決-apple-tv--hdmi-cec-關機後電視自動喚醒或螢幕沒關)
  - [排查 2：解決 Dolby Vision / HDR 10 原廠橫幅殘留與卡死](#排查-2解決-dolby-vision--hdr-10-原廠橫幅殘留與卡死)
  - [排查 3：解決 TvView 影像穿透黑屏與殘影](#排查-3解決-tvview-影像穿透黑屏與殘影)
  - [排查 4：實現真正的 HDMI-CEC 喚醒自動切換訊號源](#排查-4實現真正的-hdmi-cec-喚醒自動切換訊號源)
- [相容性與實測狀態矩陣 (Tested / Untested)](#相容性與實測狀態矩陣-tested--untested)
  - [實測機型資訊](#實測機型資訊)
  - [實體裝置訊號源對照表 (R851T02 / C715)](#實體裝置訊號源對照表-r851t02--c715)
  - [ADB 測試訊號源切換](#adb-測試訊號源切換)
- [運作原理架構](#運作原理架構)
- [技術規格](#技術規格)
- [推薦專案](#推薦專案)

---

## 為什麼需要這個專案？（核心痛點與使用場景）

### 誰最適合使用？
如果你符合以下任一特徵，這個 Launcher 就是為你量身打造的：
- **TCL Android TV / Google TV 用戶**：家中使用 TCL 智慧電視，渴望擺脫原廠系統的臃腫卡頓與開機廣告，專享為 TCL 底層電視輸入框架（`com.tcl.tvinput`）量身定制的啟動體驗。
- **純外接訊號源主義者**：日常主力都是外接設備，內建的智慧電視系統對你來說只是個「面板控制器」：
  - 🍏 **Apple TV 4K** — 日常串流追劇、電影觀影主力
  - 🎮 **PlayStation 5 (PS5) / Xbox Series X / Switch** — 次世代 4K HDR 遊戲娛樂
  - 📺 **Android TV / Google TV 盒子**（Chromecast with Google TV、NVIDIA Shield 等）
  - 💿 **4K UHD 藍光 / DVD 播放機** 或 **家庭劇院擴大機 (AV Receiver)**
- **深受 TCL 官方 HDMI-CEC 諸多 Bug 所苦**：
  - 關機假死與反覆重開：Apple TV 待機時發送 CEC 休眠廣播，TCL 原廠韌體卻連續發送兩次電源鍵模擬，導致電視關機 1 秒內立刻被敲醒。
  - 擴大機與 Soundbar eARC 握手不穩定：原廠背景服務佔滿 CPU 與記憶體，導致 CEC 握手逾時，外接音響設備時常無法聯動音量或無聲。
- **厭惡內建首頁的臃腫與廣告**：TCL 原廠 Launcher 開機載入緩慢、首頁塞滿不需要的線上影音推薦、佔用有限的系統記憶體。
- **想要「開機即用」的直覺體驗**：開啟電視後，希望像傳統電視或顯示器一樣，幾秒鐘內自動跳轉到最常看的訊號源（如 Apple TV），不必拿著遙控器在選單裡反覆點選。
- **多設備切換快速直接**：家裡同時接了 PS5 與 Apple TV，希望按個數字鍵 `1` 或 `2` 就能快速無縫切換，不用呼叫繁瑣的輸入來源選單。

---

## 最佳劇院外接配置範例

- **預設場景**：開機後，倒數 3 秒（可自訂）自動進入 **HDMI 3 (Apple TV 4K)**，完全免按任何按鍵。
- **遊戲場景**：想打電動時，遙控器直接按下數字鍵 `1`，直達 **HDMI 1 (PS5)**。
- **音訊與畫質調校**：右上角一鍵直達 TCL 原生影像與音效設定 (`com.tcl.settings`)，免去原生 Launcher 繁瑣層級。

---

## 核心亮點特色

1. **開機自動倒數直達**：
   - 支援自訂倒數秒數：`關閉`、`1s`、`2s`、`3s（預設）`、`5s`、`10s`、`15s`、`30s`。
   - 開機或按下 Home 鍵，倒數結束即自動切換至預設訊號源。
   - 倒數期間任意輕按方向鍵（D-pad）即可暫停倒數，從容選取其他訊號源。
2. **遙控器數字鍵跳轉**：
   - 遙控器按下 `1` / `2` / `3`，直達對應 HDMI 埠。
3. **長按 OK 鍵自由設定預設訊號源**：
   - 在任一 HDMI 卡片上「長按 OK 鍵」，即可將該埠標記為開機預設訊號源。
4. **輕量架構與記憶體表現**：
   - 經 R8 fullMode 深度混淆壓縮、無用代碼剝離與資源縮減。
   - 全純程式碼 View 樹，0 XML 解析開銷、0 反射。
   - 倒數計時熱路徑避免重複配置物件，消除 GC 負載。
   - **背景服務生命週期**：未啟用防假死守護（Wake Guard）與 CEC 監控時，應用程式作為純桌面運作，無常駐背景服務；啟用 CEC 自動切換或防假死守護後，會有前台服務（`CecLogReaderService`）或無障礙服務（`WakeAccessibilityService`）在背景負責喚醒監控與按鍵轉發。
5. **純黑電視專用介面 (Pure Black #000000)**：
   - 專為暗室家庭劇院與 OLED / QLED 局部控光最佳化，絕無刺眼白色閃爍。
6. **多國語言介面 (English / 繁體中文 / 簡體中文)**：
   - 依電視系統語言自動適配，無論全英環境或繁簡中文皆完美顯示。
7. **原生 Leanback 設定與開箱導覽精靈 (OOBE)**：
   - 採用 Android TV 官方標準 **Leanback `GuidedStepSupportFragment`** 設計，遵循 10 呎大螢幕 UI 規範與遙控器方向鍵焦點導航。
   - **首次開箱導覽 (OOBE)**：開箱引導使用者設定純顯示器 / App 模式、預設開機訊號源與啟用無障礙守護。
   - **App 模式專屬設定頁 (`AppModeSettingsActivity`)**：支援非同步讀取已安裝應用程式、直覺式選擇開機自動啟動 App，並自訂開機/喚醒後的延遲倒數秒數。
   - **重設所有設定**：一鍵還原所有啟動器偏好設定並重新啟動開箱設定導覽精靈。
   - 快速入口：一鍵開啟 TCL 原廠電視畫質/音效設定頁 (`com.tcl.settings`) 及 Android 系統設定。
8. **App 模式與應用程式自動啟動 (App Mode & Auto-Open)**：
   - 頂部狀態列或設定選單提供「App 模式」一鍵切換。開啟後，開機或按 Home 鍵預設直達應用程式抽屜。
   - 可在清單中「長按 OK」或進入 **App 模式設定** 指定「自動啟動」App；開機/喚醒倒數結束自動直達指定 App（如 YouTube、Netflix、動畫瘋等）。
9. **輕量應用程式抽屜 (App Drawer)**：
   - 若偶爾需開啟內建智慧電視 App，內建極速清單，支援 TV/手機側載分類、最近使用與長按解除安裝/停用。
10. **自建原生 HDMI Viewer (`TvView`) — 擺脫 `com.tcl.tv` 依賴**：
    - 內建全螢幕 HDMI 播放器（`HdmiViewerActivity`），直接由 Android 官方 TV Input Framework（`android.media.tv.TvView`）驅動。
    - 減少對 TCL 原廠電視播放器（`com.tcl.tv.TVActivity`）的依賴，零廣告、無原廠多餘橫幅。
    - 支援 4K @ 60Hz、HDR10 與 Dolby Vision 硬體圖層直通解碼。
    - **靜默硬體直通（無多餘浮層與 Toast）**：
      - 播放器內部不彈出 Toast、不繪製自訂 View 浮層，直接將 `TvView` 作為唯一 ContentView。
      - **減緩電視 GPU 快取瑕疵**：TCL 電視晶片（Realtek RTD2851）的圖層合成器在處理浮動視窗淡出時容易將半透明邊界殘留在視訊圖層；完全不呼叫 Toast 有助於避免觸發此殘影問題。
      - **動態熱插拔感知**：在無訊號狀態下開啟外接設備或插入 HDMI，晶片訊號就緒時硬體視訊即時點亮。
    - **獨立視窗架構與硬體埠對照 (Hardware ID Mapping)**：
      - 採用獨立 Task 視窗架構（`FLAG_ACTIVITY_NEW_TASK` 與獨立 `taskAffinity`），播放畫面與桌面分層獨立管理。
      - 針對 R851T02 平台精準鎖定實體晶片 Input ID（`HW1413744128`、`HW1413744384`、`HW1413744640`）並實作 `onNewIntent`。
    - 在 TCL 65C715 實測中，可**安全停用或解除安裝 `com.tcl.tv`**，完全不影響 HDMI 畫面顯示。
11. **Shizuku API 整合與 APK 下載管理頁面 (`ShizukuSettingsActivity`)**：
    - **即時服務狀態檢測**：自動偵測 Shizuku Binder 連線、存取權限狀態與服務版本號。
    - **一鍵彈出 Shizuku 授權視窗**：直覺觸發 Shizuku 原生 API 授權對話框（`Shizuku.requestPermission()`）。
    - **GitHub Release APK 自動下載**：連接 GitHub Releases API 獲取最新版，支援小數點 MB 與百分比即時進度顯示（`X.X MB / Y.Y MB (Z%)`），並透過 `FileProvider` 自動呼叫原生安裝程式。
    - **本機檔案完整性校驗與快取重用**：透過 `packageManager.getPackageArchiveInfo()` 校驗 APK，若已下載過完整 APK 檔案，返回頁面時自動重用並提供「一鍵安裝」，無需重新下載。
    - **App 停用 (凍結) / 啟用 (解凍)**：可在應用程式抽屜 (`AppListActivity`) 中長按選單，透過 Shizuku 停用背景耗電 App 或隨時還原啟用。
12. **HDMI-CEC 喚醒自動切換與系統 Logcat 監控（`CecLogReaderService` + Shizuku-API 整合）**：
    - **CEC 喚醒攔截**：透過前台服務監聽系統 Logcat 中的 `HdmiCecController` `<Active Source>` 與 `MSG_VIEW_ON` 廣播，繞過 TCL 原廠韌體缺陷，實現裝置喚醒時自動切換至對應 HDMI 埠。
    - **透過 Shizuku-API 自動授權**：整合 [Shizuku-API](https://github.com/RikkaApps/Shizuku-API)（`ShizukuHelper.tryGrantPermissions`），自動賦予 `READ_LOGS` 與 `DUMP` 系統權限，免去手動輸入 ADB 指令的繁瑣步驟。
    - **前台守護服務 (`CecLogReaderService`)**：採用前台服務架構，確保電視待機喚醒與訊號源狀態追蹤在睡眠/喚醒週期中皆能正常運作。

---

## 畫面截圖 (Screenshots)

| 主畫面（HDMI 訊號源切換） | 倒數秒數設定對話框 |
|:---:|:---:|
| ![主畫面](readme_pic/Screenshot_20260925_222244.png) | ![倒數設定](readme_pic/Screenshot_20260925_222304.png) |
| **呼叫 TCL 原生設定** | **應用程式列表（App Drawer）** |
| ![TCL 設定](readme_pic/Screenshot_20260925_222322.png) | ![應用程式列表](readme_pic/Screenshot_20260925_222344.png) |

---

## 遙控器操作指南

| 按鍵 | 操作效果 |
|---|---|
| **方向鍵 (D-Pad)** | 移動卡片焦點；在倒數進行時按任意方向鍵可**取消自動跳轉** |
| **OK / 確認鍵** | 立即切換至當前焦點選取的 HDMI 訊號源（或啟動 App） |
| **長按 OK 鍵 (主畫面)** | 將當前 HDMI 卡片設為**開機預設訊號源** |
| **長按 OK 鍵 (App 清單)** | 開啟選單：**設為自動啟動**、解除安裝、停用、應用程式資訊 |
| **數字鍵 `1` / `2` / `3`** | 在 Launcher 桌面或 Viewer 播放器中均可直切 HDMI 1 / 2 / 3 |
| **輸入源鍵 (INPUT / SOURCE)** | 在 Launcher 桌面或 Viewer 播放器中依序循環切換 HDMI 1 ➔ HDMI 2 ➔ HDMI 3 |
| **選單鍵 (MENU)** | 開啟設定選單（桌面）/ 關閉並返回桌面（播放器） |
| **設定鍵 (SETTINGS)** | 開啟設定選單（桌面）/ 呼叫 TCL 原生設定選單 (`com.tcl.settings`) |
| **返回鍵 (BACK)** | 離開對話框，或從播放器/設定頁/應用程式清單返回桌面 |

---

## 快速安裝與設定指南

### 步驟 1：建置或下載 APK

您可以直接從 [GitHub Releases](https://github.com/ian20040409/TCL-Android-TV-HDMI-R851T02/releases/latest) 下載預先編譯好的 APK，或自行編譯：

```bash
# 建置 Debug APK
./gradlew assembleDebug

# 或建置經 R8 深度最佳化的 Release APK
./gradlew assembleRelease
```

### 步驟 2：開啟電視 ADB 開發者模式並安裝

1. 在電視系統設定中，前往「關於」➔ 連續點擊「版本號碼 (Build Number)」7 次以啟用開發人員選項。
2. 在「開發人員選項」中開啟「USB 偵錯」或「網路偵錯」。
3. 使用電腦終端機連線並安裝：

```bash
# 透過 Wi-Fi ADB 連線電視（請替換為電視實際 IP）
adb connect 192.168.1.xxx:5555

# 安裝 APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 步驟 3：設為預設 TV Launcher

```bash
# 指派本 App 為系統預設 Launcher
adb shell cmd package set-home-activity com.lnu.tclhdmilauncher/.MainActivity
```
*按遙控器「Home 首頁鍵」，系統即會以本 App 作為 HDMI 啟動桌面。*

### 步驟 4：停用 TCL 原生臃腫 Launcher（建議）

若希望避免原生 Launcher 後台喚醒與資源佔用，可安全停用（非刪除，日後可無損還原）：

```bash
# 停用 Android TV 原廠桌面（安全且無損可逆）
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell am force-stop com.google.android.tvlauncher
```

> [!TIP]
> **隨時可無損還原：**  
> 若日後需要換回原生 Android TV 桌面，只需執行：
> ```bash
> adb shell pm enable com.google.android.tvlauncher
> adb shell cmd package clear-preferred-activities com.lnu.tclhdmilauncher
> ```

### 步驟 5：啟用待機喚醒與 Home 鍵映射（強烈建議）

啟用無障礙服務可提供兩項系統級能力：
1. **待機喚醒守護**：電視睡眠喚醒時監聽螢幕點亮狀態，拉回本 Launcher 桌面（避免掉入 HDMI 無訊號或 AV 端子）。
2. **Button Mapper (Home 鍵重定向)**：在任何 App 中按下遙控器 **Home 鍵** 時直接被攔截重定向至本 Launcher。

**啟用方式：**
- 在桌面頂部狀態列點擊「喚醒守護」按鈕（`⚠️ 喚醒守護：關閉`），選擇「前往開啟」（或手動前往電視設定 ➔ 裝置偏好設定 ➔ 無障礙）。
- 將 **「HDMI Launcher 待機喚醒與 Home 鍵映射」** 切換為開啟。

### 步驟 6：啟用 Shizuku 進行應用程式管理與自動授權（選用）

1. 開啟啟動器內的 **設定** ➔ **Shizuku API 設定與狀態**。
2. 若尚未安裝 Shizuku，點擊 **下載並安裝最新版 Shizuku**。
3. 開啟電視上的 Shizuku 應用程式，依 Shizuku 官方指引啟動服務：
   - **無線偵錯方式**：Android 11+ 系統可直接於 Shizuku App 內配對並啟動。
   - **電腦 ADB 指令方式**：使用 Shizuku App 畫面內指示之指令，或透過電腦 ADB 執行官方啟動腳本：
     ```bash
     adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/files/start.sh
     ```
4. 返回啟動器之 **Shizuku API 設定** 頁面，點擊 **請求 Shizuku API 權限** 授予授權。
5. 授權完成後，App 可自動配置 `READ_LOGS` 權限並支援凍結管理。

---

## 隱私與權限說明 (Privacy & Permissions)

本專案為完全開源專案，尊重使用者隱私。以下為應用程式所使用的敏感權限清單、運作目的與撤銷方式說明：

| 權限 / 服務 | 使用目的 | 資料處理範圍與安全性 | 撤銷權限方式 |
|---|---|---|---|
| **`READ_LOGS`** | 供 `CecLogReaderService` 讀取 HDMI-CEC 指令（`<Active Source>`、`<Routing Change>` 等），以判斷外接設備喚醒端口。 | **程式碼內嚴格標籤過濾**：僅監聽 `HdmiCecController`、`HdmiCecLocalDeviceTv`、`HdmiCecLocalDevice`、`HdmiControlService`、`HdmiCecNetwork`、`HdmiCecMessage`。**絕不儲存至磁碟、絕不進行任何網路傳輸。** | 終端機執行：`adb shell pm revoke com.lnu.tclhdmilauncher android.permission.READ_LOGS` |
| **無障礙服務 (`WakeAccessibilityService`)** | 攔截遙控器 `KEYCODE_HOME` 按鍵並監聽系統 `ACTION_SCREEN_ON` 喚醒事件，避免休眠喚醒後黑屏或誤入 AV 端子。 | 僅過濾原廠桌面之視窗狀態改變與遙控器按鍵事件。**絕不蒐集任何使用者輸入內容、密碼或個人資料。** | 電視設定 ➔ 裝置偏好設定 ➔ 無障礙 ➔ 關閉服務 |
| **Shizuku API (`moe.shizuku.manager.permission.API_V23`)** | 自動授權 `READ_LOGS` / `DUMP` 權限，並支援在應用程式抽屜中凍結背景耗電 App。 | 透過本機 IPC Binder 調用標準 Android 套件管理指令。**無任何遙測或外部通訊行為。** | 在 Shizuku Manager 應用程式內隨時撤銷授權 |

---

## 重要排查與疑難排解 (Troubleshooting)

> [!NOTE]
> 完整的底層技術根因分析、Logcat 追蹤日誌與細部排查步驟已拆分至獨立文件：  
> 📖 **[閱讀完整排查手冊 (docs/troubleshooting_zh.md)](docs/troubleshooting_zh.md)** (或 [English Version](docs/troubleshooting.md))

### 重點排查摘要：

- **排查 1：解決 Apple TV / HDMI-CEC 關機後電視自動喚醒或螢幕沒關**：  
  TCL 的 `com.tcl.tv` 連續收到兩次 CEC Standby 廣播而注入兩次電源鍵模擬，導致關閉後立刻被喚醒。  
  *解決方式*：針對 User 0 解除安裝 `com.tcl.tv`（`adb shell pm uninstall -k --user 0 com.tcl.tv`）。在 TCL 65C715 實測有效。
- **排查 2：解決 Dolby Vision / HDR 10 原廠橫幅殘留與卡死**：  
  在第三方啟動器環境下，`com.tcl.tv` 的 DolbyToast 定時隱藏邏輯失效，導致橫幅永久卡死。  
  *解決方式*：透過 AppOps 精準封鎖權限（`adb shell appops set com.tcl.tv SYSTEM_ALERT_WINDOW deny` 及 `adb shell appops set com.tcl.tv TOAST_WINDOW deny`）。
- **排查 3：解決 TvView 影像穿透黑屏與殘影**：  
  半透明視窗遮擋與 Realtek RTD2851 HWC Dirty Rect alpha 淡出動畫機制引發殘影或黑屏。  
  *解決方式*：採用不透明全螢幕主題、移除所有浮層，並可選將全域動畫縮放歸零（`adb shell settings put global window_animation_scale 0`）。  
  *副作用提示*：動畫歸零會套用至整台電視所有 App；如欲還原請改回 1。
- **排查 4：實現真正的 HDMI-CEC 喚醒自動切換訊號源**：  
  透過前台服務 `CecLogReaderService` 監聽 `HdmiCecController` 日誌以繞過原廠韌體缺陷。需透過 ADB 或 Shizuku 授予 `READ_LOGS` 權限。

---

## 相容性與實測狀態矩陣 (Tested / Untested)

| 類別 | 裝置 / 環境 | 驗證狀態 | 說明與備註 |
|---|---|---|---|
| **已實測** | TCL 65C715 (BeyondTV2) | ✅ **實測有效** | 晶片平台：RTD2851 / R851T02；核心 4.14.76+ |
| **已實測** | 韌體版本 `V8-R851T02-LF1V662.019382` | ✅ **實測有效** | Android 9；TV+OS V5.2.0；客戶端 ID `TCL-AP-RT2851-S1` |
| **已實測** | Apple TV 4K、PS5、Nintendo Switch | ✅ **實測有效** | HDR10、Dolby Vision 4K 60Hz 硬體直通無誤 |
| **未實測** | TCL Android 11 韌體版本 (V7xx / V8xx) | ⚠️ **未經本專案實測** | 網路社群有提到 HWC2 圖層與記憶體壓力情況，但本專案尚未實測。 <!-- TODO: 需作者確認 --> |
| **未實測** | 其他 TCL 機型平台 (RTD2885, T972, MT9615 等) | ⚠️ **未實測** | 硬體 Input ID 與 CEC 廣播可能不同。 |
| **未實測** | 非 TCL 品牌電視 (Sony, Philips, 小米等) | ⚠️ **未實測** | 底層硬體輸入服務 (`com.tcl.tvinput`) 為 TCL 專屬。 |

### 實測機型資訊

- **實測機型**：**TCL 65C715** (BeyondTV2)
- **機型平台**：**RTD2851 / R851T02**
  - **韌體版本**：`V8-R851T02-LF1V662.019382`
  - **TV+OS 版本**：`V5.2.0 (V8-2204190-MF1V520)`
  - **客戶端 ID**：`TCL-AP-RT2851-S1`
  - **產品 ID**：`660`
  - **Android 系統**：Android 9 (Kernel `4.14.76+`)

### 實體裝置訊號源對照表 (R851T02 / C715)

| 訊號來源 | 端口 | 硬體 ID | 完整 TvInput ID |
|---|---|---|---|
| **HDMI 1** | 1 | `1413744128` | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744128` |
| **HDMI 2** | 2 | `1413744384` | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744384` |
| **HDMI 3** | 3 | `1413744640` | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744640` |

### ADB 測試訊號源切換

#### 方式一：顯式組件切換（推薦）

```bash
# 切換至 HDMI 1
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 1

# 切換至 HDMI 2
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 2

# 切換至 HDMI 3
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 3
```

#### 方式二：Android TV 標準 Passthrough Intent 切換

```bash
# 切換至 HDMI 1
adb shell am start -a android.intent.action.VIEW \
  -d "content://android.media.tv/passthrough/com.tcl.tvinput%2F.passthroughinput.TvPassThroughService%2FHW1413744128"

# 切換至 HDMI 2
adb shell am start -a android.intent.action.VIEW \
  -d "content://android.media.tv/passthrough/com.tcl.tvinput%2F.passthroughinput.TvPassThroughService%2FHW1413744384"

# 切換至 HDMI 3
adb shell am start -a android.intent.action.VIEW \
  -d "content://android.media.tv/passthrough/com.tcl.tvinput%2F.passthroughinput.TvPassThroughService%2FHW1413744640"
```

---

## 運作原理架構

```
電視開機 / 睡眠喚醒 / 按 Home 鍵
                │
                ▼
       MainActivity.onCreate()
                │
        ┌───────┴───────┐
        ▼               ▼
    [無人操作]       [使用者動作]
        │               │
    倒數結束        按 1/2/3 或 OK
        │               │
        └───────┬───────┘
                │
                ▼
    HdmiViewerActivity (TvView)
                │
                ├─ 直通調台 ──► TvView.tune(inputId, channelUri)
                │                         │
                │                         ▼
                │                  硬體圖層直通解碼
                │            (4K 60Hz, HDR10, Dolby Vision)
                │                         │
                │             [按 1/2/3：直接跳轉]
                │             [按 BACK：返回桌面]
                │
                └─ 冷開機尚未就緒 ──► 自動重試最多 3 次 (間隔 1500ms)
```

**核心架構設計：**
- **零 OEM App 依賴**：直接綁定 Android TIF 硬體 Passthrough 管道，跳過 `com.tcl.tv`。
- `excludeFromRecents="true"` — 不污染最近開啟的多工清單。
- `singleTask` — 避免重複創建 Activity 實例。
- 非同步 Handler 容錯重試機制 — 克服冷開機底層 TvInput 服務初始化延遲。

---

## 技術規格

| 參數 | 數值 |
|---|---|
| `minSdk` | 25 (Android 7.1) |
| `targetSdk` | 36 |
| `compileSdk` | 37 |
| AGP | 9.2.1 |
| Gradle | 9.4.1 (Java 25 JBR / Android Studio Ladybug+) |
| 依賴庫 | AndroidX Leanback (`androidx.leanback:leanback:1.0.0`), AndroidX AppCompat |
| 主題風格 | **純黑介面 (`#000000`)**，強制停用 ForceDark |
| 語言支援 | 英文 (English)、繁體中文 (Traditional Chinese)、簡體中文 (Simplified Chinese) |
| 程式碼最佳化 | R8 fullMode 混淆壓縮、無用代碼剝離與資源縮減 |
| 背景常駐記憶體 | 極低佔用；未啟用防假死守護與 CEC 監控時無常駐背景服務；啟用時以前台服務低負載運行 |

---

## 推薦專案

- [spocky/miproja1](https://github.com/spocky/miproja1) - Projectivity Launcher for Android TV / Google TV
