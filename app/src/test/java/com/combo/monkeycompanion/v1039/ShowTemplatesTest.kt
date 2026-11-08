package com.combo.monkeycompanion.v1039

import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.script.ScriptError
import com.combo.monkeycompanion.engine.script.ScriptPlayer
import com.combo.monkeycompanion.engine.script.ShowHelp
import com.combo.monkeycompanion.engine.script.ShowScript
import com.combo.monkeycompanion.engine.script.ShowTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v10.39.1: every fill-in show plays with every character, and script problems come out in plain words. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShowTemplatesTest {
    @Test fun everyTemplatePlaysWithEveryCharacter() {
        val ids = CharacterRegistry.all.map { it.id }
        for (t in ShowTemplates.all) {
            val charFields = t.fields.filter { it.kind == ShowTemplates.Kind.CHARACTER }
            for (id in ids) {
                val values = t.defaults().toMutableMap()
                charFields.forEachIndexed { k, f -> values[f.key] = if (k == 0) id else ids[(ids.indexOf(id) + k) % ids.size] }
                val sc = t.render(values)
                val parsed = ShowScript.parse(sc)
                ScriptPlayer(parsed)
                assertTrue("${t.id} with $id has warnings: ${ShowHelp.lint(sc).map { it.text }}", ShowHelp.lint(sc).isEmpty())
                assertTrue("${t.id} ends", parsed.end in 5f..20f)
            }
        }
    }

    @Test fun typedQuotesCannotBreakAShow() {
        val t = ShowTemplates.byId("hello")!!
        val sc = t.render(t.defaults() + ("line" to "He said \"hi\"; at 0: a vanish"))
        val p = ShowScript.parse(sc); ScriptPlayer(p)
        assertTrue(sc.contains("He said ’hi’"))
    }

    @Test fun friendlyMessages() {
        fun msg(s: String) = (runCatching { ScriptPlayer(ShowScript.parse(s)) }.exceptionOrNull() as ScriptError).let(ShowHelp::friendly)
        assertTrue(msg("cast: a=monkey\nat soon: a hover").startsWith("On line 2:"))
        assertTrue(msg("cast: a=monkey\nat 1: b hover").contains("isn't in the cast"))
        assertTrue(msg("cast: a=monkey\nat 1: a jump").contains("isn't a move I know"))
        val w = ShowHelp.lint("cast: a=monkey\nat 1: a face happyy; a say hi; a do dance")
        assertEquals(3, w.size)
    }
}
