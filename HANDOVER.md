# Set-Tai: handover notes (for the next developer / AI)

Updated for **v10.34 (versionCode 72)**, 6 Nov 2026. Owner: Manikandan Gopalakrishnan (COMBO MEALS). He tests on a Motorola Edge 70 Fusion.

## v10.34 current handoff

- Baseline v10.33 `881c1b1`. Menu key `story` (g_show, label "🎭 Shows") now opens `ui/ShowsActivity` (falls back to playing the bundled default).
- `overlay/UserShows.kt`: store in `filesDir/user-shows/<slug>.show`, `validate` = parse + cast ids exist, `fetch` (https only, GitHub blob→raw, 200 KB cap, user-initiated). `ScriptShowHub.startResult(ctx, asset=…|text=…)` returns null or a line-numbered message; `start`/`startText` wrap it. The stage window uses the application context.
- Tests: `v1034/V1034Test`. No changes to engines, double-tap, swipe travel or Touch Feel.

## v10.33 handoff

- Baseline v10.32 `681401f`. `Expressions` adds `scream` (eye 1.8, mouth 1.2, brow -14, browY -6, smile -0.3, sweat 0.5, shake 3).
- `DevilRenderer` (Satan + Angel) used to clamp the drawn eye height at 1.15 and the mouth at 1.0, so fear, shocked and scream all drew the same eyes. Now the eyes grow only above 1.5 (up to 1.8) and the mouth only above 1.0 (up to 1.2). Values ≤ shocked render exactly as before. The monkey renderer already allows mouth 1.2. The chibi renderer caps the mouth at 1.0.
- `satan_vs_angel.show`: `at 16.4: both face scream for 1`; later beats +1 s (end 22.6). V1032 timings updated; new `v1033/V1033Test`.

## v10.32 handoff

- Baseline **v10.31 (69)** `ca8680c`. The full engine guide and script format are in [docs/LIBRARIES-AND-SHOWS.md](docs/LIBRARIES-AND-SHOWS.md).
- New shared engines: `engine/expression/Expressions.kt`, `engine/movement/Movement.kt`, `engine/props/Props.kt`, `engine/stagefx/StageFx.kt`, `engine/script/{ShowScript,ScriptPlayer,ScriptStageRenderer}.kt`. The Action Library has 7 actions. `Character.vanishStyle` (Devil RED_SMOKE, Angel WHITE_SMOKE, default POOF).
- App: `overlay/ScriptShowHub.kt` + `ScriptStageView.kt` (full-screen stage, same window flags as ShowStageView, tap = stop). `MonkeyView.startScriptShow()`; menu item `story` in `g_show` (V1013Test updated). `MonkeyView.onDraw` skips while a script show runs.
- Scripts: `app/src/main/assets/shows/satan_vs_angel.show`. No frame-loop RNG anywhere (the crack shape uses its own seeded java.util.Random). Engines contain no character-id strings (a test greps for them).
- Tests: `v1032/V1032Test` (parse, errors, hover/enter, fireball-near vanish + whole scene, Gajju/Billa + dosa pan, walker vs flier, shared pose, render sheet `app/build/test-out/v1032-satan-vs-angel.png`).

## v10.31 handoff

- Baseline **v10.30 (68)** `242f27b`. Everything from the v10.30 notes below still applies.
- **Libraries / shows / characters guide: [docs/LIBRARIES-AND-SHOWS.md](docs/LIBRARIES-AND-SHOWS.md)**. Read it before adding actions, characters or show scripts.
- **Mic:** `MonkeyView.speak()` drops lines while `listenTap || Voice.listening` (the Listening bubble can't be covered). `Voice` maps `ERROR_CLIENT` → `NO_MATCH` (it was `CANCELLED` = a silent close). It delivers once per session, has a tap-to-send fallback (`SEND_TIMEOUT_MS` 2.5 s, sends the last partial), and a `testEngine` seam for JVM tests. `Voice.listenHook` lets `MonkeyOverlayService` switch its FGS type to specialUse|microphone while listening (manifest + `FOREGROUND_SERVICE_MICROPHONE`, wrapped in runCatching). The tap-to-send gesture is swallowed (`listenSendSwallow`). The partial bubble keeps "Listening… tap me to send".
- **Action Library:** `shared/.../engine/action/ActionLibrary.kt`, `ListenEar` (`listen_ear`) applied in the pose post-pass by `micK`. No RNG.
- **Icon:** `overlay/CharacterIcon.kt` renders the current character (cached per id) as `IconCompat` small icon for every notification builder (vector `ic_stat_monkey` is only a fallback). `CharacterRegistry.onSelect` → service re-posts its notification. `sleepyPortrait` is per character now.
- Tests: `v1031/V1031Test` (listen prompt, partials, tap-to-send, fallback, chatter guard, ear pose on 4 bodies, library reuse, icon follows character).

## v10.30 handoff (supersedes older behaviour notes below)

- Baseline is **v10.29 (67)** at `1ff85ff` / tag `v10.29`. Do not revert Cloud default, dual FABs,
  Explain-more chip, worker 30–40 / detail 560, GatewayBrain 560, flickThrow=false, unique swipe
  travel, hide-on-edge, sulking, GoldenTrace RNG order, or power cooldowns.
- **Sharper wall hit:** `impactHit` and marble bounces call `Feel.playSharpHit` (logs `wall_hit`).
  Cancels in-flight vibration first so sling `sling_snap` cannot smear over contact. Prefers
  `VibrationEffect` composition CLICK+THUD (or HEAVY_CLICK), else EFFECT_HEAVY_CLICK, else a
  8–18ms waveform from `TouchFeel.wallHit`. Soft landings still use `feelThud` / `thump`.
  Touch feel OFF → system `LONG_PRESS`. Heartbeat / purr / tickle / sling notches unchanged.
- **Marble bounce:** slingshot with tension `< TouchFeel.MARBLE_MAX_TENSION` (0.72) enters
  `marbleOn` flight: gravity, restitution by weight, sharp bounce click, brief squash, settle
  after ~5 bounces or low speed. Side bounces never hide and never start swipe-travel / splat.
  Full-power slingshot still ImpactPlay SPLAT/THWACK/PANCAKE. Rocket Girl (`ridesProp`) keeps
  rocket fly. Debug: `debugMarbleBounce`, `debugMarbleBounces`, `debugMarbleSide`.
- **Pinch-and-pop:** 2nd pointer on the body starts pinch (cancels menu/sling/drag-throw). Squash
  from pointer distance; release → elastic overshoot + `pop` buzz + short character line (no
  banana jokes unless `monkey`). Debug: `debugPinch`, `debugPinchSx/Sy`.
- **Onboarding:** `Onboarding.Gesture.SLING` / `PINCH` added after HOLD. Skip chip unchanged.
  Only fresh installs (`onbDone` false); upgraders who already finished the 3-step demo are not
  re-shown (no separate `onbToysDone` one-shot).
- **Phone tilt:** `MotionDetector.tiltDeg()` + continuous `StageHost.tiltTick`. Slide ~32–45°,
  fall ~80–90° (weight-scaled) or upside-down-in-hand; table face-down stays sleep-mask.
  Discrete TILT_LEFT/RIGHT no longer one-shot `slideToEdge`. Debug: `debugTiltFeed`,
  `debugTiltSliding`.
- Files: `TouchFeel.kt`, `Feel.kt`, `MonkeyView.kt`, `Onboarding.kt`, `Motion.kt`, `Stage.kt`.
  Tests: `V1030FeelTest`, V1013/V10131 onboarding, MotionTest helpers. Tag `v10.30` is new
  (never move `v10.28` / `v10.29`). BUILD_PLAY_BUNDLE off.
- Full Android unit suite: **546 passed** (0 failed), including 13 GoldenTrace + 12 V1030.
  Official APK `settai-v10.30.apk` (15,276,442 bytes, 10.30 / 68, same debug signing cert as v10.29).

## v10.29 handoff (kept for Cloud / FAB / Explain-more context)


- Baseline is **v10.28 (66)** plus the test-only RealOverlayPathTest hover wait on main.
  Touch Feel, flickThrow=false, GoldenTrace, swipe paths and power cooldowns stay untouched.
- **Set-Tai Cloud is ON by default** for fresh prefs (`aiBrainEnabled=true`, `brainType=ONLINE`,
  `useOwnApiKey=false`). Stored OFF / NONE / offline / own-key always win. Give Brain OFF still
  writes `brain=NONE`. `GatewayBrain.kt` display trim is 560 code points (worker still caps
  usual replies at 200 and detail replies at 560). Anonymous `cloudUserId()` unchanged.
- **Dual FABs:** faint 💬 left + 🎤 right of the character (same idle alpha 0.18). Mic tap enables
  Voice if needed then `startListen` (Allow-mic chip when permission is missing). `fabSpeak` pref
  kept unused. Heart stays above the head.
- **"Explain more" chip** (`QuickChip.EXPLAIN_MORE`) after a successful GatewayBrain chat reply;
  sends exact phrase `Please explain that more fully` (must stay in sync with `worker.js`).
  Not shown on the long follow-up, resting/quota fallbacks, or offline/own-key replies.
- Worker **settai-brain**: https://settai-brain.settai-companion.workers.dev/health
  (`POST /v1/chat` for Android). Maintenance source/config/tests/README in
  `cloudflare/settai-brain/`. **Change model ONLY at `MODEL` in worker.js**, then
  deploy. It is `gemini-3.5-flash-lite`, minimal thinking, no sampling parameters.
  Google key is ONLY the Cloudflare GEMINI_API_KEY secret; never in repo/APK.
- Quota: 40 attempts per anonymous ID per **UTC** day, including background AI.
  SHA-256 hash + day/count mirrored in KV `settai-brain-quota`; SQLite-backed
  Quota DO reservations prevent KV eventual-consistency/concurrent overrun.
  Usual replies ~30-40 words / 200 chars; detail ask ("Please explain that more fully") ~90 words / 560 chars. No chat logs. Failed upstream attempts count. KV errors fail closed. Anonymous
  IDs are not authentication; resets/new IDs can evade per-install fair-use.
- **Live deployment:** health HTTP 200. GEMINI_API_KEY was set in the Cloudflare dashboard;
  live chat returns Gemini replies. Model stays `gemini-3.5-flash-lite` (change only at MODEL
  in worker.js). Worker length rules for v10.29 are already deployed; do not revert them.
- Voice bug causes: RANDOM overrides did not cover IDLE/BORED; MindLines' default
  branch still said "banana break" for every newer body; ProactiveLines shared
  monkey jokes; generic prompt/comedy and error lines leaked monkey identity. Newer bodies
  also inherited the low-battery "tell my bananas" line; this is now monkey-only.
  `CharacterHumor` gives all 13 built-ins distinct 3-line joke pools, guards shared
  fallbacks without new RNG calls, and selects suitable rest lines. Prompt names
  the exact body and forbids monkey/banana jokes except for monkey. Studio voice
  uses saved name/relationship/outfit and `analysisSummary`, not monkey defaults.
- `LocalChat` jokes/movement and existing PhoneCommands run before the brain, so
  battery/dance/jokes do not spend cloud quota. Public health never calls Gemini.
- v10.29: Cloud default, dual FABs, Explain-more chip, worker 30-40 / detail 560 already live.
  Worker tests: 7 passed. Full Android unit suite: 533 passed (0 failed), including GoldenTrace.
  Tag `v10.29` is new (never move `v10.28`). Official APK `settai-v10.29.apk` (15,266,426 bytes,
  10.29 / 67, same debug signing cert as v10.28). BUILD_PLAY_BUNDLE stays off.
- v10.28 focused Android tests: 10 passed; Workers runtime tests: 6 passed (mock
  Gemini; actual DO concurrency). Final full suite: 528 passed, including all 13 GoldenTrace tests. Worker tests
  are now a separate read-only, no-secrets CI job (mocked Gemini only).
- **Publication approved and completed:** repo is now PRIVATE. Existing tag
  `v10.28` stays at `68f6eb364e23a54deb2403f01c998f5160788262` (never move it).
  Release: https://github.com/planp1125-pixel/set-tai/releases/tag/v10.28 .
  Verified local APK `settai-v10.28.apk` (15,264,882 bytes, 10.28 / 66, same
  signing certificate as v10.27) was uploaded to that release alongside desktop
  assets. The tag CI's Android job failed at the RealOverlayPathTest setup hover
  assertion; other hover/GoldenTrace tests passed. Do not call that tag run green.
- **Test-only post-release fix:** `RealOverlayPathTest` no longer assumes the
  preparatory mid-air release must hover after exactly 30 frames. Its bounded
  `awaitHover` waits for real `debugHovering` (up to 900 frames), with failure
  diagnostics for character/saver, frames, hover, falling, arc/phase and box.
  All actual swipe/WindowManager/curve assertions remain. No production code,
  physics, Touch Feel, GoldenTrace or version changes; no new tag is needed.
  Focused regression passed twice; full local suite passed all 528 tests, including
  all 13 GoldenTrace tests. The test-only follow-up is on main, not a moved tag.
- CI Play bundle remains OFF. Never move tags or force push. No key/keystore added.

## 1. What the app is
Set-Tai (Settai, "mischievous" in Tamil) is a **floating overlay companion app** for Android. It is not a launcher and not a full-screen app.
Procedurally drawn 2D cartoon characters live on top of every app. They walk, float, fly, fall, react to touch and to the phone (charger, battery, keyboard, notifications), use superpowers, play small games and can chat.
They chat through an optional AI brain: an on-device model or an online API, used **only when "Give Brain" is on**.
- Package: `com.combo.monkeycompanion`. `minSdk 26`, `targetSdk 35`. Flavors: `full` (sideload) and `play` (Play Store).
- Main character on fresh installs: **Kutti Satan** (`devil`), a white devil with black oval eyes, the same as the app icon. Old installs keep their chosen character.

## 2. Module map
| Path | What's inside |
|---|---|
| `app/` | The Android app: overlay service, `MonkeyView`, activities (Main, Settings, Studio, Diary, Skills, PowerChoice …), AI hub, agent/accessibility, Robolectric tests (`app/src/test`). |
| `shared/` | Pure Kotlin + Android canvas engine shared by app and desktop: characters, rigs/renderers, powers, landings, physics, mind, Body Sense, theme. |
| `desktop/` | Desktop (Linux/Windows) build of the companion plus render/preview tests that write PNG sheets. |
| `tools/`, `docs/` | Helper scripts; older docs (`docs/CHANGELOG.md` is the long history up to v10.11, `DISNEY_12.md`, `M1_ANALYSIS.md`). |
| `.github/workflows/build.yml` | CI. |

Key files:
- `app/.../overlay/MonkeyView.kt`: the overlay view and the frame loop. It is very large. It holds touch, double-tap powers, the long-press ring (`showRing` / `onMenuItem`), speech (`speak(SCRIPT|INFO|AI)`, `sayInfo`), the Body Sense snapshot and `BSN.record` events.
- `shared/.../brain/Superpowers.kt`: the `Superpower` sealed family, `Superpowers.all` (the current character's enabled powers) and `choose()`.
- `shared/.../brain/PowerPrefs.kt`: v10.17 power choice. Only `devil` is choosable; built-ins return their fixed `powers`.
- `shared/.../brain/V1016Powers.kt`: Kutti Satan's powers, including Fireball and Juggle (v10.17).
- `shared/.../brain/Landings.kt`: landing-style pools per character. Devil: floaty landings, with jets only if boost/swoop/jets are enabled.
- `shared/.../ai/BodySense.kt`: what the AI "feels": position, state, mood, battery, time, energy, quiet time, keyboard, app, plus a fading feelings log.
- `shared/.../ai/BodyVoice.kt`: rate-limited spontaneous AI lines when Brain is on.
- `shared/.../engine/impact/ImpactPlay.kt` + `MonkeyView.tryToss / stepImpact / impactHit / drawImpactWord` (v10.20, the core): physical comedy for every character. Released HIGH (>= 25% of the screen above the floor) and fast (>= 1400 dp/s), or a flyer thrown very hard (>= 2800 dp/s), he flies under gravity and SPLATs flat on the left/right glass (slides down, peels off, falls, dizzy stars, shakes it off), THWACKs head-first into the top edge, or lands as a PANCAKE (accordion spring). Pushes from near the floor keep the old edge-hide (GoldenTrace `hide_unhide_monkey`). Squash is a canvas scale around a pivot in `drawMonkey`, so every renderer gets it. Charger: the FIRST plug-in ever shocks every character (`Fun5Store.shocks`), with a universal soot face (`drawWithSoot`: darkens whatever was drawn + white blinking eyes + smoke), then he remembers (`engine/impact/ShockLines`). Sulk pose (arms folded, turned away) + goodnight yawn are pose overrides in `applyDailyPoses`. Daily challenge: `shared/.../v5/Challenge.kt` + `Fun5Store.noteChallenge` (shown in the hello and the heart popup; +25 XP). No Random anywhere in the new code.
- **Swipe rule (v10.26)** in `MonkeyView.settleAfterDrag`: normal swipe (450..`SwipeTravel.FLICK_DP` = 1600 dp/s) = the character's own trip (`engine/swipe/SwipeTravel`); up/down on a flyer = a tour (`TOUR_UP` / `TOUR_DOWN`, a cubic curve inside the screen, longer); faster = a flick: hide at a close edge, otherwise thrown (`tryToss` → SPLAT / THWACK / PANCAKE). A haptic tick marks the flick speed while dragging. Jets: Armour Anna only (`hasJetPower`); Superboy = cape flight; Rocket Girl = her rocket.
- `shared/.../engine/show/Drama.kt` (v10.22): the mix-and-match drama, 5×6×5×4×4 = 2,400 shows (`Drama.parts(n)`, `Drama.build`). `gather()` starts from each real window's place (`ActorStart`: floor/wall/top/hidden; walls and top use the app's own climbing/hanging poses via `Track.edge`), every act starts with `restage()` (both move together, order kept, so they never merge) and ends apart unless it's a touch moment (`b.touch`). The windows report where they are through `ChaseShowHub.where` (`MonkeyView.showWho`), the stage draws them at their real scale on their real floor line, and `ChaseShowHub.finalOf` puts each window where the drama left it. `ShowEpisodeTest` checks all 2,400.
- v10.25.1: MainActivity's version label reads `packageInfo.versionName` (never hard-code it). Swipe down on a hovering body without `hasJetPower()` = `beginSmartFall()` instead of `rocketLanding` (that big arc climbs first). `Landings.ARC_IDS` = armour, superboy, rocketgirl: the ONLY bodies that may loop/arc/jet. `Landings.pool` strips JETS/SCREAM_JETS/ROCKET_ARC for everyone else; `flyTo` (non-ARC) = own swipe trip, or a gentle `slideFrom` glide for idle patrols (why == null); `rocketLanding` (non-ARC) = `beginSmartFall`. Test: `noCharacterButTheJetHeroesEverLoopsWhenSwipedFromTheBottom`. Kutti Satan has jets only while a rocket/jet power is firing. Kutti Satan/Angel swipe = `SwipeTravel.Style.MAGIC_HOPS` (3-5 puff hops, `SwipeFx.puffRed`).
- Fling rule (v10.25): at the top of `settleAfterDrag` (speed > `SWIPE_TRIP_DP`): aimed past / released near a side edge = `hideToEdge`; long throw from far (past the edge by > half a screen) = `tryToss(force = true)` SPLAT; aimed above the top = THWACK (ivy boosted to reach the top; stars shown during HOLD/FALL); else `trySwipeTrip`. `tryToss` without `force` does nothing now. Jets/thrust only when `hasJetPower()` (drawLandingThrust). Mask Man's SHEET_TRICK target keeps the swiped height; a trip ending above the floor calls `beginSmartFall()`.
- Swipe trips (v10.24): `engine/swipe/SwipeTravel.kt` (per-character `Style`, `target()`, `Trip.frame()` position/scale/alpha/rot/fx, `Trip.applyPose()`), props in `rig/SwipeFx.kt`. `MonkeyView.trySwipeTrip` runs in `settleAfterDrag` right after `tryToss`; `stepSwipeTrip` in `stepPhysics`; drawMonkey applies the frame. Styles: surfer SURF show, monkey DIVE_POP, hollowman MOONWALK, gajju EAR_FLAP (fast ears via `pose.dvAura` in GajjuRenderer), mask SHEET_TRICK, billa POUNCE, chittu MOUSE_HOLE, skater SKATE, custom RUN; armour/superboy/rocketgirl/devil/angel = null (old behaviour). Hard flings at a close edge still edge-hide; down swipes keep the old drop/land. `tryToss` now only fires for UPWARD throws (a sideways swipe never splats). Test: `V1024SwipeTest` (also writes skater-preview.png and swipes-preview.png).
- Skater Girl (v10.24, id `skater`): `character/V1024Skater.kt`, `rig/V1024SkaterRenderer.kt` (`SkaterRenderer`, `SkaterPeek`), `brain/V1024SkaterPowers.kt` (KickflipShowPower = main, GrindPower, BoardSpinPower). Slots: dvAura = on the board, dvSpell = kickflip roll, surfSpray = grind sparks. Last in `CharacterRegistry.all` and `Population.allIds`.
- Angel (v10.23, id `angel`): `character/V1023Angel.kt` (body, voice, adapt), `rig/V1023AngelRenderer.kt` (`AngelRenderer` extends `DevilRenderer`, which is now `open`; `AngelPeek`), `brain/V1023AngelPowers.kt` (HealingGlowPower = main, HaloSpinPower, AngelFlightPower, AngelFloatPower; fixed, not choosable). She borrows the devil's pose slots: dvSpell = healing sparkles, dvAura = warm glow, dvClone = halo spin, dvFire = wing flap speed. Her blink is `DevilBlink` offset by 1.7 s so the pair never blink together. Registered second in `CharacterRegistry.all`, last in `Population.allIds` (so crew picks for other characters are unchanged). Test: `V1023AngelTest` (also writes build/test-out/angel-preview.png and angel-with-satan.png). Angel-vs-Kutti-Satan dramas are not written yet.
- Surfing Girl (v10.22): `PowerStep.glide/swell` + `PowerFlight.glide()` = a continuous surf ride (speed ramps up, pumps, friction stop; carves; `glideLean`/`glideSpeed` → `AnimContext.carveLean/glideSpeed/gliding` → `SurferGirl.ride`). The renderer's water uses screen-fixed ripples (`AnimEngine.screenFeetX`).
- `shared/.../engine/show/Episode.kt`, `Episodes.kt`, `EpisodeRenderer.kt` + `app/.../overlay/ShowStageView.kt` / `ChaseShowHub.kt` (v10.21 Show 2.0): the Billa & Chittu cartoon episodes. An episode is a dope sheet: one `Track` per actor/prop made of timed segments (`move`, `hold`, `shape`, `dash` = wind-up/zip/skid, `pop`/`dive` for the mouse hole) with cartoon eases, face overrides (`Expr`), lines (speech bubbles over the speaker), effects (`Cue`), sounds + buzzes (`Beep`, names mapped in `audio/Sfx.byName`), a title card and an iris-out. Pure function of time, no Random. Episodes play in turn (`settai_show` prefs `next_episode`). `ShowEpisodeTest` guards no-teleport / no-overlap / readable lines. The old `ChaseShow`/`ChaseRun`/`StageRenderer` stay (tests) but are no longer drawn. Sounds come from `tools/gen_sfx.py`. To preview without a phone, compile `shared/` with the desktop canvas shim and render frames (see the desktop `EpisodeRenderTest`).
- `shared/.../v5/Bond.kt` + `app/.../fun5/Fun5Store.kt` (`bond`, `checkIn`, `takeMoment`, `patBond`) + `app/.../fun5/Stage.kt` (daily moments) (v10.19): the Bond. Friendship level/XP/streak stay in `Friendship`; Bond adds warmth (cools per missed day, level never drops), the daily check-in (hello / grace = 1 free missed day per week / playful sulk until a pat / welcome back after 5+ days), goodnight after 10 PM, and the AI's `YOUR BOND` prompt line (ChatHub.bondNote). The friendship heart is drawn in `MonkeyView.drawFriendHeart`. Switches: Settings › 💛 Engagement (Fun5Store.Feature with engagement = true). No Random anywhere (lines picked by date).
- `shared/.../ai/react/*` + `app/.../ai/ReactionHub.kt` (v10.18): the "Feel the touch" reaction engine. Every touch gets an AI-written line (instant from a prepared bank, plus fresh thoughts for big moments), with per-character anti-repeat memory saved in `files/reactions/`. Hooks are in `MonkeyView.feelTouch` / `stepReactionRefill`. Read `docs/REACTION_ENGINE.md` first.
- `app/.../ai/ChatHub.kt`, `app/.../ai/AppSettings.kt`: AI routing and all preferences (`defaultCharacter()` sets devil on detected fresh installs; `enabledPowers` per character).
- `shared/.../character/Character.kt`: `CharacterRegistry` and `CharacterNames`. `V1016Devil.kt` is Kutti Satan; `CustomCompanion.kt` plus `app/.../ui/CompanionStudioActivity.kt` are the user-created characters, which have their own multi-select powers.
- `app/.../theme/ThemeSkin.kt` (v10.17.1): restyles every activity's view tree after layout (hooked in `App.onActivityCreated`): grey buttons in light mode, and the System/Light/Dark mode (pref `theme_mode`, set in Settings → Appearance). Never used by the overlay.
- `shared/.../theme/Th.kt`: red / black / white palette and the colour-mapping helpers used across the UI.
- `shared/.../rig/*Renderer.kt`: all drawing (procedural `Canvas` only).

## 3. Build and test locally
```
./gradlew :app:assembleFullDebug                    # APK -> app/build/outputs/apk/full/debug/
./gradlew :app:testFullDebugUnitTest                 # app unit + Robolectric tests (slow: ~20+ min)
./gradlew :app:testFullDebugUnitTest --tests '*GoldenTraceTest'   # always run this one
./gradlew :desktop:test                              # render previews -> build/test-out/*.png
```
JDK 21. Screenshots from Robolectric screen tests go to `build/test-out/`. `CompanionStudioActivity` hangs under Robolectric, so don't screenshot it in tests.

## 4. CI / release flow
- **v10.24: the Play bundle (Play APK + AAB) is switched OFF in CI** to save time/memory; only `set-tai-full-release.apk` is built. Turn it back on with `BUILD_PLAY_BUNDLE: "true"` in `.github/workflows/build.yml` (step "Build release APKs + Play bundle"). CI sometimes dies with `OutOfMemoryError` in `packageFullRelease`: just rerun the failed job.
1. Push to `main`. The **Build** workflow runs the unit tests (step limit **28 min**, job 75 min), builds the APKs + AAB and publishes the full APK as a **pre-release `ci-<run number>`** (Releases). No Actions artifacts are stored any more (the free plan has only 0.5 GB of Actions storage; the job also deletes old artifacts).
2. For a release, create a **new** tag `vX.Y[.Z]` on the green commit. The workflow then publishes a GitHub **Release** with the full APK, Play APK, AAB and the Linux/Windows desktop builds (desktop jobs only run for `v*` tags).
3. Signing uses a keystore from repo secrets (decoded in CI). The Play signing key and the Store listing are not set up yet.
If the unit-test step times out, just re-run the job. It is usually load, not a real failure.

## 5. STANDING RULES (do not break)
- **Overlay only.** It is not a launcher.
- **Bump `versionCode`** (and `versionName` plus the version text in `MainActivity`) on every release.
- **AI is OFF unless "Give Brain" is on.** When Brain is on, characters speak AI lines from Body Sense and canned lines are suppressed. When it's off, the scripted lines stay.
- **Procedural drawing only**: no image assets for characters or effects. The launcher icon is the one exception: a traced raster.
- **`GoldenTraceTest` must pass.** Never change the order or number of frame-loop `Random` calls. Use time-based or deterministic logic instead, like `DevilBlink`.
- **No power cooldowns.**
- **Double-tap = a random enabled power, with the main (signature) power first.**
- **The 💬 chat button stays faint** and never covers the face.
- **Timers persist through interruptions**: reminders and focus timers survive restarts.
- **Never force-push and never move or delete tags.** Make a new tag instead, e.g. `v10.13.1-r2`.
- The repo is **private, with no licence file**. **Never commit API keys** or keystores.
- **A character the user hid stays hidden (v10.21.2, strict).** Only the user's tap/drag on its tab, or something the user asks for (chat, reminders they set, an explicit power), brings it out. Automatic things (mind, skits, charger trip, comfort, utility/watchdog alerts, cricket, AI actions) must call `autoUnhide()`, which refuses for a user hide (`userHidden` = hidden and not resting). Rest hides (idle rest, screen-off) may come back as before.
- The user's message to every character is universal: features should work for all characters unless they are specific on purpose.

## 6. Owner preferences
- Explain things **simply**, in short, plain words.
- Look: a **red / black / white** theme (#E0101A, #0D0D0D, white) matching the icon. Buttons are soft grey, not glaring white. Dark mode is optional (System / Light / Dark).
- **Kutti Satan is the main character** and has **all powers**: vanish & portal (main, always first), teleport hops, float a buddy, spell sparks, shadow clones, levitate, flight swoop, fireball, juggling, heat-vision (laser) eyes, spin dance and the rocket (Diwali) boost. The user picks which are on from his long-press **Powers** ring → **☑️ Choose** (`PowerChoiceActivity`), and the choice is saved.
- **Built-in characters keep their fixed powers**, with no picker. **Created (Studio) characters** have settable powers in the Studio.
- **Body Sense**: the AI should "feel" the body (position, motion, mood, time, battery, energy, touch, pain/comfort, needs, surroundings). Speech comes from the AI when Brain is on.
- The icon is a red background with a white peeking devil and black oval eyes, traced from the owner's reference. Don't redesign it without asking.

## 7. Known issues and risks
- Flaky auto-rest test, root cause found and fixed in v10.17: a timed `PEEK` state never ended in `MoodController`, so the character could stay stuck peeking forever and never rest. Timed peeks now end by themselves. The test also prints a `debugRestBlock` diagnostic if it ever fails again. The whole suite is still close to the 28 min CI limit; re-run once if it times out.
- Float-a-buddy: the lifted partner can clip at the top edge on small windows.
- Shadow clones can be cut off at the window edges.
- Fresh-install detection (`firstInstallTime == lastUpdateTime`) decides whether the devil is the default. Reinstalls and restores may behave unexpectedly.
- Borrowed powers on the devil (swoop, laser, dance, boost) use the generic power animations, so they look less tailored than his own magic. Juggling currently reuses the banana juggle drawing.
- The "occasional" Diwali rocket is a normal random entry; it has no lower weight yet.
- `CompanionStudioActivity` hangs in Robolectric screen tests.

## 8. Pending / next ideas
- Left over: Kutti Satan/Angel idle patrol flights still use the shared arc (no jets, but not hops/wing flaps); the wall ride is drawn mid-air (no real wall); Kutti Satan & Angel black-magic swipes; swipe trips are not tested on the phone yet.
- Polish the devil to match the icon more closely (head shape, eye size and placement).
- A final name for Kutti Satan (the owner will decide). Change `CharacterNames.DEVIL`.
- Play Store: signing key, listing and screenshots (later, when the owner asks).
- Optional "All rights reserved" line in README.
- v5/v10 backlog not done yet (see `FUTURE_PLAN.md` §3): M5 charger reactions for the remaining characters, M6 cat-and-mouse hide-and-seek, M7 shared physics toys (balls rolling all over the screen, water toys), M8 local memory and relationships.
- Weighting so the rocket ride is rarer, and red juggling balls for the devil.
