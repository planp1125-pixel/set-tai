package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.sin

/**
 * v10.9 a classic Indian Diwali sky rocket, drawn procedurally in its OWN frame (no photo parts):
 * x runs along the rocket (forward = +x, toward the nose), y is across it. A slim paper tube (about 4.3x as long as
 * it is wide: gold with green and red bands, little stars and a round emblem), a red foil nose cone, a little white
 * fuse at the bottom, and a long thin twisted white stick along one side (about 2.7x the tube's length below it).
 * The exhaust (v10.10: a narrow spark fountain + a thin smoke wisp) comes out of the BOTTOM of the tube (the fuse end).
 * Shared by Rocket Girl's ride, the bottle launcher and the Smash rockets. Every flicker is a hash of time (no RNG).
 */
object DiwaliRocketArt {
    /** Rocket-frame geometry (rig units for Rocket Girl's ride). */
    const val STICK_BACK = -62f
    const val TUBE_B = 30f
    const val TUBE_F = 64f
    const val NOSE = 76f
    const val R = 4f
    /** The tube sits on the "upper" side of the stick. */
    const val TY = -(R + 1.3f)

    val GOLD = Color.rgb(0xF2, 0xC2, 0x2E)
    val GOLD_LIGHT = Color.rgb(0xFF, 0xE8, 0x8A)
    val GOLD_DARK = Color.rgb(0xB8, 0x86, 0x10)
    val GREEN = Color.rgb(0x1E, 0x7A, 0x2E)
    val RED = Color.rgb(0xD8, 0x1E, 0x2A)
    val RED_LIGHT = Color.rgb(0xFF, 0x6A, 0x60)
    val RED_DARK = Color.rgb(0x8E, 0x10, 0x18)
    val STICK = Color.rgb(0xE8, 0xEA, 0xF0)
    val STICK_TWIST = Color.rgb(0x8A, 0x92, 0xB0)
    val FLAME = Color.rgb(0xFF, 0x8A, 0x1A)
    val FLAME_HOT = Color.rgb(0xFF, 0xF2, 0x9A)
    val INK = Color.rgb(0x3A, 0x22, 0x10)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val rect = RectF()
    private var tubeShader: LinearGradient? = null

    private fun h(i: Int): Float { var x = i * 374761393 + 668265263; x = (x xor (x ushr 13)) * 1274126177; return ((x xor (x ushr 16)) and 0xFFFF) / 65535f }

    /**
     * Draw in the rocket frame. [flame] 0 = none, ~0.35 = idle sparkle exhaust, 1+ = swiped (a denser, longer spark fountain; v10.10: never a blast cone).
     * [sputter] 0..1 coughs the flame, [roll] scrolls the decoration (the rocket rolling), [stickBack] = where the stick
     * ends (shorter for a stuck-in-bottle launch view), [ow] = outline width.
     */
    fun draw(c: Canvas, t: Float, flame: Float, sputter: Float = 0f, roll: Float = 0f, stickBack: Float = STICK_BACK, ow: Float = 1.6f,
             withStick: Boolean = true, fadeAlpha: Int = 255, gx: Float = -0.64f, gy: Float = 0.77f) {
        if (flame > 0.01f) drawFlame(c, t, flame, sputter, fadeAlpha, gx, gy)
        // the long thin twisted stick
        if (withStick) {
            stroke.shader = null; stroke.color = INK; stroke.alpha = fadeAlpha; stroke.strokeWidth = 2.4f + ow
            c.drawLine(stickBack, 0f, TUBE_F - 4f, 0f, stroke)
            stroke.color = STICK; stroke.alpha = fadeAlpha; stroke.strokeWidth = 2.4f
            c.drawLine(stickBack, 0f, TUBE_F - 4f, 0f, stroke)
            stroke.color = STICK_TWIST; stroke.alpha = fadeAlpha; stroke.strokeWidth = 0.9f
            var x = stickBack + 1.5f
            while (x < TUBE_F - 5f) { c.drawLine(x, 1.1f, x + 1.8f, -1.1f, stroke); x += 3.2f }
            // two little paper ties holding the tube on
            fill.shader = null; fill.color = Color.rgb(0xF4, 0xEC, 0xD8); fill.alpha = fadeAlpha
            for (tx in floatArrayOf(TUBE_B + 5f, TUBE_F - 7f)) { rect.set(tx - 1.2f, TY, tx + 1.2f, 1.4f); c.drawRect(rect, fill) }
        }
        // fuse: a little white twist out of the bottom
        stroke.shader = null; stroke.color = Color.rgb(0xF2, 0xF2, 0xF2); stroke.alpha = fadeAlpha; stroke.strokeWidth = 1.1f
        path.reset(); path.moveTo(TUBE_B + 0.5f, TY + 1f); path.quadTo(TUBE_B - 3f, TY + 2.5f, TUBE_B - 4.5f, TY + 0.5f); c.drawPath(path, stroke)
        // the tube: gold with a soft cylindrical highlight
        if (tubeShader == null) tubeShader = LinearGradient(0f, TY - R, 0f, TY + R, intArrayOf(GOLD_LIGHT, GOLD, GOLD_DARK), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        rect.set(TUBE_B, TY - R, TUBE_F, TY + R)
        fill.shader = tubeShader; fill.alpha = fadeAlpha; c.drawRect(rect, fill); fill.shader = null
        c.save(); c.clipRect(rect)
        val L = TUBE_F - TUBE_B
        val sc = ((roll % 360f) + 360f) % 360f / 360f * 6f
        // bands from the bottom: red stripes, green star band, gold, the round emblem, green star band, red stripes
        band(c, TUBE_B + 0.8f, 1.2f, RED, fadeAlpha); band(c, TUBE_B + 2.8f, 0.7f, RED, fadeAlpha)
        band(c, TUBE_B + L * 0.14f, L * 0.16f, GREEN, fadeAlpha)
        band(c, TUBE_B + L * 0.68f, L * 0.16f, GREEN, fadeAlpha)
        band(c, TUBE_F - 2.4f, 1.0f, RED, fadeAlpha); band(c, TUBE_F - 4.3f, 0.6f, RED, fadeAlpha)
        // little gold stars on the green bands, red stars on the gold
        for (b in floatArrayOf(0.22f, 0.76f)) {
            var y = TY - R + 1.2f + sc % 3f - 3f
            while (y < TY + R) { star(c, TUBE_B + L * b, y, 1.15f, GOLD_LIGHT, fadeAlpha); y += 3f }
        }
        for (b in floatArrayOf(0.36f, 0.6f)) {
            var y = TY - R + 2.2f + (sc * 1.3f) % 4f - 4f
            while (y < TY + R) { star(c, TUBE_B + L * b, y, 0.9f, RED, fadeAlpha); y += 4f }
        }
        // the round emblem in the middle
        val ex = TUBE_B + L * 0.48f
        fill.color = GREEN; fill.alpha = fadeAlpha; c.drawCircle(ex, TY, R * 0.82f, fill)
        fill.color = GOLD_LIGHT; fill.alpha = fadeAlpha; c.drawCircle(ex, TY, R * 0.58f, fill)
        fill.color = GREEN; fill.alpha = fadeAlpha; c.drawCircle(ex, TY, R * 0.28f, fill)
        // a shine streak along the top
        fill.color = Color.WHITE; fill.alpha = (fadeAlpha * 0.35f).toInt(); rect.set(TUBE_B, TY - R + 0.7f, TUBE_F, TY - R + 1.5f); c.drawRect(rect, fill)
        c.restore()
        stroke.color = INK; stroke.alpha = fadeAlpha; stroke.strokeWidth = ow * 0.8f
        rect.set(TUBE_B, TY - R, TUBE_F, TY + R); c.drawRect(rect, stroke)
        // red foil nose cone
        path.reset(); path.moveTo(TUBE_F, TY - R - 0.6f); path.lineTo(NOSE, TY); path.lineTo(TUBE_F, TY + R + 0.6f); path.close()
        fill.color = RED; fill.alpha = fadeAlpha; c.drawPath(path, fill)
        c.save(); c.clipPath(path)
        fill.color = RED_DARK; fill.alpha = (fadeAlpha * 0.55f).toInt(); rect.set(TUBE_F, TY + 0.8f, NOSE, TY + R + 1f); c.drawRect(rect, fill)
        stroke.color = RED_LIGHT; stroke.strokeWidth = 0.6f
        for (i in 0 until 6) {   // crinkled foil glints
            val fx = TUBE_F + 1.5f + i * 1.8f; val fy = TY - R + 1f + h(i * 7) * (R * 1.2f)
            stroke.alpha = (fadeAlpha * (0.4f + 0.5f * h(i * 13))).toInt(); c.drawLine(fx, fy, fx + 1.6f, fy + 0.9f, stroke)
        }
        c.restore()
        stroke.color = INK; stroke.alpha = fadeAlpha; stroke.strokeWidth = ow * 0.8f; c.drawPath(path, stroke)
        fill.alpha = 255; stroke.alpha = 255
    }

    private fun band(c: Canvas, x: Float, w: Float, col: Int, a: Int) {
        fill.shader = null; fill.color = col; fill.alpha = a; rect.set(x, TY - R, x + w, TY + R); c.drawRect(rect, fill)
    }

    private fun star(c: Canvas, x: Float, y: Float, r: Float, col: Int, a: Int) {
        path.reset()
        for (i in 0 until 10) {
            val ang = -Math.PI / 2 + i * Math.PI / 5
            val rr = if (i % 2 == 0) r else r * 0.45f
            val px = x + (kotlin.math.cos(ang) * rr).toFloat(); val py = y + (kotlin.math.sin(ang) * rr).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close(); fill.color = col; fill.alpha = a; c.drawPath(path, fill)
    }

    /**
     * v10.10 the exhaust of a real Diwali sky rocket (no blast cone): a narrow fountain of tiny gold / orange / white
     * sparks spraying out of the tube bottom, each one falling with gravity and fading, a thin grey smoke wisp and a
     * small flickering glow at the nozzle. [k] = intensity (0.25 lit / ~0.35 idle ride / 1+ swiped): more, faster and
     * longer-lived sparks, still sparkles. [gx],[gy] = "down" in the rocket frame (so the sparks fall the right way).
     * Every spark is a hash of its index and respawn cycle (no RNG).
     */
    private fun drawFlame(c: Canvas, t: Float, k: Float, sputter: Float, a: Int, gx: Float = -0.64f, gy: Float = 0.77f) {
        val sp = sputter.coerceIn(0f, 1f)
        val cough = if (sp > 0.05f && sin(t * 23f) > 0f) 1f - 0.7f * sp else 1f
        val kk = k.coerceIn(0f, 1.5f)
        val x0 = TUBE_B - 0.5f; val y0 = TY
        fill.shader = null; stroke.shader = null
        // thin smoke wisp first (behind the sparks): small grey puffs drifting back and up
        val nSmoke = 3 + (4 * kk).toInt()
        for (i in 0 until nSmoke) {
            val u = ((t * (0.9f + 0.5f * kk) + i / nSmoke.toFloat()) % 1f)
            val dist = 4f + u * (14f + 26f * kk)
            val sx = x0 - dist - gx * 6f * u; val sy = y0 + (h(i * 5 + 3) - 0.5f) * 2f - gy * 6f * u + sin(t * 3f + i) * 1.2f * u
            fill.color = Color.rgb(0xA8, 0xA8, 0xB0); fill.alpha = (a * (0.22f + 0.25f * sp) * (1f - u) * cough.coerceAtLeast(0.6f)).toInt()
            c.drawCircle(sx, sy, 1.1f + 2.6f * u, fill)
        }
        // the spark fountain
        val n = ((14 + 40 * kk) * cough).toInt()
        val life = 0.36f + 0.32f * kk
        val g = 110f + 40f * kk
        for (i in 0 until n) {
            val ph = t / life + h(i * 131 + 7)
            val cyc = ph.toInt(); val u = ph - cyc
            val r1 = h(i * 977 + cyc * 61 + 1); val r2 = h(i * 389 + cyc * 17 + 5); val r3 = h(i * 53 + cyc * 7 + 9)
            val spread = (r1 - 0.5f) * (0.34f + 0.16f * kk)          // narrow: about +-10..14 degrees
            val v0 = (36f + 64f * kk) * (0.55f + 0.45f * r2)
            val vx = -v0 * kotlin.math.cos(spread); val vy = v0 * kotlin.math.sin(spread)
            val tau = u * life * (0.7f + 0.3f * r3)
            val px = x0 + vx * tau + 0.5f * g * gx * tau * tau
            val py = y0 + vy * tau + 0.5f * g * gy * tau * tau
            val cvx = vx + g * gx * tau; val cvy = vy + g * gy * tau
            val cl = kotlin.math.max(1f, kotlin.math.sqrt(cvx * cvx + cvy * cvy))
            val len = 1.4f + (2f + 2.4f * kk) * (1f - u)
            val fade = (1f - u * u)
            stroke.strokeWidth = 0.95f - 0.35f * u
            stroke.color = when { u < 0.2f -> Color.WHITE; r3 < 0.6f -> GOLD_LIGHT; r3 < 0.85f -> FLAME_HOT; else -> FLAME }
            stroke.alpha = (a * fade).toInt().coerceIn(0, 255)
            c.drawLine(px, py, px - cvx / cl * len, py - cvy / cl * len, stroke)
            if (r2 > 0.78f && u < 0.7f) {   // a few twinkle points
                fill.color = Color.WHITE; fill.alpha = (a * fade).toInt(); c.drawCircle(px, py, 0.6f, fill)
            }
        }
        // small flickering glow at the nozzle
        val fl = (0.75f + 0.25f * sin(t * 47f) * sin(t * 31f)) * cough
        fill.color = FLAME; fill.alpha = (a * 0.28f * fl).toInt(); c.drawCircle(x0 - 0.8f, y0, (2.6f + 1.2f * kk) * fl, fill)
        fill.color = FLAME_HOT; fill.alpha = (a * 0.8f * fl).toInt(); c.drawCircle(x0 - 0.5f, y0, (1.3f + 0.5f * kk) * fl, fill)
        fill.color = Color.WHITE; fill.alpha = (a * 0.9f * fl).toInt(); c.drawCircle(x0 - 0.3f, y0, 0.7f, fill)
        fill.alpha = 255; stroke.alpha = 255
    }
}
