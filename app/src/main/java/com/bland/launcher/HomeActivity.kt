package com.bland.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class HomeActivity : Activity() {
    private lateinit var favorites: LinearLayout
    private lateinit var usage: TextView
    private lateinit var gestures: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        favorites = findViewById(R.id.favorites)
        usage = findViewById(R.id.usage)

        val clock = findViewById<View>(R.id.clock)
        clock.setOnClickListener { Launcher.tryStart(this, Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
        clock.setOnLongClickListener { openSettings(); true }

        findViewById<View>(R.id.date).setOnClickListener {
            Launcher.tryStart(this, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
        }
        findViewById<View>(R.id.hint).setOnClickListener { openDrawer() }

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                if (dy < -Ui.dp(this@HomeActivity, 60) && abs(dy) > abs(dx)) {
                    openDrawer()
                    return true
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    @Deprecated("Home screen ignores back.")
    override fun onBackPressed() { /* already home */ }

    private fun render() {
        val n = Usage.total(this)
        usage.text = when (n) {
            0 -> "no apps opened today"
            1 -> "1 app opened today"
            else -> "$n apps opened today"
        }

        val apps = Apps.load(this).associateBy { it.key }
        val pinned = Prefs.favorites(this).mapNotNull { apps[it] }.take(Prefs.maxFavorites(this))

        favorites.removeAllViews()
        if (pinned.isEmpty()) {
            favorites.addView(Ui.row(this, "swipe up, then long-press an app", 16f, Ui.DIM).apply { isSingleLine = false })
            favorites.addView(Ui.row(this, "to pin it here", 16f, Ui.DIM))
            return
        }
        for (app in pinned) {
            val tv = Ui.row(this, app.name, 28f)
            tv.setOnClickListener { Launcher.open(this, app) }
            tv.setOnLongClickListener { AppOptions.show(this, app) { render() }; true }
            favorites.addView(tv)
        }
    }

    private fun openDrawer() {
        startActivity(Intent(this, DrawerActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))
}
