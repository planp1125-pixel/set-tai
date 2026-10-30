package com.combo.monkeycompanion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSafetyTest {
    private val own = "com.combo.monkeycompanion"
    private fun snap(pkg: String, vararg n: UiNode, locked: Boolean = false) = ScreenSnapshot(pkg, pkg.substringAfterLast('.'), n.toList(), locked)
    private fun blocked(v: AgentSafety.Verdict) = v is AgentSafety.Verdict.Blocked

    @Test fun lockScreenIsBlocked() {
        assertTrue(blocked(AgentSafety.screen(snap("com.whatsapp", locked = true), own)))
        assertTrue(blocked(AgentSafety.screen(snap("com.android.systemui", UiNode(1, "EditText", password = true, editable = true)), own)))
    }

    @Test fun bankingAndPaymentAppsAreBlocked() {
        for (p in listOf("com.google.android.apps.nbu.paisa.user", "net.one97.paytm", "com.phonepe.app", "com.sbi.lotusintouch", "in.org.npci.upiapp",
            "com.someone.mybankapp", "in.example.upi.pay", "com.example.wallet"))
            assertTrue(p, blocked(AgentSafety.screen(snap(p), own)))
        for (p in listOf("com.whatsapp", "com.google.android.youtube", "com.android.settings", "com.instagram.android"))
            assertFalse(p, blocked(AgentSafety.screen(snap(p), own)))
    }

    @Test fun userListsWin() {
        assertTrue(blocked(AgentSafety.screen(snap("com.whatsapp"), own, userBlocked = listOf("com.whatsapp"))))
        assertFalse(blocked(AgentSafety.screen(snap("com.example.wallet"), own, userAllowed = listOf("com.example.wallet"))))
    }

    @Test fun neverDrivesItself() = assertTrue(blocked(AgentSafety.screen(snap(own), own)))

    @Test fun passwordFieldsAreNeverTouched() {
        val pw = UiNode(2, "android.widget.EditText", editable = true, password = true)
        val user = UiNode(1, "android.widget.EditText", text = "Username", editable = true, focused = true)
        val s = snap("com.instagram.android", user, pw)
        assertTrue(blocked(AgentSafety.action(s, pw, typing = false)))
        assertTrue(blocked(AgentSafety.action(s, pw, typing = true)))
        // Any typing on a login screen with a password field visible is refused too.
        assertTrue(blocked(AgentSafety.action(s, user, typing = true)))
        // Tapping the (non-password) login button is fine.
        assertFalse(blocked(AgentSafety.action(s, UiNode(3, "Button", text = "Log in", clickable = true), typing = false)))
    }

    @Test fun riskyButtonsAlwaysAsk() {
        for (w in listOf("Send", "Pay ₹200", "Delete chat", "Post", "Buy now", "Place order", "Uninstall", "Transfer")) assertTrue(w, AgentSafety.risky(w))
        for (w in listOf("Chats", "Search", "Amma", "Settings", "Next song")) assertFalse(w, AgentSafety.risky(w))
    }
}

class ScreenTextTest {
    private val s = ScreenSnapshot("com.whatsapp", "WhatsApp", listOf(
        UiNode(1, "android.widget.TextView", text = "Chats"),
        UiNode(2, "android.widget.ImageButton", desc = "Search", clickable = true),
        UiNode(3, "android.view.ViewGroup", text = "Amma", clickable = true),
        UiNode(4, "android.widget.EditText", text = "", desc = "Message", editable = true, focused = true),
        UiNode(5, "android.widget.EditText", text = "hunter2", editable = true, password = true),
        UiNode(6, "android.widget.Button", text = "Send", clickable = true),
        UiNode(7, "android.widget.TextView", text = "Chats"),
    ))

    @Test fun compactIsNumberedAndHidesPasswords() {
        val c = ScreenText.compact(s)
        assertTrue(c, c.startsWith("App: WhatsApp (com.whatsapp)"))
        assertTrue(c, c.contains("[2] Button \"Search\""))
        assertTrue(c, c.contains("[4] TextField \"Message\" (focused)"))
        assertTrue(c, c.contains("[5] PasswordField (hidden: password)"))
        assertFalse(c, c.contains("hunter2"))
        assertTrue(c, c.contains("[6] Button \"Send\""))
        assertFalse("repeated static text is dropped", c.contains("[7]"))
    }

    @Test fun compactIsCapped() {
        val big = ScreenSnapshot("x", "X", (1..500).map { UiNode(it, "TextView", text = "line number $it with some words") })
        assertTrue(ScreenText.compact(big, 1000).length < 1100)
    }

    @Test fun findsByIndexAndWords() {
        assertEquals(6, ScreenText.find(s, "[6]")?.index)
        assertEquals(6, ScreenText.find(s, "6")?.index)
        assertEquals(6, ScreenText.find(s, "send")?.index)
        assertEquals(3, ScreenText.find(s, "“Amma”")?.index)
        assertEquals(2, ScreenText.find(s, "search")?.index)
        assertEquals(4, ScreenText.find(s, "message", wantEditable = true)?.index)
        assertNull(ScreenText.find(s, "Pay"))
    }
}

class OtpTest {
    @Test fun findsOtps() {
        assertEquals("483920", Otp.find("483920 is your OTP for login. Do not share it with anyone."))
        assertEquals("1234", Otp.find("Your verification code is 1234"))
        assertEquals("123456", Otp.find("G-123 456 is your Google verification code"))
        assertNotNull(Otp.find("Use code 99812 to sign in. Your login code expires in 10 min"))
    }

    @Test fun ignoresNormalMessages() {
        assertNull(Otp.find("Paid ₹2500 to Ravi on 12/10"))
        assertNull(Otp.find("Meeting at 1530 tomorrow"))
        assertNull(Otp.find("Happy birthday 2026!"))
    }

    @Test fun masksCodes() {
        val m = Otp.mask("Your OTP is 483920. Valid for 5 minutes.")
        assertFalse(m, m.contains("483920"))
        assertTrue(m, m.contains("[code hidden]"))
        assertEquals("Lunch at 1?", Otp.mask("Lunch at 1?"))
    }
}
