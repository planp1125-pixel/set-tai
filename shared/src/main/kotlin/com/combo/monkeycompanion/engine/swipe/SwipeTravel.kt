package com.combo.monkeycompanion.engine.swipe

import com.combo.monkeycompanion.rig.Pose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * v10.24 each character's own swipe trip: a swipe/fling sends the character to the swiped spot in its own style and it
 * lands there. Pure functions of trip progress k (0..1) and time t: NO Random (GoldenTraceTest / frame-loop RNG safe).
 * Positions are the overlay box's top-left in px; [u] = px per dp.
 */
object SwipeTravel {
    enum class Style(val secs: Float, val hovers: Boolean, val label: String) {
        SURF(3.4f, true, "surf show"),
        DIVE_POP(1.7f, false, "jump into the screen, pop out"),
        MOONWALK(1.9f, false, "moonwalk + spin glide"),
        /** v10.26 Gajju: heavy little elephant, flies with great difficulty (frantic flaps, wobbles, a scary dip, relief). */
        EAR_FLAP(2.4f, true, "clumsy baby-elephant flight"),
        SHEET_TRICK(2.6f, false, "bedsheet magic trick"),
        POUNCE(1.3f, false, "cat pounce"),
        MOUSE_HOLE(1.9f, false, "mouse-hole dive"),
        SKATE(5.4f, false, "skate show"),
        RUN(1.3f, false, "run"),
        /** v10.25.1 Tutty (red smoke) / Angel (white-gold): vanish, reappear a hop further, 3-5 times, then land. */
        MAGIC_HOPS(2.0f, true, "smoke-puff hops"),
        /** v10.26 Angel: graceful wing flight with a golden sparkle trail. */
        WINGS(1.7f, true, "angel wing flight"),
    }

    /** v10.26 a normal swipe up / down on a flyer is a TOUR: a big curve around the screen in its own style. */
    const val TOUR_NONE = 0
    const val TOUR_DOWN = 1
    const val TOUR_UP = 2
    /** v10.26 faster than this (dp/s) = a flick: thrown (splat / thwack / pancake) or hidden at a close edge. */
    const val FLICK_DP = 1600f

    /** null = this character keeps its old swipe (Bolt, Zoom, Skye, Tutty, Angel). */
    fun styleFor(id: String, custom: Boolean): Style? = when (id) {
        "surfer" -> Style.SURF
        "monkey" -> Style.DIVE_POP
        "hollowman" -> Style.MOONWALK
        "gajju" -> Style.EAR_FLAP
        "mask" -> Style.SHEET_TRICK
        "billa" -> Style.POUNCE
        "chittu" -> Style.MOUSE_HOLE
        "skater" -> Style.SKATE
        "devil" -> Style.MAGIC_HOPS
        "angel" -> Style.WINGS
        "armour", "superboy", "rocketgirl" -> null
        else -> if (custom) Style.RUN else null
    }

    /** Where a fling lands: along the swipe, inside the screen; floor styles end ON the floor (y = [ground]). */
    fun target(style: Style, x0: Float, y0: Float, fvx: Float, fvy: Float, minX: Float, maxX: Float, minY: Float, ground: Float): FloatArray {
        val x = (x0 + fvx * 0.38f).coerceIn(minX, maxX)
        val y = if (style.hovers || style == Style.SHEET_TRICK) (y0 + fvy * 0.3f).coerceIn(minY, ground) else ground
        return floatArrayOf(x, y)
    }

    fun ss(e0: Float, e1: Float, x: Float): Float { val k = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return k * k * (3f - 2f * k) }
    fun bell(a: Float, b: Float, x: Float): Float = if (x <= a || x >= b) 0f else sin(((x - a) / (b - a)) * PI.toFloat())

    /** One frame of a trip: where the box is and how it's drawn. */
    class Frame {
        var x = 0f; var y = 0f
        var scale = 1f; var alpha = 1f; var rot = 0f
        /** Style fx progress, read by [SwipeFx]: sheet, hole at the start / end spot, curl, spray burst. */
        var sheet = 0f; var sheetFall = 0f; var holeA = 0f; var holeB = 0f; var curl = 0f; var splash = 0f; var sparkle = 0f
        /** Mouse-hole sink below the floor line (px; the view clips the body at the floor). */
        var sink = 0f
        /** v10.25.1 smoke puff 0..1 (MAGIC_HOPS). */
        var puff = 0f
        /** true once the body is at the target side (for fx that stay at the start before the swap). */
        var atTarget = false
    }

    /**
     * [tour] = [TOUR_NONE] a direct trip; [TOUR_DOWN] / [TOUR_UP] a big curve around the screen (inside the box
     * bounds [minX]..[maxX], [minY]..[maxY]) that ends at the target.
     */
    class Trip(val style: Style, val x0: Float, val y0: Float, val x1: Float, val y1: Float, val u: Float,
               val tour: Int = TOUR_NONE, minX: Float = Float.NaN, maxX: Float = Float.NaN, minY: Float = Float.NaN, maxY: Float = Float.NaN) {
        var k = 0f; private set
        val done get() = k >= 1f
        val dir get() = if (x1 >= x0) 1f else -1f
        /** v10.26.1 tours are brisk loops like Bolt's flights (a little longer than a direct trip). */
        val secs: Float get() = if (tour == TOUR_NONE) style.secs else when (style) {
            Style.MAGIC_HOPS -> 0.42f * (wx.size - 1)          // one teleport per waypoint
            Style.EAR_FLAP -> 3.4f                               // he's heavy
            Style.SURF -> 3.6f
            else -> 2.6f
        }
        /**
         * The tour: a loop through waypoints (Catmull-Rom, smooth). Up = first to the TOP MIDDLE of the screen, then a round
         * loop like Bolt's, and it stays up at the top. Down = up to the top middle, round the loop, then down to land.
         */
        private val wx: FloatArray
        private val wy: FloatArray
        init {
            val hasB = !minX.isNaN() && !maxX.isNaN() && !minY.isNaN() && !maxY.isNaN()
            val lx = if (hasB) minX else minOf(x0, x1) - 300f * u; val rx = if (hasB) maxX else maxOf(x0, x1) + 300f * u
            val ty = if (hasB) minY else minOf(y0, y1) - 500f * u; val by = if (hasB) maxY else maxOf(y0, y1)
            val w = rx - lx; val h = by - ty
            val mid = (lx + rx) / 2f; val top = ty + 0.04f * h
            val r = minOf(0.3f * w, 0.18f * h)
            val side = if (x0 < mid) 1f else -1f
            fun cx(v: Float) = v.coerceIn(lx, rx)
            fun cy(v: Float) = v.coerceIn(ty, by)
            when (tour) {
                TOUR_UP -> {
                    wx = floatArrayOf(x0, cx(mid), cx(mid + side * r), cx(mid), cx(mid - side * r), x1)
                    wy = floatArrayOf(y0, cy(top), cy(top + r), cy(top + 2f * r), cy(top + r), y1)
                }
                TOUR_DOWN -> {
                    wx = floatArrayOf(x0, cx(mid), cx(mid + side * r), cx(mid), cx(mid - side * r), cx((mid + x1) / 2f), x1)
                    wy = floatArrayOf(y0, cy(top), cy(top + r), cy(top + 2f * r), cy(top + r), cy(top + 0.55f * h), y1)
                }
                else -> { wx = floatArrayOf(x0, x1); wy = floatArrayOf(y0, y1) }
            }
        }
        /** Point on the way at [e] 0..1 (a straight line, or the tour loop). */
        fun pathX(e: Float): Float = if (tour == TOUR_NONE) x0 + (x1 - x0) * e else spline(wx, e)
        fun pathY(e: Float): Float = if (tour == TOUR_NONE) y0 + (y1 - y0) * e else spline(wy, e)
        /** MAGIC_HOPS on a tour: the hop points are exactly the waypoints (teleport to the top middle first). */
        fun waypoint(i: Int, ys: Boolean) = if (ys) wy[i] else wx[i]
        private fun spline(p: FloatArray, e: Float): Float {
            val n = p.size - 1
            val q = (e.coerceIn(0f, 1f) * n).coerceAtMost(n - 1e-4f); val i = q.toInt(); val t = q - i
            val a = p[maxOf(0, i - 1)]; val b = p[i]; val c = p[i + 1]; val d = p[minOf(n, i + 2)]
            val t2 = t * t; val t3 = t2 * t
            return 0.5f * (2f * b + (-a + c) * t + (2f * a - 5f * b + 4f * c - d) * t2 + (-a + 3f * b - 3f * c + d) * t3)
        }
        /** MAGIC_HOPS: 3..5 hops by distance (5..7 on a tour). */
        val hops: Int get() = if (tour != TOUR_NONE) wx.size - 1 else (kotlin.math.hypot(x1 - x0, y1 - y0) / (110f * u)).toInt().coerceIn(3, 5)
        fun step(dt: Float) { k = (k + dt / secs).coerceAtMost(1f) }
        fun seek(k: Float) { this.k = k.coerceIn(0f, 1f) }

        fun frame(f: Frame = Frame()): Frame {
            val k = k
            f.scale = 1f; f.alpha = 1f; f.rot = 0f; f.sheet = 0f; f.sheetFall = 0f; f.holeA = 0f; f.holeB = 0f; f.curl = 0f; f.splash = 0f; f.sparkle = 0f; f.sink = 0f; f.puff = 0f
            fun lerpX(e: Float) = pathX(e)
            fun lerpY(e: Float) = pathY(e)
            when (style) {
                Style.SURF -> {
                    val e = ss(0f, 1f, k)
                    f.x = lerpX(e)
                    val sTurn = 16f * u * sin(k * 6f * PI.toFloat()) * (1f - ss(0.28f, 0.34f, k))          // carving S-turns
                    val air = 70f * u * bell(0.5f, 0.68f, k)                                                   // aerial off the lip
                    f.y = lerpY(e) + sTurn - air
                    f.curl = bell(0.3f, 0.5f, k)                                                               // tube ride
                    f.splash = bell(0.68f, 0.84f, k)                                                           // cutback spray
                    if (k > 0.94f) f.splash = maxOf(f.splash, bell(0.94f, 1f, k) * 0.6f)                       // landing splash
                    f.atTarget = true
                }
                Style.DIVE_POP -> {
                    val gone = k in 0.42f..0.56f
                    f.atTarget = k >= 0.5f
                    f.x = if (f.atTarget) x1 else x0; f.y = if (f.atTarget) y1 else lerpY(ss(0f, 0.3f, k))
                    f.scale = when {
                        k < 0.42f -> 1f - 0.95f * ss(0.08f, 0.42f, k) + 0.08f * bell(0f, 0.12f, k)        // hop then dive INTO the screen
                        gone -> 0.05f
                        else -> 0.05f + 1.08f * ss(0.56f, 0.82f, k) - 0.13f * ss(0.82f, 1f, k)            // pops out, overshoots, settles
                    }
                    f.rot = if (k < 0.42f) 300f * ss(0.1f, 0.42f, k) else -120f * (1f - ss(0.56f, 0.8f, k))
                    f.alpha = if (gone) 0f else 1f
                    f.sparkle = bell(0.5f, 0.75f, k)
                }
                Style.MOONWALK -> { f.x = lerpX(ss(0f, 1f, k)); f.y = lerpY(ss(0f, 0.4f, k)); f.atTarget = true }
                Style.EAR_FLAP -> {   // v10.26: heavy! a slow start, wobbles, a scary dip half way, then flaps back up
                    val e = ss(0.12f, 1f, k)
                    val lift = 60f * u * sin(ss(0.1f, 1f, k) * PI.toFloat())
                    val wobble = 9f * u * sin(k * 31f) * (1f - ss(0.9f, 1f, k))
                    val dip = 55f * u * bell(0.42f, 0.62f, k)
                    f.x = lerpX(e) + 6f * u * sin(k * 17f) * (1f - ss(0.9f, 1f, k))
                    f.y = lerpY(e) - lift + wobble + dip
                    if (k >= 1f) { f.x = x1; f.y = y1 }
                    f.rot = 10f * sin(k * 23f) * (1f - ss(0.88f, 1f, k)) + 18f * bell(0.42f, 0.6f, k)
                    f.atTarget = true
                }
                Style.WINGS -> {      // v10.26: graceful, gentle bobs with each wing beat, golden sparkles behind
                    val e = ss(0f, 1f, k)
                    f.x = lerpX(e); f.y = lerpY(e) - 34f * u * sin(k * PI.toFloat()) * (if (tour == TOUR_NONE) 1f else 0.3f) +
                        7f * u * sin(k * 18f) * (1f - ss(0.85f, 1f, k))
                    if (k >= 1f) { f.x = x1; f.y = y1 }
                    f.rot = -dir * 8f * bell(0.05f, 0.95f, k)
                    f.sparkle = 0.55f * bell(0.05f, 0.95f, k) + 0.45f * bell(0.85f, 1f, k)
                    f.atTarget = true
                }
                Style.SHEET_TRICK -> {
                    f.atTarget = k >= 0.55f
                    f.x = if (f.atTarget) x1 else x0; f.y = if (f.atTarget) y1 else y0
                    f.sheet = if (k < 0.5f) ss(0f, 0.15f, k) else 0f
                    f.sheetFall = ss(0.34f, 0.5f, k)
                    f.alpha = when { k < 0.3f -> 1f; k < 0.55f -> 0f; else -> ss(0.55f, 0.7f, k) }
                    f.scale = if (f.atTarget) 0.6f + 0.4f * ss(0.55f, 0.75f, k) + 0.08f * bell(0.7f, 0.9f, k) else 1f
                    f.sparkle = bell(0.52f, 0.85f, k)
                }
                Style.POUNCE -> {
                    val e = ss(0.25f, 0.85f, k)
                    f.x = lerpX(e); f.y = lerpY(e) - 90f * u * bell(0.25f, 0.85f, k); f.atTarget = true
                    f.rot = dir * (-14f * bell(0.25f, 0.55f, k) + 14f * bell(0.55f, 0.85f, k))
                }
                Style.MOUSE_HOLE -> {
                    f.atTarget = k >= 0.5f
                    f.x = if (f.atTarget) x1 else x0; f.y = if (f.atTarget) y1 else y0
                    f.holeA = ss(0f, 0.1f, k) * (1f - ss(0.42f, 0.5f, k))
                    f.holeB = ss(0.5f, 0.58f, k) * (1f - ss(0.88f, 1f, k))
                    // dive: drops down into the hole (box moves down, the hole clips it); pops: rises up out of the new hole
                    val sink = when { k < 0.12f -> 0f; k < 0.4f -> ss(0.12f, 0.4f, k); k < 0.6f -> 1f; else -> 1f - ss(0.6f, 0.82f, k) }
                    f.sink = sink * 46f * u
                    f.scale = 1f - 0.3f * sink
                    f.alpha = if (sink > 0.97f) 0f else 1f
                }
                Style.SKATE -> {   // v10.25 show: push, ollie, kickflip, manual, heelflip, grind, wall ride, 360, landing
                    val e = ss(0f, 1f, k)
                    f.x = lerpX(e)
                    val hop = 46f * u * bell(0.1f, 0.22f, k) + 62f * u * bell(0.24f, 0.38f, k) + 56f * u * bell(0.5f, 0.6f, k) +
                        90f * u * bell(0.7f, 0.82f, k) + 50f * u * bell(0.82f, 0.92f, k)
                    f.y = lerpY(ss(0f, 0.2f, k)) - hop
                    f.rot = -dir * 18f * bell(0.1f, 0.22f, k) - dir * 14f * bell(0.4f, 0.5f, k) + dir * 65f * bell(0.7f, 0.82f, k)
                    f.splash = bell(0.93f, 1f, k)
                    f.atTarget = true
                }
                Style.MAGIC_HOPS -> {   // each hop: fade out in a puff, jump on, fade back in out of a puff
                    val n = hops; val q = (k * n).coerceAtMost(n - 0.0001f); val i = q.toInt(); val h = q - i
                    val at = if (h < 0.45f) i.toFloat() / n else (i + 1f) / n
                    val wi = if (h < 0.45f) i else i + 1
                    f.x = if (tour != TOUR_NONE) waypoint(wi, false) else lerpX(at)
                    f.y = (if (tour != TOUR_NONE) waypoint(wi, true) else lerpY(at)) - (if (h < 0.45f) 0f else 18f * u * bell(0.45f, 1f, h))
                    f.alpha = when { h < 0.25f -> 1f; h < 0.45f -> 1f - ss(0.25f, 0.45f, h); h < 0.65f -> ss(0.45f, 0.65f, h); else -> 1f }
                    f.scale = 1f - 0.25f * bell(0.2f, 0.7f, h)
                    f.puff = maxOf(bell(0.2f, 0.55f, h), bell(0.42f, 0.8f, h))
                    if (k >= 1f) { f.x = x1; f.y = y1; f.alpha = 1f; f.scale = 1f; f.puff = 0f }
                    f.atTarget = true
                }
                Style.RUN -> { f.x = lerpX(ss(0f, 1f, k)); f.y = lerpY(ss(0f, 0.4f, k)); f.atTarget = true }
            }
            return f
        }

        /** The body pose for this moment (applied over the shared pose; facing is handled by the view). */
        fun applyPose(p: Pose, t: Float) {
            val k = k
            when (style) {
                Style.SURF -> {
                    p.surf = 1f
                    val lean = 22f * cos(k * 6f * PI.toFloat()) * (1f - ss(0.28f, 0.34f, k))
                    val crouch = 0.4f + 0.5f * bell(0.3f, 0.5f, k) + 0.4f * bell(0.66f, 0.72f, k)
                    p.bodyY += -20f + 6f * crouch; p.bodyRot = lean
                    p.legLSwing = -18f - 8f * crouch; p.legRSwing = 16f + 8f * crouch
                    p.armLRaise = 70f + lean; p.armRRaise = 55f - lean
                    if (k in 0.5f..0.68f) { val a = ss(0.52f, 0.66f, k) * 2f * PI.toFloat(); p.flipS = sin(a); p.flipC = cos(a) } else { p.flipS = 0f; p.flipC = 1f }
                    if (k in 0.68f..0.84f) { p.spinX = cos(ss(0.68f, 0.84f, k) * 2f * PI.toFloat()); p.bodyRot = -28f * bell(0.68f, 0.84f, k) }
                    p.surfSpray = 0.6f + 0.4f * maxOf(bell(0.68f, 0.84f, k), bell(0.3f, 0.5f, k))
                    p.mouthSmile = 1f; p.shadow = 0.3f
                }
                Style.DIVE_POP -> {
                    if (k < 0.42f) { p.squashY = 1f - 0.2f * bell(0f, 0.12f, k); p.squashX = 1f / p.squashY; p.armLRaise = 160f; p.armRRaise = 160f }
                    else { p.armLRaise = 150f * (1f - ss(0.8f, 1f, k)) + 20f; p.armRRaise = p.armLRaise; p.eyesHappy = if (k > 0.8f) 1f else 0f }
                    p.mouthOpen = 0.5f * bell(0.1f, 0.4f, k)
                }
                Style.MOONWALK -> {
                    p.hmMoon = if (k < 0.4f || k > 0.62f) 1f else 0f
                    p.spinX = if (k in 0.4f..0.62f) cos(ss(0.4f, 0.62f, k) * 4f * PI.toFloat()) else 1f
                    p.legLSwing = 22f * sin(t * 11f); p.legRSwing = -22f * sin(t * 11f)
                    p.legLLift = 6f * maxOf(0f, sin(t * 11f)); p.legRLift = 6f * maxOf(0f, -sin(t * 11f))
                    p.armLRaise = 40f + 20f * sin(t * 11f); p.armRRaise = 40f - 20f * sin(t * 11f)
                    if (k > 0.9f) { p.armRRaise = 150f; p.headRot = -8f }   // freeze pose
                }
                Style.EAR_FLAP -> {   // straining: legs kicking, worried eyes, a gasp in the dip, a happy sigh at the end
                    p.shadow = 0f; p.dvAura = 1f - ss(0.9f, 1f, k); p.bodyRot = 10f
                    p.legLSwing = -14f + 16f * sin(t * 13f); p.legRSwing = -6f - 16f * sin(t * 13f); p.legLLift = 6f; p.legRLift = 3f
                    p.armLRaise = 70f + 30f * sin(t * 15f); p.armRRaise = 70f - 30f * sin(t * 15f)
                    val dip = bell(0.4f, 0.66f, k)
                    p.eyeOpen = 1.1f + 0.35f * dip; p.mouthOpen = 0.2f + 0.7f * dip
                    p.browAngle = -14f * (1f - ss(0.85f, 1f, k)); p.mouthSmile = if (k > 0.85f) 1f else -0.2f
                    if (k > 0.9f) p.eyesHappy = 1f
                }
                Style.WINGS -> {
                    p.shadow = 0f; p.dvFire = 1f - 0.7f * ss(0.85f, 1f, k); p.dvAura = 0.5f * bell(0f, 1f, k)
                    p.armLRaise = 100f; p.armRRaise = 100f; p.legLSwing = 15f; p.legRSwing = 22f
                    p.bodyRot = 10f; p.mouthSmile = 1f; if (k > 0.9f) p.eyesHappy = 1f
                }
                Style.SHEET_TRICK -> {
                    if (k < 0.3f) { p.armLRaise = 165f * ss(0f, 0.12f, k); p.armRRaise = p.armLRaise; p.armLElbow = -10f; p.armRElbow = -10f }
                    else if (k >= 0.55f) { p.armLRaise = 150f * bell(0.6f, 1f, k) + 20f; p.armRRaise = p.armLRaise; p.eyesHappy = 1f; p.mouthSmile = 1f }
                }
                Style.POUNCE -> {
                    when {
                        k < 0.25f -> { p.squashY = 0.82f; p.squashX = 1.15f; p.bodyY += 6f; p.bodyRot = 4f * sin(t * 22f) }   // butt wiggle
                        k < 0.85f -> { p.squashY = 1.12f; p.squashX = 0.92f; p.armLRaise = 120f; p.armRRaise = 120f; p.legLSwing = 40f; p.legRSwing = 40f; p.mouthOpen = 0.4f }
                        else -> { val b = bell(0.85f, 1f, k); p.squashY = 1f - 0.2f * b; p.squashX = 1f / p.squashY }
                    }
                }
                Style.MOUSE_HOLE -> {
                    if (k < 0.12f) { p.squashY = 0.85f; p.squashX = 1.15f }
                    else if (k < 0.45f) { p.armLRaise = 170f; p.armRRaise = 170f }
                    else if (k > 0.6f) { p.armLRaise = 150f * (1f - ss(0.85f, 1f, k)); p.armRRaise = p.armLRaise; p.eyesHappy = 1f }
                }
                Style.SKATE -> {
                    p.dvAura = 1f                                                                        // board on
                    p.dvSpell = if (k < 0.45f) ss(0.26f, 0.36f, k) else 1f - ss(0.51f, 0.59f, k)        // kickflip, then heelflip (rolls back)
                    p.surfSpray = bell(0.6f, 0.7f, k)                                                    // grind sparks
                    p.spinX = if (k in 0.82f..0.92f) cos(ss(0.82f, 0.92f, k) * 2f * PI.toFloat()) else 1f   // 360
                    val crouch = maxOf(bell(0.02f, 0.1f, k), bell(0.6f, 0.7f, k), bell(0.92f, 0.97f, k))
                    p.squashY = 1f - 0.14f * crouch; p.squashX = 1f / p.squashY
                    p.legLSwing = -14f; p.legRSwing = 18f + 22f * maxOf(0f, sin(t * 6f)) * bell(0f, 0.1f, k)   // pushing off
                    p.armLRaise = 70f + 60f * maxOf(bell(0.24f, 0.38f, k), bell(0.5f, 0.6f, k)); p.armRRaise = 50f + 70f * maxOf(bell(0.1f, 0.22f, k), bell(0.7f, 0.82f, k))
                    if (k in 0.4f..0.5f) { p.armLRaise = 100f; p.armRRaise = 100f }                       // manual: balance
                    if (k > 0.95f) { p.armLRaise = 160f; p.armRRaise = 160f; p.eyesHappy = 1f }         // landing pose
                    p.mouthSmile = 1f
                }
                Style.MAGIC_HOPS -> { p.armLRaise = 120f; p.armRRaise = 120f; p.mouthSmile = 1f }
                Style.RUN -> {
                    p.legLSwing = 38f * sin(t * 16f); p.legRSwing = -38f * sin(t * 16f)
                    p.armLRaise = 30f - 30f * sin(t * 16f); p.armRRaise = 30f + 30f * sin(t * 16f); p.bodyRot = 8f
                    p.bodyY += -3f * abs(sin(t * 16f))
                }
            }
        }
    }
}
