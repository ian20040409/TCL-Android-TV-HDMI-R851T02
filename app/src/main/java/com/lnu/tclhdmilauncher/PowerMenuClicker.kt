package com.lnu.tclhdmilauncher

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object PowerMenuClicker {
    fun clickPowerOff(service: AccessibilityService) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val rootNode = service.rootInActiveWindow
                if (rootNode != null) {
                    Log.i("PowerMenuClicker", "Searching for power off button in dialog")
                    if (clickNodeByText(rootNode, "休眠") ||
                        clickNodeByText(rootNode, "待機") ||
                        clickNodeByText(rootNode, "螢幕關閉") ||
                        clickNodeByText(rootNode, "Sleep") ||
                        clickNodeByText(rootNode, "Standby")) {
                        Log.i("PowerMenuClicker", "Successfully clicked sleep button")
                    } else {
                        Log.w("PowerMenuClicker", "Could not find sleep button (will not click shutdown to prevent cold power off)")
                    }
                    rootNode.recycle()
                } else {
                    Log.w("PowerMenuClicker", "rootInActiveWindow is null")
                }
            } catch (e: Exception) {
                Log.e("PowerMenuClicker", "Error clicking power off", e)
            }
        }, 1000)
    }

    private fun clickNodeByText(node: AccessibilityNodeInfo, text: String): Boolean {
        var clicked = false
        val list = node.findAccessibilityNodeInfosByText(text)
        for (n in list) {
            if (!clicked) {
                if (n.isClickable) {
                    n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    clicked = true
                } else {
                    val parent = n.parent
                    if (parent != null) {
                        if (parent.isClickable) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            clicked = true
                        }
                        parent.recycle()
                    }
                }
            }
            n.recycle()
        }
        return clicked
    }
}
