from pathlib import Path
path = Path('lib/main.dart')
text = path.read_text(encoding='utf-8')
marker = 'class TestStats {'
idx = text.find(marker)
if idx == -1:
    path.write_text(text.rstrip() + '\n', encoding='utf-8')
else:
    path.write_text(text[:idx].rstrip() + '\n', encoding='utf-8')
