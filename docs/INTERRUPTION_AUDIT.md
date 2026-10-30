# Interruption audit (v10.9)

**The bug the owner reported:** "After using a power, the character forgets the 25-minute Kids Study Pomodoro."

**The cause:** the study timer (`heart/FocusBuddy`) was a float that the overlay counted down once per frame. `MonkeyView.stepHeartAndCoPresence` had this line:

```kotlin
if (dragging || powerOn) fb.stop()
```

So **any power, and any drag, deleted the session.**

The timer also had three other problems:
- It lived only in memory, so a service restart or a killed app lost it.
- It froze whenever the frame loop stopped (Settai hidden, or the screen off).
- In Duo/Trio, every character's view counted it down, so it ran 2–3× too fast.

## The fix: a persistent owner for every timer

- **Wall-clock end times.** `FocusBuddy` now stores an end time on the wall clock (`endAtMs`), not seconds left. The remaining time is always `endAtMs - now`. Frames, powers, chats, Smash, character switches and Duo/Trio can't change it. Only start, stop and finish change it.
- **The scheduler.** `agent/ActivityScheduler` is the owner of the timer. It:
  - saves the end time in prefs (`settai_activity_timers_v109`) the moment it changes;
  - sets an **AlarmManager** alarm for the end time (exact if the user allowed exact alarms, otherwise inexact while-idle);
  - restores a running session when Settai starts again;
  - remembers a session that ended while Settai was away (`focus_done_unseen`) and celebrates it once.
- **The alarm.** `agent/ActivityAlarmReceiver` fires at the end time. If no overlay finished the session, it posts a "Study session complete!" notification and leaves the celebrate-once flag.
- **The overlay only reads the timer.** Only the main character's view checks for the end (`fb.tick()`), so Duo/Trio no longer counts it twice. While the body is busy (power, flight, drag, climb, exit), the study pose waits. About 1.2 s after the body is free (idle, walking or landing), the view puts her back at the desk: `SIT`, notebook, timer pill.
- **What it looks like:** the desk and the "📚 mm:ss" label are drawn from the shared timer, so they show again right away.

## Activities × interruptions

The interruptions checked against every activity were:
- a power (any path: menu, double-tap, AI, automatic);
- chat open and close;
- Smash start and end (the overlay is hidden);
- a character switch;
- a Solo/Duo/Trio change;
- overlay hide and show (edge tab, screen off);
- a service restart;
- touch reactions;
- the app being killed.

"Before" is v10.8. "Now" is v10.9.

### Study / focus Pomodoro (`FocusBuddy`)

**Before:**
- **A power or a drag:** stopped the session (the reported bug).
- **App killed or service restart:** the session was lost (it was only in memory).
- **Hidden, screen off, or Smash:** the countdown froze.
- **Duo/Trio:** it counted down 2–3× too fast.
- **A character switch:** the timer survived (it's a singleton), but the study pose wasn't brought back.
- **Chat or touch reactions:** the controller's `SIT` was replaced, so the pose was gone until the session ended.

**Now:**
- The wall-clock end time is saved in prefs, and an AlarmManager alarm is set for it.
- Nothing stops it except the user (or starting the breathing buddy, as before).
- The study pose comes back once the interruption is over.
- A session that ended while Settai was away is celebrated once on the next start, and a notification is posted.

### 60 s breathing buddy (`BreatheBuddy`)

**Before:**
- **A power or a drag:** cancelled it.
- **Duo/Trio:** it counted down 2–3× too fast.

**Now:**
- A power or a drag only pauses it.
- Only the main view counts it down.
- It's a 60 s guided exercise, so it is meant to pause with the body. It isn't persisted on purpose.

### 20-20-20 eye break (`DailyUtilityEngine`)

**Before:**
- The 20 s count was decremented by frames, so it froze while Settai was hidden or the screen was off.
- A restart lost it.

**Now:**
- A wall-clock end time (`eyeBreakEndsAt`), saved in prefs and restored on start.

### Reels / doomscroll guard (`DailyUtilityEngine`)

**Before:**
- The "watched N minutes" count was lost on a restart.

**Now:**
- The watch seconds are saved every 15 s with a timestamp.
- They're restored if Settai comes back within 3 minutes. That counts as the same session, and a longer gap starts a new one.

### Sticky reminders

**Before:**
- Already saved in prefs, so they survived everything.

**Now:**
- Unchanged.

### Water / medicine alerts

**Before:**
- Medicine reminders are sticky reminders (saved) or routines, and routines already use AlarmManager.
- The hourly water flag had no timer, so nothing could be lost.

**Now:**
- Unchanged and verified.
- No new nags were added, so the other characters behave exactly as before.

### Battery / overheat watchdog

**Before:**
- It has no state: it reads the battery on demand.

**Now:**
- Unchanged.

### OTP catcher

**Before:**
- 30 s wall-clock expiry, kept only in memory.

**Now:**
- On purpose, it is still not saved to disk. The code is private and expires in 30 s.

### Delivery / cab / train badge

**Before:**
- Wall-clock expiry, kept only in memory, so a restart lost it.

**Now:**
- The badge and its expiry are saved in prefs and restored if they haven't expired.

### Sleep nag (v5 `Stage`)

**Before:**
- The snooze, the last nag and the "screen on since" time were `elapsedRealtime` values in each view.
- A character switch, a Duo/Trio change or a restart reset them: the snooze was forgotten, and the 10-minute screen-on count started again.

**Now:**
- They're saved as wall-clock times through `ActivityScheduler.saveSleepNag/saveScreenOnSince` and restored by `Stage.resumeNag()` on start.
- A real screen-on still resets the count, and screen-off clears it.

### Mask Man's juggling

**Before:**
- It's worked out from the current state each frame, so it was never lost.

**Now:**
- Unchanged.

### Ongoing modes

These are campfire, comfort, neighbours (Duo/Trio) and the world clock.

**Before:**
- Already in the shared `MindHub` or saved (`world_json`, `custom_partner_*`).
- The comfort animation is a short one-off, so ending it on a power is intended.

**Now:**
- Unchanged.

### Mood and friendship

**Before:**
- Mood is in `MindHub` (saved by `flush`).
- Friendship XP is in `Fun5Store`, which commits on every change.

**Now:**
- Unchanged and verified.

### v10.9 additions

- **Powers:** there are no cooldown timers any more, so there is nothing to lose. `autoPowerGap` only paces automatic powers.
- **Rocket Girl's flight and sky show:** these are visual. A drag cancels the flight, and the show finishes on its own.

## Tests

`v109/V109Test` checks the fix:
- **`pomodoroSurvivesPowerChatSmashAndCharacterSwitch`:** starts a 25 min session, then runs a power, a double-tap power, chat open/close, Smash-style hide/show (`setPaused`) and a character switch (a new overlay view). It then checks:
  - the session is still running;
  - the remaining time equals the saved end time minus now (within 2 s);
  - the study pose (`SIT` and the notebook) comes back on the new view;
  - a fresh `FocusBuddy` can be restored from prefs, which is the process-death case.
- **`pomodoroRestoresAfterAKilledAppAndCelebratesIfItEndedMeanwhile`:** after a "killed" process, the session is re-attached from prefs. A session that ended while away is celebrated exactly once.
- **`duoDoesNotCountTheTimerTwiceAndEyeBreakIsWallClock`:** two views running frames never move the end time, and the eye break keeps its wall-clock end while Settai is hidden.
