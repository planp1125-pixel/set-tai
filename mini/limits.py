"""Process-tree memory supervisor for long jobs (CPU + child server RSS).

The driver may account shared iGPU allocations separately. Prefer a systemd
MemoryMax cgroup when available; this watchdog also checks system headroom.
"""
import argparse
import os
import signal
import subprocess
import time
import re
import json
from pathlib import Path
import psutil

def gpu_bytes(processes):
    """Conservative AMD shared-iGPU resident memory, deduplicated by DRM client.

    Some driver memory can overlap host RSS; summing is intentionally cautious.
    Missing fdinfo support falls back to host + free-memory + cgroup checks.
    """
    seen=set(); total=0
    for proc in processes:
        try:
            for info in Path(f'/proc/{proc.pid}/fdinfo').iterdir():
                try:
                    if not os.readlink(f'/proc/{proc.pid}/fd/{info.name}').startswith('/dev/dri/'):
                        continue
                    fields=dict(line.split(':',1) for line in info.read_text().splitlines() if ':' in line)
                    key=(fields.get('drm-driver','').strip(),fields.get('drm-client-id','').strip())
                    if not key[1] or key in seen: continue
                    seen.add(key)
                    for name in ('drm-memory-vram','drm-memory-gtt'):
                        value=fields.get(name,'')
                        match=re.search(r'(\d+)\s+KiB',value)
                        if match: total+=int(match[1])*1024
                except (OSError,ValueError): pass
        except OSError: pass
    return total

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--gib', type=float, default=None)
    p.add_argument('--min-free-gib', type=float, default=1.5)
    p.add_argument('--total-gib', type=float, default=11.5)
    p.add_argument('command', nargs=argparse.REMAINDER)
    a = p.parse_args()
    cmd = a.command[1:] if a.command and a.command[0] == '--' else a.command
    if not cmd: p.error('command required after --')
    # iGPU allocations may not all appear in process RSS. Leave 6 GiB for
    # this fixed Q4 teacher/KV/buffer configuration instead of spending the
    # entire memory allowance on host RSS as well.
    if a.gib is None:
        a.gib=5.5 if any(Path(c).name in ('run_dataset.sh','run_qwen3_pilot.sh') for c in cmd) else 10.5
    print(f'Host process-tree RSS limit: {a.gib} GiB',flush=True)
    child = subprocess.Popen(cmd, start_new_session=True)
    def stop(sig, _frame=None):
        try: os.killpg(child.pid, sig)
        except ProcessLookupError: pass
    signal.signal(signal.SIGTERM, lambda s, f: stop(s))
    signal.signal(signal.SIGINT, lambda s, f: stop(s))
    peak = 0; gpu_peak=0; combined_peak=0
    def save_stats(code,reason=None):
        log=Path(__file__).resolve().parent/'logs'/f'resources-{child.pid}.json'
        log.write_text(json.dumps({'command':cmd,'host_rss_peak_bytes':peak,'drm_peak_bytes':gpu_peak,
                                  'rss_plus_drm_peak_bytes':combined_peak,'exit_code':code,'stop_reason':reason},indent=2)+'\n')
    while child.poll() is None:
        try:
            tree = [psutil.Process(child.pid)]
            tree += tree[0].children(recursive=True)
            rss = sum(proc.memory_info().rss for proc in tree if proc.is_running())
            gpu=gpu_bytes(tree)
            peak = max(peak, rss)
            gpu_peak=max(gpu_peak,gpu); combined_peak=max(combined_peak,rss+gpu)
            available = psutil.virtual_memory().available
            if rss > a.gib * 2**30 or rss+gpu>a.total_gib*2**30 or available < a.min_free_gib * 2**30:
                print(f'MEMORY STOP: tree RSS {rss/2**30:.2f} GiB; DRM {gpu/2**30:.2f} GiB; available {available/2**30:.2f} GiB', flush=True)
                stop(signal.SIGTERM)
                try: child.wait(timeout=15)
                except subprocess.TimeoutExpired: stop(signal.SIGKILL)
                save_stats(75,'memory limit or system headroom threshold')
                raise SystemExit(75)
        except (psutil.NoSuchProcess, psutil.ZombieProcess):
            pass
        time.sleep(0.5)
    print(f'Peak summed process RSS: {peak/2**30:.2f} GiB', flush=True)
    print(f'Peak DRM resident memory: {gpu_peak/2**30:.2f} GiB; conservative peak RSS + DRM: {combined_peak/2**30:.2f} GiB',flush=True)
    save_stats(child.returncode)
    raise SystemExit(child.returncode)
if __name__ == '__main__': main()
