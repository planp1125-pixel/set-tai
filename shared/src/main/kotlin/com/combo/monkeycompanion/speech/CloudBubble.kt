package com.combo.monkeycompanion.speech

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/**
 * A puffy cloud-shaped speech bubble: a rounded core ringed by overlapping circles,
 * with a trail of shrinking dots pointing to the speaker. Pure drawing, no state
 * about timing; the owner animates [scale] / [alpha].
 */
class CloudBubble(private val density: Float, textSizePx: Float) {
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x3A, 0x22, 0x10)
        textSize = textSizePx
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x3A, 0x22, 0x10) }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 0, 0, 0) }

    private val btnPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = textSizePx * 0.95f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val btnFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private var buttons: List<String> = emptyList()
    /** Button rects in bubble-box coordinates. */
    private val btnRects = ArrayList<RectF>()
    private val btnH = 34f * density
    private val btnGap = 10f * density

    // Reply row (in-bubble chat): [ text field ][ send ][ expand ]
    private var reply = false
    private val rowH = 36f * density
    private val rowGap = 10f * density
    private val sendD = 34f * density
    private val expandD = 28f * density
    /** Field / send / expand rects in bubble-box coordinates. */
    val fieldRect = RectF()
    private val sendRect = RectF()
    private val expandRect = RectF()
    private val fieldFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xF6, 0xE8) }
    private val fieldEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xC9, 0x9A, 0x62); style = Paint.Style.STROKE; strokeWidth = 1.6f * density
    }
    private val hintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizePx * 0.9f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val sendFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE8, 0x8A, 0x2E) }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f * density; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val iconPath = android.graphics.Path()
    var replyHint = "Reply to Settai…"
    /** null: show the hint; "": empty (a real text box sits on top); else text drawn in the field. */
    var fieldText: String? = null
    val hasReply get() = reply
    /** Text size the field uses, for matching a real EditText on top. */
    val fieldTextSize get() = hintPaint.textSize
    val fieldPadX = 12f * density

    private var layout: StaticLayout? = null
    private var textH = 0f
    private val puffs = ArrayList<FloatArray>() // x, y, r relative to bubble box
    private val core = RectF()

    private val pad = 10f * density
    private val puffR = 13f * density
    private val ow = 2.2f * density

    /** Bubble box size (without the dot trail). */
    var width = 0f; private set
    var height = 0f; private set
    /** Vertical room reserved for the dot trail. */
    val trailSpace = 26f * density

    /** v4.2: quick-reply chips (pills that wrap onto rows) instead of the big Yes / No buttons. */
    private var chips = false
    private val chipH = 30f * density
    private val chipPad = 12f * density
    private val chipPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x5A, 0x32, 0x10)
        textSize = textSizePx * 0.86f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private var chipRows = 0

    /** v4.2 quick-action arc: round buttons on an arc above Settai's head (no cloud). */
    var arcMode = false; private set
    private var arcIcons: List<String> = emptyList()
    private val arcR = 25f * density
    private val arcLabel = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x3A, 0x22, 0x10); textSize = textSizePx * 0.72f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); textAlign = Paint.Align.CENTER
    }
    private val arcIcon = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = textSizePx * 1.45f; textAlign = Paint.Align.CENTER }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f * density }

    /**
     * Lays out [icons] (emoji) with [labels] on an upward arc. The last one can be the small ✕ (quit) chip.
     * The box is sized like a bubble so the owner positions it above the head the same way.
     */
    fun setArc(icons: List<String>, labels: List<String>, dim: Set<Int> = emptySet(), title: String? = null) {
        arcMode = true; reply = false; chips = false; reportable = false
        buttons = labels; arcIcons = icons; arcDim = dim; arcTitle = title
        val n = labels.size
        // v4.4: a wider arc for more items so the 50 dp buttons (>= 48 dp targets) never overlap.
        // Up to 5: a flat 220° arc. 6+: a rounder 200° arc so side buttons sit a full button + label apart vertically.
        val radius = (if (n <= 5) 100f else if (n == 6) 120f else 132f) * density
        val squash = if (n <= 5) 0.62f else 1f
        val start = if (n <= 5) 200.0 else 190.0
        val span = if (n <= 5) 220.0 else 200.0
        val lab = arcLabel.textSize * 1.3f
        val drop = radius * squash * kotlin.math.sin(Math.toRadians(start - 180.0)).toFloat()   // end buttons below the centre line
        width = 2 * radius + 2 * arcR + 44 * density
        val titleH = if (title.isNullOrEmpty()) { titleRect.setEmpty(); 0f } else {
            titlePaint.textSize = arcLabel.textSize * 1.05f
            val tw = titlePaint.measureText(title) + 20 * density; val th = titlePaint.textSize * 1.8f
            val w0 = 2 * radius + 2 * arcR + 44 * density
            titleRect.set(w0 / 2 - tw / 2, 2 * density, w0 / 2 + tw / 2, 2 * density + th)
            arcLabel.textSize * 2.2f
        }
        val cy = titleH + arcR + 4 * density + radius * squash
        height = cy + drop + arcR + lab + 8 * density
        val cx = width / 2f
        btnRects.clear()
        for (i in 0 until n) {
            val t = if (n == 1) 0.5f else i / (n - 1f)
            val ang = Math.toRadians(start - span * t)   // left, low -> right, low, the top in the middle
            val x = cx + (radius * kotlin.math.cos(ang)).toFloat()
            val y = cy - (radius * squash * kotlin.math.sin(ang)).toFloat()
            val r = if (labels[i].startsWith("✕")) arcR * 0.72f else arcR
            btnRects.add(RectF(x - r, y - r, x + r, y + r))
        }
        layout = StaticLayout.Builder.obtain("", 0, 0, textPaint, 1).build()
    }

    private var arcDim: Set<Int> = emptySet()
    private var arcTitle: String? = null
    private val titleRect = RectF()
    fun titleAt(x: Float, y: Float): Boolean {
        val slop = 6 * density
        return arcMode && !titleRect.isEmpty && x >= titleRect.left - slop && x <= titleRect.right + slop && y >= titleRect.top - slop && y <= titleRect.bottom + slop
    }
    private val titleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x3A, 0x22, 0x10) }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    private fun drawArc(c: Canvas, a: Int) {
        arcTitle?.takeIf { it.isNotEmpty() }?.let { t ->
            titlePaint.textSize = arcLabel.textSize * 1.05f; titlePaint.alpha = a
            val tw = titlePaint.measureText(t) + 20 * density; val th = titlePaint.textSize * 1.8f
            val r = RectF(width / 2 - tw / 2, 2 * density, width / 2 + tw / 2, 2 * density + th)
            titleFill.alpha = a * 220 / 255; c.drawRoundRect(r, th / 2, th / 2, titleFill)
            c.drawText(t, width / 2, r.centerY() - (titlePaint.descent() + titlePaint.ascent()) / 2, titlePaint)
        }
        for (i in btnRects.indices) {
            val r = btnRects[i]
            val quit = buttons[i].startsWith("✕")
            val a0 = a
            @Suppress("NAME_SHADOWING") val a = if (i in arcDim) a0 * 110 / 255 else a0
            shadow.alpha = a * 40 / 255
            c.drawCircle(r.centerX() + 2 * density, r.centerY() + 3 * density, r.width() / 2 + ow, shadow)
            edge.alpha = a; c.drawCircle(r.centerX(), r.centerY(), r.width() / 2 + ow, edge)
            btnFill.color = if (i in arcDim) Color.rgb(0xE6, 0xE0, 0xD8) else if (quit) Color.rgb(0xFF, 0xE4, 0xE1) else Color.rgb(0xFF, 0xF4, 0xE2); btnFill.alpha = a
            c.drawCircle(r.centerX(), r.centerY(), r.width() / 2, btnFill)
            val ic = arcIcons.getOrNull(i).orEmpty()
            arcIcon.alpha = a; arcIcon.textSize = (if (quit) 0.75f else 1f) * arcR * 0.95f
            c.drawText(ic, r.centerX(), r.centerY() - (arcIcon.descent() + arcIcon.ascent()) / 2, arcIcon)
            val lbl = if (quit) "Quit" else buttons[i]
            val ly = r.bottom + arcLabel.textSize * 1.05f
            halo.alpha = a; halo.textSize = arcLabel.textSize; halo.textAlign = Paint.Align.CENTER; halo.typeface = arcLabel.typeface
            c.drawText(lbl, r.centerX(), ly, halo)
            arcLabel.alpha = a; c.drawText(lbl, r.centerX(), ly, arcLabel)
        }
    }

    fun setText(text: String, maxTextWidth: Int, buttons: List<String> = emptyList(), reply: Boolean = false, chips: Boolean = false) {
        arcMode = false
        this.buttons = buttons
        this.reply = reply
        this.chips = chips && buttons.isNotEmpty()
        val natural = ceil(StaticLayout.getDesiredWidth(text, textPaint)).toInt()
        var minW = if (buttons.isEmpty()) (40 * density).toInt()
        else if (this.chips) buttons.maxOf { chipPaint.measureText(it) + 2 * chipPad }.toInt().coerceAtMost(maxTextWidth)
        else (buttons.size * (78 * density) + (buttons.size - 1) * btnGap).toInt()
        if (reply) minW = max(minW, (200 * density).toInt())
        val w = natural.coerceIn(minW, maxOf(minW, maxTextWidth))
        val l = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, w)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
        layout = l
        val tw = w.toFloat()
        textH = l.height.toFloat()
        // Chips: flow onto rows of pills inside the text width.
        val chipRects = ArrayList<RectF>()
        if (this.chips) {
            var x = 0f; var row = 0
            for (b in buttons) {
                val cw = (chipPaint.measureText(b) + 2 * chipPad).coerceAtMost(tw)
                if (x > 0f && x + cw > tw) { row++; x = 0f }
                chipRects.add(RectF(x, row * (chipH + btnGap * 0.6f), x + cw, row * (chipH + btnGap * 0.6f) + chipH))
                x += cw + btnGap * 0.6f
            }
            chipRows = row + 1
            // Centre each row.
            for (rw in 0 until chipRows) {
                val inRow = chipRects.filter { it.top == rw * (chipH + btnGap * 0.6f) }
                val used = (inRow.maxOfOrNull { it.right } ?: 0f)
                val shift = (tw - used) / 2f
                inRow.forEach { it.offset(shift, 0f) }
            }
        } else chipRows = 0
        val btnBlock = if (buttons.isEmpty()) 0f else if (this.chips) chipRows * chipH + (chipRows - 1) * btnGap * 0.6f + btnGap else btnH + btnGap
        val th = textH + btnBlock + (if (reply) rowH + rowGap else 0f)
        val cw = tw + 2 * pad
        val ch = max(th + 2 * pad, puffR * 1.6f)
        width = cw + 2 * puffR
        height = ch + 2 * puffR
        core.set(puffR, puffR, puffR + cw, puffR + ch)
        puffs.clear()
        // puffs along top and bottom edges
        val nx = max(2, ceil(cw / (puffR * 1.5f)).toInt())
        for (i in 0..nx) {
            val x = core.left + cw * i / nx
            val wob = if (i % 2 == 0) 1f else 0.82f
            puffs.add(floatArrayOf(x, core.top + puffR * 0.25f, puffR * wob))
            puffs.add(floatArrayOf(x, core.bottom - puffR * 0.25f, puffR * (1.8f - wob)))
        }
        btnRects.clear()
        if (this.chips) {
            val top = core.top + pad + textH + btnGap
            for (r in chipRects) btnRects.add(RectF(core.left + pad + r.left, top + r.top, core.left + pad + r.right, top + r.bottom))
        } else if (buttons.isNotEmpty()) {
            val bw = (tw - (buttons.size - 1) * btnGap) / buttons.size
            val top = core.top + pad + textH + btnGap
            for (i in buttons.indices) {
                val l0 = core.left + pad + i * (bw + btnGap)
                btnRects.add(RectF(l0, top, l0 + bw, top + btnH))
            }
        }
        if (reply) {
            val top = core.top + pad + textH + btnBlock + rowGap
            val right = core.left + pad + tw
            val cy = top + rowH / 2
            expandRect.set(right - expandD, cy - expandD / 2, right, cy + expandD / 2)
            sendRect.set(expandRect.left - 6 * density - sendD, cy - sendD / 2, expandRect.left - 6 * density, cy + sendD / 2)
            fieldRect.set(core.left + pad - 4 * density, top, sendRect.left - 6 * density, top + rowH)
        } else { fieldRect.setEmpty(); sendRect.setEmpty(); expandRect.setEmpty() }
        val ny = max(1, ceil(ch / (puffR * 1.5f)).toInt())
        for (j in 0..ny) {
            val y = core.top + ch * j / ny
            puffs.add(floatArrayOf(core.left + puffR * 0.2f, y, puffR * 0.95f))
            puffs.add(floatArrayOf(core.right - puffR * 0.2f, y, puffR * 0.95f))
        }
    }

    /**
     * Draw with the box's top-left at (left, top). Dots trail toward (tx, ty).
     * [scale] pops around the side nearest the speaker.
     */
    fun draw(c: Canvas, left: Float, top: Float, tx: Float, ty: Float, scale: Float, alpha: Float) {
        val l = layout ?: return
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        if (arcMode) {
            c.save(); c.translate(left, top); c.scale(scale, scale, width / 2f, height)
            drawArc(c, a); c.restore(); return
        }
        val below = ty > top + height / 2 // speaker below the bubble?
        val px = (tx - left).coerceIn(puffR, width - puffR)
        val py = if (below) height else 0f
        c.save()
        c.translate(left, top)
        c.scale(scale, scale, px, py)
        edge.alpha = a; white.alpha = a; shadow.alpha = a * 40 / 255; textPaint.alpha = a

        // Dot trail from the bubble toward the speaker's head.
        val sx = px; val sy = if (below) height - puffR * 0.4f else puffR * 0.4f
        val ex = tx - left; val ey = ty - top
        val d = hypot(ex - sx, ey - sy)
        if (d > 1f) {
            val fr = floatArrayOf(0.42f, 0.66f, 0.86f)
            val rr = floatArrayOf(6.5f, 4.6f, 3f)
            for (i in fr.indices) {
                val x = sx + (ex - sx) * fr[i]; val y = sy + (ey - sy) * fr[i]
                val r = rr[i] * density
                c.drawCircle(x, y, r + ow, edge); c.drawCircle(x, y, r, white)
            }
        }
        // Soft drop shadow, then outline pass, then fill pass => clean union outline.
        for (p in puffs) c.drawCircle(p[0] + 2 * density, p[1] + 3 * density, p[2] + ow, shadow)
        c.drawRect(core.left, core.top, core.right, core.bottom, edge)
        for (p in puffs) c.drawCircle(p[0], p[1], p[2] + ow, edge)
        c.drawRect(core.left, core.top, core.right, core.bottom, white)
        for (p in puffs) c.drawCircle(p[0], p[1], p[2], white)

        if (reply) { drawReplyRow(c, a); drawClose(c, a) }
        if (reportable) drawFlag(c, a)
        val textTop = if (buttons.isEmpty() && !reply) core.top + (core.height() - l.height) / 2f else core.top + pad
        if (chips) for (i in btnRects.indices) {
            val r = btnRects[i]
            btnFill.color = Color.rgb(0xFF, 0xE9, 0xC9); btnFill.alpha = a
            c.drawRoundRect(r, chipH / 2, chipH / 2, btnFill)
            fieldEdge.alpha = a; c.drawRoundRect(r, chipH / 2, chipH / 2, fieldEdge)
            chipPaint.alpha = a
            var lbl = buttons[i]
            while (lbl.length > 2 && chipPaint.measureText(lbl) > r.width() - chipPad) lbl = lbl.dropLast(2) + "…"
            c.drawText(lbl, r.centerX(), r.centerY() - (chipPaint.descent() + chipPaint.ascent()) / 2, chipPaint)
        }
        else for (i in btnRects.indices) {
            val r = btnRects[i]
            btnFill.color = if (i == 0) Color.rgb(0x4C, 0xAF, 0x50) else Color.rgb(0xE5, 0x73, 0x73)
            btnFill.alpha = a
            c.drawRoundRect(r, btnH / 2, btnH / 2, btnFill)
            btnPaint.alpha = a
            c.drawText(buttons[i], r.centerX(), r.centerY() - (btnPaint.descent() + btnPaint.ascent()) / 2, btnPaint)
        }
        c.translate(core.left + pad, textTop)
        l.draw(c)
        c.restore()
    }

    private fun drawReplyRow(c: Canvas, a: Int) {
        val r = fieldRect
        fieldFill.alpha = a; fieldEdge.alpha = a
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, fieldFill)
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, fieldEdge)
        val ft = fieldText
        val shown = ft ?: replyHint
        if (shown.isNotEmpty()) {
            hintPaint.color = if (ft == null) Color.rgb(0xA8, 0x84, 0x60) else Color.rgb(0x3A, 0x22, 0x10)
            hintPaint.alpha = a
            val avail = r.width() - 2 * fieldPadX
            var txt = shown
            if (hintPaint.measureText(txt) > avail) {
                // Show the end of long typed text, like a real single-line field.
                while (txt.length > 1 && hintPaint.measureText("…$txt") > avail) txt = txt.substring(1)
                txt = "…$txt"
            }
            c.drawText(txt, r.left + fieldPadX, r.centerY() - (hintPaint.descent() + hintPaint.ascent()) / 2, hintPaint)
        }
        // Send: orange circle with a white paper-plane arrow.
        sendFill.alpha = a
        c.drawCircle(sendRect.centerX(), sendRect.centerY(), sendD / 2, sendFill)
        val sx = sendRect.centerX(); val sy = sendRect.centerY(); val u = sendD / 34f
        iconPath.reset()
        iconPath.moveTo(sx - 8 * u, sy - 8 * u); iconPath.lineTo(sx + 9 * u, sy); iconPath.lineTo(sx - 8 * u, sy + 8 * u)
        iconPath.lineTo(sx - 4.5f * u, sy); iconPath.close()
        iconPaint.style = Paint.Style.FILL; iconPaint.color = Color.WHITE; iconPaint.alpha = a
        c.drawPath(iconPath, iconPaint)
        // Expand: outlined circle with two diagonal arrows (open the full chat panel).
        val ex = expandRect.centerX(); val ey = expandRect.centerY(); val k = expandD / 28f
        iconPaint.style = Paint.Style.STROKE; iconPaint.color = Color.rgb(0x8A, 0x5A, 0x2E); iconPaint.alpha = a
        c.drawCircle(ex, ey, expandD / 2 - density, iconPaint)
        val o = 5.5f * k; val h = 3f * k
        c.drawLine(ex - o, ey + o, ex + o, ey - o, iconPaint)
        c.drawLine(ex + o, ey - o, ex + o - h, ey - o, iconPaint); c.drawLine(ex + o, ey - o, ex + o, ey - o + h, iconPaint)
        c.drawLine(ex - o, ey + o, ex - o + h, ey + o, iconPaint); c.drawLine(ex - o, ey + o, ex - o, ey + o - h, iconPaint)
    }

    /** v4.4: a visible ✕ on the bubble chat (top-left; the ⚑ report flag keeps the top-right). Hit area >= 48 dp. */
    private fun closeX() = puffR * 0.9f
    private fun closeY() = puffR * 0.9f
    private fun drawClose(c: Canvas, a: Int) {
        val x = closeX(); val y = closeY(); val r = 11f * density
        iconPaint.style = Paint.Style.FILL; iconPaint.color = Color.rgb(0x8A, 0x5A, 0x2E); iconPaint.alpha = a
        c.drawCircle(x, y, r, iconPaint)
        iconPaint.style = Paint.Style.STROKE; iconPaint.color = Color.WHITE; iconPaint.alpha = a
        val o = 4.2f * density
        c.drawLine(x - o, y - o, x + o, y + o, iconPaint); c.drawLine(x + o, y - o, x - o, y + o, iconPaint)
    }
    /** True when (x, y) relative to the box hits the chat's ✕ close button. */
    fun closeAt(x: Float, y: Float): Boolean = reply && hypot(x - closeX(), y - closeY()) <= 24f * density

    /** v4: a small ⚑ "Report this reply" button in the top-right corner (AI replies only). */
    var reportable = false
    private val flagR get() = 9f * density
    private fun flagX() = width - puffR * 0.9f
    private fun flagY() = puffR * 0.9f
    private fun drawFlag(c: Canvas, a: Int) {
        val x = flagX(); val y = flagY(); val r = flagR
        iconPaint.style = Paint.Style.FILL; iconPaint.color = Color.rgb(0xFF, 0xF4, 0xE2); iconPaint.alpha = a
        c.drawCircle(x, y, r, iconPaint)
        iconPaint.style = Paint.Style.STROKE; iconPaint.color = Color.rgb(0xA8, 0x84, 0x60); iconPaint.alpha = a
        c.drawCircle(x, y, r, iconPaint)
        val u = r / 9f
        c.drawLine(x - 3 * u, y - 5 * u, x - 3 * u, y + 5 * u, iconPaint)
        iconPath.reset(); iconPath.moveTo(x - 3 * u, y - 5 * u); iconPath.lineTo(x + 4.5f * u, y - 2.5f * u); iconPath.lineTo(x - 3 * u, y); iconPath.close()
        iconPaint.style = Paint.Style.FILL; iconPaint.color = Color.rgb(0xE5, 0x73, 0x73); c.drawPath(iconPath, iconPaint)
    }
    /** True when (x, y) relative to the box hits the ⚑ report button. */
    fun reportAt(x: Float, y: Float): Boolean {
        if (!reportable) return false
        val r = flagR + 8 * density
        return hypot(x - flagX(), y - flagY()) <= r
    }

    /** Reply-row hit test relative to the box: 0 field, 1 send, 2 expand, -1 none. */
    fun replyAt(x: Float, y: Float): Int {
        if (!reply) return -1
        val s = 6 * density
        fun RectF.hit() = x >= left - s && x <= right + s && y >= top - s && y <= bottom + s
        return when {
            sendRect.hit() -> 1
            expandRect.hit() -> 2
            fieldRect.hit() -> 0
            else -> -1
        }
    }

    /** Index of the button at (x, y) relative to the bubble box's top-left, or -1. */
    fun buttonAt(x: Float, y: Float): Int {
        val slop = 6 * density
        for (i in btnRects.indices) {
            val r = btnRects[i]
            if (x >= r.left - slop && x <= r.right + slop && y >= r.top - slop && y <= r.bottom + slop) return i
        }
        return -1
    }

    val hasButtons get() = buttons.isNotEmpty()
}
