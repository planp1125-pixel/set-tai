package com.combo.monkeycompanion.powers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The deterministic pre-parser: phrase -> capability call (before any brain). */
class PhoneCommandsTest {
    private fun p(s: String) = PhoneCommands.parse(s)
    private fun id(s: String) = p(s)?.id

    @Test fun clockRoutes() {
        assertEquals("set_timer", id("set a timer for 5 minutes"))
        assertEquals(300, p("timer 5 min")!!.args["seconds"])
        assertEquals("set_alarm", id("wake me up at 6:30"))
        assertEquals("list_timers", id("show my timers"))
        assertEquals("cancel_timer", id("cancel the alarm"))
    }

    @Test fun torch() {
        for (s in listOf("torch on", "turn on the flashlight", "flashlight", "torch podu", "switch on torch please")) assertEquals(s, true, p(s)?.args?.get("on"))
        for (s in listOf("torch off", "turn off the flashlight", "torch anachidu")) assertEquals(s, false, p(s)?.args?.get("on"))
        assertNull(p("why does my torch get hot"))
    }

    @Test fun volume() {
        fun v(s: String) = p(s)!!.let { assertEquals(s, "set_volume", it.id); it.args["stream"] to it.args["level"] }
        assertEquals("media" to "up", v("volume up"))
        assertEquals("media" to "down", v("turn the volume down"))
        assertEquals("media" to "40", v("set volume to 40%"))
        assertEquals("media" to "max", v("max volume"))
        assertEquals("ring" to "60", v("set ringtone volume to 60"))
        assertEquals("alarm" to "up", v("increase alarm volume"))
        assertEquals("media" to "mute", v("mute the volume"))
        assertEquals("media" to "up", v("louder"))
        assertEquals("media" to "down", v("sound kammi pannu"))
    }

    @Test fun ringerAndDnd() {
        assertEquals(Invocation("set_ringer", mapOf("mode" to "silent")), p("put my phone on silent"))
        assertEquals(Invocation("set_ringer", mapOf("mode" to "vibrate")), p("vibrate mode"))
        assertEquals(Invocation("set_ringer", mapOf("mode" to "normal")), p("turn off silent mode"))
        assertEquals(Invocation("set_dnd", mapOf("on" to true)), p("turn on do not disturb"))
        assertEquals(Invocation("set_dnd", mapOf("on" to false)), p("dnd off"))
    }

    @Test fun display() {
        assertEquals(Invocation("set_brightness", mapOf("level" to "up")), p("make the screen brighter"))
        assertEquals(Invocation("set_brightness", mapOf("level" to "down")), p("dim the screen"))
        assertEquals(Invocation("set_brightness", mapOf("level" to "80")), p("brightness 80"))
        assertEquals(Invocation("set_brightness", mapOf("level" to "max")), p("full brightness"))
        assertEquals(Invocation("set_brightness", mapOf("level" to "auto")), p("turn on auto brightness"))
        assertEquals(Invocation("set_screen_timeout", mapOf("seconds" to 120)), p("set screen timeout to 2 minutes"))
        assertEquals(Invocation("set_auto_rotate", mapOf("on" to true)), p("turn on auto rotate"))
        assertEquals(Invocation("set_auto_rotate", mapOf("on" to false)), p("auto-rotate off"))
    }

    @Test fun media() {
        assertEquals("next", p("next song")!!.args["action"])
        assertEquals("next", p("skip this track")!!.args["action"])
        assertEquals("pause", p("pause the music")!!.args["action"])
        assertEquals("play", p("play music")!!.args["action"])
        assertEquals("previous", p("previous song")!!.args["action"])
        assertEquals("get_now_playing", id("what song is playing?"))
    }

    @Test fun switches() {
        assertEquals(Invocation("toggle_connection", mapOf("what" to "wifi", "on" to true)), p("turn on wifi"))
        assertEquals(Invocation("toggle_connection", mapOf("what" to "bluetooth", "on" to false)), p("switch off bluetooth"))
        assertEquals(Invocation("toggle_connection", mapOf("what" to "mobile_data", "on" to true)), p("turn on mobile data"))
        assertEquals(Invocation("toggle_connection", mapOf("what" to "airplane", "on" to true)), p("airplane mode on"))
        assertEquals(Invocation("toggle_connection", mapOf("what" to "location", "on" to false)), p("turn off location"))
        assertEquals(Invocation("toggle_connection", mapOf("what" to "hotspot", "on" to true)), p("turn on hotspot"))
    }

    @Test fun appsAndComms() {
        assertEquals(Invocation("open_app", mapOf("name" to "YouTube")), p("open YouTube"))
        assertEquals(Invocation("open_app", mapOf("name" to "whatsapp")), p("whatsapp open pannu"))
        assertEquals("open_camera", id("open the camera"))
        assertEquals("video", p("record a video")!!.args["mode"])
        assertEquals(Invocation("open_settings", mapOf("page" to "wifi")), p("open wifi settings"))
        assertEquals(Invocation("open_settings", mapOf("page" to "battery")), p("open battery settings"))
        assertEquals(Invocation("open_settings", mapOf("page" to "settings")), p("open settings"))
        assertEquals(Invocation("dial", mapOf("number" to "9876543210")), p("call 98765 43210"))
        assertEquals("compose_sms", id("text 9876543210 saying on my way"))
        assertEquals("on my way", p("text 9876543210 saying on my way")!!.args["text"])
        assertEquals(Invocation("web_search", mapOf("query" to "best biryani in Chennai")), p("search for best biryani in Chennai"))
        assertEquals(Invocation("web_search", mapOf("query" to "kotlin coroutines")), p("google kotlin coroutines"))
    }

    @Test fun queries() {
        val cases = mapOf(
            "how much battery do I have?" to "get_battery", "battery evlo" to "get_battery", "is my phone hot? temperature" to "get_battery",
            "how much storage is left" to "get_storage", "free ram?" to "get_memory", "what time is it" to "get_time",
            "what's the date today" to "get_time", "am I connected to wifi?" to "get_network", "is bluetooth on?" to "get_network",
            "what's my volume" to "get_sound", "what's the brightness" to "get_display", "what processor do I have" to "get_cpu",
        )
        for ((s, want) in cases) assertEquals(s, want, id(s))
    }

    @Test fun leavesChatToTheBrain() {
        for (s in listOf("hello settai", "dance for me", "tell me a joke", "I'm so tired today", "open your heart", "open the door",
                "how do I turn on wifi calling on other phones", "why is the sky blue", "play a game with me", "what is bluetooth", "start a conversation"))
            assertNull(s, p(s))
    }
}
