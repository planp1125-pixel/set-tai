package com.combo.monkeycompanion.overlay

import android.content.Context
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.CustomCharacter

/**
 * v10.13 per-character size for EVERY character (built-ins too). 1.0 = the character's normal look,
 * 1.25 = Large, 1.5 = XL (never capped below the chosen value; the overlay window grows to fit).
 */
object CompanionSize {
    private const val PREFS = "settai_v3"
    val CHOICES = listOf(1.0f to "Normal", 1.25f to "Large", 1.5f to "XL")
    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(c: Context, id: String): Float {
        val p = p(c)
        if (p.contains("size_$id")) return p.getFloat("size_$id", 1f).coerceIn(0.8f, 1.6f)
        // custom companions used companionScale (1.25 = their normal): keep their old look
        val ch = CharacterRegistry.byId(id)
        return if (ch is CustomCharacter) (ch.spec.companionScale / 1.25f).coerceIn(0.8f, 1.6f) else 1f
    }
    fun set(c: Context, id: String, m: Float) { p(c).edit().putFloat("size_$id", m.coerceIn(0.8f, 1.6f)).apply() }

    /** The biggest size in use: every overlay window is made big enough for it. */
    fun max(c: Context): Float = runCatching { CharacterRegistry.allWithCustom.maxOf { get(c, it.id) } }.getOrDefault(1f).coerceAtLeast(1f)
}
