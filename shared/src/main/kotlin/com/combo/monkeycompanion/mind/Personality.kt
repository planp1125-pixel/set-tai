package com.combo.monkeycompanion.mind

import kotlin.random.Random

/** Stable traits 0..1 (per character, a little different on every install, drifting slowly with friendship, or overridden by user sliders). */
data class Traits(
    val playfulness: Float, val boldness: Float, val laziness: Float,
    val curiosity: Float, val chattiness: Float, val drama: Float,
) {
    val energy: Float get() = (1f - laziness).coerceIn(0f, 1f)
    fun encode() = listOf(playfulness, boldness, laziness, curiosity, chattiness, drama).joinToString(",") { it.toString() }
    companion object {
        fun decode(s: String?): Traits? {
            val v = s?.split(',')?.mapNotNull { it.toFloatOrNull() } ?: return null
            return if (v.size == 6) Traits(v[0], v[1], v[2], v[3], v[4], v[5]) else null
        }
    }
}

enum class Corner(val label: String) { TOP_LEFT("top-left corner"), TOP_RIGHT("top-right corner"), BOTTOM_LEFT("bottom-left corner"), BOTTOM_RIGHT("bottom-right corner"), LEFT_EDGE("left edge"), RIGHT_EDGE("right edge") }

/** Favourites chosen once per install (random, persisted), so each human's creature is a bit unique. */
data class Favourites(
    val corner: Corner, val napSpot: Corner, val favApp: AppCat, val hatedApp: AppCat, val power: String, val snack: String,
) {
    fun encode() = listOf(corner.name, napSpot.name, favApp.name, hatedApp.name, power, snack).joinToString("|")
    companion object {
        fun decode(s: String?): Favourites? = runCatching {
            val p = s!!.split('|')
            Favourites(Corner.valueOf(p[0]), Corner.valueOf(p[1]), AppCat.valueOf(p[2]), AppCat.valueOf(p[3]), p[4], p[5])
        }.getOrNull()
    }
}

/** A character's personality: base traits + per-install jitter + favourites; [drift] with friendship and experiences, or user slider overrides. */
class Personality(val characterId: String, val seed: Long, val traits: Traits, val fav: Favourites) {

    /** Traits after drift (or user-configured behaviour slider override if set for [characterId]). */
    fun at(friendLevel: Int, intensity: Float): Traits {
        getCustomTraits(characterId)?.let { return it }
        val k = ((friendLevel - 1).coerceIn(0, 10)) * 0.015f
        val i = intensity.coerceIn(0f, 1f)
        fun s(v: Float) = (0.5f + (v - 0.5f) * (0.4f + 1.2f * i)).coerceIn(0f, 1f)   // intensity sharpens the character
        return Traits(s(traits.playfulness + k), s(traits.boldness + k), s(traits.laziness - k),
            s(traits.curiosity), s(traits.chattiness + k), s(traits.drama))
    }

    fun encode() = "$characterId~$seed~${traits.encode()}~${fav.encode()}"

    companion object {
        private val customTraits = HashMap<String, Traits>()

        @Synchronized fun setCustomTraits(characterId: String, traits: Traits?) {
            if (traits == null) customTraits.remove(characterId)
            else customTraits[characterId] = traits
        }

        @Synchronized fun getCustomTraits(characterId: String): Traits? = customTraits[characterId]

        @Synchronized fun clearCustomTraits(characterId: String) { customTraits.remove(characterId) }

        @Synchronized fun encodeCustomTraits(): String =
            customTraits.entries.joinToString(";") { "${it.key}=${it.value.encode()}" }

        @Synchronized fun decodeCustomTraits(s: String?) {
            customTraits.clear()
            s?.split(';')?.forEach { part ->
                val idx = part.indexOf('=')
                if (idx > 0) {
                    val id = part.substring(0, idx).trim()
                    val tr = Traits.decode(part.substring(idx + 1).trim())
                    if (id.isNotEmpty() && tr != null) customTraits[id] = tr
                }
            }
        }

        /** Base temperament per character. */
        fun base(characterId: String): Traits {
            getCustomTraits(characterId)?.let { return it }
            return when (characterId) {
                "superboy" -> Traits(playfulness = 0.6f, boldness = 0.9f, laziness = 0.2f, curiosity = 0.6f, chattiness = 0.7f, drama = 0.8f)
                "armour" -> Traits(playfulness = 0.35f, boldness = 0.6f, laziness = 0.3f, curiosity = 0.7f, chattiness = 0.55f, drama = 0.4f)
                "mask" -> Traits(playfulness = 0.9f, boldness = 0.5f, laziness = 0.5f, curiosity = 0.6f, chattiness = 0.8f, drama = 0.95f)
                "billa" -> Traits(playfulness = 0.8f, boldness = 0.75f, laziness = 0.65f, curiosity = 0.75f, chattiness = 0.65f, drama = 0.9f)
                "gajju" -> Traits(playfulness = 0.95f, boldness = 0.5f, laziness = 0.35f, curiosity = 0.8f, chattiness = 0.7f, drama = 0.55f)
                "rocketgirl" -> Traits(playfulness = 0.95f, boldness = 0.85f, laziness = 0.15f, curiosity = 0.8f, chattiness = 0.75f, drama = 0.8f)
                "hollowman" -> Traits(playfulness = 0.9f, boldness = 0.7f, laziness = 0.3f, curiosity = 0.6f, chattiness = 0.7f, drama = 0.9f)
                "skater" -> Traits(playfulness = 0.95f, boldness = 0.9f, laziness = 0.2f, curiosity = 0.8f, chattiness = 0.75f, drama = 0.6f)
                "angel" -> Traits(playfulness = 0.7f, boldness = 0.5f, laziness = 0.25f, curiosity = 0.8f, chattiness = 0.7f, drama = 0.5f)
                "devil" -> Traits(playfulness = 0.95f, boldness = 0.8f, laziness = 0.3f, curiosity = 0.9f, chattiness = 0.75f, drama = 0.8f)
                "surfer" -> Traits(playfulness = 0.95f, boldness = 0.9f, laziness = 0.15f, curiosity = 0.85f, chattiness = 0.75f, drama = 0.65f)
                "chittu" -> Traits(playfulness = 0.95f, boldness = 0.8f, laziness = 0.2f, curiosity = 0.9f, chattiness = 0.75f, drama = 0.75f)
                else -> Traits(playfulness = 0.85f, boldness = 0.55f, laziness = 0.55f, curiosity = 0.85f, chattiness = 0.6f, drama = 0.6f)
            }
        }

        /** Built-in likes (+) and dislikes (-) of each character per app category. */
        fun affinity(characterId: String, cat: AppCat): Float {
            val common = when (cat) { AppCat.BANKING -> -0.9f; AppCat.MEETING -> -0.6f; else -> 0f }
            val own = when (characterId) {
                "superboy" -> when (cat) { AppCat.MAPS -> 0.9f; AppCat.NEWS -> 0.9f; AppCat.VIDEO -> 0.3f; AppCat.CALCULATOR -> -0.3f; else -> 0f }
                "armour" -> when (cat) { AppCat.GAME -> -0.5f; AppCat.VIDEO -> -0.35f; AppCat.SETTINGS -> 0.7f; AppCat.SHOPPING -> 0.3f; AppCat.READING -> 0.4f; else -> 0f }
                "mask" -> when (cat) { AppCat.CAMERA -> 0.95f; AppCat.SOCIAL -> 0.9f; AppCat.MUSIC -> 0.5f; AppCat.READING -> -0.4f; else -> 0f }
                "billa" -> when (cat) { AppCat.FOOD -> 0.9f; AppCat.GAME -> 0.85f; AppCat.VIDEO -> 0.6f; AppCat.SETTINGS -> -0.5f; else -> 0f }
                "chittu" -> when (cat) { AppCat.FOOD -> 0.95f; AppCat.SHOPPING -> 0.85f; AppCat.MAPS -> 0.6f; AppCat.CALCULATOR -> -0.6f; else -> 0f }
                else -> when (cat) { AppCat.VIDEO -> 0.9f; AppCat.FOOD -> 0.95f; AppCat.CALCULATOR -> -0.7f; AppCat.SHOPPING -> 0.3f; AppCat.EMAIL -> -0.3f; else -> 0f }
            }
            return (common + own).coerceIn(-1f, 1f)
        }

        val snacks = mapOf(
            "superboy" to listOf("mango lassi", "masala dosa", "hero-sized vada pav"),
            "armour" to listOf("filter coffee", "murukku", "battery-shaped bajji"),
            "mask" to listOf("cotton candy", "pani puri", "mystery samosa"),
            "billa" to listOf("fried fish", "masala vadai", "warm milk"),
            "chittu" to listOf("masala cheese", "Guntur chilli bajji", "peanut chikki"),
            "monkey" to listOf("banana", "jackfruit", "banana chips"),
            "gajju" to listOf("sugarcane", "banana", "jaggery peanut balls"),
            "rocketgirl" to listOf("motichoor laddoo", "murukku", "rose milk"),
            "hollowman" to listOf("invisible ice cream", "filter coffee", "kara boondi"),
            "skater" to listOf("masala dosa", "cold coffee", "chocolate milkshake"),
            "angel" to listOf("payasam", "vanilla ice cream", "strawberry milk"),
            "devil" to listOf("red chilli bajji", "rose milk", "chocolate cake"),
            "surfer" to listOf("tender coconut", "mango ice cream", "banana chips"),
            "custom_1" to listOf("gulab jamun", "filter coffee", "chocolate ice cream"),
        )

        /** Seeded once per install. [powers] = the character's power ids (for a favourite). */
        fun seed(characterId: String, seed: Long, powers: List<String>): Personality {
            val r = Random(seed xor characterId.hashCode().toLong())
            val b = base(characterId)
            val custom = getCustomTraits(characterId)
            fun j(v: Float) = (v + (r.nextFloat() - 0.5f) * 0.2f).coerceIn(0.05f, 0.95f)
            val t = custom ?: Traits(j(b.playfulness), j(b.boldness), j(b.laziness), j(b.curiosity), j(b.chattiness), j(b.drama))
            val likes = AppCat.entries.filter { affinity(characterId, it) >= 0f && it != AppCat.HOME && it != AppCat.OTHER && it != AppCat.BANKING && it != AppCat.MEETING }
            val dislikes = AppCat.entries.filter { affinity(characterId, it) <= 0.1f && it != AppCat.HOME && it != AppCat.OTHER && it != AppCat.BANKING && it != AppCat.MEETING }
            val fav = likes.maxByOrNull { affinity(characterId, it) + r.nextFloat() * 0.6f } ?: AppCat.VIDEO
            val hated = dislikes.filter { it != fav }.minByOrNull { affinity(characterId, it) - r.nextFloat() * 0.6f } ?: AppCat.CALCULATOR
            val f = Favourites(Corner.entries[r.nextInt(Corner.entries.size)], Corner.entries[r.nextInt(4)], fav, hated,
                powers.getOrNull(if (powers.isEmpty()) 0 else r.nextInt(powers.size)) ?: "", (snacks[characterId] ?: snacks["custom_1"] ?: snacks["monkey"]!!).let { it[r.nextInt(it.size)] })
            return Personality(characterId, seed, t, f)
        }

        fun decode(s: String?): Personality? = runCatching {
            val p = s!!.split('~')
            Personality(p[0], p[1].toLong(), Traits.decode(p[2])!!, Favourites.decode(p[3])!!)
        }.getOrNull()
    }

    /** Liking for an app category, including this install's favourite / hated app. */
    fun likes(cat: AppCat): Float = (affinity(characterId, cat) + (if (cat == fav.favApp) 0.5f else 0f) - (if (cat == fav.hatedApp) 0.5f else 0f)).coerceIn(-1f, 1f)
}
