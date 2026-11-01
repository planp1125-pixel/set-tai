package com.combo.monkeycompanion.engine.show

import com.combo.monkeycompanion.brain.MonkeyState as S

/**
 * v10.21 the Billa & Chittu episodes. Each one is a complete little cartoon: a setup, a chase with a crash,
 * a plan that backfires on the cat, a turnaround with heart, and a warm ending with an iris-out.
 * Staged along the floor and one side wall so the eye can always follow; the two never melt into one blob
 * (except on purpose: a catch, a hug, a shared laddu).
 *
 * Times are absolute seconds. Track positions: x = 0..1 across the screen, y = height above the floor
 * (both in screen-width units). Lines are short Tanglish, like the rest of the app.
 */
object Episodes {
    const val HOLE_X = 0.935f
    private const val CAT = Episode.CAT
    private const val RAT = Episode.RAT

    val all: List<() -> Episode> = listOf(::ladduThief, ::bananaPeel, ::cantCatchMe)
    val titles = listOf("Laddu Thirudan", "Vazhakka Thol", "Pidikka Mudiyaadhu")

    fun byIndex(i: Int): Episode = all[Math.floorMod(i, all.size)]()

    /** Sneaky pizzicato steps. */
    private fun EpisodeBuilder.tiptoe(from: Float, to: Float, every: Float = 0.4f, base: Float = 1f) {
        var t = from; var i = 0
        val notes = floatArrayOf(1f, 1.26f, 1.12f, 1.5f)
        while (t < to) { sfx(t, "pluck", base * notes[i % notes.size], vol = 0.55f); t += every; i++ }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Episode 1: "Laddu Thirudan" (the laddu thief)
    // Billa naps by a plate of laddus. Chittu tiptoes out of his hole and steals one. Billa wakes, a frozen
    // moment, the chase, Billa SPLATs into the wall by the hole. A frying-pan trap boomerangs into his own face.
    // He cries; Chittu hesitates, then shares the laddu. They fall asleep side by side.
    // ─────────────────────────────────────────────────────────────────────────────
    fun ladduThief(): Episode {
        val b = EpisodeBuilder("laddu", "Laddu Thirudan")
        b.track("hole").place(HOLE_X).hold(60f, S.IDLE)
        b.track("plate").place(0.52f).hold(60f, S.IDLE)
        b.track("laddu1").place(0.505f, 0.03f).until(7.15f, S.IDLE).until(11.03f, S.IDLE).hide()
        b.track("laddu1").carriedBy(RAT, 7.15f, 11.03f, 0.035f, 0.085f)
        b.track("laddu2").place(0.54f, 0.03f).until(20.7f, S.IDLE).appear(0.80f, 0f).until(31.2f, S.IDLE).hide()
        b.track("laddu2").carriedBy(CAT, 19.6f, 20.7f, 0.06f, 0.11f)
        b.track("laddu2").carriedBy(RAT, 30.0f, 31.2f, 0.035f, 0.085f)
        b.track("half1").place(0f, visible = false).hide(31.2f).appear(0f).until(34.0f, S.IDLE).hide()
        b.track("half1").carriedBy(CAT, 31.2f, 34.0f, 0.06f, 0.10f)
        b.track("half2").place(0f, visible = false).hide(31.2f).appear(0f).until(34.0f, S.IDLE).hide()
        b.track("half2").carriedBy(RAT, 31.2f, 34.0f, 0.035f, 0.08f)
        // the frying pan: hidden, held up, thrown, CLANG off the wall, BONG back into Billa's face, slides off
        b.track("pan").place(0f, visible = false).hide(21.7f).appear(0.68f, 0.30f).until(24.15f, S.IDLE)
            .move(0.975f, 0.18f, S.IDLE, Ease.LINEAR, y1 = 0.12f, rot1 = 720f)
            .move(0.635f, 0.24f, S.IDLE, Ease.IN, y1 = 0.21f, rot1 = 1440f)
            .until(25.6f, S.IDLE)
            .move(0.70f, 0.35f, S.IDLE, Ease.IN, y1 = 0f, rot1 = 80f)
            .hold(30f, S.IDLE)
        b.track("pan").carriedBy(CAT, 21.7f, 24.15f, 0.06f, 0.30f, -30f)

        // ── Billa
        val cat = b.cat.place(0.28f, face = 1f)
        cat.until(7.2f, S.SLEEP, Expr(zzz = 1f))
        cat.hold(0.6f, S.SLEEP, Expr(zzz = 0f, ear = 1f))                                        // 7.8 ear twitch
        cat.hold(0.9f, S.SLEEP, Expr(eye = 0.45f, brow = 0.7f, lookX = 1f, zzz = 0f), freeze = true)   // 8.7 one eye opens
        cat.hold(0.9f, S.SLEEP, Expr(eye = 1.35f, brow = 0.2f, lookX = 1f, zzz = 0f), freeze = true)   // 9.6 wide awake
        cat.shape(0.3f, S.STARTLED, 0.9f, 1.15f, expr = Expr(eye = 1.4f, lookX = 1f))                // 9.9 springs up
        cat.shape(0.25f, S.ANGRY, 1f, 1f, ease = Ease.BACK, expr = Expr(anger = 1f, brow = 1f, shake = 0.3f, lookX = 1f))
        cat.until(10.9f, S.ANGRY, Expr(anger = 1f, brow = 1f, shake = 0.3f, lookX = 1f))
        // wind-up, then flat out into the wall: no brakes
        cat.shape(0.25f, S.DASH, 0.86f, 1.12f, -10f, x1 = 0.255f, freeze = true, pvx = 0f, pvy = 0f, expr = Expr(anger = 0.6f, brow = 1f))
        cat.move(0.905f, 0.5f, S.DASH, Ease.LINEAR, sx1 = 1.12f, sy1 = 0.92f, rot1 = 8f, expr = Expr(brow = 1f, mouth = 0.4f))  // 11.65
        cat.shape(0.06f, S.STARTLED, 0.42f, 1.22f, 0f, Ease.LINEAR, Expr.take(1.4f, mouth = 0.6f), freeze = true, pvx = 45f, pvy = 0f)   // 11.71 SPLAT
        cat.hold(0.75f, S.STARTLED, Expr.take(1.4f, mouth = 0.6f), freeze = true)
        cat.shape(0.12f, S.STARTLED, 1f, 1f, 0f, Ease.BACK, Expr.take(1.4f, mouth = 0.6f), freeze = true)                              // 12.58 springs off
        cat.shape(0.4f, S.FALLING, 1f, 1f, -90f, Ease.IN, Expr(eye = 0.6f), freeze = true, pvx = -40f, pvy = 0f)                     // 12.98 THUD
        cat.hold(1.0f, S.FALLING, Expr(eye = 0.25f, mouth = 0.3f), freeze = true)
        cat.shape(0.45f, S.STARTLED, 1f, 1f, 0f, Ease.BACK, Expr(eye = 0.5f))                                                        // 14.43 up
        cat.move(0.70f, 0.8f, S.WALK, Ease.IN_OUT, expr = Expr(eye = 0.5f, headShake = 1f, mouth = 0.3f))                             // 15.23 dizzy stagger
        cat.hold(0.4f, S.IDLE, Expr(eye = 0.6f, headShake = 1f))
        cat.hold(0.3f, S.STARTLED, Expr(eye = 1.3f, lookX = 1f), face = 1f)                                                          // 15.93 turns: the hole
        cat.hold(0.7f, S.ANGRY, Expr(anger = 1f, brow = 1f, shake = 0.3f))
        cat.hold(1.2f, S.THINKING, Expr(lookY = -1f, lookX = 0f))                                                                     // 17.83
        cat.hold(0.9f, S.IDLE, Expr.take(1.0f, smile = 1f, brow = 0.8f))                                                              // 18.73 a plan!
        cat.move(0.585f, 0.6f, S.WALK)                                                                                                // 19.33
        cat.shape(0.25f, S.IDLE, 1f, 0.88f, pvx = 0f, pvy = 0f).shape(0.2f, S.IDLE, 1f, 1f)                                        // 19.78 picks a laddu
        cat.move(0.74f, 0.7f, S.WALK, expr = Expr(smile = 1f, brow = 0.8f))                                                           // 20.48
        cat.shape(0.25f, S.IDLE, 1f, 0.88f).shape(0.2f, S.IDLE, 1f, 1f)                                                             // 20.93 bait set
        cat.move(0.62f, 0.7f, S.WALK, Ease.LINEAR, face = 1f, bounce = 0.012f, expr = Expr(smile = 1f, brow = 0.8f, lookX = 1f))     // 21.63 backs off
        cat.shape(0.3f, S.IDLE, 1.05f, 0.9f, expr = Expr(smile = 1f, brow = 0.8f, eye = 0.7f, armR = 150f))                            // 21.93 pan up
        cat.hold(1.3f, S.IDLE, Expr(eye = 0.75f, lookX = 0f, lookY = 0f, head = 0f, smile = 1f, brow = 0.8f, armR = 150f), freeze = true) // "Shh"
        cat.until(23.9f, S.IDLE, Expr(lookX = 1f, smile = 1f, brow = 0.8f, eye = 0.8f, armR = 150f, ear = 0.5f))
        cat.shape(0.25f, S.IDLE, 0.9f, 1.1f, -12f, expr = Expr(armR = 175f, brow = 1f, smile = 1f, lookX = 1f), freeze = true)        // 24.15 wind-up
        cat.shape(0.1f, S.DASH, 1.1f, 0.92f, 14f, Ease.ZIP, Expr(armR = 40f, brow = 1f, smile = 1f, lookX = 1f), freeze = true)      // 24.25 THROW
        cat.until(24.33f, S.IDLE, Expr(lookX = 1f, smile = 1f, eye = 1.1f), freeze = true)
        cat.until(24.57f, S.STARTLED, Expr.take(1.45f, smile = -0.6f, brow = -0.8f, mouth = 0.6f), freeze = true)                       // uh-oh
        cat.shape(0.05f, S.STARTLED, 1.22f, 0.8f, 0f, Ease.LINEAR, Expr(eye = 0.3f), freeze = true)                                    // 24.62 BONG, flat
        cat.until(25.95f, S.STARTLED, Expr(eye = 0.3f, shake = 0.4f), freeze = true)
        cat.shape(0.4f, S.SIT, 1f, 1f, 0f, Ease.BACK, Expr(eye = 0.4f))                                                                 // 26.35 sits down hard
        cat.until(27.0f, S.SIT, Expr(eye = 0.5f, headShake = 0.6f))
        cat.until(30.8f, S.SIT, Expr(tear = true, brow = -1f, smile = -0.9f, eye = 0.8f, lookX = 0f))                                    // crying
        cat.hold(0.6f, S.SIT, Expr(eye = 1.35f, brow = 0f, lookX = 1f, tear = true), freeze = true)                                      // 31.4 surprised
        cat.until(32.6f, S.SIT, Expr(happy = 1f, blush = 1f, smile = 1f))
        cat.until(34.0f, S.SIT, Expr(munch = true, happy = 0.6f, smile = 0.8f, blush = 0.6f))
        cat.until(45f, S.SLEEP, Expr(zzz = 1f))

        // ── Chittu
        val rat = b.rat.place(HOLE_X, face = -1f, visible = false).hide(2.8f)
        rat.pop(HOLE_X, face = -1f)                                                                    // 3.0
        rat.hold(0.5f, S.IDLE, Expr(lookX = -1f)).hold(0.5f, S.IDLE, Expr(lookX = 1f))                // looks around
        rat.hold(0.6f, S.IDLE, Expr.take(1.1f, smile = 0.9f, brow = 0.6f))                             // 4.6 "Shh"
        rat.move(0.60f, 2.4f, S.WALK, Ease.LINEAR, bounce = 0.015f, expr = Expr(brow = 0.6f, smile = 0.8f, lookX = -1f))   // 7.0
        rat.shape(0.12f, S.IDLE, 1f, 0.88f).shape(0.12f, S.IDLE, 1f, 1f)                                // 7.26 grabs a laddu
        rat.hold(0.6f, S.GIGGLE, Expr.take(1.1f, smile = 1f))                                           // 7.86 grins at you
        rat.hold(0.6f, S.STARTLED, Expr(eye = 1.3f, lookX = -1f, sweat = true), freeze = true)
        rat.until(9.0f, S.STARTLED, Expr(eye = 1.3f, lookX = -1f, sweat = true, shake = 0.25f), freeze = true)
        rat.until(10.35f, S.IDLE, Expr(smile = 0.5f, brow = -0.8f, sweat = true, lookX = -1f))
        rat.shape(0.18f, S.DASH, 0.85f, 1.15f, -10f, x1 = 0.575f, face = 1f, freeze = true)             // wind-up
        rat.move(0.92f, 0.38f, S.DASH, Ease.ZIP, sx1 = 1.1f, sy1 = 0.92f, rot1 = 8f)                    // 10.91 ZIP to the hole
        rat.dive()                                                                                       // 11.03 gone
        rat.until(15.0f, S.IDLE)
        rat.pop(HOLE_X, face = -1f, state = S.WAVE)                                                     // 15.2
        rat.hold(0.55f, S.WAVE, Expr.take(1.1f, smile = 1f))
        rat.dive(0.1f)                                                                                   // 15.85
        rat.until(23.0f, S.IDLE)
        rat.pop(HOLE_X, face = -1f)                                                                      // 23.2
        rat.hold(0.3f, S.IDLE, Expr(lookX = -1f, eye = 1.1f))
        rat.move(0.86f, 0.6f, S.WALK, Ease.LINEAR, bounce = 0.012f, expr = Expr(lookX = -1f, smile = 0.8f))   // 24.1
        rat.until(24.18f, S.IDLE, Expr(lookX = -1f, smile = 0.8f))
        rat.shape(0.06f, S.STARTLED, 1.3f, 0.45f, freeze = true, expr = Expr(eye = 1.3f))                // DUCK
        rat.until(24.4f, S.STARTLED, Expr(eye = 1.3f), freeze = true)
        rat.shape(0.2f, S.IDLE, 1f, 1f, ease = Ease.BACK, expr = Expr(eye = 1.2f, lookX = -1f))
        rat.hold(0.2f, S.IDLE, Expr(lookX = -1f, eye = 1.2f))
        rat.until(27.0f, S.GIGGLE, Expr.take(1f, smile = 1f))
        rat.hold(1.0f, S.IDLE, Expr(lookX = -1f, smile = -0.2f, brow = -0.6f))                           // 28.0 the giggle stops
        rat.move(0.81f, 0.4f, S.WALK, face = -1f, expr = Expr(lookX = -1f, brow = -0.6f))                // a step toward him
        rat.hold(0.3f, S.IDLE, Expr(lookX = -1f, brow = -0.6f))
        rat.move(0.85f, 0.35f, S.WALK, face = -1f, expr = Expr(lookX = -1f, brow = -0.6f))               // …and back
        rat.hold(0.8f, S.IDLE, Expr.take(1f, smile = 0.1f, brow = -0.6f))                                 // 29.85 "should I?"
        rat.shape(0.2f, S.IDLE, 1f, 0.85f).shape(0.15f, S.IDLE, 1f, 1f)                                  // 30.2 picks up the laddu
        rat.move(0.745f, 0.6f, S.WALK, expr = Expr(lookX = -1f, smile = 0.4f, brow = -0.3f))             // 30.8
        rat.until(32.6f, S.IDLE, Expr(lookX = -1f, smile = 0.7f, brow = -0.2f))
        rat.until(34.0f, S.SIT, Expr(munch = true, smile = 0.8f))
        rat.move(0.735f, 0.3f, S.WALK, face = -1f)
        rat.until(45f, S.SLEEP, Expr(zzz = 1f))

        // ── lines
        b.say(4.0f, Role.RAT, "Shh… 🤫", 1.3f)
        b.say(9.0f, Role.RAT, "Hehe… vanakkam? 😅", 1.0f)
        b.say(10.0f, Role.CAT, "CHITTUUU!! 😾", 1.1f)
        b.say(15.0f, Role.RAT, "Ta-ta, Billa! 👋😜", 1.1f)
        b.say(16.0f, Role.CAT, "Grrr… 😾", 1.0f)
        b.say(17.85f, Role.CAT, "Aha! Oru plan! 😼", 1.4f)
        b.say(22.0f, Role.CAT, "Shh… 🤫", 1.3f)
        b.say(25.0f, Role.RAT, "Hehehe! 😆", 1.4f)
        b.say(27.2f, Role.CAT, "Aiyo… valikkudhu… 😿", 2.0f)
        b.say(30.9f, Role.RAT, "Inda… paadhi unakku 🟠", 1.5f)
        b.say(32.45f, Role.CAT, "Th… thanks Chittu 🥹", 1.7f)

        // ── effects, sounds, buzzes
        b.fx(4.7f, Fx2.NOTES, RAT, 2.2f)
        b.tiptoe(4.7f, 7.0f)
        b.fx(7.2f, Fx2.EXCLAIM, CAT, 0.5f, big = 0.6f)
        b.fx(9.6f, Fx2.EXCLAIM, CAT, 0.8f); b.sfx(9.6f, "boing", 1.3f, buzz = 25)
        b.fx(9.7f, Fx2.EXCLAIM, RAT, 0.8f)
        b.sfx(10.53f, "zip", 1.2f); b.sfx(11.15f, "zip", 0.9f)
        b.fx(10.55f, Fx2.DUST, RAT, 0.5f); b.fx(11.15f, Fx2.DUST, CAT, 0.5f)
        b.sfx(11.0f, "pop", 1.2f, vol = 0.6f)
        b.hit(11.71f, CAT, "SPLAT!", "splat", 70, x = 0.90f, y = 0.30f)
        b.sfx(12.58f, "slide", 1f)
        b.hit(12.98f, CAT, "THUD!", "thud", 60)
        b.fx(12.98f, Fx2.DUST, CAT, 0.6f, big = 1.4f)
        b.fx(13.0f, Fx2.STARS, CAT, 2.2f)
        b.sfx(15.0f, "pop", 1.3f, vol = 0.6f); b.sfx(15.8f, "pop", 1.1f, vol = 0.6f)
        b.fx(17.1f, Fx2.BULB, CAT, 1.4f); b.sfx(17.1f, "pluck", 2f, vol = 0.8f)
        b.sfx(21.7f, "pluck", 1.8f, vol = 0.6f)
        b.sfx(23.0f, "pop", 1.3f, vol = 0.6f)
        b.tiptoe(23.5f, 24.1f, 0.2f, 1.2f)
        b.sfx(24.15f, "zip", 1.4f)
        b.hit(24.33f, "pan", "CLANG!", "clang", 40, x = 0.93f, y = 0.30f)
        b.hit(24.57f, CAT, "BONG!", "bonk", 90)
        b.fx(24.6f, Fx2.STARS, CAT, 3.4f)
        b.sfx(25.95f, "clang", 1.5f, vol = 0.5f)
        b.sfx(27.0f, "wahwah", 1f)
        b.fx(29.1f, Fx2.QUESTION, RAT, 0.8f)
        b.sfx(31.2f, "pluck", 1.5f, vol = 0.7f)
        b.fx(31.6f, Fx2.HEART, CAT, 1.4f)
        b.fx(34.4f, Fx2.HEART, null, 2.4f, x = 0.68f, y = 0.30f, big = 1.8f)
        b.sfx(34.4f, "tada", 1f)
        b.touch += 30.6f..60f
        return b.build(35.6f, 0.68f, 0.12f)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Episode 2: "Vazhakka Thol" (the banana peel)
    // Chittu munches a banana. Billa strolls in, spots a snack, sneaks up… and slips on the peel Chittu tossed
    // over his shoulder. The chase: Chittu leap-frogs over Billa, who SPLATs into the wall. Billa hurts his head;
    // Chittu brings a plaster. Friends. Chittu rides off on Billa's head.
    // ─────────────────────────────────────────────────────────────────────────────
    fun bananaPeel(): Episode {
        val b = EpisodeBuilder("banana", "Vazhakka Thol")
        b.track("banana").place(0f).until(5.6f, S.IDLE).hide()
        b.track("banana").carriedBy(RAT, 0f, 5.6f, 0.03f, 0.075f, -20f)
        // the peel: over the shoulder, onto the floor; Billa's foot sends it flying onto Chittu's head
        b.track("peel").place(0f, visible = false).hide(5.6f).appear(0.27f, 0.08f)
            .move(0.47f, 0.6f, S.IDLE, Ease.LINEAR, y1 = 0f, hop = 0.12f, rot1 = 360f)        // 6.2
            .until(8.0f, S.IDLE)
            .move(0.25f, 0.9f, S.IDLE, Ease.LINEAR, y1 = 0.13f, hop = 0.35f, rot1 = -720f)    // 8.9 lands on Chittu's head
            .until(11.2f, S.IDLE)
            .move(0.20f, 0.3f, S.IDLE, Ease.IN, y1 = 0f, rot1 = -40f)
            .hold(40f, S.IDLE)
        b.track("peel").carriedBy(RAT, 8.9f, 11.2f, 0f, 0.15f)
        b.track("plaster").place(0f, visible = false).hide(18.6f).appear(0f).hold(40f, S.IDLE)
        b.track("plaster").carriedBy(RAT, 18.6f, 20.0f, 0.03f, 0.09f)
        b.track("plaster").carriedBy(CAT, 20.0f, 60f, -0.03f, 0.345f, -30f)

        // ── Chittu: sits munching, back to the right
        val rat = b.rat.place(0.25f, face = -1f)
        rat.until(5.6f, S.SIT, Expr(munch = true, happy = 1f, smile = 1f))
        rat.hold(0.3f, S.SIT, Expr(happy = 1f, smile = 1f, armR = 150f))                    // tosses the peel
        rat.until(8.9f, S.SIT, Expr(happy = 1f, smile = 1f))                                 // humming
        rat.hold(0.4f, S.STARTLED, Expr(eye = 1.3f, lookY = -1f), freeze = true)             // something on his head
        rat.until(11.3f, S.GIGGLE, Expr.take(1f, smile = 1f))                                  // laughs at Billa anyway
        rat.hold(0.15f, S.STARTLED, Expr(eye = 1.4f, lookX = 1f, sweat = true), face = 1f, freeze = true)   // uh-oh, he's up
        // runs for the left wall, stops dead, leap-frogs over Billa
        rat.shape(0.18f, S.DASH, 0.85f, 1.15f, 10f, x1 = 0.275f, face = -1f, freeze = true)   // 11.63
        rat.move(0.10f, 0.4f, S.DASH, Ease.ZIP, sx1 = 1.1f, sy1 = 0.92f, rot1 = -8f)        // 12.03
        rat.shape(0.08f, S.STARTLED, 1.1f, 0.85f, 0f, Ease.BACK, face = 1f, pvx = 0f, pvy = 70f)   // 12.11 turn + crouch
        rat.move(0.66f, 0.7f, S.JUMP, Ease.LINEAR, hop = 0.7f, rot1 = 360f, sx1 = 1f, sy1 = 1f)    // 12.81 over his head!
        rat.shape(0.15f, S.IDLE, 1.1f, 0.88f, pvx = 0f, pvy = 0f).shape(0.15f, S.IDLE, 1f, 1f, ease = Ease.BACK)
        rat.until(15.0f, S.GIGGLE, Expr.take(1f, smile = 1f), )
        rat.hold(1.4f, S.IDLE, Expr(lookX = -1f, smile = -0.2f, brow = -0.6f), face = -1f)  // 16.4 the giggle stops
        rat.hold(0.8f, S.IDLE, Expr.take(1f, smile = 0f, brow = -0.6f))                       // 17.2 to you: "wait…"
        rat.move(1.12f, 0.55f, S.DASH, Ease.IN, face = 1f)                                     // 17.75 runs off
        rat.hide().until(18.6f, S.IDLE)
        rat.appear(1.1f, face = -1f).move(0.27f, 1.0f, S.DASH, Ease.ZIP)                       // 19.6 back with a plaster
        rat.move(0.20f, 0.3f, S.JUMP, Ease.LINEAR, hop = 0.2f)                                 // 19.9 hops up to his head
        rat.shape(0.1f, S.IDLE, 1f, 1f, expr = Expr(armL = 160f, armR = 160f))
        rat.move(0.25f, 0.3f, S.JUMP, Ease.LINEAR, hop = 0.1f, face = -1f)                     // 20.3 back down
        rat.until(22.0f, S.IDLE, Expr(lookX = -1f, smile = 0.6f))
        rat.until(23.4f, S.HAPPY_PAT, Expr(blush = 1f, happy = 1f))
        rat.until(25.0f, S.IDLE, Expr(lookX = -1f, smile = 0.9f))
        rat.move(0.115f, 0.5f, S.JUMP, Ease.LINEAR, y1 = 0.31f, hop = 0.1f)                    // 25.5 up onto Billa's head
        rat.move(0.62f, 3.0f, S.SIT, Ease.IN_OUT, y1 = 0.31f, bounce = 0.006f, face = 1f, expr = Expr(happy = 1f, smile = 1f))   // 28.5 riding
        rat.until(45f, S.WAVE, Expr.take(1f, smile = 1f))

        // ── Billa
        val cat = b.cat.place(1.18f, face = -1f)
        cat.until(2.4f, S.IDLE)
        cat.move(0.72f, 2.2f, S.WALK, Ease.IN_OUT, expr = Expr(smile = 0.6f, eye = 0.9f))      // 4.6 strolls in
        cat.hold(0.2f, S.STARTLED, Expr(eye = 1.4f, lookX = -1f), freeze = true)               // sees him
        cat.hold(1.2f, S.IDLE, Expr.take(1.4f, smile = 0.8f, mouth = 0.4f))                     // 6.0 "snack time!"
        cat.move(0.49f, 2.0f, S.WALK, Ease.LINEAR, bounce = 0.02f, expr = Expr(brow = 0.8f, smile = 0.9f, eye = 0.7f, lookX = -1f))  // 8.0
        // SLIP: feet fly up, a flip, flat on his back
        cat.shape(0.001f, S.FALLING, 1f, 1f, 0f, pvx = 0f, pvy = 60f)
        cat.move(0.50f, 0.5f, S.FALLING, Ease.LINEAR, hop = 0.2f, rot1 = 100f, face = -1f, expr = Expr(eye = 1.45f, mouth = 0.8f), freeze = true)  // 8.5
        cat.move(0.51f, 0.12f, S.FALLING, Ease.IN, y1 = -0.05f, rot1 = 90f, face = -1f, expr = Expr(eye = 1.45f, mouth = 0.8f), freeze = true)       // 8.62 THUD
        cat.shape(0.08f, S.FALLING, 1.1f, 0.8f, 90f, expr = Expr(eye = 0.3f), freeze = true)
        cat.shape(0.15f, S.FALLING, 1f, 1f, 90f, ease = Ease.BACK, expr = Expr(eye = 0.3f), freeze = true)
        cat.until(10.2f, S.FALLING, Expr(eye = 0.3f, mouth = 0.3f), freeze = true)
        cat.shape(0.5f, S.STARTLED, 1f, 1f, 0f, Ease.BACK, Expr(eye = 0.6f), y1 = 0f)            // 10.7 up
        cat.shape(0.001f, S.ANGRY, pvx = 0f, pvy = 0f)
        cat.until(11.83f, S.ANGRY, Expr(anger = 1f, brow = 1f, shake = 0.35f, lookX = -1f))
        cat.shape(0.22f, S.DASH, 0.86f, 1.12f, 10f, x1 = 0.535f, freeze = true, expr = Expr(brow = 1f, anger = 0.6f))   // 12.05 wind-up
        cat.move(0.11f, 0.65f, S.DASH, Ease.LINEAR, sx1 = 1.12f, sy1 = 0.92f, rot1 = -8f, expr = Expr(brow = 1f, mouth = 0.4f))   // 12.7
        cat.shape(0.06f, S.STARTLED, 0.42f, 1.22f, 0f, Ease.LINEAR, Expr.take(1.4f, mouth = 0.6f), freeze = true, pvx = 45f, pvy = 0f)   // SPLAT
        cat.hold(0.9f, S.STARTLED, Expr.take(1.4f, mouth = 0.6f), freeze = true)
        cat.shape(0.12f, S.STARTLED, 1f, 1f, 0f, Ease.BACK, Expr.take(1.4f, mouth = 0.6f), freeze = true)
        cat.shape(0.4f, S.FALLING, 1f, 1f, 90f, Ease.IN, Expr(eye = 0.6f), freeze = true, pvx = -40f, pvy = 0f)     // 14.18 THUD
        cat.until(15.6f, S.FALLING, Expr(eye = 0.25f, mouth = 0.3f), freeze = true)
        cat.shape(0.5f, S.SIT, 1f, 1f, 0f, Ease.BACK, Expr(eye = 0.5f))                                         // 16.1 sits up
        cat.until(20.0f, S.SIT, Expr(tear = true, brow = -1f, smile = -0.9f, eye = 0.8f, armL = 150f))           // holding his head
        cat.hold(0.6f, S.SIT, Expr(eye = 1.35f, lookX = 1f), freeze = true)                                        // 20.6 a plaster?!
        cat.until(22.0f, S.SIT, Expr(happy = 1f, blush = 1f, smile = 1f))
        cat.hold(2.6f, S.HAPPY_PAT, Expr(happy = 1f, smile = 1f), face = 1f)                                         // 24.6 pats him
        cat.shape(0.4f, S.IDLE, 1f, 1f, 0f, pvx = 0f, pvy = 0f, expr = Expr(smile = 1f))                        // 25.0 stands
        cat.until(25.5f, S.IDLE, Expr(smile = 1f, lookY = -1f))
        cat.move(0.62f, 3.0f, S.WALK, Ease.IN_OUT, expr = Expr(smile = 1f, happy = 0.5f))                       // 28.5 walk off together
        cat.until(45f, S.WAVE, Expr.take(1f, smile = 1f))

        b.say(5.0f, Role.CAT, "Aaha… snack time! 😋", 1.4f)
        b.say(9.4f, Role.RAT, "Careful-u Billa! 🍌😂", 1.6f)
        b.say(10.8f, Role.CAT, "Grrr… CHITTU!! 😾", 1.0f)
        b.say(13.0f, Role.RAT, "Wall-ku sorry sollu! 😆", 1.8f)
        b.say(16.3f, Role.CAT, "Amma… thalai… 😿", 1.8f)
        b.say(16.9f, Role.RAT, "Iru… 🏃", 0.9f)
        b.say(21.1f, Role.CAT, "Nee nalla paiyan da Chittu 🥹", 1.9f)
        b.say(23.1f, Role.RAT, "Friends? 🤝", 1.2f)
        b.say(24.1f, Role.CAT, "Friends! 💛", 1.2f)
        b.say(26.0f, Role.RAT, "Jollya poalaam! 🎉", 1.6f)

        b.fx(0.4f, Fx2.NOTES, RAT, 5.0f)
        b.fx(2.6f, Fx2.NOTES, CAT, 2.0f)
        b.fx(4.6f, Fx2.EXCLAIM, CAT, 0.7f); b.sfx(4.6f, "boing", 1.4f)
        b.tiptoe(6.0f, 8.0f, 0.5f, 0.85f)
        b.sfx(5.65f, "zip", 1.6f, vol = 0.5f); b.sfx(6.2f, "splat", 2f, vol = 0.4f)
        b.fx(8.0f, Fx2.EXCLAIM, CAT, 0.5f); b.sfx(8.0f, "slide", 1.6f)
        b.hit(8.62f, CAT, "THUD!", "thud", 70)
        b.fx(8.62f, Fx2.DUST, CAT, 0.6f, big = 1.4f)
        b.fx(8.7f, Fx2.STARS, CAT, 1.6f)
        b.sfx(8.9f, "splat", 1.6f, vol = 0.5f)
        b.sfx(11.63f, "zip", 1.3f); b.sfx(12.05f, "zip", 0.9f)
        b.fx(11.63f, Fx2.DUST, RAT, 0.5f); b.fx(12.05f, Fx2.DUST, CAT, 0.5f)
        b.sfx(12.11f, "boing", 1.6f)
        b.hit(12.76f, CAT, "SPLAT!", "splat", 70, x = 0.10f, y = 0.30f)
        b.sfx(13.7f, "slide", 1f)
        b.hit(14.18f, CAT, "THUD!", "thud", 60)
        b.fx(14.18f, Fx2.DUST, CAT, 0.6f, big = 1.4f)
        b.fx(14.2f, Fx2.STARS, CAT, 1.8f)
        b.sfx(16.1f, "wahwah", 1f)
        b.sfx(17.2f, "zip", 1.3f); b.sfx(18.6f, "zip", 1.1f)
        b.sfx(20.0f, "pluck", 1.8f, vol = 0.8f)
        b.fx(21.2f, Fx2.HEART, CAT, 1.4f)
        b.fx(22.1f, Fx2.HEARTS, RAT, 1.2f)
        b.sfx(25.2f, "boing", 1.5f, vol = 0.6f)
        b.fx(27.6f, Fx2.HEARTS, null, 2.2f, x = 0.62f, y = 0.5f, big = 1.4f)
        b.sfx(27.6f, "tada", 1f)
        b.touch += 19.55f..60f
        return b.build(28.8f, 0.62f, 0.28f)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Episode 3: "Pidikka Mudiyaadhu" (can't catch me)
    // A chase: Chittu stops dead, Billa overshoots and skids. Chittu runs up the wall and teases… and slips.
    // Billa hesitates for one heartbeat, then dives and catches him. An awkward hug, a thank-you, a blush, and the
    // chase is back on, the other way round: Chittu chasing Billa with a frying pan.
    // ─────────────────────────────────────────────────────────────────────────────
    fun cantCatchMe(): Episode {
        val b = EpisodeBuilder("catch", "Pidikka Mudiyaadhu")
        b.track("pan").place(0f, visible = false).hide(20.0f).appear(0f).hold(40f, S.IDLE)
        b.track("pan").carriedBy(RAT, 20.0f, 60f, 0.035f, 0.17f, -40f)
        val wallX = 0.985f

        // ── Chittu
        val rat = b.rat.place(-0.12f, face = 1f)
        rat.until(2.6f, S.DASH)
        rat.move(0.52f, 0.95f, S.DASH, Ease.LINEAR)                                                  // 3.55
        rat.move(0.52f, 0.5f, S.JUMP, Ease.LINEAR, hop = 0.55f)                                      // 4.05 Billa zooms under him
        rat.shape(0.12f, S.STARTLED, 1.1f, 0.85f, 0f, Ease.BACK)                                     // 4.17
        rat.hold(0.3f, S.IDLE, Expr.take(1f, smile = 1f))                                             // 4.47 cheeky look at you
        rat.until(5.6f, S.WAVE, Expr(lookX = 1f, smile = 1f))                                          // "Hi Billa!"
        rat.hold(0.3f, S.STARTLED, Expr(eye = 1.4f, lookX = 1f, sweat = true), freeze = true)         // 5.9 he charges!
        rat.shape(0.001f, S.JUMP, pvx = 0f, pvy = 70f)
        rat.move(0.93f, 0.7f, S.JUMP, Ease.LINEAR, hop = 0.5f, rot1 = 360f)                            // 6.6 over his head
        rat.shape(0.001f, S.DASH, pvx = 0f, pvy = 0f)
        rat.move(0.955f, 0.12f, S.DASH, Ease.LINEAR)                                                    // 6.72
        rat.shape(0.15f, S.WALK, 1f, 1f, -90f, Ease.IN_OUT, x1 = wallX)                                 // 6.87 onto the wall
        rat.move(wallX, 0.9f, S.WALK, Ease.IN_OUT, y1 = 0.55f, face = 1f, rot1 = -90f)                  // 7.77 up
        rat.until(9.6f, S.DANCE, Expr(happy = 1f, smile = 1f))                                          // teasing
        rat.hold(0.35f, S.STARTLED, Expr(eye = 1.45f, mouth = 0.6f, shake = 0.5f, sweat = true), freeze = true)   // 9.95 slips!
        rat.shape(0.25f, S.FALLING, 1f, 1f, 0f, Ease.IN_OUT, Expr.take(1.45f, mouth = 0.5f, brow = -1f), freeze = true, x1 = 0.93f, y1 = 0.56f, face = -1f)   // 10.2 hangs in the air
        rat.hold(0.45f, S.FALLING, Expr.take(1.45f, mouth = 0.3f, brow = -1f), freeze = true)         // 10.65 gulp
        rat.move(0.83f, 0.35f, S.FALLING, Ease.IN, y1 = 0.13f, face = -1f, expr = Expr(eye = 1.45f, mouth = 0.9f))   // 11.0 caught!
        rat.until(12.3f, S.IDLE, Expr(eye = 1.2f, lookX = -1f, lookY = -0.5f), freeze = true)         // staring at each other
        rat.until(13.4f, S.HAPPY_PAT, Expr(blush = 1f, happy = 1f, smile = 1f))                        // a thank-you kiss
        rat.move(0.86f, 0.2f, S.FALLING, Ease.IN, y1 = 0f)                                             // 13.6 dropped! plop
        rat.shape(0.1f, S.SIT, 1.15f, 0.8f).shape(0.15f, S.SIT, 1f, 1f, ease = Ease.BACK)             // 13.85
        rat.until(16.0f, S.SIT, Expr(lookX = -1f, smile = 1f, happy = 0.6f))
        rat.until(18.0f, S.IDLE, Expr(lookX = -1f, smile = 0.8f))
        rat.hold(0.4f, S.IDLE, Expr.take(1f, smile = 1f, brow = 0.6f))                                  // 18.4 a wink at you
        rat.shape(0.18f, S.DASH, 0.85f, 1.15f, -10f, x1 = 0.835f, face = 1f, freeze = true)
        rat.move(1.15f, 0.6f, S.DASH, Ease.IN, rot1 = 8f, sx1 = 1.1f, sy1 = 0.92f)                     // 19.18 off right
        rat.hide().until(20.9f, S.IDLE)
        // the turnaround: now Chittu chases Billa, with a frying pan
        rat.appear(1.15f, face = -1f).move(-0.2f, 2.3f, S.DASH, Ease.LINEAR, expr = Expr(brow = 1f, smile = 1f))   // 23.2
        rat.hide().until(23.45f, S.IDLE)
        rat.pop(0.5f, face = 1f, state = S.WAVE)                                                         // pops into the closing iris
        rat.until(45f, S.WAVE, Expr.take(1f, smile = 1f))

        // ── Billa
        val cat = b.cat.place(-0.25f, face = 1f)
        cat.until(3.0f, S.DASH)
        cat.move(0.40f, 0.7f, S.DASH, Ease.LINEAR, expr = Expr(brow = 1f, anger = 0.5f))             // 3.7
        cat.move(0.78f, 0.55f, S.DASH, Ease.ZIP, expr = Expr(brow = 1f, eye = 1.3f))                 // 4.25 right under him
        cat.shape(0.3f, S.STARTLED, 1.1f, 0.88f, -14f, Ease.ZIP, Expr(eye = 1.4f), x1 = 0.84f, freeze = true)   // 4.55 skid
        cat.shape(0.15f, S.STARTLED, 1f, 1f, 0f, Ease.BACK, Expr(eye = 1.3f))
        cat.hold(0.5f, S.IDLE, Expr.take(1.3f, smile = -0.3f), freeze = true)                        // 5.2 "where'd he go?"
        cat.hold(0.4f, S.STARTLED, Expr(eye = 1.4f, lookX = -1f), face = -1f, freeze = true)         // 5.6 there!
        cat.hold(0.28f, S.ANGRY, Expr(anger = 1f, brow = 1f))                                         // 5.88
        cat.dash(0.35f, 0.4f, Expr(brow = 1f, anger = 0.6f))                                          // 6.68 charges… he leaps over
        cat.hold(0.4f, S.IDLE, Expr.take(1.3f, smile = -0.3f), freeze = true)                        // 7.08 huh?
        cat.hold(0.3f, S.STARTLED, Expr(eye = 1.4f, lookX = 1f, lookY = -1f), face = 1f)              // 7.38 on the wall!
        cat.move(0.80f, 0.45f, S.DASH, Ease.ZIP)                                                      // 7.83
        cat.until(9.6f, S.ANGRY, Expr(anger = 1f, brow = 1f, lookY = -1f, lookX = 1f, shake = 0.2f))
        cat.hold(0.35f, S.STARTLED, Expr(eye = 1.4f, lookY = -1f, lookX = 1f), freeze = true)        // 9.95 he's slipping!
        cat.hold(0.4f, S.IDLE, Expr.take(1.2f, brow = -0.7f, smile = -0.2f), freeze = true)           // 10.35 one heartbeat: help him?
        cat.move(0.83f, 0.2f, S.DASH, Ease.ZIP, expr = Expr(eye = 1.3f, lookY = -1f, armL = 160f, armR = 160f))   // 10.55 dives under
        cat.until(11.0f, S.IDLE, Expr(eye = 1.3f, lookY = -1f, armL = 120f, armR = 120f))
        cat.shape(0.08f, S.IDLE, 1.08f, 0.88f, expr = Expr(eye = 1.3f, armL = 100f, armR = 100f))
        cat.shape(0.15f, S.IDLE, 1f, 1f, ease = Ease.BACK, expr = Expr(eye = 1.2f, armL = 100f, armR = 100f))   // caught him
        cat.until(12.3f, S.IDLE, Expr(eye = 1.2f, lookX = 0f, armL = 100f, armR = 100f), freeze = true)
        cat.until(13.4f, S.IDLE, Expr(eye = 1.45f, blush = 1f, armL = 100f, armR = 100f, shake = 0.3f), freeze = true)   // a kiss?!
        cat.move(0.66f, 0.4f, S.WALK, Ease.IN_OUT, expr = Expr(blush = 1f, eye = 1.2f))             // 13.8 drops him, walks off
        cat.until(16.0f, S.ANGRY, Expr(blush = 0.8f, brow = 0.6f, head = 15f))                        // "it's nothing!"
        cat.hold(1.8f, S.IDLE, Expr.take(1f, smile = 0.6f, brow = 0.8f), face = 1f)                   // 17.8 counting
        cat.until(19.0f, S.ANGRY, Expr(brow = 1f, anger = 0.6f, lookX = 1f))
        cat.shape(0.22f, S.DASH, 0.86f, 1.12f, -10f, x1 = 0.635f, face = 1f, freeze = true)
        cat.move(1.25f, 0.8f, S.DASH, Ease.IN, rot1 = 8f, sx1 = 1.12f, sy1 = 0.92f)                   // 20.02 off right
        cat.hide().until(20.5f, S.IDLE)
        cat.appear(1.25f, face = -1f).move(-0.3f, 2.1f, S.DASH, Ease.LINEAR, expr = Expr(eye = 1.45f, mouth = 0.8f, sweat = true, brow = -1f))   // 22.6 running FROM Chittu
        cat.hide().until(45f, S.IDLE)

        b.say(3.0f, Role.CAT, "Nillu da!! 😾", 1.1f)
        b.say(4.5f, Role.RAT, "Hi Billa! 👋", 1.0f)
        b.say(7.8f, Role.RAT, "Mela vaa poonai! 😝", 1.7f)
        b.say(8.3f, Role.CAT, "Keezha vaa da!! 😾", 1.2f)
        b.say(10.2f, Role.RAT, "Aiyo… 😨", 0.8f)
        b.say(12.4f, Role.RAT, "Thanks Billa… 💛", 1.2f)
        b.say(14.0f, Role.CAT, "Ch… chumma dhaan! 😤", 1.8f)
        b.say(16.0f, Role.CAT, "Ippo odu! Onnu… rendu… 😼", 1.8f)
        b.say(18.0f, Role.CAT, "…MOONU!! 😾", 1.0f)
        b.say(21.5f, Role.CAT, "Kaapathunga!! 😱", 1.2f)
        b.say(22.0f, Role.RAT, "Vaa da Billa! 🍳😆", 1.2f)
        b.say(23.7f, Role.RAT, "Bye bye! 👋", 1.2f)

        b.fx(3.0f, Fx2.DUST, CAT, 0.5f)
        b.sfx(3.55f, "boing", 1.6f); b.fx(3.55f, Fx2.DUST, RAT, 0.5f)
        b.sfx(4.25f, "slide", 1.8f, buzz = 20); b.fx(4.3f, Fx2.DUST, CAT, 0.6f, big = 1.3f)
        b.fx(4.8f, Fx2.QUESTION, CAT, 0.6f)
        b.fx(5.25f, Fx2.EXCLAIM, CAT, 0.5f)
        b.sfx(5.9f, "boing", 1.2f); b.sfx(6.1f, "zip", 1.0f); b.fx(6.1f, Fx2.DUST, CAT, 0.5f)
        b.fx(6.75f, Fx2.QUESTION, CAT, 0.6f)
        b.tiptoe(6.9f, 7.77f, 0.15f, 1.4f)
        b.fx(8.0f, Fx2.NOTES, RAT, 1.6f)
        b.fx(9.6f, Fx2.EXCLAIM, RAT, 0.6f); b.sfx(9.6f, "slide", 2f)
        b.sfx(10.65f, "slide", 0.8f)
        b.sfx(10.4f, "zip", 1.5f)
        b.hit(11.0f, RAT, "CATCH!", "boing", 40)
        b.fx(12.6f, Fx2.HEART, RAT, 1.2f); b.sfx(12.6f, "pluck", 2f, vol = 0.8f)
        b.fx(12.9f, Fx2.HEARTS, CAT, 1.2f)
        b.sfx(13.6f, "pop", 0.8f); b.fx(13.6f, Fx2.DUST, RAT, 0.4f)
        b.sfx(18.0f, "pluck", 1.6f)
        b.sfx(18.58f, "zip", 1.3f); b.sfx(19.22f, "zip", 1f)
        b.fx(18.58f, Fx2.DUST, RAT, 0.5f); b.fx(19.22f, Fx2.DUST, CAT, 0.5f)
        b.sfx(20.5f, "zip", 1.1f); b.sfx(20.9f, "zip", 1.3f)
        b.sfx(23.45f, "pop", 1.3f)
        b.fx(23.6f, Fx2.HEART, RAT, 1.4f)
        b.sfx(23.6f, "tada", 1f)
        b.touch += 10.8f..13.75f   // the catch
        return b.build(22.3f, 0.5f, 0.12f)
    }
}
