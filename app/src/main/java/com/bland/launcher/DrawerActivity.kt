package com.bland.launcher

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import kotlin.math.abs

class DrawerActivity : Activity() {
    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var gestures: GestureDetector
    private val adapter = AppAdapter()
    private var all: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawer)
        search = findViewById(R.id.search)
        list = findViewById(R.id.list)
        list.adapter = adapter

        list.setOnItemClickListener { _, _, pos, _ -> open(adapter.getItem(pos)) }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            AppOptions.show(this, adapter.getItem(pos)) { refresh() }
            true
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = filter()
        })
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO && adapter.count > 0) {
                open(adapter.getItem(0)); true
            } else false
        }

        // Swipe down at the top of the list closes the drawer.
        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                if (dy > Ui.dp(this@DrawerActivity, 80) && abs(dy) > abs(e2.x - e1.x) && atTop()) {
                    finish(); return true
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
        refresh()
        search.requestFocus()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    private fun atTop(): Boolean =
        list.firstVisiblePosition == 0 && (list.getChildAt(0)?.top ?: 0) >= 0

    private fun open(app: AppEntry) {
        Launcher.open(this, app)
        finish()
    }

    private fun refresh() {
        all = Apps.load(this)
        filter()
    }

    private fun filter() {
        val q = search.text.toString().trim().lowercase()
        val hidden = Prefs.hidden(this)
        val showHidden = Prefs.showHidden(this)
        var items = all.filter { (showHidden || it.key !in hidden) && (q.isEmpty() || q in it.name) }
        if (q.isNotEmpty()) items = items.sortedBy { if (it.name.startsWith(q)) 0 else 1 }
        adapter.items = items
        adapter.hidden = hidden
        adapter.paused = Prefs.distracting(this)
        adapter.notifyDataSetChanged()
    }

    private inner class AppAdapter : BaseAdapter() {
        var items: List<AppEntry> = emptyList()
        var hidden: Set<String> = emptySet()
        var paused: Set<String> = emptySet()

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: Ui.row(this@DrawerActivity, "", 22f)
            val app = items[position]
            tv.text = app.name
            // Paused apps fade back a little; hidden ones further.
            tv.setTextColor(
                when {
                    app.key in hidden -> Ui.DIM
                    app.pkg in paused -> Ui.GRAY
                    else -> Ui.WHITE
                }
            )
            return tv
        }
    }
}
