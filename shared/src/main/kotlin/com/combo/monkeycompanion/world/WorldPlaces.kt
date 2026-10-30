package com.combo.monkeycompanion.world

import com.combo.monkeycompanion.mind.AppCat
import kotlin.random.Random

/**
 * The World Dictionary: apps are *places* the human visitor walks into, notifications are *visitors/mail* at the gate,
 * Wi-Fi/data is the *bridge* to the outside world, battery is *food/energy*, temperature is *weather*, storage is *clutter*.
 */
object WorldPlaces {
    enum class Kind(val title: String, val short: String, val icon: String, val desc: String) {
        HOME_CLEARING("the home clearing", "clearing", "🌿", "the open clearing under your icons"),
        MARKET("the noisy market", "market", "🏮", "where messages and chatter fly back and forth"),
        CINEMA("the cinema tent", "cinema tent", "🎪", "where moving pictures glow on the ceiling"),
        MIRROR_STUDIO("the mirror studio", "mirror studio", "🪞", "where faces and memories are captured"),
        PLAYGROUND("the playground", "playground", "🎠", "loud, fast and full of games"),
        WATCHTOWER("the watchtower", "watchtower", "🗼", "looking out over roads and distant cities"),
        BAZAAR("the spice & parcel bazaar", "bazaar", "🧺", "smells like snacks, parcels and warm food"),
        VAULT("the locked vault", "locked vault", "🔐", "private coin-counting room where we politely look away"),
        ENGINE_ROOM("the engine room", "engine room", "⚙️", "full of gears, switches and battery pipes"),
        LIBRARY("the quiet library", "library", "📜", "long scrolls, letters and reading lamps"),
        COUNCIL_HALL("the council hall", "council hall", "🏛️", "serious meetings — tiptoe only"),
        MUSIC_STAGE("the music stage", "music stage", "🎶", "beats humming through the floorboards"),
        SIDE_STREET("a quiet side street", "side street", "🛤️", "another corner of the phone"),
    }

    data class Place(val pkg: String, val app: String, val cat: AppCat, val kind: Kind) {
        /** e.g. "the WhatsApp market", "the YouTube cinema tent", "the HDFC locked vault", "the home clearing". */
        val worldName: String get() = when (kind) {
            Kind.HOME_CLEARING -> kind.title
            Kind.SIDE_STREET -> "$app Alley"
            else -> "the $app ${kind.short}"
        }
    }

    fun kindOf(cat: AppCat): Kind = when (cat) {
        AppCat.HOME -> Kind.HOME_CLEARING
        AppCat.CHAT, AppCat.SOCIAL -> Kind.MARKET
        AppCat.VIDEO -> Kind.CINEMA
        AppCat.CAMERA -> Kind.MIRROR_STUDIO
        AppCat.GAME -> Kind.PLAYGROUND
        AppCat.MAPS -> Kind.WATCHTOWER
        AppCat.FOOD, AppCat.SHOPPING -> Kind.BAZAAR
        AppCat.BANKING -> Kind.VAULT
        AppCat.SETTINGS, AppCat.CALCULATOR -> Kind.ENGINE_ROOM
        AppCat.BROWSER, AppCat.READING, AppCat.EMAIL, AppCat.NEWS -> Kind.LIBRARY
        AppCat.MEETING -> Kind.COUNCIL_HALL
        AppCat.MUSIC -> Kind.MUSIC_STAGE
        AppCat.OTHER -> Kind.SIDE_STREET
    }

    fun of(pkg: String, isGame: Boolean = false): Place {
        val cat = AppCat.of(pkg, isGame)
        val app = if (pkg.isBlank() || cat == AppCat.HOME) "Home" else AppCat.appName(pkg)
        return Place(pkg, app, cat, kindOf(cat))
    }

    /** World-framed reaction when the human walks into a place. */
    fun placeLine(characterId: String, p: Place, rnd: Random = Random): String {
        val w = p.worldName
        return when (p.kind) {
            Kind.MARKET -> when (characterId) {
                "superboy" -> listOf("So many messengers at $w! Keeping watch from the roof 🦸", "$w is buzzing today — any urgent dispatches?").random(rnd)
                "armour" -> listOf("Entering $w. High chatter density detected 🏮", "$w again — radio traffic is lively.").random(rnd)
                "mask" -> listOf("Ooh, $w! Gossip! Drama! Secret notes! 👀", "Peeking over the stalls at $w 🎭").random(rnd)
                else -> listOf("Ooh, we're at $w! So many voices chattering 🏮", "Sniffing around the stalls at $w 👀").random(rnd)
            }
            Kind.CINEMA -> when (characterId) {
                "armour" -> "The ${p.app} cinema tent. Big projector, heavy battery draw — I'm watching the gauge 🔋"
                else -> listOf("Ooh, $w! Dim the lights, I'm sitting on the front row 🍿", "Moving pictures in $w! Scoot over 🎪").random(rnd)
            }
            Kind.BAZAAR -> when (characterId) {
                "monkey" -> listOf("We're in $w!! Smells like warm snacks and bananas 🍌🧺", "$w! Toss a parcel down here! 😋").random(rnd)
                else -> listOf("Strolling through $w — smells delicious in here 🧺", "Parcel carts rolling through $w!").random(rnd)
            }
            Kind.MIRROR_STUDIO -> when (characterId) {
                "mask" -> "The mirror studio! Wait, let me tilt my hat for the glass 📸🎩"
                else -> "Inside $w — careful, my nose prints are on the lens 🪞📸"
            }
            Kind.WATCHTOWER -> when (characterId) {
                "superboy" -> "Up in $w! I can see every road from here 🗼🦸"
                else -> "Climbing $w to see where we're heading 🗺️"
            }
            Kind.VAULT -> when (characterId) {
                "superboy" -> "That's $w. Heroes don't peek at private treasure 🔐🙈"
                "armour" -> "Entered $w. Privacy shutters down 🔐"
                "mask" -> "Ooh, $w! Turning around, hands behind my back 🙈"
                else -> "Uh-oh, $w! Coin-counting business — I'll look the other way 🙈🔐"
            }
            Kind.ENGINE_ROOM -> when (characterId) {
                "armour" -> "Ah, $w! My favourite place — pipes, switches and clean gauges ⚙️✨"
                "monkey" -> "Ugh, $w… too many dials and numbers, not enough bananas 🙉"
                else -> "Walking through $w — don't touch the red lever ⚙️"
            }
            Kind.LIBRARY -> listOf("Quietly padding into $w 📜", "Long scrolls open in $w — I'll sit by the lamp 🕯️").random(rnd)
            Kind.COUNCIL_HALL -> "Shh… $w is in session. Tiptoeing! 🤫"
            Kind.PLAYGROUND -> "Wheee, $w! Game time under the glass 🎠"
            Kind.MUSIC_STAGE -> "The floorboards are humming from $w! 🎶💃"
            Kind.HOME_CLEARING -> "Back in the home clearing 🌿"
            Kind.SIDE_STREET -> "Wandering down $w 👀"
        }
    }

    /** Notifications are visitors or mail arriving at the gate. */
    fun visitorLine(characterId: String, app: String, summary: String, count: Int = 1): String {
        val place = of(app).kind.short
        return if (count >= 4) {
            when (characterId) {
                "superboy" -> "Whoa — $count visitors crowding the $app $place gate! I'm on crowd control 🦸🔔"
                "armour" -> "Visitor flood at the $app $place ($count knocking). Raising shield bubble 🛡️"
                "mask" -> "A whole parade ($count!) just marched into the $app $place! 🎺"
                else -> "Machan, $count visitors are knocking at the $app $place gate at once! 📬🐒"
            }
        } else {
            val clean = summary.take(65)
            when (characterId) {
                "superboy" -> "A courier just landed at the $app $place: “$clean” 📬"
                "armour" -> "Mail packet arrived at the $app $place: “$clean” 📨"
                "mask" -> "Psst! A visitor slipped a note under the $app door: “$clean” ✉️"
                else -> "Ooh, a visitor at the gate with a card from $app: “$clean” 📬"
            }
        }
    }

    /** Wi-Fi / data = bridges to the outside world. */
    fun bridgeLine(characterId: String, up: Boolean): String = if (up) {
        when (characterId) {
            "armour" -> "The outer bridge is back up! Signal flowing across the span 🌉📶"
            "superboy" -> "Bridge to the outside world is open again! Clear skies ahead 🌉"
            else -> "Yay, the Wi-Fi bridge is back up! Messengers can cross again 🌉📶"
        }
    } else {
        when (characterId) {
            "armour" -> "Outer bridge is down — we're an island on local power for now 🌉⚠️"
            else -> "Ooh, the bridge to the outside world went down… just us inside the glass 🏝️"
        }
    }
}
