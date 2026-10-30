package com.combo.monkeycompanion.smash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

/** v10.5: the one screenshot for Screen Smash (memory only), plus a cartoon home screen when there's no picture. */
object SmashShot {
    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var listener: ((Bitmap?) -> Unit)? = null

    /** Wait for the next picture ([deliver] calls [cb] on the main thread, null = no picture). */
    fun await(cb: (Bitmap?) -> Unit) { listener = cb }
    fun cancel() { listener = null }

    fun deliver(bmp: Bitmap?) {
        val l = listener; listener = null
        if (l == null) { bmp?.recycle(); return }
        ui.post { l(bmp) }
    }

    /** Full display size (with the status and navigation bars) and its density dpi. */
    fun screenSize(ctx: Context): Triple<Int, Int, Int> {
        val wm = ctx.getSystemService(WindowManager::class.java)
        val dpi = ctx.resources.displayMetrics.densityDpi
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds; Triple(b.width(), b.height(), dpi)
        } else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m); Triple(m.widthPixels, m.heightPixels, dpi)
        }
    }

    private val FAKE_APPS = listOf(
        "📞" to "Phone", "💬" to "Messages", "📷" to "Camera", "🎵" to "Music",
        "🗺️" to "Maps", "🛒" to "Shop", "🎮" to "Games", "📺" to "Videos",
        "☀️" to "Weather", "📧" to "Mail", "📅" to "Calendar", "🎨" to "Gallery",
        "🍕" to "Food", "🏏" to "Cricket", "📚" to "Books", "⚙️" to "Settings",
        "🧮" to "Calculator", "⏰" to "Clock", "🚕" to "Taxi", "💰" to "Wallet",
    )
    private val ICON_COLORS = intArrayOf(0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFFFF7043.toInt(), 0xFFAB47BC.toInt(),
        0xFF26A69A.toInt(), 0xFFFFCA28.toInt(), 0xFFEC407A.toInt(), 0xFF5C6BC0.toInt())

    /** A cartoon home screen (wallpaper, clock, a grid of app icons) for when there's no real picture. */
    fun fakeScreen(w: Int, h: Int, density: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(0x2B, 0x5F, 0x9E), Color.rgb(0x7B, 0x4F, 0xB8), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
        text.textSize = 64f * density; text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText("10:28", w / 2f, h * 0.14f, text)
        text.textSize = 16f * density; text.typeface = Typeface.DEFAULT
        c.drawText("Tuesday · 28 October", w / 2f, h * 0.14f + 30f * density, text)
        val cols = 4; val cell = w / cols.toFloat(); val icon = cell * 0.56f
        val top = h * 0.26f
        val rows = 5
        val box = Paint(Paint.ANTI_ALIAS_FLAG); val r = RectF()
        val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = icon * 0.52f }
        text.textSize = 12.5f * density
        for (i in 0 until cols * rows) {
            val cx = (i % cols + 0.5f) * cell; val cy = top + (i / cols) * cell * 1.12f
            box.color = ICON_COLORS[i % ICON_COLORS.size]
            r.set(cx - icon / 2, cy - icon / 2, cx + icon / 2, cy + icon / 2); c.drawRoundRect(r, icon * 0.26f, icon * 0.26f, box)
            val (e, name) = FAKE_APPS[i % FAKE_APPS.size]
            c.drawText(e, cx, cy + icon * 0.18f, emoji)
            c.drawText(name, cx, cy + icon / 2 + 18f * density, text)
        }
        // Dock
        box.color = Color.argb(70, 255, 255, 255)
        r.set(16f * density, h - 120f * density, w - 16f * density, h - 30f * density); c.drawRoundRect(r, 28f * density, 28f * density, box)
        return bmp
    }
}
