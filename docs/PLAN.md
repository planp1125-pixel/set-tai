# Set-Tai Weekly Drama: the plan

*Draft 1 for the owner to check, 3 Nov 2026. Nothing below is built yet except what is marked ✅.*

## 1. The idea in one paragraph

Every pair of characters gets its own little cartoon show. When you double-tap a character and its partner is on screen, the two play a drama together, starting from wherever they really are on your phone. Each drama is a **small text file** (a script). New scripts are published **every week** on GitHub, and every phone picks them up without an app update. Every character can act out every script line using one shared **action library** (cry, laugh, chase, splat, vanish…), each character in its own style. Later, if people love it, paid extras (a subscription or season pass) keep the money coming.

## 2. What we already have

| Piece | State |
|---|---|
| Billa & Chittu drama, 2,400 mix-and-match shows, starting from the real characters' places | ✅ v10.22 |
| Episode engine: timed moves, cartoon timing (wind-up, zip, dead stop), faces, speech bubbles, sounds, buzzes, iris-out ending | ✅ v10.21–v10.22 |
| "Reaction engine": AI-written lines when you touch a character | ✅ v10.18 (words only, not body moves) |
| Shared body animations for all characters (sad, angry, giggle, run, jump, wave, dance, sleep, falling…) | ✅ older |
| Powers per character (Kutti Satan's teleport, Rocket Girl's rocket, Gajju's water spray…) | ✅ older |
| Test builds without tags, Releases instead of artifacts | ✅ CI fix, 3 Nov |

## 3. Rules we keep (unchanged)

- Overlay only; not a launcher. Procedural drawing only (no image files for characters).
- `GoldenTraceTest` must pass; never change frame-loop `Random` calls. New code uses no Random in the frame loop.
- `versionCode` goes up on every release; one release at a time, tested on the phone before the next.
- A character **you hid** never comes out by itself (v10.21.2).
- Plain English by default (a character-language option comes later, see §11).
- Features work for every character unless they are specific on purpose.
- **One rule changes on purpose:** see §5 (double-tap).

## 4. The pairs

| Pair | Comedy in one line | Special props / powers used |
|---|---|---|
| **Billa & Chittu** | Classic cat-and-mouse: chase, traps that backfire, a soft heart | holes, pan, peel, mousetrap, chilli, bowling ball (all ✅) |
| **Kutti Satan & Angel** *(Angel is new)* | Mischief vs goodness: he pranks, she fixes it with a smile; he secretly likes her | his teleport, fireball, juggling; her halo, wings, a gentle glow / healing sparkle |
| **Rocket Girl & Gajju** | She shows off her rocket and fireworks; Gajju puts the fire out with water from his trunk (on her, by "accident") | rocket, fireworks; Gajju's trunk spray |
| **Superboy & Armour Anna** | Two heroes competing to save the day, both crashing into things | heat-vision, flight; armour jets, gadgets |
| **Surfing Girl & Mask Man** | She glides around on water; he juggles and keeps getting splashed | surf glide (✅ v10.22), spray; juggling balls |
| **Monkey & Hollow Man** | The banana-loving prankster vs the invisible dancer ("Where did he go?") | bananas; moonwalk, vanishing |

Notes:
- **Duo / Trio crews:** if you have several characters on screen, a double-tap plays the drama of **the tapped character and its own partner**. If the partner is not on screen, it walks in from the side (like Billa/Chittu today) **unless you hid it** (then: just the normal power).
- **Created (Studio) characters:** later they can join a pair as a "guest" using the generic actions.

## 5. Double-tap (the rule change)

Today: double-tap = a random power (the main power first). New:

1. **Double-tap a character whose partner is on screen → the pair's drama.**
2. If the partner is not on screen → the drama still plays, the partner walks in (not if you hid it).
3. If there is no drama to play (setting off, or no scripts) → the power, as today.
4. **Long-press menu** keeps both: "🎬 Show" and "⚡ Power".
5. Settings › Shows: "Double-tap plays a show" on/off (for people who prefer powers).

## 6. Part A: the shared Action Library ("body reactions")

One list of actions, made **once**, usable by **every** character. Each character may give an action its own style.

### 6.1 Action list (first version, ~40)

| Group | Actions |
|---|---|
| Feelings | `happy`, `sad`, `cry` (tears), `sob` (big cry, shaking), `angry`, `furious` (red face, steam), `sulk` (arms folded, turned away), `scared` (shiver, sweat), `shy` (blush, look away), `love` (hearts), `bored` (yawn), `sleep`, `wake` |
| Laughs | `giggle`, `laugh` (big belly laugh), `roll-laugh` (rolling on the floor), `smirk` |
| Talking | `say "…"`, `whisper "…"`, `shout "…"`, `think` (thought cloud) |
| Looks | `look-at <who>`, `take` (freeze and look at you), `double-take`, `shrug`, `facepalm` |
| Moving | `walk to <place>`, `tiptoe to`, `run to`, `dash to` (wind-up, zip, skid), `jump`, `hop-over <who>`, `climb <wall>`, `drop` |
| Chase & fight | `chase <who>`, `flee`, `pounce`, `dodge`, `duck` |
| Crashes | `splat <wall>`, `bonk-head` (top edge), `slip`, `fall-back`, `pancake`, `dizzy` (stars) |
| Magic & power | `vanish`, `appear`, `teleport to`, `fly to`, `power` (signature power), `fire` (each character's own attack) |
| Props | `hold <prop>`, `give <prop> to <who>`, `throw <prop> at <who>`, `eat`, `drop <prop>` |
| Together | `hug`, `high-five`, `pat <who>`, `ride <who>`, `share <prop>`, `sleep-together`, `wave-bye` |

### 6.2 "In their own style"

Every action has a default. A character can override it:
- `fly to` / `dash to`: Kutti Satan **teleport-hops**; Surfing Girl **surfs on a wave**; Rocket Girl / Armour Anna use **jets**; Gajju **flaps his ears**; others run.
- `vanish`: Kutti Satan red smoke + portal; Hollow Man fades; others hide behind the nearest edge.
- `fire`: Kutti Satan fireball, Superboy heat-vision, Gajju water spray, Mask Man juggling-ball throw…
- `cry`: Gajju's big trunk-tears; Chittu tiny fast tears; and so on.

### 6.3 Where the library is used

1. **Dramas** (Part B): every script line is an action.
2. **AI chat:** the AI can already add tags like `[dance]`; it gets the whole list (`[cry]`, `[laugh]`, `[chase]`…), so its words and its body match.
3. **Touch reactions:** a poke plays a matching body action together with the AI / scripted line (e.g. poke → `angry` + "Hey!").

### 6.4 Tech notes

- Built on the v10.21 episode engine (`Track`, `Expr`, `Cue`, `Beep`), generalised from "cat & mouse" to any character (`ActorStart`, real scale ✅).
- Each action = a small function that adds timed segments to a character's track (pure, no Random), plus a per-character style table.
- Tests: every action × every character: no teleports, ends standing on the floor (or where it says), plays in under N seconds.

## 7. Part B: Drama scripts (text files)

### 7.1 What a script looks like

```text
# drama: The Lost Laddu
pair: billa, chittu
week: 2026-W46
length: short            # short ≈ 25 s, long ≈ 45 s

scene: kitchen-floor     # where they gather (floor, wall, top...)
billa: sleep
chittu: tiptoe to snack
chittu: whisper "Shh…"
chittu: take
chittu: hold snack
billa: wake
billa: angry, shout "HEY! My laddu!"
chittu: run to right-wall, dive hole
billa: chase chittu
billa: splat right-wall
chittu: peek, wave, say "Missed me!"
billa: sad, cry, say "Ow…"
chittu: walk to billa, give snack to billa, say "Half for you."
billa: shy, say "Thanks…"
together: sleep-together
end: iris
```

- One line = one beat. `,` = do these at the same time. Times are worked out automatically (cartoon timing: wind-up, pauses, takes), with optional `wait 0.5`.
- Places are words, not numbers: `left`, `right`, `middle`, `left-wall`, `right-wall`, `top`, `near <who>`, `away from <who>`.
- Unknown words never crash anything: the line is skipped and reported in the script checker.

### 7.2 The player

- Reads the script, starts from where both characters really are (✅ `gather()`), turns each line into actions, keeps them apart unless they touch on purpose (✅ `restage()` idea), plays it on the show stage (✅) with sounds, bubbles and the iris-out ending (✅).
- The Billa & Chittu 2,400 shows stay as the built-in "random show"; scripts are "episodes".

### 7.3 Safety (important)

- A script is **data, not code**: it can only use the actions in the library. No links, no commands, no personal data.
- Size limits (e.g. ≤ 120 lines, ≤ 90 s), text limits (bubble ≤ 60 chars), a word filter for lines.
- Every script is checked twice: by a **checker on GitHub** before it is published (CI) and again by the app before playing.

## 8. Part C: Weekly delivery

1. Scripts live in the repo: `dramas/<pair>/<week>-<name>.txt` plus one `dramas/index.json` (title, pair, week, free/premium, checksum).
2. **CI** checks every script (the same checker as the app) and publishes `index.json` + scripts to a free public place (a public "content" repo or GitHub Pages; the app repo stays private).
3. The app checks once a day on Wi-Fi/charging (tiny download, a few KB), saves new scripts on the phone, and shows a small **"🎬 New episode!"** badge on the paired characters.
4. The next double-tap plays the newest unseen episode; after that, a mix of episodes and random shows.
5. Works offline: built-in episodes ship inside the app.
6. Settings › Shows: "Download new episodes" on/off.

## 9. Part D: Writing a drama every week (content pipeline)

- **Goal:** 1 new episode per pair per week (6/week) once all pairs exist; start with Billa & Chittu only.
- **How:** the owner gives a one-line idea ("Chittu steals Billa's birthday cake") → AI drafts the script → the checker + a desktop preview (renders the drama as a video, like the previews already made) → owner approves → push → it's live.
- A small "Drama Studio" page in the app or desktop later (pick pair, type idea, preview, publish).
- Keep a seasons calendar: festivals, cricket, exams, monsoon… (world-wide neutral by default; regional festivals only if the user's region/setting says so).

## 10. Part E: Money (later, step by step)

| Stage | When | What |
|---|---|---|
| 1. Free | now → first months | All weekly episodes free. Measure: do people come back weekly? |
| 2. Supporter | when there are regular weekly viewers | Optional "Support Set-Tai" (one-time, small). |
| 3. Episode Pass | when weekly viewing is steady | Subscription (Google Play Billing): bonus episodes, early access (3 days), special seasons, extra pairs/costumes. Free episodes continue. |

- Indicative price for India: ₹49–₹99/month or ₹399–₹599/year (to test). Global prices set by Play.
- Never lock the basic companion or safety features behind payment.
- Needs: Play Store signing + listing (pending), privacy policy, Billing library, a small "premium" flag check (on the phone, verified by Play).
- Measuring without spying: on-phone counters only by default (episodes watched, finished); optional anonymous stats only with consent.

## 11. Later

- **Character language engine:** each character can speak a chosen language (Tamil, Hindi, Spanish…); scripts store line IDs so lines can be translated.
- **User-made dramas:** type an idea in the app, the AI writes a script, the characters play it (safe because only library actions exist).
- Sharing: record a drama as a short video to share (WhatsApp / Reels) with a small Set-Tai watermark: free marketing.

## 12. Release plan (one at a time, each tested on the phone)

Balance (agreed 3 Nov): about **80% everyday phone moments** (charging, notification roasts, battery, alarms, hello, bedtime) and **20% episodes**. The action library feeds both.

| Release | What | State |
|---|---|---|
| v10.23 | New character **Angel** (Kutti Satan's partner) | ✅ (Grok) |
| v10.24–v10.25.1 | Own swipe trip per character, Skater Girl, no jets for non-jet characters | ✅ (Grok) |
| **v10.26** | **One swipe rule**: normal swipe = travel with own power; up/down on flyers = tour of the screen (Satan teleport hops, Angel wings, Gajju's clumsy Dumbo flight, surfer); fast flick (felt as a tick) = splat / thwack / pancake or hide at a close edge; jets only for Armour Anna, Superboy cape flight, Rocket Girl rocket | ✅ built, to test |
| v10.26.2 | Long-press powers always start (a stuck "wait until landed" power blocked every power) | ✅ built, to test |
| **v10.27** | **Touch Feel**: weight when picked up (Gajju heavy thud, Chittu light tick), drag ticks, slingshot (long-press + pull: clicks, snap, flies), heartbeat (keep holding), purr when petted, tickle giggles, shake rattle + dizzy stars, belly squish / nose boop, landing thumps; setting to switch off | ✅ built, to test |
| v10.28 | Action Library part 1 (feelings, laughs, looks, moving, crashes) for all characters + per-character styles + tests | next |
| v10.29 | Everyday moments upgraded with the actions (charging, notification roasts, battery, alarms, hello, bedtime) | |
| v10.30 | Action Library part 2 (chase, magic, props, together) + AI chat tags use the library | |
| v10.31 | Drama script player + checker; first scripted episodes (Billa & Chittu, Kutti Satan & Angel); double-tap rule (§5) | |
| v10.32 | Remaining pairs' props + one episode per pair | |
| v10.33 | Weekly delivery (index, download, "New episode!" badge, settings) | |
| later | Drama Studio helper; Play Store launch; Billing / Episode Pass | when weekly viewers are steady |

## 13. Risks and answers

| Risk | Answer |
|---|---|
| Too much writing every week | AI drafts, owner approves; start with one pair; reuse scenes |
| Battery | Dramas only run when you start them; downloads are tiny and on Wi-Fi/charging |
| Scripts break on some phones | Checker + tests for every action; the player never crashes on bad lines |
| Double-tap change annoys power fans | Setting to switch it off; long-press keeps both |
| Copyright | Original characters and gags only; "cartoon-short style" is fine, copying a specific show is not |
| Store rules | Scripts are data (allowed); premium via Google Play Billing only |

## 14. Questions for the owner

1. **Angel:** girl or boy? Look (halo, small wings, white/pink/gold)? Name?
2. Pairs OK as listed? Is Monkey & Hollow Man fine as the 6th pair?
3. Double-tap rule in §5 OK (drama when the partner is there, power otherwise)?
4. Free first, paid later (§10) OK? Any price you have in mind?
5. Should episodes be **weekly for all pairs**, or one pair per week in rotation (less writing)?
6. Content repo: OK to make a separate **public** repo just for drama scripts (the app code stays private)?
