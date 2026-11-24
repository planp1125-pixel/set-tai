#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export LD_LIBRARY_PATH="$PWD/mini/vendor/vulkan/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export OMP_NUM_THREADS=8
export OPENBLAS_NUM_THREADS=1
model_args=(--reasoning off --chat-template-kwargs '{"enable_thinking":false}')
test_args=()
server_log=mini/logs/qwen3-server.log
if [[ "${SETTAI_QWEN3_THINKING:-0}" == 1 ]]; then
  model_args=(--reasoning on --reasoning-budget 256 --chat-template-kwargs '{"enable_thinking":true}')
  test_args=(--thinking --probe-ids en-4-armour,tanglish-3-devil,ta-4-mask,ta-6-rocketgirl)
  server_log=mini/logs/qwen3-thinking-server.log
  if [[ "${SETTAI_QWEN3_COMPACT:-0}" == 1 ]]; then
    test_args+=(--compact)
    server_log=mini/logs/qwen3-compact-thinking-server.log
  fi
elif [[ "${SETTAI_QWEN3_COMPACT:-0}" == 1 ]]; then
  test_args=(--compact --probe-ids en-4-armour,tanglish-3-devil,ta-4-mask,ta-6-rocketgirl)
  server_log=mini/logs/qwen3-compact-server.log
fi
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
  --device Vulkan0 --n-gpu-layers 99 --ctx-size 4096 --parallel 1 \
  --batch-size 256 --ubatch-size 128 --threads 8 --threads-batch 8 \
  --jinja "${model_args[@]}" \
  > "$server_log" 2>&1 &
server_pid=$!
mini/.venv/bin/python - "$server_pid" <<'PY'
import os, sys, time
from urllib.request import urlopen
for _ in range(180):
    os.kill(int(sys.argv[1]),0)
    try:
        with urlopen('http://127.0.0.1:18081/health',timeout=2) as r:
            if r.status==200: break
    except Exception: time.sleep(1)
else: raise RuntimeError('Qwen3 did not become ready; inspect qwen3-server.log')
print('Local Qwen3 Vulkan teacher ready.',flush=True)
PY
mini/.venv/bin/python -u mini/test_teacher.py "${test_args[@]}"
