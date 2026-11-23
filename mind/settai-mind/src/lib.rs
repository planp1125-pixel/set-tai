//! Set-Tai's offline brain (Plan A). It reads a chat message and answers in the character's voice in well
//! under a millisecond, fully on the phone: no internet, no login, nothing sent anywhere.
//!
//! R1: rules for understanding + a starter set of lines. R2: a small trained intent model (rules as the fallback)
//! and lines per character (`lines/<id>.tsv`); the API below stays the same.

pub mod model;
pub mod reply;
pub mod understand;
mod jni_api;

pub use understand::{understand, Intent, Mood, Understanding};

pub const VERSION: &str = concat!("settai-mind ", env!("CARGO_PKG_VERSION"), " (R2 model)");

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

    // ---------------------------------------------------------------- R2

    const HELDOUT: &str = include_str!("../../data/heldout.tsv");
    const TRAIN: &str = include_str!("../../data/train.tsv");

    fn tsv(s: &str) -> Vec<(&str, &str)> {
        s.lines().filter(|l| !l.starts_with('#') && !l.trim().is_empty()).filter_map(|l| l.split_once('\t')).collect()
    }

    #[test]
    fn the_r2_model_is_built_in() {
        assert!(VERSION.contains("(R2 model)"), "{}", VERSION);
        let m = model::builtin().expect("model/intent.bin missing or unreadable: run `cargo run --release -p settai-train`");
        assert_eq!(m.classes.len(), Intent::ALL.len());
        assert!(model::version().starts_with("r2-intent"), "{}", model::version());
        let size = std::fs::metadata(concat!(env!("CARGO_MANIFEST_DIR"), "/model/intent.bin")).unwrap().len();
        assert!(size < 2 * 1024 * 1024, "model is {} bytes", size);
    }

    #[test]
    fn heldout_accuracy_is_at_least_92_percent() {
        let m = model::builtin().expect("model");
        let rows = tsv(HELDOUT);
        assert!(rows.len() >= 1000, "only {} held-out rows", rows.len());
        let mut per: std::collections::BTreeMap<&str, (usize, usize)> = Default::default();
        for (want, text) in &rows {
            let e = per.entry(*want).or_default();
            e.1 += 1;
            if m.predict(text).0.name() == *want {
                e.0 += 1;
            }
        }
        let ok: usize = per.values().map(|p| p.0).sum();
        let acc = ok as f64 / rows.len() as f64;
        for (k, (o, n)) in &per {
            eprintln!("{:<12} {:>5.1}% ({}/{})", k, 100.0 * *o as f64 / *n as f64, o, n);
        }
        eprintln!("overall {:.1}%", acc * 100.0);
        assert_eq!(per.len(), Intent::ALL.len(), "every intent must be in the held-out set");
        assert!(acc >= 0.92, "held-out accuracy {:.3}", acc);
        // no single intent may be far behind the rest
        for (k, (o, n)) in &per {
            assert!(*o as f64 / *n as f64 >= 0.80, "{} only {}/{}", k, o, n);
        }
        // held-out really is held out
        let train: std::collections::HashSet<String> = tsv(TRAIN).iter().map(|(_, t)| model::model_text(t)).collect();
        assert!(rows.iter().all(|(_, t)| !train.contains(&model::model_text(t))));
    }

    #[test]
    fn tamil_hindi_tanglish_samples_classify_correctly() {
        // hand-written sentences that are NOT in the training data (checked below)
        let cases = [
            ("enna da panra", Intent::HowAreYou),
            ("eppadi iruka nanba", Intent::HowAreYou),
            ("nee yaaru sollu", Intent::WhoAreYou),
            ("oru nalla joke sollu machan", Intent::Joke),
            ("naan romba sogama iruken", Intent::Sad),
            ("enakku kovam varudhu da", Intent::Angry),
            ("romba bore adikudhu inniki", Intent::Bored),
            ("unna enakku romba pidikum", Intent::Love),
            ("nee oru loosu da", Intent::Insult),
            ("enakku pasikudhu amma", Intent::Hungry),
            ("thookam varudhu, naan thoonga poren", Intent::Sleepy),
            ("konjam parandhu kaatu", Intent::Fly),
            ("kuthu dance podu machan", Intent::Dance),
            ("enakku help pannu da", Intent::Help),
            ("naan bus la poitu irukken", Intent::Unknown),
            ("நீங்க எப்படி இருக்கீங்க?", Intent::HowAreYou),
            ("எனக்கு ரொம்ப பசிக்குது", Intent::Hungry),
            ("உன் பேர் என்ன?", Intent::WhoAreYou),
            ("ரொம்ப நன்றி நண்பா", Intent::Thanks),
            ("எனக்கு தூக்கம் வருது", Intent::Sleepy),
            ("டான்ஸ் ஆடு டா", Intent::Dance),
            ("நான் ரொம்ப சோகமா இருக்கேன்", Intent::Sad),
            ("आप कैसे हो?", Intent::HowAreYou),
            ("यार मुझे अभी भूख लगी है", Intent::Hungry),
            ("दोस्त तुम्हारा नाम बताओ?", Intent::WhoAreYou),
            ("बहुत बहुत धन्यवाद", Intent::Thanks),
            ("मुझे नींद आ रही है", Intent::Sleepy),
            ("मुझे बहुत गुस्सा आ रहा है", Intent::Angry),
            ("एक मज़ेदार जोक सुनाओ", Intent::Joke),
            ("yaar bahut bore ho raha hai", Intent::Bored),
            ("mujhe ek joke sunao na", Intent::Joke),
            ("bhai dance karke dikhao", Intent::Dance),
            ("thank youuuu so much for today", Intent::Thanks),
            ("im sooo sleepyyy", Intent::Sleepy),
            ("show me ur superpower", Intent::Power),
            ("i'm feeling really lonely today", Intent::Sad),
        ];
        let train: std::collections::HashSet<String> = tsv(TRAIN).iter().map(|(_, t)| model::model_text(t)).collect();
        let seen: Vec<&str> = cases.iter().map(|c| c.0).filter(|t| train.contains(&model::model_text(t))).collect();
        assert!(seen.is_empty(), "in the training data, pick fresh sentences: {:?}", seen);
        let mut wrong = Vec::new();
        for (t, want) in cases {
            let got = understand(t).intent;
            if got != want {
                wrong.push(format!("{:?}: want {:?}, got {:?}", t, want, got));
            }
        }
        assert!(wrong.is_empty(), "{}", wrong.join("\n"));
    }

    #[test]
    fn unsure_or_missing_model_falls_back_to_the_rules() {
        assert_eq!(understand::MODEL_MIN_CONFIDENCE, 0.45);
        assert_eq!(understand::understand_rules("vanakkam").0, Intent::Greeting);
        assert!(model::Model::parse(b"not a model").is_none());
        assert!(model::Model::parse(&[]).is_none());
        let m = model::builtin().expect("model");
        for t in ["qwzx vbnm", "hmm ok but no", "the the the", "kk lol bye", "my mom is making dosa", "7"] {
            let (_, p) = m.predict(t);
            let u = understand(t);
            if p < understand::MODEL_MIN_CONFIDENCE {
                assert_eq!(u.intent, understand::understand_rules(t).0, "{:?}", t);
            } else {
                assert_eq!(u.intent, m.predict(t).0, "{:?}", t);
            }
            assert_eq!(u.encode().split('|').count(), 6);
        }
    }

    #[test]
    fn every_character_has_its_own_lines_for_every_intent() {
        let ids = ["devil", "angel", "monkey", "superboy", "armour", "mask", "billa", "chittu", "gajju", "rocketgirl",
            "hollowman", "surfer", "skater"];
        assert_eq!(reply::CHARACTER_LINES.len(), ids.len());
        for id in ids {
            for i in Intent::ALL {
                let own = reply::own_lines(id, i);
                assert!(own.len() >= 6, "{} has {} lines for {}", id, own.len(), i.name());
                let mut seen = std::collections::HashSet::new();
                for l in &own {
                    assert!(seen.insert(*l), "{} repeats {:?}", id, l);
                    assert!(l.chars().count() <= 120, "{} line too long: {:?}", id, l);
                    let rest = l.replace("{you}", "").replace("{name}", "");
                    assert!(!rest.contains('{') && !rest.contains('}'), "{} unknown blank in {:?}", id, l);
                }
                assert_eq!(reply::candidates(id, i), own);
            }
            // it introduces itself by name
            assert!(reply::own_lines(id, Intent::WhoAreYou).iter().all(|l| l.contains("{name}")), "{}", id);
        }
        // unknown / future / custom characters keep the general lines
        for i in Intent::ALL {
            assert!(reply::own_lines("custom_1", i).is_empty());
            assert_eq!(reply::candidates("custom_1", i), reply::general_lines(i));
        }
        let (_, line) = respond("billa", "Billa", "Mani", "nee yaaru?", 1, &[]);
        assert!(line.contains("Billa"), "{}", line);
    }

    #[test]
    fn under_a_millisecond_per_message() {
        let msgs = ["hi da", "tell me a joke!", "I'm so bored", "epdi irukka?", "what's the weather like tomorrow in Chennai?",
            "😂😂", "मुझे बहुत भूख लगी है", "நீங்க எப்படி இருக்கீங்க?", "ok so today at office my manager said the meeting moved to friday"];
        let _ = respond("devil", "Tutty", "Mani", "warm up", 0, &[]);
        let rounds = 5000;
        let t = Instant::now();
        for r in 0..rounds {
            let _ = respond("skater", "Rollie", "Mani", msgs[r % msgs.len()], r as u64, &[1, 2, 3]);
        }
        let per = t.elapsed().as_secs_f64() * 1000.0 / rounds as f64;
        eprintln!("{:.4} ms per message", per);
        // release (what ships) must be under 1 ms; an unoptimised debug build gets more room
        let budget = if cfg!(debug_assertions) { 5.0 } else { 1.0 };
        assert!(per < budget, "{} ms per message", per);
    }
}
