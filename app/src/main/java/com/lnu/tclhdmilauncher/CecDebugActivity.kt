package com.lnu.tclhdmilauncher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** On-TV view of the CEC events observed by this app. */
class CecDebugActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var events: TextView
    private val refresh = object : Runnable {
        override fun run() {
            // Avoid interrupting active text selection
            if (!events.hasSelection()) {
                val newText = CecDebugLog.read(this@CecDebugActivity)
                if (events.text.toString() != newText) {
                    events.text = newText
                }
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply {
            text = "CEC debug — last 60 events"
            setTextColor(Color.WHITE)
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "CEC override: ${if (TclHdmiApplication.isCecInputOverrideActive()) "ACTIVE" else "idle"}"
            setTextColor(Color.LTGRAY)
            textSize = 15f
        })
        events = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setTextIsSelectable(true)
        }
        root.addView(ScrollView(this).apply { addView(events) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.END
            addView(Button(this@CecDebugActivity).apply {
                text = "Copy"
                setOnClickListener {
                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("CEC Debug Log", events.text)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(this@CecDebugActivity, "Log copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                }
            })
            addView(Button(this@CecDebugActivity).apply {
                text = "Clear"
                setOnClickListener { CecDebugLog.clear(this@CecDebugActivity) }
            })
            addView(Button(this@CecDebugActivity).apply {
                text = "Close"
                setOnClickListener { finish() }
            })
        })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
}
