package com.combo.monkeycompanion.ai

import com.combo.monkeycompanion.speech.CharacterHumor
import com.combo.monkeycompanion.speech.Lines

object LocalChat {
    private val joke = Regex("""(?i)^(please\s+)?(tell(\s+me)?(\s+a)?\s+|say\s+|another\s+)?(joke|jokes)(\s+please)?[!?. ]*$""")
    private val move = Regex("""(?i)^(please\s+)?(dance|jump|wave|spin|sleep|hide)(\s+(for me|please))?[!?. ]*$""")
    fun reply(text: String): String? {
        val t = text.trim()
        if (joke.matches(t)) return Lines.pick(CharacterHumor.jokes())
        if (move.matches(t)) return ActionParser.requestedByUser(t)?.let { "[${it.tag}] " + when (it) {
            BodyAction.DANCE -> Lines.pick(Lines.DANCE)
            BodyAction.SLEEP -> Lines.pick(Lines.SLEEPY)
            BodyAction.HIDE -> Lines.pick(Lines.HIDE)
            else -> "On it!"
        } }
        return null
    }
}
