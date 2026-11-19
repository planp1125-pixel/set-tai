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

/**
 * v10.48: a photo or document the user sent with a message (📎). [dataUrl] is a base64 data: URL for a
 * photo (image/jpeg, already shrunk) or a PDF; [text] is the content of a plain-text file.
 */
data class Attachment(val kind: Kind, val name: String, val mime: String, val dataUrl: String? = null, val text: String? = null) {
    enum class Kind { IMAGE, PDF, TEXT }
    /** How it shows in the chat log. */
    val label: String get() = when (kind) { Kind.IMAGE -> "🖼️ $name"; Kind.PDF -> "📄 $name"; Kind.TEXT -> "📄 $name" }

    companion object {
        /** Biggest PDF we send (the API takes up to ~32 MB, but phones and plans like small). */
        const val MAX_PDF_BYTES = 10 * 1024 * 1024
        /** Longest text file we send. */
        const val MAX_TEXT_CHARS = 40_000
        /** Photos are shrunk so the long side is at most this many pixels. */
        const val MAX_IMAGE_SIDE = 1600
        fun kindFor(mime: String, name: String): Kind? {
            val m = mime.lowercase(); val n = name.lowercase()
            return when {
                m.startsWith("image/") -> Kind.IMAGE
                m == "application/pdf" || n.endsWith(".pdf") -> Kind.PDF
                m.startsWith("text/") || m == "application/json" || m == "application/xml" ||
                    Regex("\\.(txt|md|csv|json|xml|html?|log|kt|java|py|js|ts|c|cpp|h|swift|yaml|yml|ini|srt)$").containsMatchIn(n) -> Kind.TEXT
                else -> null
            }
        }
    }
}

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

    /** v10.48: true when it can look at photos / documents (the ChatGPT brain). */
    val supportsAttachments: Boolean get() = false

    /** v10.48: [chat] with photos / documents attached to [user]; brains that can't see them ignore them. */
    fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, attachments: List<Attachment>, onText: (String) -> Unit): String =
        chat(system, history, user, tools, onText)

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
