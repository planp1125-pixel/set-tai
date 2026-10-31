package com.combo.monkeycompanion.theme

import android.graphics.Color

/**
 * v10.17 app theme: red / near-black / white, matching the icon. Every UI colour goes through [rgb] / [argb] / [parse],
 * which folds the old palette into the theme (alpha kept, so faint things such as the 💬 button stay faint):
 *  - neutrals: light -> white / soft grey, dark -> near-black, mid -> grey,
 *  - greens stay green (success / "on" meaning),
 *  - every other colour -> a red shade by brightness (pale tints, bright red, deep red, or near-black text).
 * Character art never uses this (it stays procedural in the rig renderers).
 */
object Th {
    const val RED = 0xFFE0101A.toInt()
    const val RED_DEEP = 0xFF8E0A10.toInt()
    const val RED_SOFT = 0xFFFF5A60.toInt()
    const val RED_TINT = 0xFFFDECEC.toInt()
    const val INK = 0xFF0D0D0D.toInt()
    const val INK_2 = 0xFF1C1C1C.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val PAPER = 0xFFF6F6F6.toInt()
    const val GREY = 0xFF8A8A8A.toInt()

    fun rgb(r: Int, g: Int, b: Int): Int = map(0xFF, r, g, b)
    fun argb(a: Int, r: Int, g: Int, b: Int): Int = map(a, r, g, b)
    fun parse(s: String): Int { val c = Color.parseColor(s); return map(c ushr 24, (c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF) }

    /** The themed colour for an old (a, r, g, b). Pure function. */
    fun map(a: Int, r: Int, g: Int, b: Int): Int {
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
        val v = mx / 255f
        val s = if (mx == 0) 0f else (mx - mn) / mx.toFloat()
        val hue = hue(r, g, b, mx, mn)
        val rgb = when {
            s < 0.2f || (v > 0.9f && s < 0.12f) -> when {          // neutral
                v > 0.94f -> WHITE
                v > 0.8f -> PAPER
                v < 0.2f -> INK
                v < 0.32f -> INK_2
                else -> grey(v)
            }
            hue in 85f..165f && s > 0.3f -> (0xFF shl 24) or (r shl 16) or (g shl 8) or b   // keep greens
            v > 0.88f && s < 0.4f -> RED_TINT                         // pastel backgrounds / cards
            v < 0.4f -> INK                                           // dark coloured text / panels
            v < 0.62f -> RED_DEEP
            s < 0.55f && v > 0.8f -> RED_SOFT
            else -> RED
        }
        return (a.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)
    }

    private fun grey(v: Float): Int { val g = (v * 255).toInt().coerceIn(0, 255); return (0xFF shl 24) or (g shl 16) or (g shl 8) or g }
    private fun hue(r: Int, g: Int, b: Int, mx: Int, mn: Int): Float {
        if (mx == mn) return 0f
        val d = (mx - mn).toFloat()
        val h = when (mx) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } * 60f
        return if (h < 0f) h + 360f else h
    }
}
