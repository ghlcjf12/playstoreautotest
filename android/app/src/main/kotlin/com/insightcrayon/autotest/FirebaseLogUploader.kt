package com.insightcrayon.autotest

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object FirebaseLogUploader {
    private const val TAG = "FirebaseLogUploader"
    private const val CONFIG_FILE = "firebase_config.json"
    private const val DOCUMENT_URL_TEMPLATE =
        "https://firestore.googleapis.com/v1/projects/%s/databases/(default)/documents/%s/%s?key=%s"

    private val executor = Executors.newSingleThreadExecutor()
    private var config: FirebaseConfig? = null
    private var initialized = false

    data class FirebaseConfig(
        val projectId: String,
        val apiKey: String,
        val collection: String = "autoTestLogs"
    ) {
        fun isValid(): Boolean = projectId.isNotBlank() && apiKey.isNotBlank()
    }

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        config = loadConfig(context)
        if (config == null) {
            Log.w(TAG, "$CONFIG_FILE not found; Firestore uploads disabled.")
        } else if (!config!!.isValid()) {
            Log.w(TAG, "$CONFIG_FILE is empty; Firestore uploads disabled.")
        } else {
            Log.d(TAG, "Firestore uploads configured for project ${config!!.projectId}")
        }
    }

    fun syncLog(log: TestLog?) {
        val cfg = config
        if (log == null || cfg == null || !cfg.isValid()) return
        if (log.id <= 0) {
            Log.w(TAG, "Skipping Firestore upload for invalid log id ${log.id}")
            return
        }
        executor.execute {
            uploadLog(cfg, log)
        }
    }

    private fun uploadLog(cfg: FirebaseConfig, log: TestLog) {
        val documentId = log.id.toString()
        val urlString = String.format(DOCUMENT_URL_TEMPLATE, cfg.projectId, cfg.collection, documentId, cfg.apiKey)
        val payload = buildPayload(log)

        try {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "PATCH"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }

            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.w(TAG, "Firestore upload failed ($responseCode): $errorText")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Firestore upload failed", t)
        }
    }

    private fun buildPayload(log: TestLog): String {
        val fields = JSONObject()
            .put("id", integerField(log.id))
            .put("packageName", stringField(log.packageName))
            .put("status", stringField(log.status))
            .put("startTime", integerField(log.startTime))
            .put("touchCount", integerField(log.touchCount.toLong()))

        if (log.endTime != null) {
            fields.put("endTime", integerField(log.endTime))
        } else {
            fields.put("endTime", nullField())
        }

        if (!log.errorMessage.isNullOrBlank()) {
            fields.put("errorMessage", stringField(log.errorMessage!!))
        } else {
            fields.put("errorMessage", nullField())
        }

        return JSONObject().put("fields", fields).toString()
    }

    private fun stringField(value: String): JSONObject = JSONObject().put("stringValue", value)
    private fun integerField(value: Long): JSONObject = JSONObject().put("integerValue", value.toString())
    private fun nullField(): JSONObject = JSONObject().put("nullValue", JSONObject.NULL)

    private fun loadConfig(context: Context): FirebaseConfig? {
        return try {
            context.assets.open(CONFIG_FILE).use { stream ->
                val json = JSONObject(stream.bufferedReader().use { it.readText() })
                FirebaseConfig(
                    projectId = json.optString("projectId", "").trim(),
                    apiKey = json.optString("apiKey", "").trim(),
                    collection = json.optString("collection", "autoTestLogs").trim().let {
                        if (it.isEmpty()) "autoTestLogs" else it
                    }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load $CONFIG_FILE", e)
            null
        }
    }
}
