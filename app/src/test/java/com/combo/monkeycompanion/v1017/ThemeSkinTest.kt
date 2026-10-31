package com.combo.monkeycompanion.v1017

import com.combo.monkeycompanion.theme.ThemeSkin
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeSkinTest {
    @Test fun buttonsAreSoftGreyNotWhite() {
        assertEquals(ThemeSkin.BTN_LIGHT, ThemeSkin.bgFor(0xFFFFFFFF.toInt(), isButton = true, dark = false))
        assertNull(ThemeSkin.bgFor(0xFFFFFFFF.toInt(), isButton = false, dark = false))
        assertNull(ThemeSkin.bgFor(0xFFE0101A.toInt(), isButton = true, dark = false))   // red primary stays red
    }
    @Test fun darkModeColours() {
        assertEquals(ThemeSkin.BTN_DARK, ThemeSkin.bgFor(0xFFFFFFFF.toInt(), true, true))
        assertEquals(ThemeSkin.CARD_DARK, ThemeSkin.bgFor(0xFFFFFFFF.toInt(), false, true))
        assertEquals(ThemeSkin.BG_DARK, ThemeSkin.bgFor(0xFFF0F0F0.toInt(), false, true))
    }
}
