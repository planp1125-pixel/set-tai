package com.combo.monkeycompanion.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.combo.monkeycompanion.ai.AppSettings
import com.combo.monkeycompanion.brain.Gag
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose

/**
 * v4.1 live character preview: draws [who] with its wardrobe, animated at ~20 fps only while on
 * screen. [demo] cycles through moves that show the wardrobe items off (skate, chicken, umbrella...).
 */
class CharacterPreviewView(ctx: Context, who: Character, private val demo: Boolean = false) : View(ctx) {
    var who: Character = who
        set(v) { field = v; renderer = v.newRenderer(); invalidate() }
    private var renderer = who.newRenderer()
    private val rig = MonkeyRig()
    private val pose = Pose()
    private val anim = AnimContext()
    private val t0 = SystemClock.uptimeMillis()
    var picked = false
        set(v) { field = v; invalidate() }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.rgb(0xF2, 0x7A, 0x1C) }
    private val r = RectF()
    /** Name of the move playing (demo), for a caption. */
    var onMove: ((String) -> Unit)? = null
    private var lastMove = ""

    private fun step(t: Float): Pair<MonkeyState, Float> {
        if (!demo) return MonkeyState.IDLE to t
        val slot = (t / 3f).toInt() % MOVES.size
        val st = t - (t / 3f).toInt() * 3f
        val (name, state, gag) = MOVES[slot]
        if (gag != null) anim.gag = gag
        if (name != lastMove) { lastMove = name; onMove?.invoke(name) }
        return state to st
    }

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() - t0) / 1000f
        val wear = CharacterRegistry.wear(who)
        renderer.wear = wear
        val (state, st) = step(t)
        who.fill(pose, state, t, st, anim, wear)
        var scale = height / 255f * com.combo.monkeycompanion.engine.CharacterProfiles.of(who).drawScale
        var ay = height * 0.90f
        rig.layout(pose, width / 2f, ay, scale, 1f)
        val ht = FloatArray(2)
        rig.headTop(ht)
        if (ht[1] < 8f) {
            val span = (ay - ht[1]).coerceAtLeast(10f)
            scale *= ((ay - 8f) / span).coerceIn(0.65f, 1f)
            rig.layout(pose, width / 2f, ay, scale, 1f)
            rig.headTop(ht)
            if (ht[1] < 8f) {
                ay += (8f - ht[1])
                rig.layout(pose, width / 2f, ay, scale, 1f)
            }
        }
        if (picked) { r.set(4f, 4f, width - 4f, height - 4f); c.drawRoundRect(r, 24f, 24f, frame) }
        renderer.draw(c, rig, pose, t)
        if (isShown) postInvalidateDelayed(50L)
    }

    companion object {
        private val MOVES = listOf(
            Triple("idle", MonkeyState.IDLE, null),
            Triple("walk (skateboard if on)", MonkeyState.WALK, null),
            Triple("utility belt gadget", MonkeyState.GAG, Gag.JUGGLE),
            Triple("poked: rubber chicken", MonkeyState.GIGGLE, null),
            Triple("dropped: umbrella float", MonkeyState.FALLING, null),
            Triple("surprise: eyes pop", MonkeyState.STARTLED, null),
            Triple("sneeze: moustache flies", MonkeyState.GAG, Gag.SNEEZE),
            Triple("crow steals the hat", MonkeyState.GAG, Gag.CHASE_FLY),
            Triple("banana phone", MonkeyState.GAG, Gag.SELFIE),
            Triple("dance", MonkeyState.DANCE, null),
        )
    }
}

/** Settings > Wardrobe: toggle each accessory of a character, pick styles, live preview. */
class WardrobeActivity : Activity() {
    private val brown = Color.rgb(0x5A, 0x36, 0x18)
    private val soft = Color.rgb(0x8A, 0x6A, 0x4A)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val s = AppSettings(this)
        val who = CharacterRegistry.byId(intent.getStringExtra("character")) ?: CharacterRegistry.current
        val wear = CharacterRegistry.wear(who)
        fun save() = s.setWardrobeFor(who.id, wear.encode())

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(28)) }
        col.addView(TextView(this).apply { text = "${who.name}'s wardrobe"; textSize = 24f; setTextColor(brown); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER_HORIZONTAL })
        val caption = TextView(this).apply { textSize = 12f; setTextColor(soft); gravity = Gravity.CENTER_HORIZONTAL }
        val preview = CharacterPreviewView(this, who, demo = true).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(260))
            onMove = { m -> caption.post { caption.text = "Now: $m" } }
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.rgb(0xFF, 0xF4, 0xE2)) }
        }
        col.addView(preview); col.addView(caption)
        col.addView(TextView(this).apply {
            text = "Switch items on or off. Switched-off items vanish from the drawing and from the animations (no skateboard = he walks; no umbrella = he just falls). The AI is told what you picked."
            textSize = 13f; setTextColor(soft); setPadding(0, dp(6), 0, dp(6))
        })
        val boxes = ArrayList<CheckBox>()
        fun refresh() { boxes.forEach { b -> b.isChecked = wear.has(b.tag as String) }; preview.invalidate() }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun small(label: String, f: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; setOnClickListener { f(); save(); refresh() }
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        row.addView(small("All on") { wear.allOn() }); row.addView(small("Minimal") { wear.allOff() }); row.addView(small("Defaults") { wear.reset() })
        col.addView(row)
        for (item in who.wardrobe) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val cb = CheckBox(this).apply {
                tag = item.id; text = item.title + (if (item.note.isNotBlank()) "  ·  ${item.note}" else ""); textSize = 15f; setTextColor(brown)
                isChecked = wear.has(item.id)
                setOnCheckedChangeListener { _, c -> wear.set(item.id, c); save(); preview.invalidate() }
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }
            boxes.add(cb); line.addView(cb)
            if (item.options.isNotEmpty()) {
                val labels = item.options.map { when (it) { "top" -> "Top hat"; "party" -> "Party hat"; "lungi" -> "Lungi turban"; else -> it } }
                line.addView(Spinner(this).apply {
                    adapter = ArrayAdapter(this@WardrobeActivity, android.R.layout.simple_spinner_dropdown_item, labels)
                    setSelection(item.options.indexOf(wear.option(item.id)).coerceAtLeast(0))
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { wear.setOption(item.id, item.options[pos]); save(); preview.invalidate() }
                        override fun onNothingSelected(p: AdapterView<*>?) {}
                    }
                })
            }
            col.addView(line)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(0xFF, 0xF8, 0xEE)); addView(col) })
    }
}
