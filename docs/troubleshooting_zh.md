# 重要排查與技術疑難排解 (Troubleshooting)

本文件詳細記錄針對 TCL Android 電視（特別是 Realtek RTD2851 / R851T02 機型架構）在硬體、CEC 與原廠韌體問題上的底層日誌追蹤分析、推測成因與實測解決方案。

---

## 目錄
- [排查 1：解決 Apple TV / HDMI-CEC 關機後電視自動喚醒或螢幕沒關](#排查-1解決-apple-tv--hdmi-cec-關機後電視自動喚醒或螢幕沒關)
- [排查 2：解決 Dolby Vision / HDR 10 原廠橫幅殘留與卡死](#排查-2解決-dolby-vision--hdr-10-原廠橫幅殘留與卡死)
- [排查 3：解決 TvView 影像穿透黑屏（invalid sideband 0/0）與殘影](#排查-3解決-tvview-影像穿透黑屏invalid-sideband-00與殘影)
- [排查 4：實現真正的 HDMI-CEC 喚醒自動切換訊號源](#排查-4實現真正的-hdmi-cec-喚醒自動切換訊號源)

---

### 排查 1：解決 Apple TV / HDMI-CEC 關機後電視自動喚醒或螢幕沒關

#### 問題現象
當透過 Apple TV 或其他外接 HDMI-CEC 裝置將電視關機（進入待機狀態）時，部分 TCL 電視（如 C715 / RTD2851 平台）會短暫關閉螢幕，但隨即在 1 秒內自動亮屏並啟動 TCL 原廠電視 App，或者看似根本沒有正常關閉螢幕。

#### 根本原因分析 (Logcat 追蹤)
1. **Apple TV 的雙重 CEC 待機指令**：  
   依據 HDMI-CEC 規範，Apple TV 休眠時會在約 79ms 內連續發送兩條 Standby 幀（先廣播 `4F:36`，再單播 `40:36`）。
2. **TCL 原廠韌體行為**：  
   TCL 的 `TclPowerManagerService` 每收到一次 Standby 即發送一則廣播 `com.tcl.voicestandby`。  
   內建的 `com.tcl.tv` App 中註冊了 `VoicePowerBroadcastReceiver`，收到此廣播後會**模擬硬體電源鍵動作（`keyCode 4000 -> 26 KEYCODE_POWER`）**。
3. **95ms 內連續注入兩次電源鍵**：  
   - **第一次按鍵**：螢幕關閉，電視進入休眠狀態（`interactive=false`）。  
   - **第二次按鍵**（約 95ms 後）：在休眠狀態下抵達，**立即喚醒了電視**（甚至觸發 Android 連按兩次電源鍵開啟相機的手勢）。

#### 實測解法：直接解除安裝 User 0 的 `com.tcl.tv`（在 TCL 65C715 + V8-R851T02-LF1V662 實測有效）

由於本專案已內建以 Android 官方 TIF 架構（`android.media.tv.TvView`）運作的直通播放器（`HdmiViewerActivity`），**完全不再需要 `com.tcl.tv`**。

實測顯示，執行 **`pm uninstall -k --user 0 com.tcl.tv`** 效果最佳，徹底阻斷 `com.tcl.tv` 被 `system_server` 喚醒拉回前台的機會：

```bash
# 1. 針對 User 0 解除安裝 com.tcl.tv（在 TCL 65C715 實測有效）
adb shell pm uninstall -k --user 0 com.tcl.tv

# 2.（選用）停用連按兩次電源鍵開啟相機手勢
adb shell settings put secure camera_double_tap_power_gesture_disabled 1
```

> [!TIP]
> **隨時可無損還原：**  
> 若日後需要裝回原廠 `com.tcl.tv`，只需執行：
> ```bash
> adb shell cmd package install-existing com.tcl.tv
> ```

> [!NOTE]
> **重要架構差異：`com.tcl.tv`（訊號 UI App）與 `com.tcl.tvinput`（硬體 HAL）**  
> - **`com.tcl.tv`（原廠電視訊號 UI App）**：它本質上是一個**純 UI 前台播放器**，專門負責繪製所有「電視訊號的狀態 UI」（如訊號橫幅、右上角 Dolby Vision / HDR 標籤、無訊號提示、電視頻道選單等），並包含問題來源 `VoicePowerBroadcastReceiver`。在有自建 TIF 播放器接管後，該 UI App 可安全解除安裝，不影響任何 HDMI 視訊輸出（在 TCL 65C715 實測無誤）。  
> - **`com.tcl.tvinput`（底層硬體 HAL 服務）**：直接與 Realtek RTD2851 SoC 溝通的硬體抽象層服務（`TvPassThroughService`），掌管 HDMI Rx 實體握手、HDCP 解密與硬體 sideband 挖洞合成。**此服務為硬體影像引擎，絕對不可停用。**

> [!TIP]
> **替代方案：若不想解除安裝**  
> - 停用整個套件：`adb shell pm disable-user --user 0 com.tcl.tv`  
> - 僅停用該組件：`adb shell pm disable com.tcl.tv/com.tcl.tv.receiver.VoicePowerBroadcastReceiver`

---

### 排查 2：解決 Dolby Vision / HDR 10 原廠橫幅殘留與卡死

#### 問題現象
當外接裝置（Apple TV 4K、PS5、Xbox）切換為 Dolby Vision 或 HDR 格式時，TCL 系統會在右上角彈出原廠 Dolby Vision / HDR 提示橫幅。在第三方啟動器或獨立播放器中，此提示經常**永久卡在螢幕右上角不消失**，即使 `com.tcl.tv` 已被 `pm disable` 亦然。

#### 根本原因分析 (DEX 反編譯與 Logcat 追蹤)
1. **系統 UID 常駐進程被殺後重啟**：  
   `com.tcl.tv` 宣告了 `android:persistent="true"` 且具備系統權限 `uid=1000 (system)`，即使用 `kill` 殺掉，Android Zygote 也會在極短時間內重啟它。
2. **定時銷毀邏輯失效（實機 Logcat 捕捉）**：  
   反編譯 `/product/app/TIF_LiveTV/TIF_LiveTV.apk` (`com.tcl.tv`) 與 `/product/app/SystemSettings/SystemSettings.apk` (`com.tcl.settings`) 可發現：
   - 當硬體偵測到 HDR/Dolby Vision 模式變化時，`tcl_system_server` 發出廣播 `com.tcl.Hdr`。
   - `com.tcl.tv` 的 `DolbyToast` 透過 `WindowManager.addView` 加入懸浮窗 (`CToast`)。
   - 其內部 `DolbyToast.checkShowDolby()` 會檢查 `isTVTop`（判斷 `com.tcl.tv.TVActivity` 是否位於前台）。實機 Logcat 顯示：
     ```text
     com.tcl.tv        D  checkShowDolby() isTVTop = false
     system_server     I  mayAddFloatingWindow w = Window{2ad38c3 u0 Toast}
     ```
   - 因 `isTVTop == false`，原本排程的自動隱藏計時器 (`mHide`) 異常中斷，導致懸浮窗**永久滯留在螢幕上**。
   - 同時，`com.tcl.settings.receiver.HdrReceiver` 也攔截廣播並呼叫 `Toast.show()`，造成多重干擾。

#### 實測解法：透過 AppOps 精準封鎖浮動視窗與 Toast 權限（在 TCL 65C715 實測有效）

- **`com.tcl.tv`**：卡死的 DolbyToast 為自訂浮動視窗（`CToast`，`SYSTEM_ALERT_WINDOW`），將其懸浮窗與 Toast 權限全部設為 deny。
- **`com.tcl.settings`**：TCL 原廠畫質/音效設定選單本身以系統懸浮窗（`type:2003`）呈現，因此**必須保留 `SYSTEM_ALERT_WINDOW allow`，僅封鎖 `TOAST_WINDOW deny`**。這能精準阻絕 HDR Toast，同時完整保留原廠畫質調整功能！

```bash
# 1. 徹底阻斷 com.tcl.tv 懸浮窗與 Toast 權限
adb shell appops set com.tcl.tv SYSTEM_ALERT_WINDOW deny
adb shell appops set com.tcl.tv TOAST_WINDOW deny

# 2. 僅封鎖 com.tcl.settings 的 Toast 權限（保留畫質調整設定頁面）
adb shell appops set com.tcl.settings TOAST_WINDOW deny
adb shell appops set com.tcl.settings SYSTEM_ALERT_WINDOW allow
```

> [!NOTE]
> `appops` 修改直接寫入電視 `/data/system/appops.xml`，**重開機後依然持久生效**。

---

### 排查 3：解決 TvView 影像穿透黑屏（invalid sideband 0/0）與殘影

#### 問題現象
進入 `HdmiViewerActivity` 時，底層硬體解碼已成功鎖定訊號，但畫面始終維持全黑。Logcat 出現合成器錯誤：
```text
VideoComposer  composer-rtk@2.1-service  E  invalid sideband 0xb6040750, 0/0
TclWinInjector system_server             I  mayAddFloatingWindow w = ...HdmiViewerActivity float
```

#### 底層硬體輸出時間序（TCL 65C715 實機 Logcat 追蹤）
1. **硬體解碼鎖定與 4K 60Hz HDR 握手：**
   ```text
   2026-09-29 15:57:40.634  sitatvservice   D  SetInputRegion wId=0, x=0, y=0, w=3840, h=2160, HDRType=4
   2026-09-29 15:57:41.476  com.tcl.tvinput D  audioFormat= 0 width = 2160 height = 3840 videoFrameRate = 60.0
   ```
2. **TvView 觸發畫面就緒回報：**
   ```text
   2026-09-29 15:57:41.502  com.lnu.tclhdmilauncher I  TvView onVideoAvailable: com.tcl.tvinput/.../HW1413744640 (video rendering active)
   ```
3. **硬體合成器 (HWC) invalid sideband 錯誤：**
   ```text
   2026-09-29 15:57:40.278  composer-rtk@2.1-service  E  invalid sideband 0xb6040750, 0/0
   2026-09-29 15:57:40.311  composer-rtk@2.1-service  E  invalid sideband 0xb6040d30, 0/0
   ```

#### 深入成因分析
1. **`windowIsTranslucent=true` 雙視窗遮蔽與尺寸為 0**：
   - 設定 `android:windowIsTranslucent="true"` 會讓 WindowManager 判定該視窗不遮擋底層（`r.occludesParent = false`）。
   - 底層的 `MainActivity`（背景為 `#000000` 純黑）**不會被暫停或隱藏**，SurfaceFlinger 會將其黑色視窗持續疊加在最上方，遮擋了底層的硬體視訊。
   - 同時，TCL 的 `TclWinInjector` 將半透明視窗誤判為浮動視窗 (`float`)，傳遞給 Realtek 圖層合成器無效尺寸（0/0），導致無法成像。
2. **Realtek RTD2851 圖層合成機制：Dirty Rect Alpha 凍結（推測）**：
   - 實機測試發現，在 4K 60Hz HDR / Dolby Vision 硬體 sideband 播放期間，不只是 Toast，就連 Android 原生音量調節橫幅在淡出後，也會在畫面留下暗色殘影。
   - **推測成因**：Realtek RTD2851 的 HWC 使用 Dirty Rect 增量更新技術。當 UI 元件執行淡出動畫（`alpha: 1.0 -> 0.0`）進入最後階段時，HWC 疑似判定該區域無更新，進而將半透明快取凍結在硬體視訊圖層之上。

#### 實測解決與系統最佳化

1. **App 端極簡化（不透明全螢幕主題 + 0 View 0 浮層 0 Toast）**：
   - 移除 `windowIsTranslucent=true`，確保 `MainActivity` 能在進入播放器後即時進入 `onStop()` 釋放圖層。
   - 移除所有多餘覆蓋層，`tvView` 作為唯一 ContentView，避免產生淡出動畫。
2. **系統級解法：關閉全域動畫縮放**：
   - 由於該現象與 alpha 淡出動畫相關，將全域動畫縮放設為 0 可讓系統元件瞬開瞬關，跳過漸變淡出：
     ```bash
     # 將全域視窗動畫、轉場動畫、動畫時長縮放全部歸零
     adb shell settings put global window_animation_scale 0
     adb shell settings put global transition_animation_scale 0
     adb shell settings put global animator_duration_scale 0
     ```
   - **全域副作用說明**：此設定會影響整台電視的所有應用程式與系統介面，所有彈出式視窗、App 切換與選單動畫皆會變為瞬間切換，無平滑過渡效果。
   - **還原指令**：若日後欲恢復原廠動畫效果，執行以下指令改回 1 即可：
     ```bash
     adb shell settings put global window_animation_scale 1
     adb shell settings put global transition_animation_scale 1
     adb shell settings put global animator_duration_scale 1
     ```
3. **修復底層 Session 競態與凍結畫面**：
   - 避免在 `tuneToPort()` 內過早呼叫 `tvView.reset()` 造成 HAL 釋放競爭（可能引發 `onVideoUnavailable(reason=0)` 凍結畫面）。
   - 在 `onStop()` 時正確執行 `tvView.reset()` 釋放硬體 Session。
   - 實作 600ms 自動重新 tune 的容錯機制。
4. **韌體經驗：降級至 Android 9 官方韌體（`V8-R851T02-LF1V662`）**：
   - **Android 11 相關描述（未經本專案驗證）**：部分社群討論提到在 TCL Android 11 (V7xx / V8xx) 韌體上，HWC2 驅動移植至 RTD2851 可能存在較多記憶體與圖層相容挑戰。 <!-- TODO: 需作者確認：關於 Android 11 HWC2 具體機制的測試與驗證細節 -->
   - **V662 (Android 9) 實測**：
     - 在 TCL 65C715 實測中，搭配官方韌體 `V8-R851T02-LF1V662`，HDMI Passthrough 運作穩定，無 sideband 畫面凍結困擾。
     - 相比高版本背景佔用更輕，喚醒反應迅速。
     - *刷機提示*：跨版本降級通常需透過 FAT32 隨身碟與 `Update.img` 強制復原模式（按住電源鍵插電開機）進行，操作具一定風險，請謹慎評估。
5. **手動清除緩衝區技巧**：
   - 若畫面上已存在凍結殘影，可按遙控器 **Home** 鍵返回本 Launcher（觸發全螢幕視圖重繪以刷新合成器緩衝區），再按 OK 重新進入 HDMI 即可清除。

---

### 排查 4：實現真正的 HDMI-CEC 喚醒自動切換訊號源

#### 問題現象
當喚醒 Apple TV 或 PS5 時，裝置會發送標準 HDMI-CEC 指令（`<Image View On>` 與 `<Active Source>`）。一般電視會自動切換至對應訊號源；但在 TCL 實測韌體上，系統僅發出私有 `MSG_VIEW_ON` 廣播點亮螢幕，**畫面仍停留在 Android 桌面**，未自動切換輸入埠。

#### 技術限制 (SELinux 與 Logcat 限制)
1. **廣播無端口資訊**：TCL 的 `MSG_VIEW_ON` 廣播未包含喚醒來源的 HDMI 埠資訊。
2. **SELinux 阻擋標準 API**：非系統應用程式呼叫 `dumpsys hdmi_control` 會被 Android 11+ SELinux 規則攔截。
3. **Logcat 權限限制**：`system_server` 雖然記錄了 `HdmiCecController: command:<Active Source> ... params: 10 00`，但一般第三方 App 預設無法跨進程讀取系統 log。

#### 實測解法：透過前台服務 (`CecLogReaderService`) 監控系統 Logcat
本專案提供前台服務 `CecLogReaderService`，針對 `HdmiCecController` 標籤過濾 Logcat。當偵測到 `<Active Source>` 指令時，即時解析實體位址（如 `10 00` 對應 HDMI 1）並執行訊號源切換。

使用此功能需透過 ADB 或 Shizuku 授予 `READ_LOGS` 權限：

```bash
# 1. 授予讀取系統日誌權限
adb shell pm grant com.lnu.tclhdmilauncher android.permission.READ_LOGS

# 2. 強制停止 App 以套用新權限
adb shell am force-stop com.lnu.tclhdmilauncher
```
授予權限後，前台服務即可攔截 CEC 事件並觸發自動切換。
