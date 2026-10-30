package com.combo.monkeycompanion.heart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HeartAndCoPresenceTest {
    @Test fun moodDetectionInEnglishAndTanglish() {
        assertEquals(UserMood.STRESSED, HeartEngine.detectMoodFromText("machan romba vela today, mudiyala so stressed"))
        assertEquals(UserMood.SAD, HeartEngine.detectMoodFromText("feeling low and kastama iruku today 😢"))
        assertEquals(UserMood.ANXIOUS, HeartEngine.detectMoodFromText("I have a big job interview tomorrow, romba bayama iruku"))
        assertEquals(UserMood.TIRED, HeartEngine.detectMoodFromText("so exhausted and thookam varuthu after a long day"))
        assertEquals(UserMood.HAPPY, HeartEngine.detectMoodFromText("semma news machan, I got the job!! 🎉"))
        assertEquals(UserMood.NEUTRAL, HeartEngine.detectMoodFromText("what is the time right now"))

        // 1-4 AM phone rhythm detection
        assertEquals(UserMood.STRESSED, HeartEngine.detectMoodFromRhythm(minuteOfDay = 2 * 60 + 15, appSwitchesIn5Min = 6, continuousNightMins = 10))
        assertEquals(UserMood.TIRED, HeartEngine.detectMoodFromRhythm(minuteOfDay = 3 * 60, appSwitchesIn5Min = 1, continuousNightMins = 35))
        assertNull(HeartEngine.detectMoodFromRhythm(minuteOfDay = 14 * 60, appSwitchesIn5Min = 8, continuousNightMins = 50))
    }

    @Test fun nextDayCheckInSchedulingAndMorningRecall() {
        val store = CareCheckInStore()
        val tEvening = 1_000_000_000L
        val (mood, checkIn) = store.recordMessage("Machan I have a big coding interview tomorrow morning, wish me luck!", tEvening)
        assertEquals(UserMood.ANXIOUS, mood)
        assertNotNull(checkIn)
        assertEquals("interview", checkIn!!.kind)
        assertEquals(1, store.pending.size)

        // Prompt note includes mood, check-in topic, and warm Tamil Nadu friend example for Gemma 2B
        val prompt = HeartEngine.promptNote(store.currentMood, store.pending)
        assertTrue(prompt, prompt.contains("anxious") && prompt.contains("your interview") && prompt.contains("Example warm tone:"))

        // Encode/decode round-trip
        val restored = CareCheckInStore()
        restored.decode(store.encode())
        assertEquals(UserMood.ANXIOUS, restored.currentMood)
        assertEquals(1, restored.pending.size)

        // 2 hours later at night: not due yet
        assertNull(restored.popDueCheckIn(tEvening + 2 * 3600_000L, 23 * 60))

        // Next morning (8 hours later at 08:30 AM): check-in pops due!
        val due = restored.popDueCheckIn(tEvening + 8 * 3600_000L, 8 * 60 + 30)
        assertNotNull(due)
        val line = HeartEngine.checkInLine(due!!, "monkey")
        assertTrue(line, line.contains("interview") && line.contains("epdi pochu"))
        assertTrue("marked done after popping", restored.pending.isEmpty())
    }

    @Test fun physicalComfortSelectionAndLines() {
        assertEquals(ComfortKind.GLASS_KNOCK_HUG, HeartEngine.pickComfort(UserMood.SAD, "feeling lonely"))
        assertEquals(ComfortKind.OFFER_COFFEE, HeartEngine.pickComfort(UserMood.TIRED, "so exhausted"))
        assertEquals(ComfortKind.SIGN_CARD, HeartEngine.pickComfort(UserMood.STRESSED, "hold a cheer sign"))
        for (kind in ComfortKind.entries) {
            val sign = HeartEngine.signTextFor(UserMood.STRESSED, Random(1))
            val line = HeartEngine.comfortLine("monkey", kind, UserMood.STRESSED, sign)
            assertTrue(line.isNotBlank())
        }
    }

    @Test fun focusBuddyBreatheBuddyAndCampfireStateMachines() {
        // 1. FocusBuddy (25m Pomodoro)
        assertEquals(25, FocusBuddy.parseMinutes("study with me"))
        assertEquals(45, FocusBuddy.parseMinutes("focus 45m"))
        assertNull(FocusBuddy.parseMinutes("tell me a joke"))
        val fb = FocusBuddy()
        fb.start(25)
        assertTrue(fb.active)
        assertEquals("📚 25:00", fb.timerLabel())
        assertFalse(fb.step(60f))
        assertEquals("📚 24:00", fb.timerLabel())
        assertTrue("completes when remaining hits 0", fb.step(24 * 60f + 1f))
        assertFalse(fb.active)

        // 2. BreatheBuddy (60s: 4s inhale · 4s hold · 6s exhale)
        assertTrue(BreatheBuddy.wantsBreathe("help me calm down and breathe"))
        val bb = BreatheBuddy()
        bb.start()
        assertTrue(bb.active)
        assertEquals(BreatheBuddy.Phase.INHALE, bb.phaseAt(1f))
        assertEquals(BreatheBuddy.Phase.HOLD, bb.phaseAt(5f))
        assertEquals(BreatheBuddy.Phase.EXHALE, bb.phaseAt(10f))
        assertTrue("inhale expands aura", bb.radiusFraction(3.9f) > bb.radiusFraction(0.2f))
        assertEquals(1.0f, bb.radiusFraction(6f), 0.01f)
        assertTrue("exhale contracts aura", bb.radiusFraction(13.5f) < bb.radiusFraction(8.5f))
        assertFalse(bb.step(30f))
        assertTrue(bb.step(30.5f))
        assertFalse(bb.active)

        // 3. CampfireScene
        assertTrue(CampfireScene.isNightCampfireHour(23 * 60 + 15))
        assertTrue(CampfireScene.isNightCampfireHour(1 * 60 + 30))
        assertFalse(CampfireScene.isNightCampfireHour(14 * 60))
        assertTrue(CampfireScene.wantsCampfire("light a campfire"))
        val cf = CampfireScene()
        cf.start(byUser = true)
        assertTrue(cf.active && cf.manual)
        cf.step(10f)
        assertEquals(10f, cf.elapsed, 0.01f)
        cf.stop()
        assertFalse(cf.active)
    }

    @Test fun billaAndChittuCatMouseDuoAndChaseBehaviors() {
        val allIds = com.combo.monkeycompanion.character.CharacterRegistry.all.map { it.id }
        assertTrue("billa" in allIds && "chittu" in allIds)
        val billa = com.combo.monkeycompanion.character.CharacterRegistry.byId("billa")!!
        val chittu = com.combo.monkeycompanion.character.CharacterRegistry.byId("chittu")!!
        assertEquals("Billa", billa.name)
        assertEquals("Chittu", chittu.name)
        assertEquals(setOf(com.combo.monkeycompanion.character.Locomotion.WALK), billa.locomotion)
        assertEquals(setOf(com.combo.monkeycompanion.character.Locomotion.WALK), chittu.locomotion)

        // Selecting Billa in DUO mode automatically pairs Chittu as neighbour (and vice versa)
        val st = com.combo.monkeycompanion.world.WorldState()
        assertEquals(listOf("billa", "chittu"), com.combo.monkeycompanion.world.Population.activeIds("billa", com.combo.monkeycompanion.world.Population.Mode.DUO, st))
        assertEquals(listOf("chittu", "billa"), com.combo.monkeycompanion.world.Population.activeIds("chittu", com.combo.monkeycompanion.world.Population.Mode.DUO, st))

        // Director picks CAT_MOUSE_CHASE with a modular CatMouseBehaviors.GagId
        val dir = com.combo.monkeycompanion.world.Director(cooldownMs = 0L)
        val scene = dir.pickScene("billa", com.combo.monkeycompanion.world.Population.Mode.DUO, st, 14 * 60, 100_000L, rnd = Random(3))
        assertNotNull(scene)
        assertEquals(com.combo.monkeycompanion.world.Director.Kind.CAT_MOUSE_CHASE, scene!!.kind)
        assertNotNull(scene.catMouseGag)
        assertTrue(scene.leadLine.isNotBlank() && scene.buddyLine.isNotBlank())

        // Slapstick pose & prop rendering for every gag
        val pB = com.combo.monkeycompanion.rig.Pose()
        val pC = com.combo.monkeycompanion.rig.Pose()
        for (gag in com.combo.monkeycompanion.world.CatMouseBehaviors.GagId.entries) {
            com.combo.monkeycompanion.world.CatMouseBehaviors.applySlapstick(gag, 0.2f, 1.0f, pB, pC)
            com.combo.monkeycompanion.world.CatMouseBehaviors.applySlapstick(gag, 0.7f, 2.0f, pB, pC)
        }
}

    @Test fun playVsFullFlavorFlagsAndManifestRemoveRule() {
        val playManifest = com.combo.monkeycompanion.TestPaths.root("app/src/play/AndroidManifest.xml").readText()
        assertTrue(playManifest.contains("SettaiAccessibilityService"))
        assertTrue(playManifest.contains("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
        assertTrue(playManifest.contains("USE_FULL_SCREEN_INTENT"))
        assertTrue(playManifest.contains("""tools:node="remove""""))

        val mergedPlay = com.combo.monkeycompanion.TestPaths.root("app/build/intermediates/merged_manifests/playRelease/processPlayReleaseManifest/AndroidManifest.xml")
        if (mergedPlay.exists()) {
            val mp = mergedPlay.readText()
            assertFalse(mp.contains("SettaiAccessibilityService"))
            assertFalse(mp.contains("BIND_ACCESSIBILITY_SERVICE"))
            assertFalse(mp.contains("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
            assertFalse(mp.contains("USE_FULL_SCREEN_INTENT"))
        }

        com.combo.monkeycompanion.agent.ForegroundAppTracker.forcePlayModeForTest = true
        assertTrue(com.combo.monkeycompanion.agent.ForegroundAppTracker.isPlayStoreBuild(
            null
        ))
        com.combo.monkeycompanion.agent.ForegroundAppTracker.forcePlayModeForTest = false
        assertFalse(com.combo.monkeycompanion.agent.ForegroundAppTracker.isPlayStoreBuild(
            null
        ))
        com.combo.monkeycompanion.agent.ForegroundAppTracker.forcePlayModeForTest = null
    }
}
