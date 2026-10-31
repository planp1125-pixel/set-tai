package com.combo.monkeycompanion.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.ai.ChatMsg
import com.combo.monkeycompanion.powers.PendingAction

/**
 * The chat UI, shared by the floating overlay panel and the in-app Chat screen:
 * history, a Yes/No row for actions waiting for approval, and a text box.
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class ChatPanelView(
    context: Context,
    private val floating: Boolean,
    private val onClose: (() -> Unit)? = null,
) : LinearLayout(context), ChatHub.Listener {

    private val d = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private val brown = com.combo.monkeycompanion.theme.Th.rgb(0x1E, 0x22, 0x40)
    private val accent = com.combo.monkeycompanion.theme.Th.rgb(0x6C, 0x5C, 0xE7)
    private val cream = com.combo.monkeycompanion.theme.Th.rgb(0xF3, 0xF4, 0xFA)

    private val chipRow = LinearLayout(context).apply { orientation = HORIZONTAL; setPadding(0, dp(2), 0, dp(2)) }
    private val list = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(10), dp(6), dp(10), dp(6)) }
    private val scroll = ScrollView(context).apply { addView(list); isFillViewport = true }
    private val subtitle = TextView(context)
    private val input = EditText(context)
    private val send = Button(context)
    private val pendingRow = LinearLayout(context)
    private val pendingText = TextView(context)
    private val maxH = (resources.displayMetrics.heightPixels * if (floating) 0.42f else 1f).toInt()

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            setColor(cream)
            cornerRadius = if (floating) dp(22).toFloat() else 0f
            if (floating) setStroke(dp(2), com.combo.monkeycompanion.theme.Th.rgb(0xDD, 0xE0, 0xF0))
        }
        if (floating) elevation = dp(8).toFloat()

        // Header
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(6), dp(4))
        }
        val titles = LinearLayout(context).apply { orientation = VERTICAL }
        titles.addView(TextView(context).apply {
            text = "\uD83D\uDCAC Chat with Set-Tai"; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
        })
        subtitle.textSize = 12f; subtitle.setTextColor(com.combo.monkeycompanion.theme.Th.rgb(0x64, 0x6A, 0x8C))
        titles.addView(subtitle)
        header.addView(titles, LayoutParams(0, WRAP_CONTENT, 1f))
        if (onClose != null) header.addView(Button(context).apply {
            text = "\u2715"; textSize = 18f; setTextColor(brown); background = null
            setOnClickListener { onClose.invoke() }
        }, LayoutParams(dp(48), dp(44)))
        addView(header, LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        addView(scroll, LayoutParams(MATCH_PARENT, 0, 1f))

        // Pending action (confirm) row
        pendingRow.orientation = HORIZONTAL; pendingRow.gravity = Gravity.CENTER_VERTICAL
        pendingRow.setPadding(dp(12), dp(6), dp(12), dp(6))
        pendingRow.background = GradientDrawable().apply { setColor(com.combo.monkeycompanion.theme.Th.rgb(0xEA, 0xE8, 0xFF)); cornerRadius = dp(14).toFloat() }
        pendingText.textSize = 14f; pendingText.setTextColor(brown)
        pendingRow.addView(pendingText, LayoutParams(0, WRAP_CONTENT, 1f))
        pendingRow.addView(pill("Yes", com.combo.monkeycompanion.theme.Th.rgb(0x4C, 0xAF, 0x50)) { ChatHub.confirmPending(true) })
        pendingRow.addView(pill("No", com.combo.monkeycompanion.theme.Th.rgb(0xE5, 0x73, 0x73)) { ChatHub.confirmPending(false) })
        pendingRow.visibility = GONE
        addView(pendingRow, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(10), dp(4), dp(10), dp(4)) })

        // Input row
        // v4.2 quick-tap chips (context-aware first: "Is this worth watching?" on YouTube, "Find cheaper?" in shops...)
        addView(android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        }, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(8), dp(2), dp(8), 0) })
        fillChips(emptyList())
        val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(4), dp(10), dp(10)) }
        input.hint = "Talk to Settai…"
        input.textSize = 16f
        input.setTextColor(brown)
        input.maxLines = 4
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setPadding(dp(14), dp(10), dp(14), dp(10))
        input.background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(22).toFloat(); setStroke(dp(1), com.combo.monkeycompanion.theme.Th.rgb(0xDD, 0xE0, 0xF0)) }
        input.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { submit(); true } else false }
        row.addView(input, LayoutParams(0, WRAP_CONTENT, 1f))
        // v4 push-to-talk (only when the Voice power is on and the mic is granted)
        if (com.combo.monkeycompanion.agent.Voice.canListen(context)) row.addView(Button(context).apply {
            text = "🎤"; textSize = 18f; isAllCaps = false
            background = GradientDrawable().apply { setColor(com.combo.monkeycompanion.theme.Th.rgb(0xEA, 0xE8, 0xFF)); cornerRadius = dp(22).toFloat() }
            setOnClickListener { listen() }
        }, LayoutParams(dp(48), dp(44)).apply { leftMargin = dp(6) })
        send.text = "Send"; send.isAllCaps = false; send.setTextColor(Color.WHITE); send.textSize = 15f
        send.background = GradientDrawable().apply { setColor(accent); cornerRadius = dp(22).toFloat() }
        send.setOnClickListener { submit() }
        row.addView(send, LayoutParams(WRAP_CONTENT, dp(44)).apply { leftMargin = dp(8) })
        addView(row, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    /** [extra] (follow-ups after an answer) first, then the context chips for the app on screen, then the standard ones. */
    private fun fillChips(extra: List<com.combo.monkeycompanion.agent.QuickChip>) {
        chipRow.removeAllViews()
        val all = (extra + ChatHub.contextChips() + com.combo.monkeycompanion.agent.QuickChip.STANDARD).distinctBy { it.id + it.arg }
        for (c in all) chipRow.addView(TextView(context).apply {
            text = c.label; textSize = 13f; setTextColor(brown)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply { setColor(com.combo.monkeycompanion.theme.Th.rgb(0xFF, 0xE9, 0xC9)); cornerRadius = dp(16).toFloat(); setStroke(dp(1), com.combo.monkeycompanion.theme.Th.rgb(0xDD, 0xE0, 0xF0)) }
            setOnClickListener { if (!ChatHub.busy) ChatHub.quick(c) }
        }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = dp(6) })
    }

    override fun onChips(chips: List<com.combo.monkeycompanion.agent.QuickChip>) = fillChips(chips)

    private fun pill(label: String, color: Int, click: () -> Unit) = Button(context).apply {
        text = label; isAllCaps = false; setTextColor(Color.WHITE); textSize = 14f
        background = GradientDrawable().apply { setColor(color); cornerRadius = dp(18).toFloat() }
        setOnClickListener { click() }
        layoutParams = LayoutParams(dp(64), dp(38)).apply { leftMargin = dp(6) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val hs = if (floating) MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.EXACTLY) else heightMeasureSpec
        super.onMeasure(widthMeasureSpec, hs)
    }

    fun focusInput() {
        input.requestFocus()
        input.postDelayed({
            context.getSystemService(InputMethodManager::class.java)?.showSoftInput(input, 0)
        }, 150)
    }

    private fun listen() {
        val v = com.combo.monkeycompanion.agent.Voice
        if (v.listening) { v.cancelListen(); input.hint = "Talk to Settai…"; return }
        input.hint = "🎤 Listening…"
        v.listen(context, onPartial = { input.setText(it) }, onMiss = { m -> if (m != com.combo.monkeycompanion.agent.Voice.Miss.CANCELLED) android.widget.Toast.makeText(context, com.combo.monkeycompanion.agent.Voice.missLine(m), android.widget.Toast.LENGTH_SHORT).show() }) { heard ->
            input.hint = "Talk to Settai…"
            if (heard == null) { input.setText(""); return@listen }
            input.setText(heard); submit(voice = true)
        }
    }

    private fun submit(voice: Boolean = false) {
        val t = input.text.toString().trim()
        if (t.isEmpty() || ChatHub.busy) return
        input.setText("")
        ChatHub.send(t, voice = voice)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (floating && event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onClose?.invoke(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ChatHub.addListener(this)
        fillChips(emptyList())
        rebuild()
        onPending(ChatHub.pending)
    }

    override fun onDetachedFromWindow() { ChatHub.removeListener(this); super.onDetachedFromWindow() }

    private fun rebuild() {
        subtitle.text = "Brain: " + ChatHub.brainLabel()
        list.removeAllViews()
        val msgs = ChatHub.memory.all()
        if (msgs.isEmpty()) list.addView(bubbleView(ChatMsg(false, "Hi! Ask me anything, or tell me to set a timer, open an app, check the battery… 🍌", note = true)))
        for (m in msgs) list.addView(bubbleView(m))
        send.isEnabled = !ChatHub.busy; send.alpha = if (ChatHub.busy) 0.5f else 1f
        scrollToEnd()
    }

    private fun bubbleView(m: ChatMsg): View {
        val tv = TextView(context).apply {
            text = m.text.ifEmpty { "…" }
            textSize = 15f
            setTextColor(if (m.fromUser) Color.WHITE else brown)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setTextIsSelectable(!floating)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (m.fromUser) accent else if (m.note) com.combo.monkeycompanion.theme.Th.rgb(0xEA, 0xE8, 0xFF) else Color.WHITE)
            }
            maxWidth = (resources.displayMetrics.widthPixels * 0.75f).toInt()
        }
        if (!m.fromUser && !m.note && m.text.isNotBlank()) {
            // v4: "Report this reply" under every AI reply (saved on the phone only).
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            col.addView(tv, LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            col.addView(TextView(context).apply {
                text = "⚑ Report"; textSize = 11f; setTextColor(com.combo.monkeycompanion.theme.Th.rgb(0xA8, 0x84, 0x60)); setPadding(dp(10), dp(2), dp(10), dp(4))
                contentDescription = "Report this reply"
                setOnClickListener {
                    com.combo.monkeycompanion.agent.ReplyReports.report(context, m.text)
                    text = "⚑ Reported — thanks!"; isEnabled = false
                }
            }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            return FrameLayout(context).apply {
                addView(col, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.START))
                layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4); bottomMargin = dp(4) }
                tag = tv
            }
        }
        return FrameLayout(context).apply {
            addView(tv, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, if (m.fromUser) Gravity.END else Gravity.START))
            layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4); bottomMargin = dp(4) }
            tag = tv
        }
    }

    private fun scrollToEnd() = scroll.post { scroll.fullScroll(View.FOCUS_DOWN); input.requestFocus() }

    override fun onHistoryChanged() = rebuild()

    override fun onThinking() { send.isEnabled = false; send.alpha = 0.5f; findLive()?.text = "Hmm…" }

    override fun onReply(text: String, done: Boolean) {
        findLive()?.text = text.ifEmpty { "…" }
        if (done) { send.isEnabled = true; send.alpha = 1f; subtitle.text = "Brain: " + ChatHub.brainLabel() }
        scrollToEnd()
    }

    override fun onError(message: String) { subtitle.text = "⚠ $message" }

    override fun onPending(action: PendingAction?) {
        pendingRow.visibility = if (action == null) GONE else VISIBLE
        pendingText.text = action?.question ?: ""
    }

    private fun findLive(): TextView? {
        val last = (0 until list.childCount).reversed().map { list.getChildAt(it) }.firstOrNull()
        return (last?.tag as? TextView)
    }
}
