package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.LEG
import kotlin.math.max
import kotlin.math.sin

/**
 * v10.1 Gajju: a chibi baby elephant on the shared biped rig. Big dome head, huge flappy ears, big glossy baby eyes,
 * a short curly trunk, a hair tuft, a chubby body on stubby legs with big round feet, and white bird wings spread
 * out sideways (fast flaps in the air, lazy near the floor). Soft dark-plum outlines; no cheek blush.
 */
class GajjuRenderer : MonkeyRenderer() {
    companion object {
        val SKIN = Color.rgb(0x9F, 0x9C, 0xDB)
        val SKIN_LIGHT = Color.rgb(0xC3, 0xC1, 0xF0)
        val SKIN_DARK = Color.rgb(0x7B, 0x77, 0xC0)
        val EAR_IN = Color.rgb(0xF7, 0xB9, 0xCB)
        val EAR_IN_DARK = Color.rgb(0xEA, 0x95, 0xB0)
        val SOLE = Color.rgb(0xF4, 0xA5, 0xC4)
        val INK = Color.rgb(0x2B, 0x22, 0x48)
        val EYE_DARK = Color.rgb(0x1E, 0x18, 0x36)
        val EYE_IRIS = Color.rgb(0x3A, 0x30, 0x66)
        val EYE_GLOW = Color.rgb(0x6A, 0x5C, 0xA8)
        val TOE = Color.rgb(0xFF, 0xFF, 0xFF)
        val WING = Color.rgb(0xFF, 0xFF, 0xFF)
        val WING_TIP = Color.rgb(0xE6, 0xDF, 0xFF)
        val FEATHER = Color.rgb(0xBF, 0xB2, 0xF2)
        val BOW = Color.rgb(0xFF, 0x7E, 0xB6)
        val BOW_DOT = Color.rgb(0xFF, 0xF0, 0xF6)
        val CROWN = Color.rgb(0xFF, 0xCF, 0x3D)

        /** Big chibi head (head space): a slightly wide dome. */
        const val HEAD_X = 41f
        const val HEAD_TOP = -78f
        const val HEAD_BOTTOM = 2f
        /** Chibi: the head is drawn this much bigger than the shared rig's. */
        const val HEAD_SCALE = 1.28f
        /** Trunk tip (head space, before [HEAD_SCALE]) when fully raised to spray water. */
        const val SPRAY_TIP_X = 32f
        const val SPRAY_TIP_Y = -9f
    }

    init { outline.color = INK }

    /** v10.11: his big head + flappy ears (out to x 72) + tuft, scaled by [HEAD_SCALE] like he draws them. */
    override val faceBox: FloatArray = floatArrayOf(-76f * HEAD_SCALE, -94f * HEAD_SCALE, 76f * HEAD_SCALE, 12f * HEAD_SCALE)

    private val headShader = RadialGradient(-14f, -56f, 64f, intArrayOf(SKIN_LIGHT, SKIN, SKIN_DARK), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
    private val bodyShader = RadialGradient(-10f, -40f, 54f, intArrayOf(SKIN_LIGHT, SKIN, SKIN_DARK), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    private val wingShader = LinearGradient(20f, 0f, 108f, 0f, WING, WING_TIP, Shader.TileMode.CLAMP)

    /** The big dome head: wide cheeks, round crown. */
    private val head = Path().apply {
        moveTo(0f, HEAD_TOP)
        cubicTo(26f, HEAD_TOP, HEAD_X, -60f, HEAD_X, -38f)
        cubicTo(HEAD_X, -14f, 26f, HEAD_BOTTOM, 0f, HEAD_BOTTOM)
        cubicTo(-26f, HEAD_BOTTOM, -HEAD_X, -14f, -HEAD_X, -38f)
        cubicTo(-HEAD_X, -60f, -26f, HEAD_TOP, 0f, HEAD_TOP)
        close()
    }

    /** Right ear (head space, attached at the head's side): a big rounded D that hangs down to the cheeks. */
    private val ear = Path().apply {
        moveTo(30f, -64f)
        cubicTo(46f, -74f, 68f, -64f, 69f, -40f)
        cubicTo(70f, -16f, 56f, 2f, 38f, -3f)
        cubicTo(33f, -5f, 30f, -10f, 31f, -18f)
        close()
    }
    private val earInner = Path().apply {
        moveTo(36f, -59f)
        cubicTo(48f, -66f, 62f, -58f, 62f, -40f)
        cubicTo(63f, -22f, 54f, -8f, 42f, -10f)
        cubicTo(38f, -12f, 37f, -16f, 37f, -22f)
        close()
    }

    /** Short, round chibi tummy (hips space). */
    private val torso = Path().apply {
        moveTo(0f, -54f)
        cubicTo(20f, -54f, 34f, -36f, 33f, -14f)
        cubicTo(32f, 6f, 18f, 13f, 0f, 13f)
        cubicTo(-18f, 13f, -32f, 6f, -33f, -14f)
        cubicTo(-34f, -36f, -20f, -54f, 0f, -54f)
        close()
    }

    /**
     * Right bird wing, spread sideways (root at the origin, +x = outward, leading edge on top): a curved arm to the
     * wrist, four long separate flight feathers at the tip, and scalloped secondary feathers along the trailing edge.
     */
    private val wing = Path().apply {
        moveTo(0f, -2f)
        cubicTo(18f, -20f, 52f, -30f, 78f, -27f)          // leading edge up to the wrist
        quadTo(96f, -27f, 108f, -21f)                      // primary 1 (longest)
        quadTo(98f, -15f, 90f, -14f)
        quadTo(102f, -10f, 106f, -4f)                      // primary 2
        quadTo(94f, -2f, 86f, -3f)
        quadTo(96f, 3f, 98f, 10f)                          // primary 3
        quadTo(86f, 8f, 78f, 5f)
        quadTo(84f, 13f, 84f, 20f)                         // primary 4
        quadTo(72f, 15f, 66f, 10f)
        quadTo(62f, 20f, 52f, 20f)                         // secondaries: rounded scallops back to the body
        quadTo(48f, 12f, 44f, 12f)
        quadTo(38f, 21f, 28f, 18f)
        quadTo(25f, 11f, 21f, 11f)
        quadTo(14f, 17f, 4f, 12f)
        close()
    }
    /** Covert line (where the small top feathers overlap the flight feathers) and feather separations. */
    private val wingLines = arrayOf(
        floatArrayOf(6f, 2f, 40f, -14f, 80f, -16f),
        floatArrayOf(78f, -18f, 92f, -18f, 100f, -19f),
        floatArrayOf(74f, -9f, 88f, -8f, 98f, -6f),
        floatArrayOf(70f, -1f, 82f, 2f, 90f, 6f),
        floatArrayOf(40f, -6f, 44f, 4f, 46f, 11f),
        floatArrayOf(24f, -2f, 25f, 5f, 24f, 10f),
    )

    private fun flapDeg(): Float {
        val airborne = pose.shadow < 0.05f
        val strain = pose.dvAura.coerceIn(0f, 1f)   // v10.26 swipe flight: frantic flapping, he's heavy
        val hz = if (airborne) 10f + 9f * strain else 3.5f
        val amp = if (airborne) 20f + 10f * strain else 9f
        return amp * sin(now * hz)
    }

    /** First hook of every frame: re-assert the plum outline (shared effects like soot reset it to brown). */
    override fun drawBehind(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) { outline.color = INK; drawWings(c, rig) }

    /** Bird wings spread out sideways from the upper back, flapping together (fast in the air, lazy near the floor). */
    private fun drawWings(c: Canvas, rig: MonkeyRig) {
        val a = flapDeg()
        c.save(); c.concat(rig.hips)
        for (s in intArrayOf(-1, 1)) {
            c.save()
            c.translate(s * 18f, -40f); c.scale(s.toFloat(), 1f); c.rotate(8f - a)
            fill.shader = wingShader; c.drawPath(wing, fill); fill.shader = null
            stroke.shader = null; stroke.color = FEATHER; stroke.strokeWidth = 2.2f
            for (l in wingLines) { tmp.reset(); tmp.moveTo(l[0], l[1]); tmp.quadTo(l[2], l[3], l[4], l[5]); c.drawPath(tmp, stroke) }
            outline.strokeWidth = 2.6f; c.drawPath(wing, outline); outline.strokeWidth = OW
            c.restore()
        }
        c.restore()
    }

    /** A chubby limb with the plum outline (the base helper uses the monkey's brown). */
    private fun eLimb(c: Canvas, m: Matrix, len: Float, w: Float) {
        c.save(); c.concat(m)
        stroke.shader = null
        stroke.color = INK; stroke.strokeWidth = w + 2 * OW; c.drawLine(0f, 0f, 0f, len, stroke)
        stroke.color = SKIN; stroke.strokeWidth = w; c.drawLine(0f, 0f, 0f, len, stroke)
        c.restore()
    }

    /** Short stubby legs ending in big round feet with pink pads and three white toenails. */
    override fun drawLegs(c: Canvas, rig: MonkeyRig) {
        val legLen = LEG * 0.55f
        eLimb(c, rig.legL, legLen, 25f); eLimb(c, rig.legR, legLen, 25f)
        for (leg in arrayOf(rig.legL, rig.legR)) {
            c.save(); c.concat(leg)
            val fy = legLen + 6f
            fill.shader = null; fill.color = SKIN
            oval.set(-15f, fy - 12f, 15f, fy + 12f); c.drawOval(oval, fill); c.drawOval(oval, outline)
            fill.color = SOLE; oval.set(-9.5f, fy - 7.5f, 9.5f, fy + 6.5f); c.drawOval(oval, fill)
            fill.color = TOE
            for (i in -1..1) { oval.set(i * 6.5f - 3.3f, fy + 6.5f, i * 6.5f + 3.3f, fy + 11.5f); c.drawOval(oval, fill); c.drawOval(oval, outline) }
            c.restore()
        }
    }

    override fun drawTorso(c: Canvas, rig: MonkeyRig) {
        c.save(); c.concat(rig.hips)
        fill.shader = bodyShader; c.drawPath(torso, fill); fill.shader = null
        c.drawPath(torso, outline)
        c.restore()
    }

    override fun drawTorsoExtras(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {}

    /** Fat little arms with round paws. */
    override fun drawArms(c: Canvas, rig: MonkeyRig) {
        eLimb(c, rig.armLUpper, rig.upperLen, 17f); eLimb(c, rig.armLFore, rig.foreLen * 0.75f, 16f)
        eLimb(c, rig.armRUpper, rig.upperLen, 17f); eLimb(c, rig.armRFore, rig.foreLen * 0.75f, 16f)
        blob(c, rig.armLFore, 0f, rig.foreLen * 0.75f + 2f, 9.5f, 9f, SKIN)
        blob(c, rig.armRFore, 0f, rig.foreLen * 0.75f + 2f, 9.5f, 9f, SKIN)
    }

    /** A thin little tail with a dark tuft at the end. */
    override fun drawTail(c: Canvas, rig: MonkeyRig) {
        val tp = rig.tailPts
        val n = 3
        tmp.reset(); tmp.moveTo(tp[0], tp[1])
        for (i in 1..n) tmp.lineTo(tp[2 * i], tp[2 * i + 1])
        stroke.shader = null
        stroke.color = INK; stroke.strokeWidth = 4f + 2 * OW; c.drawPath(tmp, stroke)
        stroke.color = SKIN; stroke.strokeWidth = 4f; c.drawPath(tmp, stroke)
        fill.shader = null; fill.color = INK
        c.drawCircle(tp[2 * n], tp[2 * n + 1], 4.5f, fill)
    }

    private fun drawEars(c: Canvas) {
        val ae = anim
        val flap = if (ae == null) 0.1f * sin(now * 3.6f)
            else 0.1f * sin(now * 3.6f) + ae.appendageAngle(com.combo.monkeycompanion.engine.anim.Appendage.EARS) * 0.006f
        val flapFast = 0.22f * pose.dvAura.coerceIn(0f, 1f) * sin(now * 24f)   // v10.24 swipe: big ears flap fast like wings
        for (s in intArrayOf(-1, 1)) {
            c.save(); c.scale(s.toFloat(), 1f)
            c.translate(30f, -34f); c.scale(1f + flap + flapFast, 1f - (flap + flapFast) * 0.4f); c.translate(-30f, 34f)
            fill.shader = null; fill.color = SKIN
            c.drawPath(ear, fill); c.drawPath(ear, outline)
            fill.color = EAR_IN; c.drawPath(earInner, fill)
            stroke.shader = null; stroke.color = EAR_IN_DARK; stroke.strokeWidth = 2.4f
            tmp.reset(); tmp.moveTo(44f, -52f); tmp.quadTo(54f, -38f, 47f, -18f); c.drawPath(tmp, stroke)
            c.restore()
        }
    }

    /**
     * Chibi head: everything drawn in head space is scaled up around the neck from here on (the shared head pass
     * restores the canvas after the last head hook), so eyes, trunk, brows and hats all grow with it.
     */
    override fun drawHeadBase(c: Canvas, p: Pose, t: Float) {
        c.scale(HEAD_SCALE, HEAD_SCALE)
        drawHeadShape(c)
    }

    private fun drawHeadShape(c: Canvas) {
        drawEars(c)
        fill.shader = headShader; c.drawPath(head, fill); fill.shader = null
        c.drawPath(head, outline)
        // Hair tuft: three little curls on top
        stroke.shader = null; stroke.color = INK; stroke.strokeWidth = 2.4f
        for (i in -1..1) {
            tmp.reset(); tmp.moveTo(i * 3f, HEAD_TOP + 1f)
            tmp.quadTo(i * 7f - 2f, HEAD_TOP - 9f, i * 9f + 3f, HEAD_TOP - 11f + kotlin.math.abs(i) * 3f)
            c.drawPath(tmp, stroke)
        }
    }

    /** No cheek blush on Gajju (owner's call): a clean face. */
    override fun drawCheeks(c: Canvas, p: Pose) {}

    /** Huge glossy baby eyes set low on the face: dark eye, a lighter purple glow below, three sparkles, a clean rim. */
    override val glintEye: FloatArray? = floatArrayOf(18f, -34f, 1.4f)

    override fun drawEyes(c: Canvas, p: Pose) {
        for (s in intArrayOf(-1, 1)) {
            val ex = s * 18f + p.lookX * 0.9f
            val ey = -34f + p.lookY * 0.8f
            if ((s < 0 && p.wink > 0.5f) || p.eyesHappy > 0.5f) {
                stroke.shader = null; stroke.color = EYE_DARK; stroke.strokeWidth = 3.2f
                tmp.reset(); tmp.moveTo(ex - 9f, ey + 3f); tmp.quadTo(ex, ey - 9f, ex + 9f, ey + 3f)
                c.drawPath(tmp, stroke)
                continue
            }
            val open = p.eyeOpen
            val big = if (open > 1f) 1f + (open - 1f) * 0.3f else 1f
            val ry = 15f * open.coerceAtMost(1f) * big
            if (ry < 2f) {
                stroke.shader = null; stroke.color = EYE_DARK; stroke.strokeWidth = 3f
                tmp.reset(); tmp.moveTo(ex - 9f, ey); tmp.quadTo(ex, ey + 5f, ex + 9f, ey); c.drawPath(tmp, stroke)
                continue
            }
            val rx = 13f * big
            fill.shader = null; fill.color = EYE_DARK
            oval.set(ex - rx, ey - ry, ex + rx, ey + ry); c.drawOval(oval, fill)
            fill.color = EYE_IRIS
            oval.set(ex - rx * 0.78f, ey + ry * 0.02f, ex + rx * 0.78f, ey + ry * 0.94f); c.drawOval(oval, fill)
            fill.color = EYE_GLOW
            oval.set(ex - rx * 0.5f, ey + ry * 0.45f, ex + rx * 0.5f, ey + ry * 0.9f); c.drawOval(oval, fill)
            fill.color = Color.WHITE
            c.drawCircle(ex + rx * 0.3f, ey - ry * 0.36f, 5.4f * big, fill)
            c.drawCircle(ex - rx * 0.42f, ey + ry * 0.36f, 2.6f * big, fill)
            c.drawCircle(ex + rx * 0.55f, ey + ry * 0.05f, 1.4f * big, fill)
            outline.strokeWidth = 2.4f
            oval.set(ex - rx, ey - ry, ex + rx, ey + ry); c.drawOval(oval, outline)
            outline.strokeWidth = OW
        }
    }

    /** Tiny soft brows high on the forehead. */
    override fun drawBrows(c: Canvas, p: Pose) {
        stroke.shader = null; stroke.color = INK; stroke.strokeWidth = 2f
        for (s in intArrayOf(-1, 1)) {
            c.save()
            c.translate(s * 20f + p.lookX * 0.7f, -59f + p.browY)
            c.rotate(-s * p.browAngle)
            tmp.reset(); tmp.moveTo(-4.5f, 1.5f); tmp.quadTo(0f, -2f, 4.5f, 1.5f); c.drawPath(tmp, stroke)
            c.restore()
        }
    }

    /** 0..1: how far the trunk is raised to spray water (set by the overlay during the belly-poke spray). */
    var spray = 0f

    /** A short chubby trunk from just under the eyes, curling up to the side (raised high while spraying). */
    override fun drawNose(c: Canvas, p: Pose) {
        val k = spray.coerceIn(0f, 1f)
        val ae = anim
        val sway = if (ae == null) (2.5f * sin(now * 2.1f) + p.lookX * 0.5f) * (1f - k)
            else (2.5f * sin(now * 2.1f) + p.lookX * 0.5f + ae.appendageAngle(com.combo.monkeycompanion.engine.anim.Appendage.TRUNK) * 0.35f) * (1f - k)
        val curl = 2.5f * sin(now * 1.6f + 1f) * (1f - k)
        val tipX = 12f + sway + (SPRAY_TIP_X - 12f) * k; val tipY = -9f - curl + (SPRAY_TIP_Y + 9f) * k
        tmp.reset()
        tmp.moveTo(p.lookX * 0.5f, -24f)
        tmp.cubicTo(0f, -14f, 1f, -4f, 6f + sway * 0.4f, -3f)
        tmp.quadTo(11f + sway, -2f, tipX, tipY)
        stroke.shader = null
        stroke.color = INK; stroke.strokeWidth = 14f + 2 * OW; c.drawPath(tmp, stroke)
        stroke.color = SKIN; stroke.strokeWidth = 14f; c.drawPath(tmp, stroke)
        stroke.color = SKIN_DARK; stroke.strokeWidth = 1.8f
        c.drawLine(-4.5f, -16f, 4.5f, -15f, stroke)
        c.drawLine(-4f, -11f, 4.5f, -9f, stroke)
        fill.shader = null; fill.color = SKIN_LIGHT; c.drawCircle(tipX, tipY, 4.6f, fill)
    }

    /** A small plum smile beside the trunk; a round pink "o" when the mouth opens. */
    override fun drawMouth(c: Canvas, p: Pose) {
        val mx = -12f + p.lookX * 0.4f; val my = -5f
        val smile = p.mouthSmile.coerceIn(-1f, 1f)
        if (p.mouthOpen > 0.15f) {
            val h = 3f + 6f * p.mouthOpen.coerceAtMost(1.2f)
            fill.shader = null; fill.color = Color.rgb(0x8A, 0x2E, 0x55)
            oval.set(mx - 4.5f, my - 1f, mx + 4.5f, my - 1f + h); c.drawOval(oval, fill)
            fill.color = EAR_IN; oval.set(mx - 3f, my - 1f + h * 0.5f, mx + 3f, my - 1f + h); c.drawOval(oval, fill)
            outline.strokeWidth = 1.8f; oval.set(mx - 4.5f, my - 1f, mx + 4.5f, my - 1f + h); c.drawOval(oval, outline); outline.strokeWidth = OW
        } else {
            stroke.shader = null; stroke.color = INK; stroke.strokeWidth = 2.4f
            tmp.reset(); tmp.moveTo(mx - 6f, my - 1f); tmp.quadTo(mx, my + 5f * smile.coerceAtLeast(0.25f), mx + 5f, my - 2f)
            c.drawPath(tmp, stroke)
        }
    }

    override fun drawHeadExtras(c: Canvas, p: Pose, t: Float) {
        if (has("crown")) {
            fill.shader = null; fill.color = CROWN
            tmp.reset()
            tmp.moveTo(-14f, HEAD_TOP + 6f); tmp.lineTo(-14f, HEAD_TOP - 6f); tmp.lineTo(-7f, HEAD_TOP); tmp.lineTo(0f, HEAD_TOP - 10f)
            tmp.lineTo(7f, HEAD_TOP); tmp.lineTo(14f, HEAD_TOP - 6f); tmp.lineTo(14f, HEAD_TOP + 6f); tmp.close()
            c.drawPath(tmp, fill); c.drawPath(tmp, outline)
        } else if (has("bow")) {
            fill.shader = null; fill.color = BOW
            val bx = 24f; val by = -68f
            for (s in intArrayOf(-1, 1)) {
                tmp.reset()
                tmp.moveTo(bx, by); tmp.lineTo(bx + s * 11f, by - 8f); tmp.lineTo(bx + s * 11f, by + 8f); tmp.close()
                c.drawPath(tmp, fill); c.drawPath(tmp, outline)
            }
            c.drawCircle(bx, by, 4f, fill); c.drawCircle(bx, by, 4f, outline)
            fill.color = BOW_DOT
            c.drawCircle(bx - 7f, by - 2f, 1.5f, fill); c.drawCircle(bx + 7f, by + 2f, 1.5f, fill)
        }
    }

    override fun drawBack(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        outline.color = INK
        drawWings(c, rig)
        drawLegs(c, rig)
        c.save(); c.concat(rig.hips)
        fill.shader = bodyShader; c.drawPath(torso, fill); fill.shader = null
        c.drawPath(torso, outline)
        c.restore()
        c.save(); c.concat(rig.head); c.scale(HEAD_SCALE, HEAD_SCALE)
        drawHeadShape(c)
        c.restore()
        drawArms(c, rig)
        drawTail(c, rig)
    }
}

/** Gajju peeking out of the edge tab: lavender head, a big pink ear, one shiny baby eye pair and the curly trunk. */
object GajjuPeek : PeekFace("gajju") {
    override fun drawFace(c: Canvas, wear: Wear?, s: State) {
        val tw = s.tw; val cy = s.cy
        val flap = if (s.peeking) sin(s.t * 6f) * 0.1f * tw else 0f
        // big ear behind the head
        fill(GajjuRenderer.SKIN); r.set(-0.85f * tw, cy - 1.05f * tw - flap, 0.2f * tw, cy + 0.05f * tw); c.drawOval(r, paint)
        fill(GajjuRenderer.EAR_IN); r.inset(0.17f * tw, 0.17f * tw); c.drawOval(r, paint)
        fill(GajjuRenderer.SKIN); c.drawCircle(0f, cy, tw * 0.62f, paint)
        // curly trunk pointing into the screen
        stroke(GajjuRenderer.SKIN, 0.24f * tw)
        path.reset(); path.moveTo(0.35f * tw, cy + 0.12f * tw)
        path.quadTo(0.85f * tw, cy + 0.2f * tw, 0.78f * tw, cy - 0.12f * tw + flap)
        c.drawPath(path, paint)
        // big shiny eyes
        for (i in 0..1) {
            val y = eyeY(s, i)
            if (s.blink) { fill(GajjuRenderer.EYE_DARK); c.drawRect(0.2f * tw, y - 0.6f * s.density, 0.36f * tw, y + 0.6f * s.density, paint); continue }
            fill(GajjuRenderer.EYE_DARK); c.drawCircle(0.28f * tw + s.look * 0.5f, y, 0.11f * tw, paint)
            fill(Color.WHITE); c.drawCircle(0.31f * tw + s.look * 0.5f, y - 0.04f * tw, 0.04f * tw, paint)
        }
        if (wear?.has("bow") == true) { fill(GajjuRenderer.BOW); c.drawCircle(-0.3f * tw, cy - 0.58f * tw, 0.13f * tw, paint) }
    }
    override val wardrobeShown = setOf("bow")
}
