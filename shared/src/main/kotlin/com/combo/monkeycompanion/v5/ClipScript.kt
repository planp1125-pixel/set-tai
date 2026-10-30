package com.combo.monkeycompanion.v5

/**
 * v5 "share a funny clip": a ~5 s timeline of one gag, rendered offscreen from the rig (never a screen recording).
 * Pure description of every frame; the app draws and encodes it (MediaCodec + MediaMuxer, H.264 / MP4).
 */
object ClipScript {
    const val W = 480
    const val H = 848
    const val FPS = 24
    const val SECONDS = 5f
    const val FRAMES = 120   // FPS * SECONDS
    val frames get() = (FPS * SECONDS).toInt()

    enum class Bit(val title: String, val state: String, val gag: String?, val caption: String) {
        DANCE("Dance", "DANCE", null, "When the salary gets credited 💃"),
        SLIP("Banana slip", "GAG", "SLIP", "Me walking into Monday 🍌"),
        AIR_GUITAR("Air guitar", "GAG", "AIR_GUITAR", "Ilaiyaraaja on loop 🎸"),
        SNEEZE("Sneeze", "GAG", "SNEEZE", "Achoo! Bless me 🤧"),
        JUGGLE("Juggle", "GAG", "JUGGLE", "Multitasking level: monkey 🍌🍌🍌"),
        SELFIE("Selfie", "GAG", "SELFIE", "New DP loading… 🤳"),
    }

    data class Frame(val index: Int, val t: Float, val walkX: Float, val captionAlpha: Float, val showLogo: Boolean)

    /** Frame [i]: intro walk-in (0-0.8 s), the bit, caption fades in at 0.6 s, logo in the last second. */
    fun frame(i: Int): Frame {
        val t = i / FPS.toFloat()
        val walk = if (t < 0.8f) -0.35f * (1f - t / 0.8f) else 0f
        return Frame(i, t, walk, ((t - 0.6f) / 0.5f).coerceIn(0f, 1f), t > SECONDS - 1f)
    }

    fun pick(text: String?): Bit {
        val t = text.orEmpty().lowercase()
        return Bit.entries.firstOrNull { b -> b.title.lowercase() in t || b.name.lowercase().replace('_', ' ') in t } ?: Bit.entries.random()
    }

    fun wants(text: String): Boolean {
        val t = text.lowercase()
        return listOf("make a clip", "make clip", "record a clip", "clip venum", "make a video", "share a clip", "funny clip").any { it in t }
    }

    fun caption(b: Bit, characterName: String) = "${b.caption}\n— $characterName"
}
