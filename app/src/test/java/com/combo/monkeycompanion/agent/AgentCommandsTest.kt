package com.combo.monkeycompanion.agent

import com.combo.monkeycompanion.powers.PhoneCommands
import org.junit.Assert.assertEquals
import org.junit.Test

/** v4 additions to the English + Tanglish pre-parser. */
class AgentCommandsTest {
    private fun id(t: String) = PhoneCommands.parse(t)?.id

    @Test fun routinesBeatAlarms() {
        assertEquals("add_routine", id("every day at 9 pm remind me to drink water"))
        assertEquals("add_routine", id("when the charger is connected say hi"))
        assertEquals("set_alarm", id("set an alarm at 7 am"))
        assertEquals("list_routines", id("show my routines"))
        assertEquals("delete_routine", id("delete routine 2"))
    }

    @Test fun memory() {
        assertEquals("remember", id("remember that I love filter coffee"))
        assertEquals("remember", id("en peru Ravi"))
        assertEquals(true, PhoneCommands.parse("my name is Ravi")!!.args["_soft"])
        assertEquals(null, PhoneCommands.parse("remember that I love dosa")!!.args["_soft"])
        assertEquals("forget", id("forget that I like coffee"))
        assertEquals("list_memories", id("what do you remember about me?"))
    }

    @Test fun notifications() {
        assertEquals("read_notifications", id("what did I miss?"))
        assertEquals("read_notifications", id("enna miss panninen"))
        assertEquals("read_notifications", id("any new messages"))
        assertEquals("show_otp", id("show my OTP"))
        assertEquals("show_otp", id("otp enna"))
        assertEquals(null, id("send the otp to ravi"))
        assertEquals("reply_notification", id("reply to Ravi saying on my way"))
        assertEquals("on my way", PhoneCommands.parse("reply to Ravi saying on my way")!!.args["text"])
        assertEquals("dismiss_notification", id("clear all notifications"))
    }

    @Test fun screen() {
        assertEquals("read_screen", id("what's on my screen"))
        assertEquals("read_screen", id("screen la enna"))
        assertEquals("press_key", id("press back"))
        assertEquals("scroll", id("scroll down"))
    }

    @Test fun oldCommandsStillWork() {
        assertEquals("set_torch", id("torch on"))
        assertEquals("set_volume", id("volume 40"))
        assertEquals("open_app", id("open youtube"))
    }
}
