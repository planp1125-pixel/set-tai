package com.combo.monkeycompanion.ai

import org.json.JSONObject

/** A function the model may call. [parameters] is a JSON-Schema object. */
class ToolSpec(val name: String, val description: String, val parameters: JSONObject)

/** Provides and runs tools (phone powers). */
interface ToolHost {
    val specs: List<ToolSpec>
    /** Runs a tool; returns a short JSON/text result for the model. Called off the main thread. */
    fun call(name: String, argsJson: String): String
    /** How many model<->tool rounds one reply may take (the v4 screen agent needs ~16). */
    val maxRounds: Int get() = 5
}

class BrainException(message: String) : Exception(message)

/** Anything that can think: online APIs, the on-device model, or the canned fallback. */
interface Brain {
    val label: String
    val supportsTools: Boolean
    /** True when a real AI is behind it (quips allowed). */
    val isReal: Boolean get() = true

    /**
     * Blocking call (run it off the main thread). [onText] receives the full visible
     * reply so far whenever it grows (streaming); returns the final reply.
     */
    fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit): String

    fun cancel() {}
    fun close() {}
}

/** Removes reasoning blocks some small models emit, and trims. */
fun cleanReply(s: String): String {
    var t = s.replace(Regex("(?s)<think>.*?</think>"), "")
    val open = t.indexOf("<think>")
    if (open >= 0) t = t.substring(0, open)
    return t.replace("**", "").trim()
}
