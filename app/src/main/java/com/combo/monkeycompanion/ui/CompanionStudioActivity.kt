package com.combo.monkeycompanion.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.Switch
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.PowerPhase
import com.combo.monkeycompanion.character.AgeGroup
import com.combo.monkeycompanion.character.CompanionGender
import com.combo.monkeycompanion.character.BodyBuild
import com.combo.monkeycompanion.character.CustomCharacter
import com.combo.monkeycompanion.character.CustomCompanionPresets
import com.combo.monkeycompanion.character.CustomCompanionRegistry
import com.combo.monkeycompanion.character.CustomCompanionSpec
import com.combo.monkeycompanion.character.CustomCompanionStore
import com.combo.monkeycompanion.character.CustomPowerCatalog
import com.combo.monkeycompanion.character.FaceMode
import com.combo.monkeycompanion.character.FacialHairStyle
import com.combo.monkeycompanion.character.FlightStyle
import com.combo.monkeycompanion.character.MeshFeatureTarget
import com.combo.monkeycompanion.character.HairStyle
import com.combo.monkeycompanion.character.OutfitStyle
import com.combo.monkeycompanion.character.PhotoCompanionAnalyzer
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Huge 360dp 60fps Live Interactive Stage View for [CompanionStudioActivity]:
 * - Multi-Level Zoom (`1.0x` Full Body, `2.0x` Upper Body, `3.5x` Face Close-Up, `5.0x` Ultra Face Zoom)
 * - Two-finger Pinch-to-Zoom (`ScaleGestureDetector`) & One-finger Drag-to-Pan (`panX`, `panY`)
 * - Split-Screen Side-by-Side Comparison (`splitCompareMode`: Uploaded Photo Face vs Created 60fps Character Face)
 * - Live 68-Point iOS Face-ID Biometric Wireframe Mesh Overlay (`showFaceMesh`)
 */
class StudioLiveStageView(context: Context, var character: CustomCharacter) : View(context), Choreographer.FrameCallback {
    enum class DemoMode(val label: String) {
        IDLE_WAVE("👋 Wave & Dance"),
        FLIGHT_LOOP("🚀 Sky Loop + Hover Jets"),
        KOVAM_ANGRY("😡 Kovam Fiery Mode"),
        NINJA_CLONES("🥷 Ninja + Shuriken"),
        CYCLE_POWERS("⚡ Cycle Selected Powers"),
    }

    private val d = resources.displayMetrics.density
    private val rig = MonkeyRig()
    private val pose = Pose()
    private val animCtx = AnimContext()
    private var renderer = character.newRenderer()
    private var t = 0f
    private var modeT = 0f
    private var running = false
    private var lastNanos = 0L

    var zoomLevel: Float = 1.0f
        set(value) {
            field = value.coerceIn(1.0f, 6.0f)
            onZoomChanged?.invoke(field)
            invalidate()
        }
    var panX: Float = 0f
        set(value) { field = value.coerceIn(-280f * d, 280f * d); invalidate() }
    var panY: Float = 0f
        set(value) { field = value.coerceIn(-280f * d, 280f * d); invalidate() }
    var splitCompareMode: Boolean = false
        set(value) { field = value; invalidate() }
    var showFaceMesh: Boolean = false
        set(value) { field = value; invalidate() }
    var moveMeshMode: Boolean = false
        set(value) { field = value; invalidate() }
    var selectedMeshFeature: MeshFeatureTarget = MeshFeatureTarget.WHOLE_MESH
        set(value) {
            field = value
            onFeatureSelected?.invoke(value)
            invalidate()
        }
    var activeDraggingFeature: MeshFeatureTarget? = null
        private set
    private var loupeTouchX: Float = 0f
    private var loupeTouchY: Float = 0f
    private var loupeActiveUntil: Float = 0f
    var uploadedPhotoThumb: Bitmap? = null
        set(value) { field = value; invalidate() }
    var onZoomChanged: ((Float) -> Unit)? = null
    var onMeshAdjusted: ((com.combo.monkeycompanion.character.FaceLandmarkMesh) -> Unit)? = null
    var onFeatureSelected: ((MeshFeatureTarget) -> Unit)? = null
    var onResampledSpec: ((CustomCompanionSpec) -> Unit)? = null

    private val loupeClipPath = Path()
    private val loupeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = com.combo.monkeycompanion.theme.Th.rgb(0xFF, 0xD5, 0x4F); style = Paint.Style.STROKE; strokeWidth = 2.6f * d
    }
    private val loupeCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = com.combo.monkeycompanion.theme.Th.rgb(0xFF, 0xD5, 0x4F); style = Paint.Style.STROKE; strokeWidth = 1.4f * d
    }

    /**
     * Nudges the currently selected [selectedMeshFeature] by normalized `(dxNorm, dyNorm, dScale)`,
     * shows the 2.5x Magnifier Loupe on that feature, and updates the 2D character 1:1 in real time!
     */
    fun nudgeSelectedFeature(dxNorm: Float, dyNorm: Float, dScale: Float = 0f) {
        if (!showFaceMesh) showFaceMesh = true
        val cur = character.spec.faceMesh
        val next = cur.nudgeFeature(selectedMeshFeature, dxNorm, dyNorm, dScale)
        val portraitBmp = uploadedPhotoThumb ?: CustomCompanionStore.getOrDecodeBitmap(character.spec.id, character.spec.facePortraitBase64)
        var updatedSpec = character.spec.copy(faceMesh = next)
        if (portraitBmp != null) {
            updatedSpec = PhotoCompanionAnalyzer.rescanFromAdjustedMesh(portraitBmp, updatedSpec)
        }
        character.updateSpec(updatedSpec)
        val (hx, hy) = next.featureAnchorScreen(selectedMeshFeature, thumbRect)
        loupeTouchX = hx
        loupeTouchY = hy
        loupeActiveUntil = t + 1.8f
        onMeshAdjusted?.invoke(next)
        onResampledSpec?.invoke(updatedSpec)
        invalidate()
    }

    var demoMode: DemoMode = DemoMode.IDLE_WAVE
        set(value) {
            field = value
            modeT = 0f
            invalidate()
        }

    private var dragLastX = 0f
    private var dragLastY = 0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (moveMeshMode && showFaceMesh) {
                val cur = character.spec.faceMesh
                val next = cur.nudgeFeature(selectedMeshFeature, 0f, 0f, dScale = (detector.scaleFactor - 1f))
                character.updateSpec(character.spec.copy(faceMesh = next))
                onMeshAdjusted?.invoke(next)
                invalidate()
            } else {
                zoomLevel = (zoomLevel * detector.scaleFactor).coerceIn(1.0f, 6.0f)
            }
            return true
        }
    })

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = com.combo.monkeycompanion.theme.Th.argb(90, 0xFF, 0xD5, 0x4F) }
    private val jetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = com.combo.monkeycompanion.theme.Th.rgb(0x00, 0xE5, 0xFF); strokeWidth = 2f * d }
    private val jetPath = Path()
    private val thumbRect = RectF()
    private val meshRect = RectF()
    private val thumbBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = com.combo.monkeycompanion.theme.Th.rgb(0x00, 0xE6, 0x76); style = Paint.Style.STROKE; strokeWidth = 2f * d
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 12.8f * d; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = com.combo.monkeycompanion.theme.Th.rgb(0x80, 0xDE, 0xEA); textSize = 11f * d
    }

    fun resetView() {
        zoomLevel = 1.0f
        panX = 0f
        panY = 0f
        invalidate()
    }

    fun refreshSpec() {
        renderer = character.newRenderer().also { it.wear = Wear(character) }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast((320 * d).toInt())
        val h = (360 * d).toInt()
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragLastX = event.x
                dragLastY = event.y
                if (showFaceMesh && !thumbRect.isEmpty) {
                    val hit = character.spec.faceMesh.hitTestFeatureHandle(event.x, event.y, thumbRect, d)
                    if (hit != null) {
                        selectedMeshFeature = hit
                        activeDraggingFeature = hit
                        loupeTouchX = event.x
                        loupeTouchY = event.y
                        loupeActiveUntil = t + 2.5f
                        invalidate()
                    } else if (moveMeshMode) {
                        // Trackpad Drag Mode: swiping anywhere moves the selected feature without needing to touch a tiny dot!
                        activeDraggingFeature = selectedMeshFeature
                        loupeTouchX = event.x
                        loupeTouchY = event.y
                        loupeActiveUntil = t + 2.5f
                        invalidate()
                    } else {
                        activeDraggingFeature = null
                    }
                } else {
                    activeDraggingFeature = null
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    val dx = event.x - dragLastX
                    val dy = event.y - dragLastY
                    dragLastX = event.x
                    dragLastY = event.y
                    val target = activeDraggingFeature ?: (if (moveMeshMode && showFaceMesh) selectedMeshFeature else null)
                    if (target != null && showFaceMesh) {
                        val span = (thumbRect.width() * 0.462f).coerceAtLeast(90f * d)
                        val dxNorm = dx / span
                        val dyNorm = dy / span
                        val next = character.spec.faceMesh.nudgeFeature(target, dxNorm, dyNorm, 0f)
                        character.updateSpec(character.spec.copy(faceMesh = next))
                        loupeTouchX = event.x
                        loupeTouchY = event.y
                        loupeActiveUntil = t + 1.8f
                        onMeshAdjusted?.invoke(next)
                        invalidate()
                    } else {
                        panX += dx
                        panY += dy
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeDraggingFeature != null) {
                    activeDraggingFeature = null
                    val portraitBmp = uploadedPhotoThumb ?: CustomCompanionStore.getOrDecodeBitmap(character.spec.id, character.spec.facePortraitBase64)
                    if (portraitBmp != null) {
                        val resampled = PhotoCompanionAnalyzer.rescanFromAdjustedMesh(portraitBmp, character.spec)
                        character.updateSpec(resampled)
                        onResampledSpec?.invoke(resampled)
                    }
                    invalidate()
                }
            }
        }
        return true
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastNanos == 0L) 0.016f else ((frameTimeNanos - lastNanos) / 1e9f).coerceIn(0.005f, 0.05f)
        lastNanos = frameTimeNanos
        t += dt
        modeT += dt
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h, com.combo.monkeycompanion.theme.Th.rgb(0x14, 0x17, 0x28), com.combo.monkeycompanion.theme.Th.rgb(0x26, 0x1D, 0x3A), Shader.TileMode.CLAMP)
        c.drawRoundRect(0f, 0f, w, h, 22f * d, 22f * d, bgPaint)

        val sp = character.spec
        val portraitBmp = uploadedPhotoThumb ?: CustomCompanionStore.getOrDecodeBitmap(sp.id, sp.facePortraitBase64)

        if (splitCompareMode && portraitBmp != null) {
            val midW = w * 0.5f
            // Left Half: Uploaded Photo / Segmented Head zoomed at exact same zoomLevel & pan
            c.save()
            c.clipRect(0f, 44f * d, midW - 2f * d, h - 8f * d)
            val pSize = (132f * d) * (0.7f + 0.35f * zoomLevel)
            val px = midW * 0.5f + panX * 0.5f
            val py = h * 0.54f + panY * 0.5f
            thumbRect.set(px - pSize / 2f, py - pSize / 2f, px + pSize / 2f, py + pSize / 2f)
            c.drawBitmap(portraitBmp, null, thumbRect, Paint(Paint.FILTER_BITMAP_FLAG))
            if (showFaceMesh) {
                sp.faceMesh.drawFaceIdWireframe(c, thumbRect, t * 0.35f, d, selectedMeshFeature)
            }
            c.restore()
            c.drawText("📷 Uploaded Photo (${"%.1f".format(zoomLevel)}x)", 14f * d, h - 14f * d, subPaint)

            // Center Divider
            c.drawLine(midW, 44f * d, midW, h - 10f * d, dividerPaint)

            // Right Half: Generated 60fps Look-Alike Character zoomed at exact same zoomLevel & pan
            c.save()
            c.clipRect(midW + 2f * d, 44f * d, w, h - 8f * d)
            drawCharacterInRegion(c, midW * 1.5f + panX * 0.5f, h, zoomLevel)
            c.restore()
            c.drawText("✨ Created Look-Alike (${"%.1f".format(zoomLevel)}x)", midW + 14f * d, h - 14f * d, subPaint)
        } else {
            // Full Stage View
            c.save()
            c.clipRect(0f, 42f * d, w, h)
            val centerX = (if (portraitBmp != null && zoomLevel < 1.8f) w * 0.56f else w * 0.5f) + panX
            drawCharacterInRegion(c, centerX, h, zoomLevel)
            c.restore()

            if (portraitBmp != null) {
                thumbRect.set(14f * d, 48f * d, 114f * d, 148f * d)
                c.drawRoundRect(thumbRect, 12f * d, 12f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = com.combo.monkeycompanion.theme.Th.rgb(0x26, 0x22, 0x38) })
                c.drawBitmap(portraitBmp, null, thumbRect, Paint(Paint.FILTER_BITMAP_FLAG))
                if (showFaceMesh) {
                    sp.faceMesh.drawFaceIdWireframe(c, thumbRect, t * 0.35f, d * 0.72f, selectedMeshFeature)
                }
                c.drawRoundRect(thumbRect, 12f * d, 12f * d, thumbBorder)
                c.drawText("📷 Tap/Drag Mesh", 14f * d, 162f * d, subPaint)
            }
        }

        // Floating 2.5x Magnifier Loupe above the user's finger when dragging or nudging a mesh feature!
        val loupeTarget = activeDraggingFeature ?: (if (t < loupeActiveUntil && showFaceMesh) selectedMeshFeature else null)
        if (loupeTarget != null && portraitBmp != null && !thumbRect.isEmpty) {
            drawMagnifierLoupe(c, portraitBmp, sp, loupeTarget)
        }

        c.drawText("✨ ${sp.name} · ${sp.faceMode.label.substringBefore(" (")} · Zoom ${"%.1f".format(zoomLevel)}x", 14f * d, 21f * d, labelPaint)
        val statusHint = if (showFaceMesh) {
            "🎯 Selected: ${selectedMeshFeature.label} — ${selectedMeshFeature.hint}"
        } else {
            "Pinch or drag to inspect · ${sp.gender.shortLabel} · ${sp.ageGroup.shortLabel}"
        }
        c.drawText(statusHint, 14f * d, 37f * d, subPaint)
    }

    private fun drawCharacterInRegion(c: Canvas, centerX: Float, stageH: Float, zoom: Float) {
        val wear = Wear(character)
        val sp = character.spec
        val heightFactor = sp.heightScale.coerceIn(0.75f, 1.25f)
        // As zoom increases from 1x to 5x, smoothly shift anchor downward so the head/face stays centered in the viewport!
        val sc = 1.95f * zoom * heightFactor * (d / 2.6f).coerceIn(0.75f, 1.35f)
        val headFocusShift = ((zoom - 1f) * 92f * d).coerceAtLeast(0f)
        val floorY = stageH - 28f * d + headFocusShift + panY

        if (zoom < 1.8f && !splitCompareMode) {
            c.drawOval(centerX - 110f * d, floorY - 10f * d, centerX + 110f * d, floorY + 12f * d, floorPaint)
        }

        var cx = centerX
        var cy = floorY
        var facing = 1f

        val inspectingFace = zoom >= 1.55f || splitCompareMode || showFaceMesh
        when (demoMode) {
            DemoMode.IDLE_WAVE -> {
                val state = if (inspectingFace) MonkeyState.IDLE else if ((t % 5f) < 2.5f) MonkeyState.WAVE else MonkeyState.DANCE
                character.fill(pose, state, t, modeT, animCtx, wear)
                pose.mouthSmile = 0.78f
                pose.eyesHappy = 0f
                if (inspectingFace) {
                    // Keep arms relaxed at sides and head steady so the sleeve NEVER obscures the chin/beard/collar!
                    pose.armLRaise = 10f
                    pose.armRRaise = 10f
                    pose.armLElbow = 0f
                    pose.armRElbow = 0f
                    pose.bodyRot = 0f
                    pose.headRot = 0f
                    pose.bodyY = 0f
                    pose.headY = 0f
                    pose.eyeOpen = 1.0f
                } else {
                    val blinkPhase = t % 3.6f
                    if (blinkPhase in 3.30f..3.42f) pose.eyeOpen = 0.12f else pose.eyeOpen = 1.0f
                }
            }
            DemoMode.FLIGHT_LOOP -> {
                val ang = modeT * 2.4f
                cx = centerX + cos(ang) * (48f * d)
                cy = floorY - 36f * d - (sin(ang * 2f) * 0.5f + 0.5f) * (42f * d)
                facing = if (-sin(ang) >= 0f) 1f else -1f
                character.fill(pose, MonkeyState.FALLING, t, modeT, animCtx, wear)
                pose.bodyRot = sin(ang) * 20f
                pose.cape = if (character.usesCapeFlight) 1f else 0f
                pose.capeWind = 0.95f
                pose.jets = if (character.usesHoverJets) 1f else 0f
                pose.palmGlow = if (character.usesHoverJets) 0.9f else 0f
            }
            DemoMode.KOVAM_ANGRY -> {
                animCtx.power = com.combo.monkeycompanion.brain.Superpower.KovamMode
                animCtx.powerPhase = PowerPhase.GROW
                animCtx.powerK = 0.7f + 0.25f * sin(t * 6f)
                character.fill(pose, MonkeyState.POWER, t, modeT, animCtx, wear)
                pose.anger = 0.95f; pose.steam = 0.9f; pose.size = 1.12f
            }
            DemoMode.NINJA_CLONES -> {
                animCtx.power = com.combo.monkeycompanion.brain.Superpower.NinjaSettai
                animCtx.powerPhase = PowerPhase.CLONES
                animCtx.powerK = 0.8f
                character.fill(pose, MonkeyState.POWER, t, modeT, animCtx, wear)
                pose.ninja = 1f; pose.clones = 1f
            }
            DemoMode.CYCLE_POWERS -> {
                val pws = character.powers
                val activePw = pws[(modeT / 2.2f).toInt() % pws.size.coerceAtLeast(1)]
                animCtx.power = activePw
                animCtx.powerPhase = PowerPhase.FLY
                animCtx.powerK = 0.75f
                character.fill(pose, MonkeyState.POWER, t, modeT, animCtx, wear)
            }
        }

        rig.layout(pose, cx, cy, sc * pose.size.coerceIn(0.8f, 1.25f), facing)

        if (zoom < 2.2f && (demoMode == DemoMode.FLIGHT_LOOP || character.usesHoverJets)) {
            val boost = if (demoMode == DemoMode.FLIGHT_LOOP) 1.25f else 0.75f
            for (side in intArrayOf(-1, 1)) {
                val fx = cx + side * 9f * sc
                val fy = cy - 4f * d
                val len = (24f + 8f * sin(t * 38f + side)) * sc * boost
                val wdt = 7f * sc
                jetPaint.shader = LinearGradient(
                    fx, fy, fx, fy + len,
                    intArrayOf(com.combo.monkeycompanion.theme.Th.rgb(0xFF, 0xF5, 0x9D), com.combo.monkeycompanion.theme.Th.rgb(0xFF, 0x98, 0x00), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                jetPath.reset()
                jetPath.moveTo(fx - wdt, fy)
                jetPath.quadTo(fx - wdt * 0.5f, fy + len * 0.6f, fx, fy + len)
                jetPath.quadTo(fx + wdt * 0.5f, fy + len * 0.6f, fx + wdt, fy)
                jetPath.close()
                c.drawPath(jetPath, jetPaint)
            }
            jetPaint.shader = null
        }

        renderer.draw(c, rig, pose, t)

        if (showFaceMesh) {
            val chRenderer = renderer as? com.combo.monkeycompanion.rig.ChibiHumanRenderer
            val (rx, topY, botY) = chRenderer?.headBounds() ?: Triple(25.8f, -71.5f, -6.8f)
            sp.faceMesh.drawOnCharacterHead(c, rig.head, rx, topY, botY, t * 0.35f, d, selectedMeshFeature)
        }
    }

    /**
     * Draws a 2.5x Magnified Circular Loupe (`radius = 42dp`) positioned above the user's fingertip
     * so their finger NEVER covers the eye, brow, lip, or chin point being aligned!
     */
    fun drawMagnifierLoupe(c: Canvas, portraitBmp: Bitmap, sp: CustomCompanionSpec, target: MeshFeatureTarget) {
        val (hx, hy) = sp.faceMesh.featureAnchorScreen(target, thumbRect)
        val r = 42f * d
        val lcx = hx.coerceIn(r + 10f * d, (width - r - 10f * d).coerceAtLeast(r + 12f * d))
        val candidateY = kotlin.math.min(if (loupeTouchY > 0f) loupeTouchY else hy, hy) - 68f * d
        val lcy = if (candidateY >= r + 44f * d) candidateY else (hy + 68f * d).coerceAtMost(height - r - 24f * d)

        c.save()
        loupeClipPath.reset()
        loupeClipPath.addCircle(lcx, lcy, r, Path.Direction.CW)
        c.clipPath(loupeClipPath)
        c.drawColor(com.combo.monkeycompanion.theme.Th.rgb(0x10, 0x12, 0x20))
        // Zoom 2.5x around (hx, hy) translated to (lcx, lcy)
        c.translate(lcx, lcy)
        c.scale(2.4f, 2.4f)
        c.translate(-hx, -hy)
        c.drawBitmap(portraitBmp, null, thumbRect, Paint(Paint.FILTER_BITMAP_FLAG))
        sp.faceMesh.drawFaceIdWireframe(c, thumbRect, t * 0.35f, d * 0.7f, target)
        c.restore()

        // Crisp gold border + center precision crosshair (+)
        c.drawCircle(lcx, lcy, r, loupeBorderPaint)
        val ch = 9f * d
        c.drawLine(lcx - ch, lcy, lcx + ch, lcy, loupeCrossPaint)
        c.drawLine(lcx, lcy - ch, lcx, lcy + ch, loupeCrossPaint)

        // Label pill under the loupe
        val pillW = 92f * d
        val pillH = 18f * d
        val pillRect = RectF(lcx - pillW / 2f, lcy + r - pillH * 0.6f, lcx + pillW / 2f, lcy + r + pillH * 0.4f)
        c.drawRoundRect(pillRect, 9f * d, 9f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = com.combo.monkeycompanion.theme.Th.rgb(0xFF, 0xD5, 0x4F) })
        c.drawText(
            "🔍 ${target.shortLabel}",
            lcx,
            pillRect.centerY() + 4f * d,
            Paint(labelPaint).apply { color = com.combo.monkeycompanion.theme.Th.rgb(0x1C, 0x14, 0x10); textSize = 10.2f * d; textAlign = Paint.Align.CENTER }
        )
    }
}

class CompanionStudioActivity : androidx.activity.ComponentActivity() {
    private val d by lazy { resources.displayMetrics.density }
    private val cream = com.combo.monkeycompanion.theme.Th.rgb(0xF3, 0xF4, 0xFA)
    private val brown = com.combo.monkeycompanion.theme.Th.rgb(0x1E, 0x22, 0x40)
    private val soft = com.combo.monkeycompanion.theme.Th.rgb(0x64, 0x6A, 0x8C)
    private val accent = com.combo.monkeycompanion.theme.Th.rgb(0x6C, 0x5C, 0xE7)
    private val purple = com.combo.monkeycompanion.theme.Th.rgb(0x09, 0x84, 0xE3)
    private val uiHandler = Handler(Looper.getMainLooper())

    private lateinit var draft: CustomCompanionSpec
    // Snapshot state invalidates Compose controls when an edit saves without rebuilding the screen.
    private var draftRevision by androidx.compose.runtime.mutableIntStateOf(0)
    private lateinit var previewChar: CustomCharacter
    private var stageView: StudioLiveStageView? = null
    private var lastUploadedPhoto: Bitmap? = null

    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatHub.init(this)
        MindHub.init(this)
        CustomCompanionStore.init(this)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)

        draft = CustomCompanionStore.activeSpec(this)
        previewChar = CustomCompanionRegistry.register(draft)
        rebuildUI()
    }

    private var activeNameInput: EditText? = null
    private var suppressNameWatcher = false

    private fun updateDraft(newDraft: CustomCompanionSpec, rebuild: Boolean = false) {
        draft = newDraft
        draftRevision++
        previewChar.updateSpec(draft)
        CustomCompanionStore.save(this, draft)
        if (ChatHub.settings.characterId.startsWith("custom_")) {
            ChatHub.settings.characterId = draft.id
            ChatHub.settings.applyCharacter()
        }
        stageView?.refreshSpec()
        if (rebuild) rebuildUI()
    }

    override fun onPause() {
        val typed = activeNameInput?.text?.toString()?.trim().orEmpty()
        if (typed.isNotBlank() && typed != draft.name) {
            draft = draft.copy(name = typed)
            previewChar.updateSpec(draft)
        }
        CustomCompanionStore.save(this, draft)
        if (ChatHub.settings.characterId.startsWith("custom_")) {
            ChatHub.settings.characterId = draft.id
            ChatHub.settings.applyCharacter()
        }
        com.combo.monkeycompanion.overlay.MonkeyOverlayService.syncCrew(this)
        super.onPause()
    }

    private fun runPhotoGenerationWithProgress(bmp: Bitmap) {
        lastUploadedPhoto = bmp
        val steps = arrayOf(
            "🕸️ Step 1/4: Scanning 68-Point Face-ID Landmark Mesh...",
            "✂️ Step 2/4: Segmenting 320×320 Smooth-Contour Head, Hair & Beard...",
            "🎨 Step 3/4: Bilateral Toon Polish + Skin & Shirt Colours...",
            "⚡ Step 4/4: Rigging 60fps Look-Alike & Opening 3.5x Zoom Compare..."
        )
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 15
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) }
        }
        val stepTv = TextView(this).apply {
            text = steps[0]; textSize = 14f; setTextColor(brown); gravity = Gravity.CENTER_HORIZONTAL
        }
        box.addView(bar); box.addView(stepTv)

        val dlg = AlertDialog.Builder(this)
            .setTitle("✨ 68-Point Face-ID Look-Alike Engine")
            .setView(box)
            .setCancelable(false)
            .create()
        runCatching { dlg.show() }

        Thread {
            val res = PhotoCompanionAnalyzer.analyze(bmp, draft)
            for (i in 1..3) {
                runCatching { Thread.sleep(220) }
                uiHandler.post {
                    bar.progress = (i + 1) * 25
                    stepTv.text = steps[i]
                }
            }
            uiHandler.post {
                runCatching { if (dlg.isShowing) dlg.dismiss() }
                CustomCompanionStore.save(this, res.spec)
                lastUploadedPhoto = res.portraitBitmap
                updateDraft(res.spec, rebuild = true)
                stageView?.uploadedPhotoThumb = res.portraitBitmap
                stageView?.splitCompareMode = true
                stageView?.zoomLevel = 3.0f
                Toast.makeText(this, "✨ ${res.spec.analysisSummary}", Toast.LENGTH_SHORT).show()
                if (askPersonTypeAfterUpload) {
                    askPersonTypeAfterUpload = false
                    showWhoIsInPhotoDialog()
                }
            }
        }.start()
    }

    private var askPersonTypeAfterUpload = false

    /**
     * Friendly 1-Tap "Who is in this Photo?" confirmation dialog so uploaded photos NEVER default to a 35yr man
     * when the user uploaded a child, girl, boy, or woman!
     */
    fun showWhoIsInPhotoDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val dlgNameInput = EditText(this).apply {
            setText(draft.name)
            hint = "Enter companion name (e.g. Priya, Anu, Kuttyma, Manik)"
            textSize = 15f; setTextColor(brown)
            setSingleLine(true)
        }
        box.addView(TextView(this).apply {
            text = "✏️ Companion Name:"
            textSize = 12.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(soft)
        })
        box.addView(dlgNameInput, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
        dlgNameInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val n = s?.toString()?.trim().orEmpty()
                if (n.isNotBlank()) {
                    draft = draft.copy(name = n)
                    previewChar.updateSpec(draft)
                    CustomCompanionStore.save(this@CompanionStudioActivity, draft)
                    stageView?.invalidate()
                }
            }
        })

        box.addView(TextView(this).apply {
            text = "Auto-detected: ${draft.gender.label} · ${draft.ageGroup.shortLabel} — Tap below to confirm or switch Person Type & Age in 1 tap:"
            textSize = 13.5f; setTextColor(brown)
            setPadding(0, 0, 0, dp(10))
        })

        var dlgRef: AlertDialog? = null
        val genderGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(6)) }
        val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(10)) }

        for ((idx, g) in listOf(CompanionGender.GIRL_CHILD, CompanionGender.BOY_CHILD, CompanionGender.FEMALE, CompanionGender.MALE).withIndex()) {
            val btn = pillButton(g.label, selected = draft.gender == g) {
                val updated = draft.applyGenderAndAge(newGender = g)
                CustomCompanionStore.save(this, updated)
                updateDraft(updated, rebuild = true)
                stageView?.uploadedPhotoThumb = lastUploadedPhoto
                stageView?.splitCompareMode = true
                stageView?.zoomLevel = 3.0f
                runCatching { dlgRef?.dismiss() }
            }
            val targetRow = if (idx < 2) r1 else r2
            targetRow.addView(btn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                if (idx % 2 == 0) rightMargin = dp(6)
            })
        }
        genderGrid.addView(r1)
        genderGrid.addView(r2)
        box.addView(genderGrid)

        box.addView(TextView(this).apply {
            text = "Or pick exact Age Range:"
            textSize = 12.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(soft)
            setPadding(0, dp(4), 0, dp(6))
        })
        val ageRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (ag in AgeGroup.entries) {
            ageRow.addView(pillButton(ag.shortLabel, selected = draft.ageGroup == ag) {
                val nextGender = when {
                    ag == AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.FEMALE -> CompanionGender.GIRL_CHILD
                    ag == AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.MALE -> CompanionGender.BOY_CHILD
                    ag != AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.GIRL_CHILD -> CompanionGender.FEMALE
                    ag != AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.BOY_CHILD -> CompanionGender.MALE
                    else -> draft.gender
                }
                val updated = draft.applyGenderAndAge(newGender = nextGender, newAge = ag)
                CustomCompanionStore.save(this, updated)
                updateDraft(updated, rebuild = true)
                stageView?.uploadedPhotoThumb = lastUploadedPhoto
                runCatching { dlgRef?.dismiss() }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(3) })
        }
        box.addView(ageRow)

        dlgRef = AlertDialog.Builder(this)
            .setTitle("✨ Who is in this Photo?")
            .setView(box)
            .setPositiveButton("Keep ${draft.gender.shortLabel} ✓", null)
            .create()
        runCatching { dlgRef.show() }
    }

    /**
     * v9.3: True 100% Fullscreen Edge-to-Edge Zoom & Mesh Inspector (`Theme_Black_NoTitleBar_Fullscreen`)
     * so two-finger pinch-to-zoom (`1x–6x`), 1-finger pan, and `[✋ Move Mesh]` have the entire phone screen!
     */
    /**
     * Builds the ergonomic Feature Target Chip Selector + Precision D-Pad (`◀ ▲ ▼ ▶ ➖ ➕`)
     * so the user can align any of the 10 facial features WITHOUT needing to poke tiny dots!
     */
    private fun buildMeshFeatureControls(stage: StudioLiveStageView, darkTheme: Boolean): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (darkTheme) com.combo.monkeycompanion.theme.Th.rgb(0x1B, 0x18, 0x2E) else com.combo.monkeycompanion.theme.Th.rgb(0xFF, 0xF8, 0xEB))
                setStroke(dp(1), if (darkTheme) com.combo.monkeycompanion.theme.Th.rgb(0x00, 0xE5, 0xFF) else accent)
            }
        }
        val statusTv = TextView(this).apply {
            text = "🎯 Active Mesh Target: ${stage.selectedMeshFeature.label} (Touch node, swipe trackpad, or tap ◀▲▼▶)"
            textSize = 11.8f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (darkTheme) com.combo.monkeycompanion.theme.Th.rgb(0x80, 0xDE, 0xEA) else brown)
            setPadding(0, 0, 0, dp(4))
        }
        box.addView(statusTv)

        val chipButtons = ArrayList<Pair<MeshFeatureTarget, Button>>()
        fun refreshChipHighlights(active: MeshFeatureTarget) {
            statusTv.text = "🎯 Active Mesh Target: ${active.label} — ${active.hint}"
            for ((t, btn) in chipButtons) {
                val sel = t == active
                btn.setTextColor(if (sel) Color.WHITE else brown)
                btn.background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(if (sel) accent else com.combo.monkeycompanion.theme.Th.rgb(0xFF, 0xF9, 0xEE))
                    setStroke(dp(1), if (sel) accent else com.combo.monkeycompanion.theme.Th.rgb(0xDD, 0xE0, 0xF0))
                }
            }
        }
        stage.onFeatureSelected = { refreshChipHighlights(it) }

        val chipScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(5)) }
        for (target in listOf(
            MeshFeatureTarget.WHOLE_MESH,
            MeshFeatureTarget.HAIRLINE_TOP,
            MeshFeatureTarget.LEFT_BROW,
            MeshFeatureTarget.RIGHT_BROW,
            MeshFeatureTarget.LEFT_EYE,
            MeshFeatureTarget.RIGHT_EYE,
            MeshFeatureTarget.NOSE_TIP,
            MeshFeatureTarget.MOUTH,
            MeshFeatureTarget.JAW_LEFT,
            MeshFeatureTarget.JAW_RIGHT,
            MeshFeatureTarget.CHIN_BOTTOM,
        )) {
            val b = pillButton(target.shortLabel, selected = stage.selectedMeshFeature == target) {
                stage.showFaceMesh = true
                stage.moveMeshMode = true
                if (!stage.splitCompareMode && stage.zoomLevel < 2.0f) {
                    stage.splitCompareMode = true
                    stage.zoomLevel = 3.0f
                }
                stage.selectedMeshFeature = target
                refreshChipHighlights(target)
            }
            chipButtons += target to b
            chipRow.addView(b, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = dp(4) })
        }
        chipScroll.addView(chipRow)
        box.addView(chipScroll)

        // Precision D-Pad & Width/Size Nudge Row: [◀] [▲] [▼] [▶] [➖ Narrow] [➕ Wide] [🎨 Sync] [↺]
        val dpadRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val step = 0.018f
        dpadRow.addView(pillButton("◀", false) { stage.nudgeSelectedFeature(-step, 0f, 0f) },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.85f).apply { rightMargin = dp(3) })
        dpadRow.addView(pillButton("▲", false) { stage.nudgeSelectedFeature(0f, -step, 0f) },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.85f).apply { rightMargin = dp(3) })
        dpadRow.addView(pillButton("▼", false) { stage.nudgeSelectedFeature(0f, step, 0f) },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.85f).apply { rightMargin = dp(3) })
        dpadRow.addView(pillButton("▶", false) { stage.nudgeSelectedFeature(step, 0f, 0f) },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.85f).apply { rightMargin = dp(3) })
        dpadRow.addView(pillButton("➖ Size", false) { stage.nudgeSelectedFeature(0f, 0f, -0.05f) },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.15f).apply { rightMargin = dp(3) })
        dpadRow.addView(pillButton("➕ Size", false) { stage.nudgeSelectedFeature(0f, 0f, +0.05f) },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.15f).apply { rightMargin = dp(3) })
        dpadRow.addView(pillButton("🎨 Copy", true) {
            val portraitBmp = stage.uploadedPhotoThumb ?: CustomCompanionStore.getOrDecodeBitmap(draft.id, draft.facePortraitBase64)
            if (portraitBmp != null) {
                val synced = PhotoCompanionAnalyzer.rescanFromAdjustedMesh(portraitBmp, previewChar.spec)
                CustomCompanionStore.save(this, synced)
                updateDraft(synced, rebuild = false)
                stage.refreshSpec()
                Toast.makeText(this, "✨ Copied mesh structure & colors to 2D character!", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.25f).apply { rightMargin = dp(3) })
        dpadRow.addView(pillButton("↺", false) {
            val resetMesh = com.combo.monkeycompanion.character.FaceLandmarkMesh()
            val nextSpec = previewChar.spec.copy(faceMesh = resetMesh)
            CustomCompanionStore.save(this, nextSpec)
            updateDraft(nextSpec, rebuild = false)
            stage.refreshSpec()
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.75f))
        box.addView(dpadRow)
        return box
    }

    fun openFullscreenZoomInspector() {
        val dlg = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val stage = StudioLiveStageView(this, previewChar).apply {
            uploadedPhotoThumb = lastUploadedPhoto
            splitCompareMode = true
            zoomLevel = 3.5f
            showFaceMesh = true
            moveMeshMode = true
            selectedMeshFeature = stageView?.selectedMeshFeature ?: MeshFeatureTarget.WHOLE_MESH
            onMeshAdjusted = { nextMesh ->
                draft = draft.copy(faceMesh = nextMesh)
                CustomCompanionStore.save(this@CompanionStudioActivity, draft)
                stageView?.refreshSpec()
            }
            onResampledSpec = { nextSpec ->
                draft = nextSpec
                CustomCompanionStore.save(this@CompanionStudioActivity, draft)
                stageView?.refreshSpec()
            }
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(com.combo.monkeycompanion.theme.Th.rgb(0x0D, 0x0B, 0x18))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        // 1. Compact Top Header Bar with Title + [👤 Sex/Age] + [✓ Done]
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(6), dp(4))
        }
        topBar.addView(TextView(this).apply {
            text = "🔍 Fullscreen Mesh & Face Editor"
            textSize = 15f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        topBar.addView(pillButton("👤 ${draft.gender.shortLabel}", selected = false) {
            showWhoIsInPhotoDialog()
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = dp(6) })
        topBar.addView(pillButton("✓ Done", selected = true) {
            dlg.dismiss()
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        wrap.addView(topBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // 2. Massive Full-Screen Canvas (LayoutParams(MATCH_PARENT, 0, 1f))
        wrap.addView(stage, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // 3. Feature Target Chip Bar + Precision D-Pad (◀ ▲ ▼ ▶ ➖ ➕ 🎨 Copy)
        wrap.addView(buildMeshFeatureControls(stage, darkTheme = true),
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) })

        // 4. Compact Bottom Control Dock (Zoom + Split + Mesh + Trackpad Mode Toggle)
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
        }
        for ((z, lbl) in listOf(1f to "1x", 2f to "2x", 3.5f to "3.5x", 5f to "5x")) {
            row1.addView(pillButton(lbl, selected = false) { stage.zoomLevel = z },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.85f).apply { rightMargin = dp(3) })
        }
        row1.addView(pillButton("🔍 Split", selected = true) { stage.splitCompareMode = !stage.splitCompareMode },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.05f).apply { rightMargin = dp(3) })
        row1.addView(pillButton("🕸️ Mesh", selected = stage.showFaceMesh) { stage.showFaceMesh = !stage.showFaceMesh },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.05f).apply { rightMargin = dp(3) })
        lateinit var moveMeshBtn: Button
        moveMeshBtn = pillButton("✋ Drag Mesh: ON", selected = true) {
            stage.moveMeshMode = !stage.moveMeshMode
            if (stage.moveMeshMode) stage.showFaceMesh = true
            moveMeshBtn.text = if (stage.moveMeshMode) "✋ Drag Mesh: ON" else "✋ Pan View"
        }
        row1.addView(moveMeshBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.45f))
        wrap.addView(row1)

        dlg.setContentView(wrap)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(com.combo.monkeycompanion.theme.Th.rgb(0x0D, 0x0B, 0x18)))
            setLayout(MATCH_PARENT, MATCH_PARENT)
        }
        runCatching { dlg.show() }
    }

    private var studioTabIndex = 0

    private fun rebuildUI() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(32))
        }

        col.addView(TextView(this).apply {
            text = "Character Creator Studio"
            textSize = 24f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        col.addView(TextView(this).apply {
            text = "Shape your companion in the live preview, then customize its appearance, movement and personality."
            textSize = 12.5f; setTextColor(soft); gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(2), 0, dp(10))
        })

        // 1. HUGE 360dp Live 60fps Stage + 1x..5x Zoom Bar + Split-Screen Compare + Face-ID Mesh Toggle
        val stage = StudioLiveStageView(this, previewChar).also {
            it.uploadedPhotoThumb = lastUploadedPhoto
            stageView = it
        }
        val previewFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(Color.WHITE) }
        }
        previewFrame.addView(TextView(this).apply {
            text = "LIVE PREVIEW"; textSize = 11f; setTypeface(typeface, Typeface.BOLD); setTextColor(accent)
            setPadding(dp(8), dp(4), 0, dp(8))
        })
        previewFrame.addView(stage, LinearLayout.LayoutParams(MATCH_PARENT, dp(220)))
        col.addView(previewFrame, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        // Multi-Level Zoom Buttons: [1x Full] [2x Upper] [3.5x Face] [5x Ultra] [🎯 Reset]
        val zoomControls = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                androidx.compose.material3.MaterialTheme {
                    StudioPreviewControls(stage)
                }
            }
        }
        col.addView(zoomControls)

        stage.onMeshAdjusted = { nextMesh ->
            draft = draft.copy(faceMesh = nextMesh)
            CustomCompanionStore.save(this@CompanionStudioActivity, draft)
        }
        stage.onResampledSpec = { nextSpec ->
            draft = nextSpec
            CustomCompanionStore.save(this@CompanionStudioActivity, draft)
        }

        // Split-Screen Compare + 68-Pt Face-ID Mesh + Trackpad Drag + Fullscreen Inspector Row
        val inspectionControls = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                androidx.compose.material3.MaterialTheme {
                    StudioInspectionControls(stage, ::openFullscreenZoomInspector)
                }
            }
        }
        col.addView(inspectionControls)

        // Interactive 10-Feature Mesh Selector + Precision D-Pad (◀ ▲ ▼ ▶ ➖ ➕ 🎨 Copy) right on the main screen!
        col.addView(buildMeshFeatureControls(stage, darkTheme = false),
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })

        // Concise Visual Guide: "How 68-Pt Mesh Copying Works"
        val guideBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(com.combo.monkeycompanion.theme.Th.rgb(0xE8, 0xF5, 0xE9))
                setStroke(dp(1), com.combo.monkeycompanion.theme.Th.rgb(0x43, 0xA0, 0x47))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }
        guideBox.addView(TextView(this).apply {
            text = "🕸️ How 68-Pt Mesh Copying Works (Zero Tiny-Dot Poking Required!):"
            textSize = 12.2f; setTypeface(typeface, Typeface.BOLD); setTextColor(com.combo.monkeycompanion.theme.Th.rgb(0x1B, 0x5E, 0x20))
        })
        guideBox.addView(TextView(this).apply {
            text = "1️⃣ Tap a feature chip above (💇 Hairline, 🤨 Brows, 👁️ Eyes, 👃 Nose, 👄 Mouth, 🧔 Jaw, or 🧔 Chin). " +
                "2️⃣ Swipe anywhere on the preview like a trackpad (with a 2.5x Magnifier Loupe above your finger!) OR tap ◀ ▲ ▼ ▶ ➖ ➕. " +
                "3️⃣ Watch the 2D character on the right reshape 1:1 in real time & tap [🎨 Copy] to sample your exact skin, hair & lip colors!"
            textSize = 11.4f; setTextColor(brown); setPadding(0, dp(2), 0, 0)
        })
        col.addView(guideBox)

        val demoControls = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                androidx.compose.material3.MaterialTheme {
                    StudioDemoControls(stage)
                }
            }
        }
        col.addView(demoControls, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = dp(10)
        })

        // 1B. Prominent Companion Name + 1-Tap Person Type (Girl / Boy / Woman / Man) & Age Selector Bar right below the stage!
        val personCard = card("👤 Companion Name, Sex & Age (1-Tap Selector)")
        val topNameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        topNameRow.addView(TextView(this).apply {
            text = "✏️ Name:"
            textSize = 13.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
            setPadding(0, 0, dp(8), 0)
        })
        val topNameEdit = EditText(this).apply {
            setText(draft.name)
            hint = "Enter Name (e.g. Priya, Kuttyma, Manik)"
            textSize = 15f; setTextColor(brown)
            setSingleLine(true)
        }
        activeNameInput = topNameEdit
        topNameEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (suppressNameWatcher) return
                val typed = s?.toString()?.trim().orEmpty()
                if (typed.isNotBlank() && typed != draft.name) {
                    draft = draft.copy(name = typed)
                    previewChar.updateSpec(draft)
                    CustomCompanionStore.save(this@CompanionStudioActivity, draft)
                    stageView?.invalidate()
                }
            }
        })
        topNameRow.addView(topNameEdit, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        topNameRow.addView(pillButton("➕ New Slot", selected = false) {
            val typed = topNameEdit.text?.toString()?.trim().orEmpty().ifBlank { "Companion ${CustomCompanionRegistry.allCharacters().size + 1}" }
            val newCh = CustomCompanionStore.saveAsNewCompanion(this, draft.copy(name = typed))
            ChatHub.settings.characterId = newCh.id
            ChatHub.settings.applyCharacter()
            updateDraft(newCh.spec, rebuild = true)
            Toast.makeText(this, "➕ Created new companion '${newCh.name}' in Characters list!", Toast.LENGTH_SHORT).show()
        })
        personCard.addView(topNameRow)
        personCard.addView(TextView(this).apply {
            text = "Switch anytime in 1 tap — Girl, Boy, Woman, or Man (never stuck on 35yr man!):"
            textSize = 12f; setTextColor(soft); setPadding(0, 0, 0, dp(6))
        })
        // Material 3 selector; keep the existing spec transformation, save and rebuild.
        val genderControls = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                androidx.compose.material3.MaterialTheme {
                    StudioGenderControls(draft.gender) { gender ->
                        val updated = draft.applyGenderAndAge(newGender = gender)
                        CustomCompanionStore.save(this@CompanionStudioActivity, updated)
                        updateDraft(updated, rebuild = true)
                    }
                }
            }
        }
        personCard.addView(genderControls)

        val ageControls = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                androidx.compose.material3.MaterialTheme {
                    StudioAgeControls(draft.ageGroup) { age ->
                        val nextGender = when {
                            age == AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.FEMALE -> CompanionGender.GIRL_CHILD
                            age == AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.MALE -> CompanionGender.BOY_CHILD
                            age != AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.GIRL_CHILD -> CompanionGender.FEMALE
                            age != AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.BOY_CHILD -> CompanionGender.MALE
                            else -> draft.gender
                        }
                        val updated = draft.applyGenderAndAge(newGender = nextGender, newAge = age)
                        CustomCompanionStore.save(this@CompanionStudioActivity, updated)
                        updateDraft(updated, rebuild = true)
                    }
                }
            }
        }
        personCard.addView(ageControls)
        col.addView(personCard)

        // Brain controls are shared settings, not stored in the character draft.
        val brainCard = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                StudioBrainControls(
                    enabled = ChatHub.isBrainEnabled(),
                    status = ChatHub.giveBrainStatusLine(),
                    onConfigure = {
                        GiveBrainDialog.show(
                            act = this@CompanionStudioActivity,
                            onChanged = { rebuildUI() },
                            onRunAiPhotoModel = { runAiBrainPhotoModeling() }
                        )
                    },
                    onToggle = {
                        ChatHub.setBrainEnabled(!ChatHub.isBrainEnabled())
                        rebuildUI()
                    },
                    onModel = { runAiBrainPhotoModeling() }
                )
            }
        }
        col.addView(brainCard)

        // Compose photo and face editor: existing updateDraft path preserves persistence and preview.
        val photoCard = card("1. Upload Photo, Face Mode & Fine-Tune Alignment")
        photoCard.addView(androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                StudioPhotoFaceControls(
                    analysisSummary = draft.analysisSummary,
                    suggestionPrompt = draft.suggestionPrompt,
                    bodyDetectedClearly = draft.bodyDetectedClearly,
                    faceModes = FaceMode.entries.map { it.label },
                    selectedFaceMode = draft.faceMode.label,
                    offsetX = draft.faceOffsetX,
                    offsetY = draft.faceOffsetY,
                    zoom = draft.faceZoomScale,
                    onPickPhoto = { pickPhotoFromGallery() },
                    onBodyTemplate = { template -> updateDraft(draft.applyBodyTemplate(template), rebuild = true) },
                    onFaceMode = { label ->
                        FaceMode.entries.firstOrNull { it.label == label }?.let { mode ->
                            updateDraft(draft.copy(faceMode = mode), rebuild = true)
                        }
                    },
                    onNudge = { dx, dy ->
                        updateDraft(draft.copy(
                            faceOffsetX = (draft.faceOffsetX + dx).coerceIn(-20f, 20f),
                            faceOffsetY = (draft.faceOffsetY + dy).coerceIn(-20f, 20f)
                        ), rebuild = true)
                    },
                    onZoom = { delta -> updateDraft(draft.copy(faceZoomScale = (draft.faceZoomScale + delta).coerceIn(0.80f, 1.35f)), rebuild = true) },
                    onReset = { updateDraft(draft.copy(faceOffsetX = 0f, faceOffsetY = 0f, faceZoomScale = 1.0f), rebuild = true) }
                )
            }
        })
        col.addView(photoCard)

        // Quick sample presets keep the existing generation and persistence callbacks.
        val presetCard = card("⚡ 1-Tap Look-Alike Sample Archetypes")
        presetCard.addView(androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                draftRevision
                StudioPresetControls(
                    selected = listOf(
                        draft.bodyBuild == BodyBuild.MID_BODY && draft.ageGroup == AgeGroup.ADULT_REGULAR,
                        draft.bodyBuild == BodyBuild.BULKY_HEAVY,
                        draft.gender == CompanionGender.FEMALE,
                        draft.gender == CompanionGender.GIRL_CHILD,
                        draft.gender == CompanionGender.BOY_CHILD,
                        draft.bodyBuild == BodyBuild.SLIM && draft.gender == CompanionGender.MALE && draft.ageGroup == AgeGroup.ADULT_REGULAR
                    ),
                    onSelect = { index ->
                        when (index) {
                            0 -> runPhotoGenerationWithProgress(createSampleAdult35MidBodyBitmap())
                            1 -> runPhotoGenerationWithProgress(createSampleBulkyPersonBitmap())
                            2 -> updateDraft(CustomCompanionPresets.womanAdult(draft), rebuild = true)
                            3 -> runPhotoGenerationWithProgress(createSamplePortraitBitmap())
                            4 -> updateDraft(CustomCompanionPresets.child5YrBoy(draft), rebuild = true)
                            5 -> runPhotoGenerationWithProgress(createSampleSlimPersonBitmap())
                        }
                    }
                )
            }
        })
        col.addView(presetCard)

        // 4. Body Build, Proportions & Age Card
        val bodyCard = card("2. Body Build, Age Proportions & Width Sliders")
        bodyCard.addView(androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                draftRevision // Observe edits that do not rebuild the Studio.
                StudioBodyControls(draft.bodyBuild, draft.bodyWidthScale, draft.shoulderScale, draft.ageGroup,
                    onBuild = { bb -> updateDraft(draft.copy(bodyBuild = bb, bodyWidthScale = bb.defaultWidthScale, shoulderScale = bb.defaultShoulderScale), rebuild = true) },
                    onWidth = { updateDraft(draft.copy(bodyWidthScale = it)) },
                    onShoulders = { updateDraft(draft.copy(shoulderScale = it)) },
                    onAge = { ag -> updateDraft(draft.applyGenderAndAge(newGender = draft.gender, newAge = ag), rebuild = true) })
            }
        })
        col.addView(bodyCard)

        // 5. Facial Hair, Hair Style & Outfit Card
        val lookCard = card("3. Facial Hair (Beard/Moustache), Hair Style & Outfit")
        val nameRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val nameInput = EditText(this).apply {
            setText(draft.name)
            hint = "Name (e.g. Priya, Kuttyma, Manik)"
            textSize = 15f; setTextColor(brown)
            setSingleLine(true)
        }
        nameInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (suppressNameWatcher) return
                val typed = s?.toString()?.trim().orEmpty()
                if (typed.isNotBlank() && typed != draft.name) {
                    draft = draft.copy(name = typed)
                    previewChar.updateSpec(draft)
                    CustomCompanionStore.save(this@CompanionStudioActivity, draft)
                    suppressNameWatcher = true
                    topNameEdit.setText(typed)
                    suppressNameWatcher = false
                    stageView?.invalidate()
                }
            }
        })
        nameRow.addView(nameInput, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        nameRow.addView(pillButton("Set Name ✓", selected = true) {
            val n = nameInput.text?.toString()?.trim().orEmpty().ifBlank { topNameEdit.text?.toString()?.trim().orEmpty().ifBlank { draft.name } }
            updateDraft(draft.copy(name = n), rebuild = true)
            Toast.makeText(this, "✏️ Name set to '$n'!", Toast.LENGTH_SHORT).show()
        })
        lookCard.addView(nameRow)

        lookCard.addView(androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                draftRevision // Observe edits that do not rebuild the Studio.
                StudioAppearanceControls(
                    draft.facialHair, draft.hairStyle, draft.outfitStyle,
                    draft.glasses, draft.cape, draft.hasBindi, draft.hairBow, draft.jasmine, draft.wings,
                    onFacialHair = { updateDraft(draft.copy(facialHair = it), rebuild = true) },
                    onHairStyle = { updateDraft(draft.copy(hairStyle = it), rebuild = true) },
                    onOutfit = { updateDraft(draft.copy(outfitStyle = it), rebuild = true) },
                    onGlasses = { updateDraft(draft.copy(glasses = it)) },
                    onCape = { updateDraft(draft.copy(cape = it)) },
                    onBindi = { updateDraft(draft.copy(hasBindi = it)) },
                    onBow = { updateDraft(draft.copy(hairBow = it)) },
                    onJasmine = { updateDraft(draft.copy(jasmine = it)) },
                    onWings = { updateDraft(draft.copy(wings = it)) })
            }
        })
        col.addView(lookCard)

        // 6. Flight / Movement Style & Multi-Select Superpowers Card
        val powerCard = card("Flight & superpowers (${draft.selectedPowers.size}/${CustomPowerCatalog.OPTIONS.size})")
        powerCard.addView(androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                draftRevision // Observe edits that do not rebuild the Studio.
                StudioPowerControls(draft.flightStyle, draft.selectedPowers,
                    onFlight = { fl ->
                        updateDraft(draft.copy(flightStyle = fl), rebuild = true)
                        if (fl != FlightStyle.WALK_AND_HOP) stageView?.demoMode = StudioLiveStageView.DemoMode.FLIGHT_LOOP
                    },
                    onAll = {
                        updateDraft(draft.copy(selectedPowers = CustomPowerCatalog.selectAllPowers()), rebuild = true)
                        stageView?.demoMode = StudioLiveStageView.DemoMode.CYCLE_POWERS
                    },
                    onTop = { updateDraft(draft.copy(selectedPowers = setOf("ninja", "kovam", "superboy_flight", "ironboy_flight")), rebuild = true) },
                    onToggle = { id, enabled ->
                        val next = draft.selectedPowers.toMutableSet()
                        if (enabled) next.add(id) else if (next.size > 1) next.remove(id)
                        updateDraft(draft.copy(selectedPowers = next), rebuild = false)
                    })
            }
        })
        col.addView(powerCard)

        // 7. Per-Character Behaviour Sliders & Diary Limits Link
        val behCard = card("Personality & behaviour")
        behCard.addView(androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                draftRevision // Observe edits that do not rebuild the Studio.
                StudioBehaviourControls(draft.playfulness, draft.chattiness, draft.curiosity, draft.energy, draft.boldness,
                    onPlayfulness = { updateDraft(draft.copy(playfulness = it)) },
                    onChattiness = { updateDraft(draft.copy(chattiness = it)) },
                    onCuriosity = { updateDraft(draft.copy(curiosity = it)) },
                    onEnergy = { updateDraft(draft.copy(energy = it)) },
                    onBoldness = { updateDraft(draft.copy(boldness = it)) },
                    onDiary = {
                        CustomCompanionStore.save(this@CompanionStudioActivity, draft)
                        startActivity(Intent(this@CompanionStudioActivity, DiaryActivity::class.java).putExtra("character", draft.id))
                    })
            }
        })
        col.addView(behCard)

        // 8. Save, Put on Screen, Export & Import .settai Character
        val saveCard = card("Save & share your character")
        saveCard.addView(actionButton("✅ Save & Put ${draft.name} on Screen Now!", primary = true) {
            val finalName = topNameEdit.text?.toString()?.trim().orEmpty()
                .ifBlank { nameInput.text?.toString()?.trim().orEmpty() }
                .ifBlank { draft.name }
            val savedSpec = draft.copy(name = finalName)
            CustomCompanionStore.save(this, savedSpec)
            ChatHub.settings.characterId = savedSpec.id
            ChatHub.settings.applyCharacter()
            MindHub.flush()
            com.combo.monkeycompanion.overlay.MonkeyOverlayService.syncCrew(this)
            Toast.makeText(this, "✨ ${savedSpec.name} is now your active companion!", Toast.LENGTH_SHORT).show()
            finish()
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })

        saveCard.addView(actionButton("➕ Save as New Companion in Roster (Keep Previous Too!)", primary = false) {
            val finalName = topNameEdit.text?.toString()?.trim().orEmpty()
                .ifBlank { nameInput.text?.toString()?.trim().orEmpty() }
                .ifBlank { draft.name }
            val newCh = CustomCompanionStore.saveAsNewCompanion(this, draft.copy(name = finalName))
            ChatHub.settings.characterId = newCh.id
            ChatHub.settings.applyCharacter()
            MindHub.flush()
            com.combo.monkeycompanion.overlay.MonkeyOverlayService.syncCrew(this)
            Toast.makeText(this, "➕ Added ${newCh.name} to your Characters Roster!", Toast.LENGTH_SHORT).show()
            finish()
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })

        val ioRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ioRow.addView(actionButton("📤 Export .settai", primary = false) { exportCharacter() },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        ioRow.addView(actionButton("📥 Import .settai", primary = false) { promptImportCharacter() },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        saveCard.addView(ioRow)
        col.addView(saveCard)

        // Keep the live stage visible while the user edits one focused group at a time.
        // Reparent existing controls: callbacks, draft persistence and custom mesh stay intact.
        val allSections = (0 until col.childCount).map { col.getChildAt(it) }
        require(allSections.size == 17) {
            "Studio layout changed: expected 17 sections, found ${allSections.size}"
        }
        val preview = allSections[2]
        val saveActions = allSections[16]
        val tabNames = listOf("Face", "Body", "Style", "Motion", "Behaviour", "Powers")
        val tabMembers = listOf(
            listOf(3, 4, 5, 6, 10),
            listOf(8, 11, 12),
            listOf(13),
            listOf(7),
            listOf(9, 15),
            listOf(14)
        )
        for (child in allSections.drop(3)) col.removeView(child)
        col.removeView(preview)
        // Compose owns navigation; the native stage and editing views remain intact.
        val tabContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val selectedTab = androidx.compose.runtime.mutableIntStateOf(studioTabIndex.coerceIn(tabNames.indices))
        fun displayTab(index: Int) {
            val next = index.coerceIn(tabNames.indices)
            studioTabIndex = next
            selectedTab.intValue = next
            tabContent.removeAllViews()
            tabMembers[next].forEach { originalIndex ->
                val view = allSections[originalIndex]
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                tabContent.addView(view)
            }
        }
        val tabCompose = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                androidx.compose.material3.MaterialTheme {
                    StudioMaterialTabs(selectedTab.intValue, ::displayTab)
                }
            }
        }
        col.addView(tabCompose, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        })
        col.addView(tabContent)
        col.addView(saveActions)
        displayTab(selectedTab.intValue)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(cream)
            isFillViewport = true
            addView(col)
        }
        val studioRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(cream) }
        studioRoot.addView(preview, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        studioRoot.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(studioRoot)
        MaterialScreenPolish.apply(this, preserveCustomButtons = true)
    }

    private fun runAiBrainPhotoModeling() {
        if (!ChatHub.isBrainEnabled()) {
            Toast.makeText(this, "🔒 AI Brain is OFF! Tap '🧠 Give Brain' first to enable AI modeling.", Toast.LENGTH_LONG).show()
            GiveBrainDialog.show(this, onChanged = { rebuildUI() }, onRunAiPhotoModel = { runAiBrainPhotoModeling() })
            return
        }
        val portraitBmp = stageView?.uploadedPhotoThumb ?: lastUploadedPhoto ?: CustomCompanionStore.getOrDecodeBitmap(draft.id, draft.facePortraitBase64)
        if (portraitBmp != null) {
            val synced = PhotoCompanionAnalyzer.rescanFromAdjustedMesh(portraitBmp, draft)
            updateDraft(synced, rebuild = false)
        }
        if (ChatHub.hasRealBrain()) {
            Toast.makeText(this, "🧠 AI Brain (${ChatHub.brainLabel()}) refining ${draft.name}'s look-alike…", Toast.LENGTH_SHORT).show()
            val prompt = "Refine look-alike traits for ${draft.name} (${draft.gender.label}, ${draft.ageGroup.shortLabel}, ${draft.hairStyle.label}, ${draft.outfitStyle.label}) in 1 short sentence."
            ChatHub.quip(prompt) { reply ->
                val note = reply?.takeIf { it.isNotBlank() } ?: "AI Brain + 68-Pt Mesh synced for ${draft.name}!"
                updateDraft(draft.copy(suggestionPrompt = "🧠 AI Brain Modeled: $note"), rebuild = true)
            }
        } else {
            updateDraft(
                draft.copy(suggestionPrompt = "🧠 AI Brain Enabled (Add your API key in Give Brain for live cloud LLM refinement — 68-Pt Mesh synced!)"),
                rebuild = true
            )
            Toast.makeText(this, "✨ 68-Pt Mesh synced! Add an API key in Give Brain for cloud AI refinement.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickPhotoFromGallery() {
        runCatching {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, REQ_PICK_PHOTO)
        }.onFailure {
            runPhotoGenerationWithProgress(createSampleAdult35MidBodyBitmap())
        }
    }

    private fun exportCharacter() {
        CustomCompanionStore.save(this, draft)
        val json = CustomCompanionStore.exportToJson(draft)
        val dir = File(filesDir, "companions").apply { mkdirs() }
        val file = File(dir, "${draft.name.lowercase().replace(Regex("[^a-z0-9]+"), "_")}.settai.json")
        runCatching { file.writeText(json) }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Settai Companion: ${draft.name} (.settai.json)")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        startActivity(Intent.createChooser(send, "Export ${draft.name} (.settai)"))
    }

    private fun promptImportCharacter() {
        val input = EditText(this).apply {
            hint = "Paste .settai.json text here, or tap 'Pick File' below"
            minLines = 3
        }
        AlertDialog.Builder(this)
            .setTitle("📥 Import .settai Companion")
            .setView(input)
            .setPositiveButton("Import JSON") { _, _ ->
                val txt = input.text?.toString()?.trim().orEmpty()
                val ch = CustomCompanionStore.importFromJson(this, txt)
                if (ch != null) {
                    updateDraft(ch.spec, rebuild = true)
                    Toast.makeText(this, "Imported ${ch.name}!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Could not parse .settai JSON", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Pick .settai File") { _, _ ->
                runCatching {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    startActivityForResult(i, REQ_IMPORT_SETTAI)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri: Uri = data.data!!
        if (requestCode == REQ_PICK_PHOTO) {
            runCatching {
                val bmp = PhotoCompanionAnalyzer.decodeBitmapWithExif(this, uri)
                if (bmp != null) {
                    askPersonTypeAfterUpload = true
                    runPhotoGenerationWithProgress(bmp)
                }
            }
        } else if (requestCode == REQ_IMPORT_SETTAI) {
            runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { r ->
                    val json = r.readText()
                    val ch = CustomCompanionStore.importFromJson(this, json)
                    if (ch != null) {
                        updateDraft(ch.spec, rebuild = true)
                        Toast.makeText(this, "✨ Imported ${ch.name}!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun card(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(14))
        background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
        addView(TextView(context).apply {
            text = title; textSize = 15.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
            setPadding(0, 0, 0, dp(6))
        })
    }

    private fun subHeader(text: String) = TextView(this).apply {
        this.text = text; textSize = 12.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(soft)
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun pillButton(label: String, selected: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 11.8f
        setTextColor(if (selected) Color.WHITE else brown)
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(if (selected) accent else com.combo.monkeycompanion.theme.Th.rgb(0xFF, 0xF9, 0xEE))
            setStroke(dp(1), if (selected) accent else com.combo.monkeycompanion.theme.Th.rgb(0xDD, 0xE0, 0xF0))
        }
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setOnClickListener { onClick() }
    }

    private fun actionButton(label: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f
        setTextColor(if (primary) Color.WHITE else brown)
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(if (primary) purple else com.combo.monkeycompanion.theme.Th.rgb(0xF5, 0xE6, 0xD0))
        }
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { onClick() }
    }

    private fun checkRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = label; isChecked = checked; textSize = 13.5f; setTextColor(brown)
        setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
    }

    private fun sliderRow(label: String, value: Float, onChange: (Float) -> Unit): View {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        val tv = TextView(this).apply {
            text = "$label: ${(value * 100).toInt()}%"
            textSize = 12.8f; setTextColor(brown)
        }
        val sb = SeekBar(this).apply {
            max = 100
            progress = (value * 100).toInt().coerceIn(5, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = (progress.coerceIn(5, 100)) / 100f
                    tv.text = "$label: ${(v * 100).toInt()}%"
                    if (fromUser) onChange(v)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        wrap.addView(tv); wrap.addView(sb)
        return wrap
    }

    companion object {
        private const val REQ_PICK_PHOTO = 901
        private const val REQ_IMPORT_SETTAI = 902

        fun createSampleAdult35MidBodyBitmap(w: Int = 240, h: Int = 300): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.rgb(0xF2, 0xF4, 0xF7))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.rgb(0x1C, 0x15, 0x12)
            c.drawOval(RectF(w * 0.33f, h * 0.12f, w * 0.67f, h * 0.34f), p)
            p.color = Color.rgb(0xDC, 0xA2, 0x76)
            c.drawOval(RectF(w * 0.34f, h * 0.17f, w * 0.66f, h * 0.45f), p)
            p.color = Color.rgb(0x1C, 0x15, 0x12)
            c.drawArc(RectF(w * 0.33f, h * 0.13f, w * 0.67f, h * 0.28f), 180f, 180f, true, p)
            p.color = Color.rgb(0x20, 0x14, 0x0E)
            c.drawCircle(w * 0.43f, h * 0.29f, w * 0.025f, p)
            c.drawCircle(w * 0.57f, h * 0.29f, w * 0.025f, p)
            p.color = Color.rgb(0x24, 0x1A, 0x15)
            c.drawRoundRect(RectF(w * 0.41f, h * 0.34f, w * 0.59f, h * 0.37f), 6f, 6f, p)
            c.drawArc(RectF(w * 0.35f, h * 0.28f, w * 0.65f, h * 0.45f), 15f, 150f, false, p)
            p.color = Color.rgb(0x1E, 0x88, 0xE5)
            c.drawRoundRect(RectF(w * 0.26f, h * 0.47f, w * 0.74f, h * 0.78f), 20f, 20f, p)
            p.color = Color.rgb(0x26, 0x32, 0x38)
            c.drawRect(w * 0.27f, h * 0.76f, w * 0.73f, h * 0.94f, p)
            return bmp
        }

        fun createSampleBulkyPersonBitmap(w: Int = 240, h: Int = 300): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.rgb(0xF2, 0xF4, 0xF7))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.rgb(0xD2, 0x96, 0x6A)
            c.drawOval(RectF(w * 0.34f, h * 0.16f, w * 0.66f, h * 0.44f), p)
            p.color = Color.rgb(0x14, 0x10, 0x0E)
            c.drawArc(RectF(w * 0.33f, h * 0.11f, w * 0.67f, h * 0.27f), 180f, 180f, true, p)
            p.color = Color.rgb(0x20, 0x14, 0x0E)
            c.drawCircle(w * 0.43f, h * 0.28f, w * 0.025f, p)
            c.drawCircle(w * 0.57f, h * 0.28f, w * 0.025f, p)
            p.color = Color.rgb(0x10, 0x0C, 0x0A)
            c.drawOval(RectF(w * 0.33f, h * 0.33f, w * 0.67f, h * 0.46f), p)
            p.color = Color.rgb(0xC6, 0x28, 0x28)
            c.drawOval(RectF(w * 0.14f, h * 0.45f, w * 0.86f, h * 0.82f), p)
            p.color = Color.rgb(0xFA, 0xF8, 0xF0)
            c.drawRect(w * 0.16f, h * 0.68f, w * 0.84f, h * 0.94f, p)
            return bmp
        }

        fun createSampleSlimPersonBitmap(w: Int = 240, h: Int = 300): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.rgb(0xF2, 0xF4, 0xF7))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.rgb(0xE0, 0xA8, 0x7C)
            c.drawOval(RectF(w * 0.34f, h * 0.17f, w * 0.66f, h * 0.44f), p)
            p.color = Color.rgb(0x20, 0x16, 0x12)
            c.drawArc(RectF(w * 0.34f, h * 0.13f, w * 0.66f, h * 0.27f), 180f, 180f, true, p)
            p.color = Color.rgb(0x20, 0x14, 0x0E)
            c.drawCircle(w * 0.43f, h * 0.29f, w * 0.024f, p)
            c.drawCircle(w * 0.57f, h * 0.29f, w * 0.024f, p)
            p.color = Color.rgb(0x00, 0x89, 0x7B)
            c.drawRoundRect(RectF(w * 0.32f, h * 0.46f, w * 0.68f, h * 0.80f), 14f, 14f, p)
            p.color = Color.rgb(0x1A, 0x23, 0x7E)
            c.drawRect(w * 0.34f, h * 0.78f, w * 0.66f, h * 0.94f, p)
            return bmp
        }

        fun createSamplePortraitBitmap(w: Int = 240, h: Int = 300): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.rgb(0xF5, 0xF0, 0xEB))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.rgb(0x1E, 0x14, 0x10)
            c.drawCircle(w * 0.5f, h * 0.26f, w * 0.25f, p)
            c.drawOval(RectF(w * 0.18f, h * 0.28f, w * 0.32f, h * 0.52f), p)
            c.drawOval(RectF(w * 0.68f, h * 0.28f, w * 0.82f, h * 0.52f), p)
            p.color = Color.rgb(0xE6, 0xAE, 0x82)
            c.drawOval(RectF(w * 0.28f, h * 0.18f, w * 0.72f, h * 0.50f), p)
            p.color = Color.rgb(0x1E, 0x14, 0x10)
            c.drawArc(RectF(w * 0.27f, h * 0.13f, w * 0.73f, h * 0.31f), 180f, 180f, true, p)
            p.color = Color.rgb(0x20, 0x14, 0x0E)
            c.drawCircle(w * 0.41f, h * 0.32f, w * 0.028f, p)
            c.drawCircle(w * 0.59f, h * 0.32f, w * 0.028f, p)
            p.color = Color.rgb(0xB7, 0x1C, 0x1C)
            c.drawCircle(w * 0.5f, h * 0.27f, w * 0.025f, p)
            p.color = Color.rgb(0xD8, 0x1B, 0x60)
            c.drawRoundRect(RectF(w * 0.26f, h * 0.52f, w * 0.74f, h * 0.86f), 20f, 20f, p)
            p.color = Color.rgb(0xFF, 0xC1, 0x07)
            c.drawRect(w * 0.26f, h * 0.76f, w * 0.74f, h * 0.88f, p)
            return bmp
        }

        fun createSampleCloseUpSelfieBitmap(w: Int = 240, h: Int = 240): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.rgb(0xE0, 0xE0, 0xE0))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.rgb(0xDC, 0xA2, 0x76)
            c.drawOval(RectF(w * 0.12f, h * 0.10f, w * 0.88f, h * 0.92f), p)
            p.color = Color.rgb(0x1C, 0x15, 0x12)
            c.drawArc(RectF(w * 0.12f, h * 0.05f, w * 0.88f, h * 0.42f), 180f, 180f, true, p)
            p.color = Color.rgb(0x20, 0x14, 0x0E)
            c.drawCircle(w * 0.36f, h * 0.48f, w * 0.035f, p)
            c.drawCircle(w * 0.64f, h * 0.48f, w * 0.035f, p)
            return bmp
        }
    }
}
