package com.combo.monkeycompanion.brain

/** v10.13 first-time demo on the floating character + 7 daily tips + "new" dots (pure, unit-tested). */
object Onboarding {
    enum class Gesture { TAP, DOUBLE_TAP, HOLD }
    data class Step(val expect: Gesture, val line: String, val praise: String)

    val steps = listOf(
        Step(Gesture.TAP, "👋 Hi! I'm Set-Tai. Tap me!", "Hehe, that tickles! 😆"),
        Step(Gesture.DOUBLE_TAP, "Now double-tap me! ⚡", "Boom! That's my power 🎆"),
        Step(Gesture.HOLD, "Now hold me! ✋", "This is my popup: games, shows, tools & more 🎉"),
    )
    const val MAX_SECS = 30f

    /** One demo run. [onGesture] returns the praise line when the expected gesture arrives. */
    class Run {
        var index = 0; private set
        var t = 0f; private set
        var skipped = false; private set
        val done get() = skipped || index >= steps.size || t >= MAX_SECS
        val current get() = steps.getOrNull(index)
        fun onGesture(g: Gesture): String? {
            if (done) return null
            val s = steps[index]
            if (s.expect != g) return null
            index++
            return s.praise
        }
        fun step(dt: Float) { t += dt }
        fun skip() { skipped = true }
    }

    val tips = listOf(
        "😋 I can bite your screen! Hold me › Games",
        "🐱🐭 Pick Billa & Chittu for a chase show!",
        "⏰ Water, tablet & eye-break reminders: Hold me › Tools",
        "🤳 Open your camera, I'll photobomb your selfie!",
        "💬 Tap 💬 to type or 🎤 to talk",
        "🎬 Hold me › Show › Record a funny clip to share",
        "⚡ Double-tap me any time for a random power",
    )

    /** Tip for install day [day] (0-based); null after the first 7 days or if already shown today. */
    fun tipFor(day: Long, lastTipDay: Long, blocked: Boolean): String? {
        if (blocked || day < 0 || day >= tips.size || lastTipDay == day) return null
        return tips[day.toInt()]
    }

    /** A popup item shows a "new" dot until first used. */
    fun isNew(id: String, used: Set<String>) = id !in used && id != "quit" && id != "back"
    fun label(label: String, new: Boolean) = if (new) "$label •" else label
}
