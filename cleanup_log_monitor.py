from pathlib import Path
path = Path('lib/log_monitor.dart')
text = path.read_text(encoding='utf-8')
artifact = '*** End Patch"}{"}*** End Patch**Errabcdefghijklmnopqrstuvwxy}'
if artifact in text:
    text = text.replace(artifact, '')
path.write_text(text, encoding='utf-8')
