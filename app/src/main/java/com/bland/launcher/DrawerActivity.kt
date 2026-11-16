package com.bland.launcher

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

/** App list drawn as a looping wheel: the row in the middle is biggest, rows fade and tilt away from it. */
class DrawerActivity : Activity() {
    companion object { private const val LOOP = 200_000 }

    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var gestures: GestureDetector
    private val adapter = WheelAdapter()
    private var all: List<AppEntry> = emptyList()
    private var rowH = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawer)
        search = findViewById(R.id.search)
        list = findViewById(R.id.list)
        rowH = (Ui.dp(this, 56) * Prefs.textScale(this)).toInt()
        search.typeface = Ui.face(this)
        list.adapter = adapter
        list.clipToPadding = false
        list.isVerticalFadingEdgeEnabled = false

        list.setOnItemClickListener { _, _, pos, _ -> open(adapter.getItem(pos)) }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            AppOptions.show(this, adapter.getItem(pos)) { refresh(keepPlace = true) }
            true
        }
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, state: Int) {
                if (state == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) snap()
            }
            override fun onScroll(view: AbsListView, first: Int, visible: Int, total: Int) = shape()
        })

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = filter(keepPlace = false)
        })
        search.setOnEditorActionListener { _, actionId, _ ->
            val center = centerPosition()
            if (actionId == EditorInfo.IME_ACTION_GO && center >= 0) { open(adapter.getItem(center)); true } else false
        }

        // A sideways swipe closes the list.
        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                if (abs(dx) > Ui.dp(this@DrawerActivity, 100) && abs(dx) > 2 * abs(e2.y - e1.y)) { finish(); return true }
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
        refresh(keepPlace = false)
        search.requestFocus()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    private fun open(app: AppEntry) {
        Launcher.open(this, app)
        finish()
    }

    private fun refresh(keepPlace: Boolean) {
        all = Apps.load(this)
        filter(keepPlace)
    }

    private fun filter(keepPlace: Boolean) {
        val q = search.text.toString().trim().lowercase()
        val hidden = Prefs.hidden(this)
        val showHidden = Prefs.showHidden(this)
        var items = all.filter { (showHidden || it.key !in hidden) && (q.isEmpty() || q in it.name) }
        if (q.isNotEmpty()) items = items.sortedBy { if (it.name.startsWith(q)) 0 else 1 }

        val keep = if (keepPlace) list.firstVisiblePosition else -1
        val keepTop = list.getChildAt(0)?.top ?: 0
        adapter.items = items
        adapter.loop = items.size > 6
        adapter.hidden = hidden
        adapter.paused = Prefs.distracting(this)
        adapter.notifyDataSetChanged()

        list.post {
            val pad = list.height / 2 - rowH / 2
            list.setPadding(0, pad, 0, pad)
            if (keep >= 0 && keep < adapter.count) {
                list.setSelectionFromTop(keep, keepTop)
            } else {
                val start = if (adapter.loop) LOOP / 2 - (LOOP / 2) % items.size else 0
                list.setSelectionFromTop(start, pad)
            }
            list.post { shape() }
        }
    }

    private fun centerPosition(): Int {
        if (list.childCount == 0) return -1
        val mid = list.height / 2
        var best = -1
        var bestD = Int.MAX_VALUE
        for (i in 0 until list.childCount) {
            val v = list.getChildAt(i)
            val d = abs((v.top + v.bottom) / 2 - mid)
            if (d < bestD) { bestD = d; best = list.firstVisiblePosition + i }
        }
        return best
    }

    /** Scale, fade and tilt each row by its distance from the middle. */
    private fun shape() {
        if (rowH == 0) return
        val mid = list.height / 2f
        val align = Prefs.appAlign(this)
        for (i in 0 until list.childCount) {
            val v = list.getChildAt(i)
            val d = ((v.top + v.bottom) / 2f - mid) / rowH
            val ad = abs(d)
            val s = 1f - min(ad, 5f) * 0.07f
            v.pivotX = when (align) { "center" -> v.width / 2f; "right" -> v.width.toFloat(); else -> 0f }
            v.pivotY = v.height / 2f
            v.scaleX = s
            v.scaleY = s
            v.alpha = (1f - ad * 0.2f).coerceAtLeast(0.08f)
            v.cameraDistance = 8000f * resources.displayMetrics.density
            v.rotationX = -d * 11f
        }
    }

    private fun snap() {
        if (list.childCount == 0) return
        val mid = list.height / 2
        var best: View? = null
        for (i in 0 until list.childCount) {
            val v = list.getChildAt(i)
            if (best == null || abs((v.top + v.bottom) / 2 - mid) < abs((best.top + best.bottom) / 2 - mid)) best = v
        }
        val delta = best?.let { (it.top + it.bottom) / 2 - mid } ?: 0
        if (abs(delta) > 2) list.smoothScrollBy(delta, 180)
    }

    private inner class WheelAdapter : BaseAdapter() {
        var items: List<AppEntry> = emptyList()
        var loop = true
        var hidden: Set<String> = emptySet()
        var paused: Set<String> = emptySet()

        override fun getCount() = if (items.isEmpty()) 0 else if (loop) LOOP else items.size
        override fun getItem(position: Int) = items[position % items.size]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: Ui.row(this@DrawerActivity, "", 24f).apply {
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH)
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER_VERTICAL or Ui.gravity(Prefs.appAlign(this@DrawerActivity))
            }
            val app = getItem(position)
            tv.text = app.name
            val locked = app.pkg in paused && Launcher.isLocked(this@DrawerActivity, app.pkg)
            tv.paintFlags = if (locked) tv.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            else tv.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            tv.setTextColor(
                when {
                    locked || app.key in hidden -> Ui.DIM
                    app.pkg in paused -> Ui.GRAY
                    else -> Ui.WHITE
                }
            )
            return tv
        }
    }
}
