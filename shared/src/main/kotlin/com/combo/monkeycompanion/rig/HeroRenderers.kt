package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.HEAD_CY
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.HEAD_R
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.LEG
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.SHOULDER_Y
import kotlin.math.abs
import kotlin.math.sin

/** Shared bits for the human-shaped heroes (v4.1): a human head, a suit torso. */
abstract class HumanRenderer : MonkeyRenderer() {
    protected abstract val skin: Int
    protected abstract val skinShade: Int
    protected abstract val hair: Int
    private var skinShader: RadialGradient? = null
    private var shaderFor = 0
    protected val suit = Path().apply {
        moveTo(0f, -60f)
        cubicTo(15f, -60f, 23f, -46f, 23f, -26f)
        cubicTo(23f, -4f, 15f, 6f, 0f, 6f)
        cubicTo(-15f, 6f, -23f, -4f, -23f, -26f)
        cubicTo(-23f, -46f, -15f, -60f, 0f, -60f)
        close()
    }
    protected val hairCap = Path().apply {
        moveTo(-35f, -34f)
        cubicTo(-38f, -74f, 38f, -78f, 35f, -34f)
        lineTo(28f, -48f); lineTo(20f, -42f); lineTo(14f, -54f); lineTo(5f, -46f); lineTo(-3f, -56f)
        lineTo(-11f, -46f); lineTo(-19f, -55f); lineTo(-25f, -44f); lineTo(-31f, -50f)
        close()
    }
    override fun drawTail(c: Canvas, rig: MonkeyRig) {}

    /** Human head: small ears, round skin-tone face, hair. */
    protected fun humanHead(c: Canvas, withHair: Boolean = true) {
        if (shaderFor != skin) {
            skinShader = RadialGradient(-10f, -48f, 52f, intArrayOf(lighten(skin), skin, skinShade), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            shaderFor = skin
        }
        fill.shader = null; fill.color = skin
        c.drawCircle(-33f, -32f, 7f, fill); c.drawCircle(-33f, -32f, 7f, outline)
        c.drawCircle(33f, -32f, 7f, fill); c.drawCircle(33f, -32f, 7f, outline)
        fill.shader = skinShader; c.drawCircle(0f, HEAD_CY, HEAD_R, fill); fill.shader = null
        c.drawCircle(0f, HEAD_CY, HEAD_R, outline)
        if (withHair) { fill.color = hair; c.drawPath(hairCap, fill); outline.strokeWidth = 1.6f; c.drawPath(hairCap, outline); outline.strokeWidth = OW }
    }

    override fun drawNose(c: Canvas, p: Pose) {
        stroke.shader = null; stroke.color = skinShade; stroke.strokeWidth = 2f
        tmp.reset(); tmp.moveTo(-1f + p.lookX * 0.7f, -31f); tmp.quadTo(4f + p.lookX * 0.7f, -26f, -1f + p.lookX * 0.7f, -24f)
        c.drawPath(tmp, stroke)
    }

    protected fun suitTorso(c: Canvas, rig: MonkeyRig, body: Int, sx: Float = 1f) {
        c.save(); c.concat(rig.hips); c.scale(sx, 1f)
        fill.shader = null; fill.color = body; c.drawPath(suit, fill); c.drawPath(suit, outline)
        c.restore()
    }

    protected fun lighten(col: Int) = Color.rgb(minOf(255, Color.red(col) + 40), minOf(255, Color.green(col) + 40), minOf(255, Color.blue(col) + 40))
}

/**
 * Superboy (v4.1): an ORIGINAL kid hero, teal + orange suit, domino mask, lightning-bolt emblem, orange cape.
 * Deliberately not Superman: no shield, no red/blue/yellow, no spit curl.
 */
class SuperboyRenderer : HumanRenderer() {
    companion object {
        val TEAL = Color.rgb(0x14, 0x9C, 0x96)
        val TEAL_DARK = Color.rgb(0x0B, 0x5F, 0x5C)
        val ORANGE = Color.rgb(0xF2, 0x7A, 0x1C)
        val ORANGE_DARK = Color.rgb(0xA8, 0x47, 0x0C)
        val CREAM = Color.rgb(0xFF, 0xF4, 0xD6)
        val LASER = Color.rgb(0xFF, 0x3B, 0x2F)
    }
    override val skin = Color.rgb(0xC6, 0x8B, 0x5E)
    override val skinShade = Color.rgb(0x9A, 0x63, 0x3E)
    override val hair = Color.rgb(0x1E, 0x14, 0x10)
    override val capeFill get() = ORANGE
    override val capeShade get() = ORANGE_DARK
    private val boltPath = Path().apply {
        moveTo(2f, -40f); lineTo(-6f, -28f); lineTo(0f, -28f); lineTo(-3f, -18f); lineTo(7f, -32f); lineTo(1f, -32f); lineTo(4f, -40f); close()
    }

    override fun drawLegs(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.legL, LEG, 11f, TEAL, true); limb(c, rig.legR, LEG, 11f, TEAL, true)
        limb(c, rig.legL, LEG, 11f, TEAL, false); limb(c, rig.legR, LEG, 11f, TEAL, false)
        blob(c, rig.legL, -2f, LEG + 2f, 9f, 6.5f, ORANGE)
        blob(c, rig.legR, 2f, LEG + 2f, 9f, 6.5f, ORANGE)
    }

    override fun drawTorso(c: Canvas, rig: MonkeyRig) {
        suitTorso(c, rig, TEAL)
        c.save(); c.concat(rig.hips)
        // belt + emblem: an orange disc with a cream lightning bolt
        fill.shader = null; fill.color = ORANGE
        oval.set(-22f, -9f, 22f, -2f); c.drawRect(oval, fill); c.drawRect(oval, outline)
        c.drawCircle(1f, -29f, 12f, fill); c.drawCircle(1f, -29f, 12f, outline)
        fill.color = CREAM; c.drawPath(boltPath, fill)
        outline.strokeWidth = 1.2f; c.drawPath(boltPath, outline); outline.strokeWidth = OW
        c.restore()
    }

    override fun drawArms(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.armLUpper, rig.upperLen, 10f, TEAL, true); limb(c, rig.armLFore, rig.foreLen, 9f, TEAL, true)
        limb(c, rig.armRUpper, rig.upperLen, 10f, TEAL, true); limb(c, rig.armRFore, rig.foreLen, 9f, TEAL, true)
        limb(c, rig.armLUpper, rig.upperLen, 10f, TEAL, false); limb(c, rig.armLFore, rig.foreLen, 9f, TEAL, false)
        limb(c, rig.armRUpper, rig.upperLen, 10f, TEAL, false); limb(c, rig.armRFore, rig.foreLen, 9f, TEAL, false)
        blob(c, rig.armLFore, 0f, rig.foreLen + 2f, 7f, 7f, ORANGE)
        blob(c, rig.armRFore, 0f, rig.foreLen + 2f, 7f, 7f, ORANGE)
    }

    override fun drawHeadBase(c: Canvas, p: Pose, t: Float) {
        humanHead(c)
        // Domino mask with white eye holes (the pupils are drawn on top).
        fill.shader = null; fill.color = TEAL_DARK
        tmp.reset()
        oval.set(-26f, -51f, 1f, -31f); tmp.addOval(oval, Path.Direction.CW)
        oval.set(-1f, -51f, 26f, -31f); tmp.addOval(oval, Path.Direction.CW)
        c.drawPath(tmp, fill); outline.strokeWidth = 1.6f; c.drawPath(tmp, outline); outline.strokeWidth = OW
        // mask ties flutter at the side
        stroke.shader = null; stroke.color = TEAL_DARK; stroke.strokeWidth = 3f
        val w = 3f * sin(t * 6f)
        c.drawLine(-25f, -42f, -36f, -38f + w, stroke); c.drawLine(-25f, -42f, -35f, -46f + w, stroke)
        fill.color = Color.WHITE
        oval.set(-19f, -48f, -5f, -34f); c.drawOval(oval, fill)
        oval.set(5f, -48f, 19f, -34f); c.drawOval(oval, fill)
    }

    override fun drawHeadExtras(c: Canvas, p: Pose, t: Float) {
        super.drawHeadExtras(c, p, t)
        if (p.laser > 0.02f) {
            val a = p.laser.coerceIn(0f, 1f)
            fill.shader = null; fill.color = LASER; fill.alpha = (200 * a).toInt()
            c.drawCircle(-12f + p.lookX, -41f, 8f + 3f * abs(sin(t * 30f)), fill)
            c.drawCircle(12f + p.lookX, -41f, 8f + 3f * abs(sin(t * 30f)), fill)
            fill.color = Color.WHITE; fill.alpha = (255 * a).toInt()
            c.drawCircle(-12f + p.lookX, -41f, 3.5f, fill); c.drawCircle(12f + p.lookX, -41f, 3.5f, fill)
            fill.alpha = 255
        }
        if (p.breath > 0.02f) {
            fill.shader = null; fill.color = Color.argb((180 * p.breath.coerceIn(0f, 1f)).toInt(), 0xD8, 0xF2, 0xFF)
            oval.set(-5f, -20f, 5f, -10f); c.drawOval(oval, fill)
        }
    }

    /** Sleeping: the cape becomes a blanket tucked over the curled-up hero. */
    override fun drawFront(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        if (p.blanket < 0.05f) return
        c.save(); c.concat(rig.hips)
        fill.shader = null; fill.color = ORANGE; fill.alpha = (255 * p.blanket.coerceIn(0f, 1f)).toInt()
        tmp.reset()
        tmp.moveTo(-40f, -46f); tmp.cubicTo(-20f, -58f, 30f, -56f, 44f, -40f)
        tmp.cubicTo(50f, -18f, 40f, 12f, 30f, 16f); tmp.cubicTo(10f, 22f, -24f, 20f, -36f, 12f)
        tmp.cubicTo(-46f, -6f, -46f, -30f, -40f, -46f); tmp.close()
        c.drawPath(tmp, fill)
        stroke.shader = null; stroke.color = ORANGE_DARK; stroke.strokeWidth = 2f; stroke.alpha = fill.alpha
        c.drawLine(-28f, -30f, 30f, -26f, stroke); c.drawLine(-30f, -8f, 34f, -4f, stroke)
        fill.alpha = 255; stroke.alpha = 255
        c.drawPath(tmp, outline)
        c.restore()
    }
}

/**
 * Armour Anna (v4.1): an original armoured uncle, gunmetal + saffron plates, glowing teal chest core,
 * a rounded visor helmet that flips up to show a cheeky moustached face. Not red/gold, no faceplate copy.
 */
class ArmourRenderer : HumanRenderer() {
    companion object {
        val GUN = Color.rgb(0x4A, 0x52, 0x5C)
        val GUN_LIGHT = Color.rgb(0x76, 0x80, 0x8C)
        val GUN_DARK = Color.rgb(0x2C, 0x32, 0x3A)
        val SAFFRON = Color.rgb(0xFF, 0x99, 0x33)
        val CORE = Color.rgb(0x3D, 0xF5, 0xC8)
        val VISOR = Color.rgb(0x1C, 0x26, 0x30)
        val SHIELD = Color.rgb(0x7F, 0xE6, 0xFF)
    }
    override val skin = Color.rgb(0xB8, 0x7A, 0x50)
    override val skinShade = Color.rgb(0x8A, 0x55, 0x34)
    override val hair = Color.rgb(0x40, 0x3A, 0x36)
    /** Real phone battery percent, for the low-power gag (set by the view). */
    var batteryPct = -1
    /** v10.36 armour shed: 0 = suited, 1 = plates blown fully off (set by the view; he wears his inner clothes). */
    var shed = 0f
    private val VEST = Color.rgb(0xF2, 0xEE, 0xE4)
    private val LUNGI = Color.rgb(0x3E, 0x6E, 0xB8)

    /** Plates fly outward from the body (dx, dy in rig units at shed=1) with a spin. */
    private inline fun plate(c: Canvas, dx: Float, dy: Float, spin: Float, body: () -> Unit) {
        val k = shed.coerceIn(0f, 1f)
        if (k <= 0.001f) { body(); return }
        c.save(); c.translate(dx * k, dy * k); c.rotate(spin * k); body(); c.restore()
    }

    private fun innerLegs(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.legL, LEG, 12f, LUNGI, true); limb(c, rig.legR, LEG, 12f, LUNGI, true)
        limb(c, rig.legL, LEG, 12f, LUNGI, false); limb(c, rig.legR, LEG, 12f, LUNGI, false)
        blob(c, rig.legL, -2f, LEG + 2f, 9f, 5f, skinShade); blob(c, rig.legR, 2f, LEG + 2f, 9f, 5f, skinShade)
    }
    private fun innerTorso(c: Canvas, rig: MonkeyRig) {
        c.save(); c.concat(rig.hips); c.scale(1.02f, 1f)
        fill.shader = null; fill.color = VEST; c.drawPath(suit, fill); c.drawPath(suit, outline)
        c.restore()
    }
    private fun innerArms(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.armLUpper, rig.upperLen, 10f, skin, true); limb(c, rig.armLFore, rig.foreLen, 9f, skin, true)
        limb(c, rig.armRUpper, rig.upperLen, 10f, skin, true); limb(c, rig.armRFore, rig.foreLen, 9f, skin, true)
        limb(c, rig.armLUpper, rig.upperLen, 10f, skin, false); limb(c, rig.armLFore, rig.foreLen, 9f, skin, false)
        limb(c, rig.armRUpper, rig.upperLen, 10f, skin, false); limb(c, rig.armRFore, rig.foreLen, 9f, skin, false)
    }

    override fun drawLegs(c: Canvas, rig: MonkeyRig) {
        if (shed > 0.001f) innerLegs(c, rig)
        plate(c, -70f, 150f, -160f) { plateLegs(c, rig) }
    }
    private fun plateLegs(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.legL, LEG, 16f, GUN, true); limb(c, rig.legR, LEG, 16f, GUN, true)
        limb(c, rig.legL, LEG, 16f, GUN, false); limb(c, rig.legR, LEG, 16f, GUN, false)
        blob(c, rig.legL, 0f, LEG * 0.45f, 6f, 5f, SAFFRON)
        blob(c, rig.legR, 0f, LEG * 0.45f, 6f, 5f, SAFFRON)
        blob(c, rig.legL, -3f, LEG + 3f, 12f, 7.5f, GUN_DARK)
        blob(c, rig.legR, 3f, LEG + 3f, 12f, 7.5f, GUN_DARK)
    }

    override fun drawTorso(c: Canvas, rig: MonkeyRig) {
        if (shed > 0.001f) innerTorso(c, rig)
        plate(c, 40f, -180f, 200f) { plateTorso(c, rig) }
    }
    private fun plateTorso(c: Canvas, rig: MonkeyRig) {
        val p = pose
        c.save(); c.concat(rig.hips); c.scale(1.18f, 1f)
        fill.shader = null; fill.color = GUN; c.drawPath(suit, fill); c.drawPath(suit, outline)
        // saffron side plates + belt
        fill.color = SAFFRON
        oval.set(-21f, -44f, -13f, -14f); c.drawRoundRect(oval, 4f, 4f, fill); c.drawRoundRect(oval, 4f, 4f, outline)
        oval.set(13f, -44f, 21f, -14f); c.drawRoundRect(oval, 4f, 4f, fill); c.drawRoundRect(oval, 4f, 4f, outline)
        fill.color = GUN_DARK; oval.set(-20f, -9f, 20f, -2f); c.drawRect(oval, fill); c.drawRect(oval, outline)
        // shoulder rocket pods
        for (s in intArrayOf(-1, 1)) {
            fill.color = GUN_LIGHT; oval.set(s * 22f - 7f, SHOULDER_Y - 10f, s * 22f + 7f, SHOULDER_Y + 2f)
            c.drawRoundRect(oval, 3f, 3f, fill); c.drawRoundRect(oval, 3f, 3f, outline)
            fill.color = SAFFRON; c.drawCircle(s * 22f, SHOULDER_Y - 10f, 3f, fill)
        }
        // chest core: dims on low power, or blazes with Iron-style Unibeam chest thrust when flying/hovering!
        val dim = 1f - 0.75f * p.lowPower.coerceIn(0f, 1f) * (0.6f + 0.4f * abs(sin(now * 9f)))
        val flyBoost = maxOf(p.jets, p.palmGlow).coerceIn(0f, 1f)
        if (flyBoost > 0.05f) {
            val pulse = 1f + 0.22f * sin(now * 32f)
            fill.color = CORE; fill.alpha = (95 * flyBoost).toInt()
            c.drawCircle(0f, -30f, 22f * pulse, fill)
            // Downward/outward chest unibeam thrust cone
            tmp.reset()
            tmp.moveTo(-7f, -28f); tmp.quadTo(-4f, -4f, 0f, 14f * pulse); tmp.quadTo(4f, -4f, 7f, -28f); tmp.close()
            fill.alpha = (145 * flyBoost).toInt()
            c.drawPath(tmp, fill)
            // Cross star flare rays
            stroke.shader = null; stroke.color = Color.WHITE; stroke.strokeWidth = 2.2f; stroke.alpha = (210 * flyBoost).toInt()
            c.drawLine(-18f * pulse, -30f, 18f * pulse, -30f, stroke)
            c.drawLine(0f, -46f * pulse, 0f, -14f * pulse, stroke)
            stroke.alpha = 255
        }
        fill.color = CORE; fill.alpha = (110 * dim).toInt(); c.drawCircle(0f, -30f, 15f, fill)
        fill.alpha = (255 * dim).toInt(); c.drawCircle(0f, -30f, 8.5f, fill); fill.alpha = 255
        fill.color = Color.WHITE; fill.alpha = (230 * dim).toInt(); c.drawCircle(0f, -30f, 4.5f + 2f * flyBoost, fill); fill.alpha = 255
        c.drawCircle(0f, -30f, 8.5f, outline)
        c.restore()
    }

    override fun drawArms(c: Canvas, rig: MonkeyRig) {
        if (shed > 0.001f) innerArms(c, rig)
        plate(c, 170f, -60f, 260f) { plateArms(c, rig) }
    }
    private fun plateArms(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.armLUpper, rig.upperLen, 13f, GUN, true); limb(c, rig.armLFore, rig.foreLen, 12f, GUN, true)
        limb(c, rig.armRUpper, rig.upperLen, 13f, GUN, true); limb(c, rig.armRFore, rig.foreLen, 12f, GUN, true)
        limb(c, rig.armLUpper, rig.upperLen, 13f, GUN, false); limb(c, rig.armLFore, rig.foreLen, 12f, GUN, false)
        limb(c, rig.armRUpper, rig.upperLen, 13f, GUN, false); limb(c, rig.armRFore, rig.foreLen, 12f, GUN, false)
        blob(c, rig.armLFore, 0f, rig.foreLen * 0.55f, 7.5f, 5f, SAFFRON)
        blob(c, rig.armRFore, 0f, rig.foreLen * 0.55f, 7.5f, 5f, SAFFRON)
        blob(c, rig.armLFore, 0f, rig.foreLen + 2f, 8.5f, 8.5f, GUN_DARK)
        blob(c, rig.armRFore, 0f, rig.foreLen + 2f, 8.5f, 8.5f, GUN_DARK)
    }

    /** Helmet shell with an open face (visor drawn on top in drawHeadExtras). */
    override fun drawHeadBase(c: Canvas, p: Pose, t: Float) {
        fill.shader = null
        // ear discs
        for (s in intArrayOf(-1, 1)) {
            fill.color = GUN_DARK; c.drawCircle(s * 35f, -36f, 10f, fill); c.drawCircle(s * 35f, -36f, 10f, outline)
            fill.color = SAFFRON; c.drawCircle(s * 35f, -36f, 4f, fill)
        }
        fill.color = GUN; c.drawCircle(0f, HEAD_CY, HEAD_R + 1f, fill); c.drawCircle(0f, HEAD_CY, HEAD_R + 1f, outline)
        // saffron crest
        fill.color = SAFFRON; oval.set(-5f, -70f, 5f, -50f); c.drawRoundRect(oval, 4f, 4f, fill); c.drawRoundRect(oval, 4f, 4f, outline)
        // open face: skin
        fill.color = skin; oval.set(-26f, -54f, 26f, -4f); c.drawOval(oval, fill)
        outline.strokeWidth = 1.6f; c.drawOval(oval, outline); outline.strokeWidth = OW
        // grey uncle hair peeking at the brow
        fill.color = hair; oval.set(-20f, -56f, 20f, -49f); c.drawOval(oval, fill)
    }

    override fun drawHeadExtras(c: Canvas, p: Pose, t: Float) {
        paintStache(c, p, 0f, Color.rgb(0x3A, 0x30, 0x2C))
        super.drawHeadExtras(c, p, t)
        // Visor: hinged at the crown; 0 = down over the face, 1 = flipped up.
        val up = p.visor.coerceIn(0f, 1f)
        c.save(); c.translate(0f, -62f); c.scale(1f, 1f - 0.82f * up); c.translate(0f, 62f - 4f * up)
        fill.shader = null; fill.color = VISOR
        oval.set(-27f, -56f, 27f, -6f); c.drawRoundRect(oval, 20f, 20f, fill); c.drawRoundRect(oval, 20f, 20f, outline)
        if (up < 0.6f) {
            val lp = 1f - 0.7f * p.lowPower.coerceIn(0f, 1f)
            stroke.shader = null; stroke.color = SAFFRON; stroke.alpha = (255 * lp * (1f - up)).toInt(); stroke.strokeWidth = 4f
            c.drawLine(-18f, -38f, -5f, -36f, stroke); c.drawLine(5f, -36f, 18f, -38f, stroke)
            stroke.alpha = 255
            stroke.color = GUN_LIGHT; stroke.strokeWidth = 2f; c.drawLine(-14f, -16f, 14f, -16f, stroke)
        }
        c.restore()
    }

    override fun drawFront(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        if (p.shield > 0.02f) {
            c.save(); c.concat(rig.root)
            val a = p.shield.coerceIn(0f, 1f); val r = 98f * (0.6f + 0.4f * a)
            fill.shader = null; fill.color = SHIELD; fill.alpha = (60 * a).toInt(); c.drawCircle(0f, -80f, r, fill)
            stroke.shader = null; stroke.color = SHIELD; stroke.alpha = (220 * a).toInt(); stroke.strokeWidth = 3f
            c.drawCircle(0f, -80f, r, stroke)
            stroke.strokeWidth = 1.4f; stroke.alpha = (110 * a).toInt()
            for (i in 0 until 6) { val ang = i * 1.047f + t * 0.6f; c.drawLine(0f, -80f, kotlin.math.cos(ang) * r, -80f + sin(ang) * r, stroke) }
            stroke.alpha = 255; fill.alpha = 255
            c.restore()
        }
        if (p.hud > 0.02f) {
            c.save(); c.concat(rig.head)
            val a = p.hud.coerceIn(0f, 1f)
            stroke.shader = null; stroke.color = CORE; stroke.alpha = (230 * a).toInt(); stroke.strokeWidth = 2.2f
            val r = 52f
            for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                val x = sx * r; val y = HEAD_CY + sy * r
                c.drawLine(x, y, x - sx * 12f, y, stroke); c.drawLine(x, y, x, y - sy * 12f, stroke)
            }
            val scan = HEAD_CY - r + 2 * r * ((t * 0.7f) % 1f)
            stroke.alpha = (140 * a).toInt(); c.drawLine(-r, scan, r, scan, stroke)
            fxText.textSize = 9f; fxText.color = CORE; fxText.alpha = (230 * a).toInt()
            c.drawText("SCAN", r - 10f, HEAD_CY - r - 4f, fxText); fxText.alpha = 255; stroke.alpha = 255
            c.restore()
        }
        if (p.lowPower > 0.05f) {
            c.save(); c.concat(rig.root); c.translate(0f, -190f)
            val a = (255 * p.lowPower.coerceIn(0f, 1f)).toInt()
            fill.shader = null; fill.color = Color.WHITE; fill.alpha = a
            oval.set(-18f, -9f, 18f, 9f); c.drawRoundRect(oval, 3f, 3f, fill)
            outline.alpha = a; c.drawRoundRect(oval, 3f, 3f, outline); outline.alpha = 255
            fill.color = OUTLINE; fill.alpha = a; oval.set(18f, -4f, 22f, 4f); c.drawRect(oval, fill)
            val lvl = if (batteryPct in 0..100) batteryPct / 100f else 0.12f
            val blink = if ((t * 3f).toInt() % 2 == 0) 1f else 0.4f
            fill.color = Color.rgb(0xFF, 0x4A, 0x3A); fill.alpha = (a * blink).toInt()
            oval.set(-15f, -6f, -15f + 30f * lvl.coerceIn(0.05f, 1f), 6f); c.drawRect(oval, fill)
            fill.alpha = 255
            c.restore()
        }
    }
}

/**
 * Mask Man (v4.1): a lanky, original carnival-masked comedy hero (not The Mask). Every accessory is a
 * wardrobe item and simply isn't drawn when switched off.
 */
class MaskManRenderer : HumanRenderer() {
    companion object {
        val SHIRT = Color.rgb(0x7B, 0x4B, 0xC8)
        val SHIRT_DARK = Color.rgb(0x52, 0x2E, 0x8E)
        val MASK = Color.rgb(0xE8, 0x3E, 0x8C)
        val MASK_GOLD = Color.rgb(0xF6, 0xC3, 0x2E)
        val HAIR = Color.rgb(0xF2, 0x6B, 0x1F)
        val PANTS = Color.rgb(0x2E, 0x55, 0x8C)
        val SOCK_A = Color.rgb(0xE8, 0x3A, 0x3A)
        val SOCK_B = Color.rgb(0x3A, 0xC4, 0x6A)
        val GLOVE = Color.rgb(0xFA, 0xFA, 0xF5)
        val BOOT = Color.rgb(0x6B, 0x3F, 0x22)
        val BELT = Color.rgb(0x8A, 0x5A, 0x2A)
        val CROW = Color.rgb(0x22, 0x22, 0x2A)
        val CHICKEN = Color.rgb(0xFF, 0xD8, 0x3A)
        val UMBRELLA = Color.rgb(0x2E, 0xB8, 0xC8)
        val BOARD = Color.rgb(0xE8, 0x4A, 0x2A)
        val SCARF_YELLOW = Color.rgb(0xF6, 0xC3, 0x2E)
        private val GADGETS = arrayOf("plunger", "duck", "fish", "piano", "sofa")
    }
    override val skin = Color.rgb(0xE6, 0xB8, 0x8A)
    override val skinShade = Color.rgb(0xB8, 0x86, 0x5E)
    override val hair = HAIR
    private val tuftPath = Path().apply {
        moveTo(-14f, -64f); lineTo(-20f, -86f); lineTo(-8f, -72f); lineTo(-4f, -94f); lineTo(4f, -72f)
        lineTo(14f, -90f); lineTo(14f, -68f); lineTo(26f, -78f); lineTo(16f, -60f); close()
    }

    override fun drawBehind(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        if (p.skate > 0.05f && has("skate")) {
            c.save(); c.concat(rig.root)
            fill.shader = null; fill.color = BOARD
            oval.set(-34f, -6f, 34f, 1f); c.drawRoundRect(oval, 4f, 4f, fill); c.drawRoundRect(oval, 4f, 4f, outline)
            fill.color = OUTLINE; c.drawCircle(-22f, 4f, 4.5f, fill); c.drawCircle(22f, 4f, 4.5f, fill)
            fill.color = Color.rgb(0xCC, 0xCC, 0xCC); c.drawCircle(-22f, 4f, 2f, fill); c.drawCircle(22f, 4f, 2f, fill)
            c.restore()
        }
        // The cape that's far too small: a little flapping triangle at the shoulders.
        if (has("cape")) {
            c.save(); c.concat(rig.hips)
            val w = 4f * sin(t * 7f) + 10f * p.capeWind + (anim?.appendageAngle(com.combo.monkeycompanion.engine.anim.Appendage.CAPE) ?: 0f) * 0.25f
            fill.shader = null; fill.color = Color.rgb(0x2E, 0xB8, 0x6A)
            tmp.reset(); tmp.moveTo(-14f, SHOULDER_Y - 2f); tmp.lineTo(14f, SHOULDER_Y - 2f); tmp.lineTo(8f + w, SHOULDER_Y + 20f); tmp.lineTo(-10f + w, SHOULDER_Y + 18f); tmp.close()
            c.drawPath(tmp, fill); c.drawPath(tmp, outline)
            c.restore()
        }
    }

    override fun drawLegs(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.legL, LEG, 8f, PANTS, true); limb(c, rig.legR, LEG, 8f, PANTS, true)
        limb(c, rig.legL, LEG, 8f, PANTS, false); limb(c, rig.legR, LEG, 8f, PANTS, false)
        // mismatched socks: red stripes left, green dots right
        c.save(); c.concat(rig.legL); fill.shader = null; fill.color = SOCK_A
        oval.set(-5f, LEG * 0.55f, 5f, LEG); c.drawRect(oval, fill)
        fill.color = Color.WHITE; for (i in 0..2) { oval.set(-5f, LEG * 0.6f + i * 4f, 5f, LEG * 0.6f + i * 4f + 1.6f); c.drawRect(oval, fill) }
        c.restore()
        c.save(); c.concat(rig.legR); fill.color = SOCK_B
        oval.set(-5f, LEG * 0.55f, 5f, LEG); c.drawRect(oval, fill)
        fill.color = Color.WHITE; c.drawCircle(-1.5f, LEG * 0.68f, 1.3f, fill); c.drawCircle(1.8f, LEG * 0.85f, 1.3f, fill)
        c.restore()
        if (has("boots")) {
            blob(c, rig.legL, -5f, LEG + 3f, 13f, 7f, BOOT); blob(c, rig.legR, 5f, LEG + 3f, 13f, 7f, BOOT)
        } else {
            blob(c, rig.legL, -3f, LEG + 2f, 9f, 5f, SOCK_A); blob(c, rig.legR, 3f, LEG + 2f, 9f, 5f, SOCK_B)
        }
    }

    override fun drawTorso(c: Canvas, rig: MonkeyRig) {
        suitTorso(c, rig, SHIRT, 0.78f)
        c.save(); c.concat(rig.hips)
        fill.shader = null; fill.color = MASK_GOLD
        for (i in 0..2) c.drawCircle(0f, -46f + i * 12f, 2.4f, fill)
        c.restore()
    }

    override fun drawTorsoExtras(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        if (has("belt")) {
            c.save(); c.concat(rig.hips)
            fill.shader = null; fill.color = BELT
            oval.set(-19f, -11f, 19f, -3f); c.drawRect(oval, fill); c.drawRect(oval, outline)
            fill.color = MASK_GOLD; oval.set(-4f, -12f, 4f, -2f); c.drawRect(oval, fill); c.drawRect(oval, outline)
            fill.color = Color.rgb(0x6A, 0x42, 0x1C)
            for (x in floatArrayOf(-15f, 9f)) { oval.set(x, -10f, x + 7f, 0f); c.drawRoundRect(oval, 2f, 2f, fill); c.drawRoundRect(oval, 2f, 2f, outline) }
            c.restore()
        }
        if (has("scarf")) paintScarf(c, rig, SCARF_YELLOW, t)
    }

    override fun drawArms(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.armLUpper, rig.upperLen, 8f, SHIRT, true); limb(c, rig.armLFore, rig.foreLen, 7f, SHIRT, true)
        limb(c, rig.armRUpper, rig.upperLen, 8f, SHIRT, true); limb(c, rig.armRFore, rig.foreLen, 7f, SHIRT, true)
        limb(c, rig.armLUpper, rig.upperLen, 8f, SHIRT, false); limb(c, rig.armLFore, rig.foreLen, 7f, SHIRT_DARK, false)
        limb(c, rig.armRUpper, rig.upperLen, 8f, SHIRT, false); limb(c, rig.armRFore, rig.foreLen, 7f, SHIRT_DARK, false)
        // oversized gloves
        blob(c, rig.armLFore, 0f, rig.foreLen + 4f, 10.5f, 10f, GLOVE)
        blob(c, rig.armRFore, 0f, rig.foreLen + 4f, 10.5f, 10f, GLOVE)
    }

    override fun drawHeadBase(c: Canvas, p: Pose, t: Float) {
        // wild hair tuft (behind the head)
        fill.shader = null; fill.color = HAIR
        c.save(); c.rotate(4f * sin(t * 3f), 0f, -64f)
        c.drawPath(tuftPath, fill); outline.strokeWidth = 1.6f; c.drawPath(tuftPath, outline); outline.strokeWidth = OW
        c.restore()
        // a long-ish face
        c.save(); c.scale(0.92f, 1.06f, 0f, -34f); humanHead(c, withHair = false); c.restore()
        if (has("mask")) {
            // big carnival mask: magenta with gold trim and curly tips
            fill.color = MASK
            tmp.reset()
            tmp.moveTo(-36f, -52f); tmp.cubicTo(-30f, -64f, -8f, -60f, 0f, -52f); tmp.cubicTo(8f, -60f, 30f, -64f, 36f, -52f)
            tmp.cubicTo(36f, -36f, 28f, -26f, 14f, -27f); tmp.cubicTo(6f, -28f, 3f, -34f, 0f, -34f)
            tmp.cubicTo(-3f, -34f, -6f, -28f, -14f, -27f); tmp.cubicTo(-28f, -26f, -36f, -36f, -36f, -52f); tmp.close()
            c.drawPath(tmp, fill); c.drawPath(tmp, outline)
            stroke.shader = null; stroke.color = MASK_GOLD; stroke.strokeWidth = 2.4f
            tmp.reset(); tmp.moveTo(-36f, -52f); tmp.cubicTo(-46f, -60f, -48f, -46f, -40f, -46f); c.drawPath(tmp, stroke)
            tmp.reset(); tmp.moveTo(36f, -52f); tmp.cubicTo(46f, -60f, 48f, -46f, 40f, -46f); c.drawPath(tmp, stroke)
        }
    }

    /** With the mask on: huge cartoon eyes that change shape with mood, and can pop out on stalks. */
    private val plainGlint = floatArrayOf(12f, -41f, 1f)
    override val glintEye: FloatArray? get() = if (has("mask")) null else plainGlint

    override fun drawEyes(c: Canvas, p: Pose) {
        if (!has("mask")) { super.drawEyes(c, p); return }
        val pop = p.eyePop.coerceIn(0f, 1f)
        for (s in intArrayOf(-1, 1)) {
            val bx = s * 15f; val by = -44f
            val ex = bx + s * 10f * pop; val ey = by - 26f * pop
            if (pop > 0.05f) { stroke.shader = null; stroke.color = Color.rgb(0xE8, 0x9A, 0x9A); stroke.strokeWidth = 3f; c.drawLine(bx, by, ex, ey, stroke) }
            val big = 1f + 0.5f * pop
            var rx = 10f * big; var ry = 12f * big * p.eyeOpen.coerceIn(0.08f, 1.5f)
            if (p.eyesHappy > 0.5f) {
                stroke.color = OUTLINE; stroke.strokeWidth = 3.4f
                tmp.reset(); tmp.moveTo(ex - 9f, ey + 3f); tmp.quadTo(ex, ey - 10f, ex + 9f, ey + 3f); c.drawPath(tmp, stroke); continue
            }
            if (ry < 2f) { stroke.color = OUTLINE; stroke.strokeWidth = 3f; c.drawLine(ex - 9f, ey, ex + 9f, ey, stroke); continue }
            fill.shader = null; fill.color = Color.WHITE
            oval.set(ex - rx, ey - ry, ex + rx, ey + ry); c.drawOval(oval, fill); c.drawOval(oval, outline)
            // mood lids: angry = slanted top, sad = droopy outer corner
            if (pop > 0.05f) Unit
            else if (p.anger > 0.2f || p.browAngle > 12f) {
                fill.color = MASK; tmp.reset(); tmp.moveTo(ex - rx - 1f, ey - ry - 1f); tmp.lineTo(ex + rx + 1f, ey - ry - 1f)
                if (s < 0) tmp.lineTo(ex + rx + 1f, ey - ry * 0.1f) else tmp.lineTo(ex - rx - 1f, ey - ry * 0.1f)
                tmp.close(); c.drawPath(tmp, fill)
            } else if (p.browAngle < -8f) {
                fill.color = MASK; tmp.reset(); tmp.moveTo(ex - rx - 1f, ey - ry - 1f); tmp.lineTo(ex + rx + 1f, ey - ry - 1f)
                if (s < 0) tmp.lineTo(ex - rx - 1f, ey - ry * 0.2f) else tmp.lineTo(ex + rx + 1f, ey - ry * 0.2f)
                tmp.close(); c.drawPath(tmp, fill)
            }
            fill.color = OUTLINE
            val pr = 4.8f * big
            c.drawCircle(ex + p.lookX * 0.9f, ey + p.lookY * 0.9f + 2f, pr, fill)
            fill.color = Color.WHITE; c.drawCircle(ex + p.lookX * 0.9f + 1.6f, ey + p.lookY * 0.9f, 1.6f, fill)
        }
    }

    override fun drawHeadExtras(c: Canvas, p: Pose, t: Float) {
        if (has("stache")) paintStache(c, p, p.stacheOff, Color.rgb(0x2A, 0x1A, 0x10))
        if (has("shades") && p.eyePop < 0.3f) { val o = p.shades; p.shades = 1f; drawShades(c, p); p.shades = o }
        if (has("hat") && p.hatOff < 0.5f) { val k = wear?.option("hat") ?: "top"; paintHat(c, k, if (k == "lungi") -76f else -66f, 1f) }
    }

    override fun drawFront(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        // banana phone at the ear
        if (p.bananaPhone > 0.05f && has("bananaphone")) {
            c.save(); c.concat(rig.armRFore); c.translate(0f, rig.foreLen + 4f); c.rotate(70f); c.scale(1.4f, 1.4f)
            fill.shader = null; fill.color = BANANA
            tmp.reset(); tmp.moveTo(-12f, -3f); tmp.quadTo(0f, 10f, 12f, -3f); tmp.quadTo(0f, 3f, -12f, -3f); tmp.close()
            c.drawPath(tmp, fill); outline.strokeWidth = 1.4f; c.drawPath(tmp, outline); outline.strokeWidth = OW
            c.restore()
        }
        // rubber chicken in the right hand, squished by [Pose.squeak]
        if (p.chicken > 0.05f && has("chicken")) {
            c.save(); c.concat(rig.armRFore); c.translate(0f, rig.foreLen + 6f)
            val sq = p.squeak.coerceIn(0f, 1f)
            c.scale(1f + 0.25f * sq, 1f - 0.3f * sq)
            fill.shader = null; fill.color = CHICKEN
            oval.set(-8f, 0f, 8f, 26f); c.drawOval(oval, fill); c.drawOval(oval, outline)
            c.drawCircle(0f, 30f, 6f, fill); c.drawCircle(0f, 30f, 6f, outline)
            fill.color = Color.rgb(0xE8, 0x3A, 0x3A); c.drawCircle(-2f, 37f, 3f, fill); c.drawCircle(2f, 37f, 3f, fill)
            fill.color = Color.rgb(0xFF, 0x8A, 0x1A); tmp.reset(); tmp.moveTo(4f, 30f); tmp.lineTo(11f, 31f); tmp.lineTo(4f, 33f); tmp.close(); c.drawPath(tmp, fill)
            fill.color = OUTLINE; c.drawCircle(2f, 29f, 1.2f, fill)
            c.restore()
            if (sq > 0.4f) {
                rig.handPoint(false, pts)
                fxText.textSize = 15f * rig.scale; fxText.color = Color.rgb(0xE8, 0x3A, 0x3A)
                c.drawText("SQUEAK!", pts[0] + 20f * rig.scale * rig.facingSign, pts[1] - 24f * rig.scale, fxText)
            }
        }
        // umbrella held high
        if (p.umbrella > 0.05f && has("umbrella")) {
            c.save(); c.concat(rig.armRFore); c.translate(0f, rig.foreLen + 4f)
            stroke.shader = null; stroke.color = OUTLINE; stroke.strokeWidth = 2.4f
            c.drawLine(0f, 0f, 0f, 46f, stroke)
            c.translate(0f, 46f)
            val k = p.umbrella.coerceIn(0f, 1f)
            fill.shader = null; fill.color = UMBRELLA
            oval.set(-48f * k, -28f, 48f * k, 28f); c.drawArc(oval, 0f, 180f, true, fill)
            c.drawArc(oval, 0f, 180f, true, outline)
            stroke.color = Color.WHITE; stroke.strokeWidth = 2f
            c.drawLine(0f, 0f, 0f, 28f, stroke); c.drawLine(0f, 0f, -26f * k, 22f, stroke); c.drawLine(0f, 0f, 26f * k, 22f, stroke)
            c.restore()
        }
        // something absurd pulled out of the belt (left hand)
        if (p.beltPull > 0.05f && has("belt")) drawGadget(c, rig, p)
        // the pet crow perched on the shoulder (unless it's off stealing the hat)
        if (has("crow") && p.fly < 0.05f) {
            c.save(); c.concat(rig.hips); c.translate(-20f, SHOULDER_Y - 8f); c.scale(-1f, 1f)
            paintCrow(c, t, perched = true)
            c.restore()
        }
    }

    private fun drawGadget(c: Canvas, rig: MonkeyRig, p: Pose) {
        val k = p.beltPull.coerceIn(0f, 1f)
        c.save(); c.concat(rig.armLFore); c.translate(0f, rig.foreLen + 8f); c.scale(k, k)
        fill.shader = null
        when (GADGETS[(p.beltItem.toInt().coerceAtLeast(0)) % GADGETS.size]) {
            "plunger" -> { stroke.shader = null; stroke.color = WOOD; stroke.strokeWidth = 4f; c.drawLine(0f, -6f, 0f, 22f, stroke)
                fill.color = Color.rgb(0xD8, 0x2A, 0x2E); oval.set(-10f, 20f, 10f, 32f); c.drawArc(oval, 180f, 180f, true, fill); c.drawArc(oval, 180f, 180f, true, outline) }
            "duck" -> { fill.color = CHICKEN; oval.set(-10f, 0f, 10f, 14f); c.drawOval(oval, fill); c.drawOval(oval, outline)
                c.drawCircle(6f, -2f, 6f, fill); c.drawCircle(6f, -2f, 6f, outline); fill.color = Color.rgb(0xFF, 0x8A, 0x1A); oval.set(10f, -3f, 17f, 1f); c.drawOval(oval, fill) }
            "fish" -> { fill.color = Color.rgb(0x8A, 0xB4, 0xD8); oval.set(-14f, 0f, 10f, 12f); c.drawOval(oval, fill); c.drawOval(oval, outline)
                tmp.reset(); tmp.moveTo(10f, 6f); tmp.lineTo(18f, 0f); tmp.lineTo(18f, 12f); tmp.close(); c.drawPath(tmp, fill); c.drawPath(tmp, outline) }
            "piano" -> { fill.color = OUTLINE; oval.set(-14f, 0f, 14f, 14f); c.drawRect(oval, fill)
                fill.color = Color.WHITE; for (i in 0..5) { oval.set(-12f + i * 4.2f, 6f, -9f + i * 4.2f, 13f); c.drawRect(oval, fill) } }
            else -> { fill.color = Color.rgb(0xC8, 0x5A, 0x8A); oval.set(-16f, 4f, 16f, 14f); c.drawRoundRect(oval, 3f, 3f, fill); c.drawRoundRect(oval, 3f, 3f, outline)
                oval.set(-16f, -4f, -10f, 14f); c.drawRoundRect(oval, 2f, 2f, fill); oval.set(10f, -4f, 16f, 14f); c.drawRoundRect(oval, 2f, 2f, fill) }
        }
        c.restore()
    }

    /** A small cartoon crow in local space (beak to +x). */
    private fun paintCrow(c: Canvas, t: Float, perched: Boolean) {
        fill.shader = null; fill.color = CROW
        oval.set(-9f, -7f, 7f, 5f); c.drawOval(oval, fill)
        c.drawCircle(7f, -7f, 5f, fill)
        fill.color = Color.rgb(0xF2, 0xB2, 0x1C); tmp.reset(); tmp.moveTo(11f, -8f); tmp.lineTo(17f, -6f); tmp.lineTo(11f, -5f); tmp.close(); c.drawPath(tmp, fill)
        fill.color = Color.WHITE; c.drawCircle(8f, -8f, 1.6f, fill); fill.color = OUTLINE; c.drawCircle(8.4f, -8f, 0.8f, fill)
        fill.color = CROW
        val flap = if (perched) 0.2f * sin(t * 2f) else sin(t * 28f)
        tmp.reset(); tmp.moveTo(-4f, -4f); tmp.lineTo(-12f, -4f - 10f * flap); tmp.lineTo(2f, -3f); tmp.close(); c.drawPath(tmp, fill)
        tmp.reset(); tmp.moveTo(-9f, -1f); tmp.lineTo(-16f, 2f); tmp.lineTo(-9f, 3f); tmp.close(); c.drawPath(tmp, fill)
    }

    /** "Chase the fly" becomes the pet crow flapping round the head, and stealing the hat. */
    override fun drawFly(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        if (!has("crow")) { super.drawFly(c, rig, p, t); return }
        c.save(); c.concat(rig.root); c.translate(p.flyX, p.flyY); c.scale(1.5f, 1.5f)
        paintCrow(c, t, perched = false)
        if (has("hat") && p.hatOff > 0.5f) { c.save(); c.translate(0f, 12f); c.scale(0.45f, 0.45f); c.rotate(-15f); paintHat(c, wear?.option("hat") ?: "top", 0f, 1f); c.restore() }
        c.restore()
    }
}
