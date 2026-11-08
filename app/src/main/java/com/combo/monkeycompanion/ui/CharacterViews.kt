package com.combo.monkeycompanion.ui

import android.app.Activity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
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
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = com.combo.monkeycompanion.theme.Th.rgb(0xF2, 0x7A, 0x1C) }
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

/** Settings > Wardrobe (v10.38.17: Material 3): toggle each accessory of a character, pick styles, live preview. */
class WardrobeActivity : androidx.activity.ComponentActivity() {
    private var rev by androidx.compose.runtime.mutableIntStateOf(0)
    private var caption by androidx.compose.runtime.mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val who = CharacterRegistry.byId(intent.getStringExtra("character")) ?: CharacterRegistry.current
        setContent { SettaiTheme(this) { Screen(who, rev) } }
    }

    @androidx.compose.runtime.Composable
    private fun Screen(who: com.combo.monkeycompanion.character.Character, @Suppress("UNUSED_PARAMETER") r: Int) {
        val s = AppSettings(this)
        val wear = androidx.compose.runtime.remember { CharacterRegistry.wear(who) }
        fun save() { s.setWardrobeFor(who.id, wear.encode()); rev++ }
        var preview by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<CharacterPreviewView?>(null) }
        SettaiScreen("${who.name}'s wardrobe", onBack = { finish() }) {
            androidx.compose.material3.Card(androidx.compose.ui.Modifier.fillMaxWidth()) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx -> CharacterPreviewView(ctx, who, demo = true).also { v -> v.onMove = { m -> v.post { caption = "Now: $m" } }; preview = v } },
                    update = { it.invalidate() },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth().height(260.dp)
                )
            }
            if (caption.isNotBlank()) SNote(caption)
            SNote("Switch items on or off. Switched-off items vanish from the drawing and the animations (no skateboard = he walks; no umbrella = he just falls). The AI is told what you picked.")
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                for ((label, f) in listOf<Pair<String, () -> Unit>>("All on" to { wear.allOn() }, "Minimal" to { wear.allOff() }, "Defaults" to { wear.reset() })) {
                    androidx.compose.material3.OutlinedButton(onClick = { f(); save(); preview?.invalidate() }, modifier = androidx.compose.ui.Modifier.weight(1f)) {
                        androidx.compose.material3.Text(label)
                    }
                }
            }
            SCard("Items") {
                for (item in who.wardrobe) {
                    SSwitch(item.title, wear.has(item.id), item.note.ifBlank { null }) { wear.set(item.id, it); save(); preview?.invalidate() }
                    if (item.options.isNotEmpty() && wear.has(item.id)) {
                        val labels = item.options.map { when (it) { "top" -> "Top hat"; "party" -> "Party hat"; "lungi" -> "Lungi turban"; else -> it } }
                        SChoice("Style", labels, item.options.indexOf(wear.option(item.id)).coerceAtLeast(0)) { pos ->
                            wear.setOption(item.id, item.options[pos]); save(); preview?.invalidate()
                        }
                    }
                }
            }
        }
    }
}
