package com.combo.monkeycompanion.mind

/**
 * v10.53 (Plan A, R1): the offline brain written in Rust (`mind/settai-mind`), shipped as libsettai_mind.so.
 * It reads a message and answers in the character's voice in well under a millisecond, fully on the phone.
 *
 * Always on as the character's own personality (no data leaves the phone). If the library is missing (an old
 * build, an unusual phone, unit tests), [available] is false and every call returns null: nothing breaks.
 */
object RustMind {
    /** What the brain read from a message. */
    data class Reading(val intent: String, val mood: String, val act: String, val gesture: String, val power: Boolean, val confidence: Float)
    data class Answer(val reading: Reading, val line: String, val micros: Long)

    @JvmStatic private external fun nativeVersion(): String
    @JvmStatic private external fun nativeUnderstand(text: String): String
    @JvmStatic private external fun nativeRespond(characterId: String, characterName: String, you: String, text: String, seed: Long, recent: IntArray): String
    @JvmStatic private external fun nativeLineHash(line: String): Int
    @JvmStatic private external fun nativeBenchNanos(text: String, rounds: Int): Long

    /** Why it isn't available (shown in Settings), or null when it is. */
    @Volatile var problem: String? = null; private set

    val available: Boolean by lazy {
        runCatching {
            // tests on a computer can point at a desktop build of the same library
            val path = System.getProperty("settai.mind.lib")
            val inSandbox = RustMind::class.java.classLoader?.javaClass?.name?.contains("Sandbox") == true
            if (inSandbox && System.getProperty("settai.mind.robolectric") != "true") error("not loaded in Robolectric tests")
            if (!path.isNullOrBlank()) System.load(path) else System.loadLibrary("settai_mind")
            nativeVersion().isNotEmpty()
        }.getOrElse { problem = it.javaClass.simpleName + ": " + (it.message ?: ""); false }
    }

    val version: String? get() = if (available) runCatching { nativeVersion() }.getOrNull() else null

    private fun parse(s: String): Reading? {
        val p = s.split('|')
        if (p.size < 6) return null
        return Reading(p[0], p[1], p[2], p[3], p[4] == "1", p[5].toFloatOrNull() ?: 0f)
    }

    fun understand(text: String): Reading? = if (!available) null else runCatching { parse(nativeUnderstand(text)) }.getOrNull()

    /** The last few lines said, so the brain doesn't repeat itself. */
    private val recent = ArrayDeque<Int>()

    /** Understand [text] and answer as [characterId] / [characterName], calling the person [you]. */
    fun respond(characterId: String, characterName: String, you: String, text: String, seed: Long = System.nanoTime()): Answer? {
        if (!available) return null
        return runCatching {
            val t0 = System.nanoTime()
            val out = synchronized(recent) { nativeRespond(characterId, characterName, you, text, seed, recent.toIntArray()) }
            val micros = (System.nanoTime() - t0) / 1000
            val nl = out.indexOf('\n')
            if (nl < 0) return null
            val reading = parse(out.substring(0, nl)) ?: return null
            val line = out.substring(nl + 1)
            synchronized(recent) {
                recent.addLast(nativeLineHash(line))
                while (recent.size > 8) recent.removeFirst()
            }
            Answer(reading, line, micros)
        }.getOrNull()
    }

    /** Average microseconds for one message (the speed shown in Settings). */
    fun benchMicros(text: String = "hi da, tell me a joke!", rounds: Int = 500): Float? =
        if (!available) null else runCatching { nativeBenchNanos(text, rounds) / 1000f }.getOrNull()
}
