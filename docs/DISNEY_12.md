# Disney's 12 principles in Settai (v10.8)

Settai draws every frame procedurally: each character fills a `Pose`, which then goes through visual layers before the
renderer draws it. **The layers never touch physics, trajectories or the frame-loop `Random`**, so `GoldenTraceTest` is
unchanged. Each new layer has its own seeded `Random`.

Switches (Settings › Rest & battery):
- **Smooth animation (beta)** (`smooth_animation`, default ON) turns on the `AnimEngine` (v10.7) and the `LivingLayer` (v10.8).
- **Living reactions (beta)** (`living_reactions`, default ON) turns on the touch engine (`engine/touch`).
- With both OFF you get the v10.6 drawing and v10.7 taps.

All numbers are data in `AnimTuning` (one object per character, `CharacterProfiles.*.anim`) and `ReactionLibrary`. The overlay has no per-character `if`s.

| # | Principle | Where in the code | Per-character data |
|---|---|---|---|
| 1 | **Squash & stretch** | `engine/anim/SquashStretch`, `AnimEngine.step` (volume-preserving `sx*sy = 1`, landing kick, motion stretch ≤ 12 %); reaction crouches use `LivingLayer.squash` | `squashMax` / `stretchMax`, e.g. Settai 0.72–1.28, Armour 0.90–1.08, Rocket Boy 0.74–1.26 |
| 2 | **Anticipation** | `LivingLayer.anticipate(CROUCH / LEAN_BACK / EYES)`. It eases into a crouch, then snaps out. It starts when a leap arc begins, a power starts (lean back), a dance starts (crouch) or a walk starts (the eyes look first). It is **visual only**: the move itself starts on time and the crouch sits on top of the move's slow start. Every reaction clip also has its own wind-up (`ReactionLayer.keys`: −0.3 before the main action) | `anticipationSec` 0.08–0.15 s (mouse 0.08, Settai 0.10, cat/Superboy 0.12, Mask 0.13, Gajju/Armour 0.15, Rocket Boy 0.09), `anticipationDepth` |
| 3 | **Staging** | `StageBus`: a character reacting to a touch announces "I have the stage". The others glance toward it (`LivingLayer` `stageDir`) and hold their idle micro-actions, so fewer things move at once. While a character is mid-reaction its own idle twitches pause too | n/a |
| 4 | **Straight-ahead vs pose-to-pose** | Both are used. *Pose-to-pose*: each character's authored poses (`Character.fill`, `Animations.kt`) are the key poses, and `AnimEngine.smoothPose` eases between them (critically damped). *Straight-ahead*: springs (`Spring`, `SpringChain`), the Verlet tail and cape, particles and `Fireworks` are simulated frame by frame. Reactions are keyed envelopes (`ReactionLayer.keys`: anticipation → overshoot → hold → wobble) laid on top of the simulation | `poseFrequency` |
| 5 | **Follow-through & overlapping action** | Springs on the tail, ears, trunk, hair, cape and scarf (v10.7). In v10.8 Rocket Boy's dangling legs swing on the tail spring (`legsDangle`) and his hair tuft and scarf have their own springs. Reactions kick the springs (`AnimEngine.kick`) so the parts keep moving after the body stops | `AppendageTuning` per part |
| 6 | **Slow in & slow out** | `Easing` (smoothstep, cubic, sine, easeOutBack); the `smoothPose` 2nd-order blend; the reaction fade in (80 ms) and out (200 ms) and 120 ms crossfades | `easeStyle`, `overshoot` |
| 7 | **Arcs** | Leaps and flights follow curved Bezier/Catmull-Rom paths (`LandingFlight`). Reaction hops and dodges use a parabola (`ReactionLayer.arc`), and the loop-de-loop is a real circle with a 360° spin. Rocket Boy's boost flies off on a bent arc (`RocketBoostPower`, `bend`). Firework sparks fall in ballistic curves with drag | n/a |
| 8 | **Secondary action** | Every few seconds when calm (`LivingLayer`): an ear twitch, a tail flick, a look-around. On top of the existing blink and breathing. The time between each is random, from the layer's own seeded RNG | `earTwitchEvery` (cat 4.5 s, mouse 3.5 s, Gajju 5 s), `tailFlickEvery` (Settai 5 s, cat 4 s, Gajju 8 s), `lookAroundEvery` (mouse 5 s, Rocket Boy 6 s, Armour 11 s). 0 = none (Superboy and chibis have no ears or tail) |
| 9 | **Timing** | A `tempo` multiplier speeds or slows each character's reactions, looks, turns and anticipation. The picker divides clip lengths by tempo (`ReactionPicker`) | `tempo`: mouse 1.3, Rocket Boy 1.25, Settai/cat 1.1, Armour 0.85, Gajju 0.8 |
| 10 | **Exaggeration** | `expressiveness` scales reaction amplitude and anticipation depth | Mask Man 1.25, Rocket Boy 1.2, Settai 1.15, Gajju 1.1, Armour 0.8 |
| 11 | **Solid drawing** | `LivingLayer`: the body leans into fast sideways motion (a spring, so it settles). On a facing flip the pupils and head lead and the body lags (a 3/4 turn hint). The head turn is clamped (±40°) so poses never break | `leanDeg`: Rocket Boy 12, Superboy 9, Gajju 6, Armour 4 |
| 12 | **Appeal** | Living eye catch-lights (`MonkeyRenderer.drawGlint`, Smooth animation only; skipped for portrait chibis and when Mask Man wears his mask). Blinks on head turns. A happy squint and blush when petted (`LivingLayer.happySquint` on LEAN_IN / NOD) | `petSquint` (Armour 0.6), `glintEye` per renderer |

## Touch engine (M2–M4), `engine/touch/`
- `TouchInput` records which part was touched (head, ear, trunk, neck, belly, tail, hand, foot, prop, private), the gesture (TAP, HOLD of ≥ 0.28 s up to the long-press menu, or FLICK), its speed, direction, force (from pressure, speed and jab), how long it was held, the repeat count, and the time since the last touch.
- `ReactionLibrary` holds verbs with parameter ranges. Every character gets the common set. Characters with a tail, ears or a trunk get those sets too, and Rocket Boy has his own. The **v10.7 per-zone reactions are kept** as `LEGACY` entries, so the classic tap still shows up now and then.
- `ReactionPicker` (own seeded RNG) weighs each option by base weight × force × mood × traits × novelty. Novelty is habituation with a 600 s half-life. It **never picks the last verb again, and avoids the last two** whenever there is another choice. Jitter: amplitude ±15 %, length ±12 %. The direction comes from the finger, never from chance.
- `ReactionLayer` is an additive player with two slots and crossfades. Interrupt rule: the same verb in its first 40 % restarts without a fade (escalation).
- The classic path still handles sleeping, grumpy (> 0.5), private-zone and busy states, and Gajju's belly spray.
