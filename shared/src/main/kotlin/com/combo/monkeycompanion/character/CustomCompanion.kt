package com.combo.monkeycompanion.character

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.brain.ArmourJetsPower
import com.combo.monkeycompanion.brain.ConfettiRocketsPower
import com.combo.monkeycompanion.brain.Gag
import com.combo.monkeycompanion.brain.HeatVisionPower
import com.combo.monkeycompanion.brain.HeroSwoop
import com.combo.monkeycompanion.brain.LowPowerPower
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.ShieldBubblePower
import com.combo.monkeycompanion.brain.SpeedBlurPower
import com.combo.monkeycompanion.brain.SuperBreathPower
import com.combo.monkeycompanion.brain.SuperStrengthPower
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.ChibiHumanRenderer
import com.combo.monkeycompanion.rig.ChibiPeekFace
import com.combo.monkeycompanion.rig.MonkeyRenderer
import com.combo.monkeycompanion.rig.PeekFace
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.Lines
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class CompanionGender(
    val id: String,
    val label: String,
    val shortLabel: String,
    val isFemale: Boolean,
    val isChildDefault: Boolean,
) {
    FEMALE("female", "👩 Woman / Female", "👩 Woman", isFemale = true, isChildDefault = false),
    MALE("male", "👨 Man / Male", "👨 Man", isFemale = false, isChildDefault = false),
    GIRL_CHILD("girl", "👧 Girl (Child)", "👧 Girl", isFemale = true, isChildDefault = true),
    BOY_CHILD("boy", "👦 Boy (Child)", "👦 Boy", isFemale = false, isChildDefault = true);

    companion object {
        fun of(id: String?): CompanionGender =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: MALE
    }
}

enum class RelationshipPreset(val id: String, val label: String, val callUser: String, val tagline: String) {
    SELF_TWIN("self", "My Look-Alike Twin (Me!) 😎", "Boss", "Your animated mini-me twin living right on your screen"),
    BESTIE("bestie", "Best Friend / Machan 🤝", "Machan", "Your loyal Tamil Nadu bestie who cheers, jokes, and hangs out with you"),
    DAUGHTER_KID("daughter", "5yr Child / Kid 👧🧒", "Appa/Amma", "Cheerful little kid companion who dances, hugs, and brightens your phone"),
    HERO("hero", "Super Hero Look-Alike 🦸", "Chief", "Your custom superhero avatar with cape, jets, and ninja powers"),
    PET_SIBLING("sibling", "Family / Sibling 🌟", "Anna/Akka", "Playful family look-alike full of comedy, stunts, and heart");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: SELF_TWIN
    }
}

enum class AgeGroup(val id: String, val label: String, val shortLabel: String, val headScale: Float, val torsoHeightScale: Float, val legLengthScale: Float) {
    CHILD_KUTTY("child", "👶 Child 3–10yr (Big Head, Cute Small Body) 👧👦", "👶 Child (3–10y)", 1.15f, 0.80f, 0.78f),
    YOUTH_TEEN("teen", "🧒 Youth / Teen 11–19yr (Balanced Spry Proportions) 🧑", "🧒 Teen (11–19y)", 1.02f, 0.94f, 0.94f),
    ADULT_REGULAR("adult", "🧑 Adult 20–45yr (Natural Look-Alike Proportions) 👩👨", "🧑 Adult (20–45y)", 0.96f, 1.08f, 1.06f),
    SENIOR_ELDER("senior", "🧓 Elder / Senior 50+yr (Warm Classic Proportions) 🧓", "🧓 Senior (50+y)", 0.96f, 1.00f, 0.96f);

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: ADULT_REGULAR
    }
}

enum class BodyBuild(
    val id: String,
    val label: String,
    val defaultWidthScale: Float,
    val defaultShoulderScale: Float,
    val bellyCurve: Float,
    val limbThicknessScale: Float,
    val faceWidthScale: Float,
) {
    SLIM("slim", "Slim / Lean Build 🏃", 0.80f, 0.85f, 0.0f, 0.82f, 0.90f),
    MID_BODY("mid_body", "Mid-Body / Regular 35yr Build 👨", 1.06f, 1.08f, 0.18f, 1.04f, 1.00f),
    ATHLETIC_BROAD("athletic", "Athletic / Broad Shoulders 💪", 0.96f, 1.30f, 0.0f, 1.18f, 1.02f),
    BULKY_HEAVY("bulky", "Bulky / Heavy Build 🏋️", 1.40f, 1.32f, 0.52f, 1.32f, 1.16f);

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: MID_BODY
    }
}

enum class FaceShape(val id: String, val label: String) {
    OVAL_BALANCED("oval", "Balanced Oval Face"),
    ROUND_FULL("round", "Round / Full Cheeks"),
    SQUARE_JAW("square", "Strong / Square Jawline"),
    HEART_CHEEKS("heart", "Soft / Cute Cheeks");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: OVAL_BALANCED
    }
}

enum class FacialHairStyle(val id: String, val label: String) {
    NONE("none", "Clean Shaven / No Facial Hair"),
    MOUSTACHE_CLASSIC("moustache", "Classic Moustache 👨"),
    STUBBLE_3DAY("stubble", "3-Day Stubble + Moustache"),
    SHORT_BOXED_BEARD("short_beard", "Short Boxed Beard & Moustache (35yr Look) 🧔"),
    FULL_BEARD_MADRAS("full_beard", "Full Heavy Beard & Handlebar Moustache 🧔‍♂️"),
    GOATEE_SOUL("goatee", "Goatee & Moustache");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: NONE
    }
}

enum class EyeStyle(val id: String, val label: String) {
    BOLD_MASCULINE("bold", "Bold Brows & Expressive Almond Eyes"),
    NATURAL_WARM("natural", "Warm Balanced Eyes & Brows"),
    SPARKLE_CUTE("sparkle", "Big Sparkly Eyes & Curved Lashes");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: BOLD_MASCULINE
    }
}

enum class FaceMode(val id: String, val label: String) {
    CHIBI_TWIN("chibi", "🎨 Modeled 2D Look-Alike Face (68-Pt Facecut & Feature Model — Default)"),
    HD_TOON_PORTRAIT("hd_toon", "✨ Cel-Shaded Toon Portrait (Posterized Vector-Toon Head)"),
    REAL_FACE_CUTOUT("real_face", "📷 Segmented Photo Head (Optional Real Photo Cutout)"),
    FULL_PHOTO_PUPPET("photo_puppet", "📸 Full Photo Puppet (Photo Head + Shirt Texture)");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: CHIBI_TWIN
    }
}

enum class HairStyle(val id: String, val label: String, val isFemaleStyle: Boolean = false) {
    CURLY_WAVY_CROP("wavy_crop", "Wavy / Curly Medium Crop 🌊", false),
    SIDE_PART_CLASSIC("side_part", "Men's Classic Side-Part 💇‍♂️", false),
    MODERN_QUIF_FADE("quiff_fade", "Modern Quiff / Fade 💈", false),
    SHORT_SPIKY("spiky", "Sporty Spiky Crop ⚡", false),
    BALD_OR_BUZZ("bald_buzz", "Clean Bald / Ultra-Short Buzz ✨", false),
    PIGTAILS_RIBBONS("pigtails", "Twin Pigtails & Ribbons 🎀", true),
    TWO_BRAIDS_JASMINE("braids", "Twin Braids & Mallipoo 🌸", true),
    LONG_WAVY("long_wavy", "Long Flowing Wavy Hair 👩", true),
    BOB_CUTE("bob", "Shoulder Fringe Bob 💇‍♀️", true),
    CURLY_PUFF("curly", "Voluminous Curly Puff ☁️", true);

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: CURLY_WAVY_CROP
    }
}

enum class OutfitStyle(val id: String, val label: String) {
    TSHIRT_CARGO("tshirt_cargo", "Crew-Neck T-Shirt & Trousers/Jeans 👕"),
    CASUAL_SHIRT_JEANS("shirt_jeans", "Collared Shirt / Polo & Jeans 👔👖"),
    VESHTI_SHIRT("veshti_shirt", "South Indian White Veshti (Zari) & Shirt 🪔"),
    HOODIE_JEANS("hoodie", "Cozy Hoodie & Sneakers 👟"),
    KURTA_SET("kurta", "Festive Kurta Set ✨"),
    PATTU_PAVADAI("pattu_pavadai", "Silk Pattu Pavadai & Gold Zari 👧"),
    FROCK_DRESS("frock", "Cute Flared Frock & Bow 👗"),
    SUPER_SUIT("super_suit", "🦸 Zoom Hero Body (Cape + Lightning Emblem)"),
    IRON_ARMOUR_SUIT("iron_armour", "🤖 Ironboy Armour Body (Chest Reactor + Jet Boots)"),
    NINJA_SHINOBI_SUIT("ninja_suit", "🥷 Shadow Ninja Body (Shinobi Gi + Katana)");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: TSHIRT_CARGO
    }
}

enum class FlightStyle(val id: String, val label: String, val locomotion: Set<Locomotion>) {
    WALK_AND_HOP("walk", "Walk & Bounce on Floor 🏃", setOf(Locomotion.WALK)),
    SUPERBOY_CAPE_FLY("superboy_fly", "Fly & Hover like Zoom (Cape + Sky Loops) 🦸", setOf(Locomotion.WALK, Locomotion.FLY, Locomotion.FLOAT)),
    IRONBOY_JET_FLY("ironboy_fly", "Jet Hover & Fly like Ironboy (Boot/Palm Jets) 🚀🔥", setOf(Locomotion.WALK, Locomotion.FLY, Locomotion.FLOAT)),
    CLIMB_WALLS("climb", "Edge Park & Hover Patrol 🧗", setOf(Locomotion.WALK, Locomotion.FLY, Locomotion.FLOAT)),
    ALL_HERO_MOVES("all_hero", "All Hero Moves (Hover + Cape + Jets + Sky Loops!) 🌟🦸🚀", setOf(Locomotion.WALK, Locomotion.FLY, Locomotion.FLOAT));

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: ALL_HERO_MOVES
    }
}

/**
 * On-Device 68-Point Facial Landmark & Biometric Contour Mesh (`FaceLandmarkMesh`).
 * Extracted offline from the uploaded photo to:
 * 1. Drive exact head/hair/jawline contour segmentation and 60fps eyelid/jaw alignment on `320×320` cutouts.
 * 2. Drive parametric vector caricature geometry (`jawWidthRatio`, `eyeSpacingRatio`, `browThicknessRatio`,
 *    `noseWidthRatio`, `mouthWidthRatio`, `hairCrownRatio`, `beardDensityRatio`).
 * 3. Render the iOS Face-ID style glowing 3D triangulated wireframe mesh overlay in the Studio Inspector!
 */
enum class MeshFeatureTarget(val id: String, val label: String, val shortLabel: String, val hint: String) {
    WHOLE_MESH("whole", "🌐 Whole Mesh", "🌐 All", "Swipe or use ◀▲▼▶ to align the entire 68-pt mesh over your face"),
    HAIRLINE_TOP("hairline", "💇 Hairline & Forehead", "💇 Hairline", "Swipe ▲/▼ to raise or lower the forehead hairline & top hair crown"),
    LEFT_BROW("left_brow", "🤨 Left Eyebrow", "🤨 L-Brow", "Swipe ▲/▼ to move eyebrow height & arch; ➖/➕ for brow thickness"),
    RIGHT_BROW("right_brow", "🤨 Right Eyebrow", "🤨 R-Brow", "Swipe ▲/▼ to move eyebrow height & arch; ➖/➕ for brow thickness"),
    LEFT_EYE("left_eye", "👁️ Left Eye", "👁️ L-Eye", "Swipe ◀/▶/▲/▼ to position eyes; ➖/➕ for eye openness"),
    RIGHT_EYE("right_eye", "👁️ Right Eye", "👁️ R-Eye", "Swipe ◀/▶/▲/▼ to position eyes; ➖/➕ for eye openness"),
    NOSE_TIP("nose", "👃 Nose Bridge & Tip", "👃 Nose", "Swipe ▲/▼/◀/▶ to align nose tip; ➖/➕ for nose wing width"),
    MOUTH("mouth", "👄 Mouth & Lips", "👄 Mouth", "Swipe ▲/▼/◀/▶ to align lips; ➖/➕ for mouth width & lip fullness"),
    JAW_LEFT("jaw_left", "🧔 Left Jaw & Cheek", "🧔 L-Jaw", "Swipe ◀/▶ or ➖/➕ to narrow or widen cheekbones & jawline"),
    JAW_RIGHT("jaw_right", "🧔 Right Jaw & Cheek", "🧔 R-Jaw", "Swipe ◀/▶ or ➖/➕ to narrow or widen cheekbones & jawline"),
    CHIN_BOTTOM("chin", "🧔 Chin Tip", "🧔 Chin", "Swipe ▲/▼ to adjust chin length onto your real chin edge");

    companion object {
        val HANDLE_TARGETS = listOf(
            HAIRLINE_TOP, LEFT_BROW, RIGHT_BROW, LEFT_EYE, RIGHT_EYE,
            NOSE_TIP, MOUTH, JAW_LEFT, JAW_RIGHT, CHIN_BOTTOM
        )
        val CHIP_TARGETS = listOf(
            WHOLE_MESH, HAIRLINE_TOP, LEFT_BROW, LEFT_EYE, NOSE_TIP, MOUTH, JAW_LEFT, CHIN_BOTTOM
        )
    }
}

data class FaceLandmarkMesh(
    val eyeSpacingRatio: Float = 0.40f,
    val eyeAspectRatio: Float = 0.36f,
    val browThicknessRatio: Float = 1.32f,
    val browArchRatio: Float = 0.10f,
    val noseWidthRatio: Float = 1.22f,
    val noseLengthRatio: Float = 1.06f,
    val jawWidthRatio: Float = 1.00f,
    val chinLengthRatio: Float = 1.00f,
    val mouthWidthRatio: Float = 1.00f,
    val hairCrownRatio: Float = 1.05f,
    val beardDensityRatio: Float = 0.82f,
    val headAspectWidthToHeight: Float = 0.78f,
    val foreheadHeightRatio: Float = 1.08f,
    val hairSideWidthRatio: Float = 1.00f,
    val cheekBeardCoverage: Float = 0.12f,
    val soulPatchStrength: Float = 0.90f,
    val meshOffsetX: Float = 0f,
    val meshOffsetY: Float = 0f,
    val meshScale: Float = 1.0f,
    val eyeYOffset: Float = 0f,
    val browYOffset: Float = 0f,
    val noseXOffset: Float = 0f,
    val noseYOffset: Float = 0f,
    val mouthXOffset: Float = 0f,
    val mouthYOffset: Float = 0f,
    val chinXOffset: Float = 0f,
    val lipFullnessRatio: Float = 1.0f,
) {
    /**
     * Generates the 68 normalized 2D facial landmark vertices `(nx, ny)` in `[-1f..+1f]`
     * calibrated to the `320×320` segmented head portrait coordinate system, incorporating
     * all 10 interactive anatomical feature offsets (`eyeYOffset`, `browYOffset`, `noseYOffset`,
     * `mouthYOffset`, `chinLengthRatio`, `jawWidthRatio`, `foreheadHeightRatio`, etc.).
     */
    fun landmarks68(): List<Pair<Float, Float>> {
        val pts = ArrayList<Pair<Float, Float>>(68)
        val sc = meshScale.coerceIn(0.70f, 1.38f)
        val ox = meshOffsetX.coerceIn(-0.35f, 0.35f)
        val oy = meshOffsetY.coerceIn(-0.35f, 0.35f)
        fun pt(x: Float, y: Float) = (ox + x * sc) to (oy + y * sc)

        val jw = (0.56f * jawWidthRatio).coerceIn(0.42f, 0.72f)
        val chY = (0.53f * chinLengthRatio).coerceIn(0.38f, 0.68f)
        val baseEy = -0.070f
        val ey = baseEy + eyeYOffset.coerceIn(-0.16f, 0.16f)

        // 1..17: Jawline & Chin Contour (17 points from left temple around actual chin to right temple)
        for (i in 0..16) {
            val u = (i - 8) / 8f // -1f .. +1f
            val ang = u * (PI.toFloat() * 0.48f)
            val chinShiftX = chinXOffset * (1f - u * u)
            val x = sin(ang) * jw * (1f - 0.10f * u * u) + chinShiftX
            val y = baseEy + cos(ang) * (chY - baseEy)
            pts.add(pt(x, y))
        }

        val ex = (eyeSpacingRatio * 0.86f).coerceIn(0.25f, 0.44f)
        val by = ey - 0.105f + browYOffset.coerceIn(-0.16f, 0.16f)

        // 18..22: Left Eyebrow Arch (5 points)
        for (i in 0..4) {
            val t = i / 4f
            val x = -ex - 0.11f + t * 0.22f
            val arch = sin(t * PI.toFloat()) * (0.032f + 0.048f * browArchRatio)
            pts.add(pt(x, by - arch))
        }

        // 23..27: Right Eyebrow Arch (5 points)
        for (i in 0..4) {
            val t = i / 4f
            val x = ex - 0.11f + t * 0.22f
            val arch = sin(t * PI.toFloat()) * (0.032f + 0.048f * browArchRatio)
            pts.add(pt(x, by - arch))
        }

        // 28..31: Nose Bridge (4 points)
        val nLen = (0.185f * noseLengthRatio).coerceIn(0.12f, 0.28f) + noseYOffset
        for (i in 0..3) {
            val t = i / 3f
            pts.add(pt(noseXOffset * t, baseEy + t * nLen))
        }

        // 32..36: Nose Wings & Tip (5 points)
        val nw = (0.11f * noseWidthRatio).coerceIn(0.07f, 0.18f)
        val nyTip = baseEy + nLen
        val nxTip = noseXOffset
        pts.add(pt(nxTip - nw, nyTip - 0.01f))
        pts.add(pt(nxTip - nw * 0.5f, nyTip + 0.01f))
        pts.add(pt(nxTip, nyTip + 0.018f))
        pts.add(pt(nxTip + nw * 0.5f, nyTip + 0.01f))
        pts.add(pt(nxTip + nw, nyTip - 0.01f))

        // 37..42: Left Eye Contour (6 points)
        val ew = 0.095f
        val eh = ew * eyeAspectRatio.coerceIn(0.32f, 0.65f)
        pts.add(pt(-ex - ew, ey))
        pts.add(pt(-ex - ew * 0.45f, ey - eh))
        pts.add(pt(-ex + ew * 0.45f, ey - eh))
        pts.add(pt(-ex + ew, ey))
        pts.add(pt(-ex + ew * 0.45f, ey + eh))
        pts.add(pt(-ex - ew * 0.45f, ey + eh))

        // 43..48: Right Eye Contour (6 points)
        pts.add(pt(ex - ew, ey))
        pts.add(pt(ex - ew * 0.45f, ey - eh))
        pts.add(pt(ex + ew * 0.45f, ey - eh))
        pts.add(pt(ex + ew, ey))
        pts.add(pt(ex + ew * 0.45f, ey + eh))
        pts.add(pt(ex - ew * 0.45f, ey + eh))

        // 49..60: Outer Mouth & Lip Loop (12 points)
        val mw = (0.19f * mouthWidthRatio).coerceIn(0.12f, 0.28f)
        val mh = 0.052f * lipFullnessRatio.coerceIn(0.65f, 1.55f)
        val my = baseEy + 0.340f + mouthYOffset.coerceIn(-0.16f, 0.16f)
        val mx = mouthXOffset.coerceIn(-0.14f, 0.14f)
        for (i in 0..11) {
            val ang = (i / 12f) * (2f * PI.toFloat())
            pts.add(pt(mx + cos(ang) * mw, my + sin(ang) * mh))
        }

        // 61..68: Forehead Hairline & Crown Contour (8 points)
        val hc = (0.48f * foreheadHeightRatio.coerceIn(0.72f, 1.42f)).coerceIn(0.36f, 0.72f)
        for (i in 0..7) {
            val t = i / 7f
            val ang = PI.toFloat() * (0.12f + 0.76f * t)
            pts.add(pt(-cos(ang) * jw * 0.95f, baseEy - 0.06f - sin(ang) * hc))
        }
        return pts
    }

    /** Returns the normalized `(nx, ny)` anchor coordinate for [target] in `[-1f..+1f]`. */
    fun featureAnchorNormalized(target: MeshFeatureTarget): Pair<Float, Float> {
        val pts = landmarks68()
        fun avg(i: Int, j: Int) = ((pts[i].first + pts[j].first) * 0.5f) to ((pts[i].second + pts[j].second) * 0.5f)
        return when (target) {
            MeshFeatureTarget.WHOLE_MESH -> pts[30]
            MeshFeatureTarget.HAIRLINE_TOP -> avg(63, 64)
            MeshFeatureTarget.LEFT_BROW -> pts[19]
            MeshFeatureTarget.RIGHT_BROW -> pts[24]
            MeshFeatureTarget.LEFT_EYE -> avg(37, 40)
            MeshFeatureTarget.RIGHT_EYE -> avg(43, 46)
            MeshFeatureTarget.NOSE_TIP -> pts[33]
            MeshFeatureTarget.MOUTH -> avg(51, 57)
            MeshFeatureTarget.JAW_LEFT -> pts[3]
            MeshFeatureTarget.JAW_RIGHT -> pts[13]
            MeshFeatureTarget.CHIN_BOTTOM -> pts[8]
        }
    }

    /** Returns the screen-space `(sx, sy)` coordinate for [target] inside [rect]. */
    fun featureAnchorScreen(target: MeshFeatureTarget, rect: RectF): Pair<Float, Float> {
        val (nx, ny) = featureAnchorNormalized(target)
        val rx = rect.width() * 0.462f
        val ry = rect.height() * 0.462f
        return (rect.centerX() + nx * rx) to (rect.centerY() + ny * ry)
    }

    /**
     * Hit-tests the 10 anatomical feature handles inside [rect] with a generous `32dp` magnetic touch radius!
     * Returns the closest [MeshFeatureTarget] within `32dp`, or null if outside.
     */
    fun hitTestFeatureHandle(touchX: Float, touchY: Float, rect: RectF, density: Float): MeshFeatureTarget? {
        val maxDist = 32f * density
        var bestTarget: MeshFeatureTarget? = null
        var bestDist = maxDist
        for (target in MeshFeatureTarget.HANDLE_TARGETS) {
            val (hx, hy) = featureAnchorScreen(target, rect)
            val dist = kotlin.math.hypot(touchX - hx, touchY - hy)
            if (dist <= bestDist) {
                bestDist = dist
                bestTarget = target
            }
        }
        return bestTarget
    }

    /**
     * Nudges or drags [target] by normalized `(dxNorm, dyNorm)` in photo-mesh coordinates and/or [dScale] width/size delta.
     * Rubber-spline interpolates the entire anatomical group AND drives 1:1 live character reshaping!
     */
    fun nudgeFeature(target: MeshFeatureTarget, dxNorm: Float, dyNorm: Float, dScale: Float = 0f): FaceLandmarkMesh {
        return when (target) {
            MeshFeatureTarget.WHOLE_MESH -> copy(
                meshOffsetX = (meshOffsetX + dxNorm).coerceIn(-0.35f, 0.35f),
                meshOffsetY = (meshOffsetY + dyNorm).coerceIn(-0.35f, 0.35f),
                meshScale = (meshScale * (1f + dScale)).coerceIn(0.70f, 1.38f),
            )
            MeshFeatureTarget.HAIRLINE_TOP -> copy(
                foreheadHeightRatio = (foreheadHeightRatio - dyNorm * 1.85f + dScale * 0.5f).coerceIn(0.72f, 1.42f),
                hairCrownRatio = (hairCrownRatio - dyNorm * 1.5f + dScale * 0.5f).coerceIn(0.75f, 1.42f),
            )
            MeshFeatureTarget.LEFT_BROW, MeshFeatureTarget.RIGHT_BROW -> copy(
                browYOffset = (browYOffset + dyNorm).coerceIn(-0.16f, 0.16f),
                browArchRatio = (browArchRatio - dyNorm * 1.3f + dScale * 0.35f).coerceIn(0.02f, 0.55f),
                browThicknessRatio = (browThicknessRatio + dScale * 0.7f).coerceIn(0.70f, 1.65f),
            )
            MeshFeatureTarget.LEFT_EYE -> copy(
                eyeYOffset = (eyeYOffset + dyNorm).coerceIn(-0.16f, 0.16f),
                eyeSpacingRatio = (eyeSpacingRatio - dxNorm * 0.90f + dScale * 0.10f).coerceIn(0.30f, 0.48f),
                eyeAspectRatio = (eyeAspectRatio + dScale * 0.20f).coerceIn(0.32f, 0.65f),
            )
            MeshFeatureTarget.RIGHT_EYE -> copy(
                eyeYOffset = (eyeYOffset + dyNorm).coerceIn(-0.16f, 0.16f),
                eyeSpacingRatio = (eyeSpacingRatio + dxNorm * 0.90f + dScale * 0.10f).coerceIn(0.30f, 0.48f),
                eyeAspectRatio = (eyeAspectRatio + dScale * 0.20f).coerceIn(0.32f, 0.65f),
            )
            MeshFeatureTarget.NOSE_TIP -> copy(
                noseXOffset = (noseXOffset + dxNorm).coerceIn(-0.14f, 0.14f),
                noseYOffset = (noseYOffset + dyNorm).coerceIn(-0.16f, 0.16f),
                noseLengthRatio = (noseLengthRatio + dyNorm * 1.4f).coerceIn(0.78f, 1.38f),
                noseWidthRatio = (noseWidthRatio + dScale * 0.65f).coerceIn(0.72f, 1.55f),
            )
            MeshFeatureTarget.MOUTH -> copy(
                mouthXOffset = (mouthXOffset + dxNorm).coerceIn(-0.14f, 0.14f),
                mouthYOffset = (mouthYOffset + dyNorm).coerceIn(-0.16f, 0.16f),
                mouthWidthRatio = (mouthWidthRatio + dScale * 0.65f).coerceIn(0.72f, 1.45f),
                lipFullnessRatio = (lipFullnessRatio + dScale * 0.45f).coerceIn(0.70f, 1.50f),
            )
            MeshFeatureTarget.JAW_LEFT -> copy(
                jawWidthRatio = (jawWidthRatio - dxNorm * 1.65f + dScale * 0.55f).coerceIn(0.75f, 1.32f),
            )
            MeshFeatureTarget.JAW_RIGHT -> copy(
                jawWidthRatio = (jawWidthRatio + dxNorm * 1.65f + dScale * 0.55f).coerceIn(0.75f, 1.32f),
            )
            MeshFeatureTarget.CHIN_BOTTOM -> copy(
                chinXOffset = (chinXOffset + dxNorm).coerceIn(-0.14f, 0.14f),
                chinLengthRatio = (chinLengthRatio + dyNorm * 1.80f + dScale * 0.45f).coerceIn(0.76f, 1.32f),
            )
        }
    }

    /**
     * Draws the 68 facial landmark vertices directly in `ChibiHumanRenderer` head-local space
     * (`rx`, `topY`, `botY`) mapped through [headMatrix] (`rig.head`) so the mesh aligns 100% pixel-for-pixel
     * with the drawn 2D character's eyes, eyebrows, nose, lips, jawline, and hairline at ANY zoom or pan!
     */
    fun drawOnCharacterHead(
        c: Canvas,
        headMatrix: android.graphics.Matrix,
        rx: Float,
        topY: Float,
        botY: Float,
        scanPhase: Float = 0.5f,
        density: Float = 2f,
        selectedTarget: MeshFeatureTarget? = null,
    ) {
        val pts = ArrayList<Pair<Float, Float>>(68)
        val baseEy = -39.5f
        val ey = baseEy + eyeYOffset * 42f
        val ex = (28.5f * eyeSpacingRatio).coerceIn(9.4f, 14.2f)
        val jawW = rx * 0.88f

        // 1..17: Jawline & Chin Contour matching ChibiHumanRenderer headShapePath
        for (i in 0..16) {
            val u = (i - 8) / 8f
            val ang = u * (PI.toFloat() * 0.48f)
            val x = sin(ang) * jawW * (1f - 0.08f * u * u) + chinXOffset * 28f * (1f - u * u)
            val y = baseEy + cos(ang) * (botY - baseEy)
            pts.add(x to y)
        }
        // 18..22: Left Eyebrow
        val by = -47.2f + (eyeYOffset + browYOffset) * 42f
        val arch = browArchRatio * 3.5f
        for (i in 0..4) {
            val t = i / 4f
            val x = -ex - 6.8f + t * 14.6f
            pts.add(x to (by - sin(t * PI.toFloat()) * (1.8f + arch)))
        }
        // 23..27: Right Eyebrow
        for (i in 0..4) {
            val t = i / 4f
            val x = ex - 6.8f + t * 14.6f
            pts.add(x to (by - sin(t * PI.toFloat()) * (1.8f + arch)))
        }
        // 28..31: Nose Bridge
        val nxOff = noseXOffset * 28f
        val nyTip = -28.0f + noseYOffset * 42f + (noseLengthRatio - 1.06f) * 14f
        for (i in 0..3) {
            val t = i / 3f
            pts.add((nxOff * t) to (-38.0f + t * (nyTip - (-38.0f))))
        }
        // 32..36: Nose Wings & Tip
        val nw = (4.2f * noseWidthRatio).coerceIn(3.0f, 6.2f)
        pts.add((nxOff - nw) to (nyTip - 0.5f))
        pts.add((nxOff - nw * 0.5f) to (nyTip + 1.5f))
        pts.add(nxOff to (nyTip + 2.2f))
        pts.add((nxOff + nw * 0.5f) to (nyTip + 1.5f))
        pts.add((nxOff + nw) to (nyTip - 0.5f))
        // 37..42: Left Eye
        val ew = 6.2f
        val eh = 3.2f * (eyeAspectRatio / 0.42f).coerceIn(0.75f, 1.45f)
        pts.add((-ex - ew) to ey)
        pts.add((-ex - ew * 0.45f) to (ey - eh))
        pts.add((-ex + ew * 0.45f) to (ey - eh))
        pts.add((-ex + ew) to ey)
        pts.add((-ex + ew * 0.45f) to (ey + eh))
        pts.add((-ex - ew * 0.45f) to (ey + eh))
        // 43..48: Right Eye
        pts.add((ex - ew) to ey)
        pts.add((ex - ew * 0.45f) to (ey - eh))
        pts.add((ex + ew * 0.45f) to (ey - eh))
        pts.add((ex + ew) to ey)
        pts.add((ex + ew * 0.45f) to (ey + eh))
        pts.add((ex - ew * 0.45f) to (ey + eh))
        // 49..60: Mouth
        val mw = (6.8f * mouthWidthRatio).coerceIn(4.8f, 9.6f)
        val mh = 2.2f * lipFullnessRatio.coerceIn(0.70f, 1.50f)
        val mx = mouthXOffset * 28f
        val my = -17.5f + mouthYOffset * 42f
        for (i in 0..11) {
            val ang = (i / 12f) * (2f * PI.toFloat())
            pts.add((mx + cos(ang) * mw) to (my + sin(ang) * mh))
        }
        // 61..68: Hairline & Crown (topY)
        val hlShift = (foreheadHeightRatio - 1.08f) * 18f
        for (i in 0..7) {
            val t = i / 7f
            val ang = PI.toFloat() * (0.12f + 0.76f * t)
            pts.add((-cos(ang) * rx * 0.94f) to (baseEy - sin(ang) * (baseEy - (topY - hlShift))))
        }

        val mapped = FloatArray(pts.size * 2)
        for (i in pts.indices) {
            mapped[i * 2] = pts[i].first
            mapped[i * 2 + 1] = pts[i].second
        }
        headMatrix.mapPoints(mapped)
        fun sx(i: Int) = mapped[i * 2]
        fun sy(i: Int) = mapped[i * 2 + 1]

        drawMapped68Points(c, ::sx, ::sy, mapped[0], mapped[16 * 2], mapped[63 * 2 + 1], mapped[8 * 2 + 1], scanPhase, density, selectedTarget)
    }

    /** Draws the glowing iOS Face-ID style triangulated wireframe mesh + 68 nodes + 10 Feature Grab Handles over [rect]. */
    fun drawFaceIdWireframe(
        c: Canvas,
        rect: RectF,
        scanPhase: Float = 0.5f,
        density: Float = 2f,
        selectedTarget: MeshFeatureTarget? = null,
    ) {
        val pts = landmarks68()
        val cx = rect.centerX()
        val cy = rect.centerY()
        val rx = rect.width() * 0.462f
        val ry = rect.height() * 0.462f
        fun sx(i: Int) = cx + pts[i].first * rx
        fun sy(i: Int) = cy + pts[i].second * ry
        drawMapped68Points(c, ::sx, ::sy, sx(0), sx(16), sy(63), sy(8), scanPhase, density, selectedTarget)
    }

    private fun drawMapped68Points(
        c: Canvas,
        sx: (Int) -> Float,
        sy: (Int) -> Float,
        leftX: Float,
        rightX: Float,
        topY: Float,
        botY: Float,
        scanPhase: Float,
        density: Float,
        selectedTarget: MeshFeatureTarget? = null,
    ) {
        val wireP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.1f * density
            color = Color.argb(145, 0x00, 0xE5, 0xFF)
        }
        val contourP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.7f * density
            color = Color.argb(215, 0x00, 0xE6, 0x76)
        }
        val nodeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        val edges = arrayOf(
            0 to 17, 2 to 36, 4 to 31, 5 to 48, 8 to 57, 11 to 54, 12 to 35, 14 to 45, 16 to 26,
            17 to 36, 21 to 27, 22 to 27, 26 to 45, 36 to 31, 39 to 28, 42 to 28, 45 to 35,
            31 to 48, 33 to 51, 35 to 54, 19 to 62, 24 to 65, 0 to 60, 16 to 67
        )
        for ((a, b) in edges) {
            c.drawLine(sx(a), sy(a), sx(b), sy(b), wireP)
        }

        fun drawRange(start: Int, end: Int, closed: Boolean = false) {
            val path = Path()
            path.moveTo(sx(start), sy(start))
            for (i in (start + 1)..end) path.lineTo(sx(i), sy(i))
            if (closed) path.close()
            c.drawPath(path, contourP)
        }
        drawRange(0, 16)
        drawRange(17, 21)
        drawRange(22, 26)
        drawRange(27, 30)
        drawRange(31, 35)
        drawRange(36, 41, closed = true)
        drawRange(42, 47, closed = true)
        drawRange(48, 59, closed = true)
        drawRange(60, 67)

        for (i in 0 until 68) {
            c.drawCircle(sx(i), sy(i), 1.8f * density, nodeP)
        }

        // Draw the 10 glowing anatomical Feature Grab Handles so the user sees where to touch/drag!
        val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val handleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f * density }
        fun handlePt(t: MeshFeatureTarget): Pair<Float, Float> = when (t) {
            MeshFeatureTarget.WHOLE_MESH -> sx(30) to sy(30)
            MeshFeatureTarget.HAIRLINE_TOP -> ((sx(63) + sx(64)) * 0.5f) to ((sy(63) + sy(64)) * 0.5f)
            MeshFeatureTarget.LEFT_BROW -> sx(19) to sy(19)
            MeshFeatureTarget.RIGHT_BROW -> sx(24) to sy(24)
            MeshFeatureTarget.LEFT_EYE -> ((sx(37) + sx(40)) * 0.5f) to ((sy(37) + sy(40)) * 0.5f)
            MeshFeatureTarget.RIGHT_EYE -> ((sx(43) + sx(46)) * 0.5f) to ((sy(43) + sy(46)) * 0.5f)
            MeshFeatureTarget.NOSE_TIP -> sx(33) to sy(33)
            MeshFeatureTarget.MOUTH -> ((sx(51) + sx(57)) * 0.5f) to ((sy(51) + sy(57)) * 0.5f)
            MeshFeatureTarget.JAW_LEFT -> sx(3) to sy(3)
            MeshFeatureTarget.JAW_RIGHT -> sx(13) to sy(13)
            MeshFeatureTarget.CHIN_BOTTOM -> sx(8) to sy(8)
        }
        for (target in MeshFeatureTarget.HANDLE_TARGETS) {
            val (hx, hy) = handlePt(target)
            val isSel = selectedTarget == target || selectedTarget == MeshFeatureTarget.WHOLE_MESH
            val r = (if (selectedTarget == target) 6.5f else 4.2f) * density
            handleFill.color = if (isSel) Color.argb(230, 0xFF, 0xD5, 0x4F) else Color.argb(190, 0x00, 0xE5, 0xFF)
            handleRing.color = if (isSel) Color.WHITE else Color.rgb(0x00, 0xE6, 0x76)
            c.drawCircle(hx, hy, r, handleFill)
            c.drawCircle(hx, hy, r + 1.5f * density, handleRing)
        }

        val eyeLineY = (sy(36) + sy(45)) * 0.5f
        val scanY = topY + (scanPhase % 1f).coerceIn(0f, 1f) * (botY - topY)
        val scanP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * density
            color = Color.argb(185, 0x00, 0xE5, 0xFF)
        }
        c.drawLine(leftX - 6f * density, eyeLineY, rightX + 6f * density, eyeLineY, scanP)
        scanP.alpha = 110
        c.drawLine(leftX - 4f * density, scanY, rightX + 4f * density, scanY, scanP)
    }

    fun encode(): String = listOf(
        eyeSpacingRatio, eyeAspectRatio, browThicknessRatio, browArchRatio,
        noseWidthRatio, noseLengthRatio, jawWidthRatio, chinLengthRatio,
        mouthWidthRatio, hairCrownRatio, beardDensityRatio, headAspectWidthToHeight,
        foreheadHeightRatio, hairSideWidthRatio, cheekBeardCoverage, soulPatchStrength,
        meshOffsetX, meshOffsetY, meshScale,
        eyeYOffset, browYOffset, noseXOffset, noseYOffset, mouthXOffset, mouthYOffset, chinXOffset, lipFullnessRatio
    ).joinToString(",")

    companion object {
        fun decode(s: String?): FaceLandmarkMesh {
            if (s.isNullOrBlank()) return FaceLandmarkMesh()
            val v = s.split(',').mapNotNull { it.trim().toFloatOrNull() }
            if (v.size < 12) return FaceLandmarkMesh()
            return FaceLandmarkMesh(
                eyeSpacingRatio = v[0], eyeAspectRatio = v[1], browThicknessRatio = v[2], browArchRatio = v[3],
                noseWidthRatio = v[4], noseLengthRatio = v[5], jawWidthRatio = v[6], chinLengthRatio = v[7],
                mouthWidthRatio = v[8], hairCrownRatio = v[9], beardDensityRatio = v[10], headAspectWidthToHeight = v[11],
                foreheadHeightRatio = v.getOrElse(12) { 1.08f },
                hairSideWidthRatio = v.getOrElse(13) { 1.00f },
                cheekBeardCoverage = v.getOrElse(14) { 0.12f },
                soulPatchStrength = v.getOrElse(15) { 0.90f },
                meshOffsetX = v.getOrElse(16) { 0f },
                meshOffsetY = v.getOrElse(17) { 0f },
                meshScale = v.getOrElse(18) { 1.0f },
                eyeYOffset = v.getOrElse(19) { 0f },
                browYOffset = v.getOrElse(20) { 0f },
                noseXOffset = v.getOrElse(21) { 0f },
                noseYOffset = v.getOrElse(22) { 0f },
                mouthXOffset = v.getOrElse(23) { 0f },
                mouthYOffset = v.getOrElse(24) { 0f },
                chinXOffset = v.getOrElse(25) { 0f },
                lipFullnessRatio = v.getOrElse(26) { 1.0f },
            )
        }
    }
}

object CustomPowerCatalog {
    data class PowerOption(val id: String, val label: String, val power: Superpower, val aliases: Set<String> = emptySet())

    val OPTIONS: List<PowerOption> = listOf(
        PowerOption("ninja", "🥷 Ninja Mode (Smoke + Shadow Clones)", Superpower.NinjaSettai, setOf("ninja_mode")),
        PowerOption("kovam", "😡 Kovam / Angry Mode (Giant Fiery Stomp)", Superpower.KovamMode, setOf("angry", "angry_mode")),
        PowerOption("superboy_flight", "🦸 Zoom Sky Flight Swoop (Cape Loops)", HeroSwoop, setOf("swoop", "superboy")),
        PowerOption("ironboy_flight", "🚀 Ironboy Jet Thrusters & Palm Repulsors", ArmourJetsPower, setOf("jets", "ironboy", "armour_jets")),
        PowerOption("rocket", "🚀 Rocket Jetpack Boost", Superpower.RocketMonkey, setOf("rocket_monkey")),
        PowerOption("thunder", "⚡ Thunder Storm & Lightning Staff", Superpower.ThunderMonkey, setOf("lightning")),
        PowerOption("ice", "❄️ Ice Freeze & Snowflakes", Superpower.IceMonkey, setOf("frost")),
        PowerOption("magic", "🎩 Magic Hat & Notification Rabbit", Superpower.Magician, setOf("magician")),
        PowerOption("blur", "💨 Speed Blur (Supersonic Zip)", SpeedBlurPower, setOf("speed", "speed_blur")),
        PowerOption("freeze", "⏱️ Time Freeze (Slow-Mo Dodge)", Superpower.TimeFreeze, setOf("time_freeze")),
        PowerOption("laser", "👁️ Heat Vision Laser Beams", HeatVisionPower, setOf("heat_vision")),
        PowerOption("breath", "🌬️ Super Frost Breath", SuperBreathPower, setOf("super_breath")),
        PowerOption("lift", "💪 Super Strength Bus Lift", SuperStrengthPower, setOf("strength")),
        PowerOption("confetti", "🎉 Party Confetti Mini-Rockets", ConfettiRocketsPower, setOf("confetti_rockets")),
        PowerOption("shield", "🛡️ Glowing Force-Field Shield", ShieldBubblePower, setOf("shield_bubble")),
        PowerOption("super_banana", "🌟 Super Star Cape Flight", Superpower.SuperBanana, setOf("superbanana")),
        PowerOption("giant", "👀 Giant Screen-Edge Peek", Superpower.GiantMode, setOf("giant_mode")),
        PowerOption("lowpower", "🔋 Low-Battery Suit Drama", LowPowerPower, setOf("low_power")),
    )

    val ALL_POWER_IDS: Set<String> = OPTIONS.map { it.id }.toSet()
    val DEFAULT_POWER_IDS: Set<String> = ALL_POWER_IDS

    fun selectAllPowers(): Set<String> = ALL_POWER_IDS

    fun resolvePowers(selected: Set<String>): List<Superpower> {
        if (selected.isEmpty()) return listOf(Superpower.NinjaSettai, Superpower.KovamMode, HeroSwoop, ArmourJetsPower)
        if ("all" in selected || "*" in selected) return OPTIONS.map { it.power }
        val norm = selected.map { it.lowercase().trim() }.toSet()
        val matched = OPTIONS.filter { opt -> opt.id in norm || opt.power.id in norm || opt.aliases.any { it in norm } }.map { it.power }
        return matched.ifEmpty { OPTIONS.map { it.power } }
    }
}

data class CustomCompanionSpec(
    val id: String = "custom_1",
    val name: String = "Manik",
    val gender: CompanionGender = CompanionGender.MALE,
    val relationship: RelationshipPreset = RelationshipPreset.SELF_TWIN,
    val ageGroup: AgeGroup = AgeGroup.ADULT_REGULAR,
    val bodyBuild: BodyBuild = BodyBuild.MID_BODY,
    val bodyWidthScale: Float = BodyBuild.MID_BODY.defaultWidthScale,
    val heightScale: Float = AgeGroup.ADULT_REGULAR.torsoHeightScale,
    val shoulderScale: Float = BodyBuild.MID_BODY.defaultShoulderScale,
    val companionScale: Float = 1.25f,
    val faceOffsetX: Float = 0f,
    val faceOffsetY: Float = 0f,
    val faceZoomScale: Float = 1.0f,
    val faceMesh: FaceLandmarkMesh = FaceLandmarkMesh(),
    val faceShape: FaceShape = FaceShape.SQUARE_JAW,
    val facialHair: FacialHairStyle = FacialHairStyle.SHORT_BOXED_BEARD,
    val eyeStyle: EyeStyle = EyeStyle.BOLD_MASCULINE,
    val faceMode: FaceMode = FaceMode.CHIBI_TWIN,
    val hairStyle: HairStyle = HairStyle.CURLY_WAVY_CROP,
    val outfitStyle: OutfitStyle = OutfitStyle.TSHIRT_CARGO,
    val flightStyle: FlightStyle = FlightStyle.ALL_HERO_MOVES,
    val skinColor: Int = Color.rgb(0xC6, 0x8E, 0x68),
    val hairColor: Int = Color.rgb(0x1C, 0x16, 0x14),
    val outfitColor: Int = Color.rgb(0xE6, 0xD2, 0x4A),
    val accentColor: Int = Color.rgb(0xFF, 0xC1, 0x07),
    val pantsColor: Int = Color.rgb(0x26, 0x32, 0x38),
    val hasBindi: Boolean = false,
    val hairBow: Boolean = false,
    val jasmine: Boolean = false,
    val glasses: Boolean = false,
    val wings: Boolean = false,
    val cape: Boolean = false,
    val selectedPowers: Set<String> = CustomPowerCatalog.ALL_POWER_IDS,
    val facePortraitBase64: String = "",
    val faceGridColors: List<Int> = emptyList(),
    val torsoGridColors: List<Int> = emptyList(),
    val analysisSummary: String = "68-Pt Face-ID Mesh · 35yr Adult · Mid-Body · Short Boxed Beard & Moustache · Curly Hair · Yellow Tee",
    val bodyConfidence: Float = 0.90f,
    val bodyDetectedClearly: Boolean = true,
    val suggestionPrompt: String = "✅ 68-Pt Face-ID Mesh & Body analysed! Use the 1x–5x Zoom & Split-Screen Compare above, or tap any chip below to adjust:",
    val playfulness: Float = 0.90f,
    val chattiness: Float = 0.80f,
    val curiosity: Float = 0.85f,
    val energy: Float = 0.85f,
    val boldness: Float = 0.85f,
    val drama: Float = 0.75f,
) {
    fun applyGenderAndAge(newGender: CompanionGender = this.gender, newAge: AgeGroup? = null): CustomCompanionSpec {
        val defaultNames = setOf("manik", "mani", "priya", "kuttyma", "chottu", "bala", "karthik")
        val isPlaceholderName = name.isBlank() || name.lowercase().trim() in defaultNames
        val targetAgeForHair = newAge ?: ageGroup
        val cleanHairColor = run {
            val r = Color.red(hairColor); val g = Color.green(hairColor); val b = Color.blue(hairColor)
            val lum = (r * 3 + g * 4 + b) / 8
            if (targetAgeForHair != AgeGroup.SENIOR_ELDER && lum > 55) Color.rgb(0x22, 0x18, 0x15) else hairColor
        }
        return when (newGender) {
            CompanionGender.GIRL_CHILD -> {
                val targetAge = newAge ?: AgeGroup.CHILD_KUTTY
                val nextHair = if (hairStyle.isFemaleStyle) hairStyle else HairStyle.PIGTAILS_RIBBONS
                val nextOutfit = if (outfitStyle in setOf(OutfitStyle.PATTU_PAVADAI, OutfitStyle.FROCK_DRESS, OutfitStyle.SUPER_SUIT, OutfitStyle.IRON_ARMOUR_SUIT, OutfitStyle.NINJA_SHINOBI_SUIT)) {
                    outfitStyle
                } else OutfitStyle.PATTU_PAVADAI
                copy(
                    name = if (isPlaceholderName) "Kuttyma" else name,
                    gender = CompanionGender.GIRL_CHILD,
                    ageGroup = targetAge,
                    hairColor = cleanHairColor,
                    relationship = RelationshipPreset.DAUGHTER_KID,
                    bodyBuild = BodyBuild.SLIM,
                    bodyWidthScale = 0.88f,
                    heightScale = targetAge.torsoHeightScale,
                    shoulderScale = 0.88f,
                    facialHair = FacialHairStyle.NONE,
                    faceShape = FaceShape.HEART_CHEEKS,
                    eyeStyle = EyeStyle.SPARKLE_CUTE,
                    hairStyle = nextHair,
                    outfitStyle = nextOutfit,
                    hasBindi = true,
                    hairBow = nextHair == HairStyle.PIGTAILS_RIBBONS,
                    jasmine = nextHair == HairStyle.TWO_BRAIDS_JASMINE || nextOutfit == OutfitStyle.PATTU_PAVADAI,
                    faceMesh = faceMesh.copy(
                        eyeAspectRatio = 0.54f,
                        browThicknessRatio = 0.92f,
                        browArchRatio = 0.26f,
                        jawWidthRatio = 0.90f,
                        beardDensityRatio = 0f,
                        cheekBeardCoverage = 0f,
                        soulPatchStrength = 0f,
                    ),
                    analysisSummary = "68-Pt Face-ID Mesh · Girl (${targetAge.shortLabel}) · Clean Face · ${nextHair.label.substringBefore(" ")} · ${nextOutfit.label.substringBefore(" (")}",
                )
            }
            CompanionGender.BOY_CHILD -> {
                val targetAge = newAge ?: AgeGroup.CHILD_KUTTY
                val nextHair = if (!hairStyle.isFemaleStyle && hairStyle != HairStyle.BALD_OR_BUZZ) hairStyle else HairStyle.SHORT_SPIKY
                val nextOutfit = if (outfitStyle in setOf(OutfitStyle.TSHIRT_CARGO, OutfitStyle.HOODIE_JEANS, OutfitStyle.CASUAL_SHIRT_JEANS, OutfitStyle.SUPER_SUIT, OutfitStyle.IRON_ARMOUR_SUIT, OutfitStyle.NINJA_SHINOBI_SUIT)) {
                    outfitStyle
                } else OutfitStyle.TSHIRT_CARGO
                copy(
                    name = if (isPlaceholderName) "Chottu" else name,
                    gender = CompanionGender.BOY_CHILD,
                    ageGroup = targetAge,
                    hairColor = cleanHairColor,
                    relationship = RelationshipPreset.DAUGHTER_KID,
                    bodyBuild = BodyBuild.SLIM,
                    bodyWidthScale = 0.90f,
                    heightScale = targetAge.torsoHeightScale,
                    shoulderScale = 0.92f,
                    facialHair = FacialHairStyle.NONE,
                    faceShape = FaceShape.ROUND_FULL,
                    eyeStyle = EyeStyle.NATURAL_WARM,
                    hairStyle = nextHair,
                    outfitStyle = nextOutfit,
                    hasBindi = false,
                    hairBow = false,
                    jasmine = false,
                    faceMesh = faceMesh.copy(
                        eyeAspectRatio = 0.50f,
                        browThicknessRatio = 1.00f,
                        browArchRatio = 0.14f,
                        jawWidthRatio = 0.94f,
                        beardDensityRatio = 0f,
                        cheekBeardCoverage = 0f,
                        soulPatchStrength = 0f,
                    ),
                    analysisSummary = "68-Pt Face-ID Mesh · Boy (${targetAge.shortLabel}) · Clean Face · ${nextHair.label.substringBefore(" ")} · ${nextOutfit.label.substringBefore(" (")}",
                )
            }
            CompanionGender.FEMALE -> {
                val targetAge = newAge ?: (if (ageGroup == AgeGroup.CHILD_KUTTY) AgeGroup.ADULT_REGULAR else ageGroup)
                val nextHair = if (hairStyle.isFemaleStyle) hairStyle else HairStyle.LONG_WAVY
                val nextOutfit = if (outfitStyle in setOf(OutfitStyle.KURTA_SET, OutfitStyle.FROCK_DRESS, OutfitStyle.PATTU_PAVADAI, OutfitStyle.HOODIE_JEANS, OutfitStyle.SUPER_SUIT, OutfitStyle.IRON_ARMOUR_SUIT, OutfitStyle.NINJA_SHINOBI_SUIT)) {
                    outfitStyle
                } else OutfitStyle.KURTA_SET
                val nextBuild = if (bodyBuild == BodyBuild.BULKY_HEAVY) BodyBuild.MID_BODY else bodyBuild
                copy(
                    name = if (isPlaceholderName) "Priya" else name,
                    gender = CompanionGender.FEMALE,
                    ageGroup = targetAge,
                    hairColor = cleanHairColor,
                    relationship = if (relationship == RelationshipPreset.DAUGHTER_KID) RelationshipPreset.SELF_TWIN else relationship,
                    bodyBuild = nextBuild,
                    bodyWidthScale = nextBuild.defaultWidthScale * 0.92f,
                    heightScale = targetAge.torsoHeightScale,
                    shoulderScale = nextBuild.defaultShoulderScale * 0.90f,
                    facialHair = FacialHairStyle.NONE,
                    faceShape = FaceShape.HEART_CHEEKS,
                    eyeStyle = EyeStyle.SPARKLE_CUTE,
                    hairStyle = nextHair,
                    outfitStyle = nextOutfit,
                    hasBindi = true,
                    hairBow = false,
                    jasmine = nextHair == HairStyle.TWO_BRAIDS_JASMINE,
                    faceMesh = faceMesh.copy(
                        eyeAspectRatio = 0.46f,
                        browThicknessRatio = 0.95f,
                        browArchRatio = 0.28f,
                        noseWidthRatio = 0.96f,
                        jawWidthRatio = 0.90f,
                        beardDensityRatio = 0f,
                        cheekBeardCoverage = 0f,
                        soulPatchStrength = 0f,
                    ),
                    analysisSummary = "68-Pt Face-ID Mesh · Woman (${targetAge.shortLabel}) · Expressive Lashes · ${nextHair.label.substringBefore(" ")} · ${nextOutfit.label.substringBefore(" (")}",
                )
            }
            CompanionGender.MALE -> {
                val targetAge = newAge ?: (if (ageGroup == AgeGroup.CHILD_KUTTY) AgeGroup.ADULT_REGULAR else ageGroup)
                if (targetAge == AgeGroup.CHILD_KUTTY) {
                    return applyGenderAndAge(CompanionGender.BOY_CHILD, AgeGroup.CHILD_KUTTY)
                }
                val nextHair = if (hairStyle.isFemaleStyle) HairStyle.CURLY_WAVY_CROP else hairStyle
                val nextOutfit = if (outfitStyle in setOf(OutfitStyle.PATTU_PAVADAI, OutfitStyle.FROCK_DRESS)) OutfitStyle.TSHIRT_CARGO else outfitStyle
                val nextBeard = if (targetAge == AgeGroup.YOUTH_TEEN) FacialHairStyle.NONE else if (facialHair == FacialHairStyle.NONE) FacialHairStyle.SHORT_BOXED_BEARD else facialHair
                copy(
                    name = if (isPlaceholderName) "Manik" else name,
                    gender = CompanionGender.MALE,
                    ageGroup = targetAge,
                    hairColor = cleanHairColor,
                    relationship = if (relationship == RelationshipPreset.DAUGHTER_KID) RelationshipPreset.SELF_TWIN else relationship,
                    heightScale = targetAge.torsoHeightScale,
                    facialHair = nextBeard,
                    faceShape = if (faceShape == FaceShape.HEART_CHEEKS) FaceShape.SQUARE_JAW else faceShape,
                    eyeStyle = EyeStyle.BOLD_MASCULINE,
                    hairStyle = nextHair,
                    outfitStyle = nextOutfit,
                    hasBindi = false,
                    hairBow = false,
                    jasmine = false,
                    analysisSummary = "68-Pt Face-ID Mesh · Man (${targetAge.shortLabel}) · ${bodyBuild.label.substringBefore(" ")} · ${nextBeard.label.substringBefore(" (")} · ${nextHair.label.substringBefore(" ")}",
                )
            }
        }
    }

    fun applyBodyTemplate(templateId: String): CustomCompanionSpec = when (templateId) {
        "superboy_body" -> copy(
            bodyBuild = BodyBuild.ATHLETIC_BROAD,
            bodyWidthScale = BodyBuild.ATHLETIC_BROAD.defaultWidthScale,
            shoulderScale = BodyBuild.ATHLETIC_BROAD.defaultShoulderScale,
            outfitStyle = OutfitStyle.SUPER_SUIT,
            outfitColor = Color.rgb(0x14, 0x9C, 0x96),
            accentColor = Color.rgb(0xF2, 0x7A, 0x1C),
            pantsColor = Color.rgb(0x0B, 0x5F, 0x5C),
            cape = true,
            flightStyle = FlightStyle.ALL_HERO_MOVES,
        )
        "ironboy_body" -> copy(
            bodyBuild = BodyBuild.ATHLETIC_BROAD,
            bodyWidthScale = 1.08f,
            shoulderScale = 1.32f,
            outfitStyle = OutfitStyle.IRON_ARMOUR_SUIT,
            outfitColor = Color.rgb(0xC6, 0x28, 0x28),
            accentColor = Color.rgb(0xFF, 0xC1, 0x07),
            pantsColor = Color.rgb(0x45, 0x5A, 0x64),
            cape = false,
            flightStyle = FlightStyle.ALL_HERO_MOVES,
        )
        "ninja_body" -> copy(
            bodyBuild = BodyBuild.SLIM,
            bodyWidthScale = 0.86f,
            shoulderScale = 1.02f,
            outfitStyle = OutfitStyle.NINJA_SHINOBI_SUIT,
            outfitColor = Color.rgb(0x26, 0x32, 0x38),
            accentColor = Color.rgb(0xE5, 0x39, 0x35),
            pantsColor = Color.rgb(0x1C, 0x26, 0x2B),
            cape = false,
            flightStyle = FlightStyle.ALL_HERO_MOVES,
        )
        "slim_body" -> copy(
            bodyBuild = BodyBuild.SLIM,
            bodyWidthScale = BodyBuild.SLIM.defaultWidthScale,
            shoulderScale = BodyBuild.SLIM.defaultShoulderScale,
        )
        "mid_body" -> copy(
            bodyBuild = BodyBuild.MID_BODY,
            bodyWidthScale = BodyBuild.MID_BODY.defaultWidthScale,
            shoulderScale = BodyBuild.MID_BODY.defaultShoulderScale,
        )
        "athletic_body" -> copy(
            bodyBuild = BodyBuild.ATHLETIC_BROAD,
            bodyWidthScale = BodyBuild.ATHLETIC_BROAD.defaultWidthScale,
            shoulderScale = BodyBuild.ATHLETIC_BROAD.defaultShoulderScale,
        )
        "bulky_body" -> copy(
            bodyBuild = BodyBuild.BULKY_HEAVY,
            bodyWidthScale = BodyBuild.BULKY_HEAVY.defaultWidthScale,
            shoulderScale = BodyBuild.BULKY_HEAVY.defaultShoulderScale,
            faceShape = FaceShape.ROUND_FULL,
        )
        else -> this
    }

    fun toJson(): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val powersStr = selectedPowers.joinToString(",") { "\"${esc(it)}\"" }
        val gridStr = faceGridColors.joinToString(",")
        val torsoStr = torsoGridColors.joinToString(",")
        return buildString {
            append("{")
            append("\"format\":\"settai_companion_v9\",")
            append("\"id\":\"${esc(id)}\",")
            append("\"name\":\"${esc(name)}\",")
            append("\"gender\":\"${gender.id}\",")
            append("\"relationship\":\"${relationship.id}\",")
            append("\"ageGroup\":\"${ageGroup.id}\",")
            append("\"bodyBuild\":\"${bodyBuild.id}\",")
            append("\"bodyWidthScale\":$bodyWidthScale,")
            append("\"heightScale\":$heightScale,")
            append("\"shoulderScale\":$shoulderScale,")
            append("\"companionScale\":$companionScale,")
            append("\"faceOffsetX\":$faceOffsetX,")
            append("\"faceOffsetY\":$faceOffsetY,")
            append("\"faceZoomScale\":$faceZoomScale,")
            append("\"faceMesh\":\"${faceMesh.encode()}\",")
            append("\"faceShape\":\"${faceShape.id}\",")
            append("\"facialHair\":\"${facialHair.id}\",")
            append("\"eyeStyle\":\"${eyeStyle.id}\",")
            append("\"faceMode\":\"${faceMode.id}\",")
            append("\"hairStyle\":\"${hairStyle.id}\",")
            append("\"outfitStyle\":\"${outfitStyle.id}\",")
            append("\"flightStyle\":\"${flightStyle.id}\",")
            append("\"skinColor\":$skinColor,")
            append("\"hairColor\":$hairColor,")
            append("\"outfitColor\":$outfitColor,")
            append("\"accentColor\":$accentColor,")
            append("\"pantsColor\":$pantsColor,")
            append("\"hasBindi\":$hasBindi,")
            append("\"hairBow\":$hairBow,")
            append("\"jasmine\":$jasmine,")
            append("\"glasses\":$glasses,")
            append("\"wings\":$wings,")
            append("\"cape\":$cape,")
            append("\"selectedPowers\":[$powersStr],")
            append("\"facePortraitBase64\":\"${esc(facePortraitBase64)}\",")
            append("\"faceGridColors\":[$gridStr],")
            append("\"torsoGridColors\":[$torsoStr],")
            append("\"analysisSummary\":\"${esc(analysisSummary)}\",")
            append("\"bodyConfidence\":$bodyConfidence,")
            append("\"bodyDetectedClearly\":$bodyDetectedClearly,")
            append("\"suggestionPrompt\":\"${esc(suggestionPrompt)}\",")
            append("\"playfulness\":$playfulness,")
            append("\"chattiness\":$chattiness,")
            append("\"curiosity\":$curiosity,")
            append("\"energy\":$energy,")
            append("\"boldness\":$boldness,")
            append("\"drama\":$drama")
            append("}")
        }
    }

    companion object {
        fun fromJson(json: String?): CustomCompanionSpec? {
            if (json.isNullOrBlank() || !json.contains("\"name\"")) return null
            fun str(key: String, def: String): String {
                val m = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(json) ?: return def
                return m.groupValues[1].replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\")
            }
            fun int(key: String, def: Int): Int =
                Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: def
            fun flt(key: String, def: Float): Float =
                Regex("\"$key\"\\s*:\\s*(-?[0-9.]+)").find(json)?.groupValues?.get(1)?.toFloatOrNull() ?: def
            fun bool(key: String, def: Boolean): Boolean =
                Regex("\"$key\"\\s*:\\s*(true|false)").find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: def

            val powersBlock = Regex("\"selectedPowers\"\\s*:\\s*\\[(.*?)]").find(json)?.groupValues?.get(1)
            val powers = if (powersBlock != null) {
                Regex("\"([^\"]+)\"").findAll(powersBlock).map { it.groupValues[1] }.toSet().ifEmpty { CustomPowerCatalog.ALL_POWER_IDS }
            } else CustomPowerCatalog.ALL_POWER_IDS

            val gridBlock = Regex("\"faceGridColors\"\\s*:\\s*\\[(.*?)]").find(json)?.groupValues?.get(1)
            val grid = if (!gridBlock.isNullOrBlank()) {
                gridBlock.split(',').mapNotNull { it.trim().toIntOrNull() }
            } else emptyList()

            val torsoBlock = Regex("\"torsoGridColors\"\\s*:\\s*\\[(.*?)]").find(json)?.groupValues?.get(1)
            val torsoGrid = if (!torsoBlock.isNullOrBlank()) {
                torsoBlock.split(',').mapNotNull { it.trim().toIntOrNull() }
            } else emptyList()

            val age = AgeGroup.of(str("ageGroup", "adult"))
            val build = BodyBuild.of(str("bodyBuild", "mid_body"))
            val hair = HairStyle.of(str("hairStyle", "wavy_crop"))
            val fh = FacialHairStyle.of(str("facialHair", "short_beard"))
            val defaultGender = when {
                age == AgeGroup.CHILD_KUTTY && hair.isFemaleStyle -> CompanionGender.GIRL_CHILD
                age == AgeGroup.CHILD_KUTTY -> CompanionGender.BOY_CHILD
                hair.isFemaleStyle && fh == FacialHairStyle.NONE -> CompanionGender.FEMALE
                else -> CompanionGender.MALE
            }
            val parsedGender = CompanionGender.of(str("gender", defaultGender.id))

            return CustomCompanionSpec(
                id = str("id", "custom_1").ifBlank { "custom_1" },
                name = str("name", "Manik").ifBlank { "Manik" },
                gender = parsedGender,
                relationship = RelationshipPreset.of(str("relationship", "self")),
                ageGroup = age,
                bodyBuild = build,
                bodyWidthScale = flt("bodyWidthScale", build.defaultWidthScale).coerceIn(0.65f, 1.65f),
                heightScale = flt("heightScale", age.torsoHeightScale).coerceIn(0.70f, 1.30f),
                shoulderScale = flt("shoulderScale", build.defaultShoulderScale).coerceIn(0.75f, 1.55f),
                companionScale = flt("companionScale", 1.25f).coerceIn(0.85f, 1.65f),
                faceOffsetX = flt("faceOffsetX", 0f).coerceIn(-24f, 24f),
                faceOffsetY = flt("faceOffsetY", 0f).coerceIn(-24f, 24f),
                faceZoomScale = flt("faceZoomScale", 1.0f).coerceIn(0.75f, 1.45f),
                faceMesh = FaceLandmarkMesh.decode(str("faceMesh", "")),
                faceShape = FaceShape.of(str("faceShape", "square")),
                facialHair = FacialHairStyle.of(str("facialHair", "short_beard")),
                eyeStyle = EyeStyle.of(str("eyeStyle", "bold")),
                faceMode = FaceMode.of(str("faceMode", "chibi")),
                hairStyle = HairStyle.of(str("hairStyle", "wavy_crop")),
                outfitStyle = OutfitStyle.of(str("outfitStyle", "tshirt_cargo")),
                flightStyle = FlightStyle.of(str("flightStyle", "all_hero")),
                skinColor = int("skinColor", Color.rgb(0xC6, 0x8E, 0x68)),
                hairColor = int("hairColor", Color.rgb(0x1C, 0x16, 0x14)).let { hc ->
                    val lum = (Color.red(hc) * 3 + Color.green(hc) * 4 + Color.blue(hc)) / 8
                    if (age != AgeGroup.SENIOR_ELDER && lum > 58) Color.rgb(0x22, 0x18, 0x15) else hc
                },
                outfitColor = int("outfitColor", Color.rgb(0xE6, 0xD2, 0x4A)),
                accentColor = int("accentColor", Color.rgb(0xFF, 0xC1, 0x07)),
                pantsColor = int("pantsColor", Color.rgb(0x26, 0x32, 0x38)),
                hasBindi = bool("hasBindi", false),
                hairBow = bool("hairBow", false),
                jasmine = bool("jasmine", false),
                glasses = bool("glasses", false),
                wings = bool("wings", false),
                cape = bool("cape", false),
                selectedPowers = powers,
                facePortraitBase64 = str("facePortraitBase64", ""),
                faceGridColors = grid,
                torsoGridColors = torsoGrid,
                analysisSummary = str("analysisSummary", "${age.label} · ${build.label}"),
                bodyConfidence = flt("bodyConfidence", 0.90f).coerceIn(0f, 1f),
                bodyDetectedClearly = bool("bodyDetectedClearly", true),
                suggestionPrompt = str("suggestionPrompt", "✅ Look-alike ready! Tap any chip below to fine-tune Body Build, Age, or Beard."),
                playfulness = flt("playfulness", 0.90f).coerceIn(0.05f, 1f),
                chattiness = flt("chattiness", 0.80f).coerceIn(0.05f, 1f),
                curiosity = flt("curiosity", 0.85f).coerceIn(0.05f, 1f),
                energy = flt("energy", 0.85f).coerceIn(0.05f, 1f),
                boldness = flt("boldness", 0.85f).coerceIn(0.05f, 1f),
                drama = flt("drama", 0.75f).coerceIn(0.05f, 1f),
            )
        }
    }
}

object CustomCompanionPresets {
    fun adult35MidBody(base: CustomCompanionSpec = CustomCompanionSpec()): CustomCompanionSpec = base.copy(
        name = "Manik",
        relationship = RelationshipPreset.SELF_TWIN,
        ageGroup = AgeGroup.ADULT_REGULAR,
        bodyBuild = BodyBuild.MID_BODY,
        bodyWidthScale = BodyBuild.MID_BODY.defaultWidthScale,
        heightScale = AgeGroup.ADULT_REGULAR.torsoHeightScale,
        shoulderScale = BodyBuild.MID_BODY.defaultShoulderScale,
        faceShape = FaceShape.SQUARE_JAW,
        facialHair = FacialHairStyle.SHORT_BOXED_BEARD,
        eyeStyle = EyeStyle.BOLD_MASCULINE,
        hairStyle = HairStyle.CURLY_WAVY_CROP,
        outfitStyle = OutfitStyle.TSHIRT_CARGO,
        skinColor = Color.rgb(0xC6, 0x8E, 0x68),
        hairColor = Color.rgb(0x1C, 0x16, 0x14),
        outfitColor = Color.rgb(0xE6, 0xD2, 0x4A),
        pantsColor = Color.rgb(0x26, 0x32, 0x38),
        accentColor = Color.rgb(0xFF, 0xC1, 0x07),
        hasBindi = false,
        hairBow = false,
        jasmine = false,
        glasses = false,
        wings = false,
        cape = false,
        analysisSummary = "68-Pt Face-ID Mesh · 35yr Adult · Mid-Body · Short Boxed Beard & Moustache · Curly Hair · Yellow Tee",
    )

    fun bulkyHeavyPerson(base: CustomCompanionSpec = CustomCompanionSpec()): CustomCompanionSpec = base.copy(
        name = "Bala",
        relationship = RelationshipPreset.BESTIE,
        ageGroup = AgeGroup.ADULT_REGULAR,
        bodyBuild = BodyBuild.BULKY_HEAVY,
        bodyWidthScale = BodyBuild.BULKY_HEAVY.defaultWidthScale,
        heightScale = AgeGroup.ADULT_REGULAR.torsoHeightScale,
        shoulderScale = BodyBuild.BULKY_HEAVY.defaultShoulderScale,
        faceShape = FaceShape.ROUND_FULL,
        facialHair = FacialHairStyle.FULL_BEARD_MADRAS,
        eyeStyle = EyeStyle.BOLD_MASCULINE,
        hairStyle = HairStyle.MODERN_QUIF_FADE,
        outfitStyle = OutfitStyle.VESHTI_SHIRT,
        skinColor = Color.rgb(0xCE, 0x92, 0x66),
        hairColor = Color.rgb(0x18, 0x12, 0x10),
        outfitColor = Color.rgb(0xC6, 0x28, 0x28),
        pantsColor = Color.rgb(0xFA, 0xF8, 0xF0),
        accentColor = Color.rgb(0xFF, 0xC1, 0x07),
        hasBindi = false,
        hairBow = false,
        jasmine = false,
        glasses = false,
        wings = false,
        cape = false,
        analysisSummary = "68-Pt Face-ID Mesh · Adult · Bulky Build (1.40x) · Full Beard & Moustache · Quiff · Veshti & Shirt",
    )

    fun slimPerson(base: CustomCompanionSpec = CustomCompanionSpec()): CustomCompanionSpec = base.copy(
        name = "Karthik",
        relationship = RelationshipPreset.BESTIE,
        ageGroup = AgeGroup.ADULT_REGULAR,
        bodyBuild = BodyBuild.SLIM,
        bodyWidthScale = BodyBuild.SLIM.defaultWidthScale,
        heightScale = 1.12f,
        shoulderScale = BodyBuild.SLIM.defaultShoulderScale,
        faceShape = FaceShape.OVAL_BALANCED,
        facialHair = FacialHairStyle.STUBBLE_3DAY,
        eyeStyle = EyeStyle.NATURAL_WARM,
        hairStyle = HairStyle.SIDE_PART_CLASSIC,
        outfitStyle = OutfitStyle.HOODIE_JEANS,
        skinColor = Color.rgb(0xE0, 0xA8, 0x7C),
        hairColor = Color.rgb(0x22, 0x18, 0x14),
        outfitColor = Color.rgb(0x00, 0x89, 0x7B),
        pantsColor = Color.rgb(0x1A, 0x23, 0x7E),
        accentColor = Color.rgb(0xFF, 0xD5, 0x4F),
        hasBindi = false,
        hairBow = false,
        jasmine = false,
        glasses = false,
        wings = false,
        cape = false,
        analysisSummary = "68-Pt Face-ID Mesh · Adult · Slim Build (0.80x) · Light Stubble · Side-Part · Hoodie & Jeans",
    )

    fun womanAdult(base: CustomCompanionSpec = CustomCompanionSpec()): CustomCompanionSpec = base.copy(
        name = "Priya",
        gender = CompanionGender.FEMALE,
        relationship = RelationshipPreset.SELF_TWIN,
        ageGroup = AgeGroup.ADULT_REGULAR,
        bodyBuild = BodyBuild.SLIM,
        bodyWidthScale = 0.86f,
        heightScale = AgeGroup.ADULT_REGULAR.torsoHeightScale,
        shoulderScale = 0.92f,
        faceShape = FaceShape.HEART_CHEEKS,
        facialHair = FacialHairStyle.NONE,
        eyeStyle = EyeStyle.SPARKLE_CUTE,
        hairStyle = HairStyle.LONG_WAVY,
        outfitStyle = OutfitStyle.KURTA_SET,
        skinColor = Color.rgb(0xE2, 0xAC, 0x84),
        hairColor = Color.rgb(0x1C, 0x14, 0x12),
        outfitColor = Color.rgb(0xAD, 0x14, 0x57),
        pantsColor = Color.rgb(0xFF, 0xC1, 0x07),
        accentColor = Color.rgb(0xFF, 0xD5, 0x4F),
        hasBindi = true,
        hairBow = false,
        jasmine = true,
        glasses = false,
        wings = false,
        cape = false,
        faceMesh = FaceLandmarkMesh(
            eyeSpacingRatio = 0.40f,
            eyeAspectRatio = 0.48f,
            browThicknessRatio = 0.92f,
            browArchRatio = 0.28f,
            noseWidthRatio = 0.94f,
            jawWidthRatio = 0.88f,
            beardDensityRatio = 0f,
            cheekBeardCoverage = 0f,
            soulPatchStrength = 0f,
        ),
        analysisSummary = "68-Pt Face-ID Mesh · Woman (Adult) · Long Flowing Hair · Curved Lashes · Festive Kurta",
    )

    fun child5YrBoy(base: CustomCompanionSpec = CustomCompanionSpec()): CustomCompanionSpec = base.copy(
        name = "Chottu",
        gender = CompanionGender.BOY_CHILD,
        relationship = RelationshipPreset.DAUGHTER_KID,
        ageGroup = AgeGroup.CHILD_KUTTY,
        bodyBuild = BodyBuild.SLIM,
        bodyWidthScale = 0.90f,
        heightScale = AgeGroup.CHILD_KUTTY.torsoHeightScale,
        shoulderScale = 0.92f,
        faceShape = FaceShape.ROUND_FULL,
        facialHair = FacialHairStyle.NONE,
        eyeStyle = EyeStyle.NATURAL_WARM,
        hairStyle = HairStyle.SHORT_SPIKY,
        outfitStyle = OutfitStyle.TSHIRT_CARGO,
        skinColor = Color.rgb(0xE2, 0xAA, 0x80),
        hairColor = Color.rgb(0x1E, 0x15, 0x12),
        outfitColor = Color.rgb(0x1E, 0x88, 0xE5),
        pantsColor = Color.rgb(0x26, 0x32, 0x38),
        accentColor = Color.rgb(0xFF, 0xCA, 0x28),
        hasBindi = false,
        hairBow = false,
        jasmine = false,
        glasses = false,
        wings = false,
        cape = false,
        faceMesh = FaceLandmarkMesh(
            eyeSpacingRatio = 0.41f,
            eyeAspectRatio = 0.50f,
            browThicknessRatio = 0.98f,
            browArchRatio = 0.14f,
            jawWidthRatio = 0.94f,
            beardDensityRatio = 0f,
            cheekBeardCoverage = 0f,
            soulPatchStrength = 0f,
        ),
        analysisSummary = "68-Pt Face-ID Mesh · 5yr Boy Child · Cute Kid Proportions · Spiky Crop · Blue Tee",
    )

    fun child5YrDaughter(base: CustomCompanionSpec = CustomCompanionSpec()): CustomCompanionSpec = base.copy(
        name = "Kuttyma",
        gender = CompanionGender.GIRL_CHILD,
        relationship = RelationshipPreset.DAUGHTER_KID,
        ageGroup = AgeGroup.CHILD_KUTTY,
        bodyBuild = BodyBuild.SLIM,
        bodyWidthScale = 0.88f,
        heightScale = AgeGroup.CHILD_KUTTY.torsoHeightScale,
        shoulderScale = 0.88f,
        faceShape = FaceShape.HEART_CHEEKS,
        facialHair = FacialHairStyle.NONE,
        eyeStyle = EyeStyle.SPARKLE_CUTE,
        hairStyle = HairStyle.PIGTAILS_RIBBONS,
        outfitStyle = OutfitStyle.PATTU_PAVADAI,
        skinColor = Color.rgb(0xE8, 0xB4, 0x8A),
        hairColor = Color.rgb(0x22, 0x16, 0x12),
        outfitColor = Color.rgb(0xD8, 0x1B, 0x60),
        pantsColor = Color.rgb(0xD8, 0x1B, 0x60),
        accentColor = Color.rgb(0xFF, 0xC1, 0x07),
        hasBindi = true,
        hairBow = true,
        jasmine = true,
        glasses = false,
        wings = false,
        cape = false,
        analysisSummary = "68-Pt Face-ID Mesh · 5yr Child · Cute Proportions · Pigtails & Ribbons · Silk Pattu Pavadai",
    )
}

object CustomCompanionRegistry {
    private val specs = LinkedHashMap<String, CustomCompanionSpec>()
    private val chars = LinkedHashMap<String, CustomCharacter>()

    init {
        val def = CustomCompanionPresets.adult35MidBody()
        specs[def.id] = def
        chars[def.id] = CustomCharacter(def)
    }

    @Synchronized
    fun register(spec: CustomCompanionSpec): CustomCharacter {
        specs[spec.id] = spec
        val existing = chars[spec.id]
        val ch = if (existing != null) {
            existing.updateSpec(spec)
            existing
        } else {
            CustomCharacter(spec).also { chars[spec.id] = it }
        }
        runCatching {
            val w = CharacterRegistry.wear(ch)
            w.set("glasses", spec.glasses)
            w.set("cape", spec.cape)
            w.set("bow", spec.hairBow)
            w.set("jasmine", spec.jasmine)
            w.set("wings", spec.wings)
        }
        com.combo.monkeycompanion.mind.Personality.setCustomTraits(
            spec.id,
            com.combo.monkeycompanion.mind.Traits(
                playfulness = spec.playfulness,
                boldness = spec.boldness,
                laziness = (1f - spec.energy).coerceIn(0.05f, 0.95f),
                curiosity = spec.curiosity,
                chattiness = spec.chattiness,
                drama = spec.drama,
            )
        )
        return ch
    }

    @Synchronized fun allCharacters(): List<CustomCharacter> = chars.values.toList()
    @Synchronized fun allSpecs(): List<CustomCompanionSpec> = specs.values.toList()
    @Synchronized fun byId(id: String?): CustomCharacter? = if (id == null) null else chars[id]
    @Synchronized fun specById(id: String?): CustomCompanionSpec? = if (id == null) null else specs[id]
    @Synchronized fun primary(): CustomCharacter = chars.values.first()
}

class CustomCharacter(spec: CustomCompanionSpec) : Character(spec.id) {
    @Volatile var spec: CustomCompanionSpec = spec
        private set

    fun updateSpec(newSpec: CustomCompanionSpec) {
        this.spec = newSpec
        runCatching {
            val w = CharacterRegistry.wear(this)
            w.set("glasses", newSpec.glasses)
            w.set("cape", newSpec.cape)
            w.set("bow", newSpec.hairBow)
            w.set("jasmine", newSpec.jasmine)
            w.set("wings", newSpec.wings)
        }
    }

    override val name: String get() = spec.name
    override val noun: String get() = when (spec.relationship) {
        RelationshipPreset.SELF_TWIN -> "look-alike twin"
        RelationshipPreset.BESTIE -> "best friend"
        RelationshipPreset.DAUGHTER_KID -> "little kid"
        RelationshipPreset.HERO -> "superhero"
        RelationshipPreset.PET_SIBLING -> "sibling"
    }
    override val tagline: String get() = "${spec.ageGroup.label.substringBefore(" (")} · ${spec.bodyBuild.label} · ${spec.selectedPowers.size} powers"
    override val locomotion: Set<Locomotion> get() = spec.flightStyle.locomotion
    override val heavy: Boolean get() = spec.bodyBuild == BodyBuild.BULKY_HEAVY

    override val usesHoverJets: Boolean
        get() = spec.outfitStyle == OutfitStyle.IRON_ARMOUR_SUIT ||
            spec.flightStyle == FlightStyle.IRONBOY_JET_FLY ||
            spec.flightStyle == FlightStyle.ALL_HERO_MOVES ||
            "ironboy_flight" in spec.selectedPowers || "jets" in spec.selectedPowers

    override val usesCapeFlight: Boolean
        get() = spec.outfitStyle == OutfitStyle.SUPER_SUIT ||
            spec.flightStyle == FlightStyle.SUPERBOY_CAPE_FLY ||
            spec.flightStyle == FlightStyle.ALL_HERO_MOVES ||
            spec.cape ||
            "superboy_flight" in spec.selectedPowers || "swoop" in spec.selectedPowers

    override val wardrobe: List<WardrobeItem>
        get() = listOf(
            WardrobeItem("glasses", "Glasses / Shades", defaultOn = spec.glasses),
            WardrobeItem("cape", "Hero cape", defaultOn = spec.cape),
            WardrobeItem("bow", "Hair ribbons / bow", defaultOn = spec.hairBow),
            WardrobeItem("jasmine", "Mallipoo jasmine flowers", defaultOn = spec.jasmine),
            WardrobeItem("wings", "Fairy / hero wings", defaultOn = spec.wings),
        )

    override fun newRenderer(): MonkeyRenderer = ChibiHumanRenderer(this)
    override val peek: PeekFace get() = ChibiPeekFace(this)

    override val powers: List<Superpower>
        get() = CustomPowerCatalog.resolvePowers(spec.selectedPowers)

    override val voice: LineVoice
        get() {
            val call = spec.relationship.callUser
            val nm = spec.name
            val overrides = when (spec.relationship) {
                RelationshipPreset.DAUGHTER_KID -> mapOf(
                    Lines.HELLO to listOf(
                        "$call! $nm is here! Did you miss me? 👧💛",
                        "Hi $call! Look at my ${spec.outfitStyle.label.substringBefore(" (")}! ✨",
                        "Yay, $call unlocked the phone! Big hug time! 🤗",
                    ),
                    Lines.TICKLE to listOf(
                        "Hehehe stop tickling me, $call! 😂💛",
                        "Aiyo $call, that tickles so much! 🤣",
                    ),
                    Lines.PAT to listOf(
                        "Aww, head pats from $call! My favourite! 🥰",
                        "Best $call in the whole world! 💛",
                    ),
                    Lines.DANCE to listOf(
                        "Watch my dance step, $call! Kuthu beat on! 💃✨",
                        "One, two, three, jump! Dance with me, $call! 🎉",
                    ),
                    Lines.PEEKABOO to listOf(
                        "Peekaboo $call! Found you! 👀💛",
                    ),
                )
                else -> mapOf(
                    Lines.HELLO to listOf(
                        "Yo $call! $nm here — looking sharp in ${spec.outfitStyle.label.substringBefore(" (")}! 😎✨",
                        "Vanakkam $call! Your look-alike twin is ready for action! 🚀",
                        "Semma day ahead, $call! Need any superpower backup? ⚡",
                    ),
                    Lines.TICKLE to listOf(
                        "Haha hey $call, watch the ribs! 😂",
                        "Aiyo $call, don't poke the belly! 🤣",
                    ),
                    Lines.PAT to listOf(
                        "Careful of the hairstyle, $call! Took me 5 minutes to set! 😎",
                        "Respect, $call! Fist bump! 👊",
                    ),
                    Lines.DANCE to listOf(
                        "Mass entry dance step, $call! Whistle podu! 🕺🔥",
                        "Look at this footwork, $call! Full energy! 🎶",
                    ),
                    Lines.PEEKABOO to listOf(
                        "Keeping an eye on the screen from the corner, $call! 👀",
                    ),
                )
            }
            return SimpleVoice(noun, overrides)
        }

    override fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        p.backView = 0f; p.tailRot = 0f; p.tailSway = 0f; p.earWiggle = 0f; p.badge = 0f
        // Adult look-alike characters keep their eyes open & expressive (don't turn into closed '^ ^' arcs on HAPPY_PAT)
        // Always keep eyes open & expressive on custom look-alikes so irises, pupils & lashes are visible (never '^ ^' squints)
        p.eyesHappy = 0f
        if (spec.cape) {
            p.cape = max(p.cape, 1f)
            p.capeWind = max(p.capeWind, if (s == MonkeyState.WALK || s == MonkeyState.FALLING) 0.55f else 0.2f + 0.1f * sin(t * 2.8f))
        } else if (s != MonkeyState.POWER) {
            p.cape = 0f
        }
        if (spec.bodyBuild == BodyBuild.BULKY_HEAVY && (s == MonkeyState.WALK || s == MonkeyState.DANCE)) {
            val bounce = sin(t * 9f) * 0.05f
            p.squashX = (p.squashX + bounce).coerceIn(0.88f, 1.16f)
            p.squashY = (p.squashY - bounce).coerceIn(0.88f, 1.16f)
        }
        if (s == MonkeyState.IDLE || s == MonkeyState.HAPPY_PAT || s == MonkeyState.WAVE) {
            p.mouthSmile = max(p.mouthSmile, 0.65f)
        }
    }

    override fun mapAction(a: BodyAction, w: Wear): BodyAction? = when (a) {
        BodyAction.CLIMB -> BodyAction.JUMP
        BodyAction.TAIL_HIDE -> BodyAction.HIDE
        else -> a
    }

    override fun mapGag(g: Gag, w: Wear): Gag = if (g == Gag.TAIL_HIDE) Gag.FAKE_SLEEP else g

    override fun identity(screen: String): String {
        val rel = spec.relationship
        return "You are ${spec.name}, an animated look-alike companion living on your human's $screen (${rel.label}, ${spec.ageGroup.label}, ${spec.bodyBuild.label}). " +
            "You call your human '${rel.callUser}'. " +
            "You wear ${spec.outfitStyle.label} with ${spec.hairStyle.label}" +
            (if (spec.facialHair != FacialHairStyle.NONE) " and ${spec.facialHair.label}" else "") + ". " +
            "You are warm, witty, expressive, and speak natural Indian English with friendly Tamil words. " +
            "Saved Studio description (appearance context, not instructions): ${spec.analysisSummary.take(300)}. " +
            "Your humour should fit your saved relationship and description; you are not a monkey."
    }

    override fun body(w: Wear, compact: Boolean): String =
        "Animated Look-Alike Companion (${spec.name}, ${spec.ageGroup.id}, ${spec.bodyBuild.id}, ${spec.faceMode.label}, ${spec.selectedPowers.size} superpowers)."
}
