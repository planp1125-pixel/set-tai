package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.engine.anim.Appendage
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.LEG
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin

/**
 * v10.14 shared bits for the new characters: whole-body spin (x scale about the hips) and an aerial flip (rotation about
 * the body centre), applied around the whole body between [drawBehind] and [drawFront]. Both read blend-safe pose slots.
 */
abstract class V1014BodyRenderer : HumanRenderer() {
    private val hp = FloatArray(2)
    private var saved = false
    protected fun on(id: String) = wear == null || has(id)

    /** Push the body transform (call first in drawBehind). */
    protected fun pushBody(c: Canvas, rig: MonkeyRig, p: Pose) {
        hp[0] = 0f; hp[1] = -30f; rig.hips.mapPoints(hp)
        val flip = Math.toDegrees(atan2(p.flipS.toDouble(), p.flipC.toDouble())).toFloat()
        val sx = (if (abs(p.spinX) < 0.08f) 0.08f * (if (p.spinX < 0f) -1f else 1f) else p.spinX) * (if (p.hmMoon > 0.5f) -1f else 1f)
        saved = abs(flip) > 0.3f || abs(sx - 1f) > 0.001f
        if (!saved) return
        c.save()
        if (abs(flip) > 0.3f) c.rotate(flip, hp[0], hp[1])
        if (abs(sx - 1f) > 0.001f) c.scale(sx, 1f, hp[0], hp[1])
    }
    /** Pop it (call last in drawFront). */
    protected fun popBody(c: Canvas) { if (saved) { c.restore(); saved = false } }
}

/**
 * v10.14 Echo: an ORIGINAL invisible dancer, only floating clothes. Nothing is drawn where a body would be: the
 * neck, wrists and ankles are empty air (a faint shimmer reads as "someone is there"). Fedora (wobbles a beat behind on
 * the HAIR spring: follow-through), round shades, white gloves, a teal jacket over a white shirt collar, black trousers,
 * shiny loafers with white socks.
 */
class HollowManRenderer : V1014BodyRenderer() {
    companion object {
        val HAT = Color.rgb(0x2B, 0x2B, 0x33)
        val HAT_BAND = Color.rgb(0xD0, 0x24, 0x3A)
        val SHADES = Color.rgb(0x14, 0x14, 0x1C)
        val GLOVE = Color.rgb(0xFA, 0xFA, 0xFF)
        val JACKET = Color.rgb(0x1E, 0x8C, 0x9A)
        val JACKET_DARK = Color.rgb(0x12, 0x5C, 0x66)
        val INSIDE = Color.rgb(0x0C, 0x24, 0x2A)
        val SHIRT = Color.rgb(0xF4, 0xF4, 0xF0)
        val TROUSER = Color.rgb(0x1A, 0x1A, 0x22)
        val SHOE = Color.rgb(0x0E, 0x0E, 0x12)
        val SOCK = Color.rgb(0xF0, 0xF0, 0xF0)
        val GOLD = Color.rgb(0xF5, 0xC4, 0x3A)
        val SHIMMER = Color.argb(70, 0xC8, 0xF0, 0xFF)
    }
    override val skin = Color.TRANSPARENT
    override val skinShade = Color.TRANSPARENT
    override val hair = Color.TRANSPARENT
    override val glintEye: FloatArray? = null
    override val faceBox: FloatArray get() = BOX
    private val BOX = floatArrayOf(-56f, -104f, 56f, 6f)

    override fun drawJets(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) = Unit
    override fun drawBehind(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) { pushBody(c, rig, p) }

    override fun drawLegs(c: Canvas, rig: MonkeyRig) {
        // trousers stop above the ankle; then empty air; then a white sock band and the loafer
        for (m in arrayOf(rig.legL, rig.legR)) { limb(c, m, LEG - 6f, 14f, TROUSER, true) }
        for (m in arrayOf(rig.legL, rig.legR)) { limb(c, m, LEG - 6f, 14f, TROUSER, false) }
        for ((i, m) in arrayOf(rig.legL, rig.legR).withIndex()) {
            c.save(); c.concat(m)
            fill.shader = null; fill.color = INSIDE; oval.set(-6.5f, LEG - 8f, 6.5f, LEG - 4f); c.drawOval(oval, fill)   // the empty trouser hem
            fill.color = SOCK; oval.set(-4.5f, LEG - 1.5f, 4.5f, LEG + 2.5f); c.drawRect(oval, fill)
            fill.color = SHOE
            val s = if (i == 0) -1f else 1f
            tmp.reset(); tmp.moveTo(-6f, LEG + 1.5f); tmp.quadTo(-7f, LEG + 8f, 0f, LEG + 8f); tmp.lineTo(10f * 1f, LEG + 8f)
            tmp.quadTo(13f, LEG + 7f, 11f, LEG + 4f); tmp.quadTo(4f, LEG + 2f, 4f, LEG + 1.5f); tmp.close()
            c.drawPath(tmp, fill); outline.strokeWidth = 1.6f; c.drawPath(tmp, outline); outline.strokeWidth = OW
            fill.color = Color.argb(150, 255, 255, 255); c.drawCircle(6f, LEG + 4.2f, 1.3f + 0.2f * s, fill)   // shine
            c.restore()
        }
    }

    override fun drawTorso(c: Canvas, rig: MonkeyRig) {
        c.save(); c.concat(rig.hips)
        fill.shader = null; fill.color = JACKET; c.drawPath(suit, fill); c.drawPath(suit, outline)
        // the neck hole: you can see INSIDE the collar (nobody there)
        fill.color = INSIDE; oval.set(-12f, -64f, 12f, -54f); c.drawOval(oval, fill)
        // white shirt V + lapels
        fill.color = SHIRT
        tmp.reset(); tmp.moveTo(-10f, -59f); tmp.lineTo(0f, -36f); tmp.lineTo(10f, -59f); tmp.quadTo(0f, -54f, -10f, -59f); tmp.close()
        c.drawPath(tmp, fill); outline.strokeWidth = 1.4f; c.drawPath(tmp, outline)
        fill.color = JACKET_DARK
        tmp.reset(); tmp.moveTo(-12f, -58f); tmp.lineTo(-3f, -36f); tmp.lineTo(-15f, -44f); tmp.close(); c.drawPath(tmp, fill); c.drawPath(tmp, outline)
        tmp.reset(); tmp.moveTo(12f, -58f); tmp.lineTo(3f, -36f); tmp.lineTo(15f, -44f); tmp.close(); c.drawPath(tmp, fill); c.drawPath(tmp, outline)
        outline.strokeWidth = OW
        fill.color = GOLD; c.drawCircle(0f, -28f, 2.2f, fill); c.drawCircle(0f, -18f, 2.2f, fill)
        // the empty waist: belt line
        stroke.shader = null; stroke.color = INSIDE; stroke.strokeWidth = 2f; c.drawLine(-20f, -4f, 20f, -4f, stroke)
        c.restore()
    }

    override fun drawArms(c: Canvas, rig: MonkeyRig) {
        val sleeve = rig.foreLen - 4f
        for ((u, f) in arrayOf(rig.armLUpper to rig.armLFore, rig.armRUpper to rig.armRFore)) {
            limb(c, u, rig.upperLen, 12f, JACKET, true); limb(c, f, sleeve, 11f, JACKET, true)
            limb(c, u, rig.upperLen, 12f, JACKET, false); limb(c, f, sleeve, 11f, JACKET, false)
            c.save(); c.concat(f)
            fill.shader = null; fill.color = INSIDE; oval.set(-5.5f, sleeve - 2f, 5.5f, sleeve + 2f); c.drawOval(oval, fill)   // empty cuff
            c.restore()
            if (on("gloves")) {
                // glove floats just past the cuff: palm + thumb + a white cuff ring (gap = invisible wrist)
                blob(c, f, 0f, rig.foreLen + 4f, 7.5f, 7f, GLOVE)
                blob(c, f, 5.5f, rig.foreLen + 1.5f, 3f, 2.4f, GLOVE)
                c.save(); c.concat(f)
                stroke.shader = null; stroke.color = Color.rgb(0xC8, 0xC8, 0xD4); stroke.strokeWidth = 1.2f
                c.drawLine(-3f, rig.foreLen + 7f, -3f, rig.foreLen + 9.5f, stroke); c.drawLine(0f, rig.foreLen + 7.5f, 0f, rig.foreLen + 10f, stroke)
                c.restore()
            }
        }
    }

    /** No head at all, just a faint shimmer where it would be. */
    override fun drawHeadBase(c: Canvas, p: Pose, t: Float) {
        fill.shader = null; fill.color = SHIMMER
        for (i in 0..2) {
            val a = t * 1.3f + i * 2.1f
            c.drawCircle(18f * sin(a), -34f + 14f * sin(a * 1.7f), 1.6f + 0.8f * sin(a * 3f), fill)
        }
    }
    override fun drawNose(c: Canvas, p: Pose) = Unit
    override fun drawCheeks(c: Canvas, p: Pose) = Unit
    override fun drawBrows(c: Canvas, p: Pose) = Unit
    override fun drawMouth(c: Canvas, p: Pose) {
        // only when he speaks or laughs: a floating grin-glint (an invisible mouth caught in the light)
        val o = p.mouthOpen.coerceIn(0f, 1f)
        if (o < 0.12f && p.mouthSmile < 0.85f) return
        stroke.shader = null; stroke.color = Color.argb(110, 255, 255, 255); stroke.strokeWidth = 1.8f
        tmp.reset(); tmp.moveTo(-6f, -18f); tmp.quadTo(0f, -14f + 5f * o, 6f, -18f); c.drawPath(tmp, stroke)
    }

    /** Round shades float where the eyes would be (they blink by dipping). */
    override fun drawEyes(c: Canvas, p: Pose) {
        if (!on("shades")) return
        val dip = (1f - p.eyeOpen.coerceIn(0f, 1f)) * 3f
        fill.shader = null; fill.color = SHADES
        for (s in intArrayOf(-1, 1)) { c.drawCircle(s * 12f + p.lookX * 0.6f, -40f + dip + p.lookY * 0.5f, 9f, fill); c.drawCircle(s * 12f + p.lookX * 0.6f, -40f + dip + p.lookY * 0.5f, 9f, outline) }
        stroke.shader = null; stroke.color = SHADES; stroke.strokeWidth = 2.4f
        c.drawLine(-3.5f + p.lookX * 0.6f, -41f + dip, 3.5f + p.lookX * 0.6f, -41f + dip, stroke)
        fill.color = Color.argb(170, 255, 255, 255)
        for (s in intArrayOf(-1, 1)) c.drawCircle(s * 12f + p.lookX * 0.6f - 3f, -43.5f + dip, 2.2f, fill)
    }

    override fun drawHeadExtras(c: Canvas, p: Pose, t: Float) {
        super.drawHeadExtras(c, p, t)
        if (!on("hat")) return
        // follow-through: the hat trails the head's motion a beat (HAIR spring) and settles
        val lag = (anim?.appendageAngle(Appendage.HAIR) ?: 0f) * 0.5f
        val tip = p.hmHatTip.coerceIn(0f, 1f)
        c.save()
        c.translate(14f * tip, -62f - 34f * tip)
        c.rotate(-8f + lag - 28f * tip)
        fill.shader = null; fill.color = HAT
        oval.set(-40f, -6f, 40f, 6f); c.drawOval(oval, fill); c.drawOval(oval, outline)                // brim
        tmp.reset(); tmp.moveTo(-24f, 0f); tmp.cubicTo(-26f, -22f, -16f, -30f, 0f, -26f)
        tmp.cubicTo(16f, -30f, 26f, -22f, 24f, 0f); tmp.close()
        c.drawPath(tmp, fill); c.drawPath(tmp, outline)                                                   // crown with a pinch
        fill.color = HAT_BAND; oval.set(-24f, -8f, 24f, -1f); c.drawRect(oval, fill)
        stroke.shader = null; stroke.color = Color.argb(90, 255, 255, 255); stroke.strokeWidth = 2f
        c.drawLine(-14f, -20f, -6f, -24f, stroke)
        c.restore()
    }

    override fun drawFront(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) { popBody(c) }
}

object HollowManPeek : PeekFace("hollowman") {
    override fun drawFace(c: Canvas, wear: Wear?, s: State) {
        val tw = s.tw; val cy = s.cy
        val bob = if (s.peeking) sin(s.t * 8f) * 0.06f * tw else 0f
        // just a floating fedora and shades peeking out of the edge
        fill(HollowManRenderer.HAT)
        r.set(-0.7f * tw, cy - 0.62f * tw + bob, 0.7f * tw, cy - 0.45f * tw + bob); c.drawOval(r, paint)
        r.set(-0.42f * tw, cy - 1.05f * tw + bob, 0.42f * tw, cy - 0.5f * tw + bob); c.drawOval(r, paint)
        fill(HollowManRenderer.HAT_BAND); c.drawRect(-0.42f * tw, cy - 0.66f * tw + bob, 0.42f * tw, cy - 0.56f * tw + bob, paint)
        for (i in 0..1) {
            val y = eyeY(s, i) * 0.6f + cy * 0.4f + bob
            fill(HollowManRenderer.SHADES); c.drawCircle(0.25f * tw + s.look * 0.5f, y, if (s.blink) 0.08f * tw else 0.15f * tw, paint)
        }
    }
}

/**
 * v10.14 Wavy: an ORIGINAL cute little girl (high ponytail + hibiscus, coral rash guard, aqua shorts) standing on
 * a yellow-and-teal surfboard that rides a procedurally drawn wave (layered swell, foam crest, droplets). The spray trail
 * grows with her real screen speed (the anim engine's velocity) and with [Pose.surfSpray]; the ponytail swings on the
 * HAIR spring (follow-through). No images.
 */
class SurferRenderer : V1014BodyRenderer() {
    companion object {
        val SKIN = Color.rgb(0xC4, 0x88, 0x5E)
        val SKIN_SHADE = Color.rgb(0x96, 0x5E, 0x3C)
        val HAIR = Color.rgb(0x2A, 0x18, 0x10)
        val TOP = Color.rgb(0xFF, 0x6F, 0x61)
        val TOP_DARK = Color.rgb(0xC8, 0x48, 0x40)
        val SHORTS = Color.rgb(0x2B, 0xC4, 0xC9)
        val BOARD = Color.rgb(0xFF, 0xD2, 0x3F)
        val BOARD_STRIPE = Color.rgb(0x13, 0x9C, 0xA8)
        val WATER = Color.rgb(0x2E, 0x9C, 0xE6)
        val WATER_DEEP = Color.rgb(0x1A, 0x6C, 0xC4)
        val FOAM = Color.rgb(0xF2, 0xFB, 0xFF)
        val FLOWER = Color.rgb(0xFF, 0x3E, 0x7E)
        val CHEEK = Color.rgb(0xFF, 0x80, 0x8A)
    }
    override val skin = SKIN
    override val skinShade = SKIN_SHADE
    override val hair = HAIR
    private val wave = Path()
    private val swellShader = android.graphics.LinearGradient(-92f, 0f, 92f, 0f,
        intArrayOf(WATER_DEEP and 0x00FFFFFF, WATER_DEEP, WATER_DEEP, WATER_DEEP and 0x00FFFFFF), floatArrayOf(0f, 0.3f, 0.7f, 1f), android.graphics.Shader.TileMode.CLAMP)
    private val faceShader = android.graphics.LinearGradient(-74f, 0f, 74f, 0f,
        intArrayOf(WATER and 0x00FFFFFF, WATER, WATER, WATER and 0x00FFFFFF), floatArrayOf(0f, 0.3f, 0.7f, 1f), android.graphics.Shader.TileMode.CLAMP)

    override fun drawJets(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) = Unit

    override fun drawBehind(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        if (p.surf > 0.05f) drawWave(c, rig, p, t)
        pushBody(c, rig, p)
        if (p.surf > 0.05f) drawBoard(c, rig, p)
        drawPonytail(c, rig, p, t)
    }

    /** Board under her feet, in hips space (tilts with her body). */
    private fun drawBoard(c: Canvas, rig: MonkeyRig, p: Pose) {
        c.save(); c.concat(rig.hips)
        val y = -MonkeyRig.HIP_Y + 4f   // just under the feet
        fill.shader = null; fill.color = BOARD
        tmp.reset(); tmp.moveTo(-46f, y); tmp.cubicTo(-40f, y - 7f, 30f, y - 8f, 52f, y - 3f)
        tmp.cubicTo(48f, y + 6f, -30f, y + 7f, -46f, y); tmp.close()
        c.drawPath(tmp, fill); c.drawPath(tmp, outline)
        stroke.shader = null; stroke.color = BOARD_STRIPE; stroke.strokeWidth = 3f
        c.drawLine(-38f, y - 0.5f, 44f, y - 2.5f, stroke)
        fill.color = BOARD_STRIPE; c.drawCircle(-36f, y + 3.5f, 2f, fill)   // fin hint
        c.restore()
    }

    /** The wave: drawn in view space below the board (it does not tilt with her), swell + foam + droplets + spray trail. */
    private fun drawWave(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        pts[0] = 0f; pts[1] = -MonkeyRig.HIP_Y + 8f; rig.hips.mapPoints(pts)
        val cx = pts[0]; val top = pts[1]
        val sc = 1f
        val a = p.surf.coerceIn(0f, 1f)
        val vx = (anim?.velocityX ?: 0f)
        val speed = (abs(vx) / 900f).coerceIn(0f, 1f)
        val spray = (p.surfSpray.coerceIn(0f, 1f) * 0.8f + speed).coerceIn(0f, 1f) * (if (on("spray")) 1f else 0.3f)
        val back = if (vx > 30f) -1f else if (vx < -30f) 1f else -1f
        // v10.22: the water stays still ON THE SCREEN while she slides over it (ripples use screen x, not her x)
        val wx = (anim?.screenFeetX ?: cx) - cx
        // deep swell (a wide hump of water that she rides on), with a curling lip rising behind her
        // v10.22: the water fades out at both ends (open water, not a blob of wave she carries around)
        c.save(); c.translate(cx, 0f)
        wave.reset(); wave.moveTo(-92f * sc, top + 30f)
        var x = -92f
        while (x <= 92f) { wave.lineTo(x * sc, top + 8f + 3.5f * sin((x + wx) * 0.08f + t * 0.8f) + 0.0016f * x * x); x += 6f }
        wave.lineTo(92f * sc, top + 30f); wave.close()
        fill.color = Color.WHITE; fill.shader = swellShader; c.drawPath(wave, fill); fill.shader = null
        c.restore()
        // the curl: a lip of water rising up behind the board and tipping over
        val lip = 0.15f + 0.6f * spray   // a small curl only when she's really moving
        wave.reset(); wave.moveTo(cx + back * 20f, top + 10f)
        wave.cubicTo(cx + back * 55f, top + 6f, cx + back * 70f, top - 22f * lip, cx + back * 58f, top - 34f * lip)
        wave.cubicTo(cx + back * 50f, top - 26f * lip, cx + back * 44f, top - 12f * lip, cx + back * 30f, top - 8f * lip)
        wave.cubicTo(cx + back * 40f, top + 2f, cx + back * 30f, top + 8f, cx + back * 20f, top + 10f); wave.close()
        fill.color = WATER; fill.alpha = (235 * a).toInt(); c.drawPath(wave, fill)
        // bright face of the swell
        c.save(); c.translate(cx, 0f)
        wave.reset(); wave.moveTo(-74f, top + 24f); x = -74f
        while (x <= 74f) { wave.lineTo(x, top + 3f + 2.5f * sin((x + wx) * 0.12f - t * 0.9f) + 0.002f * x * x); x += 6f }
        wave.lineTo(74f, top + 24f); wave.close()
        fill.color = Color.WHITE; fill.shader = faceShader; c.drawPath(wave, fill); fill.shader = null
        c.restore()
        fill.color = WATER; fill.alpha = (235 * a).toInt()
        // foam crest + the lip's foam
        stroke.shader = null; stroke.color = FOAM; stroke.alpha = (235 * a).toInt(); stroke.strokeWidth = 3.2f
        x = -50f; var lx = cx + x; var ly = top + 3f + 2.5f * sin((x + wx) * 0.12f - t * 0.9f) + 0.003f * x * x
        while (x < 50f) { x += 6f; val nx = cx + x; val ny = top + 3f + 2.5f * sin((x + wx) * 0.12f - t * 0.9f) + 0.003f * x * x; c.drawLine(lx, ly, nx, ny, stroke); lx = nx; ly = ny }
        // the wake: foam streaks left on the water behind the board (fixed on the screen, so she visibly glides past them)
        if (speed > 0.05f) {
            stroke.alpha = (170 * a * speed).toInt().coerceIn(0, 255); stroke.strokeWidth = 2f
            var i = 0
            while (i < 5) {
                val gx = Math.floorMod(wx.toInt(), 22).toFloat()       // streak spacing locked to the screen
                val sx0 = cx + back * (24f + i * 22f) - gx * back
                c.drawLine(sx0, top + 6f + i * 1.5f, sx0 + back * 14f, top + 8f + i * 1.5f, stroke)
                i++
            }
            stroke.strokeWidth = 3.2f
        }
        fill.color = FOAM; fill.alpha = (235 * a).toInt()
        for (i in 0 until 4) c.drawCircle(cx + back * (58f - i * 6f), top - 34f * lip + i * 5f, 3.2f - i * 0.4f, fill)
        // droplets + spray trail behind the board (deterministic, no allocation)
        val n = 4 + (spray * 10f).toInt()
        for (i in 0 until n) {
            val ph = ((t * 1.6f + i * 0.37f) % 1f)
            val dx = back * (20f + 55f * ph * (0.5f + spray)) + 6f * sin(i * 2.3f)
            val dy = -6f - 26f * spray * sin(ph * 3.1416f) * (0.6f + 0.4f * sin(i * 1.7f))
            fill.alpha = ((1f - ph) * 230 * a).toInt().coerceIn(0, 255)
            c.drawCircle(cx + dx, top + dy, 2f + 2.6f * (1f - ph) * (0.5f + spray), fill)
        }
        fill.alpha = 255; stroke.alpha = 255
    }

    private fun drawPonytail(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val sw = (anim?.appendageAngle(Appendage.HAIR) ?: 0f) + 6f * sin(t * 2.6f)
        c.save(); c.concat(rig.head)
        c.translate(-20f, -70f); c.rotate(-30f + sw)
        fill.shader = null; fill.color = HAIR
        tmp.reset(); tmp.moveTo(0f, 0f); tmp.cubicTo(-16f, 6f, -22f, 26f, -12f, 40f); tmp.cubicTo(-6f, 28f, 4f, 14f, 6f, 2f); tmp.close()
        c.drawPath(tmp, fill); outline.strokeWidth = 1.6f; c.drawPath(tmp, outline); outline.strokeWidth = OW
        c.restore()
    }

    override fun drawLegs(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.legL, LEG, 9f, SKIN, true); limb(c, rig.legR, LEG, 9f, SKIN, true)
        limb(c, rig.legL, LEG, 9f, SKIN, false); limb(c, rig.legR, LEG, 9f, SKIN, false)
        blob(c, rig.legL, 2f, LEG + 2f, 7f, 4f, SKIN); blob(c, rig.legR, 2f, LEG + 2f, 7f, 4f, SKIN)
    }

    override fun drawTorso(c: Canvas, rig: MonkeyRig) {
        c.save(); c.concat(rig.hips); c.scale(0.82f, 1f)
        fill.shader = null; fill.color = TOP; c.drawPath(suit, fill); c.drawPath(suit, outline)
        fill.color = SHORTS; oval.set(-23f, -12f, 23f, 7f); c.drawRect(oval, fill); c.drawRect(oval, outline)
        stroke.shader = null; stroke.color = TOP_DARK; stroke.strokeWidth = 2.5f; c.drawLine(-16f, -40f, 16f, -34f, stroke)
        fill.color = Color.WHITE; c.drawCircle(8f, -26f, 3f, fill)   // a little wave logo dot
        c.restore()
    }

    override fun drawArms(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.armLUpper, rig.upperLen, 8f, TOP, true); limb(c, rig.armLFore, rig.foreLen, 7f, SKIN, true)
        limb(c, rig.armRUpper, rig.upperLen, 8f, TOP, true); limb(c, rig.armRFore, rig.foreLen, 7f, SKIN, true)
        limb(c, rig.armLUpper, rig.upperLen, 8f, TOP, false); limb(c, rig.armLFore, rig.foreLen, 7f, SKIN, false)
        limb(c, rig.armRUpper, rig.upperLen, 8f, TOP, false); limb(c, rig.armRFore, rig.foreLen, 7f, SKIN, false)
        blob(c, rig.armLFore, 0f, rig.foreLen + 1.5f, 5f, 5f, SKIN)
        blob(c, rig.armRFore, 0f, rig.foreLen + 1.5f, 5f, 5f, SKIN)
    }

    override fun drawHeadBase(c: Canvas, p: Pose, t: Float) {
        humanHead(c)
        // a sweep of fringe + a high bun base for the ponytail
        fill.shader = null; fill.color = HAIR
        c.drawCircle(-14f, -66f, 9f, fill)
        if (on("flower")) {
            fill.color = FLOWER
            for (i in 0 until 5) { val a = i * 1.2566f; c.drawCircle(-24f + 5f * kotlin.math.cos(a), -60f + 5f * sin(a), 3.6f, fill) }
            fill.color = Color.rgb(0xFF, 0xE0, 0x5A); c.drawCircle(-24f, -60f, 2.2f, fill)
        }
    }
    override fun drawCheeks(c: Canvas, p: Pose) {
        fill.shader = null; fill.color = CHEEK; fill.alpha = 150
        c.drawCircle(-20f, -27f, 5f, fill); c.drawCircle(20f, -27f, 5f, fill); fill.alpha = 255
    }

    override fun drawFront(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) { popBody(c) }
}

object SurferPeek : PeekFace("surfer") {
    override fun drawFace(c: Canvas, wear: Wear?, s: State) {
        val tw = s.tw; val cy = s.cy
        val bob = if (s.peeking) sin(s.t * 6f) * 0.08f * tw else 0f
        fill(SurferRenderer.WATER); r.set(-0.8f * tw, cy + 0.35f * tw, 0.8f * tw, cy + 0.9f * tw); c.drawOval(r, paint)
        fill(SurferRenderer.HAIR); c.drawCircle(-0.35f * tw, cy - 0.55f * tw + bob, 0.22f * tw, paint)
        fill(SurferRenderer.SKIN); r.set(-0.44f * tw, cy - 0.5f * tw + bob, 0.44f * tw, cy + 0.45f * tw + bob); c.drawOval(r, paint)
        fill(SurferRenderer.HAIR); r.set(-0.46f * tw, cy - 0.58f * tw + bob, 0.46f * tw, cy - 0.15f * tw + bob); c.drawArc(r, 180f, 180f, true, paint)
        for (i in 0..1) {
            val y = eyeY(s, i) + bob
            fill(Color.rgb(0x2A, 0x18, 0x10)); c.drawCircle(0.25f * tw + s.look * 0.5f, y, if (s.blink) 0.02f * tw else 0.08f * tw, paint)
        }
        fill(SurferRenderer.FLOWER); c.drawCircle(-0.4f * tw, cy - 0.45f * tw + bob, 0.1f * tw, paint)
    }
}
