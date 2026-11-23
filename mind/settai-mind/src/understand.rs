//! Reading a message: what the person wants (intent), how they feel (mood), and what the body should do.
//!
//! R2: a small trained model (see `model.rs`) picks the intent; when it is unsure (probability below
//! [MODEL_MIN_CONFIDENCE]) or missing, the R1 keyword rules (English, Tanglish, Tamil, Hindi) decide.
//! Mood, act and gesture always follow the intent, so the app never has to change.

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Intent {
    Greeting,
    Goodbye,
    Thanks,
    HowAreYou,
    WhoAreYou,
    Joke,
    Sad,
    Angry,
    Bored,
    Love,
    Insult,
    Hungry,
    Sleepy,
    Fly,
    Power,
    Dance,
    Laugh,
    Yes,
    No,
    Help,
    Question,
    Unknown,
}

impl Intent {
    pub const ALL: [Intent; 22] = [
        Intent::Greeting, Intent::Goodbye, Intent::Thanks, Intent::HowAreYou, Intent::WhoAreYou, Intent::Joke,
        Intent::Sad, Intent::Angry, Intent::Bored, Intent::Love, Intent::Insult, Intent::Hungry, Intent::Sleepy,
        Intent::Fly, Intent::Power, Intent::Dance, Intent::Laugh, Intent::Yes, Intent::No, Intent::Help,
        Intent::Question, Intent::Unknown,
    ];

    pub fn name(self) -> &'static str {
        match self {
            Intent::Greeting => "greeting",
            Intent::Goodbye => "goodbye",
            Intent::Thanks => "thanks",
            Intent::HowAreYou => "how_are_you",
            Intent::WhoAreYou => "who_are_you",
            Intent::Joke => "joke",
            Intent::Sad => "sad",
            Intent::Angry => "angry",
            Intent::Bored => "bored",
            Intent::Love => "love",
            Intent::Insult => "insult",
            Intent::Hungry => "hungry",
            Intent::Sleepy => "sleepy",
            Intent::Fly => "fly",
            Intent::Power => "power",
            Intent::Dance => "dance",
            Intent::Laugh => "laugh",
            Intent::Yes => "yes",
            Intent::No => "no",
            Intent::Help => "help",
            Intent::Question => "question",
            Intent::Unknown => "unknown",
        }
    }

    pub fn from_name(s: &str) -> Option<Intent> {
        Intent::ALL.iter().copied().find(|i| i.name() == s)
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Mood {
    Happy,
    Sad,
    Angry,
    Bored,
    Excited,
    Calm,
}

impl Mood {
    pub fn name(self) -> &'static str {
        match self {
            Mood::Happy => "happy",
            Mood::Sad => "sad",
            Mood::Angry => "angry",
            Mood::Bored => "bored",
            Mood::Excited => "excited",
            Mood::Calm => "calm",
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct Understanding {
    pub intent: Intent,
    pub mood: Mood,
    /// A body action the app already knows (BodyAction tags: dance, jump, wave, giggle, angry, sleep, hide, spin, think, happy, sad, fly).
    pub act: &'static str,
    /// How the hands move while it talks (TalkGesture: explain, ask, exclaim, self, laugh, sad, think).
    pub gesture: &'static str,
    /// The person asked for a superpower.
    pub power: bool,
    /// 0..1: how sure the rules are (0 = nothing matched).
    pub confidence: f32,
}

impl Understanding {
    /// "intent|mood|act|gesture|power|confidence" for the app.
    pub fn encode(&self) -> String {
        format!(
            "{}|{}|{}|{}|{}|{:.2}",
            self.intent.name(),
            self.mood.name(),
            self.act,
            self.gesture,
            if self.power { 1 } else { 0 },
            self.confidence
        )
    }
}

/// Words and phrases for each intent. A phrase (with a space) matches anywhere; a word matches a whole token
/// or, when it ends in '*', any token starting with it.
const RULES: &[(Intent, &[&str])] = &[
    (Intent::Greeting, &["hi", "hii*", "hello", "helo", "hey", "heyy*", "yo", "vanakkam", "namaste", "namaskar", "good morning", "good evening", "gm", "morning", "hola", "sup", "wassup", "வணக்கம்", "नमस्ते", "hai"]),
    (Intent::Goodbye, &["bye", "byee*", "goodbye", "good night", "gn", "goodnight", "see you", "see ya", "cya", "tata", "poitu varen", "povom", "alvida", "later", "ttyl", "போயிட்டு"]),
    (Intent::Thanks, &["thanks", "thank*", "thx", "tnx", "ty", "nandri", "dhanyavad", "shukriya", "நன்றி", "धन्यवाद"]),
    (Intent::HowAreYou, &["how are you", "how r u", "how ru", "hru", "epdi irukka", "eppadi irukka", "epdi iruka", "how is it going", "whats up", "what's up", "kaise ho", "kya haal", "nalla irukkiya", "எப்படி இருக்க", "कैसे हो"]),
    (Intent::WhoAreYou, &["who are you", "who r u", "your name", "ur name", "nee yaaru", "nee yaru", "un per", "un peru", "tum kaun", "kaun ho", "what are you", "யார் நீ"]),
    (Intent::Joke, &["joke", "jokes", "funny", "make me laugh", "comedy", "kadi", "mokka", "something funny", "chutkula", "மொக்க"]),
    (Intent::Sad, &["sad", "upset", "cry", "crying", "depressed", "lonely", "alone", "hurt", "miss you", "heartbroken", "feel bad", "feeling low", "kashtama", "kavalai", "azhuga*", "dukhi", "udaas", "😢", "😭", "💔", "😔", "சோகம்", "दुखी"]),
    (Intent::Angry, &["angry", "mad", "furious", "annoyed", "irritat*", "kovam", "kobam", "gussa", "hate this", "fed up", "😡", "🤬", "கோபம்", "गुस्सा"]),
    (Intent::Bored, &["bored", "boring", "nothing to do", "bore", "bore adikudhu", "bore adikkuthu", "kuch nahi", "😴 bored", "போர்"]),
    (Intent::Love, &["love you", "luv you", "love u", "ily", "you are cute", "ur cute", "you're cute", "cute", "best friend", "my friend", "i like you", "pyaar", "kadhal", "chellam", "❤", "❤️", "😍", "🥰", "💕"]),
    (Intent::Insult, &["stupid", "idiot", "dumb", "useless", "shut up", "you suck", "loser", "waste fellow", "loosu", "mental", "bewakoof", "pagal"]),
    (Intent::Hungry, &["hungry", "food", "eat", "eating", "lunch", "dinner", "breakfast", "pasikudhu", "pasikuthu", "saapadu", "sapadu", "biryani", "dosa", "idli", "bhook", "khana", "பசி", "भूख"]),
    (Intent::Sleepy, &["sleepy", "tired", "sleep", "nap", "thookam", "thookkam", "thoongu*", "neend", "so tired", "exhausted", "தூக்கம்"]),
    (Intent::Fly, &["fly", "flying", "fly around", "take off", "parakku", "parandhu", "udd", "udo", "rocket"]),
    (Intent::Power, &["power", "powers", "superpower", "super power", "use power", "show power", "special move", "fireball", "sakthi", "shakti"]),
    (Intent::Dance, &["dance", "dancing", "naach", "kuthu", "aadu", "aatam", "aattam", "nachna", "groove"]),
    (Intent::Laugh, &["haha*", "hehe*", "lol*", "lmao", "rofl", "😂", "🤣", "😆", "hihi*"]),
    (Intent::Yes, &["yes", "yeah", "yep", "yup", "ok", "okay", "sure", "aama", "aamam", "seri", "sari", "haan", "ha", "theek hai"]),
    (Intent::No, &["no", "nope", "nah", "illa", "vendaam", "venam", "venaam", "nahi", "nahin"]),
    (Intent::Help, &["help", "help me", "can you help", "udhavi", "madad", "how do i", "how to", "teach me"]),
];

/// Question words (a message that starts with one, or ends with '?', is a question).
const QUESTION_STARTS: &[&str] = &[
    "what", "why", "how", "when", "where", "who", "which", "can", "could", "will", "would", "do", "does", "is", "are",
    "enna", "yen", "en", "epdi", "eppadi", "eppo", "enga", "yaaru", "evlo", "kya", "kyu", "kyun", "kaise", "kab", "kahan",
];

/// Lowercase, keep letters/digits/emoji, turn punctuation into spaces (Tamil and Hindi letters stay as they are).
pub fn normalize(text: &str) -> String {
    let mut out = String::with_capacity(text.len() + 2);
    out.push(' ');
    for ch in text.chars() {
        let ch = if ch == '\u{2019}' { '\'' } else { ch };
        // Tamil and Hindi vowel signs are marks, not letters: keep everything from U+0900 up (and emoji)
        if ch.is_alphanumeric() || (ch as u32) >= 0x0900 {
            for l in ch.to_lowercase() {
                out.push(l);
            }
        } else if ch == '\'' {
            out.push('\'');
        } else {
            out.push(' ');
        }
    }
    // squeeze spaces
    let mut squeezed = String::with_capacity(out.len() + 1);
    let mut last_space = false;
    for ch in out.chars() {
        let sp = ch == ' ';
        if !(sp && last_space) {
            squeezed.push(ch);
        }
        last_space = sp;
    }
    if !squeezed.ends_with(' ') {
        squeezed.push(' ');
    }
    squeezed
}

fn matches(norm: &str, tokens: &[&str], pat: &str) -> bool {
    if pat.contains(' ') {
        let mut p = String::with_capacity(pat.len() + 2);
        p.push(' ');
        p.push_str(pat);
        p.push(' ');
        return norm.contains(&p);
    }
    if let Some(prefix) = pat.strip_suffix('*') {
        return tokens.iter().any(|t| t.starts_with(prefix));
    }
    // emoji and non-Latin words may sit inside a token ("hi😂"), so they match anywhere
    if pat.chars().any(|c| (c as u32) >= 0x0900) {
        return norm.contains(pat);
    }
    tokens.iter().any(|t| *t == pat)
}

/// Below this probability the model's answer is not trusted and the rules decide.
pub const MODEL_MIN_CONFIDENCE: f32 = 0.45;

/// R1: the keyword rules alone -> (intent, confidence 0..1).
pub fn understand_rules(text: &str) -> (Intent, f32) {
    let norm = normalize(text);
    let tokens: Vec<&str> = norm.split(' ').filter(|t| !t.is_empty()).collect();
    let is_question = text.trim().ends_with('?')
        || tokens.first().map(|f| QUESTION_STARTS.contains(f)).unwrap_or(false);
    let mut best = Intent::Unknown;
    let mut best_score = 0.0f32;
    for (intent, pats) in RULES {
        let mut score = 0.0f32;
        for p in *pats {
            if matches(&norm, &tokens, p) {
                // longer phrases are stronger evidence than one short word
                score += 1.0 + 0.15 * (p.chars().count().min(14) as f32);
            }
        }
        // short words like "ok", "ha", "no" only count as the answer when they are most of the message
        if matches!(intent, Intent::Yes | Intent::No | Intent::Laugh) && tokens.len() > 4 {
            score *= 0.4;
        }
        if score > best_score {
            best_score = score;
            best = *intent;
        }
    }
    if best == Intent::Unknown && is_question {
        best = Intent::Question;
        best_score = 0.8;
    }
    let confidence = if best_score <= 0.0 { 0.0 } else { (best_score / 3.0).min(1.0) };
    (best, confidence)
}

/// Read one message. Pure and fast (well under a millisecond for a chat line).
pub fn understand(text: &str) -> Understanding {
    let norm = normalize(text);
    let tokens: Vec<&str> = norm.split(' ').filter(|t| !t.is_empty()).collect();
    let trimmed = text.trim();
    let is_question = trimmed.ends_with('?')
        || tokens.first().map(|f| QUESTION_STARTS.contains(f)).unwrap_or(false);
    let exclaim = trimmed.ends_with('!');

    let from_model = crate::model::builtin()
        .map(|m| m.predict(text))
        .filter(|(_, p)| *p >= MODEL_MIN_CONFIDENCE);
    let (best, confidence) = match from_model {
        Some(found) => found,
        None => understand_rules(text),
    };

    let mood = match best {
        Intent::Sad => Mood::Sad,
        Intent::Angry | Intent::Insult => Mood::Angry,
        Intent::Bored => Mood::Bored,
        Intent::Sleepy => Mood::Calm,
        Intent::Laugh | Intent::Love | Intent::Joke | Intent::Dance | Intent::Fly | Intent::Power => Mood::Excited,
        Intent::Greeting | Intent::Thanks | Intent::Yes => Mood::Happy,
        _ if exclaim => Mood::Excited,
        _ => Mood::Calm,
    };
    let act = match best {
        Intent::Greeting | Intent::Goodbye => "wave",
        Intent::Thanks | Intent::Love | Intent::Yes => "happy",
        Intent::Joke | Intent::Laugh => "giggle",
        Intent::Sad => "sad",
        Intent::Angry | Intent::Insult => "angry",
        Intent::Bored => "spin",
        Intent::Sleepy => "sleep",
        Intent::Fly | Intent::Power => "fly",
        Intent::Dance => "dance",
        Intent::No => "hide",
        Intent::Hungry => "jump",
        Intent::Question | Intent::Help | Intent::WhoAreYou | Intent::HowAreYou => "think",
        Intent::Unknown => "think",
    };
    let gesture = match best {
        Intent::Laugh | Intent::Joke => "laugh",
        Intent::Sad => "sad",
        Intent::WhoAreYou => "self",
        Intent::Question | Intent::Help => "think",
        Intent::Love | Intent::Greeting | Intent::Dance | Intent::Fly | Intent::Power => "exclaim",
        _ if is_question => "ask",
        _ => "explain",
    };
    Understanding { intent: best, mood, act, gesture, power: best == Intent::Power || best == Intent::Fly, confidence }
}
