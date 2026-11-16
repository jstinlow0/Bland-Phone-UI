package com.bland.launcher

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar
import kotlin.math.abs

/** Black-and-white month view. Swipe left or right to change month, up or down to go home. */
class CalendarActivity : Activity() {
    private data class Event(val time: String, val title: String)

    private lateinit var month: TextView
    private lateinit var year: TextView
    private lateinit var grid: LinearLayout
    private lateinit var events: LinearLayout
    private lateinit var gestures: GestureDetector
    private var offset = 0
    private var selected: Calendar = Calendar.getInstance()
    private var byDay: Map<String, List<Event>> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = Ui.dp(this, 32)
        val root = Ui.vbox(this).apply {
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            setPadding(pad, Ui.dp(this@CalendarActivity, 40), pad, Ui.dp(this@CalendarActivity, 12))
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        month = Ui.row(this, "", 30f)
        year = Ui.row(this, "", 16f, Ui.GRAY)
        head.addView(month)
        head.addView(Ui.spacer(this))
        head.addView(year)
        root.addView(head, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        grid = Ui.vbox(this)
        root.addView(grid, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = Ui.dp(this@CalendarActivity, 16) })

        root.addView(View(this).apply { setBackgroundColor(Ui.EDGE) },
            LinearLayout.LayoutParams(MATCH_PARENT, Ui.dp(this, 1)).apply { topMargin = Ui.dp(this@CalendarActivity, 18) })
        events = Ui.vbox(this)
        root.addView(ScrollView(this).apply { addView(events) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(Ui.row(this, "swipe left or right for other months", 13f, Ui.DIM, scaled = false).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val min = Ui.dp(this@CalendarActivity, 60)
                if (abs(dx) > abs(dy) && abs(dx) > min) { offset += if (dx < 0) 1 else -1; render(if (dx < 0) 1 else -1); return true }
                if (abs(dy) > abs(dx) && abs(dy) > min * 2) { finish(); return true }
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
        render(0)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        render(0)
    }

    private fun key(c: Calendar) = "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    private fun same(a: Calendar, b: Calendar) = key(a) == key(b)

    private fun render(dir: Int) {
        val now = Calendar.getInstance()
        val first = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, offset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        month.text = DateFormat.format("MMMM", first).toString().lowercase()
        year.text = first.get(Calendar.YEAR).toString()
        byDay = loadEvents(first)

        grid.removeAllViews()
        val dows = LinearLayout(this)
        for (d in listOf("S", "M", "T", "W", "T", "F", "S")) {
            dows.addView(Ui.row(this, d, 12f, Ui.DIM, scaled = false).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        grid.addView(dows)

        val day = (first.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -(get(Calendar.DAY_OF_WEEK) - 1)) }
        for (week in 0 until 6) {
            if (week == 5 && day.get(Calendar.MONTH) != first.get(Calendar.MONTH)) break
            val row = LinearLayout(this)
            for (i in 0 until 7) {
                row.addView(cell(day.clone() as Calendar, first, now), LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f))
                day.add(Calendar.DAY_OF_MONTH, 1)
            }
            grid.addView(row)
        }
        renderEvents(now)

        if (dir != 0) {
            grid.translationX = dir * Ui.dpf(this, 40f)
            grid.alpha = 0f
            grid.animate().translationX(0f).alpha(1f).setDuration(220).start()
        }
    }

    private fun cell(d: Calendar, first: Calendar, now: Calendar): View {
        val inMonth = d.get(Calendar.MONTH) == first.get(Calendar.MONTH)
        val isToday = same(d, now)
        val isSel = same(d, selected)
        val box = Ui.vbox(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        val size = Ui.dp(this, 34)
        val num = TextView(this).apply {
            text = d.get(Calendar.DAY_OF_MONTH).toString()
            gravity = Gravity.CENTER
            textSize = 16f * Prefs.textScale(this@CalendarActivity).coerceAtMost(1.15f)
            typeface = Ui.face(this@CalendarActivity)
            setTextColor(if (isToday) Color.BLACK else if (inMonth) Color.WHITE else Ui.EDGE)
            if (isToday || isSel) background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (isToday) setColor(Color.WHITE) else setStroke(Ui.dp(this@CalendarActivity, 1), Color.WHITE)
            }
        }
        box.addView(num, LinearLayout.LayoutParams(size, size))
        val dot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.GRAY) }
            visibility = if (byDay[key(d)].isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
        }
        box.addView(dot, LinearLayout.LayoutParams(Ui.dp(this, 3), Ui.dp(this, 3)).apply { topMargin = Ui.dp(this@CalendarActivity, 2) })
        box.setOnClickListener { selected = d; render(0) }
        return box
    }

    private fun renderEvents(now: Calendar) {
        events.removeAllViews()
        val title = if (same(selected, now)) "today" else DateFormat.format("EEEE, MMM d", selected).toString().lowercase()
        events.addView(Ui.row(this, title.uppercase(), 12f, Ui.GRAY, scaled = false).apply { letterSpacing = 0.1f })

        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            events.addView(Ui.row(this, "show my events", 17f).apply {
                setOnClickListener { requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1) }
            })
            events.addView(Ui.row(this, "bland only reads your calendar to show it here.", 14f, Ui.DIM).apply { isSingleLine = false })
            return
        }
        val list = byDay[key(selected)].orEmpty()
        if (list.isEmpty()) events.addView(Ui.row(this, "nothing planned", 17f, Ui.DIM))
        for (e in list) {
            val row = LinearLayout(this)
            row.addView(Ui.row(this, e.time, 17f, Ui.GRAY), LinearLayout.LayoutParams(Ui.dp(this, 80), WRAP_CONTENT))
            row.addView(Ui.row(this, e.title.lowercase(), 17f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            events.addView(row)
        }
        events.addView(Ui.row(this, "open calendar app", 15f, Ui.DIM).apply {
            setOnClickListener {
                Launcher.tryStart(this@CalendarActivity, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
            }
        })
    }

    private fun loadEvents(first: Calendar): Map<String, List<Event>> {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyMap()
        val start = (first.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -7) }.timeInMillis
        val end = (first.clone() as Calendar).apply { add(Calendar.MONTH, 1); add(Calendar.DAY_OF_MONTH, 7) }.timeInMillis
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start); ContentUris.appendId(it, end)
        }.build()
        val out = HashMap<String, MutableList<Event>>()
        val is24 = DateFormat.is24HourFormat(this)
        try {
            contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.TITLE, CalendarContract.Instances.ALL_DAY),
                null, null, CalendarContract.Instances.BEGIN + " ASC"
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val allDay = cur.getInt(2) == 1
                    // All-day events are stored in UTC; read them in UTC so they land on the right day.
                    val c = if (allDay) Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")) else Calendar.getInstance()
                    c.timeInMillis = cur.getLong(0)
                    val local = Calendar.getInstance().apply { set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)) }
                    val time = if (allDay) "all day" else DateFormat.format(if (is24) "HH:mm" else "h:mm a", c).toString().lowercase()
                    out.getOrPut(key(local)) { mutableListOf() }.add(Event(time, cur.getString(1) ?: "(untitled)"))
                }
            }
        } catch (e: Exception) { /* calendar provider missing or refused */ }
        return out
    }
}
