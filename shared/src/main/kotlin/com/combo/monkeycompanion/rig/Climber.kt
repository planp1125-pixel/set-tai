package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.combo.monkeycompanion.brain.TouchZone
import com.combo.monkeycompanion.rig.MonkeyRenderer.Companion.BLUSH
import com.combo.monkeycompanion.rig.MonkeyRenderer.Companion.EAR_IN
import com.combo.monkeycompanion.rig.MonkeyRenderer.Companion.EYE
import com.combo.monkeycompanion.rig.MonkeyRenderer.Companion.FACE
import com.combo.monkeycompanion.rig.MonkeyRenderer.Companion.FACE_LIGHT
import com.combo.monkeycompanion.rig.MonkeyRenderer.Companion.FACE_SHADE
import com.combo.monkeycompanion.rig.MonkeyRenderer.Companion.FUR
import com.combo.monkeycompanion.rig.MonkeyRenderer.Companion.FUR_DARK
import com.combo.monkeycompanion.rig.MonkeyRenderer.Companion.FUR_LIGHT
import com.combo.monkeycompanion.rig.MonkeyRenderer.Companion.MOUTH
import com.combo.monkeycompanion.rig.MonkeyRenderer.Companion.OUTLINE
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Side-on (profile) climbing monkey for walls and ceilings, simulated in screen pixels.
 *
 * - Hands (and feet on walls) are planted on fixed grip points on the surface and stay put while
 *   the body moves; when a limb gets too far from its comfortable spot it releases and re-grips
 *   a stride ahead (hand-over-hand, alternating with its partner).
 * - Every limb is a 2-bone IK chain from shoulder / hip to its grip.
 * - Secondary motion: spring-follow body, pendulum swing when hanging, lagging head, jiggling ear,
 *   a verlet tail chain with a soft curl.
 *
 * [u] = pixels per rig unit (same units as [MonkeyRig]).
 */
class Climber(private val u: Float) {
    enum class Mode { WALL, CEIL }

    var mode = Mode.WALL; private set
    /** Wall x or ceiling y in screen px. */
    var line = 0f; private set
    /** +1 = facing right on screen. On walls the monkey faces the wall. */
    var face = 1f; private set
    private var spanA = -1e9f
    private var spanB = 1e9f
    var active = false; private set

    val hip = PointF(); val sho = PointF(); val headC = PointF()
    private val hipV = PointF()
    private val shoV = PointF()
    private var lastShoVx = 0f
    private var ang = 0f; private var angV = 0f          // body angle (rad): lean on walls, pendulum on ceilings
    private var headAng = 0f; private var headAngV = 0f  // lagging head
    private var ear = 0f; private var earV = 0f
    private var lastHeadY = 0f; private var headVy = 0f

    /** 0 = profile head facing the wall / travel direction, 1 = head turned to look at the user. */
    var lookAtUser = false
    private var headFront = false
    private var headTurnK = 1f
    private var bodyTurnK = 1f

    private class Limb(val arm: Boolean, val near: Boolean) {
        val root = PointF(); val joint = PointF(); val end = PointF()
        val plant = PointF(); var planted = false
        var swinging = false; val from = PointF(); val to = PointF(); var k = 0f
        var free = false  // dangling (legs while hanging)
        val endV = PointF()
    }
    private val armN = Limb(true, true); private val armF = Limb(true, false)
    private val legN = Limb(false, true); private val legF = Limb(false, false)
    private val limbs = arrayOf(armF, legF, armN, legN)
    private var stepCool = 0f

    private val tail = Array(7) { PointF() }
    private val tailPrev = Array(7) { PointF() }

    // ---- geometry (rig units) ----
    private val TORSO = 44f
    private val UPPER = 24f; private val FORE = 23f
    private val THIGH = 20f; private val SHIN = 21f
    private val HEAD_R = 30f
    private val STRIDE = 30f
    private val SEG = 9f

    // ---------------- control ----------------

    /**
     * Start climbing (or switch surface). Limbs keep their current positions and re-grip,
     * so a surface change looks like reaching across a corner.
     */
    fun attach(m: Mode, line: Float, face: Float, spanA: Float, spanB: Float) {
        if (active && face != this.face) bodyTurnK = 0f
        val wasCeil = active && mode == Mode.CEIL
        mode = m; this.line = line; this.face = face; this.spanA = spanA; this.spanB = spanB
        for (l in limbs) {
            if (l.planted && !onSurface(l.plant)) l.planted = false
            if (!l.arm) l.free = m == Mode.CEIL
            if (!l.arm && m == Mode.WALL && wasCeil) l.planted = false
        }
    }

    /** Begin from a standing / other pose: hip, shoulder and limb tips in screen px. */
    fun activate(m: Mode, line: Float, face: Float, spanA: Float, spanB: Float,
                 hipX: Float, hipY: Float, hands: FloatArray, feet: FloatArray) {
        active = false
        attach(m, line, face, spanA, spanB)
        active = true
        hip.set(hipX, hipY); hipV.set(0f, 0f)
        sho.set(hipX, hipY - TORSO * u); shoV.set(0f, 0f)
        ang = 0f; angV = 0f; headAng = 0f; headAngV = 0f
        armN.end.set(hands[0], hands[1]); armF.end.set(hands[2], hands[3])
        legN.end.set(feet[0], feet[1]); legF.end.set(feet[2], feet[3])
        for (l in limbs) { l.planted = false; l.swinging = false; l.free = !l.arm && m == Mode.CEIL; l.endV.set(0f, 0f) }
        for (i in tail.indices) { tail[i].set(hipX - face * (12f + i * SEG) * u, hipY - i * 2f * u); tailPrev[i].set(tail[i]) }
        bodyTurnK = 0.5f
    }

    fun deactivate() { active = false }

    /** All hands (and feet on walls) are holding on. */
    val gripped: Boolean get() = armN.planted && armF.planted && (mode == Mode.CEIL || (legN.planted && legF.planted))

    private fun onSurface(p: PointF): Boolean =
        if (mode == Mode.WALL) abs(p.x - line) < 2f * u else abs(p.y - line) < 2f * u

    // ---------------- simulation ----------------

    /**
     * @param ax,ay anchor: hip (walls) or shoulders (ceiling) target in screen px
     * @param pose  current blended pose: face expression, reaction shakes, dance swing
     */
    fun update(dt0: Float, ax: Float, ay: Float, t: Float, pose: Pose) {
        if (!active) return
        val dt = dt0.coerceIn(0.001f, 0.04f)
        stepCool -= dt
        if (bodyTurnK < 1f) bodyTurnK = min(1f, bodyTurnK + dt / 0.28f)
        if (lookAtUser != headFront) { headFront = lookAtUser; headTurnK = 0f }
        if (headTurnK < 1f) headTurnK = min(1f, headTurnK + dt / 0.22f)

        if (mode == Mode.WALL) {
            val tx = line - face * 44f * u + pose.bodyRot * 0.15f * u
            val ty = ay + min(0f, pose.bodyY) * 0.25f * u
            spring(hip, hipV, tx, ty, 140f, dt)
            val target = face * 0.14f + 0.035f * sin(t * 1.3f) + Math.toRadians(pose.bodyRot.toDouble()).toFloat() * 0.5f
            angV += ((target - ang) * 90f - angV * 16f) * dt; ang += angV * dt
            sho.set(hip.x + sin(ang) * TORSO * u, hip.y - cos(ang) * TORSO * u)
            shoV.set(0f, 0f)
        } else {
            val tx = ax; val ty = line + 45f * u
            val pvx = shoV.x
            spring(sho, shoV, tx, ty, 120f, dt)
            val accX = (shoV.x - pvx) / dt
            // Pendulum: the body swings under the hands, pushed by the shoulders' acceleration.
            val drive = Math.toRadians(pose.hangSwing.toDouble()).toFloat()
            angV += (-(ang - drive) * 13f - angV * 1.6f + accX / (TORSO * u) * 0.9f) * dt
            angV += 0.18f * sin(t * 1.7f) * dt
            ang = (ang + angV * dt).coerceIn(-0.9f, 0.9f)
            hip.set(sho.x - sin(ang) * TORSO * u, sho.y + cos(ang) * TORSO * u)
            hipV.set(0f, 0f)
        }
        // Head follows the body with a little lag; the ear jiggles with head motion.
        headAngV += ((ang - headAng) * 70f - headAngV * 11f) * dt; headAng += headAngV * dt
        val upX = if (mode == Mode.WALL) sin(ang) else 0f
        val upY = if (mode == Mode.WALL) -cos(ang) else -1f
        val hu = if (mode == Mode.WALL) 25f else 19f  // hunched between the arms when hanging
        headC.set(sho.x + upX * hu * u + (if (mode == Mode.WALL) -face * 2f else face * 5f) * u, sho.y + upY * hu * u)
        val vy = (headC.y - lastHeadY) / dt; val acc = (vy - headVy) / dt
        lastHeadY = headC.y; headVy = vy
        earV += (-ear * 160f - earV * 9f - (acc / u).coerceIn(-4000f, 4000f) * 0.004f) * dt
        ear = (ear + earV * dt).coerceIn(-1.4f, 1.4f)

        // Limb roots
        val fx = face
        if (mode == Mode.WALL) {
            armN.root.set(sho.x + fx * 3f * u, sho.y + 3f * u); armF.root.set(sho.x - fx * 2f * u, sho.y + 1f * u)
            legN.root.set(hip.x + fx * 5f * u, hip.y); legF.root.set(hip.x + fx * 2f * u, hip.y - 1f * u)
        } else {
            armN.root.set(sho.x + fx * 2f * u, sho.y); armF.root.set(sho.x - fx * 1f * u, sho.y - 1f * u)
            legN.root.set(hip.x + fx * 3f * u, hip.y); legF.root.set(hip.x - fx * 1f * u, hip.y - 1f * u)
        }
        stepLimb(armN, armF, dt); stepLimb(armF, armN, dt)
        if (mode == Mode.WALL) { stepLimb(legN, legF, dt); stepLimb(legF, legN, dt) }
        else { dangle(legN, t, 0f, dt); dangle(legF, t, 1.9f, dt) }
        // A cheeky one-handed swing now and then while hanging still.
        if (mode == Mode.CEIL && abs(shoV.x) < 5f * u) {
            val cyc = (t % 8f)
            if (cyc > 5.6f && armF.planted) {
                val k = sin(((cyc - 5.6f) / 2.4f) * PI.toFloat())
                armF.end.set(armF.root.x + fx * (14f + 16f * k) * u, armF.root.y + (-18f + 34f * k) * u + sin(t * 12f) * 3f * u * k)
                if (k > 0.05f) { solve(armF, UPPER, FORE, -fx * 0.3f, 1f); armF.end.set(armF.end) }
            }
        }
        for (l in limbs) {
            if (l.arm) solve(l, UPPER, FORE, if (mode == Mode.WALL) -fx else -fx * 0.5f, if (mode == Mode.WALL) 0.7f else 1f)
            else solve(l, THIGH, SHIN, if (mode == Mode.WALL) fx * 0.7f else fx, if (mode == Mode.WALL) -1f else 0.2f)
        }
        stepTail(t, dt)
    }

    private fun spring(p: PointF, v: PointF, tx: Float, ty: Float, k: Float, dt: Float) {
        val c = 2f * sqrt(k)
        v.x += ((tx - p.x) * k - v.x * c) * dt; v.y += ((ty - p.y) * k - v.y * c) * dt
        p.x += v.x * dt; p.y += v.y * dt
    }

    private val home = PointF()

    private fun homeOf(l: Limb, out: PointF) {
        if (mode == Mode.WALL) {
            val off = if (l.arm) -20f else 22f
            val ref = if (l.arm) sho.y else hip.y
            out.set(line, (ref + off * u).coerceIn(spanA, spanB))
        } else {
            out.set((sho.x + face * (if (l.near) 7f else -7f) * u).coerceIn(spanA, spanB), line)
        }
    }

    private fun stepLimb(l: Limb, partner: Limb, dt: Float) {
        homeOf(l, home)
        if (l.swinging) {
            l.k = min(1f, l.k + dt / 0.2f)
            val e = l.k * l.k * (3f - 2f * l.k)
            val arc = sin(l.k * PI.toFloat()) * 8f * u
            val nx = if (mode == Mode.WALL) -face else 0f
            val ny = if (mode == Mode.WALL) -0.3f else 1f
            l.end.set(l.from.x + (l.to.x - l.from.x) * e + nx * arc, l.from.y + (l.to.y - l.from.y) * e + ny * arc)
            if (l.k >= 1f) { l.swinging = false; l.planted = true; l.plant.set(l.to); l.end.set(l.to) }
            return
        }
        if (!l.planted) {
            if (!partner.swinging || stepCool < -0.25f) startSwing(l, home.x, home.y)
            else { l.end.x += (home.x - l.end.x) * min(1f, dt * 3f); l.end.y += (home.y - l.end.y) * min(1f, dt * 3f) }
            return
        }
        l.end.set(l.plant)
        val dist = hypot(l.plant.x - home.x, l.plant.y - home.y)
        if (dist > STRIDE * u * 0.55f && !partner.swinging && stepCool <= 0f) {
            val dx = home.x - l.plant.x; val dy = home.y - l.plant.y
            val n = max(0.001f, hypot(dx, dy))
            var tx = home.x + dx / n * STRIDE * u * 0.35f
            var ty = home.y + dy / n * STRIDE * u * 0.35f
            if (mode == Mode.WALL) { tx = line; ty = ty.coerceIn(spanA, spanB) } else { ty = line; tx = tx.coerceIn(spanA, spanB) }
            startSwing(l, tx, ty)
        }
    }

    private fun startSwing(l: Limb, tx: Float, ty: Float) {
        l.swinging = true; l.planted = false; l.k = 0f
        l.from.set(l.end); l.to.set(tx, ty)
        stepCool = 0.09f
    }

    private fun dangle(l: Limb, t: Float, ph: Float, dt: Float) {
        l.planted = false; l.swinging = false
        val kick = 0.35f * sin(t * 3.2f + ph)
        val a = ang * 0.6f + kick * face
        val tx = l.root.x + (sin(a) * 0.4f + face * 0.35f) * 36f * u
        val ty = l.root.y + cos(a) * 34f * u
        spring(l.end, l.endV, tx, ty, 90f, dt)
    }

    private fun solve(l: Limb, l1u: Float, l2u: Float, prefX: Float, prefY: Float) {
        val l1 = l1u * u; val l2 = l2u * u
        val dx = l.end.x - l.root.x; val dy = l.end.y - l.root.y
        val real = max(0.001f, hypot(dx, dy))
        val d = real.coerceIn(abs(l1 - l2) + 0.5f, l1 + l2 - 0.5f)
        val base = atan2(dy, dx)
        val a = acos(((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d)).coerceIn(-1f, 1f))
        // two candidate elbows; take the one bending toward the preferred side
        val j1x = l.root.x + cos(base + a) * l1; val j1y = l.root.y + sin(base + a) * l1
        val j2x = l.root.x + cos(base - a) * l1; val j2y = l.root.y + sin(base - a) * l1
        val mx = l.root.x + dx * 0.5f; val my = l.root.y + dy * 0.5f
        val s1 = (j1x - mx) * prefX + (j1y - my) * prefY
        val s2 = (j2x - mx) * prefX + (j2y - my) * prefY
        if (s1 >= s2) l.joint.set(j1x, j1y) else l.joint.set(j2x, j2y)
        if (real > l1 + l2 - 0.5f) {  // can't reach: straight toward it
            l.end.set(l.root.x + dx / real * (l1 + l2 - 0.5f), l.root.y + dy / real * (l1 + l2 - 0.5f))
        }
    }

    private fun stepTail(t: Float, dt: Float) {
        val back = -face
        val root = tail[0]
        if (mode == Mode.WALL) root.set(hip.x + back * 13f * u, hip.y - 2f * u)
        else root.set(hip.x - sin(ang) * 2f * u + back * 11f * u, hip.y - 4f * u)
        tailPrev[0].set(root)
        val g = 700f * u * dt * dt
        for (i in 1 until tail.size) {
            val p = tail[i]; val q = tailPrev[i]
            val vx = (p.x - q.x) * 0.9f; val vy = (p.y - q.y) * 0.9f
            q.set(p)
            p.x += vx; p.y += vy + g
        }
        val wave = 0.25f * sin(t * 2.2f)
        for (iter in 0 until 3) {
            var dirX = back; var dirY = 0.3f
            for (i in 1 until tail.size) {
                val a = tail[i - 1]; val p = tail[i]
                // soft curl: each segment wants to bend upward a bit more than the last
                val curl = (0.32f + wave) * (-back) * (if (i > 2) 1f else 0.4f)
                val cx = dirX * cos(curl) - dirY * sin(curl); val cy = dirX * sin(curl) + dirY * cos(curl)
                val wantX = a.x + cx * SEG * u; val wantY = a.y + cy * SEG * u
                p.x += (wantX - p.x) * 0.18f; p.y += (wantY - p.y) * 0.18f
                val dx = p.x - a.x; val dy = p.y - a.y
                val n = max(0.001f, hypot(dx, dy))
                p.x = a.x + dx / n * SEG * u; p.y = a.y + dy / n * SEG * u
                dirX = dx / n; dirY = dy / n
            }
        }
    }

    // ---------------- queries ----------------

    fun bounds(out: RectF) {
        out.set(headC.x - (HEAD_R + 16f) * u, headC.y - (HEAD_R + 20f) * u, headC.x + (HEAD_R + 16f) * u, headC.y + (HEAD_R + 6f) * u)
        for (l in limbs) { out.union(l.end.x - 9f * u, l.end.y - 9f * u, l.end.x + 9f * u, l.end.y + 9f * u); out.union(l.joint.x, l.joint.y) }
        for (p in tail) out.union(p.x - 6f * u, p.y - 6f * u, p.x + 6f * u, p.y + 6f * u)
        out.union(hip.x - 24f * u, hip.y - 24f * u, hip.x + 24f * u, hip.y + 24f * u)
    }

    fun headTop(out: FloatArray) { out[0] = headC.x; out[1] = headC.y - (HEAD_R + 12f) * u }

    fun hitTest(x: Float, y: Float): TouchZone {
        for (l in limbs) if (hypot(x - l.end.x, y - l.end.y) < 12f * u) return if (l.arm) TouchZone.HAND else TouchZone.FOOT
        if (hypot(x - headC.x, y - headC.y) < (HEAD_R + 4f) * u) return TouchZone.HEAD
        for (i in 2 until tail.size) if (hypot(x - tail[i].x, y - tail[i].y) < 10f * u) return TouchZone.TAIL
        val pfx = hip.x + face * 12f * u; val pfy = hip.y + 2f * u
        if (hypot(x - pfx, y - pfy) < 9f * u) return TouchZone.PRIVATE
        if (hypot(x - sho.x, y - sho.y) < 10f * u) return TouchZone.NECK
        val mx = (hip.x + sho.x) / 2; val my = (hip.y + sho.y) / 2
        if (hypot(x - mx, y - my) < 28f * u) return TouchZone.BELLY
        return TouchZone.NONE
    }

    // ---------------- drawing ----------------

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val oval = RectF()
    private val m = Matrix()
    private val ow = MonkeyRenderer.OW

    fun draw(c: Canvas, pose: Pose, t: Float, front: MonkeyRenderer) {
        if (!active) return
        val bk = bodyTurnK
        val bsx = if (bk < 1f) max(0.14f, abs(1f - 2f * (0.5f + bk * 0.5f)) ) else 1f
        c.save()
        if (bk < 1f) c.scale(if (bk < 0.5f) max(0.14f, 1f - bk * 1.6f) else max(0.14f, (bk - 0.4f) / 0.6f), 1f, hip.x, hip.y)
        // far side (darker), then tail, body, near leg, head, near arm on top
        limb(c, armF, 10f, FUR_DARK, FACE_SHADE, 7f)
        limb(c, legF, 12f, FUR_DARK, FACE_SHADE, 8f)
        drawTail(c)
        drawTorso(c)
        limb(c, legN, 12.5f, FUR, FACE, 8.5f)
        drawHead(c, pose, t, front)
        limb(c, armN, 10.5f, FUR, FACE, 7.5f)
        c.restore()
        if (bsx < 0f) Unit
    }

    private fun limb(c: Canvas, l: Limb, w: Float, color: Int, hand: Int, hr: Float) {
        path.reset(); path.moveTo(l.root.x, l.root.y); path.lineTo(l.joint.x, l.joint.y); path.lineTo(l.end.x, l.end.y)
        stroke.color = OUTLINE; stroke.strokeWidth = (w + 2 * ow) * u; c.drawPath(path, stroke)
        stroke.color = color; stroke.strokeWidth = w * u; c.drawPath(path, stroke)
        val ang = atan2(l.end.y - l.joint.y, l.end.x - l.joint.x)
        val hx = l.end.x + cos(ang) * 2f * u; val hy = l.end.y + sin(ang) * 2f * u
        fill.color = hand; c.drawCircle(hx, hy, hr * u, fill)
        stroke.color = OUTLINE; stroke.strokeWidth = ow * u; c.drawCircle(hx, hy, hr * u, stroke)
    }

    private fun drawTail(c: Canvas) {
        path.reset(); path.moveTo(tail[0].x, tail[0].y)
        for (i in 1 until tail.size - 1) {
            val mx = (tail[i].x + tail[i + 1].x) / 2; val my = (tail[i].y + tail[i + 1].y) / 2
            path.quadTo(tail[i].x, tail[i].y, mx, my)
        }
        path.lineTo(tail.last().x, tail.last().y)
        stroke.color = OUTLINE; stroke.strokeWidth = (7f + 2 * ow) * u; c.drawPath(path, stroke)
        stroke.color = FUR; stroke.strokeWidth = 7f * u; c.drawPath(path, stroke)
    }

    private fun drawTorso(c: Canvas) {
        val cx = (hip.x + sho.x) / 2; val cy = (hip.y + sho.y) / 2
        val deg = Math.toDegrees(atan2(sho.x - hip.x, -(sho.y - hip.y)).toDouble()).toFloat()
        c.save(); c.rotate(deg, cx, cy)
        oval.set(cx - 22f * u, cy - 33f * u, cx + 22f * u, cy + 31f * u)
        fill.color = FUR; c.drawOval(oval, fill)
        fill.color = FUR_LIGHT; fill.alpha = 90
        oval.set(cx - 14f * u, cy - 28f * u, cx + 8f * u, cy + 4f * u); c.drawOval(oval, fill); fill.alpha = 255
        oval.set(cx - 22f * u, cy - 33f * u, cx + 22f * u, cy + 31f * u)
        stroke.color = OUTLINE; stroke.strokeWidth = ow * u; c.drawOval(oval, stroke)
        // belly patch on the front side
        oval.set(cx + face * 4f * u - 12f * u, cy - 20f * u, cx + face * 4f * u + 12f * u, cy + 24f * u)
        oval.offset(face * 7f * u, 0f)
        fill.color = FACE; c.drawOval(oval, fill)
        c.restore()
    }

    private fun drawHead(c: Canvas, p: Pose, t: Float, front: MonkeyRenderer) {
        val k = headTurnK
        val showFront = if (k < 0.5f) !headFront else headFront
        val sx = if (k < 1f) max(0.12f, abs(1f - 2f * k)) else 1f
        c.save()
        c.scale(sx, 1f, headC.x, headC.y)
        if (showFront) {
            m.reset()
            m.setTranslate(headC.x, headC.y)
            m.preRotate(Math.toDegrees(headAng.toDouble()).toFloat() * 0.5f)
            m.preScale(u * 0.92f, u * 0.92f)
            m.preTranslate(0f, 34f)
            val saved = p.earWiggle
            p.earWiggle = saved + ear
            front.drawHeadAt(c, m, p, t)
            p.earWiggle = saved
        } else profileHead(c, p)
        c.restore()
    }

    /** Profile head in head-local units: +x = forward (the way it faces). */
    private fun profileHead(c: Canvas, p: Pose) {
        c.save()
        c.translate(headC.x, headC.y)
        c.rotate(Math.toDegrees(headAng.toDouble()).toFloat() * 0.6f)
        c.scale(face * u, u)
        val o = ow
        // ear (behind), jiggling
        val ey = -6f - (ear + p.earWiggle) * 4f
        fill.color = FUR; c.drawCircle(-11f, ey, 12f, fill)
        stroke.color = OUTLINE; stroke.strokeWidth = o; c.drawCircle(-11f, ey, 12f, stroke)
        fill.color = EAR_IN; c.drawCircle(-10f, ey + 1f, 7f, fill)
        // tuft + skull
        path.reset(); path.moveTo(-6f, -28f); path.cubicTo(-8f, -42f, 9f, -46f, 8f, -36f); path.cubicTo(5f, -39f, 1f, -37f, 3f, -29f); path.close()
        fill.color = FUR; c.drawPath(path, fill); c.drawPath(path, stroke)
        fill.color = FUR; c.drawCircle(0f, 0f, HEAD_R, fill)
        fill.color = FUR_LIGHT; fill.alpha = 110; c.drawCircle(-6f, -10f, 16f, fill); fill.alpha = 255
        // muzzle outline first so it merges with the skull outline
        oval.set(14f, -2f, 40f, 22f)
        c.drawOval(oval, stroke)
        c.drawCircle(0f, 0f, HEAD_R, stroke)
        // face patch + muzzle
        fill.color = FACE; oval.set(-2f, -20f, 30f, 20f); c.drawOval(oval, fill)
        fill.color = FACE_LIGHT; oval.set(14f, -2f, 40f, 22f); c.drawOval(oval, fill)
        stroke.strokeWidth = 1.1f; stroke.color = FACE_SHADE; c.drawOval(oval, stroke)
        stroke.strokeWidth = o; stroke.color = OUTLINE
        if (p.anger > 0.01f) { fill.color = Color.rgb(0xFF, 0x2A, 0x1A); fill.alpha = (100 * p.anger.coerceIn(0f, 1f)).toInt(); c.drawCircle(0f, 0f, HEAD_R - 1f, fill); fill.alpha = 255 }
        // blush
        fill.color = BLUSH; fill.alpha = (70 + 120 * p.blush.coerceIn(0f, 1f)).toInt(); c.drawCircle(9f, 9f, 5f, fill); fill.alpha = 255
        // eye
        val ex = 16f + p.lookX * 0.5f; val eyy = -6f + p.lookY * 0.6f
        if (p.eyesHappy > 0.5f) {
            stroke.strokeWidth = 2.6f; path.reset(); path.moveTo(ex - 5f, eyy + 2f); path.quadTo(ex, eyy - 6f, ex + 5f, eyy + 2f); c.drawPath(path, stroke)
        } else {
            val ry = 6.4f * p.eyeOpen
            if (ry < 1.3f) { stroke.strokeWidth = 2.3f; c.drawLine(ex - 4.5f, eyy, ex + 4.5f, eyy, stroke) }
            else {
                fill.color = EYE; oval.set(ex - 4.4f, eyy - ry, ex + 4.4f, eyy + ry); c.drawOval(oval, fill)
                fill.color = Color.WHITE; c.drawCircle(ex + 1.6f, eyy - ry * 0.4f, 1.8f, fill)
            }
        }
        // brow
        stroke.strokeWidth = 3.2f
        c.save(); c.translate(ex - 1f, -18f + p.browY); c.rotate(p.browAngle * 0.8f); c.drawLine(-4.5f, 0f, 4.5f, 0f, stroke); c.restore()
        stroke.strokeWidth = o
        // nose + mouth on the muzzle
        fill.color = EYE; c.drawCircle(36f, 4f, 1.6f, fill)
        if (p.mouthOpen > 0.12f) {
            fill.color = MOUTH; oval.set(23f, 11f, 33f, 13f + 8f * p.mouthOpen.coerceAtMost(1.2f)); c.drawOval(oval, fill)
        } else {
            stroke.strokeWidth = 2f
            path.reset(); path.moveTo(22f, 13f); path.quadTo(29f, 13f + 4f * p.mouthSmile.coerceIn(-1f, 1f), 35f, 11f); c.drawPath(path, stroke)
            stroke.strokeWidth = o
        }
        c.restore()
    }
}
