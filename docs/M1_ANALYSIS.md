# M1 Analysis — Physics Extraction Plan (for approval)

> Status: **analysis + proposal only. No source has been changed.** Baseline: v9.5 (`versionCode 22`), verified
> byte-identical to `BASELINE_MANIFEST.sha256` on 2026-10-25. This document answers FUTURE_PLAN.md §4 steps 1–5
> and ends with the decisions the owner needs to make (§8).

## 1. Summary

- `MonkeyView.kt` (5,704 lines) owns all motion state: box position `mx/my`, fall velocity `vy`, `falling`,
  the tween, and the current surface `surf`. **About 41 functions write the position and about 36 replace the surface.**
  Pulling all of that out in one step can't be done safely.
- Much of the engine already exists in `shared/`: fall integrators (`Landings`), curved flights (`LandingFlight`),
  power flights (`PowerFlight`), the IK climber with a Verlet tail (`Climber`), cape physics (`Cape`), **touch zones
  including the tail** (`TouchZone`, `MonkeyRig.hitTest`), habituation, traits, and pose smoothing. M1 shouldn't rebuild any of these.
- **Recommended M1: three small steps, each with no behaviour change, behind golden-trace tests recorded on v9.5 first.**
  1. A test safety net (test files only).
  2. Move motion state into a shared `engine/PhysicsBody`. The View keeps the same field names through delegating
     properties, so the 41 writers don't change.
  3. Move the pure per-frame integrators (tween, free fall, cannonball bounce, coyote hold, edge crash) into
     `PhysicsBody`, using constants from a `PhysicsProfile` set to today's exact numbers. Side effects (haptics, lines, particles) stay in the View.

## 2. Baseline status (2026-10-25, this machine)

| Check | Result |
|---|---|
| Source vs `BASELINE_MANIFEST.sha256` | All files match; no extra files |
| `:desktop:compileKotlin`, `:app:assembleFullDebug` | BUILD SUCCESSFUL (JDK Temurin 21.0.13, platform 37.2, build-tools 37.0.0) |
| `:app:testFullDebugUnitTest` | **264 tests, 243 pass, 21 fail** |

**All 21 failures come from the environment, not the logic.** They write images and reports to hard-coded paths on the
old build box (`/workspace/monkey-companion/build/...`, `/workspace/...`), which don't exist here. They include
`RenderBenchmarkTest.benchmark`, which M1's benchmarking needs, and `HeartAndCoPresenceTest`, which reads
`/workspace/monkey-companion/app/src/play/AndroidManifest.xml`. See decision D2.

## 3. What already exists (reuse, don't rebuild)

| Need in FUTURE_PLAN | Already in v9.5 | Where | Gap |
|---|---|---|---|
| Fall physics | `Landings.fallStep / bounce / screamBrake`, gravity per style, terminal speeds | `shared/brain/Landings.kt` | Base gravity `2600 dp/s²` and the integrator loop live in `MonkeyView.stepPhysics` |
| Curved flight | `LandingFlight.plan / planBig / at / ease / bodyRot` | `shared/brain/LandingFlight.kt` | Phase machine (`stepArc`) is in the View |
| Power flight | `PowerFlight` (phases, plan, update) | `shared/brain/Superpowers.kt` | Fine as is; the View only maps fractions to pixels |
| Secondary motion | Verlet tail and 2-bone IK limbs on walls and ceilings; cape chain | `shared/rig/Climber.kt`, `rig/Cape.kt` | Front-view (floor) tail is posed, not simulated |
| Animation blending | `current.blendToward(target, 1 - e^(-14·dt))` every frame; `Character.fill = Animations.fill + adapt` | `MonkeyView.doFrame`, `shared/character/Character.kt` | One target at a time; no weighted multi-clip blend or additive layers (M3) |
| **Touch zones** | `TouchZone { HEAD, NECK, BELLY, PRIVATE, TAIL, HAND, FOOT }`; rig and climber hit tests, **tail included** | `shared/brain/MonkeyState.kt`, `rig/MonkeyRig.kt:100`, `rig/Climber.kt:333` | No touch speed or force; tail reaction is a fixed `STARTLED 1.0s` (M2) |
| Mood | `MoodController` (grumpy decay, bad-touch memory, tickle streak, flee) | `shared/brain/MoodController.kt` | Mood is one scalar (`grumpy`); no energy, trust, etc. (M4) |
| Habituation | `Habituation` (36 h half-life, novelty) | `shared/mind/Habituation.kt` | Used for apps; can be keyed by stimulus for M4 |
| Personality | `Traits` (playfulness, boldness, laziness, curiosity, chattiness, drama) | `shared/mind/Personality.kt` | Not wired into touch reactions yet |
| Cat & mouse gags | `CatMouseBehaviors.pickGag(state, last, rnd)` (already takes an injectable `Random`) | `shared/world/CatMouseBehaviors.kt` | M6 |
| Per-character physics | Scattered: `Character.fallGravity(wear)`, `heavy`, `usesHoverJets`, `usesCapeFlight`, `Locomotion` set, plus **21 `character.id == "…"` checks** in `MonkeyView` | | M1 collects these into `PhysicsProfile` and `CharacterProfile` |

## 4. Dependency map

### 4.1 Frame order (`MonkeyView.doFrame`, one Choreographer callback per window)

```
doFrame(dt ≤ 0.12 s)
 ├─ hidden tab? → static tab, 0 fps, return
 ├─ syncCharacter()
 ├─ controller.update(dt)        MoodController: state machine, decide(), stepWalk() → body.moveCenterTo()  [writes mx/my]
 ├─ stepExit()
 ├─ stepPower(dt) ─or─ stepPhysics(dt)                                                                     [writes mx/my/vy/falling/surf]
 │     stepPhysics: maskClimb → syncImeDodge → edgeCrash | tween → coyote hold → landBeat → arc → crow → free fall/land
 ├─ stepHide, stepEntrance, stepHints, bubble/beats, stepRest, stepRandomPower
 ├─ character.fill(target, state…) → current.blendToward(target) → drawPose = current + overlays (sweat, yawn, blink, props…)
 ├─ stepMoment, stepHeartAndCoPresence, stepNeighbours, stage.step (v5 games)
 ├─ pose overrides: coyote scare | edge crash | landing poses | arc pose | hover pose | mask climb
 ├─ syncFx → updateGeometry() (window size and place from mx/my + mode) → layoutRig() → climber.update()
 └─ particles / emitter → invalidate → adaptive next-frame delay (60/24/8/0)
```

### 4.2 Who owns, writes and reads each piece of state

| State | Owner | Written by | Read by |
|---|---|---|---|
| **Position** `mx, my` (box top-left, screen px) | `MonkeyView` | `stepPhysics` (tween, fall, crow, peel), `stepArc`, `stepPower`, `stepEdgeCrashLanding`, `stepMaskClimb`; `MoodController.stepWalk` via `MonkeyBody.moveCenterTo`; touch drag (`onTouchEvent`); `syncImeDodge`, `hideToEdge`, `unhide`, `relocateForWake`; widget-spot fns (`perch/peek/climb/hang/hop`); `StageHost.floorX`/`slideToEdge` (v5 games); `debugSurf` (**also used by `MonkeyOverlayService` in production** to place Duo/Trio partners) | `updateGeometry` (window placement), `layoutRig`, `MonkeyBody.centerX`, mind and AI (`bodyState`, `charX`), renderers via rig layout |
| **Velocity** `vy` (fall), `powerVx/Vy`, drag `vx/vyDrag` | `MonkeyView` | `stepPhysics`, `stepArc`, `stepEdgeCrashLanding`, fall starters (`beginFall`, `flyTo`, `rocketLanding`, `triggerMidScreenCoyoteFall`…); touch for drag velocity | Landing logic, `settleAfterDrag` (fling), power emitter, `controller.onDragMove` |
| **Surface** `surf` (`SK` FLOOR/CEIL/WALL_L/WALL_R, spot) | `MonkeyView` (private `Surf`) | ~36 fns: `goSurf`, `settleAfterDrag`, `beginFall`, landings, IME dodge, hide and entrance, spots, `refreshSurf` | `MonkeyBody.edge/min/maxCenterX`, `MoodController.gag` (refuses off-floor), `climberStep`, `roam`, `hitTest` |
| **Motion mode flags** `falling, tweenT, dragging, powerOn, hiddenTab, mcOn, arc, landStyle, edgeThrustFailurePhase, coyoteScareTimer` | `MonkeyView` | Each mode's start, step and end fns | Almost every step fn; `hovers`/`hoverOn` derived flags; `StageHost.free()`; `canShowPipe()` |
| **Behaviour state** `MonkeyState`, `stateTime`, `facing`, `AnimContext` | `MoodController` (shared) | `controller.*` calls from the View, touch, mind (`executeMind`), AI actions (`onAction`) | `character.fill` (pose), View step fns, climber `lookAtUser` |
| **Pose** `target → current → drawPose` | `MonkeyView` | `Character.fill` (Animations + adapt), then about 20 overlay writers in `doFrame` | `rig.layout`, `renderer.draw*`, `climber.update`, FX |
| **Mood** `grumpy`, bad touches, tickle streak | `MoodController` | `onTap`, `update` (decay) | `anim.anger`, reaction choice |
| **Mind / world** | `MindHub`, `WorldState` (shared, global) | Events from the View (`PET`, `POKE`, `CRASH_LANDING`, `SCREEN_OFF`…), utilities | `controller.worldSpeed`, hungry/feeding pose, `mindGate` |
| **Utilities** | `DailyUtilityEngine` | Its own timers | Draws an overlay pill and wakes the tab; **doesn't touch motion state** (good) |

### 4.3 Crew (Solo/Duo/Trio)

`MonkeyOverlayService.syncCompanionWindows` creates **one independent `MonkeyView` (own window, own physics, own
frame loop) per creature**. `partnerViews` is assigned (`MonkeyOverlayService.kt:241`) but **never read**, so crew
members don't see each other physically today. M6 (cat chases mouse) needs a shared `EngineWorld`, which this
refactor prepares for but doesn't add. The older v7 "neighbour drawn inside the lead's window" banter path still
exists separately (`stepNeighbours` / `drawNeighbours`, off when `forcedCharacterId != null`).

## 5. Findings and risks

1. **Many writers (41 position, 36 surface).** Any plan that changes call sites in bulk risks breaking a rare
   path (widget spots on the wallpaper, IME dodge, charger trip). Keep every call site textually unchanged in M1.
2. **65 calls to the global `kotlin.random.Random`** in `MonkeyView` (plus more in shared code). Moving code
   must not reorder them, or different random choices follow. Keep all `Random` calls in the View during M1.
   Golden traces should use scenarios with no random branches, or `saver = true`, which forces `SIMPLE` landings.
3. **Hard-coded character checks:** `billa` (8), `chittu` (7), `monkey` (2), `armour` (2), `mask` (1), `superboy` (1).
   M1 moves these to `CharacterProfile` flags with the same values (for example `coyoteOnMidScreenDrop = true` for billa and chittu).
4. **The desktop has its own body.** `desktop/.../Stage.kt` implements `MonkeyBody` separately. A shared
   `PhysicsBody` could serve it later. Desktop is out of scope for M1, but it must keep compiling.
5. **Probable existing glitch (not fixed in M1):** `hoverHere()` sets `my = surf.line` (the feet line,
   `y + boxH − groundPad`) and then tweens there. When the tween ends, `clampToSurf()` snaps `my` back to `surf.fix`.
   A hovering hero may dip about 110 dp for 0.45 s and then jump back up. Tests only check the final position.
   **Verify on a device.** If it's real, fix it in its own versioned build after M1, not mixed into a "no change" refactor.
6. **A production path uses debug API:** the service places partners with `debugSurf(...)`. Keep the behaviour, but
   in M1 add a non-debug alias, `placeOn(kind, frac)`, that `debugSurf` calls.
7. **Existing tests already pin a lot of motion:** `LandingE2ETest` (hover, swipe-down rocket arc, fling arc, contact sheet),
   `RealOverlayPathTest`, `RestAndScreenTest`, `CustomCompanionStudioTest`. They run real `MonkeyView`s through
   `debugFrames()` and synthetic `MotionEvent`s, so they make a good base for golden traces.

## 6. Proposed M1 (smallest safe cut)

The target is `versionCode 23`. Each step is a separate commit with all tests green before the next one starts.

### M1.0: Safety net (test code only)
- **Golden-trace tests**, recorded on untouched v9.5 and checked in as text fixtures. Each scenario logs, every frame,
  `mx, my, vy, falling, surfKind, controller.state, edgeThrustFailurePhase, edgeThrustMultiplier, coyoteScareTimer`,
  rounded to 0.01 px:
  1. SIMPLE drop from mid-screen (saver on) for monkey, superboy, mask (umbrella `fallGravity < 1`)
  2. Billa and Chittu mid-screen release → 1.0 s coyote hold → cannonball bounce → land
  3. `triggerEdgeCollisionWhileFlying()` for armour and superboy → phases 1→2→3→0
  4. Walk along floor, `WALL_L` and `CEIL` (`debugWalk`) for monkey and billa
  5. Fling-to-edge hide and unhide (tab path)
  6. Keyboard dodge open and close (`Stage.imeVisible` toggled)
- **Make test output paths configurable** (system property `settai.out`, defaulting to `build/test-out/`) so the 21
  environment failures pass on any machine and the render benchmark produces numbers (decision D2).

### M1.1: Shared motion state, no logic moved
New package `shared/src/main/kotlin/com/combo/monkeycompanion/engine/`:
```kotlin
enum class SurfaceKind { FLOOR, CEIL, WALL_L, WALL_R }              // was MonkeyView.SK
class Surface(kind, fix, a, b, line, spanA, spanB, spot)             // was MonkeyView.Surf, same code
class PhysicsBody {                                                  // plain fields, no allocation per frame
    var x = 0f; var y = 0f; var vy = 0f; var falling = false
    var tweenT = -1f; var tweenDur = 0.3f; var tweenArc = 0f
    var fromX = 0f; var fromY = 0f; var toX = 0f; var toY = 0f
    var surface: Surface = …
}
```
In `MonkeyView`, the existing names become delegating properties, for example
`private var mx: Float get() = pb.x; set(v) { pb.x = v }`. **No call site changes.** `SK`/`Surf` become type aliases
or imports. Expected diff: about 60 lines in `MonkeyView`, plus 2 new shared files.

### M1.2: Pure integrators move into `PhysicsBody`
Move only code that has no side effects. It returns an event, and the View reacts in the same order as today:
```kotlin
data class PhysicsProfile(                       // today's numbers, exactly
    val gravityDp: Float = 2600f, val umbrellaTerminalDp: Float = 420f,
    val crashGravityDp: Float = 3100f, val crashDriftDp: Float = 95f, val crashSpinDegS: Float = 320f,
    val crashSputterSinkDp: Float = 90f, val crashStartVyDp: Float = 220f, val annaDropGravityDp: Float = 4200f, …)
enum class StepEvent { NONE, TWEEN_DONE, COYOTE_END, BOUNCED, LANDED, CRASH_PLUNGE, CRASH_TOUCHDOWN, CRASH_RECOVERED }
fun PhysicsBody.stepTween(dt): StepEvent
fun PhysicsBody.stepFreeFall(dt, style, profile, fallGravity, ground, density): StepEvent   // SIMPLE + Landings.fallStep + bounce
fun PhysicsBody.stepCoyote(dt): StepEvent
fun PhysicsBody.stepEdgeCrash(dt, t, ground, screenW, boxW, facing, density, crash: EdgeCrashState): StepEvent
```
`stepPhysics` keeps its structure and calls these. Haptics, `say()`, particles, `controller.onLanded()`,
`afterLand` and `logPlayed` stay in the View at the same points. `edgeThrustMultiplier`, `coyoteScareTimer` and the
other public getters keep their names and behaviour (tests read them).

### M1.3: Character profiles
`CharacterProfile(id, physics: PhysicsProfile, walksScreenEdges, coyoteOnMidScreenDrop, edgeCrashOnFlight, climbsWithIK, …)`
uses a lookup that reproduces the 21 `character.id == …` checks exactly. All profiles start with the same
`PhysicsProfile` defaults, so behaviour is identical. The point is to have one place where M2+ can make the cat
light and bouncy and Armour heavy.

### Explicitly out of M1
The arc phase machine (`stepArc`), crow carry, peel slide, Mask Man climb, IME dodge rules, window geometry, touch
handling, climber, a multi-character `EngineWorld`, and the desktop. These stay where they are. After M1 they can
be moved one at a time with the same golden-trace method if needed.

## 7. Acceptance and benchmark for M1

- Golden traces match v9.5 **exactly** (0.01 px), and all 243 current logic tests pass, plus the 21 once D2 is done.
- `:desktop:compileKotlin` still passes. `full` and `play` release builds both assemble.
- No new allocations in `doFrame` (`PhysicsBody` and events are fields and enums; no lambdas or boxing in the step code).
- **On a device, side by side with v9.5 APKs:** same scripted gestures (drop, fling-hide, Duo cat+mouse 5 min,
  hero hover 5 min). Compare `EnergyMeter` mode and CPU ns per frame (it already exists), and the battery drain
  over 30 min idle. No regression is allowed.
- `docs/CHANGELOG.md`: a v10.0 (`versionCode 23`) entry that lists files changed and says "no behaviour change".

## 8. Decisions for the owner

| # | Decision | Recommendation |
|---|---|---|
| D1 | Approve M1 as M1.0 → M1.3 above (no visible change; preparation for M2) | Approve |
| D2 | Fix the 21 env-failing tests by making their output path configurable (**test files only**) | Yes. Without it the render benchmark can't run here |
| D3 | versionName for `versionCode 23` | `10.0` (FUTURE_PLAN suggests it) |
| D4 | `git init` this folder and commit v9.5 as the first commit, so every M1 step is a reviewable diff | Yes |
| D5 | `hoverHere()` dip (finding 5): verify on device and fix in a later, separate build | Later, separate |
| D6 | A test device for the side-by-side benchmark: which phone or emulator? | Owner to name one |
