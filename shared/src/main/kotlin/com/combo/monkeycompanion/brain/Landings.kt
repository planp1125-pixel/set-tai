package com.combo.monkeycompanion.brain

import kotlin.random.Random

/**
 * v4.4 "Smart landings": dropped mid-screen, the character decides how to come down (always its own random
 * choice, no setting). Weighted per character, never the same style twice in a row, after a short "decision"
 * beat (looks down, scratches head). Battery saver: short, effect-free variants only. Powers Off: non-power
 * landings only. Where it lands is still physics (bottom or the nearest platform). Pure, unit-tested.
 */
object Landings {
    /**
     * [power]: needs superpowers on. [fx]: uses particles / drawn props (skipped with battery saver).
     * [gravity] x normal fall gravity, [terminalDp] max fall speed (dp/s; 0 = none), [line] said on the way down.
     */
    enum class Style(val power: Boolean, val fx: Boolean, val gravity: Float, val terminalDp: Float, val lines: List<String>) {
        SIMPLE(false, false, 1f, 0f, emptyList()),
        CAPE_FLOAT(true, true, 0.22f, 230f, listOf("Up, up and… down! 🦸", "Super Banana descending 🍌")),
        JETS(true, true, 0.35f, 300f, listOf("Jets on! Controlled descent 🚀", "Landing thrusters ✓")),
        PARACHUTE(false, true, 0.2f, 190f, listOf("Leaf-chute deployed 🍃", "Floaty floaty ☂️")),
        PEEL_SLIDE(false, true, 0.55f, 520f, listOf("Wheee, banana slide! 🍌", "Sliiide 🛝")),
        CANNONBALL(false, false, 1.15f, 0f, listOf("CANNONBALL! 💣", "Incoming!!")),
        NINJA_FLIP(true, true, 1f, 0f, listOf("Hi-yah! 🥷", "Ninja landing 🥷")),
        VINE_SWING(false, true, 0f, 0f, listOf("Aaa-aa-aaah! 🌿 Tarzan time", "Vine, don't fail me now 🌿")),
        SCREAM_JETS(true, true, 1f, 0f, listOf("AAAAAAA— jets! JETS! 🚀", "I can't fly I can't fly— oh wait 🚀")),
        SNORE_FLOAT(false, false, 0.15f, 150f, listOf("Zzz… 💤", "So sleepy… mid-air… zzz")),
        SPEED_DIVE(true, true, 1.7f, 0f, listOf("Speed dive! ⚡", "Here I come!")),
        CHICKEN_FLAP(false, false, 0.4f, 320f, listOf("Bawk bawk! 🐔", "Flap flap flap!")),
        /** v5: curved rocket flight across the screen, then the character's own finish (see [LandingFlight]). */
        ROCKET_ARC(true, true, 0f, 0f, listOf("Launching! 🚀", "Let's take the scenic route 🚀", "Loop-de-loop time! 🌀")),
        /** v5 Mask Man "tries to fly": his crow grabs him by the collar, carries him a bit, then lets go. */
        CROW_CARRY(false, true, 0f, 0f, listOf("Crow! CROW! Up! 🐦‍⬛", "Taxi! Crow taxi! 🐦‍⬛")),
    }

    /** Decision beat before acting (seconds); none for a simple fall or with battery saver. */
    fun beat(s: Style, saver: Boolean) = if (s == Style.SIMPLE || saver) 0f else 0.55f

    /** Drops shorter than this just fall (no room for a show); never more than [MAX_SHORT_FRAC] of the screen. */
    const val MIN_DROP_DP = 140f
    /** v4.5: any drop above ~25 % of the screen height always gets the full show. */
    const val MAX_SHORT_FRAC = 0.25f
    fun minDropPx(density: Float, screenPx: Float) = minOf(MIN_DROP_DP * density, MAX_SHORT_FRAC * screenPx)

    /** Readable name for logs ("cape float"). */
    fun label(s: Style) = s.name.lowercase().replace('_', ' ')

    /**
     * Weighted styles for a character (before the power / saver / climb filters). v4.5: power landings carry
     * about half the weight (monkey 11/20, Mask Man 4/9 + his umbrella; Superboy and Anna are mostly powered).
     */
    fun pool(characterId: String, umbrella: Boolean): List<Pair<Style, Int>> = when {
        characterId == "superboy" -> listOf(Style.ROCKET_ARC to 5, Style.CAPE_FLOAT to 2, Style.SPEED_DIVE to 2, Style.CANNONBALL to 1)
        characterId == "armour" -> listOf(Style.ROCKET_ARC to 5, Style.JETS to 2, Style.SCREAM_JETS to 2, Style.CANNONBALL to 1)   // jet arcs, or a big suit thud
        characterId.startsWith("custom") -> {
            val fs = com.combo.monkeycompanion.character.CustomCompanionRegistry.specById(characterId)?.flightStyle
            when (fs) {
                com.combo.monkeycompanion.character.FlightStyle.SUPERBOY_CAPE_FLY ->
                    listOf(Style.ROCKET_ARC to 5, Style.CAPE_FLOAT to 2, Style.SPEED_DIVE to 2, Style.CANNONBALL to 1)
                com.combo.monkeycompanion.character.FlightStyle.IRONBOY_JET_FLY ->
                    listOf(Style.ROCKET_ARC to 5, Style.JETS to 2, Style.SCREAM_JETS to 2, Style.CANNONBALL to 1)
                com.combo.monkeycompanion.character.FlightStyle.ALL_HERO_MOVES ->
                    listOf(Style.ROCKET_ARC to 6, Style.CAPE_FLOAT to 2, Style.JETS to 2, Style.NINJA_FLIP to 2, Style.SPEED_DIVE to 2)
                else ->
                    listOf(Style.ROCKET_ARC to 4, Style.NINJA_FLIP to 3, Style.CAPE_FLOAT to 2, Style.JETS to 2, Style.PARACHUTE to 2)
            }
        }
        // v10.17 Kutti Satan: magic floaty landings; boot jets only if the user switched a jet power on for him
        characterId == "devil" -> listOf(Style.SNORE_FLOAT to 3, Style.NINJA_FLIP to 3, Style.CANNONBALL to 1) +
            (if (com.combo.monkeycompanion.character.CharacterRegistry.byId("devil")?.let { d -> PowerPrefs.enabled(d) }.orEmpty().any { it.id == "jets" || it.id == "rocket" || it.id == "boost" || it.id == "swoop" })
                listOf(Style.JETS to 2, Style.ROCKET_ARC to 3) else emptyList())
        characterId == "mask" -> listOf((if (umbrella) Style.PARACHUTE else Style.CHICKEN_FLAP) to 4, Style.CROW_CARRY to 3, Style.NINJA_FLIP to 2,
            Style.SNORE_FLOAT to 1, Style.CANNONBALL to 1)

        else -> listOf(
            Style.ROCKET_ARC to 5, Style.SCREAM_JETS to 3, Style.JETS to 2, Style.CAPE_FLOAT to 2, Style.NINJA_FLIP to 2,
            Style.PARACHUTE to 2, Style.PEEL_SLIDE to 2, Style.CANNONBALL to 2, Style.VINE_SWING to 2, Style.SNORE_FLOAT to 1,
        )
    }

    /** What may be picked now. Falls back to non-power styles, then [Style.SIMPLE]. */
    fun allowed(characterId: String, umbrella: Boolean, powersOn: Boolean, saver: Boolean, canClimb: Boolean): List<Pair<Style, Int>> {
        var p = pool(characterId, umbrella).filter { (s, _) ->
            (powersOn || !s.power) && (!saver || !s.fx) && (canClimb || s != Style.VINE_SWING)
        }
        if (p.isEmpty() && characterId == "superboy" && !saver) p = listOf(Style.CANNONBALL to 1)   // powerless hero: a heroic thud
        if (p.isEmpty() && characterId == "armour") p = listOf(Style.CANNONBALL to 1)
        if (saver) p = p + (Style.SIMPLE to 2)
        return p.ifEmpty { listOf(Style.SIMPLE to 1) }
    }

    /** Weighted random pick that never repeats [last] (unless it's the only option). */
    fun pick(options: List<Pair<Style, Int>>, last: Style?, rnd: Random = Random): Style {
        val opts = options.filter { it.second > 0 }.let { o -> if (o.size > 1) o.filter { it.first != last } else o }
        if (opts.isEmpty()) return Style.SIMPLE
        var r = rnd.nextInt(opts.sumOf { it.second })
        for ((s, w) in opts) { if (r < w) return s; r -= w }
        return opts.last().first
    }

    /** A choice and why (shown in Settings > Recent actions as "Landing: <style> (why)"). */
    data class Pick(val style: Style, val why: String)

    /**
     * The whole decision. [dropPx] = height above where it will land, [screenPx] = screen height.
     * [rushed] = an action is waiting for the landing (just drop). Not affected by power cooldowns: landings
     * only look at the powers On/Off setting, Settai's own battery saver and their own no-repeat memory.
     */
    fun decide(characterId: String, umbrella: Boolean, powersOn: Boolean, saver: Boolean, canClimb: Boolean,
               dropPx: Float, density: Float, last: Style?, screenPx: Float = Float.MAX_VALUE, rushed: Boolean = false,
               rnd: Random = Random): Pick {
        if (rushed) return Pick(Style.SIMPLE, "quick drop, something's waiting")
        val min = minDropPx(density, screenPx)
        if (dropPx < min) return Pick(Style.SIMPLE, "short drop: ${(dropPx / density).toInt()} dp < ${(min / density).toInt()} dp")
        val opts = allowed(characterId, umbrella, powersOn, saver, canClimb)
        val s = pick(opts, last, rnd)
        val why = buildList {
            add("own pick of ${opts.size}")
            if (!powersOn) add("powers Off: no power landings")
            if (saver) add("Settai battery saver: simple variants")
            if (last != null && last != Style.SIMPLE && opts.size > 1) add("not ${label(last)} again")
            if (screenPx != Float.MAX_VALUE) add("drop ${(100f * dropPx / screenPx).toInt()}% of screen")
        }.joinToString(", ")
        return Pick(s, why)
    }

    fun choose(characterId: String, umbrella: Boolean, powersOn: Boolean, saver: Boolean, canClimb: Boolean,
               dropPx: Float, density: Float, last: Style?, rnd: Random = Random): Style =
        decide(characterId, umbrella, powersOn, saver, canClimb, dropPx, density, last, rnd = rnd).style

    /** Fall speed step: gravity [g] px/s² scaled by the style, capped by its terminal speed. */
    fun fallStep(s: Style, vy: Float, g: Float, dt: Float, density: Float): Float {
        var v = vy + g * s.gravity * dt
        if (s.terminalDp > 0f) v = minOf(v, s.terminalDp * density)
        return v
    }

    /** Scream-then-jets: free fall until the last [SAVE_DP] above the ground, then hard braking to a soft speed. */
    const val SAVE_DP = 170f
    fun screamBrake(vy: Float, aboveGroundPx: Float, density: Float, dt: Float): Float =
        if (aboveGroundPx > SAVE_DP * density) vy else maxOf(120f * density, vy - 5200f * density * dt)

    /** Cannonball bounce: the first touchdown bounces up at this fraction of the impact speed. */
    fun bounce(impactVy: Float) = -0.38f * impactVy
}
