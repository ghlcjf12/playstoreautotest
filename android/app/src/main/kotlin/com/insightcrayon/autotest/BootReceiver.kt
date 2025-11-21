package com.insightcrayon.autotest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("AutoTestPrefs", Context.MODE_PRIVATE)
            val isRunning = prefs.getBoolean("is_running", false)
            if (!isRunning) return
            val packageName = prefs.getString("target_package", null)
            if (!packageName.isNullOrBlank()) {
                TestScheduler.scheduleNextRun(context, packageName)
            }
        }
    }
}
