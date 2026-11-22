package com.combo.monkeycompanion.v1052

import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.fun5.PairHub
import com.combo.monkeycompanion.v5.PairProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v10.52.1: every character can pair (Tutty used to be dropped), and a search never lists this phone itself. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TwoPhonesTest {
    @Test fun everyBuiltInCharacterCanSayHelloAndAccept() {
        for (c in CharacterRegistry.all) {
            val hello = PairProtocol.Msg.Hello("Moto-427", c.id, "abc123")
            assertEquals(c.id, hello, PairProtocol.decode(PairProtocol.encode(hello)))
            val ok = PairProtocol.Msg.Accept("Moto-918", c.id)
            assertEquals(c.id, ok, PairProtocol.decode(PairProtocol.encode(ok)))
        }
        assertNotNull(PairProtocol.decode("SETTAI1 HELLO Moto devil abc"))
        // the protocol's own list must cover every built-in character
        assertEquals(CharacterRegistry.all.size, CharacterRegistry.all.count { PairProtocol.decode("SETTAI1 ACCEPT x ${it.id}") != null })
        assertNull("still no made-up characters", PairProtocol.decode("SETTAI1 HELLO a hacker x"))
    }

    @Test fun aFullPairingWithTuttyWorks() {
        val inviter = PairProtocol.Session("Moto-427", "devil").also { it.invite("4821") }
        val joiner = PairProtocol.Session("Moto-918", "armour")
        val hello = PairProtocol.decode(PairProtocol.encode(joiner.join("4821"))) as PairProtocol.Msg.Hello
        assertTrue(inviter.checkHello(hello))
        joiner.onAnswer(PairProtocol.decode(PairProtocol.encode(inviter.accept()))!!)
        assertEquals(PairProtocol.Session.State.PAIRED, joiner.state)
        assertEquals("devil", joiner.peerCharacter)
    }

    @Test fun theSearchSkipsThisPhone() {
        assertTrue(PairHub.debugIsMine("motorola edge 70-427", "Settai-motorola_edge_70-427"))
        assertTrue("Android's renamed copy", PairHub.debugIsMine("motorola edge 70-427", "Settai-motorola_edge_70-427 (2)"))
        assertFalse(PairHub.debugIsMine("motorola edge 70-427", "Settai-motorola_edge_70-918"))
        PairHub.stop()
    }
}
