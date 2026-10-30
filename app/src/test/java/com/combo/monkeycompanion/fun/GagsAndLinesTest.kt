package com.combo.monkeycompanion.`fun`

import com.combo.monkeycompanion.ai.ActionParser
import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.ai.Persona
import com.combo.monkeycompanion.brain.Gag
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.MoodController
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.Lines
import com.combo.monkeycompanion.speech.Speech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GagsAndLinesTest {
    private class FakeSpeech : Speech {
        val said = ArrayList<String>()
        override fun say(text: String, durationMs: Long) { said.add(text) }
        override fun hush() {}
        override val isSpeaking = false
    }

    @Test fun everyGagAnimatesWithFiniteValues() {
        val p = Pose(); val c = AnimContext()
        for (g in Gag.entries) {
            c.gag = g
            var st = 0f
            while (st <= g.seconds) {
                Animations.fill(p, MonkeyState.GAG, 10f + st, st, c)
                for (i in p.v.indices) assertTrue("$g at $st slot $i = ${p.v[i]}", p.v[i].isFinite())
                st += 0.05f
            }
        }
    }

    @Test fun exitsAnimateAndLeave() {
        val p = Pose(); val c = AnimContext()
        for (e in com.combo.monkeycompanion.brain.Exit.entries) {
            c.exit = e
            var st = 0f
            while (st <= e.seconds) {
                Animations.fill(p, MonkeyState.EXIT, st, st, c)
                for (i in p.v.indices) assertTrue("$e at $st slot $i", p.v[i].isFinite())
                st += 0.05f
            }
            Animations.fill(p, MonkeyState.EXIT, e.seconds, e.seconds, c)
            when (e) {
                com.combo.monkeycompanion.brain.Exit.FAINT -> assertTrue(p.fade < 0.05f)
                com.combo.monkeycompanion.brain.Exit.ROCKET -> assertTrue("off the top", -p.bodyY > 900f)
                com.combo.monkeycompanion.brain.Exit.POOF -> assertTrue(p.fade < 0.05f && p.puff > 0.9f)
            }
        }
    }

    @Test fun gagsSetTheirProps() {
        val p = Pose(); val c = AnimContext()
        fun at(g: Gag, st: Float): Pose { c.gag = g; Animations.fill(p, MonkeyState.GAG, st, st, c); return p }
        assertTrue(at(Gag.SNEEZE, 1.38f).sneeze > 0.5f)
        assertTrue(at(Gag.SLIP, 1.0f).peel > 0.5f)
        assertTrue(at(Gag.BINOCULARS, 2.0f).binoculars > 0.9f)
        assertTrue(at(Gag.FAKE_SLEEP, 2.2f).wink > 0.5f)
        assertTrue(at(Gag.JUGGLE, 1.5f).bananas > 0.9f)
        assertTrue(at(Gag.TAIL_HIDE, 1.5f).tailFront > 0.5f)
        assertTrue(at(Gag.CHASE_FLY, 1.0f).fly > 0.9f)
        assertTrue(at(Gag.AIR_GUITAR, 1.5f).guitar > 0.9f)
        assertTrue(at(Gag.SELFIE, 1.35f).flash > 0.5f)
        // and they don't leak into the next state
        Animations.fill(p, MonkeyState.IDLE, 1f, 1f, c)
        assertEquals(0f, p.guitar + p.bananas + p.fly + p.peel + p.binoculars + p.phone + p.sneeze + p.tailFront + p.wink, 0f)
    }

    @Test fun aiTagsTriggerGags() {
        assertEquals(listOf(BodyAction.JUGGLE), ActionParser.parse("[juggle] Watch this!").actions)
        assertEquals(listOf(BodyAction.AIR_GUITAR), ActionParser.parse("[airguitar] Thank you Chennai!").actions)
        assertEquals(listOf(BodyAction.SELFIE), ActionParser.parse("*takes a selfie* Say banana!").actions)
        assertEquals(listOf(BodyAction.FAKE_SLEEP), ActionParser.parse("[fakesleep] Zzz").actions)
        assertEquals(listOf(BodyAction.CHASE_FLY), ActionParser.parse("[chasefly] Come back!").actions)
        assertEquals("Watch this!", ActionParser.parse("[juggle] Watch this!").text)
        for (a in listOf(BodyAction.SNEEZE, BodyAction.SLIP, BodyAction.BINOCULARS, BodyAction.TAIL_HIDE))
            assertEquals(listOf(a), ActionParser.parse("[${a.tag}] ok").actions)
        for (a in BodyAction.entries) if (a.ordinal >= BodyAction.SNEEZE.ordinal && a != BodyAction.FLY && a !in listOf(BodyAction.SKATE, BodyAction.CHICKEN, BodyAction.GADGET)) assertEquals(a.name, a.gag?.name)
        assertEquals(null, BodyAction.FLY.gag)
        assertEquals(null, BodyAction.DANCE.gag)
        assertEquals(listOf(BodyAction.DANCE), ActionParser.parse("[dance] still works").actions)
    }

    @Test fun userRequestsForGags() {
        assertEquals(BodyAction.JUGGLE, ActionParser.requestedByUser("can you juggle?"))
        assertEquals(BodyAction.AIR_GUITAR, ActionParser.requestedByUser("play some air guitar"))
        assertEquals(BodyAction.SELFIE, ActionParser.requestedByUser("take a selfie"))
        assertEquals(BodyAction.SLIP, ActionParser.requestedByUser("slip on a banana peel"))
        assertEquals(BodyAction.CHASE_FLY, ActionParser.requestedByUser("chase that fly"))
        assertEquals(BodyAction.DANCE, ActionParser.requestedByUser("dance for me"))
        assertEquals(null, ActionParser.requestedByUser("what's the weather"))
    }

    @Test fun controllerPlaysGagsAndSpeaksTheLine() {
        val sp = FakeSpeech()
        val mc = MoodController(sp, null)
        assertTrue(mc.gag(Gag.SNEEZE))
        assertEquals(MonkeyState.GAG, mc.state)
        repeat(200) { mc.update(1f / 60f) }   // 3.3 s
        assertTrue("line after ACHOO", sp.said.isNotEmpty())
        assertEquals(MonkeyState.IDLE, mc.state)
    }

    @Test fun randomGagsHappenButNotTooOften() {
        val sp = FakeSpeech()
        val mc = MoodController(sp, null)
        var gags = 0; var was = false
        repeat((120 * 60 * 30)) {   // 2 hours at 30 fps
            mc.update(1f / 30f)
            val now = mc.state == MonkeyState.GAG
            if (now && !was) gags++
            was = now
        }
        assertTrue("gags in 2 h: $gags", gags in 8..40)
        val saver = MoodController(FakeSpeech(), null).apply { liveliness = 0.45f }
        var sg = 0; was = false
        repeat((120 * 60 * 30)) { saver.update(1f / 30f); val now = saver.state == MonkeyState.GAG; if (now && !was) sg++; was = now }
        assertTrue("saver gags $sg < normal $gags", sg < gags)
    }

    @Test fun linePoolsAreBigAndBeatsSplit() {
        assertTrue(Lines.IDLE.size >= 20); assertTrue(Lines.BORED.size >= 10); assertTrue(Lines.PICKUP.size >= 10)
        assertTrue(Lines.REST.size >= 8); assertTrue(Lines.HEAT.size >= 5); assertTrue(Lines.BATTERY_LOW.size >= 5)
        assertEquals(listOf("I'm awake!", "Mostly."), Lines.beats("I'm awake! || Mostly."))
        assertEquals("I'm awake! … Mostly.", Lines.flat("I'm awake! || Mostly."))
        assertEquals(listOf("Plain line"), Lines.beats("Plain line"))
        for (g in Gag.entries) assertTrue(Lines.gag(g).isNotBlank())
        // no immediate repeats
        var last = ""
        repeat(200) { val l = Lines.pick(Lines.IDLE); assertFalse(l == last); last = l }
        assertTrue(Lines.idle(quietSeconds = 120f).isNotBlank())
        assertTrue(Lines.welcomeBack(8 * 3600_000L, 9) in Lines.LONG_ABSENCE)
        assertTrue(Lines.welcomeBack(60_000L, 9) in Lines.RETURNS)
    }

    @Test fun callbacksMentionRecentEvents() {
        Lines.note(Lines.Event.SLIPPED)
        val hits = (0 until 400).map { Lines.idle() }.count { it in Lines.Event.SLIPPED.lines }
        assertTrue("callback lines: $hits", hits > 10)
    }

    @Test fun promptHasComedyAndGagTags() {
        val full = Persona.systemPrompt(false)
        assertTrue(full.contains("COMEDY STYLE")); assertTrue(full.contains("[juggle]")); assertTrue(full.contains("never your human"))
        val compact = Persona.systemPrompt(false, compact = true)
        assertTrue(compact.contains("[airguitar]")); assertTrue(compact.length < full.length)
    }
}
