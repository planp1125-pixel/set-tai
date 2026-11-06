# Set-Tai libraries, characters and shows (v10.31)

This file answers three questions: **What's the plan now? What libraries do we already have? What still needs to be built** so cartoon shows and new characters are easy to make? Everything below was checked against the code at v10.31. If something is only a plan, it is marked **NOT BUILT**.

---

## 1. Plan now

**v10.31 (this release)**
- Mic: the `🎤 Listening… tap me to send` bubble shows again and stays up while you speak (live words appear in it). Tapping the character sends the words.
- New **shared Action Library** (`engine/action/ActionLibrary.kt`) with its first action, **`listen_ear`** ("listening for gossip": a hand cupped to the ear, head tilted, eyes to the side). Every character plays it from the library while the mic listens.
- Notification + always-on-display icon = the **current character**, drawn by the same renderer (Kutti Satan's silhouette, not the old monkey).
- This document.

**Later roadmap (NOT BUILT yet, in suggested order)**
1. Grow the Action Library: wave-hello, point, shrug, whisper, gasp, facepalm, bow, clap, cross-arms, sneak, etc.
2. **Expression engine**: named faces (`happy`, `shocked`, `smug`, `crying`…) that any body can show. There's a partial start already (`Expr` in `engine/show/Episode.kt`).
3. **Movement engine**: generic "walk to X / run / hop / fly to Y / enter from left / exit right", taking each body's locomotion into account.
4. **Properties (props) engine**: a shared catalog of hand-held and scene props (phone, umbrella, laddu, broom…) that any body can hold.
5. **Script player**: a plain-text scene script (from notes) → timeline → played by **any two characters**.
6. **Satan + Angel episodes** built only from 1–5 (no per-character animation code).

---

## 2. Libraries we have now (inventory)

All the drawing is procedural (no character bitmaps). **The key fact:** every character, built-in or made in the studio, is drawn from **one shared `Pose`** (≈100 float slots: arms, legs, head, face, props) through **one shared skeleton `MonkeyRig`**. Each character only has its own *renderer* (what the body looks like). So anything that writes `Pose` slots works on every body.

| Library / engine | Where | What it already does | How characters use it |
|---|---|---|---|
| **Pose** (shared parameter set) | `shared/.../rig/Pose.kt` | Arm raise/elbow, legs, head tilt, eyes/brows/mouth, blush, anger, plus prop switches (shades, phone, binoculars, magnifier, guitar, cape…) | Every renderer reads it |
| **MonkeyRig** (shared skeleton) | `shared/.../rig/MonkeyRig.kt` | Turns a Pose into bone matrices; hit-testing; two-bone arm IK (`solveArm`) | Shared by all renderers |
| **State animation library** | `shared/.../rig/Animations.kt` (`Animations.fill`) + `brain/MonkeyState.kt` | One pose per shared state: IDLE, WALK, DANCE, GIGGLE, ANGRY, THINKING, TALKING, SLEEP, gags… | `Character.fill()` calls it, then the body's `adapt()` tweaks it (e.g. float instead of walk) |
| **Action Library** *(new in v10.31, small)* | `shared/.../engine/action/ActionLibrary.kt` | Pose overlays layered on top of any state. Has **1 action so far: `listen_ear`** | Host calls `ActionLibrary.apply(id, pose, t, weight)`: same code for every body |
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
| **Character icon** *(new in v10.31)* | `app/.../overlay/CharacterIcon.kt` | Draws the current character as the notification / AOD icon | Any body |

**Honest gaps:** there's no standalone Expression engine (only `Expr` inside Episode), no Movement engine (movement lives inside `MonkeyView`, `SwipeTravel` and the show engines), no generic props catalog (props are Pose switches drawn mostly by the monkey renderer), and no text script player.

---

## 3. What still needs to be created

| To build | Why | Notes |
|---|---|---|
| More **Action Library** entries | Shows need verbs like point, shrug, whisper, clap | Same pattern as `ListenEar` (section 4) |
| **Expression engine** (`engine/expression/`) | `face("shocked")` on any body | Promote `Expr` out of Episode into a named catalog |
| **Movement engine** (`engine/movement/`) | `moveTo(actor, x, style)` working for walkers, floaters and flyers | Use `Character.locomotion` (WALK, CLIMB, HANG, FLY, FLOAT) to pick the style |
| **Props engine** (`engine/props/`) | Shared prop catalog any hand can hold | Today props are Pose flags; make them a catalog drawn at `MonkeyRig.handPoint` |
| **Script player** | Text in notes → a scene | Parse lines like `Satan: listen_ear 2s "Ooh, tell me more!"` into Episode tracks |
| **Generic two-actor Episode** | Satan + Angel (or any pair) episodes | Make `EpisodeRenderer` take any two character ids instead of Billa/Chittu |

---

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

## 6. How a future show/script would call library actions (design, NOT BUILT)

Goal: any two characters perform a scene with no per-character animation code.

```
# scene: gossip.txt
cast: A=devil, B=angel
A: enter left, walk to 0.35
B: enter right, float to 0.65
B: face smug; say "Did you hear about the cat?"
A: action listen_ear 2.5s; face curious
A: say "Ooh! Tell me more!"
B: action whisper 1.5s
A: face shocked; action jump
end: iris
```

The player would turn each line into Episode track segments:
- `enter/walk/float to` → **Movement engine** (picks walk vs float from `locomotion`).
- `face X` → **Expression engine** (named `Expr`).
- `action X` → `ActionLibrary.apply(X, pose, t, k)` with fade-in/out weight.
- `say` → speech bubble on the shared clock.
- `prop X` → **Props engine**.

Because every step only writes shared Pose slots and positions, swapping `A=devil` for `A=gajju` or a studio character needs no new code.
