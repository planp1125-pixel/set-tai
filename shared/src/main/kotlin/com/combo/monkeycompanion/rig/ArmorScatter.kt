package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.*

/** Deterministic screen-space loose metal: scatter, gravity, small bounces, ground rest, magnetic return. */
class ArmorScatter {
    data class Plate(val id: String, val bone: Int, val cy: Float, val w: Float, val h: Float,
                     val dx: Float, val dy: Float, val spin: Float)
    data class Piece(val id: String, val x: Float, val y: Float, val angle: Float,
                     val w: Float, val h: Float, val docked: Boolean, val settled: Boolean = false)
    companion object {
        private const val LAUNCH_TIME = 0.4f // convert the old rig-unit scatter strengths to initial px/s, not a hover timer
        const val HOLD = 0.18f // brief ground rest only AFTER he and every piece have landed
        const val HOME = 0.8f
        // 14 independent attachments: chest; paired shoulder, upper arm, forearm, thigh, shin, boot; helmet.
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
            Plate("bootR", 6, 43f, 23f, 13f, 185f, 145f, 420f),
            Plate("helmet", 7, MonkeyRig.HEAD_CY, 80f, 76f, -95f, -150f, -210f))
    }
    var phase = 0; private set // 0 suited, 1 ballistic fall, 2 grounded, 3 magnetic docking
    var elapsed = 0f; private set
    var recovery = 0f; private set
    val active get() = phase != 0
    val amount get() = when (phase) { 1 -> (elapsed / LAUNCH_TIME).coerceIn(0f, 1f); 2 -> 1f; 3 -> 1f - elapsed / HOME; else -> 0f }
    private val screen = RectF()
    /** Fixed launch envelope survives all phases. Window origin changes never alter the trajectory. */
    val envelope = RectF()
    private var scale = 1f
    private data class Loose(var piece: Piece, var vx: Float, var vy: Float, var spin: Float, var hits: Int = 0)
    private var loose = emptyList<Loose>()
    var floorY = 0f; private set
    val allSettled get() = active && loose.isNotEmpty() && loose.all { it.piece.settled }
    private val gravity get() = 1800f * scale
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(32, 36, 42); style = Paint.Style.STROKE; strokeWidth = 1.5f }

    private fun bone(rig: MonkeyRig, n: Int): Matrix = when (n) {
        1 -> rig.armLUpper; 2 -> rig.armRUpper; 3 -> rig.armLFore; 4 -> rig.armRFore
        5 -> rig.legL; 6 -> rig.legR; 7 -> rig.head; else -> rig.hips
    }
    /** Matrix maps each plate's own center first; rotations are never around the overlay origin. */
    fun attachments(rig: MonkeyRig, wx: Float, wy: Float): List<Piece> = plates.map { p ->
        val xy = floatArrayOf(0f, p.cy, 0f, p.cy + 1f)
        bone(rig, p.bone).mapPoints(xy)
        val angle = Math.toDegrees(atan2((xy[3] - xy[1]).toDouble(), (xy[2] - xy[0]).toDouble())).toFloat() - 90f
        Piece(p.id, xy[0] + wx, xy[1] + wy, angle, p.w * rig.scale, p.h * rig.scale, true)
    }
    fun start(rig: MonkeyRig, wx: Float, wy: Float, usable: RectF, floor: Float = usable.bottom): Boolean {
        if (active) return false
        screen.set(usable); scale = rig.scale
        floorY = floor.coerceIn(usable.top + 80f * scale, usable.bottom)
        val from = attachments(rig, wx, wy)
        envelope.setEmpty()
        loose = from.mapIndexed { i, f ->
            val p = plates[i]
            val radius = hypot(f.w, f.h) * 0.5f + 3f
            val x = f.x.coerceIn(screen.left + radius, screen.right - radius)
            val y = f.y.coerceIn(screen.top + radius, floorY - radius)
            var vx = p.dx * scale / LAUNCH_TIME
            var vy = p.dy * scale / LAUNCH_TIME
            // Forceful inward launch at physical edges; no phone-tilt applied to this metal.
            if (x + vx * LAUNCH_TIME < screen.left + radius || x + vx * LAUNCH_TIME > screen.right - radius) vx = -vx
            if (y + vy * LAUNCH_TIME < screen.top + radius) vy = abs(vy) * 0.4f
            // Stable conservative ballistic envelope, including ALL the way down to the visual floor.
            val distance = max(0f, floorY - radius - y)
            val impactSpeed = sqrt(vy * vy + 2f * gravity * distance)
            val airTime = (-vy + impactSpeed) / gravity + 0.7f * impactSpeed / gravity + 0.2f
            val travel = abs(vx) * airTime
            val apex = y - min(0f, vy).pow(2) / (2f * gravity)
            envelope.union(max(screen.left, x - travel - radius), max(screen.top, apex - radius),
                min(screen.right, x + travel + radius), floorY + 2f)
            Loose(f.copy(x=x, y=y, docked=false), vx, vy, p.spin / 0.65f)
        }
        phase = 1; elapsed = 0f; recovery = 0f
        return true
    }
    private fun fall(l: Loose, dt: Float) {
        var p = l.piece
        if (p.settled) return
        val r = hypot(p.w, p.h) * 0.5f + 2f
        l.vy += gravity * dt
        var x = p.x + l.vx * dt; var y = p.y + l.vy * dt
        var angle = p.angle + l.spin * dt
        if (x < screen.left+r) { x=screen.left+r; l.vx=abs(l.vx)*0.28f }
        if (x > screen.right-r) { x=screen.right-r; l.vx=-abs(l.vx)*0.28f }
        if (y < screen.top+r) { y=screen.top+r; l.vy=abs(l.vy)*0.25f }
        var settled = false
        if (y >= floorY-r && l.vy >= 0f) {
            y = floorY-r; l.hits++
            if (l.hits >= 2 || l.vy < 120f*scale) {
                settled=true; l.vx=0f; l.vy=0f; l.spin=0f
                angle = (angle / 180f).roundToInt() * 180f
                // Lie flat with the actual plate bottom on the floor, not balanced on its bounding circle.
                y = floorY - p.h/2f - 1f
            } else { l.vy = -l.vy*0.3f; l.vx *= 0.5f; l.spin *= 0.45f }
        }
        l.piece = p.copy(x=x, y=y, angle=angle, settled=settled)
    }
    /** busy = he has NOT yet landed/settled. Every piece keeps falling regardless of his state. */
    fun step(dt: Float, busy: Boolean): Boolean {
        if (!active) return false
        when (phase) {
            1 -> {
                elapsed += dt
                var left = dt.coerceAtMost(0.25f)
                while (left > 0f) { val sub = min(left, 1f/120f); loose.forEach { fall(it, sub) }; left -= sub }
                if (allSettled) { phase=2; elapsed=0f; recovery=0f }
            }
            2 -> {
                if (busy) recovery=0f else recovery+=dt
                if (!busy && recovery >= HOLD) { phase=3; elapsed=0f }
            }
            3 -> { elapsed += dt; if (elapsed >= HOME) { clear(); return true } }
        }
        return false
    }
    fun clear() { phase = 0; elapsed = 0f; recovery = 0f; envelope.setEmpty() }
    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)
    fun frame(rig: MonkeyRig, wx: Float, wy: Float): List<Piece> {
        if (!active) return emptyList()
        if (phase != 3) return loose.map { it.piece } // world positions: never tethered to his bouncing body
        val targets = attachments(rig, wx, wy)
        return plates.indices.map { i ->
            val f = loose[i].piece; val t = targets[i]
            val delay = i * 0.25f / (plates.size - 1)
            val k = smooth(((elapsed - delay) / 0.55f).coerceIn(0f, 1f))
            val p = t.copy(x=f.x+(t.x-f.x)*k, y=f.y+(t.y-f.y)*k,
                angle=f.angle+(t.angle-f.angle)*k, w=f.w+(t.w-f.w)*k, h=f.h+(t.h-f.h)*k,
                docked=k>=1f, settled=false)
            // Axis-aligned extent of this rotated plate, so the flat ground pose starts without a vertical pop.
            val rad = Math.toRadians(p.angle.toDouble())
            val rx = (abs(cos(rad))*p.w + abs(sin(rad))*p.h).toFloat()/2f + 1f
            val ry = (abs(sin(rad))*p.w + abs(cos(rad))*p.h).toFloat()/2f + 1f
            p.copy(x=p.x.coerceIn(screen.left+rx, max(screen.left+rx, screen.right-rx)),
                y=p.y.coerceIn(screen.top+ry, max(screen.top+ry, floorY-ry)))
        }
    }
    /** Screen-space pass: the body's squash, bank, rotation and window padding cannot distort metal. */
    fun draw(c: Canvas, rig: MonkeyRig, wx: Float, wy: Float) {
        for ((i, p) in frame(rig, wx, wy).withIndex()) {
            c.save(); c.translate(p.x - wx, p.y - wy); c.rotate(p.angle)
            if (p.id == "helmet") {
                // Hollow gunmetal shell/ear discs/crest and dark visor, never a second skin face.
                paint.color=ArmourRenderer.GUN
                c.drawOval(-p.w*0.42f, -p.h/2f, p.w*0.42f, p.h/2f, paint)
                c.drawOval(-p.w*0.42f, -p.h/2f, p.w*0.42f, p.h/2f, rim)
                for (side in intArrayOf(-1,1)) {
                    paint.color=ArmourRenderer.GUN_DARK; c.drawCircle(side*p.w*0.43f, 0f, p.w*0.07f, paint)
                }
                paint.color=ArmourRenderer.SAFFRON
                c.drawRoundRect(-p.w*0.06f, -p.h*0.49f, p.w*0.06f, -p.h*0.23f, 2f, 2f, paint)
                paint.color=ArmourRenderer.VISOR
                c.drawRoundRect(-p.w*0.32f, -p.h*0.20f, p.w*0.32f, p.h*0.36f, 8f, 8f, paint)
                paint.color=ArmourRenderer.SAFFRON
                c.drawRect(-p.w*0.24f, -p.h*0.03f, -p.w*0.06f, p.h*0.01f, paint)
                c.drawRect(p.w*0.06f, -p.h*0.03f, p.w*0.24f, p.h*0.01f, paint)
                c.restore(); continue
            }
            paint.color = ArmourRenderer.GUN; c.drawRoundRect(-p.w/2, -p.h/2, p.w/2, p.h/2, 3f * scale, 3f * scale, paint)
            c.drawRoundRect(-p.w/2, -p.h/2, p.w/2, p.h/2, 3f * scale, 3f * scale, rim)
            paint.color = ArmourRenderer.SAFFRON
            c.drawRect(-p.w * 0.35f, -p.h * 0.1f, p.w * 0.35f, p.h * 0.1f, paint)
            if (i == 0) { paint.color = ArmourRenderer.CORE; c.drawCircle(0f, 0f, p.w * 0.18f, paint) }
            c.restore()
        }
    }
}
