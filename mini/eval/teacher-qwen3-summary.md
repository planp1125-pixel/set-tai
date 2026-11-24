# Qwen3-8B local teacher test — 24 November 2026

## Decision

Do not admit this pilot to training. This specific Qwen3-8B Q4_K_M setup did not meet the Tamil/Tanglish quality gate, including after removing conflicting English-first source instructions and enabling thinking. No student training or final corpus generation has started. This is a small diagnostic, not a general ranking of Qwen models.

## Model and test setup

Official `Qwen/Qwen3-8B-GGUF`, revision `7c41481f57cb95916b40956ab2f0b139b296d974`, Apache-2.0. File: `Qwen3-8B-Q4_K_M.gguf`, 5,027,783,488 bytes, stored under `~/models/Qwen3-8B-GGUF/`. Downloaded SHA-256 matches the official pinned LFS checksum: `d98cdcbd03e17ce47681435b5150e34c1417f50b5c0019dd560e4882c5745785`.

llama.cpp revision and Vulkan driver are recorded in `../tools.json`. Eight threads, 4096-token context, one request at a time. CPU and GPU tests ran sequentially. Thinking controls used a 256-token reasoning budget; only visible final replies were retained.

The 21 baseline probes cover all 13 characters in English, Tanglish and Tamil script. Follow-up controls repeat selected difficult cases. All probes are evaluation-only authored prompts, explicitly `training_eligible=false`. These prompts and replies are never training conversations.

## Measurements

Times include prompt processing and decoding; they are teacher timings on this machine, not phone latency or student-model CPU benchmarks. Controls have different probe subsets, so means are not directly comparable as speed benchmarks.

| Configuration | Probes | Mean seconds/reply | Misplaced/multiple action tags | Detail |
|---|---:|---:|---:|---|
| Full source, Vulkan, no thinking | 21 | 11.1 | 13 | [Replies](teacher-qwen3-report.md) |
| Full source, CPU, no thinking | 9 | 30.9 | 7 | [Replies](teacher-qwen3-cpu-report.md) |
| Full source, Vulkan, thinking | 4 | 42.1 | 1 | [Replies](teacher-qwen3-thinking-report.md) |
| Compact, Vulkan, no thinking | 4 | 15.1 | 1 | [Replies](teacher-qwen3-compact-report.md) |
| Compact, Vulkan, thinking | 4 | 41.6 | 1 | [Replies](teacher-qwen3-compact-thinking-report.md) |

Total: **42 probe responses**, including repeated controls. Wrong action choices and other semantic failures are separate from the mechanical tag-position count.

## Findings from direct inspection

- English replies often show the intended character voice, but full-source non-thinking runs answered seven plus five as thirteen on both CPU and Vulkan. Compact or thinking controls answered twelve. Character style must not override facts.
- Tanglish commonly became English, mixed in Tamil script despite the Latin-only requirement, repeated the prompt, or contained nonsensical Tamil-like phrases. This persisted in the compact thinking control.
- Tamil script presence did not establish coherent Tamil. Food answers contained awkward or irrelevant suggestions, and several replies failed to answer the actual question naturally.
- Capability honesty was unreliable. The baseline Tamil camera reply first denied camera access and then suggested it could see the user. The compact thinking privacy reply instead discussed whether a camera could photograph the character, which missed the question.
- Many action tags were at the end or in the middle; a dance request sometimes produced spin. These should be rejected or prevented with structured teacher fields before any training dataset is admitted.
- The source evidence had an English-first instruction conflicting with requested Tamil/Tanglish. A compact control retained the personality and removed that instruction. This corrected the arithmetic example, but did not fix the language failures. The drafting generator now excludes the conflicting legacy identity evidence from its teacher instructions.
- CPU controls reproduced the main defects. This does not prove every backend detail equivalent, but the issue is not isolated to Vulkan.

## Memory and process state

Every test job used an 11 GiB cgroup cap with a 10 GiB high watermark and at most 512 MiB swap. CPU control peak summed process RSS was 9.31 GiB. Later Vulkan controls also tracked AMD DRM resident memory: conservative peak host RSS plus DRM was at most 6.13 GiB across those controls. The first Vulkan baseline predated DRM logging, so its host-only peak is not a total GPU-memory measurement.

The supervisor now enforces an 11.5 GiB conservative RSS-plus-DRM limit, a separate 5.5 GiB host RSS cap for Vulkan teacher jobs, and at least 1.5 GiB system available memory. These checks supplement the cgroup because integrated GPU accounting may overlap or sit outside ordinary host RSS. All pilot services exited; no teacher or training job remains active.

## Reproduce

From the repository root:

```bash
nohup mini/.venv/bin/python -u mini/download_teacher.py --qwen3 > mini/logs/download-qwen3.log 2>&1 &
make -f mini/Makefile pilot-qwen3
make -f mini/Makefile cpu-control
make -f mini/Makefile thinking-control
make -f mini/Makefile compact-control
make -f mini/Makefile compact-thinking-control
```

Run jobs sequentially; wait for each service to finish before starting the next. Existing completed probes resume without repeating inference. For an existing transient unit, use `systemctl --user restart <unit>` instead of creating the same unit again. Logs and quarantined JSONL checkpoints stay ignored under `mini/logs/` and `mini/data/pilot*/`.

## Next gate

A reliable local Tamil/Tanglish teacher or human-vetted native-language data is needed before drafting the requested 3,000-chat corpus. A next model is a candidate to test, not a promised fix. The final 30 random corpus samples and the owner’s explicit **go** remain mandatory before training. No 270M practice, 1B fine-tune, model merge, Android conversion, commit or push has been performed.

Sources: [official Qwen3 GGUF](https://huggingface.co/Qwen/Qwen3-8B-GGUF), [Qwen3 model card](https://huggingface.co/Qwen/Qwen3-8B).
