//! R2: the small trained intent model (hashed character n-grams + words -> multinomial logistic regression).
//!
//! The trainer (`mind/settai-train`) uses [features] from here, so training and the phone hash text the same way.
//! File format of `model/intent.bin` (little endian):
//!
//! ```text
//! "STM1"  magic
//! u16 len + bytes   version string (utf-8)
//! u32               buckets (N)
//! u32               classes (C)
//! C x (u8 len + bytes)   class names (Intent::name)
//! C x f32           scale per class (weight = i8 * scale)
//! C x f32           bias per class
//! N x C x i8        weights, row = bucket
//! ```

use crate::understand::{normalize, Intent};
use std::sync::OnceLock;

/// Hash buckets for features (a power of two).
pub const BUCKETS: usize = 1 << 14;

fn fnv(seed: u32, bytes: &[u8]) -> u32 {
    let mut h: u32 = 0x811C_9DC5 ^ seed;
    for b in bytes {
        h ^= *b as u32;
        h = h.wrapping_mul(0x0100_0193);
    }
    // final avalanche so close strings spread over the buckets
    h ^= h >> 15;
    h = h.wrapping_mul(0x2C1B_3C6D);
    h ^= h >> 12;
    h
}

/// The text the model reads: [normalize]d, and any letter repeated more than twice cut to two ("hiiiii" -> "hii").
pub fn model_text(text: &str) -> String {
    let norm = normalize(text);
    let mut out = String::with_capacity(norm.len());
    let mut last = '\0';
    let mut run = 0;
    for ch in norm.chars() {
        if ch == last {
            run += 1;
        } else {
            run = 1;
            last = ch;
        }
        if run <= 2 {
            out.push(ch);
        }
    }
    out
}

/// Sparse features of [text]: (bucket, value). Character 1-4 grams over " text " plus whole words, each feature
/// counted, then scaled by 1/sqrt(total) so long and short messages weigh the same.
pub fn features(text: &str) -> Vec<(u32, f32)> {
    let t = model_text(text);
    let chars: Vec<(usize, char)> = t.char_indices().collect();
    let mut idx: Vec<u32> = Vec::with_capacity(chars.len() * 4 + 8);
    let mask = (BUCKETS - 1) as u32;
    for n in 1..=4usize {
        if chars.len() < n {
            break;
        }
        for i in 0..=chars.len() - n {
            let a = chars[i].0;
            let b = if i + n < chars.len() { chars[i + n].0 } else { t.len() };
            let g = &t[a..b];
            if n == 1 && g == " " {
                continue;
            }
            idx.push(fnv(n as u32, g.as_bytes()) & mask);
        }
    }
    let words: Vec<&str> = t.split(' ').filter(|w| !w.is_empty()).collect();
    for w in &words {
        idx.push(fnv(0x5757, w.as_bytes()) & mask);
    }
    // word pairs help short phrases ("bore adikudhu", "good night")
    for p in words.windows(2) {
        let mut s = String::with_capacity(p[0].len() + p[1].len() + 1);
        s.push_str(p[0]);
        s.push(' ');
        s.push_str(p[1]);
        idx.push(fnv(0xB1B1, s.as_bytes()) & mask);
    }
    if idx.is_empty() {
        return Vec::new();
    }
    idx.sort_unstable();
    let scale = 1.0 / (idx.len() as f32).sqrt();
    let mut out: Vec<(u32, f32)> = Vec::with_capacity(idx.len());
    for i in idx {
        match out.last_mut() {
            Some((j, v)) if *j == i => *v += scale,
            _ => out.push((i, scale)),
        }
    }
    out
}

/// A loaded model. Weights stay borrowed from the bytes (no copy).
pub struct Model<'a> {
    pub version: String,
    pub classes: Vec<Intent>,
    scale: Vec<f32>,
    bias: Vec<f32>,
    weights: &'a [u8],
}

struct Reader<'a> {
    b: &'a [u8],
    at: usize,
}

impl<'a> Reader<'a> {
    fn take(&mut self, n: usize) -> Option<&'a [u8]> {
        let end = self.at.checked_add(n)?;
        let s = self.b.get(self.at..end)?;
        self.at = end;
        Some(s)
    }
    fn u8(&mut self) -> Option<u8> { Some(self.take(1)?[0]) }
    fn u16(&mut self) -> Option<u16> { Some(u16::from_le_bytes(self.take(2)?.try_into().ok()?)) }
    fn u32(&mut self) -> Option<u32> { Some(u32::from_le_bytes(self.take(4)?.try_into().ok()?)) }
    fn f32(&mut self) -> Option<f32> { Some(f32::from_le_bytes(self.take(4)?.try_into().ok()?)) }
}

impl<'a> Model<'a> {
    /// Read a model; None if the bytes are not a valid model for this build (wrong buckets, unknown intent...).
    pub fn parse(bytes: &'a [u8]) -> Option<Model<'a>> {
        let mut r = Reader { b: bytes, at: 0 };
        if r.take(4)? != b"STM1" {
            return None;
        }
        let vlen = r.u16()? as usize;
        let version = std::str::from_utf8(r.take(vlen)?).ok()?.to_string();
        let n = r.u32()? as usize;
        let c = r.u32()? as usize;
        if n != BUCKETS || c == 0 || c > 64 {
            return None;
        }
        let mut classes = Vec::with_capacity(c);
        for _ in 0..c {
            let l = r.u8()? as usize;
            classes.push(Intent::from_name(std::str::from_utf8(r.take(l)?).ok()?)?);
        }
        let mut scale = Vec::with_capacity(c);
        for _ in 0..c {
            scale.push(r.f32()?);
        }
        let mut bias = Vec::with_capacity(c);
        for _ in 0..c {
            bias.push(r.f32()?);
        }
        let weights = r.take(n * c)?;
        if r.at != bytes.len() {
            return None;
        }
        Some(Model { version, classes, scale, bias, weights })
    }

    /// Class probabilities for [text] (same order as [classes]).
    pub fn probabilities(&self, text: &str) -> Vec<f32> {
        let c = self.classes.len();
        let mut acc = vec![0f32; c];
        for (i, v) in features(text) {
            let row = &self.weights[i as usize * c..i as usize * c + c];
            for k in 0..c {
                acc[k] += v * (row[k] as i8) as f32;
            }
        }
        let mut max = f32::MIN;
        for k in 0..c {
            acc[k] = self.bias[k] + self.scale[k] * acc[k];
            max = max.max(acc[k]);
        }
        let mut sum = 0.0;
        for a in acc.iter_mut() {
            *a = (*a - max).exp();
            sum += *a;
        }
        for a in acc.iter_mut() {
            *a /= sum;
        }
        acc
    }

    /// The most likely intent and its probability.
    pub fn predict(&self, text: &str) -> (Intent, f32) {
        let p = self.probabilities(text);
        let mut best = 0;
        for k in 1..p.len() {
            if p[k] > p[best] {
                best = k;
            }
        }
        (self.classes[best], p[best])
    }
}

#[cfg(has_model)]
static MODEL_BYTES: &[u8] = include_bytes!("../model/intent.bin");
#[cfg(not(has_model))]
static MODEL_BYTES: &[u8] = &[];

/// The model built into this library, or None when the file was missing at build time or is unreadable.
pub fn builtin() -> Option<&'static Model<'static>> {
    static M: OnceLock<Option<Model<'static>>> = OnceLock::new();
    M.get_or_init(|| Model::parse(MODEL_BYTES)).as_ref()
}

/// The built-in model's version string ("none" when the rules are on their own).
pub fn version() -> &'static str {
    builtin().map(|m| m.version.as_str()).unwrap_or("none")
}
