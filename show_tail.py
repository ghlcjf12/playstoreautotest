from pathlib import Path
text = Path('lib/log_monitor.dart').read_text(encoding='utf-8')
print(repr(text[-10:]))
