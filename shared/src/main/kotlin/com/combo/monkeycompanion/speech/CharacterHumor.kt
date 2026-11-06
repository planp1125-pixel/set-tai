package com.combo.monkeycompanion.speech

import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.CharacterRegistry

/** v10.28: shared fallbacks must not turn every body into Settai the monkey. No RNG here. */
object CharacterHumor {
    private val monkeyWords = Regex("(?i)banana|monkey|monkeys|smart-ape|ooh ooh|🐒|🍌|🙉")
    val jokesById = mapOf(
        "monkey" to listOf("Why did I bring a banana to work? For the appeal!", "My banana budget is growing. My savings? Not so much.", "I opened a branch office. It was a tree."),
        "devil" to listOf("I hid my evil plan in a cloud. Now it's just a mist-chief!", "My horns are tiny. My prank paperwork is enormous.", "I summoned a scary shadow. It asked for a night-light."),
        "angel" to listOf("My halo slipped. Even kindness needs a little adjustment!", "I gave a cloud a hug. It felt on cloud nine.", "I tried to polish a star. It already had a glowing review."),
        "billa" to listOf("I caught the red dot! Then it filed an a-peel. Typical laser.", "I work very hard at napping. It's a full-time purr-fession.", "Chittu beat my trap again. I'm calling it modern art."),
        "chittu" to listOf("Billa set a trap. I left him a thank-you crumb.", "My cheese savings have holes. Still a solid investment!", "I'm not tiny. I'm travel-sized trouble."),
        "gajju" to listOf("I packed light. Just one trunk!", "My wings are tiny. My hugs are jumbo-sized.", "I tried tiptoeing. The floor applauded."),
        "superboy" to listOf("I rescued a sandwich. Hero work makes you hungry.", "My cape has one weakness: doors.", "Saving the day again. Does the day ever say thank you?"),
        "armour" to listOf("My suit needs an update. Apparently sarcasm has a patch.", "I ran a diagnostic. Result: spectacularly overqualified for standing here.", "The helmet's smart. The person looking for it? Wearing it."),
        "mask" to listOf("I pulled a rabbit from my hat. It charged me rent.", "My disguise was perfect. Even the mirror asked who I was.", "Secret mission: find the snack I hid from myself."),
        "rocketgirl" to listOf("Rocket parking is easy. It's the reverse gear that's exciting.", "I asked for more space. Mission control took that literally.", "My countdown got nervous. It needed a launch break."),
        "hollowman" to listOf("I won hide-and-seek. Nobody noticed.", "My moonwalk left no footprints. Very on-brand.", "The spotlight keeps asking where I am. I call that stage presence."),
        "surfer" to listOf("I waved at the ocean. It waved back. Very polite.", "My board is bored. Time to catch a wave!", "I tried surfing the web. My board got stuck in a tab."),
        "skater" to listOf("My board and I had a falling-out. Mostly falling.", "I nailed that trick! The pavement would like a word.", "Four wheels, two feet, one wildly optimistic landing.")
    )
    fun jokes(who: Character = CharacterRegistry.current): List<String> = jokesById[who.id]
        ?: (who as? com.combo.monkeycompanion.character.CustomCharacter)?.let { custom ->
            listOf("${custom.spec.relationship.callUser}, I dressed for adventure. Your screen was the destination!",
                "I'm ${custom.name}, your ${custom.noun}. Small screen, big personality!",
                "My ${custom.spec.outfitStyle.label.substringBefore(" (")} is ready. My dance steps? Still loading.")
        } ?: listOf("I'm ${who.name}. My big screen debut? A very small screen!", "I live rent-free on your screen. Excellent neighbourhood.", "I tried to step outside the screen. The edge had other plans.")

    fun pool(original: List<String>, replacement: List<String>?, who: Character = CharacterRegistry.current): List<String> {
        if (who.id == "monkey") return replacement ?: original
        // IDLE and BORED were not covered by the old RANDOM-only voice overrides.
        if (replacement == null && (original === Lines.IDLE || original === Lines.BORED || original === Lines.RANDOM)) return jokes(who)
        return (replacement ?: original).filterNot { monkeyWords.containsMatchIn(it) }.ifEmpty { jokes(who) }
    }
    fun safe(text: String, who: Character = CharacterRegistry.current): String =
        if (who.id != "monkey" && monkeyWords.containsMatchIn(text)) jokes(who).first() else text
    fun rest(id: String) = when (id) {
        "monkey" -> "banana break"; "devil" -> "secret prank-planning break"; "angel" -> "halo-polishing break"
        "billa" -> "catnap"; "chittu" -> "cheese break"; "gajju" -> "sugarcane break"
        "superboy" -> "quick patrol of the city"; "armour" -> "suit maintenance break"; "mask" -> "secret mission (it's a nap)"
        "rocketgirl" -> "rocket refuelling break"; "hollowman" -> "invisible dance rehearsal"; "surfer" -> "wave-watching break"; "skater" -> "board-check break"
        else -> "little rest"
    }
}
