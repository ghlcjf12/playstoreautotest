package com.insightcrayon.autotest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutoTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("AutoTestPrefs", Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean("is_running", false)
        if (!isRunning) return

        val packageName = intent?.getStringExtra("package_name") ?: prefs.getString("target_package", null)

        if (!packageName.isNullOrBlank()) {
            AutoTestService.startTestNow(context, packageName)
            TestScheduler.scheduleNextRun(context, packageName)
        }
    }
}
