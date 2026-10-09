package com.lnu.tclhdmilauncher

import android.content.Context
import android.os.Build

/**
 * 全局裝置品牌與型號偵測工具類別 (DeviceHelper)
 *
 * 負責全域檢測硬體裝置品牌 (Brand)、製造商 (Manufacturer) 與型號 (Model)，
 * 動態判定是否為 TCL 原廠電視系統，並提供全域「TCL」專有字樣與專有設定開關調適。
 */
object DeviceHelper {

    @Volatile
    private var cachedIsTcl: Boolean? = null

    @Volatile
    private var cachedModelName: String? = null

    /**
     * 檢測當前執行裝置是否為 TCL 電視（包含 TCL、Thomson、FFalcon、iFFalcon、SEMP 等品牌或內建 com.tcl.settings / com.tcl.tv）
     */
    fun isTclDevice(context: Context): Boolean {
        cachedIsTcl?.let { return it }

        val brand = Build.BRAND.uppercase()
        val manufacturer = Build.MANUFACTURER.uppercase()
        val product = Build.PRODUCT.uppercase()
        val isTclBrand = brand.contains("TCL") || manufacturer.contains("TCL") || product.contains("TCL") ||
                brand.contains("THOMSON") || brand.contains("FFALCON") ||
                brand.contains("IFFALCON") || brand.contains("SEMP")

        val hasTclPackage = try {
            context.packageManager.getPackageInfo("com.tcl.settings", 0)
            true
        } catch (_: Exception) {
            try {
                context.packageManager.getPackageInfo("com.tcl.tv", 0)
                true
            } catch (_: Exception) {
                false
            }
        }

        val result = isTclBrand || hasTclPackage
        cachedIsTcl = result
        return result
    }

    /**
     * 取得裝置品牌名稱
     */
    fun getBrand(): String = Build.BRAND

    /**
     * 取得裝置型號
     */
    fun getModel(): String = Build.MODEL

    /**
     * 取得完整裝置型號名稱 (例如 "TCL 55C835" 或 "Chromecast Google TV")
     */
    fun getDeviceModelName(): String {
        cachedModelName?.let { return it }
        val brand = Build.BRAND
        val model = Build.MODEL
        val result = if (model.startsWith(brand, ignoreCase = true)) {
            model
        } else {
            "$brand $model"
        }
        cachedModelName = result
        return result
    }

    /**
     * 取得適合目前硬體品牌的 App 完整名稱：
     * 若為 TCL 裝置，回傳 "TCL HDMI Launcher"；
     * 若非 TCL 裝置，回傳 "HDMI Launcher"。
     */
    fun getAppName(context: Context): String {
        val baseAppName = context.getString(R.string.app_name) // "HDMI Launcher"
        return if (isTclDevice(context)) {
            "TCL $baseAppName"
        } else {
            baseAppName
        }
    }

    /**
     * 取得適合目前裝置顯示的 App / 品牌標題名稱：
     * 若為 TCL 裝置，回傳 "TCL TV HDMI" (R.string.brand_name)；
     * 若非 TCL 裝置，回傳 "HDMI Launcher" (R.string.app_name)。
     */
    fun getBrandTitle(context: Context): String {
        return if (isTclDevice(context)) {
            context.getString(R.string.brand_name)
        } else {
            context.getString(R.string.app_name)
        }
    }

    /**
     * 根據裝置品牌動態過濾或替換字串內容：
     * 若非 TCL 裝置，自動將字串中的 "TCL TV HDMI"、"TCL HDMI Launcher"、"TCL 電視" 等品牌專有字樣動態過濾為通用名稱。
     */
    fun filterBrandText(context: Context, text: String): String {
        if (isTclDevice(context)) {
            return text
        }
        val appName = context.getString(R.string.app_name)
        return text
            .replace("TCL TV HDMI", appName)
            .replace("TCL HDMI Launcher", appName)
            .replace("TCL 電視", "電視")
            .replace("TCL 电视", "电视")
            .replace("TCL TV", "TV")
            .replace("TCL ", "")
            .replace("TCL", "")
    }
}
