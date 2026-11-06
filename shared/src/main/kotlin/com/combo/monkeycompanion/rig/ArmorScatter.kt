package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.*

/** Deterministic screen-space metal, separate from his squashed inner body. Never draws offscreen. */
class ArmorScatter {
    data class Plate(val id: String, val bone: Int, val cy: Float, val w: Float, val h: Float,
                     val dx: Float, val dy: Float, val spin: Float)
    data class Piece(val id: String, val x: Float, val y: Float, val angle: Float,
                     val w: Float, val h: Float, val docked: Boolean)
    companion object {
        const val OUT = 0.4f
        const val HOLD = 0.6f
        const val HOME = 0.8f
        // 13 independent attachments: chest; paired shoulder, upper arm, forearm, thigh, shin and boot.
        val plates = listOf(
            Plate("chest", 0, -29f, 36f, 34f, 0f, -140f, 170f),
            Plate("shoulderL", 1, 0f, 18f, 16f, -150f, -100f, -220f),
            Plate("shoulderR", 2, 0f, 18f, 16f, 150f, -110f, 240f),
            Plate("upperL", 1, 15f, 14f, 23f, -170f, -30f, -290f),
            Plate("upperR", 2, 15f, 14f, 23f, 175f, -15f, 300f),
            Plate("foreL", 3, 15f, 15f, 25f, -155f, 60f, -350f),
            Plate("foreR", 4, 15f, 15f, 25f, 160f, 75f, 370f),
            Plate("thighL", 5, 13f, 16f, 24f, -115f, 110f, -180f),
            Plate("thighR", 6, 13f, 16f, 24f, 120f, 125f, 210f),
            Plate("shinL", 5, 30f, 15f, 20f, -75f, 155f, -250f),
            Plate("shinR", 6, 30f, 15f, 20f, 85f, 170f, 280f),
            Plate("bootL", 5, 43f, 23f, 13f, -180f, 140f, -400f),
            Plate("bootR", 6, 43f, 23f, 13f, 185f, 145f, 420f))
    }
    var phase = 0; private set
    var elapsed = 0f; private set
    var recovery = 0f; private set
    val active get() = phase != 0
    val amount get() = when (phase) { 1 -> (elapsed / OUT).coerceIn(0f, 1f); 2 -> 1f; 3 -> 1f - elapsed / HOME; else -> 0f }
    private val screen = RectF()
    /** Fixed launch envelope survives all phases. Window origin changes never alter the trajectory. */
    val envelope = RectF()
    private var scale = 1f
    private var from = emptyList<Piece>()
    private var away = emptyList<Piece>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(32, 36, 42); style = Paint.Style.STROKE; strokeWidth = 1.5f }

    private fun bone(rig: MonkeyRig, n: Int): Matrix = when (n) {
        1 -> rig.armLUpper; 2 -> rig.armRUpper; 3 -> rig.armLFore; 4 -> rig.armRFore
        5 -> rig.legL; 6 -> rig.legR; else -> rig.hips
    }
    /** Matrix maps each plate's own center first; rotations are never around the overlay origin. */
    fun attachments(rig: MonkeyRig, wx: Float, wy: Float): List<Piece> = plates.map { p ->
        val xy = floatArrayOf(0f, p.cy, 0f, p.cy + 1f)
        bone(rig, p.bone).mapPoints(xy)
        val angle = Math.toDegrees(atan2((xy[3] - xy[1]).toDouble(), (xy[2] - xy[0]).toDouble())).toFloat() - 90f
        Piece(p.id, xy[0] + wx, xy[1] + wy, angle, p.w * rig.scale, p.h * rig.scale, true)
    }
    fun start(rig: MonkeyRig, wx: Float, wy: Float, usable: RectF): Boolean {
        if (active) return false
        screen.set(usable); scale = rig.scale
        from = attachments(rig, wx, wy)
        envelope.setEmpty()
        away = from.mapIndexed { i, f ->
            val p = plates[i]
            val radius = hypot(f.w, f.h) * 0.5f + 3f
            // Reflect an outward trajectory at the screen boundary: edge hits scatter INWARD.
            fun inward(v: Float, delta: Float, lo: Float, hi: Float): Float {
                val wanted = v + delta
                val reflected = if (wanted < lo || wanted > hi) v - delta else wanted
                return reflected.coerceIn(lo, max(lo, hi))
            }
            val x = inward(f.x, p.dx * scale, screen.left + radius, screen.right - radius)
            val y = inward(f.y, p.dy * scale, screen.top + radius, screen.bottom - radius)
            val end = f.copy(x = x, y = y, angle = f.angle + p.spin, docked = false)
            // Launch point can be near a physical edge; frame() keeps its full rotated extent visible.
            envelope.union(min(f.x, x) - radius, min(f.y, y) - radius,
                max(f.x, x) + radius, max(f.y, y) + radius)
            end
        }
        phase = 1; elapsed = 0f; recovery = 0f
        return true
    }
    /** Returns true exactly once when the last plate docks. Hold starts only AFTER motion ends. */
    fun step(dt: Float, busy: Boolean): Boolean {
        if (!active) return false
        when (phase) {
            1 -> { elapsed += dt; if (elapsed >= OUT) { phase = 2; elapsed = 0f } }
            2 -> {
                if (busy) recovery = 0f else recovery += dt
                if (recovery >= HOLD) { phase = 3; elapsed = 0f }
            }
            3 -> { elapsed += dt; if (elapsed >= HOME) { clear(); return true } }
        }
        return false
    }
    fun clear() { phase = 0; elapsed = 0f; recovery = 0f; envelope.setEmpty() }
    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)
    fun frame(rig: MonkeyRig, wx: Float, wy: Float): List<Piece> {
        if (!active) return emptyList()
        val targets = attachments(rig, wx, wy)
        return plates.indices.map { i ->
            val f: Piece; val t: Piece; val k: Float
            when (phase) {
                1 -> { f = from[i]; t = away[i]; val u = (elapsed / OUT).coerceIn(0f, 1f); k = 1f - (1f - u).pow(2) }
                2 -> { f = away[i]; t = f; k = 0f }
                else -> {
                    f = away[i]; t = targets[i]
                    // Each plate has a 0.55 s flight; 0.25 s stagger across the suit, total 0.8 s.
                    val delay = i * 0.25f / (plates.size - 1)
                    k = smooth(((elapsed - delay) / 0.55f).coerceIn(0f, 1f))
                }
            }
            val p = t.copy(x = f.x + (t.x - f.x) * k, y = f.y + (t.y - f.y) * k,
                angle = f.angle + (t.angle - f.angle) * k,
                w = f.w + (t.w - f.w) * k, h = f.h + (t.h - f.h) * k,
                docked = phase == 3 && k >= 1f)
            val r = hypot(p.w, p.h) * 0.5f + 2f
            p.copy(x = p.x.coerceIn(screen.left + r, max(screen.left + r, screen.right - r)),
                y = p.y.coerceIn(screen.top + r, max(screen.top + r, screen.bottom - r)))
        }
    }
    /** Screen-space pass: the body's squash, bank, rotation and window padding cannot distort metal. */
    fun draw(c: Canvas, rig: MonkeyRig, wx: Float, wy: Float) {
        for ((i, p) in frame(rig, wx, wy).withIndex()) {
            c.save(); c.translate(p.x - wx, p.y - wy); c.rotate(p.angle)
            paint.color = ArmourRenderer.GUN; c.drawRoundRect(-p.w/2, -p.h/2, p.w/2, p.h/2, 3f * scale, 3f * scale, paint)
            c.drawRoundRect(-p.w/2, -p.h/2, p.w/2, p.h/2, 3f * scale, 3f * scale, rim)
            paint.color = ArmourRenderer.SAFFRON
            c.drawRect(-p.w * 0.35f, -p.h * 0.1f, p.w * 0.35f, p.h * 0.1f, paint)
            if (i == 0) { paint.color = ArmourRenderer.CORE; c.drawCircle(0f, 0f, p.w * 0.18f, paint) }
            c.restore()
        }
    }
}
