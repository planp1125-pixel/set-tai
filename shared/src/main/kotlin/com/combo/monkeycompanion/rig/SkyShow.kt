package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * v10.9 Rocket Girl's sky-firework power, as a little show (screen px, drawn in the full-screen FX layer):
 * a green glass bottle appears beside her with a Diwali rocket standing in it (stick down the neck, tilted);
 * she holds her sparkler to the fuse ([reach] = anticipation), the fuse sparks, and [SHELLS] rockets launch FROM THE
 * BOTTLE in quick succession on wobbly rocket paths with a whistle and a glitter trail, bursting at different heights
 * and places, each a different layered multi-ring recipe. Rarely a grand finale adds 3 more. Own seeded [Random].
 */
class SkyShow(seed: Long, private val fw: Fireworks) {
    enum class Phase { IDLE, APPEAR, LIGHT, LAUNCH, FADE, DONE }

    private val rnd = Random(seed)
    var phase = Phase.IDLE; private set
    var t = 0f; private set
    /** Shells launched so far / planned in this show. */
    var launched = 0; private set
    var planned = 0; private set
    /** The specs of this show's shells (tests: 5 different recipes, multi-ring). */
    val specs = ArrayList<FwSpec>()
    private var bx = 0f; private var by = 0f; private var s = 1f; private var facing = 1f
    private var skyTop = 0f; private var skyBottom = 0f; private var w = 0f
    private val tx = FloatArray(MAX); private val ty = FloatArray(MAX)
    var finale = false; private set

    val active get() = phase != Phase.IDLE && phase != Phase.DONE
    /** 0..1: her hand reaching the sparkler to the fuse (the overlay feeds this to Pose.rgReach). */
    val reach: Float get() = when (phase) {
        Phase.APPEAR -> (t / APPEAR_T).coerceIn(0f, 1f) * 0.6f
        Phase.LIGHT -> 0.6f + 0.4f * ((t - APPEAR_T) / 0.25f).coerceIn(0f, 1f)
        Phase.LAUNCH -> (1f - (t - LIGHT_END) / 0.35f).coerceIn(0f, 1f)
        else -> 0f
    }
    /** Looking up at the sky (after the first launch). */
    val lookUp: Float get() = if (phase == Phase.LAUNCH || phase == Phase.FADE) 1f else 0f

    /**
     * Start: the bottle stands with its base at (baseX, baseY), [scale] px per rig unit, the sky between [skyTop] and
     * [skyBottom], screen width [width]. [facing] = which side of her it stands on (+1 = right).
     */
    fun start(baseX: Float, baseY: Float, scale: Float, facing: Float, skyTop: Float, skyBottom: Float, width: Float, finaleChance: Float = 0.08f) {
        bx = baseX; by = baseY; s = scale; this.facing = if (facing < 0f) -1f else 1f
        this.skyTop = skyTop; this.skyBottom = skyBottom; w = width
        t = 0f; launched = 0; specs.clear()
        finale = rnd.nextFloat() < finaleChance
        planned = SHELLS + if (finale) 3 else 0
        // different places and heights: spread across the sky, shuffled
        val xs = (0 until planned).map { 0.12f + 0.76f * (it + 0.5f) / planned }.shuffled(rnd)
        for (i in 0 until planned) {
            tx[i] = w * (xs[i] + (rnd.nextFloat() - 0.5f) * 0.08f)
            ty[i] = skyTop + (skyBottom - skyTop) * (0.06f + 0.38f * ((i * 0.61803f + rnd.nextFloat() * 0.2f) % 1f))
        }
        phase = Phase.APPEAR
    }

    /** Stop launching (a newer show took over); what's already in the sky keeps going. */
    fun cancel() { if (active) { phase = Phase.FADE; planned = launched } }

    /** Where the loaded rocket's tube sits / where shells leave from (the bottle mouth, up the stick). */
    private fun rocketAngle() = -90f + 14f * facing
    private fun mouthX() = bx; private fun mouthY() = by - BOTTLE_H * s
    private fun tubePoint(along: Float, out: FloatArray) {
        val a = Math.toRadians(rocketAngle().toDouble())
        val r = ROCKET_K * s
        out[0] = mouthX() + (cos(a) * along * r).toFloat(); out[1] = mouthY() + (sin(a) * along * r).toFloat()
    }
    private val pt = FloatArray(2)

    /** Fuse point in screen px (the overlay aims her sparkler here). */
    fun fusePoint(out: FloatArray) = tubePoint(DiwaliRocketArt.TUBE_B - 3f + STICK_IN, out)

    fun step(dt: Float) {
        if (!active || dt <= 0f) return
        t += dt
        when (phase) {
            Phase.APPEAR -> if (t >= APPEAR_T) phase = Phase.LIGHT
            Phase.LIGHT -> if (t >= LIGHT_END) phase = Phase.LAUNCH
            Phase.LAUNCH -> {
                val due = (((t - LIGHT_END) / GAP).toInt() + 1).coerceAtMost(planned)
                while (launched < due) fire()
                if (launched >= planned && t > LIGHT_END + (planned - 1) * GAP + 0.6f) phase = Phase.FADE
            }
            Phase.FADE -> if (!fw.active || t > LIGHT_END + planned * GAP + 6f) phase = Phase.DONE
            else -> Unit
        }
    }

    private fun fire() {
        val i = launched++
        tubePoint(DiwaliRocketArt.NOSE + STICK_IN, pt)
        val sp = fw.nextSpec(tx[i], ty[i], s * 0.9f * (0.85f + 0.3f * rnd.nextFloat()), big = finale && i >= SHELLS, avoid = specs.map { it.recipe })
        specs += sp
        fw.launchShell(sp, pt[0], pt[1], s)
    }

    // ---------------- drawing: the green glass bottle and the rocket standing in it ----------------
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()

    private fun bottleAlpha(): Float = when (phase) {
        Phase.APPEAR -> (t / APPEAR_T).coerceIn(0f, 1f)
        Phase.FADE -> (1f - (t - (LIGHT_END + (planned - 1) * GAP + 0.6f)) / 0.8f).coerceIn(0f, 1f)
        Phase.LIGHT, Phase.LAUNCH -> 1f
        else -> 0f
    }

    fun draw(c: Canvas) {
        if (!active) return
        val a = bottleAlpha(); if (a <= 0.01f) return
        val ai = (255 * a).toInt()
        val loaded = phase != Phase.FADE && (phase != Phase.LAUNCH || ((t - LIGHT_END) % GAP) > 0.12f)
        // the rocket standing in the bottle (stick down the neck), drawn first so the glass is over the stick
        if (loaded) {
            c.save(); c.translate(mouthX(), mouthY()); c.rotate(rocketAngle()); c.scale(ROCKET_K * s, ROCKET_K * s); c.translate(STICK_IN, 0f)
            val lit = phase == Phase.LIGHT || phase == Phase.LAUNCH
            val ra = Math.toRadians(rocketAngle().toDouble())
            DiwaliRocketArt.draw(c, t, if (lit) 0.25f else 0f, stickBack = -BOTTLE_H * 0.9f / ROCKET_K - STICK_IN, ow = 1.4f, fadeAlpha = ai,
                gx = sin(ra).toFloat(), gy = cos(ra).toFloat())
            c.restore()
        }
        drawBottle(c, ai)
        // v10.10 lift-off: a short fountain of tiny sparks out of the bottle mouth that fall back and fade (no blast)
        if (phase == Phase.LAUNCH) {
            val since = (t - LIGHT_END) % GAP
            if (since < LIFT_SPARK_T) {
                val k = 1f - since / LIFT_SPARK_T
                val cyc = ((t - LIGHT_END) / GAP).toInt()
                val mx = mouthX(); val my = mouthY()
                stroke.strokeWidth = 0.8f * s
                for (i in 0 until 26) {
                    val r1 = h(i * 97 + cyc * 31 + 3); val r2 = h(i * 59 + cyc * 13 + 11)
                    val ang = Math.toRadians((-90.0 + (r1 - 0.5) * 50.0)).toFloat()
                    val v0 = (40f + 50f * r2) * s
                    val tau = since * (0.7f + 0.3f * r2)
                    val px = mx + cos(ang) * v0 * tau; val py = my + sin(ang) * v0 * tau + 0.5f * 260f * s * tau * tau
                    val vy = sin(ang) * v0 + 260f * s * tau; val vx = cos(ang) * v0
                    val l = kotlin.math.max(1f, kotlin.math.hypot(vx, vy)); val len = (1.5f + 2.5f * k) * s
                    stroke.color = if (i % 3 == 0) Color.WHITE else if (i % 3 == 1) DiwaliRocketArt.GOLD_LIGHT else DiwaliRocketArt.FLAME
                    stroke.alpha = (ai * k).toInt()
                    c.drawLine(px, py, px - vx / l * len, py - vy / l * len, stroke)
                }
                fill.color = Color.rgb(0xB0, 0xB0, 0xB8)
                for (i in 0 until 4) { fill.alpha = (ai * 0.25f * k).toInt(); c.drawCircle(mx + (h(i + cyc * 5) - 0.5f) * 8f * s, my - (3f + 10f * (1f - k) * (1f + i * 0.3f)) * s, (2f + 3f * (1f - k)) * s, fill) }
            }
        }
        // fuse sparks while it's being lit
        if (phase == Phase.LIGHT || (phase == Phase.LAUNCH && (t - LIGHT_END) % GAP < 0.15f)) {
            fusePoint(pt)
            val frame = (t * 40f).toInt()
            for (i in 0 until 12) {
                val ang = h(frame * 7 + i) * 6.283f; val r = (2f + h(frame + i * 31) * 9f) * s
                stroke.color = if (i % 3 == 0) Color.WHITE else if (i % 3 == 1) DiwaliRocketArt.GOLD_LIGHT else DiwaliRocketArt.FLAME_HOT
                stroke.strokeWidth = 0.9f * s; stroke.alpha = ai
                c.drawLine(pt[0], pt[1], pt[0] + cos(ang) * r, pt[1] + sin(ang) * r, stroke)
            }
            fill.color = Color.WHITE; fill.alpha = ai; c.drawCircle(pt[0], pt[1], 1.8f * s, fill)
        }
        fill.alpha = 255; stroke.alpha = 255
    }

    private fun drawBottle(c: Canvas, ai: Int) {
        val k = s
        val H = BOTTLE_H * k; val W = 12f * k; val neck = 4.2f * k
        val x = bx; val base = by
        path.reset()
        path.moveTo(x - neck, base - H)
        path.lineTo(x - neck, base - H * 0.72f)
        path.cubicTo(x - neck, base - H * 0.55f, x - W, base - H * 0.5f, x - W, base - H * 0.32f)
        path.lineTo(x - W, base - 2f * k); path.quadTo(x - W, base, x - W + 2f * k, base)
        path.lineTo(x + W - 2f * k, base); path.quadTo(x + W, base, x + W, base - 2f * k)
        path.lineTo(x + W, base - H * 0.32f)
        path.cubicTo(x + W, base - H * 0.5f, x + neck, base - H * 0.55f, x + neck, base - H * 0.72f)
        path.lineTo(x + neck, base - H); path.close()
        fill.shader = LinearGradient(x - W, 0f, x + W, 0f, intArrayOf(Color.argb(150, 0x1A, 0x5A, 0x22), Color.argb(110, 0x4A, 0xA0, 0x48), Color.argb(170, 0x10, 0x40, 0x18)),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        fill.alpha = (ai * 0.95f).toInt(); c.drawPath(path, fill); fill.shader = null
        stroke.color = Color.rgb(0x0C, 0x30, 0x12); stroke.strokeWidth = 1.2f * k; stroke.alpha = ai; c.drawPath(path, stroke)
        // lip ring + highlights
        fill.color = Color.argb(200, 0x6A, 0xB0, 0x60); fill.alpha = ai
        c.drawRect(x - neck - 0.8f * k, base - H - 1.2f * k, x + neck + 0.8f * k, base - H + 1.4f * k, fill)
        stroke.color = Color.WHITE; stroke.alpha = (ai * 0.65f).toInt(); stroke.strokeWidth = 1.6f * k
        c.drawLine(x - W + 3f * k, base - H * 0.3f, x - W + 3f * k, base - 5f * k, stroke)
        stroke.alpha = (ai * 0.45f).toInt(); stroke.strokeWidth = 1f * k
        c.drawLine(x - neck + 1.2f * k, base - H * 0.95f, x - neck + 1.2f * k, base - H * 0.75f, stroke)
        stroke.alpha = 255
    }

    private fun h(i: Int): Float { var x = i * 374761393 + 668265263; x = (x xor (x ushr 13)) * 1274126177; return ((x xor (x ushr 16)) and 0xFFFF) / 65535f }

    companion object {
        /** v10.10: how long the lift-off spark fountain plays (s). */
        const val LIFT_SPARK_T = 0.45f
        /** One power = at least 5 shells. */
        const val SHELLS = 5
        const val MAX = 8
        const val APPEAR_T = 0.3f
        const val LIGHT_END = 0.95f
        /** Seconds between launches. */
        const val GAP = 0.42f
        /** Bottle height in rig units. */
        const val BOTTLE_H = 46f
        /** The rocket's size relative to the rig (rocket-frame units -> rig units). */
        const val ROCKET_K = 0.6f
        /** How far up the stick the bottle mouth holds it (rocket-frame units from the stick's... seat). */
        const val STICK_IN = -8f
        /** Total seconds the power should last for a full show. */
        const val SHOW_SECS = LIGHT_END + (SHELLS - 1) * GAP + 1.4f
    }
}
