package com.combo.monkeycompanion.v1054

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.mind.RustMind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** v10.54 (R1b): with Give Brain off the offline brain answers in the bubble and moves the body; Play hides ChatGPT sign-in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineChatTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val replies = ArrayList<Pair<String, Boolean>>()
    private val actions = ArrayList<BodyAction>()
    private val l = object : ChatHub.Listener {
        override fun onReply(text: String, done: Boolean) { replies += text to done }
        override fun onAction(action: BodyAction) { actions += action }
    }

    @Before fun setUp() {
        ChatHub.init(ctx)
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.aiBrainEnabled = false
        ChatHub.addListener(l)
    }
    @After fun tearDown() { ChatHub.removeListener(l); ChatHub.debugRust = null }

    private fun answer(line: String, act: String) =
        RustMind.Answer(RustMind.Reading("greeting", "happy", act, "exclaim", false, 0.9f), line, 12)

    private fun idle() { repeat(5) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) } }

    @Test fun giveBrainOffTheOfflineBrainAnswersAndTheBodyWaves() {
        ChatHub.debugRust = { answer("Hee-hee! Tutty is here 😈", "wave") }
        ChatHub.send("qzx plimbo"); idle()
        assertEquals("Hee-hee! Tutty is here 😈", replies.last { it.second }.first)
        assertTrue("the act came with it: $actions", actions.any { it.name.equals("WAVE", true) || it.toString().contains("wave", true) })
    }

    @Test fun humanPaceSendsItThroughTheWordByWordPath() {
        ChatHub.settings.humanPace = true
        ChatHub.debugRust = { answer("Bored? Let's fix that!", "spin") }
        ChatHub.send("qzx wombat"); idle()
        assertTrue("a first, not-done call starts the pacer: $replies", replies.any { !it.second && it.first == "Bored? Let's fix that!" })
        assertTrue(replies.last().second)
    }

    @Test fun withoutTheLibraryNothingChanges() {
        ChatHub.debugRust = { null }
        ChatHub.send("hi"); idle()
        assertFalse("some reply still comes (the usual offline answers)", replies.none { it.second })
    }

    @Test fun libraryStaysOutOfRobolectric() {
        // a native library can live in one class loader only, so Robolectric tests never load it (unless asked)
        assertFalse(RustMind.available)
    }

    @Test fun chatGptSignInIsOfferedOutsideThePlayBuildOrToADeveloper() {
        // unit tests run the full flavour: always offered
        assertTrue(ChatHub.chatGptOffered())
        assertFalse("off until OpenAI approves", ChatHub.CHATGPT_APPROVED)
        ChatHub.settings.devChatGpt = true
        assertTrue(ChatHub.chatGptOffered())
    }
}
