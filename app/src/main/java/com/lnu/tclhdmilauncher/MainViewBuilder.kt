package com.lnu.tclhdmilauncher

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainViewBuilder(private val activity: MainActivity) {

    fun build(): View {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density + 0.5f).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            val padH = dp(36f)
            val padV = dp(20f)
            setPadding(padH, padV, padH, padV)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
        }

        val topBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val ivBrand = ImageView(activity).apply {
            val d = activity.getDrawable(R.drawable.cable_48px)?.mutate()
            setImageDrawable(d)
            setColorFilter(0xFF64748B.toInt())
        }
        topBar.addView(ivBrand, LinearLayout.LayoutParams(dp(22f), dp(22f)).apply {
            rightMargin = dp(8f)
        })

        val tvBrand = TextView(activity).apply {
            text = DeviceHelper.getBrandTitle(activity)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setTextColor(0xFF64748B.toInt())
        }
        topBar.addView(tvBrand, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        val spacerTop = View(activity)
        topBar.addView(spacerTop, LinearLayout.LayoutParams(0, 0, 1f))

        activity.btnSettings = createPillButton(
            iconRes = R.drawable.settings_48px,
            label = activity.getString(R.string.btn_settings),
            density = density
        ).first
        topBar.addView(activity.btnSettings, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        root.addView(topBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val centerContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }

        val tvTitle = TextView(activity).apply {
            text = activity.getString(R.string.main_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFF8FAFC.toInt())
        }
        centerContainer.addView(tvTitle, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            bottomMargin = dp(10f)
        })

        activity.tvCountdown = TextView(activity).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(0xFF94A3B8.toInt())
            val hPad = dp(18f)
            val vPad = dp(6f)
            setPadding(hPad, vPad, hPad, vPad)
            isClickable = true
            isFocusable = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16f).toFloat()
                setColor(0xFF14161A.toInt())
                setStroke(dp(1f), 0xFF272A30.toInt())
            }
            setOnClickListener(activity)
        }
        centerContainer.addView(activity.tvCountdown, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            bottomMargin = dp(28f)
        })

        val rowCards = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }
        val cardWidth = dp(210f)
        val cardHeight = dp(136f)
        val cardMargin = dp(14f)

        val (c1, iv1, b1) = createHdmiCard(1, density)
        val (c2, iv2, b2) = createHdmiCard(2, density)
        val (c3, iv3, b3) = createHdmiCard(3, density)

        activity.cardHdmi1 = c1; activity.ivIcon1 = iv1; activity.tvBadge1 = b1
        activity.cardHdmi2 = c2; activity.ivIcon2 = iv2; activity.tvBadge2 = b2
        activity.cardHdmi3 = c3; activity.ivIcon3 = iv3; activity.tvBadge3 = b3

        for (card in arrayOf(activity.cardHdmi1, activity.cardHdmi2, activity.cardHdmi3)) {
            rowCards.addView(card, LinearLayout.LayoutParams(cardWidth, cardHeight).apply {
                setMargins(cardMargin, 0, cardMargin, 0)
            })
        }
        centerContainer.addView(rowCards, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        activity.btnApps = createPillButton(
            iconRes = R.drawable.apps_48px,
            label = activity.getString(R.string.btn_apps),
            density = density
        ).first
        centerContainer.addView(activity.btnApps, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            topMargin = dp(26f)
        })

        root.addView(centerContainer, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val tvHint = TextView(activity).apply {
            text = activity.getString(R.string.bottom_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0xFF475569.toInt())
        }
        root.addView(tvHint, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(6f)
        })

        setupFocusNavigation()

        return root
    }

    private fun createHdmiCard(port: Int, density: Float): Triple<LinearLayout, ImageView, TextView> {
        fun dp(v: Float): Int = (v * density + 0.5f).toInt()

        val ivIcon = ImageView(activity).apply {
            val d = activity.getDrawable(R.drawable.settings_input_hdmi_24px)?.mutate()
            setImageDrawable(d)
            setColorFilter(0xFF94A3B8.toInt())
        }

        val tvTitle = TextView(activity).apply {
            text = "HDMI $port"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        val tvBadge = TextView(activity).apply {
            text = activity.getString(R.string.card_default_badge)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF38BDF8.toInt())
            visibility = View.INVISIBLE
        }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val vPad = dp(14f)
            val hPad = dp(16f)
            setPadding(hPad, vPad, hPad, vPad)
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            background = createCardSelector(density)

            addView(ivIcon, LinearLayout.LayoutParams(dp(36f), dp(36f)).apply {
                bottomMargin = dp(8f)
            })
            addView(tvTitle, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            addView(tvBadge, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                topMargin = dp(4f)
            })

            setOnClickListener(activity)
            onFocusChangeListener = activity
        }

        return Triple(card, ivIcon, tvBadge)
    }

    private fun createPillButton(iconRes: Int, label: String, density: Float): Triple<LinearLayout, TextView, ImageView> {
        fun dp(v: Float): Int = (v * density + 0.5f).toInt()

        val iv = ImageView(activity).apply {
            val d = activity.getDrawable(iconRes)?.mutate()
            setImageDrawable(d)
            setColorFilter(0xFF94A3B8.toInt())
        }

        val tv = TextView(activity).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFE2E8F0.toInt())
        }

        val button = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val hPad = dp(16f)
            val vPad = dp(9f)
            setPadding(hPad, vPad, hPad, vPad)
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            background = createPillSelector(density)

            addView(iv, LinearLayout.LayoutParams(dp(20f), dp(20f)).apply {
                rightMargin = dp(8f)
            })
            addView(tv, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

            setOnClickListener(activity)
            onFocusChangeListener = activity
        }

        return Triple(button, tv, iv)
    }

    private fun setupFocusNavigation() {
        val idSettings = View.generateViewId()
        val idCard1 = View.generateViewId()
        val idCard2 = View.generateViewId()
        val idCard3 = View.generateViewId()
        val idApps = View.generateViewId()

        activity.btnSettings.id = idSettings
        activity.cardHdmi1.id = idCard1
        activity.cardHdmi2.id = idCard2
        activity.cardHdmi3.id = idCard3
        activity.btnApps.id = idApps

        val defaultPort = SettingsRepository.getDefaultPort(activity)

        activity.btnSettings.nextFocusLeftId = idSettings
        activity.btnSettings.nextFocusRightId = idSettings
        activity.btnSettings.nextFocusUpId = idSettings
        activity.btnSettings.nextFocusDownId = when (defaultPort) {
            1 -> idCard1
            2 -> idCard2
            else -> idCard3
        }

        activity.cardHdmi1.nextFocusUpId = idSettings
        activity.cardHdmi1.nextFocusDownId = idApps
        activity.cardHdmi1.nextFocusLeftId = idCard1
        activity.cardHdmi1.nextFocusRightId = idCard2

        activity.cardHdmi2.nextFocusUpId = idSettings
        activity.cardHdmi2.nextFocusDownId = idApps
        activity.cardHdmi2.nextFocusLeftId = idCard1
        activity.cardHdmi2.nextFocusRightId = idCard3

        activity.cardHdmi3.nextFocusUpId = idSettings
        activity.cardHdmi3.nextFocusDownId = idApps
        activity.cardHdmi3.nextFocusLeftId = idCard2
        activity.cardHdmi3.nextFocusRightId = idCard3

        activity.btnApps.nextFocusUpId = when (defaultPort) {
            1 -> idCard1
            2 -> idCard2
            else -> idCard3
        }
        activity.btnApps.nextFocusDownId = idApps
        activity.btnApps.nextFocusLeftId = idApps
        activity.btnApps.nextFocusRightId = idApps
    }

    private fun createCardSelector(density: Float): Drawable {
        val radius = 18f * density
        val strokeFocused = (3f * density + 0.5f).toInt()
        val strokeNormal = (1.5f * density + 0.5f).toInt()

        fun rect(fillColor: Int, strokeWidth: Int, strokeColor: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fillColor)
            setStroke(strokeWidth, strokeColor)
        }

        return StateListDrawable().apply {
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
                rect(0xFF14161A.toInt(), strokeNormal, 0xFF272A30.toInt())
            )
        }
    }

    private fun createPillSelector(density: Float): Drawable {
        val radius = 24f * density
        val strokeFocused = (2.5f * density + 0.5f).toInt()
        val strokeNormal = (1.5f * density + 0.5f).toInt()

        fun rect(fillColor: Int, strokeWidth: Int, strokeColor: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fillColor)
            setStroke(strokeWidth, strokeColor)
        }

        return StateListDrawable().apply {
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
    }
}
