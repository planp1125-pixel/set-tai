//! The training data generator. Everything here is hand-written and meant to be read by a person:
//!
//! 1. `mind/data/seed_<intent>.tsv`: real-looking chat lines, one per row ("intent<TAB>text").
//! 2. [SPECS]: per intent, short core phrases (with `{slot}`s filled from the word lists below) and emoji.
//! 3. [decorate] + [noise]: names / "da" / "bro", fillers, emoji, punctuation, and chat typos
//!    ("hiii", "pleaseee", dropped vowels, swapped letters, CAPS) using a fixed-seed random generator,
//!    so `cargo run -p settai-train` makes the same file every time.
//!
//! Examples are de-duplicated on the text the model actually reads (lowercase, punctuation gone, "hiiii" = "hii").
//! Each example remembers its `family` (the phrase before decoration and typos):
//! - [is_heldout]: the main 15% test set. Generated lines are split one by one (a known phrase with new names,
//!   typos, emoji); a hand-written seed sentence and its noisy copies always land on the same side.
//! - [is_heldout_family]: a stricter check (whole phrases never seen in training), reported but not the target.

use std::collections::HashSet;
use std::path::Path;

/// One example.
#[derive(Clone, Debug)]
pub struct Example {
    pub intent: &'static str,
    pub text: String,
    pub family: String,
    pub seed: bool,
}

/// SplitMix64: tiny, fixed, no dependency.
pub struct Rng(u64);

impl Rng {
    pub fn new(seed: u64) -> Rng { Rng(seed) }
    pub fn next(&mut self) -> u64 {
        self.0 = self.0.wrapping_add(0x9E37_79B9_7F4A_7C15);
        let mut z = self.0;
        z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
        z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
        z ^ (z >> 31)
    }
    pub fn below(&mut self, n: usize) -> usize { (self.next() % n.max(1) as u64) as usize }
    pub fn chance(&mut self, p: f64) -> bool { (self.next() >> 11) as f64 / (1u64 << 53) as f64 <= p }
    pub fn pick<'a, T>(&mut self, xs: &'a [T]) -> &'a T { &xs[self.below(xs.len())] }
}

// ---------------------------------------------------------------- word lists

/// What people call the buddy or each other in chat (added before or after a message).
const ADDRESS: &[&str] = &[
    "da", "di", "dei", "bro", "machan", "macha", "dude", "buddy", "yaar", "bhai", "ji", "re", "dear", "pa", "ma",
    "pogo", "tutty", "zoom", "bolt", "masko", "billa", "chittu", "gajju", "skye", "echo", "wavy", "rollie", "angel",
    "monkey", "chellam", "kutty", "boss", "thala", "anna", "akka",
];

/// Neutral starts that do not change the meaning.
const OPENERS: &[&str] = &["umm", "hmm", "so", "arey", "oye", "listen", "btw", "uff", "aiyo", "dei"];

/// Asking softly (only for requests).
const PLEASE: &[&str] = &["pls", "plz", "please", "na", "naa", "zara", "konjam", "jaldi", "now", "ippo", "abhi", "once"];

const FOOD: &[&str] = &[
    "biryani", "dosa", "idli", "pizza", "parotta", "maggi", "samosa", "chai", "pani puri", "pongal", "vada", "noodles",
    "burger", "ice cream", "chocolate", "paneer", "rajma chawal", "curd rice", "sambar rice", "momos", "banana", "laddoo",
];

const TASK: &[&str] = &[
    "fix my wifi", "write an email", "study for my exam", "set an alarm", "do my homework", "learn english",
    "make a resume", "use this app", "change the wallpaper", "save battery", "solve this maths sum", "plan my day",
    "reply to my boss", "focus on work", "remember my tasks", "find my notes", "talk to my friend", "start exercise",
];

const THING: &[&str] = &[
    "gravity", "a black hole", "the internet", "photosynthesis", "a rainbow", "cricket", "python", "bitcoin",
    "the moon", "a volcano", "democracy", "chess", "electricity", "a tsunami", "an eclipse", "the sun", "dna",
    "wifi", "a rocket engine", "the ocean", "inflation", "ai", "a computer", "the taj mahal",
];

/// People in everyday statements (the "unknown" intent: news, not a request).
const WHO: &[&str] = &[
    "my mom", "my dad", "my sister", "my brother", "my friend", "my boss", "my teacher", "my neighbour", "my cousin",
    "my roommate", "the driver", "my uncle", "grandma", "the delivery guy", "my manager", "my classmate",
];

/// Things those people did (no feelings, no requests).
const DID: &[&str] = &[
    "is cooking", "went to the bank", "is on a call", "is watching cricket", "bought a new phone", "called me",
    "is coming home late", "fixed the tap", "painted the wall", "is driving", "sent me a photo", "is at work",
    "booked tickets", "moved to pune", "planted a tree", "is reading the newspaper", "washed the car",
    "came to visit", "got a new job", "is shopping",
];

const PLACE: &[&str] = &[
    "office", "home", "college", "school", "the bus", "the metro", "my room", "the shop", "the gym", "chennai",
    "madurai", "delhi", "mumbai", "the hospital", "the market", "my aunt's house", "the station", "tuition",
];

// ---------------------------------------------------------------- per-intent phrases

pub struct Spec {
    pub intent: &'static str,
    /// Core phrases; `{food}`, `{task}`, `{thing}`, `{place}` are filled from the lists above.
    pub cores: &'static [&'static str],
    pub emoji: &'static [&'static str],
    /// A request: may get "pls", "na", "please".
    pub request: bool,
}

pub const SPECS: &[Spec] = &[
    Spec { intent: "greeting", request: false, emoji: &["👋", "😄", "🙂", "😊", "✨", "🙏"], cores: &[
        "hi", "hii", "hello", "helo", "hey", "heyy", "hey there", "hello there", "yo", "sup", "wassup", "hai",
        "good morning", "gud mrng", "morning", "good evening", "good afternoon", "gm", "hola", "namaste", "namaskar",
        "vanakkam", "vanakam", "hello hello", "hi hi", "hey buddy", "hi friend", "hello my friend", "oh hi",
        "hi again", "hey im back", "im back", "back again", "ayy", "heyyo", "hiya", "greetings",
        "வணக்கம்", "ஹாய்", "ஹலோ", "காலை வணக்கம்", "नमस्ते", "हैलो", "नमस्कार", "सुप्रभात", "राम राम",
        "hello ji", "namaste ji", "salaam", "vanakkam vanakkam", "hey whats new",
    ] },
    Spec { intent: "goodbye", request: false, emoji: &["👋", "😴", "🌙", "🙂", "💤"], cores: &[
        "bye", "byee", "bye bye", "goodbye", "good night", "gn", "goodnight", "see you", "see ya", "cya",
        "see you tomorrow", "see you later", "talk later", "ttyl", "later", "catch you later", "tata", "ta ta",
        "i have to go", "gotta go", "got to go", "im leaving", "leaving now", "going now", "i'll go now",
        "poitu varen", "poyitu varen", "naan kelamburen", "kelambaren", "apram pesalam", "naalaiku pesalam",
        "alvida", "phir milenge", "chalta hoon", "chalti hoon", "baad mein baat karte", "kal milte",
        "போயிட்டு வரேன்", "பை", "அப்புறம் பேசலாம்", "இரவு வணக்கம்", "अलविदा", "फिर मिलेंगे", "शुभ रात्रि",
        "बाय", "चलता हूँ", "sweet dreams", "night night", "signing off", "logging off",
    ] },
    Spec { intent: "thanks", request: false, emoji: &["🙏", "😊", "💛", "❤️", "🥰"], cores: &[
        "thanks", "thank you", "thank u", "thx", "tnx", "ty", "tysm", "thanks a lot", "thank you so much",
        "many thanks", "thanks buddy", "thanks for helping", "thanks for that", "that helped thanks",
        "you are the best thanks", "much appreciated", "thanks yaar", "nandri", "romba nandri", "rombha thanks",
        "dhanyavad", "dhanyawad", "shukriya", "bahut shukriya", "thank you ji", "nandri da", "thanks machan",
        "நன்றி", "ரொம்ப நன்றி", "மிக்க நன்றி", "धन्यवाद", "शुक्रिया", "बहुत धन्यवाद", "thanks for listening",
        "thank you for being here", "thanks for the joke", "cheers mate", "grateful", "thanku",
    ] },
    Spec { intent: "how_are_you", request: false, emoji: &["🙂", "😊", "🤔", "😄"], cores: &[
        "how are you", "how r u", "how ru", "hru", "how are u", "how are you doing", "how you doing",
        "how is it going", "hows it going", "how's life", "how is life", "whats up", "what's up", "wats up",
        "how was your day", "are you ok", "you good", "all good", "how do you feel", "how are things",
        "epdi irukka", "eppadi irukka", "epdi iruka", "epdi irukeenga", "nalla irukkiya", "nallaa irukkiya",
        "sowkiyama", "enna panra", "enna panre", "enna pannitu iruka", "kaise ho", "kya haal hai", "kaisa hai",
        "kaise hain aap", "sab theek", "kya chal raha hai", "kya scene hai", "kya kar rahe ho", "kya kar rahi ho",
        "kya ho raha hai", "क्या कर रहे हो",
        "எப்படி இருக்க", "எப்படி இருக்கீங்க", "நல்லா இருக்கியா", "என்ன பண்ற", "சௌக்கியமா",
        "कैसे हो", "क्या हाल है", "कैसी हो", "सब ठीक है", "क्या चल रहा है",
    ] },
    Spec { intent: "who_are_you", request: false, emoji: &["🤔", "👀", "🙂", "❓"], cores: &[
        "who are you", "who r u", "who ru", "what is your name", "whats your name", "ur name", "your name",
        "what are you", "tell me about yourself", "introduce yourself", "who is this", "are you a robot",
        "are you real", "are you human", "what should i call you", "do you have a name", "what can you do",
        "nee yaaru", "nee yaru", "neenga yaaru", "un per enna", "un peru enna", "unga per enna", "nee enna",
        "tum kaun ho", "aap kaun ho", "kaun ho tum", "tumhara naam kya hai", "aapka naam kya hai", "tum kya ho",
        "நீ யாரு", "உன் பெயர் என்ன", "உன் பேரு என்ன", "நீங்க யாரு", "तुम कौन हो", "आप कौन हैं",
        "तुम्हारा नाम क्या है", "आपका नाम क्या है", "tell me who you are", "what kind of creature are you",
        "अपना नाम बताओ", "नाम बताओ", "apna naam batao", "naam batao", "un per sollu", "unga per sollu",
        "உன் பேர் சொல்லு", "tell me your name",
    ] },
    Spec { intent: "joke", request: true, emoji: &["😂", "😄", "🤣", "😜"], cores: &[
        "tell me a joke", "tell a joke", "joke", "jokes", "say a joke", "one joke", "another joke", "a funny joke",
        "make me laugh", "say something funny", "something funny", "crack a joke", "got any jokes", "know any jokes",
        "tell me something funny", "funny story", "be funny", "do some comedy", "comedy pannu", "oru joke sollu",
        "joke sollu", "kadi joke", "mokka joke", "mokka podu", "kadi podu", "sirikka vai", "ennai sirikka vai",
        "joke sunao", "ek joke sunao", "chutkula sunao", "kuch funny bolo", "hasao mujhe", "koi joke batao",
        "ஒரு ஜோக் சொல்லு", "ஜோக் சொல்லு", "சிரிக்க வை", "மொக்க ஜோக்", "एक जोक सुनाओ", "चुटकुला सुनाओ",
        "मुझे हँसाओ", "कुछ मज़ेदार बोलो", "dad joke", "pun please",
    ] },
    Spec { intent: "sad", request: false, emoji: &["😢", "😭", "💔", "😔", "🥺", "😞"], cores: &[
        "i am sad", "im sad", "i'm so sad", "feeling sad", "feeling low", "feeling down", "i feel bad",
        "i feel lonely", "i am lonely", "so alone", "i want to cry", "im crying", "i cried today", "heartbroken",
        "my heart hurts", "nobody cares about me", "bad day", "worst day ever", "i failed my exam", "i miss my mom",
        "i miss my friends", "everything is going wrong", "i feel empty", "life is hard", "i lost my job",
        "kashtama iruku", "romba kashtama iruku", "kavalaya iruku", "manasu sariyilla", "azhuga varudhu",
        "yaarum illa", "thaniya feel panren", "mood off", "mood sari illa", "dukhi hoon", "main udaas hoon",
        "mann udaas hai", "rona aa raha hai", "dil toot gaya", "akela feel ho raha hai",
        "சோகமா இருக்கு", "கஷ்டமா இருக்கு", "அழுகையா வருது", "மனசு சரியில்லை", "தனியா இருக்கேன்",
        "मैं दुखी हूँ", "मन उदास है", "रोना आ रहा है", "दिल टूट गया", "मैं अकेला हूँ",
        "சோகமா இருக்கேன்", "ரொம்ப சோகம்", "வருத்தமா இருக்கு", "மனசு வலிக்குது", "sogama irukken", "romba sogam",
        "varuthama iruku", "manasu valikudhu", "मैं उदास हूँ", "बहुत दुख हो रहा है",
    ] },
    Spec { intent: "angry", request: false, emoji: &["😡", "🤬", "😤", "💢"], cores: &[
        "i am angry", "im so angry", "i'm angry", "i am mad", "so mad right now", "furious", "i am furious",
        "so annoyed", "this is so annoying", "irritating", "im irritated", "i hate this", "i hate today",
        "fed up", "i'm fed up", "sick of this", "my boss made me angry", "my brother is irritating me",
        "traffic is making me mad", "everyone is annoying me", "want to scream", "i could punch a wall",
        "kovama iruku", "romba kovam", "kobam varudhu", "erichala iruku", "kadupa iruku", "kaduppu", "tension aagudhu",
        "gussa aa raha hai", "bahut gussa", "mujhe gussa hai", "dimaag kharab hai", "chidh gaya", "pareshan hoon",
        "கோபமா இருக்கு", "ரொம்ப கோபம்", "எரிச்சலா இருக்கு", "கடுப்பா இருக்கு", "मुझे गुस्सा आ रहा है",
        "बहुत गुस्सा है", "दिमाग खराब है", "मैं परेशान हूँ", "argh", "grrr",
    ] },
    Spec { intent: "bored", request: false, emoji: &["😑", "🥱", "😐", "😒"], cores: &[
        "i am bored", "im bored", "so bored", "bored", "boring", "this is boring", "nothing to do",
        "i have nothing to do", "so boring today", "entertain me", "bored af", "bored out of my mind",
        "what do i do now", "nothing happening", "life is boring", "too boring", "i'm bored at {place}",
        "bore adikudhu", "bore adikkuthu", "bore adikuthu da", "bore aagudhu", "onnum panna mudiyala",
        "summa irukken", "velai illa", "bore ho raha hoon", "bore ho rahi hoon", "kuch karne ko nahi hai",
        "bahut bore", "timepass nahi ho raha", "போர் அடிக்குது", "ஒன்னும் பண்ண இல்ல", "சும்மா இருக்கேன்",
        "मैं बोर हो रहा हूँ", "बहुत बोरिंग है", "करने को कुछ नहीं है", "bored at {place}", "meh",
    ] },
    Spec { intent: "love", request: false, emoji: &["❤️", "😍", "🥰", "💕", "😘"], cores: &[
        "i love you", "love you", "luv you", "love u", "ily", "i luv u", "you are cute", "ur cute", "you're so cute",
        "so cute", "cutie", "you are my best friend", "best friend", "my bestie", "i like you", "i like you a lot",
        "you are adorable", "you're the best", "you are awesome", "i adore you", "you make me happy", "miss you buddy",
        "hug me", "give me a hug", "virtual hug", "unna pidikkum", "unna romba pidikkum", "nee cute",
        "nee romba cute", "en chellam", "en thangam", "kadhal", "pyaar", "pyaar hai tumse", "tum bahut cute ho",
        "tum best ho", "i love u so much", "உன்னை பிடிக்கும்", "நீ ரொம்ப அழகு", "என் செல்லம்",
        "ஐ லவ் யூ", "मुझे तुम पसंद हो", "तुम बहुत प्यारे हो", "आई लव यू", "तुम मेरे बेस्ट फ्रेंड हो",
    ] },
    Spec { intent: "insult", request: false, emoji: &["😒", "🙄", "😤", "🤡"], cores: &[
        "you are stupid", "stupid", "idiot", "you idiot", "dumb", "you are dumb", "useless", "you are useless",
        "shut up", "you suck", "loser", "you are a loser", "waste fellow", "worst app", "you are annoying",
        "go away", "nobody likes you", "ugly", "you are ugly", "you are boring", "stupid monkey", "dumb robot",
        "loosu", "nee oru loosu", "loosu payale", "mental", "waste", "vetti", "summa iru", "vaaya moodu",
        "bewakoof", "pagal", "tum pagal ho", "bakwas", "chup kar", "tum bekaar ho", "nikamma",
        "லூசு", "முட்டாள்", "வாய மூடு", "நீ வேஸ்ட்", "बेवकूफ", "पागल", "चुप कर", "तुम बेकार हो", "बकवास",
    ] },
    Spec { intent: "hungry", request: false, emoji: &["🍕", "🍛", "😋", "🤤", "🍔"], cores: &[
        "i am hungry", "im hungry", "so hungry", "starving", "i'm starving", "need food", "food", "what to eat",
        "i want {food}", "craving {food}", "i want to eat {food}", "lets eat {food}", "had {food} for lunch",
        "time for lunch", "lunch time", "dinner time", "breakfast", "snack time", "my stomach is growling",
        "pasikudhu", "pasikuthu", "romba pasikudhu", "pasi", "saapadu venum", "sapadu", "{food} saapdanum",
        "{food} venum", "enna saapdalam", "bhook lagi hai", "bahut bhook", "khana khana hai", "{food} khana hai",
        "kuch khila do", "பசிக்குது", "சாப்பாடு வேணும்", "ரொம்ப பசி", "{food} சாப்பிடணும்",
        "मुझे भूख लगी है", "खाना खाना है", "बहुत भूख", "yummy {food}",
    ] },
    Spec { intent: "sleepy", request: false, emoji: &["😴", "🥱", "💤", "🛌"], cores: &[
        "i am sleepy", "im sleepy", "so sleepy", "tired", "so tired", "i'm exhausted", "exhausted", "need sleep",
        "going to sleep", "time to sleep", "want to nap", "nap time", "cant keep my eyes open", "need a nap",
        "i didnt sleep last night", "sleepy head", "my eyes are closing", "drained", "worn out", "zzz",
        "thookam varudhu", "thookama iruku", "thoonganum", "thoongaporen", "romba tired", "tired ah iruku",
        "kalaippa iruku", "neend aa rahi hai", "bahut neend", "thak gaya", "thak gayi", "sona hai", "so jaata hoon",
        "தூக்கம் வருது", "தூங்கணும்", "களைப்பா இருக்கு", "நான் தூங்கப் போறேன்", "मुझे नींद आ रही है",
        "मैं थक गया हूँ", "मैं थक गई हूँ", "सोना है", "बहुत नींद आ रही",
    ] },
    Spec { intent: "fly", request: true, emoji: &["🚀", "✈️", "🕊️", "☁️"], cores: &[
        "fly", "fly around", "can you fly", "fly for me", "go fly", "take off", "fly high", "fly up",
        "show me flying", "fly across the screen", "lets fly", "zoom around", "fly like a bird", "flying time",
        "go up in the sky", "fly to the top", "do a loop in the air", "fly fly fly", "up up and away",
        "parakku", "parandhu kaatu", "parakka mudiyuma", "mela para", "vaanathula para", "parakkalam",
        "udd ke dikhao", "udo", "udna", "udh jao", "aasmaan mein udo", "hawa mein udo",
        "பற", "பறந்து காட்டு", "பறக்க முடியுமா", "வானத்துல பற", "उड़ो", "उड़ के दिखाओ", "उड़ जाओ",
        "आसमान में उड़ो", "liftoff", "blast off", "launch yourself",
    ] },
    Spec { intent: "power", request: true, emoji: &["⚡", "💥", "🔥", "✨"], cores: &[
        "power", "powers", "use your power", "show your power", "superpower", "show your superpower",
        "use superpower", "special move", "do your special move", "fireball", "do magic", "show me magic",
        "use your magic", "power up", "powerful move", "ultimate move", "what powers do you have", "show me a trick",
        "black magic", "do a trick", "super power please", "blast", "unleash your power", "power attack",
        "sakthi kaatu", "un sakthi kaatu", "power kaatu", "magic pannu", "mass scene kaatu", "super power use pannu",
        "shakti dikhao", "apni shakti dikhao", "jaadu dikhao", "power dikhao", "superpower dikhao", "jadoo karo",
        "சக்தி காட்டு", "மேஜிக் பண்ணு", "பவர் காட்டு", "शक्ति दिखाओ", "जादू दिखाओ", "पावर दिखाओ",
        "सुपरपावर दिखाओ",
    ] },
    Spec { intent: "dance", request: true, emoji: &["💃", "🕺", "🎶", "🎵"], cores: &[
        "dance", "dance for me", "can you dance", "lets dance", "show me a dance", "dance dance", "do a dance",
        "dance time", "party time", "groove", "bust a move", "show your moves", "moonwalk", "do the moonwalk",
        "shake it", "boogie", "dance party", "do a little dance", "dance to this song", "play music and dance",
        "kuthu dance", "kuthu podu", "aadu", "aadu da", "dance aadu", "aatam podu", "aattam podu", "steppu podu",
        "naacho", "naach ke dikhao", "nacho na", "thumka lagao", "dance karo", "naachna hai",
        "ஆடு", "டான்ஸ் ஆடு", "குத்து டான்ஸ்", "ஆட்டம் போடு", "नाचो", "डांस करो", "नाच के दिखाओ",
        "ठुमका लगाओ", "salsa", "bhangra",
    ] },
    Spec { intent: "laugh", request: false, emoji: &["😂", "🤣", "😆", "😹"], cores: &[
        "haha", "hahaha", "hahahaha", "hehe", "hehehe", "lol", "lool", "lmao", "lmfao", "rofl", "xd", "xD",
        "hihi", "ha ha", "ha ha ha", "haha good one", "lol that was funny", "so funny", "that's hilarious",
        "hilarious", "dying of laughter", "cant stop laughing", "lol nice", "good one", "😂", "🤣", "😂😂",
        "🤣🤣🤣", "semma comedy", "sirippu varudhu", "siruchu sethen", "vayiru valikudhu sirichu", "hahaha semma",
        "bahut funny", "hass hass ke", "pet dard ho gaya hasste", "maza aa gaya", "ஹா ஹா", "ஹாஹா",
        "சிரிப்பு வருது", "செம்ம காமெடி", "हाहा", "हा हा हा", "बहुत मज़ेदार", "हँसी आ गई", "heh",
    ] },
    Spec { intent: "yes", request: false, emoji: &["👍", "✅", "🙂", "👌"], cores: &[
        "yes", "yeah", "yep", "yup", "ok", "okay", "okk", "kk", "k", "sure", "of course", "definitely",
        "absolutely", "alright", "fine", "sounds good", "yes please", "correct", "right", "true", "agreed",
        "why not", "go ahead", "yes yes", "aama", "aamam", "aamaa", "seri", "sari", "sari da", "okay da",
        "haan", "haa", "haanji", "ha ji", "theek hai", "thik hai", "bilkul", "chalo", "accha", "acha theek hai",
        "ஆமா", "சரி", "ஆமாம்", "ஓகே", "हाँ", "हाँ जी", "ठीक है", "बिल्कुल", "जी हाँ", "👍", "👌",
    ] },
    Spec { intent: "no", request: false, emoji: &["🙅", "❌", "😐", "👎"], cores: &[
        "no", "nope", "nah", "naa", "no way", "not now", "no thanks", "never", "no no", "not really",
        "i don't want", "i dont think so", "stop", "don't", "dont do that", "nope not interested", "absolutely not",
        "not today", "no please", "nopes", "illa", "illai", "illa da", "vendaam", "venam", "venaam", "vendam da",
        "mudiyadhu", "theva illa", "nahi", "nahin", "bilkul nahi", "nahi chahiye", "mat karo", "rehne do",
        "இல்ல", "வேண்டாம்", "முடியாது", "தேவை இல்ல", "नहीं", "बिल्कुल नहीं", "नहीं चाहिए", "मत करो",
        "रहने दो", "👎", "nein",
    ] },
    Spec { intent: "help", request: true, emoji: &["🙏", "😟", "🆘", "🤔"], cores: &[
        "help", "help me", "can you help me", "i need help", "please help", "help me with {task}",
        "how do i {task}", "how to {task}", "teach me to {task}", "can you help me {task}", "i need help to {task}",
        "show me how to {task}", "i dont know how to {task}", "i'm stuck", "im stuck with {task}", "guide me",
        "give me advice", "any tips to {task}", "what should i do", "help pannu", "udhavi pannu", "udhavi venum",
        "enakku udhavi pannu", "{task} epdi pannuradhu", "epdi {task}", "madad karo", "meri madad karo",
        "help chahiye", "{task} kaise karu", "mujhe sikhao", "உதவி பண்ணு", "உதவி வேணும்", "எனக்கு உதவி பண்ணு",
        "मदद करो", "मेरी मदद करो", "मुझे मदद चाहिए", "मुझे सिखाओ", "sos",
    ] },
    Spec { intent: "question", request: false, emoji: &["🤔", "❓", "🧐"], cores: &[
        "what is {thing}", "what's {thing}", "who invented {thing}", "why is the sky blue", "how far is the moon",
        "when was {thing} discovered", "where is {place}", "how big is {thing}", "explain {thing}",
        "tell me about {thing}", "is {thing} dangerous", "how does {thing} work", "why do cats purr",
        "which is the biggest country", "what time is it", "what day is today", "how many legs does a spider have",
        "do fish sleep", "can dogs see colours", "what is 7 times 8", "who is the prime minister",
        "{thing} na enna", "{thing} pathi sollu", "ippo mani enna", "inniki enna kizhamai", "yen vaanam neelama iruku",
        "{thing} kya hai", "{thing} kaise kaam karta hai", "kitne baje hain", "aaj kaunsa din hai", "aasmaan neela kyun hai",
        "{thing} என்றால் என்ன", "மணி என்ன", "இன்னைக்கு என்ன கிழமை", "வானம் ஏன் நீலம்",
        "{thing} क्या है", "कितने बजे हैं", "आज कौन सा दिन है", "आसमान नीला क्यों है",
    ] },
    Spec { intent: "unknown", request: false, emoji: &["", "🙂", "📱", "🚌"], cores: &[
        "i am at {place}", "just reached {place}", "went to {place} today", "on the way to {place}",
        "my phone battery is low", "it is raining here", "the bus is late", "my sister is watching tv",
        "i have a meeting at 4", "the match starts at 7", "my cousin is coming tomorrow", "the shop was closed",
        "i bought a new pen", "my laptop is slow", "the fan is making noise", "weather is hot today",
        "i got a parcel", "{who} {did}", "{who} {did}", "{who} {did}", "{who} {did} today", "yesterday {who} {did}",
        "i went to {place} with {who}", "{who} is at {place}", "the {thing} documentary is on", "my friend called me", "the train is crowded", "random", "asdf", "123", "test",
        "naan {place} la irukken", "{place} ku poren", "amma samayal panranga", "appa {place} ku ponaru",
        "naan ippo {place} la", "inniki leave", "naalaiku exam iruku", "current poiduchu", "net slow ah iruku",
        "papa {place} gaye hain", "mummy khana bana rahi hai", "kal exam hai", "light chali gayi", "net slow hai",
        "main {place} se aa raha hoon", "inniki mazhai", "bus late", "amma kooptaanga",
        "main {place} mein hoon", "{place} ja raha hoon", "aaj baarish ho rahi hai", "bus late hai", "mummy ne bulaya",
        "நான் {place} ல இருக்கேன்", "இன்னைக்கு மழை", "பஸ் லேட்", "मैं घर पर हूँ", "आज बारिश हो रही है",
        "बस लेट है", "the grass is green", "my bike needs petrol", "office meeting over", "testing testing",
    ] },
];

pub fn intents() -> Vec<&'static str> { SPECS.iter().map(|s| s.intent).collect() }

fn fill(core: &str, rng: &mut Rng) -> String {
    let mut s = core.to_string();
    for (slot, list) in [("{food}", FOOD), ("{task}", TASK), ("{thing}", THING), ("{place}", PLACE), ("{who}", WHO), ("{did}", DID)] {
        while let Some(at) = s.find(slot) {
            let w = rng.pick(list).to_string();
            s.replace_range(at..at + slot.len(), &w);
        }
    }
    s
}

fn is_latin_word(w: &str) -> bool { !w.is_empty() && w.chars().all(|c| c.is_ascii_alphabetic() || c == '\'') }

const VOWELS: &[char] = &['a', 'e', 'i', 'o', 'u'];

/// One chat typo/style change on a random Latin word (Tamil and Hindi script are left as they are).
fn noise(text: &str, rng: &mut Rng) -> String {
    let mut words: Vec<String> = text.split(' ').map(|w| w.to_string()).collect();
    let latin: Vec<usize> = (0..words.len()).filter(|&i| is_latin_word(&words[i])).collect();
    if latin.is_empty() {
        return text.to_string();
    }
    let i = *rng.pick(&latin);
    let w = words[i].clone();
    let chars: Vec<char> = w.chars().collect();
    let new = match rng.below(6) {
        // elongate the last letter: "hiiii", "pleaseee", "byeee"
        0 | 1 => {
            let last = *chars.last().unwrap();
            let mut s = w.clone();
            for _ in 0..2 + rng.below(4) {
                s.push(last);
            }
            s
        }
        // drop the inner vowels: "please" -> "pls", "thanks" -> "thnks"
        2 if chars.len() > 3 => {
            let mut s = String::new();
            for (k, c) in chars.iter().enumerate() {
                if k == 0 || k == chars.len() - 1 || !VOWELS.contains(c) {
                    s.push(*c);
                }
            }
            s
        }
        // swap two neighbours: "hlelo"
        3 if chars.len() > 3 => {
            let k = 1 + rng.below(chars.len() - 2);
            let mut c = chars.clone();
            c.swap(k, k - 1);
            c.into_iter().collect()
        }
        // drop one letter: "helo", "thnks"
        4 if chars.len() > 3 => {
            let k = rng.below(chars.len());
            chars.iter().enumerate().filter(|(j, _)| *j != k).map(|(_, c)| *c).collect()
        }
        // double one letter: "helllo"
        _ => {
            let k = rng.below(chars.len());
            let mut s = String::new();
            for (j, c) in chars.iter().enumerate() {
                s.push(*c);
                if j == k {
                    s.push(*c);
                }
            }
            s
        }
    };
    words[i] = new;
    words.join(" ")
}

/// Names, fillers, "pls", emoji, punctuation and case.
fn decorate(spec: &Spec, base: &str, rng: &mut Rng) -> String {
    let mut s = base.to_string();
    if spec.request && rng.chance(0.25) {
        let p = rng.pick(PLEASE);
        s = if rng.chance(0.3) { format!("{} {}", p, s) } else { format!("{} {}", s, p) };
    }
    if rng.chance(0.4) {
        let a = rng.pick(ADDRESS);
        s = if rng.chance(0.25) { format!("{} {}", a, s) } else { format!("{} {}", s, a) };
    }
    if rng.chance(0.08) {
        s = format!("{} {}", rng.pick(OPENERS), s);
    }
    for _ in 0..(if rng.chance(0.5) { 1 } else { 0 } + if rng.chance(0.15) { 1 } else { 0 }) {
        s = noise(&s, rng);
    }
    match rng.below(10) {
        0 => s = s.to_uppercase(),
        1 | 2 => {
            let mut c = s.chars();
            if let Some(f) = c.next() {
                s = f.to_uppercase().collect::<String>() + c.as_str();
            }
        }
        _ => {}
    }
    let q = spec.intent == "question" || spec.intent == "how_are_you" || spec.intent == "who_are_you";
    let punct = match rng.below(10) {
        0 | 1 => if q { "?" } else { "!" },
        2 => if q { "??" } else { "!!" },
        3 => "...",
        4 => ".",
        5 if q => "?",
        _ => "",
    };
    s.push_str(punct);
    if rng.chance(0.3) {
        let e = rng.pick(spec.emoji);
        if !e.is_empty() {
            if rng.chance(0.3) {
                s.push_str(e);
                s.push_str(e);
            } else {
                s.push(' ');
                s.push_str(e);
            }
        }
    }
    s
}

/// Read `data/seed_<intent>.tsv` (lines "intent<TAB>text", '#' = comment).
pub fn read_seeds(data: &Path, intent: &str) -> Vec<String> {
    let path = data.join(format!("seed_{}.tsv", intent));
    let text = std::fs::read_to_string(&path).unwrap_or_else(|e| panic!("{}: {}", path.display(), e));
    text.lines()
        .filter(|l| !l.starts_with('#') && !l.trim().is_empty())
        .map(|l| {
            let (k, v) = l.split_once('\t').unwrap_or_else(|| panic!("{}: no tab in {:?}", path.display(), l));
            assert_eq!(k.trim(), intent, "{}: wrong intent in {:?}", path.display(), l);
            v.trim().to_string()
        })
        .collect()
}

/// Examples generated per intent (on top of the seeds and their noisy copies).
pub const PER_INTENT: usize = 330;

/// Build the whole data set: every seed as written, two decorated copies of each seed, then generated
/// examples until each intent has [PER_INTENT] more. Exact duplicates (by text) are dropped.
pub fn build(data: &Path) -> Vec<Example> {
    let mut rng = Rng::new(0x5E77_A1_2024);
    let mut seen: HashSet<String> = HashSet::new();
    let mut out = Vec::new();
    for spec in SPECS {
        let mut push = |text: String, family: String, seed: bool, out: &mut Vec<Example>| {
            let key = settai_mind::model::model_text(&text);
            if text.trim().is_empty() || !seen.insert(key) {
                return false;
            }
            out.push(Example { intent: spec.intent, text, family, seed });
            true
        };
        for s in read_seeds(data, spec.intent) {
            push(s.clone(), format!("seed:{}", s), true, &mut out);
            for _ in 0..2 {
                let d = decorate(spec, &s, &mut rng);
                push(d, format!("seed:{}", s), true, &mut out);
            }
        }
        let mut made = 0;
        let mut tries = 0;
        while made < PER_INTENT && tries < PER_INTENT * 200 {
            tries += 1;
            let core: &str = *rng.pick(spec.cores);
            let base = fill(core, &mut rng);
            let text = decorate(spec, &base, &mut rng);
            if push(text, base, false, &mut out) {
                made += 1;
            }
        }
    }
    out
}

/// Stable 64-bit hash (FNV-1a) for splitting and versioning.
pub fn hash64(s: &str) -> u64 {
    let mut h: u64 = 0xCBF2_9CE4_8422_2325;
    for b in s.as_bytes() {
        h ^= *b as u64;
        h = h.wrapping_mul(0x0000_0100_0000_01B3);
    }
    h
}

/// Held-out (about 15%). Seeds by family (a seed sentence is never half in training), generated lines by text.
pub fn is_heldout(e: &Example) -> bool {
    let key = if e.seed { e.family.clone() } else { settai_mind::model::model_text(&e.text) };
    hash64(&key) % 100 < 15
}

/// The stricter split: every variant of a phrase on one side.
pub fn is_heldout_family(e: &Example) -> bool { hash64(&e.family) % 100 < 15 }
