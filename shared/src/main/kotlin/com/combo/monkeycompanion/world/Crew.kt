package com.combo.monkeycompanion.world

import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.CustomCompanionRegistry

/**
 * v10.12 free crew picker rules (pure, unit-tested). The user's picked partners are ALWAYS kept
 * (never overridden by auto scoring); empty slots are filled by [Population] scoring.
 */
object CrewRules {
    /** Every character the user can put in a crew: all built-ins (incl. Gajju & Skye) + custom companions. */
    val roster: List<String>
        get() = CharacterRegistry.all.map { it.id } + runCatching { CustomCompanionRegistry.allCharacters().map { it.id } }.getOrDefault(emptyList())

    fun resolve(
        leadId: String,
        mode: Population.Mode,
        partner1: String?,
        partner2: String?,
        state: WorldState,
        roster: List<String> = this.roster,
    ): List<String> {
        if (mode == Population.Mode.SOLO) return listOf(leadId)
        val pins = ArrayList<String>()
        partner1?.takeIf { it.isNotBlank() && it != leadId && it in roster }?.let { pins += it }
        partner2?.takeIf { it.isNotBlank() && it != leadId && it in roster && it !in pins }?.let { pins += it }
        val out = ArrayList<String>()
        out += leadId
        out += pins
        if (out.size < mode.maxCreatures) {
            val auto = Population.activeIds(leadId, Population.Mode.TRIO, state, roster).drop(1)
            for (id in auto + roster) { if (out.size >= mode.maxCreatures) break; if (id !in out) out += id }
        }
        return out.take(mode.maxCreatures)
    }
}
