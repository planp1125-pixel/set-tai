package com.combo.monkeycompanion.engine

import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.engine.anim.AnimTuning
import com.combo.monkeycompanion.engine.touch.BodyFeatures
import com.combo.monkeycompanion.engine.touch.ReactionEntry
import com.combo.monkeycompanion.engine.touch.ReactionLibrary

/**
 * v10 (M1): how a character moves, in one place instead of `character.id == "…"` checks in the overlay.
 * Values reproduce v9.5 exactly; later milestones tune [physics] per character.
 */
data class CharacterProfile(
    val id: String,
    val physics: PhysicsProfile = PhysicsProfile.DEFAULT,
    /** Climbs walls and ceilings with the IK profile climber (Settai). */
    val climbsWithIK: Boolean = false,
    /** v9.3 zero-gravity walk along all 4 screen edges, whatever the power setting (Billa, Chittu). */
    val walksScreenEdges: Boolean = false,
    /** v9.3 let go mid-screen: 1 s Tom & Jerry mid-air hold, then a cannonball fall (Billa, Chittu). */
    val coyoteOnMidScreenDrop: Boolean = false,
    /** Front-view comedy climb up a side edge and hang from the top (Mask Man; no climbing profile). */
    val clumsyClimber: Boolean = false,
    /** v9.3 can lose thrust on hitting a screen edge while flying and crash-land (Superboy, Armour Anna). */
    val edgeCrashOnFlight: Boolean = false,
    /** Roaming on the floor: chance to go climb a screen wall instead (null = the default for this kind of body). */
    val wallClimbChance: Float? = null,
    /** Room below the boots for hover-jet flames, in rig units. */
    val hoverJetPadRig: Float = 18f,
    /** Hovering fliers, when idle: chance to take off on a patrol flight somewhere else on the screen. */
    val airPatrolChance: Float = 0.3f,
    /** Hovering fliers, when idle: extra chance to break into a mid-air dance (after the patrol chance). */
    val airDanceChance: Float = 0f,
    /** Drawing size vs the shared rig; below 1 for wide characters so ears and wings fit the overlay window. */
    val drawScale: Float = 1f,
    /** v10.2 Gajju: a belly poke gets a trunk-full of water sprayed across the screen, then it flies off in a huff. */
    val bellySpray: Boolean = false,
    /** v10.4 Mask Man: juggles three balls whenever he's standing about; moving him makes him wobble and drop them. */
    val juggles: Boolean = false,
    /** v10.7 Disney-principles animation feel (squash, follow-through springs, easing). Visual only. */
    val anim: AnimTuning = AnimTuning.DEFAULT,
    /** v10.8 touch engine: which optional body bits exist (the shared reaction library only offers verbs it can do). */
    val body: BodyFeatures = BodyFeatures(),
    /** v10.8 touch engine: this character's own reaction entries on top of the shared library. */
    val reactions: List<ReactionEntry> = emptyList(),
    /** v10.8 rides a prop under the body (Rocket Girl's rocket): taps on it are the PROP body part. */
    val ridesProp: Boolean = false,
) {
    /** v10.8 the full reaction table (built once per profile). */
    val reactionTable: List<ReactionEntry> by lazy { ReactionLibrary.build(body, reactions) }
}

/** Gajju: big ears + spread bird wings are ~1.6x wider than the shared rig, so it is drawn smaller to fit the window. */
const val GAJJU_DRAW_SCALE = 0.58f

object CharacterProfiles {
    val MONKEY = CharacterProfile("monkey", climbsWithIK = true, anim = AnimTuning.MONKEY, body = BodyFeatures(tail = true, ears = true))
    val BILLA = CharacterProfile("billa", walksScreenEdges = true, coyoteOnMidScreenDrop = true, wallClimbChance = 0.55f, anim = AnimTuning.CAT,
        body = BodyFeatures(tail = true, ears = true))
    val CHITTU = CharacterProfile("chittu", walksScreenEdges = true, coyoteOnMidScreenDrop = true, wallClimbChance = 0.55f, anim = AnimTuning.MOUSE,
        body = BodyFeatures(tail = true, ears = true))
    val MASK = CharacterProfile("mask", clumsyClimber = true, juggles = true, anim = AnimTuning.MASK)
    val SUPERBOY = CharacterProfile("superboy", edgeCrashOnFlight = true, anim = AnimTuning.SUPERBOY)
    val ARMOUR = CharacterProfile("armour", edgeCrashOnFlight = true, hoverJetPadRig = 42f, anim = AnimTuning.ARMOUR)
    /** v10.1 flying elephant: loops all over the screen and dances in the air a lot. */
    val GAJJU = CharacterProfile("gajju", airPatrolChance = 0.45f, airDanceChance = 0.3f, drawScale = GAJJU_DRAW_SCALE, bellySpray = true, anim = AnimTuning.GAJJU,
        body = BodyFeatures(tail = true, ears = true, trunk = true))
    /** v10.8 Rocket Girl: rides a Diwali rocket; hovers, patrols in arcs, dances in the air now and then. */
    val ROCKETGIRL = CharacterProfile("rocketgirl", edgeCrashOnFlight = false, airPatrolChance = 0.5f, airDanceChance = 0.2f, hoverJetPadRig = 30f,
        anim = AnimTuning.ROCKET, body = BodyFeatures(prop = true), reactions = ReactionLibrary.ROCKET_GIRL, ridesProp = true)
    /** v10.14 Hollow Man: walks the floor, dances; the fedora is the follow-through piece. */
    val HOLLOWMAN = CharacterProfile("hollowman", anim = AnimTuning.HOLLOW)
    /** v10.14 Surfing Girl: hovers on her wave, patrols around the screen a lot, dances on the board now and then. */
    val SURFER = CharacterProfile("surfer", edgeCrashOnFlight = false, airPatrolChance = 0.6f, airDanceChance = 0.15f, hoverJetPadRig = 44f, anim = AnimTuning.SURF)
    /** Custom photo look-alikes and anything new. */
    val DEFAULT = CharacterProfile("default", anim = AnimTuning.CHIBI)

    /** Cached profiles: no allocation per frame. */
    fun of(c: Character): CharacterProfile = when (c.id) {
        "monkey" -> MONKEY
        "billa" -> BILLA
        "chittu" -> CHITTU
        "mask" -> MASK
        "superboy" -> SUPERBOY
        "armour" -> ARMOUR
        "gajju" -> GAJJU
        "rocketgirl" -> ROCKETGIRL
        "hollowman" -> HOLLOWMAN
        "surfer" -> SURFER
        else -> DEFAULT
    }
}
