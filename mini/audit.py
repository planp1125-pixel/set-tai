"""Verify repository scope and size before committing any Plan B artifacts."""
from pathlib import Path
import subprocess

changed=subprocess.check_output(['git','diff','--name-only','HEAD'],text=True).splitlines()
untracked=subprocess.check_output(['git','ls-files','--others','--exclude-standard'],text=True).splitlines()
paths=sorted(set(changed+untracked))
for name in paths:
    if not name.startswith('mini/'):
        raise SystemExit(f'Unexpected change outside mini/: {name}')
    path=Path(name)
    if path.is_file() and path.stat().st_size>20_000_000:
        raise SystemExit(f'File exceeds 20 MB: {name}')
    if path.suffix.lower() in ('.gguf','.safetensors','.bin','.pt','.pth','.litertlm','.tflite'):
        raise SystemExit(f'Weight/model file must not be committed: {name}')
print(f'Scope/size audit passed: {len(paths)} changed or new nonignored files, all under mini/, all <=20 MB.')
