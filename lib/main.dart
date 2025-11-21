import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'log_monitor.dart';
import 'native_channel.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Play Store Auto Tester',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {

  final TextEditingController _packageController = TextEditingController();
  bool _isConfigured = false;
  bool _isRunning = false;
  String _statusMessage = '테스트할 앱을 설정해주세요';
  String? _selectedPackage;

  @override
  void initState() {
    super.initState();
    _loadConfiguration();
    _requestPermissions();
  }

  Future<void> _requestPermissions() async {
    await Permission.systemAlertWindow.request();
    await Permission.scheduleExactAlarm.request();

    // Accessibility Service 활성화 안내
    _showAccessibilityGuide();
  }

  void _showAccessibilityGuide() {
    Future.delayed(const Duration(seconds: 2), () {
      if (!mounted) return;

      showDialog(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('접근성 서비스 활성화 필요'),
          content: const Text(
            '자동 터치 기능을 사용하려면 접근성 서비스를 활성화해야 합니다.\n\n'
            '설정 방법:\n'
            '1. 설정 → 접근성\n'
            '2. "Play Store Auto Tester" 찾기\n'
            '3. 서비스 켜기\n\n'
            '지금 설정으로 이동하시겠습니까?'
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('나중에'),
            ),
            ElevatedButton(
              onPressed: () {
                Navigator.pop(context);
                _openAccessibilitySettings();
              },
              child: const Text('설정 열기'),
            ),
          ],
        ),
      );
    });
  }

  Future<void> _openAccessibilitySettings() async {
    try {
      await nativeChannel.invokeMethod('openAccessibilitySettings');
    } catch (e) {
      _showMessage('설정을 열 수 없습니다: $e');
    }
  }

  Future<void> _loadConfiguration() async {
    final prefs = await SharedPreferences.getInstance();
    final package = prefs.getString('target_package');
    final isRunning = prefs.getBool('is_running') ?? false;

    if (package != null && package.isNotEmpty) {
      setState(() {
        _selectedPackage = package;
        _packageController.text = package;
        _isConfigured = true;
        _isRunning = isRunning;
        _statusMessage = isRunning
            ? '자동화 실행 중 (매일 랜덤 시간에 실행됩니다)'
            : '설정 완료. 시작 버튼을 눌러주세요';
      });
    }
  }

  Future<void> _saveConfiguration() async {
    final package = _packageController.text.trim();
    if (package.isEmpty) {
      _showMessage('패키지명을 입력해주세요');
      return;
    }

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('target_package', package);
    await prefs.setBool('is_running', false);

    setState(() {
      _selectedPackage = package;
      _isConfigured = true;
      _statusMessage = '설정 완료. 시작 버튼을 눌러주세요';
    });

    _showMessage('설정이 저장되었습니다');
  }

  Future<void> _startAutomation() async {
    if (!_isConfigured || _selectedPackage == null) {
      _showMessage('먼저 앱을 설정해주세요');
      return;
    }

    try {
      await nativeChannel.invokeMethod('startAutomation', {
        'packageName': _selectedPackage,
      });

      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('is_running', true);

      setState(() {
        _isRunning = true;
        _statusMessage = '자동화 시작됨!\n매일 랜덤 시간(02:00~23:00)에\n앱을 3~4분간 자동 실행합니다';
      });

      _showMessage('자동화가 시작되었습니다!');
    } catch (e) {
      _showMessage('오류 발생: $e');
    }
  }

  Future<void> _stopAutomation() async {
    try {
      await nativeChannel.invokeMethod('stopAutomation');

      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('is_running', false);

      setState(() {
        _isRunning = false;
        _statusMessage = '자동화 중지됨';
      });

      _showMessage('자동화가 중지되었습니다');
    } catch (e) {
      _showMessage('오류 발생: $e');
    }
  }

  Future<void> _testNow() async {
    if (!_isConfigured || _selectedPackage == null) {
      _showMessage('먼저 앱을 설정해주세요');
      return;
    }

    try {
      await nativeChannel.invokeMethod('testNow', {
        'packageName': _selectedPackage,
      });
      _showMessage('테스트 실행 중... 잠시 후 앱이 실행됩니다');
    } catch (e) {
      _showMessage('오류 발생: $e');
    }
  }

  Future<void> _stopTestNow() async {
    try {
      await nativeChannel.invokeMethod('stopTestNow');
      _showMessage('테스트가 즉시 중지되었습니다');
    } catch (e) {
      _showMessage('오류 발생: $e');
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Play Store Auto Tester'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // 상태 카드
            Card(
              color: _isRunning ? Colors.green.shade50 : Colors.grey.shade50,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    Icon(
                      _isRunning ? Icons.check_circle : Icons.info,
                      size: 48,
                      color: _isRunning ? Colors.green : Colors.grey,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      _statusMessage,
                      textAlign: TextAlign.center,
                      style: const TextStyle(fontSize: 16),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),

            // 앱 선택 섹션
            const Text(
              '테스트할 앱 패키지명 입력',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),

            // 패키지명 입력
            TextField(
              controller: _packageController,
              decoration: InputDecoration(
                labelText: '앱 패키지명',
                hintText: '예: com.example.myapp',
                border: const OutlineInputBorder(),
                suffixIcon: IconButton(
                  icon: const Icon(Icons.clear),
                  onPressed: () => _packageController.clear(),
                ),
              ),
            ),
            const SizedBox(height: 12),

            ElevatedButton.icon(
              onPressed: _saveConfiguration,
              icon: const Icon(Icons.save),
              label: const Text('설정 저장'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
              ),
            ),

            const SizedBox(height: 24),
            const Divider(),
            const SizedBox(height: 24),

            // 제어 버튼들
            if (_isConfigured) ...[
              if (!_isRunning)
                ElevatedButton.icon(
                  onPressed: _startAutomation,
                  icon: const Icon(Icons.play_arrow),
                  label: const Text('자동화 시작'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.green,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                  ),
                )
              else
                ElevatedButton.icon(
                  onPressed: _stopAutomation,
                  icon: const Icon(Icons.stop),
                  label: const Text('자동화 중지'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                  ),
                ),

              const SizedBox(height: 12),

              OutlinedButton.icon(
                onPressed: _testNow,
                icon: const Icon(Icons.play_circle_outline),
                label: const Text('지금 바로 테스트'),
                style: OutlinedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 16),
                ),
              ),

              const SizedBox(height: 12),

              OutlinedButton.icon(
                onPressed: _stopTestNow,
                icon: const Icon(Icons.stop_circle_outlined),
                label: const Text('즉시 정지'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: Colors.red,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                ),
              ),
            ],

            const SizedBox(height: 32),

            // 안내 정보
              Card(
                color: Colors.blue.shade50,
                child: const Padding(
                  padding: EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '사용 방법',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                    ),
                    SizedBox(height: 8),
                    Text('1. 테스트할 앱을 Play Store 비공개 테스트로 설치'),
                    Text('2. 패키지명 입력 (예: com.example.myapp)'),
                    Text('3. "설정 저장" 클릭'),
                    Text('4. "자동화 시작" 클릭'),
                    SizedBox(height: 8),
                    Text(
                      '매일 랜덤 시간(02:00~23:00)에 앱이 자동 실행되고\n3~4분간 무작위 터치 후 종료됩니다.',
                      style: TextStyle(fontSize: 12, fontStyle: FontStyle.italic),
                    ),
                    SizedBox(height: 8),
                    Text(
                      '12대 폰 모두 설정 후 14일만 기다리면 프로덕션 승인!',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: Colors.green,
                      ),
                    ),
                  ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              ElevatedButton.icon(
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const LogMonitorPage()),
                ),
                icon: const Icon(Icons.bar_chart),
                label: const Text('자동화 로그 보기'),
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 16),
                ),
              ),
            ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _packageController.dispose();
    super.dispose();
  }
}
