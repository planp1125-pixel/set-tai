package com.combo.monkeycompanion.agent

import kotlin.random.Random

/** v4.2 templated proactive lines (no brain needed, no battery spent). */
object ProactiveLines {
    private fun List<String>.pick() = com.combo.monkeycompanion.speech.CharacterHumor.safe(this[Random.nextInt(size)])

    fun otp(app: String, code: String) = "🔐 Courier slipped a vault key from $app: $code. Only for your eyes — I'm looking away! 🙈"

    fun delivery(app: String, what: String) = listOf(
        "📦 Parcel rider at the $app bazaar gate: $what! Run to the door! 🏃",
        "🛵 Courier outside the $app bazaar: $what. I can smell it already 😋",
        "📍 $app parcel update at the gate: $what.",
    ).pick()

    fun important(who: String, preview: String) = if (preview.isBlank()) "💌 $who is knocking at the market gate!" else listOf(
        "💌 $who just knocked at the gate: “$preview”",
        "📲 Courier card from $who: “$preview”",
    ).pick()

    fun burst(n: Int, who: String) = listOf(
        "📬 $n visitors crowding the gate ($who)! Want me to check their cards?",
        "📬 Busy day at the gate! $n cards from $who. Shall I sum them up?",
    ).pick()

    fun wellbeing(app: String, mins: Int) = listOf(
        "🌿 $mins minutes inside $app! Step back into the home clearing and stretch with me? 🙆",
        "👀 $mins min in the $app tent… your eyes want a 20-second look out the window!",
        "💧 $mins minutes away in $app. Water break? I'll guard the clearing 🐒",
    ).pick()

    fun full() = listOf("🔋 Feeding time is done — 100% full! Unplug the current pipe before I get a tummy ache 😵", "🔋 Full tummy! Bursting with banana-current ⚡").pick()

    fun morning(name: String?) = listOf(
        "☀️ Good morning${name?.let { ", $it" } ?: ""}! Ready to conquer the day? I've stretched already 🐒",
        "🌅 Rise and shine${name?.let { ", $it" } ?: ""}! Filter coffee first, then the world ☕",
    ).pick()

    fun night(name: String?) = listOf(
        "🌙 Still up${name?.let { ", $it" } ?: ""}? Your bed misses you. 5 more minutes, then sleep, okay? 😴",
        "🌙 Late-night scrolling detected! I'm yawning on your behalf 🥱",
    ).pick()

    private val APP_QUIPS = mapOf(
        "com.google.android.youtube" to listOf("📺 YouTube! Just one video, right? ...right? 😏", "🍿 Popcorn mode on. I'll judge the thumbnails."),
        "com.instagram.android" to listOf("📸 Instagram! Don't let the reels eat your evening 😅", "📸 Reels again? I'll count the scrolls."),
        "in.swiggy.android" to listOf("😋 Swiggy! Order me a banana shake too?", "🛵 Hungry? Same. Pick something tasty!"),
        "com.application.zomato" to listOf("🍛 Zomato time! Biryani or dosa? I vote both.", "😋 Food app open = happy monkey."),
        "in.amazon.mShop.android.shopping" to listOf("🛒 Amazon! Remember: want vs need 😄", "📦 Shopping? Want me to check if it's cheaper elsewhere?"),
        "com.flipkart.android" to listOf("🛒 Flipkart! Sale or no sale, wallet is watching 👀"),
        "com.netflix.mediaclient" to listOf("🎬 Netflix! Is it 'one episode' or 'one season'? 😏"),
        "com.spotify.music" to listOf("🎧 Music time! I'll dance if you play something peppy 💃"),
        "com.google.android.apps.maps" to listOf("🗺️ Going somewhere? Take me along! Tap “How long?” for the ETA."),
        "com.ubercab" to listOf("🚕 Booking a ride? Seat belt, and no fighting with the driver 😄"),
        "com.olacabs.customer" to listOf("🚕 Ola! Window seat for me please 🐒"),
        "com.rapido.passenger" to listOf("🏍️ Rapido! Hold on tight 😄"),
        "com.phonepe.app" to listOf("💸 Paying someone? I'll look away 🙈"),
        "com.google.android.apps.nbu.paisa.user" to listOf("💸 GPay open. I'm not peeking at your balance 🙈"),
        "com.android.camera" to listOf("📷 Camera! Wait, let me fix my fur first 🐒"),
    )

    fun appQuip(pkg: String): String? = APP_QUIPS[pkg]?.pick() ?: run {
        val pl = com.combo.monkeycompanion.world.WorldPlaces.of(pkg)
        if (pl.kind != com.combo.monkeycompanion.world.WorldPlaces.Kind.HOME_CLEARING &&
            pl.kind != com.combo.monkeycompanion.world.WorldPlaces.Kind.SIDE_STREET) {
            com.combo.monkeycompanion.world.WorldPlaces.placeLine(com.combo.monkeycompanion.character.CharacterRegistry.current.id, pl)
        } else null
    }

    val JOKES = listOf(
        "Why did the phone wear glasses? It lost its contacts! 🤓📱",
        "My battery and I have one thing in common: we both get cranky below 20%. 🔋😤",
        "I tried to catch some fog this morning... I mist. 🌫️",
        "Why don't monkeys use Wi-Fi in the jungle? Too many tree-mendous connection issues! 🌴📶",
        "I told my charger a joke. It didn't get it, it just plugged along. 🔌",
        "Filter coffee walks into a bar. Bartender: 'Why so strong today?' 'Decoction, machan.' ☕",
        "What do you call a monkey with a phone? A smart-ape! 🐒📱",
        "Your notifications called. They said 'ping'. That's it. That's the joke. 🔔",
    )
    fun joke() = com.combo.monkeycompanion.speech.CharacterHumor.jokes().pick()
}
