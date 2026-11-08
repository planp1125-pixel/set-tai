# Set-Tai libraries, characters and shows (v10.32)

This file answers three questions: **What's the plan now? What libraries do we already have? What still needs to be built** so cartoon shows and new characters are easy to make? Everything below was checked against the code at v10.32. If something is only a plan, it is marked **NOT BUILT**.

---

## 1. Plan now

**v10.31 (shipped)**
- Mic: the `🎤 Listening… tap me to send` bubble shows again and stays up while you speak (live words appear in it). Tapping the character sends the words.
- New **shared Action Library** (`engine/action/ActionLibrary.kt`) with its first action, **`listen_ear`** ("listening for gossip": a hand cupped to the ear, head tilted, eyes to the side). Every character plays it from the library while the mic listens.
- Notification + always-on-display icon = the **current character**, drawn by the same renderer (Kutti Satan's silhouette, not the old monkey).
- This document.

**v10.32 (built):** the show engines are real now:
- **Action Library** grew to 7 actions: `listen_ear`, `look_at`, `raise_shield`, `cast_hands`, `throw`, `cower`, `point`.
- **Expression engine** `engine/expression/Expressions.kt`: 15 named faces (v10.33 added `scream`: wide eyes, mouth thrown open).
- **Movement engine** `engine/movement/Movement.kt`: enter from an edge, move / fly / walk, hover, vanish, appear, exit. Follows `Character.locomotion`.
- **Props engine** `engine/props/Props.kt`: fireball, shield, dosa pan, shock ball, laddu, ball. Thrown props are `Projectile`s with real positions.
- **Stage effects** `engine/stagefx/StageFx.kt`: rays from eyes or hands, a clash burst, the cracked-glass overlay, and vanish smoke in each body's `vanishStyle`.
- **Script player** `engine/script/` (`ShowScript` parser, `ScriptPlayer`, `ScriptStageRenderer`), with timed beats plus `when <prop> near <role>` rules.
- The first script is `app/src/main/assets/shows/satan_vs_angel.show`. Start it from long-press → 🎬 Show → 😈😇 Tutty vs Angel. Tap the stage to stop it.

**Still later (NOT BUILT):**
1. More actions and faces as shows need them (wave, clap, whisper, bow…).
2. **Built in v10.34:** import your own scripts by paste, file or link (see "Your own shows" below). Still later: loading straight from notes or chat, and casting by chat ("Gajju vs Billa").
3. Sounds and haptic cues inside scripts (`stage sfx …`).
4. Rules beyond "near" (e.g. `when ray hits shield`, `when <role> taps`).
5. Moving the older Billa & Chittu Drama / Episode shows onto the script player.

---

## 2. Libraries we have now (inventory)

All the drawing is procedural (no character bitmaps). **The key fact:** every character, built-in or made in the studio, is drawn from **one shared `Pose`** (≈100 float slots: arms, legs, head, face, props) through **one shared skeleton `MonkeyRig`**. Each character only has its own *renderer* (what the body looks like). So anything that writes `Pose` slots works on every body.

| Library / engine | Where | What it already does | How characters use it |
|---|---|---|---|
| **Pose** (shared parameter set) | `shared/.../rig/Pose.kt` | Arm raise/elbow, legs, head tilt, eyes/brows/mouth, blush, anger, plus prop switches (shades, phone, binoculars, magnifier, guitar, cape…) | Every renderer reads it |
| **MonkeyRig** (shared skeleton) | `shared/.../rig/MonkeyRig.kt` | Turns a Pose into bone matrices; hit-testing; two-bone arm IK (`solveArm`) | Shared by all renderers |
| **State animation library** | `shared/.../rig/Animations.kt` (`Animations.fill`) + `brain/MonkeyState.kt` | One pose per shared state: IDLE, WALK, DANCE, GIGGLE, ANGRY, THINKING, TALKING, SLEEP, gags… | `Character.fill()` calls it, then the body's `adapt()` tweaks it (e.g. float instead of walk) |
| **Action Library** | `shared/.../engine/action/ActionLibrary.kt` | Pose overlays layered on top of any state: `listen_ear`, `look_at`, `raise_shield`, `cast_hands`, `throw`, `cower`, `point` | Host calls `ActionLibrary.apply(id, pose, t, weight)`: same code for every body |
| **Body actions (AI/chat vocabulary)** | `shared/.../ai/Actions.kt` (`BodyAction`, `ActionParser`) | ~25 words the AI/user can trigger: dance, jump, wave, giggle, sleep, climb, hide, spin, gags… | `Character.mapAction()` maps or falls back per body |
| **Gags** | `brain/MonkeyState.kt` (`Gag`) + `Animations` | Sneeze, slip, binoculars, fake sleep, juggle, selfie, air guitar… | `Character.mapGag()` per body |
| **Reaction library (touch)** | `shared/.../engine/touch/Reactions.kt`, `ReactionLayer.kt`, `ReactionPicker.kt` | Verbs (flinch, giggle, lean-in, nod, show-off…) as additive pose clips on top of the base state | Same verbs on all bodies; `BodyFeatures` says if a body has tail/ears/trunk |
| **Living layer / Disney pass** | `shared/.../engine/anim/LivingLayer.kt`, `AnimEngine.kt`, `Spring.kt`, `SquashStretch.kt` | Anticipation, follow-through, eyes follow your finger, smooth slow-in/out | Applied to every body's pose |
| **Impact comedy** | `shared/.../engine/impact/ImpactPlay.kt` | Splat / thwack / pancake / dizzy for every body | Drives shared Pose + squash |
| **Swipe travel** | `shared/.../engine/swipe/SwipeTravel.kt` | Each character's own style of travelling to a swiped spot | Per-character style table |
| **Touch Feel (haptics)** | `shared/.../engine/feel/TouchFeel.kt` | Vibration patterns: heartbeat, purr, tickle, sharp wall hit… | Shared |
| **Superpowers catalog** | `shared/.../brain/Superpowers.kt` (`Superpower`) | All powers; each character lists its own + a signature power | `Character.powers`, `signaturePower` |
| **Skit engine** | `shared/.../engine/skit/Skits.kt` | Data skits: cast roles + timed beats (state + line + prop + move) for two characters | Roles can be "any body" or a fixed set |
| **Episode / dope-sheet engine** | `shared/.../engine/show/Episode.kt`, `EpisodeRenderer.kt` | Timed tracks per actor, eases, holds, takes, face overrides (`Expr`), props carried | **Only authored for Billa & Chittu** today |
| **Drama / Chase shows** | `engine/show/Drama.kt`, `ChaseShow.kt`, `Episodes.kt` | 2,400-combination Billa vs Chittu shows; Tom & Jerry chase | Hard-wired to cat + rat |
| **Clip script** | `shared/.../v5/ClipScript.kt` | 5-second shareable clip of one gag | Any current body |
| **Reaction lines (AI)** | `shared/.../ai/react/ReactionEngine.kt`, `docs/REACTION_ENGINE.md` | AI-written touch lines per character + mood | Per character id |
| **Characters (built-in)** | `shared/.../character/*.kt` + `rig/*Renderer.kt`, list in `CharacterRegistry.all` | 13 bodies: Kutti Satan, Angel, Settai, Superboy, Armour Anna, Mask Man, Billa, Chittu, Gajju, Rocket Girl, Hollow Man, Surfing Girl, Skater Girl | — |
| **Companion Studio (custom characters)** | `shared/.../character/CustomCompanion.kt` (`CustomCompanionSpec`, `CustomCharacter`, `CustomCompanionRegistry`), `app/.../ui/CompanionStudioActivity.kt`, `ChibiHumanRenderer.kt` | Make a person-like chibi character from choices (age, build, face shape, hair, outfit, relationship) | Registered at runtime; uses the shared Pose/Rig, so library actions work on it |
| **Expression engine** *(v10.32)* | `shared/.../engine/expression/Expressions.kt` | Named faces: neutral, happy, sleepy, wake, look, curious, angry, evil, determined, shocked, scream, fear, sorry, sad, smug | `Expressions.apply(name, pose, t, k)` writes the shared face slots |
| **Movement engine** *(v10.32)* | `shared/.../engine/movement/Movement.kt` (`Mover`, `Edge`) | Enter from an edge, move/fly/walk, hover with a bob, vanish/appear fades, exit | `Mover.of(character)`: fliers (FLY/FLOAT) use the air, walkers stay on the floor |
| **Props engine** *(v10.32)* | `shared/.../engine/props/Props.kt` (`Props`, `PropKind`, `Projectile`) | Hold or throw fireball, shield, dosa pan, shock ball, laddu, ball; projectiles have positions | Held props are drawn at the hand from the shared rig |
| **Stage effects** *(v10.32)* | `shared/.../engine/stagefx/StageFx.kt` | Rays (eyes/hands, any colour), clash sparks, cracked-glass overlay, vanish smoke | `Character.vanishStyle` (POOF / RED_SMOKE / WHITE_SMOKE) |
| **Script player** *(v10.32)* | `shared/.../engine/script/ShowScript.kt`, `ScriptPlayer.kt`, `ScriptStageRenderer.kt`; app `overlay/ScriptShowHub.kt`, `ScriptStageView.kt` | Plain-text scene → timeline for any cast; timed beats + proximity rules; dialogue | Any character ids in `cast:` |
| **Character icon** *(v10.31)* | `app/.../overlay/CharacterIcon.kt` | Draws the current character as the notification / AOD icon | Any body |

**Honest gaps (v10.34):** scripts can be imported by paste, file or link, but not yet read straight from notes or chat. The older Billa & Chittu Drama still uses its own Episode engine. Props are a small catalog. The only rule type is "near".

---

## 3. What still needs to be created

| To build | Status |
|---|---|
| Expression engine | **Built in v10.32** (`engine/expression/`) |
| Movement engine | **Built in v10.32** (`engine/movement/`) |
| Props engine | **Built in v10.32** (`engine/props/`). Add more kinds to `Props.catalog` |
| Script player | **Built in v10.32** (`engine/script/`) |
| Stage effects (rays, clash, crack) | **Built in v10.32** (`engine/stagefx/`) |
| Import scripts (paste / file / link) + Shows list | **Built in v10.34** |
| Scripts straight from notes / chat, casting by chat | NOT BUILT |
| Sound / haptic cues in scripts | NOT BUILT |
| More rule types (`hits`, `taps`, `after <event>`) | NOT BUILT |
| Billa & Chittu dramas moved onto the script player | NOT BUILT |

## 4. How to add a reusable library action (worked example: `listen_ear`)

1. Open `shared/src/main/kotlin/com/combo/monkeycompanion/engine/action/ActionLibrary.kt`.
2. Add an `object` extending `ActionLibrary.Action(id, title, loop)` and implement `apply(p: Pose, t: Float, k: Float)`:
   - Only **blend** Pose slots toward targets with weight `k` (`lerp(current, target, k)`), so it layers on top of whatever the body is doing and fades in/out smoothly.
   - Use only `t` (seconds) for motion. **Never call `Random`** (GoldenTrace RNG order).
   - Use shared slots (arms, head, face). Don't branch on character id.
   `listen_ear` sets right arm raise 165° / elbow −80° (hand at the ear on every body), head tilt +12°, eyes sideways, brows up, a small "o" mouth, and a slow nod.
3. Register it in `ActionLibrary.all`.
4. Play it from the host. In `MonkeyView` (pose post-pass near the `micK` code):
   ```kotlin
   if (micK > 0.02f) ActionLibrary.apply(ActionLibrary.ListenEar.id, drawPose, listenActT, micK)
   ```
   `micK` eases 0→1 while `Voice.listening` is true, so the hand rises and lowers smoothly.
5. Test it on all bodies (see `V1031Test.libraryActionIsOneSharedObjectAppliedToEveryBody`).
6. Check it visually: render a grid of characters with the action applied (Robolectric `@GraphicsMode(NATIVE)`; see `PreviewSheetTest`).

If one body truly can't do it (e.g. no arms), handle that in **that character's** `adapt()` or renderer, not by copying the action.

---

## 5. How to create / register a character

**Studio (no code):** open Companion Studio, pick choices, save. `CustomCompanionRegistry.register(spec)` creates a `CustomCharacter` drawn by `ChibiHumanRenderer`. It gets all states, reactions and library actions automatically.

**Built-in (code):**
1. `shared/.../character/MyChar.kt`: `object MyChar : Character("mychar")` with `name`, `noun`, `tagline`, `locomotion`, `powers`, `identity()`, `body()`, `newRenderer()`. Optional: `adapt()` (re-shape shared poses), `mapAction()`/`mapGag()` fallbacks, `voice` (its own joke lines, not monkey banana jokes), `peek` face.
2. `shared/.../rig/MyCharRenderer.kt`: extend `MonkeyRenderer` or `HumanRenderer` and draw using the rig matrices (`rig.head`, `rig.armRFore`…). Read Pose slots; don't invent per-character timing.
3. Add it to `CharacterRegistry.all` (and an alias in `byId` if it needs one).
4. Give it a swipe style in `SwipeTravel` and a TTS voice in `Voice.style` if wanted.
5. Tests: render it in a preview grid; it should play `listen_ear` with no extra code.

The notification / AOD icon (`CharacterIcon`) and the asleep portrait are drawn from the renderer, so new characters get them for free.

---

## 6. Show scripts (built in v10.32)

Scripts are plain text in `app/src/main/assets/shows/<name>.show`. The worked example is `satan_vs_angel.show`, the user's scene. The player is `ScriptPlayer`, and nothing in it is specific to Satan or Angel.

**Lines**
- `title: <text>`
- `cast: role=characterId, role=characterId`. The id can be any built-in id (devil, angel, monkey, gajju, billa, chittu…) or a studio id.
- `at <seconds>: <command>; <command>; …`
- `when <prop> near <role> [within <r>]: <command>; …`. This fires once, the first time a flying `<prop>` comes within `r` screen widths (default 0.08) of a visible `<role>`.
- `end at <seconds>`
- `# comment`

**Commands** are `<who> <verb> <args>`, where `<who>` is a role, `both` / `all`, or `stage`.

| Verb | Example | Engine |
|---|---|---|
| enter | `satan enter left to 0.22 0.62 in 1.3` | Movement |
| move / fly / walk | `angel fly to 0.5 0.3 in 2` | Movement (walkers stay on the floor) |
| hover | `angel hover` | Movement (fliers bob) |
| vanish / appear | `angel vanish in 0.5`, `angel appear at 0.74 0.36 in 0.6` | Movement + StageFx smoke in the body's `vanishStyle` |
| exit | `satan exit right in 1` | Movement |
| turn | `angel turn left` | Movement |
| face | `both face fear [for 2]`, `both face scream for 1` | Expression engine |
| do / stop | `satan do throw for 2.2`, `both stop` | Action Library |
| look | `angel look satan` / `look crack` / `look left` | Action Library `look_at` (also turns to face) |
| say | `both say "sorry bro" for 2` | Speech bubble |
| hold / drop | `angel hold shield`, `angel drop` | Props engine |
| throw | `satan throw fireball at angel every 0.3 x7 speed 1.5` | Props engine (projectiles) |
| ray | `satan ray eyes to angel red for 2.2`, `angel ray hands to satan yellow for 3.9` | StageFx |
| stage clash | `stage clash satan angel for 4` | Rays between the two meet at a drifting clash point |
| stage drop | `stage drop clash in 0.8` | The clash point falls to the glass |
| stage crack | `stage crack at clash` or `stage crack at 0.5 0.7` | Cracked-glass overlay (+ shake) |
| stage shake | `stage shake for 0.4` | Screen shake |

Coordinates are screen fractions: x from 0 (left) to 1 (right), y from 0 (top) to 1 (bottom). `0.86` is the floor.

**The fireball-near-Angel rule** is `when fireball near angel within 0.08: angel vanish in 0.5`. The player checks every projectile against every rule each step (prop kind matches, the target is visible, distance ≤ r), then runs that rule's commands once. To swap rays for objects, change one word: `rival throw dosa_pan at hero` + `when dosa_pan near hero: hero vanish`. The test `V1032Test.sameVerbsWorkForAnyCastAndAnyProp` does exactly that with Gajju and Billa.

**Play it:** long-press the character → 🎬 Show → 😈😇 Tutty vs Angel (`MonkeyView.startScriptShow()`). It plays on a full-screen stage window, the companion windows hide meanwhile, and a tap stops it. Double-tap is unchanged (it still fires a random power, the main one first).

**Add a new show:** copy `satan_vs_angel.show`, change `cast:` and the beats, and add a menu entry calling `startScriptShow("shows/<file>.show")`. An unknown verb, role or bad time throws `ScriptError` with the line number.

## 7. Your own shows (v10.34)

You don't need a new APK for each script. Long-press the character → 🎬 Show → 🎭 Shows opens the Shows list:
- **⭐ bundled shows** (Tutty vs Angel) and **📝 your shows**. ▶ plays, 🗑 deletes one of yours. Tap the screen during a show to stop it.
- **Add a show**, three ways:
  1. **Paste** the whole script, then *Save & play* or *Save*.
  2. **Pick a file**: a `.show` or `.txt` file through Android's file picker (no storage permission).
  3. **Fetch link**: any `https://` text link. A GitHub `…/blob/…` link is turned into its raw link automatically. A private repo's link won't work without a login, so use a public file or a paste.
- A script is checked before it's saved. If something's wrong, you see e.g. `Script problem on line 3: unknown verb 'dance-wildly'` and nothing is saved.
- Saved in app-private storage: `files/user-shows/<title-slug>.show`. The same title overwrites the old one. Uninstalling the app deletes them.

Code: `overlay/UserShows.kt` (store, list, check, fetch), `ui/ShowsActivity.kt` (the list + add screen; it's an Activity because the Android file picker needs one), and `ScriptShowHub.startResult(ctx, asset | text)`, which returns null when the show is playing or a message with the line number.

Small example:
```
title: Wave hello
cast: hero=monkey, pal=gajju
at 0: hero enter left to 0.3 0.86 in 1.5
at 0.5: pal enter right to 0.7 0.5 in 1.5
at 2: hero face happy; hero do point for 1.5; hero say "Hello friend!" for 2
at 3: pal look hero; pal say "Hi!" for 1.5
at 5: both vanish
end at 6
```
