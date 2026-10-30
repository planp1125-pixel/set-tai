# Settai Changelog

Package `com.combo.monkeycompanion`, flavors `full` and `play`. Every build gets a new `versionCode`. **Never reuse one.**
Commit hashes and times up to v9.5 come from `git log` in the old build repo (`/workspace/monkey-companion`); from v10.0 on, from this folder's own git repo. Times are IST (UTC+5:30).
Future developers: add new entries at the top, starting from versionCode 23.

## v10.11 (versionCode 34): the chat button stays faint and never covers the face (owner phone test)
Built on v10.10. GoldenTraceTest traces are unchanged. No new RNG calls anywhere (the button placement is pure geometry).

**Always faint.** v10.10 made the 💬 button solid whenever the character moved (walk, drag, flight, any small position change) or was touched, and only faded to 18% after 4 s of stillness, so on the phone it looked dark most of the time. `FloatButton` no longer has an idle timer or a "moving" input: its alpha is `IDLE_ALPHA` (0.18) at all times, and 1.0 (dark + solid) only while a finger is down on the button itself (`hold(true)` … `hold(false)` / cancel). Touching the body (`wake()`) does nothing to it now. Release opens the chat exactly as before; sliding off cancels.

**Never over the face.** The button used to sit at shoulder/head height beside the body and trailed the character with a spring, so it slid across her face during moves. Now it is placed every frame, with no lag, low beside the feet on the side away from the face (`ChatBubbleSpot`):
- each renderer reports its head box (`MonkeyRenderer.faceRect`, through the same head matrix, Rocket Girl's head shift/drop/scale and Gajju's 1.28× head with ears included), mapped through the wall/ceiling turn and edge-duck offset `drawMonkey` uses;
- preferred side: away from Duo/Trio partners when they are on screen, else away from where the head leans, else behind where the character faces; the other side and lower spots are fallbacks; a spot must keep the whole button inside the window and the screen and ≥ 2 dp clear of the head box;
- while held, the button doesn't move. Finger target radius 21 dp (a 42 dp target; the button is drawn at 30 dp).
- The old v9.5 pop-up 💬 (shown only when Settings › Chat button is OFF) is unchanged.

**Campfire in the v10.10 preview.** That was the v8 "Night campfire" (auto-lit 23:00–04:59 by the device clock when idle on the floor). The preview was rendered at ~09:10 IST, but the build box clock is UTC (03:40 = "night"), so it lit. On the phone it only appears late at night (or when asked). Previews/tests can now set `MonkeyView.debugNoNightCampfire`.

**Tests** (`v1011/V1011Test`, 11): faint on appearing, while walking and right after; during a drag, the drop and landing; after a body tap and through a rocket flight; dark only while held (and the whole time it's held), faint again on release and on cancel; `FloatButton` has no timers; ≥ 40 dp touch target; the button circle never meets the head box for all 8 characters × 5 screen positions (both edges) × idle/wave/dance/pat, walking left and right (312 checks), Rocket Girl riding and flying (153 checks), Duo/Trio (on the side away from the partners); the spot picker unit cases. `V1010Test` chat-button checks updated (faint from the first frame, faint again on release). `engine/V1011PreviewTest` renders `settai-v10.11-preview.png` (set `SETTAI_FACEBOXES=1` to outline the head boxes).

## v10.10 (versionCode 33): Rocket Girl rides astride, one double-tap = one whole power, sparkle exhaust, chat button press (owner phone test)
Built on v10.9. GoldenTraceTest traces are unchanged. No new frame-loop RNG calls (the spark fountain is a hash of time; the overlay sparks use the existing effects RNG).

**Double-tap bug (root cause).** Rocket Girl has two powers: the sky show and "Rocket boost" (zooms off the screen). The double-tap picker chose between them at random (50/50), never the same twice in a row. So the first double-tap often picked the boost and she flew UP. The second double-tap could not repeat the boost, so it was always the sky show. On top of that, a single tap had a 6% "BOOST" reaction that launched the same flight 0.3 s later, so even the first tap of a double-tap could send her up.

Fixes:
- Every character has a **signature power** (`Character.signaturePower`; Rocket Girl = the sky show, others = their first power). The **first double-tap on each character always plays it** (remembered per character in prefs), and after that the picker gives it **50%** of the picks (`PowerShortcut.SIGNATURE_WEIGHT`), still never twice in a row.
- **A tap never launches a flight.** The BOOST tap reaction is now just a whoosh + a happy rev in place. The swipe launch also needs the finger to really travel (≥ 36 dp), so a wobbly tap can't count as a swipe.
- One double-tap carries the whole sky show (bottle → light → launch → 5 bursts); nothing needs a second trigger.
- **One-time tip** the first time a character is picked (double-tap power on): Rocket Girl says "Double-tap me for fireworks! Swipe me to fly!"; others "Double-tap me for my <power>! ⚡ Tap 💬 to chat."

**Riding pose.** She now sits ASTRIDE the rocket like a broomstick: her bottom on the stick right above the tube, the gold tube hanging under the stick in front of her, the near leg over it (knee bent, foot dangling below) and the far leg tucked behind it, the torso leaning forward, both arms reaching forward-down to the tube (back hand, front hand near the cone), the dress draped over the rocket, pigtails streaming back. Draw order: far leg → far skirt → far arm → blouse → rocket → near skirt → near leg → hands → head. Hovering shows a 3/4 view (far leg and shoulder show more); thrust turns her to a side view. The ridden rocket is 1.5× (was 1.2×) at 38°; its stick is ~1.5× the tube so it fits her window. The fuse-lighting reach uses a long sparkler wire so the bottle stands past her rocket's nose.

**Exhaust (no Iron Man blast).** The rocket's flame plume is gone. Out of the tube's bottom comes a narrow fountain of tiny white/gold/orange sparks that fall with gravity and fade, a thin grey smoke wisp, and a small flickering glow at the nozzle. A swipe makes it denser and longer, still sparkles. The overlay trail uses new `Particles.EMBER` (falling spark streak) and `Particles.WISP` (thin smoke). Rocket Girl never shows the landing boot jets (`drawLandingThrust` skips them; her renderer's `drawJets` is empty). The bottle lift-off also sprays a short spark fountain from the mouth.

**Chat button.** Idle opacity 18% (was 30%). Touch-down = pressed look (fully opaque, darker orange); the chat opens on release; sliding off cancels.

**Tests** (`v1010/V1010Test`, 7): one double-tap → the whole sky show with ≥ 5 bursts and no flight; the first double-tap on Rocket Girl is always the sky show and the picker favours the signature ~50% with no repeats; monkey/Billa/Superboy also start with their signature; 80 single taps (some wobbly) never launch a flight or a power; the one-time tip shows once; the chat button is faint when idle, dark + solid while pressed and opens on release; sliding off cancels.
`engine/V1010PreviewTest` renders `settai-v10.10-preview.png` (side + 3/4 close-ups, thumbnail, 5 ride poses, the spark exhaust idle/swiped/bottle lift-off/night ride, chat button idle vs pressed from the real overlay).
All 347 unit tests pass, including GoldenTraceTest (13).

## v10.9 (versionCode 32): Rocket Girl, real rocket flight, bottle sky show, no power cooldowns, double-tap power + chat button, timers that survive interruptions (owner request)
Built on v10.8. GoldenTraceTest traces are unchanged. Every new random choice uses its own seeded `Random`, and nothing new draws from the frame-loop stream.

**Rocket Girl** (was Rocket Boy). She's id `rocketgirl`, and the old `rocketboy` id still loads (it's mapped in `Character.byId` and `AppSettings.characterId`).
- **Look:**
  - A slim, cute little girl: a smaller oval face with a soft pointed chin (`headScale` 0.9, `headDrop` 20 for a short neck), big sparkly lashed eyes, rosy cheeks and a bindi (wardrobe `bindi`, optional).
  - High pigtails with ribbon bows (wardrobe `ribbon`), a green blouse and a pink pavadai with a gold zari border. No jasmine.
  - Fully procedural (`rig/RocketGirlRenderer.kt`). Her picker thumbnail and peek face are updated.
- **The ride:** she sits astride a classic Diwali sky rocket (`rig/DiwaliRocketArt.kt`) like a broom. The rocket is tilted about 45°, her hands are on the tube, her knees are bent and her shins dangle. Hair, ribbons and skirt flutter on springs.
- **The rocket:** a gold tube about 4.5× as long as it is wide, with green and red bands, stars, a round emblem, a red foil cone, a fuse and a twisted stick.
- **The exhaust** comes out of the tube's bottom, never from her. Overlay sparks and smoke come from `RocketGeom.exhaust`, rotated with the ride.
- **Swipe = launch** (`brain/RocketFlight.kt`):
  - She flies in the swipe direction, with power ∝ swipe speed.
  - Phases: an ignite dip, then thrust plus gravity with a heading that turns gradually toward the target, with a corkscrew wobble, then a sputter, then a glide down.
  - The nose follows the velocity: the whole rider rotates, clamped to ±80° so she never flips over.
  - A smoke and spark trail follows her. She bounces softly off the screen edges.
  - A fast sideways swipe near an edge still tucks her into the edge tab.
- **Sky show** (`rig/SkyShow.kt`, the `SkyBurstPower` UI name is now "Sky show"):
  - A green glass bottle appears beside her. Her hand reaches down with a sparkler and lights the fuse (anticipation).
  - At least 5 shells launch from the bottle with a whistle and a wobbly, dotted glitter trail.
  - The shells spread across the sky at different heights. Each one is a different recipe in the same show. 8 % of shows end with a 3-shell finale.

**Fireworks** (`rig/Fireworks.kt`, `rig/FireworkRecipes.kt`):
- 58 named recipes as data. Each has a shape, 2–4 rings, a core (crackle, glitter or strobe), an outer layer (willow, crossette, comet or horsetail), an optional fixed palette, colour change and multi-break.
- New shapes: triple ring, dahlia, kamuro, horsetail, crossette, comet, crackle, strobe and smiley.
- Stars are tapered streaks that are brightest at the tip. There are twinkles and a flash.
- The same recipe, shape or palette never comes twice in a row.
- The cap is now 600 live sparks. When it's full, the oldest sparks are reused (graceful degradation).

**Powers have no cooldown, for every character.**
- The menu's greyed-out "recharging" state, the countdown and the "Recharging" line are gone.
- Using a power mid-power chains straight into the new one.
- `autoPowerGap` (25 s) now only paces *automatic* powers. Abuse is handled by the particle caps.

**Double-tap = power** (Settings › Rest & battery › "Double-tap for power", `double_tap_power`, default ON):
- A double-tap fires a random one of the character's powers, never the same one twice in a row. It uses its own seeded `Random` (`brain/PowerShortcut.kt`). A character with one power just fires it.
- The single-tap reaction plays at once, and a second tap within 250 ms blends into the power with a crouch.
- Double-tapping during a power chains the next one. Long-press is unchanged.
- OFF: double-tap opens the chat exactly as before.

**Floating 💬 chat button** (Settings › Rest & battery › "Chat button", `chat_button`, default ON):
- A 30 dp button that follows the character with spring lag and stays on screen. It flips sides near the edges and never covers the face.
- It fades to 30 % when idle and comes back when the character is touched or moves.
- It hides during powers, Smash, fullscreen apps, calls, DND, the edge tab and the entrance. There is one per character in Duo/Trio.
- It lives inside the character's own window, so it never takes touches meant for apps.
- A tap opens the chat exactly like the old double-tap. The AI only runs with Give Brain.

**Pomodoro fix and interruption audit** (`docs/INTERRUPTION_AUDIT.md`):
- **The bug:** a power (or a drag) stopped the study timer. The study / focus timer is now a wall-clock end time owned by `agent/ActivityScheduler`. It's saved in prefs, an AlarmManager alarm is set for the end time (`ActivityAlarmReceiver`), it's restored after a restart or a killed app, and a session that ended while away is celebrated once.
- The study pose comes back after any interruption. Duo/Trio no longer counts it 2–3× too fast.
- The eye break is wall-clock and saved. The Reels watch time and the delivery badge are saved.
- The sleep nag's snooze, last nag and screen-on time survive a character switch.

**Tests** (`v109/V109Test`, `engine/V109PreviewTest`):
- recipes: ≥ 50 unique, every one layered, no back-to-back repeats;
- the bottle show: ≥ 5 shells, 5 different multi-ring recipes at different heights, under the cap, and it runs from the real power;
- no cooldowns: 12 powers back to back all fire, the cap holds, and a second power works right away on other characters;
- the flight: the nose follows the velocity, the heading turns gradually, it ends in a sputter and glide, and it stays in bounds;
- the overlay swipe launch: it flies with the swipe and emits exhaust;
- double-tap: a random power with no back-to-back repeat, and slow taps aren't a double-tap;
- the chat button: it opens the chat and hides during powers and the edge tab;
- the switches: OFF/OFF gives back the old double-tap chat and no button;
- the Pomodoro survives a power, a double-tap power, chat, Smash hide/show and a character switch, with the right remaining time and the study pose back;
- killed-app restore and celebrate-once;
- Duo doesn't count the timer twice, and the eye break is wall-clock.

All 339 unit tests pass, including GoldenTraceTest (13, unchanged traces). Both release APKs build (debug key) and `:desktop:compileKotlin` passes.

`settai-v10.9-preview.png` shows:
- the face close-up and the picker thumbnail;
- the 45° ride with the tail flame;
- a swipe flight;
- the bottle-launch sequence;
- 4 multi-ring bursts;
- the chat button (from the real overlay);
- a side-by-side with the reference photos (read by the test only, never shipped).

## v10.8 (versionCode 31): Disney part 2, living touch reactions, Rocket Boy, fireworks, Smash polish (owner request)
Built on v10.7. Visual layers only: physics, trajectories, golden traces and the frame-loop `Random` order are unchanged. Every new random choice uses its own seeded `Random`.
- **Switches:** "Smooth animation (beta)" (`smooth_animation`) now also turns on the `LivingLayer`. New: "Living reactions (beta)" (`living_reactions`, default ON) and "Sound effects" (`sound_effects`, default ON). All three are in Settings › Rest & battery. *(`AppSettings.livingReactions`, `soundEffects`)*
- **Disney principles 2–12** (`engine/anim/LivingLayer.kt`, `StageBus`; numbers in `AnimTuning`):
  - Anticipation: a crouch or lean-back of 80–150 ms before leaps, powers and dances, and the eyes look first before walks.
  - Secondary action: ear twitches, tail flicks and look-arounds, plus a blink on head turns.
  - Staging: the others glance at whoever is reacting, and idle twitches pause.
  - Timing: a `tempo` value per character. Exaggeration: an `expressiveness` value per character.
  - Solid drawing: a lean into sideways motion, and the head leads a turn.
  - Appeal: eye catch-lights and a happy squint with a blush on pets.
  - Arcs: reaction hops and loops follow curves.
  - Each principle is mapped to the code in `docs/DISNEY_12.md`.
- **Touch engine M2–M4** (`engine/touch/`):
  - `TouchInput` reads ears and trunk from sub-regions of the head, and Rocket Boy's rocket via `MonkeyRig.propHit`/`earHit`/`trunkHit`.
  - Reaction verbs: FLINCH, GIGGLE, LEAN_IN, NOD, SHOW_OFF, tail yelp/swat/curl, ear flick/wiggle, trunk boop, spin-look, paw bat, dodge, hop, dizzy, plus Rocket Boy's own.
  - `ReactionPicker` never repeats the last verb and avoids the last two. Choices are weighted by mood, traits, force and habituation, with jitter on top.
  - `ReactionLayer` plays reactions as an additive layer with crossfades and interrupt rules.
  - The v10.7 reactions stay as LEGACY entries.
  - Sleeping, grumpy, private-zone and busy states still use the classic path, and so does Gajju's belly spray.
- **Rocket Boy** (`rocketboy`, new): a cheerful little boy on a red-and-gold Diwali rocket with a bamboo stick tail, drawn by `RocketBoyRenderer` (fully procedural).
  - **Idle and flight:** he hovers with a bob and a sparkly exhaust. His legs dangle on a spring, and his hair and scarf flutter. While flying, a sparkle trail follows him. He leans 12° into turns.
  - **Touch:** tap his head and he giggles. Tap the rocket and it sputters and spins. A flick gives a loop-de-loop. Hold him and he lights a phuljhadi. Rarely, he boosts off-screen and whistles back in (`RocketBoostPower`).
  - **Power:** `SkyBurstPower` fires 3 mini fireworks.
  - **Everything else:** goggles wardrobe, peek face, voice lines, traits and snacks, in the picker, in Population names, and in the AI identity (Give Brain only).
- **Procedural fireworks** (`rig/Fireworks.kt`):
  - 11 shapes: peony, chrysanthemum, ring, double ring, willow, palm, heart, star, glitter crackle, multi-stage re-burst, spiral chakra.
  - 11 palettes, with colours shifting over each spark's life.
  - Random size, spark count, speed, gravity, drag, trail and twinkle.
  - The same shape or the same palette never comes twice in a row.
  - About 5 % of launches are a grand finale of 3 staggered bursts.
  - Pooled struct-of-arrays, capped at 300 live sparks.
  - The boom pitch and crackle vary with each burst.
- **Diwali season accents:** golden twinkles and a small floating diya for every character from 7 days before to 3 days after Deepavali. Behind Settings › Festivals. *(`Festivals.diwaliSeason`, `Pose.festive`, `MonkeyRenderer.drawFestive`)*
- **Smash polish:**
  - Sound effects through `audio/Sfx` (SoundPool): crack, shatter, whoosh, MEGA boom, firework boom and crackle, whistle and fizz. The WAVs are made by `tools/gen_sfx.py` (~240 KB). Nothing plays when the phone is on silent or vibrate, the media volume is 0, or the setting is off.
  - Characters hop on each throw, and fliers (Superboy, Armour, Gajju, Rocket Boy) hover and fly up.
  - Victory arms on a shatter, and an "oops" shrug when you tap a hole that is already smashed.
  - New reveal: cream and saffron wallpaper with jagged holes, a starry night inside, and sometimes a blinking peeking monkey.
  - Rocket Boy throws mini rockets that burst into fireworks.
- **Tests:**
  - `LivingReactionsTest` (8): no immediate repeats for every character and part; not the last two; same seed gives the same reactions; legacy kept; tail, ear and trunk coverage; Rocket Boy's verbs and a rare boost; the layer clamps and ends; the LivingLayer is deterministic, crouches, and leaves the global RNG untouched.
  - `FireworksTest` (2): 20 bursts with no back-to-back shape or palette and a peak ≤ 300; launches burst; finales are rare.
  - `V108PreviewTest` renders `build/test-out/settai-v10.8-preview.png` and checks that every picker thumbnail head is inside its card.
  - `RocketBoyOverlayTest` (3): living reactions in the real overlay view for 4 characters with no repeats; a flick gives a loop or dizzy; the sky-burst launches fireworks under the cap; switching off gives the classic taps.
  - Updated: CharactersTest (8 characters) and CharacterProfileTest (Rocket Boy's jet pad).
- All 325 tests pass, including GoldenTraceTest (13, unchanged traces). Both release APKs build (signed with the debug key) and `:desktop:compileKotlin` passes.
- Versions: `versionCode 31`, `versionName 10.8`.

## v10.7 (versionCode 30): Disney principles part 1 (owner request)
Built on v10.6. A reusable animation ENGINE (`shared/.../engine/anim/`) with per-character tuning. Visual pose layer only: physics bodies, trajectories, golden traces and the `Random` call order are unchanged (the engine never calls Random).
- **One switch:** Settings › Rest & battery › "Smooth animation (beta)" (pref `smooth_animation`, default ON). OFF = exactly the v10.6 drawing path. *(`AppSettings.smoothAnimation`, `MonkeyView.smoothAnim`)*
- **Engine:** `Easing` (smoothstep, cubic, sine, easeOutBack), `Spring` + `SpringChain` (damped, sub-stepped, frame-rate independent, deterministic), `SquashStretch` (volume-preserving sx*sy = 1, clamp, velocity stretch), `AnimTuning`/`AppendageTuning` (per character), `AnimEngine` (one per overlay window).
- **Squash & stretch:** every authored squashX/squashY is turned into a volume-preserving pair, clamped per character (e.g. Settai 0.72-1.28, Armour 0.90-1.08). Landings add a spring "kick" that squashes and rebounds. In flight/drag a small stretch along the motion (<= 12%) is added and the squash pivots at the body centre (feet on the ground). *(`MonkeyRig.squashPivot`)*
- **Follow-through:** springs driven by on-screen velocity/acceleration: tail (Settai, Billa, Chittu via the shared rig tail), ears (Settai via `earWiggle`; Gajju ear flap), Gajju trunk sway, chibi long hair (extra input into its existing hair spring), the red/orange cape (Superboy, power capes) and Mask Man's tiny cape.
- **Slow in & slow out:** body/head/limb/tail pose slots blend with a critically damped second-order step (eases in and out, stable at any frame rate); face/prop slots keep the old blend. Prop envelopes (shades, goggles, notebook...) are eased; the speech-bubble and chat-button pops use easeOutBack with the character's overshoot.
- **Per-character tuning** lives in `CharacterProfiles` (`anim = AnimTuning.MONKEY` etc.): monkey bouncy, cat springy, mouse light, Superboy firm with a trailing cape, Armour heavy, Mask Man rubbery, Gajju heavy with slow ears/trunk, custom chibis with more hair swing.
- Skipped: Armour Anna and Mask Man have no tail/ears/hair parameter to drive; Gajju's wings and the climber's wall/ceiling tail (already Verlet) are unchanged; the engine is off while the IK climber is active.
- Tests: `AnimEngineTest` (spring settles + overshoots, deterministic and frame-rate independent, volume preservation, tail lag after a stop, easing ends, every character has tuning) and `AnimPreviewTest` (renders `build/test-out/settai-v10.7-preview.png`). 311 tests pass, including GoldenTraceTest (13) and RealOverlayPathTest.
- Versions: `versionCode 30`, `versionName 10.7`.

## v10.6 (versionCode 29): Screen Smash "feel" pass (owner request)
Built on Claude's v10.5 source. Only Screen Smash changed; other characters, the overlay, AI-off defaults and golden traces are unchanged.
- **Hit-stop:** the game freezes for 50 ms when a tile shatters and for 90 ms on a MEGA smash. Drawing keeps going. *(`SmashGame.hitStop`, `HIT_STOP_BREAK`, `HIT_STOP_MEGA`, `step()`)*
- **Screen shake:** only the smash layer (tiles, shards, shots and bursts) shakes, in a random direction, with a decaying sine wave. Shatter: 6 dp over 150 ms. MEGA: 14 dp over 300 ms. Crack: a light 2.5 dp tap. The HUD, character and score card stay still. It's off when Android's "Remove animations" setting is on. *(`SmashView.shake()`, `step()`, `onDraw()`)*
- **Less text clutter:** at most 2 comic bursts on screen at once, and a new burst replaces any that's too close. Bursts only appear on shatters and megas, not cracks. They pop in with an ease-out-back overshoot, then fade. The combo label only shows at milestones (×3, ×5, ×10, then every 10) and pops in, then fades. It no longer sits there the whole time. *(`impacted()`, `drawPows()`, `drawHud()`, `easeOutBack()`)*
- **Debris stays:** shards no longer vanish after 1.7 s. They fall, bounce off the walls and floor with low restitution, then settle into a floor pile that builds up per 14 dp column (capped at 44 dp). At most 120 shards rest in the pile, and the oldest fade out beyond that. The total cap stays at 420. Shards fade during the repair and clear when it finishes or on Again. *(`SmashGame` `shardRest`, `shardAlpha`, `pileH`, `floorAt()`, `removeShard()`, `clearShards()`)*
- **Wind-up:** thrown and shot attacks (stones, balls, tawa, cheese, blasts, water) crouch for 100 ms first: a squash, a dip and the arm pulled back, then release. Superboy's laser stays instant. *(`SmashView.fire()` -> `launch()`, `windUpK()`, `layoutChar()`)*
- **60 fps cap:** Smash redraws at most every ~16 ms, even on 90/120 Hz screens. *(`onDraw()`)*
- Tests: `SmashGameTest` now checks that shards stay and pile up, plus 2 new tests (hit-stop freezes the timer; the pile is capped and the repair clears it). `SmashPreviewTest` has a new "debris pile" panel and renders `build/test-out/settai-v10.6-smash.png`. All 304 tests pass, including GoldenTraceTest.
- Versions: `versionCode 29`, `versionName 10.6`.

## v10.5 (versionCode 28): Screen Smash game (owner request)
Branch `screen-smash` (from `maskman-juggle` / v10.4).
- **What it is:** your character smashes a pretend copy of your screen. The real screen and apps are never touched; at the end the copy "repairs" itself and closes over the real screen.
- **Start it:** long-press menu › More › 💥 Smash, or Home › Friendship & Mini-Games › 💥 Screen Smash! (it steps back first so it smashes the screen you were on).
- **The picture:** the floating characters hide, then one screenshot is taken. When Settai's accessibility screen agent is on (full version, Android 11+) it's taken directly; otherwise Android's screen-capture permission dialog appears every game (an Android rule), and recording stops right after the one picture. If refused, a cartoon home screen is used. The picture lives in memory only: never saved, never sent. *(`smash/SmashActivity`, `SmashCaptureService`, `SmashShot`; accessibility config `canTakeScreenshot`)*
- **Gameplay:** the screen is cut into 4 x 9 tiles. Tap or swipe: the character fires its attack (Superboy heat-vision laser, Armour Anna repulsor blasts, Mask Man juggling balls, Gajju water, Billa tawa, Chittu cheese, Settai and custom companions stones). The first hit cracks a tile (spider-web crack), the second shatters it into 8 spinning glass shards cut from the picture. Comic POW/ZAP/SPLASH bursts, haptics, combo counter; every 8 quick hits charges a MEGA SMASH that shatters a 3 x 3 block. 30 s timer; smash everything for a time bonus. Score card with best score, Again or Done, then the screen repairs itself. X or Back quits.
- New shared game logic `smash/SmashGame` (tiles, cracks, shards, projectiles, combo, timer; pure, unit-tested). Long-press menu item `SMASH` (on by default, lands in More).
- Manifest: `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `SmashActivity`, `SmashCaptureService` (`mediaProjection`). Overlay service actions `SMASH_HIDE` / `SMASH_SHOW`.
- Tests: `SmashGameTest` (9), `SmashPreviewTest` (renders `build/test-out/settai-v10.5-smash.png`), `LongPressMenuTest` updated for the new item. 302 tests pass. Not yet tried on a real phone (capture permission flow is device-only).
- Versions: `versionCode 28`, `versionName 10.5`.

## v10.4 (versionCode 27): Mask Man juggles; shaking or flinging him drops the balls (owner request)
Branch `maskman-juggle` (from `elephant` / v10.3). First piece of M7 "shared physics toys".
- **Always juggling:** whenever Mask Man is standing, walking, talking or being carried, he juggles three balls (red, yellow, blue) in a cascade that goes up above his hat. His window grows upward to make room while he juggles.
- **Drag him:** the faster you move him, the more he wobbles (arms flail, body sways, eyes go wide) and the faster balls slip out. Each dropped ball keeps the speed it really had on screen. First drop: a line like "Don't shake me, I'm performing!! 🤹". If he falls, the balls he's holding drop with him.
- **Fast fling:** every ball in his hands flies off in the swipe direction (speed capped so you can watch it), bounces off all four screen edges, rolls along the floor, rests, then fades. The frame loop keeps running for the balls even after he ducks behind the edge.
- **Picks up new balls** about 1.5 s after the lost ones settle ("Fresh balls! And… focus. 🤹").
- New shared engine `engine/JuggleBalls` (cascade positions, loose-ball physics: gravity, edge bounces, floor rolling, rest and fade; fixed arrays, no randomness). `CharacterProfile.juggles` (Mask Man only). Juggling is off with Settai's battery saver; while juggling he idles at 30 fps instead of 24.
- **Golden traces:** `drop_short_mask` and `ime_dodge_mask` were re-recorded because Mask Man's window now grows upward while he juggles. A field-by-field check confirmed that only the `win=` geometry changed (position, speed, surface and state are identical), and the other 11 traces are byte-identical.
- Tests: `JuggleBallsTest`, `MaskManJuggleTest` (real overlay: juggle -> shake drops -> refill -> fling sends all flying -> settle), `MaskManJugglePreviewTest` (phone-screen sheet `build/test-out/settai-v10.4-juggle.png`). 292 tests pass.
- Versions: `versionCode 27`, `versionName 10.4`.

## v10.3 (versionCode 26): Gajju belly poke = water spray (owner request)
Branch `elephant`.
- **Poke Gajju's belly:** it looks startled, says a line like "Don't touch that! 🐘💦", swings its trunk out and sprays water across the display for about 1 s (drops arc and fall in the full-screen effects window), then ducks behind the nearest screen edge in a huff and peeks back out about 10 s later. *(`MonkeyView.startTrunkSpray/stepTrunkSpray`, `CharacterProfile.bellySpray`, `GajjuRenderer.spray`, `Gajju.SPRAY`)*
- New `Particles.WATER` drop (blue drop with a streak and a glint, falls with gravity). Battery saver sprays fewer drops.
- Gajju no longer shows the shared red anger flush (it was a round disc on the elephant's face); grumpiness shows in the brows.
- Tests: `GajjuSprayTest` (real overlay: spray -> drops in the air -> hidden at the edge), a new preview shot. 287 tests pass.
- Versions: `versionCode 26`, `versionName 10.3`.

## v10.2 (versionCode 25): Gajju redesign, chibi baby elephant (owner feedback)
Branch `elephant`. The owner found v10.1 too bee-like and shared a reference for the cuteness wanted (not to copy).
- **Chibi proportions:** the head is drawn 1.28x bigger, with a short round tummy, fat stubby arms, and short legs with big round feet (pink pads, white toenails). Soft lavender-periwinkle skin, dark-plum outlines, a hair tuft, huge pink-lined ears.
- **Face:** huge glossy baby eyes set low (dark eye, purple glow, three sparkles, clean rim), tiny soft brows, a short chubby trunk curling up to the side, a small plum smile. No cheek blush and no trunk heart (owner's call). The pink bow is now off by default (wardrobe).
- **Bird wings:** white wings spread sideways from the upper back (curved leading edge, four separate flight feathers, scalloped secondaries), flapping fast in the air and lazily near the floor.
- **Fits the overlay window:** new `CharacterProfile.drawScale` (Gajju `0.58`, everyone else `1`) is applied in `MonkeyView.effectiveRigScale` and the home grid (`CharacterViews`), so the ears and wings aren't clipped by the 96 x 118 dp window. `GajjuPreviewTest` draws at phone proportions with the window marked.
- Versions: `versionCode 25`, `versionName 10.2`. 286 tests; the only failure seen was the known flaky `RealOverlayPathTest.swipeDown...`.

## v10.1 (versionCode 24): Gajju the flying elephant (owner request, try-out build)
Branch `elephant` (from `m1-physics` / v10.0). Not part of the M1–M8 roadmap; built on the owner's request to try a new character.
- **New character Gajju** (`shared/.../character/Gajju.kt`, `rig/GajjuRenderer.kt`): an original baby-blue elephant with pearly lavender wings (flap fast in the air, lazily near the floor), big flappy pink-lined ears, a swaying curly trunk, toe nails, a little tufted tail, soft navy outlines and a pink bow (wardrobe: bow on by default, tiny crown). Its own edge-tab peek face (`GajjuPeek`), voice lines, personality, snacks and AI identity.
- **Flies and dances:** floats everywhere like the heroes (`Locomotion.FLOAT + FLY`). Its `CharacterProfile` takes off on patrol flights across the screen more often (`airPatrolChance 0.45`, others 0.3) and breaks into mid-air dances (`airDanceChance 0.3`, others 0). Powers: Ice, Ninja clones, Time Freeze.
- `MonkeyView.hoverRoam`: patrol chance and the new mid-air dance come from the profile (unchanged for every existing character; golden traces still match).
- Registered in `CharacterRegistry.all`, `CharacterNames`, `Population.displayName`, `Personality`. Not added to the automatic Duo/Trio picker.
- Tests: `GajjuPreviewTest` (writes `build/test-out/settai-v10.1-gajju.png`), `CharactersTest` list updated. 286 tests pass.
- Versions: `versionCode 24`, `versionName 10.1`.

## v10.0 (versionCode 23) — M1: physics extraction, no behaviour change
Branch `m1-physics` in this folder's git repo (v9.5 is tag `v9.5`). Commits `8b36f1d`, `d41de4b`, `021f777`, `e494295`, `7096ad0`, `b7d8636` (2026-10-25 IST).
Release candidate: **not shipped until the side-by-side device benchmark against v9.5 passes** (FUTURE_PLAN §4.8).
- **New shared engine** (`shared/.../engine/`): `Surface`/`SurfaceKind` (moved from `MonkeyView`), `PhysicsBody` (box position, fall speed, tween, surface, coyote hold, edge-crash state and their per-frame maths), `PhysicsProfile` (v9.5's exact numbers), `CharacterProfile`/`CharacterProfiles` (replaces the movement `character.id == "…"` checks).
- **`MonkeyView.kt`:** the old field names delegate to `PhysicsBody`; tween, coyote hold, free fall, cannonball bounce and the flying-hero edge crash call the engine and react to its `StepEvent`s. Haptics, lines, particles and every `Random` call stay in the view in their original order.
- **Tests:** 13 golden motion traces recorded on v9.5 and matched bit for bit (`engine/GoldenTraceTest.kt`, `app/src/test/resources/golden/`), plus `PhysicsBodyTest` and `CharacterProfileTest`. Test outputs go to `build/test-out/` instead of `/workspace/...` (`TestPaths.kt`), so all 285 tests run on any machine.
- **Known flaky tests (also flaky on untouched v9.5, random roaming):** `RealOverlayPathTest.swipeDownMovesTheRealWindowAlongACurve` (1/6 runs) and `RestAndScreenTest.autoRestHidesAndStopsTheFrameLoop` (2/8 runs).
- Versions: `versionCode 23`, `versionName 10.0` (`app/build.gradle.kts`).

## v9.5 (versionCode 22) — the official baseline
Commits `e023328` (2026-10-23 23:56 IST) and `037292c` (2026-10-24 00:05 IST, HEAD).
- **Woman / long hair:** the back hair is drawn behind the neck and torso, so there's no grey neck or chin mask. Hair colour detection filters out glare. *(`ChibiHumanRenderer.kt`, `PhotoCompanionAnalyzer.kt`)*
- **Head clipping fixed:** the whole head, hair and hats now stay inside both the floating overlay and the character grid cards. *(`MonkeyView.kt`, `CharacterViews.kt`, `MainActivity.kt`)*
- **Custom names and roster:** custom names are saved and never reverted to "Mani". The active name updates as soon as it's saved, and there's a multi-character roster. *(`CustomCompanionStore.kt`, `CustomCompanion.kt`, `CompanionStudioActivity.kt`, `MainActivity.kt`)*
- **🧠 Give Brain:** an opt-in card on Home, Studio and Settings. AI is OFF by default (`AppSettings.aiBrainEnabled = false`, checked through `ChatHub.isBrainEnabled`). *(`AppSettings.kt`, `ChatHub.kt`, `SecureStore.kt`, `SettingsActivity.kt`)*
- **Daily utilities** (`DailyUtilityEngine.kt`, `SettaiNotificationListener.kt`, `MonkeyView.kt`, `MainActivity.kt`):
  - 1-tap floating OTP and delivery catcher
  - YouTube/Reels screen-time guard (doomscroll guard). It keeps working while tucked at the `<` edge tab: it wakes up, alerts, then tucks itself back.
  - 20-20-20 eye break
  - 5-slot clipboard backpack
  - Sticky reminders, water and medicine alerts
  - Kids study Pomodoro / 20-minute focus timer
  - Battery 85%/100% and overheat watchdog

## v9.4 (versionCode 21)
Commit `c071e8d` (2026-10-23 23:19 IST).
- Interactive 68-point mesh editor: individually draggable feature handles (32dp, magnetic), a 2.5x floating magnifier loupe, trackpad swipe mode and a precision D-pad nudge.
- Live 1:1 copying from the mesh to the character, with colour resampling.
- Files: `CompanionStudioActivity.kt`, `PhotoCompanionAnalyzer.kt`, `ChibiHumanRenderer.kt`, `CustomCompanion.kt`, `MonkeyView.kt`, `MainActivity.kt`.

## v9.3 (versionCode 20)
Commit `460ff3d` (2026-10-23 22:17 IST).
- Photos are detected as Girl / Boy / Woman / Man, with a 1-tap gender and age selector popup. Photos no longer default to a 35-year-old man.
- The 68-point mesh is locked to facial features, and there's a full-screen, edge-to-edge pinch inspector.
- Natural hairline and a compact circle beard.
- Chottu/Chittu now has a visible neck.
- Solo / Duo / Trio really spawns 1, 2 or 3 live character windows. *(`MonkeyOverlayService.syncCrew`, `Population.kt`)*
- **Cat & Mouse Duo** walks all 4 screen edges with zero gravity. There's no separate start button.
- 1-second Tom & Jerry mid-air scare (`coyoteScareTimer`) before falling.
- Superboy and Armour randomly lose thrust when they hit an edge, then crash-land (`edgeThrustMultiplier`).
- The notification summary scrolls smoothly. *(`NotifSummary.kt`)*

## v9.2 (versionCode 19)
Commit `981e157` (2026-10-23 20:00 IST).
- Iron Man-style flight thrust for Armour Anna / Ironboy: hand repulsors, chest unibeam and boot jets while flying. Her normal stance is unchanged otherwise. *(`HeroRenderers.kt`, `MonkeyRenderer.kt`, `MonkeyView.kt`)*
- Clean, modeled 2D look-alike face with no raw photo paste: 68-point face cut, open forehead, tapered curly crown, circle beard and soul patch. *(`ChibiHumanRenderer.kt`, `PhotoCompanionAnalyzer.kt`, `CustomCompanion.kt`)*
