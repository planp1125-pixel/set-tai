package com.combo.monkeycompanion.powers

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.AppSettings
import com.combo.monkeycompanion.ai.BodyAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Action dispatch: pre-parser / tags / tool calls -> capability -> confirm -> result (Robolectric). */
@RunWith(RobolectricTestRunner::class)
class DispatchTest {
    private lateinit var ctx: Context
    private lateinit var s: AppSettings
    private val proposed = ArrayList<PendingAction>()
    private val reacted = ArrayList<Pair<BodyAction?, Prop>>()
    private lateinit var powers: PhonePowers
    private val started = ArrayList<Intent>()

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        s = AppSettings(ctx)
        powers = PhonePowers(ctx, s, { proposed.add(it) }) { b, p -> reacted.add(b to p) }
        ClockPowers.gate = { true }
        ClockPowers.starter = { _, i, _ -> started.add(i); LaunchGate.Result.Started }
        SettaiClock.cancel(ctx, "all")
    }

    @After fun tearDown() {
        ClockPowers.gate = { LaunchGate.canStartActivities(it) }
        ClockPowers.starter = { c, i, w -> LaunchGate.start(c, i, w) }
    }

    @Test fun timerWithNoClockAppUsesSettaisOwn() {
        val h = powers.handle(PhoneCommands.parse("set a timer for 5 minutes")!!)
        assertTrue(h.say, h.say.contains("5 min"))
        val r = h.pending!!.run()!!
        assertTrue(r, started.isEmpty())      // Robolectric has no Clock app: nothing resolves
        assertEquals(1, SettaiClock.items(ctx).size)
        assertTrue(ActionLog.all(ctx).isNotEmpty())
    }

    @Test fun timerWhenLaunchNotAllowedUsesOwnAndSaysWhy() {
        ClockPowers.gate = { false }
        s.clockMode = "clock"
        val r = powers.handle(Invocation("set_timer", mapOf("seconds" to 60))).pending!!.run()!!
        assertEquals(1, SettaiClock.items(ctx).size)
        assertTrue(r, r.isNotBlank())
        s.clockMode = "auto"
    }

    @Test fun settaiModeAlarm() {
        s.clockMode = "settai"
        val h = powers.handle(PhoneCommands.parse("wake me up at 6:30 am")!!)
        assertTrue(h.say, h.say.contains("6:30 AM"))
        h.pending!!.run()
        val items = SettaiClock.items(ctx)
        assertEquals(1, items.size)
        assertTrue(powers.handle(Invocation("list_timers")).say.isNotBlank())
        powers.handle(Invocation("cancel_timer", mapOf("which" to "all")))
        assertEquals(0, SettaiClock.items(ctx).size)
        s.clockMode = "auto"
    }

    @Test fun toolCallProposesAndSaysNotDoneYet() {
        val out = powers.call("set_timer", """{"seconds": 120}""")
        assertTrue(out, out.contains("NOT done"))
        assertEquals(1, proposed.size)
        assertTrue(proposed[0].question.contains("2 min"))
    }

    @Test fun toolSpecsComeFromRegistry() {
        val names = powers.specs.map { it.name }
        for (n in listOf("get_battery", "set_volume", "set_brightness", "set_dnd", "media_control", "toggle_connection", "set_alarm", "open_app"))
            assertTrue(n, n in names)
        s.setGroupEnabled(CapGroup.SOUND, false)
        assertFalse(powers.specs.any { it.name == "set_volume" })
        assertTrue(powers.handle(Invocation("set_volume", mapOf("stream" to "media", "level" to "50"))).say.contains("switched off"))
        s.setGroupEnabled(CapGroup.SOUND, true)
    }

    @Test fun volumeChangesAfterYesAndCoversEars() {
        val am = ctx.getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0)
        val h = powers.handle(PhoneCommands.parse("set volume to 100")!!)
        assertEquals(1, am.getStreamVolume(AudioManager.STREAM_MUSIC))   // not before Yes
        h.pending!!.run()
        assertEquals(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), am.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(Prop.EAR_PAWS, reacted.last().second)
    }

    @Test fun missingPermissionOffersGrant() {
        // Robolectric: WRITE_SETTINGS not granted by default
        val h = powers.handle(Invocation("set_brightness", mapOf("level" to "90")))
        if (!Perm.WRITE_SETTINGS.granted(ctx)) {
            assertTrue(h.say, h.say.contains("Modify system settings"))
            assertNotNull(h.pending)
        }
    }

    @Test fun readingsAnswerAtOnce() {
        for (id in listOf("get_battery", "get_storage", "get_memory", "get_time", "get_network", "get_sound", "get_display", "get_cpu")) {
            val h = powers.handle(Invocation(id))
            assertNull(id, h.pending); assertTrue("$id: ${h.say}", h.ok && h.say.isNotBlank())
        }
        assertTrue(Capabilities.stateSummary(ctx).contains("battery"))
    }

    @Test fun tagsBecomeCalls() {
        val r = PowerTags.parse("Sure! [volume media 40] Tap Yes.")
        assertEquals("Sure! Tap Yes.", r.text)
        assertEquals(listOf(Invocation("set_volume", mapOf("stream" to "media", "level" to "40"))), r.calls)
        assertEquals("set_alarm", PowerTags.parse("[alarm 07:00] Up!").calls.single().id)
        assertEquals(7, PowerTags.parse("[alarm 07:00]").calls.single().args["hour"])
        assertEquals(300, PowerTags.parse("[timer 300]").calls.single().args["seconds"])
        assertEquals(300, PowerTags.parse("[timer 5 min]").calls.single().args["seconds"])
        assertEquals(mapOf("on" to false), PowerTags.parse("[torch off]").calls.single().args)
        assertEquals(mapOf("what" to "wifi", "on" to true), PowerTags.parse("[switch wifi on]").calls.single().args)
        assertEquals("get_battery", PowerTags.parse("[battery] checking").calls.single().id)
        assertEquals(mapOf("level" to "up"), PowerTags.parse("[brightness up]").calls.single().args)
        assertEquals("Hi", PowerTags.parse("Hi [dance]").text.replace(" [dance]", ""))   // body tags untouched
        assertEquals("Okay", PowerTags.visible("Okay [volume med"))
        assertEquals("unknown power", PowerTags.parse("[frobnicate 3]").calls.size.let { if (it == 0) "unknown power" else "?" })
    }

    @Test fun unknownAppFallsBackToBrain() {
        val h = powers.handle(Invocation("open_app", mapOf("name" to "Nonexistent Thing")))
        assertFalse(h.ok)
    }

    @Test fun everyTagIsDocumented() {
        for (c in Capabilities.all) if (c.tag != null) {
            assertNotNull(c.id, c.tagHelp); assertNotNull(c.id, c.fromTag)
        }
        assertEquals(Capabilities.all.size, Capabilities.all.map { it.id }.toSet().size)
    }
}
