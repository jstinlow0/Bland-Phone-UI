package com.bland.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView

class SettingsActivity : Activity() {
    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = Ui.dp(this, 32)
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            addView(box)
        })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        box.removeAllViews()
        box.addView(Ui.row(this, "settings", 34f).apply {
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 24))
        })

        item(if (isDefaultHome()) "bland is your home screen" else "set bland as home screen") {
            if (!Launcher.tryStart(this, Intent(Settings.ACTION_HOME_SETTINGS))) {
                Launcher.tryStart(this, Intent(Settings.ACTION_SETTINGS))
            }
        }

        val delay = Prefs.delaySeconds(this)
        item("pause length: ${if (delay == 0) "off" else "${delay}s"}") {
            val steps = listOf(0, 5, 10, 20, 30)
            Prefs.setDelaySeconds(this, steps[(steps.indexOf(delay) + 1) % steps.size])
            render()
        }

        val max = Prefs.maxFavorites(this)
        item("apps on home: $max") {
            Prefs.setMaxFavorites(this, if (max >= 8) 3 else max + 1)
            render()
        }

        val showHidden = Prefs.showHidden(this)
        item("hidden apps: ${if (showHidden) "shown dimmed" else "hidden"}") {
            Prefs.setShowHidden(this, !showHidden)
            render()
        }

        item("make the whole phone grayscale") { grayscaleHelp() }
        item("android settings") { Launcher.tryStart(this, Intent(Settings.ACTION_SETTINGS)) }

        box.addView(Ui.row(this, "", 8f))
        note("long-press any app in the list to pin it, hide it, or add a pause before it opens.")
        note("tap the clock for alarms. tap the date for your calendar. long-press the clock to return here.")
    }

    private fun item(text: String, onClick: () -> Unit) {
        box.addView(Ui.row(this, text, 20f).apply {
            isSingleLine = false
            setOnClickListener { onClick() }
        })
    }

    private fun note(text: String) {
        box.addView(Ui.row(this, text, 14f, Ui.DIM).apply { isSingleLine = false })
    }

    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == packageName
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
            .setPositiveButton("open accessibility") { _, _ ->
                Launcher.tryStart(this, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("close", null)
            .show()
    }
}
