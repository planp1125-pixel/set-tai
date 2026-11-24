# Pipeline smoke test

- Five local Gemma-generated English conversations; train/test: 4/1.
- 270M CPU LoRA: 20 actual steps in 11.6 seconds.
- Loss on the same five training probes: 5.0071 → 3.6337.
- Re-running skipped completed training; global step remained 20.
- Status output: four lines. Failure logging was exercised by earlier failed attempts, then recovery succeeded.
- Production GO was not created.
- Full 30-prompt bake-off, 3000-chat corpus, 1B training and LiteRT conversion are not validated by this smoke fixture. Those run through the production command after its required gates.
