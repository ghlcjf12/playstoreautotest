package com.insightcrayon.autotest

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.*

data class TestLog(
    val id: Long = 0,
    val packageName: String,
    val startTime: Long,
    val endTime: Long? = null,
    val touchCount: Int = 0,
    val status: String, // "running", "completed", "failed"
    val errorMessage: String? = null
)

data class TestStats(
    val totalTests: Int,
    val todayTests: Int,
    val weekTests: Int,
    val successRate: Float,
    val lastRunTime: Long?,
    val isCurrentlyRunning: Boolean
)

class TestLogDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "autotest_logs.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_LOGS = "test_logs"

        private const val COLUMN_ID = "id"
        private const val COLUMN_PACKAGE = "package_name"
        private const val COLUMN_START_TIME = "start_time"
        private const val COLUMN_END_TIME = "end_time"
        private const val COLUMN_TOUCH_COUNT = "touch_count"
        private const val COLUMN_STATUS = "status"
        private const val COLUMN_ERROR = "error_message"

        @Volatile
        private var instance: TestLogDatabase? = null

        fun getInstance(context: Context): TestLogDatabase {
            return instance ?: synchronized(this) {
                instance ?: TestLogDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_LOGS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PACKAGE TEXT NOT NULL,
                $COLUMN_START_TIME INTEGER NOT NULL,
                $COLUMN_END_TIME INTEGER,
                $COLUMN_TOUCH_COUNT INTEGER DEFAULT 0,
                $COLUMN_STATUS TEXT NOT NULL,
                $COLUMN_ERROR TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOGS")
        onCreate(db)
    }

    // 새 테스트 시작 기록
    fun startTest(packageName: String): TestLog {
        val db = writableDatabase
        val startTime = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(COLUMN_PACKAGE, packageName)
            put(COLUMN_START_TIME, startTime)
            put(COLUMN_STATUS, "running")
            put(COLUMN_TOUCH_COUNT, 0)
        }
        val id = db.insert(TABLE_LOGS, null, values)
        return TestLog(
            id = id,
            packageName = packageName,
            startTime = startTime,
            endTime = null,
            touchCount = 0,
            status = "running",
            errorMessage = null
        )
    }

    // 터치 카운트 증가
    fun incrementTouchCount(testId: Long) {
        val db = writableDatabase
        db.execSQL("UPDATE $TABLE_LOGS SET $COLUMN_TOUCH_COUNT = $COLUMN_TOUCH_COUNT + 1 WHERE $COLUMN_ID = ?", arrayOf(testId.toString()))
    }

    // 테스트 완료 기록
    fun completeTest(testId: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_END_TIME, System.currentTimeMillis())
            put(COLUMN_STATUS, "completed")
        }
        db.update(TABLE_LOGS, values, "$COLUMN_ID = ?", arrayOf(testId.toString()))
    }

    // 테스트 실패 기록
    fun failTest(testId: Long, error: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_END_TIME, System.currentTimeMillis())
            put(COLUMN_STATUS, "failed")
            put(COLUMN_ERROR, error)
        }
        db.update(TABLE_LOGS, values, "$COLUMN_ID = ?", arrayOf(testId.toString()))
    }

    // 현재 실행 중인 테스트 ID 가져오기
    fun getCurrentRunningTestId(): Long? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_LOGS,
            arrayOf(COLUMN_ID),
            "$COLUMN_STATUS = ?",
            arrayOf("running"),
            null, null,
            "$COLUMN_START_TIME DESC",
            "1"
        )

        return if (cursor.moveToFirst()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
            cursor.close()
            id
        } else {
            cursor.close()
            null
        }
    }

    // 통계 가져오기
    fun getStats(): TestStats {
        val db = readableDatabase
        val now = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val weekStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // 전체 테스트 수
        val totalCursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_LOGS WHERE $COLUMN_STATUS != 'running'", null)
        val totalTests = if (totalCursor.moveToFirst()) totalCursor.getInt(0) else 0
        totalCursor.close()

        // 오늘 테스트 수
        val todayCursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_LOGS WHERE $COLUMN_START_TIME >= ? AND $COLUMN_STATUS != 'running'",
            arrayOf(todayStart.toString())
        )
        val todayTests = if (todayCursor.moveToFirst()) todayCursor.getInt(0) else 0
        todayCursor.close()

        // 이번 주 테스트 수
        val weekCursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_LOGS WHERE $COLUMN_START_TIME >= ? AND $COLUMN_STATUS != 'running'",
            arrayOf(weekStart.toString())
        )
        val weekTests = if (weekCursor.moveToFirst()) weekCursor.getInt(0) else 0
        weekCursor.close()

        // 성공률
        val successCursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_LOGS WHERE $COLUMN_STATUS = 'completed'",
            null
        )
        val successCount = if (successCursor.moveToFirst()) successCursor.getInt(0) else 0
        successCursor.close()

        val successRate = if (totalTests > 0) (successCount.toFloat() / totalTests.toFloat() * 100) else 0f

        // 마지막 실행 시간
        val lastRunCursor = db.rawQuery(
            "SELECT $COLUMN_START_TIME FROM $TABLE_LOGS WHERE $COLUMN_STATUS != 'running' ORDER BY $COLUMN_START_TIME DESC LIMIT 1",
            null
        )
        val lastRunTime = if (lastRunCursor.moveToFirst()) lastRunCursor.getLong(0) else null
        lastRunCursor.close()

        // 현재 실행 중인지
        val runningCursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_LOGS WHERE $COLUMN_STATUS = 'running'",
            null
        )
        val isRunning = if (runningCursor.moveToFirst()) runningCursor.getInt(0) > 0 else false
        runningCursor.close()

        return TestStats(
            totalTests = totalTests,
            todayTests = todayTests,
            weekTests = weekTests,
            successRate = successRate,
            lastRunTime = lastRunTime,
            isCurrentlyRunning = isRunning
        )
    }

    // 최근 로그 가져오기 (최대 limit개)
    fun getRecentLogs(limit: Int = 50): List<TestLog> {
        val db = readableDatabase
        val logs = mutableListOf<TestLog>()

        val cursor = db.query(
            TABLE_LOGS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_START_TIME DESC",
            limit.toString()
        )

        while (cursor.moveToNext()) {
            logs.add(
                toTestLog(cursor)
            )
        }
        cursor.close()

        return logs
    }

    fun getLogById(testId: Long): TestLog? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_LOGS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(testId.toString()),
            null,
            null,
            null,
            "1"
        )
        return cursor.use {
            if (it.moveToFirst()) {
                toTestLog(it)
            } else {
                null
            }
        }
    }

    private fun toTestLog(cursor: Cursor): TestLog {
        val endTimeIndex = cursor.getColumnIndexOrThrow(COLUMN_END_TIME)
        val errorIndex = cursor.getColumnIndexOrThrow(COLUMN_ERROR)
        return TestLog(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
            packageName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PACKAGE)),
            startTime = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_START_TIME)),
            endTime = if (cursor.isNull(endTimeIndex)) null else cursor.getLong(endTimeIndex),
            touchCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TOUCH_COUNT)),
            status = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATUS)),
            errorMessage = if (cursor.isNull(errorIndex)) null else cursor.getString(errorIndex)
        )
    }

    // 모든 로그 삭제 (초기화)
    fun clearAllLogs() {
        val db = writableDatabase
        db.delete(TABLE_LOGS, null, null)
    }

    // 오래된 로그 삭제 (30일 이상)
    fun deleteOldLogs() {
        val db = writableDatabase
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        db.delete(TABLE_LOGS, "$COLUMN_START_TIME < ?", arrayOf(thirtyDaysAgo.toString()))
    }
}

fun TestStats.toMap(): Map<String, Any?> {
    return mapOf(
        "totalTests" to totalTests,
        "todayTests" to todayTests,
        "weekTests" to weekTests,
        "successRate" to successRate,
        "lastRunTime" to lastRunTime,
        "isCurrentlyRunning" to isCurrentlyRunning
    )
}

fun TestLog.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "packageName" to packageName,
        "startTime" to startTime,
        "endTime" to endTime,
        "touchCount" to touchCount,
        "status" to status,
        "errorMessage" to errorMessage
    )
}

object TestLogTracker {
    @Volatile
    var currentTestId: Long? = null
}
