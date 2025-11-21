from pathlib import Path
path = Path('lib/log_monitor.dart')
text = path.read_text(encoding='utf-8')
if 'String _twoDigits' not in text:
    text = text.rstrip() + '\n'
    text += "String _twoDigits(int value) => value.toString().padLeft(2, '0');\n"
    path.write_text(text, encoding='utf-8')
