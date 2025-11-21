from pathlib import Path
lines = Path('lib/log_monitor.dart').read_text(encoding='utf-8').splitlines()
for idx in range(125, 180):
    if idx < len(lines):
        print(f"{idx+1}: {lines[idx]}")
