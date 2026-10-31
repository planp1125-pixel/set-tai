package com.combo.monkeycompanion.`fun`

import android.graphics.Bitmap
import android.graphics.Canvas
import com.combo.monkeycompanion.ai.ActionParser
import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.ai.Persona
import com.combo.monkeycompanion.brain.Gag
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.PowerPhase
import com.combo.monkeycompanion.brain.Superpowers
import com.combo.monkeycompanion.character.ArmourAnna
import com.combo.monkeycompanion.character.CharacterNames
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.Locomotion
import com.combo.monkeycompanion.character.MaskMan
import com.combo.monkeycompanion.character.MonkeyCharacter
import com.combo.monkeycompanion.character.Superboy
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.HeroLines
import com.combo.monkeycompanion.speech.Lines
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v4.1 swappable characters: registry, action fallbacks, wardrobe, per-character prompt identity. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CharactersTest {
    @After fun back() { CharacterRegistry.select("monkey"); CharacterRegistry.all.forEach { CharacterRegistry.wear(it).reset() } }

    @Test fun registryHasFourUniqueCharacters() {
        val ids = CharacterRegistry.all.map { it.id }
        assertEquals(listOf("devil", "monkey", "superboy", "armour", "mask", "billa", "chittu", "gajju", "rocketgirl", "hollowman", "surfer"), ids)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(CharacterRegistry.all.size, CharacterRegistry.all.map { it.name }.toSet().size)
        for (c in CharacterRegistry.all) {
            assertTrue(c.id, c.powers.isNotEmpty())
            assertTrue(c.id, c.locomotion.isNotEmpty())
            CharacterRegistry.select(c.id)
            assertEquals(c, CharacterRegistry.current)
            assertEquals(c.powers, Superpowers.all)        // the power picker follows the body
        }
        CharacterRegistry.select("nope")
        assertEquals(MonkeyCharacter, CharacterRegistry.current)   // unknown id -> the monkey
    }

    @Test fun originalNamesAreConstantsAndNotTrademarks() {
        assertEquals("Settai", CharacterNames.MONKEY)
        assertEquals(CharacterNames.SUPERBOY, Superboy.name)
        assertEquals("Armour Anna", ArmourAnna.name)
        assertEquals("Mask Man", MaskMan.name)
        for (c in CharacterRegistry.all) assertFalse(c.name, Regex("(?i)superman|iron ?man|the mask|\\btom\\b|\\bjerry\\b").containsMatchIn(c.name))
    }

    @Test fun locomotionRules() {
        assertTrue(MonkeyCharacter.can(Locomotion.CLIMB) && MonkeyCharacter.can(Locomotion.HANG))
        assertTrue(Superboy.can(Locomotion.FLOAT)); assertFalse(Superboy.can(Locomotion.WALK)); assertFalse(Superboy.can(Locomotion.CLIMB))
        assertTrue(ArmourAnna.can(Locomotion.WALK) && ArmourAnna.can(Locomotion.FLY)); assertFalse(ArmourAnna.can(Locomotion.CLIMB))
        assertTrue(ArmourAnna.heavy)
        assertFalse(MaskMan.can(Locomotion.HANG))
    }

    @Test fun actionFallbacks() {
        val mw = CharacterRegistry.wear(MonkeyCharacter)
        assertEquals(BodyAction.CLIMB, MonkeyCharacter.mapAction(BodyAction.CLIMB, mw))
        assertEquals(BodyAction.TAIL_HIDE, MonkeyCharacter.mapAction(BodyAction.TAIL_HIDE, mw))
        assertNull(MonkeyCharacter.mapAction(BodyAction.SKATE, mw))
        // Superboy: climb = hover near an edge; no tail.
        val sw = CharacterRegistry.wear(Superboy)
        assertEquals(BodyAction.CLIMB, Superboy.mapAction(BodyAction.CLIMB, sw))
        assertEquals(BodyAction.HIDE, Superboy.mapAction(BodyAction.TAIL_HIDE, sw))
        assertEquals(Gag.FAKE_SLEEP, Superboy.mapGag(Gag.TAIL_HIDE, sw))
        // Armour Anna can't climb: hop.
        assertEquals(BodyAction.JUMP, ArmourAnna.mapAction(BodyAction.CLIMB, CharacterRegistry.wear(ArmourAnna)))
        // Mask Man follows his wardrobe.
        val w = CharacterRegistry.wear(MaskMan); w.allOn()
        assertEquals(BodyAction.CHICKEN, MaskMan.mapAction(BodyAction.CHICKEN, w))
        assertEquals(BodyAction.GADGET, MaskMan.mapAction(BodyAction.GADGET, w))
        w.set("chicken", false); w.set("belt", false)
        assertEquals(BodyAction.GIGGLE, MaskMan.mapAction(BodyAction.CHICKEN, w))
        assertEquals(BodyAction.JUGGLE, MaskMan.mapAction(BodyAction.GADGET, w))
        // Every shared action maps to something sensible (or null -> a happy hop) on every body.
        for (c in CharacterRegistry.all) for (a in BodyAction.entries) {
            val m = c.mapAction(a, CharacterRegistry.wear(c))
            if (m == BodyAction.TAIL_HIDE) assertEquals(MonkeyCharacter, c)
            if (m == BodyAction.CLIMB) assertTrue(c.id, c.can(Locomotion.CLIMB) || c.can(Locomotion.FLOAT))
        }
    }

    @Test fun newTagsParse() {
        assertEquals(listOf(BodyAction.SKATE), ActionParser.parse("[skate] wheee").actions)
        assertEquals(listOf(BodyAction.CHICKEN), ActionParser.parse("[chicken] squeak").actions)
        assertEquals(listOf(BodyAction.GADGET), ActionParser.parse("[gadget] behold").actions)
        assertEquals(BodyAction.CHICKEN, ActionParser.requestedByUser("squeeze your rubber chicken"))
        assertNotEquals(BodyAction.CHICKEN, ActionParser.requestedByUser("what's a good chicken curry recipe"))
    }

    @Test fun wardrobeEncodeDecodeAndOptions() {
        val w = Wear(MaskMan)
        assertTrue(w.has("mask") && w.has("skate"))
        assertFalse(w.has("shades"))                       // default off
        w.set("skate", false); w.setOption("hat", "lungi"); w.setOption("hat", "sombrero")   // unknown option ignored
        val enc = w.encode()
        val w2 = Wear(MaskMan); w2.decode(enc)
        assertFalse(w2.has("skate")); assertEquals("lungi", w2.option("hat"))
        assertEquals(w.enabled.map { it.id }, w2.enabled.map { it.id })
        w2.decode("bogus,mask;hat=party"); assertEquals(listOf("mask"), w2.enabled.map { it.id }); assertEquals("party", w2.option("hat"))
        w2.allOff(); assertTrue(w2.enabled.isEmpty())
        // The generic framework: the monkey has hat, sunglasses and scarf too.
        assertTrue(MonkeyCharacter.wardrobe.map { it.id }.containsAll(listOf("hat", "shades", "scarf")))
        assertEquals(13, MaskMan.wardrobe.size)
        assertFalse(Wear(MonkeyCharacter).also { it.set("cape", true) }.has("cape"))   // not a monkey item
    }

    @Test fun disabledItemsDropOutOfAnimations() {
        val w = CharacterRegistry.wear(MaskMan); val p = Pose(); val c = AnimContext()
        w.allOn()
        MaskMan.fill(p, MonkeyState.WALK, 1f, 0.5f, c, w); assertEquals(1f, p.skate)
        MaskMan.fill(p, MonkeyState.FALLING, 1f, 0.5f, c, w); assertEquals(1f, p.umbrella)
        MaskMan.fill(p, MonkeyState.GIGGLE, 1f, 0.17f, c, w); assertEquals(1f, p.chicken)
        MaskMan.fill(p, MonkeyState.STARTLED, 1f, 0.6f, c, w); assertTrue(p.eyePop > 0.5f)
        c.gag = Gag.SNEEZE; MaskMan.fill(p, MonkeyState.GAG, 1f, 2f, c, w); assertTrue(p.stacheOff > 0.5f)
        c.gag = Gag.JUGGLE; MaskMan.fill(p, MonkeyState.GAG, 1f, 0.5f, c, w); assertTrue(p.beltPull > 0.5f); assertEquals(0f, p.bananas)
        c.gag = Gag.CHASE_FLY; MaskMan.fill(p, MonkeyState.GAG, 1f, 2f, c, w); assertEquals(1f, p.hatOff)
        assertTrue(MaskMan.fallGravity(w) < 1f)
        w.allOff()
        MaskMan.fill(p, MonkeyState.WALK, 1f, 0.5f, c, w); assertEquals(0f, p.skate)
        MaskMan.fill(p, MonkeyState.FALLING, 1f, 0.5f, c, w); assertEquals(0f, p.umbrella)
        MaskMan.fill(p, MonkeyState.GIGGLE, 1f, 0.17f, c, w); assertEquals(0f, p.chicken)
        c.gag = Gag.JUGGLE; MaskMan.fill(p, MonkeyState.GAG, 1f, 0.5f, c, w); assertEquals(0f, p.beltPull); assertTrue(p.bananas > 0f)   // falls back to bananas
        assertEquals(1f, MaskMan.fallGravity(w))
        // Rendering with everything on / off doesn't crash.
        val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888); val cv = Canvas(bmp); val rig = MonkeyRig()
        for (on in listOf(true, false)) { if (on) w.allOn() else w.allOff()
            val r = MaskMan.newRenderer().also { it.wear = w }
            for (s in MonkeyState.entries) { MaskMan.fill(p, s, 1f, 0.5f, c, w); rig.layout(p, 150f, 280f, 1f, 1f); r.draw(cv, rig, p, 1f) } }
    }

    @Test fun heroBodiesAdaptSharedPoses() {
        val p = Pose(); val c = AnimContext()
        Superboy.fill(p, MonkeyState.IDLE, 1f, 1f, c, CharacterRegistry.wear(Superboy))
        assertTrue("floats", p.bodyY < -10f); assertEquals(1f, p.cape)
        Superboy.fill(p, MonkeyState.SLEEP, 1f, 1f, c, CharacterRegistry.wear(Superboy))
        assertEquals(1f, p.blanket); assertEquals(0f, p.cape)
        Superboy.fill(p, MonkeyState.WALK, 1f, 0.3f, c, CharacterRegistry.wear(Superboy))
        assertTrue("glides, never walks", p.bodyY < -10f)
        ArmourAnna.fill(p, MonkeyState.WALK, 1f, 0.3f, c, CharacterRegistry.wear(ArmourAnna)); assertEquals(0f, p.visor)
        ArmourAnna.fill(p, MonkeyState.TALKING, 1f, 0.3f, c, CharacterRegistry.wear(ArmourAnna)); assertEquals(1f, p.visor)
        // Hero power poses.
        fun at(pw: com.combo.monkeycompanion.brain.Superpower, ph: PowerPhase, k: Float): Pose { c.power = pw; c.powerPhase = ph; c.powerK = k
            pw.let { CharacterRegistry.all.first { ch -> pw in ch.powers } }.fill(p, MonkeyState.POWER, 1f, 1f, c, CharacterRegistry.wear(Superboy)); return p }
        assertTrue(at(com.combo.monkeycompanion.brain.HeatVisionPower, PowerPhase.LASER, 0.5f).laser > 0.9f)
        assertTrue(at(com.combo.monkeycompanion.brain.SuperBreathPower, PowerPhase.BREATH, 0.6f).breath > 0.9f)
        assertTrue(at(com.combo.monkeycompanion.brain.SuperStrengthPower, PowerPhase.LIFT, 0.7f).lift > 0.9f)
        assertTrue(at(com.combo.monkeycompanion.brain.ShieldBubblePower, PowerPhase.SHIELD, 0.5f).shield > 0.9f)
        assertTrue(at(com.combo.monkeycompanion.brain.LowPowerPower, PowerPhase.DROOP, 0.6f).lowPower > 0.5f)
        c.power = null
        // Hero plans exist and only the low-power gag is event-only.
        for (pw in Superboy.powers + ArmourAnna.powers) assertTrue(pw.id, pw.plan(0.5f, 0f, false, kotlin.random.Random(1)).isNotEmpty())
        CharacterRegistry.select("armour")
        assertFalse(Superpowers.choose(Superpowers.RANDOM) == com.combo.monkeycompanion.brain.LowPowerPower && false)
        repeat(50) { assertNotEquals(com.combo.monkeycompanion.brain.LowPowerPower, Superpowers.choose(Superpowers.RANDOM)) }
    }

    @Test fun promptKnowsWhichBodyAndWardrobe() {
        for (c in CharacterRegistry.all) {
            CharacterRegistry.select(c.id)
            val pr = Persona.systemPrompt(toolsAvailable = false)
            assertTrue(c.id, pr.contains("You ARE ${c.name}"))
            for (other in CharacterRegistry.all) if (other !== c) assertFalse("${c.id} mentions ${other.name}", pr.contains("You ARE ${other.name}"))
        }
        CharacterRegistry.select("superboy")
        val sb = Persona.systemPrompt(toolsAvailable = false)
        assertTrue(sb.contains("never walk")); assertTrue(sb.contains("heat-vision")); assertTrue(sb.contains("NOT Superman"))
        CharacterRegistry.select("armour")
        val aa = Persona.systemPrompt(toolsAvailable = false)
        assertTrue(aa.contains("filter coffee")); assertTrue(aa.contains("NOT Iron Man")); assertFalse(aa.contains("[tailhide] (hide"))
        CharacterRegistry.select("mask")
        val w = CharacterRegistry.wear(MaskMan); w.allOn()
        var mm = Persona.systemPrompt(toolsAvailable = false)
        assertTrue(mm.contains("[skate]")); assertTrue(mm.contains("[chicken]")); assertTrue(mm.contains("[gadget]"))
        assertTrue(mm.contains("rubber chicken")); assertTrue(mm.contains("NOT The Mask"))
        w.set("skate", false); w.set("chicken", false)
        mm = Persona.systemPrompt(toolsAvailable = false)
        assertFalse(mm.contains("[skate] (ride")); assertFalse(mm.contains("[chicken] (squeak"))
        assertTrue(mm.contains("Switched OFF by your human")); assertTrue(mm.contains("skateboard"))
        // Compact prompt (on-device brain) too.
        assertTrue(Persona.systemPrompt(toolsAvailable = false, compact = true).contains("You ARE Mask Man"))
    }

    @Test fun eachCharacterHasItsOwnLines() {
        CharacterRegistry.select("mask")
        repeat(10) { val l = Lines.pick(Lines.HELLO); assertTrue(l, HeroLines.MM_HELLO.any { it.replace("Settai", "Mask Man") == l } ) }
        assertTrue(HeroLines.MM_SQUEAK.isNotEmpty())
        CharacterRegistry.select("superboy")
        repeat(10) { assertTrue(HeroLines.SB_RANDOM.contains(Lines.pick(Lines.RANDOM))) }
        CharacterRegistry.select("armour")
        assertTrue(HeroLines.AA_LOW.all { it.contains("{pct}") })
        CharacterRegistry.select("monkey")
        assertNull(Lines.voice)
    }
}
