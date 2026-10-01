package com.lnu.tclhdmilauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.ArrayMap
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import java.text.Collator
import java.util.concurrent.Executors

/**
 * 原生極致輕量 App 清單啟動器
 * - 100% 純程式碼建構 View（消除 LayoutInflater 反射與 XML I/O）
 * - 移除 getInstalledApplications(GET_META_DATA) 重型 Binder IPC，直接由 queryIntentActivities 建構清單
 * - 快取 ComponentName：點擊啟動 App 時 0 PackageManager 查詢開銷
 * - 雙階段極速渲染：先載入文字清單（< 15ms 瞬間顯示），App 圖示依可視範圍背景非同步延遲載入（避免一次解碼 60+ 圖示造成 GC 卡頓）
 */
class AppListActivity : Activity() {

    companion object {
        private const val PREFS_RECENT = "app_list_recent"
        private const val KEY_RECENT_PKGS = "recent_pkgs"
        private const val RECENT_SEPARATOR = "|"
        private const val MAX_RECENT_COUNT = 8

        private const val VIEW_TYPE_SECTION = 0
        private const val VIEW_TYPE_APP = 1

        @Volatile
        var isForegroundFocused: Boolean = false
            internal set
    }

    private sealed class ListItem {
        data class Section(val title: String) : ListItem()
        data class App(
            val label: String,
            val packageName: String,
            val componentName: ComponentName,
            val isLeanback: Boolean,
            val appInfo: ApplicationInfo,
            val isSystem: Boolean,
            val isDisableable: Boolean
        ) : ListItem()
    }

    private lateinit var listView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvAutoOpenBanner: TextView
    private lateinit var btnSettings: LinearLayout
    private lateinit var btnHdmi: LinearLayout

    private lateinit var tvTitle: TextView
    private lateinit var tvHint: TextView
    private lateinit var btnBatchAction: LinearLayout
    private lateinit var btnCancelSelect: LinearLayout

    private var isMultiSelectMode = false
    private val selectedPackages = HashSet<String>()

    private val items = ArrayList<ListItem>(64)
    private val iconCache = ArrayMap<String, Drawable>(64)
    private val loadingIcons = HashSet<String>(32)
    private lateinit var adapter: AppListAdapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private var isDestroyedFlag = false
    private var isActivityResumed = false
    private var isAutoOpenCancelled = false

    private var autoOpenSecondsLeft = 0
    private var isAutoOpenCountdownRunning = false
    private val autoOpenTickRunnable = object : Runnable {
        override fun run() {
            if (!isAutoOpenCountdownRunning || isAutoOpenCancelled || isDestroyedFlag || isFinishing || !isActivityResumed || !hasWindowFocus()) return
            autoOpenSecondsLeft--
            val pkg = MainActivity.getAutoOpenPackage(this@AppListActivity)
            val label = MainActivity.getAutoOpenLabel(this@AppListActivity).ifBlank { pkg }
            if (pkg.isBlank() || !MainActivity.isAppModeEnabled(this@AppListActivity)) {
                cancelAutoOpenCountdown()
                return
            }
            if (autoOpenSecondsLeft > 0) {
                tvAutoOpenBanner.text = getString(R.string.auto_open_countdown_banner, autoOpenSecondsLeft, label)
                mainHandler.postDelayed(this, 1000L)
            } else {
                cancelAutoOpenCountdown()
                launchAutoOpenPackage(pkg, label)
            }
        }
    }

    // Ordered list of recently launched package names (most recent first)
    private val recentPackages = ArrayDeque<String>(MAX_RECENT_COUNT)

    /** Load recent package list from SharedPreferences (background-safe). */
    private fun loadRecentPackages(): List<String> {
        val raw = getSharedPreferences(PREFS_RECENT, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_PKGS, "") ?: ""
        return if (raw.isBlank()) emptyList()
        else raw.split(RECENT_SEPARATOR).filter { it.isNotBlank() }
    }

    /** Push a package to the front of the recents list and persist it. */
    private fun saveRecentPackage(pkg: String) {
        recentPackages.remove(pkg)
        recentPackages.addFirst(pkg)
        while (recentPackages.size > MAX_RECENT_COUNT) recentPackages.removeLast()
        val serialized = recentPackages.joinToString(RECENT_SEPARATOR)
        getSharedPreferences(PREFS_RECENT, Context.MODE_PRIVATE).edit()
            .putString(KEY_RECENT_PKGS, serialized).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())

        adapter = AppListAdapter()
        listView.adapter = adapter

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            cancelAutoOpenCountdown()
            val item = items.getOrNull(pos)
            if (item is ListItem.App) {
                if (isMultiSelectMode) {
                    toggleSelection(item.packageName)
                } else {
                    launchApp(item)
                }
            }
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            cancelAutoOpenCountdown()
            val item = items.getOrNull(pos)
            if (item is ListItem.App) {
                if (isMultiSelectMode) {
                    showBatchActionDialog()
                } else {
                    showAppMenu(item)
                }
                true
            } else {
                false
            }
        }

        loadApps()

        isAutoOpenCancelled = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isAutoOpenCancelled = false
        if (isActivityResumed && hasWindowFocus()) {
            triggerBootOrWakeAutoOpenIfConfigured()
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        isForegroundFocused = hasWindowFocus()
        isAutoOpenCancelled = false
        if (hasWindowFocus()) {
            triggerBootOrWakeAutoOpenIfConfigured()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        isForegroundFocused = hasFocus && isActivityResumed
        if (hasFocus && isActivityResumed) {
            triggerBootOrWakeAutoOpenIfConfigured()
        } else {
            cancelAutoOpenCountdown()
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
        isForegroundFocused = false
        cancelAutoOpenCountdown()
    }

    override fun onDestroy() {
        isDestroyedFlag = true
        isForegroundFocused = false
        cancelAutoOpenCountdown()
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        bgExecutor.shutdownNow()
        iconCache.clear()
        loadingIcons.clear()
    }

    private fun triggerBootOrWakeAutoOpenIfConfigured() {
        if (isAutoOpenCancelled || isDestroyedFlag || isFinishing || !isActivityResumed || !hasWindowFocus()) return
        if (!MainActivity.isAppModeEnabled(this)) return
        val pkg = MainActivity.getAutoOpenPackage(this)
        if (pkg.isBlank()) return
        val label = MainActivity.getAutoOpenLabel(this).ifBlank { pkg }

        mainHandler.removeCallbacks(autoOpenTickRunnable)
        autoOpenSecondsLeft = MainActivity.getAutoOpenDelaySeconds(this)
        isAutoOpenCountdownRunning = true
        tvAutoOpenBanner.text = getString(R.string.auto_open_countdown_banner, autoOpenSecondsLeft, label)
        tvAutoOpenBanner.visibility = View.VISIBLE
        mainHandler.postDelayed(autoOpenTickRunnable, 1000L)
    }

    private fun cancelAutoOpenCountdown() {
        isAutoOpenCancelled = true
        if (isAutoOpenCountdownRunning) {
            isAutoOpenCountdownRunning = false
            mainHandler.removeCallbacks(autoOpenTickRunnable)
            if (::tvAutoOpenBanner.isInitialized) {
                tvAutoOpenBanner.visibility = View.GONE
            }
        }
    }

    private fun launchAutoOpenPackage(pkg: String, label: String) {
        val loadedApp = items.firstOrNull { it is ListItem.App && it.packageName == pkg } as? ListItem.App
        if (loadedApp != null) {
            launchApp(loadedApp)
            return
        }
        saveRecentPackage(pkg)
        val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            try {
                WakeAccessibilityService.temporarilyIgnorePackage(pkg)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                startActivity(launchIntent)
            } catch (_: Exception) {
            }
        }
    }

    private fun loadApps() {
        progressBar.visibility = View.VISIBLE
        listView.visibility = View.GONE
        tvEmpty.visibility = View.GONE

        // Read recents on the main thread (SharedPreferences is main-thread-safe)
        val savedRecents = loadRecentPackages()
        // Sync in-memory list from prefs (in case activity was recreated)
        recentPackages.clear()
        recentPackages.addAll(savedRecents)

        bgExecutor.execute {
            val pm = packageManager
            val selfPkg = packageName

            // 直接查詢 LEANBACK_LAUNCHER 與 LAUNCHER，加入 MATCH_DISABLED_COMPONENTS(512) 以顯示被凍結的 App
            val flags = android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
            val leanbackResolves = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), flags
            )
            val mobileResolves = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), flags
            )

            // 以 packageName 去重：優先保留 TV Leanback 入口
            val resolvedMap = ArrayMap<String, Pair<ResolveInfo, Boolean>>(64)
            for (ri in leanbackResolves) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg != selfPkg && !resolvedMap.containsKey(pkg)) {
                    resolvedMap[pkg] = Pair(ri, true)
                }
            }
            for (ri in mobileResolves) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg != selfPkg && !resolvedMap.containsKey(pkg)) {
                    resolvedMap[pkg] = Pair(ri, false)
                }
            }

            val tvUserApps = ArrayList<ListItem.App>(24)
            val mobileUserApps = ArrayList<ListItem.App>(16)
            val systemApps = ArrayList<ListItem.App>(32)
            val frozenApps = ArrayList<ListItem.App>(16)
            // Map for quick recent-app lookup
            val pkgToApp = ArrayMap<String, ListItem.App>(resolvedMap.size)

            for (i in 0 until resolvedMap.size) {
                val (ri, isLeanback) = resolvedMap.valueAt(i)
                val actInfo = ri.activityInfo ?: continue
                val appInfo = actInfo.applicationInfo ?: continue
                val pkg = actInfo.packageName

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isPersistent = (appInfo.flags and ApplicationInfo.FLAG_PERSISTENT) != 0

                val label = try {
                    ri.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                        ?: pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    pkg
                }

                val app = ListItem.App(
                    label = label,
                    packageName = pkg,
                    componentName = ComponentName(pkg, actInfo.name),
                    isLeanback = isLeanback,
                    appInfo = appInfo,
                    isSystem = isSystem,
                    isDisableable = isSystem && !isPersistent
                )

                pkgToApp[pkg] = app
                
                when {
                    !appInfo.enabled -> frozenApps.add(app)
                    isSystem -> systemApps.add(app)
                    isLeanback -> tvUserApps.add(app)
                    else -> mobileUserApps.add(app)
                }
            }

            val col = Collator.getInstance()
            val comp = Comparator<ListItem.App> { a, b -> col.compare(a.label, b.label) }
            tvUserApps.sortWith(comp)
            mobileUserApps.sortWith(comp)
            systemApps.sortWith(comp)
            frozenApps.sortWith(comp)

            // Build recent apps list (preserve recency order, skip stale packages)
            val recentApps = savedRecents.mapNotNull { pkgToApp[it] }.filter { it.appInfo.enabled }

            val result = ArrayList<ListItem>(
                recentApps.size + tvUserApps.size + mobileUserApps.size + systemApps.size + frozenApps.size + 5
            )
            if (recentApps.isNotEmpty()) {
                result.add(ListItem.Section(getString(R.string.section_recent, recentApps.size)))
                result.addAll(recentApps)
            }
            if (tvUserApps.isNotEmpty()) {
                result.add(ListItem.Section(getString(R.string.section_tv_apps, tvUserApps.size)))
                result.addAll(tvUserApps)
            }
            if (mobileUserApps.isNotEmpty()) {
                result.add(ListItem.Section(getString(R.string.section_mobile_apps, mobileUserApps.size)))
                result.addAll(mobileUserApps)
            }
            if (systemApps.isNotEmpty()) {
                result.add(ListItem.Section(getString(R.string.section_system_apps, systemApps.size)))
                result.addAll(systemApps)
            }
            if (frozenApps.isNotEmpty()) {
                result.add(ListItem.Section("已停用 App (${frozenApps.size})"))
                result.addAll(frozenApps)
            }

            mainHandler.post {
                if (isDestroyedFlag || isFinishing) return@post
                items.clear()
                items.addAll(result)
                adapter.notifyDataSetChanged()

                progressBar.visibility = View.GONE
                if (items.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    listView.visibility = View.VISIBLE
                    if (!btnSettings.hasFocus() && !btnHdmi.hasFocus()) {
                        listView.requestFocus()
                        val firstApp = items.indexOfFirst { it is ListItem.App }
                        if (firstApp >= 0) listView.setSelection(firstApp)
                    }
                }
            }
        }
    }


    private fun loadIconAsync(app: ListItem.App) {
        val pkg = app.packageName
        if (iconCache.containsKey(pkg) || !loadingIcons.add(pkg)) return

        bgExecutor.execute {
            if (isDestroyedFlag) return@execute
            val icon = try {
                packageManager.getApplicationIcon(app.appInfo)
            } catch (_: Exception) {
                null
            }
            mainHandler.post {
                if (isDestroyedFlag || isFinishing) return@post
                if (icon != null) {
                    iconCache[pkg] = icon
                    updateVisibleRowIcon(pkg, icon)
                } else {
                    // 載入失敗時移除快取鍵，允許下次可見時重試
                    loadingIcons.remove(pkg)
                }
            }
        }
    }

    private fun updateVisibleRowIcon(pkg: String, icon: Drawable) {
        val first = listView.firstVisiblePosition
        val last = listView.lastVisiblePosition
        for (pos in first..last) {
            val child = listView.getChildAt(pos - first) ?: continue
            val holder = child.tag as? AppViewHolder ?: continue
            if (holder.boundPackage == pkg) {
                holder.ivIcon.clearColorFilter()
                holder.ivIcon.setImageDrawable(icon)
            }
        }
    }

    private data class MenuOption(
        val title: String,
        val action: () -> Unit
    )

    private fun setAutoOpenSelection(pkg: String, label: String) {
        MainActivity.setAutoOpenApp(this, pkg, label)
        if (pkg.isNotBlank() && !MainActivity.isAppModeEnabled(this)) {
            MainActivity.setAppModeEnabled(this, true)
        }
        adapter.notifyDataSetChanged()
        if (pkg.isBlank()) {
            cancelAutoOpenCountdown()
        }
    }

    private fun showAppMenu(app: ListItem.App) {
        val isCurrentlyAutoOpen = MainActivity.getAutoOpenPackage(this) == app.packageName
        val options = ArrayList<MenuOption>(6).apply {
            add(MenuOption(getString(R.string.menu_open)) {
                launchApp(app)
            })
            add(MenuOption("選取 (批次停用/啟用)") {
                enterMultiSelectMode(app.packageName)
            })
            if (isCurrentlyAutoOpen) {
                add(MenuOption(getString(R.string.menu_clear_auto_open)) {
                    setAutoOpenSelection("", "")
                })
            } else {
                add(MenuOption(getString(R.string.menu_set_auto_open)) {
                    setAutoOpenSelection(app.packageName, app.label)
                })
            }
            if (!app.isSystem) {
                add(MenuOption(getString(R.string.menu_uninstall)) {
                    uninstallApp(app)
                })
            }
            if (app.isDisableable) {
                if (app.appInfo.enabled) {
                    add(MenuOption("透過 Shizuku 停用 (凍結)") {
                        toggleAppFreeze(app, true)
                    })
                } else {
                    add(MenuOption("透過 Shizuku 啟用 (解凍)") {
                        toggleAppFreeze(app, false)
                    })
                }
            }
            add(MenuOption(getString(R.string.menu_app_info)) {
                openAppInfo(app)
            })
        }

        val items = options.map { it.title }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(app.label)
            .setItems(items) { _, which ->
                options[which].action()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        // 從外部 App 返回時重新載入清單，確保「最近使用」區段即時更新
        loadApps()
    }

    override fun onStop() {
        super.onStop()
        isForegroundFocused = false
        cancelAutoOpenCountdown()
        // 進入背景時釋放圖示快取，確保外部 App 執行時 0 點陣圖常駐記憶體；
        // 且「不」呼叫 finish()，讓 AppListActivity 持續擋在 MainActivity 上方，
        // 確保外部 App 轉場或關閉時絕對不會誤喚醒底層的 MainActivity 計時器！
        iconCache.clear()
        loadingIcons.clear()
    }

    private fun launchApp(app: ListItem.App) {
        cancelAutoOpenCountdown()
        if (!app.appInfo.enabled) {
            android.widget.Toast.makeText(this, "請先長按解凍 ${app.label}", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        saveRecentPackage(app.packageName)
        try {
            WakeAccessibilityService.temporarilyIgnorePackage(app.packageName)
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(
                    if (app.isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER
                    else Intent.CATEGORY_LAUNCHER
                )
                component = app.componentName
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(intent)
        } catch (_: Exception) {
            val fallback = packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
                ?: packageManager.getLaunchIntentForPackage(app.packageName)
            if (fallback != null) {
                try {
                    WakeAccessibilityService.temporarilyIgnorePackage(app.packageName)
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(fallback)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun uninstallApp(app: ListItem.App) {
        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
    }

    private fun toggleAppFreeze(app: ListItem.App, freeze: Boolean) {
        val success = ShizukuHelper.setAppDisabled(app.packageName, freeze)
        if (success) {
            val action = if (freeze) "停用" else "啟用"
            android.widget.Toast.makeText(this, "已透過 Shizuku $action ${app.label}", android.widget.Toast.LENGTH_SHORT).show()
            loadApps() // 重新載入列表
        } else {
            // 如果 Shizuku 未授權或失敗，提示並引導至 Shizuku 設定與檢測頁面
            android.widget.Toast.makeText(this, "Shizuku 操作失敗，請確認 Shizuku 正在執行並已授權", android.widget.Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ShizukuSettingsActivity::class.java))
        }
    }

    private fun openAppInfo(app: ListItem.App) {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${app.packageName}")
            )
        )
    }

    private fun openSettings() {
        cancelAutoOpenCountdown()
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun enterMultiSelectMode(initialPkg: String) {
        isMultiSelectMode = true
        selectedPackages.clear()
        selectedPackages.add(initialPkg)
        updateHeaderUI()
        adapter.notifyDataSetChanged()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedPackages.clear()
        updateHeaderUI()
        adapter.notifyDataSetChanged()
    }

    private fun toggleSelection(pkg: String) {
        if (selectedPackages.contains(pkg)) {
            selectedPackages.remove(pkg)
            if (selectedPackages.isEmpty()) {
                exitMultiSelectMode()
                return
            }
        } else {
            selectedPackages.add(pkg)
        }
        updateHeaderUI()
        adapter.notifyDataSetChanged()
    }

    private fun updateHeaderUI() {
        if (isMultiSelectMode) {
            tvTitle.text = "已選取 ${selectedPackages.size} 個"
            tvHint.visibility = View.GONE
            btnSettings.visibility = View.GONE
            btnHdmi.visibility = View.GONE
            btnBatchAction.visibility = View.VISIBLE
            btnCancelSelect.visibility = View.VISIBLE
            btnBatchAction.requestFocus()
        } else {
            tvTitle.text = getString(R.string.app_list_title)
            tvHint.visibility = View.VISIBLE
            btnSettings.visibility = View.VISIBLE
            btnHdmi.visibility = View.VISIBLE
            btnBatchAction.visibility = View.GONE
            btnCancelSelect.visibility = View.GONE
        }
    }

    private fun showBatchActionDialog() {
        if (selectedPackages.isEmpty()) return
        
        val options = ArrayList<MenuOption>()
        options.add(MenuOption("停用 (凍結) 已選取") {
            executeBatchFreeze(true)
        })
        options.add(MenuOption("啟用 (解凍) 已選取") {
            executeBatchFreeze(false)
        })
        options.add(MenuOption("全選") {
            selectAllApps()
        })
        
        val items = options.map { it.title }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("批次操作 (${selectedPackages.size} 個)")
            .setItems(items) { _, which ->
                options[which].action()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }
    
    private fun selectAllApps() {
        for (item in items) {
            if (item is ListItem.App) {
                selectedPackages.add(item.packageName)
            }
        }
        updateHeaderUI()
        adapter.notifyDataSetChanged()
    }

    private fun executeBatchFreeze(freeze: Boolean) {
        progressBar.visibility = View.VISIBLE
        listView.visibility = View.GONE
        bgExecutor.execute {
            var successCount = 0
            var failCount = 0
            for (pkg in selectedPackages) {
                if (isDestroyedFlag) return@execute
                val success = ShizukuHelper.setAppDisabled(pkg, freeze)
                if (success) successCount++ else failCount++
            }
            mainHandler.post {
                if (isDestroyedFlag || isFinishing) return@post
                val action = if (freeze) "停用" else "啟用"
                android.widget.Toast.makeText(
                    this@AppListActivity, 
                    "已批次$action $successCount 個 App" + if (failCount > 0) "，失敗 $failCount 個" else "", 
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                exitMultiSelectMode()
                loadApps()
            }
        }
    }

    private fun returnToMainActivity() {
        cancelAutoOpenCountdown()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_FROM_APP_LIST, true)
        }
        startActivity(intent)
        finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (isAutoOpenCountdownRunning) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_MENU -> {
                        cancelAutoOpenCountdown()
                        if (::tvAutoOpenBanner.isInitialized) {
                            tvAutoOpenBanner.visibility = View.GONE
                        }
                        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                            if (isMultiSelectMode) exitMultiSelectMode()
                            return true
                        }
                    }
                }
            }
            if (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_SETTINGS) {
                if (isMultiSelectMode) showBatchActionDialog() else openSettings()
                return true
            }
            if (listView.hasFocus() && event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                val firstAppPos = items.indexOfFirst { it is ListItem.App }
                if (firstAppPos < 0 || listView.selectedItemPosition <= firstAppPos) {
                    if (isMultiSelectMode) btnBatchAction.requestFocus() else btnSettings.requestFocus()
                    return true
                }
            } else if ((btnSettings.hasFocus() || btnHdmi.hasFocus() || btnBatchAction.hasFocus() || btnCancelSelect.hasFocus()) && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (items.isNotEmpty()) {
                    listView.requestFocus()
                    val firstAppPos = items.indexOfFirst { it is ListItem.App }
                    if (firstAppPos >= 0 && listView.selectedItemPosition < firstAppPos) {
                        listView.setSelection(firstAppPos)
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isMultiSelectMode) {
                exitMultiSelectMode()
                return true
            }
            returnToMainActivity()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ── 純程式碼建構介面（消除 XML LayoutInflater 與多餘背景重繪） ─────────────
    private fun buildContentView(): View {
        val density = resources.displayMetrics.density
        fun dp(v: Float): Int = (v * density + 0.5f).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
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
            val pad = dp(20f)
            setPadding(pad, pad, pad, pad)
        }

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val ivTitleIcon = ImageView(this).apply {
            setImageResource(R.drawable.apps_48px)
            setColorFilter(0xFF60A5FA.toInt())
        }
        titleBox.addView(ivTitleIcon, LinearLayout.LayoutParams(dp(30f), dp(30f)).apply {
            rightMargin = dp(12f)
        })
        tvTitle = TextView(this).apply {
            text = getString(R.string.app_list_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFF8FAFC.toInt())
            isSingleLine = true
        }
        titleBox.addView(tvTitle, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        header.addView(titleBox, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        tvHint = TextView(this).apply {
            text = getString(R.string.app_list_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0xFF475569.toInt())
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        header.addView(tvHint, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
            leftMargin = dp(16f)
            rightMargin = dp(16f)
        })

        // 設定按鈕（開啟 SettingsActivity）
        val (settingsBtn, _, _) = createHeaderPillButton(
            iconRes = R.drawable.settings_48px,
            label = getString(R.string.btn_settings),
            density = density
        ) {
            openSettings()
        }
        btnSettings = settingsBtn
        header.addView(btnSettings, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            rightMargin = dp(10f)
        })

        // HDMI 訊號源切換按鈕
        val (hdmiBtn, _, _) = createHeaderPillButton(
            iconRes = R.drawable.settings_input_hdmi_24px,
            label = getString(R.string.btn_hdmi_selector),
            density = density
        ) {
            returnToMainActivity()
        }
        btnHdmi = hdmiBtn
        header.addView(btnHdmi, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        val (batchBtn, _, _) = createHeaderPillButton(
            iconRes = R.drawable.settings_48px,
            label = "批次操作",
            density = density
        ) {
            showBatchActionDialog()
        }
        btnBatchAction = batchBtn
        btnBatchAction.visibility = View.GONE
        header.addView(btnBatchAction, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            rightMargin = dp(10f)
        })

        val (cancelSelBtn, _, _) = createHeaderPillButton(
            iconRes = 0,
            label = "取消",
            density = density
        ) {
            exitMultiSelectMode()
        }
        btnCancelSelect = cancelSelBtn
        btnCancelSelect.visibility = View.GONE
        header.addView(btnCancelSelect, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        val idSettings = View.generateViewId()
        val idHdmi = View.generateViewId()
        val idBatch = View.generateViewId()
        val idCancel = View.generateViewId()
        btnSettings.id = idSettings
        btnHdmi.id = idHdmi
        btnBatchAction.id = idBatch
        btnCancelSelect.id = idCancel

        btnSettings.nextFocusLeftId = idSettings
        btnSettings.nextFocusRightId = idHdmi

        btnHdmi.nextFocusLeftId = idSettings
        btnHdmi.nextFocusRightId = idHdmi

        btnBatchAction.nextFocusLeftId = idBatch
        btnBatchAction.nextFocusRightId = idCancel

        btnCancelSelect.nextFocusLeftId = idBatch
        btnCancelSelect.nextFocusRightId = idCancel

        root.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // 分隔線
        val dividerView = View(this).apply {
            setBackgroundColor(0xFF1E293B.toInt())
        }
        root.addView(dividerView, LinearLayout.LayoutParams(MATCH_PARENT, 1))

        // 開機/喚醒自動啟動倒數提示橫幅
        tvAutoOpenBanner = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF38BDF8.toInt())
            setBackgroundColor(0xFF0F172A.toInt())
            gravity = Gravity.CENTER
            val vPad = dp(10f)
            setPadding(dp(20f), vPad, dp(20f), vPad)
            visibility = View.GONE
        }
        root.addView(tvAutoOpenBanner, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // 載入中指示器
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }
        root.addView(progressBar, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(80f)
        })

        // 空清單提示
        tvEmpty = TextView(this).apply {
            text = getString(R.string.app_list_empty)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(0xFF64748B.toInt())
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(tvEmpty, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(80f)
        })

        // App 清單 ListView
        listView = ListView(this).apply {
            visibility = View.GONE
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = true
            divider = ColorDrawable(0xFF1E293B.toInt())
            dividerHeight = 1
            selector = createListSelector(density)
            isDrawSelectorOnTop = false
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(listView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        return root
    }

    private fun createHeaderPillButton(
        iconRes: Int,
        label: String,
        density: Float,
        onClickAction: () -> Unit
    ): Triple<LinearLayout, TextView, ImageView?> {
        fun dp(v: Float): Int = (v * density + 0.5f).toInt()
        val radius = 24f * density
        val strokeFocused = (2.5f * density + 0.5f).toInt()
        val strokeNormal = (1.5f * density + 0.5f).toInt()

        fun rect(fillColor: Int, strokeWidth: Int, strokeColor: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fillColor)
            setStroke(strokeWidth, strokeColor)
        }

        val bgSelector = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                rect(0xFF2563EB.toInt(), strokeFocused, 0xFF93C5FD.toInt())
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                rect(0xFF1D4ED8.toInt(), strokeFocused, 0xFFBFDBFE.toInt())
            )
            addState(
                intArrayOf(),
                rect(0xFF18181B.toInt(), strokeNormal, 0xFF2E2E33.toInt())
            )
        }

        val iv = if (iconRes != 0) {
            ImageView(this).apply {
                val d = getDrawable(iconRes)?.mutate()
                setImageDrawable(d)
                setColorFilter(0xFF94A3B8.toInt())
            }
        } else null

        val tv = TextView(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFE2E8F0.toInt())
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }

        val button = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val hPad = dp(16f)
            val vPad = dp(9f)
            setPadding(hPad, vPad, hPad, vPad)
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            background = bgSelector

            if (iv != null) {
                addView(iv, LinearLayout.LayoutParams(dp(20f), dp(20f)).apply {
                    rightMargin = dp(8f)
                })
            }
            addView(tv, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

            setOnClickListener { onClickAction() }
            setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.08f else 1.0f
                v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
                v.elevation = if (hasFocus) dp(6f).toFloat() else 0f
            }
        }

        return Triple(button, tv, iv)
    }

    private fun createListSelector(density: Float): Drawable {
        val strokeWidth = (2f * density + 0.5f).toInt()
        val focused = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(0xFF1E3A5F.toInt())
            setStroke(strokeWidth, 0xFF2563EB.toInt())
        }
        val focusedPressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(0xFF1D4ED8.toInt())
            setStroke(strokeWidth, 0xFF93C5FD.toInt())
        }
        val pressed = ColorDrawable(0xFF1D4ED8.toInt())
        val transparent = ColorDrawable(Color.TRANSPARENT)

        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused, android.R.attr.state_pressed),
                focusedPressed
            )
            addState(
                intArrayOf(android.R.attr.state_focused),
                focused
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                pressed
            )
            addState(intArrayOf(), transparent)
        }
    }

    inner class AppListAdapter : BaseAdapter() {

        override fun getCount() = items.size
        override fun getItem(pos: Int): Any? = items[pos]
        override fun getItemId(pos: Int) = pos.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(pos: Int) =
            if (items[pos] is ListItem.Section) VIEW_TYPE_SECTION else VIEW_TYPE_APP

        override fun isEnabled(pos: Int) = items[pos] is ListItem.App

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View =
            when (val item = items[pos]) {
                is ListItem.Section -> getSectionView(item, convertView, parent)
                is ListItem.App -> getAppView(item, convertView)
            }

        private fun getSectionView(
            section: ListItem.Section,
            convertView: View?,
            parent: ViewGroup
        ): View {
            val tv = (convertView as? TextView) ?: TextView(parent.context).also { tv ->
                tv.setPadding(dpToPx(20), dpToPx(18), dpToPx(20), dpToPx(8))
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                tv.setTextColor(0xFF64748B.toInt())
                tv.isAllCaps = true
                tv.letterSpacing = 0.08f
                tv.isFocusable = false
                tv.isClickable = false
            }
            tv.text = section.title
            return tv
        }

        private fun getAppView(app: ListItem.App, convertView: View?): View {
            val view: View
            val holder: AppViewHolder

            if (convertView == null || convertView.tag !is AppViewHolder) {
                holder = createAppRowViewHolder()
                view = holder.rootView
                view.tag = holder
            } else {
                view = convertView
                holder = convertView.tag as AppViewHolder
            }

            holder.bind(app)
            return view
        }
    }

    private fun createAppRowViewHolder(): AppViewHolder {
        val hPad = dpToPx(20)
        val iconSize = dpToPx(48)

        val row = LinearLayout(this).apply {
            layoutParams = AbsListView.LayoutParams(MATCH_PARENT, dpToPx(72))
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(hPad, 0, hPad, 0)
            isFocusable = false
            isClickable = false
        }

        val ivIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        row.addView(ivIcon, LinearLayout.LayoutParams(iconSize, iconSize))

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), 0, 0, 0)
        }

        val tvLabel = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(0xFFF1F5F9.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textCol.addView(tvLabel, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        val tvPkg = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0xFF475569.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textCol.addView(tvPkg, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        row.addView(textCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val tvBadge = TextView(this).apply {
            text = getString(R.string.badge_auto_open)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF38BDF8.toInt())
            visibility = View.GONE
        }
        row.addView(tvBadge, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            leftMargin = dpToPx(12)
        })

        val cbSelect = android.widget.CheckBox(this).apply {
            isFocusable = false
            isClickable = false
            visibility = View.GONE
        }
        row.addView(cbSelect, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            leftMargin = dpToPx(16)
        })

        return AppViewHolder(row, ivIcon, tvLabel, tvPkg, tvBadge, cbSelect)
    }

    private inner class AppViewHolder(
        val rootView: View,
        val ivIcon: ImageView,
        val tvLabel: TextView,
        val tvPkg: TextView,
        val tvBadge: TextView,
        val cbSelect: android.widget.CheckBox
    ) {
        var boundPackage: String = ""

        fun bind(app: ListItem.App) {
            boundPackage = app.packageName
            
            if (app.appInfo.enabled) {
                tvLabel.text = app.label
                tvLabel.setTextColor(Color.WHITE)
                tvPkg.text = app.packageName
            } else {
                tvLabel.text = "${app.label} (已停用)"
                tvLabel.setTextColor(0xFF64748B.toInt()) // Gray color for disabled
                tvPkg.text = app.packageName + " [FROZEN]"
            }
            
            tvBadge.visibility = if (MainActivity.getAutoOpenPackage(this@AppListActivity) == app.packageName) {
                View.VISIBLE
            } else {
                View.GONE
            }

            if (isMultiSelectMode) {
                cbSelect.visibility = View.VISIBLE
                cbSelect.isChecked = selectedPackages.contains(app.packageName)
            } else {
                cbSelect.visibility = View.GONE
            }

            val cached = iconCache[app.packageName]
            if (cached != null) {
                ivIcon.clearColorFilter()
                ivIcon.setImageDrawable(cached)
            } else {
                ivIcon.setImageResource(R.drawable.apps_48px)
                ivIcon.setColorFilter(0xFF334155.toInt())
                loadIconAsync(app)
            }
        }
    }
}
