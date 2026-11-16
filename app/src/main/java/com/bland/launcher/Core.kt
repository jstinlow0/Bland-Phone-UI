package com.bland.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

/** One launchable app. [key] identifies the exact activity; [pkg] the app. */
data class AppEntry(val label: String, val component: ComponentName) {
    val key: String get() = component.flattenToString()
    val pkg: String get() = component.packageName
    val name: String get() = label.lowercase()
}

fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

object Apps {
    fun load(c: Context): List<AppEntry> {
        val pm = c.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != c.packageName }
            .map { AppEntry(it.loadLabel(pm).toString(), ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
            .distinctBy { it.key }
            .sortedBy { it.name }
    }

    /** Apps plus browser bookmarks: everything that can be opened from home or the list. */
    fun withBookmarks(c: Context) = (load(c) + Shortcuts.load(c)).sortedBy { it.name }

    fun label(c: Context, pkg: String): String = try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }
}

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("bland", Context.MODE_PRIVATE)

    // Home apps
    fun favorites(c: Context): List<String> = p(c).getString("favorites", "")!!.split("|").filter { it.isNotEmpty() }
    fun setFavorites(c: Context, keys: List<String>) = p(c).edit().putString("favorites", keys.joinToString("|")).apply()
    fun maxFavorites(c: Context) = p(c).getInt("maxFavorites", 6)
    fun setMaxFavorites(c: Context, v: Int) = p(c).edit().putInt("maxFavorites", v).apply()

    fun hidden(c: Context): Set<String> = p(c).getStringSet("hidden", emptySet())!!.toSet()
    fun toggleHidden(c: Context, key: String) = toggle(c, "hidden", key)
    fun showHidden(c: Context) = p(c).getBoolean("showHidden", false)
    fun setShowHidden(c: Context, v: Boolean) = p(c).edit().putBoolean("showHidden", v).apply()

    // Pauses and limits (by package)
    fun distracting(c: Context): Set<String> = p(c).getStringSet("distracting", emptySet())!!.toSet()
    fun toggleDistracting(c: Context, pkg: String) = toggle(c, "distracting", pkg)
    fun delaySeconds(c: Context) = p(c).getInt("delay", 10)
    fun setDelaySeconds(c: Context, v: Int) = p(c).edit().putInt("delay", v).apply()

    /** Daily opens allowed per paused app. 0 = no limit. */
    fun limit(c: Context): Int {
        val sp = p(c)
        val pending = sp.getInt("pendingLimit", -1)
        if (pending >= 0 && today() > sp.getString("pendingDay", "")!!) {
            sp.edit().putInt("limit", pending).remove("pendingLimit").remove("pendingDay").apply()
            return pending
        }
        return sp.getInt("limit", 3)
    }

    fun pendingLimit(c: Context): Int = p(c).getInt("pendingLimit", -1)

    /** Stricter limits apply now; looser ones wait until tomorrow. Returns true if applied now. */
    fun requestLimit(c: Context, v: Int): Boolean {
        val cur = limit(c)
        val looser = cur > 0 && (v == 0 || v > cur)
        if (looser) {
            p(c).edit().putInt("pendingLimit", v).putString("pendingDay", today()).apply()
        } else {
            p(c).edit().putInt("limit", v).remove("pendingLimit").remove("pendingDay").apply()
        }
        return !looser
    }

    // Look
    fun font(c: Context) = p(c).getString("font", "sans")!!
    fun setFont(c: Context, v: String) = p(c).edit().putString("font", v).apply()
    fun textScale(c: Context) = p(c).getFloat("scale", 1f)
    fun setTextScale(c: Context, v: Float) = p(c).edit().putFloat("scale", v).apply()
    fun appAlign(c: Context) = p(c).getString("appAlign", "left")!!
    fun setAppAlign(c: Context, v: String) = p(c).edit().putString("appAlign", v).apply()

    // Clock
    fun clockPos(c: Context) = p(c).getString("clockPos", "top")!!
    fun clockAlign(c: Context) = p(c).getString("clockAlign", "left")!!
    fun clockStyle(c: Context) = p(c).getString("clockStyle", "thin")!!
    fun clockSize(c: Context) = p(c).getString("clockSize", "large")!!
    fun setClock(c: Context, key: String, v: String) = p(c).edit().putString(key, v).apply()

    // Notifications: "allow", "quiet" or "block" per package
    fun notifMode(c: Context, pkg: String) = p(c).getString("mode:$pkg", "allow")!!
    fun setNotifMode(c: Context, pkg: String, v: String) = p(c).edit().putString("mode:$pkg", v).apply()

    // Widgets
    fun widgets(c: Context): Set<String> = p(c).getStringSet("widgets", setOf("search"))!!.toSet()
    // Not toggle(): that would start from an empty set and drop the default search bar.
    fun toggleWidget(c: Context, id: String) = widgets(c).let { on ->
        p(c).edit().putStringSet("widgets", if (id in on) on - id else on + id).apply()
    }
    fun todos(c: Context) = p(c).getString("todos", "")!!.split("\n").filter { it.isNotEmpty() }
    fun setTodos(c: Context, v: List<String>) = p(c).edit().putString("todos", v.joinToString("\n")).apply()

    fun showQuote(c: Context) =p(c).getBoolean("showQuote", true)
    fun setShowQuote(c: Context, v: Boolean) = p(c).edit().putBoolean("showQuote", v).apply()

    // Notion
    fun notionToken(c: Context) = p(c).getString("notionToken", "")!!
    fun notionDb(c: Context) = p(c).getString("notionDb", "")!!
    fun notionName(c: Context) = p(c).getString("notionName", "Inbox")!!
    fun setNotion(c: Context, token: String, db: String, name: String) =
        p(c).edit().putString("notionToken", token).putString("notionDb", db).putString("notionName", name).apply()

    private fun toggle(c: Context, setName: String, value: String) {
        val s = p(c).getStringSet(setName, emptySet())!!.toMutableSet()
        if (!s.remove(value)) s.add(value)
        p(c).edit().putStringSet(setName, s).apply()
    }
}

/** Counts app opens made through this launcher, reset every day. */
object Usage {
    private fun p(c: Context) = c.getSharedPreferences("usage", Context.MODE_PRIVATE).also {
        val t = today()
        if (it.getString("day", null) != t) it.edit().clear().putString("day", t).apply()
    }

    fun record(c: Context, pkg: String) {
        val sp = p(c)
        sp.edit().putInt("app:$pkg", sp.getInt("app:$pkg", 0) + 1).putInt("total", sp.getInt("total", 0) + 1).apply()
    }

    fun count(c: Context, pkg: String) = p(c).getInt("app:$pkg", 0)
    fun total(c: Context) = p(c).getInt("total", 0)
    fun top(c: Context, n: Int): List<Pair<String, Int>> = p(c).all
        .mapNotNull { (k, v) -> if (k.startsWith("app:") && v is Int) k.removePrefix("app:") to v else null }
        .sortedByDescending { it.second }.take(n)
    fun recordBlocked(c: Context) { val sp = p(c); sp.edit().putInt("blocked", sp.getInt("blocked", 0) + 1).apply() }
    fun blocked(c: Context) = p(c).getInt("blocked", 0)
}

object Launcher {
    fun isLocked(c: Context, pkg: String): Boolean {
        val limit = Prefs.limit(c)
        return pkg in Prefs.distracting(c) && limit > 0 && Usage.count(c, pkg) >= limit
    }

    /** Removing a pause loosens the limit, so a locked app stays paused until tomorrow. */
    fun togglePause(c: Context, pkg: String) {
        if (isLocked(c, pkg)) Ui.toast(c, "locked for today. the pause can come off tomorrow.")
        else Prefs.toggleDistracting(c, pkg)
    }

    /** Opens an app, applying the pause and the daily limit. Returns false if it was held back. */
    fun open(a: Activity, app: AppEntry) = openComponent(a, app.component, app.label)

    fun openComponent(a: Activity, cn: ComponentName, label: String, direct: (() -> Unit)? = null): Boolean {
        val pkg = cn.packageName
        if (isLocked(a, pkg) || (pkg in Prefs.distracting(a) && Prefs.delaySeconds(a) > 0)) {
            a.startActivity(
                Intent(a, GateActivity::class.java)
                    .putExtra(GateActivity.EXTRA_KEY, cn.flattenToString())
                    .putExtra(GateActivity.EXTRA_LABEL, label)
            )
            fade(a)
            return false
        }
        if (direct != null) { direct(); Usage.record(a, pkg) } else launchNow(a, cn)
        return true
    }

    fun launchNow(c: Context, cn: ComponentName): Boolean {
        val bookmark = Shortcuts.idOf(cn)
        return try {
            if (bookmark != null) Shortcuts.start(c, cn, bookmark)
            else c.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            Usage.record(c, cn.packageName); true
        } catch (e: Exception) {
            Toast.makeText(c, "can't open that app", Toast.LENGTH_SHORT).show(); false
        }
    }

    /** Opens the main activity of a package (used when only the package is known). */
    fun componentFor(c: Context, pkg: String): ComponentName? =
        c.packageManager.getLaunchIntentForPackage(pkg)?.component

    fun tryStart(c: Context, intent: Intent): Boolean = try {
        c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }

    @Suppress("DEPRECATION")
    fun fade(a: Activity) = a.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
}

object Ui {
    const val WHITE = Color.WHITE
    const val BLACK = Color.BLACK
    val GRAY = Color.parseColor("#8A8A8A")
    val DIM = Color.parseColor("#4A4A4A")
    val EDGE = Color.parseColor("#262626")

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
    fun dpf(c: Context, v: Float) = v * c.resources.displayMetrics.density

    fun face(c: Context): Typeface = when (Prefs.font(c)) {
        "serif" -> Typeface.SERIF
        "mono" -> Typeface.MONOSPACE
        "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        else -> Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    fun row(c: Context, text: String, sizeSp: Float, color: Int = WHITE, scaled: Boolean = true): TextView =
        TextView(c).apply {
            this.text = text
            setTextColor(color)
            textSize = if (scaled) sizeSp * Prefs.textScale(c) else sizeSp
            typeface = face(c)
            setPadding(0, dp(c, 10), 0, dp(c, 10))
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }

    fun gravity(align: String) = when (align) {
        "center" -> android.view.Gravity.CENTER_HORIZONTAL
        "right" -> android.view.Gravity.END
        else -> android.view.Gravity.START
    }

    fun dialog(c: Context) = AlertDialog.Builder(c, R.style.BlandDialog)

    fun toast(c: Context, msg: String) = Toast.makeText(c, msg, Toast.LENGTH_SHORT).show()

    fun vbox(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

    fun spacer(c: Context) = View(c).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) }

    /** A horizontal row of pill buttons; the current one is outlined in white. */
    fun <T> chips(c: Context, options: List<Pair<T, String>>, current: T?, onPick: (T) -> Unit): View {
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        for ((value, label) in options) {
            val on = value == current
            val chip = TextView(c).apply {
                text = label
                textSize = 14f
                typeface = face(c)
                setTextColor(if (on) WHITE else GRAY)
                setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dpf(c, 18f)
                    setStroke(dp(c, 1), if (on) WHITE else DIM)
                }
                setOnClickListener { onPick(value) }
            }
            row.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(c, 8)
            })
        }
        return HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(c, 6), 0, dp(c, 6))
            addView(row)
        }
    }
}

object Notif {
    val MODES = listOf("allow", "quiet", "block")
    fun label(mode: String) = when (mode) { "quiet" -> "hold quietly"; "block" -> "block"; else -> "show" }

    fun menu(a: Activity, pkg: String, onChange: () -> Unit) {
        val cur = Prefs.notifMode(a, pkg)
        val items = arrayOf(
            "show" + if (cur == "allow") "  ·" else "",
            "hold quietly (out of the status bar)" + if (cur == "quiet") "  ·" else "",
            "block" + if (cur == "block") "  ·" else "",
            "android sound & alert settings",
        )
        Ui.dialog(a).setTitle("${Apps.label(a, pkg).lowercase()} notifications").setItems(items) { _, w ->
            when (w) {
                3 -> Launcher.tryStart(a, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
                else -> Prefs.setNotifMode(a, pkg, MODES[w])
            }
            onChange()
        }.show()
    }
}

/** Long-press menu shared by the home screen and the app list. */
object AppOptions {
    fun show(a: Activity, app: AppEntry, onChange: () -> Unit) {
        val favs = Prefs.favorites(a)
        val idx = favs.indexOf(app.key)
        val opts = mutableListOf<Pair<String, () -> Unit>>()
        fun add(label: String, action: () -> Unit) { opts.add(label to action) }

        if (idx >= 0) {
            add("remove from home") { Prefs.setFavorites(a, favs - app.key) }
            if (idx > 0) add("move up") { Prefs.setFavorites(a, swapped(favs, idx, idx - 1)) }
            if (idx < favs.size - 1) add("move down") { Prefs.setFavorites(a, swapped(favs, idx, idx + 1)) }
        } else {
            add("add to home") {
                val max = Prefs.maxFavorites(a)
                if (favs.size >= max) Ui.toast(a, "home holds $max apps. less is more.")
                else Prefs.setFavorites(a, favs + app.key)
            }
        }
        val bookmark = Shortcuts.idOf(app.component)
        // A bookmark's package is the browser, so pause, notifications, info and uninstall would act on the whole browser.
        if (bookmark == null) {
            val paused = app.pkg in Prefs.distracting(a)
            add(if (paused) "remove pause & limit" else "pause & limit this app") { Launcher.togglePause(a, app.pkg) }
            add("notifications: ${Notif.label(Prefs.notifMode(a, app.pkg))}") { Notif.menu(a, app.pkg, onChange) }
        }
        val hidden = app.key in Prefs.hidden(a)
        add(if (hidden) "unhide" else "hide from list") { Prefs.toggleHidden(a, app.key) }
        if (bookmark == null) {
            add("app info") {
                Launcher.tryStart(a, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
            }
            add("uninstall") { Launcher.tryStart(a, Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))) }
        } else {
            add("remove bookmark") { Shortcuts.remove(a, app.component, bookmark); Prefs.setFavorites(a, favs - app.key) }
        }

        Ui.dialog(a).setTitle(app.name)
            .setItems(opts.map { it.first }.toTypedArray()) { _, which -> opts[which].second(); onChange() }
            .show()
    }

    private fun swapped(list: List<String>, i: Int, j: Int): List<String> =
        list.toMutableList().also { Collections.swap(it, i, j) }
}
