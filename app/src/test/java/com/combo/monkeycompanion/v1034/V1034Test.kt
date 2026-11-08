package com.combo.monkeycompanion.v1034

import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.engine.script.ScriptError
import com.combo.monkeycompanion.engine.script.ScriptPlayer
import com.combo.monkeycompanion.engine.script.ShowScript
import com.combo.monkeycompanion.overlay.ScriptShowHub
import com.combo.monkeycompanion.overlay.UserShows
import com.combo.monkeycompanion.ui.ShowsActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v10.34: user show scripts: store, list, play from text, line-numbered errors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class V1034Test {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val wave = """
        title: Wave hello
        cast: hero=monkey, pal=gajju
        at 0: hero enter left to 0.3 0.86 in 1.5
        at 0.5: pal enter right to 0.7 0.5 in 1.5
        at 2: hero face happy; hero do point for 1.5; hero say "Hello friend!" for 2
        at 3: pal look hero; pal say "Hi!" for 1.5
        at 5: both vanish
        end at 6
    """.trimIndent()

    @Before fun clean() { UserShows.dir(ctx).listFiles()?.forEach { it.delete() } }
    @After fun after() { ScriptShowHub.finish(ctx) }

    @Test fun waveHelloParsesAndPlaysFromText() {
        val p = ScriptPlayer(ShowScript.parse(wave))
        p.advanceTo(2.5f)
        assertEquals("Hello friend!", p.actor("hero")!!.saying(p.t))
        assertEquals("point", p.actor("hero")!!.actionAt(p.t))
        assertTrue(p.actor("pal")!!.mover.canFly)
        p.advanceTo(6.1f); assertTrue(p.done)
        assertNull(ScriptShowHub.startResult(ctx, text = wave))
        assertTrue(ScriptShowHub.running)
        assertEquals("Wave hello", ScriptShowHub.player!!.script.title)
    }

    @Test fun storeSavesListsOverwritesDeletes() {
        val e = UserShows.save(ctx, wave)
        assertEquals("wave-hello.show", e.file!!.name)
        assertTrue(e.file!!.parentFile!!.absolutePath.startsWith(ctx.filesDir.absolutePath))
        UserShows.save(ctx, wave.replace("Hello friend!", "Vanakkam!"))
        val users = UserShows.user(ctx)
        assertEquals(1, users.size); assertTrue(users[0].text(ctx).contains("Vanakkam!"))
        val all = UserShows.all(ctx)
        assertTrue("bundled Tutty vs Angel still there", all.any { it.bundled && it.title == "Tutty vs Angel" })
        assertTrue(all.any { !it.bundled && it.title == "Wave hello" })
        assertTrue(UserShows.delete(users[0])); assertTrue(UserShows.user(ctx).isEmpty())
        assertEquals("wave-hello", UserShows.slug("  Wave   hello!! "))
        assertEquals("https://raw.githubusercontent.com/a/b/main/x.show", UserShows.rawUrl("https://github.com/a/b/blob/main/x.show"))
    }

    @Test fun badScriptReportsLineNumberAndIsNotSaved() {
        val bad = "title: Oops\ncast: hero=monkey\nat 1: hero dance-wildly"
        val e = assertThrows(ScriptError::class.java) { UserShows.save(ctx, bad) }
        assertEquals(3, e.line)
        assertTrue(UserShows.user(ctx).isEmpty())
        val msg = ScriptShowHub.startResult(ctx, text = bad)!!
        assertTrue(msg, msg.contains("line 3")); assertFalse(ScriptShowHub.running)
        assertTrue(UserShows.check("cast: a=monkey\nat soon: a hover")!!.contains("line 2"))
        assertTrue(UserShows.check("cast: a=nobody\nat 1: a hover") != null)   // unknown character id
    }

    @Test fun showsScreenAddsByPasteAndListsBundled() {
        val a = Robolectric.buildActivity(ShowsActivity::class.java).setup().get()
        assertFalse(a.addText("cast: x=monkey\nat 1: x fly-away", playNow = false))
        assertTrue(a.lastMessage!!.contains("line 2"))
        assertTrue(a.addText(wave, playNow = false))
        assertEquals(1, UserShows.user(ctx).size)
        assertNotNull(ScriptShowHub.load(ctx))   // bundled Tutty vs Angel still loads
    }
}
