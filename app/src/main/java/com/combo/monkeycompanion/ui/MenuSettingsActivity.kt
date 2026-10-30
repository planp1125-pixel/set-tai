package com.combo.monkeycompanion.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.combo.monkeycompanion.ai.AppSettings
import com.combo.monkeycompanion.brain.LongPressMenu
import com.combo.monkeycompanion.brain.Superpowers
import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.speech.CloudBubble

/**
 * v4.4 Settings > Long-press menu: switch items on/off, drag (≡) to reorder, pin this character's powers.
 * The first 6 "on" items make the main ring; the rest go into "… More" (Quit always stays reachable).
 * A live preview of the ring sits on top.
 */
class MenuSettingsActivity : Activity() {
    private val brown = Color.rgb(0x1E, 0x22, 0x40)
    private val soft = Color.rgb(0x64, 0x6A, 0x8C)
    private val accent = Color.rgb(0x6C, 0x5C, 0xE7)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private lateinit var s: AppSettings
    private lateinit var who: Character
    private lateinit var cfg: LongPressMenu.Config
    private var pins: MutableList<String> = mutableListOf()
    private lateinit var preview: RingPreview
    private lateinit var moreLine: TextView
    private lateinit var list: DragList

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = AppSettings(this)
        runCatching { s.applyCharacter() }
        who = CharacterRegistry.current
        load()
        setContentView(build())
    }

    private fun load() {
        cfg = LongPressMenu.withPowers(s.menuConfig, LongPressMenu.powerRing(who.powers))
        pins = s.menuPins(who.id).toMutableList()
    }

    private fun visible(): List<String> {
        val mine = LongPressMenu.powerRing(who.powers).map { LongPressMenu.pin(it) }.toSet()
        return cfg.order.filter { !LongPressMenu.isPower(it) || it in mine }
    }

    private fun isOn(id: String) = if (LongPressMenu.isPower(id)) LongPressMenu.powerId(id) in pins else id !in cfg.off

    private fun save() {
        s.menuConfig = cfg
        s.setMenuPins(who.id, pins)
        refreshPreview()
    }

    private fun ring() = LongPressMenu.layout(cfg, pins, who.powers.map { it.id }, context = true)

    private fun refreshPreview() {
        val r = ring()
        preview.show(r.main.map { entry(it) })
        moreLine.text = if (r.more.isEmpty()) "Nothing in More." else "In … More: " + r.more.joinToString(" · ") { entry(it).second.let { l -> if (l == "✕") "Quit" else l } }
        // Badges on rows.
        for (i in 0 until list.childCount) (list.getChildAt(i).tag as? Row)?.let { badge(it, r) }
    }

    private fun entry(id: String): Pair<String, String> = when {
        id == LongPressMenu.MORE -> "⋯" to "More"
        id == "quit" -> "✕" to "✕"
        LongPressMenu.isPower(id) -> {
            val pid = LongPressMenu.powerId(id)
            LongPressMenu.powerIcon(pid) to LongPressMenu.shortName(pid, Superpowers.byId(pid)?.title ?: pid)
        }
        else -> LongPressMenu.Item.of(id)?.let { it.icon to it.label } ?: ("❔" to id)
    }

    private class Row(val id: String, val view: LinearLayout, val badge: TextView, val toggle: Switch)

    private fun badge(r: Row, ring: LongPressMenu.Ring) {
        val (t, c) = when {
            r.id in ring.main -> "Main ring" to Color.rgb(0x2E, 0x7D, 0x32)
            r.id in ring.more -> (if (!isOn(r.id)) "In More (always)" else "In More") to Color.rgb(0xB2, 0x6A, 0x00)
            else -> "Off" to Color.rgb(0x9E, 0x9E, 0x9E)
        }
        r.badge.text = t; r.badge.setTextColor(c)
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun row(id: String): LinearLayout {
        val (icon, label0) = entry(id)
        val label = if (id == "quit") "Quit" else label0
        val help = if (LongPressMenu.isPower(id)) "Pin ${who.name}'s ${Superpowers.byId(LongPressMenu.powerId(id))?.title ?: label} to the ring (this character only)"
            else LongPressMenu.Item.of(id)?.help.orEmpty() + if (id == "quit") ". Off = it still waits in More" else ""
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(8), dp(4)); minimumHeight = dp(60)
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE); setStroke(dp(1), Color.rgb(0xF0, 0xDF, 0xC8)) }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) }
        }
        v.addView(TextView(this).apply {
            text = "≡"; textSize = 24f; setTextColor(soft); gravity = Gravity.CENTER
            contentDescription = "Drag to reorder $label"
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(52))
        })
        v.addView(TextView(this).apply { text = icon; textSize = 22f; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(40), WRAP_CONTENT) })
        val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply { text = label; textSize = 16f; setTextColor(brown); setTypeface(typeface, Typeface.BOLD) })
        val badge = TextView(this).apply { textSize = 11f; setPadding(dp(8), 0, 0, 0) }
        top.addView(badge)
        mid.addView(top)
        mid.addView(TextView(this).apply { text = help; textSize = 12f; setTextColor(soft) })
        v.addView(mid)
        val sw = Switch(this).apply {
            isChecked = isOn(id); minimumWidth = dp(48); minimumHeight = dp(48)
            setOnCheckedChangeListener { _, on ->
                if (LongPressMenu.isPower(id)) { val pid = LongPressMenu.powerId(id); pins.remove(pid); if (on) pins.add(pid) }
                else cfg = LongPressMenu.toggle(cfg, id, on)
                save()
            }
        }
        v.addView(sw)
        v.tag = Row(id, v, badge, sw)
        return v
    }

    private fun build(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(28)); setBackgroundColor(Color.rgb(0xF3, 0xF4, 0xFA)) }
        col.addView(TextView(this).apply { text = "Long-press menu"; textSize = 24f; setTextColor(brown); setTypeface(typeface, Typeface.BOLD) })
        col.addView(TextView(this).apply {
            text = "Long-press ${who.name} to open it. Switch items on or off and drag ≡ to reorder. The first ${LongPressMenu.MAX_MAIN} make the ring; the rest go into “… More”. Power pins are for ${who.name} only."
            textSize = 13f; setTextColor(soft)
        })
        preview = RingPreview(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(205)).apply { topMargin = dp(6) } }
        col.addView(preview)
        moreLine = TextView(this).apply { textSize = 12f; setTextColor(soft); gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 0, 0, dp(8)) }
        col.addView(moreLine)
        list = DragList(this, dp(48)) { from, to ->
            cfg = LongPressMenu.move(cfg, visible(), from, to); save()
        }
        list.orientation = LinearLayout.VERTICAL
        for (id in visible()) list.addView(row(id))
        col.addView(list)
        col.addView(Button(this).apply {
            text = "Reset to defaults"; isAllCaps = false; setTextColor(brown); minimumHeight = dp(48)
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(Color.WHITE); setStroke(dp(2), Color.rgb(0xDD, 0xE0, 0xF0)) }
            setOnClickListener { s.resetMenu(); load(); list.removeAllViews(); for (id in visible()) list.addView(row(id)); refreshPreview() }
        })
        refreshPreview()
        return ScrollView(this).apply { addView(col); isFillViewport = true; setBackgroundColor(Color.rgb(0xF3, 0xF4, 0xFA)) }
    }

    /** The ring as Settai shows it (same drawing code), above a small character silhouette. */
    class RingPreview(ctx: Context) : View(ctx) {
        private val d = ctx.resources.displayMetrics.density
        private val bubble = CloudBubble(d, 15f * d)
        fun show(items: List<Pair<String, String>>) { bubble.setArc(items.map { it.first }, items.map { it.second }); invalidate() }
        override fun onDraw(c: Canvas) {
            if (!bubble.arcMode) return
            val sc = minOf(1f, width / bubble.width, height / bubble.height)
            c.save(); c.translate((width - bubble.width * sc) / 2f, (height - bubble.height * sc) / 2f); c.scale(sc, sc)
            bubble.draw(c, 0f, 0f, bubble.width / 2f, bubble.height + 40f, 1f, 1f)
            c.restore()
        }
    }

    /**
     * A vertical list whose rows can be dragged by their left handle (the first [handleW] px).
     * The list keeps the touch, so rows can move under the finger; [onMove] reports (from, to) on release.
     */
    class DragList(ctx: Context, private val handleW: Int, private val onMove: (Int, Int) -> Unit) : LinearLayout(ctx) {
        private var drag: View? = null
        private var from = -1
        private var lastY = 0f
        private var offset = 0f

        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_DOWN || e.x > handleW + paddingLeft) return false
            val v = (0 until childCount).map { getChildAt(it) }.firstOrNull { e.y >= it.top && e.y < it.bottom } ?: return false
            drag = v; from = indexOfChild(v); lastY = e.y; offset = 0f
            v.elevation = 8f * resources.displayMetrics.density; v.alpha = 0.9f
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            val v = drag ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    offset += e.y - lastY; lastY = e.y
                    var i = indexOfChild(v)
                    // Crossed half a neighbour: swap places.
                    while (offset > 0 && i < childCount - 1 && offset > getChildAt(i + 1).height / 2f) {
                        val n = getChildAt(i + 1); val h = n.height
                        removeViewAt(i + 1); addView(n, i); i++; offset -= h
                    }
                    while (offset < 0 && i > 0 && -offset > getChildAt(i - 1).height / 2f) {
                        val n = getChildAt(i - 1); val h = n.height
                        removeViewAt(i - 1); addView(n, i); i--; offset += h
                    }
                    v.translationY = offset
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.translationY = 0f; v.elevation = 0f; v.alpha = 1f
                    val to = indexOfChild(v); drag = null
                    if (to != from && from >= 0) onMove(from, to)
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            return true
        }
    }
}
