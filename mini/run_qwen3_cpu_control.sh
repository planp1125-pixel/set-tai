#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export OMP_NUM_THREADS=8
export OPENBLAS_NUM_THREADS=1
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
  --model "$HOME/models/Qwen3-8B-GGUF/Qwen3-8B-Q4_K_M.gguf" \
  --alias local-qwen-teacher --host 127.0.0.1 --port 18081 \
  --device none --n-gpu-layers 0 --ctx-size 4096 --parallel 1 \
  --batch-size 256 --ubatch-size 128 --threads 8 --threads-batch 8 \
  --jinja --reasoning off --chat-template-kwargs '{"enable_thinking":false}' \
  > mini/logs/qwen3-cpu-server.log 2>&1 &
server_pid=$!
mini/.venv/bin/python - "$server_pid" <<'PY'
import os,sys,time
from urllib.request import urlopen
for _ in range(180):
    os.kill(int(sys.argv[1]),0)
    try:
        with urlopen('http://127.0.0.1:18081/health',timeout=2) as r:
            if r.status==200: break
    except Exception:time.sleep(1)
else:raise RuntimeError('CPU teacher did not become ready')
PY
mini/.venv/bin/python -u mini/test_teacher.py --backend cpu --topics fact,identity,food,phone
