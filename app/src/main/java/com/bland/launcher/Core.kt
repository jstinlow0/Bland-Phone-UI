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

object Apps {
    fun load(c: Context): List<AppEntry> {
        val pm = c.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != c.packageName }
            .map {
                AppEntry(
                    it.loadLabel(pm).toString(),
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                )
            }
            .distinctBy { it.key }
            .sortedBy { it.name }
    }
}

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("bland", Context.MODE_PRIVATE)

    fun favorites(c: Context): List<String> =
        p(c).getString("favorites", "")!!.split("|").filter { it.isNotEmpty() }

    fun setFavorites(c: Context, keys: List<String>) =
        p(c).edit().putString("favorites", keys.joinToString("|")).apply()

    fun hidden(c: Context): Set<String> = p(c).getStringSet("hidden", emptySet())!!.toSet()
    fun toggleHidden(c: Context, key: String) = toggle(c, "hidden", key)

    /** Packages that get a mindful pause before opening. */
    fun distracting(c: Context): Set<String> = p(c).getStringSet("distracting", emptySet())!!.toSet()
    fun toggleDistracting(c: Context, pkg: String) = toggle(c, "distracting", pkg)

    fun delaySeconds(c: Context) = p(c).getInt("delay", 10)
    fun setDelaySeconds(c: Context, v: Int) = p(c).edit().putInt("delay", v).apply()

    fun maxFavorites(c: Context) = p(c).getInt("maxFavorites", 6)
    fun setMaxFavorites(c: Context, v: Int) = p(c).edit().putInt("maxFavorites", v).apply()

    fun showHidden(c: Context) = p(c).getBoolean("showHidden", false)
    fun setShowHidden(c: Context, v: Boolean) = p(c).edit().putBoolean("showHidden", v).apply()

    private fun toggle(c: Context, setName: String, value: String) {
        val s = p(c).getStringSet(setName, emptySet())!!.toMutableSet()
        if (!s.remove(value)) s.add(value)
        p(c).edit().putStringSet(setName, s).apply()
    }
}

/** Counts app opens made through this launcher, reset every day. */
object Usage {
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun p(c: Context) = c.getSharedPreferences("usage", Context.MODE_PRIVATE).also {
        val t = today()
        if (it.getString("day", null) != t) it.edit().clear().putString("day", t).apply()
    }

    fun record(c: Context, pkg: String) {
        val sp = p(c)
        sp.edit()
            .putInt("app:$pkg", sp.getInt("app:$pkg", 0) + 1)
            .putInt("total", sp.getInt("total", 0) + 1)
            .apply()
    }

    fun count(c: Context, pkg: String) = p(c).getInt("app:$pkg", 0)
    fun total(c: Context) = p(c).getInt("total", 0)
}

object Launcher {
    fun open(a: Activity, app: AppEntry) {
        if (app.pkg in Prefs.distracting(a) && Prefs.delaySeconds(a) > 0) {
            a.startActivity(
                Intent(a, GateActivity::class.java)
                    .putExtra(GateActivity.EXTRA_KEY, app.key)
                    .putExtra(GateActivity.EXTRA_LABEL, app.label)
            )
            @Suppress("DEPRECATION")
            a.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            launchNow(a, app.component)
        }
    }

    fun launchNow(c: Context, cn: ComponentName): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(cn)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            c.startActivity(intent)
            Usage.record(c, cn.packageName)
            true
        } catch (e: Exception) {
            Toast.makeText(c, "can't open that app", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun tryStart(c: Context, intent: Intent): Boolean = try {
        c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) {
        false
    }
}

object Ui {
    const val WHITE = Color.WHITE
    val GRAY = Color.parseColor("#8A8A8A")
    val DIM = Color.parseColor("#4A4A4A")

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun row(c: Context, text: String, sizeSp: Float, color: Int = WHITE): TextView =
        TextView(c).apply {
            this.text = text
            setTextColor(color)
            textSize = sizeSp
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, dp(c, 10), 0, dp(c, 10))
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }

    fun dialog(c: Context) = AlertDialog.Builder(c, R.style.BlandDialog)
}

/** Long-press menu shared by the home screen and the app list. */
object AppOptions {
    fun show(a: Activity, app: AppEntry, onChange: () -> Unit) {
        val favs = Prefs.favorites(a)
        val idx = favs.indexOf(app.key)
        val opts = mutableListOf<Pair<String, () -> Unit>>()
        fun add(label: String, action: () -> Unit) { opts.add(label to action) }

        if (idx >= 0) {
            add("unpin from home") { Prefs.setFavorites(a, favs - app.key) }
            if (idx > 0) add("move up") { Prefs.setFavorites(a, swapped(favs, idx, idx - 1)) }
            if (idx < favs.size - 1) add("move down") { Prefs.setFavorites(a, swapped(favs, idx, idx + 1)) }
        } else {
            add("pin to home") {
                val max = Prefs.maxFavorites(a)
                if (favs.size >= max) {
                    Toast.makeText(a, "home holds $max apps. less is more.", Toast.LENGTH_LONG).show()
                } else {
                    Prefs.setFavorites(a, favs + app.key)
                }
            }
        }

        val paused = app.pkg in Prefs.distracting(a)
        add((if (paused) "remove pause" else "pause before opening")) {
            Prefs.toggleDistracting(a, app.pkg)
        }

        val hidden = app.key in Prefs.hidden(a)
        add((if (hidden) "unhide" else "hide from list")) { Prefs.toggleHidden(a, app.key) }

        add("app info") {
            Launcher.tryStart(a, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
        }
        add("uninstall") {
            Launcher.tryStart(a, Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
        }

        Ui.dialog(a)
            .setTitle(app.name)
            .setItems(opts.map { it.first }.toTypedArray()) { _, which ->
                opts[which].second()
                onChange()
            }
            .show()
    }

    private fun swapped(list: List<String>, i: Int, j: Int): List<String> =
        list.toMutableList().also { Collections.swap(it, i, j) }
}
