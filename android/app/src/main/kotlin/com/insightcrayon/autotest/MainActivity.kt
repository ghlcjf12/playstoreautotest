package com.insightcrayon.autotest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.*

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.autotest/native"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startAutomation" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        scheduleRandomDailyTasks(packageName)
                        result.success("Automation started for $packageName")
                    } else {
                        result.error("INVALID_PACKAGE", "Package name is null", null)
                    }
                }
                "stopAutomation" -> {
                    cancelScheduledTasks()
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
                    AutoTouchAccessibilityService.stopTouching(applicationContext)
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

    private fun scheduleRandomDailyTasks(packageName: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Save package name to SharedPreferences
        val prefs = getSharedPreferences("AutoTestPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("target_package", packageName).apply()

        // Schedule daily alarm at random time between 2 AM and 11 PM
        val calendar = Calendar.getInstance()
        val random = Random()

        // Set random hour between 2 and 23
        val randomHour = 2 + random.nextInt(22)
        val randomMinute = random.nextInt(60)

        calendar.set(Calendar.HOUR_OF_DAY, randomHour)
        calendar.set(Calendar.MINUTE, randomMinute)
        calendar.set(Calendar.SECOND, 0)

        // If the time is in the past, schedule for tomorrow
        if (calendar.timeInMillis < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(this, AutoTestReceiver::class.java).apply {
            putExtra("package_name", packageName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule repeating alarm
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun cancelScheduledTasks() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AutoTestReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
