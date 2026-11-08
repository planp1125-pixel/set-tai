package com.combo.monkeycompanion.rig

import kotlin.math.abs

import android.graphics.Matrix
import com.combo.monkeycompanion.brain.TouchZone
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The skeleton. Each part has a joint (pivot) and a transform that is composed
 * hierarchically: root -> hips -> {legs, arms -> forearms, head, tail chain}.
 * [layout] turns a [Pose] into one matrix per part (rig units -> view pixels);
 * the renderer draws with those matrices and [hitTest] inverts them, so touch
 * zones stay correct while the monkey bounces, tilts, jumps or faces the other way.
 */
class MonkeyRig {
    val root = Matrix()
    val hips = Matrix()
    val legL = Matrix()
    val legR = Matrix()
    val armLUpper = Matrix()
    val armLFore = Matrix()
    val armRUpper = Matrix()
    val armRFore = Matrix()
    val head = Matrix()

    /** Tail chain points in hips space (x,y pairs), and the same in view space. */
    val tailPts = FloatArray(TAIL_SEGS * 2 + 2)
    val tailView = FloatArray(TAIL_SEGS * 2 + 2)

    var scale = 1f; private set
    var anchorX = 0f; private set
    var anchorY = 0f; private set
    var facingSign = 1; private set
    /** v10.7: rig units above the feet that squash/stretch pivots around (0 = feet, the v9.5 behaviour). */
    var squashPivot = 0f
    /** Arm segment lengths this frame (stretched while hanging: long monkey arms). */
    var upperLen = UPPER_ARM; private set
    var foreLen = FOREARM; private set

    fun layout(p: Pose, ax: Float, ay: Float, scale: Float, facing: Float) {
        this.scale = scale; anchorX = ax; anchorY = ay; facingSign = if (facing < 0) -1 else 1
        upperLen = UPPER_ARM * p.armStretch; foreLen = FOREARM * p.armStretch
        root.reset()
        root.setTranslate(ax, ay)
        if (squashPivot == 0f) root.preScale(scale * facing * p.squashX * p.size, scale * p.squashY * p.size)
        else {
            // v10.7 smooth animation: squash around a point [squashPivot] rig units above the feet (body centre in flight).
            root.preScale(scale * facing * p.size, scale * p.size)
            root.preTranslate(0f, -squashPivot); root.preScale(p.squashX, p.squashY); root.preTranslate(0f, squashPivot)
        }

        hips.set(root)
        hips.preTranslate(p.bodyX, HIP_Y + p.bodyY)
        hips.preRotate(p.bodyRot)

        legL.set(hips); legL.preTranslate(-HIP_X, -2f - p.legLLift); legL.preRotate(p.legLSwing)
        legR.set(hips); legR.preTranslate(HIP_X, -2f - p.legRLift); legR.preRotate(-p.legRSwing)

        armLUpper.set(hips); armLUpper.preTranslate(-SHOULDER_X, SHOULDER_Y); armLUpper.preRotate(p.armLRaise)
        armLFore.set(armLUpper); armLFore.preTranslate(0f, upperLen); armLFore.preRotate(p.armLElbow)
        armRUpper.set(hips); armRUpper.preTranslate(SHOULDER_X, SHOULDER_Y); armRUpper.preRotate(-p.armRRaise)
        armRFore.set(armRUpper); armRFore.preTranslate(0f, upperLen); armRFore.preRotate(-p.armRElbow)

        head.set(hips); head.preTranslate(0f, NECK_Y + p.headY); head.preRotate(p.headRot)

        // Tail: a chain of segments, each bending a bit more -> S-curve ending in a curl.
        var x = TAIL_BASE_X; var y = TAIL_BASE_Y
        tailPts[0] = x; tailPts[1] = y
        var a = 20f - p.tailRot
        val bends = bendsBuf
        bends[0] = 0f; bends[1] = -40f - 12f * p.tailSway; bends[2] = -50f - 10f * p.tailSway; bends[3] = -80f
        for (i in 0 until TAIL_SEGS) {
            a += bends[i]
            val r = Math.toRadians(a.toDouble())
            x += (TAIL_LEN[i] * cos(r)).toFloat()
            y += (TAIL_LEN[i] * sin(r)).toFloat()
            tailPts[2 * i + 2] = x; tailPts[2 * i + 3] = y
        }
        hips.mapPoints(tailView, tailPts)
    }

    private val bendsBuf = FloatArray(TAIL_SEGS)
    private val inv = Matrix()
    private val pt = FloatArray(2)

    private fun local(m: Matrix, x: Float, y: Float): FloatArray {
        m.invert(inv)
        pt[0] = x; pt[1] = y
        inv.mapPoints(pt)
        return pt
    }

    private fun inCircle(m: Matrix, x: Float, y: Float, cx: Float, cy: Float, r: Float): Boolean {
        val l = local(m, x, y)
        return hypot(l[0] - cx, l[1] - cy) <= r
    }

    private fun inEllipse(lx: Float, ly: Float, cx: Float, cy: Float, rx: Float, ry: Float): Boolean {
        val dx = (lx - cx) / rx; val dy = (ly - cy) / ry
        return dx * dx + dy * dy <= 1f
    }

    /** v10.8: is view point (x, y) on the prop ridden under the hips (Skye's rocket and its stick)? */
    fun propHit(x: Float, y: Float): Boolean {
        val h = local(hips, x, y)
        // v10.9: Skye's tilted rocket (tube + long stick), in the rocket's own frame
        val r = FloatArray(2); RocketGeom.toRocket(h[0], h[1], r)
        return r[0] in (RocketGeom.RIDE_STICK_BACK - 6f)..(DiwaliRocketArt.NOSE + 4f) && r[1] in (DiwaliRocketArt.TY - DiwaliRocketArt.R - 6f)..8f
    }

    /** v10.8: on an ear (or the outer top edge of the head, where floppy ears sit)? */
    fun earHit(x: Float, y: Float): Boolean {
        val k = local(head, x, y); val kx = kotlin.math.abs(k[0]); val ky = k[1]
        return hypot(kx - EAR_X, ky - EAR_Y) <= EAR_R + 2f || (kx > HEAD_R * 0.72f && ky < HEAD_CY + 4f && hypot(kx, ky - HEAD_CY) <= HEAD_R + 6f)
    }

    /** v10.8: on the lower middle of the face (Gajju's trunk curls up from there)? */
    fun trunkHit(x: Float, y: Float): Boolean {
        val k = local(head, x, y)
        return kotlin.math.abs(k[0]) < 16f && k[1] in -30f..-6f
    }

    /** Which body part is under view point (x, y)? */
    fun hitTest(x: Float, y: Float): TouchZone {
        // Extremities first: they sit on top of everything else.
        if (inCircle(armLFore, x, y, 0f, foreLen + 2f, 11f) || inCircle(armRFore, x, y, 0f, foreLen + 2f, 11f)) return TouchZone.HAND
        if (inCircle(legL, x, y, 0f, LEG + 3f, 12f) || inCircle(legR, x, y, 0f, LEG + 3f, 12f)) return TouchZone.FOOT

        val h = local(hips, x, y); val hx = h[0]; val hy = h[1]
        if (inEllipse(hx, hy, 0f, -2f, 13f, 10f)) return TouchZone.PRIVATE

        val k = local(head, x, y); val kx = k[0]; val ky = k[1]
        if (ky in -8f..9f && kotlin.math.abs(kx) < 22f) return TouchZone.NECK
        if (inEllipse(hx, hy, 0f, -26f, 21f, 19f)) return TouchZone.BELLY
        if (hypot(kx, ky - HEAD_CY) <= HEAD_R + 2f ||
            hypot(kotlin.math.abs(kx) - EAR_X, ky - EAR_Y) <= EAR_R + 2f ||
            (ky in -84f..-64f && kotlin.math.abs(kx) < 10f)
        ) return TouchZone.HEAD
        if (inEllipse(hx, hy, 0f, -28f, 30f, 34f)) return TouchZone.BELLY
        // Tail: distance to the chain in view pixels.
        val tol = 9f * scale
        for (i in 0 until TAIL_SEGS) {
            if (distToSeg(x, y, tailView[2 * i], tailView[2 * i + 1], tailView[2 * i + 2], tailView[2 * i + 3]) <= tol)
                return TouchZone.TAIL
        }
        return TouchZone.NONE
    }

    /** Hip joint position in view space. */
    fun hipPoint(out: FloatArray) { out[0] = 0f; out[1] = 0f; hips.mapPoints(out) }

    /** Sole of a foot in view space, [down] rig units further along the leg (for jet flames). */
    fun footPoint(left: Boolean, out: FloatArray, down: Float) {
        out[0] = 0f; out[1] = LEG + 6f + down; (if (left) legL else legR).mapPoints(out)
    }

    /** Hand centre in view space. */
    fun handPoint(left: Boolean, out: FloatArray) {
        out[0] = 0f; out[1] = foreLen + 2f; (if (left) armLFore else armRFore).mapPoints(out)
    }

    /**
     * Two-bone IK: set the arm's raise/elbow in [p] so the hand reaches view point (tx, ty).
     * Call after [layout]; then lay out again. Elbows bend outward/down like a real arm.
     */
    fun solveArm(p: Pose, left: Boolean, tx: Float, ty: Float) {
        hips.invert(inv)
        pt[0] = tx; pt[1] = ty; inv.mapPoints(pt)
        val sx = if (left) -SHOULDER_X else SHOULDER_X
        var dx = pt[0] - sx; val dy = pt[1] - SHOULDER_Y
        if (!left) dx = -dx  // mirror the right arm into the left arm's convention
        val l1 = upperLen; val l2 = foreLen + 2f
        val dist = kotlin.math.hypot(dx, dy).coerceIn(abs(l1 - l2) + 0.5f, l1 + l2 - 0.01f)
        val base = Math.toDegrees(kotlin.math.atan2(-dx, dy).toDouble()).toFloat()  // raise pointing straight at target
        val cosA = ((l1 * l1 + dist * dist - l2 * l2) / (2 * l1 * dist)).coerceIn(-1f, 1f)
        val a = Math.toDegrees(kotlin.math.acos(cosA).toDouble()).toFloat()
        val raise = base - a
        val rr = Math.toRadians(raise.toDouble())
        val ex = (-l1 * kotlin.math.sin(rr)).toFloat(); val ey = (l1 * kotlin.math.cos(rr)).toFloat()
        val real = kotlin.math.hypot(dx, dy).coerceAtLeast(0.001f)
        val tdx = dx * dist / real; val tdy = dy * dist / real
        val r2 = Math.toDegrees(kotlin.math.atan2(-(tdx - ex).toDouble(), (tdy - ey).toDouble())).toFloat()
        var elbow = r2 - raise
        while (elbow > 180f) elbow -= 360f
        while (elbow < -180f) elbow += 360f
        if (left) { p.armLRaise = raise; p.armLElbow = elbow } else { p.armRRaise = raise; p.armRElbow = elbow }
    }

    /** Smallest y (view space) of the two hand tips: used to hang the monkey by its hands. */
    fun handsTopY(): Float {
        pt[0] = 0f; pt[1] = foreLen + 6f; armLFore.mapPoints(pt); val a = pt[1]
        pt[0] = 0f; pt[1] = foreLen + 6f; armRFore.mapPoints(pt)
        return kotlin.math.min(a, pt[1])
    }

    /** Top of the head in view space (for the speech-bubble tail and effects). */
    fun headTop(out: FloatArray) {
        out[0] = 0f; out[1] = HEAD_CY - HEAD_R - 18f // clears the hair tuft
        head.mapPoints(out)
    }

    private fun distToSeg(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        var t = if (len2 == 0f) 0f else ((px - ax) * dx + (py - ay) * dy) / len2
        t = t.coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    companion object {
        // Rig geometry, rig units. Feet touch y = 0.
        const val HIP_Y = -34f
        const val HIP_X = 12f
        const val LEG = 27f
        const val SHOULDER_X = 21f
        const val SHOULDER_Y = -48f
        const val UPPER_ARM = 20f
        const val FOREARM = 18f
        const val NECK_Y = -56f
        const val HEAD_CY = -34f
        const val HEAD_R = 34f
        const val EAR_X = 34f
        const val EAR_Y = -36f
        const val EAR_R = 12f
        const val TAIL_BASE_X = 15f
        const val TAIL_BASE_Y = -6f
        const val TAIL_SEGS = 4
        val TAIL_LEN = floatArrayOf(17f, 16f, 13f, 8f)
        /** Total height incl. tuft, used to size the overlay window. */
        const val HEIGHT = 168f
    }
}
