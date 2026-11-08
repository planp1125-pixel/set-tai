package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.engine.anim.Appendage
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.HEAD_CY
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v10.9 where Skye's rocket sits, in hips space: it runs through [SEAT_X],[SEAT_Y] (where she sits on the stick)
 * at [TILT] degrees (nose up and forward; with her ~8 degree forward lean it reads as about 45 degrees on screen).
 * Shared by the renderer, the touch hit-test ([MonkeyRig.propHit]) and the overlay's exhaust particles.
 */
object RocketGeom {
    const val TILT = -38f
    private val ca = cos(Math.toRadians(TILT.toDouble())).toFloat()
    private val sa = sin(Math.toRadians(TILT.toDouble())).toFloat()
    /**
     * v10.10 she sits ASTRIDE the rocket here (rocket frame "along"; just in front of the tube's bottom), so the nozzle
     * and its spark fountain are behind her bottom and the stick trails out behind like a broom's handle.
     */
    const val SEAT_ALONG = 40f
    /** Where she sits (her crotch on the rocket's line), in hips space. */
    const val CROTCH_X = 0f; const val CROTCH_Y = 1f
    /** The ridden rocket is drawn a little bigger than the bottle one, so it reads at overlay size. */
    const val K = 1.5f
    /** The rocket frame's origin in hips space (derived from the seat). */
    val SEAT_X = CROTCH_X - SEAT_ALONG * K * ca
    val SEAT_Y = CROTCH_Y - SEAT_ALONG * K * sa
    /** Where the ridden rocket's stick ends (rocket frame): ~1.8x the tube, so it fits her window (the bottle rocket keeps the full 2.7x). */
    const val RIDE_STICK_BACK = -20f
    /**
     * v10.10 the ridden rocket is drawn MIRRORED across its stick ([MIRROR] = -1): the tube hangs on the stick's lower side,
     * she sits on the stick right above the tube's bottom, and her hands reach forward-down to the tube like a broom.
     */
    const val MIRROR = -1f
    /** Rocket-art frame (along, across) -> hips space. */
    fun toHips(along: Float, across: Float, out: FloatArray) {
        val ac = across * MIRROR
        out[0] = SEAT_X + (along * ca - ac * sa) * K
        out[1] = SEAT_Y + (along * sa + ac * ca) * K
    }
    /** Hips space -> rocket-art frame (along, across). */
    fun toRocket(hx: Float, hy: Float, out: FloatArray) {
        val dx = hx - SEAT_X; val dy = hy - SEAT_Y
        out[0] = (dx * ca + dy * sa) / K
        out[1] = (-dx * sa + dy * ca) / K * MIRROR
    }
    /** Hips-space point of the tube bottom, where the exhaust comes out. */
    fun exhaust(out: FloatArray) = toHips(DiwaliRocketArt.TUBE_B - 2f, DiwaliRocketArt.TY, out)
    /** Hips-space point just past the far end of the stick (smoke and sparks trail from here). */
    fun stickEnd(out: FloatArray) = toHips(RIDE_STICK_BACK - 4f, 0f, out)
    /** Unit direction of the rocket's nose in hips space. */
    val noseX get() = ca
    val noseY get() = sa
}

/**
 * v10.9 Skye (was Rocket Boy): an ORIGINAL slim, cute little Indian girl riding a classic Diwali sky rocket like a
 * broomstick. Procedurally drawn (no photo parts). A smaller head than the chibi kids ([headScale]), a soft oval face
 * with a little pointed chin, big sparkly lashed eyes, rosy cheeks, two pigtails with ribbons (and a small bindi), a
 * green blouse and a pink pavadai skirt with a gold border, slim arms and legs with tiny gold juttis.
 * The rocket (see [DiwaliRocketArt], [RocketGeom]) is tilted nose-up; both hands grip it, her legs dangle on the TAIL
 * spring, the skirt flutters on the CAPE spring, the pigtails swing on the HAIR spring. Its exhaust comes out of the
 * rocket's tube, never from her. [Pose.rbThrust] = the big launch burst.
 */
class RocketGirlRenderer : HumanRenderer() {
    companion object {
        val SKIN = Color.rgb(0xC9, 0x8E, 0x66)
        val SKIN_SHADE = Color.rgb(0x9C, 0x62, 0x40)
        val SKIN_LIGHT = Color.rgb(0xE6, 0xB4, 0x8E)
        val HAIR = Color.rgb(0x22, 0x14, 0x10)
        val HAIR_SHINE = Color.rgb(0x5A, 0x3C, 0x30)
        val BLOUSE = Color.rgb(0x18, 0x9E, 0x78)
        val BLOUSE_DARK = Color.rgb(0x0E, 0x6E, 0x54)
        val SKIRT = Color.rgb(0xF0, 0x3E, 0x8E)
        val SKIRT_DARK = Color.rgb(0xB8, 0x22, 0x66)
        val ZARI = Color.rgb(0xFF, 0xC8, 0x3A)
        val RIBBON = Color.rgb(0xFF, 0x3B, 0x5C)
        val JUTTI = Color.rgb(0xE8, 0xA8, 0x20)
        val EYE_IRIS = Color.rgb(0x3A, 0x22, 0x18)
        val LASH = Color.rgb(0x1A, 0x10, 0x0C)
        val CHEEK = Color.rgb(0xFF, 0x7A, 0x8C)
        val LIP = Color.rgb(0xD8, 0x4A, 0x5A)
        val BINDI = Color.rgb(0xE0, 0x1A, 0x3A)
        val GOLD = DiwaliRocketArt.GOLD
        val FLAME_HOT = DiwaliRocketArt.FLAME_HOT
        val RED = DiwaliRocketArt.RED
        /**
         * v10.10 her riding skeleton (hips space, facing +x): she sits astride the rocket at the hips with her torso
         * leaning [LEAN] degrees forward over it. Shoulders are at the top of the leaning torso; the head sits forward
         * over them ([headShiftX]).
         */
        const val LEAN = 14f
        const val TORSO_H = 38f
        const val UP = 17f; const val FORE = 16f
        const val THIGH = 13f; const val SHIN = 15f
        var LEFT_BEND = 1f
        var NEAR_BEND = 1f
        const val LEG_L = SHIN
        /** Leg angles (limb convention: 0 = straight down, negative = forward): thigh forward-down beside the rocket, shin hanging. */
        const val THIGH_A = -52f; const val SHIN_A = 18f
        /** Grip points on the rocket (along the rocket frame, on the tube): back hand, then the front hand near the cone. */
        const val GRIP_L = 56f; const val GRIP_R = 63f
        /** Where her right hand goes to light a fuse (hips space), and the sparkler tip there (= the fuse). */
        const val REACH_X = 28f; const val REACH_Y = -8f
        /** v10.10: a long phuljhadi wire (the bottle stands out past her rocket's nose, so the two never tangle). */
        const val WIRE_X = 29f; const val WIRE_Y = 14f
        const val FUSE_X = REACH_X + WIRE_X; const val FUSE_Y = REACH_Y + WIRE_Y
    }
    override val skin = SKIN
    override val skinShade = SKIN_SHADE
    override val hair = HAIR
    override val headScale: Float = 0.9f
    override val headDrop: Float = 23f
    override val headShiftX: Float = 11.5f
    override val glintEye: FloatArray? = floatArrayOf(10.5f, -34.5f, 0.9f)

    private fun h(i: Int): Float { var x = i * 374761393 + 668265263; x = (x xor (x ushr 13)) * 1274126177; return ((x xor (x ushr 16)) and 0xFFFF) / 65535f }

    // her own limb matrices (built each frame from the hips)
    private val upL = Matrix(); private val foreL = Matrix(); private val upR = Matrix(); private val foreR = Matrix()
    private val legLm = Matrix(); private val legRm = Matrix(); private val shinLm = Matrix(); private val shinRm = Matrix()
    private val q = FloatArray(2)
    private var faceShader: RadialGradient? = null

    /** Two-bone IK in hips space: (upper angle, fore angle) in the limb convention (0 deg = straight down). */
    private fun ik(sx: Float, sy: Float, tx: Float, ty: Float, out: FloatArray, bendOut: Float) {
        val dx = tx - sx; val dy = ty - sy
        val dist = hypot(dx, dy).coerceIn(abs(UP - FORE) + 0.5f, UP + FORE - 0.3f)
        val base = Math.toDegrees(atan2(-dx, dy).toDouble()).toFloat()
        val cosA = ((UP * UP + dist * dist - FORE * FORE) / (2 * UP * dist)).coerceIn(-1f, 1f)
        val a = Math.toDegrees(kotlin.math.acos(cosA).toDouble()).toFloat()
        val cosB = ((UP * UP + FORE * FORE - dist * dist) / (2 * UP * FORE)).coerceIn(-1f, 1f)
        val b = 180f - Math.toDegrees(kotlin.math.acos(cosB).toDouble()).toFloat()
        out[0] = base + bendOut * a
        out[1] = out[0] - bendOut * b
    }

    private fun lerpAng(a: Float, b: Float, k: Float): Float { var d = b - a; while (d > 180f) d -= 360f; while (d < -180f) d += 360f; return a + d * k }
    private fun smooth(e0: Float, e1: Float, x: Float): Float { val k = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return k * k * (3f - 2f * k) }

    private val torsoM = Matrix()
    private val shL = FloatArray(2); private val shR = FloatArray(2)
    /** 0 = pure side view (full thrust), up to ~0.55 = a three-quarter view (hovering): the far leg and shoulder show more. */
    private var turn = 0f

    /** Shoulder point in hips space (the torso leans [LEAN] forward about the hips); [side] -1 = far, 1 = near. */
    private fun shoulder(side: Int, out: FloatArray) {
        val sx = side * (1.5f + 3.5f * turn); val sy = -TORSO_H + 3f
        val a = Math.toRadians(LEAN.toDouble()); val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        out[0] = sx * ca - sy * sa; out[1] = sx * sa + sy * ca - 1f
    }

    /** Build her arms (both hands grip the rocket in front of her) and her astride legs (thighs down beside the rocket). */
    private fun buildArms(rig: MonkeyRig, p: Pose) {
        turn = 0.55f * (1f - p.rbThrust.coerceIn(0f, 1f))
        torsoM.set(rig.hips); torsoM.preTranslate(0f, -1f); torsoM.preRotate(LEAN)
        val ang = FloatArray(2)
        // far (left) arm -> the back grip, just in front of her tummy
        shoulder(-1, shL)
        RocketGeom.toHips(GRIP_L, DiwaliRocketArt.TY, q)
        ik(shL[0], shL[1], q[0], q[1], ang, LEFT_BEND)
        val wl = 1f - smooth(55f, 100f, p.armLRaise)
        val poseLU = p.armLRaise; val poseLF = p.armLRaise + p.armLElbow
        val luA = lerpAng(poseLU, ang[0], wl); val lfA = lerpAng(poseLF, ang[1], wl)
        upL.set(rig.hips); upL.preTranslate(shL[0], shL[1]); upL.preRotate(luA)
        foreL.set(upL); foreL.preTranslate(0f, UP); foreL.preRotate(lfA - luA)
        // near (right) arm -> the front grip near the cone (or the sparkler / the fuse)
        shoulder(1, shR)
        RocketGeom.toHips(GRIP_R, DiwaliRocketArt.TY, q)
        val reach = p.rgReach.coerceIn(0f, 1f)
        if (reach > 0.01f) { q[0] += (REACH_X - q[0]) * reach; q[1] += (REACH_Y - q[1]) * reach }   // reaching down to a fuse
        ik(shR[0], shR[1], q[0], q[1], ang, NEAR_BEND)
        val wr = (1f - smooth(55f, 100f, p.armRRaise)) * (1f - smooth(0.05f, 0.4f, p.sparkler)).coerceAtLeast(reach)
        val poseRU = -p.armRRaise; val poseRF = -p.armRRaise - p.armRElbow
        val ruA = lerpAng(poseRU, ang[0], wr); val rfA = lerpAng(poseRF, ang[1], wr)
        upR.set(rig.hips); upR.preTranslate(shR[0], shR[1]); upR.preRotate(ruA)
        foreR.set(upR); foreR.preTranslate(0f, UP); foreR.preRotate(rfA - ruA)
        // legs astride: thighs forward-down on either side of the rocket, knees bent, shins dangle (TAIL spring swings them)
        val fl = THIGH_A + 6f * turn + p.legLLift * 0.2f; val nl = THIGH_A + p.legRLift * 0.2f
        legLm.set(rig.hips); legLm.preTranslate(-2f - 9f * turn, -3.5f - 3f * turn); legLm.preRotate(fl)
        shinLm.set(legLm); shinLm.preTranslate(0f, THIGH); shinLm.preRotate(SHIN_A + 8f - 14f * turn - fl + p.legLSwing * 0.5f)
        legRm.set(rig.hips); legRm.preTranslate(1f, -2.5f); legRm.preRotate(nl)
        shinRm.set(legRm); shinRm.preTranslate(0f, THIGH); shinRm.preRotate(SHIN_A - nl + p.legRSwing * 0.55f)
    }

    /** Right hand centre in view space (sparkler, fuse lighting). */
    fun rightHand(out: FloatArray) { out[0] = 0f; out[1] = FORE + 1f; foreR.mapPoints(out) }

    /** v10.10 Skye never shows boot jets: the rocket's own spark fountain is her only exhaust. */
    override fun drawJets(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) = Unit

    /**
     * Draw order for riding astride (v10.10): far leg -> far side of the skirt -> far arm -> blouse -> the rocket (between
     * her legs, up in front of her tummy) -> the near skirt draped over it -> near leg -> both hands + near arm -> head.
     */
    override fun drawBehind(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        buildArms(rig, p)
        drawLeg(c, legLm, shinLm, far = true)
        drawSkirtBack(c, rig, p, t)
    }

    private fun skirtWind(p: Pose, t: Float): Float {
        val sp = anim?.appendageAngle(Appendage.CAPE) ?: 0f
        return (6f + 10f * p.capeWind.coerceIn(0f, 1f) + 0.35f * sp + 1.5f * sin(t * 7f))
    }

    /** The far side of the pavadai hanging down behind the rocket and blowing back. */
    private fun drawSkirtBack(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val w = skirtWind(p, t)
        c.save(); c.concat(rig.hips)
        fill.shader = null; fill.color = SKIRT_DARK
        tmp.reset(); tmp.moveTo(-6f, -14f); tmp.lineTo(8f, -16f)
        tmp.cubicTo(12f, -6f, 13f, 4f, 10f, 11f)
        tmp.quadTo(-2f - w * 0.3f, 15f + sin(t * 8f), -12f - w * 0.45f, 11f + 2f * sin(t * 9f))
        tmp.cubicTo(-12f - w * 0.3f, 2f, -10f, -8f, -6f, -14f); tmp.close()
        c.drawPath(tmp, fill); outline.strokeWidth = 1.5f; c.drawPath(tmp, outline); outline.strokeWidth = OW
        c.restore()
    }

    /** One leg: thigh + shin + anklet + tiny gold jutti. The far leg is a shade darker (it's behind the rocket). */
    private fun drawLeg(c: Canvas, thigh: Matrix, shin: Matrix, far: Boolean) {
        val col = if (far) SKIN_SHADE else SKIN
        limb(c, thigh, THIGH, 5.6f, col, true); limb(c, shin, SHIN, 4.8f, col, true)
        limb(c, thigh, THIGH, 5.6f, col, false); limb(c, shin, SHIN, 4.8f, col, false)
        c.save(); c.concat(shin)
        fill.shader = null; fill.color = Color.rgb(0xE8, 0xE8, 0xF0)
        for (i in -1..1) c.drawCircle(i * 1.8f, LEG_L - 3.5f, 0.9f, fill)
        fill.color = if (far) Color.rgb(0xB8, 0x80, 0x12) else JUTTI
        // pointy toe forward (+x)
        tmp.reset(); tmp.moveTo(-3f, LEG_L - 1f); tmp.quadTo(-3.5f, LEG_L + 4f, 1f, LEG_L + 4f); tmp.quadTo(6f, LEG_L + 4f, 8f, LEG_L + 1.5f)
        tmp.quadTo(4f, LEG_L + 0.5f, 3f, LEG_L - 1f); tmp.close()
        c.drawPath(tmp, fill); outline.strokeWidth = 1.3f; c.drawPath(tmp, outline); outline.strokeWidth = OW
        c.restore()
    }

    /** Her far arm (behind her body; its hand is drawn on the rocket later). */
    override fun drawLegs(c: Canvas, rig: MonkeyRig) {
        limb(c, upL, UP, 4.4f, SKIN_SHADE, true); limb(c, foreL, FORE, 4.0f, SKIN_SHADE, true)
        limb(c, upL, UP, 4.4f, SKIN_SHADE, false); limb(c, foreL, FORE, 4.0f, SKIN_SHADE, false)
    }

    /** The rocket between her legs: out of her lap and up in front of her tummy (she rides it like a broom). */
    private fun drawRideRocket(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        c.save(); c.concat(rig.hips)
        val sleepy = p.zzz > 0.3f
        val flame = if (sleepy) 0f else (0.35f + 0.85f * p.rbThrust.coerceIn(0f, 1.3f)) * (if (p.fade < 1f) p.fade else 1f)
        val ga = Math.toRadians(RocketGeom.TILT.toDouble())
        c.save(); c.translate(RocketGeom.SEAT_X, RocketGeom.SEAT_Y); c.rotate(RocketGeom.TILT); c.scale(RocketGeom.K, RocketGeom.K * RocketGeom.MIRROR)
        DiwaliRocketArt.draw(c, t, flame, p.rbSputter, p.rbSpin, stickBack = RocketGeom.RIDE_STICK_BACK, ow = 1.8f,
            gx = sin(ga).toFloat(), gy = cos(ga).toFloat() * RocketGeom.MIRROR)
        c.restore()
        c.restore()
    }

    override fun drawTorso(c: Canvas, rig: MonkeyRig) {
        val p = pose; val t = now
        val w = skirtWind(p, t)
        val hw = 7.5f + 2.5f * turn   // half-width: wider when she turns toward us
        // the blouse on the leaning torso
        c.save(); c.concat(torsoM)
        fill.shader = null; fill.color = SKIN; oval.set(-2.6f, -TORSO_H - 6f, 2.6f, -TORSO_H + 2f); c.drawRect(oval, fill)
        fill.color = SKIN_SHADE; fill.alpha = 90; oval.set(-2.6f, -TORSO_H - 4f, 2.6f, -TORSO_H - 1.5f); c.drawRect(oval, fill); fill.alpha = 255
        fill.color = BLOUSE
        tmp.reset(); tmp.moveTo(-hw, -TORSO_H + 1f); tmp.quadTo(0f, -TORSO_H - 2f, hw, -TORSO_H + 1f)
        tmp.quadTo(hw + 2.5f, -TORSO_H + 10f, hw - 0.5f, -19f); tmp.lineTo(-hw + 0.5f, -19f); tmp.quadTo(-hw - 1.5f, -TORSO_H + 10f, -hw, -TORSO_H + 1f); tmp.close()
        c.drawPath(tmp, fill); outline.strokeWidth = 1.7f; c.drawPath(tmp, outline)
        stroke.shader = null; stroke.color = ZARI; stroke.strokeWidth = 1.3f
        tmp.reset(); tmp.moveTo(-4.5f, -TORSO_H - 0.3f); tmp.quadTo(0f, -TORSO_H + 4.5f, 4.5f, -TORSO_H - 0.3f); c.drawPath(tmp, stroke)
        fill.color = ZARI; oval.set(-hw - 0.5f, -20.5f, hw + 0.5f, -17.2f); c.drawRoundRect(oval, 1.5f, 1.5f, fill)
        outline.strokeWidth = 1.2f; c.drawRoundRect(oval, 1.5f, 1.5f, outline); outline.strokeWidth = OW
        c.restore()
        drawRideRocket(c, rig, p, t)
        // the near side of the pavadai, draped OVER the rocket: from her waist down past the rocket, hem blowing back
        c.save(); c.concat(rig.hips)
        val hem = 12f + 1.5f * sin(t * 6f)
        val bx = -9f - w * 0.45f          // the back corner (kept short so the nozzle and its sparks show behind her)
        fill.color = SKIRT
        tmp.reset(); tmp.moveTo(-1f, -18.5f); tmp.lineTo(13.5f, -14.5f)
        tmp.cubicTo(16f, -6f, 16.5f, 2f, 14.5f, hem - 3f)
        tmp.quadTo(5f - w * 0.2f, hem + 2.5f + sin(t * 8f), bx, hem - 1f + sin(t * 8f + 1f))
        tmp.cubicTo(bx + 1f, 2f, -8f, -10f, -1f, -18.5f); tmp.close()
        c.drawPath(tmp, fill); c.drawPath(tmp, outline)
        stroke.color = SKIRT_DARK; stroke.strokeWidth = 1.1f
        for (i in 0..3) { val x0 = 1f + i * 3.5f; c.drawLine(x0, -15f, x0 * 1.1f - 3f - w * 0.25f * (1f - i / 4f), hem - 1f, stroke) }
        c.save(); c.clipPath(tmp)
        stroke.color = ZARI; stroke.strokeWidth = 3.2f
        val bp = Path(); bp.moveTo(17f, hem - 5f); bp.quadTo(5f - w * 0.2f, hem - 0.5f, bx - 2f, hem - 3f)
        c.drawPath(bp, stroke)
        fill.color = Color.rgb(0xFF, 0xF0, 0xB0)
        for (i in 0 until 6) { c.drawCircle(12f - i * 3.8f - w * 0.1f * i, hem - 5.8f + i * 0.25f, 0.8f, fill) }
        c.restore()
        c.restore()
        // the near leg, in front of the rocket and the skirt
        drawLeg(c, legRm, shinRm, far = false)
    }

    private fun drawHand(c: Canvas, fore: Matrix, col: Int) {
        c.save(); c.concat(fore)
        stroke.shader = null; stroke.color = ZARI; stroke.strokeWidth = 1.2f; c.drawLine(-2.6f, FORE - 3f, 2.6f, FORE - 3f, stroke)
        fill.shader = null; fill.color = col; c.drawCircle(0f, FORE + 0.8f, 3.1f, fill)
        outline.strokeWidth = 1.3f; c.drawCircle(0f, FORE + 0.8f, 3.1f, outline); outline.strokeWidth = OW
        c.restore()
    }

    override fun drawArms(c: Canvas, rig: MonkeyRig) {
        // the far hand on the rocket, then the near arm (in front of everything) + both puff sleeves
        drawHand(c, foreL, SKIN_SHADE)
        limb(c, upR, UP, 4.6f, SKIN, true); limb(c, foreR, FORE, 4.2f, SKIN, true)
        limb(c, upR, UP, 4.6f, SKIN, false); limb(c, foreR, FORE, 4.2f, SKIN, false)
        for ((m, col) in arrayOf(upL to BLOUSE_DARK, upR to BLOUSE)) {
            c.save(); c.concat(m)
            fill.shader = null; fill.color = col; oval.set(-4.6f, -2.5f, 4.6f, 7.5f); c.drawOval(oval, fill)
            outline.strokeWidth = 1.4f; c.drawOval(oval, outline)
            stroke.shader = null; stroke.color = ZARI; stroke.strokeWidth = 1.1f; c.drawLine(-3.6f, 6.4f, 3.6f, 6.4f, stroke)
            c.restore()
        }
        drawHand(c, foreR, SKIN)
    }

    // ---------------- the head (designed at full size; drawHeadAt scales it by headScale) ----------------
    private val faceOval = Path().apply {
        moveTo(0f, -66f)
        cubicTo(17f, -66f, 27f, -54f, 27f, -38f)
        cubicTo(27f, -24f, 16f, -12f, 0f, -8.5f)       // a soft, slightly pointed chin (short: she's little)
        cubicTo(-16f, -12f, -27f, -24f, -27f, -38f)
        cubicTo(-27f, -54f, -17f, -66f, 0f, -66f)
        close()
    }

    override fun drawHeadBase(c: Canvas, p: Pose, t: Float) {
        val sw = (anim?.appendageAngle(Appendage.HAIR) ?: 0f) + 5f * sin(t * 3.1f)
        // back hair (a soft cap behind the face)
        fill.shader = null; fill.color = HAIR
        oval.set(-30f, -71f, 30f, -26f); c.drawOval(oval, fill)
        // pigtails, swinging on the HAIR spring, each tied with a ribbon
        for (s in intArrayOf(-1, 1)) {
            // high bunches tied at the top sides, flaring out and curling down
            // v10.10: both pigtails stream BACKWARD in the wind (more with speed)
            val stream = 22f + 26f * p.capeWind.coerceIn(0f, 1f) + 14f * p.rbThrust.coerceIn(0f, 1f)
            c.save(); c.translate(s * 22f, -63f); c.rotate(-s * 50f + stream * 1.05f + sw * (if (s < 0) 1.1f else 0.9f))
            fill.color = HAIR
            tmp.reset(); tmp.moveTo(-4f, 0f)
            tmp.cubicTo(-11f, 7f, -10f, 19f, -3f + s * 2f, 27f)
            tmp.quadTo(s * 4f + 1f, 30f, 4f + s * 3f, 25f)
            tmp.cubicTo(10f, 17f, 10f, 6f, 4f, 0f); tmp.close()
            c.drawPath(tmp, fill); outline.strokeWidth = 1.5f; c.drawPath(tmp, outline)
            stroke.shader = null; stroke.color = HAIR_SHINE; stroke.strokeWidth = 1.2f
            c.drawLine(-2f, 6f, -3f, 18f, stroke); c.drawLine(3f, 7f, 3.5f, 17f, stroke)
            if (has("ribbon")) {
                fill.color = RIBBON
                tmp.reset(); tmp.moveTo(0f, 2f); tmp.quadTo(-6f, -7f, -10.5f, -3f); tmp.quadTo(-11f, 4f, -9f, 7.5f); tmp.close(); c.drawPath(tmp, fill); c.drawPath(tmp, outline)
                tmp.reset(); tmp.moveTo(0f, 2f); tmp.quadTo(6f, -7f, 10.5f, -3f); tmp.quadTo(11f, 4f, 9f, 7.5f); tmp.close(); c.drawPath(tmp, fill); c.drawPath(tmp, outline)
                // ribbon tails flutter
                stroke.color = RIBBON; stroke.strokeWidth = 2f
                c.drawLine(0f, 3f, -3f + 2f * sin(t * 9f), 11f, stroke); c.drawLine(0f, 3f, 3f + 2f * sin(t * 9f + 1f), 11f, stroke)
                fill.color = Color.rgb(0xFF, 0x9A, 0xAE); c.drawCircle(0f, 2f, 2.2f, fill); c.drawCircle(0f, 2f, 2.2f, outline)
            }
            outline.strokeWidth = OW
            c.restore()
        }
        // little ears with gold jhumka studs
        fill.color = SKIN
        for (s in intArrayOf(-1, 1)) {
            oval.set(s * 26.5f - 4f, -42f, s * 26.5f + 4f, -32f); c.drawOval(oval, fill)
            outline.strokeWidth = 1.4f; c.drawOval(oval, outline); outline.strokeWidth = OW
            fill.color = ZARI; c.drawCircle(s * 26.5f, -29.5f, 1.8f, fill); fill.color = SKIN
        }
        // the face
        if (faceShader == null) faceShader = RadialGradient(-6f, -46f, 40f, intArrayOf(SKIN_LIGHT, SKIN, SKIN_SHADE), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
        fill.shader = faceShader; c.drawPath(faceOval, fill); fill.shader = null
        outline.strokeWidth = 2f; c.drawPath(faceOval, outline); outline.strokeWidth = OW
        // fringe: soft side-swept bangs
        fill.color = HAIR
        tmp.reset(); tmp.moveTo(-28f, -40f)
        tmp.cubicTo(-30f, -64f, -12f, -72f, 4f, -70f)
        tmp.cubicTo(20f, -69f, 30f, -58f, 28f, -40f)
        tmp.quadTo(25f, -49f, 18f, -51f)
        tmp.quadTo(13f, -46f, 7f, -48f)
        tmp.quadTo(1f, -52f, -5f, -48.5f)
        tmp.quadTo(-12f, -45.5f, -18f, -51f)
        tmp.quadTo(-25f, -48f, -28f, -40f); tmp.close()
        c.drawPath(tmp, fill); outline.strokeWidth = 1.6f; c.drawPath(tmp, outline); outline.strokeWidth = OW
        stroke.shader = null; stroke.color = HAIR_SHINE; stroke.strokeWidth = 1.6f
        tmp.reset(); tmp.moveTo(-14f, -64f); tmp.quadTo(-4f, -67.5f, 8f, -66f); c.drawPath(tmp, stroke)
        // a small bindi
        if (has("bindi")) { fill.color = BINDI; c.drawCircle(0f + p.lookX * 0.3f, -43.5f, 1.5f, fill) }
    }

    override fun drawCheeks(c: Canvas, p: Pose) {
        val a = (90 + 120 * p.blush.coerceIn(0f, 1f)).toInt()
        fill.shader = null; fill.color = CHEEK; fill.alpha = a
        oval.set(-22f, -26.5f, -12.5f, -21f); c.drawOval(oval, fill)
        oval.set(12.5f, -26.5f, 22f, -21f); c.drawOval(oval, fill)
        fill.alpha = 255
    }

    override fun drawEyes(c: Canvas, p: Pose) {
        for (s in intArrayOf(-1, 1)) {
            val ex = s * 10.5f + p.lookX * 0.8f
            val ey = -33.5f + p.lookY * 0.8f
            val closed = (s < 0 && p.wink > 0.5f)
            if (p.eyesHappy > 0.5f || closed) {   // happy squint: a curved lid with lashes
                stroke.shader = null; stroke.color = LASH; stroke.strokeWidth = 2.2f
                tmp.reset(); tmp.moveTo(ex - 6f, ey + 1.5f); tmp.quadTo(ex, ey - 5.5f, ex + 6f, ey + 1.5f); c.drawPath(tmp, stroke)
                stroke.strokeWidth = 1.3f; c.drawLine(ex + s * 5.5f, ey, ex + s * 8f, ey - 2f, stroke)
                continue
            }
            val open = p.eyeOpen
            val ry = 7.8f * open
            if (ry < 1.4f) {   // blink: a closed lid with lashes
                stroke.shader = null; stroke.color = LASH; stroke.strokeWidth = 2f
                tmp.reset(); tmp.moveTo(ex - 6f, ey); tmp.quadTo(ex, ey + 2.5f, ex + 6f, ey); c.drawPath(tmp, stroke)
                stroke.strokeWidth = 1.2f
                for (k in 0..2) { val lx = ex + s * (2f + k * 2f); c.drawLine(lx, ey + 1.6f, lx + s * 1.2f, ey + 4f, stroke) }
                continue
            }
            val rx = 6.1f * (if (open > 1f) 1f + (open - 1f) * 0.5f else 1f)
            // white
            fill.shader = null; fill.color = Color.WHITE
            oval.set(ex - rx, ey - ry, ex + rx, ey + ry); c.drawOval(oval, fill)
            // big brown iris + pupil
            fill.color = EYE_IRIS
            val ir = rx * 0.82f
            oval.set(ex - ir, ey - ry * 0.86f, ex + ir, ey + ry * 0.92f); c.drawOval(oval, fill)
            fill.color = Color.rgb(0x7A, 0x4A, 0x2A); fill.alpha = 150
            oval.set(ex - ir * 0.75f, ey + ry * 0.05f, ex + ir * 0.75f, ey + ry * 0.85f); c.drawOval(oval, fill); fill.alpha = 255
            fill.color = Color.rgb(0x10, 0x08, 0x06); c.drawCircle(ex, ey + 0.4f, ir * 0.45f, fill)
            // sparkly highlights
            fill.color = Color.WHITE
            c.drawCircle(ex + s * 0.4f + 1.9f, ey - ry * 0.38f, 2.1f, fill)
            c.drawCircle(ex - 1.8f, ey + ry * 0.42f, 1.0f, fill)
            fill.alpha = 200; c.drawCircle(ex + 2.6f, ey + ry * 0.2f, 0.6f, fill); fill.alpha = 255
            // upper lid line + long lashes flicking out
            stroke.shader = null; stroke.color = LASH; stroke.strokeWidth = 2.1f
            oval.set(ex - rx - 0.4f, ey - ry - 0.4f, ex + rx + 0.4f, ey + ry + 0.4f)
            c.drawArc(oval, 195f, 150f, false, stroke)
            stroke.strokeWidth = 1.4f
            val lx0 = ex + s * rx * 0.85f; val ly0 = ey - ry * 0.45f
            c.drawLine(lx0, ly0, lx0 + s * 3.4f, ly0 - 2.6f, stroke)
            c.drawLine(lx0 - s * 1.6f, ly0 - ry * 0.35f, lx0 + s * 1.4f, ly0 - ry * 0.35f - 3.2f, stroke)
            c.drawLine(lx0 - s * 4f, ey - ry - 0.2f, lx0 - s * 2.6f, ey - ry - 3.4f, stroke)
            // lower lash hint
            stroke.strokeWidth = 0.9f; stroke.alpha = 120
            c.drawArc(oval, 30f, 120f, false, stroke); stroke.alpha = 255
        }
    }

    override fun drawBrows(c: Canvas, p: Pose) {
        stroke.shader = null; stroke.color = HAIR; stroke.strokeWidth = 1.7f
        for (s in intArrayOf(-1, 1)) {
            c.save(); c.translate(s * 10.5f + p.lookX * 0.6f, -44.5f + p.browY * 0.7f); c.rotate(-s * p.browAngle * 0.8f)
            tmp.reset(); tmp.moveTo(-4.2f, 1f); tmp.quadTo(0f, -1.6f, 4.2f, 0.6f); c.drawPath(tmp, stroke)
            c.restore()
        }
    }

    override fun drawNose(c: Canvas, p: Pose) {
        stroke.shader = null; stroke.color = SKIN_SHADE; stroke.strokeWidth = 1.4f
        tmp.reset(); tmp.moveTo(p.lookX * 0.5f - 1f, -25.5f); tmp.quadTo(p.lookX * 0.5f + 1.2f, -23.8f, p.lookX * 0.5f + 2f, -24.6f)
        c.drawPath(tmp, stroke)
    }

    override fun drawMouth(c: Canvas, p: Pose) {
        val mx = p.lookX * 0.5f; val my = -18f
        val smile = p.mouthSmile.coerceIn(-1f, 1f)
        if (p.mouthOpen > 0.15f) {
            val hh = 1.5f + 6f * p.mouthOpen.coerceAtMost(1.2f)
            val w = 4.2f + 1.5f * kotlin.math.max(0f, smile)
            tmp.reset(); tmp.moveTo(mx - w, my - smile)
            tmp.quadTo(mx, my + 0.6f, mx + w, my - smile)
            tmp.cubicTo(mx + w * 0.8f, my + hh, mx - w * 0.8f, my + hh, mx - w, my - smile); tmp.close()
            fill.shader = null; fill.color = Color.rgb(0x8E, 0x22, 0x30); c.drawPath(tmp, fill)
            c.save(); c.clipPath(tmp); fill.color = Color.rgb(0xFF, 0x8A, 0x9A); oval.set(mx - 3f, my + hh * 0.45f, mx + 3f, my + hh * 1.3f); c.drawOval(oval, fill); c.restore()
            outline.strokeWidth = 1.4f; c.drawPath(tmp, outline); outline.strokeWidth = OW
        } else {
            stroke.shader = null; stroke.color = LIP; stroke.strokeWidth = 1.8f
            tmp.reset(); tmp.moveTo(mx - 4.5f, my - 1.8f * smile); tmp.quadTo(mx, my + 3.6f * smile, mx + 4.5f, my - 1.8f * smile)
            c.drawPath(tmp, stroke)
        }
    }

    /** In front: the phuljhadi sparkler in her right hand (procedural sparks, no RNG). */
    override fun drawFront(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val k = maxOf(p.sparkler, p.rgReach * 0.9f).coerceIn(0f, 1f)
        if (k < 0.03f) return
        rightHand(pts)
        val hx = pts[0]; val hy = pts[1]
        val s = rig.scale
        val down = p.rgReach > p.sparkler
        val tipX = hx + (if (down) WIRE_X * k else 4f) * s * rig.facingSign; val tipY = hy + (if (down) WIRE_Y else -26f) * s * k
        stroke.shader = null; stroke.color = Color.rgb(0x6A, 0x6A, 0x70); stroke.strokeWidth = 1.4f * s
        c.drawLine(hx, hy, tipX, tipY, stroke)
        val frame = (t * 40f).toInt()
        for (i in 0 until 16) {
            val a = h(frame * 7 + i) * 6.283f; val r = (3f + h(frame + i * 31) * 13f) * s * k
            val ex = tipX + cos(a) * r; val ey = tipY + sin(a) * r
            stroke.color = if (i % 4 == 0) Color.WHITE else if (i % 4 == 1) GOLD else FLAME_HOT
            stroke.strokeWidth = 1.0f * s; stroke.alpha = (230 * k).toInt()
            c.drawLine(tipX + cos(a) * r * 0.35f, tipY + sin(a) * r * 0.35f, ex, ey, stroke)
        }
        fill.shader = null; fill.color = Color.WHITE; fill.alpha = (255 * k).toInt(); c.drawCircle(tipX, tipY, 2.2f * s, fill)
        fill.color = GOLD; fill.alpha = (90 * k).toInt(); c.drawCircle(tipX, tipY, 8f * s * (0.8f + 0.2f * abs(sin(t * 30f))), fill)
        fill.alpha = 255; stroke.alpha = 255
    }

    @Suppress("unused") private fun len(x: Float, y: Float) = sqrt(x * x + y * y)
}

/** Skye's edge-tab face: a slim oval face, pigtails with ribbons, lashes, a bindi and a sparkle. */
object RocketGirlPeek : PeekFace("rocketgirl") {
    override fun drawFace(c: Canvas, wear: Wear?, s: State) {
        val tw = s.tw; val cy = s.cy
        val bob = if (s.peeking) sin(s.t * 8f) * 0.06f * tw else 0f
        // pigtails + ribbons
        fill(RocketGirlRenderer.HAIR)
        c.drawCircle(-0.5f * tw, cy - 0.15f * tw + bob, 0.2f * tw, paint)
        c.drawCircle(0.5f * tw, cy - 0.15f * tw + bob, 0.2f * tw, paint)
        fill(RocketGirlRenderer.RIBBON)
        c.drawCircle(-0.45f * tw, cy - 0.4f * tw + bob, 0.09f * tw, paint)
        c.drawCircle(0.45f * tw, cy - 0.4f * tw + bob, 0.09f * tw, paint)
        // face (a soft oval)
        fill(RocketGirlRenderer.SKIN)
        r.set(-0.44f * tw, cy - 0.55f * tw + bob, 0.44f * tw, cy + 0.5f * tw + bob); c.drawOval(r, paint)
        // fringe
        fill(RocketGirlRenderer.HAIR)
        path.reset(); path.moveTo(-0.46f * tw, cy - 0.05f * tw + bob); path.quadTo(-0.4f * tw, cy - 0.7f * tw + bob, 0.1f * tw, cy - 0.62f * tw + bob)
        path.quadTo(0.48f * tw, cy - 0.55f * tw + bob, 0.46f * tw, cy - 0.05f * tw + bob); path.quadTo(0.2f * tw, cy - 0.35f * tw + bob, -0.46f * tw, cy - 0.05f * tw + bob); c.drawPath(path, paint)
        for (i in 0..1) {
            val y = eyeY(s, i) + bob
            if (s.blink) { fill(RocketGirlRenderer.LASH); c.drawRect(0.16f * tw, y - 0.6f * s.density, 0.34f * tw, y + 0.6f * s.density, paint); continue }
            fill(RocketGirlRenderer.EYE_IRIS); c.drawCircle(0.25f * tw + s.look * 0.5f, y, 0.09f * tw, paint)
            fill(Color.WHITE); c.drawCircle(0.28f * tw + s.look * 0.5f, y - 0.035f * tw, 0.032f * tw, paint)
            fill(RocketGirlRenderer.LASH); c.drawRect(0.33f * tw + s.look * 0.5f, y - 0.11f * tw, 0.36f * tw + s.look * 0.5f, y - 0.06f * tw, paint)
        }
        fill(RocketGirlRenderer.CHEEK); c.drawCircle(0.3f * tw, cy + 0.2f * tw + bob, 0.07f * tw, paint)
        fill(RocketGirlRenderer.BINDI); c.drawCircle(0.05f * tw, cy - 0.32f * tw + bob, 0.035f * tw, paint)
        fill(RocketGirlRenderer.GOLD); c.drawCircle(0.5f * tw, cy - 0.75f * tw, 0.06f * tw * (1f + 0.4f * sin(maxOf(0f, s.t) * 12f)), paint)
    }
}
