package com.bland.launcher

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Sends quick notes into a Notion database (your second brain's inbox). */
object Notion {
    private const val API = "https://api.notion.com/v1"
    private const val VERSION = "2022-06-28"

    fun configured(c: Context) = Prefs.notionToken(c).isNotEmpty() && Prefs.notionDb(c).isNotEmpty()

    /** Accepts a database link or a bare id and returns the 32-character id. */
    fun parseId(input: String): String? {
        val clean = input.substringBefore("?").replace("-", "")
        return Regex("[0-9a-fA-F]{32}").findAll(clean).lastOrNull()?.value
    }

    /** Saves [text] as a new page. The first line is the title, the rest becomes the body. */
    fun send(c: Context, text: String, done: (ok: Boolean, message: String) -> Unit) {
        val app = c.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val result = try {
                val token = Prefs.notionToken(app)
                val db = Prefs.notionDb(app)
                val titleProp = titleProperty(token, db)
                val lines = text.trim().lines()
                val title = lines.first().take(200)
                val body = lines.drop(1).joinToString("\n").trim()

                val page = JSONObject()
                    .put("parent", JSONObject().put("database_id", db))
                    .put("properties", JSONObject().put(titleProp,
                        JSONObject().put("title", JSONArray().put(textObj(title)))))
                if (body.isNotEmpty()) {
                    val blocks = JSONArray()
                    body.chunked(1900).forEach {
                        blocks.put(JSONObject().put("object", "block").put("type", "paragraph")
                            .put("paragraph", JSONObject().put("rich_text", JSONArray().put(textObj(it)))))
                    }
                    page.put("children", blocks)
                }
                request("POST", "$API/pages", token, page.toString())
                true to "saved to ${Prefs.notionName(app).lowercase()}"
            } catch (e: Exception) {
                false to (e.message ?: "couldn't reach notion")
            }
            main.post { done(result.first, result.second) }
        }.start()
    }

    private fun textObj(s: String) = JSONObject().put("type", "text").put("text", JSONObject().put("content", s))

    /** Every Notion database has exactly one title column; find its name. */
    private fun titleProperty(token: String, db: String): String {
        val props = JSONObject(request("GET", "$API/databases/$db", token, null)).getJSONObject("properties")
        for (k in props.keys()) if (props.getJSONObject(k).optString("type") == "title") return k
        return "Name"
    }

    private fun request(method: String, url: String, token: String, body: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Notion-Version", VERSION)
        conn.setRequestProperty("Content-Type", "application/json")
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val msg = try { JSONObject(text).optString("message") } catch (e: Exception) { "" }
            throw Exception(when (code) {
                401 -> "notion rejected the secret. check it in settings."
                404 -> "notion can't see that database. share it with your integration."
                else -> msg.ifEmpty { "notion error $code" }
            })
        }
        return text
    }

    /** Asks for the integration secret and the inbox database link. */
    fun setupDialog(a: Activity, after: () -> Unit) {
        val pad = Ui.dp(a, 24)
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, Ui.dp(a, 8), pad, 0) }
        fun field(hint: String, value: String, secret: Boolean = false) = EditText(a).apply {
            this.hint = hint
            setText(value)
            setSingleLine()
            setTextColor(Ui.WHITE)
            setHintTextColor(Ui.DIM)
            if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            box.addView(this)
        }
        val token = field("integration secret (ntn_…)", Prefs.notionToken(a), secret = true)
        val db = field("inbox database link", Prefs.notionDb(a))
        val name = field("name shown on screen", Prefs.notionName(a))

        Ui.dialog(a)
            .setTitle("connect notion")
            .setMessage("1. notion.so/profile/integrations → new integration → copy the secret.\n" +
                "2. open your inbox database → ··· → connections → add the integration.\n" +
                "3. copy the database link (··· → copy link) and paste it below.")
            .setView(box)
            .setNegativeButton("cancel", null)
            .setPositiveButton("connect") { _, _ ->
                val id = parseId(db.text.toString())
                val t = token.text.toString().trim()
                if (t.isEmpty() || id == null) {
                    Ui.toast(a, "both the secret and a database link are needed")
                } else {
                    Prefs.setNotion(a, t, id, name.text.toString().trim().ifEmpty { "Inbox" })
                    Ui.toast(a, "notion connected")
                    after()
                }
            }
            .show()
    }
}

/** Notes that couldn't be sent yet; retried the next time a note is saved. */
object Outbox {
    private fun p(c: Context) = c.getSharedPreferences("outbox", Context.MODE_PRIVATE)
    fun all(c: Context): List<String> = try {
        val a = JSONArray(p(c).getString("notes", "[]")); (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) { emptyList() }
    fun set(c: Context, notes: List<String>) = p(c).edit().putString("notes", JSONArray(notes).toString()).apply()
    fun add(c: Context, note: String) = set(c, all(c) + note)

    fun flush(c: Context) {
        val pending = all(c)
        if (pending.isEmpty() || !Notion.configured(c)) return
        set(c, emptyList())
        for (note in pending) Notion.send(c, note) { ok, _ -> if (!ok) add(c, note) }
    }
}
