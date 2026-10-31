# Reaction engine ("Feel the touch", v10.18)

Every touch on a character gets words written by the AI, from what the body actually felt, the character's mood, the time, energy, friendship and history. The words are never fixed lines from code. It works for every character (built-in and Studio) and for every brain (on-device or API).

It only runs when **Give Brain** is on. With Brain off, the scripted lines play exactly as before. Animations and powers never change: the engine only adds the voice. Double-tap is still a random enabled power.

## The flow

```
                 ┌──────────────────────────────┐
  You touch ───► │ 1. SENSE  What happened?     │  where (belly/head/tail…), how (tickle/poke/throw…),
  the character  │                              │  how hard, how many times, what he was doing
                 └──────────────┬───────────────┘
                                ▼
                 ┌──────────────────────────────┐
                 │ 2. FEEL  How is he right now?│  mood, energy, time, battery, friendship,
                 │                              │  recent touches, memories, personality
                 └──────────────┬───────────────┘
                                ▼
                 ┌──────────────────────────────┐
                 │ 3. REFLEX (instant)          │  body animation + a line the AI wrote
                 │                              │  a little earlier (from the "bank")
                 └──────────────┬───────────────┘
                                ▼
                 ┌──────────────────────────────┐   only for big moments:
                 │ 4. THINK (1–3 s later)       │   3rd touch in a row, thrown, woken up, empty bank…
                 │                              │   AI writes a FRESH line + maybe a body action
                 └──────────────┬───────────────┘
                                ▼
                 ┌──────────────────────────────┐
                 │ 5. REMEMBER                  │  every line he says is remembered,
                 └──────────────────────────────┘  so he never repeats himself

  In quiet moments: 6. PREPARE → the AI writes fresh lines for the bank, avoiding everything
  he said before, with rotating styles (joke, drama, question back, Tanglish, revenge…)
```

## Each step in plain words

1. **Sense.** The overlay turns a finger into a `TouchFeel`: the kind (tickle, pat, poke, tail, hold, pick up, throw, wall bump, bad touch, woken up, double-tap power), the body part, the strength (gentle / firm / rough), the streak (same kind within 3 s), and what he was doing (sleeping, flying, dancing…). Example sentence the AI gets: *"Your belly was just tickled gently, 3 times in a row, while you were sleeping."*
2. **Feel.** A `MoodKey` sums up his state: time of day, mood (happy / a bit annoyed / grumpy / sleepy), energy, and friendship (new friend → best friends). The full live body note (Body Sense: battery, app, feelings, recent events) is already in the AI's system prompt with his personality.
3. **Reflex.** The touch takes a ready line from the bank for that kind and mood. If there isn't one for the exact mood, it uses one written for the same time of day and friendship. It shows instantly, with no waiting for the AI.
4. **Think.** For big moments (throw, wall bump, bad touch, wake-up), a streak of 3+, or when the bank is empty, the AI is asked for a fresh line. Only one at a time, at least 8 s apart; answers later than 10 s are dropped. The line may carry one action tag ([angry], [hide]…), which the body plays.
5. **Remember.** Every shown line goes into the character's `LineHistory` (last 300). Any new line that is the same, starts with the same three words, or uses mostly the same words is rejected, even if the AI writes it again weeks later.
6. **Prepare.** When he is idle, the engine asks the AI (at most every 45 s) to write 2 lines each for the 4 most-used touch kinds that are running low, with rotating styles and a "don't repeat these" list. Lines older than 6 hours are thrown away.

## Where the code is

| Part | File |
|---|---|
| Touch kinds, `TouchFeel`, `MoodKey` | `shared/.../ai/react/TouchFeel.kt` |
| Anti-repeat memory | `shared/.../ai/react/LineHistory.kt` |
| What we ask the AI, and how answers are read | `shared/.../ai/react/ReactionPrompts.kt` |
| The engine (bank, think, prepare, save/load) | `shared/.../ai/react/ReactionEngine.kt` |
| Android side: one engine per character, saved to `files/reactions/<id>.txt` | `app/.../ai/ReactionHub.kt` |
| AI request (same persona + Body Sense prompt as other lines) | `ChatHub.reactRequest` |
| Touch hooks: tap/hold, pick-up, throw, wall bump, double-tap power, idle refill | `MonkeyView.feelTouch`, `stepReactionRefill` |
| Tests | `app/src/test/.../react/ReactionEngineTest.kt` |

Rules kept: no `Random` anywhere (GoldenTraceTest), Brain off = unchanged behaviour, nothing leaves the phone except the AI request itself (API brains), memory files are never synced.

## Next: Phase 2, your own tiny reaction model

The engine stays the same; only the brain that writes lines changes. Plan: generate thousands of (situation → line) examples per character, have the owner pick and fix the best, fine-tune a small model (for example Gemma 3 270M) on a Colab GPU, convert it to `.litertlm`, and plug it in as a separate "reaction brain". That brain is faster and always in character. Lines the engine keeps in `files/reactions/` are good training material.
