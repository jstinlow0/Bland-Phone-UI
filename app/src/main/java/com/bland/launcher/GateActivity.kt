package com.bland.launcher

import android.app.Activity
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView

/** A short, unskippable pause before opening an app the user marked as distracting. */
class GateActivity : Activity() {
    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_LABEL = "label"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timer: TextView
    private lateinit var openBtn: TextView
    private var remaining = 0
    private var ready = false
    private var component: ComponentName? = null

    private val tick = object : Runnable {
        override fun run() {
            if (remaining > 0) {
                timer.text = remaining.toString()
                remaining--
                handler.postDelayed(this, 1000)
            } else {
                timer.text = "·"
                ready = true
                openBtn.setTextColor(Ui.WHITE)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gate)

        component = intent.getStringExtra(EXTRA_KEY)?.let { ComponentName.unflattenFromString(it) }
        val cn = component ?: run { finish(); return }
        val label = intent.getStringExtra(EXTRA_LABEL) ?: cn.packageName

        timer = findViewById(R.id.timer)
        openBtn = findViewById(R.id.open)
        findViewById<TextView>(R.id.label).text = label.lowercase()

        val opened = Usage.count(this, cn.packageName)
        findViewById<TextView>(R.id.count).text = when (opened) {
            0 -> "not opened yet today"
            1 -> "opened once today"
            else -> "opened $opened times today"
        }

        findViewById<TextView>(R.id.cancel).setOnClickListener { finish() }
        openBtn.setOnClickListener {
            if (ready) {
                Launcher.launchNow(this, cn)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Leaving and coming back restarts the pause.
        remaining = Prefs.delaySeconds(this)
        ready = false
        openBtn.setTextColor(Ui.DIM)
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }
}
