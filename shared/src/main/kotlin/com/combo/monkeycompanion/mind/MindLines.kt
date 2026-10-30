package com.combo.monkeycompanion.mind

import kotlin.random.Random

/** Offline, in-character templates for everything the mind says (the LLM is optional garnish). */
object MindLines {
    private fun <T> List<T>.pick(r: Random) = this[r.nextInt(size)]

    fun excuse(id: String) = when (id) {
        "superboy" -> "quick patrol of the city"; "armour" -> "suit maintenance break"; "mask" -> "secret mission (it's a nap)"; else -> "banana break"
    }
    fun leave(id: String) = when (id) {
        "superboy" -> "Citizens need me! Back in a bit 🦸"; "armour" -> "Running diagnostics. Don't touch anything."; "mask" -> "Psst… I was never here 🥷"; else -> "Banana break! 🍌 Back soon."
    }
    fun back(id: String) = when (id) {
        "superboy" -> "City's safe. Did you miss me?"; "armour" -> "Systems at 100%. I'm back."; "mask" -> "Ta-da! Missed me? 🎩"; else -> "I'm baaack 🍌 What did I miss?"
    }
    fun respectHide(id: String) = when (id) {
        "superboy" -> "Money stuff. Even heroes look away 🙈"; "armour" -> "Private financial data detected. Visor down."; "mask" -> "Not peeking! Promise! 🙈"; else -> "Money business… I'll look away 🙈"
    }
    fun dislike(id: String, s: Scene) = when (id) {
        "superboy" -> "${s.app}? Not my kind of adventure."; "armour" -> "${s.app}. Battery hog. Judging silently."; "mask" -> "Ugh, ${s.app}. So boring my mask is yawning."
        else -> "${s.app}?! Numbers make my head hurt 🙉"
    }

    fun attention(id: String, p: Personality, r: Random) = when (id) {
        "superboy" -> listOf("Hey! Need a hero for anything?", "I've been very heroic today. Just so you know.", "Pat me? For morale?")
        "armour" -> listOf("Status check: you OK?", "My sensors detect a lack of attention.", "Want to see a cool power? Just asking.")
        "mask" -> listOf("Psst. Over here. Hi. 👋", "Want to see a magic trick?", "I'm bored. Entertain me. Please?")
        else -> listOf("Ooh ooh! Play with me?", "Psst… got a ${p.fav.snack}?", "Pet me? Just once? 🥺")
    }.pick(r)

    fun habit(id: String, h: Habit?, p: Personality) = when (h) {
        Habit.MORNING_STRETCH -> when (id) { "superboy" -> "Morning! Hero stretches 💪"; "armour" -> "Morning boot sequence… stretching servos."; "mask" -> "Mornin'! *cracks every joint*"; else -> "Good morning! *big monkey stretch* 🙆" }
        Habit.AFTER_LUNCH_NAP -> "Post-lunch nap at my ${p.fav.napSpot.label}. Our tradition. Zzz…"
        Habit.EVENING_SNACK -> "Evening ${p.fav.snack} time! 😋"
        null -> "…"
    }

    /** A comment about the app in front. */
    fun comment(id: String, s: Scene, p: Personality, r: Random): String {
        val a = p.likes(s.cat); val app = s.app
        val place = com.combo.monkeycompanion.world.WorldPlaces.of(s.pkg, s.cat == AppCat.GAME)
        if (a >= 0.5f) return if (r.nextBoolean()) com.combo.monkeycompanion.world.WorldPlaces.placeLine(id, place, r) else loves(id, s, r)
        if (a <= -0.4f) return dislike(id, s)
        if (place.kind != com.combo.monkeycompanion.world.WorldPlaces.Kind.SIDE_STREET && r.nextFloat() < 0.65f) {
            return com.combo.monkeycompanion.world.WorldPlaces.placeLine(id, place, r)
        }
        return when (id) {
            "superboy" -> listOf("$app, huh? Any villains in there?", "Scanning $app… no danger detected.", "Ooh, $app. Carry on, citizen.")
            "armour" -> listOf("$app: analysing… mildly interesting.", "Logging $app to my database.", "$app again? Noted.")
            "mask" -> listOf("$app! What are we doing in here? 👀", "Is $app a secret lair?", "I've never been inside $app before…")
            else -> listOf("Ooh, $app! What's in here? 👀", "Hmm, $app. Smells interesting.", "$app? Can I help? I can't. But I'll watch.")
        }.pick(r)
    }

    private fun loves(id: String, s: Scene, r: Random): String = when (s.cat) {
        AppCat.VIDEO -> listOf("YouTube time?! Make it a monkey video 🐒", "Ooh, videos! Scoot over, I'm watching too 🍿")
        AppCat.FOOD -> listOf("FOOD?! Order me a banana shake 🍌", "Are we ordering? I'm VERY hungry. Always.")
        AppCat.MAPS -> listOf("Maps! Where are we flying? 🗺️", "Plotting a route… I could just fly you there.")
        AppCat.NEWS -> listOf("News! Any city in trouble? 🦸", "Reading the headlines… keeping an eye out for villains.")
        AppCat.CAMERA -> listOf("Camera! Get my good side 📸", "Wait wait, let me fix my mask first! 📸")
        AppCat.SOCIAL -> listOf("Scrolling? Tag me in something 😎", "Ooh, what's trending? Is it me?")
        AppCat.SETTINGS -> listOf("Settings! Finally, someone optimising things ⚙️", "Ooh, tweak the battery saver while you're here.")
        AppCat.MUSIC -> listOf("Music! Is this a dance cue? 🎶")
        AppCat.READING -> listOf("Reading? Respect. I'll be quiet… mostly.")
        AppCat.SHOPPING -> listOf("Shopping! Add something for me? 🛒")
        else -> listOf("I love ${s.app}! 💛")
    }.pick(r)
}
