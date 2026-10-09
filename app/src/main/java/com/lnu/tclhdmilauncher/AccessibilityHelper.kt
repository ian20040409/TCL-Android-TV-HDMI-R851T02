package com.lnu.tclhdmilauncher

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast

/**
 * Android TV / Google TV 無障礙設定統一輔助工具 (AccessibilityHelper)
 *
 * 核心設計：
 * 1. 統一管理無障礙服務啟用狀態檢測（AccessibilityManager + Secure.ENABLED_ACCESSIBILITY_SERVICES 雙重驗證）
 * 2. 由於 Android TV / TCL 系統已將無障礙直達捷徑拔除或指向空 Stub，直接打開 Android TV 原生主設定 (MainSettings / SETTINGS)
 * 3. 同步跳出 Toast 引導使用者：「請至 裝置偏好設定 > 無障礙 開啟服務」
 */
object AccessibilityHelper {

    private const val TAG = "AccessibilityHelper"

    /**
     * 檢測本應用的無障礙服務是否已被系統啟用
     */
    fun isServiceEnabled(context: Context): Boolean {
        val expectedService = ComponentName(context, WakeAccessibilityService::class.java)
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (am != null) {
            try {
                val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                for (service in enabledServices) {
                    if (service.resolveInfo?.serviceInfo?.packageName == context.packageName &&
                        service.resolveInfo?.serviceInfo?.name == WakeAccessibilityService::class.java.name) {
                        return true
                    }
                }
            } catch (_: Exception) {}
        }

        val enabledServicesSetting = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (_: Exception) {
            null
        } ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedService) {
                return true
            }
        }
        return false
    }

    /**
     * 直接開啟 Android 系統設定主頁面（MainSettings）
     * 包含多重候選 Intent（優先命中 Android TV 官方 MainSettings）與無障礙白名單豁免
     * @return true 若成功啟動；false 若均失敗
     */
    fun openAndroidSystemSettings(context: Context): Boolean {
        val pm = context.packageManager
        WakeAccessibilityService.temporarilyIgnorePackage("com.android.tv.settings")
        WakeAccessibilityService.temporarilyIgnorePackage("com.tcl.settings")
        WakeAccessibilityService.temporarilyIgnorePackage("com.android.settings")

        val candidates = listOf(
            // 1. Android TV 官方系統設定首頁
            Intent().setComponent(ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings")),
            // 2. 標準 Action
            Intent("android.settings.SETTINGS"),
            Intent("android.settings.TV_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS),
            // 3. 套件 Leanback / 啟動 Intent
            pm.getLeanbackLaunchIntentForPackage("com.android.tv.settings"),
            pm.getLaunchIntentForPackage("com.android.tv.settings"),
            // 4. 標準手機版 Android Settings
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings")),
            // 5. TCL 原廠設定備選
            Intent().setComponent(ComponentName("com.tcl.settings", "com.tcl.settings.MainActivity")),
            pm.getLeanbackLaunchIntentForPackage("com.tcl.settings"),
            pm.getLaunchIntentForPackage("com.tcl.settings")
        )

        for (candidate in candidates) {
            if (candidate == null) continue
            try {
                if (context !is android.app.Activity) {
                    candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(candidate)
                Log.i(TAG, "Successfully started system settings via: $candidate")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Candidate failed: $candidate, reason: ${e.message}")
            }
        }

        Log.e(TAG, "All system settings candidates failed to start")
        return false
    }

    /**
     * 彈出 Alert 對話框顯示無障礙服務開啟教學與路徑，點選「前往設定」後再切換至系統設定
     */
    fun showAccessibilityGuideDialog(context: Context) {
        if (context !is android.app.Activity || context.isFinishing || context.isDestroyed) {
            val opened = openAndroidSystemSettings(context)
            if (!opened) {
                Toast.makeText(context.applicationContext, R.string.toast_error_open_settings, Toast.LENGTH_SHORT).show()
            }
            return
        }

        val msg = DeviceHelper.filterBrandText(context, context.getString(R.string.dialog_accessibility_req_msg))
        val builder = android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(context.getString(R.string.dialog_accessibility_req_title))
            .setMessage(msg)
            .setPositiveButton(context.getString(R.string.dialog_accessibility_req_go)) { d, _ ->
                val opened = openAndroidSystemSettings(context)
                if (!opened) {
                    Toast.makeText(context.applicationContext, R.string.toast_error_open_settings, Toast.LENGTH_SHORT).show()
                }
                d.dismiss()
            }
            .setNegativeButton(context.getString(R.string.dialog_cancel)) { d, _ ->
                d.dismiss()
            }

        if (ShizukuHelper.isShizukuPermissionGranted()) {
            builder.setNeutralButton(R.string.dialog_accessibility_shizuku) { _, _ ->
                val enabled = ShizukuHelper.enableAccessibilityService(context)
                val messageRes = if (enabled) {
                    R.string.toast_accessibility_shizuku_enabled
                } else {
                    R.string.toast_accessibility_shizuku_failed
                }
                Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show()
                if (!enabled) openAndroidSystemSettings(context)
            }
        }

        builder.create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.requestFocus()
                }
                dialog.show()
            }
    }

    /**
     * 開啟無障礙設定
     *
     * 若為 Activity，彈出 Alert 對話框顯示開啟路徑指引；按下「前往設定」後開啟系統設定。
     * 若非 Activity，則直接開啟系統設定。
     */
    fun openAccessibilitySettings(context: Context): Boolean {
        if (context is android.app.Activity && !context.isFinishing && !context.isDestroyed) {
            showAccessibilityGuideDialog(context)
            return true
        }
        return openAndroidSystemSettings(context)
    }
}
