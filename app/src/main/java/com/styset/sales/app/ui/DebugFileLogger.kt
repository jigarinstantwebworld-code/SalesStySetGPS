package com.styset.sales.app.ui

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaStoreFileLogger(private val context: Context) {
    private var currentLogUri: Uri? = null
    private var currentFileName: String? = null
    private var isLoggingActive = false
    private var sessionId: String? = null

    fun startNewLog(): Uri {
        // If already active, don't create new one
        if (isLoggingActive) {
            Log.w("MediaStoreFileLogger", "⚠️ Log already active, returning existing URI: $currentLogUri")
            return currentLogUri ?: Uri.EMPTY
        }

        return try {
            sessionId = System.currentTimeMillis().toString()
            currentFileName = "app_sync_log_${sessionId}.txt"

            Log.d("MediaStoreFileLogger", "📁 Creating new log file: $currentFileName")

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, currentFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val resolver = context.contentResolver
            currentLogUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (currentLogUri == null) {
                // Fallback to app-specific directory if MediaStore fails
                currentLogUri = createFallbackLogFile()
            }

            if (currentLogUri != null) {
                // Write header
                writeToLog("=== SYNC LOG STARTED ===")
                writeToLog("Session ID: ${sessionId}")
                writeToLog("Time: ${Date()}")
                writeToLog("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                writeToLog("Android Version: ${Build.VERSION.RELEASE}")
                writeToLog("App Version: ${getAppVersion()}")
                writeToLog("=".repeat(80))

                isLoggingActive = true
                Log.d("MediaStoreFileLogger", "✅ Log file created successfully: $currentLogUri")
            } else {
                Log.e("MediaStoreFileLogger", "❌ Failed to create log file")
            }

            currentLogUri ?: Uri.EMPTY
        } catch (e: Exception) {
            Log.e("MediaStoreFileLogger", "Failed to start new log: ${e.message}")
            Uri.EMPTY
        }
    }

    private fun createFallbackLogFile(): Uri? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val logFile = File(downloadsDir, currentFileName)
            Uri.fromFile(logFile)
        } catch (e: Exception) {
            Log.e("MediaStoreFileLogger", "Failed to create fallback log: ${e.message}")
            null
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun log(tag: String, message: String) {
        // Only log if session is active
        if (!isLoggingActive) {
            Log.w("MediaStoreFileLogger", "⚠️ Logging inactive, ignoring log: $message")
            return
        }

        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logEntry = "$timestamp [$tag] $message\n"
        writeToLog(logEntry)

        // Also log to Android's Logcat for debugging
        Log.d(tag, message)
    }

    private fun writeToLog(text: String) {
        if (currentLogUri == null || currentLogUri == Uri.EMPTY) {
            Log.e("MediaStoreFileLogger", "Cannot write to log: URI is null or empty")
            return
        }

        try {
            currentLogUri?.let { uri ->
                context.contentResolver.openOutputStream(uri, "wa")?.use { outputStream ->
                    outputStream.write(text.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
            }
        } catch (e: Exception) {
            Log.e("MediaStoreFileLogger", "Failed to write to log: ${e.message}")
            writeToFallbackFile(text)
        }
    }

    private fun writeToFallbackFile(text: String) {
        try {
            if (currentLogUri?.scheme == "file") {
                val file = File(currentLogUri?.path ?: return)
                file.appendText(text)
            }
        } catch (e: Exception) {
            Log.e("MediaStoreFileLogger", "Failed to write to fallback file: ${e.message}")
        }
    }

    fun finishLog() {
        if (!isLoggingActive) {
            Log.d("MediaStoreFileLogger", "No active log to finish")
            return
        }

        try {
            writeToLog("\n=== SYNC LOG ENDED ===")
            writeToLog("=".repeat(80))
            writeToLog("Log file saved to Downloads folder")
            writeToLog("Total logs in this session captured successfully")

            isLoggingActive = false
            Log.d("MediaStoreFileLogger", "✅ Log session finished: $currentFileName")
        } catch (e: Exception) {
            Log.e("MediaStoreFileLogger", "Failed to finish log: ${e.message}")
        }
    }

    fun getLogUri(): Uri? = currentLogUri?.takeIf { it != Uri.EMPTY }

    fun isActive(): Boolean = isLoggingActive

    fun getCurrentFileName(): String? = currentFileName
}

class StoragePermissionHelper(private val activity: Activity) {

    companion object {
        const val REQUEST_CODE_STORAGE = 1001
    }

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - Environment.isExternalStorageManager()
            Environment.isExternalStorageManager()
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${activity.packageName}")
                activity.startActivityForResult(intent, REQUEST_CODE_STORAGE)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivityForResult(intent, REQUEST_CODE_STORAGE)
            }
        } else {
            @Suppress("DEPRECATION")
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CODE_STORAGE
            )
        }
    }
}