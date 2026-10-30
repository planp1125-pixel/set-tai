# Settai — Future Plan (updated for v10.8)

> Status: **M1–M4 are built** (v10.0 and v10.7–v10.8). M5–M8 are still plans. v9.5 (`versionCode 22`) stays the old fallback,
> and each v10.x source zip is a fallback for the next build. Every new item still needs the owner's go-ahead.

## 1. Vision
Make Settai the **iPhone-like standard** for living, touchable, playable cartoon companions on the phone.
Characters live on top of the whole phone as a floating overlay. Their reactions are dynamic and never exactly the same twice. They have real physics, their own personality and mood, optional local memory, and optional AI (**Give Brain**, OFF by default).
If someone copies the idea, Settai should be what they're compared against.

## 2. Techniques
- **Procedural animation**: each pose is computed per frame from physics + mood + personality instead of replaying a fixed clip.
- **Animation blending**: mix base poses (idle/walk/scared/happy) by weights.
- **Layered / additive animation**: stack small offsets (head tilt, shiver, breathing, tail swish) on the blended base.
- **Mood + personality-weighted reaction picker**: choose a reaction by weighted random, where the weights come from traits × current mood × touch speed/force × recent history.
- **Per-character physics**: mass, bounce (restitution), grip, drag, reaction speed, and spring stiffness/damping per body part.
- **Habituation & memory**: repeated stimuli get weaker reactions (boredom), annoyance builds into grudges, and kind treatment builds trust.

## 3. Roadmap

### Done
| Milestone | Build | Where |
|---|---|---|
| **M1** physics extraction with no behaviour change | v10.0 (vc 23) | `engine/PhysicsBody`, `CharacterProfile`, `GoldenTraceTest` |
| **M2** shared touch zones + tail reactions for everyone with a tail | v10.8 (vc 31) | `engine/touch/TouchInput`, `MonkeyRig.earHit/trunkHit/propHit`, `ReactionLibrary.TAIL/EARS/TRUNK` |
| **M3** blending, secondary motion and additive layers | v10.7 + v10.8 | `engine/anim/AnimEngine`, `LivingLayer`, `engine/touch/ReactionLayer` (see `docs/DISNEY_12.md`) |
| **M4** mood-aware reaction picker (traits × mood × force × habituation, no repeats) | v10.8 | `engine/touch/ReactionPicker` |
| Extras | v10.1–v10.8 | Gajju, Mask Man juggling, Screen Smash (+ v10.8 sound and reveal), Rocket Boy, procedural fireworks, Diwali accents |

### Still to do (approved order)
**M5: Charger reactions**
- Superman/Superboy blocks the shock with a shield.
- Ironman/Armour Anna opposes it or absorbs the charge in her chest.
- The cat burns out from the shock.
- The creature swallows the shock and spits the current back into the battery.
- The monkey jumps all over the display.

**M6: Cat-and-mouse interaction**
- The cat chases the mouse all over the display, jumps, hides behind screen edges and finds the mouse.
- Sometimes the cat dashes into an edge and its head flattens like a **dosa pan**.

**M7: Shared physics toys** (one physics system for all of them)
- Juggling balls that drop and roll all over the screen.
- Water surfing, water pipe, elephant water show.
- Mouse searching for cheese, and other toys later.

**M8: Local memory and relationships**
- Grudges, habits, trust, and the cat–mouse rivalry. Stored on the device.
- Give Brain stays optional and **strictly OFF unless enabled**.

**Across all stages:**
- Quality bar: 60 fps while active, low battery use, keep adaptive FPS 60/24/8/0, no jank, never block important UI.
- Brand protection: Indian trademark search for "Settai", dated design records (the versioned src zips), early launch.

## 4. Working rules (unchanged in spirit)
1. Keep the previous build's src zip + APKs as the fallback; work in a fresh `settai-vX.Y` folder.
2. Analyse what exists before adding (springs, Verlet tail, IK climber, cape, Traits, MoodController, Habituation, the reaction picker); extend, don't rebuild.
3. New behaviour goes behind a Settings switch while it's beta (today: "Smooth animation (beta)", "Living reactions (beta)", "Sound effects").
4. Visual layers must never change physics, trajectories or the frame-loop `Random` order: `GoldenTraceTest` must stay green. New random choices get their own seeded `Random`.
5. Data, not ifs: per-character numbers live in `CharacterProfile` / `AnimTuning` / `ReactionLibrary`.
6. Preserve Solo/Duo/Trio crew, every character's feel, adaptive FPS, AI-off defaults (Give Brain), edge tab, keyboard dodge, never blocking important UI.

### Ideas for next (not approved)
- M5 charger reactions using the new reaction layer (shield / absorb / burn-out / spit-back / jump around).
- Rocket Boy x Smash "finale" mode; firework shows on festival days (behind Festivals).
- Touch engine: chained reactions on rapid repeats (escalation ladders), "back" touch zone, two-finger pinch/tickle.
- Per-character reaction sounds (behind the Sound effects switch).

## 5. Standing rules for any future developer
- **Overlay app only.** Never turn it into a launcher.
- **Bump `versionCode` and `versionName` on every build**, and never reuse one. The next build is `versionCode 32` (versionName 10.9 or 11.0).
- Every release ships **4 files**: full APK, play APK, src zip, preview PNG (named like `settai-vX.Y.apk`, `settai-vX.Y-play.apk`, `settai-vX.Y-src.zip`, `settai-vX.Y-preview.png`).
- **AI features stay OFF** unless the user enables **Give Brain** (`AppSettings.aiBrainEnabled`, default `false`; gate everything through `ChatHub.isBrainEnabled`).
- **No raw photo cutouts on characters.** Generate clean 2D vector look-alikes from the 68-point mesh.
- **Keep the previous release as the fallback** (its src zip and APKs). Do new work in a fresh folder.
- Package stays `com.combo.monkeycompanion`. Keep the `full` and `play` flavors.

## 6. Engine notes (as built)
- `Pose` (~106 float slots) is the output of every layer; renderers only read it.
- Frame order in `MonkeyView`: `Character.fill` → authored overlays (landing, hover, juggle…) → `AnimEngine.step` (springs, squash) → `LivingLayer.step` (anticipation, secondary action, staging, lean, appeal) → `ReactionLayer.apply` (touch reactions) → `rig.layout` → renderer.
- Touch: `onTouchEvent` → `hitTest` + `partAt` → `TouchTracker.build` (part, kind, speed, force, hold, repeats) → `ReactionPicker.pick` → `ReactionLayer.play`; LEGACY picks / asleep / grumpy / private / busy → `MoodController.onTap` (the v10.7 path).
- Fireworks: `rig/Fireworks` (pooled, ≤ 300 sparks), drawn in the FX window (outside the character window) and inside it, like particles.
- Budget kept: no allocations in the frame loop for the new layers; 60 fps only while a reaction, anticipation or firework is running.
