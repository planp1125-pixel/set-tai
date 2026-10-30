package android.text

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.noEmoji

/** Desktop stand-ins for the bits of android.text that CloudBubble uses (word-wrapped, centred text). */
open class TextPaint(flags: Int = 0) : Paint(flags)

open class Layout {
    enum class Alignment { ALIGN_NORMAL, ALIGN_CENTER, ALIGN_OPPOSITE }
}

class StaticLayout private constructor(text: String, private val paint: TextPaint, private val width: Int, private val align: Layout.Alignment) : Layout() {
    private val lines = ArrayList<String>()
    private val lineH = -paint.ascent() + paint.descent() + paint.textSize * 0.12f
    val height: Int
    val lineCount get() = lines.size

    init {
        for (para in noEmoji(text).split('\n')) {
            var cur = ""
            for (word in para.split(' ')) {
                val cand = if (cur.isEmpty()) word else "$cur $word"
                if (paint.measureText(cand) <= width || cur.isEmpty()) {
                    if (paint.measureText(cand) > width && cur.isEmpty()) {
                        // a very long word: hard-break it
                        var w = word
                        while (paint.measureText(w) > width && w.length > 1) {
                            var n = w.length
                            while (n > 1 && paint.measureText(w.substring(0, n)) > width) n--
                            lines.add(w.substring(0, n)); w = w.substring(n)
                        }
                        cur = w
                    } else cur = cand
                } else { lines.add(cur); cur = word }
            }
            lines.add(cur)
        }
        height = kotlin.math.ceil(lines.size * lineH).toInt()
    }

    fun draw(c: Canvas) {
        val old = paint.textAlign
        for ((i, l) in lines.withIndex()) {
            val y = i * lineH - paint.ascent()
            when (align) {
                Alignment.ALIGN_CENTER -> { paint.textAlign = Paint.Align.CENTER; c.drawText(l, width / 2f, y, paint) }
                Alignment.ALIGN_OPPOSITE -> { paint.textAlign = Paint.Align.RIGHT; c.drawText(l, width.toFloat(), y, paint) }
                else -> { paint.textAlign = Paint.Align.LEFT; c.drawText(l, 0f, y, paint) }
            }
        }
        paint.textAlign = old
    }

    class Builder private constructor(private val text: CharSequence, private val paint: TextPaint, private val width: Int) {
        private var align = Alignment.ALIGN_NORMAL
        fun setAlignment(a: Alignment) = apply { align = a }
        fun setIncludePad(b: Boolean) = this
        fun setLineSpacing(add: Float, mult: Float) = this
        fun setMaxLines(n: Int) = this
        fun build() = StaticLayout(text.toString(), paint, width, align)
        companion object {
            @JvmStatic fun obtain(text: CharSequence, start: Int, end: Int, paint: TextPaint, width: Int) = Builder(text.subSequence(start, end), paint, width)
        }
    }

    companion object {
        @JvmStatic fun getDesiredWidth(text: CharSequence, paint: TextPaint): Float =
            noEmoji(text.toString()).split('\n').maxOf { paint.measureText(it) }
    }
}
