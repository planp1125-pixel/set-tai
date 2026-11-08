package com.combo.monkeycompanion.v10395

import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.script.ScriptPlayer
import com.combo.monkeycompanion.engine.script.ShowScript
import com.combo.monkeycompanion.engine.script.ShowWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v10.39.5 "✨ Describe a show": brains write short story lines; the app turns them into a show that plays. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShowWriterTest {
    private fun plays(script: String) { ScriptPlayer(ShowScript.parse(script)) }

    @Test fun cleanStoryLinesBecomeAPlayableShow() {
        val r = ShowWriter.compile("""
            title Tutty's prank
            cast t=tutty a=angel
            t enter left
            a enter right
            t say Boo! Did I scare you?
            a face shocked
            a shield
            t throw fireball at a
            a vanish
            t face smug
        """.trimIndent())
        assertTrue(r.problems.toString(), r.ok)
        val s = ShowScript.parse(r.script)
        assertEquals("Tutty's prank", s.title)
        assertEquals(mapOf("t" to "devil", "a" to "angel"), s.cast)
        plays(r.script)
    }

    @Test fun messySmallModelAnswerStillPlays() {
        // code fences, numbering, "Title:", a misspelt face, a stage clash: the kind of thing a small on-device model writes
        val r = ShowWriter.compile("```\nTitle: Cat and mouse\nCast: cat=billa, mouse=chittu\n1. cat walk middle\n2. mouse say Can't catch me!\n" +
            "3. cat face angri\n4. cat throw laddu at mouse\n5. clash cat mouse\n6. crack\n7. mouse exit right\n```")
        assertTrue(r.problems.toString(), r.ok)
        assertTrue(r.script.contains("cat face angry"))
        assertTrue(r.script.contains("stage clash cat mouse"))
        plays(r.script)
    }

    @Test fun cutOffCloudReplyStillPlays() {
        // the cloud cuts long replies with "…": the half line is skipped
        val r = ShowWriter.compile("title Race\ncast z=zoom b=bolt\nz enter left\nb enter right\nz fly right\nb say I'm faster th…")
        assertTrue(r.problems.toString(), r.ok); plays(r.script)
    }

    @Test fun unknownCharacterIsAProblemAndTheFallbackUsesNamedCharacters() {
        val bad = ShowWriter.compile("Sure! Here is your show:\ntitle Bad\ncast x=dragon")
        assertFalse(bad.ok)
        val fb = ShowWriter.fallback("Billa chases Chittu for the last laddu")
        val s = ShowScript.parse(fb); plays(fb)
        assertEquals(listOf("billa", "chittu"), s.cast.values.toList())
    }

    @Test fun fullScriptFromABigModelIsCheckedAsIs() {
        val r = ShowWriter.compile("title: Hi\ncast: a=monkey\nat 0: a enter left to 0.4 0.86 in 1.2\nat 1.4: a say \"Hello!\" for 2\nend at 4")
        assertTrue(r.problems.toString(), r.ok)
    }

    @Test fun rulesNameEveryCharacterAndStayShort() {
        val cast = CharacterRegistry.all.map { it.id to it.name }
        val rules = ShowWriter.rules(cast, 7)
        for ((id, _) in cast) assertTrue(id, rules.contains(id))
        assertTrue("fits the cloud's 4500-character system prompt", rules.length < 2500)
        assertEquals(listOf("devil", "angel"), ShowWriter.mentioned("Tutty pranks Angel"))
    }
}
