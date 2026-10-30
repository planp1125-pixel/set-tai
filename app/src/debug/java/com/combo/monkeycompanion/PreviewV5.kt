package com.combo.monkeycompanion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.fun5.ClipRenderer
import com.combo.monkeycompanion.fun5.Props5
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.v5.ClipScript
import com.combo.monkeycompanion.v5.Festivals
import com.combo.monkeycompanion.v5.Friendship
import com.combo.monkeycompanion.v5.WeatherMood

/** Debug-only v5 preview sheet: clip frames, festival & weather props, motion, games, munch, friendship. */
object PreviewV5 {
    private val brown = Color.rgb(0x5A, 0x3A, 0x1E)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x8A, 0x6A, 0x4A); textSize = 18f; textAlign = Paint.Align.CENTER }
    private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 30f; typeface = Typeface.DEFAULT_BOLD }

    fun render(): Bitmap {
        val chars = CharacterRegistry.all
        val cw = 300; val W = cw * 4 + 40
        val clipH = 480; val cardH = 330
        val H = 90 + (clipH + 70) + 4 * (cardH + 60) + 60
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.rgb(0xFF, 0xF3, 0xE0))
        val big = Paint(head).apply { textSize = 40f; textAlign = Paint.Align.CENTER }
        c.drawText("Settai v5.0 · fun pack (every character, its own way)", W / 2f, 56f, big)
        var y = 90f
        c.drawText("🎬 Share a clip: 5 s, rendered offscreen from the rig (frames from each character)", 20f, y + 26f, head); y += 44f
        val bits = listOf(ClipScript.Bit.DANCE, ClipScript.Bit.AIR_GUITAR, ClipScript.Bit.SLIP, ClipScript.Bit.SNEEZE)
        chars.take(4).forEachIndexed { i, ch ->
            val r = ClipRenderer(ch, Wear(ch), bits[i % bits.size])
            val f = r.frame(60)
            val sc = clipH.toFloat() / f.height
            val dst = RectF(20f + i * cw + (cw - f.width * sc) / 2f, y, 20f + i * cw + (cw + f.width * sc) / 2f, y + clipH)
            c.drawBitmap(f, null, dst, null)
            c.drawText("${ch.name}: ${bits[i % bits.size].title}", 20f + i * cw + cw / 2f, y + clipH + 22f, sub)
        }
        y += clipH + 40f

        class Cell(val label: String, val draw: (Canvas, Float, Float, Float, Float, Float) -> Unit, val state: MonkeyState) {
            constructor(label: String, draw: (Canvas, Float, Float, Float, Float, Float) -> Unit) : this(label, draw, MonkeyState.IDLE)
        }
        val rows = listOf(
            "🪔 Festival moods (on-device 2026–27 calendar)" to listOf(
                Cell("Deepavali: diya") { cv, hx, hy, fx, fy, s -> Props5.festival(cv, Festivals.Prop.DIYA, hx, hy, fx, fy, s, 0.3f) },
                Cell("Pongal: pot boils over") { cv, hx, hy, fx, fy, s -> Props5.festival(cv, Festivals.Prop.PONGAL_POT, hx, hy, fx, fy, s, 0.3f) },
                Cell("Independence Day: flag") { cv, hx, hy, fx, fy, s -> Props5.festival(cv, Festivals.Prop.FLAG, hx, hy, fx, fy, s, 0.3f) },
                Cell("4 PM filter coffee") { cv, hx, hy, _, _, s -> Props5.coffee(cv, hx, hy, s, 0.6f) },
            ),
            "🌦️ Weather (Open-Meteo, every 3 h at most)" to listOf(
                Cell("Rain: umbrella") { cv, hx, hy, fx, fy, s -> Props5.weather(cv, WeatherMood.Mood.RAIN, hx, hy, fx, fy, s, 0.4f) },
                Cell("Hot: fan + tender coconut") { cv, hx, hy, fx, fy, s -> Props5.weather(cv, WeatherMood.Mood.HOT, hx, hy, fx, fy, s, 0.4f) },
                Cell("Cold: shawl + shiver") { cv, hx, hy, fx, fy, s -> Props5.weather(cv, WeatherMood.Mood.COLD, hx, hy, fx, fy, s, 0.4f) },
                Cell("Cloudy") { cv, hx, hy, fx, fy, s -> Props5.weather(cv, WeatherMood.Mood.CLOUDY, hx, hy, fx, fy, s, 0.4f) },
            ),
            "📳 Shake & tilt · 😴 sleep nag · 🤝 friendship" to listOf(
                Cell("Shake: dizzy stars", { cv, hx, hy, _, _, s -> Props5.dizzy(cv, hx, hy, s, 0.5f) }, MonkeyState.STARTLED),
                Cell("Face-down: sleep mask", { cv, hx, hy, _, _, s -> Props5.sleepMask(cv, hx, hy, s) }, MonkeyState.SLEEP),
                Cell("23:30+: yawning nag", { cv, hx, hy, _, _, _ ->
                    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 17f; color = Color.WHITE }
                    val line = com.combo.monkeycompanion.v5.SleepNag.line(2, "monkey").take(34)
                    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 0x3A, 0x26, 0x18) }
                    cv.drawRoundRect(RectF(hx - 140f, hy - 60f, hx + 140f, hy - 24f), 16f, 16f, bg); cv.drawText(line, hx - 130f, hy - 36f, tp)
                }, MonkeyState.SLEEP),
                Cell("Level 5: crown", { cv, hx, hy, _, _, s -> Props5.crown(cv, hx, hy, s) }, MonkeyState.HAPPY_PAT),
            ),
            "🎮 Games · 🍔 eats a (fake) notification" to listOf(
                Cell("Banana catch", { cv, hx, hy, fx, fy, s ->
                    for (k in 0 until 4) Props5.banana(cv, fx - 90f + k * 60f, hy - 20f - (k % 2) * 40f, 1.3f, k == 2, k * 40f)
                }),
                Cell("Staring contest", { cv, hx, hy, _, _, s -> Props5.stare(cv, hx, hy, s, 0.7f, 0.2f) }),
                Cell("Rock-paper-scissors", { cv, hx, hy, _, _, _ ->
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f }; cv.drawText("✊ vs ✌️", hx - 60f, hy - 12f, p)
                }),
                Cell("Munch: banner bitten", { cv, hx, hy, _, _, s -> Props5.banner(cv, hx - 110f, hy - 60f, 220f, 40f, "Amma: dinner ready", 0.62f, 1.3f) }),
            ),
        )
        for ((label, cells) in rows) {
            c.drawText(label, 20f, y + 26f, head); y += 40f
            cells.forEachIndexed { i, cell ->
                val ch = chars[i % chars.size]
                val r = RectF(20f + i * cw + 8f, y, 20f + (i + 1) * cw - 8f, y + cardH - 40f)
                card(c, r, ch, cell.state, cell.draw)
                c.drawText("${ch.name} · ${cell.label}", r.centerX(), y + cardH - 14f, sub)
            }
            y += cardH + 20f
        }
        // Friendship card text (as on the app home)
        val st = Friendship.State(xp = Friendship.xpFor(5) + 30, streak = 4)
        val lvl = Friendship.level(st.xp)
        c.drawText("🤝 Home card: Level $lvl — ${Friendship.title(lvl)} · ${(Friendship.progress(st.xp) * 100).toInt()}% · 🔥 4-day streak · unlocked: " +
            Friendship.unlocked(st.xp).joinToString(", ") { it.title }, 20f, y + 20f, sub.apply { textAlign = Paint.Align.LEFT })
        sub.textAlign = Paint.Align.CENTER
        return bmp
    }

    private fun card(c: Canvas, r: RectF, ch: Character, state: MonkeyState, draw: (Canvas, Float, Float, Float, Float, Float) -> Unit) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(0xDD, 0xF1, 0xD8); c.drawRoundRect(r, 24f, 24f, p)
        p.color = Color.rgb(0xB8, 0xDA, 0xA8); c.drawRect(r.left, r.bottom - 40f, r.right, r.bottom, p)
        c.save(); c.clipRect(r)
        val rig = MonkeyRig(); val pose = Pose()
        Animations.fill(pose, state, 0.8f, 0.8f, AnimContext())
        val s = 1.05f
        val fx = r.centerX(); val fy = r.bottom - 34f
        rig.layout(pose, fx, fy, s, 1f)
        ch.newRenderer().also { it.wear = Wear(ch) }.draw(c, rig, pose, 0.8f)
        val ht = FloatArray(2); rig.headTop(ht)
        draw(c, ht[0], ht[1], fx, fy, s)
        c.restore()
    }
}
