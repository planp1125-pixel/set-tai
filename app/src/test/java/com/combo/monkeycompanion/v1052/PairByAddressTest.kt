package com.combo.monkeycompanion.v1052

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.fun5.PairHub
import com.combo.monkeycompanion.v5.PairProtocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** v10.54.1: two phones can pair by typing an address when Wi-Fi discovery finds nothing (here both ends are this computer). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PairByAddressTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    @After fun after() { PairHub.stop() }

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 8000
        while (!cond() && System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(30) }
        assertTrue("timed out waiting for $what", cond())
    }

    @Test fun theInvitingPhoneListensOnTheFixedPort() {
        var asked: Pair<String, String>? = null
        val l = object : PairHub.Listener { override fun onAsk(peerName: String, peerCharacter: String) { asked = peerName to peerCharacter } }
        PairHub.add(l)
        try {
            val code = PairProtocol.newCode()
            PairHub.host(ctx, "Moto-427", "devil", code)
            Thread.sleep(400)
            // the other phone, played by a plain socket: what "Join by address" sends
            val other = PairProtocol.Session("Moto-918", "armour")
            java.net.Socket().use { c ->
                c.connect(java.net.InetSocketAddress("127.0.0.1", PairHub.TCP_PORT), 3000)
                java.io.PrintWriter(c.getOutputStream(), true).println(PairProtocol.encode(other.join(code)))
                waitFor("the inviter to be asked") { asked != null }
            }
            assertEquals("armour", asked!!.second)
        } finally { PairHub.remove(l) }
    }

    @Test fun joinByAddressSendsHelloToThatAddress() {
        java.net.ServerSocket(0).use { probe -> probe.close() }
        val server = runCatching { java.net.ServerSocket(PairHub.TCP_PORT) }.getOrNull() ?: return   // port busy on this machine: skip
        server.use { ss ->
            ss.soTimeout = 5000
            PairHub.joinAddress("Moto-918", "armour", "127.0.0.1", "4821")
            ss.accept().use { c ->
                val line = java.io.BufferedReader(java.io.InputStreamReader(c.getInputStream())).readLine()
                val m = PairProtocol.decode(line)
                assertTrue("got: $line", m is PairProtocol.Msg.Hello && m.characterId == "armour")
            }
        }
    }

    @Test fun anUnreachableAddressSaysSo() {
        val statuses = ArrayList<String>()
        val l = object : PairHub.Listener { override fun onStatus(text: String) { statuses += text } }
        PairHub.add(l)
        try {
            PairHub.joinAddress("Moto-918", "armour", "127.0.0.1", "1234")
            waitFor("a status") { statuses.any { it.startsWith("Couldn't reach") } }
        } finally { PairHub.remove(l) }
    }

    @Test fun myAddressesNeverCrashes() { PairHub.myAddresses() }
}
