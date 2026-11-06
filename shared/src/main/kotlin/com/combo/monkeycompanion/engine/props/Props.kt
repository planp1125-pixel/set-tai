package com.combo.monkeycompanion.engine.props

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * v10.32 Props engine: one catalog of objects ANY character can hold or throw (no per-character drawing).
 * Held props are drawn at the body's hand (MonkeyRig.handPoint); thrown props become [Projectile]s with real
 * positions so a script can test "when fireball near Angel". All shapes procedural; motion is pure (no Random).
 */
class PropKind(val id: String, val title: String, val radius: Float, val color: Int, val glow: Int, val spin: Float, val shape: Shape) {
    enum class Shape { ORB, SHIELD, PAN, BOLT, BALL }
}

object Props {
    val catalog: Map<String, PropKind> = linkedMapOf(
        "fireball" to PropKind("fireball", "Fireball", 0.022f, Color.rgb(0xFF, 0x5A, 0x1F), Color.argb(120, 0xFF, 0x2A, 0x10), 9f, PropKind.Shape.ORB),
        "shield" to PropKind("shield", "Shield", 0.07f, Color.rgb(0xF5, 0xC2, 0x42), Color.argb(90, 0xFF, 0xEB, 0x9C), 0f, PropKind.Shape.SHIELD),
        "dosa_pan" to PropKind("dosa_pan", "Dosa pan", 0.035f, Color.rgb(0x3A, 0x3A, 0x40), Color.argb(0, 0, 0, 0), 14f, PropKind.Shape.PAN),
        "shock" to PropKind("shock", "Shock ball", 0.02f, Color.rgb(0x7F, 0xE7, 0xFF), Color.argb(120, 0x40, 0xC0, 0xFF), 12f, PropKind.Shape.BOLT),
        "laddu" to PropKind("laddu", "Laddu", 0.018f, Color.rgb(0xF2, 0xA1, 0x2C), Color.argb(0, 0, 0, 0), 6f, PropKind.Shape.BALL),
        "ball" to PropKind("ball", "Ball", 0.02f, Color.rgb(0xE5, 0x1C, 0x2A), Color.argb(0, 0, 0, 0), 6f, PropKind.Shape.BALL),
    )
    fun byId(id: String): PropKind? = catalog[id.lowercase()]

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val oval = RectF()

    /** Draw [k] centred at (x, y) px; [w] = screen width px (sizes are width fractions), [t] = seconds. */
    fun draw(c: Canvas, k: PropKind, x: Float, y: Float, w: Float, t: Float, alpha: Float = 1f) {
        val r = k.radius * w
        val a = (255 * alpha.coerceIn(0f, 1f)).toInt()
        c.save(); c.translate(x, y); c.rotate(t * k.spin * 57.3f)
        when (k.shape) {
            PropKind.Shape.ORB -> {
                fill.color = k.glow; fill.alpha = (Color.alpha(k.glow) * alpha).toInt(); c.drawCircle(0f, 0f, r * 1.8f, fill)
                fill.color = k.color; fill.alpha = a; c.drawCircle(0f, 0f, r, fill)
                fill.color = Color.rgb(0xFF, 0xE0, 0x6A); fill.alpha = a; c.drawCircle(-r * 0.2f, -r * 0.2f, r * 0.5f, fill)
            }
            PropKind.Shape.SHIELD -> {
                fill.color = k.glow; fill.alpha = (Color.alpha(k.glow) * alpha).toInt(); c.drawCircle(0f, 0f, r * 1.15f, fill)
                line.color = k.color; line.alpha = a; line.strokeWidth = r * 0.16f; c.drawCircle(0f, 0f, r, line)
                line.strokeWidth = r * 0.08f; c.drawCircle(0f, 0f, r * 0.62f, line)
                fill.color = Color.WHITE; fill.alpha = (a * 0.8f).toInt(); c.drawCircle(-r * 0.3f, -r * 0.35f, r * 0.14f, fill)
            }
            PropKind.Shape.PAN -> {
                fill.color = k.color; fill.alpha = a; c.drawCircle(0f, 0f, r, fill)
                line.color = Color.rgb(0x6B, 0x4A, 0x2B); line.alpha = a; line.strokeWidth = r * 0.3f; c.drawLine(r, 0f, r * 2.1f, 0f, line)
                fill.color = Color.rgb(0xE8, 0xC0, 0x7A); fill.alpha = a; c.drawCircle(0f, 0f, r * 0.7f, fill)
            }
            PropKind.Shape.BOLT -> {
                fill.color = k.glow; fill.alpha = (Color.alpha(k.glow) * alpha).toInt(); c.drawCircle(0f, 0f, r * 1.8f, fill)
                line.color = k.color; line.alpha = a; line.strokeWidth = r * 0.25f
                for (i in 0 until 5) { val an = i * 1.2566f; c.drawLine(0f, 0f, cos(an) * r * 1.4f, sin(an) * r * 1.4f, line) }
                fill.color = Color.WHITE; fill.alpha = a; c.drawCircle(0f, 0f, r * 0.55f, fill)
            }
            PropKind.Shape.BALL -> { fill.color = k.color; fill.alpha = a; c.drawCircle(0f, 0f, r, fill) }
        }
        c.restore()
    }
}

/** A thrown prop in flight (screen fractions; y down). Moves in a straight line at [vx], [vy] per second. */
class Projectile(val kind: PropKind, val owner: String, val target: String?, var x: Float, var y: Float, val vx: Float, val vy: Float, val born: Float) {
    var alive = true
    fun step(dt: Float) { x += vx * dt; y += vy * dt; if (x < -0.3f || x > 1.3f || y < -0.3f || y > 1.3f) alive = false }
    /** Distance (width fractions; y scaled by [aspect] = h/w) to (px, py). */
    fun distTo(px: Float, py: Float, aspect: Float) = hypot(x - px, (y - py) * aspect)
}
