# Intent data (R2)

- `seed_<intent>.tsv`: hand-written seed lines, one per row, `intent<TAB>text`. Edit these first.
- `train.tsv`, `heldout.tsv`: **generated**. Don't edit them; rerun the trainer instead.

Generator: `../settai-train/src/gen.rs`. It holds per-intent core phrases and word lists (names, foods,
tasks, places), then adds names/"da"/"bro", "pls", emoji, punctuation and chat typos ("hiii", "pls",
swapped/dropped letters, CAPS). It uses a fixed random seed, so the output is the same every run.

Held-out set (15%): generated lines are split one by one. A seed sentence and its noisy copies always
land on the same side. The trainer also reports a stricter score where whole phrases are never seen in
training. That score is for information only and is not the target.

Provenance: the seed lines, word lists and core phrases were typed by hand for this repo, in the
Claude Code session that built R2 (Nov 2026). They are not bulk output pasted from a chatbot. Please
review them, especially the Tamil and Hindi script, and replace anything that doesn't sound like real
chat.

Rebuild:

```sh
cd mind
cargo run --release -p settai-train   # data -> model/intent.bin, prints accuracy per intent
cargo test --release
cargo run --release -p settai-train --example try -- "enna da panra" "मुझे नींद आ रही है"
```
