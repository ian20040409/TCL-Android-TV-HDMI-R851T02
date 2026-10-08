package com.lnu.tclhdmilauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.ArrayMap
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.concurrent.Executors

/** App discovery, launching and management stay independent of Compose presentation. */
class AppListActivity : ComponentActivity() {

    companion object {
        private const val PREFS_RECENT = "app_list_recent"
        private const val KEY_RECENT_PKGS = "recent_pkgs"
        private const val RECENT_SEPARATOR = "|"
        private const val MAX_RECENT_COUNT = 8

        @Volatile
        var isForegroundFocused: Boolean = false
            internal set
    }

    private var isMultiSelectMode by mutableStateOf(false)
    private var selectedPackages by mutableStateOf<Set<String>>(emptySet())
    private var items by mutableStateOf<List<AppListItem>>(emptyList())
    private val iconCache = mutableStateMapOf<String, Bitmap>()
    private val loadingIcons = HashSet<String>(32)
    private var isLoading by mutableStateOf(true)
    private var countdownText by mutableStateOf<String?>(null)
    private var autoOpenPackage by mutableStateOf("")
    private var headerFocusGeneration by mutableStateOf(0)
    private var dialog by mutableStateOf<AppListDialog?>(null)
    private var iconLoadGeneration = 0
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
            val pkg = SettingsRepository.getAutoOpenPackage(this@AppListActivity)
            val label = SettingsRepository.getAutoOpenLabel(this@AppListActivity).ifBlank { pkg }
            if (pkg.isBlank() || !SettingsRepository.isAppModeEnabled(this@AppListActivity)) {
                cancelAutoOpenCountdown()
                return
            }
            if (autoOpenSecondsLeft > 0) {
                countdownText = getString(R.string.auto_open_countdown_banner, autoOpenSecondsLeft, label)
                mainHandler.postDelayed(this, 1000L)
            } else {
                cancelAutoOpenCountdown()
                launchAutoOpenPackage(pkg)
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
        autoOpenPackage = SettingsRepository.getAutoOpenPackage(this)
        setContent {
            LauncherTheme {
                AppListScreen(
                    items = items,
                    icons = iconCache,
                    isLoading = isLoading,
                    countdownText = countdownText,
                    isMultiSelectMode = isMultiSelectMode,
                    selectedPackages = selectedPackages,
                    autoOpenPackage = autoOpenPackage,
                    headerFocusGeneration = headerFocusGeneration,
                    dialog = dialog,
                    onAppClick = { app ->
                        cancelAutoOpenCountdown()
                        if (isMultiSelectMode) toggleSelection(app.packageName) else launchApp(app)
                    },
                    onAppLongClick = { app ->
                        cancelAutoOpenCountdown()
                        if (isMultiSelectMode) showBatchActionDialog() else showAppMenu(app)
                    },
                    onIconNeeded = ::loadIconAsync,
                    onSettingsClick = ::openSettings,
                    onHdmiClick = ::returnToMainActivity,
                    onBatchClick = ::showBatchActionDialog,
                    onCancelSelection = ::exitMultiSelectMode,
                    onDialogDismiss = { dialog = null },
                )
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    dialog != null -> dialog = null
                    isAutoOpenCountdownRunning -> {
                        cancelAutoOpenCountdown()
                        if (isMultiSelectMode) exitMultiSelectMode()
                    }
                    isMultiSelectMode -> exitMultiSelectMode()
                    else -> returnToMainActivity()
                }
            }
        })
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
        autoOpenPackage = SettingsRepository.getAutoOpenPackage(this)
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
        if (!SettingsRepository.isAppModeEnabled(this)) return
        val pkg = SettingsRepository.getAutoOpenPackage(this)
        if (pkg.isBlank()) return
        val label = SettingsRepository.getAutoOpenLabel(this).ifBlank { pkg }

        mainHandler.removeCallbacks(autoOpenTickRunnable)
        autoOpenSecondsLeft = SettingsRepository.getAutoOpenDelaySeconds(this)
        isAutoOpenCountdownRunning = true
        countdownText = getString(R.string.auto_open_countdown_banner, autoOpenSecondsLeft, label)
        mainHandler.postDelayed(autoOpenTickRunnable, 1000L)
    }

    private fun cancelAutoOpenCountdown() {
        isAutoOpenCancelled = true
        if (isAutoOpenCountdownRunning) {
            isAutoOpenCountdownRunning = false
            mainHandler.removeCallbacks(autoOpenTickRunnable)
            countdownText = null
        }
    }

    private fun launchAutoOpenPackage(pkg: String) {
        val loadedApp = items.firstOrNull { it is AppListItem.App && it.packageName == pkg } as? AppListItem.App
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
        isLoading = true

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

            val tvUserApps = ArrayList<AppListItem.App>(24)
            val mobileUserApps = ArrayList<AppListItem.App>(16)
            val systemApps = ArrayList<AppListItem.App>(32)
            val frozenApps = ArrayList<AppListItem.App>(16)
            // Map for quick recent-app lookup
            val pkgToApp = ArrayMap<String, AppListItem.App>(resolvedMap.size)

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

                val app = AppListItem.App(
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
            val comp = Comparator<AppListItem.App> { a, b -> col.compare(a.label, b.label) }
            tvUserApps.sortWith(comp)
            mobileUserApps.sortWith(comp)
            systemApps.sortWith(comp)
            frozenApps.sortWith(comp)

            // Build recent apps list (preserve recency order, skip stale packages)
            val recentApps = savedRecents.mapNotNull { pkgToApp[it] }.filter { it.appInfo.enabled }

            val result = ArrayList<AppListItem>(
                recentApps.size + tvUserApps.size + mobileUserApps.size + systemApps.size + frozenApps.size + 5
            )
            if (recentApps.isNotEmpty()) {
                result.add(AppListItem.Section(getString(R.string.section_recent, recentApps.size)))
                result.addAll(recentApps)
            }
            if (tvUserApps.isNotEmpty()) {
                result.add(AppListItem.Section(getString(R.string.section_tv_apps, tvUserApps.size)))
                result.addAll(tvUserApps)
            }
            if (mobileUserApps.isNotEmpty()) {
                result.add(AppListItem.Section(getString(R.string.section_mobile_apps, mobileUserApps.size)))
                result.addAll(mobileUserApps)
            }
            if (systemApps.isNotEmpty()) {
                result.add(AppListItem.Section(getString(R.string.section_system_apps, systemApps.size)))
                result.addAll(systemApps)
            }
            if (frozenApps.isNotEmpty()) {
                result.add(AppListItem.Section(getString(R.string.section_frozen_apps, frozenApps.size)))
                result.addAll(frozenApps)
            }

            mainHandler.post {
                if (isDestroyedFlag || isFinishing) return@post
                items = result
                autoOpenPackage = SettingsRepository.getAutoOpenPackage(this)
                isLoading = false
            }
        }
    }


    private fun loadIconAsync(app: AppListItem.App) = loadIconAsync(app, retryOnFailure = true)

    private fun loadIconAsync(app: AppListItem.App, retryOnFailure: Boolean) {
        val pkg = app.packageName
        if (isDestroyedFlag || isFinishing || iconCache.containsKey(pkg) || !loadingIcons.add(pkg)) return
        val generation = iconLoadGeneration
        val iconSize = (48 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        bgExecutor.execute {
            if (isDestroyedFlag) return@execute
            val icon = try {
                packageManager.getApplicationIcon(app.appInfo).toBitmap(iconSize, iconSize)
            } catch (_: Exception) {
                null
            }
            mainHandler.post {
                if (isDestroyedFlag || isFinishing || generation != iconLoadGeneration) return@post
                if (icon != null) iconCache[pkg] = icon
                loadingIcons.remove(pkg)
                if (icon == null && retryOnFailure) {
                    // Retry transient PackageManager failures once, never across cache eviction.
                    mainHandler.postDelayed({
                        if (generation == iconLoadGeneration && isActivityResumed) {
                            loadIconAsync(app, retryOnFailure = false)
                        }
                    }, 500L)
                }
            }
        }
    }

    private fun setAutoOpenSelection(pkg: String, label: String) {
        SettingsRepository.setAutoOpenApp(this, pkg, label)
        if (pkg.isNotBlank() && !SettingsRepository.isAppModeEnabled(this)) {
            SettingsRepository.setAppModeEnabled(this, true)
        }
        autoOpenPackage = SettingsRepository.getAutoOpenPackage(this)
        if (pkg.isBlank()) {
            cancelAutoOpenCountdown()
        }
    }

    private fun showAppMenu(app: AppListItem.App) {
        val isCurrentlyAutoOpen = SettingsRepository.getAutoOpenPackage(this) == app.packageName
        val options = ArrayList<AppListMenuOption>(6).apply {
            add(AppListMenuOption(getString(R.string.menu_open)) {
                launchApp(app)
            })
            add(AppListMenuOption(getString(R.string.menu_batch_select)) {
                enterMultiSelectMode(app.packageName)
            })
            if (isCurrentlyAutoOpen) {
                add(AppListMenuOption(getString(R.string.menu_clear_auto_open)) {
                    setAutoOpenSelection("", "")
                })
            } else {
                add(AppListMenuOption(getString(R.string.menu_set_auto_open)) {
                    setAutoOpenSelection(app.packageName, app.label)
                })
            }
            if (!app.isSystem) {
                add(AppListMenuOption(getString(R.string.menu_uninstall)) {
                    uninstallApp(app)
                })
            }
            if (app.isDisableable) {
                if (app.appInfo.enabled) {
                    add(AppListMenuOption(getString(R.string.menu_freeze)) {
                        toggleAppFreeze(app, true)
                    })
                } else {
                    add(AppListMenuOption(getString(R.string.menu_unfreeze)) {
                        toggleAppFreeze(app, false)
                    })
                }
            }
            add(AppListMenuOption(getString(R.string.menu_app_info)) {
                openAppInfo(app)
            })
        }

        cancelAutoOpenCountdown()
        dialog = AppListDialog(app.label, options)
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
        iconLoadGeneration++
        iconCache.clear()
        loadingIcons.clear()
    }

    private fun launchApp(app: AppListItem.App) {
        cancelAutoOpenCountdown()
        if (!app.appInfo.enabled) {
            android.widget.Toast.makeText(this, getString(R.string.toast_unfreeze_first, app.label), android.widget.Toast.LENGTH_SHORT).show()
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

    private fun uninstallApp(app: AppListItem.App) {
        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
    }

    private fun toggleAppFreeze(app: AppListItem.App, freeze: Boolean) {
        val success = ShizukuHelper.setAppDisabled(app.packageName, freeze)
        if (success) {
            val action = if (freeze) getString(R.string.action_disable) else getString(R.string.action_enable)
            android.widget.Toast.makeText(this, getString(R.string.toast_freeze_success, action, app.label), android.widget.Toast.LENGTH_SHORT).show()
            loadApps() // 重新載入列表
        } else {
            // 如果 Shizuku 未授權或失敗，提示並引導至 Shizuku 設定與檢測頁面
            android.widget.Toast.makeText(this, getString(R.string.toast_shizuku_fail), android.widget.Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ShizukuSettingsActivity::class.java))
        }
    }

    private fun openAppInfo(app: AppListItem.App) {
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
        cancelAutoOpenCountdown()
        isMultiSelectMode = true
        selectedPackages = setOf(initialPkg)
        headerFocusGeneration++
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedPackages = emptySet()
    }

    private fun toggleSelection(pkg: String) {
        selectedPackages = if (pkg in selectedPackages) selectedPackages - pkg else selectedPackages + pkg
        if (selectedPackages.isEmpty()) exitMultiSelectMode()
        else headerFocusGeneration++
    }

    private fun showBatchActionDialog() {
        if (selectedPackages.isEmpty()) return

        val options = ArrayList<AppListMenuOption>()
        options.add(AppListMenuOption(getString(R.string.menu_batch_freeze)) {
            executeBatchFreeze(true)
        })
        options.add(AppListMenuOption(getString(R.string.menu_batch_unfreeze)) {
            executeBatchFreeze(false)
        })
        options.add(AppListMenuOption(getString(R.string.menu_select_all)) {
            selectAllApps()
        })

        cancelAutoOpenCountdown()
        dialog = AppListDialog(getString(R.string.title_batch_action, selectedPackages.size), options)
    }

    private fun selectAllApps() {
        selectedPackages = items.filterIsInstance<AppListItem.App>().map { it.packageName }.toSet()
        headerFocusGeneration++
    }

    private fun executeBatchFreeze(freeze: Boolean) {
        isLoading = true
        val packages = selectedPackages.toList()
        bgExecutor.execute {
            var successCount = 0
            var failCount = 0
            for (pkg in packages) {
                if (isDestroyedFlag) return@execute
                val success = ShizukuHelper.setAppDisabled(pkg, freeze)
                if (success) successCount++ else failCount++
            }
            mainHandler.post {
                if (isDestroyedFlag || isFinishing) return@post
                val action = if (freeze) getString(R.string.action_disable_upper) else getString(R.string.action_enable_upper)
                val successStr = getString(R.string.toast_batch_success, action, successCount)
                val failStr = if (failCount > 0) getString(R.string.toast_batch_fail, failCount) else ""
                android.widget.Toast.makeText(
                    this@AppListActivity, 
                    successStr + failStr, 
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
        if (dialog != null) return super.dispatchKeyEvent(event)
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
        }
        return super.dispatchKeyEvent(event)
    }
}
