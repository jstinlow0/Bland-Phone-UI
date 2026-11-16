package com.bland.launcher

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/** A notification Bland took out of the status bar and is keeping for later. */
data class Held(val key: String, val pkg: String, val title: String, val text: String, val time: Long)

/**
 * Applies each app's notification choice:
 * show = left alone, hold quietly = moved out of the status bar into Bland's list, block = dismissed.
 */
class BlandNotificationService : NotificationListenerService() {
    companion object {
        @Volatile var instance: BlandNotificationService? = null
        /** Home or the app list redraws through this when an app's notifications come or go. */
        var onChange: (() -> Unit)? = null

        /** Apps with a notification still in the status bar, for the white dot. */
        fun unread(c: Context): Set<String> = try {
            instance?.activeNotifications?.filter { relevant(c, it) }?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (e: Exception) { emptySet() }

        /** App name with a dot when it has unread notifications. Bookmarks skip it: their package is the browser's. */
        fun label(app: AppEntry, unread: Set<String>) =
            if (app.pkg in unread && Shortcuts.idOf(app.component) == null) "${app.name} •" else app.name
        /** Tap targets for held notifications. Kept in memory only; they can't be saved. */
        val intents = HashMap<String, PendingIntent>()

        private fun p(c: Context) = c.getSharedPreferences("held", Context.MODE_PRIVATE)

        fun held(c: Context): List<Held> {
            val arr = try { JSONArray(p(c).getString("list", "[]")) } catch (e: Exception) { JSONArray() }
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Held(o.getString("key"), o.getString("pkg"), o.optString("title"), o.optString("text"), o.getLong("time"))
            }
        }

        private fun saveHeld(c: Context, list: List<Held>) {
            val arr = JSONArray()
            list.take(100).forEach {
                arr.put(JSONObject().put("key", it.key).put("pkg", it.pkg).put("title", it.title).put("text", it.text).put("time", it.time))
            }
            p(c).edit().putString("list", arr.toString()).apply()
        }

        fun removeHeld(c: Context, key: String) = saveHeld(c, held(c).filter { it.key != key })
        fun clearHeld(c: Context) { saveHeld(c, emptyList()); intents.clear() }

        fun title(n: Notification) = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        fun text(n: Notification) = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""

        /** Notifications worth listing: not our own, not ongoing (music, downloads), not group summaries. */
        fun relevant(c: Context, sbn: StatusBarNotification) =
            sbn.packageName != c.packageName && !sbn.isOngoing && sbn.isClearable &&
                (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
    }

    override fun onListenerConnected() { instance = this; sweep(); changed() }
    override fun onListenerDisconnected() { instance = null }

    override fun onNotificationPosted(sbn: StatusBarNotification) { handle(sbn); changed() }
    override fun onNotificationRemoved(sbn: StatusBarNotification) = changed()

    private fun changed() { android.os.Handler(mainLooper).post { onChange?.invoke() } }

    /** Apply the rules to anything already showing, e.g. after a setting changes. */
    fun sweep() {
        try { activeNotifications?.forEach { handle(it) } } catch (e: Exception) { }
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing || !sbn.isClearable) return
        when (Prefs.notifMode(this, sbn.packageName)) {
            "block" -> { cancelNotification(sbn.key); Usage.recordBlocked(this) }
            "quiet" -> {
                if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0) {
                    val n = sbn.notification
                    val h = Held(sbn.key, sbn.packageName, title(n), text(n), sbn.postTime)
                    saveHeld(this, listOf(h) + held(this).filter { it.key != sbn.key })
                    n.contentIntent?.let { intents[sbn.key] = it }
                }
                cancelNotification(sbn.key)
            }
        }
    }
}
