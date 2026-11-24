#!/usr/bin/env bash
# Invoke through limits.py and a capped user service; never loads two teachers.
set -euo pipefail
cd "$(dirname "$0")/.."
teacher_size="$1"
task="$2"
shift 2
export LD_LIBRARY_PATH="$PWD/mini/vendor/vulkan/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export OMP_NUM_THREADS=8
export OPENBLAS_NUM_THREADS=1
export TOKENIZERS_PARALLELISM=false
model_path=$(mini/.venv/bin/python -c 'import json,sys;print(json.load(open("mini/teachers/gemma3-"+sys.argv[1]+".json"))["path"])' "$teacher_size")
server_pid=""
parallel=1
context=2048
batch=128
ubatch=64
workers=1
gpu_layers=99
if [[ "$teacher_size" == 12b ]]; then context=1536; batch=64; ubatch=32; gpu_layers=32; fi
if [[ "$task" == generate && "$teacher_size" == 4b ]]; then parallel=2; context=4096; workers=2; fi
export SETTAI_TEACHER_GPU_LAYERS="$gpu_layers"
export SETTAI_TEACHER_CONTEXT="$context"
cleanup() {
 if [[ -n "$server_pid" ]];then kill "$server_pid" 2>/dev/null || true; wait "$server_pid" 2>/dev/null || true;fi
}
trap cleanup EXIT
trap 'exit 130' INT TERM
mini/vendor/llama.cpp/build/bin/llama-server \
 --model "$model_path" --alias local-settai-teacher --host 127.0.0.1 --port 18081 --cache-ram 0 --fit off \
 --device Vulkan0 --n-gpu-layers "$gpu_layers" --ctx-size "$context" --parallel "$parallel" \
 --batch-size "$batch" --ubatch-size "$ubatch" --threads 8 --threads-batch 8 --jinja \
 > "mini/logs/gemma-${teacher_size}-server.log" 2>&1 &
server_pid=$!
mini/.venv/bin/python - "$server_pid" <<'PY'
import os,sys,time
from urllib.request import urlopen
for _ in range(240):
 os.kill(int(sys.argv[1]),0)
 try:
  with urlopen('http://127.0.0.1:18081/health',timeout=2) as r:
   if r.status==200:break
 except Exception:time.sleep(1)
else:raise RuntimeError('Teacher did not become ready; inspect Gemma server log')
print('Gemma Vulkan teacher ready.',flush=True)
PY
case "$task" in
 bakeoff) mini/.venv/bin/python -u mini/bakeoff_gemma.py --size "$teacher_size" "$@" ;;
 generate) mini/.venv/bin/python -u mini/gen_gemma_chats.py --size "$teacher_size" --workers "$workers" "$@" ;;
 diagnostic) mini/.venv/bin/python -u mini/gemma_diagnostic.py ;;
 *) echo "Unknown task: $task" >&2;exit 2 ;;
esac
