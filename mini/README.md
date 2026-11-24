> Production has not started. The isolated smoke test passed: five locally generated chats, 20 CPU LoRA steps. Full 1B training and Android conversion were not executed in this test.

# Set-Tai Mini Plan B pipeline

Run from the repo root:
```bash
nohup bash mini/run_pipeline.sh >/tmp/settai-mini-launch.log 2>&1 &
bash mini/status.sh
```
Each stage checkpoints and skips validated completed outputs. Logs: `mini/logs/pipeline.log`; failures: `mini/logs/FAILED.txt`. No log polling is needed. `status.sh` prints four lines. The only idle check is the requested silent GO check every 60 seconds.

Review `mini/data/samples_for_review.md` (30 samples: 10 English, 10 Tanglish, 10 Tamil; bake-off summary included), then authorize training:
```bash
touch mini/data/GO
```
The GO file is the training authorization. Remove it before intentionally starting a fresh corpus review. CPU training: 270M/500 chats/1 epoch, then 1B/all training chats/2 epochs, LoRA rank 16, lr 2e-4, context 512. Checkpoints/merged weights stay under `~/models/settai-mini/`. Models, tokens, GO, caches and logs are never committed. Never merge into main or change tags.

Gemma 3 4B and 12B QAT Q4_0 are downloaded using the existing HF login. Models run sequentially; 12B is skipped on its approximately 10 GB memory guard. A shared local rubric grades the same 30 prompts (not human-validated scores). 12B must beat 4B by >=10%; otherwise 4B wins. Few-shot TSVs are editable; `human_examples.tsv` takes precedence. A language below 1.2/2 overall or naturalness gets half its share; spare share goes to the strongest language. Baseline mix 65/25/10; 30 Hindi chats are reserved from English. All actual conversations are locally generated; the explicitly authorized authored few-shots are context only.

Assistant-only SFT masks user/system tokens, saves resumable optimizer checkpoints and merges LoRA. Evaluation compares base/tuned CPU replies on 40 held-out prompts and flags large training-text overlaps. Google LiteRT Torch 0.10.0 exports int4/int8 with context 1024 in an isolated CPU environment. Exact conversion errors are recorded; GGUF is exported as fallback before the pipeline fails. Nothing is claimed phone-tested.

Quick isolated test:
```bash
bash mini/run_pipeline.sh --dry-run --chats 5 --steps 20
```
This generates five local English chats and runs 20 real 270M CPU steps in an ignored workspace. It auto-creates only its isolated GO file, substitutes 270M for the downstream evaluation fixture, and does not execute 1B training or real Android conversion. Production GO/data are untouched.

Latest run: `abefde885932f57b`; dry run: `True`; elapsed so far: 49.6 seconds.
Counts: `{"en": 5}`; train/test: 4/1.
Run artifacts: `/home/scouter/Downloads/settai/set-tai/mini/.dry-run`. Weight root: `/home/scouter/models/settai-mini/smoke/abefde885932f57b`.

Measured stage summaries:
```json
{
  "practice": {
    "data_sha256": "15cd6999ad25dccb5883a4b305170ee386bdfae0739f39aa87ac320e2315d8f2",
    "base": "/home/scouter/models/gemma-3-270m-it",
    "epochs": 1,
    "steps": 20,
    "length": 128,
    "rows": 4,
    "seconds_this_run": 11.610908076007036,
    "global_step": 20,
    "losses": [
      4.7526,
      4.4329,
      3.5608,
      3.8531
    ],
    "fixed_probe_loss_before": 5.0070648193359375,
    "fixed_probe_loss_after": 3.633699119091034,
    "before_after": [
      {
        "prompt": [
          {
            "role": "system",
            "content": "You are Tutty, a cute little devil on the user's phone screen. Reply short, warm, playful, in character."
          },
          {
            "role": "user",
            "content": "This song is really catchy!"
          }
        ],
        "before": "Oh, my goodness! You're right, it'",
        "after": "Oh, my goodness! That's the best song ever"
      }
    ],
    "merged": "/home/scouter/models/settai-mini/smoke/abefde885932f57b/practice/merged",
    "smoke": true
  },
  "real": {
    "dry_run": true,
    "trained_1b": false,
    "merged": "/home/scouter/models/settai-mini/smoke/abefde885932f57b/practice/merged"
  },
  "conversion": {
    "dry_run": true,
    "conversion_executed": false
  }
}
```
