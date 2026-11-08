package com.combo.monkeycompanion.brain

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * v10.9 Skye's swipe launch: a real little rocket flight, not a straight rise or a canned arc.
 * Thrust along a HEADING that turns gradually toward a target picked along the swipe (power ~ swipe speed), plus
 * gravity and drag, a corkscrew wobble on the heading, then a sputter (thrust coughs) and a glide down. The nose always
 * points along the VELOCITY ([noseDeg]). Pure maths in screen px (y down), own seeded [Random] (never the frame loop's).
 */
class RocketFlight(seed: Long = 1L) {
    enum class Phase { IDLE, IGNITE, BURN, SPUTTER, GLIDE, DONE }

    private val rnd = Random(seed)
    var phase = Phase.IDLE; private set
    var x = 0f; private set
    var y = 0f; private set
    var vx = 0f; private set
    var vy = 0f; private set
    /** Thrust heading (radians, screen space). */
    var heading = 0f; private set
    /** 0..1 how hard the engine pushes right now (drawn as the tail flame burst). */
    var thrust = 0f; private set
    var t = 0f; private set
    var tx = 0f; private set
    var ty = 0f; private set
    var power = 0f; private set
    private var phaseT = 0f
    private var burnSecs = 1f
    private var wobPhase = 0f
    private var wobFreq = 11f
    private var s = 1f
    private var minX = 0f; private var maxX = 1f; private var minY = 0f; private var maxY = 1f

    val active get() = phase != Phase.IDLE && phase != Phase.DONE
    val speed get() = hypot(vx, vy)
    /** Where the nose points: along the velocity (degrees, screen space, 0 = right, -90 = up). */
    val noseDeg: Float get() = if (speed < 1f || phase == Phase.IGNITE) Math.toDegrees(heading.toDouble()).toFloat() else Math.toDegrees(atan2(vy, vx).toDouble()).toFloat()

    /**
     * Launch from (x0, y0) toward the swipe direction (dirX, dirY), [speed01] 0..1 = swipe strength, [dp] = px per dp,
     * inside the box [minX..maxX] x [minY..maxY] (screen px of the rider's centre).
     */
    fun launch(x0: Float, y0: Float, dirX: Float, dirY: Float, speed01: Float, dp: Float, minX: Float, maxX: Float, minY: Float, maxY: Float) {
        s = dp; this.minX = minX; this.maxX = maxX; this.minY = minY; this.maxY = maxY
        x = x0; y = y0; vx = 0f; vy = 0f; t = 0f; phaseT = 0f
        power = speed01.coerceIn(0f, 1f)
        val l = hypot(dirX, dirY).coerceAtLeast(1e-3f)
        var dx = dirX / l; var dy = dirY / l
        // a rocket wants to climb: a flat or downward swipe still launches slightly upward first
        if (dy > -0.25f) { dy = -0.25f; val k = hypot(dx, dy); dx /= k; dy /= k }
        heading = atan2(dy, dx)
        val dist = (230f + 420f * power) * dp
        tx = (x0 + dx * dist).coerceIn(minX, maxX)
        ty = (y0 + dy * dist).coerceIn(minY, maxY)
        burnSecs = 0.75f + 0.75f * power
        wobPhase = rnd.nextFloat() * 2f * PI.toFloat()
        wobFreq = 9f + rnd.nextFloat() * 5f
        // the bearing to the target, curving: start a little off so the climb bends naturally
        heading += (if (rnd.nextBoolean()) 1f else -1f) * (0.12f + rnd.nextFloat() * 0.18f)
        phase = Phase.IGNITE
    }

    fun cancel() { phase = Phase.IDLE; thrust = 0f }

    fun step(dt: Float) {
        if (!active || dt <= 0f) return
        t += dt; phaseT += dt
        val g = 520f * s
        var acc = 0f
        var drag = 1.4f
        var grav = g
        when (phase) {
            Phase.IGNITE -> {   // anticipation: the fuse catches, a little dip back, then the flame bursts
                thrust = (phaseT / 0.14f).coerceIn(0f, 1f) * 0.6f
                vx += (-cos(heading) * 40f * s - vx) * (dt * 10f).coerceAtMost(1f)
                vy += (-sin(heading) * 40f * s + 20f * s - vy) * (dt * 10f).coerceAtMost(1f)
                grav = 0f
                if (phaseT >= 0.14f) next(Phase.BURN)
            }
            Phase.BURN -> {
                thrust = 1f
                acc = (1900f + 1500f * power) * s
                steer(dt, 2.6f)
                if (phaseT >= burnSecs || hypot(tx - x, ty - y) < 34f * s) next(Phase.SPUTTER)
            }
            Phase.SPUTTER -> {   // cough cough: thrust flickers out
                val on = sin(phaseT * 70f) > -0.2f
                thrust = if (on) 0.55f * (1f - phaseT / 0.45f) else 0.05f
                acc = thrust * 1300f * s
                steer(dt, 1.2f)
                if (phaseT >= 0.45f) next(Phase.GLIDE)
            }
            Phase.GLIDE -> {   // engine out: a floaty glide down, the nose dipping with the velocity
                thrust = 0f
                grav = g * 0.32f; drag = 1.9f
                if (phaseT >= 1.0f && speed < 140f * s || phaseT >= 2.2f) next(Phase.DONE)
            }
            else -> Unit
        }
        if (acc > 0f) {
            // corkscrew wobble on the heading
            val wob = 0.22f * sin(t * wobFreq + wobPhase)
            vx += cos(heading + wob) * acc * dt; vy += sin(heading + wob) * acc * dt
        }
        vy += grav * dt
        val d = exp(-drag * dt)
        vx *= d; vy *= d
        x += vx * dt; y += vy * dt
        // the screen edges: bounce off softly and turn back in
        // (the velocity bounces; the rocket's heading then TURNS back in gradually toward a new target inside)
        if (x < minX) { x = minX; vx = -vx * 0.4f; tx = x + 0.35f * (maxX - minX); ty = (y - 60f * s).coerceIn(minY, maxY) }
        if (x > maxX) { x = maxX; vx = -vx * 0.4f; tx = x - 0.35f * (maxX - minX); ty = (y - 60f * s).coerceIn(minY, maxY) }
        if (y < minY) { y = minY; vy = -vy * 0.3f; ty = y + 0.25f * (maxY - minY) }
        if (y > maxY) { y = maxY; if (vy > 0f) vy = 0f; if (phase == Phase.GLIDE) next(Phase.DONE) }
    }

    /** Turn the heading toward the target at most [rate] rad/s (gradual: the path curves). */
    private fun steer(dt: Float, rate: Float) {
        val want = atan2(ty - y, tx - x)
        var d = want - heading
        while (d > PI) d -= 2f * PI.toFloat()
        while (d < -PI) d += 2f * PI.toFloat()
        heading += d.coerceIn(-rate * dt, rate * dt)
    }

    private fun next(p: Phase) { phase = p; phaseT = 0f }
}
