package com.combo.monkeycompanion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.overlay.OverlayHost
import com.combo.monkeycompanion.wallpaper.WallpaperBackground
import kotlin.math.max
import kotlin.math.min

/** Debug-only: v3 preview frames (climbing, corners, entrances, edge hide, wallpaper spots). */
object PreviewV3 {
    class Host(val w: Int, val h: Int, override val wallpaper: Boolean, private val spotList: List<RectF> = emptyList(), private val d: Float) : OverlayHost {
        val win = Rect()
        override fun screenWidth() = w
        override fun screenHeight() = h
        override fun placeWindow(x: Int, y: Int, w: Int, h: Int) { win.set(x, y, x + w, y + h) }
        override fun topInset() = (24 * d).toInt()
        override fun bottomInset() = (if (wallpaper) 64 else 48) * d.toInt()
        override fun spots() = spotList
    }

    private lateinit var ctx: Context
    private var d = 1f
    private var W = 0; private var H = 0

    fun spotsFor(w: Int, h: Int) = listOf(
        RectF(w * 0.07f, h * 0.10f, w * 0.93f, h * 0.24f),   // clock widget
        RectF(w * 0.50f, h * 0.42f, w * 0.93f, h * 0.60f),   // weather widget
    )

    /** Renders every frame; returns name -> bitmap. */
    fun renderAll(context: Context, w: Int, h: Int): Map<String, Bitmap> {
        ctx = context; d = context.resources.displayMetrics.density; W = w; H = h
        val out = LinkedHashMap<String, Bitmap>()

        // --- Side climbing (left wall), 4 frames + right wall ---
        run {
            val (v, host) = make(false)
            v.debugSurf("WALL_L", frac = 0.85f); v.debugFrames(60)
            v.debugWalk(0.15f, 1f)
            for (i in 0 until 4) { v.debugFrames(14); out["climb_$i"] = crop(shotApp(v, host), v.debugBox(), 30, 30) }
            val (v2, h2) = make(false)
            v2.debugSurf("WALL_R", frac = 0.6f); v2.debugFrames(70)
            v2.debugWalk(0.2f, 1f); v2.debugFrames(20)
            out["climb_r"] = crop(shotApp(v2, h2), v2.debugBox(), 30, 30)
        }
        // --- Corner: wall -> ceiling ---
        run {
            val (v, host) = make(false)
            v.debugSurf("WALL_L", frac = 0.0f); v.debugFrames(70)
            val region = RectF(0f, 0f, 175 * d, 185 * d)
            out["corner_0"] = crop(shotApp(v, host), region, 0, 0)
            v.debugCorner("CEIL")
            for ((i, n) in listOf(19, 14, 40).withIndex()) { v.debugFrames(n); out["corner_${i + 1}"] = crop(shotApp(v, host), region, 0, 0) }
        }
        // --- Ceiling hand-over-hand ---
        run {
            val (v, host) = make(false)
            v.debugSurf("CEIL", frac = 0.15f); v.debugFrames(70)
            v.debugWalk(0.8f, 1f)
            for (i in 0 until 4) { v.debugFrames(13); out["ceil_$i"] = crop(shotApp(v, host), v.debugBox(), 20, 0) }
        }
        // --- Wake entrance at the bottom edge ---
        run {
            for ((i, st) in listOf(1.75f, 2.95f, 3.7f, 4.3f, 5.0f, 7.6f).withIndex()) {
                val (v, host) = make(false)
                v.debugSurf("FLOOR", frac = 0.5f); v.debugFrames(5)
                v.debugEntrance(0, st)
                out["wake_$i"] = crop(shotApp(v, host, navBar = true), v.debugBox(), 30, 10, extraBottom = (48 * d).toInt())
            }
            val (v, host) = make(false)
            v.debugEntrance(1, 2.2f)
            out["wake_side"] = crop(shotApp(v, host), v.debugBox(), 40, 30)
        }
        // --- Edge hide ---
        run {
            val (v, host) = make(false)
            v.debugSurf("FLOOR", frac = 0.55f); v.debugFrames(20)
            v.debugHide(1); v.debugFrames(14)
            val b = v.debugBox()
            out["hide_dash"] = crop(shotApp(v, host, navBar = true), RectF(b.left - 40 * d, b.top - 20 * d, W.toFloat(), b.bottom + 48 * d), 0, 0)
            v.debugFrames(40)
            val tab = RectF(host.win)
            out["hide_tab_r"] = crop(shotApp(v, host, navBar = true), RectF(W - 150 * d, tab.top - 70 * d, W.toFloat(), tab.bottom + 70 * d), 0, 0)
            val (v2, h2) = make(false)
            v2.debugSurf("FLOOR", frac = 0.3f); v2.debugFrames(20)
            v2.debugHide(2); v2.debugFrames(60)
            val t2 = RectF(h2.win)
            out["hide_tab_b"] = crop(shotApp(v2, h2, navBar = true), RectF(t2.left - 90 * d, t2.top - 110 * d, t2.right + 90 * d, H.toFloat()), 0, 0)
        }
        // --- Wallpaper with climbing spots ---
        run {
            val sp = spotsFor(W, H)
            fun wall(setup: (MonkeyView) -> Unit, say: String? = null): Bitmap {
                val (v, host) = make(true, sp)
                setup(v)
                if (say != null) { v.debugSay(say); v.debugFrames(25) }
                return shotWall(v, host, sp)
            }
            out["wp_peek"] = wall({ v -> v.debugSurf("FLOOR", 0, 0.25f); v.debugFrames(5); v.debugEntrance(0, 2.1f) })
            out["wp_sit"] = wall({ v -> v.debugSurf("FLOOR", 1, 0.5f); v.debugPlay(MonkeyState.SIT, 99f); v.debugFrames(40) }, "Just chilling on your widget.")
            out["wp_side"] = wall({ v -> v.debugSurf("WALL_R", 1, 0.9f); v.debugFrames(50); v.debugWalk(0.3f, 1f); v.debugFrames(20) })
            out["wp_hang"] = wall({ v -> v.debugSurf("CEIL", 0, 0.6f); v.debugFrames(90) })
        }
        views.forEach { it.goDead() }; views.clear()
        return out
    }

    private val views = ArrayList<MonkeyView>()

    private fun make(wallpaper: Boolean, spots: List<RectF> = emptyList()): Pair<MonkeyView, Host> {
        val host = Host(W, H, wallpaper, spots, d)
        val v = MonkeyView(ctx, host)
        v.layout(0, 0, W, H)
        v.goLive()
        views.add(v)
        return v to host
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** A mock app under the overlay, then the overlay window's pixels. */
    private fun shotApp(v: MonkeyView, host: Host, navBar: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xF4, 0xF5, 0xF8))
        bgPaint.color = Color.rgb(0x4A, 0x6C, 0xD4); c.drawRect(0f, 0f, W.toFloat(), 80 * d, bgPaint)
        bgPaint.color = Color.WHITE
        var y = 100 * d
        while (y < H) { c.drawRoundRect(16 * d, y, W - 16 * d, y + 90 * d, 14 * d, 14 * d, bgPaint); y += 106 * d }
        if (navBar) { bgPaint.color = Color.rgb(0x22, 0x22, 0x26); c.drawRect(0f, H - 48 * d, W.toFloat(), H.toFloat(), bgPaint) }
        val r = host.win
        v.layout(0, 0, r.width(), r.height())
        c.save(); c.translate(r.left.toFloat(), r.top.toFloat()); c.clipRect(0, 0, r.width(), r.height())
        v.draw(c)
        c.restore()
        return bmp
    }

    /** Wallpaper background, Settai, then the launcher's widgets and icons in front. */
    private fun shotWall(v: MonkeyView, host: Host, spots: List<RectF>): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        WallpaperBackground.jungle(c, W, H, d)
        v.layout(0, 0, W, H)
        v.draw(c)
        // launcher in front
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        spots.getOrNull(0)?.let { r ->
            p.color = Color.argb(215, 0x1E, 0x2A, 0x2A); c.drawRoundRect(r, 24 * d, 24 * d, p)
            tp.textSize = r.height() * 0.42f; c.drawText("10:24", r.centerX(), r.centerY() + r.height() * 0.1f, tp)
            tp.textSize = r.height() * 0.13f; tp.isFakeBoldText = false; c.drawText("Thursday, 22 October", r.centerX(), r.bottom - r.height() * 0.12f, tp); tp.isFakeBoldText = true
        }
        spots.getOrNull(1)?.let { r ->
            p.color = Color.argb(225, 0x3D, 0x7E, 0xC9); c.drawRoundRect(r, 24 * d, 24 * d, p)
            p.color = Color.rgb(0xFF, 0xD5, 0x4F); c.drawCircle(r.left + r.width() * 0.3f, r.top + r.height() * 0.38f, r.height() * 0.18f, p)
            tp.textSize = r.height() * 0.26f; c.drawText("31°", r.left + r.width() * 0.68f, r.top + r.height() * 0.48f, tp)
            tp.textSize = r.height() * 0.11f; tp.isFakeBoldText = false; c.drawText("Chennai · Sunny", r.centerX(), r.bottom - r.height() * 0.14f, tp); tp.isFakeBoldText = true
        }
        val cols = 4; val iconR = 26 * d
        val colors = intArrayOf(0xFFE57373.toInt(), 0xFF64B5F6.toInt(), 0xFF81C784.toInt(), 0xFFFFB74D.toInt(), 0xFFBA68C8.toInt(), 0xFF4DB6AC.toInt(), 0xFFF06292.toInt(), 0xFF90A4AE.toInt())
        for (row in 0 until 2) for (col in 0 until cols) {
            val cx = W * (col + 0.5f) / cols
            val cy = if (row == 0) H * 0.70f else H - 150 * d
            p.color = colors[(row * cols + col) % colors.size]; c.drawCircle(cx, cy, iconR, p)
        }
        return bmp
    }

    private fun crop(src: Bitmap, r: RectF, padX: Int, padY: Int, extraBottom: Int = 0): Bitmap {
        val l = max(0, (r.left - padX * d).toInt()); val t = max(0, (r.top - padY * d).toInt())
        val rr = min(src.width, (r.right + padX * d).toInt()); val b = min(src.height, (r.bottom + padY * d).toInt() + extraBottom)
        return Bitmap.createBitmap(src, l, t, max(1, rr - l), max(1, b - t))
    }
}
