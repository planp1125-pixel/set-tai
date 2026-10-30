package com.combo.monkeycompanion.brain

/**
 * v4.3 gesture map (simple and predictable, every character; pure and unit-tested):
 *  - single tap anywhere on the character -> touch-zone reaction only (giggle, pat, "bad touch"...). Never a menu.
 *  - double-tap                           -> chat
 *  - long-press ~0.45 s, finger still     -> options menu (arc): Missed? · Screen · 🎤 Talk · Funny/context · ✕ Quit
 *        stays until an item / a tap outside it / ~8 s. 🎤 Talk listens (auto-stops on silence; tap again to send).
 *  - drag / fling                         -> move / throw / hide (moving past touch slop cancels the long-press;
 *        long-press then drag closes the menu and drags)
 * Wallpaper and the in-app preview have no menu: long-press there shows the coach line.
 */
object Gestures {
    const val DOUBLE_TAP_MS = 280L
    const val LONG_PRESS_MS = 450L
    const val MENU_MS = 8000L
    const val COACH = "Tap = tickle · Double-tap = chat · Long-press = menu 👆"
    const val COACH_SIMPLE = "Tap = tickle · Double-tap = chat 👆"

    enum class Press { NONE, MENU }

    /** A finger held [heldMs] after travelling [travelPx] (max distance from the down point); [slopPx] = touch slop. */
    fun press(heldMs: Long, travelPx: Float, slopPx: Float): Press =
        if (travelPx <= slopPx && heldMs >= LONG_PRESS_MS) Press.MENU else Press.NONE

    /** Moving past the slop is a drag, whether or not the menu already opened (it closes). */
    fun isDrag(travelPx: Float, slopPx: Float) = travelPx > slopPx

    /** A single tap is always a touch-zone reaction (no menu on tap since v4.3). */
    fun tap(zone: TouchZone): Boolean = zone != TouchZone.NONE

    /** Menu auto-closes after [MENU_MS]. */
    fun menuExpired(openForMs: Long) = openForMs >= MENU_MS

    /** Coach marks once per installed version (first run and after an update). */
    fun needsCoach(seenVersion: Long, currentVersion: Long) = seenVersion != currentVersion

    fun coachLine(menu: Boolean) = if (menu) COACH else COACH_SIMPLE
}
