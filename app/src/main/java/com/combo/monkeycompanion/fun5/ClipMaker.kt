package com.combo.monkeycompanion.fun5

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.combo.monkeycompanion.brain.Gag
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.v5.ClipScript
import java.io.File
import kotlin.math.sin

/**
 * v5 "share a funny clip": the character's rig is drawn offscreen (never a screen recording) on a jungle
 * background with a caption, frame by frame, then encoded to H.264 MP4 with MediaCodec + MediaMuxer and handed to
 * the Android share sheet (WhatsApp status is one of the targets there). Nothing is posted automatically.
 */
class ClipRenderer(private val character: Character, private val wear: Wear?, val bit: ClipScript.Bit) {
    private val rig = MonkeyRig()
    private val pose = Pose()
    private val ctx = AnimContext().apply { bit.gag?.let { gag = Gag.valueOf(it) } }
    private val renderer = character.newRenderer().also { it.wear = wear }
    private val state = MonkeyState.valueOf(bit.state)
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val shadow = Paint(caption).apply { color = Color.argb(160, 0, 0, 0) }

    /** Draws frame [i] into [c] (ClipScript.W x ClipScript.H). */
    fun draw(c: Canvas, i: Int) {
        val f = ClipScript.frame(i)
        val w = ClipScript.W.toFloat(); val h = ClipScript.H.toFloat()
        // Jungle: sky-to-green gradient, sun, back hills, vines, leaves, ground.
        bg.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.rgb(0x9F, 0xE2, 0xFF), Color.rgb(0xC8, 0xF0, 0xC0), Color.rgb(0x3E, 0x8E, 0x41)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, bg)
        p.style = Paint.Style.FILL
        p.color = Color.argb(200, 0xFF, 0xE0, 0x6A); c.drawCircle(w * 0.8f, h * 0.12f, 46f, p)
        p.color = Color.rgb(0x5C, 0xA8, 0x4E); c.drawCircle(w * 0.15f, h * 0.84f, 220f, p); c.drawCircle(w * 0.9f, h * 0.86f, 200f, p)
        p.color = Color.rgb(0x2F, 0x7A, 0x34)
        for (k in 0 until 6) { // swaying vines from the top
            val x = (k + 0.5f) * w / 6f; val sway = 10f * sin(f.t * 1.5f + k)
            p.style = Paint.Style.STROKE; p.strokeWidth = 5f; path.reset(); path.moveTo(x, 0f); path.quadTo(x + sway, 90f + k * 14f, x - sway * 0.5f, 150f + k * 22f); c.drawPath(path, p)
            p.style = Paint.Style.FILL; c.drawOval(x - sway * 0.5f - 14f, 140f + k * 22f, x - sway * 0.5f + 14f, 166f + k * 22f, p)
        }
        p.color = Color.rgb(0x5B, 0x3A, 0x1E); c.drawRect(0f, h * 0.8f, w, h, p)
        p.color = Color.rgb(0x7A, 0xB8, 0x3A); c.drawRect(0f, h * 0.8f, w, h * 0.8f + 14f, p)

        // The character: walks in, then does the bit.
        val scale = 2.6f
        val ax = w * (0.5f + f.walkX); val ay = h * 0.8f + 4f
        val st = if (f.walkX < 0f) MonkeyState.WALK else state
        val stTime = if (f.walkX < 0f) f.t else f.t - 0.8f
        Animations.fill(pose, st, f.t, stTime, ctx)
        rig.layout(pose, ax, ay, scale, 1f)
        renderer.draw(c, rig, pose, f.t)

        // Caption (two lines) with a soft shadow, and the logo at the end.
        if (f.captionAlpha > 0f) {
            caption.textSize = 34f; shadow.textSize = 34f
            caption.alpha = (255 * f.captionAlpha).toInt(); shadow.alpha = (160 * f.captionAlpha).toInt()
            ClipScript.caption(bit, character.name).split('\n').forEachIndexed { li, line ->
                val y = 90f + li * 44f; if (li == 1) { caption.textSize = 24f; shadow.textSize = 24f }
                c.drawText(line, w / 2 + 2f, y + 2f, shadow); c.drawText(line, w / 2, y, caption)
            }
        }
        if (f.showLogo) { caption.textSize = 22f; caption.alpha = 230; c.drawText("made with Set-Tai", w / 2, h - 30f, caption) }
    }

    fun frame(i: Int): Bitmap = Bitmap.createBitmap(ClipScript.W, ClipScript.H, Bitmap.Config.ARGB_8888).also { draw(Canvas(it), i) }
}

object ClipMaker {
    const val MIME = "video/avc"

    /** Renders and encodes the clip; returns the MP4 file (in cache/clips) or throws. Run off the main thread. */
    fun encode(ctx: Context, r: ClipRenderer, onProgress: (Float) -> Unit = {}): File {
        val dir = File(ctx.cacheDir, "clips").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
        val out = File(dir, "settai-${System.currentTimeMillis()}.mp4")
        val w = ClipScript.W; val h = ClipScript.H
        val fmt = MediaFormat.createVideoFormat(MIME, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, ClipScript.FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1; var muxing = false
        val info = MediaCodec.BufferInfo()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val argb = IntArray(w * h)
        val n = ClipScript.frames
        var next = 0; var inputDone = false; var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        if (next >= n) {
                            codec.queueInputBuffer(ii, 0, 0, next * 1_000_000L / ClipScript.FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else {
                            r.draw(canvas, next)
                            bmp.getPixels(argb, 0, w, 0, 0, w, h)
                            val img = codec.getInputImage(ii) ?: error("no input image")
                            Yuv.fill(img, argb, w, h)
                            codec.queueInputBuffer(ii, 0, w * h * 3 / 2, next * 1_000_000L / ClipScript.FPS, 0)
                            next++; onProgress(next / n.toFloat())
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start(); muxing = true }
                    oi >= 0 -> {
                        val buf = codec.getOutputBuffer(oi)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxing) { buf.position(info.offset); buf.limit(info.offset + info.size); muxer.writeSampleData(track, buf, info) }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }; codec.release()
            runCatching { if (muxing) muxer.stop() }; runCatching { muxer.release() }
            bmp.recycle()
        }
        return out
    }

    /** Opens the share sheet for [file] (the user picks WhatsApp status or anything else, and presses post). */
    fun share(ctx: Context, file: File, caption: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".clips", file)
        val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, caption)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Share the clip").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}

/** ARGB → YUV420 into a MediaCodec input Image (handles planar and semi-planar layouts via the strides). */
object Yuv {
    fun y(r: Int, g: Int, b: Int) = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16)
    fun u(r: Int, g: Int, b: Int) = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128)
    fun v(r: Int, g: Int, b: Int) = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128)

    /** Planar I420 (testable on the JVM; the encoder writes the same values through [fill]). */
    fun toI420(argb: IntArray, w: Int, h: Int, yp: ByteArray, up: ByteArray, vp: ByteArray) {
        for (j in 0 until h) for (i in 0 until w) {
            val c = argb[j * w + i]; val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            yp[j * w + i] = y(r, g, b).toByte()
            if (j % 2 == 0 && i % 2 == 0) { val o = (j / 2) * (w / 2) + i / 2; up[o] = u(r, g, b).toByte(); vp[o] = v(r, g, b).toByte() }
        }
    }

    /** Writes into the codec's input image, honouring its row/pixel strides (planar or semi-planar). */
    fun fill(img: android.media.Image, argb: IntArray, w: Int, h: Int) {
        val pl = img.planes
        val yb = pl[0].buffer; val yRow = pl[0].rowStride; val yPix = pl[0].pixelStride
        val ub = pl[1].buffer; val vb = pl[2].buffer; val cRow = pl[1].rowStride; val cPix = pl[1].pixelStride
        for (j in 0 until h) for (i in 0 until w) {
            val c = argb[j * w + i]; val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            yb.put(j * yRow + i * yPix, y(r, g, b).toByte())
            if (j % 2 == 0 && i % 2 == 0) {
                val o = (j / 2) * cRow + (i / 2) * cPix
                ub.put(o, u(r, g, b).toByte()); vb.put(o, v(r, g, b).toByte())
            }
        }
    }
}
