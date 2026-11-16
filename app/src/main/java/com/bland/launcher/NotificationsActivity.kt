package com.bland.launcher

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.abs

/** Bland's own notification list: new ones first, then the ones held quietly. */
class NotificationsActivity : Activity() {
    private data class Item(val key: String, val pkg: String, val title: String, val text: String, val time: Long, val intent: PendingIntent?, val held: Boolean)

    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var sub: android.widget.TextView
    private lateinit var gestures: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = Ui.dp(this, 32)
        val root = Ui.vbox(this).apply {
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            setPadding(pad, Ui.dp(this@NotificationsActivity, 40), pad, Ui.dp(this@NotificationsActivity, 8))
        }
        root.addView(Ui.row(this, "notifications", 30f))
        sub = Ui.row(this, "", 14f, Ui.GRAY).apply { setPadding(0, 0, 0, Ui.dp(this@NotificationsActivity, 8)) }
        root.addView(sub)
        list = Ui.vbox(this)
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val actions = LinearLayout(this)
        actions.addView(Ui.row(this, "clear all", 15f, Ui.GRAY).apply { setOnClickListener { clearAll() } })
        actions.addView(Ui.spacer(this))
        actions.addView(Ui.row(this, "choose what reaches you", 15f, Ui.GRAY).apply {
            setOnClickListener { startActivity(Intent(this@NotificationsActivity, SettingsActivity::class.java).putExtra("page", "notifs")) }
        })
        root.addView(actions, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val atBottom = !scroll.canScrollVertically(1)
                if (dy < -Ui.dp(this@NotificationsActivity, 80) && abs(dy) > abs(e2.x - e1.x) && atBottom) { finish(); return true }
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
        BlandNotificationService.instance?.sweep()
        render()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    private fun hasAccess(): Boolean =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(ComponentName(this, BlandNotificationService::class.java).flattenToString()) == true

    private fun render() {
        list.removeAllViews()
        if (!hasAccess()) {
            sub.text = "bland needs permission to sort your notifications"
            list.addView(Ui.row(this, "allow notification access", 20f).apply {
                setOnClickListener { Launcher.tryStart(this@NotificationsActivity, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            })
            list.addView(Ui.row(this, "this lets bland show, hold or block each app's notifications. nothing leaves your phone.", 14f, Ui.DIM).apply { isSingleLine = false })
            return
        }

        val service = BlandNotificationService.instance
        val now = (try { service?.activeNotifications } catch (e: Exception) { null }).orEmpty()
            .filter { BlandNotificationService.relevant(this, it) }
            .sortedByDescending { it.postTime }
            .map { Item(it.key, it.packageName, BlandNotificationService.title(it.notification), BlandNotificationService.text(it.notification), it.postTime, it.notification.contentIntent, false) }
        val held = BlandNotificationService.held(this)
            .map { Item(it.key, it.pkg, it.title, it.text, it.time, BlandNotificationService.intents[it.key], true) }

        sub.text = "${now.size} new · ${held.size} held · ${Usage.blocked(this)} blocked today"
        if (now.isEmpty() && held.isEmpty()) {
            section("all clear")
            list.addView(Ui.row(this, "nothing needs you right now.", 17f, Ui.DIM))
            return
        }
        if (now.isNotEmpty()) { section("new"); now.forEach { list.addView(row(it)) } }
        if (held.isNotEmpty()) { section("held quietly"); held.forEach { list.addView(row(it)) } }
        list.addView(Ui.row(this, "hold any notification to change what that app can send you.", 14f, Ui.DIM).apply {
            isSingleLine = false; setPadding(0, Ui.dp(this@NotificationsActivity, 14), 0, 0)
        })
    }

    private fun section(text: String) {
        list.addView(Ui.row(this, text.uppercase(), 12f, Ui.DIM, scaled = false).apply {
            letterSpacing = 0.1f
            setPadding(0, Ui.dp(this@NotificationsActivity, 18), 0, Ui.dp(this@NotificationsActivity, 4))
        })
    }

    private fun row(item: Item): View {
        val box = Ui.vbox(this).apply { setPadding(0, Ui.dp(this@NotificationsActivity, 10), 0, Ui.dp(this@NotificationsActivity, 10)) }
        val head = LinearLayout(this)
        head.addView(Ui.row(this, Apps.label(this, item.pkg).lowercase(), 12f, Ui.GRAY, scaled = false).apply { setPadding(0, 0, 0, 0) })
        head.addView(Ui.spacer(this))
        head.addView(Ui.row(this, DateUtils.getRelativeTimeSpanString(item.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase(),
            12f, Ui.GRAY, scaled = false).apply { setPadding(0, 0, 0, 0) })
        box.addView(head, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        if (item.title.isNotEmpty()) box.addView(Ui.row(this, item.title, 17f, if (item.held) Ui.GRAY else Ui.WHITE).apply { setPadding(0, Ui.dp(this@NotificationsActivity, 3), 0, 0) })
        if (item.text.isNotEmpty()) box.addView(Ui.row(this, item.text, 15f, if (item.held) Ui.DIM else Ui.GRAY).apply {
            setPadding(0, Ui.dp(this@NotificationsActivity, 2), 0, 0); isSingleLine = false; maxLines = 2
        })
        box.setOnClickListener { openItem(item) }
        box.setOnLongClickListener { Notif.menu(this, item.pkg) { BlandNotificationService.instance?.sweep(); render() }; true }
        return box
    }

    private fun openItem(item: Item) {
        val cn = Launcher.componentFor(this, item.pkg) ?: return
        val direct: (() -> Unit)? = item.intent?.let { pi -> { try { pi.send() } catch (e: Exception) { Launcher.tryStart(this, Intent().setComponent(cn)) }; Unit } }
        val opened = Launcher.openComponent(this, cn, Apps.label(this, item.pkg), direct)
        if (opened) {
            if (item.held) BlandNotificationService.removeHeld(this, item.key)
            else try { BlandNotificationService.instance?.cancelNotification(item.key) } catch (e: Exception) { }
            finish()
        }
    }

    private fun clearAll() {
        try { BlandNotificationService.instance?.cancelAllNotifications() } catch (e: Exception) { }
        BlandNotificationService.clearHeld(this)
        render()
    }
}
