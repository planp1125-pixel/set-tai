package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.combo.monkeycompanion.brain.PowerPhase
import com.combo.monkeycompanion.brain.Superpower
import kotlin.math.hypot
import kotlin.random.Random

/**
 * v3.2 superpower particles: a fixed pool (struct of arrays, capped) in view pixels. Nothing is
 * allocated per frame; the pool is only touched while a power runs, then drains and goes idle.
 */
class Particles(val cap: Int = CAP) {
    private val kind = IntArray(cap)
    private val x = FloatArray(cap); private val y = FloatArray(cap)
    private val vx = FloatArray(cap); private val vy = FloatArray(cap)
    private val age = FloatArray(cap); private val life = FloatArray(cap)
    private val size = FloatArray(cap)
    var count = 0; private set
    val active get() = count > 0

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    fun clear() { count = 0 }

    /** Adds a particle; when full the oldest slot is recycled (the cap always holds). */
    fun spawn(k: Int, px: Float, py: Float, pvx: Float, pvy: Float, lifeS: Float, sz: Float) {
        var i = count
        if (i >= cap) {
            var best = 0; var most = -1f
            for (j in 0 until cap) { val f = age[j] / life[j]; if (f > most) { most = f; best = j } }
            i = best
        } else count++
        kind[i] = k; x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy; age[i] = 0f; life[i] = lifeS; size[i] = sz
    }

    fun update(dt: Float) {
        var i = 0
        while (i < count) {
            age[i] += dt
            if (age[i] >= life[i]) {
                val l = count - 1
                kind[i] = kind[l]; x[i] = x[l]; y[i] = y[l]; vx[i] = vx[l]; vy[i] = vy[l]; age[i] = age[l]; life[i] = life[l]; size[i] = size[l]
                count--
                continue
            }
            x[i] += vx[i] * dt; y[i] += vy[i] * dt
            when (kind[i]) {
                FLAME -> { vx[i] *= 0.9f; vy[i] *= 0.9f }
                SMOKE -> { vx[i] *= 0.95f; vy[i] = vy[i] * 0.95f - 40f * dt * size[i] / 10f }
                DUST -> { vx[i] *= 0.9f; vy[i] = vy[i] * 0.9f + 30f * dt }
                SPARK -> { vx[i] *= 0.86f; vy[i] *= 0.86f }
                SNOW -> { vx[i] = vx[i] * 0.98f + kotlin.math.sin(age[i] * 5f + size[i]) * 6f * dt * size[i] }
                SPARKLE -> { vx[i] *= 0.94f; vy[i] = vy[i] * 0.94f + 10f * dt }
                CONFETTI -> { vx[i] *= 0.97f; vy[i] = vy[i] * 0.97f + 260f * dt * size[i] / 10f }
                WATER -> { vx[i] *= 0.995f; vy[i] += 110f * size[i] * dt }   // v10.2: arcs and falls
                EMBER -> { vx[i] *= 0.985f; vy[i] = vy[i] * 0.985f + 75f * size[i] * dt }   // v10.10: a rocket spark, falls with gravity
                WISP -> { vx[i] *= 0.93f; vy[i] = vy[i] * 0.93f - 14f * dt * size[i] / 4f }
                else -> Unit
            }
            i++
        }
    }

    fun draw(c: Canvas) {
        for (i in 0 until count) {
            val f = age[i] / life[i]
            val a = 1f - f
            when (kind[i]) {
                FLAME -> {
                    fill.color = if (f < 0.35f) Color.rgb(0xFF, 0xE8, 0x6A) else Color.rgb(0xFF, 0x8A, 0x1E)
                    fill.alpha = (230 * a).toInt()
                    c.drawCircle(x[i], y[i], size[i] * (1f - 0.6f * f), fill)
                }
                SMOKE -> {
                    fill.color = Color.rgb(0x55, 0x52, 0x50); fill.alpha = (150 * a).toInt()
                    c.drawCircle(x[i], y[i], size[i] * (0.7f + 1.4f * f), fill)
                }
                DUST -> {
                    fill.color = Color.rgb(0xC9, 0xA8, 0x7A); fill.alpha = (170 * a).toInt()
                    c.drawCircle(x[i], y[i], size[i] * (0.6f + 1.2f * f), fill)
                }
                RING -> {
                    val r = size[i] * (1f + 2.6f * f)
                    stroke.color = Color.rgb(0x3F, 0xD8, 0xFF); stroke.alpha = (150 * a).toInt(); stroke.strokeWidth = r * 0.5f
                    c.drawCircle(x[i], y[i], r, stroke)
                    stroke.color = Color.WHITE; stroke.alpha = (250 * a).toInt(); stroke.strokeWidth = r * 0.2f
                    c.drawCircle(x[i], y[i], r, stroke)
                }
                SPARK -> {
                    val sp = hypot(vx[i], vy[i]).coerceAtLeast(1f)
                    stroke.color = Color.rgb(0xBF, 0xE8, 0xFF); stroke.alpha = (240 * a).toInt(); stroke.strokeWidth = size[i] * 0.35f
                    c.drawLine(x[i], y[i], x[i] - vx[i] / sp * size[i] * 1.6f, y[i] - vy[i] / sp * size[i] * 1.6f, stroke)
                }
                SNOW -> {
                    fill.color = Color.WHITE; fill.alpha = (230 * minOf(1f, a * 3f)).toInt()
                    c.drawCircle(x[i], y[i], size[i] * 0.5f, fill)
                }
                SPARKLE -> {
                    val r = size[i] * (1f - 0.5f * f)
                    stroke.color = if (i % 2 == 0) Color.rgb(0xFF, 0xD3, 0x3A) else Color.WHITE; stroke.alpha = (250 * a).toInt(); stroke.strokeWidth = r * 0.28f
                    c.drawLine(x[i] - r, y[i], x[i] + r, y[i], stroke); c.drawLine(x[i], y[i] - r, x[i], y[i] + r, stroke)
                }
                CONFETTI -> {
                    fill.color = CONFETTI_COLS[i % CONFETTI_COLS.size]; fill.alpha = (255 * minOf(1f, a * 2.5f)).toInt()
                    val wob = kotlin.math.abs(kotlin.math.sin(age[i] * 9f + i))
                    c.drawRect(x[i] - size[i] * 0.5f, y[i] - size[i] * 0.25f * wob, x[i] + size[i] * 0.5f, y[i] + size[i] * 0.25f * wob + 0.5f, fill)
                }
                WATER -> {
                    // A light-blue drop with a short streak behind it and a white glint.
                    val sp = hypot(vx[i], vy[i]).coerceAtLeast(1f)
                    val r = size[i] * 0.5f; val al = minOf(1f, a * 2.2f)
                    stroke.color = Color.rgb(0x7F, 0xC8, 0xFF); stroke.alpha = (150 * al).toInt(); stroke.strokeWidth = r * 1.2f
                    c.drawLine(x[i], y[i], x[i] - vx[i] / sp * r * 2.6f, y[i] - vy[i] / sp * r * 2.6f, stroke)
                    fill.color = Color.rgb(0x4F, 0xB3, 0xFF); fill.alpha = (225 * al).toInt(); c.drawCircle(x[i], y[i], r, fill)
                    fill.color = Color.WHITE; fill.alpha = (220 * al).toInt(); c.drawCircle(x[i] - r * 0.3f, y[i] - r * 0.3f, r * 0.32f, fill)
                }
                EMBER -> {
                    // v10.10: a tiny spark streak along its motion: white-hot, then gold / orange, fading
                    val sp = hypot(vx[i], vy[i]).coerceAtLeast(1f)
                    val l = size[i] * (0.6f + 1.4f * (1f - f))
                    stroke.color = if (f < 0.22f) Color.WHITE else if (i % 3 == 0) Color.rgb(0xFF, 0x8A, 0x1A) else Color.rgb(0xFF, 0xD8, 0x5A)
                    stroke.alpha = (245 * a).toInt(); stroke.strokeWidth = size[i] * 0.32f
                    c.drawLine(x[i], y[i], x[i] - vx[i] / sp * l, y[i] - vy[i] / sp * l, stroke)
                }
                WISP -> {
                    fill.color = Color.rgb(0x9A, 0x9A, 0xA2); fill.alpha = (70 * a).toInt()
                    c.drawCircle(x[i], y[i], size[i] * (0.5f + 1.3f * f), fill)
                }
                SPEED -> {
                    val sp = hypot(vx[i], vy[i]).coerceAtLeast(1f)
                    val l = size[i] * (1f - 0.5f * f)
                    stroke.color = Color.WHITE; stroke.alpha = (200 * a).toInt(); stroke.strokeWidth = size[i] * 0.06f
                    c.drawLine(x[i], y[i], x[i] + vx[i] / sp * l, y[i] + vy[i] / sp * l, stroke)
                    stroke.color = Color.rgb(0x5A, 0x36, 0x18); stroke.alpha = (90 * a).toInt(); stroke.strokeWidth = size[i] * 0.025f
                    c.drawLine(x[i], y[i], x[i] + vx[i] / sp * l, y[i] + vy[i] / sp * l, stroke)
                }
            }
        }
    }

    companion object {
        const val CAP = 64
        const val FLAME = 0; const val SMOKE = 1; const val DUST = 2; const val RING = 3; const val SPEED = 4
        const val SPARK = 5; const val SNOW = 6; const val SPARKLE = 7; const val CONFETTI = 8; const val WATER = 9
        /** v10.10 rocket exhaust: a tiny falling spark and a thin smoke wisp. */
        const val EMBER = 10; const val WISP = 11
        private val CONFETTI_COLS = intArrayOf(Color.rgb(0xFF, 0x99, 0x33), Color.rgb(0x3D, 0xF5, 0xC8), Color.rgb(0xFF, 0x5A, 0x8A), Color.rgb(0xFF, 0xE2, 0x5A), Color.WHITE)
    }
}

/**
 * Spawns a power's particles from the rig each frame (feet jets, palm rings, speed lines, landing
 * dust, crash smoke). Shared by the overlay and the preview sheet. [s] = rig scale (px per unit).
 */
class PowerEmitter(val parts: Particles) {
    private val rnd = Random(7)
    private var flameAcc = 0f
    private var smokeAcc = 0f
    private var speedAcc = 0f
    private var ringAcc = 0f
    private var lastIndex = -1
    private var dusted = false
    private var lastPop = -1
    private val a = FloatArray(2)
    private val b = FloatArray(2)
    /** Which hand fired last (alternates). */
    var leftHand = false; private set
    /** Seconds since the last ring (pose recoil). */
    var sinceRing = 9f; private set

    private var ox = 0f
    private var oy = 0f
    private fun foot(rig: MonkeyRig, left: Boolean, out: FloatArray, down: Float) { rig.footPoint(left, out, down); out[0] += ox; out[1] += oy }
    private fun hand(rig: MonkeyRig, left: Boolean, out: FloatArray) { rig.handPoint(left, out); out[0] += ox; out[1] += oy }
    private fun hip(rig: MonkeyRig, out: FloatArray) { rig.hipPoint(out); out[0] += ox; out[1] += oy }
    private fun headTop(rig: MonkeyRig, out: FloatArray) { rig.headTop(out); out[0] += ox; out[1] += oy }

    fun reset() { flameAcc = 0f; smokeAcc = 0f; speedAcc = 0f; ringAcc = 0f; lastIndex = -1; dusted = false; lastPop = -1; sinceRing = 9f }

    /**
     * One frame. [vx],[vy] = monkey velocity in px/s, [facing] +1/-1, [index]/[phase]/[k] from the
     * flight, [s] = px per rig unit. Call after rig.layout.
     */
    fun frame(rig: MonkeyRig, p: Pose, power: Superpower, phase: PowerPhase, index: Int, k: Float,
              vx: Float, vy: Float, facing: Float, s: Float, dt: Float, offX: Float = 0f, offY: Float = 0f) {
        ox = offX; oy = offY
        if (index != lastIndex) { lastIndex = index; dusted = false; ringAcc = 0.15f }
        sinceRing += dt
        // Foot jets: flames (and black smoke while sputtering).
        if (power.has(com.combo.monkeycompanion.brain.PowerKit.FOOT_JETS) && p.jets > 0.08f) {
            flameAcc += dt * 34f * p.jets
            while (flameAcc >= 1f) {
                flameAcc -= 1f
                val left = rnd.nextBoolean()
                foot(rig, left, a, 0f); foot(rig, left, b, 30f)
                var dx = b[0] - a[0]; var dy = b[1] - a[1]
                val l = hypot(dx, dy).coerceAtLeast(0.01f); dx /= l; dy /= l
                val sp = (220f + rnd.nextFloat() * 160f) * s * (0.5f + 0.5f * p.jets)
                val jitter = (rnd.nextFloat() - 0.5f) * 0.5f
                parts.spawn(Particles.FLAME, a[0] + dx * 10f * s, a[1] + dy * 10f * s,
                    (dx + dy * jitter) * sp - vx * 0.25f, (dy - dx * jitter) * sp - vy * 0.25f,
                    0.22f + rnd.nextFloat() * 0.16f, (5f + rnd.nextFloat() * 4f) * s * (0.6f + 0.4f * p.jets))
            }
        }
        if (phase == PowerPhase.SPUTTER || (phase == PowerPhase.CRASH && k < 0.5f && power.has(com.combo.monkeycompanion.brain.PowerKit.FOOT_JETS))) {
            smokeAcc += dt * 9f
            while (smokeAcc >= 1f) {
                smokeAcc -= 1f
                foot(rig, rnd.nextBoolean(), a, 6f)
                parts.spawn(Particles.SMOKE, a[0], a[1], -vx * 0.2f + (rnd.nextFloat() - 0.5f) * 40f * s, 30f * s, 0.9f, (6f + rnd.nextFloat() * 4f) * s)
            }
        }
        // Speed lines streaming behind.
        val speed = hypot(vx, vy)
        if (power.has(com.combo.monkeycompanion.brain.PowerKit.SPEED_LINES) && speed > 380f * s && (phase == PowerPhase.FLY || (phase == PowerPhase.CRASH && k < 0.16f))) {
            speedAcc += dt * 16f
            while (speedAcc >= 1f) {
                speedAcc -= 1f
                hip(rig, a)
                val nx = -vy / speed; val ny = vx / speed
                val off = (rnd.nextFloat() - 0.5f) * 110f * s
                parts.spawn(Particles.SPEED, a[0] - vx / speed * 30f * s + nx * off, a[1] - vy / speed * 30f * s + ny * off,
                    -vx * 0.12f, -vy * 0.12f, 0.32f, (46f + rnd.nextFloat() * 30f) * s)
            }
        }
        // Palm rings.
        if (power.has(com.combo.monkeycompanion.brain.PowerKit.PALM_RINGS) && phase == PowerPhase.FIRE) {
            ringAcc -= dt
            if (ringAcc <= 0f && k < 0.85f) {
                ringAcc = 0.42f
                leftHand = !leftHand
                sinceRing = 0f
                hand(rig, leftHand, a)
                hip(rig, b)
                // Mostly sideways out of the palm: a little train of three rings.
                val dx = if (a[0] >= b[0]) 1f else -1f
                for (r in 0 until 3) parts.spawn(Particles.RING, a[0] + dx * (10f + r * 22f) * s, a[1] - r * 2f * s,
                    dx * (300f - r * 40f) * s, -10f * s, 0.75f - r * 0.12f, (7f + r * 2.5f) * s)
            }
        }
        // Landing thud / crash slide end: a dust burst at the feet.
        val dustAt = when (phase) { PowerPhase.LAND -> 0.5f; PowerPhase.CRASH -> 0.97f; else -> 2f }
        if (!dusted && k >= dustAt) {
            dusted = true
            foot(rig, true, a, 0f); foot(rig, false, b, 0f)
            val gy = maxOf(a[1], b[1]); val cx = (a[0] + b[0]) / 2f
            for (i in 0 until 12) {
                val dir = if (i % 2 == 0) -1f else 1f
                val sp = (120f + rnd.nextFloat() * 220f) * s
                parts.spawn(Particles.DUST, cx + dir * rnd.nextFloat() * 14f * s, gy - rnd.nextFloat() * 6f * s,
                    dir * sp, -(30f + rnd.nextFloat() * 70f) * s, 0.55f + rnd.nextFloat() * 0.3f, (5f + rnd.nextFloat() * 4f) * s)
            }
        }
        // ---- v4 show-off powers ----
        when (phase) {
            PowerPhase.CHARGE -> {   // sparks flying off the raised hand (staff tip)
                speedAcc += dt * 22f * (0.3f + k)
                while (speedAcc >= 1f) {
                    speedAcc -= 1f; hand(rig, false, a)
                    val ang = rnd.nextFloat() * 6.283f; val sp = (140f + rnd.nextFloat() * 160f) * s
                    parts.spawn(Particles.SPARK, a[0], a[1] - 30f * s, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 0.25f, 6f * s)
                }
            }
            PowerPhase.STRIKE -> if (!dusted && k > 0.05f) { dusted = true; hand(rig, false, a)
                for (i in 0 until 10) { val ang = rnd.nextFloat() * 6.283f; val sp = (200f + rnd.nextFloat() * 200f) * s
                    parts.spawn(Particles.SPARK, a[0], a[1], kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 0.35f, 7f * s) } }
            PowerPhase.TELEPORT -> if (power.id != "vanish" && power.id != "devilhop") {   // v10.16: the devil draws his own red smoke
                // Smoke bombs: one burst as he vanishes, one as he reappears.
                val burst = if (!dusted && k >= 0.3f) 0 else if (dusted && k >= 0.5f && ringAcc > 0f) 1 else -1
                if (burst >= 0) {
                    if (burst == 0) dusted = true else ringAcc = -1f
                    hip(rig, a)
                    for (i in 0 until 9) {
                        val ang = rnd.nextFloat() * 6.283f; val sp = (60f + rnd.nextFloat() * 90f) * s
                        parts.spawn(Particles.SMOKE, a[0] + kotlin.math.cos(ang) * 14f * s, a[1] + kotlin.math.sin(ang) * 20f * s,
                            kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp * 0.6f, 0.7f + rnd.nextFloat() * 0.3f, (10f + rnd.nextFloat() * 6f) * s)
                    }
                }
            }
            PowerPhase.SLAM -> if (!dusted && k >= 0.32f) {
                dusted = true; foot(rig, true, a, 0f); foot(rig, false, b, 0f)
                val cx = (a[0] + b[0]) / 2f; val gy = maxOf(a[1], b[1])
                for (i in 0 until 14) { val dir = if (i % 2 == 0) -1f else 1f
                    parts.spawn(Particles.DUST, cx + dir * rnd.nextFloat() * 20f * s, gy, dir * (160f + rnd.nextFloat() * 260f) * s,
                        -(40f + rnd.nextFloat() * 90f) * s, 0.6f + rnd.nextFloat() * 0.3f, (6f + rnd.nextFloat() * 5f) * s) }
            }
            PowerPhase.FROST -> {
                flameAcc += dt * 10f
                while (flameAcc >= 1f) { flameAcc -= 1f; headTop(rig, a)
                    parts.spawn(Particles.SNOW, a[0] + (rnd.nextFloat() - 0.5f) * 150f * s, a[1] - 40f * s, 0f, (40f + rnd.nextFloat() * 30f) * s, 2.6f, (4f + rnd.nextFloat() * 3f) * s) }
            }
            PowerPhase.CONFETTI -> if (power.id != "skyburst") {   // v10.8: Rocket Girl's sky-burst draws Fireworks instead
                // A pop from alternate shoulders four times: bursts of confetti.
                val pop = (k * 4f).toInt()
                if (pop != lastPop && k > 0.05f) { lastPop = pop; headTop(rig, a)
                    val side = if (pop % 2 == 0) -1f else 1f
                    for (i in 0 until 12) { val sp = (180f + rnd.nextFloat() * 220f) * s
                        val ang = -1.57f + side * (0.2f + rnd.nextFloat() * 0.6f)
                        parts.spawn(Particles.CONFETTI, a[0] + side * 26f * s, a[1] + 40f * s, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 1.6f + rnd.nextFloat() * 0.6f, (6f + rnd.nextFloat() * 4f) * s) } }
            }
            PowerPhase.BREATH -> if (k > 0.32f && k < 0.9f) {
                flameAcc += dt * 14f
                while (flameAcc >= 1f) { flameAcc -= 1f; headTop(rig, a)
                    val f = rig.facingSign.toFloat()
                    parts.spawn(Particles.SNOW, a[0] + f * 20f * s, a[1] + 50f * s, f * (300f + rnd.nextFloat() * 200f) * s, (rnd.nextFloat() - 0.5f) * 80f * s, 1.1f, (4f + rnd.nextFloat() * 3f) * s) }
            }
            PowerPhase.MAGIC -> {
                if (k < 0.45f || (k in 0.45f..0.7f)) {
                    flameAcc += dt * (if (k >= 0.45f) 26f else 8f)
                    while (flameAcc >= 1f) { flameAcc -= 1f; hand(rig, true, a)
                        val ang = rnd.nextFloat() * 6.283f; val sp = (60f + rnd.nextFloat() * 120f) * s
                        parts.spawn(Particles.SPARKLE, a[0], a[1] - 10f * s, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp - 60f * s, 0.6f, 6f * s) }
                }
            }
            else -> Unit
        }
        // Sooty after a rocket crash: wisps from the head.
        if (p.soot > 0.5f && phase == PowerPhase.POSE) {
            smokeAcc += dt * 3f
            while (smokeAcc >= 1f) {
                smokeAcc -= 1f
                headTop(rig, a)
                parts.spawn(Particles.SMOKE, a[0] + (rnd.nextFloat() - 0.5f) * 30f * s, a[1], (rnd.nextFloat() - 0.5f) * 20f * s, -40f * s, 1.0f, 5f * s)
            }
        }
        parts.update(dt)
    }

    
}
