package com.bland.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process

/**
 * Home-screen bookmarks from browsers (Brave, Chrome, …), which Android calls pinned shortcuts.
 * Only the default home app can see them, and only the ones pinned while it was the home app.
 * A bookmark is stored as an [AppEntry] whose class name is "#shortcut:<id>", so favorites, hiding and the
 * browser's own pause & limit work on it unchanged.
 */
object Shortcuts {
    private const val PREFIX = "#shortcut:"

    private fun la(c: Context) = c.getSystemService(LauncherApps::class.java)
    private fun pinned(c: Context, pkg: String? = null) = try {
        val q = LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        if (pkg != null) q.setPackage(pkg)
        if (la(c).hasShortcutHostPermission()) la(c).getShortcuts(q, Process.myUserHandle()).orEmpty() else emptyList()
    } catch (e: Exception) { emptyList() } // not the home app yet, or the phone is still locked after a restart

    fun component(pkg: String, id: String) = ComponentName(pkg, PREFIX + id)
    fun idOf(cn: ComponentName): String? = cn.className.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)

    fun load(c: Context): List<AppEntry> = pinned(c).filter { it.isEnabled }
        .map { AppEntry((it.shortLabel ?: it.`package`).toString(), component(it.`package`, it.id)) }

    fun start(c: Context, cn: ComponentName, id: String) =
        la(c).startShortcut(cn.packageName, id, null, null, Process.myUserHandle())

    /** Unpinning is done by re-pinning every other bookmark from the same browser. */
    fun remove(c: Context, cn: ComponentName, id: String) = try {
        la(c).pinShortcuts(cn.packageName, pinned(c, cn.packageName).map { it.id } - id, Process.myUserHandle())
    } catch (e: Exception) { Ui.toast(c, "couldn't remove that bookmark") }
}

/** Android opens this when a browser asks to add a page to the home screen. The browser already asked you, so just accept. */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val req = getSystemService(LauncherApps::class.java).getPinItemRequest(intent)
        val info = req?.shortcutInfo
        if (req != null && info != null && req.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT && req.isValid && req.accept()) {
            val name = (info.shortLabel ?: "bookmark").toString().lowercase()
            val favs = Prefs.favorites(this)
            if (favs.size < Prefs.maxFavorites(this)) {
                Prefs.setFavorites(this, favs + Shortcuts.component(info.`package`, info.id).flattenToString())
                Ui.toast(this, "added $name to home")
            } else {
                Ui.toast(this, "added $name to your app list")
            }
        }
        finish()
    }
}
