package com.combo.monkeycompanion.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import kotlin.math.max
import kotlin.random.Random

/** Renders (and caches) the wallpaper background: soft jungle, a plain colour, or the user's image. */
class WallpaperBackground(private val ctx: Context) {
    private var cache: Bitmap? = null
    private var key = ""

    fun draw(c: Canvas, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val k = "$w x $h v${WallpaperPrefs.version(ctx)}"
        if (k != key || cache == null) {
            cache?.recycle()
            cache = runCatching { render(w, h) }.getOrNull()
            key = k
        }
        cache?.let { c.drawBitmap(it, 0f, 0f, null) } ?: c.drawColor(Color.rgb(0x2E, 0x5E, 0x4E))
    }

    fun release() { cache?.recycle(); cache = null; key = "" }

    private fun render(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        when (WallpaperPrefs.bgMode(ctx)) {
            WallpaperPrefs.BG_COLOR -> c.drawColor(WallpaperPrefs.bgColor(ctx))
            WallpaperPrefs.BG_IMAGE -> if (!drawImage(c, w, h, WallpaperPrefs.bgImage(ctx))) jungle(c, w, h, ctx.resources.displayMetrics.density)
            else -> jungle(c, w, h, ctx.resources.displayMetrics.density)
        }
        return bmp
    }

    /** Centre-crop the picked image (decoded at about screen size). */
    private fun drawImage(c: Canvas, w: Int, h: Int, uri: String?): Boolean = drawCropped(ctx, c, w, h, uri, 255)

    companion object {
        fun drawCropped(ctx: Context, c: Canvas, w: Int, h: Int, uri: String?, alpha: Int): Boolean {
            if (uri.isNullOrBlank()) return false
            return runCatching {
                val u = Uri.parse(uri)
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
                var sample = 1
                while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2
                val img = ctx.contentResolver.openInputStream(u)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return false
                val s = max(w / img.width.toFloat(), h / img.height.toFloat())
                val m = Matrix().apply { setScale(s, s); postTranslate((w - img.width * s) / 2f, (h - img.height * s) / 2f) }
                c.drawBitmap(img, m, Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha })
                img.recycle()
                true
            }.getOrDefault(false)
        }

        /** Soft jungle: misty gradient, sun glow, distant canopy, hanging vines and big leaves. */
        fun jungle(c: Canvas, w: Int, h: Int, d: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                intArrayOf(Color.rgb(0xCF, 0xEB, 0xD6), Color.rgb(0x8C, 0xC9, 0xA4), Color.rgb(0x3F, 0x8A, 0x67), Color.rgb(0x1F, 0x4E, 0x3C)),
                floatArrayOf(0f, 0.35f, 0.75f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = RadialGradient(w * 0.72f, h * 0.16f, w * 0.6f, Color.argb(150, 255, 248, 214), Color.argb(0, 255, 248, 214), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = null
            val rnd = Random(7)
            // distant canopy blobs
            for (layer in 0..2) {
                p.color = when (layer) { 0 -> Color.argb(70, 0x5E, 0xA8, 0x7E); 1 -> Color.argb(90, 0x3E, 0x88, 0x60); else -> Color.argb(120, 0x2A, 0x66, 0x48) }
                val baseY = h * (0.62f + layer * 0.1f)
                var x = -40f * d
                while (x < w + 40 * d) {
                    val r = (40 + rnd.nextInt(50)) * d
                    c.drawCircle(x, baseY + rnd.nextInt(30) * d, r, p)
                    x += r * 1.1f
                }
                c.drawRect(0f, baseY + 20 * d, w.toFloat(), h.toFloat(), p)
            }
            // hanging vines with little leaves
            val vine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3.2f * d; color = Color.rgb(0x3B, 0x6E, 0x3A); strokeCap = Paint.Cap.ROUND }
            val leaf = Paint(Paint.ANTI_ALIAS_FLAG)
            val path = Path()
            for (i in 0 until 5) {
                val x0 = w * (0.08f + i * 0.21f) + rnd.nextInt(30) * d
                val len = h * (0.18f + rnd.nextFloat() * 0.3f)
                val sway = (rnd.nextFloat() - 0.5f) * 60 * d
                path.reset(); path.moveTo(x0, -5f); path.cubicTo(x0 + sway, len * 0.35f, x0 - sway, len * 0.7f, x0 + sway * 0.4f, len)
                c.drawPath(path, vine)
                var k = 0.12f
                while (k < 1f) {
                    val y = len * k
                    val x = x0 + sway * 0.4f * k * (if ((k * 10).toInt() % 2 == 0) 1 else -1)
                    leaf.color = if (rnd.nextBoolean()) Color.rgb(0x4F, 0x9A, 0x4C) else Color.rgb(0x3E, 0x82, 0x3F)
                    drawLeaf(c, leaf, x, y, 13 * d, if (rnd.nextBoolean()) 35f else 145f)
                    k += 0.11f + rnd.nextFloat() * 0.06f
                }
            }
            // big corner leaves
            val big = Paint(Paint.ANTI_ALIAS_FLAG)
            val leaves = arrayOf(
                floatArrayOf(-10f, h * 0.9f, 120f, -40f), floatArrayOf(w * 0.18f, h * 1.02f, 140f, -70f),
                floatArrayOf(w + 10f, h * 0.86f, 130f, -140f), floatArrayOf(w * 0.85f, h * 1.03f, 110f, -110f),
                floatArrayOf(-20f, h * 0.05f, 90f, 30f), floatArrayOf(w + 20f, h * 0.02f, 100f, 150f),
            )
            for (lf in leaves) {
                big.color = Color.rgb(0x2C, 0x6E, 0x3E); drawLeaf(c, big, lf[0], lf[1], lf[2] * d, lf[3])
                big.color = Color.argb(70, 0xB8, 0xE6, 0x9A); drawLeaf(c, big, lf[0], lf[1], lf[2] * d * 0.55f, lf[3])
            }
        }

        private fun drawLeaf(c: Canvas, p: Paint, x: Float, y: Float, len: Float, deg: Float) {
            c.save(); c.translate(x, y); c.rotate(deg)
            val path = Path()
            path.moveTo(0f, 0f)
            path.cubicTo(len * 0.3f, -len * 0.38f, len * 0.75f, -len * 0.3f, len, 0f)
            path.cubicTo(len * 0.75f, len * 0.3f, len * 0.3f, len * 0.38f, 0f, 0f)
            c.drawPath(path, p)
            c.restore()
        }
    }
}
