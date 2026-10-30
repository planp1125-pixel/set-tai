package com.combo.monkeycompanion.engine.show

import com.combo.monkeycompanion.brain.MonkeyState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * v10.13 Tom-and-Jerry chase show: Billa (cat) vs Chittu (rat). Pure + unit-tested.
 * A show = INTRO + 3-4 random beats + one of 4 endings (8P3·4 + 8P4·4 = 1344+6720 combos), 10-20 s.
 * The overlay windows read [frame] every frame and really move across the screen.
 * Uses its OWN Random (never the frame-loop RNG).
 */
enum class Role(val id: String) { CAT("billa"), RAT("chittu") }
enum class BeatKind { INTRO, EDGE_WRAP, MOUSE_HOLE, WALL_CLIMB, TAIL_BITE, DOSA_PAN, LADDU_TRICK, SLIP, TIPTOE }
enum class EndKind { RAT_WINS, CAT_BONKED, FRIENDS, CAT_BALL }
enum class FxKind { DUST, BANG, STARS, HOLE, PROP, HEARTS, STEAM }

/** [x] NaN = at the actor; else a screen fraction. */
data class Fx(val at: Float, val dur: Float, val role: Role, val kind: FxKind, val text: String = "", val x: Float = Float.NaN)
data class Line(val at: Float, val dur: Float, val role: Role, val text: String)

/** x: 0..1 of the usable screen width (can go past the edges while wrapping); lift: 0..1 of screen height above the floor. */
class Frame {
    var x = 0.5f; var lift = 0f; var state = MonkeyState.IDLE; var facing = 1f
    var sx = 1f; var sy = 1f; var rot = 0f; var visible = true
    fun set(x: Float, lift: Float, state: MonkeyState, facing: Float, sx: Float = 1f, sy: Float = 1f, rot: Float = 0f, visible: Boolean = true) {
        this.x = x; this.lift = lift; this.state = state; this.facing = facing; this.sx = sx; this.sy = sy; this.rot = rot; this.visible = visible
    }
}

class ChaseShow private constructor(val beats: List<BeatKind>, val ending: EndKind, private val rnd: Random, scale0: Float) {
    class Seg(val kind: BeatKind?, val end: EndKind?, val t0: Float, val dur: Float, val c0: Float, val r0: Float, val c1: Float, val r1: Float, val side: Float)

    val segments = ArrayList<Seg>()
    val lines = ArrayList<Line>()
    val fx = ArrayList<Fx>()
    val total: Float
    val signature get() = beats.joinToString(",") + "|" + ending

    init {
        val raw = listOf(BeatKind.INTRO) + beats
        var sum = raw.sumOf { baseDur(it).toDouble() }.toFloat() + 2.8f
        val k = if (sum * scale0 > 19.5f) 19.5f / sum else scale0
        var t = 0f; var c = 0.12f; var r = 0.62f
        for (b in raw) {
            val d = baseDur(b) * k
            val (c1, r1, side) = ends(b, c, r)
            val s = Seg(b, null, t, d, c, r, c1, r1, side); segments += s; script(s)
            t += d; c = c1; r = r1
        }
        val d = 2.8f * k
        val (c1, r1, side) = endEnds(ending, c, r)
        val s = Seg(null, ending, t, d, c, r, c1, r1, side); segments += s; script(s)
        total = t + d
        lines.sortBy { it.at }; fx.sortBy { it.at }
    }

    private fun baseDur(b: BeatKind) = when (b) {
        BeatKind.INTRO -> 2.6f; BeatKind.EDGE_WRAP -> 3.6f; BeatKind.MOUSE_HOLE -> 3.6f; BeatKind.WALL_CLIMB -> 3.8f
        BeatKind.TAIL_BITE -> 4.0f; BeatKind.DOSA_PAN -> 3.6f; BeatKind.LADDU_TRICK -> 3.4f; BeatKind.SLIP -> 3.2f; BeatKind.TIPTOE -> 3.2f
    }

    private fun away(c: Float, r: Float) = if (r >= c) 1f else -1f
    private fun cl(x: Float) = x.coerceIn(0.08f, 0.92f)

    private fun ends(b: BeatKind, c: Float, r: Float): Triple<Float, Float, Float> = when (b) {
        BeatKind.INTRO -> Triple(0.40f, 0.62f, 1f)
        BeatKind.EDGE_WRAP -> Triple(0.90f, 0.25f, 1f)
        BeatKind.MOUSE_HOLE -> Triple(0.45f, 0.85f, 1f)
        BeatKind.WALL_CLIMB -> { val we = if (r >= 0.5f) 1f else 0f; Triple(if (we > 0.5f) 0.86f else 0.14f, 0.5f, we) }
        BeatKind.TAIL_BITE -> { val f = away(c, r); Triple(cl(c + 0.12f * f), cl(c - 0.07f * f), f) }
        BeatKind.DOSA_PAN -> { val f = away(c, r); Triple(cl(r - 0.06f * f), cl(r + 0.28f * f), f) }
        BeatKind.LADDU_TRICK -> { val f = away(c, r); Triple(cl((c + r) / 2f), cl(r + 0.3f * f), f) }
        BeatKind.SLIP -> { val f = away(c, r); Triple(cl(c + 0.2f * f), cl(r + 0.25f * f), f) }
        BeatKind.TIPTOE -> { val f = away(c, r); Triple(cl(r - 0.08f * f), cl(r + 0.3f * f), f) }
    }
    private fun endEnds(e: EndKind, c: Float, r: Float): Triple<Float, Float, Float> = when (e) {
        EndKind.RAT_WINS -> Triple(c, r, away(c, r))
        EndKind.CAT_BONKED -> { val f = away(c, r); Triple(if (f > 0) 0.95f else 0.05f, cl(r - 0.15f * f), f) }
        EndKind.FRIENDS -> Triple(0.44f, 0.56f, 1f)
        EndKind.CAT_BALL -> { val f = if (c < 0.5f) 1f else -1f; Triple(cl(c + 0.5f * f), r, f) }
    }

    private fun pick(vararg s: String) = s[rnd.nextInt(s.size)]
    private fun say(s: Seg, u: Float, role: Role, text: String, dur: Float = 1.6f) { lines += Line(s.t0 + u * s.dur, dur, role, text) }
    private fun pop(s: Seg, u: Float, role: Role, kind: FxKind, dur: Float = 0.7f, text: String = "", x: Float = Float.NaN) { fx += Fx(s.t0 + u * s.dur, dur, role, kind, text, x) }

    private fun script(s: Seg) {
        val C = Role.CAT; val R = Role.RAT
        when (s.kind) {
            BeatKind.INTRO -> {
                pop(s, 0f, R, FxKind.PROP, s.dur * 0.8f, "🟠")
                say(s, 0.05f, C, pick("Hehe… saapadu ready 😼", "Shh… tiptoe tiptoe 😼"))
                pop(s, 0.78f, R, FxKind.BANG, 0.6f, "!")
                say(s, 0.8f, R, pick("Aiyo! Poonai!! 🐭", "Billa varaan! Odu! 🐭💨"), 1.3f)
            }
            BeatKind.EDGE_WRAP -> {
                pop(s, 0.48f, C, FxKind.STARS, 1.4f); pop(s, 0.46f, C, FxKind.DUST, 0.5f)
                say(s, 0.48f, C, pick("AIYOO en thalai! 😵", "Suvaru-ku eppadi vandhuchu?! 😵"))
                say(s, 0.75f, R, pick("Ingae irukken! 😜", "Screen round-u da! 🌍"), 1.2f)
            }
            BeatKind.MOUSE_HOLE -> {
                pop(s, 0f, R, FxKind.HOLE, s.dur, "", 0.5f)
                pop(s, 0.42f, R, FxKind.DUST, 0.5f)
                say(s, 0.55f, C, pick("Pidichitten!… illaye?? 🤨", "Kai-la onnum illa! 😾"))
                pop(s, 0.75f, R, FxKind.DUST, 0.5f)
                say(s, 0.78f, R, pick("Ta-taa! 😜", "Bye bye poonai! 👋"), 1.2f)
            }
            BeatKind.WALL_CLIMB -> {
                pop(s, 0.62f, C, FxKind.BANG, 0.6f, "?")
                say(s, 0.62f, C, pick("Enga poyitaan?? 🤔", "Mela-va? Keezha-va? 🤔"), 1.3f)
                pop(s, 0.98f, R, FxKind.DUST, 0.5f)
                say(s, 0.8f, R, pick("Wheee! 🐭", "Surprise! 😆"), 1.0f)
            }
            BeatKind.TAIL_BITE -> {
                pop(s, 0.2f, R, FxKind.DUST, 0.5f)
                pop(s, 0.5f, C, FxKind.BANG, 0.8f, "!!")
                say(s, 0.5f, C, "AAAAAH!! 🙀", 1.2f); say(s, 0.45f, R, pick("Kadi! 😁", "Nyam! 😁"), 0.9f)
                pop(s, 0.78f, C, FxKind.DUST, 0.5f)
            }
            BeatKind.DOSA_PAN -> {
                pop(s, 0f, C, FxKind.PROP, s.dur * 0.6f, "🍳")
                say(s, 0.4f, R, pick("Miss! 😜", "Kuniyaren! 😜"), 1.0f)
                pop(s, 0.62f, C, FxKind.BANG, 0.6f, "BOING"); pop(s, 0.62f, C, FxKind.STARS, 1.2f)
                pop(s, 0.62f, C, FxKind.PROP, s.dur * 0.38f, "🍳")
                say(s, 0.64f, C, pick("Dosa kal en mugathula! 🍳😵", "En mookku flat aayiduchu! 🍳"))
            }
            BeatKind.LADDU_TRICK -> {
                pop(s, 0.1f, R, FxKind.PROP, s.dur * 0.45f, "🟠", (s.c0 + s.r0) / 2f)
                say(s, 0.55f, C, pick("Laddu! Nyam… 😋", "Free laddu-va? 😋"), 1.0f)
                pop(s, 0.72f, C, FxKind.STEAM, 1.0f)
                say(s, 0.72f, C, pick("Kaaram!! Idhu milagai! 🌶️🔥", "Aaa vaai erigudhu! 🌶️🔥"), 1.4f)
                say(s, 0.85f, R, "Hehe, fake laddu! 😂", 1.0f)
            }
            BeatKind.SLIP -> {
                pop(s, 0.15f, R, FxKind.PROP, s.dur * 0.6f, "🍌", s.c0 + (s.r0 - s.c0) * 0.6f)
                pop(s, 0.5f, C, FxKind.DUST, 0.6f); pop(s, 0.5f, C, FxKind.BANG, 0.6f, "!")
                say(s, 0.52f, C, pick("Vazhakkai thol!! 🍌😵", "Waaah! Slip-u! 🍌"), 1.4f)
            }
            BeatKind.TIPTOE -> {
                say(s, 0.05f, C, pick("Mella… mella… 🤫", "Sathamae illa… 🤫"), 1.4f)
                pop(s, 0.6f, R, FxKind.BANG, 0.6f, "!"); pop(s, 0.6f, C, FxKind.BANG, 0.6f, "!")
                say(s, 0.68f, R, pick("Odu Chittu odu! 💨", "Ayyayyo! 💨"), 1.0f)
                pop(s, 0.7f, R, FxKind.DUST, 0.5f)
            }
            null -> when (s.end) {
                EndKind.RAT_WINS -> { pop(s, 0f, R, FxKind.PROP, s.dur, "🟠"); say(s, 0.05f, R, pick("Chittu wins! 🐭🏆", "Laddu enakku dhaan! 🏆")); say(s, 0.5f, C, "Adutha thadava paaru! 😾", 1.3f) }
                EndKind.CAT_BONKED -> { pop(s, 0.45f, C, FxKind.STARS, 1.5f); pop(s, 0.45f, C, FxKind.DUST, 0.5f); say(s, 0.48f, C, "Amma… 😵💫", 1.4f); say(s, 0.2f, R, "Olé! 😆", 0.8f) }
                EndKind.FRIENDS -> { pop(s, 0.4f, C, FxKind.HEARTS, 1.6f); pop(s, 0.4f, R, FxKind.HEARTS, 1.6f); say(s, 0.3f, C, "Sari sari, friends! 🤝", 1.3f); say(s, 0.6f, R, "Laddu half-half! 🟠😋", 1.1f) }
                EndKind.CAT_BALL -> { say(s, 0.1f, R, "Poonai ball! 😂⚽", 1.3f); pop(s, 0.1f, C, FxKind.DUST, 0.4f); pop(s, 0.5f, C, FxKind.DUST, 0.4f); pop(s, 0.9f, C, FxKind.DUST, 0.4f) }
                null -> {}
            }
        }
    }

    private fun ease(u: Float) = (u * u * (3f - 2f * u)).coerceIn(0f, 1f)
    private fun seg(u: Float, a: Float, b: Float) = ((u - a) / (b - a)).coerceIn(0f, 1f)
    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k
    private fun runBob(t: Float) = 1f + 0.06f * sin(t * 26f)
    private fun hop(k: Float, h: Float) = h * sin(PI.toFloat() * k)

    fun segmentAt(t: Float): Seg = segments.lastOrNull { t >= it.t0 } ?: segments.first()
    fun done(t: Float) = t >= total

    /** Where [role] is at show time [t] (seconds). */
    fun frame(role: Role, t: Float, out: Frame): Frame {
        val s = segmentAt(t.coerceAtMost(total - 0.001f))
        val u = ((t - s.t0) / s.dur).coerceIn(0f, 1f)
        val cat = role == Role.CAT
        val W = MonkeyState.WALK; val D = MonkeyState.DASH
        when (s.kind) {
            BeatKind.INTRO -> if (cat) out.set(lerp(s.c0, s.c1, ease(u)), 0f, W, 1f, 1.05f, 0.9f)
                else out.set(s.r0, 0f, if (u < 0.78f) MonkeyState.IDLE else MonkeyState.STARTLED, if (u < 0.78f) 1f else -1f, sy = if (u > 0.78f) 1.15f else 1f)
            BeatKind.EDGE_WRAP -> if (cat) {
                val k = seg(u, 0f, 0.46f)
                if (u < 0.46f) out.set(lerp(s.c0, 0.93f, ease(k)), 0f, D, 1f, runBob(t), 1f / runBob(t))
                else if (u < 0.62f) out.set(0.93f, 0f, MonkeyState.STARTLED, 1f, 0.72f, 1.25f, -8f)
                else out.set(lerp(0.93f, s.c1, seg(u, 0.62f, 1f)), 0f, MonkeyState.SAD, -1f)
            } else {
                if (u < 0.42f) out.set(lerp(s.r0, 1.2f, seg(u, 0f, 0.42f)), 0f, D, 1f, runBob(t), 1f / runBob(t))
                else if (u < 0.55f) out.set(1.25f, 0f, D, 1f, visible = false)
                else out.set(lerp(-0.2f, s.r1, ease(seg(u, 0.55f, 0.9f))), 0f, if (u < 0.9f) D else MonkeyState.GIGGLE, 1f)
            }
            BeatKind.MOUSE_HOLE -> if (cat) {
                if (u < 0.55f) out.set(lerp(s.c0, s.c1, ease(seg(u, 0f, 0.55f))), 0f, D, if (s.c1 >= s.c0) 1f else -1f, runBob(t), 1f / runBob(t))
                else if (u < 0.75f) out.set(s.c1, 0f, MonkeyState.ANGRY, 1f, 1.15f, 0.88f)
                else out.set(s.c1, 0f, MonkeyState.THINKING, 1f)
            } else {
                val f = if (0.5f >= s.r0) 1f else -1f
                if (u < 0.36f) out.set(lerp(s.r0, 0.5f, seg(u, 0f, 0.36f)), 0f, D, f)
                else if (u < 0.46f) { val k = seg(u, 0.36f, 0.46f); out.set(0.5f, -0.06f * k, D, f, 1f - 0.4f * k, 1f - 0.7f * k) }
                else if (u < 0.74f) out.set(0.5f, -0.1f, D, f, visible = false)
                else { val k = seg(u, 0.74f, 1f); out.set(s.r1, hop(k, 0.06f), MonkeyState.GIGGLE, -1f, 1f, 0.8f + 0.2f * k) }
            }
            BeatKind.WALL_CLIMB -> {
                val we = s.side; val wx = if (we > 0.5f) 0.98f else 0.02f; val f = if (we > 0.5f) 1f else -1f
                if (cat) {
                    if (u < 0.6f) out.set(lerp(s.c0, s.c1, ease(seg(u, 0f, 0.6f))), 0f, D, f, runBob(t), 1f / runBob(t))
                    else out.set(s.c1, 0f, if (u < 0.9f) MonkeyState.THINKING else MonkeyState.STARTLED, if (sin(t * 5f) > 0) 1f else -1f)
                } else {
                    if (u < 0.28f) out.set(lerp(s.r0, wx, seg(u, 0f, 0.28f)), 0f, D, f)
                    else if (u < 0.55f) out.set(wx, 0.45f * seg(u, 0.28f, 0.55f), W, f, rot = -90f * f)
                    else if (u < 0.75f) out.set(wx + 0.06f * f, 0.45f, W, f, visible = false)
                    else { val k = seg(u, 0.75f, 1f); out.set(lerp(wx, s.r1, k), lerp(0.45f, 0f, k) + hop(k, 0.12f), MonkeyState.JUMP, -f, rot = 360f * k * -f) }
                }
            }
            BeatKind.TAIL_BITE -> {
                val f = s.side
                if (cat) {
                    if (u < 0.5f) out.set(lerp(s.c0, s.c0 + 0.04f * f, u * 2f), 0f, if (u < 0.2f) D else MonkeyState.THINKING, if (u > 0.3f && sin(t * 6f) > 0) -f else f)
                    else if (u < 0.76f) { val k = seg(u, 0.5f, 0.76f); out.set(lerp(s.c0 + 0.04f * f, s.c1, k), hop(k, 0.35f), MonkeyState.STARTLED, f, 0.85f, 1.25f) }
                    else { val k = seg(u, 0.76f, 1f); out.set(s.c1, 0.07f * abs(sin(k * 3f * PI.toFloat())) * (1f - k), MonkeyState.SLEEP, f, 0.82f, 0.72f, 360f * k * 2f) }
                } else {
                    if (u < 0.2f) out.set(lerp(s.r0, s.r0 + 0.08f * f, u * 5f), 0f, D, f)
                    else if (u < 0.4f) out.set(s.r0, 0f, D, f, visible = false)
                    else if (u < 0.55f) out.set(s.r1, 0f, MonkeyState.GAG, f, 1.1f, 0.9f)
                    else out.set(s.r1, 0f, MonkeyState.GIGGLE, f)
                }
            }
            BeatKind.DOSA_PAN -> {
                val f = s.side
                if (cat) {
                    if (u < 0.4f) out.set(lerp(s.c0, s.c1, ease(seg(u, 0f, 0.4f))), 0f, D, f, runBob(t), 1f / runBob(t))
                    else if (u < 0.6f) out.set(s.c1, 0f, MonkeyState.ANGRY, f, rot = lerp(-25f, 25f, seg(u, 0.4f, 0.6f)) * f)
                    else out.set(s.c1, 0f, MonkeyState.STARTLED, f, 1.25f, 0.78f)
                } else {
                    if (u < 0.38f) out.set(s.r0, 0f, MonkeyState.IDLE, -f)
                    else if (u < 0.6f) out.set(s.r0, 0f, MonkeyState.SIT, -f, 1.15f, 0.6f)
                    else out.set(lerp(s.r0, s.r1, ease(seg(u, 0.6f, 1f))), 0f, if (u < 0.9f) D else MonkeyState.GIGGLE, f)
                }
            }
            BeatKind.LADDU_TRICK -> {
                val f = s.side
                if (cat) {
                    if (u < 0.55f) out.set(lerp(s.c0, s.c1, ease(seg(u, 0f, 0.55f))), 0f, W, f)
                    else if (u < 0.72f) out.set(s.c1, 0f, MonkeyState.HAPPY_PAT, f)
                    else out.set(s.c1, hop(seg(u, 0.72f, 0.9f), 0.08f), MonkeyState.ANGRY, if (sin(t * 14f) > 0) 1f else -1f, 1.1f, 0.92f)
                } else {
                    if (u < 0.15f) out.set(s.r0, 0f, MonkeyState.PLAYFUL, -f)
                    else if (u < 0.5f) out.set(lerp(s.r0, s.r1, ease(seg(u, 0.15f, 0.5f))), 0f, D, f)
                    else out.set(s.r1, 0f, MonkeyState.GIGGLE, -f)
                }
            }
            BeatKind.SLIP -> {
                val f = s.side
                if (cat) {
                    if (u < 0.5f) out.set(lerp(s.c0, s.c0 + (s.c1 - s.c0) * 0.6f, seg(u, 0f, 0.5f)), 0f, D, f, runBob(t), 1f / runBob(t))
                    else if (u < 0.75f) { val k = seg(u, 0.5f, 0.75f); out.set(lerp(s.c0 + (s.c1 - s.c0) * 0.6f, s.c1, k), hop(k, 0.12f), MonkeyState.FALLING, f, rot = -110f * k * f) }
                    else out.set(s.c1, 0f, MonkeyState.SAD, f, 1.2f, 0.8f, -90f * f * (1f - seg(u, 0.85f, 1f)))
                } else {
                    if (u < 0.6f) out.set(lerp(s.r0, s.r1, seg(u, 0f, 0.6f)), 0f, D, f)
                    else out.set(s.r1, 0f, MonkeyState.GIGGLE, -f)
                }
            }
            BeatKind.TIPTOE -> {
                val f = s.side
                if (cat) {
                    if (u < 0.6f) out.set(lerp(s.c0, s.c1, seg(u, 0f, 0.6f)), 0f, W, f, 1.06f, 0.88f + 0.04f * sin(t * 8f))
                    else if (u < 0.7f) out.set(s.c1, 0f, MonkeyState.STARTLED, f, 0.9f, 1.15f)
                    else out.set(lerp(s.c1, s.c1 + 0.15f * f, seg(u, 0.7f, 1f)), 0f, D, f, runBob(t), 1f / runBob(t))
                } else {
                    if (u < 0.6f) out.set(s.r0, 0f, MonkeyState.IDLE, f)
                    else if (u < 0.7f) out.set(s.r0, 0.03f, MonkeyState.STARTLED, -f, 0.9f, 1.2f)
                    else out.set(lerp(s.r0, s.r1, ease(seg(u, 0.7f, 1f))), 0f, D, f, runBob(t), 1f / runBob(t))
                }
            }
            null -> when (s.end!!) {
                EndKind.RAT_WINS -> if (cat) out.set(s.c1, 0f, MonkeyState.SAD, if (s.r1 > s.c1) 1f else -1f, 1f, 0.92f)
                    else out.set(s.r1, hop((u * 3f) % 1f, 0.04f), MonkeyState.DANCE, if (sin(t * 4f) > 0) 1f else -1f)
                EndKind.CAT_BONKED -> if (cat) {
                    if (u < 0.45f) out.set(lerp(s.c0, s.c1, ease(seg(u, 0f, 0.45f))), 0f, D, s.side, runBob(t), 1f / runBob(t))
                    else out.set(s.c1, 0f, MonkeyState.SAD, -s.side, 0.75f + 0.25f * seg(u, 0.45f, 1f), 1.25f - 0.25f * seg(u, 0.45f, 1f), -10f * s.side)
                } else out.set(lerp(s.r0, s.r1, seg(u, 0.1f, 0.3f)), if (u in 0.1f..0.3f) hop(seg(u, 0.1f, 0.3f), 0.06f) else 0f, MonkeyState.GIGGLE, s.side)
                EndKind.FRIENDS -> if (cat) out.set(lerp(s.c0, s.c1, ease(seg(u, 0f, 0.4f))), 0f, if (u < 0.4f) W else MonkeyState.HAPPY_PAT, 1f)
                    else out.set(lerp(s.r0, s.r1, ease(seg(u, 0f, 0.4f))), 0f, if (u < 0.4f) W else MonkeyState.HAPPY_PAT, -1f)
                EndKind.CAT_BALL -> if (cat) out.set(lerp(s.c0, s.c1, u), 0.08f * abs(sin(u * 4f * PI.toFloat())), MonkeyState.SLEEP, s.side, 0.8f, 0.75f, 540f * u * s.side)
                    else out.set(s.r1, 0f, MonkeyState.GIGGLE, if (s.c1 > s.r1) 1f else -1f)
            }
        }
        return out
    }

    companion object {
        val middleBeats = BeatKind.entries.filter { it != BeatKind.INTRO }

        /** A new show; never the same signature or the same first beat as [last]. */
        fun random(rnd: Random, last: ChaseShow? = null): ChaseShow {
            repeat(20) {
                val n = 3 + rnd.nextInt(2)
                val beats = middleBeats.shuffled(rnd).take(n)
                val end = EndKind.entries[rnd.nextInt(EndKind.entries.size)]
                val sig = beats.joinToString(",") + "|" + end
                if (last == null || (sig != last.signature && beats.first() != last.beats.first() && end != last.ending)) return ChaseShow(beats, end, rnd, 1f)
            }
            val beats = middleBeats.filter { it != last?.beats?.first() }.shuffled(rnd).take(3)
            return ChaseShow(beats, EndKind.entries.first { it != last?.ending }, rnd, 1f)
        }
        fun of(beats: List<BeatKind>, end: EndKind, seed: Long = 1L) = ChaseShow(beats, end, Random(seed), 1f)
        fun comboCount(): Int { val m = middleBeats.size; return (m * (m - 1) * (m - 2) + m * (m - 1) * (m - 2) * (m - 3)) * EndKind.entries.size }
    }
}
