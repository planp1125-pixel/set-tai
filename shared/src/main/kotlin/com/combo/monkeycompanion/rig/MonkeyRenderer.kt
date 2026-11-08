package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.EAR_R
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.EAR_X
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.EAR_Y
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.HEAD_CY
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.HEAD_R
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.LEG
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.TAIL_SEGS
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Draws the monkey from vector shapes using the matrices computed by [MonkeyRig]. */
open class MonkeyRenderer {
    companion object {
        /** v10.11 default head box in head space: ears/hair/hat tuft included (HEAD_R 34, ears out to 46, tuft to -86). */
        val FACE_BOX = floatArrayOf(-54f, -94f, 54f, 6f)
        private val GOLD_SPARK = android.graphics.Color.rgb(0xFF, 0xD5, 0x4A)
        private val DIYA_CLAY = android.graphics.Color.rgb(0xB5, 0x55, 0x2B)
        private val DIYA_FLAME = android.graphics.Color.rgb(0xFF, 0x9A, 0x1F)
        val FUR = Color.rgb(0x8E, 0x5B, 0x2E)
        val FUR_LIGHT = Color.rgb(0xB0, 0x7A, 0x45)
        val FUR_DARK = Color.rgb(0x6A, 0x40, 0x1E)
        val FACE = Color.rgb(0xF4, 0xD7, 0xAE)
        val FACE_LIGHT = Color.rgb(0xFF, 0xEB, 0xCF)
        val FACE_SHADE = Color.rgb(0xE0, 0xB4, 0x84)
        val EAR_IN = Color.rgb(0xE9, 0xA9, 0x8E)
        val OUTLINE = Color.rgb(0x3A, 0x22, 0x10)
        val EYE = Color.rgb(0x24, 0x16, 0x0C)
        val MOUTH = Color.rgb(0x7A, 0x24, 0x2A)
        val TONGUE = Color.rgb(0xF0, 0x74, 0x74)
        val BLUSH = Color.rgb(0xFF, 0x86, 0x86)
        val ANGER_RED = Color.rgb(0xFF, 0x2A, 0x1A)
        val CLOTH = Color.rgb(0x5C, 0x9C, 0xE6)
        val CLOTH_DARK = Color.rgb(0x3A, 0x6E, 0xB0)
        val SWEAT = Color.rgb(0x7F, 0xC8, 0xFF)
        const val OW = 2.6f // outline width, rig units
        val BANANA = Color.rgb(0xFF, 0xD3, 0x3A)
        val BANANA_DARK = Color.rgb(0xC9, 0x9A, 0x12)
        val GUITAR = Color.rgb(0xE0, 0x4A, 0x36)
        val CAPE_RED = Color.rgb(0xD8, 0x2A, 0x2E)
        val CAPE_DARK = Color.rgb(0x8E, 0x14, 0x1C)
        val BADGE_GOLD = Color.rgb(0xF2, 0xB2, 0x1C)
        val JET_ORANGE = Color.rgb(0xFF, 0x7A, 0x1A)
        val JET_YELLOW = Color.rgb(0xFF, 0xE2, 0x5A)
        val NOZZLE = Color.rgb(0x6B, 0x72, 0x80)
        val GOGGLE_RIM = Color.rgb(0x2E, 0x35, 0x40)
        val GOGGLE_LENS = Color.rgb(0x6E, 0xF0, 0x9A)
        val WHEEL = Color.rgb(0x33, 0x33, 0x3D)
        val NOTE_COVER = Color.rgb(0x3F, 0x6F, 0xC8)
        val SOOT = Color.rgb(0x2A, 0x26, 0x24)
        val GUITAR_DARK = Color.rgb(0x8C, 0x2A, 0x1C)
        val WOOD = Color.rgb(0x6B, 0x45, 0x25)
        val PHONE = Color.rgb(0x22, 0x26, 0x30)
        val SCREEN = Color.rgb(0x7F, 0xC8, 0xFF)
        val SCARF_RED = Color.rgb(0xE0, 0x3C, 0x4A)
        // v4 show-off colours
        val BOLT = Color.rgb(0xBF, 0xE8, 0xFF)
        val BOLT_CORE = Color.rgb(0xFF, 0xFF, 0xF2)
        val NINJA_BAND = Color.rgb(0x26, 0x2A, 0x36)
        val NINJA_RED = Color.rgb(0xD8, 0x2A, 0x2E)
        val ICE = Color.rgb(0xB8, 0xE6, 0xFF)
        val ICE_DEEP = Color.rgb(0x6C, 0xB6, 0xF0)
        val HAT = Color.rgb(0x1E, 0x1E, 0x26)
        val RABBIT = Color.rgb(0xFA, 0xFA, 0xFA)
        val STEAM = Color.rgb(0xF2, 0xF2, 0xF2)
        // Constant arrays (no per-frame allocation in draw)
        private val SIDES = intArrayOf(-1, 1)
        private val SWEAT_X = floatArrayOf(-27f, 25f, 31f)
    }

    protected val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = OUTLINE; strokeWidth = OW
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    protected val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    protected val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    protected val fxText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }

    // Soft shading: radial gradients in part-local space, lit from the upper left.
    private val headShader = RadialGradient(-14f, -52f, 54f, intArrayOf(FUR_LIGHT, FUR, FUR_DARK), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    private val bodyShader = RadialGradient(-10f, -44f, 56f, intArrayOf(FUR_LIGHT, FUR, FUR_DARK), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    private val faceShader = RadialGradient(-6f, -40f, 36f, intArrayOf(FACE_LIGHT, FACE, FACE_SHADE), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    private val bellyShader = RadialGradient(-5f, -32f, 28f, intArrayOf(FACE_LIGHT, FACE, FACE_SHADE), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)

    private val torso = Path().apply {
        moveTo(0f, -61f)
        cubicTo(17f, -61f, 26f, -44f, 28f, -22f)
        cubicTo(30f, -2f, 17f, 6f, 0f, 6f)
        cubicTo(-17f, 6f, -30f, -2f, -28f, -22f)
        cubicTo(-26f, -44f, -17f, -61f, 0f, -61f)
        close()
    }
    private val belly = Path().apply { addOval(RectF(-18f, -48f, 18f, 0f), Path.Direction.CW) }
    private val faceMask = Path().apply {
        val a = Path().apply { addCircle(-12f, -40f, 14.5f, Path.Direction.CW) }
        val b = Path().apply { addCircle(12f, -40f, 14.5f, Path.Direction.CW) }
        val m = Path().apply { addOval(RectF(-24f, -37f, 24f, -3f), Path.Direction.CW) }
        op(a, b, Path.Op.UNION); op(m, Path.Op.UNION)
    }
    private val tuft = Path().apply {
        moveTo(-7f, -65f)
        cubicTo(-10f, -79f, 8f, -84f, 8f, -73f)
        cubicTo(5f, -77f, 0f, -75f, 3f, -66f)
        close()
    }
    // v3.2 prop shapes, built once (rig units)
    private val bananaPath = Path().apply {
        moveTo(-11f, -3f); quadTo(0f, 9f, 11f, -3f); quadTo(0f, 3f, -11f, -3f); close()
    }
    private val peelPetal = Path().apply {
        moveTo(0f, 0f); cubicTo(-4f, -3f, -9f, -2f, -12f, 2f); cubicTo(-8f, 1f, -4f, 2f, 0f, 2f); close()
    }
    protected val tmp = Path()
    protected val pts = FloatArray(2)
    protected val oval = RectF()

    // ---------------- v4.1 character hooks ----------------
    /** The pose / time being drawn (for part hooks that need them). */
    protected var pose: Pose = Pose(); protected var now = 0f
    /** v4.1 wardrobe of the character being drawn (null = defaults: nothing optional). */
    var wear: com.combo.monkeycompanion.character.Wear? = null
    /** v10.7 Disney-principles engine outputs (null = "Smooth animation" off: exact previous drawing). */
    var anim: com.combo.monkeycompanion.engine.anim.AnimEngine? = null
    protected fun has(id: String) = wear?.has(id) == true
    protected open val capeFill: Int get() = CAPE_RED
    protected open val capeShade: Int get() = CAPE_DARK
    /** Behind everything (jetpacks, wings...). */
    protected open fun drawBehind(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {}
    /** Over the torso, under the arms (scarf, belt, emblem...). */
    protected open fun drawTorsoExtras(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) { if (has("scarf")) paintScarf(c, rig, SCARF_RED, t) }
    /** In front of everything (shield bubble, HUD, gadgets...). */
    protected open fun drawFront(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {}
    /** Head base in head space: ears, hair, skull, face. Default: the monkey. */
    protected open fun drawHeadBase(c: Canvas, p: Pose, t: Float) = monkeyHeadBase(c, p)
    protected open fun drawNose(c: Canvas, p: Pose) {
        fill.color = EYE
        c.drawCircle(-2.8f + p.lookX * 0.7f, -27f, 1.5f, fill)
        c.drawCircle(2.8f + p.lookX * 0.7f, -27f, 1.5f, fill)
    }
    /** Last in head space (hats, sunglasses, visors). Default: the wardrobe hat / shades. */
    protected open fun drawHeadExtras(c: Canvas, p: Pose, t: Float) {
        if (has("shades") && p.shades < 0.5f) { val o = p.shades; p.shades = 1f; drawShades(c, p); p.shades = o }
        if (has("hat") && p.hatOff < 0.5f) paintHat(c, wear?.option("hat") ?: "top", -60f, 1f)
    }

    /** A hat sitting on the head at [y] (head space). Kinds: top, party, lungi (turban). */
    protected fun paintHat(c: Canvas, kind: String, y: Float, k: Float) {
        c.save(); c.translate(0f, y); c.scale(k, k)
        when (kind) {
            "party" -> {
                tmp.reset(); tmp.moveTo(-16f, 2f); tmp.lineTo(0f, -34f); tmp.lineTo(16f, 2f); tmp.close()
                fill.shader = null; fill.color = Color.rgb(0x8E, 0x5B, 0xE8); c.drawPath(tmp, fill)
                stroke.shader = null; stroke.color = BANANA; stroke.strokeWidth = 3f
                c.drawLine(-10f, -10f, 6f, -2f, stroke); c.drawLine(-5f, -22f, 10f, -12f, stroke)
                c.drawPath(tmp, outline)
                fill.color = Color.rgb(0xFF, 0x6B, 0x9A); c.drawCircle(0f, -35f, 4.5f, fill); c.drawCircle(0f, -35f, 4.5f, outline)
            }
            "lungi" -> {
                // A wrapped cloth turban in lungi checks, knot at the side.
                fill.shader = null; fill.color = Color.rgb(0x2E, 0x7D, 0x5A)
                oval.set(-30f, -16f, 30f, 8f); c.drawOval(oval, fill)
                stroke.shader = null; stroke.color = Color.rgb(0xF5, 0xF0, 0xE0); stroke.strokeWidth = 2f
                for (i in -2..2) c.drawLine(i * 10f, -14f, i * 10f + 4f, 6f, stroke)
                c.drawLine(-28f, -4f, 28f, -4f, stroke)
                c.drawOval(oval, outline)
                fill.color = Color.rgb(0x2E, 0x7D, 0x5A); c.drawCircle(26f, -8f, 7f, fill); c.drawCircle(26f, -8f, 7f, outline)
            }
            else -> {
                fill.shader = null; fill.color = Color.rgb(0x1E, 0x1E, 0x26)
                oval.set(-26f, -4f, 26f, 5f); c.drawOval(oval, fill); c.drawOval(oval, outline)
                oval.set(-16f, -34f, 16f, 1f); c.drawRoundRect(oval, 3f, 3f, fill); c.drawRoundRect(oval, 3f, 3f, outline)
                fill.color = CAPE_RED; oval.set(-16f, -8f, 16f, -3f); c.drawRect(oval, fill)
            }
        }
        c.restore()
    }

    /** A scarf around the neck, one end fluttering (hips space). */
    protected fun paintScarf(c: Canvas, rig: MonkeyRig, color: Int, t: Float) {
        c.save(); c.concat(rig.hips)
        fill.shader = null; fill.color = color
        val y = MonkeyRig.SHOULDER_Y
        oval.set(-19f, y - 7f, 19f, y + 5f); c.drawRoundRect(oval, 6f, 6f, fill); c.drawRoundRect(oval, 6f, 6f, outline)
        val w = 3f * sin(t * 5f)
        tmp.reset(); tmp.moveTo(6f, y); tmp.lineTo(14f + w, y + 22f); tmp.lineTo(4f + w, y + 24f); tmp.lineTo(0f, y + 2f); tmp.close()
        c.drawPath(tmp, fill); c.drawPath(tmp, outline)
        stroke.shader = null; stroke.color = Color.WHITE; stroke.strokeWidth = 2f; stroke.alpha = 160
        c.drawLine(-14f, y - 2f, 14f, y - 2f, stroke); stroke.alpha = 255
        c.restore()
    }

    /** Moustache under the nose (head space); [off] 0..1 = flying away. */
    protected fun paintStache(c: Canvas, p: Pose, off: Float, color: Int) {
        if (off >= 1f) return
        c.save(); c.translate(32f * off, -22f - 40f * off + 60f * off * off); c.rotate(200f * off)
        fill.shader = null; fill.color = color
        tmp.reset(); tmp.moveTo(0f, 0f)
        tmp.cubicTo(-6f, -5f, -14f, -5f, -19f, 2f); tmp.cubicTo(-13f, 0f, -7f, 3f, 0f, 2f)
        tmp.cubicTo(7f, 3f, 13f, 0f, 19f, 2f); tmp.cubicTo(14f, -5f, 6f, -5f, 0f, 0f); tmp.close()
        c.drawPath(tmp, fill); outline.strokeWidth = 1.4f; c.drawPath(tmp, outline); outline.strokeWidth = OW
        c.restore()
    }

    fun draw(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        pose = p; now = t
        if (p.backView > 0.5f) { drawBack(c, rig, p, t); return }
        if (p.smoke > 0.02f) drawSmoke(c, rig, p, t)
        if (p.fade <= 0.01f) { if (p.puff > 0.01f) drawPuff(c, rig, p); return }
        val layered = p.fade < 0.99f
        if (layered) c.saveLayerAlpha(null, (255 * p.fade).toInt())
        drawBody(c, rig, p, t)
        if (layered) c.restore()
        if (p.puff > 0.01f) drawPuff(c, rig, p)
    }

    private fun drawBody(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        if (p.shadow > 0.05f && p.bodyY > -60f) drawShadow(c, rig, p)
        if (p.rocket > 0.05f) drawRocket(c, rig, p, t)
        if (p.peel > 0.05f) drawPeel(c, rig, p)
        val tailInFront = p.tailFront > 0.5f
        drawBehind(c, rig, p, t)
        if (!tailInFront) drawTail(c, rig)
        if (p.cape > 0.05f) drawCape(c, rig, p, t)
        if (p.jets > 0.02f) drawJets(c, rig, p, t)
        drawLegs(c, rig)
        drawTorso(c, rig)
        drawTorsoExtras(c, rig, p, t)
        if (p.badge > 0.05f) drawBadge(c, rig, p)
        if (p.cape > 0.05f) drawCapeCollar(c, rig)
        if (p.guitar > 0.05f) drawGuitar(c, rig, p)
        if (p.stethoscope > 0.05f) drawStethoscope(c, rig, p)
        if (p.wheel > 0.05f) drawWheel(c, rig, p, t)
        drawArms(c, rig)
        if (p.palmGlow > 0.02f) drawPalmGlow(c, rig, p, t)
        if (p.phone > 0.05f) drawPhone(c, rig, p)
        if (p.magnifier > 0.05f) drawMagnifier(c, rig, p, t)
        if (p.notebook > 0.05f) drawNotebook(c, rig, p, t)
        if (p.globe > 0.05f) drawGlobe(c, rig, p, t)
        if (p.staff > 0.05f) drawStaff(c, rig, p, t)
        if (p.wand > 0.05f) drawWand(c, rig, p, t)
        if (p.hat > 0.05f) drawHat(c, rig, p, t)
        if (p.frost > 0.02f) drawFrostBody(c, rig, p)
        drawHead(c, rig, p, t)
        if (tailInFront) drawTail(c, rig)
        drawFront(c, rig, p, t)
        drawFx(c, rig, p, t)
        if (p.festive > 0.05f) drawFestive(c, rig, p, t)
    }

    /** v10.8 appeal: where the eye catch-lights sit in head space (x of the right eye, y, size), or null for none. */
    protected open val glintEye: FloatArray? = floatArrayOf(12f, -41f, 1f)
    /** v10.9: head drawn smaller around the neck (1 = the chibi ratio every other character keeps). */
    protected open val headScale: Float = 1f
    /** v10.9: moves the head down along the neck (rig units); 0 for everyone but Skye. */
    protected open val headDrop: Float = 0f
    /** v10.10: the head sits forward over a leaning torso (Skye riding); 0 = everyone else. */
    protected open val headShiftX: Float = 0f

    /** v10.8 (Smooth animation only): a tiny living catch-light in each eye that gently breathes. */
    private fun drawGlint(c: Canvas, p: Pose) {
        val g = glintEye ?: return
        if (p.eyeOpen < 0.55f || p.eyesHappy > 0.5f || p.backView > 0.5f) return
        fill.shader = null; fill.color = android.graphics.Color.WHITE
        for (s in SIDES) {
            if (s < 0 && p.wink > 0.5f) continue
            val x = s * g[0] + p.lookX + 2.4f * g[2]; val y = g[1] + p.lookY - 2.8f * g[2]
            fill.alpha = (150 + 90 * p.glint).toInt().coerceIn(0, 255)
            c.drawCircle(x, y, (1.2f + 0.5f * p.glint) * g[2], fill)
        }
        fill.alpha = 255
    }

    /** v10.8 Diwali season (Settings › Festivals): a few golden twinkles around the head and a little floating diya. */
    private fun drawFestive(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val k = p.festive.coerceIn(0f, 1f)
        c.save(); c.concat(rig.head)
        fill.shader = null
        for (i in 0 until 4) {
            val ang = t * 0.6f + i * 1.5708f
            val r = MonkeyRig.HEAD_R + 12f + 3f * kotlin.math.sin(t * 2f + i)
            val x = kotlin.math.cos(ang) * r; val y = MonkeyRig.HEAD_CY + kotlin.math.sin(ang) * r * 0.8f
            val tw = 0.5f + 0.5f * kotlin.math.sin(t * 5f + i * 2.1f)
            fill.color = GOLD_SPARK; fill.alpha = (200 * k * tw).toInt()
            tmp.reset(); val a = 4f * tw + 1f
            tmp.moveTo(x, y - a); tmp.lineTo(x + a * 0.3f, y - a * 0.3f); tmp.lineTo(x + a, y); tmp.lineTo(x + a * 0.3f, y + a * 0.3f)
            tmp.lineTo(x, y + a); tmp.lineTo(x - a * 0.3f, y + a * 0.3f); tmp.lineTo(x - a, y); tmp.lineTo(x - a * 0.3f, y - a * 0.3f); tmp.close()
            c.drawPath(tmp, fill)
        }
        // diya: clay bowl + flickering flame, bobbing beside the head
        val dx = -(MonkeyRig.HEAD_R + 20f); val dy = 6f + 2f * kotlin.math.sin(t * 1.8f)
        fill.alpha = (255 * k).toInt()
        fill.color = DIYA_CLAY; tmp.reset(); tmp.moveTo(dx - 9f, dy); tmp.quadTo(dx, dy + 9f, dx + 9f, dy); tmp.close(); c.drawPath(tmp, fill)
        val fl = 1f + 0.15f * kotlin.math.sin(t * 17f) + 0.1f * kotlin.math.sin(t * 29f)
        fill.color = DIYA_FLAME; c.drawOval(dx - 2.6f, dy - 9f * fl, dx + 2.6f, dy, fill)
        fill.color = android.graphics.Color.rgb(0xFF, 0xF4, 0xC0); c.drawOval(dx - 1.2f, dy - 5f * fl, dx + 1.2f, dy, fill)
        fill.alpha = 255
        c.restore()
    }

    // ---------------- v3.2 superpowers ----------------

    private val cape = Cape()
    /** Start the cape simulation fresh (new flight / a preview still). */
    fun resetCape() = cape.reset()
    private val capePath = Path()
    private val jetPath = Path()

    /** Red cape: a verlet chain hanging from the shoulders (hips space), streaming in the flight wind. */
    protected open fun drawCape(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val ae = anim
        if (ae == null) cape.step(t, p.bodyRot, p.capeWind)
        else cape.step(t, p.bodyRot + ae.appendageAngle(com.combo.monkeycompanion.engine.anim.Appendage.CAPE), p.capeWind)
        cape.outline(capePath)
        c.save(); c.concat(rig.hips)
        fill.shader = null; fill.color = capeFill; fill.alpha = (255 * p.cape.coerceIn(0f, 1f)).toInt()
        c.drawPath(capePath, fill)
        fill.color = capeShade; fill.alpha = (fill.alpha * 0.55f).toInt()
        c.save(); c.scale(0.45f, 1f, 0f, MonkeyRig.SHOULDER_Y); c.drawPath(capePath, fill); c.restore()
        fill.alpha = 255
        c.drawPath(capePath, outline)
        c.restore()
    }

    /** The cape's knot at the collar, in front of the chest. */
    private fun drawCapeCollar(c: Canvas, rig: MonkeyRig) {
        c.save(); c.concat(rig.hips)
        fill.color = capeFill
        for (s in SIDES) {
            tmp.reset(); tmp.moveTo(s * 3f, MonkeyRig.SHOULDER_Y - 3f); tmp.lineTo(s * 17f, MonkeyRig.SHOULDER_Y - 5f); tmp.lineTo(s * 13f, MonkeyRig.SHOULDER_Y + 5f); tmp.close()
            c.drawPath(tmp, fill); c.drawPath(tmp, outline)
        }
        fill.color = BADGE_GOLD
        c.drawCircle(0f, MonkeyRig.SHOULDER_Y - 2f, 4.2f, fill); c.drawCircle(0f, MonkeyRig.SHOULDER_Y - 2f, 4.2f, outline)
        c.restore()
    }

    /** Banana badge on the chest (original design: a gold disc with a little banana). */
    private fun drawBadge(c: Canvas, rig: MonkeyRig, p: Pose) {
        c.save(); c.concat(rig.hips)
        fill.color = BADGE_GOLD; c.drawCircle(0f, -30f, 9.5f, fill); c.drawCircle(0f, -30f, 9.5f, outline)
        fill.color = Color.rgb(0xFF, 0xF3, 0x8A)
        tmp.reset(); tmp.moveTo(-6f, -33f); tmp.quadTo(-1f, -22f, 7f, -31f); tmp.quadTo(-1f, -26f, -6f, -33f); tmp.close()
        c.drawPath(tmp, fill)
        outline.strokeWidth = 1.2f; c.drawPath(tmp, outline); outline.strokeWidth = OW
        c.restore()
    }

    /** Flame jets out of both soles (flicker; short and patchy while the engine sputters). */
    protected open fun drawJets(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val j = p.jets.coerceIn(0f, 1.2f)
        for (left in booleanArrayOf(true, false)) {
            val m = if (left) rig.legL else rig.legR
            val fl = 0.82f + 0.18f * sin(t * 47f + (if (left) 0f else 2.1f))
            val len = (16f + 26f * j) * fl
            c.save(); c.concat(m); c.translate(0f, MonkeyRig.LEG + 7f)
            jetPath.reset(); jetPath.moveTo(-8f, 0f); jetPath.quadTo(-6f, len * 0.55f, 0f, len); jetPath.quadTo(6f, len * 0.55f, 8f, 0f); jetPath.close()
            fill.shader = null; fill.color = JET_ORANGE; fill.alpha = (235 * min(1f, j * 1.6f)).toInt()
            c.drawPath(jetPath, fill)
            c.scale(0.55f, 0.6f)
            fill.color = JET_YELLOW; c.drawPath(jetPath, fill)
            c.scale(0.6f, 0.6f)
            fill.color = Color.WHITE; c.drawPath(jetPath, fill)
            fill.alpha = 255
            c.restore()
            // Boot nozzle
            c.save(); c.concat(m)
            fill.color = NOZZLE; oval.set(-9f, MonkeyRig.LEG + 2f, 9f, MonkeyRig.LEG + 9f); c.drawRoundRect(oval, 3f, 3f, fill); c.drawRoundRect(oval, 3f, 3f, outline)
            c.restore()
        }
    }

    /** Palms glowing before / after each ring blast + hand repulsor thruster plumes during jet flight. */
    private fun drawPalmGlow(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val g = p.palmGlow.coerceIn(0f, 1f)
        for (left in booleanArrayOf(true, false)) {
            val m = if (left) rig.armLFore else rig.armRFore
            if (p.jets > 0.05f) {
                val fl = 0.82f + 0.18f * sin(t * 51f + (if (left) 0.5f else 2.4f))
                val len = (14f + 20f * g) * fl
                c.save(); c.concat(m); c.translate(0f, rig.foreLen + 4f)
                jetPath.reset()
                jetPath.moveTo(-5.5f, 0f); jetPath.quadTo(-4f, len * 0.55f, 0f, len); jetPath.quadTo(4f, len * 0.55f, 5.5f, 0f); jetPath.close()
                fill.shader = null; fill.color = Color.rgb(0x3D, 0xF5, 0xFF); fill.alpha = (210 * g).toInt()
                c.drawPath(jetPath, fill)
                c.scale(0.55f, 0.65f)
                fill.color = Color.WHITE; c.drawPath(jetPath, fill)
                fill.alpha = 255
                c.restore()
            }
            rig.handPoint(left, pts)
            val r = (10f + 4f * sin(t * 30f)) * rig.scale
            fill.shader = null
            fill.color = Color.rgb(0x7F, 0xF4, 0xFF); fill.alpha = (95 * g).toInt(); c.drawCircle(pts[0], pts[1], r * 1.7f, fill)
            fill.alpha = (180 * g).toInt(); c.drawCircle(pts[0], pts[1], r, fill)
            fill.color = Color.WHITE; fill.alpha = (240 * g).toInt(); c.drawCircle(pts[0], pts[1], r * 0.48f, fill)
            fill.alpha = 255
        }
    }

    /** Sooty face after the rocket crash (head space). */
    private fun drawSoot(c: Canvas, p: Pose) {
        val a = p.soot.coerceIn(0f, 1f)
        fill.shader = null; fill.color = SOOT
        fill.alpha = (150 * a).toInt()
        oval.set(-24f, -40f, -6f, -26f); c.drawOval(oval, fill)
        oval.set(8f, -22f, 26f, -10f); c.drawOval(oval, fill)
        oval.set(-12f, -62f, 14f, -50f); c.drawOval(oval, fill)
        fill.alpha = (110 * a).toInt()
        c.drawCircle(-14f, -14f, 4f, fill); c.drawCircle(18f, -42f, 3.5f, fill)
        fill.alpha = 255
        // Frazzled tuft sparks
        outline.color = SOOT; outline.strokeWidth = 1.6f
        for (i in 0 until 5) {
            val x = -10f + i * 5f
            c.drawLine(x, -66f, x + (if (i % 2 == 0) -3f else 3f), -75f, outline)
        }
        outline.color = OUTLINE; outline.strokeWidth = OW
    }

    // ---------------- v3.2 exits ----------------

    private val rocketFin = Path().apply { moveTo(-17f, -46f); lineTo(-34f, -2f); lineTo(-17f, -12f); close() }
    private val flamePath = Path()

    /** A big banana strapped into a rocket, behind the monkey (root space: it lifts off with it). */
    private fun drawRocket(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val a = (255 * p.rocket.coerceIn(0f, 1f)).toInt()
        c.save(); c.concat(rig.root)
        if (p.flame > 0.02f) {
            val len = 18f + 70f * p.flame + 6f * sin(t * 47f)
            val w = 8f + 6f * p.flame
            flamePath.reset(); flamePath.moveTo(-w, -6f); flamePath.quadTo(-w * 0.6f, len * 0.6f, 0f, len); flamePath.quadTo(w * 0.6f, len * 0.6f, w, -6f); flamePath.close()
            fill.shader = null; fill.color = Color.rgb(0xFF, 0x7A, 0x1A); fill.alpha = a; c.drawPath(flamePath, fill)
            c.save(); c.scale(0.55f, 0.6f, 0f, -6f); fill.color = Color.rgb(0xFF, 0xE4, 0x5C); fill.alpha = a; c.drawPath(flamePath, fill); c.restore()
        }
        fill.shader = null
        fill.color = GUITAR; fill.alpha = a
        c.drawPath(rocketFin, fill); c.save(); c.scale(-1f, 1f); c.drawPath(rocketFin, fill); c.restore()
        outline.alpha = a; outline.strokeWidth = 2f
        c.drawPath(rocketFin, outline); c.save(); c.scale(-1f, 1f); c.drawPath(rocketFin, outline); c.restore()
        fill.color = BANANA; fill.alpha = a
        oval.set(-19f, -232f, 19f, -4f); c.drawRoundRect(oval, 19f, 30f, fill)
        outline.strokeWidth = OW; c.drawRoundRect(oval, 19f, 30f, outline)
        stroke.shader = null; stroke.color = BANANA_DARK; stroke.alpha = a; stroke.strokeWidth = 2.2f
        c.drawLine(9f, -215f, 9f, -20f, stroke)
        fill.color = Color.rgb(0x6B, 0x45, 0x25); fill.alpha = a          // the banana stem as the nose
        oval.set(-4f, -246f, 4f, -228f); c.drawRoundRect(oval, 3f, 3f, fill)
        fill.color = SCREEN; fill.alpha = a; c.drawCircle(0f, -200f, 7f, fill)
        outline.strokeWidth = 2f; c.drawCircle(0f, -200f, 7f, outline)
        outline.strokeWidth = OW; outline.alpha = 255; fill.alpha = 255; stroke.alpha = 255
        c.restore()
    }

    /** Smoke trail from the launch spot up to the rocket (view space). */
    private fun drawSmoke(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        pts[0] = 0f; pts[1] = 0f; rig.root.mapPoints(pts)
        val gx = rig.anchorX; val gy = rig.anchorY
        val bx = pts[0]; val by = pts[1]
        val s = rig.scale
        val n = 16
        fill.shader = null
        for (i in 0..n) {
            val k = i / n.toFloat()                       // 0 = ground, 1 = rocket
            val x = gx + (bx - gx) * k + 7f * s * sin(i * 1.9f + t * 2.5f)
            val y = gy + (by - gy) * k
            val r = (14f + 16f * (1f - k) + 4f * sin(i * 2.3f)) * s
            fill.color = Color.rgb(0xE6, 0xE6, 0xE6); fill.alpha = (200 * p.smoke * (0.45f + 0.55f * k)).toInt()
            c.drawCircle(x, y, r, fill)
        }
        fill.alpha = 255
    }

    /** The vanishing puff of smoke with a few sparkles (view space, centred on the body). */
    protected open fun drawPuff(c: Canvas, rig: MonkeyRig, p: Pose) {
        pts[0] = 0f; pts[1] = -75f; rig.root.mapPoints(pts)
        val cx = pts[0]; val cy = pts[1]; val s = rig.scale
        val k = p.puff.coerceIn(0f, 1f)
        val a = (235 * (1f - k * k)).toInt()
        fill.shader = null
        for (i in 0 until 9) {
            val ang = i * 0.698f + 0.3f
            val d = (14f + 58f * k) * s
            val r = (30f + 14f * sin(i * 1.7f)) * s * (1f - 0.35f * k)
            fill.color = Color.rgb(0xF2, 0xF2, 0xF2); fill.alpha = a
            c.drawCircle(cx + kotlin.math.cos(ang) * d, cy + sin(ang) * d * 0.8f, r, fill)
        }
        if (k < 0.6f) { fill.color = Color.WHITE; fill.alpha = (a * (1f - k / 0.6f)).toInt(); c.drawCircle(cx, cy, 44f * s, fill) }
        stroke.shader = null; stroke.color = Color.rgb(0xFF, 0xD3, 0x3A); stroke.strokeWidth = 2.4f * s; stroke.alpha = a
        for (i in 0 until 5) {
            val ang = i * 1.256f
            val d = (40f + 70f * k) * s
            val x = cx + kotlin.math.cos(ang) * d; val y = cy + sin(ang) * d
            val r = 6f * s
            c.drawLine(x - r, y, x + r, y, stroke); c.drawLine(x, y - r, x, y + r, stroke)
        }
        fill.alpha = 255; stroke.alpha = 255
    }

    // ---------------- v3.2 gag props ----------------

    /** Banana peel lying on the floor, a step ahead of the feet (root space, so it follows facing). */
    private fun drawPeel(c: Canvas, rig: MonkeyRig, p: Pose) {
        val a = (255 * p.peel.coerceIn(0f, 1f)).toInt()
        c.save(); c.concat(rig.root); c.translate(p.peelX, -1f)
        fill.shader = null
        for (i in 0 until 3) {
            c.save(); c.rotate(-40f + i * 70f)
            fill.color = BANANA; fill.alpha = a; c.drawPath(peelPetal, fill)
            outline.alpha = a; outline.strokeWidth = 1.6f; c.drawPath(peelPetal, outline)
            c.restore()
        }
        fill.color = BANANA_DARK; fill.alpha = a
        oval.set(-3f, -3f, 3f, 2f); c.drawOval(oval, fill)
        outline.strokeWidth = OW; outline.alpha = 255; fill.alpha = 255
        c.restore()
    }

    /** Air guitar across the belly (hips space; arms are drawn over it). */
    private fun drawGuitar(c: Canvas, rig: MonkeyRig, p: Pose) {
        val a = (255 * p.guitar.coerceIn(0f, 1f)).toInt()
        c.save(); c.concat(rig.hips); c.scale(1.3f, 1.3f, 4f, -16f)
        stroke.shader = null; stroke.color = WOOD; stroke.alpha = a; stroke.strokeWidth = 5.5f
        c.drawLine(2f, -24f, -40f, -56f, stroke)
        fill.shader = null; fill.color = WOOD; fill.alpha = a
        c.save(); c.rotate(-37f, -42f, -57f); oval.set(-48f, -61f, -36f, -53f); c.drawRect(oval, fill); c.restore()
        fill.color = GUITAR; fill.alpha = a
        c.drawCircle(11f, -13f, 13f, fill); c.drawCircle(1f, -24f, 9.5f, fill)
        outline.alpha = a
        c.drawCircle(11f, -13f, 13f, outline); c.drawCircle(1f, -24f, 9.5f, outline)
        fill.color = GUITAR; c.drawCircle(11f, -13f, 11.5f, fill); c.drawCircle(1f, -24f, 8f, fill)
        fill.color = GUITAR_DARK; fill.alpha = a; c.drawCircle(5f, -18f, 3.6f, fill)
        stroke.color = Color.WHITE; stroke.strokeWidth = 0.6f; stroke.alpha = a / 2
        c.drawLine(12f, -10f, -40f, -55f, stroke)
        stroke.alpha = 255; fill.alpha = 255; outline.alpha = 255
        c.restore()
    }

    // ---------------- v4 agent props ----------------

    protected fun al(v: Float) = (255 * v.coerceIn(0f, 1f)).toInt()

    /** Reading the screen: green scanner goggles with a sweeping scan line (head space). */
    private fun drawGoggles(c: Canvas, p: Pose, t: Float) {
        val a = al(p.goggles)
        stroke.shader = null; stroke.color = PHONE; stroke.strokeWidth = 3.4f; stroke.alpha = a
        c.drawLine(-36f, -44f, 36f, -44f, stroke)
        for (s in SIDES) {
            val cx = s * 12.5f; val cy = -41f
            fill.shader = null; fill.color = GOGGLE_RIM; fill.alpha = a; c.drawCircle(cx, cy, 10.5f, fill)
            fill.color = GOGGLE_LENS; fill.alpha = (a * 0.85f).toInt(); c.drawCircle(cx, cy, 7.5f, fill)
            val sy = cy - 6f + 12f * ((t * 1.6f) % 1f)
            stroke.color = Color.WHITE; stroke.strokeWidth = 1.2f; stroke.alpha = (a * 0.8f).toInt()
            c.drawLine(cx - 6f, sy, cx + 6f, sy, stroke)
            outline.alpha = a; c.drawCircle(cx, cy, 10.5f, outline)
        }
        stroke.alpha = 255; fill.alpha = 255; outline.alpha = 255
    }

    /** Driving the phone: a steering wheel held in front of the tummy (hips space), turning a little. */
    private fun drawWheel(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val a = al(p.wheel)
        c.save(); c.concat(rig.hips); c.translate(0f, -20f); c.rotate(14f * sin(t * 1.7f))
        stroke.shader = null; stroke.color = WHEEL; stroke.alpha = a; stroke.strokeWidth = 5.5f
        c.drawCircle(0f, 0f, 19f, stroke)
        stroke.strokeWidth = 3.2f
        c.drawLine(0f, 0f, -17f, 5f, stroke); c.drawLine(0f, 0f, 17f, 5f, stroke); c.drawLine(0f, 0f, 0f, -18f, stroke)
        fill.shader = null; fill.color = BADGE_GOLD; fill.alpha = a; c.drawCircle(0f, 0f, 4.5f, fill)
        outline.alpha = a; outline.strokeWidth = 1.4f
        c.drawCircle(0f, 0f, 22f, outline); c.drawCircle(0f, 0f, 16.2f, outline)
        outline.strokeWidth = OW; outline.alpha = 255; stroke.alpha = 255; fill.alpha = 255
        c.restore()
    }

    /** Notifications: a detective magnifier in the right hand, its lens glinting. */
    private fun drawMagnifier(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val a = al(p.magnifier)
        c.save(); c.concat(rig.armRFore); c.translate(0f, rig.foreLen + 4f); c.rotate(160f + 10f * sin(t * 2.2f))
        stroke.shader = null; stroke.color = WOOD; stroke.alpha = a; stroke.strokeWidth = 5f
        c.drawLine(0f, 0f, 0f, 14f, stroke)
        fill.shader = null; fill.color = GOGGLE_LENS; fill.alpha = (a * 0.45f).toInt(); c.drawCircle(0f, 25f, 11f, fill)
        stroke.color = NOZZLE; stroke.strokeWidth = 3.4f; c.drawCircle(0f, 25f, 11f, stroke)
        fill.color = Color.WHITE; fill.alpha = (a * 0.8f).toInt(); c.drawCircle(-4f, 21f, 2.6f, fill)
        outline.alpha = a; outline.strokeWidth = 1.3f; c.drawCircle(0f, 25f, 13f, outline); outline.strokeWidth = OW; outline.alpha = 255
        stroke.alpha = 255; fill.alpha = 255
        c.restore()
        blob(c, rig.armRFore, 0f, rig.foreLen + 2f, 5f, 5f, FACE)
    }

    /** Memory: a little notebook in the left hand, pencil scribbling in the right. */
    private fun drawNotebook(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val a = al(p.notebook)
        c.save(); c.concat(rig.armLFore); c.translate(0f, rig.foreLen + 6f)
        fill.shader = null; fill.color = NOTE_COVER; fill.alpha = a
        oval.set(-11f, -13f, 11f, 13f); c.drawRoundRect(oval, 2.5f, 2.5f, fill)
        fill.color = Color.rgb(0xFF, 0xFB, 0xEE); fill.alpha = a
        oval.set(-8f, -11f, 10f, 11f); c.drawRect(oval, fill)
        stroke.shader = null; stroke.color = Color.rgb(0x9E, 0xB7, 0xD6); stroke.strokeWidth = 1f; stroke.alpha = a
        var y = -6f; while (y < 10f) { c.drawLine(-6f, y, 8f, y, stroke); y += 4f }
        outline.alpha = a; outline.strokeWidth = 1.5f; oval.set(-11f, -13f, 11f, 13f); c.drawRoundRect(oval, 2.5f, 2.5f, outline)
        outline.strokeWidth = OW; outline.alpha = 255; stroke.alpha = 255; fill.alpha = 255
        c.restore()
        c.save(); c.concat(rig.armRFore); c.translate(0f, rig.foreLen + 4f); c.rotate(-30f + 12f * sin(t * 14f))
        stroke.color = BADGE_GOLD; stroke.alpha = a; stroke.strokeWidth = 3.4f; c.drawLine(0f, -8f, 0f, 10f, stroke)
        stroke.color = PHONE; stroke.strokeWidth = 2f; c.drawLine(0f, 10f, 0f, 13f, stroke); stroke.alpha = 255
        c.restore()
    }

    /** Phone doctor: stethoscope around the neck, chest piece on the tummy (hips space). */
    private fun drawStethoscope(c: Canvas, rig: MonkeyRig, p: Pose) {
        val a = al(p.stethoscope)
        c.save(); c.concat(rig.hips)
        stroke.shader = null; stroke.color = PHONE; stroke.alpha = a; stroke.strokeWidth = 2.6f
        tmp.reset(); tmp.moveTo(-12f, MonkeyRig.SHOULDER_Y); tmp.quadTo(-16f, -22f, 0f, -16f); tmp.quadTo(14f, -22f, 12f, MonkeyRig.SHOULDER_Y)
        val st = stroke.style; c.drawPath(tmp, stroke)
        fill.shader = null; fill.color = NOZZLE; fill.alpha = a; c.drawCircle(0f, -14f, 5f, fill)
        fill.color = Color.rgb(0xD9, 0xDE, 0xE6); c.drawCircle(0f, -14f, 3f, fill)
        outline.alpha = a; outline.strokeWidth = 1.2f; c.drawCircle(0f, -14f, 5f, outline); outline.strokeWidth = OW; outline.alpha = 255
        stroke.alpha = 255; fill.alpha = 255; stroke.style = st
        c.restore()
    }

    /** Web / weather: a small spinning globe on the right palm. */
    private fun drawGlobe(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val a = al(p.globe)
        c.save(); c.concat(rig.armRFore); c.translate(0f, rig.foreLen + 14f)
        fill.shader = null; fill.color = Color.rgb(0x3E, 0x8E, 0xDE); fill.alpha = a; c.drawCircle(0f, 0f, 11f, fill)
        fill.color = Color.rgb(0x5C, 0xB8, 0x5C); fill.alpha = a
        val sh = ((t * 9f) % 22f) - 11f
        oval.set(sh - 5f, -7f, sh + 3f, 2f); c.drawOval(oval, fill)
        oval.set(sh + 6f - 22f, -1f, sh + 12f - 22f, 7f); c.drawOval(oval, fill)
        oval.set(sh + 6f, -1f, sh + 12f, 7f); c.drawOval(oval, fill)
        outline.alpha = a; c.drawCircle(0f, 0f, 11f, outline); outline.alpha = 255; fill.alpha = 255
        c.restore()
    }

    /** Selfie phone held in the right hand (forearm space). */
    private fun drawPhone(c: Canvas, rig: MonkeyRig, p: Pose) {
        val a = (255 * p.phone.coerceIn(0f, 1f)).toInt()
        c.save(); c.concat(rig.armRFore); c.translate(0f, rig.foreLen + 5f)
        fill.shader = null; fill.color = PHONE; fill.alpha = a
        oval.set(-8f, -13f, 8f, 13f); c.drawRoundRect(oval, 3f, 3f, fill)
        fill.color = SCREEN; fill.alpha = a
        oval.set(-6f, -10f, 6f, 10f); c.drawRoundRect(oval, 1.5f, 1.5f, fill)
        outline.alpha = a; outline.strokeWidth = 1.6f
        oval.set(-8f, -13f, 8f, 13f); c.drawRoundRect(oval, 3f, 3f, outline)
        outline.strokeWidth = OW; outline.alpha = 255; fill.alpha = 255
        c.restore()
        // The thumb over it, so it reads as "held".
        blob(c, rig.armRFore, 0f, rig.foreLen + 2f, 5f, 5f, FACE)
    }

    /** Binoculars over the eyes (head space). */
    private fun drawBinoculars(c: Canvas, p: Pose) {
        val a = (255 * p.binoculars.coerceIn(0f, 1f)).toInt()
        fill.shader = null
        fill.color = PHONE; fill.alpha = a
        oval.set(-6f, -46f, 6f, -37f); c.drawRect(oval, fill)
        for (s in SIDES) {
            val cx = s * 12f
            fill.color = PHONE; fill.alpha = a; c.drawCircle(cx, -41f, 10f, fill)
            fill.color = Color.rgb(0x4A, 0x6E, 0x96); fill.alpha = a; c.drawCircle(cx, -41f, 6.5f, fill)
            fill.color = Color.WHITE; fill.alpha = (a * 0.7f).toInt(); c.drawCircle(cx - 2.5f, -43.5f, 2f, fill)
            outline.alpha = a; c.drawCircle(cx, -41f, 10f, outline)
        }
        outline.alpha = 255; fill.alpha = 255
    }

    /** Seen from behind (climbing): no face or belly; tail in front. */
    protected open fun drawBack(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        drawLegs(c, rig)
        c.save(); c.concat(rig.hips)
        fill.shader = bodyShader
        c.drawPath(torso, fill)
        fill.shader = null
        c.drawPath(torso, outline)
        // a little darker spine stripe
        stroke.shader = null; stroke.color = FUR_DARK; stroke.strokeWidth = 3f
        c.drawLine(0f, -44f, 0f, -12f, stroke)
        c.restore()
        c.save(); c.concat(rig.head)
        for (s in SIDES) {
            fill.shader = null; fill.color = FUR
            c.drawCircle(s * EAR_X, EAR_Y, EAR_R, fill); c.drawCircle(s * EAR_X, EAR_Y, EAR_R, outline)
        }
        fill.color = FUR
        c.drawPath(tuft, fill); c.drawPath(tuft, outline)
        fill.shader = headShader
        c.drawCircle(0f, HEAD_CY, HEAD_R, fill)
        fill.shader = null
        c.drawCircle(0f, HEAD_CY, HEAD_R, outline)
        // back-of-head fur whorl
        stroke.color = FUR_DARK; stroke.strokeWidth = 2.2f
        oval.set(-9f, HEAD_CY - 14f, 9f, HEAD_CY + 2f); c.drawArc(oval, 200f, 250f, false, stroke)
        c.restore()
        drawArms(c, rig)
        drawTail(c, rig)
        drawFx(c, rig, p, t)
    }

    protected open fun drawShadow(c: Canvas, rig: MonkeyRig, p: Pose) {
        val lift = (-p.bodyY / 60f).coerceIn(0f, 1f)
        val w = 34f * (1f - 0.45f * lift) * rig.scale
        fill.shader = null
        fill.color = Color.argb((60 * (1f - 0.5f * lift)).toInt(), 40, 20, 0)
        oval.set(rig.anchorX - w, rig.anchorY - 4f * rig.scale, rig.anchorX + w, rig.anchorY + 4f * rig.scale)
        c.drawOval(oval, fill)
    }

    protected open fun drawTail(c: Canvas, rig: MonkeyRig) {
        val tp = rig.tailPts
        tmp.reset()
        tmp.moveTo(tp[0], tp[1])
        for (i in 1 until TAIL_SEGS) {
            val mx = (tp[2 * i] + tp[2 * i + 2]) / 2f
            val my = (tp[2 * i + 1] + tp[2 * i + 3]) / 2f
            tmp.quadTo(tp[2 * i], tp[2 * i + 1], mx, my)
        }
        tmp.lineTo(tp[2 * TAIL_SEGS], tp[2 * TAIL_SEGS + 1])
        c.save(); c.concat(rig.hips)
        stroke.shader = null
        stroke.color = OUTLINE; stroke.strokeWidth = 7f + 2 * OW
        c.drawPath(tmp, stroke)
        stroke.color = FUR; stroke.strokeWidth = 7f
        c.drawPath(tmp, stroke)
        c.restore()
    }

    protected fun limb(c: Canvas, m: Matrix, len: Float, w: Float, color: Int, outlinePass: Boolean) {
        c.save(); c.concat(m)
        stroke.shader = null
        if (outlinePass) { stroke.color = OUTLINE; stroke.strokeWidth = w + 2 * OW }
        else { stroke.color = color; stroke.strokeWidth = w }
        c.drawLine(0f, 0f, 0f, len, stroke)
        c.restore()
    }

    protected fun blob(c: Canvas, m: Matrix, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        c.save(); c.concat(m)
        oval.set(cx - rx, cy - ry, cx + rx, cy + ry)
        fill.shader = null; fill.color = color
        c.drawOval(oval, fill); c.drawOval(oval, outline)
        c.restore()
    }

    protected open fun drawLegs(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.legL, LEG, 13f, FUR, true); limb(c, rig.legR, LEG, 13f, FUR, true)
        limb(c, rig.legL, LEG, 13f, FUR_DARK, false); limb(c, rig.legR, LEG, 13f, FUR_DARK, false)
        blob(c, rig.legL, -3f, LEG + 3f, 11f, 6.5f, FACE)
        blob(c, rig.legR, 3f, LEG + 3f, 11f, 6.5f, FACE)
    }

    protected open fun drawTorso(c: Canvas, rig: MonkeyRig) {
        c.save(); c.concat(rig.hips)
        fill.shader = bodyShader
        c.drawPath(torso, fill)
        fill.shader = bellyShader
        c.drawPath(belly, fill)
        fill.shader = null
        c.drawPath(torso, outline)
        // belly button
        fill.color = FACE_SHADE
        c.drawCircle(0f, -18f, 1.6f, fill)
        c.restore()
    }

    protected open fun drawArms(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.armLUpper, rig.upperLen, 11f, FUR, true); limb(c, rig.armLFore, rig.foreLen, 10f, FUR, true)
        limb(c, rig.armRUpper, rig.upperLen, 11f, FUR, true); limb(c, rig.armRFore, rig.foreLen, 10f, FUR, true)
        limb(c, rig.armLUpper, rig.upperLen, 11f, FUR, false); limb(c, rig.armLFore, rig.foreLen, 10f, FUR, false)
        limb(c, rig.armRUpper, rig.upperLen, 11f, FUR, false); limb(c, rig.armRFore, rig.foreLen, 10f, FUR, false)
        blob(c, rig.armLFore, 0f, rig.foreLen + 2f, 7.5f, 7.5f, FACE)
        blob(c, rig.armRFore, 0f, rig.foreLen + 2f, 7.5f, 7.5f, FACE)
    }

    private fun drawHead(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) = drawHeadAt(c, rig.head, p, t)

    /**
     * v10.11: the whole head (face, hair, ears, hats) as a box in head space (left, top, right, bottom; neck at 0,0),
     * before [headShiftX]/[headDrop]/[headScale]. Generous on purpose: the chat button must never touch it.
     */
    open val faceBox: FloatArray get() = FACE_BOX
    private val faceM = Matrix()
    private val facePts = FloatArray(8)

    /** v10.11: the head box in view space for this frame's [rig] layout (the same transform [drawHeadAt] uses). */
    fun faceRect(rig: MonkeyRig, out: android.graphics.RectF) {
        faceM.set(rig.head)
        if (headDrop != 0f || headShiftX != 0f) faceM.preTranslate(headShiftX, headDrop)
        if (headScale != 1f) faceM.preScale(headScale, headScale)
        val b = faceBox
        facePts[0] = b[0]; facePts[1] = b[1]; facePts[2] = b[2]; facePts[3] = b[1]
        facePts[4] = b[2]; facePts[5] = b[3]; facePts[6] = b[0]; facePts[7] = b[3]
        faceM.mapPoints(facePts)
        out.set(minOf(facePts[0], facePts[2], facePts[4], facePts[6]), minOf(facePts[1], facePts[3], facePts[5], facePts[7]),
            maxOf(facePts[0], facePts[2], facePts[4], facePts[6]), maxOf(facePts[1], facePts[3], facePts[5], facePts[7]))
    }

    /** Front-facing head drawn with an arbitrary matrix (head-local space, neck at origin). Used by the profile climber too. */
    fun drawHeadAt(c: Canvas, m: Matrix, p: Pose, t: Float) {
        c.save(); c.concat(m)
        if (headDrop != 0f || headShiftX != 0f) c.translate(headShiftX, headDrop)   // v10.9: a shorter neck (Skye); v10.10: + lean shift; 0 = everyone else
        if (headScale != 1f) c.scale(headScale, headScale)   // v10.9: slimmer head-to-body ratio (Skye); 1 = everyone else
        drawHeadBase(c, p, t)
        // Anger flush over the whole head
        if (p.anger > 0.01f) {
            fill.color = ANGER_RED; fill.alpha = (110 * p.anger.coerceIn(0f, 1f)).toInt()
            c.drawCircle(0f, HEAD_CY, HEAD_R - 1f, fill)
            fill.alpha = 255
        }
        drawCheeks(c, p)

        drawEyes(c, p)
        if (anim != null && p.glint > 0f) drawGlint(c, p)
        drawBrows(c, p)
        if (p.binoculars > 0.05f) drawBinoculars(c, p)
        if (p.goggles > 0.05f) drawGoggles(c, p, t)
        drawNose(c, p)
        drawMouth(c, p)
        if (p.mouthCloth > 0.05f) drawCloth(c, p)
        if (p.sneeze > 0.05f) drawSpray(c, p)
        if (p.shades > 0.05f) drawShades(c, p)
        if (p.earPaws > 0.05f) drawEarPaws(c, p)
        if (p.soot > 0.05f) drawSoot(c, p)
        if (p.sweat > 0.05f) drawSweat(c, p, t)
        if (p.anger > 0.35f) drawVein(c, p, t)
        if (p.ninja > 0.05f) drawNinjaBand(c, p, t)
        if (p.frizz > 0.05f) drawFrizz(c, p, t)
        if (p.frost > 0.02f) drawFrostHead(c, p)
        if (p.steam > 0.05f) drawSteam(c, p, t)
        drawHeadExtras(c, p, t)
        c.restore()
    }

    /** The monkey's ears, tuft, skull and face mask (head space). */
    protected fun monkeyHeadBase(c: Canvas, p: Pose) {
        // Ears (behind head), jiggling with [Pose.earWiggle]
        for (s in SIDES) {
            val pk = p.earPerk.coerceIn(0f, 1f)
            val ey = EAR_Y - p.earWiggle * 3.5f * (if (s < 0) 1f else 0.6f) - pk * 7f
            val ex = s * (EAR_X + p.earWiggle * 1.2f + pk * 4f)
            val er = EAR_R * (1f + 0.3f * pk)
            fill.shader = null; fill.color = FUR
            c.drawCircle(ex, ey, er, fill); c.drawCircle(ex, ey, er, outline)
            fill.color = EAR_IN
            c.drawCircle(ex + s * 1f, ey + 1f, er - 5f, fill)
            if (pk > 0.4f) {
                // Listening: two little sound arcs beside each ear.
                val a0 = outline.alpha
                outline.alpha = (255 * (pk - 0.4f) / 0.6f).toInt().coerceIn(0, 255)
                for (k in 1..2) {
                    val rr = er + 5f + k * 6f
                    oval.set(ex - rr, ey - rr, ex + rr, ey + rr)
                    c.drawArc(oval, if (s > 0) -35f else 145f, 70f, false, outline)
                }
                outline.alpha = a0
            }
        }
        // Tuft + head
        fill.color = FUR
        c.drawPath(tuft, fill); c.drawPath(tuft, outline)
        fill.shader = headShader
        c.drawCircle(0f, HEAD_CY, HEAD_R, fill)
        fill.shader = null
        c.drawCircle(0f, HEAD_CY, HEAD_R, outline)
        // Face mask
        fill.shader = faceShader
        c.drawPath(faceMask, fill)
        fill.shader = null
        outline.strokeWidth = 1.2f; outline.color = FACE_SHADE
        c.drawPath(faceMask, outline)
        outline.strokeWidth = OW; outline.color = OUTLINE
    }

    // ---------------- v4 show-off props ----------------

    /** Thunder Monkey: a wooden staff in the right hand; crackling bolts around its tip when charged. */
    private fun drawStaff(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val a = al(p.staff)
        c.save(); c.concat(rig.armRFore); c.translate(0f, rig.foreLen + 3f)
        stroke.shader = null; stroke.color = WOOD; stroke.alpha = a; stroke.strokeWidth = 5f
        c.drawLine(0f, 50f, 0f, -22f, stroke)          // the staff runs past the hand both ways
        outline.alpha = a; outline.strokeWidth = 1.2f; c.drawLine(2.6f, 50f, 2.6f, -22f, outline); outline.strokeWidth = OW
        // Orb at the far end (beyond the hand = +y, "up" when the arm is raised)
        val g = p.staffGlow.coerceIn(0f, 1f)
        fill.shader = null; fill.color = BOLT; fill.alpha = (a * (0.25f + 0.6f * g)).toInt(); c.drawCircle(0f, 56f, 7f + 6f * g, fill)
        fill.color = BOLT_CORE; fill.alpha = a; c.drawCircle(0f, 56f, 4.5f, fill)
        if (g > 0.1f) {
            stroke.color = BOLT; stroke.strokeWidth = 1.8f; stroke.alpha = (a * g).toInt()
            for (i in 0 until 4) {
                val ang = (t * 7f + i * 1.7f) % 6.283f
                val r1 = 7f; val r2 = 13f + 7f * g
                val cx = kotlin.math.cos(ang); val sy = kotlin.math.sin(ang)
                tmp.reset(); tmp.moveTo(cx * r1, 56f + sy * r1)
                tmp.lineTo(cx * (r1 + r2) * 0.5f + sy * 3f, 56f + sy * (r1 + r2) * 0.5f - cx * 3f)
                tmp.lineTo(cx * r2, 56f + sy * r2)
                c.drawPath(tmp, stroke)
            }
        }
        outline.alpha = 255; stroke.alpha = 255; fill.alpha = 255
        c.restore()
        blob(c, rig.armRFore, 0f, rig.foreLen + 2f, 5f, 5f, FACE)
    }

    /** Magician: a wand with a white tip and sparkles while tapping. */
    private fun drawWand(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val a = al(p.wand)
        c.save(); c.concat(rig.armRFore); c.translate(0f, rig.foreLen + 3f); c.rotate(-20f)
        stroke.shader = null; stroke.color = HAT; stroke.alpha = a; stroke.strokeWidth = 3.4f; c.drawLine(0f, -4f, 0f, 22f, stroke)
        stroke.color = Color.WHITE; c.drawLine(0f, 22f, 0f, 27f, stroke)
        fill.shader = null; fill.color = BADGE_GOLD
        for (i in 0 until 3) {
            val ph = (t * 2.5f + i * 0.33f) % 1f
            fill.alpha = (a * (1f - ph)).toInt()
            c.drawCircle(6f * kotlin.math.sin(i * 2.1f + t * 3f), 30f + 12f * ph, 2.2f * (1f - ph) + 0.6f, fill)
        }
        stroke.alpha = 255; fill.alpha = 255
        c.restore()
        blob(c, rig.armRFore, 0f, rig.foreLen + 2f, 5f, 5f, FACE)
    }

    /** Magician: a top hat held brim-up in the left hand, a rabbit rising out of it holding the count sign. */
    var rabbitSign: String = "7"
    private fun drawHat(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val a = al(p.hat)
        c.save(); c.concat(rig.armLFore); c.translate(0f, rig.foreLen + 10f); c.scale(1.45f, 1.45f)
        // Undo the forearm's spin a little so the hat stays roughly upright: draw in a simple frame.
        val r = p.rabbit.coerceIn(0f, 1f)
        if (r > 0.02f) {
            // Rabbit behind the hat's front, rising out of the opening (-y = out of the hat when held brim-up).
            val rise = -6f - 30f * r
            c.save(); c.translate(0f, rise)
            fill.shader = null; fill.color = RABBIT; fill.alpha = a
            oval.set(-5f, -22f, -1f, -6f); c.drawOval(oval, fill); oval.set(1f, -22f, 5f, -6f); c.drawOval(oval, fill)   // ears
            c.drawCircle(0f, 0f, 9f, fill)
            outline.alpha = a; outline.strokeWidth = 1.3f; c.drawCircle(0f, 0f, 9f, outline)
            fill.color = BLUSH; oval.set(-4f, -20f, -2f, -9f); c.drawOval(oval, fill); oval.set(2f, -20f, 4f, -9f); c.drawOval(oval, fill)
            fill.color = EYE; c.drawCircle(-3f, -1f, 1.3f, fill); c.drawCircle(3f, -1f, 1.3f, fill)
            fill.color = TONGUE; c.drawCircle(0f, 2.5f, 1.2f, fill)
            // Sign with the count
            if (r > 0.5f) {
                fill.color = Color.WHITE; oval.set(-15f, 9f, 15f, 25f); c.drawRoundRect(oval, 3f, 3f, fill)
                c.drawRoundRect(oval, 3f, 3f, outline)
                fxText.color = CAPE_RED; fxText.textSize = 11f; fxText.alpha = a
                c.drawText(rabbitSign, 0f, 21f, fxText)
            }
            outline.strokeWidth = OW
            c.restore()
        }
        fill.shader = null; fill.color = HAT; fill.alpha = a
        oval.set(-15f, -6f, 15f, -1f); c.drawOval(oval, fill)                // brim (the opening)
        oval.set(-11f, -3f, 11f, 22f); c.drawRoundRect(oval, 3f, 3f, fill)    // crown below the brim
        fill.color = CAPE_RED; oval.set(-11f, 2f, 11f, 6f); c.drawRect(oval, fill)
        outline.alpha = a; outline.strokeWidth = 1.4f; oval.set(-11f, -3f, 11f, 22f); c.drawRoundRect(oval, 3f, 3f, outline)
        outline.strokeWidth = OW; outline.alpha = 255; fill.alpha = 255
        c.restore()
        blob(c, rig.armLFore, 0f, rig.foreLen + 2f, 5f, 5f, FACE)
    }

    /** Ninja: dark headband across the forehead with red tails fluttering behind. */
    private fun drawNinjaBand(c: Canvas, p: Pose, t: Float) {
        val a = al(p.ninja)
        fill.shader = null; fill.color = NINJA_BAND; fill.alpha = a
        oval.set(-HEAD_R + 2f, -60f, HEAD_R - 2f, -52f); c.drawRect(oval, fill)
        fill.color = Color.rgb(0xB8, 0xBE, 0xC8); oval.set(-6f, -60f, 6f, -52f); c.drawRoundRect(oval, 2f, 2f, fill)   // metal plate
        stroke.shader = null; stroke.color = NINJA_RED; stroke.alpha = a; stroke.strokeWidth = 3f
        val w = 4f * sin(t * 9f)
        tmp.reset(); tmp.moveTo(HEAD_R - 3f, -56f); tmp.quadTo(HEAD_R + 8f, -58f + w, HEAD_R + 16f, -50f + w); c.drawPath(tmp, stroke)
        tmp.reset(); tmp.moveTo(HEAD_R - 3f, -55f); tmp.quadTo(HEAD_R + 6f, -50f - w, HEAD_R + 12f, -42f - w); c.drawPath(tmp, stroke)
        stroke.alpha = 255; fill.alpha = 255
    }

    /** Zapped / charged: spiky fur sticking up around the head. */
    private fun drawFrizz(c: Canvas, p: Pose, t: Float) {
        val f = p.frizz.coerceIn(0f, 1f)
        stroke.shader = null; stroke.color = FUR_DARK; stroke.strokeWidth = 3f; stroke.alpha = 255
        for (i in 0 until 9) {
            val ang = (-160f + i * 17.5f) * (Math.PI.toFloat() / 180f)
            val cx = kotlin.math.cos(ang); val sy = kotlin.math.sin(ang)
            val r0 = HEAD_R - 2f; val r1 = HEAD_R + (5f + 4f * sin(t * 30f + i)) * f
            c.drawLine(cx * r0, HEAD_CY + sy * r0, cx * r1, HEAD_CY + sy * r1, stroke)
        }
    }

    /** Ice: a frosty blue film over the head with little icicles under the chin. */
    private fun drawFrostHead(c: Canvas, p: Pose) {
        val f = p.frost.coerceIn(0f, 1f)
        fill.shader = null; fill.color = ICE; fill.alpha = (60 * f).toInt()
        c.drawCircle(0f, HEAD_CY, HEAD_R, fill)
        fill.color = Color.WHITE; fill.alpha = (200 * f).toInt()
        oval.set(-HEAD_R + 4f, HEAD_CY - HEAD_R + 1f, HEAD_R - 4f, HEAD_CY - HEAD_R + 9f); c.drawOval(oval, fill)   // snow cap
        fill.color = ICE_DEEP; fill.alpha = (220 * f).toInt()
        for (i in 0 until 4) {
            val x = -14f + i * 9f; val len = (5f + (i % 2) * 4f) * f
            tmp.reset(); tmp.moveTo(x - 2.5f, -6f); tmp.lineTo(x + 2.5f, -6f); tmp.lineTo(x, -6f + len); tmp.close()
            c.drawPath(tmp, fill)
        }
        fill.alpha = 255
    }

    private fun drawFrostBody(c: Canvas, rig: MonkeyRig, p: Pose) {
        val f = p.frost.coerceIn(0f, 1f)
        c.save(); c.concat(rig.hips)
        fill.shader = null; fill.color = ICE; fill.alpha = (60 * f).toInt(); c.drawPath(torso, fill)
        fill.alpha = 255
        c.restore()
    }

    /** Kovam: puffs of steam shooting out of the ears. */
    private fun drawSteam(c: Canvas, p: Pose, t: Float) {
        val s = p.steam.coerceIn(0f, 1f)
        fill.shader = null; fill.color = STEAM
        for (side in SIDES) for (i in 0 until 3) {
            val ph = (t * 1.8f + i / 3f) % 1f
            fill.alpha = (200 * s * (1f - ph)).toInt()
            c.drawCircle(side * (EAR_X + 6f + 18f * ph), EAR_Y - 6f - 14f * ph, 3f + 6f * ph, fill)
        }
        fill.alpha = 255
    }

    /** Brightness up: cool sunglasses. */
    protected fun drawShades(c: Canvas, p: Pose) {
        val a = (255 * p.shades.coerceIn(0f, 1f)).toInt()
        fill.shader = null; fill.color = Color.rgb(0x1C, 0x1C, 0x24); fill.alpha = a
        for (s in SIDES) {
            tmp.reset()
            val cx = s * 12.5f; val cy = -41f
            tmp.moveTo(cx - 10f, cy - 6f); tmp.lineTo(cx + 10f, cy - 6f); tmp.quadTo(cx + 10f, cy + 8f, cx, cy + 8f); tmp.quadTo(cx - 10f, cy + 8f, cx - 10f, cy - 6f); tmp.close()
            c.drawPath(tmp, fill)
            fill.color = Color.WHITE; fill.alpha = (a * 0.55f).toInt()
            c.drawCircle(cx - 4f, cy - 2f, 2f, fill)
            fill.color = Color.rgb(0x1C, 0x1C, 0x24); fill.alpha = a
        }
        stroke.shader = null; stroke.color = Color.rgb(0x1C, 0x1C, 0x24); stroke.strokeWidth = 2.4f; stroke.alpha = a
        c.drawLine(-3f, -46f, 3f, -46f, stroke)
        c.drawLine(-22f, -46f, -34f, -48f, stroke); c.drawLine(22f, -46f, 34f, -48f, stroke)
        stroke.alpha = 255; fill.alpha = 255
    }

    /** Volume up: paws pressed over the ears. */
    private fun drawEarPaws(c: Canvas, p: Pose) {
        val a = (255 * p.earPaws.coerceIn(0f, 1f)).toInt()
        for (s in SIDES) {
            val cx = s * 41f; val cy = -44f
            fill.shader = null; fill.color = FUR; fill.alpha = a
            oval.set(cx - 11f, cy - 13f, cx + 11f, cy + 13f); c.drawOval(oval, fill)
            fill.color = FACE; fill.alpha = a
            oval.set(cx - 6f, cy - 7f, cx + 6f, cy + 7f); c.drawOval(oval, fill)
            outline.alpha = a; oval.set(cx - 11f, cy - 13f, cx + 11f, cy + 13f); c.drawOval(oval, outline); outline.alpha = 255
        }
        fill.alpha = 255
    }

    /** Silent / Do-not-disturb: a little cloth tied over the mouth. */
    private fun drawCloth(c: Canvas, p: Pose) {
        val a = (255 * p.mouthCloth.coerceIn(0f, 1f)).toInt()
        tmp.reset()
        tmp.moveTo(-26f, -24f); tmp.quadTo(0f, -19f, 26f, -24f)
        tmp.lineTo(24f, -8f); tmp.quadTo(0f, -2f, -24f, -8f); tmp.close()
        fill.shader = null; fill.color = CLOTH; fill.alpha = a
        c.drawPath(tmp, fill)
        outline.alpha = a; c.drawPath(tmp, outline); outline.alpha = 255
        // knot + ties at the side of the head
        fill.color = CLOTH_DARK; fill.alpha = a
        c.drawCircle(29f, -16f, 4f, fill)
        tmp.reset(); tmp.moveTo(30f, -15f); tmp.quadTo(38f, -10f, 37f, -2f); tmp.lineTo(33f, -4f); tmp.quadTo(34f, -10f, 29f, -13f); tmp.close()
        c.drawPath(tmp, fill)
        stroke.shader = null; stroke.color = CLOTH_DARK; stroke.strokeWidth = 1.2f; stroke.alpha = a
        c.drawLine(-18f, -16f, 18f, -16f, stroke)
        stroke.alpha = 255; fill.alpha = 255
    }

    /** Hot phone / CPU: sweat drops sliding down the forehead. */
    private fun drawSweat(c: Canvas, p: Pose, t: Float) {
        val a = (230 * p.sweat.coerceIn(0f, 1f)).toInt()
        for (i in SWEAT_X.indices) {
            val x = SWEAT_X[i]
            val k = ((t * 0.7f + i * 0.37f) % 1f)
            val y = -62f + i * 6f + k * 16f
            val r = 3.2f + (i % 2) * 0.8f
            tmp.reset()
            tmp.moveTo(x, y - r * 2.2f)
            tmp.cubicTo(x + r * 1.1f, y - r * 0.6f, x + r, y + r, x, y + r)
            tmp.cubicTo(x - r, y + r, x - r * 1.1f, y - r * 0.6f, x, y - r * 2.2f)
            tmp.close()
            fill.shader = null; fill.color = SWEAT; fill.alpha = (a * (1f - k * 0.6f)).toInt()
            c.drawPath(tmp, fill)
            fill.color = Color.WHITE; fill.alpha = (a * 0.8f * (1f - k)).toInt()
            c.drawCircle(x - r * 0.35f, y - r * 0.2f, r * 0.3f, fill)
        }
        fill.alpha = 255
    }

    /** ACHOO droplets in front of the mouth (head space). */
    private fun drawSpray(c: Canvas, p: Pose) {
        val a = (220 * p.sneeze.coerceIn(0f, 1f)).toInt()
        val k = 1f + (1f - p.sneeze) * 0.8f
        fill.shader = null; fill.color = SWEAT
        for (i in 0 until 7) {
            val ang = -1.2f + i * 0.4f
            val r = (14f + (i % 3) * 5f) * k
            fill.alpha = a
            c.drawCircle(sin(ang) * r, -10f + kotlin.math.cos(ang) * r * 0.6f, 1.6f + (i % 2), fill)
        }
        fill.alpha = 255
    }

    protected open fun drawEyes(c: Canvas, p: Pose) {
        for (s in SIDES) {
            val ex = s * 12f + p.lookX
            val ey = -41f + p.lookY
            if (s < 0 && p.wink > 0.5f) {   // left eye stays shut (one-eye-open fake sleep)
                stroke.shader = null; stroke.color = EYE; stroke.strokeWidth = 2.4f
                c.drawLine(ex - 5f, ey, ex + 5f, ey, stroke)
                continue
            }
            if (p.eyesHappy > 0.5f) {
                stroke.shader = null; stroke.color = EYE; stroke.strokeWidth = 2.8f
                tmp.reset(); tmp.moveTo(ex - 6f, ey + 2f); tmp.quadTo(ex, ey - 7f, ex + 6f, ey + 2f)
                c.drawPath(tmp, stroke)
                continue
            }
            val open = p.eyeOpen
            val ry = 7f * open
            if (ry < 1.3f) {
                stroke.color = EYE; stroke.strokeWidth = 2.4f
                c.drawLine(ex - 5f, ey, ex + 5f, ey, stroke)
                continue
            }
            val rx = 5.4f * (if (open > 1f) 1f + (open - 1f) * 0.6f else 1f)
            if (open > 1.12f) { // wide-eyed: white ring
                fill.color = Color.WHITE
                oval.set(ex - rx - 2.2f, ey - ry - 2.2f, ex + rx + 2.2f, ey + ry + 2.2f)
                c.drawOval(oval, fill)
                outline.strokeWidth = 1.2f; c.drawOval(oval, outline); outline.strokeWidth = OW
            }
            fill.color = EYE
            oval.set(ex - rx, ey - ry, ex + rx, ey + ry)
            c.drawOval(oval, fill)
            fill.color = Color.WHITE
            c.drawCircle(ex + 1.8f, ey - ry * 0.4f, 2.1f, fill)
            c.drawCircle(ex - 1.8f, ey + ry * 0.45f, 0.9f, fill)
        }
    }

    protected open fun drawCheeks(c: Canvas, p: Pose) {
        val blushA = (70 + 120 * p.blush.coerceIn(0f, 1f)).toInt()
        fill.color = BLUSH; fill.alpha = blushA
        oval.set(-28f, -27f, -16f, -20f); c.drawOval(oval, fill)
        oval.set(16f, -27f, 28f, -20f); c.drawOval(oval, fill)
        fill.alpha = 255
    }

    protected open fun drawBrows(c: Canvas, p: Pose) {
        stroke.shader = null; stroke.color = OUTLINE; stroke.strokeWidth = 3.4f
        for (s in SIDES) {
            c.save()
            c.translate(s * 12f + p.lookX * 0.8f, -54f + p.browY)
            c.rotate(-s * p.browAngle)
            c.drawLine(-4.8f, 0f, 4.8f, 0f, stroke)
            c.restore()
        }
    }

    protected open fun drawMouth(c: Canvas, p: Pose) {
        val mx = p.lookX * 0.6f
        val my = -15f
        val smile = p.mouthSmile.coerceIn(-1f, 1f)
        if (p.mouthOpen > 0.12f) {
            val h = 2f + 10f * p.mouthOpen.coerceAtMost(1.2f)
            tmp.reset()
            if (smile < 0.3f && p.mouthOpen > 0.5f && smile > -0.5f) {
                // round "O" of surprise
                oval.set(mx - 4.5f, my - 2f, mx + 4.5f, my - 2f + h)
                tmp.addOval(oval, Path.Direction.CW)
            } else {
                val w = 7f + 3f * max(0f, smile)
                val cy = my - 2f * smile
                tmp.moveTo(mx - w, cy)
                tmp.quadTo(mx, my + (if (smile < 0f) -3f else 1f), mx + w, cy)
                tmp.cubicTo(mx + w, cy + h, mx - w, cy + h, mx - w, cy)
                tmp.close()
            }
            fill.shader = null; fill.color = MOUTH
            c.drawPath(tmp, fill)
            c.save(); c.clipPath(tmp)
            fill.color = TONGUE
            oval.set(mx - 5f, my + h * 0.45f, mx + 5f, my + h * 1.2f)
            c.drawOval(oval, fill)
            c.restore()
            outline.strokeWidth = 2f
            c.drawPath(tmp, outline)
            outline.strokeWidth = OW
        } else {
            stroke.shader = null; stroke.color = OUTLINE; stroke.strokeWidth = 2.4f
            tmp.reset()
            tmp.moveTo(mx - 8f, my - 3f * smile)
            tmp.quadTo(mx, my + 7f * smile, mx + 8f, my - 3f * smile)
            c.drawPath(tmp, stroke)
        }
    }

    private fun drawVein(c: Canvas, p: Pose, t: Float) {
        val pulse = 1f + 0.15f * sin(t * 10f)
        c.save()
        c.translate(23f, -60f)
        c.scale(1.5f * pulse, 1.5f * pulse)
        stroke.color = ANGER_RED; stroke.strokeWidth = 2.6f
        stroke.alpha = (255 * ((p.anger - 0.35f) / 0.65f).coerceIn(0f, 1f)).toInt()
        for (i in 0 until 4) {
            c.save(); c.rotate(i * 90f)
            tmp.reset(); tmp.moveTo(2f, 6f); tmp.quadTo(2f, 2f, 6f, 2f)
            c.drawPath(tmp, stroke)
            c.restore()
        }
        stroke.alpha = 255
        c.restore()
    }

    /** Hearts and music notes, drawn un-mirrored in view space above the head. */
    private fun drawFx(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        if (p.zzz > 0.05f) drawZzz(c, rig, p, t)
        if (p.bananas > 0.05f) drawJuggle(c, rig, p)
        if (p.fly > 0.05f) drawFly(c, rig, p, t)
        if (p.flash > 0.05f) drawFlash(c, rig, p)
        if (p.sneeze > 0.5f) {
            rig.headTop(pts)
            fxText.color = Color.rgb(0x26, 0x9F, 0xD6); fxText.alpha = 255; fxText.textSize = 22f * rig.scale
            c.drawText("ACHOO!", pts[0] + 30f * rig.scale * rig.facingSign, pts[1] + 4f * rig.scale, fxText)
        }
        if (p.hearts < 0.05f && p.notes < 0.05f) return
        rig.headTop(pts)
        val s = rig.scale
        for (i in 0 until 3) {
            val ph = ((t * 0.6f + i / 3f) % 1f)
            val x = pts[0] + (i - 1) * 26f * s + sin(ph * 7f + i) * 6f * s
            val y = pts[1] - 6f * s - ph * 46f * s
            val a = (sin(ph * Math.PI.toFloat()) * 255).toInt()
            if (p.hearts > 0.05f) {
                fill.shader = null; fill.color = Color.rgb(0xFF, 0x5C, 0x8A)
                fill.alpha = (a * min(1f, p.hearts)).toInt()
                heart(c, x, y, 7f * s * (0.8f + 0.3f * ph))
            }
            if (p.notes > 0.05f) {
                fxText.color = if (i == 1) Color.rgb(0x7E, 0x57, 0xC2) else Color.rgb(0x26, 0x9F, 0xD6)
                fxText.alpha = (a * min(1f, p.notes)).toInt()
                fxText.textSize = 20f * s
                c.drawText(if (i == 1) "\u266B" else "\u266A", x, y, fxText)
            }
        }
        fill.alpha = 255
    }

    private fun drawZzz(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        rig.headTop(pts)
        val s = rig.scale
        for (i in 0 until 3) {
            val ph = ((t * 0.35f + i / 3f) % 1f)
            val x = pts[0] + (12f + ph * 34f) * s * (if (rig.facingSign < 0) -1f else 1f)
            val y = pts[1] + 10f * s - ph * 50f * s
            fxText.color = Color.rgb(0x5C, 0x6B, 0xC0)
            fxText.alpha = (sin(ph * Math.PI.toFloat()) * 255 * min(1f, p.zzz)).toInt()
            fxText.textSize = (11f + 12f * ph) * s
            c.drawText("Z", x, y, fxText)
        }
        fxText.alpha = 255
    }

    /** Three bananas on a cascade between the hands (view space). */
    private fun drawJuggle(c: Canvas, rig: MonkeyRig, p: Pose) {
        rig.handPoint(true, pts); val lx = pts[0]; val ly = pts[1]
        rig.handPoint(false, pts); val rx = pts[0]; val ry = pts[1]
        val s = rig.scale
        val a = (255 * p.bananas.coerceIn(0f, 1f)).toInt()
        for (i in 0 until 3) {
            val ph = (p.propT * 1.05f + i / 3f) % 1f
            // first half: high arc left -> right; second half: low pass back right -> left
            val x: Float; val y: Float
            if (ph < 0.62f) {
                val k = ph / 0.62f
                x = lx + (rx - lx) * k; y = ly + (ry - ly) * k - 125f * s * 4f * k * (1f - k)
            } else {
                val k = (ph - 0.62f) / 0.38f
                x = rx + (lx - rx) * k; y = ry + (ly - ry) * k - 10f * s * 4f * k * (1f - k)
            }
            c.save(); c.translate(x, y - 8f * s); c.rotate(ph * 720f); c.scale(1.7f * s, 1.7f * s)
            fill.shader = null; fill.color = BANANA; fill.alpha = a
            c.drawPath(bananaPath, fill)
            outline.alpha = a; outline.strokeWidth = 1.6f; c.drawPath(bananaPath, outline)
            fill.color = BANANA_DARK; c.drawCircle(-11f, -3f, 1.6f, fill)
            outline.strokeWidth = OW; outline.alpha = 255
            c.restore()
        }
        fill.alpha = 255
    }

    /** The fly (root space so it stays around the head while the monkey moves). */
    protected open fun drawFly(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val a = (255 * p.fly.coerceIn(0f, 1f)).toInt()
        c.save(); c.concat(rig.root); c.translate(p.flyX, p.flyY); c.scale(1.9f, 1.9f)
        val flap = 0.4f + 0.6f * kotlin.math.abs(sin(t * 60f))
        fill.shader = null; fill.color = Color.argb((a * 0.55f).toInt(), 0xD8, 0xEE, 0xFF)
        oval.set(-6f, -5f * flap - 1f, -0.5f, 0f); c.drawOval(oval, fill)
        oval.set(0.5f, -5f * flap - 1f, 6f, 0f); c.drawOval(oval, fill)
        fill.color = Color.rgb(0x22, 0x22, 0x22); fill.alpha = a
        c.drawCircle(0f, 1f, 2.8f, fill)
        fill.alpha = 255
        c.restore()
    }

    /** Camera flash from the selfie phone (view space). */
    private fun drawFlash(c: Canvas, rig: MonkeyRig, p: Pose) {
        rig.handPoint(false, pts)
        val s = rig.scale
        val a = (230 * p.flash.coerceIn(0f, 1f)).toInt()
        fill.shader = null; fill.color = Color.WHITE; fill.alpha = a / 2
        c.drawCircle(pts[0], pts[1] - 6f * s, 26f * s * p.flash, fill)
        stroke.shader = null; stroke.color = Color.rgb(0xFF, 0xF2, 0x9E); stroke.alpha = a; stroke.strokeWidth = 2.2f * s
        for (i in 0 until 8) {
            val ang = i * 0.785f
            val r0 = 12f * s; val r1 = (20f + 14f * p.flash) * s
            c.drawLine(pts[0] + kotlin.math.cos(ang) * r0, pts[1] - 6f * s + sin(ang) * r0, pts[0] + kotlin.math.cos(ang) * r1, pts[1] - 6f * s + sin(ang) * r1, stroke)
        }
        stroke.alpha = 255; fill.alpha = 255
    }

    private fun heart(c: Canvas, x: Float, y: Float, r: Float) {
        tmp.reset()
        tmp.moveTo(x, y + r)
        tmp.cubicTo(x - r * 1.6f, y - r * 0.2f, x - r * 0.7f, y - r * 1.4f, x, y - r * 0.5f)
        tmp.cubicTo(x + r * 0.7f, y - r * 1.4f, x + r * 1.6f, y - r * 0.2f, x, y + r)
        tmp.close()
        c.drawPath(tmp, fill)
    }

}
