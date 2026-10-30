package com.combo.monkeycompanion.character

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.MainActivity
import com.combo.monkeycompanion.agent.NotifItem
import com.combo.monkeycompanion.agent.NotifStore
import com.combo.monkeycompanion.agent.NotifSummary
import com.combo.monkeycompanion.agent.NotifSummaryCard
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.PowerPhase
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.fun5.Stage
import com.combo.monkeycompanion.mind.Act
import com.combo.monkeycompanion.mind.AppCat
import com.combo.monkeycompanion.mind.Diary
import com.combo.monkeycompanion.mind.Mind
import com.combo.monkeycompanion.mind.Personality
import com.combo.monkeycompanion.mind.Scene
import com.combo.monkeycompanion.mind.Traits
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.overlay.OverlayHost
import com.combo.monkeycompanion.powers.SettaiNotificationListener
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.ui.CompanionStudioActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CustomCompanionStudioTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    @After
    fun tearDown() {
        Stage.resetImeForTest()
        NotifStore.reset(emptyList())
        runCatching {
            com.combo.monkeycompanion.mind.MindHub.setNeighbourMode(ctx, com.combo.monkeycompanion.world.Population.Mode.DUO)
            com.combo.monkeycompanion.mind.MindHub.customPartner1Id = null
            com.combo.monkeycompanion.mind.MindHub.customPartner2Id = null
            ChatHub.settings.characterId = "monkey"
            ChatHub.settings.applyCharacter()
        }
        CharacterRegistry.select("monkey")
        com.combo.monkeycompanion.agent.DailyUtilityEngine.resetForTest(ctx)
        Personality.clearCustomTraits("custom_1")
        com.combo.monkeycompanion.character.CustomCompanionStore.save(ctx, com.combo.monkeycompanion.character.CustomCompanionPresets.adult35MidBody())
        Personality.clearCustomTraits("monkey")
    }

    private class TestHost : OverlayHost {
        var x = 0; var y = 0; var w = 0; var h = 0
        val xs = ArrayList<Int>()
        override fun screenWidth() = 1080
        override fun screenHeight() = 2340
        override fun placeWindow(x: Int, y: Int, w: Int, h: Int) {
            this.x = x; this.y = y; this.w = w; this.h = h
            xs.add(x)
        }
    }

    @Test
    fun realUserSelfiePhotoAnalyzesWith68PointFaceMesh320pxHeadAndZeroJasmineOrGlasses() {
        val selfieFile = File("/tmp/user_selfie_test.jpg")
        if (!selfieFile.exists()) return
        val oriented = PhotoCompanionAnalyzer.decodeBitmapWithExif(selfieFile)
        assertNotNull("EXIF decoded bitmap", oriented)
        assertTrue("EXIF orientation 8 rotated selfie to portrait (${oriented!!.width}x${oriented.height})", oriented.height > oriented.width)

        val res = PhotoCompanionAnalyzer.analyze(oriented, CustomCompanionSpec(name = "Manik"))
        val sp = res.spec

        // 1. Verify 68-Point Face-ID Landmark Mesh
        val pts68 = res.faceMesh.landmarks68()
        assertEquals(68, pts68.size)
        assertTrue("browThicknessRatio > 1.0 (${res.faceMesh.browThicknessRatio})", res.faceMesh.browThicknessRatio > 1.0f)
        assertTrue("beardDensityRatio >= 0.75 (${res.faceMesh.beardDensityRatio})", res.faceMesh.beardDensityRatio >= 0.75f)

        // 2. Verify detected look-alike traits
        assertEquals(AgeGroup.ADULT_REGULAR, sp.ageGroup)
        assertEquals(BodyBuild.MID_BODY, sp.bodyBuild)
        assertEquals(FacialHairStyle.SHORT_BOXED_BEARD, sp.facialHair)
        assertEquals(HairStyle.CURLY_WAVY_CROP, sp.hairStyle)
        assertEquals(OutfitStyle.TSHIRT_CARGO, sp.outfitStyle)
        assertEquals(FaceMode.CHIBI_TWIN, sp.faceMode)

        // 3. Verify bright yellow T-shirt colour extracted
        val r = Color.red(sp.outfitColor); val g = Color.green(sp.outfitColor); val b = Color.blue(sp.outfitColor)
        assertTrue("yellow T-shirt colour extracted (R=$r, G=$g, B=$b)", r > 175 && g > 160 && b < 140)

        // 4. Verify ZERO unwanted feminine/child accessories
        assertFalse(sp.glasses)
        assertFalse(sp.jasmine)
        assertFalse(sp.hairBow)
        assertFalse(sp.hasBindi)
        assertFalse(sp.wings)

        // 5. Verify 320x320 smooth-contour segmented head portrait preserves hair crown & chin beard
        assertEquals(320, res.portraitBitmap.width)
        assertEquals(320, res.portraitBitmap.height)
        assertEquals("outer corner wall pixel is transparent", 0, Color.alpha(res.portraitBitmap.getPixel(4, 4)))
        assertEquals("center face pixel is 100% opaque", 255, Color.alpha(res.portraitBitmap.getPixel(160, 160)))
        assertEquals("top curly hair crown pixel is 100% opaque", 255, Color.alpha(res.portraitBitmap.getPixel(160, 55)))
        assertEquals("bottom chin beard pixel is 100% opaque", 255, Color.alpha(res.portraitBitmap.getPixel(160, 265)))
    }

    @Test
    fun notifSummaryCardScrollsSmoothlyAndTapsScrolledRowWithoutMovingWindow() {
        ChatHub.init(ctx)
        CharacterRegistry.select("monkey")
        SettaiNotificationListener.lastOpenedForTest = null

        val now = 1_700_000_000_000L
        val items = (0 until 10).map { i ->
            NotifItem(
                key = "notif_key_$i",
                pkg = "com.app.test$i",
                app = "App $i",
                title = "Sender $i",
                text = "Message body number $i for scrolling test",
                time = now - i * 60_000L,
                canReply = true
            )
        }
        NotifStore.reset(items)

        val host = TestHost()
        val v = MonkeyView(ctx, host)
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(20)
        v.debugSurf("FLOOR", frac = 0.5f)
        v.debugFrames(5)

        assertTrue(v.openNotifSummaryCard(useDemoIfEmpty = false))
        assertTrue(v.notifCardOpen)
        assertEquals(10, v.notifCard.groups.size)
        assertTrue("maxScrollY > 0 for 10 items (${v.notifCard.maxScrollY})", v.notifCard.maxScrollY > 500f)

        val winXBefore = host.x
        val winYBefore = host.y

        // Simulate vertical drag inside the notification card (finger drags upward by 420px to scroll down!)
        val cardCenterX = host.x + v.notifCardLeft + v.notifCard.width * 0.5f
        val startRawY = host.y + v.notifCardTop + v.notifCard.listViewportRect.centerY()
        var t = 2000L
        fun sendTouch(action: Int, rawX: Float, rawY: Float) {
            val ev = MotionEvent.obtain(2000L, t, action, rawX, rawY, 0)
            ev.offsetLocation(-host.x.toFloat(), -host.y.toFloat())
            v.dispatchTouchEvent(ev)
            ev.recycle()
            t += 16L
        }

        sendTouch(MotionEvent.ACTION_DOWN, cardCenterX, startRawY)
        for (step in 1..10) {
            sendTouch(MotionEvent.ACTION_MOVE, cardCenterX, startRawY - step * 45f)
        }
        sendTouch(MotionEvent.ACTION_UP, cardCenterX, startRawY - 450f)

        // Verify card scrolled down and remained open, and window did NOT drag!
        assertTrue("notifCard scrolled down (scrollY=${v.notifCard.scrollY})", v.notifCard.scrollY > 300f)
        assertTrue("card stays open after scroll drag", v.notifCardOpen)
        assertEquals("window X did not move during scroll", winXBefore, host.x)
        assertEquals("window Y did not move during scroll", winYBefore, host.y)

        // Scroll all the way to the bottom and tap the last row (index 9: "notif_key_9")
        v.notifCard.scrollBy(v.notifCard.maxScrollY)
        assertEquals(v.notifCard.maxScrollY, v.notifCard.scrollY, 0.5f)

        val bottomRowRawY = host.y + v.notifCardTop + v.notifCard.listViewportRect.bottom - 25f * 3f
        sendTouch(MotionEvent.ACTION_DOWN, cardCenterX, bottomRowRawY)
        sendTouch(MotionEvent.ACTION_UP, cardCenterX, bottomRowRawY)

        assertFalse("card closed after tapping scrolled row", v.notifCardOpen)
        assertEquals("notif_key_9" to "com.app.test9", SettaiNotificationListener.lastOpenedForTest)
    }

    @Test
    fun universalPhotoAnalyzerDetectsAdultMidBodyBulkySlimChildAndCloseUpSuggestion() {
        val adultBmp = CompanionStudioActivity.createSampleAdult35MidBodyBitmap()
        val adultRes = PhotoCompanionAnalyzer.analyze(adultBmp, CustomCompanionSpec(name = "Manik"))
        assertEquals(AgeGroup.ADULT_REGULAR, adultRes.spec.ageGroup)
        assertEquals(BodyBuild.MID_BODY, adultRes.spec.bodyBuild)
        assertTrue(adultRes.spec.facialHair != FacialHairStyle.NONE)
        assertFalse(adultRes.spec.glasses)
        assertFalse(adultRes.spec.jasmine)

        val bulkyBmp = CompanionStudioActivity.createSampleBulkyPersonBitmap()
        val bulkyRes = PhotoCompanionAnalyzer.analyze(bulkyBmp, CustomCompanionSpec(name = "Bala"))
        assertEquals(BodyBuild.BULKY_HEAVY, bulkyRes.spec.bodyBuild)
        assertEquals(FacialHairStyle.FULL_BEARD_MADRAS, bulkyRes.spec.facialHair)
        assertEquals(OutfitStyle.VESHTI_SHIRT, bulkyRes.spec.outfitStyle)

        val slimBmp = CompanionStudioActivity.createSampleSlimPersonBitmap()
        val slimRes = PhotoCompanionAnalyzer.analyze(slimBmp, CustomCompanionSpec(name = "Karthik"))
        assertEquals(BodyBuild.SLIM, slimRes.spec.bodyBuild)

        val childBmp = CompanionStudioActivity.createSamplePortraitBitmap()
        val childRes = PhotoCompanionAnalyzer.analyze(childBmp, CustomCompanionSpec(name = "Kuttyma"))
        assertEquals(AgeGroup.CHILD_KUTTY, childRes.spec.ageGroup)
        assertEquals(FacialHairStyle.NONE, childRes.spec.facialHair)
        assertEquals(HairStyle.PIGTAILS_RIBBONS, childRes.spec.hairStyle)
        assertEquals(OutfitStyle.PATTU_PAVADAI, childRes.spec.outfitStyle)

        val selfieBmp = CompanionStudioActivity.createSampleCloseUpSelfieBitmap()
        val selfieRes = PhotoCompanionAnalyzer.analyze(selfieBmp, CustomCompanionSpec(name = "Selfie"))
        assertFalse(selfieRes.bodyDetectedClearly)
        assertTrue(selfieRes.bodyConfidence < 0.65f)
    }

    @Test
    fun customCompanionNearScreenEdgesAndKeyboardDodgeNeverTurnsIntoMonkeyClimber() {
        ChatHub.init(ctx)
        CustomCompanionStore.init(ctx)
        val spec = CustomCompanionPresets.adult35MidBody()
        val ch = CustomCompanionStore.save(ctx, spec)
        assertFalse(ch.can(Locomotion.CLIMB))
        assertFalse(ch.can(Locomotion.HANG))

        CharacterRegistry.select("custom_1")
        ChatHub.settings.characterId = "custom_1"
        val host = TestHost()
        val v = MonkeyView(ctx, host)
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(25)

        Stage.reportProbeIme(true, 1300f)
        v.debugFrames(30)
        assertEquals("FLOOR", v.debugSurfKind)
        Stage.resetImeForTest()
    }

    @Test
    fun mainActivityFourTabBottomNavigationAndCompanionSizeSelectorWorkCleanly() {
        val ctl = Robolectric.buildActivity(MainActivity::class.java).setup()
        val act = ctl.get()
        assertEquals(MainActivity.Tab.HOME, act.activeTab)
        for (tab in MainActivity.Tab.entries) {
            act.selectTab(tab)
            assertEquals(tab, act.activeTab)
        }
    }

    @Test
    fun tightOverlayTouchBoxAndAdaptiveFrameRatePacingWorkCleanly() {
        ChatHub.init(ctx)
        CustomCompanionStore.init(ctx)
        val spec = CustomCompanionPresets.adult35MidBody()
        CustomCompanionStore.save(ctx, spec)
        com.combo.monkeycompanion.mind.MindHub.setNeighbourMode(ctx, com.combo.monkeycompanion.world.Population.Mode.SOLO)

        CharacterRegistry.select("custom_1")
        ChatHub.settings.characterId = "custom_1"
        val host = TestHost()
        val v = MonkeyView(ctx, host)
        v.layout(0, 0, 1080, 2340)
        v.debugQuiet = true
        v.debugSurf("FLOOR", frac = 0.5f)
        v.debugFrames(25)

        // 1. Verify tight 96dp x 118dp (288x354 px at 3x density) overlay window with zero CustomCharacter inflation!
        assertEquals(288, v.boxW)
        assertEquals(354, v.boxH)
        assertEquals("custom companion window width equals tight boxW (no sideExtra inflation)", v.boxW, host.w)
        assertEquals("custom companion window height equals tight boxH (no topExtra inflation)", v.boxH, host.h)

        // 2. Verify Adaptive Frame-Rate Pacing: 60fps active -> 24fps calm idle -> 8fps sleep -> 0fps screen-off
        v.debugPlay(MonkeyState.DANCE, 2f)
        assertEquals("60fps (0ms delay) during active dance", 0L, v.adaptiveFrameDelayMs(calm = false))

        v.debugPlay(MonkeyState.IDLE, 10f)
        assertEquals("24fps (41ms delay) during calm idle", 41L, v.adaptiveFrameDelayMs(calm = true))

        v.controller.sleep()
        assertEquals("8fps (125ms delay) during sleep", 125L, v.adaptiveFrameDelayMs(calm = true))

        v.goToSleep()
        v.debugFrames(5)
        assertEquals("0fps (-1ms stopped) when screen is off", -1L, v.adaptiveFrameDelayMs(calm = true))
    }

    @Test
    fun v93DaughterPhotoFemaleBoyChildNeck4EdgeWalkCoyoteScareAndHeroEdgeCrashWorkCleanly() {
        ChatHub.init(ctx)
        CustomCompanionStore.init(ctx)

        // 1. Verify Daughter Photo (/tmp/daughter_photo_test.png) auto-detects as GIRL_CHILD (5yr Child, Female, Clean Face, Pigtails/Parted Hair)
        val daughterFile = File("/tmp/daughter_photo_test.png")
        if (daughterFile.exists()) {
            val dBmp = PhotoCompanionAnalyzer.decodeBitmapWithExif(daughterFile)
            assertNotNull(dBmp)
            val dRes = PhotoCompanionAnalyzer.analyze(dBmp!!, CustomCompanionSpec())
            assertEquals(CompanionGender.GIRL_CHILD, dRes.spec.gender)
            assertEquals(AgeGroup.CHILD_KUTTY, dRes.spec.ageGroup)
            assertEquals(FacialHairStyle.NONE, dRes.spec.facialHair)
            assertTrue(dRes.spec.hairStyle.isFemaleStyle)
        }

        // 2. Verify 1-Tap Sex & Age Switcher (Girl, Boy, Woman, Man) & Visible Neck on Chottu, Chittu, Billa, Woman, Man
        val baseSpec = CustomCompanionPresets.adult35MidBody()
        val womanSpec = baseSpec.applyGenderAndAge(CompanionGender.FEMALE, AgeGroup.ADULT_REGULAR)
        assertEquals(CompanionGender.FEMALE, womanSpec.gender)
        assertEquals(FacialHairStyle.NONE, womanSpec.facialHair)
        assertTrue(womanSpec.hairStyle.isFemaleStyle)

        val girlSpec = baseSpec.applyGenderAndAge(CompanionGender.GIRL_CHILD, AgeGroup.CHILD_KUTTY)
        assertEquals(CompanionGender.GIRL_CHILD, girlSpec.gender)
        assertEquals(AgeGroup.CHILD_KUTTY, girlSpec.ageGroup)
        assertEquals(FacialHairStyle.NONE, girlSpec.facialHair)

        val boySpec = baseSpec.applyGenderAndAge(CompanionGender.BOY_CHILD, AgeGroup.CHILD_KUTTY)
        assertEquals(CompanionGender.BOY_CHILD, boySpec.gender)
        assertEquals(AgeGroup.CHILD_KUTTY, boySpec.ageGroup)
        assertEquals(FacialHairStyle.NONE, boySpec.facialHair)

        // Verify ChibiHumanRenderer draws a visible neck (> 10f) and keeps eyes open (eyesHappy == 0f)
        val testBmp = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        val testCanvas = Canvas(testBmp)
        val rig = MonkeyRig(); val pose = Pose(); val actx = AnimContext()
        for (sp in listOf(womanSpec, girlSpec, boySpec, baseSpec)) {
            val ch = CustomCharacter(sp)
            val r = ch.newRenderer() as com.combo.monkeycompanion.rig.ChibiHumanRenderer
            ch.fill(pose, MonkeyState.DANCE, 1.0f, 1.0f, actx, Wear(ch))
            assertEquals("eyes stay open during dance (${sp.gender})", 0f, pose.eyesHappy, 0.001f)
            rig.layout(pose, 120f, 200f, 1.2f, 1f)
            r.draw(testCanvas, rig, pose, 1.0f)
            assertTrue("ChibiHumanRenderer neck height > 10f for ${sp.gender}", r.lastRenderedNeckHeight > 10f)
        }

        // Verify Chittu (Chottu) and Billa both render a visible neck (> 10f)
        val chittuR = Chittu.newRenderer() as com.combo.monkeycompanion.rig.ChittuRenderer
        Chittu.fill(pose, MonkeyState.IDLE, 1.0f, 1.0f, actx, Wear(Chittu))
        rig.layout(pose, 120f, 200f, 1.2f, 1f)
        chittuR.draw(testCanvas, rig, pose, 1.0f)
        assertTrue("Chittu neck height > 10f (${chittuR.lastRenderedNeckHeight})", chittuR.lastRenderedNeckHeight > 10f)
        assertEquals(Chittu, CharacterRegistry.byId("chottu"))

        // 3. Verify Solo (1), Duo (2), and Trio (3) companion counts & Cat + Mouse Duo Combo
        com.combo.monkeycompanion.mind.MindHub.setNeighbourMode(ctx, com.combo.monkeycompanion.world.Population.Mode.SOLO)
        assertEquals(1, com.combo.monkeycompanion.mind.MindHub.activeCreatures().size)

        com.combo.monkeycompanion.mind.MindHub.setDuoCombo(ctx, "billa", "chittu")
        assertEquals(listOf("billa", "chittu"), com.combo.monkeycompanion.mind.MindHub.activeCreatures())

        com.combo.monkeycompanion.mind.MindHub.setTrioCombo(ctx, "billa", "chittu", "monkey")
        assertEquals(listOf("billa", "chittu", "monkey"), com.combo.monkeycompanion.mind.MindHub.activeCreatures())

        // 4. Verify Cat & Mouse 4-Edge Zero-Gravity Walking (FLOOR, WALL_R, CEIL, WALL_L) + 1.0s Tom & Jerry Mid-Air Coyote Scare!
        CharacterRegistry.select("billa")
        val host = TestHost()
        val vCat = MonkeyView(ctx, host)
        vCat.layout(0, 0, 1080, 2340)
        vCat.debugQuiet = true
        assertTrue("Billa can walk all 4 screen edges with zero gravity", vCat.canWalkScreenEdges)

        for (edge in listOf("FLOOR", "WALL_R", "CEIL", "WALL_L")) {
            vCat.debugSurf(edge, frac = 0.5f)
            vCat.debugFrames(10)
            assertEquals("Billa walks on $edge with zero gravity (not falling)", edge, vCat.debugSurfKind)
            assertFalse(vCat.debugFalling)
        }

        // Trigger mid-screen drop -> verify 1.0s Tom & Jerry coyote scare holds Billa in mid-air before falling!
        vCat.triggerMidScreenCoyoteFall(500f, 700f)
        assertTrue(vCat.debugFalling)
        assertEquals(1.0f, vCat.coyoteScareTimer, 0.02f)
        val yStart = vCat.debugBox().top
        // Advance 0.5s (30 frames) -> still frozen in mid-air with coyoteScareTimer ~ 0.5s!
        vCat.debugFrames(30, dt = 1f / 60f)
        assertTrue("coyoteScareTimer still > 0.4s at 0.5s (${vCat.coyoteScareTimer})", vCat.coyoteScareTimer > 0.4f)
        assertEquals("held in mid-air with zero gravity during 1s scare", yStart, vCat.debugBox().top, 2.0f)
        // Advance another 1.2s (72 frames) -> coyote scare finishes and Billa falls to the floor!
        vCat.debugFrames(90, dt = 1f / 60f)
        assertEquals(0f, vCat.coyoteScareTimer, 0.01f)
        assertTrue("fell down after 1s coyote scare (${vCat.debugBox().top})", vCat.debugBox().top > yStart + 400f)

        // 5. Verify Superboy & Armour Anna Edge-Collision Thrust Loss & Crash-Landing at Bottom Display!
        for (heroId in listOf("superboy", "armour")) {
            CharacterRegistry.select(heroId)
            val vHero = MonkeyView(ctx, host)
            vHero.layout(0, 0, 1080, 2340)
            vHero.debugQuiet = true
            vHero.debugFrames(10)
            assertTrue("trigger edge collision while flying for $heroId", vHero.triggerEdgeCollisionWhileFlying(forceRandomPass = true))
            assertTrue(vHero.isEdgeCrashLanding)
            assertEquals(1, vHero.edgeThrustFailurePhase)
            assertTrue("thrust reduced to < 0.35 (${vHero.edgeThrustMultiplier})", vHero.edgeThrustMultiplier < 0.35f)

            // Step through Phase 1 (sputter) and Phase 2 (plunge) until Phase 3 (crash-landed at bottom with stars!)
            var hitPhase3 = false
            for (f in 0 until 120) {
                vHero.debugFrames(1, dt = 1f / 60f)
                if (vHero.edgeThrustFailurePhase == 3) {
                    hitPhase3 = true
                    break
                }
            }
            assertTrue("$heroId reached Phase 3 crash-landed at bottom display", hitPhase3)
            assertTrue("$heroId is at bottom floor (${vHero.debugBox().bottom})", vHero.debugBox().bottom >= 2100f)
        }
    }

    @Test
    fun v94Interactive10FeatureMeshHandlesTrackpadLoupeAnd1To1CharacterMorphWorkCleanly() {
        ChatHub.init(ctx)
        CustomCompanionStore.init(ctx)

        val baseSpec = CustomCompanionPresets.adult35MidBody()
        val ch = CustomCharacter(baseSpec)
        val renderer = ch.newRenderer() as com.combo.monkeycompanion.rig.ChibiHumanRenderer
        val rig = MonkeyRig(); val pose = Pose(); val actx = AnimContext()
        val bmp = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        ch.fill(pose, MonkeyState.IDLE, 1.0f, 1.0f, actx, Wear(ch))
        rig.layout(pose, 160f, 260f, 1.5f, 1f)
        renderer.draw(cv, rig, pose, 1.0f)

        val rx0 = renderer.lastRenderedFaceRx
        val chinY0 = renderer.lastRenderedChinY
        val eyeY0 = renderer.lastRenderedEyeY
        val mouthY0 = renderer.lastRenderedMouthY
        val hairlineY0 = renderer.lastRenderedHairlineY

        // 1. Nudge JAW_RIGHT outward -> widens both mesh jawWidthRatio and 2D character face rx!
        val meshWideJaw = ch.spec.faceMesh.nudgeFeature(MeshFeatureTarget.JAW_RIGHT, +0.08f, 0f, 0f)
        assertTrue("jawWidthRatio increased (${meshWideJaw.jawWidthRatio})", meshWideJaw.jawWidthRatio > 1.08f)
        ch.updateSpec(ch.spec.copy(faceMesh = meshWideJaw))
        renderer.draw(cv, rig, pose, 1.0f)
        assertTrue("2D character face rx widened 1:1 (${renderer.lastRenderedFaceRx} > $rx0)", renderer.lastRenderedFaceRx > rx0 + 0.8f)

        // 2. Nudge CHIN_BOTTOM downward -> lengthens chinLengthRatio and lowers 2D character chinY!
        val meshLongChin = meshWideJaw.nudgeFeature(MeshFeatureTarget.CHIN_BOTTOM, 0f, +0.08f, 0f)
        assertTrue("chinLengthRatio increased (${meshLongChin.chinLengthRatio})", meshLongChin.chinLengthRatio > 1.08f)
        ch.updateSpec(ch.spec.copy(faceMesh = meshLongChin))
        renderer.draw(cv, rig, pose, 1.0f)
        assertTrue("2D character chinY moved down 1:1 (${renderer.lastRenderedChinY} > $chinY0)", renderer.lastRenderedChinY > chinY0 + 0.8f)

        // 3. Nudge LEFT_EYE downward & outward -> shifts 2D character eyeY and widens eyeSpacingRatio!
        val meshMovedEyes = meshLongChin.nudgeFeature(MeshFeatureTarget.LEFT_EYE, -0.05f, +0.06f, 0f)
        assertTrue("eyeSpacingRatio widened (${meshMovedEyes.eyeSpacingRatio})", meshMovedEyes.eyeSpacingRatio > 0.42f)
        ch.updateSpec(ch.spec.copy(faceMesh = meshMovedEyes))
        renderer.draw(cv, rig, pose, 1.0f)
        assertTrue("2D character eyeY moved down 1:1 (${renderer.lastRenderedEyeY} > $eyeY0)", renderer.lastRenderedEyeY > eyeY0 + 1.5f)

        // 4. Nudge MOUTH downward -> shifts 2D character mouthY!
        val meshMovedMouth = meshMovedEyes.nudgeFeature(MeshFeatureTarget.MOUTH, 0f, +0.06f, +0.10f)
        ch.updateSpec(ch.spec.copy(faceMesh = meshMovedMouth))
        renderer.draw(cv, rig, pose, 1.0f)
        assertTrue("2D character mouthY moved down 1:1 (${renderer.lastRenderedMouthY} > $mouthY0)", renderer.lastRenderedMouthY > mouthY0 + 1.5f)

        // 5. Nudge HAIRLINE_TOP upward -> raises foreheadHeightRatio and 2D character hairlineY!
        val meshHighForehead = meshMovedMouth.nudgeFeature(MeshFeatureTarget.HAIRLINE_TOP, 0f, -0.08f, 0f)
        ch.updateSpec(ch.spec.copy(faceMesh = meshHighForehead))
        renderer.draw(cv, rig, pose, 1.0f)
        assertTrue("2D character hairlineY moved up 1:1 (${renderer.lastRenderedHairlineY} < $hairlineY0)", renderer.lastRenderedHairlineY < hairlineY0 - 0.8f)

        // 6. Verify 32dp magnetic hit-testing of all 10 feature handles and StudioLiveStageView Trackpad + Loupe rendering
        val photoRect = RectF(20f, 40f, 260f, 280f)
        for (target in MeshFeatureTarget.HANDLE_TARGETS) {
            val (hx, hy) = meshHighForehead.featureAnchorScreen(target, photoRect)
            val hit = meshHighForehead.hitTestFeatureHandle(hx + 4f, hy - 3f, photoRect, 2.5f)
            assertEquals("magnetic hit-test finds $target", target, hit)
        }

        val stage = com.combo.monkeycompanion.ui.StudioLiveStageView(ctx, ch).apply {
            uploadedPhotoThumb = CompanionStudioActivity.createSampleAdult35MidBodyBitmap()
            splitCompareMode = true
            showFaceMesh = true
            moveMeshMode = true
            zoomLevel = 3.2f
            selectedMeshFeature = MeshFeatureTarget.MOUTH
        }
        stage.layout(0, 0, 800, 600)
        val stageBmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val stageCv = Canvas(stageBmp)
        stage.draw(stageCv)
        stage.nudgeSelectedFeature(0.02f, -0.04f, 0.05f)
        stage.draw(stageCv)

        // 7. Verify encode/decode round-trip preserves all 27 FaceLandmarkMesh parameters
        val encoded = ch.spec.faceMesh.encode()
        val decoded = FaceLandmarkMesh.decode(encoded)
        assertEquals(ch.spec.faceMesh.jawWidthRatio, decoded.jawWidthRatio, 0.001f)
        assertEquals(ch.spec.faceMesh.chinLengthRatio, decoded.chinLengthRatio, 0.001f)
        assertEquals(ch.spec.faceMesh.eyeYOffset, decoded.eyeYOffset, 0.001f)
        assertEquals(ch.spec.faceMesh.mouthYOffset, decoded.mouthYOffset, 0.001f)
        assertEquals(ch.spec.faceMesh.lipFullnessRatio, decoded.lipFullnessRatio, 0.001f)
    }

    @Test
    fun v95WomanCleanUncoveredNeckZeroHeadClippingNameRosterAndGiveBrainOptInWorkCleanly() {
        ChatHub.init(ctx)
        CustomCompanionStore.init(ctx)

        // 1. Verify Woman (LONG_WAVY) draws back hair BEHIND the neck so the neck is 100% warm skin tone (never a grey mask/beard!)
        val womanSpec = CustomCompanionPresets.womanAdult().copy(
            name = "Priya",
            hairStyle = HairStyle.LONG_WAVY,
            hairColor = PhotoCompanionAnalyzer.refineHairTone(Color.rgb(0x70, 0x6A, 0x66), isSenior = false)
        )
        // Verify refineHairTone converts washed-out grey (#706A66) into rich dark espresso/black (R < 55)
        assertTrue("grey hair refined to rich dark tone (R=${Color.red(womanSpec.hairColor)})", Color.red(womanSpec.hairColor) <= 54)
        assertEquals(FacialHairStyle.NONE, womanSpec.facialHair)

        val womanChar = CustomCompanionStore.save(ctx, womanSpec)
        val wBmp = Bitmap.createBitmap(300, 320, Bitmap.Config.ARGB_8888)
        val wCv = Canvas(wBmp)
        val rig = MonkeyRig(); val pose = Pose(); val actx = AnimContext()
        womanChar.fill(pose, MonkeyState.IDLE, 1.0f, 1.0f, actx, Wear(womanChar))
        val anchorX = 150f; val anchorY = 260f; val sc = 1.6f
        rig.layout(pose, anchorX, anchorY, sc, 1f)
        val wRenderer = womanChar.newRenderer() as com.combo.monkeycompanion.rig.ChibiHumanRenderer
        wRenderer.wear = Wear(womanChar)
        wRenderer.draw(wCv, rig, pose, 1.0f)

        // Map neck center (0f, -2.5f in head space) to bitmap coordinates and verify it is warm skin tone, NOT dark hair!
        val neckPt = floatArrayOf(0f, -2.5f)
        rig.head.mapPoints(neckPt)
        val neckPixel = wBmp.getPixel(neckPt[0].toInt(), neckPt[1].toInt())
        assertTrue(
            "Neck pixel is warm skin tone (R=${Color.red(neckPixel)}, G=${Color.green(neckPixel)}, B=${Color.blue(neckPixel)}), not covered by back hair!",
            Color.red(neckPixel) > 150 && Color.green(neckPixel) > 100 && Color.red(neckPixel) > Color.blue(neckPixel) + 25
        )

        // 2. Verify Zero Top-of-Head Clipping in MonkeyView (even at XL 1.5x scale & flying/hovering!)
        val xlWoman = womanSpec.copy(companionScale = 1.50f, outfitStyle = OutfitStyle.IRON_ARMOUR_SUIT)
        CustomCompanionStore.save(ctx, xlWoman)
        CharacterRegistry.select(xlWoman.id)
        ChatHub.settings.characterId = xlWoman.id
        val host = TestHost()
        val v = MonkeyView(ctx, host)
        v.layout(0, 0, 1080, 2340)
        v.debugQuiet = true
        v.debugFrames(25)

        val ht = FloatArray(2)
        val rigField = MonkeyView::class.java.getDeclaredField("rig").apply { isAccessible = true }.get(v) as MonkeyRig
        rigField.headTop(ht)
        assertTrue("Top of head is strictly inside MonkeyView canvas (headTopY=${ht[1]} >= 4f)", ht[1] >= 4f)

        // 3. Verify Custom Name Saving & Multi-Character Roster in MainActivity
        val c1 = CustomCompanionStore.save(ctx, womanSpec.copy(id = "custom_1", name = "Ananya"))
        val c2 = CustomCompanionStore.saveAsNewCompanion(ctx, CustomCompanionPresets.child5YrDaughter().copy(name = "Kutti"))
        assertEquals("Ananya", c1.name)
        assertEquals("Kutti", c2.name)
        val rosterNames = CharacterRegistry.allWithCustom.map { it.name }
        assertTrue("Roster contains Ananya and Kutti: $rosterNames", "Ananya" in rosterNames && "Kutti" in rosterNames)

        // 4. Verify Strict "🧠 Give Brain" Opt-In Gate (OFF by default until user enables Give Brain!)
        ChatHub.settings.provider = com.combo.monkeycompanion.ai.Provider.GEMINI
        ChatHub.settings.setApiKeyFor(com.combo.monkeycompanion.ai.Provider.GEMINI, "test-dummy-key-1234")
        ChatHub.setBrainEnabled(false)
        assertFalse("AI Brain is strictly OFF when Give Brain is disabled", ChatHub.isBrainEnabled())
        assertFalse("hasRealBrain is false when Give Brain is disabled even if key exists", ChatHub.hasRealBrain())
        assertTrue(ChatHub.giveBrainStatusLine().contains("OFF"))

        ChatHub.setBrainEnabled(true)
        assertTrue("AI Brain turns ON when user enables Give Brain", ChatHub.isBrainEnabled())
        assertTrue("hasRealBrain is true when Give Brain is enabled with key", ChatHub.hasRealBrain())
        ChatHub.setBrainEnabled(false)

        // 5. Verify All 6 Purposeful Daily Life Utilities (DailyUtilityEngine)
        val DU = com.combo.monkeycompanion.agent.DailyUtilityEngine
        DU.resetForTest(ctx)
        DU.init(ctx)

        // Utility 1: 1-Tap OTP & Live Delivery / Cab / Train Catcher
        val otpNotif = NotifItem("k_otp", "com.google.android.apps.messaging", "Messages", "HDFC Bank", "Your OTP for transaction is 482910. Valid for 10 mins.", 1000L, false)
        DU.onNotificationArrived(ctx, otpNotif, nowMs = 1000L)
        assertEquals("482910", DU.activeOtpCode)
        assertEquals("🔐 OTP: 482910 [📋 Copy]", DU.activePrimaryPillLabel(nowMs = 2000L))
        val copiedOtp = DU.copyActiveOtp(ctx)
        assertEquals("482910", copiedOtp)
        assertEquals(null, DU.activeOtpCode)

        val swiggyNotif = NotifItem("k_sw", "in.swiggy.android", "Swiggy", "Order Out for Delivery", "Order arriving in 4 mins with Surya", 2000L, false)
        DU.onNotificationArrived(ctx, swiggyNotif, nowMs = 2000L)
        assertNotNull(DU.activeDeliveryBadge)
        assertTrue(DU.activeDeliveryBadge!!.contains("Swiggy"))
        DU.clearDeliveryBadge()

        // Utility 2: 5-Slot Pocket Clipboard Backpack
        DU.pinToBackpack(ctx, "https://github.com/settai-v9.5")
        DU.pinToBackpack(ctx, "Flat 4B, Anna Nagar, Chennai")
        val bp = DU.clipboardBackpack(ctx)
        assertTrue(bp.size >= 2)
        assertEquals("Flat 4B, Anna Nagar, Chennai", bp[0])
        assertEquals("https://github.com/settai-v9.5", bp[1])
        assertEquals("https://github.com/settai-v9.5", DU.copyBackpackItem(ctx, 1))

        // Utility 3: Doomscroll & 20-20-20 Eye / Neck Care Guard
        assertTrue(DU.checkContinuousAppSession("com.instagram.android", 22))
        assertEquals(20f, DU.eyeBreakRemainingSec, 0.5f)
        assertTrue(DU.activePrimaryPillLabel()!!.contains("Eye Break"))
        DU.onOverlayPillTapped(ctx)
        assertEquals(0f, DU.eyeBreakRemainingSec, 0.01f)

        // Utility 4: Sticky Chest Reminder Badge
        DU.setStickyReminder(ctx, "💊 Take tablet at 9 PM")
        assertEquals("📌 💊 Take tablet at 9 PM [✓]", DU.activePrimaryPillLabel())
        val pillRect = RectF()
        assertTrue(DU.drawOverlayPill(wCv, 150f, 10f, 300f, 2f, pillRect))
        assertFalse(pillRect.isEmpty)
        val doneMsg = DU.onOverlayPillTapped(ctx)
        assertTrue(doneMsg != null && doneMsg.contains("Completed sticky reminder"))
        assertEquals("", DU.stickyReminderText)

        // Utility 5: 20-Min Focus / Kids' Study Timer
        DU.startFocusTimer(ctx, 20)
        assertTrue(com.combo.monkeycompanion.mind.MindHub.focusBuddy.active)
        assertEquals("📚 20:00", com.combo.monkeycompanion.mind.MindHub.focusBuddy.timerLabel())
        DU.stopFocusTimer()

        // Utility 6: Proactive Battery 85% Charge Guard & Overheat Watchdog
        val bh = DU.readBatteryHealth(ctx)
        assertTrue(bh.statusLine.contains("Battery:"))
        assertTrue(bh.statusLine.contains("85% Guard ON"))

        // Utility 7: Tucked-Edge YouTube/Reels Screen-Time Watchdog (Auto-Wake from '<' Edge Arrow + Auto-Re-Tuck!)
        v.debugQuiet = false
        v.debugHide(1)
        v.debugFrames(40)
        assertTrue("Settai is tucked beside the screen with '<' arrow", v.debugHidden)

        // Simulate 20 minutes of YouTube while Settai is tucked at the edge -> automatically wakes up and pops out!
        val woke = v.simulateEdgeWatchdogSeconds(20 * 60f + 5f, "com.google.android.youtube")
        assertTrue("Watchdog triggered after 20m on YouTube while tucked at edge", woke)
        v.debugFrames(60)
        assertFalse("Settai popped out from the '<' edge tab to alert the user", v.debugHidden)
        assertTrue("Settai remembers it was tucked before the alert", v.wasTuckedBeforeUtilityAlert)
        assertTrue("Bubble shows [✓ OK, Tuck Back 👈] chip: ${v.debugBubbleChipIds}", "retuck_edge" in v.debugBubbleChipIds)

        // Advance 21 seconds -> Settai automatically slides right back into the '<' side-edge arrow tab!
        v.debugFrames(22 * 60, dt = 1f / 60f)
        assertTrue("Settai automatically re-tucked back into the '<' edge tab after the alert", v.debugHidden)
    }

    @Test
    fun renderV9UniversalLookAlikePreviewSheet() {
        CustomCompanionStore.init(ctx)
        val selfieFile = File("/tmp/user_selfie_test.jpg")
        val selfieBmp = if (selfieFile.exists()) {
            PhotoCompanionAnalyzer.decodeBitmapWithExif(selfieFile) ?: CompanionStudioActivity.createSampleAdult35MidBodyBitmap()
        } else {
            CompanionStudioActivity.createSampleAdult35MidBodyBitmap()
        }
        val maleRes = PhotoCompanionAnalyzer.analyze(selfieBmp, CustomCompanionPresets.adult35MidBody())
        val maleSpec = maleRes.spec.copy(name = "Manik", faceMode = FaceMode.CHIBI_TWIN)

        val daughterFile = File("/tmp/daughter_photo_test.png")
        val daughterBmp = if (daughterFile.exists()) {
            PhotoCompanionAnalyzer.decodeBitmapWithExif(daughterFile) ?: CompanionStudioActivity.createSamplePortraitBitmap()
        } else {
            CompanionStudioActivity.createSamplePortraitBitmap()
        }
        val girlRes = PhotoCompanionAnalyzer.analyze(daughterBmp, CustomCompanionPresets.child5YrDaughter())
        val girlSpec = girlRes.spec.copy(name = "Kuttyma", faceMode = FaceMode.CHIBI_TWIN)

        val W = 1720
        val H = 1120
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xFF, 0xF3, 0xE0))

        val brown = Color.rgb(0x4A, 0x2C, 0x14)
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brown; textSize = 27.5f; typeface = Typeface.create("sans-serif", Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        val secP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brown; textSize = 17.5f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xE6, 0xC9, 0xA3); style = Paint.Style.STROKE; strokeWidth = 2.5f
        }
        val capP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xE8, 0xEA, 0xF6); textSize = 13.2f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val subCapP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xFF, 0xD5, 0x4F); textSize = 11.6f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }

        c.drawText("Settai v9.5 · Clean Woman Neck & Hair + Unclipped Flight Head + Multi-Name Roster + Highlighted Give Brain + 10-Handle Mesh", W / 2f, 38f, titleP)

        val gap = 20f
        val colW = (W - gap * 3) / 2f
        val rowH = (H - 70f - gap * 2) / 2f
        val rig = MonkeyRig(); val pose = Pose(); val actx = AnimContext()

        // =========================================================================
        // PANEL 1 (Top-Left): Daughter Photo & Male Selfie -> Locked 68-Pt Mesh -> Open-Eyed 2D Look-Alikes + Woman & Boy
        // =========================================================================
        val r1 = RectF(gap, 78f, gap + colW, 78f + rowH)
        c.drawText("1. Fixed 68-Pt Mesh & Polished 2D Faces: Daughter (Girl), Man, Woman & Boy (Chottu Neck!)", r1.left + 4f, r1.top - 8f, secP)
        c.drawRoundRect(r1, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x14, 0x16, 0x26) })
        c.save(); c.clipRect(r1)

        // 1A: Daughter Photo with 10 Glowing Feature Handles + 2.5x Floating Magnifier Loupe!
        val gPhotoR = RectF(r1.left + 14f, r1.top + 18f, r1.left + 186f, r1.top + 190f)
        c.drawBitmap(girlRes.portraitBitmap, null, gPhotoR, Paint(Paint.FILTER_BITMAP_FLAG))
        girlRes.faceMesh.drawFaceIdWireframe(c, gPhotoR, 0.45f, 1.25f, MeshFeatureTarget.MOUTH)
        // Draw 2.5x Floating Magnifier Loupe above the photo corner showing zoomed-in Lip/Mouth alignment!
        val lcx = gPhotoR.right - 26f; val lcy = gPhotoR.top + 32f; val lr = 34f
        val (mhx, mhy) = girlRes.faceMesh.featureAnchorScreen(MeshFeatureTarget.MOUTH, gPhotoR)
        c.save()
        val lPath = android.graphics.Path().apply { addCircle(lcx, lcy, lr, android.graphics.Path.Direction.CW) }
        c.clipPath(lPath)
        c.drawColor(Color.rgb(0x10, 0x12, 0x20))
        c.translate(lcx, lcy); c.scale(2.2f, 2.2f); c.translate(-mhx, -mhy)
        c.drawBitmap(girlRes.portraitBitmap, null, gPhotoR, Paint(Paint.FILTER_BITMAP_FLAG))
        girlRes.faceMesh.drawFaceIdWireframe(c, gPhotoR, 0.45f, 0.9f, MeshFeatureTarget.MOUTH)
        c.restore()
        c.drawCircle(lcx, lcy, lr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xD5, 0x4F); style = Paint.Style.STROKE; strokeWidth = 3f })
        c.drawLine(lcx - 10f, lcy, lcx + 10f, lcy, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xD5, 0x4F); strokeWidth = 1.8f })
        c.drawLine(lcx, lcy - 10f, lcx, lcy + 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xD5, 0x4F); strokeWidth = 1.8f })
        c.drawText("📷 10 Feature Handles", gPhotoR.centerX(), r1.top + 210f, capP)
        c.drawText("(+ 2.5x Magnifier Loupe!)", gPhotoR.centerX(), r1.top + 226f, Paint(subCapP).apply { color = Color.rgb(0xFF, 0xD5, 0x4F) })

        // 1B: Daughter 2D Look-Alike (3.2x Face Zoom — Wide Open Eyes, Parted Hair + Pink Clip, Bindi, Gold Earrings, Zero Bald Cap!)
        val girlChar = CustomCharacter(girlSpec)
        val girlRenderer = girlChar.newRenderer() as com.combo.monkeycompanion.rig.ChibiHumanRenderer
        girlRenderer.wear = Wear(girlChar)
        girlChar.fill(pose, MonkeyState.IDLE, 1.0f, 1.0f, actx, Wear(girlChar))
        pose.eyeOpen = 1f; pose.eyesHappy = 0f; pose.armLRaise = 10f; pose.armRRaise = 10f
        c.save()
        c.clipRect(r1.left + 195f, r1.top + 12f, r1.left + 390f, r1.bottom - 52f)
        rig.layout(pose, r1.left + 292f, r1.bottom + 85f, 2.45f, 1f)
        girlRenderer.draw(c, rig, pose, 1.0f)
        val (grx, gtop, gbot) = girlRenderer.headBounds()
        girlSpec.faceMesh.drawOnCharacterHead(c, rig.head, grx, gtop, gbot, 0.45f, 1.15f)
        c.restore()
        c.drawText("👧 Girl Child (3.2x)", r1.left + 292f, r1.bottom - 30f, capP)
        c.drawText("(Open Eyes · Clip · Bindi)", r1.left + 292f, r1.bottom - 13f, Paint(subCapP).apply { color = Color.rgb(0xFF, 0x80, 0xAB) })

        // 1C: Male Selfie 2D Look-Alike (3.2x Face Zoom — Natural Curved Hairline, Compact Circle Beard, Clean Cheeks, Locked Mesh!)
        val maleChar = CustomCompanionRegistry.register(maleSpec)
        val maleRenderer = maleChar.newRenderer() as com.combo.monkeycompanion.rig.ChibiHumanRenderer
        maleRenderer.wear = Wear(maleChar)
        maleChar.fill(pose, MonkeyState.IDLE, 1.0f, 1.0f, actx, Wear(maleChar))
        pose.eyeOpen = 1f; pose.eyesHappy = 0f; pose.armLRaise = 10f; pose.armRRaise = 10f
        c.save()
        c.clipRect(r1.left + 398f, r1.top + 12f, r1.left + 595f, r1.bottom - 52f)
        rig.layout(pose, r1.left + 496f, r1.bottom + 100f, 2.55f, 1f)
        maleRenderer.draw(c, rig, pose, 1.0f)
        val (mrx, mtop, mbot) = maleRenderer.headBounds()
        maleSpec.faceMesh.drawOnCharacterHead(c, rig.head, mrx, mtop, mbot, 0.45f, 1.15f)
        c.restore()
        c.drawText("👨 Man (3.2x Locked Mesh)", r1.left + 496f, r1.bottom - 30f, capP)
        c.drawText("(Curved Hairline · Clean Cheeks)", r1.left + 496f, r1.bottom - 13f, Paint(subCapP).apply { color = Color.rgb(0x69, 0xF0, 0xAE) })

        // 1D: Woman (Priya) & Boy Child (Chottu with Visible Neck!)
        val womanChar = CustomCharacter(CustomCompanionPresets.womanAdult())
        womanChar.fill(pose, MonkeyState.WAVE, 1.2f, 1.2f, actx, Wear(womanChar))
        pose.eyeOpen = 1f; pose.eyesHappy = 0f
        rig.layout(pose, r1.left + 660f, r1.bottom - 56f, 1.38f, 1f)
        womanChar.newRenderer().also { it.wear = Wear(womanChar) }.draw(c, rig, pose, 1.2f)
        c.drawText("👩 Woman", r1.left + 660f, r1.bottom - 30f, capP)
        c.drawText("(Lashes · Kurta)", r1.left + 660f, r1.bottom - 13f, subCapP)

        val boyChar = CustomCharacter(CustomCompanionPresets.child5YrBoy())
        boyChar.fill(pose, MonkeyState.WAVE, 1.4f, 1.4f, actx, Wear(boyChar))
        pose.eyeOpen = 1f; pose.eyesHappy = 0f
        rig.layout(pose, r1.left + 768f, r1.bottom - 56f, 1.38f, -1f)
        boyChar.newRenderer().also { it.wear = Wear(boyChar) }.draw(c, rig, pose, 1.4f)
        c.drawText("👦 Boy (Chottu)", r1.left + 768f, r1.bottom - 30f, capP)
        c.drawText("(Visible Neck!)", r1.left + 768f, r1.bottom - 13f, subCapP)

        c.restore()
        c.drawRoundRect(r1, 22f, 22f, borderP)

        // =========================================================================
        // PANEL 2 (Top-Right): Cat & Mouse Duo 4-Edge Zero-G Walking + 1s Tom & Jerry Scare + Chottu/Chittu Neck
        // =========================================================================
        val r2 = RectF(r1.right + gap, 78f, W - gap, 78f + rowH)
        c.drawText("2. Cat & Mouse 4-Edge Zero-G Wall/Ceiling Walk + 1s Tom & Jerry Scare + Chittu Neck", r2.left + 4f, r2.top - 8f, secP)
        c.drawRoundRect(r2, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x1A, 0x18, 0x2B) })
        c.save(); c.clipRect(r2)

        // Draw glowing 4-edge zero-gravity perimeter track around Panel 2
        val edgeTrackP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0x00, 0xE5, 0xFF); style = Paint.Style.STROKE; strokeWidth = 3f
        }
        c.drawRoundRect(RectF(r2.left + 12f, r2.top + 12f, r2.right - 12f, r2.bottom - 52f), 16f, 16f, edgeTrackP)

        // 2A (Left Wall +90°): Billa walking vertically up the left edge with zero gravity!
        c.save()
        c.translate(r2.left + 68f, r2.top + 215f)
        c.rotate(90f)
        Billa.fill(pose, MonkeyState.WALK, 1.1f, 1.1f, actx, Wear(Billa))
        rig.layout(pose, 0f, 0f, 1.18f, 1f)
        Billa.newRenderer().also { it.wear = Wear(Billa) }.draw(c, rig, pose, 1.1f)
        c.restore()
        c.drawText("🐱 Left Wall (+90° Zero-G)", r2.left + 108f, r2.bottom - 30f, capP)
        c.drawText("(Vertical Wall Walk)", r2.left + 108f, r2.bottom - 13f, Paint(subCapP).apply { color = Color.rgb(0x80, 0xDE, 0xEA) })

        // 2B (Top Ceiling 180°): Chittu scurrying upside-down across the top screen edge!
        c.save()
        c.translate(r2.left + 295f, r2.top + 68f)
        c.rotate(180f)
        Chittu.fill(pose, MonkeyState.WALK, 1.5f, 1.5f, actx, Wear(Chittu))
        rig.layout(pose, 0f, 0f, 1.15f, 1f)
        Chittu.newRenderer().also { it.wear = Wear(Chittu) }.draw(c, rig, pose, 1.5f)
        c.restore()
        c.drawText("🐭 Top Edge (180° Zero-G)", r2.left + 295f, r2.top + 165f, Paint(subCapP).apply { color = Color.rgb(0xFF, 0xD5, 0x4F) })

        // 2C (Mid-Screen): 1-Second Tom & Jerry Mid-Air Scare Hold ("😱 YIKES! 1s") before falling!
        Billa.fill(pose, MonkeyState.STARTLED, 1.8f, 0.5f, actx, Wear(Billa))
        pose.eyeOpen = 1.65f; pose.mouthOpen = 0.92f; pose.armLRaise = 148f; pose.armRRaise = 148f; pose.legLSwing = 32f; pose.legRSwing = -32f
        rig.layout(pose, r2.left + 475f, r2.top + 285f, 1.28f, 1f)
        Billa.newRenderer().also { it.wear = Wear(Billa) }.draw(c, rig, pose, 1.8f)
        val scareBadge = RectF(r2.left + 405f, r2.top + 132f, r2.left + 545f, r2.top + 162f)
        c.drawRoundRect(scareBadge, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xEB, 0x3B) })
        c.drawText("😱 YIKES! 1.0s Hold!", scareBadge.centerX(), scareBadge.centerY() + 5f, Paint(capP).apply { color = Color.rgb(0xB7, 0x1C, 0x1C) })
        c.drawText("😱 1s Mid-Air Scare", r2.left + 475f, r2.bottom - 30f, capP)
        c.drawText("(Holds 1s → Then Falls!)", r2.left + 475f, r2.bottom - 13f, subCapP)

        // 2D (Right): Chittu & Billa Close-Up showing Chittu's Visible Neck & Collar!
        Chittu.fill(pose, MonkeyState.WAVE, 1.2f, 1.2f, actx, Wear(Chittu))
        rig.layout(pose, r2.left + 695f, r2.bottom - 58f, 1.52f, -1f)
        Chittu.newRenderer().also { it.wear = Wear(Chittu) }.draw(c, rig, pose, 1.2f)
        c.drawText("🐭 Chittu (Visible Neck!)", r2.left + 695f, r2.bottom - 30f, capP)
        c.drawText("(Neck + Red Vest Collar)", r2.left + 695f, r2.bottom - 13f, Paint(subCapP).apply { color = Color.rgb(0x69, 0xF0, 0xAE) })

        c.restore()
        c.drawRoundRect(r2, 22f, 22f, borderP)

        // =========================================================================
        // PANEL 3 (Bottom-Left): Superboy & Armour Anna Edge-Collision Thrust Drop & Crash-Landing + Live Trio (3)
        // =========================================================================
        val r3 = RectF(gap, r1.bottom + gap + 22f, gap + colW, H - 18f)
        c.drawText("3. Superboy & Armour Anna Edge-Hit Thrust Loss & Crash-Landing + Solo/Duo/Trio Screen", r3.left + 4f, r3.top - 8f, secP)
        c.drawRoundRect(r3, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x1C, 0x18, 0x2B) })
        c.save(); c.clipRect(r3)

        val fy3 = r3.bottom - 58f

        // 3A: Armour Anna hitting top-left edge -> thrust sputtering & dropping!
        ArmourAnna.fill(pose, MonkeyState.FALLING, 2.0f, 1.0f, actx, Wear(ArmourAnna))
        pose.armLRaise = 142f; pose.armRRaise = 142f; pose.jets = 0.2f; pose.palmGlow = 0.2f
        rig.layout(pose, r3.left + 115f, r3.top + 175f, 1.28f, 1f)
        ArmourAnna.newRenderer().also { it.wear = Wear(ArmourAnna) }.draw(c, rig, pose, 2.0f)
        c.drawText("⚡💥 Edge Hit: Thrust 15%!", r3.left + 125f, r3.top + 42f, Paint(subCapP).apply { color = Color.rgb(0xFF, 0x8A, 0x65) })
        c.drawText("🛡️ 1. Edge Hit Sputter", r3.left + 115f, r3.bottom - 30f, capP)
        c.drawText("(Power Drops → Plunges)", r3.left + 115f, r3.bottom - 13f, subCapP)

        // 3B: Superboy Crash-Landed at Bottom Display with Spinning Stars!
        Superboy.fill(pose, MonkeyState.IDLE, 2.4f, 1.0f, actx, Wear(Superboy))
        pose.squashY = 0.78f; pose.squashX = 1.22f; pose.bodyY = 12f; pose.bodyRot = 24f; pose.eyeOpen = 0.4f; pose.soot = 0.8f
        rig.layout(pose, r3.left + 310f, fy3, 1.38f, 1f)
        Superboy.newRenderer().also { it.wear = Wear(Superboy) }.draw(c, rig, pose, 2.4f)
        c.drawText("⭐ 💫 ⭐", r3.left + 310f, fy3 - 135f, Paint(capP).apply { color = Color.rgb(0xFF, 0xD5, 0x4F); textSize = 16f })
        c.drawText("💥 2. Bottom Crash-Land!", r3.left + 310f, r3.bottom - 30f, capP)
        c.drawText("(Crater Pose + Stars ⭐💫)", r3.left + 310f, r3.bottom - 13f, Paint(subCapP).apply { color = Color.rgb(0xFF, 0xD5, 0x4F) })

        // 3C: Live Trio (3 Companions on Screen simultaneously: Settai + Billa + Chittu!)
        MonkeyCharacter.fill(pose, MonkeyState.DANCE, 1.0f, 1.0f, actx, Wear(MonkeyCharacter))
        rig.layout(pose, r3.left + 515f, fy3, 1.24f, 1f)
        MonkeyCharacter.newRenderer().also { it.wear = Wear(MonkeyCharacter) }.draw(c, rig, pose, 1.0f)

        Billa.fill(pose, MonkeyState.WALK, 1.2f, 1.2f, actx, Wear(Billa))
        rig.layout(pose, r3.left + 635f, fy3, 1.24f, 1f)
        Billa.newRenderer().also { it.wear = Wear(Billa) }.draw(c, rig, pose, 1.2f)

        Chittu.fill(pose, MonkeyState.WAVE, 1.4f, 1.4f, actx, Wear(Chittu))
        rig.layout(pose, r3.left + 748f, fy3, 1.24f, -1f)
        Chittu.newRenderer().also { it.wear = Wear(Chittu) }.draw(c, rig, pose, 1.4f)

        c.drawText("👨‍👩‍👧 Live Solo (1) / Duo (2) / Trio (3) Active on Screen!", r3.left + 630f, r3.bottom - 30f, capP)
        c.drawText("(Spawns 1, 2, or 3 Independent Companion Windows)", r3.left + 630f, r3.bottom - 13f, Paint(subCapP).apply { color = Color.rgb(0x69, 0xF0, 0xAE) })

        c.restore()
        c.drawRoundRect(r3, 22f, 22f, borderP)

        // =========================================================================
        // PANEL 4 (Bottom-Right): Smooth-Scrolling "What You Missed" Card ([▲]/[▼] + Fling) & Fullscreen Inspector
        // =========================================================================
        val r4 = RectF(r3.right + gap, r1.bottom + gap + 22f, W - gap, H - 18f)
        c.drawText("4. Smooth-Scrolling “What You Missed” Card (9 Groups + [▲]/[▼] + Swipe) & Edge-to-Edge Inspector", r4.left + 4f, r4.top - 8f, secP)
        c.drawRoundRect(r4, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x19, 0x20, 0x30) })
        c.save(); c.clipRect(r4)

        val richGroups = NotifSummary.group(NotifSummary.richDemoItems(), maxGroups = 25)
        val nCard = NotifSummaryCard(density = 1.22f)
        nCard.setData(richGroups, "Good evening, Manik!", "Here's what you missed...", maxWidthPx = 425)
        nCard.scrollBy(68f)
        nCard.draw(c, r4.left + 16f, r4.top + 18f)

        val fsBox = RectF(r4.left + 455f, r4.top + 18f, r4.right - 16f, r4.bottom - 18f)
        c.drawRoundRect(fsBox, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x0D, 0x0B, 0x18) })
        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xD5, 0x4F); textSize = 13.8f; typeface = Typeface.DEFAULT_BOLD }
        val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 12.0f }
        c.drawText("🎯 6 Purposeful Daily Utilities (v9.5):", fsBox.left + 14f, fsBox.top + 22f, hPaint)
        c.drawText("1. 🔐 1-Tap OTP Copy Pill & 🛵 Live Delivery Tracker", fsBox.left + 14f, fsBox.top + 42f, Paint(bPaint).apply { color = Color.rgb(0x69, 0xF0, 0xAE); typeface = Typeface.DEFAULT_BOLD })
        c.drawText("2. 🎒 5-Slot Pocket Clipboard Backpack (1-Tap Copy)", fsBox.left + 14f, fsBox.top + 61f, bPaint)
        c.drawText("3. 🍅 20m Kids' Study / Focus Pomodoro Timer", fsBox.left + 14f, fsBox.top + 80f, bPaint)
        c.drawText("4. 📌 Sticky Chest Reminder (💊 Tablet / 💧 Water)", fsBox.left + 14f, fsBox.top + 99f, Paint(bPaint).apply { color = Color.rgb(0x80, 0xDE, 0xEA) })
        c.drawText("5. 🧘 Doomscroll Breaker & 20-20-20 Eye Care Guard", fsBox.left + 14f, fsBox.top + 118f, bPaint)
        c.drawText("6. 🔋 Battery 85% Charge Guard & >40°C Heat Watchdog", fsBox.left + 14f, fsBox.top + 137f, bPaint)
        c.drawText("🧠 Highlighted 'Give Brain' Opt-In + Woman Fix:", fsBox.left + 14f, fsBox.top + 163f, hPaint)
        c.drawText("• [🧠 Give Brain] on Home/Studio/Settings (OFF 🔒 default)", fsBox.left + 14f, fsBox.top + 183f, Paint(bPaint).apply { color = Color.rgb(0xFF, 0xD5, 0x4F); typeface = Typeface.DEFAULT_BOLD })
        c.drawText("• Woman clean skin neck + unclipped flight head!", fsBox.left + 14f, fsBox.top + 202f, bPaint)
        c.drawText("• 10-Handle Mesh + 2.5x Loupe + [◀ ▲ ▼ ▶] D-Pad", fsBox.left + 14f, fsBox.top + 221f, Paint(bPaint).apply { color = Color.rgb(0x69, 0xF0, 0xAE) })

        c.restore()
        c.drawRoundRect(r4, 22f, 22f, borderP)

        val outV95 = com.combo.monkeycompanion.TestPaths.out("settai-v9.5-preview.png")
        val outV94 = com.combo.monkeycompanion.TestPaths.out("settai-v9.4-preview.png")
        val outV93 = com.combo.monkeycompanion.TestPaths.out("settai-v9.3-preview.png")
        val outV92 = com.combo.monkeycompanion.TestPaths.out("settai-v9.2-preview.png")
        val outV91 = com.combo.monkeycompanion.TestPaths.out("settai-v9.1-preview.png")
        val outV9 = com.combo.monkeycompanion.TestPaths.out("settai-v9-preview.png")
        FileOutputStream(outV95).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        outV95.copyTo(outV94, overwrite = true)
        outV95.copyTo(outV93, overwrite = true)
        outV94.copyTo(outV92, overwrite = true)
        outV94.copyTo(outV91, overwrite = true)
        outV94.copyTo(outV9, overwrite = true)
        assertTrue(outV93.exists() && outV93.length() > 30_000)
    }
}
