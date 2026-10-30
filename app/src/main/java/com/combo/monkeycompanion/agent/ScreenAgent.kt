package com.combo.monkeycompanion.agent

import java.util.concurrent.CopyOnWriteArrayList

/**
 * v4: one screen-agent "drive" = the screen tools used while answering one message (observe -> plan
 * -> act, by the brain's tool loop). Max [MAX_STEPS] steps, a visible "Settai is driving" state and a
 * stop switch (tap Settai or the notification).
 */
object ScreenAgent {
    const val MAX_STEPS = 15
    @Volatile var driving = false; private set
    @Volatile var steps = 0; private set
    @Volatile var stopRequested = false; private set
    @Volatile var last: ScreenSnapshot? = null
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun addListener(l: (Boolean) -> Unit) { listeners.addIfAbsent(l) }
    fun removeListener(l: (Boolean) -> Unit) { listeners.remove(l) }

    /** Called before each screen step. Returns a refusal for the model when it must stop. */
    @Synchronized fun step(): String? {
        if (!driving) { driving = true; steps = 0; stopRequested = false; listeners.forEach { it(true) } }
        if (stopRequested) return "STOPPED: the user pressed stop. Do not use screen tools again; tell them you stopped."
        steps++
        if (steps > MAX_STEPS) return "STEP LIMIT ($MAX_STEPS) reached. Stop now and tell the user what is done and what is left."
        return null
    }

    fun stop() { if (driving) stopRequested = true }

    /** The reply is finished: the drive ends. */
    @Synchronized fun end() {
        if (!driving) return
        driving = false; last = null
        listeners.forEach { it(false) }
    }
}
