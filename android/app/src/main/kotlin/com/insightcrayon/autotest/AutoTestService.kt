package com.insightcrayon.autotest

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast

class AutoTestService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private lateinit var targetPackageName: String
    private val logDb by lazy { TestLogDatabase.getInstance(this) }
    private var currentLogId: Long? = null

    override fun onCreate() {
        super.onCreate()
        FirebaseLogUploader.initialize(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        targetPackageName = intent?.getStringExtra("package_name") ?: return START_NOT_STICKY

        Toast.makeText(this, "Automation test started: $targetPackageName", Toast.LENGTH_SHORT).show()

        val log = logDb.startTest(targetPackageName)
        currentLogId = log.id
        if (log.id > 0) {
            TestLogTracker.currentTestId = log.id
        }
        FirebaseLogUploader.syncLog(log)

        // Step 1: Launch target app
        launchTargetApp()

        handler.postDelayed({
            stopTestAndGoHome()
        }, SESSION_DURATION_MS)

        return START_NOT_STICKY
    }

    private fun launchTargetApp() {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                isRunning = true
            } else {
                Toast.makeText(this, "Target app not found: $targetPackageName", Toast.LENGTH_LONG).show()
                failCurrentTest("target app not found")
                stopSelf()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Launch failed: ${e.message}", Toast.LENGTH_LONG).show()
            failCurrentTest("launch error: ${e.message ?: "unknown"}")
            stopSelf()
        }
    }

    private fun stopTestAndGoHome() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)

        // Go to home screen
        handler.postDelayed({
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)

            Toast.makeText(this, "Automation test complete", Toast.LENGTH_SHORT).show()

            completeCurrentTest()

            stopSelf()
        }, 1000)
    }

    private fun completeCurrentTest() {
        val logId = currentLogId
        if (logId != null && TestLogTracker.currentTestId == logId) {
            logDb.completeTest(logId)
            FirebaseLogUploader.syncLog(logDb.getLogById(logId))
            TestLogTracker.currentTestId = null
        }
        currentLogId = null
    }

    private fun failCurrentTest(message: String) {
        val logId = currentLogId
        if (logId != null && TestLogTracker.currentTestId == logId) {
            logDb.failTest(logId, message)
            FirebaseLogUploader.syncLog(logDb.getLogById(logId))
            TestLogTracker.currentTestId = null
        }
        currentLogId = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        if (currentLogId != null && TestLogTracker.currentTestId == currentLogId) {
            failCurrentTest("service destroyed")
        }
    }

    companion object {
        private const val SESSION_DURATION_MS = 30_000L

        fun startTestNow(context: Context, packageName: String) {
            val intent = Intent(context, AutoTestService::class.java).apply {
                putExtra("package_name", packageName)
            }
            context.startService(intent)
        }

        fun stopTestNow(context: Context) {
            val db = TestLogDatabase.getInstance(context)
            FirebaseLogUploader.initialize(context.applicationContext)
            TestLogTracker.currentTestId?.let {
                db.failTest(it, "Stopped manually")
                FirebaseLogUploader.syncLog(db.getLogById(it))
                TestLogTracker.currentTestId = null
            }
            val intent = Intent(context, AutoTestService::class.java)
            context.stopService(intent)
        }
    }
}
