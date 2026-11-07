package com.combo.monkeycompanion.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.combo.monkeycompanion.engine.script.ScriptError
import com.combo.monkeycompanion.overlay.ScriptShowHub
import com.combo.monkeycompanion.overlay.UserShows
import com.combo.monkeycompanion.theme.Th

/**
 * v10.34 Shows: opened from long-press → 🎬 Show → 🎭 Shows. Play the bundled shows (Satan vs Angel) and your own;
 * add a show by pasting the script, picking a .show / .txt file (Storage Access Framework, no storage permission),
 * or pasting an https link (GitHub links work). A bad script shows its line number and is not saved.
 */
class ShowsActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var paste: EditText
    private lateinit var url: EditText
    private val ui = Handler(Looper.getMainLooper())
    /** Tests: the last message shown (errors include the line number). */
    var lastMessage: String? = null; private set

    companion object {
        const val PICK = 4711
        /** Play [e] on the overlay stage after this screen closes. Returns an error message or null. */
        fun play(ctx: Context, text: String): String? {
            val dm = ctx.resources.displayMetrics
            return ScriptShowHub.startResult(ctx.applicationContext, text = text, aspect = (dm.heightPixels.toFloat() / dm.widthPixels.coerceAtLeast(1)).coerceIn(1.2f, 3f))
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        col.addView(text("🎭 Shows", 22f, bold = true))
        col.addView(text("Tap ▶ to play. Tap the screen during a show to stop it.", 13f))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list)
        status = text("", 14f).apply { setTextColor(Color.rgb(0xFF, 0x8A, 0x80)); visibility = View.GONE }
        col.addView(status)

        col.addView(text("➕ Add a show", 18f, bold = true).apply { setPadding(0, dp(18), 0, dp(4)) })
        col.addView(text("Paste the whole script:", 13f))
        paste = EditText(this).apply {
            hint = "title: Wave hello\ncast: hero=monkey, pal=gajju\nat 0: hero enter left to 0.3 0.86 in 1.5\n…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 5; gravity = Gravity.TOP; typeface = Typeface.MONOSPACE; textSize = 13f; setTextColor(Th.WHITE); setHintTextColor(Color.GRAY)
        }
        col.addView(paste)
        col.addView(button("Save & play") { addText(paste.text.toString(), playNow = true) })
        col.addView(button("Save") { addText(paste.text.toString(), playNow = false) })
        col.addView(text("Or a link (GitHub or any https text file):", 13f).apply { setPadding(0, dp(12), 0, 0) })
        url = EditText(this).apply { hint = "https://github.com/you/repo/blob/main/my.show"; inputType = InputType.TYPE_TEXT_VARIATION_URI; setTextColor(Th.WHITE); setHintTextColor(Color.GRAY) }
        col.addView(url)
        col.addView(button("Fetch link") { fetch(url.text.toString()) })
        col.addView(button("Pick a .show / .txt file") {
            runCatching { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/*", "application/octet-stream")), PICK) }
                .onFailure { say("No file picker on this phone") }
        })
        setContentView(ScrollView(this).apply { setBackgroundColor(Th.INK); addView(col) })
        MaterialScreenPolish.apply(this, dark = true)
        refresh()
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != PICK || res != RESULT_OK) return
        val uri = data?.data ?: return
        val t = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().take(200_000).toByteArray().toString(Charsets.UTF_8) } }.getOrNull()
        if (t == null) { say("Couldn't read that file"); return }
        paste.setText(t)
        addText(t, playNow = false)
    }

    private fun refresh() {
        list.removeAllViews()
        for (e in UserShows.all(this)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
            row.addView(text((if (e.bundled) "⭐ " else "📝 ") + e.title, 16f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(small("▶") { val m = runCatching { e.text(this) }.getOrNull(); if (m == null) say("Couldn't read ${e.title}") else playLater(m) })
            if (!e.bundled) row.addView(small("🗑") { UserShows.delete(e); say("Deleted ${e.title}"); refresh() })
            list.addView(row)
        }
    }

    /** Saves [t] (if it parses) and optionally plays it. Public for tests. */
    fun addText(t: String, playNow: Boolean): Boolean {
        if (t.isBlank()) { say("Paste a script first"); return false }
        val e = try { UserShows.save(this, t) } catch (x: ScriptError) { say(UserShows.message(x)); return false }
        say("Saved “${e.title}” ✅"); refresh()
        if (playNow) playLater(t)
        return true
    }

    private fun fetch(link: String) {
        if (!link.trim().startsWith("https://")) { say("Use an https:// link"); return }
        say("Fetching…")
        Thread {
            val r = runCatching { UserShows.fetch(link) }
            ui.post {
                r.onSuccess { t -> paste.setText(t); addText(t, playNow = false) }
                    .onFailure { say("Couldn't fetch: ${it.message}") }
            }
        }.start()
    }

    private fun playLater(text: String) {
        UserShows.check(text)?.let { say(it); return }
        val app = applicationContext
        finish()
        ui.postDelayed({ play(app, text)?.let { Toast.makeText(app, it, Toast.LENGTH_LONG).show() } }, 350L)
    }

    private fun say(m: String) {
        lastMessage = m
        status.text = m; status.visibility = View.VISIBLE
        runCatching { Toast.makeText(this, m, Toast.LENGTH_LONG).show() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(s: String, size: Float, bold: Boolean = false) = TextView(this).apply { text = s; textSize = size; setTextColor(Th.WHITE); if (bold) typeface = Typeface.DEFAULT_BOLD }
    private fun button(s: String, on: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; setTextColor(Th.WHITE); background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Th.RED) }
        setOnClickListener { on() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
    }
    private fun small(s: String, on: () -> Unit) = Button(this).apply { text = s; isAllCaps = false; setOnClickListener { on() } }
}
