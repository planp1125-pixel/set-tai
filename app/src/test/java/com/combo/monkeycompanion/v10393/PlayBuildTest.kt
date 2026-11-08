package com.combo.monkeycompanion.v10393

import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.agent.QuickChip
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.Distribution
import com.combo.monkeycompanion.brain.LongPressMenu
import com.combo.monkeycompanion.powers.CapGroup
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v10.39.3: the Play build offers no screen agent anywhere (menu, chips, switches, AI tools). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayBuildTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    @After fun after() { Distribution.screenAgent = true }

    @Test fun fullBuildOffersTheScreenAgent() {
        Distribution.screenAgent = true
        assertTrue(QuickChip.STANDARD.any { it.id == "screen" })
        assertTrue("screen" in LongPressMenu.groupItems("g_access"))
    }

    @Test fun playBuildHidesEveryScreenAgentEntry() {
        ChatHub.init(ctx)
        ChatHub.settings.setGroupEnabled(CapGroup.SCREEN, true)
        Distribution.screenAgent = false
        assertTrue(QuickChip.STANDARD.none { it.id in Distribution.SCREEN_ITEMS })
        assertTrue(QuickChip.STANDARD.any { it.id == "think" })
        assertFalse("screen" in LongPressMenu.groupItems("g_access"))
        assertTrue("chat" in LongPressMenu.groupItems("g_access"))
        val ring = LongPressMenu.layout(LongPressMenu.defaults(), emptyList(), emptyList(), false)
        assertFalse("screen" in ring.main + ring.more)
        assertFalse("AI tools never get the screen group", ChatHub.settings.groupEnabled(CapGroup.SCREEN))
        ChatHub.settings.setGroupEnabled(CapGroup.SCREEN, false)
    }
}
