package com.insightcrayon.autotest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutoTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra("package_name")

        if (packageName != null) {
            // Start the test service
            AutoTestService.startTestNow(context, packageName)
        } else {
            // Try to get package name from SharedPreferences
            val prefs = context.getSharedPreferences("AutoTestPrefs", Context.MODE_PRIVATE)
            val savedPackageName = prefs.getString("target_package", null)

            if (savedPackageName != null) {
                AutoTestService.startTestNow(context, savedPackageName)
            }
        }
    }
}
