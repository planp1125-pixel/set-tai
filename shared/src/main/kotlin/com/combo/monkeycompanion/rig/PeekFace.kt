package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.combo.monkeycompanion.character.Wear
import kotlin.math.sin

/**
 * v4.5: the face that peeks out of the edge-hide tab (resting, auto-rest peek, binocular peek), drawn by each
 * character. Local tab space: the screen edge is x = 0, +x points into the screen, the head sits at (0, cy) and its
 * top points to -y (it leans round the corner). [tw] = tab thickness.
 */
abstract class PeekFace(val characterId: String) {
    /** Everything that changes per frame. [t] = seconds into a peek (-1 = resting, static). [bino] 0..1. */
    class State {
        var tw = 24f; var cy = 30f; var t = -1f; var blink = false; var look = 0f; var bino = 0f; var density = 1f
        val peeking get() = t >= 0f
    }

    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val path = Path()
    protected val r = RectF()

    /** Wardrobe ids this peek shows when worn (for tests / docs). */
    open val wardrobeShown: Set<String> get() = emptySet()

    fun draw(c: Canvas, wear: Wear?, s: State) {
        paint.style = Paint.Style.FILL; paint.strokeWidth = 0f; paint.alpha = 255
        drawFace(c, wear, s)
        if (s.bino > 0.01f) drawBinoculars(c, s)
    }

    protected abstract fun drawFace(c: Canvas, wear: Wear?, s: State)

    protected fun fill(c: Int) { paint.style = Paint.Style.FILL; paint.color = c }
    protected fun stroke(c: Int, w: Float) { paint.style = Paint.Style.STROKE; paint.strokeWidth = w; paint.strokeCap = Paint.Cap.ROUND; paint.color = c }

    /** The two eye positions along the edge (y) and how far out they are (x). */
    protected fun eyeY(s: State, i: Int) = if (i == 0) s.cy - 0.17f * s.tw else s.cy + 0.2f * s.tw

    protected fun dotEyes(c: Canvas, s: State, x: Float, rad: Float, color: Int) {
        fill(color)
        for (i in 0..1) {
            val y = eyeY(s, i)
            if (s.blink) c.drawRect(x - rad, y - 0.5f * s.density, x + rad, y + 0.5f * s.density, paint)
            else c.drawCircle(x + s.look, y, rad, paint)
        }
    }

    open fun drawBinoculars(c: Canvas, s: State) {
        val tw = s.tw; val a = (255 * s.bino.coerceIn(0f, 1f)).toInt()
        fill(Color.argb(a, 0x22, 0x26, 0x30))
        c.drawRect(tw * 0.22f, eyeY(s, 0), tw * 0.5f, eyeY(s, 1), paint)
        c.drawCircle(tw * 0.42f, eyeY(s, 0), tw * 0.16f, paint); c.drawCircle(tw * 0.42f, eyeY(s, 1), tw * 0.16f, paint)
        fill(Color.argb(a, 0x4A, 0x6E, 0x96))
        c.drawCircle(tw * 0.47f, eyeY(s, 0), tw * 0.1f, paint); c.drawCircle(tw * 0.47f, eyeY(s, 1), tw * 0.1f, paint)
    }

    /** A little hat on the top of the head (-y), in the worn style. */
    protected fun hat(c: Canvas, s: State, style: String) {
        val tw = s.tw; val top = s.cy - 0.6f * tw
        when (style) {
            "party" -> { fill(Color.rgb(0xE9, 0x4F, 0x8A)); path.reset(); path.moveTo(-0.25f * tw, top + 0.05f * tw); path.lineTo(0.3f * tw, top + 0.05f * tw); path.lineTo(0.02f * tw, top - 0.5f * tw); path.close(); c.drawPath(path, paint)
                fill(Color.rgb(0xFF, 0xD5, 0x4A)); c.drawCircle(0.02f * tw, top - 0.5f * tw, 0.08f * tw, paint) }
            "lungi" -> { fill(Color.rgb(0x2E, 0x8B, 0x57)); r.set(-0.4f * tw, top - 0.12f * tw, 0.4f * tw, top + 0.14f * tw); c.drawRoundRect(r, 0.1f * tw, 0.1f * tw, paint)
                fill(Color.rgb(0xF4, 0xE1, 0x9C)); c.drawRect(-0.4f * tw, top - 0.02f * tw, 0.4f * tw, top + 0.03f * tw, paint) }
            else -> { fill(Color.rgb(0x1E, 0x1E, 0x24)); r.set(-0.42f * tw, top - 0.02f * tw, 0.42f * tw, top + 0.1f * tw); c.drawRoundRect(r, 0.05f * tw, 0.05f * tw, paint)
                r.set(-0.26f * tw, top - 0.5f * tw, 0.26f * tw, top + 0.02f * tw); c.drawRect(r, paint)
                fill(Color.rgb(0xC0, 0x39, 0x2B)); c.drawRect(-0.26f * tw, top - 0.1f * tw, 0.26f * tw, top - 0.02f * tw, paint) }
        }
    }

    /** Sunglasses over both eyes. */
    protected fun shades(c: Canvas, s: State, x: Float) {
        val tw = s.tw
        fill(Color.rgb(0x15, 0x15, 0x1C))
        for (i in 0..1) { r.set(x - 0.13f * tw, eyeY(s, i) - 0.13f * tw, x + 0.11f * tw, eyeY(s, i) + 0.13f * tw); c.drawRoundRect(r, 0.06f * tw, 0.06f * tw, paint) }
        stroke(Color.rgb(0x15, 0x15, 0x1C), 0.05f * tw); c.drawLine(x, eyeY(s, 0), x, eyeY(s, 1), paint)
        fill(Color.argb(150, 255, 255, 255)); c.drawCircle(x + 0.04f * tw, eyeY(s, 0) - 0.05f * tw, 0.03f * tw, paint)
    }
}

/** Settai the monkey: brown head, ear, tan face, two dark eyes; hat and sunglasses from the wardrobe. */
object MonkeyPeek : PeekFace("monkey") {
    override val wardrobeShown = setOf("hat", "shades")
    override fun drawFace(c: Canvas, wear: Wear?, s: State) {
        val tw = s.tw; val cy = s.cy
        fill(Color.argb(235, 0x8B, 0x5A, 0x2B))
        c.drawCircle(0f, cy, tw * 0.62f, paint)
        c.drawCircle(-tw * 0.05f, cy - tw * 0.7f, tw * 0.26f, paint)          // ear
        fill(Color.argb(235, 0xF0, 0xC8, 0x96)); c.drawCircle(tw * 0.08f, cy + tw * 0.05f, tw * 0.42f, paint)
        fill(Color.argb(235, 0xE8, 0x9E, 0x8A)); c.drawCircle(-tw * 0.05f, cy - tw * 0.7f, tw * 0.13f, paint)
        dotEyes(c, s, tw * 0.27f, tw * 0.085f, Color.rgb(0x2B, 0x1A, 0x10))
        if (wear?.has("shades") == true) shades(c, s, tw * 0.27f)
        if (wear?.has("hat") == true) hat(c, s, wear.option("hat"))
    }
}

/** Zoom: skin, black hair tuft, teal domino mask with white eye slits, orange cape corner flapping out. */
object SuperboyPeek : PeekFace("superboy") {
    override fun drawFace(c: Canvas, wear: Wear?, s: State) {
        val tw = s.tw; val cy = s.cy
        val flap = if (s.peeking) sin(s.t * 9f) * 0.18f * tw else 0f
        // cape corner (behind the head, sticking out past the chin side)
        fill(Color.rgb(0xF2, 0x7A, 0x1E))
        path.reset(); path.moveTo(-0.3f * tw, cy + 0.35f * tw); path.lineTo(0.55f * tw + flap, cy + 1.05f * tw + flap * 0.5f)
        path.lineTo(-0.1f * tw, cy + 1.15f * tw); path.close(); c.drawPath(path, paint)
        fill(Color.rgb(0xF1, 0xC2, 0x9A)); c.drawCircle(0f, cy, tw * 0.6f, paint)
        // hair tuft
        fill(Color.rgb(0x1C, 0x1A, 0x22))
        path.reset(); path.moveTo(-0.55f * tw, cy - 0.15f * tw); path.quadTo(-0.3f * tw, cy - 0.85f * tw, 0.3f * tw, cy - 0.55f * tw)
        path.lineTo(0.15f * tw, cy - 0.8f * tw); path.lineTo(0.45f * tw, cy - 0.62f * tw); path.quadTo(0.1f * tw, cy - 0.35f * tw, -0.55f * tw, cy - 0.15f * tw); c.drawPath(path, paint)
        // domino mask
        fill(Color.rgb(0x14, 0x9C, 0x9C))
        r.set(0.12f * tw, eyeY(s, 0) - 0.16f * tw, 0.42f * tw, eyeY(s, 1) + 0.16f * tw); c.drawRoundRect(r, 0.14f * tw, 0.14f * tw, paint)
        fill(Color.WHITE)
        for (i in 0..1) {
            val y = eyeY(s, i)
            if (s.blink) c.drawRect(0.22f * tw, y - 0.6f * s.density, 0.36f * tw, y + 0.6f * s.density, paint)
            else { r.set(0.2f * tw, y - 0.08f * tw, 0.37f * tw, y + 0.08f * tw); c.drawOval(r, paint)
                fill(Color.rgb(0x1C, 0x1A, 0x22)); c.drawCircle(0.3f * tw + s.look * 0.6f, y, 0.04f * tw, paint); fill(Color.WHITE) }
        }
        if (wear?.has("scarf") == true) { fill(Color.rgb(0xD9, 0x3F, 0x3F)); r.set(-0.4f * tw, cy + 0.48f * tw, 0.45f * tw, cy + 0.66f * tw); c.drawRoundRect(r, 0.08f * tw, 0.08f * tw, paint) }
    }
    override val wardrobeShown = setOf("scarf")
}

/** Bolt: gunmetal helmet, glowing teal visor slit; mid-peek the visor flips up for a moustache peek. */
object ArmourPeek : PeekFace("armour") {
    override val wardrobeShown = setOf("scarf")
    /** 0 = visor down, 1 = up (mid-peek, for a second). */
    fun visorUp(t: Float): Float = if (t < 0f) 0f else {
        val k = t % 4f
        when { k < 1.1f -> 0f; k < 1.4f -> (k - 1.1f) / 0.3f; k < 2.6f -> 1f; k < 2.9f -> 1f - (k - 2.6f) / 0.3f; else -> 0f }
    }
    override fun drawFace(c: Canvas, wear: Wear?, s: State) {
        val tw = s.tw; val cy = s.cy
        fill(Color.rgb(0x4A, 0x50, 0x5A)); c.drawCircle(0f, cy, tw * 0.64f, paint)
        fill(Color.rgb(0xE8, 0xA3, 0x1E)); r.set(-0.62f * tw, cy - 0.08f * tw, -0.4f * tw, cy + 0.08f * tw); c.drawRect(r, paint)   // saffron stripe
        val up = visorUp(s.t)
        // face behind the visor
        fill(Color.rgb(0xE0, 0xB0, 0x88)); r.set(0.02f * tw, cy - 0.38f * tw, 0.5f * tw, cy + 0.42f * tw); c.drawRoundRect(r, 0.2f * tw, 0.2f * tw, paint)
        if (up > 0.3f) {
            dotEyes(c, s, tw * 0.3f, tw * 0.06f, Color.rgb(0x22, 0x18, 0x10))
            // moustache: curly bar along the edge, below the eyes (towards +x = into the screen)
            stroke(Color.rgb(0x2A, 0x1C, 0x14), 0.1f * tw)
            path.reset(); path.moveTo(0.44f * tw, cy - 0.3f * tw); path.quadTo(0.36f * tw, cy, 0.44f * tw, cy + 0.32f * tw); c.drawPath(path, paint)
            c.drawCircle(0.5f * tw, cy - 0.33f * tw, 0.04f * tw, paint); c.drawCircle(0.5f * tw, cy + 0.35f * tw, 0.04f * tw, paint)
        }
        // visor plate: slides back (-x) as it flips up
        val vx = -up * 0.45f * tw
        fill(Color.rgb(0x3A, 0x3F, 0x48)); r.set(0f + vx, cy - 0.44f * tw, 0.56f * tw + vx, cy + 0.48f * tw); c.drawRoundRect(r, 0.22f * tw, 0.22f * tw, paint)
        if (up < 0.7f) {
            val glow = 0.75f + 0.25f * (if (s.peeking) sin(s.t * 5f) else 0f)
            fill(Color.argb((90 * glow).toInt(), 0x3D, 0xE8, 0xD8)); r.set(0.22f * tw + vx, eyeY(s, 0) - 0.12f * tw, 0.42f * tw + vx, eyeY(s, 1) + 0.12f * tw); c.drawRoundRect(r, 0.1f * tw, 0.1f * tw, paint)
            fill(Color.argb((255 * glow).toInt(), 0x3D, 0xE8, 0xD8)); r.set(0.28f * tw + vx, eyeY(s, 0) - 0.07f * tw, 0.36f * tw + vx, eyeY(s, 1) + 0.07f * tw); c.drawRoundRect(r, 0.04f * tw, 0.04f * tw, paint)
        }
        if (wear?.has("scarf") == true) { fill(Color.rgb(0xD9, 0x3F, 0x3F)); r.set(-0.4f * tw, cy + 0.52f * tw, 0.45f * tw, cy + 0.7f * tw); c.drawRoundRect(r, 0.08f * tw, 0.08f * tw, paint) }
    }
}

/** Masko: huge carnival-mask eyes that blink and change mood; hat tip and the pet crow from the wardrobe. */
object MaskManPeek : PeekFace("mask") {
    override val wardrobeShown = setOf("mask", "hat", "crow", "shades", "stache")
    enum class Mood { CALM, SURPRISED, HAPPY, SUSPICIOUS }
    fun mood(t: Float): Mood = if (t < 0f) Mood.CALM else when ((t / 1.1f).toInt() % 4) { 0 -> Mood.SUSPICIOUS; 1 -> Mood.SURPRISED; 2 -> Mood.HAPPY; else -> Mood.CALM }
    override fun drawFace(c: Canvas, wear: Wear?, s: State) {
        val tw = s.tw; val cy = s.cy
        if (wear?.has("crow") != false && s.peeking) {   // the crow pokes out first, just past his chin
            val cx = 0.25f * tw + 0.15f * tw * sin(s.t * 4f); val cyc = cy + 0.95f * tw
            fill(Color.rgb(0x1A, 0x1A, 0x20)); c.drawCircle(cx, cyc, 0.22f * tw, paint)
            fill(Color.rgb(0xF2, 0xB1, 0x2E)); path.reset(); path.moveTo(cx + 0.18f * tw, cyc - 0.06f * tw); path.lineTo(cx + 0.38f * tw, cyc); path.lineTo(cx + 0.18f * tw, cyc + 0.06f * tw); path.close(); c.drawPath(path, paint)
            fill(Color.WHITE); c.drawCircle(cx + 0.08f * tw, cyc - 0.05f * tw, 0.05f * tw, paint)
        }
        fill(Color.rgb(0x6B, 0x3F, 0xA0)); c.drawCircle(0f, cy, tw * 0.62f, paint)   // hood
        fill(Color.rgb(0xE6, 0xB8, 0x8A)); c.drawCircle(0.1f * tw, cy + 0.08f * tw, tw * 0.42f, paint)
        val masked = wear?.has("mask") != false
        val m = mood(s.t)
        if (masked) {
            fill(Color.rgb(0x22, 0x22, 0x2A))
            r.set(0.02f * tw, eyeY(s, 0) - 0.3f * tw, 0.56f * tw, eyeY(s, 1) + 0.3f * tw); c.drawRoundRect(r, 0.24f * tw, 0.24f * tw, paint)
            val big = when (m) { Mood.SURPRISED -> 0.24f; Mood.SUSPICIOUS -> 0.17f; else -> 0.2f } * tw
            for (i in 0..1) {
                val y = eyeY(s, i) + (if (i == 0) -0.06f else 0.06f) * tw
                fill(Color.WHITE)
                when {
                    s.blink -> c.drawRect(0.18f * tw, y - 0.8f * s.density, 0.44f * tw, y + 0.8f * s.density, paint)
                    m == Mood.HAPPY -> { stroke(Color.WHITE, 0.07f * tw); r.set(0.16f * tw, y - big, 0.46f * tw, y + big); c.drawArc(r, 120f, 120f, false, paint) }
                    else -> {
                        r.set(0.31f * tw - big * 0.75f, y - big, 0.31f * tw + big * 0.75f, y + big); c.drawOval(r, paint)
                        if (m == Mood.SUSPICIOUS) { fill(Color.rgb(0x22, 0x22, 0x2A)); c.drawRect(0.31f * tw - big, y - big, 0.24f * tw, y + big, paint) }
                        fill(Color.rgb(0x15, 0x15, 0x1C)); c.drawCircle(0.34f * tw + s.look, y, big * (if (m == Mood.SURPRISED) 0.28f else 0.42f), paint)
                    }
                }
            }
        } else dotEyes(c, s, 0.3f * tw, 0.08f * tw, Color.rgb(0x22, 0x18, 0x10))
        if (wear?.has("shades") == true && !masked) shades(c, s, 0.3f * tw)
        if (wear?.has("stache") == true) { stroke(Color.rgb(0x2A, 0x1C, 0x14), 0.09f * tw); c.drawLine(0.5f * tw, cy - 0.12f * tw, 0.5f * tw, cy + 0.42f * tw, paint) }
        if (wear?.has("hat") == true) hat(c, s, wear.option("hat"))
    }
}
