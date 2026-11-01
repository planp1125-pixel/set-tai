package com.combo.monkeycompanion.engine.show

import kotlin.math.abs
import kotlin.math.max
import com.combo.monkeycompanion.brain.MonkeyState as S

/**
 * v10.22 the Billa & Chittu drama, mix and match: 5 openings × 6 chases × 5 traps × 4 heart moments × 4 endings
 * = 2,400 different shows. It starts from wherever the two REALLY are (floor, wall, top, hiding at an edge), uses
 * their real size (the renderer gets their scale), speaks plain English, and ends with the iris-out.
 *
 * Every act begins by re-staging the two to its marks (running, keeping their order, so they never pass through
 * each other), and ends with the two apart, unless it's a moment where they touch on purpose (registered in
 * [EpisodeBuilder.touch]). `s` is the side the mouse is on (+1 = right of the cat): he flees toward that wall.
 */
object Drama {
    const val OPENINGS = 5
    const val CHASES = 6
    const val TRAPS = 5
    const val HEARTS = 4
    const val ENDINGS = 4
    const val COMBOS = OPENINGS * CHASES * TRAPS * HEARTS * ENDINGS

    val openingNames = listOf("Snack thief", "Sneak attack", "Feather tickle", "Dance party", "Ball bonk")
    val chaseNames = listOf("Mouse-hole splat", "Leap-frog", "Overshoot", "Wall tease", "Two holes", "Spin-out")
    val trapNames = listOf("Frying pan boomerang", "Banana peel", "Mousetrap", "Chilli laddu", "Bowling ball")
    val heartNames = listOf("Shared snack", "A plaster", "Help up", "Make him laugh")
    val endingNames = listOf("Sleep side by side", "A ride home", "Your turn to run!", "Bye-bye wave")

    /** The parts of show number [n]: every show differs from the one before, and all 2,400 come up. */
    fun parts(n: Int): IntArray {
        var k = Math.floorMod(n.toLong() * 1013L, COMBOS.toLong()).toInt()
        val o = k % OPENINGS; k /= OPENINGS
        val c = k % CHASES; k /= CHASES
        val t = k % TRAPS; k /= TRAPS
        val h = k % HEARTS; k /= HEARTS
        val e = k % ENDINGS
        return intArrayOf(o, c, t, h, e)
    }

    fun build(n: Int, catStart: ActorStart, ratStart: ActorStart): Episode = build(parts(n), catStart, ratStart, "drama-$n")

    fun build(p: IntArray, catStart: ActorStart, ratStart: ActorStart, id: String = "drama"): Episode {
        val b = EpisodeBuilder(id, "", p)
        val d = D(b)
        d.gather(catStart, ratStart, p[0])
        when (p[0]) { 0 -> d.oSnack(); 1 -> d.oSneak(); 2 -> d.oFeather(); 3 -> d.oDance(); else -> d.oBall() }
        when (p[1]) { 0 -> d.cHole(); 1 -> d.cLeapFrog(); 2 -> d.cOvershoot(); 3 -> d.cWallTease(); 4 -> d.cTwoHoles(); else -> d.cSpinOut() }
        when (p[2]) { 0 -> d.tPan(); 1 -> d.tPeel(); 2 -> d.tMousetrap(); 3 -> d.tChilli(); else -> d.tBowling() }
        val touchFrom = d.now()
        d.hurt()
        when (p[3]) { 0 -> d.hShare(); 1 -> d.hPlaster(); 2 -> d.hHelpUp(); else -> d.hLaugh() }
        val (ix, iy) = when (p[4]) { 0 -> d.eSleep(); 1 -> d.eRide(); 2 -> d.eReverse(); else -> d.eWave() }
        b.touch += touchFrom..999f
        val irisAt = d.now()
        // everyone holds still under the closing iris
        b.cat.until(irisAt + Episode.IRIS_LEN + 1f, b.cat.lastState.let { if (it == S.DASH || it == S.WALK) S.IDLE else it })
        b.rat.until(irisAt + Episode.IRIS_LEN + 1f, b.rat.lastState.let { if (it == S.DASH || it == S.WALK) S.IDLE else it })
        for ((pid, tr) in b.tracks) if (pid != Episode.CAT && pid != Episode.RAT) tr.until(irisAt + Episode.IRIS_LEN + 1f, S.IDLE)
        return b.build(irisAt, ix, iy)
    }
}

private const val CAT = Episode.CAT
private const val RAT = Episode.RAT

/** A mark: [off] screen-widths from the middle toward side [s]. */
private fun m(s: Float, off: Float) = 0.5f + s * off

internal class D(val b: EpisodeBuilder) {
    val cat = b.cat
    val rat = b.rat
    /** The side the mouse is on (he flees toward that wall). */
    val s get() = if (rat.x >= cat.x) 1f else -1f
    fun now() = max(cat.t, rat.t)

    private fun settle(tr: Track) = when (tr.lastState) { S.DASH, S.WALK, S.JUMP, S.STARTLED, S.FALLING, S.WAVE -> S.IDLE; else -> tr.lastState }

    /** Bring both tracks to the same moment (the one that finished first waits, in a calm pose). */
    fun sync(): Float {
        val t = now()
        cat.until(t, settle(cat)); rat.until(t, settle(rat))
        return t
    }

    /** Both run to their marks at once (same timing, same ease), so their order never flips and they never merge. */
    fun restage(cm: Float, rm: Float, cs: S = S.DASH, rs: S = S.DASH) {
        sync()
        val sd = s
        val dc = abs(cat.x - cm); val dr = abs(rat.x - rm)
        val dist = max(dc, dr)
        if (dist > 0.02f) {
            val dur = (dist / 0.7f).coerceIn(0.4f, 1.5f)
            if (dc > 0.01f) cat.move(cm, dur, cs) else cat.hold(dur, S.IDLE, face = sd)
            if (dr > 0.01f) rat.move(rm, dur, rs) else rat.hold(dur, S.IDLE, face = -sd)
            if (cs == S.DASH) b.fx(cat.t - dur, Fx2.DUST, CAT, 0.4f)
        }
        cat.hold(0.15f, S.IDLE, face = sd); rat.hold(0.15f, S.IDLE, face = -sd)
    }

    /** A prop that shows from [from] to [to] at ([x], [y]). */
    fun prop(id: String, x: Float, y: Float, from: Float, to: Float): Track {
        val tr = b.track(id)
        if (tr.segs.isEmpty()) tr.place(x, y, visible = false)
        if (from > tr.t + 0.001f) tr.hide(from - tr.t)
        tr.appear(x, y).until(to, S.IDLE)
        return tr.hide()
    }

    fun say(at: Float, cat: Boolean, text: String, dur: Float = 1.4f) = b.say(at, if (cat) Role.CAT else Role.RAT, text, dur)

    private fun tiptoe(from: Float, to: Float, every: Float = 0.38f, base: Float = 1f) {
        var t = from; var i = 0
        val notes = floatArrayOf(1f, 1.26f, 1.12f, 1.5f)
        while (t < to) { b.sfx(t, "pluck", base * notes[i % 4], vol = 0.5f); t += every; i++ }
    }

    // ───────────── getting into place from wherever they really are ─────────────

    fun gather(cs: ActorStart, rs: ActorStart, opening: Int) {
        // decide sides from where they are now (a missing one enters from the far side)
        val catX = if (cs.edge == ActorStart.OFF) Float.NaN else cs.x
        val ratX = if (rs.edge == ActorStart.OFF) Float.NaN else rs.x
        val side = when {
            !catX.isNaN() && !ratX.isNaN() -> if (ratX >= catX) 1f else -1f
            !catX.isNaN() -> if (catX < 0.5f) 1f else -1f
            !ratX.isNaN() -> if (ratX >= 0.5f) 1f else -1f
            else -> 1f
        }
        bringIn(cat, cs, -side)
        bringIn(rat, rs, side)
        b.touch += 0f..(now() + 1.6f)   // while they climb down / walk in they may cross
        val (cm, rm) = when (opening) {
            0 -> -0.2f to 0.38f
            1 -> -0.3f to 0.25f
            2 -> -0.2f to 0.36f
            3 -> -0.3f to 0.25f
            else -> -0.25f to 0.3f
        }
        restage(m(side, cm), m(side, rm), S.WALK, S.WALK)
    }

    private fun bringIn(tr: Track, st: ActorStart, enterSide: Float) {
        when (st.edge) {
            ActorStart.WALL_L, ActorStart.WALL_R -> {
                // climbing the wall exactly as the app draws it (upright climbing pose), then down to the floor
                val left = st.edge == ActorStart.WALL_L
                tr.place(st.x, st.lift.coerceAtLeast(0f), if (left) -1f else 1f).edge(2)
                tr.hold(0.15f, S.IDLE)
                val down = (st.lift / 0.6f).coerceIn(0.35f, 1.4f)
                tr.move(st.x, down, S.WALK, Ease.IN_OUT, y1 = 0f)
                tr.edge(0).hold(0.2f, S.IDLE, face = if (left) 1f else -1f)
            }
            ActorStart.TOP -> {
                // hanging from the top edge (the app's hanging pose), lets go, drops and lands
                tr.place(st.x.coerceIn(0.08f, 0.92f), st.lift, st.face).edge(1)
                tr.hold(0.3f, S.IDLE, Expr(eye = 1.3f))
                tr.edge(0).move(tr.x, (st.lift * 0.6f).coerceIn(0.5f, 1.4f), S.FALLING, Ease.IN, y1 = 0f, face = st.face)
                tr.shape(0.08f, S.IDLE, 1.15f, 0.82f, pvx = 0f, pvy = 0f).shape(0.18f, S.IDLE, 1f, 1f, ease = Ease.BACK)
            }
            ActorStart.OFF -> {
                tr.place(if (enterSide > 0f) 1.14f else -0.14f, 0f, -enterSide)
                tr.hold(0.3f, S.IDLE)
            }
            else -> {
                tr.place(st.x.coerceIn(0.04f, 0.96f), st.lift.coerceAtLeast(0f), st.face)
                if (st.lift > 0.02f) {   // on a ledge / a widget: hop down
                    tr.move(tr.x, 0.5f, S.JUMP, Ease.IN, y1 = 0f, hop = 0.05f)
                    tr.shape(0.08f, S.IDLE, 1.12f, 0.85f).shape(0.15f, S.IDLE, 1f, 1f, ease = Ease.BACK)
                } else tr.hold(0.2f, S.IDLE)
            }
        }
    }

    // ───────────── the cat crashes into a wall (reused by several chases) ─────────────

    /** Wind-up, flat-out run into wall [side], SPLAT, spring off, fall back, stars, get up, stagger out. */
    private fun catSplat(side: Float, run: Float = -1f): Float {
        val wallC = m(side, 0.405f)
        cat.shape(0.22f, S.DASH, 0.86f, 1.12f, -10f * side, x1 = cat.x - 0.025f * side, freeze = true, pvx = 0f, pvy = 0f, face = side,
            expr = Expr(brow = 1f, anger = 0.6f))
        b.sfx(cat.t, "zip", 0.9f); b.fx(cat.t, Fx2.DUST, CAT, 0.5f)
        val dur = if (run > 0f) run else (abs(wallC - cat.x) / 1.25f).coerceIn(0.3f, 0.8f)
        cat.move(wallC, dur, S.DASH, Ease.LINEAR, face = side, sx1 = 1.12f, sy1 = 0.92f, rot1 = 8f * side, expr = Expr(brow = 1f, mouth = 0.4f))
        val hit = cat.t
        cat.shape(0.06f, S.STARTLED, 0.42f, 1.22f, 0f, Ease.LINEAR, Expr.take(1.4f, mouth = 0.6f), freeze = true, pvx = 45f, pvy = 0f)
        b.hit(hit + 0.03f, CAT, "SPLAT!", "splat", 70, x = m(side, 0.4f), y = 0.32f)
        cat.hold(0.7f, S.STARTLED, Expr.take(1.4f, mouth = 0.6f), freeze = true)
        cat.shape(0.12f, S.STARTLED, 1f, 1f, 0f, Ease.BACK, Expr.take(1.4f, mouth = 0.6f), freeze = true)
        b.sfx(cat.t, "slide", 1f)
        cat.shape(0.4f, S.FALLING, 1f, 1f, -90f * side, Ease.IN, Expr(eye = 0.6f), freeze = true, pvx = -40f, pvy = 0f)
        b.hit(cat.t, CAT, "THUD!", "thud", 60); b.fx(cat.t, Fx2.DUST, CAT, 0.6f, big = 1.4f); b.fx(cat.t + 0.05f, Fx2.STARS, CAT, 1.8f)
        cat.hold(0.9f, S.FALLING, Expr(eye = 0.25f, mouth = 0.3f), freeze = true)
        cat.shape(0.45f, S.STARTLED, 1f, 1f, 0f, Ease.BACK, Expr(eye = 0.5f))
        cat.shape(0.001f, S.IDLE, pvx = 0f, pvy = 0f)
        cat.move(m(side, 0.2f), 0.8f, S.WALK, Ease.IN_OUT, face = -side, expr = Expr(eye = 0.5f, headShake = 1f, mouth = 0.3f))
        return hit
    }

    /** Falls flat on his back where he stands (a slip, a strike), then sits up dazed. */
    private fun catFallBack(dir: Float, lie: Float = 1.0f) {
        cat.shape(0.001f, S.FALLING, pvx = -40f, pvy = 0f)
        cat.shape(0.35f, S.FALLING, 1f, 1f, -90f * dir, Ease.IN, Expr(eye = 1.4f, mouth = 0.8f), freeze = true)
        b.hit(cat.t, CAT, "THUD!", "thud", 60); b.fx(cat.t, Fx2.DUST, CAT, 0.6f, big = 1.3f); b.fx(cat.t + 0.05f, Fx2.STARS, CAT, lie + 1.2f)
        cat.hold(lie, S.FALLING, Expr(eye = 0.25f, mouth = 0.3f), freeze = true)
        cat.shape(0.45f, S.SIT, 1f, 1f, 0f, Ease.BACK, Expr(eye = 0.5f))
        cat.shape(0.001f, S.SIT, pvx = 0f, pvy = 0f)
    }

    // ───────────── OPENINGS ─────────────

    /** Billa dozes by his snack; Chittu tiptoes in and steals it; one eye opens… */
    fun oSnack() {
        val t = sync(); val sd = s
        val snackX = cat.x + sd * 0.09f
        cat.hold(0.8f, S.IDLE, Expr(mouth = 0.9f, eye = 0.3f, head = 0f), face = sd)          // a big yawn
        b.say(t + 0.1f, Role.CAT, "*yaaawn* Nap time…", 1.2f)
        cat.until(t + 4.6f, S.SLEEP, Expr(zzz = 1f))
        rat.hold(0.9f, S.IDLE, Expr.take(1.1f, smile = 0.9f, brow = 0.6f))
        b.say(t + 1.5f, Role.RAT, "Shh… 🤫", 1.2f)
        val grabX = snackX + sd * 0.095f
        rat.move(grabX, 2.0f, S.WALK, Ease.LINEAR, bounce = 0.015f, expr = Expr(brow = 0.6f, smile = 0.8f, lookX = -sd))
        tiptoe(t + 0.95f, t + 2.9f)
        rat.shape(0.12f, S.IDLE, 1f, 0.88f).shape(0.12f, S.IDLE, 1f, 1f)
        val grab = rat.t - 0.12f
        rat.hold(0.6f, S.GIGGLE, Expr.take(1.1f, smile = 1f))
        cat.hold(0.5f, S.SLEEP, Expr(zzz = 0f, ear = 1f))                                       // an ear twitches
        cat.hold(0.8f, S.SLEEP, Expr(eye = 0.45f, brow = 0.7f, lookX = sd, zzz = 0f), freeze = true)   // one eye opens
        rat.until(cat.t, S.STARTLED, Expr(eye = 1.3f, lookX = -sd, sweat = true), freeze = true)
        cat.hold(0.7f, S.SLEEP, Expr(eye = 1.35f, lookX = sd, zzz = 0f), freeze = true)
        rat.until(cat.t, S.IDLE, Expr(smile = 0.5f, brow = -0.8f, sweat = true, lookX = -sd))
        b.say(rat.t - 0.65f, Role.RAT, "Hehe… hi?", 1.0f)
        cat.shape(0.3f, S.STARTLED, 0.9f, 1.15f, expr = Expr(eye = 1.4f, lookX = sd))
        b.fx(cat.t - 0.3f, Fx2.EXCLAIM, CAT, 0.7f); b.sfx(cat.t - 0.3f, "boing", 1.3f, buzz = 25)
        cat.shape(0.2f, S.ANGRY, 1f, 1f, ease = Ease.BACK, expr = Expr(anger = 1f, brow = 1f, shake = 0.3f, lookX = sd))
        cat.hold(0.6f, S.ANGRY, Expr(anger = 1f, brow = 1f, shake = 0.3f, lookX = sd))
        b.say(cat.t - 0.75f, Role.CAT, "HEY! My snack!", 1.1f)
        rat.until(cat.t, S.STARTLED, Expr(eye = 1.4f, sweat = true))
        prop("snack", snackX, 0.02f, t, cat.t).carriedBy(RAT, grab, cat.t, 0.035f, 0.085f)
    }

    /** Chittu munches cheese with his back turned; Billa sneaks up… and gets spotted. */
    fun oSneak() {
        val t = sync(); val sd = s
        prop("cheese", rat.x, 0.06f, t, t + 6.2f).carriedBy(RAT, t, t + 6.2f, 0.03f, 0.06f)
        rat.until(t + 3.4f, S.SIT, Expr(munch = true, happy = 1f, smile = 1f))
        rat.hold(0.01f, S.SIT, face = sd)
        cat.hold(1.0f, S.IDLE, Expr.take(1.4f, smile = 0.8f, mouth = 0.4f), face = sd)
        b.say(t + 0.15f, Role.CAT, "Ooh… a snack! 😋", 1.2f)
        cat.move(m(sd, 0.05f), 2.2f, S.WALK, Ease.LINEAR, bounce = 0.02f, expr = Expr(brow = 0.8f, smile = 0.9f, eye = 0.7f, lookX = sd))
        tiptoe(t + 1.05f, t + 3.2f, 0.5f, 0.85f)
        rat.hold(0.4f, S.SIT, Expr(ear = 1f, munch = true))
        rat.hold(0.4f, S.STARTLED, Expr.take(1.4f, mouth = 0.5f), face = -sd, freeze = true)
        b.fx(rat.t - 0.4f, Fx2.EXCLAIM, RAT, 0.6f); b.sfx(rat.t - 0.4f, "boing", 1.6f)
        cat.until(rat.t, S.IDLE, Expr(brow = 0.8f, smile = 0.9f, eye = 0.7f))
        cat.hold(0.7f, S.STARTLED, Expr.take(1.3f, smile = -0.3f, brow = -0.5f), freeze = true)
        rat.hold(0.7f, S.IDLE, Expr(eye = 1.3f, lookX = -sd), freeze = true)
        cat.hold(0.9f, S.WAVE, Expr(smile = 0.4f, brow = -0.6f, sweat = true))
        b.say(cat.t - 0.85f, Role.CAT, "Uh… hi there!", 0.9f)
        rat.hold(1.0f, S.IDLE, Expr(eye = 1.2f, brow = 0.8f, lookX = -sd))
        b.say(rat.t - 0.95f, Role.RAT, "Nope! Bye!", 0.9f)
        cat.until(rat.t, S.WAVE, Expr(smile = 0.2f, brow = -0.6f, sweat = true))
    }

    /** Billa naps; Chittu tickles his nose with a feather… ACHOO! */
    fun oFeather() {
        val t = sync(); val sd = s
        cat.hold(0.8f, S.IDLE, Expr(mouth = 0.9f, eye = 0.3f, head = 0f), face = sd)
        cat.until(t + 3.7f, S.SLEEP, Expr(zzz = 1f))
        prop("feather", rat.x, 0.1f, t + 0.3f, t + 5.2f).carriedBy(RAT, t + 0.3f, t + 5.2f, 0.05f, 0.11f, -40f)
        rat.hold(0.9f, S.IDLE, Expr.take(1.1f, smile = 1f, brow = 0.7f))
        b.say(t + 0.4f, Role.RAT, "Hee hee…", 1.0f)
        rat.move(cat.x + sd * 0.17f, 1.6f, S.WALK, Ease.LINEAR, bounce = 0.015f, expr = Expr(brow = 0.6f, smile = 0.9f, lookX = -sd))
        tiptoe(t + 0.95f, t + 2.5f)
        rat.hold(1.2f, S.IDLE, Expr(smile = 1f, armR = 120f, lookX = -sd))
        cat.hold(0.6f, S.SLEEP, Expr(zzz = 0f, mouth = 0.3f, eye = 0.15f))
        b.say(cat.t - 0.5f, Role.CAT, "Ah… ah…", 0.8f)
        cat.shape(0.25f, S.SLEEP, 0.92f, 1.12f, expr = Expr(eye = 0.1f, mouth = 0.4f), freeze = true)
        cat.shape(0.12f, S.STARTLED, 1.12f, 0.9f, expr = Expr(eye = 0.2f, mouth = 1f), freeze = true)
        b.hit(cat.t - 0.12f, CAT, "ACHOO!", "boing", 40)
        rat.until(cat.t - 0.12f, S.IDLE, Expr(smile = 1f, armR = 120f))
        rat.move(rat.x + sd * 0.1f, 0.35f, S.JUMP, Ease.ZIP, hop = 0.07f, face = -sd, expr = Expr(eye = 1.4f, mouth = 0.6f))   // blown back
        cat.shape(0.2f, S.ANGRY, 1f, 1f, ease = Ease.BACK, expr = Expr(anger = 1f, brow = 1f, lookX = sd))
        cat.hold(0.8f, S.ANGRY, Expr(anger = 1f, brow = 1f, shake = 0.3f, lookX = sd))
        b.say(cat.t - 0.8f, Role.CAT, "Who did that?!", 1.0f)
        rat.until(cat.t, S.IDLE, Expr(smile = 0.5f, brow = -0.8f, sweat = true, lookX = -sd))
    }

    /** Chittu dances to loud music; Billa covers his ears and stomps over. */
    fun oDance() {
        val t = sync(); val sd = s
        rat.until(t + 3.2f, S.DANCE, Expr(happy = 1f, smile = 1f))
        b.fx(t, Fx2.NOTES, RAT, 3.2f)
        tiptoe(t, t + 3.2f, 0.25f, 1.3f)
        cat.hold(0.6f, S.IDLE, Expr(eye = 1.2f, lookX = sd), face = sd)
        cat.hold(1.4f, S.ANGRY, Expr(armL = 170f, armR = 170f, eye = 0.3f, brow = 1f))
        b.say(t + 0.8f, Role.CAT, "Too loud!!", 1.1f)
        cat.move(m(sd, 0f), 1.1f, S.WALK, Ease.IN_OUT, expr = Expr(anger = 1f, brow = 1f))
        rat.hold(0.5f, S.STARTLED, Expr.take(1.3f, mouth = 0.5f), freeze = true, face = -sd)
        b.say(rat.t - 0.4f, Role.RAT, "Oops!", 0.8f)
        rat.hold(1.4f, S.GIGGLE, Expr.take(1f, smile = 1f))
        b.say(rat.t - 1.3f, Role.RAT, "Catch me if you can!", 1.2f)
        cat.until(rat.t, S.ANGRY, Expr(anger = 1f, brow = 1f, shake = 0.3f))
    }

    /** Chittu kicks a ball… BONK, right on Billa's head. */
    fun oBall() {
        val t = sync(); val sd = s
        cat.until(t + 2.2f, S.SIT, Expr(happy = 1f, smile = 0.8f))
        val ball = b.track("ball").place(rat.x - sd * 0.05f, 0.02f, visible = false).hide(t)
        ball.appear(rat.x - sd * 0.05f, 0.02f).until(t + 1.0f, S.IDLE)
        ball.move(cat.x + sd * 0.02f, 0.75f, S.IDLE, Ease.LINEAR, y1 = 0.27f, hop = 0.22f, rot1 = -720f * sd)
        ball.move(m(sd, 0.08f), 0.7f, S.IDLE, Ease.LINEAR, y1 = 0.02f, hop = 0.18f, rot1 = -1080f * sd)
        ball.until(t + 6.5f, S.IDLE).hide()
        rat.hold(0.7f, S.IDLE, Expr(smile = 0.8f, lookX = -sd))
        rat.shape(0.15f, S.IDLE, 1.05f, 0.9f).shape(0.15f, S.IDLE, 1f, 1f, ease = Ease.BACK)      // the kick
        b.sfx(t + 0.85f, "pop", 0.8f)
        rat.until(t + 1.75f, S.IDLE, Expr(eye = 1.2f, lookX = -sd))
        b.hit(t + 1.75f, CAT, "BONK!", "bonk", 50)
        b.fx(t + 1.8f, Fx2.STARS, CAT, 1.0f)
        cat.until(t + 2.6f, S.SIT, Expr(eye = 0.3f, headShake = 1f))
        cat.shape(0.3f, S.ANGRY, 1f, 1f, ease = Ease.BACK, expr = Expr(anger = 1f, brow = 1f), face = sd)
        cat.hold(0.9f, S.ANGRY, Expr(anger = 1f, brow = 1f, shake = 0.3f, lookX = sd))
        b.say(cat.t - 0.9f, Role.CAT, "Who threw that?!", 1.1f)
        rat.until(t + 2.6f, S.IDLE, Expr(eye = 0.8f, mouth = 0.3f, lookX = 0f, lookY = -0.5f))
        b.fx(t + 2.0f, Fx2.NOTES, RAT, 1.2f)
        rat.until(cat.t, S.GIGGLE, Expr.take(1f, smile = 1f))
    }

    // ───────────── CHASES ─────────────

    /** Chittu dives into his mouse hole; Billa can't stop… SPLAT. */
    fun cHole() {
        val t = sync(); val sd = s
        val holeX = m(sd, 0.44f)
        prop("hole", holeX, 0f, t, t + 7.5f)
        rat.shape(0.18f, S.DASH, 0.85f, 1.15f, -10f * sd, x1 = rat.x - 0.025f * sd, face = sd, freeze = true)
        b.sfx(t + 0.18f, "zip", 1.2f); b.fx(t + 0.18f, Fx2.DUST, RAT, 0.5f)
        rat.move(holeX, (abs(holeX - rat.x) / 1.2f).coerceIn(0.25f, 0.6f), S.DASH, Ease.ZIP, sx1 = 1.1f, sy1 = 0.92f, rot1 = 8f * sd)
        rat.dive()
        b.sfx(rat.t, "pop", 1.2f, vol = 0.6f)
        cat.hold(0.4f, S.STARTLED, Expr(eye = 1.3f), face = sd)
        b.say(t + 0.1f, Role.CAT, "Come back here!", 1.0f)
        catSplat(sd)
        rat.until(cat.t, S.IDLE)
        rat.pop(holeX, face = -sd, state = S.WAVE)
        b.sfx(rat.t - 0.2f, "pop", 1.3f, vol = 0.6f)
        rat.hold(0.9f, S.WAVE, Expr.take(1.1f, smile = 1f))
        b.say(rat.t - 0.9f, Role.RAT, "Missed me! 😜", 1.2f)
        cat.until(rat.t, S.ANGRY, Expr(anger = 1f, brow = 1f, eye = 0.7f))
    }

    /** Chittu waits by the wall, leap-frogs over Billa's head, and Billa hits the wall. */
    fun cLeapFrog() {
        restage(m(s, -0.25f), m(s, 0.15f))
        val t = sync(); val sd = s
        rat.dash(m(sd, 0.40f), 0.4f, windUp = 0.18f, skid = 0.18f)
        rat.shape(0.1f, S.STARTLED, 1.1f, 0.85f, 0f, Ease.BACK, face = -sd, pvx = 0f, pvy = 70f)    // turns + crouches (t+0.86)
        b.say(t + 0.9f, Role.RAT, "Come on then!", 0.9f)
        cat.until(t + 0.54f, S.ANGRY, Expr(anger = 1f, brow = 1f))
        rat.move(m(sd, -0.15f), 0.7f, S.JUMP, Ease.LINEAR, hop = 0.7f, rot1 = -360f * sd, sx1 = 1f, sy1 = 1f)   // over his head!
        b.sfx(t + 0.86f, "boing", 1.6f)
        rat.shape(0.15f, S.IDLE, 1.1f, 0.88f, pvx = 0f, pvy = 0f).shape(0.15f, S.IDLE, 1f, 1f, ease = Ease.BACK)
        catSplat(sd, run = 0.55f)
        rat.until(cat.t - 2.5f, S.GIGGLE, Expr.take(1f, smile = 1f))
        b.say(rat.t, Role.RAT, "Wall says hi! 😆", 1.4f)
        rat.until(cat.t, S.GIGGLE, Expr(smile = 1f, lookX = sd))
    }

    /** Chittu stops dead and hops straight up; Billa zooms underneath and skids away. */
    fun cOvershoot() {
        restage(m(s, -0.35f), m(s, -0.02f))
        val t = sync(); val sd = s
        rat.dash(m(sd, 0.15f), 0.35f, windUp = 0.18f, skid = 0.18f)            // t+0.71
        rat.hold((t + 0.78f - rat.t).coerceAtLeast(0.01f), S.IDLE, face = -sd)
        rat.move(rat.x, 0.6f, S.JUMP, Ease.LINEAR, hop = 0.65f, face = -sd)       // straight up as Billa passes under
        b.sfx(t + 0.78f, "boing", 1.5f)
        rat.shape(0.12f, S.IDLE, 1.1f, 0.85f).shape(0.15f, S.IDLE, 1f, 1f, ease = Ease.BACK)
        cat.until(t + 0.55f, S.ANGRY, Expr(anger = 1f, brow = 1f))
        cat.shape(0.22f, S.DASH, 0.86f, 1.12f, -10f * sd, x1 = cat.x - 0.025f * sd, freeze = true, face = sd)
        b.sfx(cat.t, "zip", 1f); b.fx(cat.t, Fx2.DUST, CAT, 0.5f)
        cat.move(m(sd, 0.40f), 0.5f, S.DASH, Ease.LINEAR, sx1 = 1.12f, sy1 = 0.92f, rot1 = 8f * sd)
        cat.shape(0.3f, S.STARTLED, 1.1f, 0.88f, -14f * sd, Ease.ZIP, Expr(eye = 1.4f), x1 = m(sd, 0.44f), freeze = true)   // skid
        b.sfx(cat.t - 0.3f, "slide", 1.8f, buzz = 20); b.fx(cat.t - 0.25f, Fx2.DUST, CAT, 0.6f, big = 1.3f)
        cat.shape(0.15f, S.STARTLED, 1f, 1f, 0f, Ease.BACK, Expr(eye = 1.3f))
        cat.hold(0.6f, S.IDLE, Expr.take(1.3f, smile = -0.3f), freeze = true)
        b.fx(cat.t - 0.5f, Fx2.QUESTION, CAT, 0.6f)
        b.say(cat.t - 0.5f, Role.CAT, "Huh? Where'd he go?", 1.1f)
        rat.until(cat.t, S.IDLE, Expr(smile = 1f, lookX = sd))
        rat.hold(0.9f, S.WAVE, Expr(smile = 1f, lookX = sd))
        b.say(rat.t - 0.85f, Role.RAT, "Over here! 👋", 1.0f)
        cat.hold(0.4f, S.STARTLED, Expr(eye = 1.4f, lookX = -sd), face = -sd, freeze = true)
        cat.hold(0.5f, S.ANGRY, Expr(anger = 1f, brow = 1f))
        rat.until(cat.t, S.GIGGLE, Expr(smile = 1f))
    }

    /** Chittu runs up the wall and teases; Billa jumps, tries to climb, and slides back down. */
    fun cWallTease() {
        restage(m(s, -0.2f), m(s, 0.2f))
        val t = sync(); val sd = s
        val wall = m(sd, 0.455f)
        rat.dash(wall, 0.35f, windUp = 0.18f, skid = 0f)
        rat.edge(2).move(wall, 0.9f, S.WALK, Ease.IN_OUT, y1 = 0.5f, face = sd)
        tiptoe(rat.t - 0.9f, rat.t, 0.15f, 1.4f)
        b.say(rat.t + 0.2f, Role.RAT, "Come up here! 😝", 1.4f)
        cat.until(t + 0.4f, S.STARTLED, Expr(eye = 1.3f))
        cat.dash(m(sd, 0.28f), 0.45f)
        for (i in 0 until 3) {
            cat.move(cat.x, 0.42f, S.JUMP, Ease.LINEAR, hop = 0.12f, face = sd, expr = Expr(anger = 1f, brow = 1f, lookY = -1f))
            b.sfx(cat.t - 0.42f, "boing", 1.1f + i * 0.15f, vol = 0.6f)
        }
        b.say(cat.t - 1.0f, Role.CAT, "Grr! Come down!", 1.1f)
        val catWall = m(sd, 0.43f)
        cat.move(catWall, 0.25f, S.DASH, Ease.ZIP)
        cat.edge(2).move(catWall, 0.4f, S.WALK, Ease.IN_OUT, y1 = 0.12f, face = sd)
        cat.hold(0.3f, S.IDLE, Expr.take(1.4f, mouth = 0.5f), freeze = true)
        b.sfx(cat.t, "slide", 0.9f)
        b.say(cat.t, Role.CAT, "Whoa-oa-oa…", 1.0f)
        cat.edge(0).move(catWall, 0.55f, S.FALLING, Ease.IN, y1 = 0f, face = sd, expr = Expr(eye = 1.4f, mouth = 0.8f))
        cat.shape(0.3f, S.SIT, 1f, 1f, 0f, Ease.BACK, Expr(eye = 0.4f), x1 = m(sd, 0.40f))
        b.hit(cat.t - 0.25f, CAT, "THUD!", "thud", 50); b.fx(cat.t, Fx2.STARS, CAT, 1.4f)
        cat.hold(1.0f, S.SIT, Expr(eye = 0.4f, headShake = 0.8f))
        cat.move(m(sd, 0.15f), 0.7f, S.WALK, Ease.IN_OUT, face = -sd, expr = Expr(brow = 0.8f, smile = -0.6f))
        b.say(cat.t - 0.6f, Role.CAT, "Hmph.", 0.8f)
        rat.until(cat.t, S.DANCE, Expr(happy = 1f, smile = 1f))
        rat.move(wall, 0.6f, S.WALK, Ease.IN_OUT, y1 = 0f, face = sd)
        rat.edge(0).shape(0.18f, S.IDLE, 1f, 1f, 0f, Ease.IN_OUT, x1 = m(sd, 0.44f), face = -sd)
        rat.hold(0.7f, S.GIGGLE, Expr.take(1f, smile = 1f))
        b.say(rat.t - 0.7f, Role.RAT, "Too slow!", 0.9f)
        cat.until(rat.t, S.ANGRY, Expr(anger = 1f, brow = 1f))
    }

    /** Two mouse holes: Chittu pops out of the other one behind Billa, again and again. */
    fun cTwoHoles() {
        restage(m(s, -0.1f), m(s, 0.2f))
        val t = sync(); val sd = s
        val a = m(sd, 0.44f); val bx = m(sd, -0.44f)
        prop("hole", a, 0f, t, t + 9.5f); prop("hole2", bx, 0f, t, t + 9.5f)
        rat.dash(a, 0.4f, windUp = 0.15f, skid = 0f)
        rat.dive()
        b.sfx(rat.t, "pop", 1.2f, vol = 0.6f)
        cat.until(t + 0.3f, S.STARTLED, Expr(eye = 1.3f))
        cat.dash(m(sd, 0.28f), 0.45f)
        cat.shape(0.25f, S.IDLE, 1f, 0.85f, 15f * sd, expr = Expr(lookX = sd, lookY = 0.5f, brow = 0.8f))   // peers into the hole
        cat.hold(0.9f, S.IDLE, Expr(lookX = sd, lookY = 0.5f, brow = 0.8f))
        b.say(cat.t - 0.9f, Role.CAT, "Come out, little mouse…", 1.2f)
        rat.until(cat.t, S.IDLE)
        rat.pop(bx, face = sd)
        b.sfx(rat.t - 0.2f, "pop", 1.4f, vol = 0.6f)
        rat.hold(0.5f, S.IDLE, Expr.take(1f, smile = 1f))
        b.say(rat.t - 0.5f, Role.RAT, "Boo!", 0.8f)
        cat.shape(0.15f, S.STARTLED, 1f, 1f, 0f, Ease.BACK, Expr(eye = 1.45f, mouth = 0.9f))
        cat.move(cat.x, 0.45f, S.STARTLED, Ease.LINEAR, hop = 0.3f, face = -sd, expr = Expr(eye = 1.45f, mouth = 0.9f))   // jumps out of his fur
        b.fx(cat.t - 0.45f, Fx2.EXCLAIM, CAT, 0.6f); b.sfx(cat.t - 0.45f, "boing", 1.2f, buzz = 20)
        cat.dash(m(sd, -0.27f), 0.55f)
        rat.until(cat.t - 0.6f, S.GIGGLE, Expr(smile = 1f))
        rat.dive()
        rat.until(cat.t + 0.3f, S.IDLE)
        rat.pop(a, face = -sd)
        b.sfx(rat.t - 0.2f, "pop", 1.2f, vol = 0.6f)
        rat.hold(0.6f, S.WAVE, Expr(smile = 1f, lookX = -sd))
        b.say(rat.t - 0.6f, Role.RAT, "Over here!", 0.9f)
        cat.hold(0.4f, S.IDLE, Expr(eye = 1.3f, lookX = -sd), face = -sd)
        cat.hold(0.35f, S.IDLE, Expr(eye = 1.3f, lookX = sd), face = sd)
        cat.hold(0.35f, S.IDLE, Expr(eye = 1.3f, lookX = -sd), face = -sd)
        cat.hold(1.2f, S.SIT, Expr(sweat = true, eye = 0.6f, mouth = 0.6f), face = sd)
        b.say(cat.t - 1.1f, Role.CAT, "I give up…", 1.0f)
        rat.until(cat.t, S.GIGGLE, Expr(smile = 1f))
    }

    /** Billa charges, Chittu hops, Billa can't stop and spins like a top. */
    fun cSpinOut() {
        restage(m(s, -0.35f), m(s, 0.1f))
        val t = sync(); val sd = s
        rat.hold(0.8f, S.GIGGLE, Expr.take(1f, smile = 1f))
        b.say(t + 0.05f, Role.RAT, "Come and get me!", 1.0f)
        cat.until(t + 0.8f, S.ANGRY, Expr(anger = 1f, brow = 1f))
        cat.shape(0.22f, S.DASH, 0.86f, 1.12f, -10f * sd, x1 = cat.x - 0.025f * sd, freeze = true, face = sd)
        val run0 = cat.t
        b.sfx(run0, "zip", 1f); b.fx(run0, Fx2.DUST, CAT, 0.5f)
        cat.move(m(sd, 0.30f), 0.55f, S.DASH, Ease.LINEAR, sx1 = 1.12f, sy1 = 0.92f, rot1 = 8f * sd)
        rat.until(run0 + 0.137f, S.IDLE, Expr(eye = 1.3f, lookX = -sd))
        rat.move(rat.x, 0.6f, S.JUMP, Ease.LINEAR, hop = 0.65f, face = -sd)
        b.sfx(run0 + 0.137f, "boing", 1.6f)
        rat.shape(0.12f, S.IDLE, 1.1f, 0.85f).shape(0.15f, S.IDLE, 1f, 1f, ease = Ease.BACK)
        cat.shape(0.001f, S.STARTLED, 1.12f, 0.92f, 8f * sd, pvx = 0f, pvy = 60f)
        cat.shape(0.9f, S.STARTLED, 1f, 1f, 720f * sd, Ease.IN_OUT, Expr(eye = 1.4f, mouth = 0.7f), freeze = true, x1 = m(sd, 0.38f))
        b.sfx(cat.t - 0.9f, "slide", 1.4f)
        cat.shape(0.001f, S.SIT, pvx = 0f, pvy = 0f)
        cat.shape(0.2f, S.SIT, 1.1f, 0.85f).shape(0.15f, S.SIT, 1f, 1f, ease = Ease.BACK)
        b.hit(cat.t - 0.3f, CAT, "THUMP!", "thud", 40); b.fx(cat.t, Fx2.STARS, CAT, 1.6f)
        cat.hold(1.3f, S.SIT, Expr(eye = 0.3f, headShake = 1f))
        b.say(rat.t + 0.2f, Role.RAT, "Whee! 😆", 0.9f)
        rat.until(cat.t, S.GIGGLE, Expr(smile = 1f, lookX = sd))
        cat.shape(0.3f, S.ANGRY, 1f, 1f, ease = Ease.BACK, face = -sd, expr = Expr(anger = 1f, brow = 1f))
        rat.until(cat.t, S.GIGGLE, Expr(smile = 1f))
    }

    // ───────────── TRAPS (Billa's plan backfires) ─────────────

    /** A frying pan thrown at Chittu: he ducks, it hits the wall and boomerangs into Billa's face. */
    fun tPan() {
        restage(m(s, -0.25f), m(s, 0.30f))
        val t = sync(); val sd = s
        cat.hold(1.0f, S.THINKING, Expr(lookY = -1f), face = sd)
        b.fx(t + 0.4f, Fx2.BULB, CAT, 1.2f); b.sfx(t + 0.4f, "pluck", 2f, vol = 0.8f)
        cat.shape(0.3f, S.IDLE, 1.05f, 0.9f, expr = Expr(smile = 1f, brow = 0.8f, eye = 0.7f, armR = 150f))
        cat.hold(0.9f, S.IDLE, Expr(eye = 0.75f, lookX = 0f, lookY = 0f, head = 0f, smile = 1f, brow = 0.8f, armR = 150f), freeze = true)
        b.say(t + 1.35f, Role.CAT, "Heh heh heh…", 1.0f)
        cat.shape(0.25f, S.IDLE, 0.9f, 1.1f, -12f * sd, expr = Expr(armR = 175f, brow = 1f, smile = 1f, lookX = sd), freeze = true)
        val throwAt = cat.t
        cat.shape(0.1f, S.DASH, 1.1f, 0.92f, 14f * sd, Ease.ZIP, Expr(armR = 40f, brow = 1f, smile = 1f, lookX = sd), freeze = true)
        val cx = cat.x
        val pan = b.track("pan").place(cx, 0.3f, visible = false).hide(t + 1.0f)
        pan.appear(cx + sd * 0.06f, 0.3f).until(throwAt, S.IDLE)
        pan.carriedBy(CAT, t + 1.0f, throwAt, 0.06f, 0.30f, -30f)
        pan.move(m(sd, 0.475f), 0.2f, S.IDLE, Ease.LINEAR, y1 = 0.12f, rot1 = 720f * sd)
        val clang = pan.t
        pan.move(cx + sd * 0.015f, 0.25f, S.IDLE, Ease.IN, y1 = 0.21f, rot1 = 1440f * sd)
        val bong = pan.t
        pan.hold(1.0f, S.IDLE)
        pan.move(cx + sd * 0.06f, 0.35f, S.IDLE, Ease.IN, y1 = 0f, rot1 = 80f * sd)
        pan.until(bong + 5f, S.IDLE).hide()
        b.sfx(throwAt, "zip", 1.4f)
        b.hit(clang, "pan", "CLANG!", "clang", 40, x = m(sd, 0.42f), y = 0.3f)
        b.hit(bong, CAT, "BONG!", "bonk", 90)
        rat.until(throwAt + 0.07f, S.IDLE, Expr(eye = 1.2f, lookX = -sd))
        rat.shape(0.06f, S.STARTLED, 1.3f, 0.45f, freeze = true, expr = Expr(eye = 1.3f))              // ducks
        rat.until(throwAt + 0.43f, S.STARTLED, Expr(eye = 1.3f), freeze = true)
        rat.shape(0.2f, S.IDLE, 1f, 1f, ease = Ease.BACK, expr = Expr(eye = 1.2f, lookX = -sd))
        cat.until(clang, S.IDLE, Expr(lookX = sd, smile = 1f, eye = 1.1f), freeze = true)
        cat.until(bong, S.STARTLED, Expr.take(1.45f, smile = -0.6f, brow = -0.8f, mouth = 0.6f), freeze = true)   // uh-oh
        cat.shape(0.05f, S.STARTLED, 1.22f, 0.8f, 0f, Ease.LINEAR, Expr(eye = 0.3f), freeze = true)            // flat face
        cat.until(bong + 1.35f, S.STARTLED, Expr(eye = 0.3f, shake = 0.4f), freeze = true)
        cat.shape(0.4f, S.SIT, 1f, 1f, 0f, Ease.BACK, Expr(eye = 0.4f))
        b.fx(bong + 0.05f, Fx2.STARS, CAT, 2.6f)
        b.sfx(bong + 1.35f, "clang", 1.5f, vol = 0.5f)
        rat.until(bong + 0.3f, S.IDLE, Expr(eye = 1.2f))
        rat.until(cat.t, S.GIGGLE, Expr.take(1f, smile = 1f))
        b.say(bong + 0.4f, Role.RAT, "Hahaha! 😆", 1.2f)
    }

    /** Billa tosses a banana peel for Chittu… then forgets it's there. */
    fun tPeel() {
        restage(m(s, -0.3f), m(s, 0.20f))
        val t = sync(); val sd = s
        cat.hold(0.9f, S.THINKING, Expr(lookY = -1f), face = sd)
        b.fx(t + 0.3f, Fx2.BULB, CAT, 1.0f); b.sfx(t + 0.3f, "pluck", 2f, vol = 0.8f)
        val peel = b.track("peel").place(cat.x, 0.1f, visible = false).hide(t + 1.1f)
        peel.appear(cat.x + sd * 0.05f, 0.12f).move(m(sd, -0.05f), 0.55f, S.IDLE, Ease.LINEAR, y1 = 0f, hop = 0.12f, rot1 = 360f * sd)
        cat.shape(0.2f, S.IDLE, 1.05f, 0.92f, expr = Expr(armR = 150f, smile = 1f, brow = 0.8f))
        cat.hold(1.0f, S.IDLE, Expr(eye = 0.75f, lookX = 0f, lookY = 0f, head = 0f, smile = 1f, brow = 0.8f))
        b.say(t + 1.4f, Role.CAT, "Slip on that! Heh!", 1.2f)
        rat.until(t + 2.2f, S.IDLE, Expr(lookX = -sd, lookY = 0.5f))
        rat.hold(0.8f, S.IDLE, Expr.take(1f, brow = 0.7f, smile = 0.4f))                          // "really?"
        cat.until(rat.t, S.ANGRY, Expr(anger = 1f, brow = 1f))
        b.say(rat.t - 0.6f, Role.RAT, "Really? 🙄", 0.9f)
        cat.shape(0.2f, S.DASH, 0.86f, 1.12f, -10f * sd, x1 = cat.x - 0.02f * sd, freeze = true, face = sd)
        cat.move(m(sd, -0.06f), 0.4f, S.DASH, Ease.IN, expr = Expr(brow = 1f, mouth = 0.4f))
        // SLIP: feet fly up, a flip, flat on his back
        cat.shape(0.001f, S.FALLING, 1f, 1f, 0f, pvx = 0f, pvy = 60f)
        b.fx(cat.t, Fx2.EXCLAIM, CAT, 0.5f); b.sfx(cat.t, "slide", 1.6f)
        cat.move(m(sd, -0.01f), 0.5f, S.FALLING, Ease.LINEAR, hop = 0.2f, rot1 = -100f * sd, face = sd, expr = Expr(eye = 1.45f, mouth = 0.8f), freeze = true)
        cat.move(m(sd, 0f), 0.12f, S.FALLING, Ease.IN, y1 = -0.05f, rot1 = -90f * sd, face = sd, expr = Expr(eye = 1.45f, mouth = 0.8f), freeze = true)
        val thud = cat.t
        b.hit(thud, CAT, "THUD!", "thud", 70); b.fx(thud, Fx2.DUST, CAT, 0.6f, big = 1.4f); b.fx(thud + 0.05f, Fx2.STARS, CAT, 2.0f)
        peel.until(thud - 0.6f, S.IDLE).move(m(sd, -0.42f), 0.8f, S.IDLE, Ease.LINEAR, y1 = 0f, hop = 0.3f, rot1 = -720f * sd)
        peel.until(thud + 4f, S.IDLE).hide()
        cat.hold(1.1f, S.FALLING, Expr(eye = 0.3f, mouth = 0.3f), freeze = true)
        cat.shape(0.5f, S.SIT, 1f, 1f, 0f, Ease.BACK, Expr(eye = 0.5f), y1 = 0f)
        cat.shape(0.001f, S.SIT, pvx = 0f, pvy = 0f)
        rat.until(thud + 0.3f, S.IDLE, Expr(eye = 1.2f, lookX = -sd))
        rat.until(cat.t, S.GIGGLE, Expr.take(1f, smile = 1f))
        b.say(thud + 0.4f, Role.RAT, "Watch your step! 😂", 1.4f)
    }

    /** Billa sets a mousetrap; Chittu lifts the cheese off gently; Billa pokes the trap… SNAP. */
    fun tMousetrap() {
        restage(m(s, -0.3f), m(s, 0.36f))
        val t = sync(); val sd = s
        val trapX = m(sd, 0.06f)
        cat.move(m(sd, -0.08f), 0.6f, S.WALK, face = sd)
        cat.shape(0.3f, S.IDLE, 1f, 0.86f).shape(0.2f, S.IDLE, 1f, 1f)
        prop("trap", trapX, 0f, t + 0.8f, t + 8.5f)
        val cheese = b.track("cheese").place(trapX, 0.025f, visible = false).hide(t + 0.8f)
        cheese.appear(trapX, 0.025f)
        cat.move(m(sd, -0.3f), 0.8f, S.WALK, Ease.LINEAR, face = sd, bounce = 0.012f, expr = Expr(smile = 1f, brow = 0.8f))
        cat.hold(1.2f, S.IDLE, Expr(eye = 0.75f, lookX = 0f, lookY = 0f, head = 0f, smile = 1f, brow = 0.8f))
        b.say(cat.t - 1.1f, Role.CAT, "Come and get it… 😼", 1.2f)
        rat.until(t + 1.6f, S.IDLE, Expr(lookX = -sd, eye = 1.1f))
        rat.move(m(sd, 0.17f), 1.1f, S.WALK, Ease.LINEAR, bounce = 0.012f, face = -sd, expr = Expr(lookX = -sd, smile = 0.6f))
        tiptoe(t + 1.6f, t + 2.7f, 0.22f, 1.2f)
        rat.shape(0.3f, S.IDLE, 1f, 0.82f, expr = Expr(armR = 80f, eye = 1.1f)).hold(0.4f, S.IDLE, Expr(armR = 80f, sweat = true))
        val lift = rat.t
        cheese.until(lift, S.IDLE).hide()
        val ch2 = b.track("cheese2").place(0f, 0f, visible = false).hide(lift)
        ch2.appear(0f).until(lift + 3.2f, S.IDLE).hide()
        ch2.carriedBy(RAT, lift, lift + 3.2f, 0.03f, 0.06f)
        rat.shape(0.2f, S.IDLE, 1f, 1f)
        rat.move(m(sd, 0.33f), 0.6f, S.WALK, face = sd)
        rat.until(lift + 3.2f, S.SIT, Expr(munch = true, happy = 1f, smile = 1f), freeze = false)
        b.say(lift + 1.0f, Role.RAT, "Yum! 🧀", 1.0f)
        cat.until(lift + 0.8f, S.IDLE, Expr(eye = 1.3f, lookX = sd))
        cat.hold(0.6f, S.IDLE, Expr.take(1.3f, smile = -0.3f))
        b.fx(cat.t - 0.5f, Fx2.QUESTION, CAT, 0.6f)
        cat.move(m(sd, -0.06f), 0.7f, S.WALK, face = sd, expr = Expr(brow = 0.5f, lookX = sd))
        cat.shape(0.25f, S.IDLE, 1f, 0.86f, expr = Expr(armR = 90f, lookY = 0.6f))                         // pokes it…
        val snap = cat.t
        b.hit(snap, CAT, "SNAP!", "clang", 80)
        cat.move(cat.x, 0.4f, S.STARTLED, Ease.LINEAR, hop = 0.18f, expr = Expr(eye = 1.45f, mouth = 1f, armR = 160f, tear = true))
        cat.move(cat.x, 0.4f, S.STARTLED, Ease.LINEAR, hop = 0.14f, expr = Expr(eye = 1.45f, mouth = 1f, armR = 160f, tear = true))
        b.say(snap + 0.1f, Role.CAT, "OWW OWW OWW!", 1.2f)
        cat.shape(0.3f, S.SIT, 1f, 1f, ease = Ease.BACK, expr = Expr(tear = true, brow = -1f, armR = 120f))
        rat.until(cat.t, S.SIT, Expr(munch = true, smile = 1f))
    }

    /** Billa leaves a "gift" laddu; Chittu swaps it for a chilli while Billa gloats; Billa eats it. HOT! */
    fun tChilli() {
        restage(m(s, -0.25f), m(s, 0.28f))
        val t = sync(); val sd = s
        val giftX = m(sd, 0.0f)
        val gift = b.track("snack").place(cat.x, 0.1f, visible = false).hide(t)
        gift.appear(cat.x + sd * 0.06f, 0.11f).until(t + 1.0f, S.IDLE)
        gift.carriedBy(CAT, t, t + 1.0f, 0.06f, 0.11f)
        cat.move(m(sd, -0.1f), 0.6f, S.WALK, face = sd)
        cat.shape(0.2f, S.IDLE, 1f, 0.86f).shape(0.2f, S.IDLE, 1f, 1f)
        gift.appear(giftX, 0.02f).until(t + 1.0f + 0.01f, S.IDLE)
        cat.move(m(sd, -0.28f), 0.6f, S.WALK, face = sd)
        b.say(t + 1.1f, Role.CAT, "A gift for you! 🎁", 1.2f)
        cat.hold(0.1f, S.IDLE, face = -sd)
        cat.hold(2.0f, S.IDLE, Expr(eye = 0.6f, lookX = 0f, lookY = 0f, head = 0f, smile = 1f, brow = 0.8f), face = -sd)   // gloats, back turned
        b.say(cat.t - 1.8f, Role.CAT, "Heh heh heh…", 1.1f)
        val swapAt = t + 2.4f
        rat.until(t + 1.6f, S.IDLE, Expr(lookX = -sd, brow = 0.7f))
        rat.move(m(sd, 0.12f), 0.6f, S.WALK, Ease.LINEAR, bounce = 0.012f, face = -sd)
        rat.shape(0.2f, S.IDLE, 1f, 0.85f).shape(0.2f, S.IDLE, 1f, 1f)            // the swap (t+2.6)
        gift.until(swapAt, S.IDLE).hide()
        prop("chilli", giftX, 0.02f, swapAt, cat.t + 3.2f)
        val ladduR = b.track("snack2").place(0f, 0f, visible = false).hide(swapAt)
        ladduR.appear(0f).until(swapAt + 8f, S.IDLE).hide()
        ladduR.carriedBy(RAT, swapAt, swapAt + 8f, 0.035f, 0.08f)
        rat.move(m(sd, 0.3f), 0.6f, S.WALK, face = sd)
        rat.hold(0.4f, S.IDLE, Expr(eye = 0.8f, mouth = 0.3f, lookX = 0f), face = -sd)
        b.fx(rat.t - 0.4f, Fx2.NOTES, RAT, 1.0f)
        cat.hold(0.4f, S.IDLE, Expr(eye = 1.2f, lookX = sd), face = sd)
        b.say(cat.t, Role.CAT, "Ooh, he left it! Mine!", 1.1f)
        cat.move(m(sd, -0.09f), 0.5f, S.WALK, face = sd)
        cat.shape(0.2f, S.IDLE, 1f, 0.86f).shape(0.2f, S.IDLE, 1f, 1f)
        b.track("chilli").carriedBy(CAT, cat.t - 0.2f, cat.t + 1.0f, 0.04f, 0.2f, 30f)
        cat.hold(0.8f, S.IDLE, Expr(munch = true, smile = 0.8f, eye = 0.5f))
        cat.hold(0.4f, S.STARTLED, Expr(eye = 1.45f, anger = 1f, mouth = 1f), freeze = true)
        val hot = cat.t
        b.hit(hot - 0.35f, CAT, "HOT!!", "boing", 60)
        cat.move(m(sd, -0.35f), 0.5f, S.DASH, Ease.IN_OUT, expr = Expr(anger = 1f, mouth = 1f, eye = 1.4f, sweat = true))
        cat.move(m(sd, -0.12f), 0.5f, S.DASH, Ease.IN_OUT, expr = Expr(anger = 1f, mouth = 1f, eye = 1.4f, sweat = true))
        cat.move(m(sd, -0.3f), 0.5f, S.DASH, Ease.IN_OUT, expr = Expr(anger = 1f, mouth = 1f, eye = 1.4f, sweat = true))
        b.say(hot, Role.CAT, "Hot hot HOT! 🔥", 1.3f)
        cat.shape(0.3f, S.SIT, 1f, 1f, ease = Ease.BACK, expr = Expr(anger = 0.6f, mouth = 0.8f, tear = true, sweat = true))
        rat.until(cat.t, S.SIT, Expr(munch = true, smile = 1f))
        b.say(cat.t - 1.6f, Role.RAT, "Hehe, nice swap 😜", 1.3f)
    }

    /** Bowling: Billa rolls a ball at Chittu, who hops; it bounces off the wall and knocks Billa flat. STRIKE! */
    fun tBowling() {
        restage(m(s, -0.3f), m(s, 0.25f))
        val t = sync(); val sd = s
        cat.hold(0.8f, S.THINKING, Expr(lookY = -1f), face = sd)
        b.fx(t + 0.3f, Fx2.BULB, CAT, 1.0f); b.sfx(t + 0.3f, "pluck", 2f, vol = 0.8f)
        b.say(t + 0.9f, Role.CAT, "Bowling time! 🎳", 1.2f)
        cat.shape(0.3f, S.IDLE, 1.05f, 0.88f, expr = Expr(armR = 60f, brow = 0.8f, smile = 1f))
        cat.shape(0.35f, S.IDLE, 0.95f, 1.05f, -10f * sd, expr = Expr(armR = 150f, brow = 0.8f, smile = 1f), freeze = true)
        cat.shape(0.15f, S.DASH, 1.1f, 0.9f, 12f * sd, Ease.ZIP, Expr(armR = 30f, brow = 1f, smile = 1f), freeze = true)
        val roll = cat.t
        val ball = b.track("bowl").place(cat.x, 0.05f, visible = false).hide(t + 0.8f)
        ball.appear(cat.x + sd * 0.05f, 0.12f).until(roll, S.IDLE)
        ball.carriedBy(CAT, t + 0.8f, roll, 0.05f, 0.12f)
        ball.move(cat.x + sd * 0.06f, 0.05f, S.IDLE, y1 = 0.025f)
        ball.move(m(sd, 0.47f), 0.7f, S.IDLE, Ease.LINEAR, y1 = 0.025f, rot1 = 720f * sd)
        val wallHit = ball.t
        ball.move(cat.x + sd * 0.04f, 0.42f, S.IDLE, Ease.LINEAR, y1 = 0.025f, rot1 = 0f)
        val strike = ball.t
        ball.move(m(sd, -0.05f), 0.6f, S.IDLE, Ease.ZIP, y1 = 0.025f, rot1 = 360f * sd)
        ball.until(strike + 5f, S.IDLE).hide()
        b.sfx(roll, "zip", 0.7f)
        b.hit(wallHit, "bowl", "BONK!", "thud", 30, x = m(sd, 0.42f), y = 0.2f)
        // the ball passes under Chittu: he hops
        val pass = roll + 0.05f + 0.7f * ((m(sd, 0.25f) - cat.x) * sd / ((m(sd, 0.47f) - cat.x) * sd)).coerceIn(0f, 1f)
        rat.until(pass - 0.22f, S.IDLE, Expr(eye = 1.2f, lookX = -sd))
        rat.move(rat.x, 0.45f, S.JUMP, Ease.LINEAR, hop = 0.12f, face = -sd)
        rat.until(wallHit + 0.1f, S.IDLE, Expr(eye = 1.2f, lookX = sd))
        cat.until(wallHit, S.IDLE, Expr(smile = 1f, lookX = sd, eye = 1.1f))
        cat.until(strike, S.STARTLED, Expr.take(1.45f, mouth = 0.7f, brow = -0.8f), freeze = true)
        b.hit(strike, CAT, "STRIKE!", "bonk", 80)
        catFallBack(sd, 1.0f)
        rat.until(cat.t, S.GIGGLE, Expr.take(1f, smile = 1f))
        b.say(strike + 0.4f, Role.RAT, "Strike! 🎳😂", 1.3f)
    }

    // ───────────── the turnaround: Billa is hurt, Chittu stops laughing ─────────────

    fun hurt() {
        val t = sync(); val sd = s
        cat.until(t + 3.0f, S.SIT, Expr(tear = true, brow = -1f, smile = -0.9f, eye = 0.8f, lookX = 0f))
        b.sfx(t + 0.1f, "wahwah", 1f)
        b.say(t + 0.3f, Role.CAT, "Ow… it really hurts… 😿", 1.8f)
        rat.hold(1.0f, S.IDLE, Expr(lookX = -sd, smile = -0.2f, brow = -0.6f), face = -sd)
        rat.move(rat.x - sd * 0.03f, 0.4f, S.WALK, face = -sd, expr = Expr(lookX = -sd, brow = -0.6f))
        rat.hold(0.3f, S.IDLE, Expr(lookX = -sd, brow = -0.6f))
        rat.move(rat.x + sd * 0.03f, 0.35f, S.WALK, face = -sd, expr = Expr(lookX = -sd, brow = -0.6f))
        rat.hold(0.8f, S.IDLE, Expr.take(1f, smile = 0.1f, brow = -0.6f))
        b.fx(rat.t - 0.7f, Fx2.QUESTION, RAT, 0.7f)
        cat.until(rat.t, S.SIT, Expr(tear = true, brow = -1f, smile = -0.9f, eye = 0.8f))
    }

    // ───────────── HEART moments ─────────────

    /** Chittu shares his snack: half each. */
    fun hShare() {
        val t = sync(); val sd = s
        val near = cat.x + sd * 0.15f
        rat.move(near, (abs(near - rat.x) / 0.4f).coerceIn(0.4f, 1.4f), S.WALK, face = -sd)
        val give = rat.t
        val whole = b.track("gift").place(0f, 0f, visible = false).hide(t + 0.1f)
        whole.appear(0f).until(give + 1.2f, S.IDLE).hide()
        whole.carriedBy(RAT, t + 0.1f, give + 1.2f, 0.035f, 0.085f)
        rat.hold(1.2f, S.IDLE, Expr(lookX = -sd, smile = 0.6f, brow = -0.2f, armL = 90f))
        b.say(give, Role.RAT, "Here… half for you.", 1.4f)
        val split = give + 1.2f
        b.track("half1").place(0f, 0f, visible = false).hide(split).appear(0f).until(split + 2.6f, S.IDLE).hide()
        b.track("half1").carriedBy(CAT, split, split + 2.6f, 0.06f, 0.10f)
        b.track("half2").place(0f, 0f, visible = false).hide(split).appear(0f).until(split + 2.6f, S.IDLE).hide()
        b.track("half2").carriedBy(RAT, split, split + 2.6f, 0.035f, 0.08f)
        b.sfx(split, "pluck", 1.5f, vol = 0.7f)
        cat.until(give, S.SIT, Expr(tear = true, brow = -0.8f, smile = -0.6f, lookX = sd))
        cat.hold(0.6f, S.SIT, Expr(eye = 1.35f, brow = 0f, lookX = sd, tear = true), freeze = true)
        cat.until(split + 0.6f, S.SIT, Expr(happy = 1f, blush = 1f, smile = 1f))
        b.say(split + 0.2f, Role.CAT, "Thank you, Chittu 🥹", 1.5f)
        b.fx(split + 0.3f, Fx2.HEART, CAT, 1.4f)
        cat.until(split + 2.6f, S.SIT, Expr(munch = true, happy = 0.6f, smile = 0.8f, blush = 0.6f))
        rat.until(split + 2.6f, S.SIT, Expr(munch = true, smile = 0.8f))
    }

    /** Chittu runs off and comes back with a plaster for Billa's head. */
    fun hPlaster() {
        val t = sync(); val sd = s
        val away = if (sd > 0f) 1.12f else -0.12f
        b.say(t, Role.RAT, "Wait here!", 0.9f)
        rat.move(away, (abs(away - rat.x) / 1.4f).coerceIn(0.25f, 0.6f), S.DASH, Ease.IN, face = sd)
        b.sfx(t, "zip", 1.3f)
        rat.hide(0.6f)
        val back = rat.t
        rat.appear(away, face = -sd)
        val near = cat.x + sd * 0.16f
        rat.move(near, (abs(near - away) / 1.4f).coerceIn(0.3f, 0.8f), S.DASH, Ease.ZIP)
        b.sfx(back, "zip", 1.1f)
        rat.move(cat.x + sd * 0.09f, 0.3f, S.JUMP, Ease.LINEAR, hop = 0.2f, face = -sd)
        val stick = rat.t
        rat.shape(0.15f, S.IDLE, 1f, 1f, expr = Expr(armL = 160f, armR = 160f))
        rat.move(cat.x + sd * 0.15f, 0.3f, S.JUMP, Ease.LINEAR, hop = 0.1f, face = -sd)
        val pl = b.track("plaster").place(0f, 0f, visible = false).hide(back)
        pl.appear(0f).until(999f, S.IDLE)
        pl.carriedBy(RAT, back, stick, 0.03f, 0.09f)
        pl.carriedBy(CAT, stick, 999f, -0.03f, 0.27f, -30f)
        b.sfx(stick, "pluck", 1.8f, vol = 0.8f)
        cat.until(stick + 0.2f, S.SIT, Expr(tear = true, brow = -1f, smile = -0.9f, eye = 0.8f))
        cat.hold(0.6f, S.SIT, Expr(eye = 1.35f, lookX = sd), freeze = true)
        cat.hold(1.6f, S.SIT, Expr(happy = 1f, blush = 1f, smile = 1f))
        b.say(cat.t - 1.5f, Role.CAT, "You're a good friend 🥹", 1.6f)
        b.fx(cat.t - 1.4f, Fx2.HEART, CAT, 1.4f)
        rat.until(cat.t, S.HAPPY_PAT, Expr(blush = 1f, happy = 1f))
    }

    /** Chittu tries to pull Billa up, falls on his bottom, tries again… and Billa pats his head. */
    fun hHelpUp() {
        val t = sync(); val sd = s
        val near = cat.x + sd * 0.15f
        rat.move(near, (abs(near - rat.x) / 0.4f).coerceIn(0.4f, 1.4f), S.WALK, face = -sd)
        b.say(rat.t, Role.RAT, "Come on, up you get!", 1.3f)
        rat.hold(0.9f, S.IDLE, Expr(armL = 100f, armR = 100f, brow = 0.6f, shake = 0.5f, sweat = true), freeze = true)
        rat.shape(0.25f, S.SIT, 1.1f, 0.85f, ease = Ease.ZIP, x1 = rat.x + sd * 0.03f)                   // plop
        b.sfx(rat.t, "pop", 0.8f)
        rat.hold(0.4f, S.SIT, Expr(eye = 0.5f, mouth = 0.4f))
        rat.shape(0.2f, S.IDLE, 1f, 1f, ease = Ease.BACK, x1 = rat.x - sd * 0.03f)
        rat.hold(0.9f, S.IDLE, Expr(armL = 100f, armR = 100f, brow = 0.8f, shake = 0.6f, sweat = true), freeze = true)
        val up = rat.t
        cat.until(up - 0.9f, S.SIT, Expr(tear = true, brow = -0.8f, smile = -0.5f, lookX = sd))
        cat.until(up - 0.3f, S.SIT, Expr(eye = 1.2f, lookX = sd, smile = 0.3f))
        cat.shape(0.4f, S.IDLE, 1f, 1f, ease = Ease.BACK, expr = Expr(smile = 0.8f, lookX = sd), face = sd)
        cat.hold(1.6f, S.HAPPY_PAT, Expr(happy = 1f, smile = 1f))
        b.say(cat.t - 1.5f, Role.CAT, "Thanks, little buddy 💛", 1.5f)
        b.fx(cat.t - 1.2f, Fx2.HEARTS, CAT, 1.4f)
        rat.until(cat.t, S.HAPPY_PAT, Expr(blush = 1f, happy = 1f))
    }

    /** Chittu pulls silly faces and dances until Billa can't help laughing. */
    fun hLaugh() {
        val t = sync(); val sd = s
        val near = cat.x + sd * 0.2f
        rat.move(near, (abs(near - rat.x) / 0.5f).coerceIn(0.4f, 1.2f), S.WALK, face = -sd)
        b.say(rat.t, Role.RAT, "Hey… look at me!", 1.0f)
        rat.hold(1.6f, S.DANCE, Expr(happy = 1f, smile = 1f, mouth = 0.7f))
        b.fx(rat.t - 1.6f, Fx2.NOTES, RAT, 1.6f)
        tiptoe(rat.t - 1.6f, rat.t, 0.2f, 1.4f)
        rat.hold(0.8f, S.GIGGLE, Expr(eye = 0.3f, mouth = 1f, lookX = 0f, head = 20f))
        cat.until(rat.t - 2.0f, S.SIT, Expr(tear = true, brow = -0.8f, smile = -0.6f, lookX = sd))
        cat.until(rat.t - 0.6f, S.SIT, Expr(brow = 0.3f, smile = 0.2f, lookX = sd, blush = 0.4f))
        cat.hold(1.6f, S.GIGGLE, Expr(smile = 1f, happy = 1f))
        b.say(cat.t - 1.5f, Role.CAT, "Haha! Stop it! 😹", 1.4f)
        b.fx(cat.t - 1.0f, Fx2.HEARTS, CAT, 1.4f)
        rat.until(cat.t, S.GIGGLE, Expr(smile = 1f, happy = 1f))
    }

    // ───────────── ENDINGS (all end under the iris) ─────────────

    /** Both yawn and fall asleep side by side; a heart floats up. */
    fun eSleep(): Pair<Float, Float> {
        val t = sync(); val sd = s
        rat.move(cat.x + sd * 0.12f, 0.4f, S.WALK, face = -sd)
        cat.hold(0.6f, S.SIT, Expr(mouth = 0.9f, eye = 0.3f))
        rat.hold(0.5f, S.IDLE, Expr(mouth = 0.8f, eye = 0.3f))
        cat.until(t + 3.4f, S.SLEEP, Expr(zzz = 1f))
        rat.until(t + 3.4f, S.SLEEP, Expr(zzz = 1f))
        val mid = (cat.x + rat.x) / 2f
        b.fx(t + 1.1f, Fx2.HEART, null, 2.2f, x = mid, y = 0.3f, big = 1.8f)
        b.sfx(t + 1.1f, "tada", 1f)
        return mid to 0.1f
    }

    /** Chittu hops onto Billa's head and they walk off together, waving. */
    fun eRide(): Pair<Float, Float> {
        val t = sync(); val sd = s
        cat.shape(0.4f, S.IDLE, 1f, 1f, 0f, pvx = 0f, pvy = 0f, expr = Expr(smile = 1f))
        rat.until(t + 0.4f, S.IDLE, Expr(smile = 1f, lookX = -sd))
        rat.move(cat.x, 0.5f, S.JUMP, Ease.LINEAR, y1 = 0.30f, hop = 0.1f)
        b.sfx(t + 0.4f, "boing", 1.5f, vol = 0.6f)
        b.say(t + 0.9f, Role.RAT, "Giddy up! 🐱", 1.1f)
        val goal = if (abs(cat.x - 0.5f) < 0.12f) cat.x + (if (cat.x < 0.5f) 0.15f else -0.15f) else 0.5f
        cat.until(t + 0.9f, S.IDLE, Expr(smile = 1f, lookY = -1f))
        cat.move(goal, 2.0f, S.WALK, Ease.IN_OUT, expr = Expr(smile = 1f, happy = 0.5f))
        rat.move(goal, 2.0f, S.SIT, Ease.IN_OUT, y1 = 0.30f, bounce = 0.006f, face = cat.face, expr = Expr(happy = 1f, smile = 1f))
        cat.hold(1.2f, S.WAVE, Expr.take(1f, smile = 1f))
        rat.hold(1.2f, S.WAVE, Expr.take(1f, smile = 1f))
        b.fx(cat.t - 1.1f, Fx2.HEARTS, null, 2.0f, x = goal, y = 0.5f, big = 1.4f)
        b.sfx(cat.t - 1.1f, "tada", 1f)
        return goal to 0.25f
    }

    /** All better… so now it's Chittu's turn to chase Billa, right off the screen. Then a wave from the iris. */
    fun eReverse(): Pair<Float, Float> {
        val t = sync(); val sd = s
        cat.shape(0.4f, S.IDLE, 1f, 1f, 0f, pvx = 0f, pvy = 0f, expr = Expr(smile = 0.6f), face = sd)
        rat.hold(0.4f, S.IDLE, Expr(smile = 1f, brow = 0.8f))
        val feather = b.track("feather2").place(0f, 0f, visible = false).hide(t + 0.4f)
        feather.appear(0f).until(t + 9f, S.IDLE).hide()
        feather.carriedBy(RAT, t + 0.4f, t + 9f, 0.05f, 0.11f, -40f)
        b.say(t + 0.5f, Role.RAT, "Now it's MY turn! 😈", 1.3f)
        cat.until(t + 1.6f, S.STARTLED, Expr(eye = 1.45f, mouth = 0.9f, sweat = true), freeze = true)
        b.say(t + 1.6f, Role.CAT, "Eek! Not the tickles!", 1.2f)
        val off = if (sd > 0f) -0.2f else 1.2f
        cat.move(off, 1.4f, S.DASH, Ease.IN, face = -sd, expr = Expr(eye = 1.45f, mouth = 0.8f, sweat = true, brow = -1f)).hide()
        rat.until(t + 1.9f, S.IDLE, Expr(smile = 1f, brow = 0.8f))
        rat.move(off, 1.6f, S.DASH, Ease.IN, face = -sd, expr = Expr(smile = 1f, brow = 0.8f)).hide()
        b.sfx(t + 1.6f, "zip", 1.1f); b.sfx(t + 1.9f, "zip", 1.4f)
        val back = rat.t + 0.6f
        rat.until(back, S.IDLE)
        rat.pop(0.5f, face = 1f, state = S.WAVE)
        rat.hold(0.4f, S.WAVE, Expr.take(1f, smile = 1f))
        b.sfx(back, "pop", 1.3f); b.sfx(back + 0.2f, "tada", 1f)
        b.say(back + 0.2f, Role.RAT, "Bye bye! 👋", 1.3f)
        b.fx(back + 0.2f, Fx2.HEART, RAT, 1.4f)
        cat.until(rat.t, S.IDLE)
        return 0.5f to 0.12f
    }

    /** Side by side, they turn to you and wave goodbye. */
    fun eWave(): Pair<Float, Float> {
        val t = sync(); val sd = s
        cat.shape(0.4f, S.IDLE, 1f, 1f, 0f, pvx = 0f, pvy = 0f, expr = Expr(smile = 1f))
        rat.move(cat.x + sd * 0.17f, 0.4f, S.WALK, face = -sd)
        cat.hold(1.6f, S.WAVE, Expr.take(1f, smile = 1f))
        rat.hold(1.6f, S.WAVE, Expr.take(1f, smile = 1f))
        b.say(t + 0.5f, Role.CAT, "Bye bye! 👋", 1.2f)
        b.say(t + 0.7f, Role.RAT, "See you! 💛", 1.2f)
        val mid = (cat.x + rat.x) / 2f
        b.fx(t + 0.8f, Fx2.HEARTS, null, 2.0f, x = mid, y = 0.45f, big = 1.4f)
        b.sfx(t + 0.8f, "tada", 1f)
        return mid to 0.15f
    }
}
