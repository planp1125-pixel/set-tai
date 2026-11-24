"""Parse mini scripts and verify shell syntax without launching any model."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parent
for p in root.glob('*.py'):compile(p.read_text(),str(p),'exec')
for p in root.glob('*.sh'):subprocess.run(['bash','-n',str(p)],check=True)
print('All mini Python and shell scripts pass syntax checks.')
