package com.combo.monkeycompanion.v1051

import com.combo.monkeycompanion.v5.Friendship
import org.junit.Assert.assertTrue
import org.junit.Test

/** v10.51.1: the brain is told the heart's percent and what the next levels unlock, so it never says "I don't know". */
class FriendshipAnswerTest {
    @Test fun level7At97PercentNamesTheGarlandNext() {
        val a = Friendship.xpFor(7); val b = Friendship.xpFor(8)
        val xp = a + ((b - a) * 0.97f).toInt()
        val d = Friendship.describe(xp)
        assertTrue(d, d.startsWith("Thick friends, level 7"))
        assertTrue(d, d.contains("97% of the way to level 8") || d.contains("96% of the way to level 8"))
        assertTrue(d, d.contains("level 8 unlocks flower garland"))
        assertTrue(d, d.contains("Best friends forever"))
        assertTrue(d, d.contains("already unlocked") && d.contains("moonwalk"))
    }

    @Test fun atTheTopEverythingIsUnlocked() {
        val d = Friendship.describe(Friendship.xpFor(10))
        assertTrue(d, d.contains("everything is unlocked"))
    }
}
