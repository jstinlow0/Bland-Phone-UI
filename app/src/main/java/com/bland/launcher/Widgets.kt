package com.bland.launcher

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/** Small text-only blocks under the home apps. Turned on in settings → widgets. */
object Widgets {
    val ALL = listOf("weather" to "weather", "usage" to "app usage", "todo" to "to-do list", "coin" to "coin flip")
    private const val DONE = "✓"
    private const val MAX_TODOS = 5

    fun build(a: Activity, refresh: () -> Unit): View? {
        val on = Prefs.widgets(a)
        if (on.isEmpty()) return null
        val box = Ui.vbox(a).apply {
            gravity = Ui.gravity(Prefs.appAlign(a))
            setPadding(0, Ui.dp(a, 20), 0, 0)
        }
        for ((id, _) in ALL) if (id in on) box.addView(when (id) {
            "weather" -> weather(a)
            "usage" -> usage(a)
            "todo" -> todo(a, refresh)
            else -> coin(a)
        })
        return box
    }

    private fun weather(a: Activity): View {
        val tv = Ui.row(a, Weather.cached(a) ?: "weather · tap to set your city", 16f, Ui.GRAY)
        tv.setOnClickListener { Weather.cityDialog(a) { tv.text = it } }
        Weather.refresh(a, force = false) { tv.text = it }
        return tv
    }

    private fun usage(a: Activity): View {
        val top = Usage.top(a, 3)
        val text = if (top.isEmpty()) "no app opens yet today"
        else "today: " + top.joinToString(" · ") { (pkg, n) -> "${Apps.label(a, pkg).lowercase()} $n" }
        return Ui.row(a, text, 16f, Ui.GRAY)
    }

    /** Tap to tick off, long-press to delete. Five at most, so the home screen never fills up. */
    private fun todo(a: Activity, refresh: () -> Unit): View {
        val box = Ui.vbox(a).apply { gravity = Ui.gravity(Prefs.appAlign(a)) }
        val items = Prefs.todos(a)
        items.forEachIndexed { i, item ->
            val done = item.startsWith(DONE)
            box.addView(Ui.row(a, (if (done) "● " else "○ ") + item.removePrefix(DONE), 16f, if (done) Ui.DIM else Ui.WHITE).apply {
                setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6))
                if (done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                setOnClickListener {
                    Prefs.setTodos(a, items.toMutableList().also { it[i] = if (done) item.removePrefix(DONE) else DONE + item })
                    refresh()
                }
                setOnLongClickListener { Prefs.setTodos(a, items.filterIndexed { j, _ -> j != i }); refresh(); true }
            })
        }
        box.addView(Ui.row(a, "+ to-do", 14f, Ui.DIM).apply {
            setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6))
            setOnClickListener {
                if (Prefs.todos(a).size >= MAX_TODOS) { Ui.toast(a, "five is plenty. finish one first."); return@setOnClickListener }
                val input = field(a, "what needs doing?", "")
                Ui.dialog(a).setTitle("to-do").setView(padded(a, input))
                    .setNegativeButton("cancel", null)
                    .setPositiveButton("add") { _, _ ->
                        val t = input.text.toString().trim().removePrefix(DONE)
                        if (t.isNotEmpty()) { Prefs.setTodos(a, Prefs.todos(a) + t); refresh() }
                    }.show()
            }
        })
        return box
    }

    /**
     * A circle that flips in 2D, one frame at a time:
     * face → diagonal (narrow oval) → side (thin edge) → diagonal of the other face → other face.
     */
    private fun coin(a: Activity): View {
        val ring = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(Ui.dp(a, 2), Color.WHITE) }
        val edge = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
        val label = Ui.row(a, "tap the coin to flip", 13f, Ui.DIM)
        val face = TextView(a).apply {
            gravity = Gravity.CENTER
            textSize = 24f
            typeface = Typeface.SERIF
            setTextColor(Color.WHITE)
        }
        var heads = true
        var flipping = false
        val widths = floatArrayOf(1f, 0.55f, 0.1f, 0.55f)
        fun show(frame: Int, fromHeads: Boolean) {
            face.scaleX = widths[frame]
            face.background = if (frame == 2) edge else ring
            val up = if (frame == 3) !fromHeads else fromHeads
            face.text = if (frame == 2) "" else if (up) "H" else "T"
        }
        show(0, heads)
        face.contentDescription = "coin. tap to flip"
        face.setOnClickListener {
            if (flipping) return@setOnClickListener
            flipping = true
            val start = heads
            val result = Random.nextBoolean()
            // Each half-turn swaps the face, so an odd count lands on the other side.
            val total = (6 + if (result != start) 1 else 0) * 4
            val handler = Handler(Looper.getMainLooper())
            var step = 0
            handler.post(object : Runnable {
                override fun run() {
                    if (step == total) {
                        heads = result
                        show(0, heads)
                        label.text = if (heads) "heads" else "tails"
                        flipping = false
                        return
                    }
                    show(step % 4, if ((step / 4) % 2 == 0) start else !start)
                    step++
                    handler.postDelayed(this, 35L + step * 3) // slows down as it lands
                }
            })
        }
        val size = Ui.dp(a, 64)
        return Ui.vbox(a).apply {
            gravity = Ui.gravity(Prefs.appAlign(a))
            setPadding(0, Ui.dp(a, 10), 0, 0)
            addView(face, LinearLayout.LayoutParams(size, size))
            addView(label)
        }
    }

    fun field(c: Context, hint: String, value: String) = EditText(c).apply {
        this.hint = hint
        setText(value)
        setSingleLine()
        setTextColor(Ui.WHITE)
        setHintTextColor(Ui.DIM)
    }

    fun padded(c: Context, v: View) = LinearLayout(c).apply {
        setPadding(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), 0)
        addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
}

/** Current weather from open-meteo.com (free, no account). Only the city's coordinates are sent. */
object Weather {
    private fun p(c: Context) = c.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    fun cached(c: Context): String? = p(c).getString("text", null)
    fun city(c: Context): String? = p(c).getString("city", null)

    /** Fetches at most every 30 minutes, so switching apps quickly never piles up requests. */
    fun refresh(c: Context, force: Boolean, done: (String) -> Unit) {
        val sp = p(c)
        val lat = sp.getString("lat", null) ?: return
        val lon = sp.getString("lon", null) ?: return
        val now = System.currentTimeMillis()
        if (!force && now - sp.getLong("at", 0) < 30 * 60_000L) return
        sp.edit().putLong("at", now).apply()
        val city = sp.getString("city", "")!!
        val unit = if (Locale.getDefault().country in setOf("US", "LR", "MM")) "&temperature_unit=fahrenheit" else ""
        Thread {
            val text = try {
                val j = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto$unit"))
                val cur = j.getJSONObject("current")
                val day = j.getJSONObject("daily")
                val hi = day.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()
                val lo = day.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()
                "${cur.getDouble("temperature_2m").roundToInt()}° ${sky(cur.getInt("weather_code"))} · $hi°/$lo° · $city"
            } catch (e: Exception) { null }
            main.post {
                if (text != null) sp.edit().putString("text", text).apply()
                done(text ?: cached(c) ?: "couldn't load weather · tap to retry")
            }
        }.start()
    }

    fun cityDialog(a: Activity, done: (String) -> Unit) {
        val input = Widgets.field(a, "city", city(a) ?: "")
        Ui.dialog(a).setTitle("weather for").setView(Widgets.padded(a, input))
            .setNegativeButton("cancel", null)
            .setPositiveButton("set") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                Thread {
                    val r = try {
                        JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(name, "UTF-8")))
                            .getJSONArray("results").getJSONObject(0)
                    } catch (e: Exception) { null }
                    main.post {
                        if (r == null) { Ui.toast(a, "couldn't find that city"); return@post }
                        p(a).edit().putString("city", r.getString("name").lowercase())
                            .putString("lat", r.getDouble("latitude").toString())
                            .putString("lon", r.getDouble("longitude").toString()).apply()
                        refresh(a, force = true, done)
                    }
                }.start()
            }.show()
    }

    private fun sky(code: Int) = when (code) {
        0 -> "clear"
        1, 2 -> "partly cloudy"
        3 -> "cloudy"
        45, 48 -> "fog"
        in 51..57 -> "drizzle"
        in 61..67, in 80..82 -> "rain"
        in 71..77, 85, 86 -> "snow"
        in 95..99 -> "storm"
        else -> ""
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        try {
            if (conn.responseCode != 200) throw Exception("weather error ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
