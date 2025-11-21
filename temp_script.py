from pathlib import Path
text = Path('lib/main.dart').read_text(encoding='utf-8')
for idx, line in enumerate(text.splitlines(), 1):
    if 600 <= idx <= 630:
        print(f"{idx}: {line!r}")
