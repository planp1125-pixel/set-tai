#!/usr/bin/env bash
# One entry point. All model artifacts remain outside the repository.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p mini/logs mini/.pipeline
exec 9>mini/.pipeline/run.lock
if ! flock -n 9; then echo 'A pipeline is already running.' >&2; exit 1; fi
exec >>mini/logs/pipeline.log 2>&1
trap 'code=$?; if [[ ! -s mini/logs/FAILED.txt ]]; then printf "Pipeline entry failed (exit %s)\n" "$code" >mini/logs/FAILED.txt; fi; exit "$code"' ERR
if [[ ! -x mini/.venv/bin/python ]]; then
  python3 -m venv mini/.venv
  mini/.venv/bin/pip install -r mini/requirements.txt
fi
export OMP_NUM_THREADS=8 OPENBLAS_NUM_THREADS=1 TOKENIZERS_PARALLELISM=false
export CUDA_VISIBLE_DEVICES=""
if systemctl --user show-environment >/dev/null 2>&1; then
  systemd-run --user --scope --quiet --property=MemoryMax=11G --property=MemoryHigh=10G --property=MemorySwapMax=512M mini/.venv/bin/python -u mini/pipeline.py "$@"
else
  mini/.venv/bin/python -u mini/pipeline.py "$@"
fi
