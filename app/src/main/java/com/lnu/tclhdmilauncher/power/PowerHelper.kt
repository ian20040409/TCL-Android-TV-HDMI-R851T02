package com.lnu.tclhdmilauncher.power

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.lnu.tclhdmilauncher.shizuku.ShizukuHelper
import java.util.concurrent.Executors

object PowerHelper {
    private const val TAG = "PowerHelper"
    private val bgExecutor = Executors.newSingleThreadExecutor()

    fun wakeScreen(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) {
                val wl = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                    "TclHdmiApp:CecWakeLock"
                )
                wl.acquire(3000)
                Log.i(TAG, "wakeScreen: WakeLock acquired to turn on screen")
                
                bgExecutor.execute {
                    try {
                        if (ShizukuHelper.isShizukuPermissionGranted()) {
                            Log.i(TAG, "Using Shizuku to execute input keyevent 224")
                            ShizukuHelper.executeShellCommand("input keyevent 224")
                        } else {
                            Log.i(TAG, "Using Runtime to execute input keyevent 224")
                            Runtime.getRuntime().exec("input keyevent 224")
                        }
                        
                        Thread.sleep(500)
                        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                        val isScreenOn = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY).state != android.view.Display.STATE_OFF
                        
                        if (!isScreenOn) {
                            Log.i(TAG, "Screen still off, trying fallback input keyevent 26")
                            if (ShizukuHelper.isShizukuPermissionGranted()) {
                                ShizukuHelper.executeShellCommand("input keyevent 26")
                            } else {
                                Runtime.getRuntime().exec("input keyevent 26")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "input keyevent wake failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "wakeScreen: failed to acquire wake lock", e)
        }
    }

    fun sleepScreen(service: AccessibilityService): Boolean {
        try {
            var success = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                Log.i(TAG, "GLOBAL_ACTION_LOCK_SCREEN result: $success")
            }
            
            if (!success) {
                success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                Log.i(TAG, "GLOBAL_ACTION_POWER_DIALOG result: $success")
                if (success) {
                    PowerMenuClicker.clickPowerOff(service)
                }
            }

            if (!success) {
                Log.i(TAG, "Attempting input keyevent 223 for sleep")
                var shizukuSuccess = false
                if (ShizukuHelper.isShizukuPermissionGranted()) {
                    val exitCode = ShizukuHelper.executeShellCommand("input keyevent 223")
                    shizukuSuccess = (exitCode == 0)
                }

                if (shizukuSuccess) {
                    success = true
                } else {
                    val process = Runtime.getRuntime().exec("input keyevent 223")
                    process.waitFor()
                    success = process.exitValue() == 0
                }
                
                Thread.sleep(500)
                val displayManager = service.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                val isScreenOnAfter223 = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY).state != android.view.Display.STATE_OFF
                
                if (isScreenOnAfter223) {
                    Log.i(TAG, "Screen still on, trying fallback input keyevent 26")
                    if (ShizukuHelper.isShizukuPermissionGranted()) {
                        ShizukuHelper.executeShellCommand("input keyevent 26")
                    } else {
                        Runtime.getRuntime().exec("input keyevent 26")
                    }
                }
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Sleep execution failed", e)
            return false
        }
    }
}
