package com.combo.monkeycompanion.brain

import com.combo.monkeycompanion.character.Character

/**
 * v10.17 power choice for the main character (Tutty): the user switches his powers on/off from the long-press
 * powers ring. No saved choice = the character's own powers
 * (the old behaviour). The main (signature) power is always on. [store] is wired by the app (null in tests).
 */
object PowerPrefs {
    @Volatile var store: ((String) -> Set<String>?)? = null

    /** Everything this character could use: its own powers first, then all others (no internal low-power routine). */
    fun available(c: Character): List<Superpower> = c.powers.filter { it != LowPowerPower }

    /** v10.17 (revised): only the main character (Tutty) has a choosable set; built-ins keep fixed powers and
     *  Companion Studio characters use the Studio's own multi-select. */
    fun choosable(c: Character): Boolean = c.id == "devil"

    /** The defaults (= the character's own powers). */
    fun defaults(c: Character): Set<String> = c.powers.map { it.id }.toSet()

    /** The powers switched on for [c], main power first. */
    fun enabled(c: Character): List<Superpower> {
        if (!choosable(c)) return c.powers
        val ids = runCatching { store?.invoke(c.id) }.getOrNull() ?: return c.powers
        val sig = c.signaturePower
        val list = available(c).filter { it.id in ids || it == sig }
        val ordered = if (sig != null && sig in list) listOf(sig) + (list - sig) else list
        return ordered.ifEmpty { c.powers }
    }

    fun isOn(c: Character, p: Superpower): Boolean = p == c.signaturePower || p in enabled(c)
}
