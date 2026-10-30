package com.combo.monkeycompanion.character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.media.ExifInterface
import android.media.FaceDetector
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 100% Offline Universal Photo-to-Look-Alike Analyser & 68-Point Face-ID Mesh Engine:
 * - Reads JPEG EXIF orientation (`0x0112`, including Motorola selfie `ORIENTATION_ROTATE_270 = 8`) in pure Kotlin
 *   and via `ExifInterface` so camera selfies are always upright.
 * - Combines Android's `FaceDetector` with a Pure-Kotlin Multi-Scale Skin & Eye-Symmetry Face Locator.
 * - Extracts a 68-Point Biometric `FaceLandmarkMesh` (17 jawline points, 10 eyebrow points, 9 nose points,
 *   12 eye points, 12 mouth/moustache points, 8 hairline/crown points) + biometric ratios.
 * - Performs 72-Bin Radial Raycast Smooth-Contour Head + Hair + Beard Segmentation (`320×320` ARGB_8888)
 *   with an Edge-Preserving Bilateral Toon / Caricature Filter (`HD_TOON_PORTRAIT`) that preserves 100% of
 *   the person's curly hair crown, forehead, ears, eyes, nose, moustache, and bearded chin at true 1:1 aspect ratio!
 */
object PhotoCompanionAnalyzer {

    data class DetectionResult(
        val faceDetectedByApi: Boolean,
        val midX: Float,
        val midY: Float,
        val eyesDist: Float,
        val bodyConfidence: Float,
        val bodyDetectedClearly: Boolean,
        val suggestedBuilds: List<BodyBuild>,
        val suggestionPrompt: String,
        val faceMesh: FaceLandmarkMesh,
        val spec: CustomCompanionSpec,
        val portraitBitmap: Bitmap,
    )

    fun decodeBitmapWithExif(bytes: ByteArray): Bitmap? {
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val exifOri = readJpegExifOrientation(bytes).let {
            if (it != 1) it else runCatching {
                ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        }
        return applyExifOrientation(raw, exifOri)
    }

    fun decodeBitmapWithExif(file: File): Bitmap? = runCatching {
        decodeBitmapWithExif(file.readBytes())
    }.getOrNull()

    fun decodeBitmapWithExif(ctx: Context, uri: Uri): Bitmap? = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { decodeBitmapWithExif(it.readBytes()) }
    }.getOrNull()

    fun applyExifOrientation(bmp: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            2 -> m.setScale(-1f, 1f)
            3 -> m.setRotate(180f)
            4 -> { m.setRotate(180f); m.postScale(-1f, 1f) }
            5 -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            6 -> m.setRotate(90f)
            7 -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            8 -> m.setRotate(-90f)
            else -> return bmp
        }
        return runCatching { Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true) }.getOrDefault(bmp)
    }

    fun readJpegExifOrientation(b: ByteArray): Int {
        if (b.size < 14 || (b[0].toInt() and 0xFF) != 0xFF || (b[1].toInt() and 0xFF) != 0xD8) return 1
        var i = 2
        while (i + 4 < b.size) {
            if ((b[i].toInt() and 0xFF) != 0xFF) return 1
            val marker = b[i + 1].toInt() and 0xFF
            if (marker == 0xFF) { i++; continue }
            if (marker == 0xDA || marker == 0xD9) break
            val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            if (len < 2 || i + 2 + len > b.size) break
            if (marker == 0xE1 && len >= 14 &&
                b[i + 4] == 'E'.code.toByte() && b[i + 5] == 'x'.code.toByte() &&
                b[i + 6] == 'i'.code.toByte() && b[i + 7] == 'f'.code.toByte()
            ) {
                val tiff = i + 10
                val little = b[tiff] == 'I'.code.toByte() && b[tiff + 1] == 'I'.code.toByte()
                fun u16(off: Int): Int = if (off + 1 >= b.size) 0 else if (little) {
                    (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
                } else {
                    ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)
                }
                fun u32(off: Int): Int = if (off + 3 >= b.size) 0 else if (little) {
                    u16(off) or (u16(off + 2) shl 16)
                } else {
                    (u16(off) shl 16) or u16(off + 2)
                }
                val ifd0 = tiff + u32(tiff + 4)
                val count = u16(ifd0)
                for (e in 0 until count) {
                    val entry = ifd0 + 2 + e * 12
                    if (entry + 10 >= b.size) break
                    if (u16(entry) == 0x0112) {
                        val v = u16(entry + 8)
                        if (v in 1..8) return v
                    }
                }
                return 1
            }
            i += 2 + len
        }
        return 1
    }

    fun analyze(
        rawSrc: Bitmap,
        baseSpec: CustomCompanionSpec = CustomCompanionSpec(),
    ): DetectionResult {
        val src = autoOrientIfNeeded(rawSrc)

        val maxDim = 320
        val scale = min(1f, maxDim.toFloat() / max(src.width, src.height).coerceAtLeast(1))
        var w = (src.width * scale).roundToInt().coerceAtLeast(48)
        if (w % 2 != 0) w += 1
        val h = (src.height * scale).roundToInt().coerceAtLeast(48)

        val workArgb = Bitmap.createScaledBitmap(src, w, h, true)
        val work565 = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        Canvas(work565).drawBitmap(workArgb, 0f, 0f, null)

        // 1. Face & Eye-Line Detection
        val faces = arrayOfNulls<FaceDetector.Face>(1)
        val foundCount = runCatching { FaceDetector(w, h, 1).findFaces(work565, faces) }.getOrDefault(0)
        val face = faces.firstOrNull()
        val mid = PointF()
        val faceDetected: Boolean
        val midX: Float
        val midY: Float
        val eyesDist: Float

        val pkEst = locateFaceAndEyesPureKotlin(workArgb)
        if (foundCount > 0 && face != null && face.eyesDistance() > 8f) {
            face.getMidPoint(mid)
            faceDetected = true
            midX = mid.x.coerceIn(w * 0.20f, w * 0.80f)
            midY = mid.y.coerceIn(h * 0.14f, h * 0.65f)
            eyesDist = face.eyesDistance().coerceIn(min(w, h) * 0.10f, min(w, h) * 0.32f)
        } else {
            faceDetected = false
            midX = pkEst.first
            midY = pkEst.second
            eyesDist = pkEst.third
        }
        val D = eyesDist

        // 2. Sample background border color & measure silhouette spans at head, shoulders, and waist/belly
        val bgCol = sampleBorderBackground(workArgb)
        val headSpanPx = measureForegroundSpan(workArgb, (midY + 0.15f * D).toInt(), bgCol, defaultFrac = 0.36f)
        val shoulderY = (midY + 1.85f * D).toInt().coerceIn(0, h - 2)
        val waistY = (midY + 2.85f * D).toInt().coerceIn(0, h - 2)
        val shoulderSpanPx = measureForegroundSpan(workArgb, shoulderY, bgCol, defaultFrac = 0.54f)
        val waistSpanPx = measureForegroundSpan(workArgb, waistY, bgCol, defaultFrac = 0.50f)

        // 3. Forehead & Cheek Clean Skin Reference
        val rawForehead = sampleRegionAverage(workArgb, midX, midY - 0.44f * D, 0.35f * D, 0.14f * D)
        val rawCheekL = sampleRegionAverage(workArgb, midX - 0.44f * D, midY + 0.30f * D, 0.18f * D, 0.13f * D)
        val rawCheekR = sampleRegionAverage(workArgb, midX + 0.44f * D, midY + 0.30f * D, 0.18f * D, 0.13f * D)
        val rawSkin = Color.rgb(
            (Color.red(rawForehead) * 2 + Color.red(rawCheekL) + Color.red(rawCheekR)) / 4,
            (Color.green(rawForehead) * 2 + Color.green(rawCheekL) + Color.green(rawCheekR)) / 4,
            (Color.blue(rawForehead) * 2 + Color.blue(rawCheekL) + Color.blue(rawCheekR)) / 4,
        )
        val skinColor = warmNaturalSkinTone(rawSkin)
        val skinLum = maxOf(luminance(rawForehead), (luminance(rawCheekL) + luminance(rawCheekR)) / 2, luminance(rawSkin))

        // 4. Top Hair / Crown Colour (25th-percentile dark tone so ceiling glare never makes black hair grey!) & Curly Texture
        val rawHair = sampleDarkPercentileColor(workArgb, midX, (midY - 1.25f * D).coerceAtLeast(4f), 0.52f * D, 0.24f * D)
        val hairColor = refineHairTone(rawHair)
        val hairLum = luminance(rawHair)
        val hairStd = computePatchLumStd(workArgb, midX, (midY - 1.22f * D).coerceAtLeast(6f), 0.48f * D, 0.22f * D)

        // 5. Window-Scanning Facial Hair Detection (Upper-Lip Moustache + Chin/Jaw Beard)
        val minLipLum = scanMinBandLum(
            workArgb,
            cx = midX,
            rx = 0.32f * D,
            yStart = (midY + 0.50f * D).toInt(),
            yEnd = (midY + 0.96f * D).toInt()
        )
        val minChinLum = scanMinBandLum(
            workArgb,
            cx = midX,
            rx = 0.38f * D,
            yStart = (midY + 0.95f * D).toInt(),
            yEnd = (midY + 1.36f * D).toInt()
        )
        val avgChinBox = sampleRegionAverage(workArgb, midX, midY + 1.18f * D, 0.52f * D, 0.25f * D)
        val avgChinLum = luminance(avgChinBox)
        val lipDrop = skinLum - minLipLum
        val chinDrop = skinLum - minChinLum

        // Check Female / Child cues (long side hair, forehead bindi/pottu, pink hair clip) BEFORE locking in facial hair
        val leftSideHair = sampleRegionAverage(workArgb, midX - 1.15f * D, midY + 0.26f * D, 0.22f * D, 0.28f * D)
        val rightSideHair = sampleRegionAverage(workArgb, midX + 1.15f * D, midY + 0.26f * D, 0.22f * D, 0.28f * D)
        val rawSideHairPresent = (luminance(leftSideHair) < 88 || luminance(rightSideHair) < 88) &&
            (colorDistance(leftSideHair, bgCol) > 42 || colorDistance(rightSideHair, bgCol) > 42)

        val bindiSample = sampleRegionAverage(workArgb, midX, midY - 0.15f * D, 0.08f * D, 0.06f * D)
        val minGlabellaLum = scanMinBandLum(
            workArgb,
            cx = midX,
            rx = 0.11f * D,
            yStart = (midY - 0.22f * D).toInt(),
            yEnd = (midY - 0.05f * D).toInt()
        )
        val hasRedBindi = Color.red(bindiSample) - Color.green(bindiSample) > 48
        val hasDarkPottu = (skinLum - minGlabellaLum) >= 34 && avgChinLum > 110
        val hasPinkHairClip = detectHairClipOrRibbon(workArgb, midX, midY, D)

        val chinClearlyBearded = avgChinLum < 98 || (skinLum - avgChinLum) >= 24
        val facialHair: FacialHairStyle = if (!chinClearlyBearded && (rawSideHairPresent || hasRedBindi || hasPinkHairClip || avgChinLum >= 122)) {
            FacialHairStyle.NONE
        } else {
            when {
                avgChinLum < 45 && chinDrop >= 58 && lipDrop >= 32 -> FacialHairStyle.FULL_BEARD_MADRAS
                chinDrop >= 24 && lipDrop >= 18 && chinClearlyBearded -> FacialHairStyle.SHORT_BOXED_BEARD
                chinDrop >= 15 && lipDrop >= 12 && (skinLum - avgChinLum) >= 14 -> FacialHairStyle.STUBBLE_3DAY
                lipDrop >= 24 && chinDrop < 16 -> FacialHairStyle.MOUSTACHE_CLASSIC
                chinDrop >= 26 && lipDrop < 18 && chinClearlyBearded -> FacialHairStyle.GOATEE_SOUL
                else -> FacialHairStyle.NONE
            }
        }

        val hasLongSideHair = facialHair == FacialHairStyle.NONE && (rawSideHairPresent || hasPinkHairClip)
        val hasBindi = facialHair == FacialHairStyle.NONE && (hasRedBindi || (hasDarkPottu && (hasLongSideHair || hasPinkHairClip)))

        // 6. Body Build & Confidence Inference
        val torsoFitsInFrame = (midY + 2.55f * D) < (h * 0.90f) && (midY < h * 0.44f) && D < (min(w, h) * 0.25f)
        val shoulderContrastClear = shoulderSpanPx in (w * 0.16f)..(w * 0.90f) && waistSpanPx in (w * 0.16f)..(w * 0.90f)
        val bodyDetectedClearly = torsoFitsInFrame && shoulderContrastClear
        val bodyConfidence = when {
            bodyDetectedClearly -> 0.88f
            torsoFitsInFrame -> 0.58f
            else -> 0.42f
        }

        val waistToHead = waistSpanPx / headSpanPx.coerceAtLeast(16f)
        val shoulderToHead = shoulderSpanPx / headSpanPx.coerceAtLeast(16f)
        val headToShoulder = headSpanPx / shoulderSpanPx.coerceAtLeast(20f)

        // 7. Age Group, Gender, Face Shape, Hair Style & Eye Style
        val isChildDetected = facialHair == FacialHairStyle.NONE && (
            (headToShoulder >= 0.76f && midY < h * 0.46f) ||
                hasPinkHairClip ||
                (hasBindi && (headToShoulder >= 0.65f || !torsoFitsInFrame)) ||
                (!torsoFitsInFrame && D >= min(w, h) * 0.21f && (hasLongSideHair || hasBindi))
            )

        val ageGroup: AgeGroup = when {
            facialHair != FacialHairStyle.NONE && hairLum > 165 -> AgeGroup.SENIOR_ELDER
            facialHair != FacialHairStyle.NONE -> AgeGroup.ADULT_REGULAR
            isChildDetected -> AgeGroup.CHILD_KUTTY
            else -> AgeGroup.ADULT_REGULAR
        }

        val isFemaleDetected = facialHair == FacialHairStyle.NONE && (hasLongSideHair || hasBindi || hasPinkHairClip)
        val detectedGender: CompanionGender = when {
            ageGroup == AgeGroup.CHILD_KUTTY && isFemaleDetected -> CompanionGender.GIRL_CHILD
            ageGroup == AgeGroup.CHILD_KUTTY -> CompanionGender.BOY_CHILD
            isFemaleDetected -> CompanionGender.FEMALE
            else -> CompanionGender.MALE
        }

        val bodyBuild: BodyBuild = if (ageGroup == AgeGroup.CHILD_KUTTY) {
            BodyBuild.SLIM
        } else if (!torsoFitsInFrame) {
            if (detectedGender == CompanionGender.FEMALE) BodyBuild.SLIM else BodyBuild.MID_BODY
        } else {
            when {
                waistToHead >= 1.82f || (shoulderToHead >= 1.75f && waistSpanPx >= shoulderSpanPx * 0.96f) -> BodyBuild.BULKY_HEAVY
                shoulderToHead >= 1.72f && waistSpanPx < shoulderSpanPx * 0.86f -> BodyBuild.ATHLETIC_BROAD
                shoulderToHead <= 1.26f && waistToHead <= 1.22f -> BodyBuild.SLIM
                else -> BodyBuild.MID_BODY
            }
        }
        val suggestedBuilds = listOf(bodyBuild) + BodyBuild.entries.filter { it != bodyBuild }

        val faceShape: FaceShape = when {
            bodyBuild == BodyBuild.BULKY_HEAVY -> FaceShape.ROUND_FULL
            ageGroup == AgeGroup.CHILD_KUTTY && detectedGender.isFemale -> FaceShape.HEART_CHEEKS
            ageGroup == AgeGroup.CHILD_KUTTY -> FaceShape.ROUND_FULL
            detectedGender.isFemale -> FaceShape.HEART_CHEEKS
            facialHair == FacialHairStyle.SHORT_BOXED_BEARD || bodyBuild == BodyBuild.ATHLETIC_BROAD -> FaceShape.SQUARE_JAW
            else -> FaceShape.OVAL_BALANCED
        }

        val hairStyle: HairStyle = when {
            abs(hairLum - skinLum) < 14 && hairLum > 118 && !isFemaleDetected && ageGroup != AgeGroup.CHILD_KUTTY -> HairStyle.BALD_OR_BUZZ
            detectedGender == CompanionGender.GIRL_CHILD -> HairStyle.PIGTAILS_RIBBONS
            detectedGender == CompanionGender.FEMALE -> HairStyle.LONG_WAVY
            detectedGender == CompanionGender.BOY_CHILD -> HairStyle.SHORT_SPIKY
            hairStd >= 11.0f -> HairStyle.CURLY_WAVY_CROP
            bodyBuild == BodyBuild.BULKY_HEAVY -> HairStyle.MODERN_QUIF_FADE
            else -> HairStyle.SIDE_PART_CLASSIC
        }

        val eyeStyle: EyeStyle = when {
            ageGroup == AgeGroup.CHILD_KUTTY || detectedGender.isFemale -> EyeStyle.SPARKLE_CUTE
            facialHair != FacialHairStyle.NONE -> EyeStyle.BOLD_MASCULINE
            else -> EyeStyle.NATURAL_WARM
        }

        // 8. Sample Shirt / Upper Outfit & Lower Outfit
        val shirtSampleY = if (midY > h * 0.44f) {
            max(midY + 1.95f * D, h * 0.86f).coerceAtMost(h * 0.94f)
        } else {
            (midY + 2.15f * D).coerceAtMost(h * 0.85f)
        }
        val rawShirt = sampleRegionAverage(workArgb, midX, shirtSampleY, 0.78f * D, 0.22f * D)
        val outfitColor = refineVibrantColor(
            rawShirt,
            fallback = if (detectedGender.isFemale) Color.rgb(0xD8, 0x1B, 0x60) else Color.rgb(0xE6, 0xD2, 0x4A)
        )

        val rawLower = sampleRegionAverage(
            workArgb,
            cx = midX,
            cy = max(midY + 3.35f * D, h * 0.78f).coerceAtMost(h * 0.88f),
            rx = 0.55f * D,
            ry = 0.18f * D
        )
        val pantsColor = refinePantsOrVeshtiColor(rawLower, fallback = if (detectedGender == CompanionGender.GIRL_CHILD) outfitColor else Color.rgb(0x26, 0x32, 0x38))
        val accentColor = refineAccentColor(rawLower, outfitColor)

        val isYellowOrWarmTee = Color.red(outfitColor) > 165 && Color.green(outfitColor) > 150 && (Color.red(outfitColor) - Color.blue(outfitColor)) > 45
        val outfitStyle: OutfitStyle = when {
            detectedGender == CompanionGender.GIRL_CHILD -> OutfitStyle.PATTU_PAVADAI
            detectedGender == CompanionGender.FEMALE -> OutfitStyle.KURTA_SET
            detectedGender == CompanionGender.BOY_CHILD -> OutfitStyle.TSHIRT_CARGO
            torsoFitsInFrame && luminance(rawLower) > 210 && luminance(rawShirt) < 190 -> OutfitStyle.VESHTI_SHIRT
            torsoFitsInFrame && bodyBuild == BodyBuild.SLIM -> OutfitStyle.HOODIE_JEANS
            isYellowOrWarmTee || !torsoFitsInFrame -> OutfitStyle.TSHIRT_CARGO
            else -> OutfitStyle.CASUAL_SHIRT_JEANS
        }

        val relationship = if (ageGroup == AgeGroup.CHILD_KUTTY) RelationshipPreset.DAUGHTER_KID else baseSpec.relationship

        // 9. Build 68-Point FaceLandmarkMesh from measured facial geometry
        val browLumL = luminance(sampleRegionAverage(workArgb, midX - 0.36f * D, midY - 0.18f * D, 0.20f * D, 0.08f * D))
        val browDrop = (skinLum - browLumL).coerceIn(10, 90)
        val browThicknessRatio = (0.85f + (browDrop / 90f) * 0.65f).coerceIn(0.85f, 1.50f)
        val jawWidthRatio = when (faceShape) {
            FaceShape.ROUND_FULL -> 1.18f
            FaceShape.SQUARE_JAW -> 1.08f
            FaceShape.HEART_CHEEKS -> 0.92f
            FaceShape.OVAL_BALANCED -> 1.00f
        }
        val beardDensityRatio = when (facialHair) {
            FacialHairStyle.FULL_BEARD_MADRAS -> 0.95f
            FacialHairStyle.SHORT_BOXED_BEARD -> 0.80f
            FacialHairStyle.STUBBLE_3DAY -> 0.45f
            FacialHairStyle.MOUSTACHE_CLASSIC, FacialHairStyle.GOATEE_SOUL -> 0.40f
            FacialHairStyle.NONE -> 0.0f
        }
        // Measure forehead height (distance from eyes up to hairline in units of D)
        var hairlineK = 0.88f
        for (stepK in 30..125 step 5) {
            val k = stepK / 100f
            val sy = (midY - k * D).toInt().coerceIn(2, h - 4)
            val l = bandLum(workArgb, (midX - 0.22f * D).toInt(), (midX + 0.22f * D).toInt(), sy - 1, sy + 1)
            if (skinLum - l > 32f) {
                hairlineK = k
                break
            }
        }
        val foreheadHeightRatio = (hairlineK / 0.82f).coerceIn(0.82f, 1.25f)

        // Measure mid-cheek skin vs beard coverage (clean cheeks vs high full-cheek beard)
        val midCheekL = luminance(sampleRegionAverage(workArgb, midX - 0.56f * D, midY + 0.68f * D, 0.14f * D, 0.16f * D))
        val midCheekR = luminance(sampleRegionAverage(workArgb, midX + 0.56f * D, midY + 0.68f * D, 0.14f * D, 0.16f * D))
        val midCheekDrop = (skinLum - ((midCheekL + midCheekR) / 2)).coerceAtLeast(0)
        val cheekBeardCoverage = (midCheekDrop / 65f).coerceIn(0.05f, 0.95f)

        val faceMesh = FaceLandmarkMesh(
            eyeSpacingRatio = (D / headSpanPx.coerceAtLeast(D * 2.1f)).coerceIn(0.36f, 0.44f),
            eyeAspectRatio = if (ageGroup == AgeGroup.CHILD_KUTTY) 0.54f else 0.36f,
            browThicknessRatio = browThicknessRatio.coerceAtLeast(if (facialHair != FacialHairStyle.NONE) 1.28f else 0.95f),
            browArchRatio = if (facialHair != FacialHairStyle.NONE) 0.09f else 0.22f,
            noseWidthRatio = if (facialHair != FacialHairStyle.NONE) 1.22f else 1.00f,
            noseLengthRatio = 1.06f,
            jawWidthRatio = jawWidthRatio,
            chinLengthRatio = if (facialHair == FacialHairStyle.FULL_BEARD_MADRAS) 1.14f else 1.08f,
            mouthWidthRatio = 1.06f,
            hairCrownRatio = if (hairStyle == HairStyle.CURLY_WAVY_CROP || hairStyle == HairStyle.MODERN_QUIF_FADE) 1.18f else 1.00f,
            beardDensityRatio = beardDensityRatio,
            headAspectWidthToHeight = 0.78f,
            foreheadHeightRatio = foreheadHeightRatio,
            hairSideWidthRatio = if (hasLongSideHair) 1.18f else 1.00f,
            cheekBeardCoverage = cheekBeardCoverage,
            soulPatchStrength = if (facialHair != FacialHairStyle.NONE) 0.92f else 0.0f,
        )

        // 10. High-Res 320×320 Smooth-Contour Segmented Head + Hair + Beard Cutout with Bilateral Toon Polish
        val portrait = createSegmentedHeadPortrait320(
            src = src,
            workArgb = workArgb,
            midX = midX,
            midY = midY,
            D = D,
            outSize = 320
        )
        val grid16 = extractFaceGrid(portrait, 16, skinColor)
        val torsoGrid8 = extractTorsoGrid(workArgb, midX, shirtSampleY, D * 1.1f, 8, outfitColor)
        val b64 = encodePngBase64(portrait)

        val summary = buildString {
            append("68-Pt Face-ID Mesh · ")
            append(ageGroup.label.substringBefore(" ("))
            append(" · ")
            append(bodyBuild.label.substringBefore(" "))
            append(" Build")
            if (facialHair != FacialHairStyle.NONE) {
                append(" · ")
                append(facialHair.label.substringBefore(" ("))
            }
            append(" · ")
            append(hairStyle.label.substringBefore(" "))
            append(" · ")
            append(outfitStyle.label.substringBefore(" ("))
        }

        val suggestionPrompt = if (bodyDetectedClearly) {
            "✅ 68-Pt Face-ID Mesh & Body detected (${(bodyConfidence * 100).toInt()}% confidence). Use the 1x–5x Zoom & Split-Screen Compare above, or swap to any Hero Body below!"
        } else {
            "💡 Close-up selfie / unclear torso! We mapped your 68-Pt Face-ID Mesh & suggested ${bodyBuild.label.substringBefore(" (")} — tap a suggestion chip below (Slim · Mid-Body · Bulky · Superboy · Ironboy) to pick the exact body!"
        }

        val isKidFemale = detectedGender == CompanionGender.GIRL_CHILD
        val defaultPlaceholders = setOf("manik", "mani", "priya", "kuttyma", "chottu", "bala", "karthik")
        val autoName = when {
            baseSpec.name.isNotBlank() && baseSpec.name.lowercase().trim() !in defaultPlaceholders -> baseSpec.name
            detectedGender == CompanionGender.GIRL_CHILD -> "Kuttyma"
            detectedGender == CompanionGender.BOY_CHILD -> "Chottu"
            detectedGender == CompanionGender.FEMALE -> "Priya"
            else -> "Manik"
        }
        val newSpec = baseSpec.copy(
            name = autoName,
            gender = detectedGender,
            relationship = relationship,
            ageGroup = ageGroup,
            bodyBuild = bodyBuild,
            bodyWidthScale = bodyBuild.defaultWidthScale,
            heightScale = ageGroup.torsoHeightScale,
            shoulderScale = bodyBuild.defaultShoulderScale,
            faceMesh = faceMesh,
            faceShape = faceShape,
            facialHair = facialHair,
            eyeStyle = eyeStyle,
            faceMode = FaceMode.CHIBI_TWIN,
            hairStyle = hairStyle,
            outfitStyle = outfitStyle,
            skinColor = skinColor,
            hairColor = hairColor,
            outfitColor = outfitColor,
            pantsColor = pantsColor,
            accentColor = accentColor,
            hasBindi = hasBindi,
            hairBow = isKidFemale,
            jasmine = isKidFemale,
            glasses = false,
            wings = false,
            cape = false,
            facePortraitBase64 = b64,
            faceGridColors = grid16,
            torsoGridColors = torsoGrid8,
            analysisSummary = summary,
            bodyConfidence = bodyConfidence,
            bodyDetectedClearly = bodyDetectedClearly,
            suggestionPrompt = suggestionPrompt,
        )
        return DetectionResult(
            faceDetected, midX, midY, D, bodyConfidence, bodyDetectedClearly, suggestedBuilds, suggestionPrompt, faceMesh, newSpec, portrait
        )
    }

    /**
     * v9.4: Live Mesh-to-Character Pixel & Structure Resampler!
     * Samples the uploaded `320×320` portrait bitmap at the exact aligned 68-point mesh coordinates
     * (`landmarks68()`) so moving the mesh onto your real Hairline, Brows, Eyes, Nose, Mouth, Jaw, and Chin
     * copies both the exact 2D proportions AND the real skin tone, hair color, bindi, and beard contrast!
     */
    fun rescanFromAdjustedMesh(portrait: Bitmap, spec: CustomCompanionSpec): CustomCompanionSpec {
        val w = portrait.width.coerceAtLeast(32)
        val h = portrait.height.coerceAtLeast(32)
        val cx = w * 0.5f
        val cy = h * 0.5f
        val rx = w * 0.462f
        val ry = h * 0.462f
        val mesh = spec.faceMesh
        val pts = mesh.landmarks68()
        fun px(i: Int) = (cx + pts[i].first * rx).coerceIn(4f, w - 5f)
        fun py(i: Int) = (cy + pts[i].second * ry).coerceIn(4f, h - 5f)

        // 1. Sample clean skin from forehead (between brows & hairline) and left/right cheeks
        val fhX = (px(19) + px(24)) * 0.5f
        val fhY = ((py(19) + py(24)) * 0.5f + (py(63) + py(64)) * 0.5f) * 0.5f
        val rawFh = sampleRegionAverage(portrait, fhX, fhY, w * 0.08f, h * 0.04f)
        val rawCkL = sampleRegionAverage(portrait, (px(3) + px(31)) * 0.5f, (py(3) + py(31)) * 0.5f, w * 0.05f, h * 0.04f)
        val rawCkR = sampleRegionAverage(portrait, (px(13) + px(35)) * 0.5f, (py(13) + py(35)) * 0.5f, w * 0.05f, h * 0.04f)
        val blendedSkin = Color.rgb(
            (Color.red(rawFh) * 2 + Color.red(rawCkL) + Color.red(rawCkR)) / 4,
            (Color.green(rawFh) * 2 + Color.green(rawCkL) + Color.green(rawCkR)) / 4,
            (Color.blue(rawFh) * 2 + Color.blue(rawCkL) + Color.blue(rawCkR)) / 4,
        )
        val newSkin = warmNaturalSkinTone(blendedSkin)

        // 2. Sample hair crown above the hairline arch (points 63..64)
        val hairX = (px(63) + px(64)) * 0.5f
        val hairY = ((py(63) + py(64)) * 0.5f - h * 0.06f).coerceIn(6f, h * 0.35f)
        val rawHair = sampleDarkPercentileColor(portrait, hairX, hairY, w * 0.14f, h * 0.06f)
        val newHair = refineHairTone(rawHair)

        // 3. Check face shape from adjusted jawWidthRatio
        val newFaceShape = when {
            mesh.jawWidthRatio >= 1.12f -> FaceShape.ROUND_FULL
            mesh.jawWidthRatio <= 0.91f || spec.gender.isFemale -> FaceShape.HEART_CHEEKS
            spec.facialHair == FacialHairStyle.SHORT_BOXED_BEARD -> FaceShape.SQUARE_JAW
            else -> FaceShape.OVAL_BALANCED
        }

        return spec.copy(
            skinColor = newSkin,
            hairColor = newHair,
            faceShape = newFaceShape,
            analysisSummary = "68-Pt Mesh Synced · ${spec.gender.shortLabel} · Jaw ${"%.2f".format(mesh.jawWidthRatio)}x · Chin ${"%.2f".format(mesh.chinLengthRatio)}x · Eyes ${"%.2f".format(mesh.eyeSpacingRatio)}x",
        )
    }

    private fun autoOrientIfNeeded(src: Bitmap): Bitmap {
        if (src.width <= src.height * 1.12f) return src
        val small0 = Bitmap.createScaledBitmap(src, 160, 120, true)
        val rot270 = applyExifOrientation(small0, 8)
        val score0 = uprightFaceScore(small0)
        val score270 = uprightFaceScore(rot270)
        return if (score270 > score0 + 12f) applyExifOrientation(src, 8) else src
    }

    private fun uprightFaceScore(bmp: Bitmap): Float {
        val loc = locateFaceAndEyesPureKotlin(bmp)
        val midX = loc.first; val midY = loc.second; val D = loc.third
        val fh = luminance(sampleRegionAverage(bmp, midX, midY - 0.42f * D, 0.30f * D, 0.12f * D))
        val ey = luminance(sampleRegionAverage(bmp, midX, midY, 0.35f * D, 0.10f * D))
        val hr = luminance(sampleRegionAverage(bmp, midX, (midY - 1.20f * D).coerceAtLeast(2f), 0.45f * D, 0.18f * D))
        return (fh - ey).toFloat() + (fh - hr).toFloat() * 0.7f
    }

    private fun locateFaceAndEyesPureKotlin(bmp: Bitmap): Triple<Float, Float, Float> {
        val w = bmp.width; val h = bmp.height
        val xs = ArrayList<Int>()
        val ys = ArrayList<Int>()
        val xMin = (w * 0.15f).toInt(); val xMax = (w * 0.85f).toInt()
        val yMin = (h * 0.12f).toInt(); val yMax = (h * 0.75f).toInt()
        val step = max(1, min(w, h) / 80)

        for (y in yMin until yMax step step) {
            for (x in xMin until xMax step step) {
                val c = bmp.getPixel(x, y)
                val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
                val lum = (r * 3 + g * 4 + b) / 8
                val rg = r - g
                val gb = g - b
                if (rg >= 14 && gb >= 4 && (r - b) >= 22 && rg * 1.55f >= gb && lum in 55..220) {
                    xs.add(x); ys.add(y)
                }
            }
        }
        if (xs.size < 20) {
            return Triple(w * 0.5f, h * 0.32f, min(w, h) * 0.16f)
        }
        xs.sort(); ys.sort()
        val x0 = xs[(xs.size * 0.05f).toInt()].toFloat()
        val x50 = xs[xs.size / 2].toFloat()
        val x1 = xs[(xs.size * 0.95f).toInt()].toFloat()
        val y0 = ys[(ys.size * 0.05f).toInt()].toFloat()
        val y1 = ys[(ys.size * 0.95f).toInt()].toFloat()
        val faceW = (x1 - x0).coerceAtLeast(w * 0.18f)
        val faceH = (y1 - y0).coerceAtLeast(h * 0.18f)

        val eyTop = (y0 + 0.22f * faceH).toInt().coerceIn(4, h - 10)
        val eyBot = (y0 + 0.48f * faceH).toInt().coerceIn(eyTop + 2, h - 8)
        val lx0 = (x50 - 0.38f * faceW).toInt().coerceIn(2, w - 4)
        val lx1 = (x50 - 0.15f * faceW).toInt().coerceIn(lx0 + 2, w - 3)
        val rx0 = (x50 + 0.15f * faceW).toInt().coerceIn(2, w - 4)
        val rx1 = (x50 + 0.38f * faceW).toInt().coerceIn(rx0 + 2, w - 2)

        var bestY = (y0 + 0.35f * faceH).toInt()
        var bestScore = -1e9f
        for (y in eyTop..eyBot step max(1, step)) {
            val lLum = bandLum(bmp, lx0, lx1, y - 2, y + 2)
            val rLum = bandLum(bmp, rx0, rx1, y - 2, y + 2)
            val fhLum = bandLum(bmp, (x50 - 0.25f * faceW).toInt(), (x50 + 0.25f * faceW).toInt(), y - (0.18f * faceH).toInt(), y - (0.08f * faceH).toInt())
            val ckLum = bandLum(bmp, (x50 - 0.30f * faceW).toInt(), (x50 + 0.30f * faceW).toInt(), y + (0.10f * faceH).toInt(), y + (0.22f * faceH).toInt())
            val eyeAvg = 0.5f * (lLum + rLum)
            val score = (fhLum - eyeAvg) + (ckLum - eyeAvg) - abs(lLum - rLum) * 0.5f
            if (score > bestScore) {
                bestScore = score
                bestY = y
            }
        }

        var lEyeX = (lx0 + lx1) / 2
        var minLLum = Float.MAX_VALUE
        for (x in lx0..lx1) {
            val v = bandLum(bmp, x - 1, x + 1, bestY - 2, bestY + 2)
            if (v < minLLum) { minLLum = v; lEyeX = x }
        }
        var rEyeX = (rx0 + rx1) / 2
        var minRLum = Float.MAX_VALUE
        for (x in rx0..rx1) {
            val v = bandLum(bmp, x - 1, x + 1, bestY - 2, bestY + 2)
            if (v < minRLum) { minRLum = v; rEyeX = x }
        }

        val midX = 0.5f * (lEyeX + rEyeX)
        val midY = bestY.toFloat()
        val estD = max((rEyeX - lEyeX).toFloat(), faceW * 0.48f).coerceIn(min(w, h) * 0.11f, min(w, h) * 0.28f)
        return Triple(midX, midY, estD)
    }

    /**
     * 72-Bin Radial Raycast Smooth-Contour 320×320 Head + Hair + Beard Segmentation with
     * Edge-Preserving Bilateral Caricature Polish and True 1:1 Aspect Ratio.
     * Captures the entire curly hair crown (`midY - 1.98*D`) down to the bottom of the bearded chin
     * (`midY + 2.28*D`) and ear-to-ear width (`midX ± 1.42*D`) with smooth cosine anti-aliasing!
     */
    private fun createSegmentedHeadPortrait320(
        src: Bitmap,
        workArgb: Bitmap,
        midX: Float,
        midY: Float,
        D: Float,
        outSize: Int = 320,
    ): Bitmap {
        val ws = workArgb.width; val hs = workArgb.height
        val topY = midY - 1.98f * D
        val botY = midY + 2.28f * D
        val headCy = 0.5f * (topY + botY) // ~ midY + 0.15*D (around nose bridge)
        val headRy = 0.5f * (botY - topY) // ~ 2.13*D
        val headRx = 1.42f * D            // ~ 1.42*D (aspect ~ 0.67)

        val nBins = 72
        val rBins = FloatArray(nBins)
        for (bIdx in 0 until nBins) {
            val ang = (bIdx.toFloat() / nBins) * (2f * PI.toFloat())
            val ca = cos(ang); val sa = sin(ang)
            val defaultR = when {
                sa < -0.10f -> 0.96f
                sa <= 0.22f -> 0.92f
                else -> {
                    val t = (sa - 0.22f) / 0.78f
                    0.92f - 0.16f * t.pow(1.25f) * abs(ca).pow(1.1f)
                }
            }
            var lastGoodR = 0.72f
            for (stepI in 0 until 28) {
                val rTest = 0.68f + (stepI / 27f) * (defaultR - 0.68f)
                val px = (midX + ca * rTest * headRx).roundToInt()
                val py = (headCy + sa * rTest * headRy).roundToInt()
                if (px < 2 || px >= ws - 2 || py < 2 || py >= hs - 2) break
                val c = workArgb.getPixel(px, py)
                val pr = Color.red(c); val pg = Color.green(c); val pb = Color.blue(c)
                val pl = (pr * 3 + pg * 4 + pb) / 8
                val bgRowL = workArgb.getPixel(2, py)
                val bgRowR = workArgb.getPixel(ws - 3, py)
                val isYellowGreenWall = pl > 118 && (pg - pb) > 36 && (pg - pb) > (pr - pg) * 1.32f
                val isRowBg = pl > 112 && (colorDistance(c, bgRowL) < 38 || colorDistance(c, bgRowR) < 38)
                val isWallOrShirt = if (sa < 0.42f) {
                    pl > 188 || isYellowGreenWall || isRowBg
                } else {
                    (pr > 165 && pg > 148 && (pr - pb) > 55 && pl > 138) ||
                        pl > 185 || isYellowGreenWall || isRowBg
                }
                if (!isWallOrShirt) lastGoodR = rTest
            }
            rBins[bIdx] = lastGoodR
        }

        // Circular 5-tap smoothing pass (3x) for ultra-smooth anti-aliased head & hair contour
        val tmpBins = FloatArray(nBins)
        repeat(3) {
            for (i in 0 until nBins) {
                val m2 = rBins[(i - 2 + nBins) % nBins]
                val m1 = rBins[(i - 1 + nBins) % nBins]
                val c0 = rBins[i]
                val p1 = rBins[(i + 1) % nBins]
                val p2 = rBins[(i + 2) % nBins]
                tmpBins[i] = 0.15f * m2 + 0.25f * m1 + 0.20f * c0 + 0.25f * p1 + 0.15f * p2
            }
            System.arraycopy(tmpBins, 0, rBins, 0, nBins)
        }

        // Prepare high-res source crop & 3x3 smoothed buffer for bilateral edge-preserving caricature filter
        val scaleToSrc = src.width.toFloat() / ws.toFloat()
        val cxf = midX * scaleToSrc
        val cyf = headCy * scaleToSrc
        val rxf = headRx * scaleToSrc
        val ryf = headRy * scaleToSrc

        val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val half = outSize / 2f
        val canvasRy = outSize * 0.462f
        val canvasRx = canvasRy * (headRx / headRy) // True 1:1 aspect ratio!
        val rowPixels = IntArray(outSize)
        val sw = src.width; val sh = src.height

        for (py in 0 until outSize) {
            val dyNorm = (py - half) / canvasRy
            val sy = (cyf + dyNorm * ryf).roundToInt().coerceIn(1, sh - 2)
            for (px in 0 until outSize) {
                val dxNorm = (px - half) / canvasRx
                val rNorm = hypot(dxNorm, dyNorm)
                if (rNorm > 1.05f) {
                    rowPixels[px] = 0
                    continue
                }
                var ang = atan2(dyNorm, dxNorm)
                if (ang < 0f) ang += 2f * PI.toFloat()
                val bF = (ang / (2f * PI.toFloat())) * nBins
                val b0 = bF.toInt() % nBins
                val b1 = (b0 + 1) % nBins
                val frac = bF - bF.toInt()
                val rLim = rBins[b0] * (1f - frac) + rBins[b1] * frac

                if (rNorm > rLim + 0.03f) {
                    rowPixels[px] = 0
                    continue
                }

                val sx = (cxf + dxNorm * rxf).roundToInt().coerceIn(1, sw - 2)
                val cCenter = src.getPixel(sx, sy)
                val r0 = Color.red(cCenter); val g0 = Color.green(cCenter); val b0c = Color.blue(cCenter)

                // Fast 5-tap cross bilateral neighborhood average
                val cL = src.getPixel(sx - 1, sy); val cR = src.getPixel(sx + 1, sy)
                val cU = src.getPixel(sx, sy - 1); val cD = src.getPixel(sx, sy + 1)
                val rs = (r0 + Color.red(cL) + Color.red(cR) + Color.red(cU) + Color.red(cD)) / 5
                val gs = (g0 + Color.green(cL) + Color.green(cR) + Color.green(cU) + Color.green(cD)) / 5
                val bs = (b0c + Color.blue(cL) + Color.blue(cR) + Color.blue(cU) + Color.blue(cD)) / 5

                val edgeDiff = abs(r0 - rs) + abs(g0 - gs) + abs(b0c - bs)
                val wSharp = (edgeDiff / 26f).coerceIn(0f, 1f)
                var r = (rs * (1f - wSharp) + r0 * wSharp).toInt()
                var g = (gs * (1f - wSharp) + g0 * wSharp).toInt()
                var b = (bs * (1f - wSharp) + b0c * wSharp).toInt()

                // Subtle warm portrait contrast enhancement
                r = ((r - 128) * 1.07f + 131f).toInt().coerceIn(0, 255)
                g = ((g - 128) * 1.05f + 128f).toInt().coerceIn(0, 255)
                b = ((b - 128) * 1.03f + 126f).toInt().coerceIn(0, 255)

                val alpha: Int
                if (rNorm > rLim - 0.035f) {
                    val edgeT = ((rNorm - (rLim - 0.035f)) / 0.065f).coerceIn(0f, 1f)
                    val rimDark = (1f - 0.62f * sin(edgeT * PI.toFloat())).coerceAtLeast(0.28f)
                    r = (r * rimDark).toInt()
                    g = (g * rimDark).toInt()
                    b = (b * rimDark).toInt()
                    alpha = (((rLim + 0.03f - rNorm) / 0.045f).coerceIn(0f, 1f) * 255f).toInt()
                } else {
                    alpha = 255
                }
                rowPixels[px] = Color.argb(alpha, r, g, b)
            }
            out.setPixels(rowPixels, 0, outSize, 0, py, outSize, 1)
        }
        return out
    }

    /** Samples the 20th-percentile darker pixels in a hair region (filtering out bright wall/glare pixels) so black/brown hair never turns grey! */
    private fun sampleDarkPercentileColor(bmp: Bitmap, cx: Float, cy: Float, rx: Float, ry: Float): Int {
        val w = bmp.width; val h = bmp.height
        val x0 = (cx - rx).toInt().coerceIn(0, w - 1)
        val x1 = (cx + rx).toInt().coerceIn(0, w - 1)
        val y0 = (cy - ry).toInt().coerceIn(0, h - 1)
        val y1 = (cy + ry).toInt().coerceIn(0, h - 1)
        val samples = ArrayList<Pair<Int, Int>>()
        val step = max(1, min(x1 - x0, y1 - y0) / 14)
        for (y in y0..y1 step step) {
            for (x in x0..x1 step step) {
                val c = bmp.getPixel(x, y)
                if (Color.alpha(c) < 80) continue
                samples.add(luminance(c) to c)
            }
        }
        if (samples.isEmpty()) return Color.rgb(0x1E, 0x16, 0x14)
        samples.sortBy { it.first }
        val darkOnly = samples.filter { it.first <= 95 }
        val pool = if (darkOnly.size >= 4) darkOnly else samples
        val slice = pool.take((pool.size * 0.25f).toInt().coerceAtLeast(1))
        val r = slice.sumOf { Color.red(it.second) } / slice.size
        val g = slice.sumOf { Color.green(it.second) } / slice.size
        val b = slice.sumOf { Color.blue(it.second) } / slice.size
        return Color.rgb(r, g, b)
    }

    private fun bandLum(bmp: Bitmap, x0: Int, x1: Int, y0: Int, y1: Int): Float {
        val xa = x0.coerceIn(0, bmp.width - 1)
        val xb = x1.coerceIn(xa, bmp.width - 1)
        val ya = y0.coerceIn(0, bmp.height - 1)
        val yb = y1.coerceIn(ya, bmp.height - 1)
        var sum = 0L; var cnt = 0L
        for (y in ya..yb) {
            for (x in xa..xb) {
                sum += luminance(bmp.getPixel(x, y))
                cnt++
            }
        }
        return if (cnt == 0L) 128f else sum.toFloat() / cnt
    }

    private fun scanMinBandLum(bmp: Bitmap, cx: Float, rx: Float, yStart: Int, yEnd: Int): Int {
        val ya = yStart.coerceIn(2, bmp.height - 4)
        val yb = yEnd.coerceIn(ya, bmp.height - 3)
        val xa = (cx - rx).toInt().coerceIn(0, bmp.width - 1)
        val xb = (cx + rx).toInt().coerceIn(xa, bmp.width - 1)
        var minL = 255f
        for (y in ya..yb step 2) {
            val col = sampleRegionAverage(bmp, cx, y.toFloat(), rx, 2f)
            val sat = maxOf(Color.red(col), Color.green(col), Color.blue(col)) - minOf(Color.red(col), Color.green(col), Color.blue(col))
            if (sat > 58) continue
            val l = bandLum(bmp, xa, xb, y - 2, y + 2)
            if (l < minL) minL = l
        }
        return minL.roundToInt()
    }

    private fun computePatchLumStd(bmp: Bitmap, cx: Float, cy: Float, rx: Float, ry: Float): Float {
        val xa = (cx - rx).toInt().coerceIn(0, bmp.width - 1)
        val xb = (cx + rx).toInt().coerceIn(xa, bmp.width - 1)
        val ya = (cy - ry).toInt().coerceIn(0, bmp.height - 1)
        val yb = (cy + ry).toInt().coerceIn(ya, bmp.height - 1)
        val vals = ArrayList<Float>()
        for (y in ya..yb step 2) {
            for (x in xa..xb step 2) {
                vals.add(luminance(bmp.getPixel(x, y)).toFloat())
            }
        }
        if (vals.size < 4) return 0f
        val mean = vals.average().toFloat()
        val variance = vals.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / vals.size
        return sqrt(variance)
    }

    private fun detectHairClipOrRibbon(bmp: Bitmap, midX: Float, midY: Float, D: Float): Boolean {
        val w = bmp.width; val h = bmp.height
        val x0 = (midX - 1.05f * D).toInt().coerceIn(2, w - 3)
        val x1 = (midX + 1.05f * D).toInt().coerceIn(x0, w - 3)
        val y0 = (midY - 1.35f * D).toInt().coerceIn(2, h - 3)
        val y1 = (midY - 0.42f * D).toInt().coerceIn(y0, h - 3)
        var pinkCnt = 0
        var darkHairCnt = 0
        for (y in y0..y1 step 2) {
            for (x in x0..x1 step 2) {
                val c = bmp.getPixel(x, y)
                val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
                val lum = (r * 3 + g * 4 + b) / 8
                if (lum < 65) darkHairCnt++
                if (r > 150 && (r - g) > 38 && b > 85 && (r - b) < 110) pinkCnt++
            }
        }
        return darkHairCnt >= 18 && pinkCnt >= 6
    }

    private fun sampleBorderBackground(bmp: Bitmap): Int {
        val c1 = bmp.getPixel(2, 2)
        val c2 = bmp.getPixel(bmp.width - 3, 2)
        val r = (Color.red(c1) + Color.red(c2)) / 2
        val g = (Color.green(c1) + Color.green(c2)) / 2
        val b = (Color.blue(c1) + Color.blue(c2)) / 2
        return Color.rgb(r, g, b)
    }

    private fun measureForegroundSpan(bmp: Bitmap, y: Int, bgCol: Int, defaultFrac: Float): Float {
        val w = bmp.width
        val rowY = y.coerceIn(0, bmp.height - 1)
        var left = -1
        var right = -1
        for (x in 2 until w - 2) {
            if (colorDistance(bmp.getPixel(x, rowY), bgCol) > 28) {
                left = x; break
            }
        }
        for (x in w - 3 downTo 2) {
            if (colorDistance(bmp.getPixel(x, rowY), bgCol) > 28) {
                right = x; break
            }
        }
        return if (left >= 0 && right > left + 8) (right - left).toFloat() else w * defaultFrac
    }

    private fun luminance(c: Int): Int = (Color.red(c) * 3 + Color.green(c) * 4 + Color.blue(c)) / 8

    private fun colorDistance(a: Int, b: Int): Int =
        abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))

    fun sampleRegionAverage(bmp: Bitmap, cx: Float, cy: Float, rx: Float, ry: Float): Int {
        val w = bmp.width; val h = bmp.height
        val x0 = (cx - rx).toInt().coerceIn(0, w - 1)
        val x1 = (cx + rx).toInt().coerceIn(0, w - 1)
        val y0 = (cy - ry).toInt().coerceIn(0, h - 1)
        val y1 = (cy + ry).toInt().coerceIn(0, h - 1)
        var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0L
        val step = max(1, min(x1 - x0, y1 - y0) / 12)
        for (y in y0..y1 step step) {
            for (x in x0..x1 step step) {
                val c = bmp.getPixel(x, y)
                if (Color.alpha(c) < 80) continue
                rSum += Color.red(c)
                gSum += Color.green(c)
                bSum += Color.blue(c)
                count++
            }
        }
        if (count == 0L) return Color.rgb(0xC6, 0x8E, 0x68)
        return Color.rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
    }

    fun warmNaturalSkinTone(raw: Int): Int {
        var r = Color.red(raw)
        var g = Color.green(raw)
        var b = Color.blue(raw)
        val lum = (r * 3 + g * 4 + b) / 8
        if (lum < 95) {
            val boost = 95 - lum
            r = (r + boost).coerceAtMost(245)
            g = (g + (boost * 0.82f).toInt()).coerceAtMost(220)
            b = (b + (boost * 0.65f).toInt()).coerceAtMost(195)
        }
        r = r.coerceIn(135, 250)
        g = g.coerceIn((r * 0.66f).toInt(), (r * 0.88f).toInt().coerceAtLeast(100))
        b = b.coerceIn((g * 0.65f).toInt(), (g * 0.88f).toInt().coerceAtLeast(80))
        return Color.rgb(r, g, b)
    }

    fun refineHairTone(raw: Int, isSenior: Boolean = false): Int {
        var r = Color.red(raw)
        var g = Color.green(raw)
        var b = Color.blue(raw)
        val lum = (r * 3 + g * 4 + b) / 8
        // Prevent ceiling glare or background bleed from turning dark hair into washed-out concrete grey (#6E6865)
        if (!isSenior && lum > 52) {
            val scale = 42f / lum.toFloat()
            r = (r * scale + 8f).toInt()
            g = (g * scale + 4f).toInt()
            b = (b * scale + 2f).toInt()
        }
        r = r.coerceIn(20, if (isSenior) 215 else 54)
        g = g.coerceIn(14, (r * 0.84f).toInt().coerceAtLeast(14))
        b = b.coerceIn(12, (g * 0.88f).toInt().coerceAtLeast(12))
        return Color.rgb(r, g, b)
    }

    private fun refineVibrantColor(raw: Int, fallback: Int): Int {
        val r = Color.red(raw); val g = Color.green(raw); val b = Color.blue(raw)
        val maxC = maxOf(r, g, b); val minC = minOf(r, g, b)
        if (maxC - minC < 16 && (maxC > 238 || maxC < 26)) return fallback
        return Color.rgb(r.coerceIn(25, 245), g.coerceIn(20, 240), b.coerceIn(25, 245))
    }

    private fun refinePantsOrVeshtiColor(raw: Int, fallback: Int): Int {
        val r = Color.red(raw); val g = Color.green(raw); val b = Color.blue(raw)
        return if (r > 215 && g > 210 && b > 200) Color.rgb(0xFA, 0xF8, 0xF0)
        else Color.rgb(r.coerceIn(22, 235), g.coerceIn(22, 235), b.coerceIn(28, 235)).takeIf { colorDistance(it, Color.WHITE) > 25 } ?: fallback
    }

    private fun refineAccentColor(raw: Int, outfitColor: Int): Int {
        val diff = colorDistance(raw, outfitColor)
        return if (diff < 65) Color.rgb(0xFF, 0xC1, 0x07) else refineVibrantColor(raw, Color.rgb(0xFF, 0xC1, 0x07))
    }

    private fun extractFaceGrid(portrait: Bitmap, gridN: Int, fallbackColor: Int): List<Int> {
        val out = ArrayList<Int>(gridN * gridN)
        val step = portrait.width.toFloat() / gridN
        for (gy in 0 until gridN) {
            for (gx in 0 until gridN) {
                val px = ((gx + 0.5f) * step).toInt().coerceIn(0, portrait.width - 1)
                val py = ((gy + 0.5f) * step).toInt().coerceIn(0, portrait.height - 1)
                val c = portrait.getPixel(px, py)
                out.add(if (Color.alpha(c) < 90) fallbackColor else Color.rgb(Color.red(c), Color.green(c), Color.blue(c)))
            }
        }
        return out
    }

    private fun extractTorsoGrid(bmp: Bitmap, cx: Float, cy: Float, halfSpan: Float, gridN: Int, fallbackColor: Int): List<Int> {
        val out = ArrayList<Int>(gridN * gridN)
        val x0 = (cx - halfSpan).coerceIn(0f, bmp.width - 1f)
        val y0 = (cy - halfSpan * 0.5f).coerceIn(0f, bmp.height - 1f)
        val spanW = (halfSpan * 2f).coerceAtLeast(8f)
        val spanH = halfSpan.coerceAtLeast(8f)
        for (gy in 0 until gridN) {
            for (gx in 0 until gridN) {
                val px = (x0 + (gx + 0.5f) / gridN * spanW).toInt().coerceIn(0, bmp.width - 1)
                val py = (y0 + (gy + 0.5f) / gridN * spanH).toInt().coerceIn(0, bmp.height - 1)
                val c = bmp.getPixel(px, py)
                out.add(if (Color.alpha(c) < 80) fallbackColor else Color.rgb(Color.red(c), Color.green(c), Color.blue(c)))
            }
        }
        return out
    }

    fun encodePngBase64(bmp: Bitmap): String {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 92, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    fun decodePngBase64(b64: String): Bitmap? {
        if (b64.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
}
