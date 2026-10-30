package com.combo.monkeycompanion.ai

import com.combo.monkeycompanion.speech.Lines

/** Used when no brain is set up: canned monkey talk plus a hint to set one up. */
class CannedBrain(private val why: String? = null) : Brain {
    override val label = "No brain yet"
    override val supportsTools = false
    override val isReal = false

    override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit): String {
        val hint = why ?: "I don't have a brain yet! Open the Settai app → Settai's brain & settings to give me one."
        val reply = Lines.pick(Lines.RANDOM) + " " + hint
        onText(reply)
        return reply
    }
}
