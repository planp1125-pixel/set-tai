package com.combo.monkeycompanion.brain

/**
 * v10.39.3: which build is running. The Play Store build has no screen agent (Android's Accessibility service is
 * removed from its manifest, see app/src/play), so nothing may offer it: no menu button, chat chip, switch or AI tool.
 * The app sets [screenAgent] once at start (ChatHub.init); the full build keeps the default.
 */
object Distribution {
    @Volatile var screenAgent: Boolean = true
    /** Items that need the screen agent (long-press menu ids and chat chip ids). */
    val SCREEN_ITEMS = setOf("screen", "summarise", "reply_for_me", "roast", "worth", "cheaper", "how_long")
    const val NO_AGENT_LINE = "I can't look inside other apps in this version of Set-Tai. I can still chat, open apps, set timers and play! 🙈"
}
