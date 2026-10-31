package com.combo.monkeycompanion.world

/**
 * Population & roles under the glass:
 *  - Settai ("monkey"): SCOUT — the mischief-maker, snack-hunter & scout
 *  - Armour Anna ("armour"): ENGINEER — inspects battery pipes, heat & storage clutter
 *  - Superboy ("superboy"): GUARD — patrols the top edge and handles visitor floods
 *  - Mask Man ("mask"): JESTER — theatrical wildcard, mirror-studio regular & comic relief
 */
object Population {
    enum class Mode(val id: String, val label: String, val maxCreatures: Int) {
        SOLO("SOLO", "Solo (1)", 1),
        DUO("DUO", "Duo (2)", 2),
        TRIO("TRIO", "Trio (3)", 3);

        companion object {
            fun of(s: String?): Mode = entries.firstOrNull { it.id.equals(s, ignoreCase = true) } ?: DUO
        }
    }

    enum class Role(val characterId: String, val title: String, val duty: String) {
        SCOUT("monkey", "Mischief-maker & scout", "Sniffs out new places, juggles visitor cards and hunts for banana-current"),
        ENGINEER("armour", "Chief engineer", "Watches the battery gauge, vents excess heat and keeps the engine room tidy"),
        GUARD("superboy", "Gate & sky guard", "Patrols the upper edge and keeps order when visitor notifications flood in"),
        JESTER("mask", "Theatrical wildcard", "Poses in the mirror studio, pulls props from his hat and lightens the mood"),
        SCHEMER("billa", "Plotting street cat", "Tiptoes after Chittu, sets comic traps that backfire and naps by the battery"),
        TRICKSTER("chittu", "Clever tiny mouse", "Zips across ledges with cheese & chillies, outsmarts Billa and whistles");

        companion object {
            fun of(characterId: String): Role = entries.firstOrNull { it.characterId == characterId } ?: SCOUT
        }
    }

    val allIds = listOf("devil", "monkey", "armour", "superboy", "mask", "billa", "chittu", "gajju", "rocketgirl", "hollowman", "surfer")

    fun displayName(id: String): String = when (id) {
        "superboy" -> "Superboy"
        "armour" -> "Armour Anna"
        "mask" -> "Mask Man"
        "billa" -> "Billa"
        "chittu" -> "Chittu"
        "gajju" -> "Gajju"
        "rocketgirl" -> "Rocket Girl"
        "hollowman" -> "Hollow Man"
        "surfer" -> "Surfing Girl"
        "devil" -> "Kutti Satan"
        "monkey" -> "Settai"
        else -> com.combo.monkeycompanion.character.CustomCompanionRegistry.byId(id)?.name ?: "Settai"
    }

    /**
     * Picks which creatures are on screen right now: [leadId] first, plus 0, 1 (DUO) or 2 (TRIO) neighbours
     * chosen according to current [WorldState] needs (Anna for battery/heat/engine room, Superboy for floods/watchtower,
     * Mask Man for mirror studio/cinema/evening, Settai for food bazaar/mischief).
     */
    fun activeIds(leadId: String, mode: Mode, state: WorldState, pool: List<String> = allIds): List<String> {
        if (mode == Mode.SOLO) return listOf(leadId)
        val candidates = pool.distinct().filter { it != leadId }
        val scored = candidates.map { id ->
            var s = 0f
            if ((leadId == "billa" && id == "chittu") || (leadId == "chittu" && id == "billa")) s += 10.0f
            when (id) {
                "armour" -> {
                    if (state.hungry || state.feeding) s += 3.0f
                    if (state.hot || state.cramped) s += 2.5f
                    if (state.currentPlace.kind == WorldPlaces.Kind.ENGINE_ROOM) s += 2.0f
                    s += 0.4f // default engineering buddy
                }
                "superboy" -> {
                    if (state.flooded || state.visitorsWaiting >= 2) s += 3.2f
                    if (state.currentPlace.kind == WorldPlaces.Kind.WATCHTOWER) s += 2.2f
                    s += 0.3f
                }
                "mask" -> {
                    if (state.currentPlace.kind in setOf(WorldPlaces.Kind.MIRROR_STUDIO, WorldPlaces.Kind.CINEMA, WorldPlaces.Kind.MUSIC_STAGE)) s += 2.5f
                    if (state.timeOfDay == TimeOfDay.EVENING) s += 1.0f
                    s += 0.2f
                }
                "monkey" -> {
                    if (state.currentPlace.kind in setOf(WorldPlaces.Kind.BAZAAR, WorldPlaces.Kind.PLAYGROUND)) s += 2.8f
                    s += 0.5f
                }
            }
            id to s
        }.sortedByDescending { it.second }.map { it.first }
        return (listOf(leadId) + scored).take(mode.maxCreatures)
    }
}
