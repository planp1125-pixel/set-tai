package com.combo.monkeycompanion.v10131

import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.overlay.MonkeyView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v10.13.1: while a reply is loading the popup still opens, and the thinking cloud has a Stop chip. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ThinkingPopupTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun popupOpensWhileThinking() {
        ChatHub.init(ctx); MindHub.init(ctx)
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        CharacterRegistry.select("monkey")
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.layout(0, 0, 1080, 2340); v.debugFrames(60); v.debugSurf("FLOOR", frac = 0.45f); v.debugFrames(10); v.debugQuiet = true
        v.onThinking()
        assertTrue(v.debugThinking)
        assertTrue("thinking cloud offers Stop", v.debugBubbleChipIds.contains("menu"))
        v.debugOpenMenu()
        assertFalse("popup replaced the thinking cloud", v.debugThinking)
        assertTrue("popup is open (chips shown)", v.debugBubbleChipIds.isNotEmpty())
    }
}
