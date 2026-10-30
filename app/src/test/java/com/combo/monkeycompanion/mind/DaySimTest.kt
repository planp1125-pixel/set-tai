package com.combo.monkeycompanion.mind

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.random.Random

/**
 * Scripted 24-hour simulation of Settai's mind (pure Kotlin, no LLM, deterministic with a seed).
 * Writes build/test-out/settai-v6-day-sim.txt with drive values, focus, utility scores, chosen actions,
 * reasons, diary entries and CPU timings.
 */
class DaySimTest {
    private val z = ZoneId.of("Asia/Kolkata")
    private fun ts(day: Int, h: Int, m: Int): Long =
        LocalDateTime.of(2026, 10, day, h, m).atZone(z).toInstant().toEpochMilli()
    private fun fmtTime(h: Int, m: Int) = "%02d:%02d".format(Locale.ROOT, h, m)
    private fun f(v: Float) = "%.2f".format(Locale.ROOT, v)

    @Test fun scriptedDaySimulationWritesReport() {
        val rnd = Random(2026_10_22L)
        val pers = Personality.seed("monkey", 42L, listOf("Super Banana", "Rocket Monkey", "Thunder Strike"))
        val mind = Mind("monkey", pers, Diary(z))
        mind.intensity = 0.6f
        mind.friendLevel = 4

        var cpuNs = 0L
        var steps = 0
        fun <T> timed(b: () -> T): T {
            val t0 = System.nanoTime()
            try { return b() } finally { cpuNs += System.nanoTime() - t0; steps++ }
        }

        // Seed yesterday's diary + two prior days of after-lunch naps so recall & emergent habits appear today.
        val day1 = 20; val day2 = 21; val today = 22
        mind.diary.add(Diary.Kind.SHAKEN, "YouTube", ts(day2, 19, 10))
        mind.diary.add(Diary.Kind.CHAT_LINE, "my boss thinks Excel is a vegetable", ts(day2, 20, 30))
        mind.diary.add(Diary.Kind.FESTIVAL, "Ayudha Poojai", ts(day1, 10, 0))
        for (d in listOf(day1, day2)) {
            val tNap = ts(d, 13, 45)
            mind.heartbeat(tNap, 13 * 60 + 45, rnd)
            mind.commit(Decision(Act.NAP, emptyMap(), "seeded lunch nap", Scene(minute = 13 * 60 + 45)), tNap)
            mind.onEvent(MindEvent.WOKE, tNap + 25 * 60_000L, 14 * 60 + 10, rnd)
        }
        mind.onEvent(MindEvent.SCREEN_OFF, ts(day2, 23, 15), 23 * 60 + 15, rnd)

        val out = StringBuilder()
        out.appendLine("================================================================================")
        out.appendLine("SETTAI v6.0 — SCRIPTED DAY SIMULATION LOG (2026-10-22, Asia/Kolkata)")
        out.appendLine("Character: Settai (monkey) | Friendship: Level ${mind.friendLevel} | Independence: ${(mind.intensity * 100).toInt()}%")
        out.appendLine("Seeded favourites: corner=${pers.fav.corner.label}, nap=${pers.fav.napSpot.label},")
        out.appendLine("  loves=${pers.fav.favApp.label}, dislikes=${pers.fav.hatedApp.label}, power=${pers.fav.power}, snack=${pers.fav.snack}")
        out.appendLine("Traits: playful=${f(mind.traits.playfulness)} bold=${f(mind.traits.boldness)} lazy=${f(mind.traits.laziness)} curious=${f(mind.traits.curiosity)} chatty=${f(mind.traits.chattiness)} drama=${f(mind.traits.drama)}")
        out.appendLine("================================================================================")
        out.appendLine()

        fun logStep(h: Int, m: Int, tag: String, d: Decision?) {
            val dr = mind.drives
            val fo = mind.focus(ts(today, h, m))
            out.appendLine("[${fmtTime(h, m)}] $tag")
            out.appendLine("  Drives : energy=${f(dr.energy)} bored=${f(dr.boredom)} curious=${f(dr.curiosity)} social=${f(dr.social)} grumpy=${f(dr.annoyance)} content=${f(dr.contentment)} | focus=${f(fo)}")
            if (d != null) {
                val topScores = d.scores.entries.sortedByDescending { it.value }.take(4).joinToString(", ") { "${it.key.name}=${f(it.value)}" }
                out.appendLine("  Choice : ${d.act.name} (${d.reason})" + if (topScores.isNotEmpty()) "  [top: $topScores]" else "")
                if (d.line != null) out.appendLine("  Says   : \"${d.line}\"" + if (d.wantsLlm) "  (LLM optional garnish eligible)" else "  (offline template)")
            }
        }

        fun sceneAt(h: Int, m: Int, pkg: String, kb: Boolean = false, fs: Boolean = false, call: Boolean = false, dnd: Boolean = false, quiet: Boolean = false, dwell: Int = 0) {
            val now = ts(today, h, m)
            val s = Scene(pkg, AppCat.of(pkg), keyboard = kb, fullscreen = fs, call = call, dnd = dnd, quietHours = quiet, minute = h * 60 + m, dwellSecs = dwell)
            val d = timed { mind.perceive(s, now, rnd) }
            mind.commit(d, now)
            logStep(h, m, "SCENE -> ${s.app} (${s.cat.label}, kb=$kb, fs=$fs, call=$call, dnd=$dnd)", d)
        }

        fun beatAt(h: Int, m: Int, note: String = "HEARTBEAT") {
            val now = ts(today, h, m)
            val d = timed { mind.heartbeat(now, h * 60 + m, rnd) }
            mind.commit(d, now)
            logStep(h, m, note, d)
        }

        fun eventAt(h: Int, m: Int, ev: MindEvent, subj: String = "") {
            val now = ts(today, h, m)
            val d = timed { mind.onEvent(ev, now, h * 60 + m, rnd, subj) }
            if (d != null) mind.commit(d, now)
            logStep(h, m, "EVENT -> $ev ${if (subj.isNotBlank()) "($subj)" else ""}", d)
        }

        // --- 07:15 Morning unlock (after 8 hours away) -> morning stretch ritual ---
        eventAt(7, 15, MindEvent.UNLOCK)
        sceneAt(7, 16, "com.android.launcher3")
        eventAt(7, 18, MindEvent.PET)

        // --- 08:05 Checking WhatsApp & typing a reply (hard respect override: never interrupt typing) ---
        sceneAt(8, 5, "com.whatsapp", kb = true)
        beatAt(8, 6, "HEARTBEAT (while typing in WhatsApp)")

        // --- 09:10 Opening Google Maps for the first time today (novel app) ---
        sceneAt(9, 10, "com.google.android.apps.maps")
        beatAt(9, 25)

        // --- 10:00 Long reading in Gmail & Docs -> focus rises, Settai eventually takes a banana break & returns ---
        sceneAt(10, 0, "com.google.android.gm", dwell = 180)
        for (min in listOf(2, 6, 8, 12, 16, 28, 32)) beatAt(10, min, "HEARTBEAT (long email session +${min}m)")

        // --- 11:30 Opening HDFC Bank app -> immediate respectful hide ---
        sceneAt(11, 30, "com.snapwork.hdfc")
        sceneAt(11, 35, "com.android.launcher3")
        beatAt(11, 36, "HEARTBEAT (after leaving banking app)")

        // --- 12:45 Ordering lunch on Swiggy (Settai loves food apps!) ---
        sceneAt(12, 45, "in.swiggy.android")
        beatAt(12, 50)

        // --- 13:35 Post-lunch lull -> emergent after-lunch nap habit at favourite nap spot ---
        sceneAt(13, 30, "com.android.launcher3")
        beatAt(13, 35, "HEARTBEAT (post-lunch window -> emergent habit)")
        eventAt(14, 10, MindEvent.WOKE)

        // --- 15:00 Zoom team call -> hard quiet respect ---
        sceneAt(15, 0, "us.zoom.videomeetings", call = true)
        beatAt(15, 10, "HEARTBEAT (during Zoom call)")

        // --- 16:15 User pokes Settai 3 times and shakes the phone ---
        sceneAt(16, 15, "com.android.launcher3")
        eventAt(16, 16, MindEvent.POKE)
        eventAt(16, 17, MindEvent.POKE)
        eventAt(16, 18, MindEvent.POKE)
        eventAt(16, 20, MindEvent.SHAKE)
        beatAt(16, 25)

        // --- 17:30 Opening Instagram and swiping Settai's bubble away twice (learns fewer interruptions) ---
        sceneAt(17, 30, "com.instagram.android")
        eventAt(17, 31, MindEvent.SWIPE_AWAY)
        eventAt(17, 32, MindEvent.SWIPE_AWAY)
        beatAt(17, 35, "HEARTBEAT (after swipe-away in Instagram)")

        // --- 18:20 Playing Banana Catch + funny chat ---
        eventAt(18, 20, MindEvent.GAME_WON, "banana catch")
        eventAt(18, 25, MindEvent.FUNNY_CHAT, "you eat more bananas than my uncle 😂")
        beatAt(18, 40)

        // --- 20:00 Watching YouTube fullscreen ---
        sceneAt(20, 0, "com.google.android.youtube")
        sceneAt(20, 5, "com.google.android.youtube", fs = true)
        beatAt(20, 15, "HEARTBEAT (fullscreen video)")

        // --- 21:30 Evening wind-down & recall ---
        sceneAt(21, 30, "com.android.launcher3")
        beatAt(21, 35)
        beatAt(21, 50)

        // --- 23:20 Quiet hours & screen off ---
        sceneAt(23, 20, "com.android.launcher3", quiet = true)
        beatAt(23, 25, "HEARTBEAT (quiet hours)")
        eventAt(23, 30, MindEvent.SCREEN_OFF)

        out.appendLine()
        out.appendLine("--------------------------------------------------------------------------------")
        out.appendLine("WHAT'S SETTAI THINKING AT END OF DAY:")
        out.appendLine(mind.thinking(ts(today, 23, 30)))
        out.appendLine()
        out.appendLine("DIARY PAGE (in-character, newest first):")
        for ((whenStr, line) in mind.diary.page("monkey", ts(today, 23, 30), 20)) {
            out.appendLine("  • [$whenStr] $line")
        }
        out.appendLine()
        out.appendLine("AI CHAT PROMPT INJECTION SNIPPET:")
        out.appendLine("  " + mind.diary.promptSnippet("monkey", ts(today, 23, 30), 5))
        out.appendLine()
        val avgUs = cpuNs / 1000.0 / steps.coerceAtLeast(1)
        out.appendLine("CPU PROFILE: $steps mind evaluations, %.2f µs average (%.3f ms total for the entire day)".format(Locale.ROOT, avgUs, cpuNs / 1e6))
        out.appendLine("================================================================================")

        val text = out.toString()
        val file = com.combo.monkeycompanion.TestPaths.out("settai-v6-day-sim.txt")
        file.writeText(text)
        assertTrue(file.exists() && file.length() > 1500)
        assertTrue("average decision under 500 µs: $avgUs µs", avgUs < 500.0)
    }
}
