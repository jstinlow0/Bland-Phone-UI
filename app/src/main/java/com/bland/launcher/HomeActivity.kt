package com.bland.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import kotlin.math.abs

class HomeActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var usage: TextView
    private lateinit var gestures: GestureDetector
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { updateTime(); handler.postDelayed(this, 5000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            setPadding(Ui.dp(this@HomeActivity, 32), Ui.dp(this@HomeActivity, 40), Ui.dp(this@HomeActivity, 32), Ui.dp(this@HomeActivity, 8))
        }
        setContentView(root)

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                val min = Ui.dp(this@HomeActivity, 60)
                when {
                    abs(dy) > abs(dx) && dy < -min -> open(DrawerActivity::class.java)
                    abs(dy) > abs(dx) && dy > min -> open(NotificationsActivity::class.java)
                    abs(dx) > abs(dy) && abs(dx) > min -> open(CalendarActivity::class.java)
                    else -> return false
                }
                return true
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        build()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    @Deprecated("Home screen ignores back.")
    override fun onBackPressed() { /* already home */ }

    private fun build() {
        root.removeAllViews()
        val align = Prefs.clockAlign(this)
        val pos = Prefs.clockPos(this)

        val clockBox = Ui.vbox(this).apply { gravity = Ui.gravity(align) }
        clock = TextView(this).apply {
            setTextColor(Color.WHITE)
            includeFontPadding = false
            gravity = Ui.gravity(align)
            setOnClickListener { Launcher.tryStart(this@HomeActivity, Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
            setOnLongClickListener { open(SettingsActivity::class.java); true }
        }
        styleClock()
        date = Ui.row(this, "", 18f, Ui.GRAY).apply {
            setPadding(0, Ui.dp(this@HomeActivity, 6), 0, 0)
            setOnClickListener { open(CalendarActivity::class.java) }
        }
        usage = Ui.row(this, "", 14f, Ui.DIM).apply { setPadding(0, Ui.dp(this@HomeActivity, 6), 0, 0) }
        clockBox.addView(clock, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        clockBox.addView(date)
        clockBox.addView(usage)

        val favs = buildFavorites()
        val sp1 = Ui.spacer(this)
        val sp2 = Ui.spacer(this)
        when (pos) {
            "middle" -> { root.addView(sp1); root.addView(clockBox); favs.setPadding(0, Ui.dp(this, 28), 0, 0); root.addView(favs); root.addView(sp2) }
            "bottom" -> { root.addView(sp1); root.addView(favs); root.addView(sp2); root.addView(clockBox) }
            else -> { root.addView(clockBox); root.addView(sp1); root.addView(favs); root.addView(sp2) }
        }

        Widgets.build(this) { build() }?.let { root.addView(it) }
        if (Prefs.showQuote(this)) root.addView(buildQuote())

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val apps = Ui.row(this, "apps", 15f, Ui.GRAY).apply {
            setPadding(0, Ui.dp(this@HomeActivity, 16), Ui.dp(this@HomeActivity, 24), Ui.dp(this@HomeActivity, 16))
            setOnClickListener { open(DrawerActivity::class.java) }
        }
        val note = Ui.row(this, "+ note", 15f, Ui.GRAY).apply {
            setPadding(Ui.dp(this@HomeActivity, 24), Ui.dp(this@HomeActivity, 16), 0, Ui.dp(this@HomeActivity, 16))
            setOnClickListener { open(NoteActivity::class.java) }
        }
        bottom.addView(apps)
        bottom.addView(Ui.spacer(this))
        bottom.addView(note)
        root.addView(bottom, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = Ui.dp(this@HomeActivity, 12) })

        updateTime()
    }

    private fun buildFavorites(): LinearLayout {
        val box = Ui.vbox(this).apply { gravity = Ui.gravity(Prefs.appAlign(this@HomeActivity)) }
        val apps = Apps.load(this).associateBy { it.key }
        val pinned = Prefs.favorites(this).mapNotNull { apps[it] }.take(Prefs.maxFavorites(this))
        if (pinned.isEmpty()) {
            box.addView(Ui.row(this, "swipe up, then long-press an app\nto add it here", 16f, Ui.DIM).apply { isSingleLine = false })
            return box
        }
        for (app in pinned) {
            val locked = Launcher.isLocked(this, app.pkg)
            val tv = Ui.row(this, app.name, 28f, if (locked) Ui.DIM else Ui.WHITE)
            tv.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8))
            tv.setOnClickListener { Launcher.open(this, app) }
            tv.setOnLongClickListener { AppOptions.show(this, app) { build() }; true }
            box.addView(tv)
        }
        return box
    }

    private fun buildQuote(): LinearLayout {
        val (text, author) = Quotes.today()
        val box = Ui.vbox(this).apply {
            gravity = Ui.gravity(Prefs.appAlign(this@HomeActivity))
            setPadding(0, Ui.dp(this@HomeActivity, 16), 0, 0)
        }
        box.addView(Ui.row(this, text, 15f, Ui.GRAY).apply {
            isSingleLine = false; maxLines = 3; setPadding(0, 0, 0, 0)
            gravity = Ui.gravity(Prefs.appAlign(this@HomeActivity))
        })
        box.addView(Ui.row(this, "— ${author.lowercase()}", 13f, Ui.DIM).apply { setPadding(0, Ui.dp(this@HomeActivity, 4), 0, 0) })
        box.setOnClickListener {
            Ui.dialog(this).setTitle("today's quote").setMessage("$text\n\n— $author")
                .setNegativeButton("close", null)
                .setPositiveButton("save to inbox") { _, _ ->
                    val note = "\"$text\" — $author"
                    if (!Notion.configured(this)) Notion.setupDialog(this) { saveQuote(note) } else saveQuote(note)
                }.show()
        }
        return box
    }

    private fun saveQuote(note: String) {
        Notion.send(this, note) { ok, message ->
            if (!ok) Outbox.add(this, note)
            Ui.toast(this, if (ok) message else "$message — kept on your phone, will retry")
        }
    }

    private fun styleClock() {
        val style = Prefs.clockStyle(this)
        clock.typeface = when (style) {
            "bold" -> Typeface.create("sans-serif", Typeface.BOLD)
            "serif" -> Typeface.SERIF
            "mono" -> Typeface.MONOSPACE
            "words" -> Ui.face(this)
            else -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
        }
        val size = when (Prefs.clockSize(this)) { "small" -> 48f; "medium" -> 64f; "huge" -> 104f; else -> 80f }
        clock.textSize = if (style == "words") size * 0.5f else size
    }

    private fun updateTime() {
        val now = Calendar.getInstance()
        val h = now.get(Calendar.HOUR_OF_DAY)
        val m = now.get(Calendar.MINUTE)
        clock.text = if (Prefs.clockStyle(this) == "words") Words.time(h, m)
        else if (DateFormat.is24HourFormat(this)) String.format("%02d:%02d", h, m)
        else String.format("%d:%02d", if (h % 12 == 0) 12 else h % 12, m)
        date.text = DateFormat.format("EEEE, MMMM d", now).toString()
        val n = Usage.total(this)
        usage.text = when (n) { 0 -> "no apps opened today"; 1 -> "1 app opened today"; else -> "$n apps opened today" }
    }

    private fun open(cls: Class<*>) {
        startActivity(Intent(this, cls))
        Launcher.fade(this)
    }
}

object Words {
    private val ones = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val tens = listOf("", "", "twenty", "thirty", "forty", "fifty")

    fun time(h24: Int, m: Int): String {
        val h = ones[h24 % 12]
        return when {
            m == 0 -> "$h o'clock"
            m < 10 -> "$h oh ${ones[m]}"
            m < 20 -> "$h ${ones[m]}"
            else -> "$h ${tens[m / 10]}" + if (m % 10 != 0) "-${ones[m % 10]}" else ""
        }
    }
}
