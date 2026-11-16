package com.bland.launcher

import android.app.Activity
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView

/**
 * Shown before a paused app opens: a short, unskippable countdown.
 * If the app has used up today's opens it shows a lock instead, with no way through.
 */
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
    private var locked = false
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
        findViewById<TextView>(R.id.cancel).setOnClickListener { finish() }

        val limit = Prefs.limit(this)
        val opened = Usage.count(this, cn.packageName)
        locked = Launcher.isLocked(this, cn.packageName)
        val count = findViewById<TextView>(R.id.count)
        val prompt = findViewById<TextView>(R.id.prompt)

        if (locked) {
            count.text = "you've used all $limit opens for today."
            timer.text = "locked"
            timer.textSize = 28f
            prompt.text = "it unlocks at midnight.\ngo do something else."
            openBtn.visibility = View.GONE
            findViewById<TextView>(R.id.cancel).text = "ok"
            return
        }

        count.text = if (limit > 0) {
            "opened $opened of $limit times today" + if (limit - opened == 1) ". this is the last one." else ""
        } else when (opened) {
            0 -> "not opened yet today"
            1 -> "opened once today"
            else -> "opened $opened times today"
        }
        openBtn.setOnClickListener {
            if (ready) {
                Launcher.launchNow(this, cn)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (locked) return
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
