package com.bland.launcher

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Lets a double tap on the home screen lock the phone. It reads nothing on screen; it only asks Android to lock.
 * Uses accessibility instead of device admin so fingerprint and face unlock keep working.
 */
class LockService : AccessibilityService() {
    companion object {
        @Volatile private var instance: LockService? = null

        fun lock(a: Activity) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) { Ui.toast(a, "locking needs android 9 or newer"); return }
            val s = instance
            if (s != null) { s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN); return }
            Ui.dialog(a).setTitle("double tap to lock")
                .setMessage("turn on bland in accessibility settings and double-tapping the home screen will lock your phone.\n\n" +
                    "bland only uses it to lock the screen. it doesn't read what's on your screen.\n\n" +
                    "if android says the setting is restricted: app info → bland → ⋮ → allow restricted settings, then try again.")
                .setNegativeButton("not now", null)
                .setPositiveButton("open settings") { _, _ -> Launcher.tryStart(a, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .show()
        }
    }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
