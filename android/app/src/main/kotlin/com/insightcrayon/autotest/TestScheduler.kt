package com.insightcrayon.autotest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.util.Calendar
import kotlin.random.Random

object TestScheduler {
    private const val PREFS_NAME = "AutoTestPrefs"
    private const val PREF_NEXT_RUN = "next_run_time"
    const val ACTION_RUN_TEST = "com.insightcrayon.autotest.RUN_TEST"
    private const val ALARM_REQUEST_CODE = 42

    fun scheduleNextRun(context: Context, packageName: String) {
        val calendar = Calendar.getInstance().apply {
            val randomHour = 2 + Random.nextInt(22) // 2 ~ 23
            val randomMinute = Random.nextInt(60)
            set(Calendar.HOUR_OF_DAY, randomHour)
            set(Calendar.MINUTE, randomMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutoTestReceiver::class.java).apply {
            action = ACTION_RUN_TEST
            putExtra("package_name", packageName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        getPrefs(context).edit().putLong(PREF_NEXT_RUN, calendar.timeInMillis).apply()
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutoTestReceiver::class.java).apply {
            action = ACTION_RUN_TEST
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        getPrefs(context).edit().remove(PREF_NEXT_RUN).apply()
    }

    fun getNextRunTime(context: Context): Long? {
        val value = getPrefs(context).getLong(PREF_NEXT_RUN, -1L)
        return if (value > 0L) value else null
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
