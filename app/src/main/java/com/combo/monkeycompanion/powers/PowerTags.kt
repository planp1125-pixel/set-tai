package com.combo.monkeycompanion.powers

/**
 * Tag protocol for brains without tool calling (the on-device Gemma, or any model). The tags come from
 * [Capabilities] ([Capability.tag]): e.g. [alarm 07:00], [timer 300], [torch on], [open YouTube],
 * [volume media 60], [brightness 80], [dnd on], [media next], [switch wifi on], [battery]…
 * Tags are hidden from the user and turned into confirm-first actions (or answers, for readings).
 */
object PowerTags {
    private val names by lazy { Capabilities.all.mapNotNull { it.tag } + listOf("alarms") }
    private val tag by lazy { Regex("""\[\s*(${names.joinToString("|")})\b[:=]?\s*([^\]\n]{0,80}?)\s*]""", RegexOption.IGNORE_CASE) }
    private val partial by lazy { Regex("""\[\s*(${names.joinToString("|")})[^\]\n]{0,80}$""", RegexOption.IGNORE_CASE) }

    data class Parsed(val text: String, val calls: List<Invocation>)

    fun parse(raw: String): Parsed {
        val calls = ArrayList<Invocation>()
        val text = tag.replace(raw) { m ->
            toCall(m.groupValues[1].lowercase(), m.groupValues[2].trim())?.let { calls.add(it) }
            ""
        }
        return Parsed(text.replace(Regex("[ \\t]{2,}"), " ").replace(Regex(" +([,.!?])"), "$1").trim(), calls.distinct().take(2))
    }

    /** For streaming: hide complete tags and a half-typed one at the end. */
    fun visible(partialText: String): String = parse(partialText).text.let { partial.replace(it, "").trimEnd() }

    fun toCall(kind: String, arg: String): Invocation? {
        val cap = Capabilities.byTag(if (kind == "alarms") "timers" else kind) ?: return null
        val args = cap.fromTag?.invoke(arg) ?: return null
        return Invocation(cap.id, args)
    }
}
