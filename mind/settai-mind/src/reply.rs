//! Answering in the character's voice: pick a line for the intent, fill the blanks, never repeat the last few.
//!
//! Every character (including ones added later) gets the general lines. A character can have its own file in
//! `lines/<id>.tsv` (added to `CHARACTER_LINES` below); its lines are preferred when it has some for that intent.

use crate::understand::Intent;

const GENERAL: &str = include_str!("../lines/general.tsv");

/// Per-character line files (id, contents), one per character in shared/.../character/ (CharacterRegistry.all).
/// Ids not listed here (custom companions, future characters) use the general lines.
pub const CHARACTER_LINES: &[(&str, &str)] = &[
    ("devil", include_str!("../lines/devil.tsv")),
    ("angel", include_str!("../lines/angel.tsv")),
    ("monkey", include_str!("../lines/monkey.tsv")),
    ("superboy", include_str!("../lines/superboy.tsv")),
    ("armour", include_str!("../lines/armour.tsv")),
    ("mask", include_str!("../lines/mask.tsv")),
    ("billa", include_str!("../lines/billa.tsv")),
    ("chittu", include_str!("../lines/chittu.tsv")),
    ("gajju", include_str!("../lines/gajju.tsv")),
    ("rocketgirl", include_str!("../lines/rocketgirl.tsv")),
    ("hollowman", include_str!("../lines/hollowman.tsv")),
    ("surfer", include_str!("../lines/surfer.tsv")),
    ("skater", include_str!("../lines/skater.tsv")),
];

fn lines_for<'a>(table: &'a str, intent: Intent) -> Vec<&'a str> {
    let name = intent.name();
    table
        .lines()
        .filter(|l| !l.starts_with('#') && !l.trim().is_empty())
        .filter_map(|l| {
            let (k, v) = l.split_once('\t')?;
            if k.trim() == name { Some(v.trim()) } else { None }
        })
        .collect()
}

/// Only this character's own lines for [intent] (empty for ids without a file).
pub fn own_lines(character_id: &str, intent: Intent) -> Vec<&'static str> {
    CHARACTER_LINES.iter().find(|(id, _)| *id == character_id).map(|(_, t)| lines_for(t, intent)).unwrap_or_default()
}

/// The shared lines for [intent].
pub fn general_lines(intent: Intent) -> Vec<&'static str> { lines_for(GENERAL, intent) }

/// All lines this character could say for [intent] (its own first, else the general ones).
pub fn candidates(character_id: &str, intent: Intent) -> Vec<&'static str> {
    if let Some((_, table)) = CHARACTER_LINES.iter().find(|(id, _)| *id == character_id) {
        let own = lines_for(table, intent);
        if !own.is_empty() {
            return own;
        }
    }
    let general = lines_for(GENERAL, intent);
    if general.is_empty() { lines_for(GENERAL, Intent::Unknown) } else { general }
}

/// A small deterministic mixer (no clock, no global random): the app passes a seed.
fn mix(seed: u64) -> u64 {
    let mut z = seed.wrapping_add(0x9E37_79B9_7F4A_7C15);
    z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
    z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
    z ^ (z >> 31)
}

/// FNV-1a, for remembering which lines were said recently without storing them.
pub fn line_hash(s: &str) -> u32 {
    let mut h: u32 = 0x811C_9DC5;
    for b in s.as_bytes() {
        h ^= *b as u32;
        h = h.wrapping_mul(0x0100_0193);
    }
    h
}

/// Pick a line, skipping any whose hash is in [recent] when there is another choice, then fill the blanks.
pub fn reply(character_id: &str, character_name: &str, you: &str, intent: Intent, seed: u64, recent: &[u32]) -> String {
    let all = candidates(character_id, intent);
    let fresh: Vec<&str> = all.iter().copied().filter(|l| !recent.contains(&line_hash(l))).collect();
    let pool = if fresh.is_empty() { &all } else { &fresh };
    let pick = pool[(mix(seed) % pool.len() as u64) as usize];
    let you = if you.trim().is_empty() { "friend" } else { you.trim() };
    let name = if character_name.trim().is_empty() { "me" } else { character_name.trim() };
    pick.replace("{name}", name).replace("{you}", you)
}
