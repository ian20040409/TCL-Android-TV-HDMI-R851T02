package com.lnu.tclhdmilauncher

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class PowerMenuClicker {
    companion object {
        fun clickPowerOff(service: AccessibilityService) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val rootNode = service.rootInActiveWindow
                    if (rootNode != null) {
                        Log.i("PowerMenuClicker", "Searching for power off button in dialog")
                        if (clickNodeByText(rootNode, "休眠") ||
                            clickNodeByText(rootNode, "關機") ||
                            clickNodeByText(rootNode, "Power off") ||
                            clickNodeByText(rootNode, "待機") ||
                            clickNodeByText(rootNode, "Sleep")) {
                            Log.i("PowerMenuClicker", "Successfully clicked power off button")
                        } else {
                            Log.w("PowerMenuClicker", "Could not find power off button")
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
            val list = node.findAccessibilityNodeInfosByText(text)
            for (n in list) {
                if (n.isClickable) {
                    n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                val parent = n.parent
                if (parent != null && parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
            return false
        }
    }
}
