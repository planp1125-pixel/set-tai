package com.combo.monkeycompanion.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.getValue
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.setValue
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

    // Compose state: every screen part that reads the draft updates by itself when it changes.
    private val draftState = androidx.compose.runtime.mutableStateOf<CustomCompanionSpec?>(null)
    private var draft: CustomCompanionSpec
        get() = draftState.value!!
        set(v) { draftState.value = v }
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

        // v10.38.22: "Open Creator Studio" on a character opens THAT character (custom ids), not always the first one.
        val asked = intent?.getStringExtra("character")
        draft = asked?.let { CustomCompanionRegistry.specById(it) } ?: CustomCompanionStore.activeSpec(this)
        originalSpec = draft
        previewChar = CustomCompanionRegistry.register(draft)
        rebuildUI()
    }


    /** v10.38.22: the character as it was when the Studio opened (Discard changes) and recent edits (Undo). */
    private var originalSpec: CustomCompanionSpec? = null
    private val undoStack = ArrayDeque<CustomCompanionSpec>()
    private var undoCount by androidx.compose.runtime.mutableIntStateOf(0)
    private var discardAsk by androidx.compose.runtime.mutableStateOf(false)

    private fun pushUndo() {
        val cur = draftState.value ?: return
        if (undoStack.lastOrNull() !== cur) { undoStack.addLast(cur); if (undoStack.size > 40) undoStack.removeFirst() }
        undoCount = undoStack.size
    }

    private fun applySpec(spec: CustomCompanionSpec) {
        draft = spec
        draftRevision++
        previewChar.updateSpec(spec)
        CustomCompanionStore.save(this, spec)
        stageView?.refreshSpec()
    }

    private fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        undoCount = undoStack.size
        applySpec(prev)
    }

    private fun discardChanges() {
        val orig = originalSpec ?: return
        undoStack.clear(); undoCount = 0
        applySpec(orig)
        Toast.makeText(this, "Changes discarded", Toast.LENGTH_SHORT).show()
    }

    private fun updateDraft(newDraft: CustomCompanionSpec, rebuild: Boolean = false) {
        pushUndo()
        draft = newDraft
        draftRevision++
        previewChar.updateSpec(draft)
        CustomCompanionStore.save(this, draft)
        // v10.38.22: editing a character never makes it the main one by itself (only "Save & use" does).
        if (ChatHub.settings.characterId == draft.id) ChatHub.settings.applyCharacter()
        stageView?.refreshSpec()
        if (rebuild) rebuildUI()
    }

    override fun onPause() {
        CustomCompanionStore.save(this, draft)
        // v10.38.22: editing a character never makes it the main one by itself (only "Save & use" does).
        if (ChatHub.settings.characterId == draft.id) ChatHub.settings.applyCharacter()
        com.combo.monkeycompanion.overlay.MonkeyOverlayService.syncCrew(this)
        super.onPause()
    }

    /** Photo -> look-alike progress (shown as a Material 3 dialog). null = hidden. */
    private var photoProgress by androidx.compose.runtime.mutableStateOf<Pair<Float, String>?>(null)

    private fun runPhotoGenerationWithProgress(bmp: Bitmap) {
        lastUploadedPhoto = bmp
        val steps = arrayOf(
            "Finding the face…",
            "Cutting out head, hair and beard…",
            "Picking skin, hair and shirt colours…",
            "Bringing your character to life…"
        )
        photoProgress = 0.15f to steps[0]
        Thread {
            val res = PhotoCompanionAnalyzer.analyze(bmp, draft)
            for (i in 1..3) {
                runCatching { Thread.sleep(220) }
                uiHandler.post { photoProgress = (i + 1) * 0.25f to steps[i] }
            }
            uiHandler.post {
                photoProgress = null
                CustomCompanionStore.save(this, res.spec)
                lastUploadedPhoto = res.portraitBitmap
                updateDraft(res.spec, rebuild = true)
                stageView?.uploadedPhotoThumb = res.portraitBitmap
                stageView?.splitCompareMode = true
                stageView?.zoomLevel = 3.0f
                showPhotoMatch()
                Toast.makeText(this, res.spec.analysisSummary, Toast.LENGTH_SHORT).show()
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
    private var whoDialogOpen by androidx.compose.runtime.mutableStateOf(false)
    fun showWhoIsInPhotoDialog() { whoDialogOpen = true }

    @androidx.compose.runtime.Composable
    private fun WhoIsInPhotoDialog() {
        var name by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(draft.name) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { whoDialogOpen = false },
            title = { androidx.compose.material3.Text("Who is in this photo?") },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { v ->
                        name = v
                        val t = v.trim()
                        if (t.isNotBlank()) { draft = draft.copy(name = t); previewChar.updateSpec(draft); CustomCompanionStore.save(this@CompanionStudioActivity, draft) }
                    }, singleLine = true, label = { androidx.compose.material3.Text("Name") })
                    androidx.compose.material3.Text("We guessed ${draft.gender.label}, ${draft.ageGroup.shortLabel}. Change it if it's wrong:",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    StudioGenderControls(draft.gender, onSelect = { g ->
                        updateDraft(draft.applyGenderAndAge(newGender = g))
                        stageView?.uploadedPhotoThumb = lastUploadedPhoto
                    })
                    StudioAgeControls(draft.ageGroup, onSelect = { ag ->
                        val nextGender = when {
                            ag == AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.FEMALE -> CompanionGender.GIRL_CHILD
                            ag == AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.MALE -> CompanionGender.BOY_CHILD
                            ag != AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.GIRL_CHILD -> CompanionGender.FEMALE
                            ag != AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.BOY_CHILD -> CompanionGender.MALE
                            else -> draft.gender
                        }
                        updateDraft(draft.applyGenderAndAge(newGender = nextGender, newAge = ag))
                        stageView?.uploadedPhotoThumb = lastUploadedPhoto
                    })
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { whoDialogOpen = false }) { androidx.compose.material3.Text("Done") } }
        )
    }

    /**
     * v9.3: True 100% Fullscreen Edge-to-Edge Zoom & Mesh Inspector (`Theme_Black_NoTitleBar_Fullscreen`)
     * so two-finger pinch-to-zoom (`1x–6x`), 1-finger pan, and `[✋ Move Mesh]` have the entire phone screen!
     */
    /**
     * Builds the ergonomic Feature Target Chip Selector + Precision D-Pad (`◀ ▲ ▼ ▶ ➖ ➕`)
     * so the user can align any of the 10 facial features WITHOUT needing to poke tiny dots!
     */
    /** Pick a face feature, then nudge / resize it; Copy takes colours from the photo; ↺ resets the mesh. */
    @androidx.compose.runtime.Composable
    private fun MeshFeatureControls(stage: StudioLiveStageView) {
        var active by androidx.compose.runtime.remember(stage) { androidx.compose.runtime.mutableStateOf(stage.selectedMeshFeature) }
        androidx.compose.runtime.DisposableEffect(stage) {
            stage.onFeatureSelected = { active = it }
            onDispose { stage.onFeatureSelected = null }
        }
        androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Text(active.hint, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.Row(
                androidx.compose.ui.Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
            ) {
                for (target in listOf(
                    MeshFeatureTarget.WHOLE_MESH, MeshFeatureTarget.HAIRLINE_TOP, MeshFeatureTarget.LEFT_BROW, MeshFeatureTarget.RIGHT_BROW,
                    MeshFeatureTarget.LEFT_EYE, MeshFeatureTarget.RIGHT_EYE, MeshFeatureTarget.NOSE_TIP, MeshFeatureTarget.MOUTH,
                    MeshFeatureTarget.JAW_LEFT, MeshFeatureTarget.JAW_RIGHT, MeshFeatureTarget.CHIN_BOTTOM,
                )) {
                    androidx.compose.material3.FilterChip(selected = active == target, onClick = {
                        stage.showFaceMesh = true
                        stage.moveMeshMode = true
                        if (!stage.splitCompareMode && stage.zoomLevel < 2.0f) { stage.splitCompareMode = true; stage.zoomLevel = 3.0f }
                        stage.selectedMeshFeature = target
                        active = target
                    }, label = { androidx.compose.material3.Text(target.shortLabel) })
                }
            }
            val step = 0.018f
            androidx.compose.foundation.layout.Row(
                androidx.compose.ui.Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
            ) {
                for ((lbl, act) in listOf<Pair<String, () -> Unit>>(
                    "◀" to { stage.nudgeSelectedFeature(-step, 0f, 0f) },
                    "▲" to { stage.nudgeSelectedFeature(0f, -step, 0f) },
                    "▼" to { stage.nudgeSelectedFeature(0f, step, 0f) },
                    "▶" to { stage.nudgeSelectedFeature(step, 0f, 0f) },
                    "Smaller" to { stage.nudgeSelectedFeature(0f, 0f, -0.05f) },
                    "Bigger" to { stage.nudgeSelectedFeature(0f, 0f, +0.05f) },
                )) {
                    androidx.compose.material3.OutlinedButton(onClick = act, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                        androidx.compose.material3.Text(lbl)
                    }
                }
            }
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = {
                    val portraitBmp = stage.uploadedPhotoThumb ?: CustomCompanionStore.getOrDecodeBitmap(draft.id, draft.facePortraitBase64)
                    if (portraitBmp != null) {
                        val synced = PhotoCompanionAnalyzer.rescanFromAdjustedMesh(portraitBmp, previewChar.spec)
                        CustomCompanionStore.save(this@CompanionStudioActivity, synced)
                        updateDraft(synced)
                        stage.refreshSpec()
                        Toast.makeText(this@CompanionStudioActivity, "Copied face shape and colours", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(this@CompanionStudioActivity, "Upload a photo first", Toast.LENGTH_SHORT).show()
                }) { androidx.compose.material3.Text("Copy from photo") }
                androidx.compose.material3.OutlinedButton(onClick = {
                    val nextSpec = previewChar.spec.copy(faceMesh = com.combo.monkeycompanion.character.FaceLandmarkMesh())
                    CustomCompanionStore.save(this@CompanionStudioActivity, nextSpec)
                    updateDraft(nextSpec)
                    stage.refreshSpec()
                }) { androidx.compose.material3.Text("Reset face") }
            }
        }
    }

    private var inspectorOpen by androidx.compose.runtime.mutableStateOf(false)
    fun openFullscreenZoomInspector() { inspectorOpen = true }

    /** Full-screen face editor: big preview + feature tools + zoom / split / mesh switches. */
    @androidx.compose.runtime.Composable
    private fun FullscreenFaceEditor() {
        val big = androidx.compose.runtime.remember {
            StudioLiveStageView(this, previewChar).apply {
                uploadedPhotoThumb = lastUploadedPhoto
                splitCompareMode = true; zoomLevel = 3.5f; showFaceMesh = true; moveMeshMode = true
                selectedMeshFeature = stageView?.selectedMeshFeature ?: MeshFeatureTarget.WHOLE_MESH
                onMeshAdjusted = { nextMesh -> pushUndo(); draft = draft.copy(faceMesh = nextMesh); CustomCompanionStore.save(this@CompanionStudioActivity, draft); stageView?.refreshSpec() }
                onResampledSpec = { nextSpec -> draft = nextSpec; CustomCompanionStore.save(this@CompanionStudioActivity, draft); stageView?.refreshSpec() }
            }
        }
        val atOpen = androidx.compose.runtime.remember { draft }
        var split by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
        var mesh by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
        var drag by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
        androidx.compose.ui.window.Dialog(onDismissRequest = { inspectorOpen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.Text("Face editor", style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                            modifier = androidx.compose.ui.Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { showWhoIsInPhotoDialog() }) { androidx.compose.material3.Text(draft.gender.shortLabel) }
                        androidx.compose.material3.TextButton(onClick = { applySpec(atOpen); inspectorOpen = false }) { androidx.compose.material3.Text("Cancel") }
                        androidx.compose.material3.Button(onClick = { inspectorOpen = false }) { androidx.compose.material3.Text("Done") }
                    }
                    androidx.compose.ui.viewinterop.AndroidView(factory = { big },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth().weight(1f))
                    MeshFeatureControls(big)
                    androidx.compose.foundation.layout.Row(
                        androidx.compose.ui.Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                    ) {
                        for ((z, lbl) in listOf(1f to "1×", 2f to "2×", 3.5f to "3.5×", 5f to "5×")) {
                            androidx.compose.material3.AssistChip(onClick = { big.zoomLevel = z }, label = { androidx.compose.material3.Text(lbl) })
                        }
                        androidx.compose.material3.FilterChip(selected = split, onClick = { split = !split; big.splitCompareMode = split }, label = { androidx.compose.material3.Text("Side by side") })
                        androidx.compose.material3.FilterChip(selected = mesh, onClick = { mesh = !mesh; big.showFaceMesh = mesh }, label = { androidx.compose.material3.Text("Show mesh") })
                        androidx.compose.material3.FilterChip(selected = drag, onClick = {
                            drag = !drag; big.moveMeshMode = drag; if (drag) { big.showFaceMesh = true; mesh = true }
                        }, label = { androidx.compose.material3.Text(if (drag) "Drag: face" else "Drag: view") })
                    }
                }
            }
        }
    }

    // ---------------- v10.38.12 Studio: one Material 3 screen ----------------
    // Live preview stays pinned on top; six tabs below; Save bar at the bottom. Every change saves and the
    // preview updates at once (no screen rebuild, no jump back to the top).
    private var studioTab by androidx.compose.runtime.mutableIntStateOf(0)
    /** v10.38.21: face tools (68-point mesh) open by themselves once there is a photo. */
    private var faceToolsOpen by androidx.compose.runtime.mutableStateOf(false)
    private fun hasPhoto() = stageView?.uploadedPhotoThumb != null || lastUploadedPhoto != null || draft.facePortraitBase64.isNotBlank()

    /** After a photo is turned into a look-alike: go to the Face tab with the photo beside the character and the mesh on. */
    private fun showPhotoMatch() {
        studioTab = 1
        faceToolsOpen = true
        stageView?.let { it.showFaceMesh = true; it.moveMeshMode = true; it.splitCompareMode = true; if (it.zoomLevel < 2.2f) it.zoomLevel = 3.0f }
    }
    private var studioComposed = false
    private val studioTabs = listOf("Basics", "Face", "Body", "Style", "Powers", "Personality")

    private fun ensureStage(): StudioLiveStageView {
        stageView?.let { return it }
        val st = StudioLiveStageView(this, previewChar).also {
            it.uploadedPhotoThumb = lastUploadedPhoto ?: runCatching { CustomCompanionStore.getOrDecodeBitmap(draft.id, draft.facePortraitBase64) }.getOrNull()
            it.onMeshAdjusted = { nextMesh ->
                pushUndo()
                draft = draft.copy(faceMesh = nextMesh)
                CustomCompanionStore.save(this@CompanionStudioActivity, draft)
            }
            it.onResampledSpec = { nextSpec ->
                draft = nextSpec
                CustomCompanionStore.save(this@CompanionStudioActivity, draft)
            }
        }
        stageView = st
        return st
    }

    /** Old name kept: callers now just refresh the Compose screen (it re-reads [draft]). */
    private fun rebuildUI() {
        if (studioComposed) { draftRevision++; return }
        studioComposed = true
        ensureStage()
        setContent {
            val dark = com.combo.monkeycompanion.theme.ThemeSkin.isDark(this@CompanionStudioActivity)
            androidx.compose.material3.MaterialTheme(colorScheme = SettaiDesignTokens.scheme(dark)) {
                StudioScreen()
            }
        }
    }

    private fun saveAndUse(asNew: Boolean) {
        val spec = draft.copy(name = draft.name.trim().ifBlank { "Companion" })
        if (asNew) {
            val newCh = CustomCompanionStore.saveAsNewCompanion(this, spec)
            ChatHub.settings.characterId = newCh.id
            Toast.makeText(this, "Added ${newCh.name} to your characters", Toast.LENGTH_SHORT).show()
        } else {
            CustomCompanionStore.save(this, spec)
            ChatHub.settings.characterId = spec.id
            Toast.makeText(this, "${spec.name} is now on your screen", Toast.LENGTH_SHORT).show()
        }
        ChatHub.settings.applyCharacter()
        MindHub.flush()
        com.combo.monkeycompanion.overlay.MonkeyOverlayService.syncCrew(this)
        finish()
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun StudioScreen() {
        @Suppress("UNUSED_VARIABLE") val rev = draftRevision   // re-read draft after every edit
        val stage = ensureStage()
        var menuOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.material3.Scaffold(
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = {
                        androidx.compose.foundation.layout.Column {
                            androidx.compose.material3.Text("Character Studio", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                            androidx.compose.material3.Text(draft.name.ifBlank { "New character" },
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        androidx.compose.material3.TextButton(onClick = { finish() }) { androidx.compose.material3.Text("←", fontSize = 22.sp) }
                    },
                    actions = {
                        androidx.compose.material3.TextButton(onClick = { undo() }, enabled = undoCount > 0) { androidx.compose.material3.Text("↶ Undo") }
                        androidx.compose.foundation.layout.Box {
                            androidx.compose.material3.TextButton(onClick = { menuOpen = true }) { androidx.compose.material3.Text("⋮", fontSize = 22.sp) }
                            androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                androidx.compose.material3.DropdownMenuItem(text = { androidx.compose.material3.Text("Discard all changes") }, onClick = { menuOpen = false; discardAsk = true })
                                androidx.compose.material3.DropdownMenuItem(text = { androidx.compose.material3.Text("Export character") }, onClick = { menuOpen = false; exportCharacter() })
                                androidx.compose.material3.DropdownMenuItem(text = { androidx.compose.material3.Text("Import character") }, onClick = { menuOpen = false; promptImportCharacter() })
                                androidx.compose.material3.DropdownMenuItem(text = { androidx.compose.material3.Text("Open diary") }, onClick = {
                                    menuOpen = false
                                    CustomCompanionStore.save(this@CompanionStudioActivity, draft)
                                    startActivity(Intent(this@CompanionStudioActivity, DiaryActivity::class.java).putExtra("character", draft.id))
                                })
                            }
                        }
                    }
                )
            },
            bottomBar = {
                androidx.compose.material3.Surface(tonalElevation = 3.dp) {
                    androidx.compose.foundation.layout.Row(
                        androidx.compose.ui.Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                    ) {
                        androidx.compose.material3.OutlinedButton(onClick = { saveAndUse(asNew = true) },
                            modifier = androidx.compose.ui.Modifier.weight(1f).heightIn(min = 48.dp)) { androidx.compose.material3.Text("Save as new") }
                        androidx.compose.material3.Button(onClick = { saveAndUse(asNew = false) },
                            modifier = androidx.compose.ui.Modifier.weight(1.4f).heightIn(min = 48.dp)) { androidx.compose.material3.Text("Save & use") }
                    }
                }
            }
        ) { pad ->
            photoProgress?.let { (prog, step) ->
                androidx.compose.material3.AlertDialog(onDismissRequest = {}, confirmButton = {},
                    title = { androidx.compose.material3.Text("Making your look-alike") },
                    text = {
                        androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                            androidx.compose.material3.LinearProgressIndicator(progress = { prog }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Text(step)
                        }
                    })
            }
            if (whoDialogOpen) WhoIsInPhotoDialog()
            if (importOpen) ImportDialog()
            if (inspectorOpen) FullscreenFaceEditor()
            if (discardAsk) androidx.compose.material3.AlertDialog(onDismissRequest = { discardAsk = false },
                title = { androidx.compose.material3.Text("Discard all changes?") },
                text = { androidx.compose.material3.Text("${draft.name} goes back to how it was when you opened the Studio.") },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { discardAsk = false; discardChanges() }) { androidx.compose.material3.Text("Discard") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { discardAsk = false }) { androidx.compose.material3.Text("Keep editing") } })
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().padding(pad)) {
                // Pinned live preview: always visible while you change things.
                androidx.compose.material3.Card(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(SettaiDesignTokens.cornerCardDp.dp)
                ) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { (stage.parent as? android.view.ViewGroup)?.removeView(stage); stage },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth().height(220.dp)
                    )
                    StudioDemoControls(stage, androidx.compose.ui.Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                androidx.compose.material3.ScrollableTabRow(selectedTabIndex = studioTab, edgePadding = 12.dp) {
                    studioTabs.forEachIndexed { i, label ->
                        androidx.compose.material3.Tab(selected = studioTab == i, onClick = { studioTab = i },
                            text = { androidx.compose.material3.Text(label) })
                    }
                }
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SettaiDesignTokens.sectionSpacingDp.dp)
                ) {
                    when (studioTab) {
                        0 -> BasicsTab()
                        1 -> FaceTab(stage)
                        2 -> Section("Body shape") {
                            StudioBodyControls(draft.bodyBuild, draft.bodyWidthScale, draft.shoulderScale, draft.ageGroup,
                                onBuild = { bb -> updateDraft(draft.copy(bodyBuild = bb, bodyWidthScale = bb.defaultWidthScale, shoulderScale = bb.defaultShoulderScale)) },
                                onWidth = { updateDraft(draft.copy(bodyWidthScale = it)) },
                                onShoulders = { updateDraft(draft.copy(shoulderScale = it)) },
                                onAge = { ag -> updateDraft(draft.applyGenderAndAge(newGender = draft.gender, newAge = ag)) })
                        }
                        3 -> Section("Hair, outfit & extras") {
                            StudioAppearanceControls(
                                draft.facialHair, draft.hairStyle, draft.outfitStyle,
                                draft.glasses, draft.cape, draft.hasBindi, draft.hairBow, draft.jasmine, draft.wings,
                                onFacialHair = { updateDraft(draft.copy(facialHair = it)) },
                                onHairStyle = { updateDraft(draft.copy(hairStyle = it)) },
                                onOutfit = { updateDraft(draft.copy(outfitStyle = it)) },
                                onGlasses = { updateDraft(draft.copy(glasses = it)) },
                                onCape = { updateDraft(draft.copy(cape = it)) },
                                onBindi = { updateDraft(draft.copy(hasBindi = it)) },
                                onBow = { updateDraft(draft.copy(hairBow = it)) },
                                onJasmine = { updateDraft(draft.copy(jasmine = it)) },
                                onWings = { updateDraft(draft.copy(wings = it)) })
                        }
                        4 -> Section("How it moves & its powers (${draft.selectedPowers.size}/${CustomPowerCatalog.OPTIONS.size})") {
                            StudioPowerControls(draft.flightStyle, draft.selectedPowers,
                                onFlight = { fl ->
                                    updateDraft(draft.copy(flightStyle = fl))
                                    if (fl != FlightStyle.WALK_AND_HOP) stage.demoMode = StudioLiveStageView.DemoMode.FLIGHT_LOOP
                                },
                                onAll = {
                                    updateDraft(draft.copy(selectedPowers = CustomPowerCatalog.selectAllPowers()))
                                    stage.demoMode = StudioLiveStageView.DemoMode.CYCLE_POWERS
                                },
                                onTop = { updateDraft(draft.copy(selectedPowers = setOf("ninja", "kovam", "superboy_flight", "ironboy_flight"))) },
                                onToggle = { id, enabled ->
                                    val next = draft.selectedPowers.toMutableSet()
                                    if (enabled) next.add(id) else if (next.size > 1) next.remove(id)
                                    updateDraft(draft.copy(selectedPowers = next))
                                },
                                onOnly = { id -> updateDraft(draft.copy(selectedPowers = setOf(id))) })
                            SNote("Double-tap and the long-press ⚡ menu use only the powers ticked here. The first ticked power is the main one: it plays first. Tap \"Only this\" to keep just one power.")
                        }
                        else -> PersonalityTab(draftRevision)
                    }
                    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Section(title: String, hint: String? = null, content: @androidx.compose.runtime.Composable () -> Unit) {
        androidx.compose.material3.Card(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(SettaiDesignTokens.cornerCardDp.dp)
        ) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                if (hint != null) androidx.compose.material3.Text(hint, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                content()
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun BasicsTab() {
        var name by androidx.compose.runtime.remember(draft.id) { androidx.compose.runtime.mutableStateOf(draft.name) }
        Section("Name") {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { v ->
                    name = v
                    val t = v.trim()
                    if (t.isNotBlank() && t != draft.name) {
                        draft = draft.copy(name = t)
                        previewChar.updateSpec(draft)
                        CustomCompanionStore.save(this@CompanionStudioActivity, draft)
                        stageView?.invalidate()
                    }
                },
                singleLine = true,
                placeholder = { androidx.compose.material3.Text("e.g. Priya, Kuttyma, Manik") },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            )
        }
        Section("Who is it?") {
            StudioGenderControls(draft.gender, onSelect = { g -> updateDraft(draft.applyGenderAndAge(newGender = g)) })
            StudioAgeControls(draft.ageGroup, onSelect = { age ->
                val nextGender = when {
                    age == AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.FEMALE -> CompanionGender.GIRL_CHILD
                    age == AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.MALE -> CompanionGender.BOY_CHILD
                    age != AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.GIRL_CHILD -> CompanionGender.FEMALE
                    age != AgeGroup.CHILD_KUTTY && draft.gender == CompanionGender.BOY_CHILD -> CompanionGender.MALE
                    else -> draft.gender
                }
                updateDraft(draft.applyGenderAndAge(newGender = nextGender, newAge = age))
            })
        }
        Section("Make it look like someone", "Upload a photo and get a cartoon look-alike.") {
            androidx.compose.material3.Button(onClick = { pickPhotoFromGallery() },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth().heightIn(min = 52.dp)) { androidx.compose.material3.Text("Upload a photo") }
        }
        Section("Quick start", "Pick a starting look, then change anything in the other tabs.") {
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
                    // v10.38.24: quick looks apply straight away (no sample photo) and keep the name you typed
                    val keepName = draft.name
                    val look = when (index) {
                        0 -> CustomCompanionPresets.adult35MidBody(draft)
                        1 -> CustomCompanionPresets.bulkyHeavyPerson(draft)
                        2 -> CustomCompanionPresets.womanAdult(draft)
                        3 -> CustomCompanionPresets.child5YrDaughter(draft)
                        4 -> CustomCompanionPresets.child5YrBoy(draft)
                        else -> CustomCompanionPresets.slimPerson(draft)
                    }
                    updateDraft(look.copy(name = keepName, id = draft.id))
                }
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun FaceTab(stage: StudioLiveStageView) {
        val photo = hasPhoto()
        Section("Look-alike from a photo", if (photo) "Your photo is on the left of the preview, the character on the right. Match them with the face tools below."
            else "Pick a clear photo of a face. Set-Tai turns it into a cartoon look-alike, then you can match eyes, nose, mouth and jaw.") {
            androidx.compose.material3.Button(onClick = { pickPhotoFromGallery() },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                androidx.compose.material3.Text(if (photo) "Use a different photo" else "Upload a photo")
            }
            if (photo) androidx.compose.material3.OutlinedButton(onClick = { showPhotoMatch(); draftRevision++ },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()) { androidx.compose.material3.Text("Show photo beside the character") }
        }
        Section("Face tools (68-point mesh)", "1. Tap a feature (hairline, brows, eyes, nose, mouth, jaw, chin).  2. Swipe on the preview like a trackpad, or tap the arrows.  3. Tap Copy to take skin, hair and lip colours from the photo.") {
            if (!photo) SNote("Upload a photo first to see the mesh over it.")
            androidx.compose.material3.OutlinedButton(onClick = {
                faceToolsOpen = !faceToolsOpen
                if (faceToolsOpen) { stage.showFaceMesh = true; stage.moveMeshMode = true; if (!stage.splitCompareMode) { stage.splitCompareMode = true; stage.zoomLevel = 3.0f } }
            }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                androidx.compose.material3.Text(if (faceToolsOpen) "Hide face tools" else "Show face tools")
            }
            if (faceToolsOpen) {
                androidx.compose.runtime.key(faceToolsOpen) { StudioInspectionControls(stage, ::openFullscreenZoomInspector) }
                MeshFeatureControls(stage)
            }
        }
        Section("Preview zoom") { StudioPreviewControls(stage) }
        Section("Face shape & position") {
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
                onBodyTemplate = { template -> updateDraft(draft.applyBodyTemplate(template)) },
                onFaceMode = { label -> FaceMode.entries.firstOrNull { it.label == label }?.let { updateDraft(draft.copy(faceMode = it)) } },
                onNudge = { dx, dy ->
                    updateDraft(draft.copy(
                        faceOffsetX = (draft.faceOffsetX + dx).coerceIn(-20f, 20f),
                        faceOffsetY = (draft.faceOffsetY + dy).coerceIn(-20f, 20f)
                    ))
                },
                onZoom = { delta -> updateDraft(draft.copy(faceZoomScale = (draft.faceZoomScale + delta).coerceIn(0.80f, 1.35f))) },
                onReset = { updateDraft(draft.copy(faceOffsetX = 0f, faceOffsetY = 0f, faceZoomScale = 1.0f)) }
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun PersonalityTab(@Suppress("UNUSED_PARAMETER") rev: Int) {
        Section("Personality", "How it behaves on your screen.") {
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

    private var importOpen by androidx.compose.runtime.mutableStateOf(false)
    private fun promptImportCharacter() { importOpen = true }

    @androidx.compose.runtime.Composable
    private fun ImportDialog() {
        var txt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { importOpen = false },
            title = { androidx.compose.material3.Text("Import a character") },
            text = {
                androidx.compose.material3.OutlinedTextField(value = txt, onValueChange = { txt = it }, minLines = 3,
                    placeholder = { androidx.compose.material3.Text("Paste the .settai text here, or pick a file") })
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val ch = CustomCompanionStore.importFromJson(this@CompanionStudioActivity, txt.trim())
                    if (ch != null) { updateDraft(ch.spec, rebuild = true); Toast.makeText(this@CompanionStudioActivity, "Imported ${ch.name}", Toast.LENGTH_SHORT).show(); importOpen = false }
                    else Toast.makeText(this@CompanionStudioActivity, "That text isn't a Set-Tai character", Toast.LENGTH_SHORT).show()
                }) { androidx.compose.material3.Text("Import") }
            },
            dismissButton = {
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.TextButton(onClick = {
                        importOpen = false
                        runCatching {
                            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, REQ_IMPORT_SETTAI)
                        }
                    }) { androidx.compose.material3.Text("Pick file") }
                    androidx.compose.material3.TextButton(onClick = { importOpen = false }) { androidx.compose.material3.Text("Cancel") }
                }
            }
        )
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
