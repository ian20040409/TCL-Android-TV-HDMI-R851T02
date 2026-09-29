package com.lnu.tclhdmilauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.ArrayMap
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.text.Collator
import java.util.concurrent.Executors

/**
 * TCL TV HDMI Launcher 集中設定頁面 (SettingsActivity)
 *
 * 核心特性：
 * - 100% 純程式碼 View 樹建構（0 XML I/O、0 反射）
 * - 橫向雙欄 Leanback TV 介面，遙控器方向鍵流暢導航
 * - 支援 Android 原生 Switch/ToggleButton 切換開關元件
 * - 整合全域 Launcher 設定與系統/TCL設定捷徑
 * - 記憶體友善、零額外第三方依賴
 */
class SettingsActivity : Activity(), View.OnClickListener, View.OnFocusChangeListener {

    private data class SettingItemViewHolder(
        val root: LinearLayout,
        val ivIcon: ImageView,
        val tvTitle: TextView,
        val tvSummary: TextView,
        val tvBadge: TextView,
        val switchWidget: Switch? = null
    )

    private lateinit var itemAppMode: SettingItemViewHolder
    private lateinit var itemCountdown: SettingItemViewHolder
    private lateinit var itemDefaultPort: SettingItemViewHolder
    private lateinit var itemSignalSearch: SettingItemViewHolder
    private lateinit var itemWakeGuard: SettingItemViewHolder
    private lateinit var itemAutoOpen: SettingItemViewHolder
    private lateinit var itemTclSettings: SettingItemViewHolder
    private lateinit var itemAndroidSettings: SettingItemViewHolder

    private var countdownDialog: AlertDialog? = null
    private var defaultPortDialog: AlertDialog? = null
    private var wakeGuardDialog: AlertDialog? = null
    private var autoOpenDialog: AlertDialog? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
        setupFocusNavigation()
        refreshAllItemStates()
        itemAppMode.root.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        refreshAllItemStates()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownDialog?.dismiss()
        defaultPortDialog?.dismiss()
        wakeGuardDialog?.dismiss()
        autoOpenDialog?.dismiss()
        bgExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onClick(v: View) {
        when (v) {
            itemAppMode.root -> toggleAppMode()
            itemCountdown.root -> showCountdownDialog()
            itemDefaultPort.root -> showDefaultPortDialog()
            itemSignalSearch.root -> toggleSignalSearch()
            itemWakeGuard.root -> showWakeGuardDialog()
            itemAutoOpen.root -> showAutoOpenDialog()
            itemTclSettings.root -> MainActivity.launchTclSettings(this)
            itemAndroidSettings.root -> MainActivity.launchAndroidSystemSettings(this)
        }
    }

    override fun onFocusChange(v: View, hasFocus: Boolean) {
        val density = resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density + 0.5f).toInt()

        val scale = if (hasFocus) 1.035f else 1.0f
        v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        v.elevation = if (hasFocus) dp(8f).toFloat() else 0f

        val holder = v.tag as? SettingItemViewHolder ?: return
        if (hasFocus) {
            holder.ivIcon.setColorFilter(0xFF60A5FA.toInt()) // Focused icon: vibrant sky blue
            holder.tvTitle.setTextColor(Color.WHITE)
            holder.tvSummary.setTextColor(0xFFCBD5E1.toInt()) // High contrast summary on dark blue
        } else {
            holder.ivIcon.setColorFilter(0xFF94A3B8.toInt())
            holder.tvTitle.setTextColor(0xFFF1F5F9.toInt())
            holder.tvSummary.setTextColor(0xFF94A3B8.toInt())
        }
    }

    private fun refreshAllItemStates() {
        // 1. App 模式 (切換開關)
        val appMode = MainActivity.isAppModeEnabled(this)
        itemAppMode.switchWidget?.isChecked = appMode
        updateItemState(
            holder = itemAppMode,
            summary = getString(if (appMode) R.string.setting_app_mode_desc_on else R.string.setting_app_mode_desc_off),
            badgeText = getString(if (appMode) R.string.setting_state_on else R.string.setting_state_off),
            badgeColor = if (appMode) 0xFF86EFAC.toInt() else 0xFF94A3B8.toInt(),
            badgeBgColor = if (appMode) 0xFF064E3B.toInt() else 0xFF27272A.toInt()
        )

        // 2. 自動倒數秒數
        val countdown = MainActivity.getCountdownSeconds(this)
        val countdownSummary = if (countdown <= 0) {
            getString(R.string.setting_countdown_desc_off)
        } else {
            getString(R.string.setting_countdown_desc, countdown)
        }
        val countdownBadge = if (countdown <= 0) {
            getString(R.string.setting_state_off)
        } else {
            "${countdown}s"
        }
        updateItemState(
            holder = itemCountdown,
            summary = countdownSummary,
            badgeText = countdownBadge,
            badgeColor = if (countdown > 0) 0xFF38BDF8.toInt() else 0xFF94A3B8.toInt(),
            badgeBgColor = if (countdown > 0) 0xFF0C4A6E.toInt() else 0xFF27272A.toInt()
        )

        // 3. 預設訊號源
        val defaultPort = MainActivity.getDefaultPort(this)
        updateItemState(
            holder = itemDefaultPort,
            summary = getString(R.string.setting_default_port_desc, defaultPort),
            badgeText = "HDMI $defaultPort",
            badgeColor = 0xFF38BDF8.toInt(),
            badgeBgColor = 0xFF0C4A6E.toInt()
        )

        // 4. 訊號搜尋畫面 (切換開關)
        val signalSearch = MainActivity.isSignalSearchScreenEnabled(this)
        itemSignalSearch.switchWidget?.isChecked = signalSearch
        updateItemState(
            holder = itemSignalSearch,
            summary = getString(if (signalSearch) R.string.setting_signal_search_desc_on else R.string.setting_signal_search_desc_off),
            badgeText = getString(if (signalSearch) R.string.setting_state_on else R.string.setting_state_off),
            badgeColor = if (signalSearch) 0xFF86EFAC.toInt() else 0xFF94A3B8.toInt(),
            badgeBgColor = if (signalSearch) 0xFF064E3B.toInt() else 0xFF27272A.toInt()
        )

        // 5. 待機喚醒與 Home 鍵保障
        val wakeGuard = MainActivity.isAccessibilityServiceEnabled(this)
        updateItemState(
            holder = itemWakeGuard,
            summary = getString(if (wakeGuard) R.string.setting_wake_guard_desc_on else R.string.setting_wake_guard_desc_off),
            badgeText = getString(if (wakeGuard) R.string.setting_state_enabled else R.string.setting_state_disabled),
            badgeColor = if (wakeGuard) 0xFF86EFAC.toInt() else 0xFFFDE047.toInt(),
            badgeBgColor = if (wakeGuard) 0xFF064E3B.toInt() else 0xFF713F12.toInt()
        )

        // 6. 自動啟動 App
        val autoPkg = MainActivity.getAutoOpenPackage(this)
        val autoLabel = MainActivity.getAutoOpenLabel(this)
        val autoDesc = if (autoPkg.isNotBlank()) {
            getString(R.string.setting_auto_open_desc_app, autoLabel.ifBlank { autoPkg })
        } else {
            getString(R.string.setting_auto_open_desc_none)
        }
        val autoBadge = if (autoPkg.isNotBlank()) autoLabel.ifBlank { autoPkg } else getString(R.string.dialog_auto_open_none)
        updateItemState(
            holder = itemAutoOpen,
            summary = autoDesc,
            badgeText = autoBadge,
            badgeColor = if (autoPkg.isNotBlank()) 0xFF38BDF8.toInt() else 0xFF94A3B8.toInt(),
            badgeBgColor = if (autoPkg.isNotBlank()) 0xFF0C4A6E.toInt() else 0xFF27272A.toInt()
        )

        // 7. TCL 電視設定 (無標籤)
        updateItemState(
            holder = itemTclSettings,
            summary = getString(R.string.setting_tcl_settings_desc)
        )

        // 8. Android 系統設定 (無標籤)
        updateItemState(
            holder = itemAndroidSettings,
            summary = getString(R.string.setting_android_settings_desc)
        )
    }

    private fun updateItemState(
        holder: SettingItemViewHolder,
        summary: String,
        badgeText: String = "",
        badgeColor: Int = 0,
        badgeBgColor: Int = 0
    ) {
        val density = resources.displayMetrics.density
        fun dp(v: Float): Int = (v * density + 0.5f).toInt()

        holder.tvSummary.text = summary
        if (holder.switchWidget != null) {
            holder.tvBadge.visibility = View.GONE
            holder.switchWidget.visibility = View.VISIBLE
        } else if (badgeText.isNotBlank()) {
            holder.tvBadge.visibility = View.VISIBLE
            holder.tvBadge.text = badgeText
            holder.tvBadge.setTextColor(badgeColor)
            holder.tvBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12f).toFloat()
                setColor(badgeBgColor)
            }
        } else {
            holder.tvBadge.visibility = View.GONE
        }
    }

    private fun toggleAppMode() {
        val newMode = !MainActivity.isAppModeEnabled(this)
        MainActivity.setAppModeEnabled(this, newMode)
        refreshAllItemStates()
    }

    private fun toggleSignalSearch() {
        val newState = !MainActivity.isSignalSearchScreenEnabled(this)
        MainActivity.setSignalSearchScreenEnabled(this, newState)
        refreshAllItemStates()
    }

    private fun showCountdownDialog() {
        countdownDialog?.dismiss()
        val current = MainActivity.getCountdownSeconds(this)
        val secondsOptions = listOf(
            0 to getString(R.string.dialog_option_off),
            1 to getString(R.string.dialog_option_1s),
            2 to getString(R.string.dialog_option_2s),
            3 to getString(R.string.dialog_option_3s_default),
            5 to getString(R.string.dialog_option_5s),
            10 to getString(R.string.dialog_option_10s),
            15 to getString(R.string.dialog_option_15s),
            30 to getString(R.string.dialog_option_30s)
        )

        val labels = secondsOptions.map { it.second }.toTypedArray()
        val currentIndex = secondsOptions.indexOfFirst { it.first == current }.let {
            if (it != -1) it else 3
        }

        countdownDialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.dialog_countdown_title))
            .setSingleChoiceItems(labels, currentIndex) { d, which ->
                val selectedSeconds = secondsOptions[which].first
                MainActivity.setCountdownSeconds(this, selectedSeconds)
                refreshAllItemStates()
                d.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_cancel)) { d, _ ->
                d.dismiss()
            }
            .create().also { it.show() }
    }

    private fun showDefaultPortDialog() {
        defaultPortDialog?.dismiss()
        val currentPort = MainActivity.getDefaultPort(this)
        val ports = listOf(
            1 to getString(R.string.port_hdmi_1),
            2 to getString(R.string.port_hdmi_2),
            3 to getString(R.string.port_hdmi_3)
        )
        val labels = ports.map { it.second }.toTypedArray()
        val currentIndex = ports.indexOfFirst { it.first == currentPort }.let {
            if (it != -1) it else 2
        }

        defaultPortDialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.dialog_default_port_title))
            .setSingleChoiceItems(labels, currentIndex) { d, which ->
                val selectedPort = ports[which].first
                MainActivity.setDefaultPort(this, selectedPort)
                refreshAllItemStates()
                d.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_cancel)) { d, _ ->
                d.dismiss()
            }
            .create().also { it.show() }
    }

    private fun showWakeGuardDialog() {
        wakeGuardDialog?.dismiss()
        val isEnabled = MainActivity.isAccessibilityServiceEnabled(this)
        val title = getString(R.string.dialog_wake_guard_title)
        val message = if (isEnabled) {
            getString(R.string.dialog_wake_guard_msg_on)
        } else {
            getString(R.string.dialog_wake_guard_msg_off)
        }

        val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setMessage(message)

        if (isEnabled) {
            builder.setPositiveButton(getString(R.string.dialog_btn_manage)) { d, _ ->
                d.dismiss()
                MainActivity.openAccessibilitySettings(this)
            }
            builder.setNegativeButton(getString(R.string.dialog_btn_ok)) { d, _ ->
                d.dismiss()
            }
        } else {
            builder.setPositiveButton(getString(R.string.dialog_btn_go_settings)) { d, _ ->
                d.dismiss()
                MainActivity.openAccessibilitySettings(this)
            }
            builder.setNegativeButton(getString(R.string.dialog_cancel)) { d, _ ->
                d.dismiss()
            }
        }

        wakeGuardDialog = builder.create().also { it.show() }
    }

    private data class QuickAppInfo(val pkg: String, val label: String)

    private fun showAutoOpenDialog() {
        autoOpenDialog?.dismiss()
        val currentPkg = MainActivity.getAutoOpenPackage(this)

        bgExecutor.execute {
            val pm = packageManager
            val selfPkg = packageName

            val leanbackResolves = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
            )
            val mobileResolves = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            )

            val resolvedMap = ArrayMap<String, ResolveInfo>(64)
            for (ri in leanbackResolves) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg != selfPkg && !resolvedMap.containsKey(pkg)) {
                    resolvedMap[pkg] = ri
                }
            }
            for (ri in mobileResolves) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg != selfPkg && !resolvedMap.containsKey(pkg)) {
                    resolvedMap[pkg] = ri
                }
            }

            val appList = ArrayList<QuickAppInfo>(resolvedMap.size)
            for (i in 0 until resolvedMap.size) {
                val ri = resolvedMap.valueAt(i)
                val actInfo = ri.activityInfo ?: continue
                val appInfo = actInfo.applicationInfo ?: continue
                val pkg = actInfo.packageName

                val label = try {
                    ri.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                        ?: pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    pkg
                }
                appList.add(QuickAppInfo(pkg, label))
            }

            val col = Collator.getInstance()
            appList.sortWith { a, b -> col.compare(a.label, b.label) }

            mainHandler.post {
                if (isFinishing || isDestroyed) return@post

                val labels = Array(appList.size + 1) { idx ->
                    if (idx == 0) getString(R.string.dialog_auto_open_none)
                    else appList[idx - 1].label
                }
                val checkedIndex = if (currentPkg.isBlank()) {
                    0
                } else {
                    val found = appList.indexOfFirst { it.pkg == currentPkg }
                    if (found >= 0) found + 1 else 0
                }

                autoOpenDialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(getString(R.string.dialog_auto_open_title))
                    .setSingleChoiceItems(labels, checkedIndex) { d, which ->
                        if (which == 0) {
                            MainActivity.setAutoOpenApp(this, "", "")
                        } else {
                            val chosen = appList[which - 1]
                            MainActivity.setAutoOpenApp(this, chosen.pkg, chosen.label)
                            if (!MainActivity.isAppModeEnabled(this)) {
                                MainActivity.setAppModeEnabled(this, true)
                            }
                        }
                        refreshAllItemStates()
                        d.dismiss()
                    }
                    .setNegativeButton(getString(R.string.dialog_cancel), null)
                    .create().also { it.show() }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (countdownDialog?.isShowing == true ||
                defaultPortDialog?.isShowing == true ||
                wakeGuardDialog?.isShowing == true ||
                autoOpenDialog?.isShowing == true) {
                if (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_BACK) {
                    countdownDialog?.dismiss()
                    defaultPortDialog?.dismiss()
                    wakeGuardDialog?.dismiss()
                    autoOpenDialog?.dismiss()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }

            if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_MENU) {
                finish()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ── 畫面建構（0 XML、雙欄 Leanback TV Layout） ─────────────────────────────
    private fun buildContentView(): View {
        val density = resources.displayMetrics.density
        fun dp(v: Float): Int = (v * density + 0.5f).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dp(36f)
            val padV = dp(20f)
            setPadding(padH, padV, padH, padV)
            clipChildren = false
            clipToPadding = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
        }

        // 頂部標題列
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val ivSettings = ImageView(this).apply {
            val d = getDrawable(R.drawable.settings_48px)?.mutate()
            setImageDrawable(d)
            setColorFilter(0xFF38BDF8.toInt())
        }
        header.addView(ivSettings, LinearLayout.LayoutParams(dp(26f), dp(26f)).apply {
            rightMargin = dp(10f)
        })

        val tvTitle = TextView(this).apply {
            text = getString(R.string.settings_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFF8FAFC.toInt())
        }
        header.addView(tvTitle, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        val tvHint = TextView(this).apply {
            text = getString(R.string.settings_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0xFF64748B.toInt())
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        header.addView(tvHint, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
            rightMargin = dp(4f)
        })

        root.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = dp(14f)
        })

        // 雙欄容器（水平平分）
        val columnsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }

        // 左欄：Launcher 設定
        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val tvCat1 = TextView(this).apply {
            text = getString(R.string.setting_category_launcher)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF38BDF8.toInt())
            letterSpacing = 0.05f
        }
        leftCol.addView(tvCat1, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            bottomMargin = dp(8f)
        })

        itemAppMode = createSettingItem(R.drawable.apps_48px, getString(R.string.setting_app_mode_title), density, isToggle = true)
        itemCountdown = createSettingItem(R.drawable.timer_48px, getString(R.string.setting_countdown_title), density)
        itemDefaultPort = createSettingItem(R.drawable.settings_input_hdmi_24px, getString(R.string.setting_default_port_title), density)
        itemSignalSearch = createSettingItem(R.drawable.cable_48px, getString(R.string.setting_signal_search_title), density, isToggle = true)

        for (item in arrayOf(itemAppMode, itemCountdown, itemDefaultPort, itemSignalSearch)) {
            leftCol.addView(item.root, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(10f)
            })
        }

        // 右欄：電視與系統設定
        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val tvCat2 = TextView(this).apply {
            text = getString(R.string.setting_category_system)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF38BDF8.toInt())
            letterSpacing = 0.05f
        }
        rightCol.addView(tvCat2, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            bottomMargin = dp(8f)
        })

        itemWakeGuard = createSettingItem(R.drawable.accessibility_new_48px, getString(R.string.setting_wake_guard_title), density)
        itemAutoOpen = createSettingItem(R.drawable.settings_power_48px, getString(R.string.setting_auto_open_title), density)
        itemTclSettings = createSettingItem(R.drawable.open_in_new_48px, getString(R.string.setting_tcl_settings_title), density)
        itemAndroidSettings = createSettingItem(R.drawable.open_in_new_48px, getString(R.string.setting_android_settings_title), density)

        for (item in arrayOf(itemWakeGuard, itemAutoOpen, itemTclSettings, itemAndroidSettings)) {
            rightCol.addView(item.root, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(10f)
            })
        }

        // 加入兩欄到水平容器
        columnsLayout.addView(leftCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
            rightMargin = dp(12f)
        })
        columnsLayout.addView(rightCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
            leftMargin = dp(12f)
        })

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            addView(columnsLayout, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        root.addView(scrollView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        return root
    }

    private fun createSettingItem(
        iconRes: Int,
        title: String,
        density: Float,
        isToggle: Boolean = false
    ): SettingItemViewHolder {
        fun dp(v: Float): Int = (v * density + 0.5f).toInt()

        val ivIcon = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(0xFF94A3B8.toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val tvTitle = TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFF1F5F9.toInt())
            isSingleLine = true
        }

        val tvSummary = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0xFF94A3B8.toInt())
            isSingleLine = true
        }

        val tvBadge = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            val hPad = dp(10f)
            val vPad = dp(4f)
            setPadding(hPad, vPad, hPad, vPad)
            gravity = Gravity.CENTER
            isSingleLine = true
            visibility = if (isToggle) View.GONE else View.VISIBLE
        }

        val switchWidget = if (isToggle) {
            Switch(this).apply {
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = false
                splitTrack = false

                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                )
                val thumbColors = intArrayOf(
                    0xFF22C55E.toInt(), // Checked: 高飽和亮綠色 #22C55E
                    0xFF94A3B8.toInt()  // Unchecked: 灰藍 #94A3B8
                )
                val trackColors = intArrayOf(
                    0xFF15803D.toInt(), // Checked: 深綠色軌道 #15803D
                    0xFF334155.toInt()  // Unchecked: 暗灰藍軌道 #334155
                )
                thumbTintList = ColorStateList(states, thumbColors)
                trackTintList = ColorStateList(states, trackColors)
            }
        } else null

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(tvTitle, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            addView(tvSummary, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                topMargin = dp(2f)
            })
        }

        val itemRoot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hPad = dp(14f)
            val vPad = dp(10f)
            setPadding(hPad, vPad, hPad, vPad)
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            background = createItemSelector(density)

            addView(ivIcon, LinearLayout.LayoutParams(dp(26f), dp(26f)).apply {
                rightMargin = dp(12f)
            })
            addView(textContainer, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8f)
            })
            if (switchWidget != null) {
                addView(switchWidget, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    leftMargin = dp(4f)
                })
            }
            addView(tvBadge, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

            setOnClickListener(this@SettingsActivity)
            onFocusChangeListener = this@SettingsActivity
        }

        val holder = SettingItemViewHolder(itemRoot, ivIcon, tvTitle, tvSummary, tvBadge, switchWidget)
        itemRoot.tag = holder
        return holder
    }

    private fun setupFocusNavigation() {
        val idL1 = View.generateViewId()
        val idL2 = View.generateViewId()
        val idL3 = View.generateViewId()
        val idL4 = View.generateViewId()

        val idR1 = View.generateViewId()
        val idR2 = View.generateViewId()
        val idR3 = View.generateViewId()
        val idR4 = View.generateViewId()

        itemAppMode.root.id = idL1
        itemCountdown.root.id = idL2
        itemDefaultPort.root.id = idL3
        itemSignalSearch.root.id = idL4

        itemWakeGuard.root.id = idR1
        itemAutoOpen.root.id = idR2
        itemTclSettings.root.id = idR3
        itemAndroidSettings.root.id = idR4

        // 左欄導航
        itemAppMode.root.nextFocusUpId = idL1
        itemAppMode.root.nextFocusDownId = idL2
        itemAppMode.root.nextFocusLeftId = idL1
        itemAppMode.root.nextFocusRightId = idR1

        itemCountdown.root.nextFocusUpId = idL1
        itemCountdown.root.nextFocusDownId = idL3
        itemCountdown.root.nextFocusLeftId = idL2
        itemCountdown.root.nextFocusRightId = idR2

        itemDefaultPort.root.nextFocusUpId = idL2
        itemDefaultPort.root.nextFocusDownId = idL4
        itemDefaultPort.root.nextFocusLeftId = idL3
        itemDefaultPort.root.nextFocusRightId = idR3

        itemSignalSearch.root.nextFocusUpId = idL3
        itemSignalSearch.root.nextFocusDownId = idL4
        itemSignalSearch.root.nextFocusLeftId = idL4
        itemSignalSearch.root.nextFocusRightId = idR4

        // 右欄導航
        itemWakeGuard.root.nextFocusUpId = idR1
        itemWakeGuard.root.nextFocusDownId = idR2
        itemWakeGuard.root.nextFocusLeftId = idL1
        itemWakeGuard.root.nextFocusRightId = idR1

        itemAutoOpen.root.nextFocusUpId = idR1
        itemAutoOpen.root.nextFocusDownId = idR3
        itemAutoOpen.root.nextFocusLeftId = idL2
        itemAutoOpen.root.nextFocusRightId = idR2

        itemTclSettings.root.nextFocusUpId = idR2
        itemTclSettings.root.nextFocusDownId = idR4
        itemTclSettings.root.nextFocusLeftId = idL3
        itemTclSettings.root.nextFocusRightId = idR3

        itemAndroidSettings.root.nextFocusUpId = idR3
        itemAndroidSettings.root.nextFocusDownId = idR4
        itemAndroidSettings.root.nextFocusLeftId = idL4
        itemAndroidSettings.root.nextFocusRightId = idR4
    }

    private fun createItemSelector(density: Float): Drawable {
        val radius = 14f * density
        val strokeFocused = (2.5f * density + 0.5f).toInt()
        val strokeNormal = (1.2f * density + 0.5f).toInt()

        fun rect(fillColor: Int, strokeWidth: Int, strokeColor: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fillColor)
            setStroke(strokeWidth, strokeColor)
        }

        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused, android.R.attr.state_pressed),
                rect(0xFF1D4ED8.toInt(), strokeFocused, 0xFFBAE6FD.toInt())
            )
            addState(
                intArrayOf(android.R.attr.state_focused),
                // 聚焦時：高雅沉穩的藍色底 (0xFF1E3A8A) 搭配醒目清晰的高亮外框 (0xFF60A5FA)
                rect(0xFF1E3A8A.toInt(), strokeFocused, 0xFF60A5FA.toInt())
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                rect(0xFF1D4ED8.toInt(), strokeFocused, 0xFF93C5FD.toInt())
            )
            addState(
                intArrayOf(),
                // 一般狀態：精緻深黑灰底色，細微邊框質感
                rect(0xFF14161A.toInt(), strokeNormal, 0xFF272A30.toInt())
            )
        }
    }
}
