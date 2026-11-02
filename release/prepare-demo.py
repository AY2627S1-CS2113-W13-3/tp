"""Prepare fictional demo records through the submitted executable, without overwriting data."""
import argparse
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('jar', type=Path)
parser.add_argument('snapshot', type=Path)
args = parser.parse_args()
jar, snapshot = args.jar.resolve(), args.snapshot.resolve()
if snapshot.exists():
    parser.error('Choose a new snapshot path; existing data is never overwritten.')
names = ['Digital oscilloscope', 'Function generator', 'Bench multimeter',
         'DC power supply', 'Logic analyser', 'Soldering station',
         'Thermal camera', 'Digital microscope', 'LCR meter', 'Spectrum analyser']
commands = []
for index in range(20):
    commands.append(f'add LAB-{index+1:03} n/{names[index//2]} c/{names[index//2]} '
                    f'm/Teaching Series {index//2+1} l/Lab {"A" if index%2==0 else "B"}')
for index, name in enumerate(['Jamie Tan', 'Aisha Lim', 'Ravi Kumar', 'Mei Chen', 'Daniel Lee']):
    commands.append(f'add-borrower A012345{index}X n/{name} '
                    f'p/9123456{index} e/demo{index+1}@u.nus.edu')
commands += ['list', 'exit']
result = subprocess.run(['java', '-jar', str(jar), str(snapshot)],
                        input='\n'.join(commands)+'\n', text=True,
                        capture_output=True, timeout=60, check=True)
if 'Error:' in result.stdout or not snapshot.is_file():
    raise SystemExit(result.stdout + result.stderr)
text = snapshot.read_text(encoding='utf-8')
if 'equipment.count=20' not in text or 'borrower.count=5' not in text:
    raise SystemExit('Unexpected record counts; inspect ' + str(snapshot))
print('Created 20 equipment units and five fictional borrowers at', snapshot)

