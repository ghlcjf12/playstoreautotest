from pathlib import Path
path = Path('lib/log_monitor.dart')
text = path.read_text(encoding='utf-8')
while text.endswith('\\n'):
    text = text[:-2]
text = text.rstrip() + '\n'
path.write_text(text, encoding='utf-8')
