package com.combo.monkeycompanion.v5

import java.time.LocalDate

/**
 * v5: on-device festival calendar (2026-2027), no network. Each day gets a themed prop, a pose and lines,
 * per character.
 *
 * Sources (checked 22 Oct 2026):
 *  - 2026: Tamil Nadu Government Gazette Extraordinary No. 721, G.O. Ms. No. 708 (Public), 11 Nov 2025,
 *    https://www.stationeryprinting.tn.gov.in/extraordinary/2025/721_Ex_II_1_2025.pdf
 *  - 2027: Tamil Nadu public holidays 2027 as reported by The Hindu, 22 Oct 2026,
 *    https://www.thehindu.com/news/national/tamil-nadu/tamil-nadu-government-releases-list-of-public-holidays-for-2027/article71611311.ece
 *    (same dates in Daily Thanthi / Indian Express Tamil the same day); the central list (ESIC RO Tamil Nadu,
 *    https://rotamilnadu.esic.gov.in/attachments/circularfile/Holiday_final_list_2027_1786507084.pdf) puts North-Indian
 *    Diwali on 29 Oct 2027 (Deepavali in Tamil Nadu is 28 Oct, Naraka Chaturdashi).
 *  - Ramzan / Bakrid follow moon sighting: the government dates are used but marked [approximate] (may move a day).
 *  - Fixed dates (New Year, Republic Day, Independence Day, Christmas) need no source.
 */
object Festivals {
    enum class Kind(val id: String, val title: String, val prop: Prop) {
        NEW_YEAR("newyear", "New Year", Prop.PARTY),
        PONGAL("pongal", "Pongal", Prop.PONGAL_POT),
        REPUBLIC("republic", "Republic Day", Prop.FLAG),
        RAMZAN("ramzan", "Ramzan", Prop.CRESCENT),
        TAMIL_NEW_YEAR("puthandu", "Tamil New Year", Prop.KOLAM),
        BAKRID("bakrid", "Bakrid", Prop.CRESCENT),
        INDEPENDENCE("independence", "Independence Day", Prop.FLAG),
        VINAYAGAR("vinayagar", "Vinayagar Chathurthi", Prop.MODAK),
        AYUDHA_POOJA("ayudha", "Ayudha Pooja", Prop.GARLAND),
        DEEPAVALI("deepavali", "Deepavali", Prop.DIYA),
        DIWALI("diwali", "Diwali", Prop.DIYA),
        CHRISTMAS("christmas", "Christmas", Prop.STAR),
    }

    /** Drawn by FestivalFx (shared rig). */
    enum class Prop { PARTY, PONGAL_POT, FLAG, CRESCENT, KOLAM, MODAK, GARLAND, DIYA, STAR }

    data class Day(val date: LocalDate, val kind: Kind, val approximate: Boolean = false)

    private fun d(y: Int, m: Int, day: Int, k: Kind, approx: Boolean = false) = Day(LocalDate.of(y, m, day), k, approx)

    val days: List<Day> = listOf(
        // 2026 (TN Gazette, G.O. Ms. No. 708)
        d(2026, 1, 1, Kind.NEW_YEAR), d(2026, 1, 15, Kind.PONGAL), d(2026, 1, 26, Kind.REPUBLIC),
        d(2026, 3, 21, Kind.RAMZAN, approx = true), d(2026, 4, 14, Kind.TAMIL_NEW_YEAR), d(2026, 5, 28, Kind.BAKRID, approx = true),
        d(2026, 8, 15, Kind.INDEPENDENCE), d(2026, 9, 14, Kind.VINAYAGAR), d(2026, 10, 19, Kind.AYUDHA_POOJA),
        d(2026, 11, 8, Kind.DEEPAVALI), d(2026, 12, 25, Kind.CHRISTMAS),
        // 2027 (TN list, 22 Oct 2026; Diwali from the central list)
        d(2027, 1, 1, Kind.NEW_YEAR), d(2027, 1, 15, Kind.PONGAL), d(2027, 1, 26, Kind.REPUBLIC),
        d(2027, 3, 10, Kind.RAMZAN, approx = true), d(2027, 4, 14, Kind.TAMIL_NEW_YEAR), d(2027, 5, 17, Kind.BAKRID, approx = true),
        d(2027, 8, 15, Kind.INDEPENDENCE), d(2027, 9, 4, Kind.VINAYAGAR), d(2027, 10, 8, Kind.AYUDHA_POOJA),
        d(2027, 10, 28, Kind.DEEPAVALI), d(2027, 10, 29, Kind.DIWALI), d(2027, 12, 25, Kind.CHRISTMAS),
    )

    /** The festival on [date] (Deepavali wins over Diwali when both fall on one day), or null. */
    fun on(date: LocalDate): Day? = days.filter { it.date == date }.minByOrNull { it.kind.ordinal }

    /** Next festival on or after [date] (null after 2027). */
    fun next(date: LocalDate): Day? = days.filter { !it.date.isBefore(date) }.minByOrNull { it.date }

    /** v10.8: the Deepavali / Diwali season (a week before to 3 days after) for the little diya and sparkle accents. */
    fun diwaliSeason(date: LocalDate): Boolean = days.any { (it.kind == Kind.DEEPAVALI || it.kind == Kind.DIWALI) &&
        !date.isBefore(it.date.minusDays(7)) && !date.isAfter(it.date.plusDays(3)) }

    fun covers(year: Int) = year in 2026..2027

    fun lines(k: Kind, characterId: String): List<String> {
        val base = when (k) {
            Kind.NEW_YEAR -> listOf("Happy New Year machan! 🎉 Resolution: more bananas.", "New year, same cute me 🎊")
            Kind.PONGAL -> listOf("Pongalo Pongal! 🍚 The pot is overflowing, like my love for you.", "Happy Pongal! Sweet pongal for everyone 🌾")
            Kind.REPUBLIC -> listOf("Happy Republic Day! 🇮🇳 Salute!", "Jai Hind! 🇮🇳")
            Kind.RAMZAN -> listOf("Ramzan Mubarak! 🌙 Biryani at your place?", "Eid Mubarak, machan! 🌙✨")
            Kind.TAMIL_NEW_YEAR -> listOf("Puthandu vazhthukkal! 🌸 Iniya Tamil puthaandu!", "Happy Tamil New Year! Kolam ready 🌸")
            Kind.BAKRID -> listOf("Bakrid Mubarak! 🌙", "Eid ul-Adha Mubarak! 🌙 Share the feast!")
            Kind.INDEPENDENCE -> listOf("Happy Independence Day! 🇮🇳 Flag up!", "Jai Hind! Freedom tastes like jalebi 🇮🇳")
            Kind.VINAYAGAR -> listOf("Happy Vinayagar Chathurthi! 🐘 Kozhukattai for me please!", "Ganapati Bappa! One modak per notification 🐘")
            Kind.AYUDHA_POOJA -> listOf("Happy Ayudha Pooja! I'm blessing your phone 🌼", "Ayudha Pooja: your phone gets a garland today 🌼")
            Kind.DEEPAVALI, Kind.DIWALI -> listOf("Happy Deepavali! 🪔 Oil bath done? Crackers later!", "Deepavali vazhthukkal! 🪔 Leave some sweets for me.")
            Kind.CHRISTMAS -> listOf("Merry Christmas! 🎄 Ho ho ho machan!", "Merry Christmas! Star on top ⭐")
        }
        val extra = when (characterId) {
            "superboy" -> "Zoom salutes the festival! ⚡"
            "armour" -> "Suit lights set to festival mode 🎆"
            "mask" -> "Masko brought sweets… from his belt 🍬"
            else -> "Pogo special: extra banana today 🍌"
        }
        return base + extra
    }
}

/** v5: 4 PM filter-coffee break (once a day), and the time window logic. Pure. */
object CoffeeBreak {
    const val START_MIN = 16 * 60
    const val END_MIN = 16 * 60 + 45

    /** Due when it's 16:00-16:45, not done today, and nothing holds it back. */
    fun due(minuteOfDay: Int, today: String, lastDay: String?, blocked: Boolean) =
        !blocked && minuteOfDay in START_MIN until END_MIN && lastDay != today

    /** Milliseconds from [minuteOfDay]:[second] to the next 16:00 (today or tomorrow). */
    fun msUntilNext(minuteOfDay: Int, second: Int = 0): Long {
        val now = minuteOfDay * 60 + second
        val at = START_MIN * 60
        val s = if (now < at) at - now else 24 * 3600 - now + at
        return s * 1000L
    }

    fun line(characterId: String): String = when (characterId) {
        "superboy" -> "4 PM! Even heroes need filter coffee ☕ Strong. Like me."
        "armour" -> "Coffee break! Suit's coffee port engaged ☕"
        "mask" -> "4 o'clock! Degree coffee from the utility belt ☕"
        else -> listOf("4 PM machan! Filter coffee break ☕ Davara-tumbler style!", "Coffee time! Pour from high, foam is everything ☕").random()
    }
}
