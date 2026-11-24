.PHONY: status inspect check characters resume stop review quarantine audit pilot-qwen3 pilot-status cpu-control thinking-control compact-control compact-thinking-control

status:
	@mini/.venv/bin/python mini/status.py
	@systemctl --user show settai-mini-draft -p ActiveState -p SubState -p MemoryCurrent -p MemoryPeak -p MemoryMax

inspect:
	@mini/.venv/bin/python mini/status.py --inspect

check:
	@mini/.venv/bin/pip check
	@mini/.venv/bin/python mini/check.py

gemma-status:
	@mini/.venv/bin/python mini/gemma_status.py

gemma-select:
	@mini/.venv/bin/python mini/select_gemma_teacher.py

gemma-generate:
	@mini/.venv/bin/python -c "import json,sys;sys.path.insert(0,'mini');from gemma_jobs import start;print(start(json.load(open('mini/teachers/selected.json'))['size'],'generate'))"

characters:
	@mini/.venv/bin/python mini/make_characters.py

resume:
	@systemctl --user restart settai-mini-draft

stop:
	@systemctl --user stop settai-mini-draft

review:
	@mini/.venv/bin/python mini/status.py --review

quarantine:
	@mini/.venv/bin/python mini/quarantine_pilot.py

audit:
	@git status --short --branch
	@mini/.venv/bin/python mini/audit.py

pilot-qwen3:
	@systemd-run --user --unit=settai-mini-qwen3-pilot --property=Type=exec --property=MemoryMax=11G --property=MemoryHigh=10G --property=MemorySwapMax=512M --property="StandardOutput=append:$(CURDIR)/mini/logs/qwen3-pilot.log" --property=StandardError=inherit --working-directory="$(CURDIR)" /usr/bin/nohup "$(CURDIR)/mini/.venv/bin/python" -u mini/limits.py -- /usr/bin/bash mini/run_qwen3_pilot.sh

pilot-status:
	@systemctl --user show settai-mini-qwen3-pilot -p ActiveState -p SubState -p MemoryCurrent -p MemoryPeak -p MemoryMax -p Result
	@tail -n 15 mini/logs/qwen3-pilot.log

cpu-control:
	@! systemctl --user is-active --quiet settai-mini-qwen3-pilot
	@systemd-run --user --unit=settai-mini-qwen3-cpu --property=Type=exec --property=MemoryMax=11G --property=MemoryHigh=10G --property=MemorySwapMax=512M --property="StandardOutput=append:$(CURDIR)/mini/logs/qwen3-cpu.log" --property=StandardError=inherit --working-directory="$(CURDIR)" /usr/bin/nohup "$(CURDIR)/mini/.venv/bin/python" -u mini/limits.py -- /usr/bin/bash mini/run_qwen3_cpu_control.sh

thinking-control:
	@! systemctl --user is-active --quiet settai-mini-qwen3-pilot
	@! systemctl --user is-active --quiet settai-mini-qwen3-cpu
	@systemd-run --user --unit=settai-mini-qwen3-thinking --setenv=SETTAI_QWEN3_THINKING=1 --property=Type=exec --property=MemoryMax=11G --property=MemoryHigh=10G --property=MemorySwapMax=512M --property="StandardOutput=append:$(CURDIR)/mini/logs/qwen3-thinking.log" --property=StandardError=inherit --working-directory="$(CURDIR)" /usr/bin/nohup "$(CURDIR)/mini/.venv/bin/python" -u mini/limits.py -- /usr/bin/bash mini/run_qwen3_pilot.sh

compact-control:
	@! systemctl --user is-active --quiet settai-mini-qwen3-thinking
	@! systemctl --user is-active --quiet settai-mini-qwen3-cpu
	@systemd-run --user --unit=settai-mini-qwen3-compact --setenv=SETTAI_QWEN3_COMPACT=1 --property=Type=exec --property=MemoryMax=11G --property=MemoryHigh=10G --property=MemorySwapMax=512M --property="StandardOutput=append:$(CURDIR)/mini/logs/qwen3-compact.log" --property=StandardError=inherit --working-directory="$(CURDIR)" /usr/bin/nohup "$(CURDIR)/mini/.venv/bin/python" -u mini/limits.py -- /usr/bin/bash mini/run_qwen3_pilot.sh

compact-thinking-control:
	@! systemctl --user is-active --quiet settai-mini-qwen3-compact
	@! systemctl --user is-active --quiet settai-mini-qwen3-cpu
	@systemd-run --user --unit=settai-mini-qwen3-compact-thinking --setenv=SETTAI_QWEN3_COMPACT=1 --setenv=SETTAI_QWEN3_THINKING=1 --property=Type=exec --property=MemoryMax=11G --property=MemoryHigh=10G --property=MemorySwapMax=512M --property="StandardOutput=append:$(CURDIR)/mini/logs/qwen3-compact-thinking.log" --property=StandardError=inherit --working-directory="$(CURDIR)" /usr/bin/nohup "$(CURDIR)/mini/.venv/bin/python" -u mini/limits.py -- /usr/bin/bash mini/run_qwen3_pilot.sh
