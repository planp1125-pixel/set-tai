package com.combo.monkeycompanion.engine.show

import com.combo.monkeycompanion.brain.MonkeyState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * v10.21 Show 2.0: a short cartoon EPISODE, authored like an animator's dope sheet.
 *
 * Every actor (Billa, Chittu and each prop) has a [Track]: a list of timed segments. Each segment moves the actor
 * from where the last one ended (never a jump) with a cartoon ease: slow-in/out, ZIP (a fast start that hits the
 * mark and stops dead), wind-ups, holds and freeze-frame "takes". Faces (eyes, brows, mouth, tears) are overrides
 * on top of the normal animation. Lines, effects and sound cues sit on the same clock.
 *
 * Coordinates: x in screen-width units (0 = left edge, 1 = right edge); y = height ABOVE THE FLOOR in screen-width
 * units (0 = standing on the floor). Everything is a pure function of time: no Random, no per-frame state.
 */
enum class Ease { LINEAR, IN_OUT, ZIP, IN, BACK }

/** Face / body overrides applied after the state's normal animation. NaN = leave it as the animation drew it. */
class Expr(
    val eye: Float = Float.NaN, val happy: Float = Float.NaN, val brow: Float = Float.NaN,
    val mouth: Float = Float.NaN, val smile: Float = Float.NaN, val lookX: Float = Float.NaN, val lookY: Float = Float.NaN,
    val head: Float = Float.NaN, val anger: Float = Float.NaN, val blush: Float = Float.NaN,
    val armL: Float = Float.NaN, val armR: Float = Float.NaN, val ear: Float = Float.NaN, val zzz: Float = Float.NaN,
    /** Tremble amplitude (fear, rage, "held" impacts). */
    val shake: Float = 0f,
    /** Tears run down the cheeks. */
    val tear: Boolean = false,
    /** Sweat drops fly off the head (nervous). */
    val sweat: Boolean = false,
    /** Chewing: the mouth opens and closes. */
    val munch: Boolean = false,
    /** The head shakes no / dizzily. */
    val headShake: Float = 0f,
) {
    companion object {
        val NONE = Expr()
        /** Looking straight at the viewer, the classic "take". */
        fun take(eye: Float = 1.25f, smile: Float = Float.NaN, brow: Float = Float.NaN, mouth: Float = Float.NaN) =
            Expr(eye = eye, lookX = 0f, lookY = 0f, head = 0f, smile = smile, brow = brow, mouth = mouth)
    }
}

/** One stretch of a track. Positions are blended from (x0, y0) to (x1, y1) with [ease]. */
class Seg(
    val t0: Float, val dur: Float,
    val x0: Float, val x1: Float, val y0: Float, val y1: Float,
    val ease: Ease, val state: MonkeyState, val face: Float,
    val sx0: Float, val sx1: Float, val sy0: Float, val sy1: Float, val rot0: Float, val rot1: Float,
    /** The squash / rotate pivot, in rig units from the feet (x toward the facing side, y up). */
    val pvx: Float, val pvy: Float,
    val visible: Boolean, val expr: Expr, val freeze: Boolean,
    /** Arc height (width units) over the segment, e.g. a jump. */
    val hop: Float,
    /** Tiptoe bounce height (width units), repeated each step. */
    val bounce: Float,
) {
    val t1 get() = t0 + dur
    /** Distance run before this segment (for leg phase), filled by the track. */
    var d0 = 0f
    /** When the current state began (a run of segments with the same state), filled by the track. */
    var st0 = 0f
}

/** A prop held by an actor between [t0] and [t1], offset (width units) from the actor's feet. */
class Carry(val t0: Float, val t1: Float, val parent: String, val dx: Float, val dy: Float, val rot: Float = 0f, val faceSide: Boolean = true)

/** Where an actor is at one moment. */
class Snap {
    var x = 0f; var y = 0f; var state = MonkeyState.IDLE; var face = 1f
    var sx = 1f; var sy = 1f; var rot = 0f; var pvx = 0f; var pvy = 0f
    var visible = true; var expr: Expr = Expr.NONE
    /** Animation clock for the body (frozen on "takes", stride-locked while running). */
    var anim = 0f
    /** Seconds since this state began (for one-shot animations like startled / jump). */
    var st = 0f
    var dist = 0f
    /** Horizontal speed (width units / s). */
    var vx = 0f
}

class Track(val id: String) {
    val segs = ArrayList<Seg>()
    val carries = ArrayList<Carry>()
    // the "pen": where the last segment ended
    var t = 0f; private set
    var x = 0f; private set
    var y = 0f; private set
    var face = 1f; private set
    private var sx = 1f; private var sy = 1f; private var rot = 0f
    private var pvx = 0f; private var pvy = 0f
    private var vis = true

    /** Where the actor starts (before the first segment). */
    fun place(x: Float, y: Float = 0f, face: Float = 1f, visible: Boolean = true): Track {
        this.x = x; this.y = y; this.face = face; vis = visible; return this
    }

    private fun add(
        dur: Float, x1: Float, y1: Float, state: MonkeyState, ease: Ease, face: Float, expr: Expr?, freeze: Boolean,
        sx1: Float, sy1: Float, rot1: Float, hop: Float, bounce: Float, visible: Boolean, pvx: Float, pvy: Float,
    ): Track {
        val d = dur.coerceAtLeast(0.001f)
        segs += Seg(t, d, x, x1, y, y1, ease, state, face, sx, sx1, sy, sy1, rot, rot1, pvx, pvy, visible, expr ?: Expr.NONE, freeze, hop, bounce)
        t += d; x = x1; y = y1; this.face = face; sx = sx1; sy = sy1; vis = visible; this.pvx = pvx; this.pvy = pvy
        // a full turn ends upright: carry on from 0 so the next segment doesn't spin back
        rot = if (abs(rot1 % 360f) < 0.01f) 0f else rot1
        return this
    }

    /** Stay put (optionally frozen like a held cartoon drawing). */
    fun hold(dur: Float, state: MonkeyState, expr: Expr? = null, freeze: Boolean = false, face: Float = this.face) =
        add(dur, x, y, state, Ease.LINEAR, face, expr, freeze, sx, sy, rot, 0f, 0f, vis, pvx, pvy)

    /** Hold until the absolute show time [at]. */
    fun until(at: Float, state: MonkeyState, expr: Expr? = null, freeze: Boolean = false) =
        if (at > t + 0.001f) hold(at - t, state, expr, freeze) else this

    /** Move to [x1] (and [y1]); faces the way it moves unless [face] is given. */
    fun move(
        x1: Float, dur: Float, state: MonkeyState, ease: Ease = Ease.IN_OUT, y1: Float = y, hop: Float = 0f,
        expr: Expr? = null, face: Float = if (x1 > x + 1e-4f) 1f else if (x1 < x - 1e-4f) -1f else this.face,
        bounce: Float = 0f, sx1: Float = 1f, sy1: Float = 1f, rot1: Float = 0f, freeze: Boolean = false,
    ) = add(dur, x1, y1, state, ease, face, expr, freeze, sx1, sy1, rot1, hop, bounce, true, pvx, pvy)

    /** Change shape in place: squash / stretch / rotate around a pivot (rig units from the feet). */
    fun shape(
        dur: Float, state: MonkeyState, sx1: Float = 1f, sy1: Float = 1f, rot1: Float = 0f, ease: Ease = Ease.IN_OUT,
        expr: Expr? = null, freeze: Boolean = false, pvx: Float = this.pvx, pvy: Float = this.pvy,
        x1: Float = x, y1: Float = y, face: Float = this.face,
    ) = add(dur, x1, y1, state, ease, face, expr, freeze, sx1, sy1, rot1, 0f, 0f, true, pvx, pvy)

    /** The classic cartoon wind-up, zip and dead stop: lean back, shoot off, skid to a halt. */
    fun dash(x1: Float, dur: Float, expr: Expr? = null, windUp: Float = 0.22f, skid: Float = 0.18f): Track {
        val dir = if (x1 >= x) 1f else -1f
        shape(windUp, MonkeyState.DASH, 0.86f, 1.12f, -10f * dir, Ease.IN_OUT, expr, freeze = true, pvx = 0f, pvy = 0f, x1 = x - 0.025f * dir, face = dir)
        move(x1, dur, MonkeyState.DASH, Ease.ZIP, expr = expr, face = dir, sx1 = 1.12f, sy1 = 0.92f, rot1 = 8f * dir)
        return shape(skid, MonkeyState.STARTLED, 1f, 1f, 0f, Ease.BACK, expr, freeze = true, face = dir)
    }

    fun hide(dur: Float = 0.001f) = add(dur, x, y, MonkeyState.IDLE, Ease.LINEAR, face, null, false, 1f, 1f, 0f, 0f, 0f, false, 0f, 0f)
    /** Appear at ([x1], [y1]) (a pop, not a slide). */
    fun appear(x1: Float, y1: Float = 0f, face: Float = this.face, s0: Float = 1f): Track { x = x1; y = y1; this.face = face; sx = s0; sy = s0; rot = 0f; vis = true; pvx = 0f; pvy = 0f; return this }
    /** Pop out (from small) at ([x1], [y1]), e.g. out of the mouse hole. */
    fun pop(x1: Float, y1: Float = 0f, face: Float = this.face, state: MonkeyState = MonkeyState.IDLE) =
        appear(x1, y1, face, 0.3f).shape(0.2f, state, 1f, 1f, 0f, Ease.BACK, pvx = 0f, pvy = 0f)
    /** Shrink away (into the hole) and hide. */
    fun dive(dur: Float = 0.12f) = shape(dur, MonkeyState.DASH, 0.25f, 0.25f, 0f, Ease.IN, pvx = 0f, pvy = 0f).hide()
    /** Jump the pen without a segment (only while hidden). */
    fun jumpTo(x1: Float, y1: Float = 0f) { require(!vis) { "only while hidden" }; x = x1; y = y1 }

    fun carriedBy(parent: String, t0: Float, t1: Float, dx: Float, dy: Float, rot: Float = 0f) { carries += Carry(t0, t1, parent, dx, dy, rot) }

    fun finish() {
        var d = 0f
        var prev: Seg? = null
        for (s in segs) {
            s.d0 = d; d += abs(s.x1 - s.x0) + abs(s.y1 - s.y0)
            s.st0 = if (prev != null && prev.state == s.state && prev.visible == s.visible) prev.st0 else s.t0
            prev = s
        }
    }

    val end get() = t

    fun segAt(t: Float): Seg? {
        if (segs.isEmpty() || t < segs.first().t0) return null
        var lo = 0; var hi = segs.size - 1
        while (lo < hi) { val m = (lo + hi + 1) / 2; if (segs[m].t0 <= t) lo = m else hi = m - 1 }
        return segs[lo]
    }

    fun sample(t: Float, out: Snap): Snap {
        val s = segAt(t)
        if (s == null) {
            out.x = x0(); out.y = y0(); out.visible = startVisible(); out.state = MonkeyState.IDLE; out.face = startFace()
            out.sx = 1f; out.sy = 1f; out.rot = 0f; out.expr = Expr.NONE; out.anim = t; out.st = t; out.dist = 0f; out.vx = 0f; out.pvx = 0f; out.pvy = 0f
            return out
        }
        val u = ((t - s.t0) / s.dur).coerceIn(0f, 1f)
        val k = ease(s.ease, u)
        out.x = s.x0 + (s.x1 - s.x0) * k
        out.y = s.y0 + (s.y1 - s.y0) * k + s.hop * sin(PI.toFloat() * u) + s.bounce * abs(sin(t * 9f))
        out.state = s.state; out.face = s.face; out.visible = s.visible; out.expr = s.expr
        out.sx = s.sx0 + (s.sx1 - s.sx0) * k; out.sy = s.sy0 + (s.sy1 - s.sy0) * k; out.rot = s.rot0 + (s.rot1 - s.rot0) * k
        out.pvx = s.pvx; out.pvy = s.pvy
        out.dist = s.d0 + abs(out.x - s.x0) + abs(out.y - s.y0 - s.hop * sin(PI.toFloat() * u))
        out.anim = if (s.freeze) s.t0 else t
        out.st = out.anim - s.st0
        // speed from the ease's slope (central difference, pure)
        val du = 0.01f
        val k2 = ease(s.ease, (u + du).coerceAtMost(1f)); val k1 = ease(s.ease, (u - du).coerceAtLeast(0f))
        val span = ((u + du).coerceAtMost(1f) - (u - du).coerceAtLeast(0f)).coerceAtLeast(1e-4f)
        out.vx = (s.x1 - s.x0) * (k2 - k1) / span / s.dur
        return out
    }

    // before the first segment: where the first one starts
    private fun x0() = segs.firstOrNull()?.x0 ?: x
    private fun y0() = segs.firstOrNull()?.y0 ?: y
    private fun startFace() = segs.firstOrNull()?.face ?: face
    private fun startVisible() = segs.firstOrNull()?.visible ?: vis

    companion object {
        fun ease(e: Ease, u: Float): Float = when (e) {
            Ease.LINEAR -> u
            Ease.IN_OUT -> u * u * (3f - 2f * u)
            Ease.ZIP -> { val v = 1f - u; 1f - v * v * v }
            Ease.IN -> u * u
            Ease.BACK -> { val c = 1.70158f; val v = u - 1f; 1f + (c + 1f) * v * v * v + c * v * v }
        }
    }
}

enum class Fx2 { STARS, DUST, WORD, HEART, HEARTS, BULB, EXCLAIM, QUESTION, NOTES, TEAR_POOL, IMPRINT }

/** A visual effect. Follows [track]'s head (or feet for DUST) when given, otherwise sits at ([x], [y]). */
class Cue(val at: Float, val dur: Float, val kind: Fx2, val track: String? = null, val text: String = "", val x: Float = Float.NaN, val y: Float = 0f, val big: Float = 1f)

/** A sound and/or a buzz at [at]. [sfx] = a name the app maps to its sound pool. */
class Beep(val at: Float, val sfx: String?, val pitch: Float = 1f, val buzzMs: Int = 0, val vol: Float = 1f)

class Episode(
    val id: String,
    val title: String,
    val tracks: Map<String, Track>,
    val lines: List<Line>,
    val cues: List<Cue>,
    val beeps: List<Beep>,
    /** The iris-out closes on this point (width units, y above the floor) starting at [irisAt]. */
    val irisAt: Float, val irisX: Float, val irisY: Float,
    /** Moments where the two are meant to touch (a hug, a catch, a shared laddu). */
    val touchWindows: List<ClosedFloatingPointRange<Float>>,
) {
    val total = irisAt + IRIS_LEN
    fun track(id: String) = tracks[id]

    companion object {
        const val TITLE_LEN = 2.6f
        /** Close to a small circle, hold, close fully, "The End". */
        const val IRIS_CLOSE = 1.1f
        const val IRIS_HOLD = 0.9f
        const val IRIS_SHUT = 0.45f
        const val END_CARD = 1.4f
        const val IRIS_LEN = IRIS_CLOSE + IRIS_HOLD + IRIS_SHUT + END_CARD
        const val CAT = "billa"
        const val RAT = "chittu"
    }
}

/** Small authoring helper: collects tracks, lines, cues and beeps for one episode. */
class EpisodeBuilder(val id: String, val title: String) {
    val tracks = LinkedHashMap<String, Track>()
    val lines = ArrayList<Line>()
    val cues = ArrayList<Cue>()
    val beeps = ArrayList<Beep>()
    val touch = ArrayList<ClosedFloatingPointRange<Float>>()
    fun track(id: String) = tracks.getOrPut(id) { Track(id) }
    val cat get() = track(Episode.CAT)
    val rat get() = track(Episode.RAT)
    fun say(at: Float, role: Role, text: String, dur: Float = 1.6f) { lines += Line(at, dur, role, text) }
    fun fx(at: Float, kind: Fx2, track: String? = null, dur: Float = 1f, text: String = "", x: Float = Float.NaN, y: Float = 0f, big: Float = 1f) { cues += Cue(at, dur, kind, track, text, x, y, big) }
    fun sfx(at: Float, name: String?, pitch: Float = 1f, buzz: Int = 0, vol: Float = 1f) { beeps += Beep(at, name, pitch, buzz, vol) }
    /** A comic impact: the word burst, a sound and a buzz. */
    fun hit(at: Float, track: String, word: String, sound: String, buzz: Int = 45, x: Float = Float.NaN, y: Float = 0f) {
        fx(at, Fx2.WORD, track, 0.9f, word, x, y); sfx(at, sound, buzz = buzz)
    }
    fun build(irisAt: Float, irisX: Float, irisY: Float): Episode {
        for (t in tracks.values) t.finish()
        lines.sortBy { it.at }; cues.sortBy { it.at }; beeps.sortBy { it.at }
        return Episode(id, title, tracks, lines, cues, beeps, irisAt, irisX, irisY, touch)
    }
}
