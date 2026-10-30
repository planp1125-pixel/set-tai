package com.combo.monkeycompanion.agent

import com.combo.monkeycompanion.powers.BirthdayMath
import com.combo.monkeycompanion.powers.PhoneCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebParseTest {
    @Test fun weatherLine() {
        val j = """{"current":{"temperature_2m":31.4,"apparent_temperature":36.2,"weather_code":2,"relative_humidity_2m":70},
            "daily":{"temperature_2m_max":[33.1],"temperature_2m_min":[26.0],"precipitation_probability_max":[40]}}"""
        assertEquals("Chennai: partly cloudy, 31°C (feels 36°C), humidity 70%. Today 26–33°C, rain chance 40%.", Web.parseWeather(j, "Chennai"))
        assertEquals("thunderstorm", Web.weatherText(95))
    }

    @Test fun ddg() {
        assertTrue(Web.parseDdg("""{"AbstractText":"Chennai is the capital of Tamil Nadu.","AbstractURL":"https://en.wikipedia.org/wiki/Chennai","Answer":""}""")!!.startsWith("Chennai is the capital"))
        assertNull(Web.parseDdg("""{"AbstractText":"","Answer":"","RelatedTopics":[]}"""))
        assertEquals("Hello world", Web.stripHtml("<html><script>x()</script><b>Hello</b>&nbsp;world</html>"))
    }
}

class MoreCommandsTest {
    private fun inv(t: String) = PhoneCommands.parse(t)

    @Test fun weather() {
        assertEquals("get_weather", inv("what's the weather")?.id)
        assertEquals("chennai", inv("weather in Chennai today")?.args?.get("place"))
        assertEquals("get_weather", inv("will it rain today?")?.id)
        assertEquals("get_weather", inv("mazhai varuma")?.id)
    }

    @Test fun dayAndContacts() {
        assertEquals("my_day", inv("what's my day")?.id)
        assertEquals("my_day", inv("inniku enna plan")?.id)
        assertEquals("upcoming_birthdays", inv("any birthdays coming up?")?.id)
        assertEquals("amma", inv("call Amma")?.args?.get("name"))
        assertEquals("call_contact", inv("amma ku call pannu")?.id)
        assertEquals("dial", inv("call 9876543210")?.id)
    }

    @Test fun doctorAndVoice() {
        assertEquals("phone_checkup", inv("why is my phone so slow?")?.id)
        assertEquals("phone_checkup", inv("which apps use the most battery")?.id)
        assertEquals("say_aloud", inv("say out loud: good morning")?.id)
    }

    @Test fun birthdayMath() {
        val today = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 22, 10, 0) }
        assertEquals(0, BirthdayMath.daysUntil(10, 22, today))
        assertEquals(1, BirthdayMath.daysUntil(10, 23, today))
        assertEquals(364, BirthdayMath.daysUntil(10, 21, today))
        assertEquals(71, BirthdayMath.daysUntil(1, 1, today))
    }

    @Test fun voiceClean() {
        assertEquals("Done! Sleeping now", Voice.clean("Done! 🐒 [sleep] Sleeping now 😴"))
    }
}
