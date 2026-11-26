package com.combo.monkeycompanion.speech

import com.combo.monkeycompanion.character.Character

/**
 * v10.47 what a character shows while its brain is thinking, instead of a plain "Hmm…": short, in its own style,
 * a new one every second or so ("Scheming…", "Plotting mischief…"). Studio characters get friendly general ones.
 */
object ThinkingWords {
    private val GENERAL = listOf("Thinking…", "Cooking up an answer…", "Hmm, let me see…", "Putting words together…", "Almost got it…", "Thinking really hard…")

    private val BY_ID = mapOf(
        "devil" to listOf("Scheming…", "Plotting mischief…", "Stirring the pot…", "Sharpening my horns…", "Brewing trouble…", "Checking my naughty list…"),
        "angel" to listOf("Asking the clouds…", "Polishing my halo…", "Thinking kind thoughts…", "Consulting the stars…", "Fluffing my wings…"),
        "monkey" to listOf("Peeling the answer…", "Consulting my banana…", "Swinging through ideas…", "Scratching my head…", "Monkeying around…"),
        "superboy" to listOf("Flying to the answer…", "Scanning the city…", "Using super-brain…", "Saving the day… one sec…", "Cape flapping…"),
        "armour" to listOf("Booting up…", "Running diagnostics…", "Charging the thrusters…", "Calculating…", "Tightening bolts…"),
        "mask" to listOf("Sneaking up on it…", "Gathering clues…", "Thinking in the shadows…", "Cracking the case…"),
        "rocketgirl" to listOf("Launching thoughts…", "3… 2… 1… thinking…", "Fuelling up…", "Plotting a course…"),
        "hollowman" to listOf("Echo… echo… thinking…", "Floating through ideas…", "Haunting the answer…", "Whispering to myself…"),
        "surfer" to listOf("Riding the wave…", "Waiting for the big one…", "Catching an idea…", "Paddling out…"),
        "skater" to listOf("Rolling it around…", "Doing a kickflip of thought…", "Grinding on it…", "Picking up speed…"),
        "mermaid" to listOf("Blowing thought bubbles…", "Diving for an answer…", "Asking the fishes…", "Swimming through ideas…"),
        "billa" to listOf("Licking my paw… thinking…", "Purring over it…", "Stalking the answer…", "Nine lives, one idea…"),
        "chittu" to listOf("Sniffing out the answer…", "Nibbling on it…", "Running in my wheel…", "Squeak… thinking…"),
        "gajju" to listOf("Thinking with my big brain…", "Remembering everything…", "Trunk-storming…", "Stomping through ideas…"),
    )

    /** Its own random numbers: never the companion frame loop's (GoldenTraceTest). */
    private val rng = java.util.Random()

    fun all(c: Character): List<String> = BY_ID[c.id] ?: GENERAL

    /** The [i]-th word for [c] (wraps around); a negative [i] picks one at random. */
    fun pick(c: Character, i: Int): String {
        val list = all(c)
        val k = if (i < 0) rng.nextInt(list.size) else i % list.size
        return list[k]
    }
}
