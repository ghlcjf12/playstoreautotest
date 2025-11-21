from pathlib import Path
path = Path('lib/main.dart')
text = path.read_text(encoding='utf-8')
target = "    try {\n      // 안드로이드 네이티브로 자동화 시작\n      final result = await nativeChannel.invokeMethod('startAutomation', {\n        'packageName': _selectedPackage,\n      });"
replacement = "    try {\n      await nativeChannel.invokeMethod('startAutomation', {\n        'packageName': _selectedPackage,\n      });"
if target not in text:
    raise SystemExit('target not found')
path.write_text(text.replace(target, replacement, 1), encoding='utf-8')
