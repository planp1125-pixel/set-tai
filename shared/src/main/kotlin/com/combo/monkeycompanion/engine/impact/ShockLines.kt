package com.combo.monkeycompanion.engine.impact

/**
 * v10.20 the charger learning moment: the very first plug-in shocks him (burnt soot face), the next time he
 * remembers. Lines per character (Brain off; with Brain on the AI speaks from Body Sense instead).
 */
object ShockLines {
    fun first(characterId: String): String = when (characterId) {
        "devil" -> "Ayyo! Even a devil can get shocked?! 😈⚡ My horns are smoking!"
        "superboy" -> "ZAP! Heroes aren't supposed to burn 😵⚡ Who plugged that in?!"
        "armour" -> "Suit short-circuit!! 🤖⚡ Smoke everywhere… cough cough"
        "mask" -> "My mask! My moustache! All burnt 😵⚡"
        "gajju" -> "Trunk ZAPPED! 🐘⚡ Paati said never touch the plug…"
        "rocketgirl" -> "Rocket fuel + current = BAD idea 🚀⚡ cough!"
        "hollowman" -> "Even invisible me can feel that! 👻⚡ Now I'm visible… and black!"
        "surfer" -> "Wipeout… by electricity?! 🏄⚡ My hair!"
        else -> "Current adikuthu!! ⚡ Who plugged that in?! *cough*"
    }

    fun remember(characterId: String): String = when (characterId) {
        "devil" -> "I remember you, current 😈 Careful this time…"
        "armour" -> "Insulation ON this time 🧤"
        "gajju" -> "Trunk away from the plug this time 🐘"
        else -> "I remember you, current! Careful this time… 🧤"
    }
}
