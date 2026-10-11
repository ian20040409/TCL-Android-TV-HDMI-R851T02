package com.lnu.tclhdmilauncher

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.ArrayMap
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.Collator
import java.util.concurrent.Executors

/**
 * 結合 Material Components (MDC) 與 Android TV 原生 Leanback 佈局的 App List 啟動器
 * - 使用 MaterialCardView 作為項目基底，ColorStateList 控制邊框與背景反饋
 * - cardElevation 設為 0dp，避免低階電視 GPU 動態陰影掉幀
 * - VerticalGridView 固定 5 欄 (app:numberOfColumns="5")，鎖定欄數降低測量開銷
 * - windowAlignment = BaseGridView.WINDOW_ALIGN_BOTH_EDGE，滾動時平順對齊
 * - 頂部按鈕與分類過濾採用 Material 原生 MaterialButton 與 Chip / ChipGroup
 * - 保留開機自動啟動倒數、Shizuku 應用凍結/解凍、批次操作與遠端遙控器 DPAD 最佳化
 */
class AppListActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_RECENT = "app_list_recent"
        private const val KEY_RECENT_PKGS = "recent_pkgs"
        private const val RECENT_SEPARATOR = "|"
        private const val MAX_RECENT_COUNT = 10

        @Volatile
        var isForegroundFocused: Boolean = false
            internal set
    }

    data class AppItem(
        val label: String,
        val packageName: String,
        val componentName: ComponentName,
        val isLeanback: Boolean,
        val appInfo: ApplicationInfo,
        val isSystem: Boolean,
        val isDisableable: Boolean
    )

    enum class Category {
        ALL, RECENT, TV, MOBILE, SYSTEM, FROZEN
    }

    private lateinit var vgvApps: VerticalGridView
    private lateinit var tvTitle: TextView
    private lateinit var tvHint: TextView
    private lateinit var btnSettings: MaterialButton
    private lateinit var btnHdmi: MaterialButton
    private lateinit var btnBatchAction: MaterialButton
    private lateinit var btnCancelSelect: MaterialButton
    private lateinit var hsvCategories: HorizontalScrollView
    private lateinit var chipGroupCategories: ChipGroup
    private lateinit var tvAutoOpenBanner: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    private var currentCategory = Category.ALL
    private var isMultiSelectMode = false
    private val selectedPackages = HashSet<String>()

    private val allApps = ArrayList<AppItem>()
    private val recentApps = ArrayList<AppItem>()
    private val tvUserApps = ArrayList<AppItem>()
    private val mobileUserApps = ArrayList<AppItem>()
    private val systemApps = ArrayList<AppItem>()
    private val frozenApps = ArrayList<AppItem>()
    private val displayedApps = ArrayList<AppItem>()

    private val iconCache = ArrayMap<String, Drawable>(64)
    private val loadingIcons = HashSet<String>(32)
    private lateinit var adapter: AppCardAdapter

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
                tvAutoOpenBanner.text = getString(R.string.auto_open_countdown_banner, autoOpenSecondsLeft, label)
                mainHandler.postDelayed(this, 1000L)
            } else {
                cancelAutoOpenCountdown()
                launchAutoOpenPackage(pkg, label)
            }
        }
    }

    private val recentPackages = ArrayDeque<String>(MAX_RECENT_COUNT)

    private fun loadRecentPackages(): List<String> {
        val raw = getSharedPreferences(PREFS_RECENT, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_PKGS, "") ?: ""
        return if (raw.isBlank()) emptyList()
        else raw.split(RECENT_SEPARATOR).filter { it.isNotBlank() }
    }

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
        setContentView(R.layout.activity_tv_app_list)

        initViews()
        setupGridView()

        adapter = AppCardAdapter()
        vgvApps.adapter = adapter

        loadApps()
        isAutoOpenCancelled = false
    }

    private fun initViews() {
        vgvApps = findViewById(R.id.vgv_apps)
        tvTitle = findViewById(R.id.tv_page_title)
        tvHint = findViewById(R.id.tv_page_hint)
        btnSettings = findViewById(R.id.btn_settings)
        btnHdmi = findViewById(R.id.btn_hdmi)
        btnBatchAction = findViewById(R.id.btn_batch_action)
        btnCancelSelect = findViewById(R.id.btn_cancel_select)
        hsvCategories = findViewById(R.id.hsv_categories)
        chipGroupCategories = findViewById(R.id.chip_group_categories)
        tvAutoOpenBanner = findViewById(R.id.tv_auto_open_banner)
        progressBar = findViewById(R.id.progress_bar)
        tvEmpty = findViewById(R.id.tv_empty)

        btnSettings.setOnClickListener { openSettings() }
        btnHdmi.setOnClickListener { returnToMainActivity() }
        btnBatchAction.setOnClickListener { showBatchActionDialog() }
        btnCancelSelect.setOnClickListener { exitMultiSelectMode() }
    }

    private fun setupGridView() {
        vgvApps.setNumColumns(5)
        // 讓焦點項目滾動時自動對齊視窗垂直中央
        vgvApps.windowAlignment = BaseGridView.WINDOW_ALIGN_BOTH_EDGE
        vgvApps.windowAlignmentOffsetPercent = 50f
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

    override fun onStart() {
        super.onStart()
        loadApps()
    }

    override fun onStop() {
        super.onStop()
        isForegroundFocused = false
        cancelAutoOpenCountdown()
        iconCache.clear()
        loadingIcons.clear()
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
        val loadedApp = allApps.firstOrNull { it.packageName == pkg }
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
        vgvApps.visibility = View.GONE
        tvEmpty.visibility = View.GONE

        val savedRecents = loadRecentPackages()
        recentPackages.clear()
        recentPackages.addAll(savedRecents)

        bgExecutor.execute {
            val pm = packageManager
            val selfPkg = packageName

            val flags = PackageManager.MATCH_DISABLED_COMPONENTS
            val leanbackResolves = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), flags
            )
            val mobileResolves = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), flags
            )

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

            val tvAppsList = ArrayList<AppItem>(24)
            val mobileAppsList = ArrayList<AppItem>(16)
            val systemAppsList = ArrayList<AppItem>(32)
            val frozenAppsList = ArrayList<AppItem>(16)
            val pkgToApp = ArrayMap<String, AppItem>(resolvedMap.size)

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

                val app = AppItem(
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
                    !appInfo.enabled -> frozenAppsList.add(app)
                    isSystem -> systemAppsList.add(app)
                    isLeanback -> tvAppsList.add(app)
                    else -> mobileAppsList.add(app)
                }
            }

            val col = Collator.getInstance()
            val comp = Comparator<AppItem> { a, b -> col.compare(a.label, b.label) }
            tvAppsList.sortWith(comp)
            mobileAppsList.sortWith(comp)
            systemAppsList.sortWith(comp)
            frozenAppsList.sortWith(comp)

            val recentsList = savedRecents.mapNotNull { pkgToApp[it] }.filter { it.appInfo.enabled }

            // 全部應用列表：TV 用戶應用 -> 手機應用 -> 系統應用 -> 已停用應用
            val combinedAll = ArrayList<AppItem>(tvAppsList.size + mobileAppsList.size + systemAppsList.size + frozenAppsList.size)
            combinedAll.addAll(tvAppsList)
            combinedAll.addAll(mobileAppsList)
            combinedAll.addAll(systemAppsList)
            combinedAll.addAll(frozenAppsList)

            mainHandler.post {
                if (isDestroyedFlag || isFinishing) return@post

                allApps.clear()
                allApps.addAll(combinedAll)

                recentApps.clear()
                recentApps.addAll(recentsList)

                tvUserApps.clear()
                tvUserApps.addAll(tvAppsList)

                mobileUserApps.clear()
                mobileUserApps.addAll(mobileAppsList)

                systemApps.clear()
                systemApps.addAll(systemAppsList)

                frozenApps.clear()
                frozenApps.addAll(frozenAppsList)

                progressBar.visibility = View.GONE

                setupFilterTabs()
                filterAndDisplayApps()

                if (displayedApps.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    vgvApps.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    vgvApps.visibility = View.VISIBLE
                    if (!isMultiSelectMode) {
                        vgvApps.requestFocus()
                        vgvApps.scrollToPosition(0)
                    }
                }
            }
        }
    }

    private fun isHeaderFocused(): Boolean {
        return btnSettings.hasFocus() || btnHdmi.hasFocus() ||
                btnBatchAction.hasFocus() || btnCancelSelect.hasFocus() ||
                chipGroupCategories.hasFocus()
    }

    private data class TabData(val category: Category, val title: String)

    private fun setupFilterTabs() {
        chipGroupCategories.removeAllViews()

        val tabs = ArrayList<TabData>()
        tabs.add(TabData(Category.ALL, getString(R.string.tab_all, allApps.size)))
        if (recentApps.isNotEmpty()) {
            tabs.add(TabData(Category.RECENT, getString(R.string.tab_recent, recentApps.size)))
        }
        if (tvUserApps.isNotEmpty()) {
            tabs.add(TabData(Category.TV, getString(R.string.tab_tv, tvUserApps.size)))
        }
        if (mobileUserApps.isNotEmpty()) {
            tabs.add(TabData(Category.MOBILE, getString(R.string.tab_mobile, mobileUserApps.size)))
        }
        if (systemApps.isNotEmpty()) {
            tabs.add(TabData(Category.SYSTEM, getString(R.string.tab_system, systemApps.size)))
        }
        if (frozenApps.isNotEmpty()) {
            tabs.add(TabData(Category.FROZEN, getString(R.string.tab_frozen, frozenApps.size)))
        }

        for (tab in tabs) {
            val chip = Chip(this).apply {
                text = tab.title
                isCheckable = true
                isClickable = true
                isFocusable = true
                isChecked = (tab.category == currentCategory)
                chipBackgroundColor = ContextCompat.getColorStateList(this@AppListActivity, R.color.tv_chip_bg_color)
                chipStrokeColor = ContextCompat.getColorStateList(this@AppListActivity, R.color.tv_chip_stroke_color)
                chipStrokeWidth = dpToPx(3).toFloat()
                setTextColor(ContextCompat.getColorStateList(this@AppListActivity, R.color.tv_chip_text_color))
                setOnClickListener {
                    selectCategory(tab.category)
                }
            }
            chipGroupCategories.addView(chip)
        }
    }

    private fun selectCategory(cat: Category) {
        currentCategory = cat
        for (i in 0 until chipGroupCategories.childCount) {
            val child = chipGroupCategories.getChildAt(i) as? Chip ?: continue
            val tabData = child.text.toString()
            val matches = when (cat) {
                Category.ALL -> tabData.startsWith(getString(R.string.tab_all, 0).substringBefore("("))
                Category.RECENT -> tabData.startsWith(getString(R.string.tab_recent, 0).substringBefore("("))
                Category.TV -> tabData.startsWith(getString(R.string.tab_tv, 0).substringBefore("("))
                Category.MOBILE -> tabData.startsWith(getString(R.string.tab_mobile, 0).substringBefore("("))
                Category.SYSTEM -> tabData.startsWith(getString(R.string.tab_system, 0).substringBefore("("))
                Category.FROZEN -> tabData.startsWith(getString(R.string.tab_frozen, 0).substringBefore("("))
            }
            child.isChecked = matches
        }
        filterAndDisplayApps()
    }

    private fun filterAndDisplayApps() {
        displayedApps.clear()
        when (currentCategory) {
            Category.ALL -> displayedApps.addAll(allApps)
            Category.RECENT -> displayedApps.addAll(recentApps)
            Category.TV -> displayedApps.addAll(tvUserApps)
            Category.MOBILE -> displayedApps.addAll(mobileUserApps)
            Category.SYSTEM -> displayedApps.addAll(systemApps)
            Category.FROZEN -> displayedApps.addAll(frozenApps)
        }

        adapter.notifyDataSetChanged()

        if (displayedApps.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            vgvApps.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            vgvApps.visibility = View.VISIBLE
            vgvApps.post { vgvApps.scrollToPosition(0) }
        }
    }

    private fun loadIconAsync(app: AppItem) {
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
                    loadingIcons.remove(pkg)
                }
            }
        }
    }

    private fun updateVisibleRowIcon(pkg: String, icon: Drawable) {
        for (i in 0 until vgvApps.childCount) {
            val child = vgvApps.getChildAt(i) ?: continue
            val holder = vgvApps.getChildViewHolder(child) as? AppCardViewHolder ?: continue
            if (holder.boundPackage == pkg) {
                holder.ivIcon.clearColorFilter()
                holder.ivIcon.setImageDrawable(icon)
            }
        }
    }

    private fun launchApp(app: AppItem) {
        cancelAutoOpenCountdown()
        if (!app.appInfo.enabled) {
            Toast.makeText(this, getString(R.string.toast_unfreeze_first, app.label), Toast.LENGTH_SHORT).show()
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

    private data class MenuOption(
        val title: String,
        val action: () -> Unit
    )

    private fun setAutoOpenSelection(pkg: String, label: String) {
        SettingsRepository.setAutoOpenApp(this, pkg, label)
        if (pkg.isNotBlank() && !SettingsRepository.isAppModeEnabled(this)) {
            SettingsRepository.setAppModeEnabled(this, true)
        }
        adapter.notifyDataSetChanged()
        if (pkg.isBlank()) {
            cancelAutoOpenCountdown()
        }
    }

    private fun showAppMenu(app: AppItem) {
        val isCurrentlyAutoOpen = SettingsRepository.getAutoOpenPackage(this) == app.packageName
        val options = ArrayList<MenuOption>(6).apply {
            add(MenuOption(getString(R.string.menu_open)) {
                launchApp(app)
            })
            add(MenuOption(getString(R.string.menu_batch_select)) {
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
                    add(MenuOption(getString(R.string.menu_freeze)) {
                        toggleAppFreeze(app, true)
                    })
                } else {
                    add(MenuOption(getString(R.string.menu_unfreeze)) {
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

    private fun uninstallApp(app: AppItem) {
        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
    }

    private fun toggleAppFreeze(app: AppItem, freeze: Boolean) {
        val success = ShizukuHelper.setAppDisabled(app.packageName, freeze)
        if (success) {
            val action = if (freeze) getString(R.string.action_disable) else getString(R.string.action_enable)
            Toast.makeText(this, getString(R.string.toast_freeze_success, action, app.label), Toast.LENGTH_SHORT).show()
            loadApps()
        } else {
            Toast.makeText(this, getString(R.string.toast_shizuku_fail), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ShizukuSettingsActivity::class.java))
        }
    }

    private fun openAppInfo(app: AppItem) {
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
            tvTitle.text = getString(R.string.title_selected_count, selectedPackages.size)
            tvHint.visibility = View.GONE
            btnSettings.visibility = View.GONE
            btnHdmi.visibility = View.GONE
            hsvCategories.visibility = View.GONE
            btnBatchAction.visibility = View.VISIBLE
            btnCancelSelect.visibility = View.VISIBLE
            btnBatchAction.requestFocus()
        } else {
            tvTitle.text = getString(R.string.app_list_title)
            tvHint.visibility = View.VISIBLE
            btnSettings.visibility = View.VISIBLE
            btnHdmi.visibility = View.VISIBLE
            hsvCategories.visibility = View.VISIBLE
            btnBatchAction.visibility = View.GONE
            btnCancelSelect.visibility = View.GONE
        }
    }

    private fun showBatchActionDialog() {
        if (selectedPackages.isEmpty()) return

        val options = ArrayList<MenuOption>()
        options.add(MenuOption(getString(R.string.menu_batch_freeze)) {
            executeBatchFreeze(true)
        })
        options.add(MenuOption(getString(R.string.menu_batch_unfreeze)) {
            executeBatchFreeze(false)
        })
        options.add(MenuOption(getString(R.string.menu_select_all)) {
            selectAllApps()
        })

        val items = options.map { it.title }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.title_batch_action, selectedPackages.size))
            .setItems(items) { _, which ->
                options[which].action()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun selectAllApps() {
        for (item in displayedApps) {
            selectedPackages.add(item.packageName)
        }
        updateHeaderUI()
        adapter.notifyDataSetChanged()
    }

    private fun executeBatchFreeze(freeze: Boolean) {
        progressBar.visibility = View.VISIBLE
        vgvApps.visibility = View.GONE
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
                val action = if (freeze) getString(R.string.action_disable_upper) else getString(R.string.action_enable_upper)
                val successStr = getString(R.string.toast_batch_success, action, successCount)
                val failStr = if (failCount > 0) getString(R.string.toast_batch_fail, failCount) else ""
                Toast.makeText(
                    this@AppListActivity,
                    successStr + failStr,
                    Toast.LENGTH_SHORT
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
                if (isMultiSelectMode) {
                    showBatchActionDialog()
                } else {
                    val focusedChild = vgvApps.focusedChild
                    val holder = focusedChild?.let { vgvApps.findContainingViewHolder(it) } as? AppCardViewHolder
                    if (holder != null && holder.currentApp != null) {
                        showAppMenu(holder.currentApp!!)
                    } else {
                        openSettings()
                    }
                }
                return true
            }

            // 遙控器向上切換至標題/分類標籤
            if (vgvApps.hasFocus() && event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                val focusedChild = vgvApps.focusedChild
                val position = focusedChild?.let { vgvApps.getChildAdapterPosition(it) } ?: -1
                if (position in 0..4) { // 第一列
                    if (isMultiSelectMode) {
                        btnBatchAction.requestFocus()
                    } else if (chipGroupCategories.childCount > 0) {
                        var chipFocused = false
                        for (i in 0 until chipGroupCategories.childCount) {
                            val chip = chipGroupCategories.getChildAt(i) as? Chip
                            if (chip?.isChecked == true) {
                                chip.requestFocus()
                                chipFocused = true
                                break
                            }
                        }
                        if (!chipFocused) chipGroupCategories.getChildAt(0)?.requestFocus()
                    } else {
                        btnSettings.requestFocus()
                    }
                    return true
                }
            }

            // 遙控器從分類標籤向上切換至設定按鈕
            if (chipGroupCategories.hasFocus() && event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                btnSettings.requestFocus()
                return true
            }

            // 遙控器從設定/HDMI按鈕向下切換至分類標籤
            if ((btnSettings.hasFocus() || btnHdmi.hasFocus()) && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (chipGroupCategories.childCount > 0) {
                    var chipFocused = false
                    for (i in 0 until chipGroupCategories.childCount) {
                        val chip = chipGroupCategories.getChildAt(i) as? Chip
                        if (chip?.isChecked == true) {
                            chip.requestFocus()
                            chipFocused = true
                            break
                        }
                    }
                    if (!chipFocused) chipGroupCategories.getChildAt(0)?.requestFocus()
                    return true
                }
            }

            // 遙控器向下切換回網格
            if (isHeaderFocused() && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (displayedApps.isNotEmpty()) {
                    vgvApps.requestFocus()
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
            if (currentCategory != Category.ALL) {
                selectCategory(Category.ALL)
                return true
            }
            returnToMainActivity()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    inner class AppCardAdapter : RecyclerView.Adapter<AppCardViewHolder>() {
        override fun getItemCount(): Int = displayedApps.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppCardViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tv_app_card, parent, false)
            return AppCardViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppCardViewHolder, position: Int) {
            val app = displayedApps[position]
            holder.bind(app)
        }
    }

    inner class AppCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.card_view)
        val ivIcon: ImageView = itemView.findViewById(R.id.iv_app_icon)
        val tvName: TextView = itemView.findViewById(R.id.tv_app_name)
        val tvBadge: TextView = itemView.findViewById(R.id.tv_badge)
        val cbSelect: CheckBox = itemView.findViewById(R.id.cb_select)
        val ivDisabledBadge: ImageView = itemView.findViewById(R.id.iv_disabled_badge)

        var currentApp: AppItem? = null
        var boundPackage: String = ""

        fun bind(app: AppItem) {
            currentApp = app
            boundPackage = app.packageName

            if (app.appInfo.enabled) {
                tvName.text = app.label
                tvName.setTextColor(Color.WHITE)
                ivIcon.alpha = 1.0f
                ivDisabledBadge.visibility = View.GONE
            } else {
                tvName.text = getString(R.string.app_disabled_suffix, app.label)
                tvName.setTextColor(0xFF64748B.toInt())
                ivIcon.alpha = 0.5f
                ivDisabledBadge.visibility = View.VISIBLE
            }

            // 開機自動啟動標記
            val isAutoOpen = SettingsRepository.getAutoOpenPackage(this@AppListActivity) == app.packageName
            tvBadge.visibility = if (isAutoOpen) View.VISIBLE else View.GONE

            // 批次選取狀態
            if (isMultiSelectMode) {
                cbSelect.visibility = View.VISIBLE
                val isSelected = selectedPackages.contains(app.packageName)
                cbSelect.isChecked = isSelected
                if (isSelected) {
                    cardView.strokeColor = 0xFF38BDF8.toInt()
                } else {
                    cardView.setStrokeColor(ContextCompat.getColorStateList(this@AppListActivity, R.color.tv_card_stroke_color))
                }
            } else {
                cbSelect.visibility = View.GONE
                cardView.setStrokeColor(ContextCompat.getColorStateList(this@AppListActivity, R.color.tv_card_stroke_color))
            }

            // 圖示異步載入
            val cached = iconCache[app.packageName]
            if (cached != null) {
                ivIcon.clearColorFilter()
                ivIcon.setImageDrawable(cached)
            } else {
                ivIcon.setImageResource(R.drawable.apps_48px)
                ivIcon.setColorFilter(0xFF334155.toInt())
                loadIconAsync(app)
            }

            cardView.setOnClickListener {
                cancelAutoOpenCountdown()
                if (isMultiSelectMode) {
                    toggleSelection(app.packageName)
                } else {
                    launchApp(app)
                }
            }

            cardView.setOnLongClickListener {
                cancelAutoOpenCountdown()
                if (isMultiSelectMode) {
                    showBatchActionDialog()
                } else {
                    showAppMenu(app)
                }
                true
            }
        }
    }
}
