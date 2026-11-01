# Set-Tai: handover notes (for the next developer / AI)

Updated for **v10.19 (versionCode 50)**, 31 Oct 2026. Owner: Manikandan Gopalakrishnan (COMBO MEALS). He tests on a Motorola Edge 70 Fusion.

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
1. Push to `main`. The **Build** workflow runs the unit tests (step limit **28 min**, job 75 min) and builds the full APK, Play APK and Play AAB as artifacts, plus Linux and Windows desktop builds.
2. For a release, create a **new** tag `vX.Y[.Z]` on the green commit and push it. The same workflow then publishes a GitHub **Release** with the full APK, Play APK and AAB attached.
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
- Polish the devil to match the icon more closely (head shape, eye size and placement).
- A final name for Kutti Satan (the owner will decide). Change `CharacterNames.DEVIL`.
- Play Store: signing key, listing and screenshots (later, when the owner asks).
- Optional "All rights reserved" line in README.
- v5/v10 backlog not done yet (see `FUTURE_PLAN.md` §3): M5 charger reactions for the remaining characters, M6 cat-and-mouse hide-and-seek, M7 shared physics toys (balls rolling all over the screen, water toys), M8 local memory and relationships.
- Weighting so the rocket ride is rarer, and red juggling balls for the devil.
