from pathlib import Path
lines = Path('lib/main.dart').read_text(encoding='utf-8').splitlines()
for idx in range(138, 148):
    print(f"{idx+1}: {lines[idx]!r}")
