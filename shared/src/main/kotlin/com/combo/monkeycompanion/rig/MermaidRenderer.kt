package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.engine.anim.Appendage
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v10.55 Meenu: an ORIGINAL cute little mermaid girl (same chibi human style as Wavy / Rollie). Long wavy black hair
 * with a pearl string and a coral starfish clip, a sky-blue top with a pearl trim, gold bangles, a lavender-to-pink tail
 * with golden fins. The screen is her pool:
 *  - while she moves she leans into the swim (the whole body tilts toward where she's going), the tail beats harder
 *    and the hair streams behind;
 *  - bubbles always rise from her (straight up the screen), a trail of them when she swims fast.
 * Pose slots she reads: legL/RSwing (average) = tail bend, legL/RLift = tail curl, dvSpell = bubble-show progress,
 * surfSpray = tail splash, dvAura = pearl glow. All motion is a pure function of time (no RNG).
 */
class MermaidRenderer : V1014BodyRenderer() {
    companion object {
        val SKIN = Color.rgb(0xD8, 0x9C, 0x72)
        val SKIN_SHADE = Color.rgb(0xAE, 0x72, 0x4E)
        val SKIN_LIGHT = Color.rgb(0xF0, 0xC0, 0x98)
        val HAIR = Color.rgb(0x2E, 0x1C, 0x14)
        val HAIR_SHINE = Color.rgb(0x5A, 0x3C, 0x30)
        val SHELL = Color.rgb(0xFF, 0xA8, 0xC0)
        val TOP = Color.rgb(0x7C, 0xC4, 0xFF)
        val TOP_DARK = Color.rgb(0x4E, 0x96, 0xDA)
        val TAIL_TOP = Color.rgb(0xB0, 0x8C, 0xF0)
        val TAIL_BOTTOM = Color.rgb(0xFF, 0x8C, 0xB6)
        val SCALE = Color.argb(150, 0xFF, 0xFF, 0xFF)
        val FIN = Color.rgb(0xFF, 0xC8, 0x66)
        val FIN_DARK = Color.rgb(0xE0, 0x96, 0x2E)
        val GOLD = Color.rgb(0xF5, 0xC4, 0x3A)
        val PEARL = Color.rgb(0xFB, 0xF7, 0xF0)
        val STAR = Color.rgb(0xFF, 0x7E, 0x6A)
        val BUBBLE = Color.rgb(0x6E, 0xC6, 0xFF)
        val WATER = Color.rgb(0x3F, 0xA9, 0xF5)
        /** Tail length in hips space (rig units), from the waist. */
        const val TAIL_LEN = 52f
        const val WAIST_Y = -10f
    }
    override val skin = SKIN
    override val skinShade = SKIN_SHADE
    override val hair = HAIR

    private val tailShader = LinearGradient(0f, WAIST_Y, 0f, WAIST_Y + TAIL_LEN + 10f, TAIL_TOP, TAIL_BOTTOM, Shader.TileMode.CLAMP)
    private val tail = Path()
    private val sx = FloatArray(11); private val sy = FloatArray(11)
    private val nx = FloatArray(11); private val ny = FloatArray(11)
    private val hp = FloatArray(2)

    // swim state, smoothed across frames (visual only, never touches the frame loop's Random)
    private var swimK = 0f          // 0 floating .. 1 swimming fast
    private var lean = 0f           // degrees the whole body leans into the swim
    private var dirX = 1f           // which way she last swam
    private var lastT = Float.NaN
    private var tilted = false
    private var pivotX = 0f; private var pivotY = 0f
    /** Tail tip in hips space (for the splash), written by drawLegs. */
    private var tipX = 0f; private var tipY = 0f

    override fun drawJets(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) = Unit

    private fun updateSwim(t: Float) {
        val vx = anim?.velocityX ?: 0f; val vy = anim?.velocityY ?: 0f
        val dt = if (lastT.isNaN()) 0.016f else (t - lastT).coerceIn(0f, 0.1f)
        lastT = t
        val target = (hypot(vx, vy) / 700f).coerceIn(0f, 1f)
        val a = (dt * 4f).coerceIn(0f, 1f)
        swimK += (target - swimK) * a
        if (abs(vx) > 60f) dirX = if (vx > 0f) 1f else -1f
        // lean into the swim: mostly sideways travel tilts the body forward (head first), going up/down tilts less
        val side = if (target > 0.02f) abs(vx) / (hypot(vx, vy) + 1f) else 0f
        val want = 38f * swimK * side * dirX
        lean += (want - lean) * a
    }

    override fun drawBehind(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        updateSwim(t)
        hp[0] = 0f; hp[1] = -24f; rig.hips.mapPoints(hp)
        pivotX = hp[0]; pivotY = hp[1]
        tilted = abs(lean) > 0.3f
        c.save()
        if (tilted) c.rotate(lean, pivotX, pivotY)
        pushBody(c, rig, p)
    }

    /** The tail, drawn on the rig's legs: bent by the average leg swing, curled by the lift, beating while she swims. */
    override fun drawLegs(c: Canvas, rig: MonkeyRig) {
        val p = pose; val t = now
        val swing = Math.toRadians(0.5 * (p.legLSwing + p.legRSwing)).toFloat()
        val curl = (0.5f * (p.legLLift + p.legRLift)).coerceIn(0f, 30f)
        val len = TAIL_LEN * (1f - 0.012f * curl)
        val beatAmp = 5f + 9f * swimK
        val beatF = 2.2f + 4.5f * swimK
        c.save(); c.concat(rig.hips)
        // spine of the tail: down from the waist, bending sideways by the swing (and more when curled), with a travelling wave
        for (i in 0..10) {
            val s = i / 10f
            val bend = -sin(swing) * len * s * s - curl * 1.4f * s * s * s * (if (swing >= 0f) 1f else -1f)
            val wave = beatAmp * sin(t * beatF - s * 2.6f) * s
            sx[i] = bend + wave
            sy[i] = WAIST_Y + len * s * (1f - 0.25f * s * curl / 30f)
        }
        for (i in 0..10) normal(i)
        tail.reset()
        for (i in 0..10) { val w = halfWidth(i / 10f); if (i == 0) tail.moveTo(sx[i] - nx[i] * w, sy[i] - ny[i] * w) else tail.lineTo(sx[i] - nx[i] * w, sy[i] - ny[i] * w) }
        for (i in 10 downTo 0) { val w = halfWidth(i / 10f); tail.lineTo(sx[i] + nx[i] * w, sy[i] + ny[i] * w) }
        tail.close()
        fill.color = Color.WHITE   // full alpha: the shadow drawn just before leaves the paint half see-through
        fill.shader = tailShader; c.drawPath(tail, fill); fill.shader = null
        // scales: little white arcs in rows along the tail
        stroke.shader = null; stroke.color = SCALE; stroke.strokeWidth = 1.3f
        for (row in 1..7) {
            val i = row; val w = halfWidth(i / 10f) * 0.7f
            for (j in -1..1) {
                val off = j * w * 0.62f + (if (row % 2 == 0) w * 0.3f else 0f)
                if (abs(off) > w) continue
                oval.set(sx[i] + off - 3.2f, sy[i] - 3.2f, sx[i] + off + 3.2f, sy[i] + 3.2f)
                c.drawArc(oval, 20f, 140f, false, stroke)
            }
        }
        c.drawPath(tail, outline)
        // the fluke (two fin lobes), angled along the tail's end and flapping with the beat
        val ang = Math.toDegrees(atan2((sx[10] - sx[9]).toDouble(), (sy[10] - sy[9]).toDouble())).toFloat()
        val flap = (10f + 8f * swimK) * sin(t * beatF - 2.6f - 0.6f)
        tipX = sx[10]; tipY = sy[10]
        c.save(); c.translate(sx[10], sy[10]); c.rotate(-ang + flap)
        tmp.reset()
        tmp.moveTo(-4f, -2f)
        tmp.cubicTo(-10f, 6f, -22f, 8f, -27f, 19f)
        tmp.cubicTo(-16f, 15f, -7f, 17f, 0f, 11f)
        tmp.cubicTo(7f, 17f, 16f, 15f, 27f, 19f)
        tmp.cubicTo(22f, 8f, 10f, 6f, 4f, -2f)
        tmp.close()
        fill.color = FIN; c.drawPath(tmp, fill)
        stroke.color = FIN_DARK; stroke.strokeWidth = 1.2f
        for (s in floatArrayOf(-1f, 1f)) for (k in 1..3) c.drawLine(s * 2f, 3f, s * (6f + k * 5f), 10f + k * 2f, stroke)
        c.drawPath(tmp, outline)
        c.restore()
        // small side fins at the hips
        fill.color = FIN
        for (s in floatArrayOf(-1f, 1f)) {
            tmp.reset(); val bx = sx[3] + s * halfWidth(0.3f); val by = sy[3]
            tmp.moveTo(bx, by - 4f); tmp.quadTo(bx + s * 12f, by + 2f + 2f * sin(t * beatF), bx + s * 4f, by + 9f); tmp.close()
            c.drawPath(tmp, fill); outline.strokeWidth = 1.4f; c.drawPath(tmp, outline); outline.strokeWidth = OW
        }
        c.restore()
    }

    private fun halfWidth(s: Float) = 20f - 14f * s + 2f * sin(s * 3.1416f)
    /** Unit normal of the tail spine at point [i] (into [nx] / [ny], no allocation). */
    private fun normal(i: Int) {
        val a = (i - 1).coerceAtLeast(0); val b = (i + 1).coerceAtMost(10)
        val dx = sx[b] - sx[a]; val dy = sy[b] - sy[a]
        val l = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        nx[i] = dy / l; ny[i] = -dx / l
    }

    override fun drawTorso(c: Canvas, rig: MonkeyRig) {
        c.save(); c.concat(rig.hips); c.scale(0.84f, 1f)
        fill.shader = null; fill.color = TOP; c.drawPath(suit, fill)
        // the lower body is already tail (same colour as the tail's top), up to a wavy pearl waistline
        c.save(); c.clipPath(suit)
        fill.color = TAIL_TOP; c.drawRect(-30f, -12f, 30f, 10f, fill)
        // scalloped hem of the top, just above the tail
        fill.color = TOP
        for (i in -3..3) c.drawCircle(i * 7f, -14f, 4.2f, fill)
        stroke.shader = null; stroke.color = TOP_DARK; stroke.strokeWidth = 1.4f
        for (i in -3..3) { oval.set(i * 7f - 4.2f, -18.2f, i * 7f + 4.2f, -9.8f); c.drawArc(oval, 10f, 160f, false, stroke) }
        // a little star print
        fill.color = Color.WHITE; c.drawCircle(7f, -32f, 2.4f, fill)
        c.restore()
        c.drawPath(suit, outline)
        // pearl collar
        fill.color = PEARL
        for (i in -3..3) c.drawCircle(i * 4.5f, -55f + 0.35f * i * i, 1.7f, fill)
        c.restore()
    }

    override fun drawArms(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.armLUpper, rig.upperLen, 7.5f, SKIN, true); limb(c, rig.armLFore, rig.foreLen, 7f, SKIN, true)
        limb(c, rig.armRUpper, rig.upperLen, 7.5f, SKIN, true); limb(c, rig.armRFore, rig.foreLen, 7f, SKIN, true)
        limb(c, rig.armLUpper, rig.upperLen, 7.5f, SKIN, false); limb(c, rig.armLFore, rig.foreLen, 7f, SKIN, false)
        limb(c, rig.armRUpper, rig.upperLen, 7.5f, SKIN, false); limb(c, rig.armRFore, rig.foreLen, 7f, SKIN, false)
        blob(c, rig.armLFore, 0f, rig.foreLen + 1.5f, 5f, 5f, SKIN)
        blob(c, rig.armRFore, 0f, rig.foreLen + 1.5f, 5f, 5f, SKIN)
        // gold bangles
        stroke.shader = null; stroke.color = GOLD; stroke.strokeWidth = 2.2f
        for (m in arrayOf(rig.armLFore, rig.armRFore)) {
            c.save(); c.concat(m)
            c.drawLine(-4.2f, rig.foreLen - 5f, 4.2f, rig.foreLen - 5f, stroke)
            c.drawLine(-4.2f, rig.foreLen - 8f, 4.2f, rig.foreLen - 8f, stroke)
            c.restore()
        }
    }

    /** v10.55.1 Skye's cute face (smaller soft face, big sparkly eyes, lashes), a side-swept fringe, two locks framing it, pearls, starfish. */
    private val faceOval = Path().apply {
        moveTo(0f, -66f)
        cubicTo(17f, -66f, 27f, -54f, 27f, -38f)
        cubicTo(27f, -24f, 16f, -12f, 0f, -8.5f)
        cubicTo(-16f, -12f, -27f, -24f, -27f, -38f)
        cubicTo(-27f, -54f, -17f, -66f, 0f, -66f)
        close()
    }
    private val faceShader = RadialGradient(-6f, -46f, 40f, intArrayOf(SKIN_LIGHT, SKIN, SKIN_SHADE), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
    override val glintEye: FloatArray? = floatArrayOf(10.5f, -34.5f, 0.9f)

    override fun drawHeadBase(c: Canvas, p: Pose, t: Float) {
        val sw = (anim?.appendageAngle(Appendage.HAIR) ?: 0f) + 5f * sin(t * 3.1f)
        fill.shader = null; fill.color = HAIR
        oval.set(-30f, -71f, 30f, -26f); c.drawOval(oval, fill)
        // two pigtails (like Skye's), each tied with a pink shell clip; they stream back while she swims
        for (s in intArrayOf(-1, 1)) {
            val stream = 30f * swimK * (if (s.toFloat() == -dirX) 1.2f else 0.6f)
            c.save(); c.translate(s * 22f, -63f); c.rotate(-s * 50f - dirX * stream + sw * (if (s < 0) 1.1f else 0.9f))
            fill.color = HAIR
            tmp.reset(); tmp.moveTo(-4f, 0f)
            tmp.cubicTo(-11f, 7f, -10f, 19f, -3f + s * 2f, 27f)
            tmp.quadTo(s * 4f + 1f, 30f, 4f + s * 3f, 25f)
            tmp.cubicTo(10f, 17f, 10f, 6f, 4f, 0f); tmp.close()
            c.drawPath(tmp, fill); outline.strokeWidth = 1.5f; c.drawPath(tmp, outline)
            stroke.shader = null; stroke.color = HAIR_SHINE; stroke.strokeWidth = 1.2f
            c.drawLine(-2f, 6f, -3f, 18f, stroke); c.drawLine(3f, 7f, 3.5f, 17f, stroke)
            // shell clip: a little fan with ridges
            fill.color = SHELL
            tmp.reset(); tmp.moveTo(0f, 4f); tmp.lineTo(-6.5f, -2f); tmp.quadTo(0f, -8.5f, 6.5f, -2f); tmp.close()
            c.drawPath(tmp, fill); c.drawPath(tmp, outline)
            stroke.color = Color.rgb(0xE0, 0x70, 0x90); stroke.strokeWidth = 1f
            for (k in -1..1) c.drawLine(0f, 3f, k * 3.6f, -4.6f, stroke)
            outline.strokeWidth = OW
            c.restore()
        }
        // little ears with pearl studs
        fill.shader = null
        for (s in intArrayOf(-1, 1)) {
            fill.color = SKIN; oval.set(s * 26.5f - 4f, -42f, s * 26.5f + 4f, -32f); c.drawOval(oval, fill)
            outline.strokeWidth = 1.4f; c.drawOval(oval, outline); outline.strokeWidth = OW
            fill.color = PEARL; c.drawCircle(s * 26.5f, -29.5f, 2f, fill)
        }
        // the face
        fill.shader = faceShader; c.drawPath(faceOval, fill); fill.shader = null
        outline.strokeWidth = 2f; c.drawPath(faceOval, outline); outline.strokeWidth = OW
        // soft side-swept fringe
        fill.color = HAIR
        tmp.reset(); tmp.moveTo(-28f, -40f)
        tmp.cubicTo(-30f, -64f, -12f, -72f, 4f, -70f)
        tmp.cubicTo(20f, -69f, 30f, -58f, 28f, -40f)
        tmp.quadTo(25f, -49f, 18f, -51f)
        tmp.quadTo(13f, -46f, 7f, -48f)
        tmp.quadTo(1f, -52f, -5f, -48.5f)
        tmp.quadTo(-12f, -45.5f, -18f, -51f)
        tmp.quadTo(-25f, -48f, -28f, -40f); tmp.close()
        c.drawPath(tmp, fill); outline.strokeWidth = 1.6f; c.drawPath(tmp, outline)
        outline.strokeWidth = OW
        stroke.shader = null; stroke.color = HAIR_SHINE; stroke.strokeWidth = 1.6f
        tmp.reset(); tmp.moveTo(-14f, -64f); tmp.quadTo(-4f, -67.5f, 8f, -66f); c.drawPath(tmp, stroke)
        if (on("pearls")) {   // a string of pearls along the hairline
            var a = -140f
            while (a <= -40f) {
                val r = Math.toRadians(a.toDouble())
                val x = (24f * cos(r)).toFloat(); val y = (-46f + 22f * sin(r)).toFloat()
                fill.color = PEARL; c.drawCircle(x, y, 2f, fill)
                fill.color = Color.WHITE; c.drawCircle(x - 0.6f, y - 0.6f, 0.7f, fill)
                a += 12.5f
            }
        }
        if (on("starfish")) star(c, 17f, -66f, 6f, STAR, t)
    }

    override fun drawCheeks(c: Canvas, p: Pose) = cuteGirlCheeks(c, p)
    override fun drawEyes(c: Canvas, p: Pose) = cuteGirlEyes(c, p)
    override fun drawBrows(c: Canvas, p: Pose) = cuteGirlBrows(c, p, HAIR)
    override fun drawNose(c: Canvas, p: Pose) = cuteGirlNose(c, p, SKIN_SHADE)
    override fun drawMouth(c: Canvas, p: Pose) = cuteGirlMouth(c, p)

    private fun star(c: Canvas, x: Float, y: Float, r: Float, col: Int, t: Float) {
        tmp.reset()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.45f
            val a = -1.5708f + i * 0.6283f + 0.1f * sin(t * 1.3f)
            val px = x + rr * cos(a); val py = y + rr * sin(a)
            if (i == 0) tmp.moveTo(px, py) else tmp.lineTo(px, py)
        }
        tmp.close()
        fill.shader = null; fill.color = col; c.drawPath(tmp, fill)
        outline.strokeWidth = 1.2f; c.drawPath(tmp, outline); outline.strokeWidth = OW
        fill.color = Color.argb(160, 255, 255, 255); c.drawCircle(x - 1f, y - 1f, 1.2f, fill)
    }


    override fun drawFront(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        popBody(c)
        c.restore()   // the swim lean (saved in drawBehind)
        // everything below is in screen orientation: bubbles always rise straight up
        if (p.surfSpray > 0.02f) drawSplash(c, rig, p, t)
        if (p.dvAura > 0.02f) drawPearl(c, rig, p, t)
        if (p.dvSpell > 0.005f) drawBubbleShow(c, rig, p, t)
        if (on("bubbles")) drawRisingBubbles(c, rig, p, t)
    }

    /** A point given in hips space, mapped to the canvas including the swim lean. */
    private fun hipsPoint(rig: MonkeyRig, x: Float, y: Float): FloatArray {
        hp[0] = x; hp[1] = y; rig.hips.mapPoints(hp)
        if (tilted) {
            val a = Math.toRadians(lean.toDouble()); val dx = hp[0] - pivotX; val dy = hp[1] - pivotY
            hp[0] = (pivotX + dx * cos(a) - dy * sin(a)).toFloat(); hp[1] = (pivotY + dx * sin(a) + dy * cos(a)).toFloat()
        }
        return hp
    }

    /** Few bubbles while floating, a trail of them behind the tail while swimming fast. They drift up and fade. */
    private fun drawRisingBubbles(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val sleeping = p.eyeOpen < 0.05f && p.shadow > 0f
        val n = 3 + (swimK * 9f).toInt()
        val base = hipsPoint(rig, tipX, tipY)
        val bx = base[0]; val by = base[1]
        val mouth = hipsPoint(rig, 0f, -78f)
        val mx = mouth[0]; val my = mouth[1]
        for (i in 0 until n) {
            val ph = ((t * (0.45f + 0.5f * swimK) + i * 0.618f) % 1f)
            val fromMouth = sleeping || i % 4 == 3
            val ox = if (fromMouth) mx + 10f else bx - dirX * 18f * swimK * (1f + i % 3)
            val oy = if (fromMouth) my else by
            val x = ox + 7f * sin(t * 2.3f + i * 1.7f) * ph + (i % 3 - 1) * 5f
            val y = oy - (60f + 50f * swimK) * ph - 6f
            val r = (2.8f + (i % 3) * 1.6f) * (0.7f + 0.5f * ph)
            bubble(c, x, y, r, ((1f - ph) * (1f - ph) * 230f).toInt())
        }
    }

    private fun bubble(c: Canvas, x: Float, y: Float, r: Float, alpha: Int) {
        if (alpha <= 2) return
        fill.shader = null; fill.color = BUBBLE; fill.alpha = (alpha * 0.3f).toInt(); c.drawCircle(x, y, r, fill)
        stroke.shader = null; stroke.color = BUBBLE; stroke.alpha = alpha; stroke.strokeWidth = 1.6f; c.drawCircle(x, y, r, stroke)
        fill.color = Color.WHITE; fill.alpha = alpha; c.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.25f + 0.3f, fill)
        fill.alpha = 255; stroke.alpha = 255
    }

    /** Bubble show: a giant bubble grows at her mouth and floats up, then pops into a shower of tiny bubbles. */
    private fun drawBubbleShow(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val k = p.dvSpell.coerceIn(0f, 1f)
        val m = hipsPoint(rig, 0f, -62f)
        val mx = m[0]; val my = m[1]
        if (k < 0.55f) {
            val g = SwipeK.ss(0.05f, 0.5f, k)
            val r = 4f + 30f * g
            val cx = mx + 6f + r * 0.95f + 4f * sin(t * 3f) * g; val cy = my - r * 0.5f - 40f * SwipeK.ss(0.3f, 0.55f, k)
            fill.shader = null; fill.color = BUBBLE; fill.alpha = 60; c.drawCircle(cx, cy, r, fill)
            stroke.shader = null; stroke.color = Color.WHITE; stroke.alpha = 220; stroke.strokeWidth = 1.6f; c.drawCircle(cx, cy, r, stroke)
            stroke.color = Color.rgb(0xFF, 0xB8, 0xF0); stroke.alpha = 150; stroke.strokeWidth = 2f
            oval.set(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f); c.drawArc(oval, 200f, 70f, false, stroke)
            fill.color = Color.WHITE; fill.alpha = 230; c.drawCircle(cx - r * 0.4f, cy - r * 0.4f, r * 0.12f + 1f, fill)
            fill.alpha = 255; stroke.alpha = 255
        } else {
            val cx = mx + 40f; val cy = my - 60f
            val u = SwipeK.ss(0.55f, 1f, k)
            if (k < 0.66f) {   // the pop ring
                val q = (k - 0.55f) / 0.11f
                stroke.shader = null; stroke.color = Color.WHITE; stroke.alpha = ((1f - q) * 230).toInt(); stroke.strokeWidth = 2f
                c.drawCircle(cx, cy, 34f + 14f * q, stroke); stroke.alpha = 255
            }
            val fade = 1f - SwipeK.ss(0.82f, 1f, k)
            for (i in 0 until 16) {
                val a = i * 0.3927f + 0.2f
                val d = 10f + 70f * u * (0.7f + 0.3f * sin(i * 2.1f))
                val x = cx + cos(a) * d; val y = cy + sin(a) * d * 0.8f - 40f * u
                bubble(c, x, y, 2.2f + (i % 3) * 1.4f, (230 * fade).toInt())
            }
        }
    }

    /** Tail splash: water drops flying up from the tail tip in arcs, falling back down. */
    private fun drawSplash(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val s = p.surfSpray.coerceIn(0f, 1f)
        val b = hipsPoint(rig, tipX, tipY)
        val x0 = b[0]; val y0 = b[1]
        fill.shader = null
        for (i in 0 until 14) {
            val ph = ((t * 1.3f + i * 0.071f) % 1f)
            val side = if (i % 2 == 0) 1f else -1f
            val vx = side * (18f + 6f * (i % 5))
            val x = x0 + vx * ph * 2f
            val y = y0 - 70f * s * ph + 90f * ph * ph * s * 0.8f
            fill.color = if (i % 3 == 0) Color.WHITE else WATER
            fill.alpha = ((1f - ph) * 230 * s).toInt().coerceIn(0, 255)
            c.drawCircle(x, y, 2.4f + 1.8f * (1f - ph), fill)
        }
        // a splash crown at the tip
        stroke.shader = null; stroke.color = WATER; stroke.alpha = (200 * s).toInt(); stroke.strokeWidth = 2.4f
        oval.set(x0 - 26f * s, y0 - 6f, x0 + 26f * s, y0 + 6f); c.drawOval(oval, stroke)
        fill.alpha = 255; stroke.alpha = 255
    }

    /** Pearl glow: a shining pearl held over her head, a soft halo and sparkles circling it. */
    private fun drawPearl(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val e = p.dvAura.coerceIn(0f, 1f)
        val h = hipsPoint(rig, 0f, -134f)
        val x = h[0]; val y = h[1]
        fill.shader = null
        fill.color = Color.rgb(0xFF, 0xF2, 0xC8); fill.alpha = (70 * e).toInt(); c.drawCircle(x, y, 22f * e + 3f * sin(t * 4f), fill)
        fill.alpha = (110 * e).toInt(); c.drawCircle(x, y, 13f * e, fill)
        fill.color = PEARL; fill.alpha = (255 * e).toInt(); c.drawCircle(x, y, 7f * e, fill)
        fill.color = Color.WHITE; c.drawCircle(x - 2.2f * e, y - 2.2f * e, 2f * e, fill)
        for (i in 0 until 6) {
            val a = t * 1.8f + i * 1.047f
            val sxp = x + cos(a) * 24f * e; val syp = y + sin(a) * 16f * e
            val tw = 0.5f + 0.5f * sin(t * 6f + i)
            fill.color = GOLD; fill.alpha = (230 * e * tw).toInt()
            tmp.reset(); val r = 3.5f * tw + 1f
            tmp.moveTo(sxp, syp - r); tmp.lineTo(sxp + r * 0.3f, syp - r * 0.3f); tmp.lineTo(sxp + r, syp); tmp.lineTo(sxp + r * 0.3f, syp + r * 0.3f)
            tmp.lineTo(sxp, syp + r); tmp.lineTo(sxp - r * 0.3f, syp + r * 0.3f); tmp.lineTo(sxp - r, syp); tmp.lineTo(sxp - r * 0.3f, syp - r * 0.3f); tmp.close()
            c.drawPath(tmp, fill)
        }
        fill.alpha = 255
    }

    private object SwipeK {
        fun ss(e0: Float, e1: Float, x: Float): Float { val k = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return k * k * (3f - 2f * k) }
    }
}

object MermaidPeek : PeekFace("mermaid") {
    override val wardrobeShown: Set<String> get() = setOf("starfish")
    override fun drawFace(c: Canvas, wear: Wear?, s: State) {
        val tw = s.tw; val cy = s.cy
        val bob = if (s.peeking) sin(s.t * 5f) * 0.08f * tw else 0f
        // a bit of water, her long hair on both sides, the face, eyes, a starfish clip and a little bubble
        fill(MermaidRenderer.WATER); r.set(-0.8f * tw, cy + 0.4f * tw, 0.8f * tw, cy + 0.95f * tw); c.drawOval(r, paint)
        fill(MermaidRenderer.HAIR); r.set(-0.56f * tw, cy - 0.62f * tw + bob, 0.56f * tw, cy + 0.7f * tw + bob); c.drawOval(r, paint)
        fill(MermaidRenderer.SKIN); r.set(-0.42f * tw, cy - 0.46f * tw + bob, 0.42f * tw, cy + 0.44f * tw + bob); c.drawOval(r, paint)
        fill(MermaidRenderer.HAIR); r.set(-0.46f * tw, cy - 0.6f * tw + bob, 0.46f * tw, cy - 0.12f * tw + bob); c.drawArc(r, 180f, 180f, true, paint)
        for (i in 0..1) {
            val y = eyeY(s, i) + bob
            fill(Color.rgb(0x22, 0x16, 0x1E)); c.drawCircle(0.25f * tw + s.look * 0.5f, y, if (s.blink) 0.02f * tw else 0.08f * tw, paint)
        }
        if (wear == null || wear.has("starfish")) { fill(MermaidRenderer.STAR); c.drawCircle(-0.38f * tw, cy - 0.42f * tw + bob, 0.09f * tw, paint) }
        if (s.peeking) {
            val ph = (s.t * 0.8f) % 1f
            stroke(MermaidRenderer.BUBBLE, 0.04f * tw); c.drawCircle(0.6f * tw, cy - ph * 0.9f * tw, 0.08f * tw, paint)
        }
    }
}
