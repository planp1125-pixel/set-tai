//! Set-Tai's offline brain (Plan A). It reads a chat message and answers in the character's voice in well
//! under a millisecond, fully on the phone: no internet, no login, nothing sent anywhere.
//!
//! R1: rules for understanding + a starter set of lines. R2 swaps the rules for a small trained model and adds
//! thousands of lines per character; the API below stays the same.

pub mod reply;
pub mod understand;
mod jni_api;

pub use understand::{understand, Intent, Mood, Understanding};

pub const VERSION: &str = concat!("settai-mind ", env!("CARGO_PKG_VERSION"), " (R1 rules)");

/// Understand [text] and answer it as [character_id] / [character_name]. Returns (understanding, reply line).
pub fn respond(character_id: &str, character_name: &str, you: &str, text: &str, seed: u64, recent: &[u32]) -> (Understanding, String) {
    let u = understand(text);
    let line = reply::reply(character_id, character_name, you, u.intent, seed, recent);
    (u, line)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Instant;

    fn intent(t: &str) -> Intent { understand(t).intent }

    #[test]
    fn understands_english_tanglish_tamil_hindi() {
        assert_eq!(intent("hi"), Intent::Greeting);
        assert_eq!(intent("Hiiii da!"), Intent::Greeting);
        assert_eq!(intent("vanakkam"), Intent::Greeting);
        assert_eq!(intent("வணக்கம்"), Intent::Greeting);
        assert_eq!(intent("नमस्ते"), Intent::Greeting);
        assert_eq!(intent("epdi irukka?"), Intent::HowAreYou);
        assert_eq!(intent("How are you?"), Intent::HowAreYou);
        assert_eq!(intent("tell me a joke"), Intent::Joke);
        assert_eq!(intent("I feel so sad today 😢"), Intent::Sad);
        assert_eq!(intent("bore adikudhu"), Intent::Bored);
        assert_eq!(intent("fly da!"), Intent::Fly);
        assert_eq!(intent("show your superpower"), Intent::Power);
        assert_eq!(intent("hahaha"), Intent::Laugh);
        assert_eq!(intent("nee yaaru?"), Intent::WhoAreYou);
        assert_eq!(intent("thank you so much"), Intent::Thanks);
        assert_eq!(intent("pasikudhu"), Intent::Hungry);
        assert_eq!(intent("what is the capital of France?"), Intent::Question);
        assert_eq!(intent("the cat sat on the mat"), Intent::Unknown);
    }

    #[test]
    fn mood_act_and_gesture_follow_the_intent() {
        let u = understand("I'm so sad 😭");
        assert_eq!((u.mood, u.act, u.gesture), (Mood::Sad, "sad", "sad"));
        let u = understand("fly!");
        assert!(u.power);
        assert_eq!(u.act, "fly");
        assert_eq!(understand("hello").encode().split('|').count(), 6);
    }

    #[test]
    fn every_intent_has_lines_and_blanks_are_filled() {
        for i in Intent::ALL {
            assert!(!reply::candidates("anyone", i).is_empty(), "{:?}", i);
        }
        let (_, line) = respond("devil", "Tutty", "Mani", "who are you?", 7, &[]);
        assert!(!line.contains('{'), "{}", line);
        assert!(line.contains("Tutty") || !line.contains("{name}"));
    }

    #[test]
    fn a_future_character_still_answers() {
        let (u, line) = respond("dragon_2027", "Dragu", "", "hi", 3, &[]);
        assert_eq!(u.intent, Intent::Greeting);
        assert!(!line.is_empty() && !line.contains("{you}") && !line.contains("{name}"));
    }

    #[test]
    fn recent_lines_are_not_repeated() {
        let mut recent = Vec::new();
        let n = reply::candidates("devil", Intent::Joke).len();
        for s in 0..n as u64 {
            let line = reply::reply("devil", "Tutty", "you", Intent::Joke, s * 7919, &recent);
            assert!(!recent.contains(&reply::line_hash(&line)), "repeated: {}", line);
            recent.push(reply::line_hash(&line));
        }
    }

    #[test]
    fn fast_enough_to_feel_instant() {
        let msgs = ["hi da", "tell me a joke!", "I'm so bored", "epdi irukka?", "what's the weather like tomorrow in Chennai?", "😂😂"];
        let rounds = 2000;
        let t = Instant::now();
        for r in 0..rounds {
            let m = msgs[r % msgs.len()];
            let _ = respond("devil", "Tutty", "Mani", m, r as u64, &[1, 2, 3]);
        }
        let per = t.elapsed().as_secs_f64() * 1000.0 / rounds as f64;
        // the budget is 5 ms on the phone; a debug test build on a CI machine is still far under it
        assert!(per < 2.0, "{} ms per message", per);
    }
}
