package com.combo.monkeycompanion.ai

import com.combo.monkeycompanion.speech.Lines

/** Used when no brain is set up: canned monkey talk plus a hint to set one up. */
class CannedBrain(private val why: String? = null) : Brain {
    override val label = "Local scripted brain"
    override val supportsTools = false
    override val isReal = false

    override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit): String {
        val hint = why ?: "Local mode. Give Brain enables cloud chat when you want it."
        val reply = Lines.pick(Lines.RANDOM) + " " + hint
        onText(reply)
        return reply
    }
}
