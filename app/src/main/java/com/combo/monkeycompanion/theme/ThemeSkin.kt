package com.combo.monkeycompanion.theme

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView

/**
 * v10.17.1 app skin applied to every app screen (never the overlay character): soft grey buttons in light mode,
 * and an optional dark mode (System / Light / Dark, saved in prefs "theme_mode"). Works by restyling the view tree
 * after layout, so every screen gets the same look without touching each builder.
 */
object ThemeSkin {
    const val SYSTEM = "system"; const val LIGHT = "light"; const val DARK = "dark"

    // light
    const val BTN_LIGHT = 0xFFECECEC.toInt(); const val BTN_LIGHT_LINE = 0xFFD6D6D6.toInt(); const val TEXT_LIGHT = 0xFF1C1C1C.toInt()
    // dark
    const val BG_DARK = 0xFF0D0D0D.toInt(); const val CARD_DARK = 0xFF1A1A1A.toInt(); const val BTN_DARK = 0xFF2A2A2A.toInt()
    const val BTN_DARK_LINE = 0xFF3A3A3A.toInt(); const val TEXT_DARK = 0xFFEDEDED.toInt(); const val SUB_DARK = 0xFFB0B0B0.toInt()
    const val TINT_DARK = 0xFF2A1416.toInt()

    fun mode(a: android.content.Context): String =
        a.getSharedPreferences("settai_v3", 0).getString("theme_mode", SYSTEM) ?: SYSTEM
    fun setMode(a: android.content.Context, m: String) { a.getSharedPreferences("settai_v3", 0).edit().putString("theme_mode", m).apply() }

    fun isDark(a: android.content.Context): Boolean = when (mode(a)) {
        DARK -> true; LIGHT -> false
        else -> (a.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /** Hook an activity: restyle after every layout pass (cheap, idempotent). */
    fun attach(a: Activity) {
        val dark = isDark(a)
        if (dark) runCatching { a.window.setBackgroundDrawable(ColorDrawable(BG_DARK)) }
        val root = a.window.decorView
        root.viewTreeObserver.addOnGlobalLayoutListener { runCatching { skin(root, dark) } }
    }

    private fun lum(c: Int) = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
    private fun sat(c: Int): Float { val mx = maxOf(Color.red(c), Color.green(c), Color.blue(c)); val mn = minOf(Color.red(c), Color.green(c), Color.blue(c)); return if (mx == 0) 0f else (mx - mn) / mx.toFloat() }
    private fun neutralLight(c: Int) = Color.alpha(c) > 200 && lum(c) > 0.86f && sat(c) < 0.12f
    private fun pinkTint(c: Int) = Color.alpha(c) > 200 && lum(c) > 0.86f && sat(c) >= 0.04f && Color.red(c) > Color.green(c) + 6

    private fun solid(v: View): Int? = when (val b = v.background) {
        is ColorDrawable -> b.color
        is GradientDrawable -> if (b.colors == null) b.color?.defaultColor else null
        else -> null
    }
    private fun setSolid(v: View, c: Int, line: Int?) {
        when (val b = v.background) {
            is ColorDrawable -> v.setBackgroundColor(c)
            is GradientDrawable -> { b.mutate(); b.setColor(c); if (line != null) b.setStroke((1f * v.resources.displayMetrics.density).toInt().coerceAtLeast(1), line) }
            else -> Unit
        }
    }

    /** Pure decision used by tests: the new background for a view (null = keep). */
    fun bgFor(c: Int, isButton: Boolean, dark: Boolean): Int? = when {
        isButton && neutralLight(c) -> if (dark) BTN_DARK else BTN_LIGHT
        !dark -> null
        neutralLight(c) -> if (lum(c) > 0.97f) CARD_DARK else BG_DARK
        pinkTint(c) -> TINT_DARK
        else -> null
    }

    fun skin(v: View, dark: Boolean) {
        if (v.getTag(TAG) == true && v !is ViewGroup) return
        val isBtn = v is Button && v !is CompoundButton
        solid(v)?.let { c -> bgFor(c, isBtn, dark)?.let { nb -> setSolid(v, nb, if (isBtn) (if (dark) BTN_DARK_LINE else BTN_LIGHT_LINE) else null) } }
        if (v is TextView) {
            val tc = v.currentTextColor
            if (isBtn && !dark && solid(v) == BTN_LIGHT && lum(tc) > 0.8f) v.setTextColor(TEXT_LIGHT)
            if (dark && Color.alpha(tc) > 120 && lum(tc) < 0.45f && sat(tc) < 0.5f) v.setTextColor(if (lum(tc) < 0.3f) TEXT_DARK else SUB_DARK)
            else if (dark && Color.alpha(tc) > 120 && sat(tc) >= 0.5f && Color.red(tc) > 100 && lum(tc) < 0.4f) v.setTextColor(0xFFFF6A70.toInt())   // deep-red small text -> readable soft red
            if (dark && v is EditText) v.setHintTextColor(0xFF777777.toInt())
            v.setTag(TAG, true)
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) skin(v.getChildAt(i), dark)
    }
    private val TAG = "themeSkin".hashCode() or 0x7F000000
}
