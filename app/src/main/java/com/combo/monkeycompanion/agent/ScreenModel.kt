package com.combo.monkeycompanion.agent

/**
 * v4 screen agent: a platform-free view of the screen (built from the accessibility node tree by
 * [SettaiAccessibilityService]) so the compact text, the safety rules and target matching are
 * plain Kotlin and unit-testable.
 */
data class UiNode(
    val index: Int,
    val cls: String,
    val text: String = "",
    val desc: String = "",
    val viewId: String = "",
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val checked: Boolean? = null,
    val password: Boolean = false,
    val focused: Boolean = false,
    val depth: Int = 0,
    /** v4.2: index of the nearest kept ancestor (0 = none), and the top/left on screen (px) for row grouping. */
    val parent: Int = 0,
    val top: Int = 0,
    val left: Int = 0,
)

data class ScreenSnapshot(val pkg: String, val app: String, val nodes: List<UiNode>, val locked: Boolean = false) {
    val hasPassword get() = nodes.any { it.password }
}

object ScreenText {
    /** Short class name: android.widget.Button -> Button. */
    fun kind(n: UiNode): String {
        val c = n.cls.substringAfterLast('.')
        return when {
            n.password -> "PasswordField"
            n.editable -> "TextField"
            c.contains("Button", true) -> "Button"
            c.contains("CheckBox", true) || c.contains("Switch", true) || c.contains("Toggle", true) -> c
            c.contains("Image", true) && n.clickable -> "ImageButton"
            n.scrollable -> "List"
            n.clickable -> "Tappable"
            else -> "Text"
        }
    }

    private fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim()

    /**
     * Compact, numbered text for the model, e.g.
     * `App: WhatsApp (com.whatsapp)` / `[3] Button "Send"` / `[5] TextField "Message" (focused)`.
     * Password fields never show their content. Long screens are cut at [maxChars].
     */
    fun compact(s: ScreenSnapshot, maxChars: Int = 3500): String {
        val sb = StringBuilder()
        sb.append("App: ").append(s.app).append(" (").append(s.pkg).append(")\n")
        var seen = HashSet<String>()
        for (n in s.nodes) {
            val label = clean(if (n.password) "" else n.text.ifBlank { n.desc }).take(80)
            val interesting = n.clickable || n.editable || n.scrollable || label.isNotEmpty()
            if (!interesting) continue
            val k = kind(n)
            val line = buildString {
                append('[').append(n.index).append("] ").append(k)
                if (n.password) append(" (hidden: password)")
                else if (label.isNotEmpty()) append(" \"").append(label).append('"')
                if (n.desc.isNotBlank() && n.text.isNotBlank() && !n.password) append(" — ").append(clean(n.desc).take(40))
                if (n.checked != null) append(if (n.checked) " [on]" else " [off]")
                if (n.focused) append(" (focused)")
            }
            val key = "$k|$label"
            if (!n.clickable && !n.editable && !seen.add(key)) continue   // repeated static text
            if (sb.length + line.length > maxChars) { sb.append("… (more on screen; scroll to see)\n"); break }
            sb.append(line).append('\n')
        }
        if (s.nodes.isEmpty()) sb.append("(nothing readable on this screen)\n")
        return sb.toString().trimEnd()
    }

    /**
     * Finds what the model/user means by [target]: a "[n]" index, a bare number, or words matching
     * text / description / view id (exact > starts-with > contains; tappable ones first).
     */
    fun find(s: ScreenSnapshot, target: String, wantEditable: Boolean = false): UiNode? {
        val t = target.trim().removePrefix("[").removeSuffix("]").trim()
        t.toIntOrNull()?.let { i -> return s.nodes.firstOrNull { it.index == i } }
        val w = t.lowercase().trim('"', '\'', '“', '”')
        if (w.isEmpty()) return null
        fun lab(n: UiNode) = (n.text.ifBlank { n.desc }).lowercase().trim()
        val pool = s.nodes.filter { if (wantEditable) it.editable else true }
        fun score(n: UiNode): Int {
            val l = lab(n); val d = n.desc.lowercase(); val id = n.viewId.substringAfterLast('/').lowercase().replace('_', ' ')
            val m = when {
                l == w || d == w -> 30
                l.startsWith(w) || d.startsWith(w) -> 20
                w.length >= 3 && (l.contains(w) || d.contains(w)) -> 10
                w.length >= 3 && id.contains(w) -> 6
                else -> 0
            }
            if (m == 0) return 0
            return m + (if (n.clickable || n.editable) 5 else 0)
        }
        return pool.map { it to score(it) }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }
}
