@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package android.graphics

/*
 * Desktop shim: the tiny subset of android.graphics that Settai's shared rig code uses,
 * implemented on Java2D. Lets the exact same MonkeyRenderer / Climber / MonkeyRig source
 * draw the monkey on the Ubuntu desktop.
 */

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.NoninvertibleTransformException
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

object Color {
    const val BLACK = -0x1000000
    const val WHITE = -0x1
    const val TRANSPARENT = 0
    const val RED = -0x10000
    const val GRAY = -0x777778
    fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    fun argb(a: Int, r: Int, g: Int, b: Int): Int = ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    fun alpha(c: Int) = c ushr 24
    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF
    fun toAwt(c: Int) = java.awt.Color(red(c), green(c), blue(c), alpha(c))
}

class PointF() {
    @JvmField var x = 0f
    @JvmField var y = 0f
    constructor(x: Float, y: Float) : this() { this.x = x; this.y = y }
    fun set(x: Float, y: Float) { this.x = x; this.y = y }
    fun set(p: PointF) { x = p.x; y = p.y }
    fun offset(dx: Float, dy: Float) { x += dx; y += dy }
}

class RectF() {
    @JvmField var left = 0f
    @JvmField var top = 0f
    @JvmField var right = 0f
    @JvmField var bottom = 0f
    constructor(l: Float, t: Float, r: Float, b: Float) : this() { set(l, t, r, b) }
    constructor(o: RectF) : this() { set(o) }
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun set(o: RectF) = set(o.left, o.top, o.right, o.bottom)
    fun setEmpty() = set(0f, 0f, 0f, 0f)
    fun width() = right - left
    fun height() = bottom - top
    fun centerX() = (left + right) / 2f
    fun centerY() = (top + bottom) / 2f
    val isEmpty get() = left >= right || top >= bottom
    fun offset(dx: Float, dy: Float) { left += dx; right += dx; top += dy; bottom += dy }
    fun offsetTo(x: Float, y: Float) = offset(x - left, y - top)
    fun inset(dx: Float, dy: Float) { left += dx; right -= dx; top += dy; bottom -= dy }
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
    fun union(l: Float, t: Float, r: Float, b: Float) {
        if (l >= r || t >= b) return
        if (left >= right || top >= bottom) { set(l, t, r, b); return }
        left = minOf(left, l); top = minOf(top, t); right = maxOf(right, r); bottom = maxOf(bottom, b)
    }
    fun union(o: RectF) = union(o.left, o.top, o.right, o.bottom)
    fun union(x: Float, y: Float) { left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y) }
    fun intersects(l: Float, t: Float, r: Float, b: Float) = left < r && l < right && top < b && t < bottom
    internal fun awt(): Rectangle2D.Float = Rectangle2D.Float(left, top, right - left, bottom - top)
    override fun toString() = "RectF($left, $top, $right, $bottom)"
}

class Matrix() {
    internal val at = AffineTransform()
    constructor(o: Matrix) : this() { at.setTransform(o.at) }
    fun reset() = at.setToIdentity()
    fun set(o: Matrix) = at.setTransform(o.at)
    val isIdentity get() = at.isIdentity
    fun setTranslate(dx: Float, dy: Float) = at.setToTranslation(dx.toDouble(), dy.toDouble())
    fun setScale(sx: Float, sy: Float) = at.setToScale(sx.toDouble(), sy.toDouble())
    fun setScale(sx: Float, sy: Float, px: Float, py: Float) { at.setToTranslation(px.toDouble(), py.toDouble()); at.scale(sx.toDouble(), sy.toDouble()); at.translate(-px.toDouble(), -py.toDouble()) }
    fun setRotate(deg: Float) = at.setToRotation(Math.toRadians(deg.toDouble()))
    fun setRotate(deg: Float, px: Float, py: Float) = at.setToRotation(Math.toRadians(deg.toDouble()), px.toDouble(), py.toDouble())
    // pre: applied to points first (this = this * M)
    fun preTranslate(dx: Float, dy: Float): Boolean { at.translate(dx.toDouble(), dy.toDouble()); return true }
    fun preScale(sx: Float, sy: Float): Boolean { at.scale(sx.toDouble(), sy.toDouble()); return true }
    fun preScale(sx: Float, sy: Float, px: Float, py: Float): Boolean { at.translate(px.toDouble(), py.toDouble()); at.scale(sx.toDouble(), sy.toDouble()); at.translate(-px.toDouble(), -py.toDouble()); return true }
    fun preRotate(deg: Float): Boolean { at.rotate(Math.toRadians(deg.toDouble())); return true }
    fun preRotate(deg: Float, px: Float, py: Float): Boolean { at.rotate(Math.toRadians(deg.toDouble()), px.toDouble(), py.toDouble()); return true }
    fun preConcat(o: Matrix): Boolean { at.concatenate(o.at); return true }
    // post: applied after (this = M * this)
    fun postTranslate(dx: Float, dy: Float): Boolean { at.preConcatenate(AffineTransform.getTranslateInstance(dx.toDouble(), dy.toDouble())); return true }
    fun postScale(sx: Float, sy: Float): Boolean { at.preConcatenate(AffineTransform.getScaleInstance(sx.toDouble(), sy.toDouble())); return true }
    fun postScale(sx: Float, sy: Float, px: Float, py: Float): Boolean { val m = AffineTransform.getTranslateInstance(px.toDouble(), py.toDouble()); m.scale(sx.toDouble(), sy.toDouble()); m.translate(-px.toDouble(), -py.toDouble()); at.preConcatenate(m); return true }
    fun postRotate(deg: Float): Boolean { at.preConcatenate(AffineTransform.getRotateInstance(Math.toRadians(deg.toDouble()))); return true }
    fun postRotate(deg: Float, px: Float, py: Float): Boolean { at.preConcatenate(AffineTransform.getRotateInstance(Math.toRadians(deg.toDouble()), px.toDouble(), py.toDouble())); return true }
    fun postConcat(o: Matrix): Boolean { at.preConcatenate(o.at); return true }
    fun mapPoints(pts: FloatArray) = at.transform(pts, 0, pts, 0, pts.size / 2)
    fun mapPoints(dst: FloatArray, src: FloatArray) = at.transform(src, 0, dst, 0, src.size / 2)
    fun invert(out: Matrix): Boolean = try { out.at.setTransform(at.createInverse()); true } catch (e: NoninvertibleTransformException) { false }
}

class Path() {
    enum class Direction { CW, CCW }
    enum class FillType { WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD }
    internal val p = Path2D.Float()
    constructor(o: Path) : this() { p.append(o.p, false) }
    var fillType: FillType = FillType.WINDING
        set(v) { field = v; p.windingRule = if (v == FillType.EVEN_ODD) Path2D.WIND_EVEN_ODD else Path2D.WIND_NON_ZERO }
    private fun ensure() { if (p.currentPoint == null) p.moveTo(0f, 0f) }
    fun reset() = p.reset()
    fun rewind() = p.reset()
    fun set(o: Path) { p.reset(); p.append(o.p, false) }
    fun moveTo(x: Float, y: Float) = p.moveTo(x, y)
    fun lineTo(x: Float, y: Float) { ensure(); p.lineTo(x, y) }
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) { ensure(); p.quadTo(x1, y1, x2, y2) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { ensure(); p.curveTo(x1, y1, x2, y2, x3, y3) }
    fun rLineTo(dx: Float, dy: Float) { ensure(); val c = p.currentPoint; p.lineTo(c.x + dx, c.y + dy) }
    fun close() { if (p.currentPoint != null) p.closePath() }
    fun addOval(r: RectF, dir: Direction) = p.append(Ellipse2D.Float(r.left, r.top, r.width(), r.height()), false)
    fun addCircle(x: Float, y: Float, rad: Float, dir: Direction) = p.append(Ellipse2D.Float(x - rad, y - rad, 2 * rad, 2 * rad), false)
    fun addRect(l: Float, t: Float, r: Float, b: Float, dir: Direction) = p.append(Rectangle2D.Float(l, t, r - l, b - t), false)
    fun addRect(r: RectF, dir: Direction) = addRect(r.left, r.top, r.right, r.bottom, dir)
    fun addRoundRect(r: RectF, rx: Float, ry: Float, dir: Direction) = p.append(RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), 2 * rx, 2 * ry), false)
    fun addPath(o: Path) = p.append(o.p, false)
    fun addArc(r: RectF, start: Float, sweep: Float) = p.append(Arc2D.Float(r.left, r.top, r.width(), r.height(), -start, -sweep, Arc2D.OPEN), false)
    fun arcTo(r: RectF, start: Float, sweep: Float, forceMoveTo: Boolean = false) =
        p.append(Arc2D.Float(r.left, r.top, r.width(), r.height(), -start, -sweep, Arc2D.OPEN), !forceMoveTo && p.currentPoint != null)
    fun offset(dx: Float, dy: Float) = p.transform(AffineTransform.getTranslateInstance(dx.toDouble(), dy.toDouble()))
    fun transform(m: Matrix) = p.transform(m.at)
    val isEmpty get() = p.currentPoint == null

    enum class Op { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }
    fun op(other: Path, op: Op): Boolean = op(Path(this), other, op)
    fun op(a: Path, b: Path, op: Op): Boolean {
        val x = Area(a.p); val y = Area(b.p)
        when (op) {
            Op.UNION -> x.add(y); Op.INTERSECT -> x.intersect(y); Op.DIFFERENCE -> x.subtract(y); Op.XOR -> x.exclusiveOr(y)
            Op.REVERSE_DIFFERENCE -> { y.subtract(x); p.reset(); p.append(y, false); return true }
        }
        p.reset(); p.append(x, false); return true
    }
}

open class Shader {
    enum class TileMode { CLAMP, REPEAT, MIRROR }
    internal open fun awt(): java.awt.Paint = java.awt.Color.BLACK
    internal fun cycle(t: TileMode) = when (t) {
        TileMode.CLAMP -> MultipleGradientPaint.CycleMethod.NO_CYCLE
        TileMode.REPEAT -> MultipleGradientPaint.CycleMethod.REPEAT
        TileMode.MIRROR -> MultipleGradientPaint.CycleMethod.REFLECT
    }
    internal fun stops(n: Int, pos: FloatArray?): FloatArray {
        val f = pos?.copyOf() ?: FloatArray(n) { if (n == 1) 0f else it / (n - 1f) }
        for (i in 1 until f.size) if (f[i] <= f[i - 1]) f[i] = minOf(1f, f[i - 1] + 1e-4f)
        return f
    }
}

class RadialGradient(private val cx: Float, private val cy: Float, private val r: Float,
                     private val colors: IntArray, private val pos: FloatArray?, private val tile: TileMode) : Shader() {
    constructor(cx: Float, cy: Float, r: Float, c0: Int, c1: Int, tile: TileMode) : this(cx, cy, r, intArrayOf(c0, c1), null, tile)
    private val paint by lazy {
        java.awt.RadialGradientPaint(Point2D.Float(cx, cy), maxOf(0.001f, r), stops(colors.size, pos),
            Array(colors.size) { Color.toAwt(colors[it]) }, cycle(tile))
    }
    override fun awt(): java.awt.Paint = paint
}

class LinearGradient(private val x0: Float, private val y0: Float, private val x1: Float, private val y1: Float,
                     private val colors: IntArray, private val pos: FloatArray?, private val tile: TileMode) : Shader() {
    constructor(x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, tile: TileMode) : this(x0, y0, x1, y1, intArrayOf(c0, c1), null, tile)
    private val paint by lazy {
        val end = if (x0 == x1 && y0 == y1) Point2D.Float(x1 + 0.01f, y1) else Point2D.Float(x1, y1)
        java.awt.LinearGradientPaint(Point2D.Float(x0, y0), end, stops(colors.size, pos), Array(colors.size) { Color.toAwt(colors[it]) }, cycle(tile))
    }
    override fun awt(): java.awt.Paint = paint
}

class Typeface private constructor(val bold: Boolean, val family: String) {
    companion object {
        @JvmField val DEFAULT = Typeface(false, Font.SANS_SERIF)
        @JvmField val DEFAULT_BOLD = Typeface(true, Font.SANS_SERIF)
        @JvmField val SANS_SERIF = DEFAULT
        @JvmField val MONOSPACE = Typeface(false, Font.MONOSPACED)
        const val NORMAL = 0; const val BOLD = 1
        fun create(f: Typeface?, style: Int) = Typeface(style and 1 != 0, f?.family ?: Font.SANS_SERIF)
        /** Android family names map to the desktop's sans font ("-medium" renders bold-ish). */
        fun create(name: String?, style: Int) = Typeface(style and 1 != 0 || name?.endsWith("-medium") == true, if (name?.contains("mono") == true) Font.MONOSPACED else Font.SANS_SERIF)
    }
}

open class PathEffect
class DashPathEffect(val intervals: FloatArray, val phase: Float) : PathEffect()

open class Paint() {
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }
    enum class Align { LEFT, CENTER, RIGHT }
    companion object { const val ANTI_ALIAS_FLAG = 1; const val FILTER_BITMAP_FLAG = 2; const val DITHER_FLAG = 4 }

    constructor(flags: Int) : this()
    constructor(o: Paint) : this() {
        color = o.color; style = o.style; strokeWidth = o.strokeWidth; strokeCap = o.strokeCap; strokeJoin = o.strokeJoin
        textSize = o.textSize; textAlign = o.textAlign; typeface = o.typeface; isFakeBoldText = o.isFakeBoldText; shader = o.shader
    }

    var color: Int = Color.BLACK
    var alpha: Int
        get() = color ushr 24
        set(v) { color = (color and 0xFFFFFF) or (v.coerceIn(0, 255) shl 24) }
    var style = Style.FILL
    var strokeWidth = 0f
    var strokeCap = Cap.BUTT
    var strokeJoin = Join.MITER
    var strokeMiter = 4f
    var textSize = 12f
    var textAlign = Align.LEFT
    var typeface: Typeface? = null
    var isFakeBoldText = false
    var isAntiAlias = true
    var shader: Shader? = null
    var pathEffect: PathEffect? = null
    fun setShadowLayer(radius: Float, dx: Float, dy: Float, color: Int) {}
    fun clearShadowLayer() {}
    fun setARGB(a: Int, r: Int, g: Int, b: Int) { color = Color.argb(a, r, g, b) }

    internal fun font(): Font {
        val bold = isFakeBoldText || typeface?.bold == true
        return Font(typeface?.family ?: Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(textSize)
    }
    private val frc = FontRenderContext(null, true, true)
    fun measureText(s: String): Float = font().getStringBounds(noEmoji(s), frc).width.toFloat()
    fun ascent(): Float = -font().getLineMetrics("Ag", frc).ascent
    fun descent(): Float = font().getLineMetrics("Ag", frc).descent
}

/** Canvas over a Java2D Graphics2D. */
class Canvas(val g: Graphics2D) {
    private val stack = ArrayList<Triple<AffineTransform, Shape?, Float>>()
    /** saveLayerAlpha approximation: multiplies the alpha of everything drawn until restore(). */
    private var layerAlpha = 1f

    init {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    }

    fun save(): Int { stack.add(Triple(g.transform, g.clip, layerAlpha)); return stack.size }
    fun saveLayerAlpha(bounds: RectF?, alpha: Int): Int { val n = save(); layerAlpha *= (alpha.coerceIn(0, 255) / 255f); return n }
    fun restore() { if (stack.isEmpty()) return; val (t, c, a) = stack.removeAt(stack.size - 1); g.transform = t; g.clip = c; layerAlpha = a }
    fun restoreToCount(n: Int) { while (stack.size >= n && stack.isNotEmpty()) restore() }
    fun translate(dx: Float, dy: Float) = g.translate(dx.toDouble(), dy.toDouble())
    fun rotate(deg: Float) = g.rotate(Math.toRadians(deg.toDouble()))
    fun rotate(deg: Float, px: Float, py: Float) = g.rotate(Math.toRadians(deg.toDouble()), px.toDouble(), py.toDouble())
    fun scale(sx: Float, sy: Float) = g.scale(sx.toDouble(), sy.toDouble())
    fun scale(sx: Float, sy: Float, px: Float, py: Float) { translate(px, py); scale(sx, sy); translate(-px, -py) }
    fun concat(m: Matrix) = g.transform(m.at)

    fun clipRect(l: Float, t: Float, r: Float, b: Float): Boolean { g.clip(Rectangle2D.Float(l, t, r - l, b - t)); return true }
    fun clipRect(r: RectF) = clipRect(r.left, r.top, r.right, r.bottom)
    fun clipRect(l: Int, t: Int, r: Int, b: Int) = clipRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
    fun clipPath(p: Path): Boolean { g.clip(p.p); return true }
    fun clipOutRect(l: Float, t: Float, r: Float, b: Float): Boolean {
        val big = Area(g.clip ?: Rectangle2D.Float(-1e5f, -1e5f, 2e5f, 2e5f))
        big.subtract(Area(Rectangle2D.Float(l, t, r - l, b - t)))
        g.clip = big; return true
    }
    fun clipOutRect(r: RectF) = clipOutRect(r.left, r.top, r.right, r.bottom)

    fun drawColor(c: Int) {
        val old = g.transform; val oc = g.composite
        g.transform = AffineTransform()
        g.composite = AlphaComposite.SrcOver; g.color = Color.toAwt(c)
        g.fill(Rectangle2D.Float(-1e5f, -1e5f, 2e5f, 2e5f))
        g.transform = old; g.composite = oc
    }
    fun drawCircle(cx: Float, cy: Float, r: Float, p: Paint) = shape(Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r), p)
    fun drawOval(r: RectF, p: Paint) = shape(Ellipse2D.Float(r.left, r.top, r.width(), r.height()), p)
    fun drawOval(l: Float, t: Float, r: Float, b: Float, p: Paint) = shape(Ellipse2D.Float(l, t, r - l, b - t), p)
    fun drawRect(l: Float, t: Float, r: Float, b: Float, p: Paint) = shape(Rectangle2D.Float(l, t, r - l, b - t), p)
    fun drawRect(r: RectF, p: Paint) = shape(r.awt(), p)
    fun drawRoundRect(r: RectF, rx: Float, ry: Float, p: Paint) = shape(RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), 2 * rx, 2 * ry), p)
    fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: Paint) = shape(RoundRectangle2D.Float(l, t, r - l, b - t, 2 * rx, 2 * ry), p)
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, p: Paint) { apply(p); g.stroke = stroke(p); g.draw(Line2D.Float(x0, y0, x1, y1)) }
    fun drawPath(path: Path, p: Paint) = shape(path.p, p)
    fun drawArc(oval: RectF, start: Float, sweep: Float, useCenter: Boolean, p: Paint) =
        shape(Arc2D.Float(oval.left, oval.top, oval.width(), oval.height(), -start, -sweep, if (useCenter) Arc2D.PIE else Arc2D.OPEN), p)
    fun drawText(text: String, x: Float, y: Float, p: Paint) {
        val text = noEmoji(text)
        if (text.isEmpty()) return
        apply(p)
        g.font = p.font()
        val w = p.measureText(text)
        val dx = when (p.textAlign) { Paint.Align.LEFT -> 0f; Paint.Align.CENTER -> -w / 2f; Paint.Align.RIGHT -> -w }
        g.drawString(text, x + dx, y)
    }
    fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, p: Paint) = drawText(text.subSequence(start, end).toString(), x, y, p)

    private fun shape(s: Shape, p: Paint) {
        apply(p)
        when (p.style) {
            Paint.Style.FILL -> g.fill(s)
            Paint.Style.STROKE -> { g.stroke = stroke(p); g.draw(s) }
            Paint.Style.FILL_AND_STROKE -> { g.fill(s); g.stroke = stroke(p); g.draw(s) }
        }
    }

    private fun apply(p: Paint) {
        val sh = p.shader
        if (sh != null) {
            g.paint = sh.awt()
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, p.alpha / 255f * layerAlpha)
        } else {
            g.composite = if (layerAlpha >= 0.999f) AlphaComposite.SrcOver else AlphaComposite.getInstance(AlphaComposite.SRC_OVER, layerAlpha)
            g.color = Color.toAwt(p.color)
        }
    }

    private fun stroke(p: Paint): BasicStroke {
        val cap = when (p.strokeCap) { Paint.Cap.BUTT -> BasicStroke.CAP_BUTT; Paint.Cap.ROUND -> BasicStroke.CAP_ROUND; Paint.Cap.SQUARE -> BasicStroke.CAP_SQUARE }
        val join = when (p.strokeJoin) { Paint.Join.MITER -> BasicStroke.JOIN_MITER; Paint.Join.ROUND -> BasicStroke.JOIN_ROUND; Paint.Join.BEVEL -> BasicStroke.JOIN_BEVEL }
        val dash = (p.pathEffect as? DashPathEffect)
        return if (dash != null) BasicStroke(p.strokeWidth, cap, join, maxOf(1f, p.strokeMiter), dash.intervals, dash.phase)
        else BasicStroke(p.strokeWidth, cap, join, maxOf(1f, p.strokeMiter))
    }
}


/** Java2D (JDK 21) can't draw colour emoji on Linux: drop them rather than show boxes. */
fun noEmoji(s: String): String {
    if (s.none { it.isSurrogate() || it.code in 0x2600..0x27BF || it.code == 0xFE0F || it.code == 0x200D }) return s
    val b = StringBuilder()
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        val emoji = cp >= 0x1F000 || cp in 0x2600..0x27BF || cp == 0xFE0F || cp == 0x200D || cp in 0x2B00..0x2BFF
        if (!emoji) b.appendCodePoint(cp)
        i += Character.charCount(cp)
    }
    return b.toString().replace(Regex(" {2,}"), " ").replace(Regex(" ([.,!?…])"), "$1").trim()
}
