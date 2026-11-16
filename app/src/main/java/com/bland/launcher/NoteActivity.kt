package com.bland.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout

/** Quick capture: type a thought and send it to the Notion inbox. */
class NoteActivity : Activity() {
    private lateinit var input: EditText
    private var sending = false
    /** Set once the note is sent or cancelled, so leaving doesn't save it back as a draft. */
    private var discard = false
    private fun drafts() = getSharedPreferences("note", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = Ui.dp(this, 32)
        val root = Ui.vbox(this).apply {
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            setPadding(pad, Ui.dp(this@NoteActivity, 40), pad, Ui.dp(this@NoteActivity, 12))
        }
        root.addView(Ui.row(this, "capture → notion · ${Prefs.notionName(this).lowercase()}".uppercase(), 12f, Ui.GRAY, scaled = false).apply {
            letterSpacing = 0.1f
        })
        input = EditText(this).apply {
            hint = "what's on your mind?"
            setHintTextColor(Ui.DIM)
            setTextColor(Color.WHITE)
            textSize = 22f * Prefs.textScale(this@NoteActivity)
            typeface = Ui.face(this@NoteActivity)
            background = null
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        input.setText(drafts().getString("draft", ""))
        input.setSelection(input.text.length)
        root.addView(input, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(Ui.row(this, "cancel", 18f, Ui.GRAY).apply { setOnClickListener { clearDraft(); finish() } })
        row.addView(Ui.spacer(this))
        row.addView(Ui.row(this, "save to inbox", 18f).apply {
            setPadding(Ui.dp(this@NoteActivity, 20), Ui.dp(this@NoteActivity, 10), Ui.dp(this@NoteActivity, 20), Ui.dp(this@NoteActivity, 10))
            background = GradientDrawable().apply { cornerRadius = Ui.dpf(this@NoteActivity, 22f); setStroke(Ui.dp(this@NoteActivity, 1), Color.WHITE) }
            setOnClickListener { save() }
        })
        root.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)
        input.requestFocus()
    }

    // Pressing home closes this screen, so keep what was typed for next time.
    override fun onPause() {
        super.onPause()
        if (!discard) drafts().edit().putString("draft", input.text.toString()).apply()
    }

    private fun clearDraft() {
        discard = true
        drafts().edit().remove("draft").apply()
    }

    private fun save() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) { Ui.toast(this, "write something first"); return }
        if (!Notion.configured(this)) { Notion.setupDialog(this) { save() }; return }
        if (sending) return
        sending = true
        Outbox.flush(this)
        Notion.send(this, text) { ok, message ->
            sending = false
            clearDraft() // sent, or kept in the outbox for retry
            if (ok) {
                Ui.toast(this, message)
            } else {
                Outbox.add(this, text)
                Ui.toast(this, "$message — kept on your phone, will retry next time")
            }
            finish()
        }
    }
}
