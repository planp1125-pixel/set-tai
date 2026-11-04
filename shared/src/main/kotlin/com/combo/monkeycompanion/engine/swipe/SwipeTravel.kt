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
        SURF(4.2f, true, "surf show"),
        DIVE_POP(1.7f, false, "jump into the screen, pop out"),
        MOONWALK(1.9f, false, "moonwalk + spin glide"),
        EAR_FLAP(2.0f, true, "ear-wing flight"),
        SHEET_TRICK(2.6f, false, "bedsheet magic trick"),
        POUNCE(1.3f, false, "cat pounce"),
        MOUSE_HOLE(1.9f, false, "mouse-hole dive"),
        SKATE(5.4f, false, "skate show"),
        RUN(1.3f, false, "run"),
    }

    /** null = this character keeps its old swipe (Anna, Superboy, Rocket Girl, Kutti Satan, Angel). */
    fun styleFor(id: String, custom: Boolean): Style? = when (id) {
        "surfer" -> Style.SURF
        "monkey" -> Style.DIVE_POP
        "hollowman" -> Style.MOONWALK
        "gajju" -> Style.EAR_FLAP
        "mask" -> Style.SHEET_TRICK
        "billa" -> Style.POUNCE
        "chittu" -> Style.MOUSE_HOLE
        "skater" -> Style.SKATE
        "armour", "superboy", "rocketgirl", "devil", "angel" -> null
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
        /** true once the body is at the target side (for fx that stay at the start before the swap). */
        var atTarget = false
    }

    class Trip(val style: Style, val x0: Float, val y0: Float, val x1: Float, val y1: Float, val u: Float) {
        var k = 0f; private set
        val done get() = k >= 1f
        val dir get() = if (x1 >= x0) 1f else -1f
        fun step(dt: Float) { k = (k + dt / style.secs).coerceAtMost(1f) }
        fun seek(k: Float) { this.k = k.coerceIn(0f, 1f) }

        fun frame(f: Frame = Frame()): Frame {
            val k = k
            f.scale = 1f; f.alpha = 1f; f.rot = 0f; f.sheet = 0f; f.sheetFall = 0f; f.holeA = 0f; f.holeB = 0f; f.curl = 0f; f.splash = 0f; f.sparkle = 0f; f.sink = 0f
            fun lerpX(e: Float) = x0 + (x1 - x0) * e
            fun lerpY(e: Float) = y0 + (y1 - y0) * e
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
                Style.EAR_FLAP -> {
                    val e = ss(0f, 1f, k)
                    f.x = lerpX(e); f.y = lerpY(e) - 60f * u * sin(k * PI.toFloat()); f.atTarget = true
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
                Style.EAR_FLAP -> {
                    p.shadow = 0f; p.dvAura = 1f - ss(0.85f, 1f, k); p.bodyRot = 10f
                    p.legLSwing = -14f; p.legRSwing = -6f; p.legLLift = 6f; p.legRLift = 3f; p.mouthSmile = 1f
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
                Style.RUN -> {
                    p.legLSwing = 38f * sin(t * 16f); p.legRSwing = -38f * sin(t * 16f)
                    p.armLRaise = 30f - 30f * sin(t * 16f); p.armRRaise = 30f + 30f * sin(t * 16f); p.bodyRot = 8f
                    p.bodyY += -3f * abs(sin(t * 16f))
                }
            }
        }
    }
}
