# Set-Tai: handover notes (for the next developer / AI)

Updated for **v10.25.1 (versionCode 61)**, 4 Nov 2026. Owner: Manikandan Gopalakrishnan (COMBO MEALS). He tests on a Motorola Edge 70 Fusion.

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
- `shared/.../engine/show/Drama.kt` (v10.22): the mix-and-match drama, 5×6×5×4×4 = 2,400 shows (`Drama.parts(n)`, `Drama.build`). `gather()` starts from each real window's place (`ActorStart`: floor/wall/top/hidden; walls and top use the app's own climbing/hanging poses via `Track.edge`), every act starts with `restage()` (both move together, order kept, so they never merge) and ends apart unless it's a touch moment (`b.touch`). The windows report where they are through `ChaseShowHub.where` (`MonkeyView.showWho`), the stage draws them at their real scale on their real floor line, and `ChaseShowHub.finalOf` puts each window where the drama left it. `ShowEpisodeTest` checks all 2,400.
- v10.25.1: MainActivity's version label reads `packageInfo.versionName` (never hard-code it). Swipe down on a hovering body without `hasJetPower()` = `beginSmartFall()` instead of `rocketLanding` (that big arc climbs first). `Landings.pool` strips JETS/SCREAM_JETS/ROCKET_ARC for `NO_JET_IDS` (surfer, gajju, hollowman, skater, angel, devil). Kutti Satan has jets only while a rocket/jet power is firing. Kutti Satan/Angel swipe = `SwipeTravel.Style.MAGIC_HOPS` (3-5 puff hops, `SwipeFx.puffRed`).
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
