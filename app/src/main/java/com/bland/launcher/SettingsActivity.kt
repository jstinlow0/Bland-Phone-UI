package com.bland.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView

class SettingsActivity : Activity() {
    private lateinit var box: LinearLayout
    private lateinit var scroll: ScrollView
    private var page = "main"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = Ui.dp(this, 32)
        box = Ui.vbox(this).apply { setPadding(pad, Ui.dp(this@SettingsActivity, 40), pad, pad) }
        scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            isVerticalScrollBarEnabled = false
            addView(box)
        }
        setContentView(scroll)
        page = intent.getStringExtra("page") ?: "main"
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (page != "main" && intent.getStringExtra("page") == null) go("main") else super.onBackPressed()
    }

    private fun go(p: String) { page = p; render(); scroll.scrollTo(0, 0) }

    private fun render() {
        box.removeAllViews()
        when (page) {
            "clock" -> clockPage()
            "text" -> textPage()
            "home" -> homePage()
            "notifs" -> notifPage()
            "limits" -> limitsPage()
            "notion" -> notionPage()
            "widgets" -> widgetsPage()
            else -> mainPage()
        }
    }

    // --- building blocks ---
    private fun title(t: String) = box.addView(Ui.row(this, t, 32f).apply { setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 16)) })
    private fun back() = box.addView(Ui.row(this, "← settings", 15f, Ui.GRAY).apply {
        setOnClickListener { if (intent.getStringExtra("page") != null) finish() else go("main") }
    })
    private fun label(t: String) = box.addView(Ui.row(this, t.uppercase(), 12f, Ui.DIM, scaled = false).apply {
        letterSpacing = 0.1f; setPadding(0, Ui.dp(this@SettingsActivity, 18), 0, Ui.dp(this@SettingsActivity, 2))
    })
    private fun note(t: String) = box.addView(Ui.row(this, t, 14f, Ui.DIM).apply { isSingleLine = false })
    private fun <T> chips(options: List<Pair<T, String>>, current: T?, pick: (T) -> Unit) =
        box.addView(Ui.chips(this, options, current) { pick(it); render() })

    private fun item(text: String, value: String? = null, onClick: () -> Unit) {
        val row = LinearLayout(this)
        row.addView(Ui.row(this, text, 19f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (value != null) row.addView(Ui.row(this, value, 19f, Ui.GRAY).apply { setPadding(Ui.dp(this@SettingsActivity, 12), Ui.dp(this@SettingsActivity, 10), 0, Ui.dp(this@SettingsActivity, 10)) })
        row.setOnClickListener { onClick() }
        box.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    // --- pages ---
    private fun mainPage() {
        title("settings")
        item(if (isDefaultHome()) "bland is your home screen" else "set bland as home screen") {
            if (!Launcher.tryStart(this, Intent(Settings.ACTION_HOME_SETTINGS))) Launcher.tryStart(this, Intent(Settings.ACTION_SETTINGS))
        }
        item("clock", "${Prefs.clockStyle(this)} · ${Prefs.clockPos(this)}") { go("clock") }
        item("text", "${Prefs.font(this)} · ${sizeName(Prefs.textScale(this))}") { go("text") }
        item("home apps", "${Prefs.favorites(this).size.coerceAtMost(Prefs.maxFavorites(this))} of ${Prefs.maxFavorites(this)}") { go("home") }
        item("widgets", "${Prefs.widgets(this).size} on") { go("widgets") }
        item("daily quote", if (Prefs.showQuote(this)) "on" else "off") { Prefs.setShowQuote(this, !Prefs.showQuote(this)); render() }
        item("notifications") { go("notifs") }
        val limit = Prefs.limit(this)
        item("pauses & daily limits", if (limit > 0) "$limit/day" else "no limit") { go("limits") }
        item("notion second brain", if (Notion.configured(this)) "connected" else "not connected") { go("notion") }
        item("make the whole phone grayscale") { grayscaleHelp() }
        item("android settings") { Launcher.tryStart(this, Intent(Settings.ACTION_SETTINGS)) }
        note("swipe up for apps, down for notifications, left or right for the calendar. double-tap an empty spot to lock. long-press the clock to come back here.")
    }

    private fun clockPage() {
        back(); title("clock")
        label("position"); chips(listOf("top" to "top", "middle" to "middle", "bottom" to "bottom"), Prefs.clockPos(this)) { Prefs.setClock(this, "clockPos", it) }
        label("alignment"); chips(listOf("left" to "left", "center" to "center", "right" to "right"), Prefs.clockAlign(this)) { Prefs.setClock(this, "clockAlign", it) }
        label("style"); chips(listOf("thin" to "thin", "bold" to "bold", "serif" to "serif", "mono" to "mono", "words" to "in words"), Prefs.clockStyle(this)) { Prefs.setClock(this, "clockStyle", it) }
        label("size"); chips(listOf("small" to "small", "medium" to "medium", "large" to "large", "huge" to "huge"), Prefs.clockSize(this)) { Prefs.setClock(this, "clockSize", it) }
    }

    private val sizes = listOf(0.85f to "small", 1f to "normal", 1.15f to "large", 1.3f to "huge")
    private fun sizeName(v: Float) = sizes.minByOrNull { kotlin.math.abs(it.first - v) }!!.second

    private fun textPage() {
        back(); title("text")
        label("font"); chips(listOf("sans" to "sans", "serif" to "serif", "mono" to "mono", "condensed" to "condensed"), Prefs.font(this)) { Prefs.setFont(this, it) }
        label("size"); chips(sizes, sizes.minByOrNull { kotlin.math.abs(it.first - Prefs.textScale(this)) }!!.first) { Prefs.setTextScale(this, it) }
        label("app alignment"); chips(listOf("left" to "left", "center" to "center", "right" to "right"), Prefs.appAlign(this)) { Prefs.setAppAlign(this, it) }
        label("preview")
        box.addView(Ui.row(this, "messages", 28f))
    }

    private fun homePage() {
        back(); title("home apps")
        label("how many fit"); chips((3..8).map { it to it.toString() }, Prefs.maxFavorites(this)) { Prefs.setMaxFavorites(this, it) }
        label("tap to add or remove")
        val favs = Prefs.favorites(this)
        for (app in Apps.withBookmarks(this)) {
            val pos = favs.indexOf(app.key)
            item(app.name, if (pos >= 0) "#${pos + 1}" else "") {
                val cur = Prefs.favorites(this)
                if (app.key in cur) Prefs.setFavorites(this, cur - app.key)
                else if (cur.size >= Prefs.maxFavorites(this)) Ui.toast(this, "home holds ${Prefs.maxFavorites(this)} apps. less is more.")
                else Prefs.setFavorites(this, cur + app.key)
                render()
            }
        }
        note("long-press an app on the home screen to move it up or down.")
    }

    private val people = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer", "com.android.server.telecom",
        "com.whatsapp", "org.thoughtcrime.securesms", "org.telegram.messenger", "com.facebook.orca",
        "com.google.android.calendar", "com.samsung.android.calendar", "com.google.android.deskclock", "com.sec.android.app.clockpackage",
    )
    private val work = setOf("com.google.android.gm", "notion.id", "com.microsoft.office.outlook", "com.Slack", "com.google.android.apps.docs", "com.microsoft.teams")

    private fun notifPage() {
        back(); title("notifications")
        if (BlandNotificationService.instance == null) {
            item("allow notification access first") { Launcher.tryStart(this, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        val apps = Apps.load(this).distinctBy { it.pkg }
        label("one-tap setups")
        chips(listOf("people" to "people only", "work" to "people + work", "all" to "show everything", "none" to "hold everything"), null) { v ->
            for (a in apps) {
                val mode = when {
                    v == "all" -> "allow"
                    v == "none" -> "quiet"
                    a.pkg in people || (v == "work" && a.pkg in work) -> "allow"
                    else -> "quiet"
                }
                Prefs.setNotifMode(this, a.pkg, mode)
            }
            BlandNotificationService.instance?.sweep()
            Ui.toast(this, "notifications updated")
        }
        label("per app · tap to change")
        for (a in apps) {
            val cur = Prefs.notifMode(this, a.pkg)
            item(a.name, Notif.label(cur)) {
                Prefs.setNotifMode(this, a.pkg, Notif.MODES[(Notif.MODES.indexOf(cur) + 1) % Notif.MODES.size])
                BlandNotificationService.instance?.sweep()
                render()
            }
        }
        note("show: appears as normal. hold quietly: taken out of the status bar and kept in bland's list. block: dismissed right away. " +
            "android may still buzz once when a notification arrives; for full silence, long-press the app and choose android sound & alert settings.")
    }

    private fun limitsPage() {
        back(); title("pauses & limits")
        label("pause before opening")
        chips(listOf(0 to "off", 5 to "5s", 10 to "10s", 20 to "20s", 30 to "30s"), Prefs.delaySeconds(this)) { Prefs.setDelaySeconds(this, it) }
        label("daily opens per paused app")
        chips(listOf(1 to "1", 2 to "2", 3 to "3", 5 to "5", 10 to "10", 0 to "no limit"), Prefs.limit(this)) {
            if (!Prefs.requestLimit(this, it)) Ui.toast(this, "looser limits start tomorrow")
        }
        val pending = Prefs.pendingLimit(this)
        if (pending >= 0) note("changes to ${if (pending == 0) "no limit" else pending} tomorrow. stricter limits apply right away; looser ones wait a day so you can't talk yourself out of it.")
        label("paused apps · tap to change")
        val paused = Prefs.distracting(this)
        val limit = Prefs.limit(this)
        for (a in Apps.load(this).distinctBy { it.pkg }) {
            val value = if (a.pkg !in paused) "" else if (Launcher.isLocked(this, a.pkg)) "locked today"
                else "${Usage.count(this, a.pkg)}/${if (limit > 0) limit else "∞"} today"
            item(a.name, value) { Launcher.togglePause(this, a.pkg); render() }
        }
    }

    private fun widgetsPage() {
        back(); title("widgets")
        label("on the home screen · tap to change")
        val on = Prefs.widgets(this)
        for ((id, name) in Widgets.ALL) item(name, if (id in on) "on" else "off") { Prefs.toggleWidget(this, id); render() }
        if ("weather" in on) item("weather city", Weather.city(this) ?: "not set") { Weather.cityDialog(this) { render() } }
        note("weather comes from open-meteo.com and only sends the city you type. app usage counts apps opened from bland today. " +
            "to-do: tap to tick off, long-press to delete.")
    }

    private fun notionPage() {
        back(); title("notion")
        val connected = Notion.configured(this)
        item(if (connected) "change connection" else "connect your second brain", if (connected) Prefs.notionName(this).lowercase() else null) {
            Notion.setupDialog(this) { render() }
        }
        if (connected) item("disconnect") { Prefs.setNotion(this, "", "", "Inbox"); render() }
        val waiting = Outbox.all(this).size
        if (waiting > 0) item("send $waiting waiting note${if (waiting == 1) "" else "s"}") { Outbox.flush(this); Ui.toast(this, "sending"); render() }
        note("notes from + note on the home screen land in this database as new pages. the first line becomes the title. " +
            "sort them into projects, areas, resources and archives later, the PARA way.")
    }

    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

    private fun grayscaleHelp() {
        Ui.dialog(this)
            .setTitle("grayscale everything")
            .setMessage(
                "a launcher can only style itself. to drain the color from every app:\n\n" +
                    "• Settings → Accessibility → Color correction → Grayscale\n\n" +
                    "• or Digital Wellbeing → Bedtime mode → Grayscale\n\n" +
                    "• or Developer options → Simulate color space → Monochromacy"
            )
            .setPositiveButton("open accessibility") { _, _ -> Launcher.tryStart(this, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("close", null)
            .show()
    }
}
