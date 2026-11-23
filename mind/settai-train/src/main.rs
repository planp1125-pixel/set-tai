//! `cargo run --release -p settai-train` (from `mind/`):
//! 1. builds the data from `data/seed_*.tsv` + the word lists in gen.rs -> `data/train.tsv`, `data/heldout.tsv`
//! 2. trains multinomial logistic regression on hashed char 1-4 grams + words (settai_mind::model::features)
//! 3. quantizes to int8 and writes `settai-mind/model/intent.bin`
//! 4. reloads that file with the phone's own loader and prints held-out accuracy per intent.

mod gen;

use settai_mind::model::{features, Model, BUCKETS};
use settai_mind::Intent;
use std::fmt::Write as _;
use std::path::{Path, PathBuf};
use std::time::Instant;

const EPOCHS: usize = 30;
const LR0: f32 = 0.6;
const L2: f32 = 1e-5;
/// Weights beyond this many standard deviations are clipped before int8 so one big weight does not flatten the rest.
const CLIP_SIGMAS: f32 = 8.0;

struct Row {
    y: usize,
    x: Vec<(u32, f32)>,
}

fn write_tsv(path: &Path, header: &str, rows: &[&gen::Example]) {
    let mut s = String::from(header);
    for e in rows {
        // tabs and newlines can't be in a TSV cell
        let t = e.text.replace(['\t', '\n'], " ");
        let _ = writeln!(s, "{}\t{}", e.intent, t);
    }
    std::fs::write(path, s).unwrap_or_else(|e| panic!("{}: {}", path.display(), e));
}

fn softmax(z: &mut [f32]) {
    let m = z.iter().cloned().fold(f32::MIN, f32::max);
    let mut sum = 0.0;
    for v in z.iter_mut() {
        *v = (*v - m).exp();
        sum += *v;
    }
    for v in z.iter_mut() {
        *v /= sum;
    }
}

fn train(rows: &[Row], c: usize) -> (Vec<f32>, Vec<f32>) {
    let mut w = vec![0f32; BUCKETS * c];
    let mut b = vec![0f32; c];
    let mut order: Vec<usize> = (0..rows.len()).collect();
    let mut rng = gen::Rng::new(7);
    let mut z = vec![0f32; c];
    for epoch in 0..EPOCHS {
        // Fisher-Yates with the fixed generator: same model every run
        for i in (1..order.len()).rev() {
            order.swap(i, rng.below(i + 1));
        }
        let lr = LR0 / (1.0 + epoch as f32 * 0.25);
        let mut loss = 0.0f64;
        for &i in &order {
            let r = &rows[i];
            z.copy_from_slice(&b);
            for &(f, v) in &r.x {
                let row = &w[f as usize * c..f as usize * c + c];
                for k in 0..c {
                    z[k] += v * row[k];
                }
            }
            softmax(&mut z);
            loss -= (z[r.y].max(1e-9) as f64).ln();
            z[r.y] -= 1.0; // gradient of cross-entropy wrt the scores
            for k in 0..c {
                b[k] -= lr * 0.1 * z[k];
            }
            for &(f, v) in &r.x {
                let row = &mut w[f as usize * c..f as usize * c + c];
                for k in 0..c {
                    row[k] -= lr * (z[k] * v + L2 * row[k]);
                }
            }
        }
        if epoch % 5 == 4 || epoch == EPOCHS - 1 {
            println!("  epoch {:2}  loss {:.4}", epoch + 1, loss / rows.len() as f64);
        }
    }
    (w, b)
}

fn quantize(w: &[f32], c: usize) -> (Vec<f32>, Vec<i8>) {
    let mut scale = vec![0f32; c];
    for k in 0..c {
        let col: Vec<f32> = (0..BUCKETS).map(|i| w[i * c + k]).collect();
        let n = col.len() as f32;
        let mean = col.iter().sum::<f32>() / n;
        let sd = (col.iter().map(|v| (v - mean) * (v - mean)).sum::<f32>() / n).sqrt();
        let max = col.iter().fold(0f32, |m, v| m.max(v.abs()));
        let lim = max.min(mean.abs() + CLIP_SIGMAS * sd).max(1e-6);
        scale[k] = lim / 127.0;
    }
    let mut q = vec![0i8; w.len()];
    for i in 0..BUCKETS {
        for k in 0..c {
            q[i * c + k] = (w[i * c + k] / scale[k]).round().clamp(-127.0, 127.0) as i8;
        }
    }
    (scale, q)
}

fn encode(version: &str, classes: &[&str], scale: &[f32], bias: &[f32], q: &[i8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(q.len() + 1024);
    out.extend_from_slice(b"STM1");
    out.extend_from_slice(&(version.len() as u16).to_le_bytes());
    out.extend_from_slice(version.as_bytes());
    out.extend_from_slice(&(BUCKETS as u32).to_le_bytes());
    out.extend_from_slice(&(classes.len() as u32).to_le_bytes());
    for name in classes {
        out.push(name.len() as u8);
        out.extend_from_slice(name.as_bytes());
    }
    for s in scale {
        out.extend_from_slice(&s.to_le_bytes());
    }
    for b in bias {
        out.extend_from_slice(&b.to_le_bytes());
    }
    out.extend(q.iter().map(|v| *v as u8));
    out
}

fn main() {
    let mind: PathBuf = Path::new(env!("CARGO_MANIFEST_DIR")).parent().unwrap().to_path_buf();
    let data = mind.join("data");
    let names = gen::intents();
    for n in &names {
        assert!(Intent::from_name(n).is_some(), "gen.rs intent {:?} is not an Intent", n);
    }
    assert_eq!(names.len(), Intent::ALL.len(), "every Intent needs a Spec in gen.rs");
    let c = names.len();

    // 1. data
    let all = gen::build(&data);
    let (test, trainset): (Vec<&gen::Example>, Vec<&gen::Example>) = all.iter().partition(|e| gen::is_heldout(e));
    let header = "# Generated by `cargo run --release -p settai-train` from data/seed_*.tsv + settai-train/src/gen.rs. Do not edit.\n# intent<TAB>text\n";
    write_tsv(&data.join("train.tsv"), header, &trainset);
    write_tsv(&data.join("heldout.tsv"), header, &test);
    println!("data: {} examples ({} train, {} held out) across {} intents", all.len(), trainset.len(), test.len(), c);
    for n in &names {
        let k = all.iter().filter(|e| e.intent == *n).count();
        assert!(k >= 200, "{} has only {} examples", n, k);
    }

    // 2. train
    let idx = |n: &str| names.iter().position(|x| *x == n).unwrap();
    let rows: Vec<Row> = trainset.iter().map(|e| Row { y: idx(e.intent), x: features(&e.text) }).collect();
    let t0 = Instant::now();
    println!("training: {} buckets x {} classes, {} epochs", BUCKETS, c, EPOCHS);
    let (w, b) = train(&rows, c);
    println!("trained in {:.1} s", t0.elapsed().as_secs_f32());

    // 3. quantize + write
    let mut data_hash = 0u64;
    for e in &all {
        data_hash = data_hash.rotate_left(5) ^ gen::hash64(&format!("{}\t{}", e.intent, e.text));
    }
    let version = format!("r2-intent lr-ngram1-4 {}k n={} data={:08x}", BUCKETS / 1024, trainset.len(), data_hash as u32);
    let (scale, q) = quantize(&w, c);
    let bytes = encode(&version, &names, &scale, &b, &q);
    let out = mind.join("settai-mind/model/intent.bin");
    std::fs::create_dir_all(out.parent().unwrap()).unwrap();
    std::fs::write(&out, &bytes).unwrap();
    println!("wrote {} ({} bytes = {:.2} MB), version \"{}\"", out.display(), bytes.len(), bytes.len() as f64 / 1048576.0, version);

    // 4. evaluate the written file with the phone's loader
    let m = Model::parse(&bytes).expect("the written model must load");
    let mut per: Vec<(usize, usize)> = vec![(0, 0); c];
    let mut confusions: Vec<(String, &str, &str)> = Vec::new();
    let (mut seed_ok, mut seed_n) = (0, 0);
    for e in &test {
        let (got, _) = m.predict(&e.text);
        let k = idx(e.intent);
        per[k].1 += 1;
        let ok = got.name() == e.intent;
        if ok {
            per[k].0 += 1;
        } else if confusions.len() < 40 {
            confusions.push((e.text.clone(), e.intent, got.name()));
        }
        if e.seed {
            seed_n += 1;
            if ok {
                seed_ok += 1;
            }
        }
    }
    let train_ok = trainset.iter().filter(|e| m.predict(&e.text).0.name() == e.intent).count();
    println!("\nheld-out accuracy per intent:");
    for (k, n) in names.iter().enumerate() {
        let (ok, tot) = per[k];
        println!("  {:<12} {:>4}/{:<4} {:>6.1}%", n, ok, tot, 100.0 * ok as f64 / tot.max(1) as f64);
    }
    let ok: usize = per.iter().map(|p| p.0).sum();
    let tot: usize = per.iter().map(|p| p.1).sum();
    println!("  {:<12} {:>4}/{:<4} {:>6.1}%", "OVERALL", ok, tot, 100.0 * ok as f64 / tot as f64);
    println!("  (hand-written seeds only: {}/{} = {:.1}%; training set: {:.1}%)", seed_ok, seed_n,
        100.0 * seed_ok as f64 / seed_n.max(1) as f64, 100.0 * train_ok as f64 / trainset.len() as f64);
    if !confusions.is_empty() {
        println!("\nsome held-out mistakes (text | wanted -> got):");
        for (t, want, got) in confusions.iter().take(25) {
            println!("  {:<40} | {} -> {}", t, want, got);
        }
    }
    // the stricter check: a second model trained without whole phrase families, tested on those phrases only
    let (strict_test, strict_train): (Vec<&gen::Example>, Vec<&gen::Example>) = all.iter().partition(|e| gen::is_heldout_family(e));
    let srows: Vec<Row> = strict_train.iter().map(|e| Row { y: idx(e.intent), x: features(&e.text) }).collect();
    println!("\nstricter check (whole phrases never seen in training, informational):");
    let (sw, sb) = train(&srows, c);
    let (ss, sq) = quantize(&sw, c);
    let sbytes = encode("strict", &names, &ss, &sb, &sq);
    let sm = Model::parse(&sbytes).unwrap();
    let sok = strict_test.iter().filter(|e| sm.predict(&e.text).0.name() == e.intent).count();
    println!("  unseen-phrase accuracy {}/{} = {:.1}%", sok, strict_test.len(), 100.0 * sok as f64 / strict_test.len().max(1) as f64);

    let msgs = ["hi da", "tell me a joke!", "I'm so bored", "epdi irukka?", "what's the weather like tomorrow in Chennai?", "😂😂"];
    let rounds = 20000;
    let t = Instant::now();
    let mut sink = 0usize;
    for r in 0..rounds {
        sink += m.predict(msgs[r % msgs.len()]).0.name().len();
    }
    println!("\nspeed: {:.4} ms per message ({})", t.elapsed().as_secs_f64() * 1000.0 / rounds as f64, sink % 7);
    println!("rebuild: cd mind && cargo run --release -p settai-train && cargo test --release");
}
