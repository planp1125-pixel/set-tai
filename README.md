# Tai

Tai — Tactile AI Interface (Settai companion app)

# Settai (sett'AI) — living cartoon companions for Android

Settai ("mischievous" in Tamil) is a **floating overlay app** for Android. 2D vector-rigged cartoon characters live on top of the whole phone. They walk the screen edges, fly, fall, react to touch, and help with daily tasks.
It's **an overlay only, not a launcher.**

- Package: `com.combo.monkeycompanion`. This folder is **v10.8 (`versionCode 31`)**. The next build must be `versionCode 32` or higher.
- `minSdk 26`, `targetSdk 35`, `compileSdk 37 (minor 2)`. Flavors: `full` (default) and `play`.
- What changed in each build: **[docs/CHANGELOG.md](docs/CHANGELOG.md)**. What's left on the roadmap: **[FUTURE_PLAN.md](FUTURE_PLAN.md)**.
- Libraries, characters and how to build shows (plan + what exists + what to build): **[docs/LIBRARIES-AND-SHOWS.md](docs/LIBRARIES-AND-SHOWS.md)**.
- How each of Disney's 12 animation principles maps to the code: **[docs/DISNEY_12.md](docs/DISNEY_12.md)**.
- v9.5 is still the old fallback; the M1 physics-extraction notes are in [docs/M1_ANALYSIS.md](docs/M1_ANALYSIS.md).
- Older long-form design notes (v7 "phone as a world" dictionary, mind and diary design): [docs/README-v7-legacy.md](docs/README-v7-legacy.md).

## New in v10.x
- **v10.0 (M1):** physics moved into `engine/PhysicsBody` with data `CharacterProfile`s and golden-trace tests, with no behaviour change.
- **v10.1–10.3: Gajju**, a chibi flying baby elephant. Poking his belly makes him spray water from his trunk.
- **v10.4:** Mask Man juggles, and the balls drop and roll when he's shaken or flung.
- **v10.5–10.6: Screen Smash.** Smash a pretend copy of your screen. It has hit-stop, shake, a debris pile and wind-ups.
- **v10.7: Disney part 1.** An animation engine with squash & stretch, follow-through springs and slow in/out, behind "Smooth animation (beta)".
- **v10.8:**
  - Disney principles 2–12 through a `LivingLayer`: anticipation, staging, secondary action, timing, exaggeration, solid drawing, appeal and arcs.
  - **Living reactions:** touch zones (including ears, trunk and rocket), a big reaction library, and a picker that never repeats itself.
  - **Rocket Boy**, a little boy riding a Diwali rocket.
  - **Procedural fireworks**, different every time.
  - Diwali accents.
  - Smash sounds, a new reveal behind the smashed tiles, and character reactions.

## Features through v9.5
- **Characters**: Settai the monkey, Armour Anna / Ironboy, Superboy, Mask Man, Billa the cat and the mouse (Cat & Mouse), plus custom photo look-alikes (Girl / Boy / Woman / Man with an age selector, including Chottu). Since v10: Gajju (v10.1) and Rocket Boy (v10.8).
- **Solo / Duo / Trio crew**: actually spawns 1, 2 or 3 live characters (`MonkeyOverlayService.syncCrew`).
- **Cat & Mouse Duo**: they walk all 4 screen edges with zero gravity (FLOOR / WALL_L / WALL_R / CEIL), and do a 1-second Tom & Jerry mid-air scare (`coyoteScareTimer`) before falling.
- **Heroes**: Iron Man-style flight thrust (hand repulsors, chest unibeam, boot jets). Superboy and Armour can randomly lose thrust when they hit an edge and crash-land (`edgeThrustMultiplier`).
- Drag, fling, fall and bounce physics, hiding at the edge as a `<` tab, landings, superpowers, a radial long-press menu, and adaptive FPS (60/24/8/0).
- **Photo-to-look-alike Companion Studio**: EXIF fix, a 68-point face mesh, gender and age detection, and hair colour. Editing uses zoom from 1x to 6x, split compare, Move Mesh handles, a 2.5x loupe, trackpad mode, a D-pad nudge and live 1:1 mesh-to-character copying. It always produces clean 2D vector faces, never raw photo cutouts.
- **Multi-character roster** (`CustomCompanionStore`). Custom names are saved and never overwritten.
- **🧠 Give Brain** card on Home, Studio and Settings. **Set-Tai Cloud is ON by default** (`aiBrainEnabled=true`, `brainType=ONLINE`, no own key). Turn Brain off any time for local/canned lines only.
- **Daily utilities** (`DailyUtilityEngine`):
  - OTP and delivery catcher
  - Doomscroll / YouTube / Reels screen-time guard, plus a 20-20-20 eye break. This works **even while tucked at the edge tab**: it auto-wakes, alerts, then re-tucks.
  - Clipboard backpack
  - Sticky reminders, water and medicine alerts
  - Kids study Pomodoro
  - Battery 85%/100% and overheat watchdog
- Notification summary cards (scrollable), diary, routines, an opt-in accessibility screen agent (asks Yes/No before acting), a live wallpaper, and a desktop (Swing) companion sharing the same code.

## Project structure
```
app/                       Android app (flavors full / play), adds ../shared/src/main/kotlin as a source dir
  overlay/MonkeyView.kt            Main overlay View: frame loop, physics, edge walking, flight/thrust, touch, drawing (~5.7k lines)
  overlay/MonkeyOverlayService.kt  Foreground service; WindowManager windows; Solo/Duo/Trio crew (syncCrew)
  MainActivity.kt                  Home: character grid, crew mode, Give Brain card, utilities entry
  ui/CompanionStudioActivity.kt    Photo -> look-alike studio (mesh editing, loupe, trackpad, D-pad, roster)
  ui/SettingsActivity.kt           Settings (incl. Give Brain)
  character/PhotoCompanionAnalyzer.kt  Face analysis: EXIF, 68-pt FaceLandmarkMesh, gender/age, hair colour
  character/CustomCompanionStore.kt    Saved custom characters roster
  agent/DailyUtilityEngine.kt      OTP/delivery/backpack/pomodoro/reminders/battery + edge-tab screen-time watchdog
  agent/NotifSummary.kt, Otp.kt, ForegroundAppTracker.kt, SettaiAccessibilityService.kt
  ai/AppSettings.kt, ChatHub.kt, OfflineBrain.kt   Settings + optional AI brain (gated by Give Brain)
  wallpaper/                      Live wallpaper variant
shared/src/main/kotlin/com/combo/monkeycompanion/   Platform-neutral code (also used by desktop/)
  rig/Pose.kt, MonkeyRig.kt, Climber.kt, Cape.kt, Animations.kt   Pose slots, skeleton, IK, Verlet tail, cape
  rig/MonkeyRenderer.kt, ChibiHumanRenderer.kt, HeroRenderers.kt, CatMouseRenderers.kt  Vector art renderers
  character/Character.kt, Heroes.kt, CatMouse.kt, CustomCompanion.kt  Character definitions + registry
  brain/MoodController.kt, PhoneMoments.kt, Superpowers.kt, Landings.kt  Mood, phone moments, powers
  mind/Personality.kt, Mind.kt, Habituation.kt, Diary.kt          Traits, utility AI, novelty decay, diary
  world/WorldState.kt, Director.kt, CatMouseBehaviors.kt           World signals, banter, cat-mouse gags
  engine/PhysicsBody.kt, CharacterProfile.kt                       v10 physics + per-character data (no per-character ifs)
  engine/anim/  AnimEngine, Spring, Easing, SquashStretch, AnimTuning, LivingLayer (+StageBus)   v10.7/10.8 animation layers
  engine/touch/ TouchInput, Reactions (library), ReactionPicker, ReactionLayer                   v10.8 living touch reactions
  rig/Fireworks.kt, RocketBoyRenderer.kt; character/RocketBoy.kt; brain/RocketBoyPowers.kt      v10.8 Rocket Boy + fireworks
  smash/SmashGame.kt               Screen Smash game logic (the view is in app/.../smash)
app/.../audio/Sfx.kt               v10.8 SoundPool sound effects (WAVs from tools/gen_sfx.py, in res/raw)
desktop/                   Ubuntu/Windows Swing companion (shares shared/)
```

## Build (this exact setup worked on the build box)
JDK 21 (Temurin 21.0.12), Android SDK platform `android-37.2` and build-tools `37.0.0`, Gradle wrapper 9.8.2, AGP 9.4.1, Kotlin 2.4.21.
```bash
export JAVA_HOME=/path/to/jdk-21          # box: /workspace/tools/jdk-21.0.12.1+1  (or: . /workspace/tools/env.sh)
export ANDROID_HOME=/path/to/android-sdk  # box: /workspace/tools/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleFullRelease :app:assemblePlayRelease   # release is signed with the debug key
#  -> app/build/outputs/apk/full/release/app-full-release.apk  (ship as settai-vX.Y.apk)
#  -> app/build/outputs/apk/play/release/app-play-release.apk  (ship as settai-vX.Y-play.apk)
./gradlew :app:testFullDebugUnitTest      # unit + Robolectric tests
./gradlew :desktop:compileKotlin          # desktop companion
```
Before every build, bump `versionCode` and `versionName` in `app/build.gradle.kts`. Ship 4 files per release: the full APK, the play APK, a src zip and a preview PNG.
