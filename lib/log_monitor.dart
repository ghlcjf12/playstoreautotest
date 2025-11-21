import 'package:flutter/material.dart';

import 'native_channel.dart';

class LogMonitorPage extends StatefulWidget {
  const LogMonitorPage({super.key});

  @override
  State<LogMonitorPage> createState() => _LogMonitorPageState();
}

class _LogMonitorPageState extends State<LogMonitorPage> {
  late Future<List<dynamic>> _combinedFuture;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  void _loadData() {
    setState(() {
      _combinedFuture = Future.wait([_fetchStats(), _fetchLogs()]);
    });
  }

  Future<Map<String, dynamic>> _fetchStats() async {
    final raw = await nativeChannel.invokeMethod('getTestStats');
    return Map<String, dynamic>.from(raw as Map<dynamic, dynamic>);
  }

  Future<List<Map<String, dynamic>>> _fetchLogs() async {
    final raw = await nativeChannel.invokeMethod('getRecentLogs', {'limit': 50});
    final list = raw as List<dynamic>;
    return list.map((entry) => Map<String, dynamic>.from(entry as Map<dynamic, dynamic>)).toList();
  }

  Future<void> _clearLogs() async {
    await nativeChannel.invokeMethod('clearLogs');
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('로그를 초기화했습니다')),
    );
    _loadData();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('자동화 로그'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _loadData),
          IconButton(icon: const Icon(Icons.delete_forever), onPressed: _clearLogs),
        ],
      ),
      body: FutureBuilder<List<dynamic>>(
        future: _combinedFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(child: Text('데이터를 불러올 수 없습니다: '));
          }
          final stats = snapshot.data![0] as Map<String, dynamic>;
          final logs = snapshot.data![1] as List<Map<String, dynamic>>;
          return RefreshIndicator(
            onRefresh: () async => _loadData(),
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                _buildStatsCard(stats),
                const SizedBox(height: 16),
                const Text('최근 로그', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                ...logs.map(_buildLogTile),
                if (logs.isEmpty)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 48),
                    child: Center(child: Text('기록이 없습니다.')),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildStatsCard(Map<String, dynamic> stats) {
    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: stats.entries.map((entry) {
            return Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(entry.key, style: const TextStyle(color: Colors.black54)),
                  Text(entry.value?.toString() ?? '-', style: const TextStyle(fontWeight: FontWeight.bold)),
                ],
              ),
            );
          }).toList(),
        ),
      ),
    );
  }

  Widget _buildLogTile(Map<String, dynamic> log) {
    final status = (log['status'] ?? 'unknown').toString();
    final packageName = (log['packageName'] ?? 'unknown').toString();
    final touchCount = log['touchCount']?.toString() ?? '0';
    final startTime = _formatTimestamp(log['startTime']);
    final endTime = _formatTimestamp(log['endTime']);
    final statusColor = _statusColor(status);
    return Card(
      elevation: 1,
      margin: const EdgeInsets.symmetric(vertical: 6),
      child: ListTile(
        title: Text(packageName),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
        Text('$startTime → $endTime'),
        Text('터치 횟수: $touchCount'),
          ],
        ),
        trailing: Chip(
          label: Text(status, style: const TextStyle(fontSize: 12)),
          backgroundColor: statusColor.withAlpha((0.2 * 255).round()),
          labelStyle: TextStyle(color: statusColor),
        ),
      ),
    );
  }
}

Color _statusColor(String status) {
  final normalized = status.toLowerCase();
  if (normalized == 'running') return Colors.orange;
  if (normalized == 'completed') return Colors.green;
  if (normalized == 'failed') return Colors.red;
  return Colors.blueGrey;
}

String _formatTimestamp(dynamic value) {
  if (value == null) return '미정';
  final int time = value is int ? value : int.tryParse(value.toString()) ?? 0;
  if (time <= 0) return '미정';
  final dt = DateTime.fromMillisecondsSinceEpoch(time);
  return '${dt.year}-${_twoDigits(dt.month)}-${_twoDigits(dt.day)} ${_twoDigits(dt.hour)}:${_twoDigits(dt.minute)}';
}
String _twoDigits(int value) => value.toString().padLeft(2, '0');
