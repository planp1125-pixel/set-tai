package com.combo.monkeycompanion.ui
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Shared Material-like visual layer for legacy feature screens; preserves existing listeners and state. */
internal object MaterialScreenPolish {
    fun apply(activity: Activity, dark: Boolean = false, preserveCustomButtons: Boolean = false) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density + 0.5f).toInt()
        val accent = if (dark) 0xFFFF555B.toInt() else 0xFF6C5CE7.toInt()
        val tint = ColorStateList.valueOf(accent)
        fun shape(fill: Int, outline: Boolean = false) = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(fill)
            if (outline) setStroke(dp(1), if (dark) 0xFF575762.toInt() else 0xFFDDE0F0.toInt())
        }
        fun walk(v: View) {
            when (v) {
                is EditText -> {
                    v.background = shape(if (dark) 0xFF24242D.toInt() else 0xFFF9F8FF.toInt(), true)
                    v.setPadding(dp(16), dp(12), dp(16), dp(12))
                    v.minHeight = dp(52)
                }
                is Spinner -> {
                    v.background = shape(if (dark) 0xFF24242D.toInt() else 0xFFF9F8FF.toInt(), true)
                    v.minimumHeight = dp(52)
                    v.setPadding(dp(12), dp(8), dp(12), dp(8))
                }
                is Switch -> {
                    v.minHeight = dp(48)
                    if (Build.VERSION.SDK_INT >= 21) v.thumbTintList = tint
                }
                is CheckBox -> {
                    v.minHeight = dp(48)
                    if (Build.VERSION.SDK_INT >= 21) v.buttonTintList = tint
                }
                is RadioButton -> {
                    v.minHeight = dp(48)
                    if (Build.VERSION.SDK_INT >= 21) v.buttonTintList = tint
                }
                is SeekBar -> if (Build.VERSION.SDK_INT >= 21) {
                    v.progressTintList = tint
                    v.thumbTintList = tint
                }
                is Button -> {
                    v.isAllCaps = false
                    v.minHeight = dp(48)
                    if (!preserveCustomButtons) {
                        val primary = v.text.toString().contains("Save & play", true) || v.text.toString().contains("Start", true)
                        v.background = shape(if (primary) accent else if (dark) 0xFF24242D.toInt() else Color.WHITE, !primary)
                        v.setTextColor(if (primary || dark) Color.WHITE else 0xFF1E2240.toInt())
                    }
                }
                is ScrollView -> { v.clipToPadding = false; v.isFillViewport = true }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(activity.findViewById<ViewGroup>(android.R.id.content) ?: return)
    }
}
