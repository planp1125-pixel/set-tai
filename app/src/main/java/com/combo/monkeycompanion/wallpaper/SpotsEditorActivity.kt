package com.combo.monkeycompanion.wallpaper

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Climbing spots editor: full-screen preview of the wallpaper where the user adds, drags and
 * resizes rectangles to line up with their home-screen widgets. A screenshot of the home screen
 * can be shown as a semi-transparent guide. Saved per orientation, as fractions of the screen.
 */
class SpotsEditorActivity : androidx.activity.ComponentActivity() {
    private lateinit var editor: SpotsEditorView
    private val landscape get() = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw edge to edge so the view's coordinates match the wallpaper's (full screen).
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        editor = SpotsEditorView(this)
        editor.load(WallpaperPrefs.spotFractions(this, landscape), WallpaperPrefs.guideImage(this, landscape))
        val root = FrameLayout(this)
        root.addView(editor, FrameLayout.LayoutParams(-1, -1))
        root.addView(toolbar(), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(56) })
        setContentView(root)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** v10.38.23: Material 3 tool panel over the full-screen canvas. */
    private fun toolbar(): View = androidx.compose.ui.platform.ComposeView(this).apply {
        setContent {
            androidx.compose.material3.MaterialTheme(colorScheme = com.combo.monkeycompanion.ui.SettaiDesignTokens.scheme(true)) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    modifier = androidx.compose.ui.Modifier.padding(horizontal = 12.dp).fillMaxWidth()
                ) {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(14.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Text("Line up boxes with your widgets. Drag to move, drag a corner dot to resize. Tip: take a screenshot of your home screen and pick it as a guide.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.FilledTonalButton(onClick = { editor.addSpot() }, modifier = androidx.compose.ui.Modifier.weight(1f)) { androidx.compose.material3.Text("+ Add spot") }
                            androidx.compose.material3.OutlinedButton(onClick = { editor.deleteSelected() }, modifier = androidx.compose.ui.Modifier.weight(1f)) { androidx.compose.material3.Text("Delete") }
                        }
                        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.OutlinedButton(onClick = { pickGuide() }, modifier = androidx.compose.ui.Modifier.weight(1f)) { androidx.compose.material3.Text("Guide image") }
                            androidx.compose.material3.OutlinedButton(onClick = { editor.setGuide(null); WallpaperPrefs.setGuideImage(this@SpotsEditorActivity, landscape, null) },
                                modifier = androidx.compose.ui.Modifier.weight(1f)) { androidx.compose.material3.Text("No guide") }
                        }
                        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.TextButton(onClick = { finish() }, modifier = androidx.compose.ui.Modifier.weight(1f)) { androidx.compose.material3.Text("Cancel") }
                            androidx.compose.material3.Button(onClick = {
                                WallpaperPrefs.saveSpotFractions(this@SpotsEditorActivity, landscape, editor.fractions())
                                Toast.makeText(this@SpotsEditorActivity, "Saved ${editor.count()} climbing spot(s)", Toast.LENGTH_SHORT).show()
                                finish()
                            }, modifier = androidx.compose.ui.Modifier.weight(1f)) { androidx.compose.material3.Text("✔ Save") }
                        }
                    }
                }
            }
        }
    }

    private fun pickGuide() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        @Suppress("DEPRECATION") startActivityForResult(i, 41)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        val u = data?.data ?: return
        if (requestCode == 41 && resultCode == RESULT_OK) {
            runCatching { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            WallpaperPrefs.setGuideImage(this, landscape, u.toString())
            editor.setGuide(u.toString())
        }
    }
}

/** The editable canvas (also used to render the preview sheet). */
@SuppressLint("ViewConstructor")
class SpotsEditorView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val spots = ArrayList<RectF>()       // view px
    private var pending: List<RectF>? = null     // fractions until we know our size
    private var guideUri: String? = null
    private var bgBmp: Bitmap? = null
    private var bgKey = ""
    private var selected = -1
    private var mode = 0                         // 0 none, 1 move, 2 resize TL, 3 resize BR, 4 resize TR, 5 resize BL
    private var lastX = 0f; private var lastY = 0f
    private val loc = IntArray(2)
    /** Screen size used for the fractions (the wallpaper covers the whole screen). */
    var screenW = 0; var screenH = 0

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * d }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * d; pathEffect = DashPathEffect(floatArrayOf(8 * d, 6 * d), 0f); color = Color.WHITE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 14 * d; isFakeBoldText = true; setShadowLayer(3 * d, 0f, 1 * d, Color.BLACK) }

    fun load(fr: List<RectF>, guide: String?) { pending = fr; guideUri = guide; requestLayout(); invalidate() }
    fun setGuide(uri: String?) { guideUri = uri; bgKey = ""; invalidate() }
    fun count() = spots.size

    private fun screenSize() {
        if (screenW > 0) return
        val wm = context.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) { val b = wm.maximumWindowMetrics.bounds; screenW = b.width(); screenH = b.height() }
        else { val m = android.util.DisplayMetrics(); @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m); screenW = m.widthPixels; screenH = m.heightPixels }
    }

    private fun offX(): Float { if (isAttachedToWindow) getLocationOnScreen(loc) else { loc[0] = 0; loc[1] = 0 }; return loc[0].toFloat() }
    private fun offY(): Float = loc[1].toFloat()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        screenSize()
        if (screenW <= 0) { screenW = w; screenH = h }
        val ox = offX(); val oy = offY()
        pending?.let { fr ->
            spots.clear()
            for (r in fr) spots.add(RectF(r.left * screenW - ox, r.top * screenH - oy, r.right * screenW - ox, r.bottom * screenH - oy))
            pending = null
        }
    }

    fun fractions(): List<RectF> {
        val ox = offX(); val oy = offY()
        return spots.map { RectF((it.left + ox) / screenW, (it.top + oy) / screenH, (it.right + ox) / screenW, (it.bottom + oy) / screenH) }
    }

    fun addSpot() {
        val w = width.toFloat(); val h = height.toFloat()
        val cw = w * 0.6f; val ch = h * 0.12f
        val off = (spots.size % 4) * 30 * d
        spots.add(RectF((w - cw) / 2, h * 0.3f + off, (w + cw) / 2, h * 0.3f + off + ch))
        selected = spots.size - 1
        invalidate()
    }

    fun deleteSelected() { if (selected in spots.indices) { spots.removeAt(selected); selected = -1; invalidate() } }

    override fun onDraw(c: Canvas) {
        val w = width; val h = height
        if (w <= 0) return
        val k = "$w x $h $guideUri ${WallpaperPrefs.version(context)}"
        if (k != bgKey) {
            bgBmp?.recycle()
            bgBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                val bc = Canvas(bmp)
                WallpaperBackground(context).draw(bc, w, h)
                WallpaperBackground.drawCropped(context, bc, w, h, guideUri, 140)
            }
            bgKey = k
        }
        bgBmp?.let { c.drawBitmap(it, 0f, 0f, null) }
        c.drawColor(Color.argb(40, 0, 0, 0))
        for ((i, r) in spots.withIndex()) {
            val sel = i == selected
            fill.color = if (sel) Color.argb(90, 0xE8, 0x8A, 0x2E) else Color.argb(70, 255, 255, 255)
            c.drawRoundRect(r, 14 * d, 14 * d, fill)
            edge.color = if (sel) Color.rgb(0xE8, 0x8A, 0x2E) else Color.argb(220, 255, 255, 255)
            c.drawRoundRect(r, 14 * d, 14 * d, edge)
            c.drawText("Spot ${i + 1}", r.left + 10 * d, r.top + 20 * d, label)
            if (sel) for ((hx, hy) in corners(r)) {
                fill.color = Color.WHITE; c.drawCircle(hx, hy, 11 * d, fill)
                edge.color = Color.rgb(0xE8, 0x8A, 0x2E); c.drawCircle(hx, hy, 11 * d, edge)
            }
        }
        if (spots.isEmpty()) {
            val t = "Tap \"+ Add spot\" to add a climbing spot"
            label.textAlign = Paint.Align.CENTER; c.drawText(t, w / 2f, h * 0.4f, label); label.textAlign = Paint.Align.LEFT
            c.drawRect(w * 0.15f, h * 0.43f, w * 0.85f, h * 0.53f, dash)
        }
    }

    private fun corners(r: RectF) = listOf(r.left to r.top, r.right to r.bottom, r.right to r.top, r.left to r.bottom)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = 0
                if (selected in spots.indices) {
                    corners(spots[selected]).forEachIndexed { ci, (hx, hy) -> if (abs(x - hx) < 26 * d && abs(y - hy) < 26 * d && mode == 0) mode = 2 + ci }
                }
                if (mode == 0) {
                    selected = spots.indexOfLast { it.contains(x, y) }
                    if (selected >= 0) mode = 1
                }
                lastX = x; lastY = y
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val r = spots.getOrNull(selected) ?: return true
                val dx = x - lastX; val dy = y - lastY
                lastX = x; lastY = y
                val minS = 40 * d
                when (mode) {
                    1 -> r.offset(dx, dy)
                    2 -> { r.left = min(r.left + dx, r.right - minS); r.top = min(r.top + dy, r.bottom - minS) }
                    3 -> { r.right = max(r.right + dx, r.left + minS); r.bottom = max(r.bottom + dy, r.top + minS) }
                    4 -> { r.right = max(r.right + dx, r.left + minS); r.top = min(r.top + dy, r.bottom - minS) }
                    5 -> { r.left = min(r.left + dx, r.right - minS); r.bottom = max(r.bottom + dy, r.top + minS) }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = 0
        }
        return true
    }

    /** For the preview sheet. */
    fun select(i: Int) { selected = i; invalidate() }
}
