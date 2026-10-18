#!/usr/bin/env python3
"""Run the Wrapper integration suite; reject empty discovery and misleading exit code 0."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
log = root / 'work/validation/gametests.log'
log.parent.mkdir(parents=True, exist_ok=True)
expected = sum(len(re.findall(r'@GameTest\s*\(', p.read_text())) for p in
               (root / 'src/main/java/stone/mae2/gametests').glob('*.java'))
wrapper = ['cmd', '/c', 'gradlew.bat'] if os.name == 'nt' else ['sh', './gradlew']
with log.open('w', encoding='utf-8') as output:
    process = subprocess.Popen(wrapper + ['--no-daemon', 'runGameTestServer'], cwd=root,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
    lines = []
    for line in process.stdout:
        print(line, end='')
        output.write(line)
        lines.append(line)
    status = process.wait()
text = ''.join(lines)
complete = re.findall(r'(\d+) GAME TESTS COMPLETE', text)
passed = re.findall(r'All (\d+) required tests passed', text)
actual = int(complete[-1]) if complete else 0
success = status == 0 and actual == expected and passed and int(passed[-1]) == expected
result = {'expected': expected, 'executed': actual, 'passed': int(passed[-1]) if passed else 0,
          'process_exit_code': status, 'verified': bool(success), 'log': str(log.relative_to(root))}
(log.parent / 'gametests.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
sys.exit(0 if success else 1)
