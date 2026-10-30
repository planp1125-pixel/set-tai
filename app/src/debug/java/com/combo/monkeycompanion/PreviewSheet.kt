package com.combo.monkeycompanion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.PowerPhase as P
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.MonkeyRenderer
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.CloudBubble

/** Debug-only: renders a sheet of the monkey's poses (used by the receiver and a JVM test). */
object PreviewSheet {
    private class Shot(val label: String, val state: MonkeyState, val t: Float, val st: Float,
                       val say: String? = null, val facing: Float = 1f, val eyes: Float = 1f,
                       val move: Int = -1, val buttons: List<String> = emptyList(),
                       val reply: Boolean = false, val field: String? = null,
                       val edge: Int = AnimContext.GROUND, val wallLeft: Boolean = true,
                       val gag: com.combo.monkeycompanion.brain.Gag? = null,
                       val exit: com.combo.monkeycompanion.brain.Exit? = null)

    /** v3.2 sheet: the comedy gags. */
    fun renderGags(): Bitmap {
        fun g(label: String, gag: com.combo.monkeycompanion.brain.Gag, st: Float, say: String? = null) =
            Shot(label, MonkeyState.GAG, st, st, say, gag = gag)
        fun x(label: String, e: com.combo.monkeycompanion.brain.Exit, st: Float, say: String? = null) =
            Shot(label, MonkeyState.EXIT, st, st, say, exit = e)
        return render(listOf(
            g("Sneeze: ah... ah...", com.combo.monkeycompanion.brain.Gag.SNEEZE, 1.0f),
            g("Sneeze: ACHOO!", com.combo.monkeycompanion.brain.Gag.SNEEZE, 1.38f),
            g("Banana peel: whoops", com.combo.monkeycompanion.brain.Gag.SLIP, 0.95f),
            g("Banana peel: thud", com.combo.monkeycompanion.brain.Gag.SLIP, 2.6f, "I meant to do that."),
            g("Binoculars", com.combo.monkeycompanion.brain.Gag.BINOCULARS, 2.5f, "Spotted: one human."),
            g("Fake sleep: one eye open", com.combo.monkeycompanion.brain.Gag.FAKE_SLEEP, 2.2f),
            g("Juggling bananas", com.combo.monkeycompanion.brain.Gag.JUGGLE, 1.3f),
            g("Hiding behind its tail", com.combo.monkeycompanion.brain.Gag.TAIL_HIDE, 1.2f, "You can't see me."),
            g("Chasing a fly", com.combo.monkeycompanion.brain.Gag.CHASE_FLY, 1.0f),
            g("Swat!", com.combo.monkeycompanion.brain.Gag.CHASE_FLY, 1.85f),
            g("Air guitar", com.combo.monkeycompanion.brain.Gag.AIR_GUITAR, 1.4f, "Thank you, Chennai!"),
            g("Selfie (flash!)", com.combo.monkeycompanion.brain.Gag.SELFIE, 1.35f, "Say banana!"),
            Shot("Long-press: Quit Settai?", MonkeyState.SAD, 1.0f, 1.0f, "Leaving me already, machan? Quit Settai?", buttons = listOf("Yes", "No")),
            x("Quit exit 1: hero faint", com.combo.monkeycompanion.brain.Exit.FAINT, 0.9f, "Tell my bananas... I loved them..."),
            x("...swoons, then fades", com.combo.monkeycompanion.brain.Exit.FAINT, 3.1f),
            x("Quit exit 2: lights the fuse", com.combo.monkeycompanion.brain.Exit.ROCKET, 1.0f, "To infinity and banana!"),
            x("...banana rocket lift-off", com.combo.monkeycompanion.brain.Exit.ROCKET, 1.8f),
            x("Quit exit 3: bye bye machan", com.combo.monkeycompanion.brain.Exit.POOF, 0.75f, "Bye bye, machan! \uD83D\uDC4B"),
            x("...poof!", com.combo.monkeycompanion.brain.Exit.POOF, 1.85f),
        ))
    }

    private class PowerShot(val label: String, val power: com.combo.monkeycompanion.brain.Superpower,
                            val phase: com.combo.monkeycompanion.brain.PowerPhase, val k: Float,
                            val vx: Float = 0f, val vy: Float = 0f, val say: String? = null,
                            val lift: Float = 0f, val crashed: Boolean = false, val sinceRing: Float = 9f,
                            val ringLeft: Boolean = false, val sim: Float = 0.45f, val wall: Int = 0)

    /** v3.2 sheet: superpowers (with their particles simulated for a moment before each still). */
    fun renderPowers(): Bitmap {
        val R = com.combo.monkeycompanion.brain.Superpower.RocketMonkey
        val B = com.combo.monkeycompanion.brain.Superpower.SuperBanana
        val shots = listOf(
            PowerShot("Rocket Monkey: ignition", R, P.TAKEOFF, 0.75f, vy = -420f, lift = 40f, say = "Hold my banana. Taking off!"),
            PowerShot("Swooping on foot jets", R, P.FLY, 0.5f, vx = 900f, vy = -520f, lift = 230f),
            PowerShot("Palm ring blasts", R, P.FIRE, 0.5f, lift = 230f, sinceRing = 0.05f, ringLeft = false, sim = 0.64f, say = "Pew! Pew!"),
            PowerShot("Engine sputters...", R, P.SPUTTER, 0.4f, vx = 520f, vy = 160f, lift = 200f, say = "Machan, fuel low! FUEL LOW!"),
            PowerShot("Crash into the screen edge", R, P.CRASH, 0.35f, lift = 170f, crashed = true, wall = 1, say = "Ayyo! Who put a screen edge here?!"),
            PowerShot("Sooty, but fine", R, P.POSE, 0.3f, crashed = true, sim = 0.8f, say = "*cough* I'm fine. Totally fine."),
            PowerShot("Thud landing + dust", R, P.LAND, 0.62f, sim = 0.2f, say = "Touchdown! Semma landing!"),
            PowerShot("Super Banana: lift-off", B, P.TAKEOFF, 0.8f, vy = -500f, lift = 40f, say = "Up, up and a-banana!"),
            PowerShot("Fist-first flight + speed lines", B, P.FLY, 0.5f, vx = 1400f, vy = -80f, lift = 220f),
            PowerShot("Swooping dive, cape flapping", B, P.FLY, 0.6f, vx = 900f, vy = 700f, lift = 230f),
            PowerShot("Super landing", B, P.LAND, 0.72f, sim = 0.25f),
            PowerShot("Heroic pose", B, P.POSE, 0.5f, say = "Fear not, citizen! Settai is here!"),
        )
        val cols = 4; val cw = 520; val ch = 760
        val bmp = Bitmap.createBitmap(cols * cw, ((shots.size + cols - 1) / cols) * ch, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xFF, 0xF4, 0xE2))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x5A, 0x36, 0x18); textSize = 30f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xDD, 0xEE, 0xFF) }
        val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x9A, 0xB4, 0xD0) }
        val rig = MonkeyRig(); val renderer = MonkeyRenderer(); val ctx = AnimContext()
        val pose = Pose()
        val parts = com.combo.monkeycompanion.rig.Particles()
        val emitter = com.combo.monkeycompanion.rig.PowerEmitter(parts)
        val scale = 2.1f; val density = 2.1f
        val scratch = android.graphics.Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        shots.forEachIndexed { i, s ->
            val x0 = (i % cols) * cw.toFloat(); val y0 = (i / cols) * ch.toFloat()
            val cardR = RectF(x0 + 10, y0 + 10, x0 + cw - 10, y0 + ch - 10)
            c.drawRoundRect(cardR, 36f, 36f, card)
            val facing = if (s.vx < 0f) -1f else if (s.wall != 0) s.wall.toFloat() else 1f
            val sp = kotlin.math.hypot(s.vx, s.vy)
            ctx.power = s.power; ctx.powerPhase = s.phase; ctx.crashed = s.crashed
            ctx.speed = (sp / (900f * density)).coerceIn(0f, 1f) + (if (sp > 0f) 0.5f else 0f)
            ctx.speed = ctx.speed.coerceAtMost(1f)
            ctx.heading = if (sp > 0f) Math.toDegrees(kotlin.math.atan2(kotlin.math.abs(s.vx).toDouble(), (-s.vy).toDouble())).toFloat() else 0f
            ctx.sinceRing = s.sinceRing; ctx.ringLeft = s.ringLeft
            val ax = if (s.wall != 0) x0 + cw - 10f - 72f * scale * 0.72f else x0 + cw / 2f
            val ay = y0 + ch - 70f - s.lift
            if (s.wall != 0) c.drawRect(RectF(x0 + cw - 24f, y0 + 10f, x0 + cw - 10f, y0 + ch - 10f), wallPaint)
            // Simulate the last moments of flight so trails, rings and dust exist in the still.
            parts.clear(); emitter.reset(); renderer.resetCape()
            val n = (s.sim * 60f).toInt().coerceAtLeast(1)
            val dt = 1f / 60f
            for (f in 0..n) {
                val back = (n - f) * dt
                val kk = (s.k - back * (if (s.phase == P.LAND) 1.1f else 0.5f)).coerceIn(0f, 1f)
                ctx.powerK = kk
                if (s.phase == P.FIRE) ctx.sinceRing = emitter.sinceRing.coerceAtMost(9f)
                if (s.phase == P.FIRE) ctx.ringLeft = emitter.leftHand
                val t = 1.0f + f * dt
                Animations.fill(pose, MonkeyState.POWER, t, t, ctx)
                rig.layout(pose, ax - s.vx * scale / density * back * 0.5f, ay - s.vy * scale / density * back * 0.5f, scale, facing)
                emitter.frame(rig, pose, s.power, s.phase, 0, kk, s.vx * scale / density, s.vy * scale / density, facing, scale, dt)
                renderer.draw(scratch, rig, pose, t)  // steps the cape
            }
            val tEnd = 1.0f + n * dt
            c.save(); c.clipRect(cardR)
            parts.draw(c)
            rig.layout(pose, ax, ay, scale, facing)
            renderer.draw(c, rig, pose, tEnd)
            c.restore()
            if (s.say != null) {
                val b = CloudBubble(density, 15f * density)
                b.setText(s.say, (150 * density).toInt(), emptyList(), false)
                val head = FloatArray(2); rig.headTop(head)
                val left = (x0 + cw / 2f - b.width / 2f).coerceIn(x0 + 4f, maxOf(x0 + 4f, x0 + cw - 4f - b.width))
                val top = (head[1] + 8f - b.trailSpace - b.height).coerceAtLeast(y0 + 14f)
                b.draw(c, left, top, head[0].coerceIn(left + 20f, left + b.width - 20f), minOf(head[1], top + b.height + b.trailSpace + 40f), 1f, 1f)
            }
            c.drawText(s.label, x0 + cw / 2f, y0 + ch - 24f, label)
        }
        return bmp
    }

    /** v4 sheet: the seven show-off powers, with their screen effects drawn on each card. */
    fun renderShowOff(): Bitmap {
        val shots = listOf(
            PowerShot("Thunder Monkey: charging", com.combo.monkeycompanion.brain.Superpower.ThunderMonkey, P.CHARGE, 0.85f, sim = 0.5f, say = "Minnal power! Stand back, machan!"),
            PowerShot("Thunder strike!", com.combo.monkeycompanion.brain.Superpower.ThunderMonkey, P.STRIKE, 0.3f, sim = 0.2f),
            PowerShot("Ninja smoke-bomb teleport", com.combo.monkeycompanion.brain.Superpower.NinjaSettai, P.TELEPORT, 0.62f, sim = 0.5f, say = "Poof! Over here!"),
            PowerShot("Shadow clones dance", com.combo.monkeycompanion.brain.Superpower.NinjaSettai, P.CLONES, 0.5f, sim = 0.1f, say = "Shadow clones, assemble!"),
            PowerShot("Kovam Mode: growing", com.combo.monkeycompanion.brain.Superpower.KovamMode, P.GROW, 0.9f, sim = 0.1f, say = "Kovam varudhu!"),
            PowerShot("Kovam SLAM (fake crack)", com.combo.monkeycompanion.brain.Superpower.KovamMode, P.SLAM, 0.6f, sim = 0.4f),
            PowerShot("...shrinks, embarrassed", com.combo.monkeycompanion.brain.Superpower.KovamMode, P.SHRINK, 0.6f, sim = 0.1f, say = "Is that crack real? It isn't. Phew."),
            PowerShot("Ice Monkey (hot phone)", com.combo.monkeycompanion.brain.Superpower.IceMonkey, P.FROST, 0.6f, sim = 1.6f, say = "Brrr! Cooling you down!"),
            PowerShot("Giant Mode: the eye", com.combo.monkeycompanion.brain.Superpower.GiantMode, P.PEEK, 0.4f, sim = 0.1f),
            PowerShot("Time Freeze dodge", com.combo.monkeycompanion.brain.Superpower.TimeFreeze, P.DODGE, 0.5f, sim = 0.1f, say = "Slow-mo! Watch my moves!"),
            PowerShot("Magician: the hat", com.combo.monkeycompanion.brain.Superpower.Magician, P.MAGIC, 0.3f, sim = 0.4f, say = "Abracadabra... banana-dabra!"),
            PowerShot("Rabbit shows the count", com.combo.monkeycompanion.brain.Superpower.Magician, P.MAGIC, 0.8f, sim = 0.3f),
        )
        val cols = 4; val cw = 520; val ch = 760
        val bmp = Bitmap.createBitmap(cols * cw, ((shots.size + cols - 1) / cols) * ch, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xFF, 0xF4, 0xE2))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x5A, 0x36, 0x18); textSize = 30f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xDD, 0xEE, 0xFF) }
        val rig = MonkeyRig(); val renderer = MonkeyRenderer(); val ctx = AnimContext()
        val pose = Pose()
        val parts = com.combo.monkeycompanion.rig.Particles()
        val emitter = com.combo.monkeycompanion.rig.PowerEmitter(parts)
        renderer.rabbitSign = "7"
        val scale = 1.7f; val density = 2.1f
        shots.forEachIndexed { i, s ->
            val x0 = (i % cols) * cw.toFloat(); val y0 = (i / cols) * ch.toFloat()
            val cardR = RectF(x0 + 10, y0 + 10, x0 + cw - 10, y0 + ch - 10)
            c.drawRoundRect(cardR, 36f, 36f, card)
            ctx.power = s.power; ctx.powerPhase = s.phase; ctx.crashed = s.crashed; ctx.speed = 0f; ctx.heading = 0f
            val ax = x0 + cw / 2f; val ay = y0 + ch - 80f
            parts.clear(); emitter.reset()
            val n = (s.sim * 60f).toInt().coerceAtLeast(1); val dt = 1f / 60f
            for (f in 0..n) {
                val kk = (s.k - (n - f) * dt * 0.3f).coerceIn(0f, 1f)
                ctx.powerK = kk
                val t = 1f + f * dt
                Animations.fill(pose, MonkeyState.POWER, t, t, ctx)
                rig.layout(pose, ax, ay, scale, 1f)
                emitter.frame(rig, pose, s.power, s.phase, 0, kk, 0f, 0f, 1f, scale, dt)
            }
            val tEnd = 1f + n * dt
            c.save(); c.clipRect(cardR)
            // Screen effects in card space.
            val fx = com.combo.monkeycompanion.rig.ScreenFx()
            if (s.phase == P.SHRINK || s.phase == P.SLAM) fx.crackAt(ax - x0, ay - y0 - 40f)
            val hand = FloatArray(2); rig.handPoint(false, hand)
            c.save(); c.translate(x0, y0)
            fx.draw(c, cw.toFloat(), ch.toFloat(), s.phase, s.k, tEnd, s.crashed, ax - x0, ay - y0, hand[0] - x0, hand[1] - y0, density * 0.8f)
            c.restore()
            parts.draw(c)
            rig.layout(pose, ax, ay, scale, 1f)
            if (pose.clones > 0.02f) {
                val dodge = s.phase == P.DODGE
                for (j in 0 until 3) {
                    c.saveLayerAlpha(cardR, if (dodge) (70 / (j + 1)) else (150 * pose.clones).toInt())
                    if (dodge) c.translate(-(j + 1) * 14f * scale, 0f)
                    else when (j) {
                        0 -> c.translate(-120f * scale * pose.clones, 0f)
                        1 -> c.translate(120f * scale * pose.clones, 0f)
                        else -> { c.translate(ax, ay - 26f * scale); c.scale(0.78f, 0.78f); c.translate(-ax, -ay) }
                    }
                    renderer.draw(c, rig, pose, tEnd - j * 0.08f)
                    c.restore()
                }
            }
            renderer.draw(c, rig, pose, tEnd)
            c.restore()
            if (s.say != null) {
                val b = CloudBubble(density, 15f * density)
                b.setText(s.say, (150 * density).toInt(), emptyList(), false)
                val head = FloatArray(2); rig.headTop(head)
                val left = (x0 + cw / 2f - b.width / 2f).coerceIn(x0 + 4f, maxOf(x0 + 4f, x0 + cw - 4f - b.width))
                val top = (head[1] + 8f - b.trailSpace - b.height).coerceAtLeast(y0 + 14f)
                b.draw(c, left, top, head[0].coerceIn(left + 20f, left + b.width - 20f), minOf(head[1], top + b.height + b.trailSpace + 40f), 1f, 1f)
            }
            c.drawText(s.label, x0 + cw / 2f, y0 + ch - 24f, label)
        }
        return bmp
    }

    // ---------------- v4.1 characters ----------------
    private class CharShot(
        val label: String, val who: com.combo.monkeycompanion.character.Character,
        val state: MonkeyState = MonkeyState.IDLE, val st: Float = 1f,
        val power: com.combo.monkeycompanion.brain.Superpower? = null, val phase: P? = null, val k: Float = 0.5f, val sim: Float = 0.3f,
        val gag: com.combo.monkeycompanion.brain.Gag? = null, val wear: String? = null, val say: String? = null,
        val hud: Boolean = false, val speed: Float = 0f, val heading: Float = 0f,
    )

    /** v4.1: every character in key poses and powers; Mask Man with all items on and with minimal items. */
    fun renderCharacters(): Bitmap {
        val M = com.combo.monkeycompanion.character.MonkeyCharacter
        val SB = com.combo.monkeycompanion.character.Superboy
        val AA = com.combo.monkeycompanion.character.ArmourAnna
        val MM = com.combo.monkeycompanion.character.MaskMan
        val G = com.combo.monkeycompanion.brain.Gag.JUGGLE
        val all = "mask,cape,hat,shades,stache,scarf,belt,bananaphone,chicken,umbrella,skate,crow,boots;hat=top"
        val allNoShades = "mask,cape,hat,stache,belt,bananaphone,chicken,umbrella,skate,crow,boots;hat=lungi"
        val minimal = ";"
        val shots = listOf(
            CharShot("Settai (the original)", M, MonkeyState.IDLE, 1f, wear = ";", say = "Still the boss of this phone."),
            CharShot("Settai wardrobe: party hat, shades, scarf", M, MonkeyState.HAPPY_PAT, 0.6f, wear = "hat,shades,scarf;hat=party"),
            CharShot("Superboy: always floating", SB, MonkeyState.IDLE, 1f, say = "Never fear, Superboy is here!"),
            CharShot("Superboy sleeps under his cape", SB, MonkeyState.SLEEP, 2f),
            CharShot("Superboy: flight swoop", SB, power = com.combo.monkeycompanion.brain.HeroSwoop, phase = P.FLY, k = 0.5f, speed = 0.9f, heading = 70f),
            CharShot("Superboy: heat vision", SB, power = com.combo.monkeycompanion.brain.HeatVisionPower, phase = P.LASER, k = 0.55f, say = "Pew pew... with my EYES."),
            CharShot("Superboy: super breath", SB, power = com.combo.monkeycompanion.brain.SuperBreathPower, phase = P.BREATH, k = 0.6f, sim = 1f),
            CharShot("Superboy: super strength", SB, power = com.combo.monkeycompanion.brain.SuperStrengthPower, phase = P.LIFT, k = 0.7f),
            CharShot("Superboy: speed blur", SB, power = com.combo.monkeycompanion.brain.SpeedBlurPower, phase = P.BLUR, k = 0.5f),
            CharShot("Superboy: heroic landing", SB, power = com.combo.monkeycompanion.brain.HeroSwoop, phase = P.LAND, k = 0.8f),
            CharShot("Armour Anna: visor up", AA, MonkeyState.TALKING, 1f, say = "Filter coffee levels: critical."),
            CharShot("Armour Anna: heavy walk, visor down", AA, MonkeyState.WALK, 0.5f),
            CharShot("Armour Anna: jets + palm blasters", AA, power = com.combo.monkeycompanion.brain.ArmourJetsPower, phase = P.FIRE, k = 0.5f, sim = 0.6f),
            CharShot("Armour Anna: confetti rockets", AA, power = com.combo.monkeycompanion.brain.ConfettiRocketsPower, phase = P.CONFETTI, k = 0.55f, sim = 0.7f, say = "...Who loaded confetti?!"),
            CharShot("Armour Anna: shield bubble", AA, power = com.combo.monkeycompanion.brain.ShieldBubblePower, phase = P.SHIELD, k = 0.5f),
            CharShot("Armour Anna: scanner HUD (agent mode)", AA, MonkeyState.THINKING, 1f, hud = true),
            CharShot("Armour Anna: low battery droop", AA, power = com.combo.monkeycompanion.brain.LowPowerPower, phase = P.DROOP, k = 0.6f, say = "Suit power at 12%..."),
            CharShot("Mask Man: ALL items on", MM, MonkeyState.IDLE, 1f, wear = all, say = "Entry scene! Slow motion!"),
            CharShot("Mask Man: skateboard", MM, MonkeyState.WALK, 0.5f, wear = all),
            CharShot("Mask Man: utility belt (a fish?!)", MM, MonkeyState.GAG, 2.3f, gag = G, wear = allNoShades),
            CharShot("Mask Man: poked = SQUEAK", MM, MonkeyState.GIGGLE, 0.17f, wear = allNoShades),
            CharShot("Mask Man: umbrella float", MM, MonkeyState.FALLING, 0.8f, wear = allNoShades),
            CharShot("Mask Man: surprise, eyes pop", MM, MonkeyState.STARTLED, 0.6f, wear = allNoShades),
            CharShot("Mask Man: sneeze, moustache flies", MM, MonkeyState.GAG, 1.75f, gag = com.combo.monkeycompanion.brain.Gag.SNEEZE, wear = allNoShades),
            CharShot("Mask Man: crow steals the hat", MM, MonkeyState.GAG, 2.6f, gag = com.combo.monkeycompanion.brain.Gag.CHASE_FLY, wear = allNoShades),
            CharShot("Mask Man: banana phone", MM, MonkeyState.GAG, 1f, gag = com.combo.monkeycompanion.brain.Gag.SELFIE, wear = allNoShades, say = "Ring ring ring ring..."),
            CharShot("Mask Man: minimal items", MM, MonkeyState.IDLE, 1f, wear = minimal),
            CharShot("Minimal: no skateboard = walks", MM, MonkeyState.WALK, 0.5f, wear = minimal),
            CharShot("Minimal: no umbrella = just falls", MM, MonkeyState.FALLING, 0.8f, wear = minimal),
            CharShot("Mask Man: angry eyes (mask on)", MM, MonkeyState.ANGRY, 0.6f, wear = "mask,cape,belt;"),
        )
        val reg = com.combo.monkeycompanion.character.CharacterRegistry
        val saved = reg.all.associate { it.id to reg.wear(it).encode() }
        val cols = 4; val cw = 520; val ch = 720
        val bmp = Bitmap.createBitmap(cols * cw, ((shots.size + cols - 1) / cols) * ch, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xFF, 0xF4, 0xE2))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x5A, 0x36, 0x18); textSize = 27f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val card = Paint(Paint.ANTI_ALIAS_FLAG)
        val rig = MonkeyRig(); val ctx = AnimContext(); val pose = Pose()
        val parts = com.combo.monkeycompanion.rig.Particles()
        val emitter = com.combo.monkeycompanion.rig.PowerEmitter(parts)
        val scale = 1.6f; val density = 2.1f
        val scratch = Canvas(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        shots.forEachIndexed { i, s ->
            val x0 = (i % cols) * cw.toFloat(); val y0 = (i / cols) * ch.toFloat()
            val cardR = RectF(x0 + 10, y0 + 10, x0 + cw - 10, y0 + ch - 10)
            card.color = when (s.who) { SB -> Color.rgb(0xDD, 0xF4, 0xF2); AA -> Color.rgb(0xE8, 0xEA, 0xEE); MM -> Color.rgb(0xF4, 0xE6, 0xFA); else -> Color.rgb(0xFF, 0xEB, 0xCF) }
            c.drawRoundRect(cardR, 36f, 36f, card)
            val wear = reg.wear(s.who)
            if (s.wear != null) { wear.allOff(); wear.decode(s.wear) } else wear.reset()
            val renderer = s.who.newRenderer().also { it.wear = wear }
            if (renderer is com.combo.monkeycompanion.rig.ArmourRenderer) renderer.batteryPct = 12
            renderer.resetCape()
            val ax = x0 + cw / 2f; val ay = y0 + ch - 90f
            parts.clear(); emitter.reset()
            ctx.crashed = false; ctx.speed = s.speed; ctx.heading = s.heading
            var tEnd: Float
            if (s.power != null && s.phase != null) {
                ctx.power = s.power; ctx.powerPhase = s.phase
                val n = (s.sim * 60f).toInt().coerceAtLeast(1); val dt = 1f / 60f
                for (f in 0..n) {
                    val kk = (s.k - (n - f) * dt * 0.3f).coerceIn(0f, 1f)
                    ctx.powerK = kk
                    val t = 1f + f * dt
                    s.who.fill(pose, MonkeyState.POWER, t, t, ctx, wear)
                    rig.layout(pose, ax, ay - 120f * (if (s.phase == P.FLY) 1f else 0f), scale, 1f)
                    emitter.frame(rig, pose, s.power, s.phase, 0, kk, 0f, 0f, 1f, scale, dt)
                    renderer.draw(scratch, rig, pose, t)   // step the cape sim
                }
                tEnd = 1f + n * dt
            } else {
                ctx.power = null
                if (s.gag != null) ctx.gag = s.gag
                // A few frames so the cape / sims settle.
                var t = s.st
                for (f in 0..20) { t = s.st - (20 - f) / 60f; s.who.fill(pose, s.state, 1f + t, t.coerceAtLeast(0f), ctx, wear); rig.layout(pose, ax, ay, scale, 1f); renderer.draw(scratch, rig, pose, 1f + t) }
                tEnd = 1f + s.st
                if (s.hud) pose.hud = 1f
            }
            rig.layout(pose, ax, ay - 120f * (if (s.phase == P.FLY) 1f else 0f), scale, 1f)
            c.save(); c.clipRect(cardR)
            if (s.phase != null) {
                val fx = com.combo.monkeycompanion.rig.ScreenFx()
                val hand = FloatArray(2); rig.handPoint(false, hand)
                val eye = floatArrayOf(0f, -41f); rig.head.mapPoints(eye)
                val top = FloatArray(2); rig.headTop(top)
                c.save(); c.translate(x0, y0)
                fx.draw(c, cw.toFloat(), ch.toFloat(), s.phase, s.k, tEnd, false, ax - x0, ay - y0, hand[0] - x0,
                    (if (s.phase == P.LIFT) top[1] else hand[1]) - y0, density * 0.8f, eye[0] - x0, eye[1] - y0, 1, 12)
                c.restore()
            }
            parts.draw(c)
            if (s.phase == P.BLUR) for (j in 3 downTo 1) { c.saveLayerAlpha(cardR, 70 / j); c.translate(-j * 22f * scale, 0f); renderer.draw(c, rig, pose, tEnd - j * 0.08f); c.restore() }
            renderer.draw(c, rig, pose, tEnd)
            c.restore()
            if (s.say != null) {
                val b = CloudBubble(density, 14f * density)
                b.setText(s.say, (150 * density).toInt(), emptyList(), false)
                val head = FloatArray(2); rig.headTop(head)
                val left = (x0 + cw / 2f - b.width / 2f).coerceIn(x0 + 4f, maxOf(x0 + 4f, x0 + cw - 4f - b.width))
                val top = (head[1] + 8f - b.trailSpace - b.height).coerceAtLeast(y0 + 14f)
                b.draw(c, left, top, head[0].coerceIn(left + 20f, left + b.width - 20f), minOf(head[1], top + b.height + b.trailSpace + 40f), 1f, 1f)
            }
            c.drawText(s.label, x0 + cw / 2f, y0 + ch - 26f, label)
        }
        reg.all.forEach { reg.wear(it).decode(saved[it.id]) }
        return bmp
    }

    /** Version 2 sheet: chat states and the new dances. */
    fun renderV2(): Bitmap = render(
        listOf(
            Shot("Bubble chat (long-press, tap 💬)", MonkeyState.IDLE, 0.4f, 0.4f, "What's up? \uD83D\uDC12", reply = true),
            Shot("Typing a reply in the bubble", MonkeyState.IDLE, 1.2f, 1.2f, "What's up? \uD83D\uDC12", reply = true, field = "Tell me a banana joke"),
            Shot("Thinking (waiting for AI)", MonkeyState.THINKING, 0.6f, 0.6f, "Hmm..."),
            Shot("AI reply + reply field", MonkeyState.TALKING, 0.9f, 0.9f, "Why did the banana see a doctor? It wasn't peeling well! \uD83C\uDF4C", reply = true),
            Shot("Quip you can reply to", MonkeyState.IDLE, 2.0f, 2.0f, "I'm Settai: Tamil for naughty! Hide your bananas.", reply = true),
            Shot("Asks before acting", MonkeyState.TALKING, 1.4f, 1.4f, "Set an alarm for 7:00 AM?", buttons = listOf("Yes", "No")),
            Shot("Dance: the twist", MonkeyState.DANCE, 0.5f, 0.15f, move = 3),
            Shot("Dance: robot", MonkeyState.DANCE, 0.5f, 0.3f, move = 4),
            Shot("Dance: jump & clap", MonkeyState.DANCE, 0.5f, 0.31f, move = 5),
            Shot("Dance: spin", MonkeyState.DANCE, 0.5f, 0.4f, facing = 0.35f, move = 6),
            Shot("Dance: disco", MonkeyState.DANCE, 0.5f, 0.2f, move = 2),
        )
    )

    /** v2.1 sheet: screen edges, falling, sleeping and the wake-up routine. */
    fun renderEdges(): Bitmap = render(
        listOf(
            Shot("Hanging from the top", MonkeyState.IDLE, 0.5f, 0.5f, edge = AnimContext.TOP),
            Shot("One-handed swing + wave", MonkeyState.IDLE, 5.9f, 5.9f, edge = AnimContext.TOP),
            Shot("Hand-over-hand along the top", MonkeyState.WALK, 0.3f, 0.3f, edge = AnimContext.TOP),
            Shot("Talking while hanging", MonkeyState.TALKING, 0.9f, 0.9f, "Semma view from up here!", edge = AnimContext.TOP),
            Shot("Clinging to the left side", MonkeyState.IDLE, 1.0f, 1.0f, edge = AnimContext.WALL),
            Shot("Climbing (right side)", MonkeyState.WALK, 0.35f, 0.35f, edge = AnimContext.WALL, wallLeft = false),
            Shot("Looking back at you", MonkeyState.IDLE, 5.6f, 5.6f, edge = AnimContext.WALL),
            Shot("Dropped: falling!", MonkeyState.FALLING, 0.4f, 0.4f, "Wheeeee!"),
            Shot("Screen off: asleep (Zzz)", MonkeyState.SLEEP, 1.2f, 1.2f),
            Shot("Wake: big yawn + stretch", MonkeyState.WAKE, 0.85f, 0.85f),
            Shot("Wake: rub eyes", MonkeyState.WAKE, 2.2f, 2.2f),
            Shot("Wake: startled jump", MonkeyState.WAKE, 3.1f, 3.1f),
            Shot("Wake: wild shake / spin", MonkeyState.WAKE, 3.95f, 3.95f, facing = -1f),
            Shot("Wake: happy wave", MonkeyState.WAKE, 5.3f, 5.3f, "Whoa! I was dreaming of bananas! Good morning!"),
            Shot("Perfect landing", MonkeyState.LANDING, 0.1f, 0.1f, "Ta-da! Perfect landing!"),
        )
    )

    fun render(): Bitmap = render(
        listOf(
            Shot("Idle", MonkeyState.IDLE, 0.4f, 0.4f, "Hi! I'm Settai!"),
            Shot("Walking", MonkeyState.WALK, 0.17f, 0.17f, facing = -1f),
            Shot("Dancing", MonkeyState.DANCE, 0.55f, 0.3f, "Let's dance!"),
            Shot("Picked up (drag)", MonkeyState.DRAGGED, 1.1f, 1.1f, "Whoa whoa whoa!"),
            Shot("Neck / belly: giggle", MonkeyState.GIGGLE, 0.45f, 0.45f, "Hehe, that tickles!"),
            Shot("Head: happy pat", MonkeyState.HAPPY_PAT, 0.5f, 0.5f, "Aww, I like that"),
            Shot("Tail: startled jump", MonkeyState.STARTLED, 0.27f, 0.27f, "Whoa! My tail!"),
            Shot("Bad touch: angry", MonkeyState.ANGRY, 0.13f, 0.13f, "Hey! That's a bad touch!"),
        )
    )

    private fun render(shots: List<Shot>): Bitmap {
        val cols = 4; val cw = 520; val ch = 760
        val bmp = Bitmap.createBitmap(cols * cw, ((shots.size + cols - 1) / cols) * ch, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xFF, 0xF4, 0xE2))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x5A, 0x36, 0x18); textSize = 30f; typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xE7, 0xC4) }
        val rig = MonkeyRig(); val renderer = MonkeyRenderer(); val ctx = AnimContext()
        val pose = Pose()
        val density = 2.1f
        shots.forEachIndexed { i, s ->
            val x0 = (i % cols) * cw.toFloat(); val y0 = (i / cols) * ch.toFloat()
            c.drawRoundRect(RectF(x0 + 10, y0 + 10, x0 + cw - 10, y0 + ch - 10), 36f, 36f, card)
            ctx.anger = 0f
            ctx.danceMoves = if (s.move >= 0) intArrayOf(s.move) else intArrayOf(0, 1, 2)
            ctx.edge = s.edge
            s.gag?.let { ctx.gag = it }
            s.exit?.let { ctx.exit = it }
            Animations.fill(pose, s.state, s.t, s.st, ctx)
            val scale = 2.1f
            val world = android.graphics.Matrix()
            val bark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x7A, 0x52, 0x2E) }
            when (s.edge) {
                AnimContext.TOP -> {
                    // A branch along the top of the card.
                    c.drawRoundRect(RectF(x0 + 16, y0 + 70f, x0 + cw - 16, y0 + 96f), 13f, 13f, bark)
                    rig.layout(pose, x0 + cw / 2f, y0 + ch - 70f, scale, s.facing)
                    val ht = FloatArray(2); rig.headTop(ht)
                    val shift = (y0 + 84f) - minOf(rig.handsTopY(), ht[1] - 8f)
                    rig.layout(pose, x0 + cw / 2f, y0 + ch - 70f + shift, scale, s.facing)
                    world.setRotate(pose.hangSwing, x0 + cw / 2f, y0 + 84f)
                }
                AnimContext.WALL -> {
                    val wx = if (s.wallLeft) x0 + 150f else x0 + cw - 150f
                    c.drawRect(if (s.wallLeft) RectF(x0 + 10, y0 + 10, wx, y0 + ch - 10) else RectF(wx, y0 + 10, x0 + cw - 10, y0 + ch - 10), bark)
                    val inset = 30f * scale + 4f
                    rig.layout(pose, if (s.wallLeft) wx + inset else wx - inset, y0 + ch - 150f, scale, if (s.wallLeft) 1f else -1f)
                }
                else -> rig.layout(pose, x0 + cw / 2f, y0 + ch - 70f - (if (s.state == MonkeyState.FALLING) 150f else 0f), scale, s.facing)
            }
            c.save(); c.concat(world)
            renderer.draw(c, rig, pose, s.t + 0.3f)
            c.restore()
            if (s.say != null) {
                val b = CloudBubble(density, 15f * density)
                b.setText(s.say, (150 * density).toInt(), s.buttons, s.reply)
                b.fieldText = s.field
                val head = FloatArray(2); rig.headTop(head); world.mapPoints(head)
                val left = (x0 + cw / 2f - b.width / 2f).coerceIn(x0 + 4f, maxOf(x0 + 4f, x0 + cw - 4f - b.width))
                if (s.edge == AnimContext.TOP) {
                    // Hanging at the top: the bubble goes below, pointing at the chin.
                    val chinY = head[1] + (2 * MonkeyRig.HEAD_R + 30f) * scale
                    b.draw(c, left, y0 + ch - 60f - b.height, head[0], chinY, 1f, 1f)
                } else {
                    val top = (head[1] + 8f - b.trailSpace - b.height).coerceAtLeast(y0 + 14f)
                    b.draw(c, left, top, head[0], head[1], 1f, 1f)
                }
            }
            c.drawText(s.label, x0 + cw / 2f, y0 + ch - 24f, label)
        }
        return bmp
    }
}
