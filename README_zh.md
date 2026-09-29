# TCL HDMI Launcher

<p align="left">
  <a href="README.md">English</a> | <b>繁體中文</b>
</p>

> **專為「把 TCL 電視當純顯示器用」的發燒友與極簡主義者打造。**  
> 徹底擺脫 TCL 原廠臃腫慢速、充斥推薦廣告的系統首頁；開機微秒級直達外接裝置（Apple TV / PS5 / 電視盒 / 藍光播放器），讓電視回歸純粹頂級螢幕！

---

## 為什麼需要這個專案？（核心痛點與使用場景）

### 誰最適合使用？
如果你符合以下任一特徵，這個 Launcher 就是為你量身打造的：
- **TCL Android TV / Google TV 用戶**：家中使用 TCL 智慧電視，渴望徹底擺脫原廠系統的臃腫卡頓與開機廣告，專享為 TCL 底層電視輸入框架（`com.tcl.tvinput`）量身定制的極速啟動體驗。
- **純外接訊號源主義者**：日常主力都是外接設備，內建的智慧電視系統對你來說只是個「面板控制器」。
  - 🍏 **Apple TV 4K** — 日常串流追劇、電影觀影主力
  - 🎮 **PlayStation 5 (PS5) / Xbox Series X / Switch** — 次世代 4K HDR 遊戲娛樂
  - 📺 **Android TV / Google TV 盒子**（Chromecast with Google TV、NVIDIA Shield 等）
  - 💿 **4K UHD 藍光 / DVD 播放機** 或 **家庭劇院擴大機 (AV Receiver)**
- **厭惡內建首頁的臃腫與廣告**：TCL 原廠 Launcher 開機載入緩慢、首頁塞滿不需要的線上影音推薦、佔用有限的系統記憶體。
- **想要「開機即用」的直覺體驗**：開啟電視後，希望像傳統電視或高階顯示器一樣，幾秒鐘內自動跳轉到最常看的訊號源（如 Apple TV），不必拿著遙控器在選單裡點來點去。
- **多設備切換要求快速極致**：家裡同時接了 PS5 與 Apple TV，希望按個數字鍵 `1` 或 `2` 就能瞬間無縫切換，不用呼叫繁瑣的輸入來源選單。

---

## 最佳劇院外接配置範例

```
                 ┌─────────────────────────────────┐
                 │    TCL 4K QLED / Mini-LED 電視  │
                 │   (純顯示器模式 Pure Monitor)   │
                 └────────────────┬────────────────┘
                                  │
      ┌───────────────────────────┼───────────────────────────┐
      │                           │                           │
  [ HDMI 1 ]                  [ HDMI 2 ]                  [ HDMI 3 (eARC) ]
      │                           │                           │
      ▼                           ▼                           ▼
🎮 PlayStation 5            📺 藍光播放機 / Switch       🍏 Apple TV 4K / 擴大機
(開機按數字鍵 1 直達)        (開機按數字鍵 2 直達)       (設為預設：開機 3 秒自動進入)
```

- **預設場景**：開機後，倒數 3 秒（可自訂）自動進入 **HDMI 3 (Apple TV 4K)**，完全免按任何按鍵。
- **遊戲場景**：想打電動時，遙控器直接按下數字鍵 `1`，微秒級直達 **HDMI 1 (PS5)**。
- **音訊與畫質調校**：右上角一鍵直達 TCL 原生影像與音效設定 (`com.tcl.settings`)，免去原生 Launcher 繁瑣層級。

---

## 核心亮點特色

1. **開機自動倒數直達**：
   - 支援自訂倒數秒數：`關閉`、`1s`、`2s`、`3s（預設）`、`5s`、`10s`、`15s`、`30s`。
   - 開機或按下 Home 鍵，倒數結束即微秒級自動切換至預設訊號源。
   - 倒數期間任意輕按方向鍵（D-pad）即可暫停倒數，從容選取其他訊號源。
2. **遙控器數字鍵極速跳轉**：
   - 遙控器按下 `1` / `2` / `3`，立即直達對應 HDMI 埠，切換無延遲。
3. **長按 OK 鍵自由設定預設訊號源**：
   - 在任一 HDMI 卡片上「長按 OK 鍵」，即可將該埠標記為開機預設訊號源。
4. **極致輕量、零後台常駐、零 GC 負載**：
   - **混淆後僅約 9.0 KB**，全純程式碼 View 樹，0 XML 解析開銷、0 反射。
   - 倒數計時熱路徑達成 **0 記憶體配置 (0 GC)**，絕不卡頓。
   - 切換訊號後立即執行 `finishAndRemoveTask()` 退出並釋放所有記憶體，將 TV 晶片算力 100% 留給 4K 影像解碼與音效處理。
5. **純黑電視專用介面 (Pure Black #000000)**：
   - 專為暗室家庭劇院與 OLED / QLED 局部控光最佳化，絕無刺眼白色閃爍。
6. **多國語言介面 (English / 繁體中文 / 簡體中文)**：
   - 依電視系統語言自動適配，無論全英環境或繁簡中文皆完美顯示。
7. **完整保留必要電視功能**：
   - **TCL 設定快捷鍵**：右上角獨立按鈕，一鍵開啟 TCL 原廠電視畫質/音效設定頁。
   - **輕量應用程式抽屜 (App Drawer)**：若偶爾需開啟內建智慧電視 App，內建極速清單，支援 TV/手機側載分類、最近使用與長按解除安裝/停用。
8. **App 模式與應用程式自動啟動 (App Mode & Auto-Open)**：
   - 頂部狀態列提供「App 模式」一鍵切換。開啟後，開機或按 Home 鍵預設直達應用程式抽屜。
   - 在清單中「長按 OK」可將常用 App 設為「自動啟動」；開機倒數結束自動直達指定 App（如 YouTube、Netflix、動畫瘋等）。
9. **自建原生 HDMI Viewer (`TvView`) — 完全擺脫 `com.tcl.tv` 依賴**：
   - 內建全螢幕 HDMI 播放器（`HdmiViewerActivity`），直接由 Android 官方 TV Input Framework（`android.media.tv.TvView`）驅動。
   - 徹底終結對 TCL 原廠電視播放器（`com.tcl.tv.TVActivity`）的依賴，零廣告、零原廠干擾橫幅。
   - 完整支援 4K @ 60Hz、HDR10 與 Dolby Vision 硬體圖層直通解碼。
   - **全靜默 0 浮層 0 Toast 硬體直通（徹底根除黑影與殘影）**：
     - **純淨硬體輸出**：播放器內部完全不彈出任何 Toast、不繪製任何自訂 View 浮層，直接將 `TvView` 作為唯一 ContentView。
     - **根絕電視 GPU 快取 Bug**：TCL 電視晶片（Realtek RTD2851）的圖層合成器在處理浮動視窗淡出（包含 Android 原生 Toast 淡出動畫）時，會將半透明邊界卡在視訊圖層上形成「黑色矩形黑影」。完全不呼叫 Toast 可保證畫面 100% 純淨無任何瑕疵。
     - **動態熱插拔感知**：在無訊號狀態下開啟外接設備或插入 HDMI，晶片訊號就緒時硬體視訊瞬間直接點亮。
   - **獨立視窗架構與精準硬體埠對照 (Hardware ID Mapping)**：
     - 採用獨立 Task 視窗架構（`FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_MULTIPLE_TASK` 與獨立 `taskAffinity`），播放畫面與桌面分層獨立管理。
     - 針對 R851T02 平台精準鎖定實體晶片 Input ID（`HW1413744128`、`HW1413744384`、`HW1413744640`）並全面實作 `onNewIntent`，徹底修復官方 TIF 因納入 CEC/AV 字串排序偏移導致 HDMI 1/2 切換混淆或跳至 HDMI 3 的缺陷。
   - 現在可以**安心直接停用 `com.tcl.tv` 整包原廠電視 App**，完全不影響 HDMI 畫面顯示！

---

## 畫面截圖 (Screenshots)

| 主畫面（HDMI 訊號源切換） | 倒數秒數設定對話框 |
|:---:|:---:|
| ![主畫面](readme_pic/Screenshot_20260925_222244.png) | ![倒數設定](readme_pic/Screenshot_20260925_222304.png) |
| **呼叫 TCL 原生設定** | **應用程式列表（App Drawer）** |
| ![TCL 設定](readme_pic/Screenshot_20260925_222322.png) | ![應用程式列表](readme_pic/Screenshot_20260925_222344.png) |
| **系統應用管理（長按 OK）** | **第三方應用管理（長按 OK）** |
| ![系統應用管理](readme_pic/Screenshot_20260925_222413.png) | ![第三方應用管理](readme_pic/Screenshot_20260925_222455.png) |

---

## 實測驗證裝置 (Tested Device)

- **測試機型**：**TCL 65C715**（C715 系列 65 吋 4K QLED Android TV）
- **機芯平台 (Chassis Platform)**：**RTD2851 / R851T02**
  - **軟體版本**：`V8-R851T02-LF1V701.000031`
  - **TV+OS 版本**：`V5.2.0 (V8-2112290-MF1V520)`
  - **用戶端平台識別**：`TCL-AP-RT2851-S1`
  - **產品 ID**：`660`
  - **Linux 核心版本**：`4.14.236+ #2 SMP PREEMPT Tue Jan 18 20:32:54 CST 2022 armv7l`
  > [!NOTE]
  > **關於 R851T02 平台：**  
  > `R851T02` 是 TCL 廣泛應用於多款主力 Android TV（涵蓋 C715、P715、P615、S434 等系列）的晶片與主機板架構（Realtek RTD2851 方案）。凡是搭載 **R851T02 機芯架構** 的機型，其底層電視訊號輸入服務（`com.tcl.tvinput`）與 Passthrough 直通架構規格均高度統一。
- **機型規格摘要**：
  - **螢幕面板**：65" 4K UHD (3840 × 2160) 量子點 QLED、60Hz、支援 Dolby Vision / HDR10+
  - **HDMI 配置**：共 3 組實體 HDMI 2.0 端子（支援 HDCP 2.2、HDMI-ARC / CEC）
  - **處理器與架構**：4 核心 ARM Cortex-A55 處理器（32 位元用戶空間 armv7l）、2 GB RAM / 16 GB ROM
  - **系統環境**：Android TV 11 (Android R)
  - **實測結果**：HDMI 1 ~ 3 訊號源微秒級切換、倒數計時自動跳轉、開機預設、遙控器按鍵（數字鍵/選單鍵/設定鍵）均 100% 驗證通過。

## 實體裝置訊號源對照表（R851T02 / C715 實測驗證）

| 訊號源 | Port | Hardware ID | 完整 TvInput ID |
|---|---|---|---|
| **HDMI 1** | 1 | `1413744128` | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744128` |
| **HDMI 2** | 2 | `1413744384` | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744384` |
| **HDMI 3** | 3 | `1413744640` | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744640` |

### ADB 測試各訊號源切換

本專案支援兩種 ADB 切換方式：

#### 方式一：顯式組件極速切換（推薦，微秒級直達）

```bash
# 切換至 HDMI 1
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 1

# 切換至 HDMI 2
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 2

# 切換至 HDMI 3
adb shell am start -n com.lnu.tclhdmilauncher/.HdmiViewerActivity --ei port 3
```

#### 方式二：標準 Android TV Passthrough 意圖（隱式廣播接管）

> [!NOTE]
> 本專案已在 `AndroidManifest.xml` 中宣告接管 `android.media.tv` 與 `vnd.android.cursor.item/channel` 意圖。即使已停用原廠 `com.tcl.tv`，下列標準 Android TV 指令亦可自動被本專案之 `HdmiViewerActivity` 攔截並無縫切換：

```bash
# 切換 HDMI 1
adb shell am start -a android.intent.action.VIEW \
  -d "content://android.media.tv/passthrough/com.tcl.tvinput%2F.passthroughinput.TvPassThroughService%2FHW1413744128"

# 切換 HDMI 2
adb shell am start -a android.intent.action.VIEW \
  -d "content://android.media.tv/passthrough/com.tcl.tvinput%2F.passthroughinput.TvPassThroughService%2FHW1413744384"

# 切換 HDMI 3
adb shell am start -a android.intent.action.VIEW \
  -d "content://android.media.tv/passthrough/com.tcl.tvinput%2F.passthroughinput.TvPassThroughService%2FHW1413744640"
```

---

## 遙控器操作指南 (Remote Control Shortcuts)

| 按鍵 | 操作效果 |
|---|---|
| **方向鍵 (D-Pad)** | 移動卡片焦點；在倒數進行時按任意方向鍵可**取消自動跳轉** |
| **OK / 確認鍵** | 立即切換至當前焦點選取的 HDMI 訊號源（或啟動 App） |
| **長按 OK 鍵 (主畫面)** | 將當前 HDMI 卡片設為**開機預設訊號源** |
| **長按 OK 鍵 (App 清單)** | 開啟選單：**設為自動啟動**、解除安裝、停用、應用程式資訊 |
| **數字鍵 `1` / `2` / `3`** | **秒切快捷鍵**：在 Launcher 桌面或 Viewer 播放器中均可直切 HDMI 1 / 2 / 3 |
| **選單鍵 (MENU)** | 開啟倒數設定對話框（桌面）/ 關閉並返回桌面（播放器） |
| **設定鍵 (SETTINGS)** | 一鍵呼叫 TCL 原生設定選單 (`com.tcl.settings`) |
| **返回鍵 (BACK)** | 離開對話框，或從播放器/應用程式清單返回桌面 |

---

## 安裝與設為預設桌面教學

### 步驟 1：建置 Debug 或 Release APK

```bash
# 建置 Debug APK
./gradlew assembleDebug

# 或建置極限混淆 Release APK (僅 ~9.0 KB)
./gradlew assembleRelease
```

### 步驟 2：開啟電視 ADB 開發者模式並安裝
1. 在電視系統設定中，前往「關於」-> 連續點擊「版本號碼 (Build Number)」7 次以啟用開發人員選項。
2. 在「開發人員選項」中開啟「USB 偵錯」或「網路偵錯」。
3. 使用電腦終端機連線並安裝：

```bash
# 透過 Wi-Fi ADB 連線電視（以電視 IP 為準）
adb connect 192.168.1.xxx:5555

# 安裝 APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 步驟 3：設為預設 TV Launcher（首頁啟動器）

```bash
# 指派本 App 為系統預設 Launcher
adb shell cmd package set-home-activity com.lnu.tclhdmilauncher/.MainActivity
```
*按遙控器「Home 首頁鍵」，系統即會以本 App 作為純淨 HDMI 啟動桌面。*

### 步驟 4：停用 TCL 原生臃腫 Launcher（選用進階步驟，追求極限純淨）
若希望杜絕原生 Launcher 任何後台喚醒與資源佔用，可安全停用（非刪除，日後可無損還原）：

```bash
# 1. 查詢 TCL 內建 Launcher 套件名稱
adb shell pm list packages | grep -i launcher

# 2. 停用 TCL 內建 Launcher（以查詢到的套件名為準）
adb shell pm disable-user --user 0 <tcl.launcher.package.name>
```

> [!TIP]
> **隨時可無損還原：**  
> 若日後需要換回 TCL 原廠桌面，只需執行：
> ```bash
> adb shell pm enable <tcl.launcher.package.name>
> adb shell cmd package clear-preferred-activities com.lnu.tclhdmilauncher
> ```

---

### 步驟 5：（強烈建議）開啟「待機喚醒與 Home 鍵映射」

在 TCL 等 Android TV 上，開啟無障礙服務將為本 App 賦予系統最高保障：
1. **待機喚醒防掉源**：電視睡眠喚醒時 100% 強制返回本 Launcher 桌面（徹底避免掉入 HDMI 無訊號或 AV 端子）。
2. **Button Mapper（Home 鍵重定向）**：無論目前在任何 App，只要按下遙控器 **Home 鍵**，都會直接攔截並切換回本 HDMI Launcher 桌面！

**啟用方式：**
- 開啟 Launcher，頂部狀態列的「喚醒保障」若顯示 `⚠️ 喚醒保障: 未開啟`，按確認點選「前往設定」（或手動至電視「設定」➔「裝置偏好設定」➔「無障礙」）。
- 將 **「HDMI Launcher 待機喚醒與 Home 鍵映射」** 開啟即可。

---

### 步驟 6：（重要排查）解決 Apple TV / HDMI-CEC 關機後電視自動喚醒或螢幕沒關

#### 問題現象
使用 Apple TV 或其他外接設備透過 HDMI-CEC 關機/待機時，部分 TCL 電視（如 C715 / RTD2851 平台）會出現**螢幕剛暗下不到 1 秒又立刻亮起並開啟 TCL TV App**，或者**看似沒關機**。

#### 原因深度剖析（Logcat 追蹤結果）
1. **Apple TV 的 CEC 待機連發機制**：
   根據 HDMI-CEC 規範，Apple TV 休眠時會在 ~79ms 內連續發送兩筆 Standby 指令（一筆廣播 `4F:36`、一筆單播 `40:36`）。
2. **TCL 原廠韌體的異常處理**：
   TCL 底層 `TclPowerManagerService` 收到每筆指令時均會發送 `com.tcl.voicestandby` 廣播。
   系統內建的 `com.tcl.tv` 套件中的 `VoicePowerBroadcastReceiver` 監聽到廣播後，會調用內部方法並**模擬注入硬體電源鍵（`keyCode 4000 -> 26 KEYCODE_POWER`）**。
3. **95ms 內連續兩次電源鍵**：
   - **第 1 次電源鍵**：電視螢幕關閉，進入休眠狀態（`interactive=false`）。
   - **第 2 次電源鍵**（約 95ms 後到達）：此時電視已在睡眠中，這第二下電源鍵**立刻把剛睡著的電視敲醒**（甚至被 Android 系統辨識為雙擊電源鍵手勢）！

#### 終極治本解法：直接解除該使用者的安裝（最乾淨，效果最佳！）

由於本專案現已內建原生 HDMI 播放器（`HdmiViewerActivity`，基於 Android TIF `android.media.tv.TvView` 獨立渲染），**系統已不再需要 `com.tcl.tv` 提供任何畫面！**

經實測，**`pm uninstall -k --user 0 com.tcl.tv` 效果最徹底**，能完全切斷 `com.tcl.tv` 被 system_server 喚醒或彈出畫面的問題，比純 `pm disable-user` 更乾淨俐落：

```bash
# 1. 解除 User 0 的安裝（最乾淨彻底，實測效果最好！）
adb shell pm uninstall -k --user 0 com.tcl.tv

# 2. （可選）關閉系統電源鍵雙擊手勢（防止 95ms 雙擊誤判定）
adb shell settings put secure camera_double_tap_power_gesture_disabled 1
```

> [!TIP]
> **隨時可還原：**
> 如果未來需要恢復原廠 `com.tcl.tv`，只需執行以下指令即可無損復原安裝：
> ```bash
> adb shell cmd package install-existing com.tcl.tv
> ```

> [!NOTE]
> **重要架構定位剖析：`com.tcl.tv`（訊號 UI App）vs `com.tcl.tvinput`（硬體驅動 HAL）**  
> - **`com.tcl.tv`（原廠電視訊號 UI App）**：它本質上是一個**純 UI 前台播放器**，專門負責繪製所有「電視訊號的狀態 UI」（如切換訊號源時的橫幅、右上角 Dolby Vision / HDR 標籤、無訊號時的畫面提示、第四台頻道選單等），並內嵌了惹禍的 `VoicePowerBroadcastReceiver`。由於其 UI 邏輯預設前台永遠只有它自己，在第三方環境下容易因定時器失常導致各類黑屏、浮層與殘留。**在內建原生 TIF 播放器的本 App 接管後，此 UI App 可 100% 安全解除安裝，完全不影響任何 HDMI 視訊畫面！**  
> - **`com.tcl.tvinput`（底層硬體訊號 HAL 服務）**：系統底層驅動服務（`TvPassThroughService`），直接與 Realtek RTD2851 晶片對接，負責實體 HDMI Rx 埠訊號握手、HDCP 金鑰解密與硬體圖層打洞。**此服務為視訊硬體核心，必須永久保留！**

> [!TIP]
> **次選方案：僅停用套件或個別組件：**  
> 若您暫時不想解除安裝：  
> - 停用整包：`adb shell pm disable-user --user 0 com.tcl.tv`
> - 僅停用組件：`adb shell pm disable com.tcl.tv/com.tcl.tv.receiver.VoicePowerBroadcastReceiver`

---

### 步驟 7：（重要排查）解決 Dolby Vision / HDR 10 原廠 Toast 橫幅殘留與卡死問題

#### 問題現象
外接設備（如 Apple TV 4K、PS5、Xbox）切換為 Dolby Vision 或 HDR 格式時，電視畫面右上角會彈出原廠的 Dolby Vision / HDR 標籤。在非原廠電視 App 前台時，該橫幅往往**一直停留卡死在畫面上無法自動消失**，且即使執行過 `pm disable com.tcl.tv`，該橫幅依然會出現。

#### 原因深度剖析（DEX 反編譯與 Logcat 追蹤結果）
1. **系統級常駐程序無法停用**：
   `com.tcl.tv` 在系統中宣告為 `android:persistent="true"` 且使用系統最高權限 `uid=1000 (system)`。即使執行 `pm disable` 或 `kill`，Android Zygote 也會在幾毫秒內將其無條件自動拉起。
2. **自動消失邏輯判斷失常（實機 Logcat 印證）**：
   反編譯 `/product/app/TIF_LiveTV/TIF_LiveTV.apk`（`com.tcl.tv`）與 `/product/app/SystemSettings/SystemSettings.apk`（`com.tcl.settings`）的 DEX 字節碼發現：
   - 當訊號源轉為 HDR / Dolby Vision 時，底層 `tcl_system_server` 會連續廣播 `com.tcl.Hdr`。
   - `com.tcl.tv` 內部的 `DolbyToast` 會透過 `WindowManager.addView` 建立名為 `Toast` 的懸浮視窗（`CToast`）。
   - 關鍵在於 `DolbyToast.checkShowDolby()` 檢查了 `isTVTop`（判斷目前前台是否為原廠 `com.tcl.tv.TVActivity`）。當使用第三方 Launcher 或獨立 HDMI Viewer 時，實機 Logcat 精準捕捉到：
     ```text
     com.tcl.tv        D  checkShowDolby() isTVTop = false
     system_server     I  mayAddFloatingWindow w = Window{2ad38c3 u0 Toast}
     ```
   - 因為 `isTVTop == false`，導致自動定時延遲移除（`mHide`）的邏輯失常、被覆蓋或取消，視窗因而**永久殘留懸掛在畫面上**！
   - 同時 `com.tcl.settings.receiver.HdrReceiver` 也收到廣播並重複觸發 `Toast.show()`，造成多重橫幅覆蓋。

#### 終極治本解法：透過 AppOps 精準封鎖浮動視窗與 Toast 權限

Android 系統內建了強大的底層權限控管機制（`appops`）。經實測分析：
- **`com.tcl.tv`**：原廠電視 App 的 DolbyToast 是透過自訂懸浮視窗（`CToast`，`SYSTEM_ALERT_WINDOW`）卡死在畫面上。**必須全面封鎖其懸浮視窗與 Toast 權限**。
- **`com.tcl.settings`**：原廠畫質/音效設定選單本身是採用系統懸浮窗（`type:2003`）渲染，因此**必須保留 `SYSTEM_ALERT_WINDOW`，僅封鎖 `TOAST_WINDOW`**，即可精準阻絕其 `HdrReceiver` 彈出 Toast，同時 100% 完整保留右上角 TCL 原廠畫質/音效設定功能！

請透過 ADB 執行以下精確配置：

```bash
# 1. 徹底封鎖 com.tcl.tv 彈出懸浮視窗與 Toast（解決 DolbyToast 卡死殘留）
adb shell appops set com.tcl.tv SYSTEM_ALERT_WINDOW deny
adb shell appops set com.tcl.tv TOAST_WINDOW deny

# 2. 封鎖 com.tcl.settings 的 Toast 權限（保留系統懸浮窗以維持設定選單正常彈出）
adb shell appops set com.tcl.settings TOAST_WINDOW deny
adb shell appops set com.tcl.settings SYSTEM_ALERT_WINDOW allow
```

> [!NOTE]
> `appops` 的設定會直接永久儲存在電視的 `/data/system/appops.xml` 中，**電視重新開機後依然永久生效**。

---

### 步驟 8：（重要排查）解決 TCL TvView 影像穿透黑屏卡死（`invalid sideband 0/0`）與殘影問題

#### 問題現象：進入 HDMI 畫面完全出不來，卡在黑底
進入 `HdmiViewerActivity` 後，底層視訊晶片已成功鎖定訊號，但畫面始終全黑，Logcat 出現異常錯誤：
```text
VideoComposer  composer-rtk@2.1-service  E  invalid sideband 0xb6040750, 0/0
TclWinInjector system_server             I  mayAddFloatingWindow w = ...HdmiViewerActivity float
```

#### 完整硬體出圖時序解析（實機 Logcat 真實記錄）
透過 Logcat 追蹤 Realtek RTD2851 平台從 HDMI 握手到視訊出圖的底層時序：

1. **底層硬體解碼成功握手，鎖定 4K 60Hz HDR 視訊：**
   ```text
   2026-09-29 15:57:40.634  sitatvservice   D  SetInputRegion wId=0, x=0, y=0, w=3840, h=2160, HDRType=4
   2026-09-29 15:57:41.476  com.tcl.tvinput D  audioFormat= 0 width = 2160 height = 3840 videoFrameRate = 60.0
   ```
   *外接訊號（Apple TV / PS5 等）成功送出 4K 60Hz HDR 畫面，TV 底層晶片鎖定訊號並開始解碼。*

2. **TvView 成功觸發渲染回呼：**
   ```text
   2026-09-29 15:57:41.502  com.lnu.tclhdmilauncher I  TvView onVideoAvailable: com.tcl.tvinput/.../HW1413744640 (video rendering active)
   ```
   *本 App 準確抓到了硬體開始渲染視訊的時刻。*

3. **GPU 硬體合成器（HWC）異常報錯：**
   ```text
   2026-09-29 15:57:40.278  composer-rtk@2.1-service  E  invalid sideband 0xb6040750, 0/0
   2026-09-29 15:57:40.311  composer-rtk@2.1-service  E  invalid sideband 0xb6040d30, 0/0
   ```

#### 根本原因深度分析
1. **`windowIsTranslucent=true` 導致雙層視窗遮擋與尺寸失效**：
   - 先前為避免遮擋視訊，在主題中設定了 `android:windowIsTranslucent="true"`。
   - **致命遮蔽（MainActivity 遮擋）**：上層為透明時，Android 判定 `r.occludesParent = false`，底層 `MainActivity`（帶有全域純黑背景）**不會被調用 `onStop()` 隱藏**，而是作為中介層持續留在畫面堆疊中，死死蓋在底層硬體視訊上面！
   - **OEM 浮動視窗誤判**：TCL 定製系統服務（`TclWinInjector`）將透明視窗視為「浮動小窗 (float)」，導致 Realtek 晶片硬體合成器（`RTKHWC2` / `VideoComposer`）收到無效的視訊圖層邊界（寬高為 0，即 `invalid sideband ..., 0/0`），硬體無法正常給予視訊輸出。
2. **Realtek RTD2851 圖層合成器致命缺陷：Alpha 淡出髒矩形凍結（連「系統音量條」都會殘留）**：
   - 經深入實測驗證，在播放 4K 60Hz HDR / Dolby Vision 硬體直通視訊時，**不僅是 Toast，就連 Android 原廠的「系統音量條 (Volume Dialog)」在消失退場時，都會在螢幕上留下不可磨滅的暗色矩形殘影！**
   - **底層機理**：Realtek RTD2851 的 HWC 為節省功耗採用「髒矩形 (Dirty Rect)」增量更新。當任何 View（音量條、Toast、對話框）執行淡出退場動畫（`alpha: 1.0 -> 0.0`，即 Logcat 中的 `AnimatingExit`）時，在動畫逼近結尾的最後一瞬間，HWC 誤判該區塊「無更新」，**直接將半透明過渡幀的 Alpha 快取永遠凍結在圖層合成緩衝區中**，浮在底層硬體視訊上形成黑影烙印！

#### 終極解法與全域系統優化配置
1. **App 端極限純粹化（全螢幕實體主題 + 0 View 0 浮層 0 Toast 直通）**：
   - 在 `Theme.HdmiViewer` 中徹底移除 `windowIsTranslucent`，使用純黑標準全螢幕視窗，確保底層 `MainActivity` 立即進入 `onStop()` 退出合成。
   - 徹底移除 `blackCoverLayout`、進度條、文字等所有覆蓋 View，直接以 `tvView` 作為唯一 ContentView。
   - `HdmiViewerActivity` 內部完全不調用任何 `Toast`，無任何懸浮或動畫視窗，切換訊號源與出圖 100% 靜默。
2. **系統級解法：關閉系統動畫縮放（徹底杜絕系統音量條與任何彈窗殘影）**：
   - 由於此晶片 Bug 的觸發條件是 **「半透明漸變淡出動畫（Alpha Fade）」**，只要將系統動畫縮放設為 0（瞬間出現、瞬間消失，無漸變過渡幀），HWC 就能乾淨俐落地切換圖層，徹底杜絕音量條與彈窗退場時的黑影凍結！
   - 請透過電腦終端執行以下 ADB 指令：
     ```bash
     # 關閉視窗動畫、轉場動畫與動畫時長縮放（立即生效且重啟依然有效）
     adb shell settings put global window_animation_scale 0
     adb shell settings put global transition_animation_scale 0
     adb shell settings put global animator_duration_scale 0
     ```
4. **底層 Session 競態與畫面凍死排查（重要修正）**：
   - **痛點**：若在 `tune()` 之前同步呼叫 `tvView.reset()`，因 `reset()` 在底層 HAL 為異步釋放（需時 ~250ms），會與隨後的 `tune()` 產生競態衝突，拋出 `NullPointerException: getPackageName()`，導致硬體解碼器拋出 `onVideoUnavailable(reason=0)` 並將畫面永久凍死在最後一幀！
   - **治本架構**：
     1. 移除 `tune()` 前盲目的 `reset()`，交由 TIF 平滑切換。
     2. 在 `onStop()` 嚴格執行 `tvView.reset()` 釋放硬體 Session，避免重入時新舊實例爭搶 HDMI Rx 晶片。
     3. 在 `onVideoUnavailable(reason=0)` 加入 600ms 自動自我修復重新調諧（Auto Re-tune），瞬間敲醒晶片！
5. **終極硬體與韌體解法：降級至 Android 9 官方韌體（`V8-R851T02-LF1V662`）**：
   - **問題根本原因**：TCL 在 Android 11 (V7xx / V8xx) 韌體中，強行將現代 HWC2 驅動移植到 Realtek RTD2851 晶片上，導致 `VideoComposer` 出現嚴重的 Sideband 圖層死鎖與記憶體洩漏，引發 HDMI 畫面隨機凍死、音訊直通中斷以及 `com.tcl.tv` 與系統 UI 互搶圖層的致命缺陷。
   - **V662 (Android 9) 實測驗證**：
     - `V8-R851T02-LF1V662` 是全球 XDA 與 4PDA 論壇公認 R851T02 平台**最穩定、調校最成熟的終極版本**。
     - **徹底根治 HDMI 畫面凍結（Freeze Bug）**：Android 9 使用原生成熟的 SurfaceView 渲染管線，無 Sideband 髒矩形快取死鎖問題。
     - **超輕量與記憶體釋放**：無 Android 11 的激進背景守護與繁重監控，系統可用 RAM 多出約 300MB ~ 500MB，待機喚醒與 HDMI 握手皆在毫秒級完成。
     - **刷機方式**：若從 Android 11 降級，需將 `Update.img` 放入 FAT32 隨身碟，電視拔掉插頭後按住機身實體電源鍵插電強制刷入。
6. **臨時黑影「一鍵沖刷消除」技巧（Android 11 適用）**：
   - 若在未關閉動畫前畫面上已出現音量條殘留黑影，只需按下遙控器 **Home 鍵** 返回 Launcher（全螢幕 View Tree 會強制重寫覆蓋整個 HWC 緩衝區），再按確認鍵進入 HDMI，殘影即刻完全消失！


## 運作原理架構

```
電視開機 / 睡眠喚醒 / 按 Home 鍵
                │
                ▼
       MainActivity.onCreate()
                │
        ┌───────┴───────┐
        ▼               ▼
   [無人操作 (預設)]  [用戶操作]
        │               │
   倒數 N 秒結束   遙控器按 1/2/3 或 OK
        │               │
        └───────┬───────┘
                │
                ▼
    HdmiViewerActivity (TvView)
                │
                ├─ 直接調諧 ──► TvView.tune(inputId, channelUri)
                │                      │
                │                      ▼
                │               硬體覆蓋層直通解碼
                │          (4K 60Hz, HDR10, Dolby Vision)
                │                      │
                │             [按 1/2/3：秒切其他 HDMI]
                │             [按 返回/選單：退出回桌面]
                │
                └─ 冷開機硬體未就緒 ──► 自動延遲重試最多 3 次 (1500ms)
```

**關鍵設計原則：**
- **零 OEM 原廠 App 依賴**：透過 `TvView.tune()` 直通 Android TIF 硬體 Passthrough 管線，徹底擺脫 `com.tcl.tv` 的臃腫、廣告與 Bug。
- `excludeFromRecents="true"` — 不污染多工清單。
- `singleTask` — 防止多重實例堆疊。
- 非阻塞 Handler 重試 — 冷開機 TV 底層硬體輸入服務初始化時自動容錯。

---

## 推薦專案 (Recommended Projects)

- [spocky/miproja1](https://github.com/spocky/miproja1) - Projectivity Launcher for Android TV / Google TV。

---

## 技術規格

| 項目 | 規格值 |
|---|---|
| `minSdk` | 25 (Android 7.1) |
| `targetSdk` | 36 |
| `compileSdk` | 37 |
| AGP | 9.2.1 |
| Gradle | 9.4.1 (相容 Java 25 JBR / Android Studio Ladybug+) |
| 第三方依賴 | **0 依賴**（100% Android SDK 原生呼叫） |
| 主題規範 | **強制純黑 Pure Black (#000000)**，禁用 ForceDark |
| 國際化 | 支援 English、繁體中文 (TW/HK)、簡體中文 (CN) |
| Release 體積 | R8 fullMode 混淆壓縮後僅約 **300 KB** |
| 背景記憶體 | 跳轉後呼叫 `finishAndRemoveTask()`，**0 背景常駐** |
