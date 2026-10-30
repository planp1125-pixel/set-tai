package com.combo.monkeycompanion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReportsTest {
    @Test fun reportsAreStoredLocallyMaskedAndDeduped() {
        val f = File.createTempFile("reports", ".json"); f.delete()
        val st = ReplyReportStore(f)
        assertNull(st.add("   ", "q", "GEMINI"))
        val r = st.add("Your code is 482913, go!", "what's my otp", "GEMINI")
        assertNotNull(r)
        assertFalse("codes never kept in reports", st.all()[0].reply.contains("482913"))
        st.add("Your code is 482913, go!", "again", "GEMINI")
        assertEquals(1, st.all().size)
        st.add("Bananas are blue.", "colour of bananas?", "OFFLINE")
        assertEquals(2, st.all().size)
        assertTrue(ReplyReportStore.shareText(st.all()[1]).contains("colour of bananas?"))
        st.remove(st.all()[0].id); assertEquals(1, st.all().size)
        st.clear(); assertEquals(0, st.all().size)
    }

    @Test fun hiddenOtpsOnAndroid15AreDetected() {
        val now = 10_000_000L
        val hidden = NotifItem("k", "com.google.android.apps.messaging", "Messages", "AX-HDFC", "Sensitive notification content hidden", now - 1000, false)
        val plain = NotifItem("k2", "com.whatsapp", "WhatsApp", "Ravi", "See you at 6", now - 1000, true)
        val visible = NotifItem("k3", "com.google.android.apps.messaging", "Messages", "AX-BANK", "Your OTP is 123456", now - 1000, false)
        assertTrue(NotifStore.otpHidden(listOf(hidden, plain), now))
        assertFalse(NotifStore.otpHidden(listOf(plain), now))
        assertFalse("a readable code isn't 'hidden'", NotifStore.otpHidden(listOf(visible), now))
        assertFalse("old ones don't count", NotifStore.otpHidden(listOf(hidden.copy(time = now - 3_600_000L)), now))
    }
}
