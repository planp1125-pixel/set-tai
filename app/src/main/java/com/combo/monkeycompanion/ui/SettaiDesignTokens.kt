package com.combo.monkeycompanion.ui

import android.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color as ComposeColor

/** Single source of UI color and geometry values for Compose and legacy feature screens. */
internal object SettaiDesignTokens {
    const val cornerCardDp = 20
    const val cornerControlDp = 16
    const val cornerPillDp = 24
    const val controlHeightDp = 48
    const val inputHeightDp = 52
    const val contentPaddingDp = 20
    const val sectionSpacingDp = 14

    const val lightBackground = 0xFFF1F1F1.toInt()
    const val lightSurface = Color.WHITE
    const val lightInk = 0xFF282828.toInt()
    const val lightMuted = 0xFF777777.toInt()
    const val lightPrimary = 0xFFB73540.toInt()
    const val lightOutline = 0xFFE1E1E4.toInt()
    const val lightInput = 0xFFF9F8FA.toInt()

    const val darkBackground = 0xFF171719.toInt()
    const val darkSurface = 0xFF27272A.toInt()
    const val darkInk = 0xFFF3F3F3.toInt()
    const val darkMuted = 0xFFB0B0B0.toInt()
    const val darkPrimary = 0xFFE05A64.toInt()
    const val darkOutline = 0xFF575762.toInt()
    const val darkInput = 0xFF24242D.toInt()

    fun primary(dark: Boolean) = if (dark) darkPrimary else lightPrimary
    fun ink(dark: Boolean) = if (dark) darkInk else lightInk
    fun surface(dark: Boolean) = if (dark) darkSurface else lightSurface
    fun outline(dark: Boolean) = if (dark) darkOutline else lightOutline
    fun input(dark: Boolean) = if (dark) darkInput else lightInput

    fun scheme(dark: Boolean): ColorScheme = if (dark) {
        darkColorScheme(
            primary = ComposeColor(darkPrimary), onPrimary = ComposeColor(darkBackground),
            background = ComposeColor(darkBackground), surface = ComposeColor(darkSurface),
            onSurface = ComposeColor(darkInk), onBackground = ComposeColor(darkInk),
            outline = ComposeColor(darkOutline), surfaceVariant = ComposeColor(darkInput),
            onSurfaceVariant = ComposeColor(darkMuted)
        )
    } else {
        lightColorScheme(
            primary = ComposeColor(lightPrimary), onPrimary = ComposeColor.White,
            background = ComposeColor(lightBackground), surface = ComposeColor(lightSurface),
            onSurface = ComposeColor(lightInk), onBackground = ComposeColor(lightInk),
            outline = ComposeColor(lightOutline), surfaceVariant = ComposeColor(lightInput),
            onSurfaceVariant = ComposeColor(lightMuted)
        )
    }
}
