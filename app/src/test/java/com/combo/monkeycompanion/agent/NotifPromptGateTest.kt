package com.combo.monkeycompanion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** v10.21.1 the notification-summary question is asked once, not again and again. */
class NotifPromptGateTest {
    private val g = NotifPromptGate
    private val min = 60_000L
    @Before fun clean() = g.reset()

    @Test fun asksOnceThenStaysQuietForTheSameNotifications() {
        assertTrue(g.shouldAsk(1000L, 10 * min)); g.asked(1000L, 10 * min)
        assertFalse("another character, a minute later", g.shouldAsk(1000L, 11 * min))
        assertFalse("much later, nothing new, phone not unlocked", g.shouldAsk(1000L, 120 * min))
    }

    @Test fun laterComesBackAfterTheNextUnlockAndAGap() {
        g.asked(1000L, 10 * min)
        g.unlocked()
        assertFalse("too soon", g.shouldAsk(1000L, 20 * min))
        assertTrue(g.shouldAsk(1000L, 45 * min))
    }

    @Test fun newNotificationsCanBeOfferedButNotSpammed() {
        g.asked(1000L, 10 * min)
        assertFalse("new one, but only 5 minutes later", g.shouldAsk(5000L, 15 * min))
        assertTrue(g.shouldAsk(5000L, 41 * min))
    }

    @Test fun openedMeansSeen() {
        g.asked(1000L, 10 * min); g.opened(1000L); g.unlocked()
        assertFalse(g.shouldAsk(1000L, 200 * min))
        assertFalse("nothing at all", g.shouldAsk(0L, 300 * min))
    }
}
