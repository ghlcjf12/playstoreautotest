package com.insightcrayon.autotest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.autotest/native"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startAutomation" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        val prefs = getSharedPreferences("AutoTestPrefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("target_package", packageName).apply()
                        TestScheduler.scheduleNextRun(applicationContext, packageName)
                        result.success("Automation scheduled for $packageName")
                    } else {
                        result.error("INVALID_PACKAGE", "Package name is null", null)
                    }
                }
                "stopAutomation" -> {
                    TestScheduler.cancel(applicationContext)
                    result.success("Automation stopped")
                }
                "testNow" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        AutoTestService.startTestNow(applicationContext, packageName)
                        result.success("Test started immediately")
                    } else {
                        result.error("INVALID_PACKAGE", "Package name is null", null)
                    }
                }
                "stopTestNow" -> {
                    AutoTestService.stopTestNow(applicationContext)
                    result.success("Test stopped immediately")
                }
                "getTestStats" -> {
                    val stats = TestLogDatabase.getInstance(applicationContext).getStats()
                    result.success(stats.toMap())
                }
                "getRecentLogs" -> {
                    val limit = call.argument<Int>("limit") ?: 50
                    val logs = TestLogDatabase.getInstance(applicationContext).getRecentLogs(limit)
                    result.success(logs.map { it.toMap() })
                }
                "clearLogs" -> {
                    TestLogDatabase.getInstance(applicationContext).clearAllLogs()
                    result.success(null)
                }
                "openAccessibilitySettings" -> {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        result.success("Opened accessibility settings")
                    } catch (e: Exception) {
                        result.error("ERROR", "Failed to open settings: ${e.message}", null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

}
