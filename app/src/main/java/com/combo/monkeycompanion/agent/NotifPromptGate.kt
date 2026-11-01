package com.combo.monkeycompanion.agent

/**
 * v10.21.1 "You have N notifications, want a summary?" asks politely, ONCE:
 *  - only one character asks (this gate is shared by every overlay window);
 *  - it asks again only when NEW notifications came in, or after "Later" once you've unlocked the phone again,
 *    and never sooner than [MIN_GAP_MS] after the last ask;
 *  - once you've opened the summary, those notifications are never offered again.
 * Pure (times passed in), so it is unit-tested.
 */
object NotifPromptGate {
    const val MIN_GAP_MS = 30 * 60_000L

    private var lastAskAt = 0L
    private var offeredUpTo = 0L
    private var openedUpTo = 0L
    private var unlockedSinceAsk = false

    /** [newest] = time of the newest unread notification. */
    @Synchronized fun shouldAsk(newest: Long, now: Long): Boolean {
        if (newest <= 0L || newest <= openedUpTo) return false
        if (lastAskAt == 0L) return true
        if (now - lastAskAt < MIN_GAP_MS) return false
        return newest > offeredUpTo || unlockedSinceAsk
    }

    @Synchronized fun asked(newest: Long, now: Long) { lastAskAt = now; offeredUpTo = maxOf(offeredUpTo, newest); unlockedSinceAsk = false }

    /** The phone was unlocked: a "Later" may come back now (after the gap). */
    @Synchronized fun unlocked() { unlockedSinceAsk = true }

    /** The summary card was opened: everything up to [newest] is seen. */
    @Synchronized fun opened(newest: Long) { openedUpTo = maxOf(openedUpTo, newest) }

    @Synchronized fun reset() { lastAskAt = 0L; offeredUpTo = 0L; openedUpTo = 0L; unlockedSinceAsk = false }
}
