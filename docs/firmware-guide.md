# 韌體版本與相容性指南 (Firmware Guide)

本專案主要針對 TCL Android TV（Realtek RTD2851 / R851T02 平台）開發與除錯。

---

## 實測與驗證狀態 (Hardware & Firmware Matrix)

| 平台 / 韌體 | 狀態 | 核心表現與說明 |
|---|---|---|
| **TCL 65C715 + V8-R851T02-LF1V662** (Android 9) | ✅ **實測驗證通過** | 4K 60Hz、HDR10、Dolby Vision、TvView 影像直通皆穩定流暢。無背景圖層卡死或記憶體洩漏問題。 |
| **TCL Android 11 韌體** (V7xx / V8xx) | ⚠️ **未經本專案驗證** | 社群（如 XDA、4PDA）反饋指出 RTD2851 晶片升級至 Android 11 後，新的 HWC2 驅動易出現 sideband buffer 爭用與記憶體緊張。<!-- TODO: 需作者確認：關於 Android 11 HWC2 具體機制的測試與驗證細節 --> |
| **其他晶片平台** (RTD2885, MT9615, T972) | ⚠️ **未實測** | 底層 TvInput 硬體 ID（HW1413744xxx）可能不同，需透過 `dumpsys tv_input` 重新確認。 |

---

## 韌體降級至 Android 9 (`V8-R851T02-LF1V662`) 說明

若您在 Android 11 遇到無法解決的 HDMI 畫面凍結、Dolby Vision 閃退或系統卡頓，可考慮降級至官方 Android 9 穩定版本。

### 降級特性
- **純淨硬體管線**：Android 9 使用成熟穩定的 SurfaceView / TvView 合成管線，無側邊帶（sideband）合成器死鎖問題。
- **釋放系統記憶體**：移除高版本背景繁雜的監控服務，為 4K 視訊解碼騰出更多運算與記憶體餘裕。

### 刷機警告與步驟
> [!CAUTION]
> 跨 Android 大版本降級需清除所有使用者資料，並使用「強制復原（Recovery Force-Flash）」方式進行。操作具備一定風險，請務必先備份重要資料。

1. 準備一個格式化為 **FAT32** 的 USB 隨身碟。
2. 將官方韌體包（解壓縮後的 `Update.img`）放置在隨身碟**根目錄**。
3. 將隨身碟插入電視的 USB 2.0 / 3.0 連接埠。
4. 電視拔掉電源插頭。
5. 按住電視本體上的**實體電源按鈕**不放，同時插上電源線。
6. 等待電視指示燈開始規律閃爍或螢幕顯示更新畫面後，放開電源按鈕。
7. 等待韌體刷寫完成自動重啟（約需 10-15 分鐘）。
