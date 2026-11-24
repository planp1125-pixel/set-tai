#!/usr/bin/env bash
# Run this whole job under a memory-limited user cgroup (see README).
set -euo pipefail
cd "$(dirname "$0")/.."
export LD_LIBRARY_PATH="$PWD/mini/vendor/vulkan/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export OMP_NUM_THREADS=8
export OPENBLAS_NUM_THREADS=1
export TOKENIZERS_PARALLELISM=false
mini/.venv/bin/python - <<'PY'
import json
from pathlib import Path
root=Path('mini')
teacher=json.loads((root/'teacher.json').read_text())
key=teacher['repo']+'@'+teacher['revision']
validation=json.loads((root/'teacher_validation.json').read_text()).get(key,{})
if validation.get('status')!='passed':
    raise SystemExit('Teacher quality gate has not passed. Corpus drafting is stopped; inspect mini/eval/teacher-qwen3-summary.md.')
PY
server_pid=""
cleanup() {
  if [[ -n "$server_pid" ]]; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT TERM
mini/vendor/llama.cpp/build/bin/llama-server \
  --model "$HOME/models/Qwen2.5-7B-Instruct-GGUF/qwen2.5-7b-instruct-q4_k_m-00001-of-00002.gguf" \
  --alias local-qwen-teacher --host 127.0.0.1 --port 18081 \
  --device Vulkan0 --n-gpu-layers 99 --ctx-size 4096 --parallel 1 \
  --batch-size 256 --ubatch-size 128 --threads 8 --threads-batch 8 \
  > mini/logs/teacher-server.log 2>&1 &
server_pid=$!
mini/.venv/bin/python - "$server_pid" <<'PY'
import os, sys, time
from urllib.request import urlopen
pid=int(sys.argv[1])
for _ in range(180):
    os.kill(pid,0)
    try:
        with urlopen('http://127.0.0.1:18081/health',timeout=2) as r:
            if r.status==200: break
    except Exception: time.sleep(1)
else: raise RuntimeError('Teacher did not become ready; inspect teacher-server.log')
print('Local Vulkan teacher ready.',flush=True)
PY
mini/.venv/bin/python -u mini/gen_chats.py "$@"
