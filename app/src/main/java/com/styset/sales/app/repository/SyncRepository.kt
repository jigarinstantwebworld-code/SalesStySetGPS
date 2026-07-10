package com.styset.sales.app.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.GsonBuilder
import com.styset.sales.app.data.local.AppDatabase
import com.styset.sales.app.data.network.NetworkClient
import com.styset.sales.app.models.Resource
import com.styset.sales.app.models.SyncDataEntity
import com.styset.sales.app.ui.MediaStoreFileLogger
import com.styset.sales.app.util.PreferenceManager
import com.styset.sales.app.workers.StopRequestData
import com.styset.sales.app.workers.StopSyncRequest
import com.styset.sales.app.workers.SyncResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SyncRepository(private val context: Context) {

    companion object {
        private const val API_NAME = "SYNC_STOPS"
        private const val DAYS_TO_KEEP = 7
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }
    private val placesApiService = NetworkClient.placesApiService
    private val apiService = NetworkClient.apiService

//    private val fileLogger = MediaStoreFileLogger(context)
    private var currentSyncJob: Job? = null
    @Volatile
    private var isLoggingSessionActive = false



    private val syncDao = AppDatabase.Companion.get(context).syncDao()
    private val stopDao = AppDatabase.Companion.get(context).stopDao()
    private val preferenceManager = PreferenceManager.Companion.getInstance(context)

    suspend fun performSync(): Flow<Resource<SyncResponse>> = flow {
        // Prevent multiple concurrent sync sessions
        if (isLoggingSessionActive) {
            Log.w("SyncManager", "⚠️ Sync already in progress, skipping")
            writeSyncLogEntry(System.currentTimeMillis(), "Skipped - Another sync is already in progress")
            emit(Resource.Error("Sync already in progress"))
            return@flow
        }

        isLoggingSessionActive = true



        val currentTime = System.currentTimeMillis()
        var requestJsonStr: String? = null
        try {

            emit(Resource.Loading())

//            fileLogger.log("SyncManager", "Current time: $currentTime (${Date(currentTime)})")

            val lastSync = syncDao.getLastSuccessfulSync(API_NAME)
            val fromTime = lastSync?.lastSyncedTime ?: getStartOfDayUTC()
            val toTime = currentTime


//            fileLogger.log("SyncManager", "From time (epoch): $fromTime")
//            fileLogger.log("SyncManager", "To time (epoch): $toTime")

            // Get stops - includes those with NULL endTime

            val stopsToSync = stopDao.getStopsInTimeRanges(fromTime, toTime)


//            fileLogger.log("SyncManager", "Stop IDs: ${stopsToSync.map { it.id }.joinToString(", ")}")
//            fileLogger.log("SyncManager", "Stop TripID: ${stopsToSync.map { it.tripId }.joinToString(", ")}")
//            fileLogger.log("SyncManager", "Stop SalesID: ${stopsToSync.map { it.salesExecutiveId }.joinToString(", ")}")

            // Log NULL endTime stops
            val nullEndTimeStops = stopsToSync.filter { it.endWallTimeMillis == null }

            // ✅ CHECK: If no stops to sync, skip API call
            if (stopsToSync.isEmpty()) {

                // ✅ Write background sync status to text file in Downloads
                writeSyncLogEntry(currentTime, "Skipped - No new stops found to sync")
                val syncRecord = SyncDataEntity(
                    apiName = API_NAME,
                    lastSyncedTime = toTime,
                    status = 1,
                    errorMessage = null
                )
                syncDao.insertSyncRecord(syncRecord)
//                fileLogger.log("SyncManager", "Sync record inserted: no stops to sync")

                emit(
                    Resource.Success(
                        SyncResponse(
                            success = 1,
                            message = "No stops to sync",
                            syncedCount = 0,
                            serverTime = currentTime
                        )
                    )
                )
//                fileLogger.log("SyncManager", "✅ Success response emitted (no stops)")
                return@flow
            }

            // Convert to API format - NO filter, include NULL endTimes
            val stopRequestList = stopsToSync.map { stop ->
                val durationMinutes: Long? = if (stop.endWallTimeMillis != null) {
                    val diff = stop.endWallTimeMillis - stop.startWallTimeMillis
                    if (diff > 0) diff / 60_000L else null
                } else {
                    null // ongoing stop — send null, not a wrong number
                }
                StopRequestData(
                    id = stop.id,
                    mappingId = stop.mappingId,
                    name = stop.name ?: "Unknown Shop",
                    coordinates = "${stop.lat},${stop.lng}",
                    startTime = stop.startWallTimeMillis,
                    endTime = stop.endWallTimeMillis,
                    durationMinutes = durationMinutes,
                    address = stop.address,
                    phone = stop.phone,
                    salesExecutiveId = stop.salesExecutiveId.toString(),
                    tripId = stop.tripId.toString()
                )
            }
//            fileLogger.log("SyncManager", "Stop request list created with ${stopRequestList.size} items")

            // Get IDs from preferences

            val salesExecutiveId = preferenceManager.getSalesExecutiveId()
            val tripId = preferenceManager.getTripId()

            // Validate trip exists
            if (salesExecutiveId == 0 || tripId.isNullOrBlank()) {
                val errorMsg = "No active trip found. Please start a trip first."

                val syncRecord = SyncDataEntity(
                    apiName = API_NAME,
                    lastSyncedTime = currentTime,
                    status = 0,
                    errorMessage = errorMsg
                )
                syncDao.insertSyncRecord(syncRecord)
//                fileLogger.log("SyncManager", "Error sync record inserted")

                emit(Resource.Error(errorMsg))
//                fileLogger.log("SyncManager", "❌ Error response emitted")
                return@flow
            }

            // Create request with all stops (including NULLs)
            val request = StopSyncRequest(
                places = stopRequestList,
//                salesExecutiveId = salesExecutiveId.toString(),
//                tripId = tripId
            )

            // JSON serialization test
            val gson = GsonBuilder().serializeNulls().create()
            val testJson = gson.toJson(request)
            requestJsonStr = testJson
//            fileLogger.log("SyncManager", "=== Manual Serialization Test ===")
//            fileLogger.log("SyncManager", "Request JSON: $testJson")

            // Pretty print JSON for better readability
            try {
                val prettyJson = gson.toJson(request)
//                fileLogger.log("SyncManager", "=== Pretty Printed JSON ===")
//                fileLogger.log("SyncManager", prettyJson)
            } catch (e: Exception) {
//                fileLogger.log("SyncManager", "Failed to pretty print JSON: ${e.message}")
            }

            // 🔍 DEBUG: Log the serialized JSON before sending
            logSerializedRequest(request)
//            fileLogger.log("SyncManager", "logSerializedRequest() called")

            // Verify NULL endTime is in the JSON
            verifyNullEndTimeInRequest(stopRequestList)

            val apiStartTime = System.currentTimeMillis()
            val response = withContext(Dispatchers.IO) {
                val apiService = NetworkClient.apiService
                placesApiService.syncStops(request)
            }
            val apiDuration = System.currentTimeMillis() - apiStartTime


            // Log response
            logResponseDetails(response)

            // Save sync record

            val syncRecord = SyncDataEntity(
                apiName = API_NAME,
                lastSyncedTime = toTime,
                status = if (response.success == 1) 1 else 0,
                errorMessage = if (response.success == 0) response.message else null
            )
            syncDao.insertSyncRecord(syncRecord)

            // After successful sync, update local records
            if (response.success == 1) {
                nullEndTimeStops.forEach { stop ->
                    stopDao.updateStopSyncTime(stop.id, currentTime)
                }
                
                // ✅ Write background sync status to text file in Downloads
                writeSyncLogEntry(currentTime, "Success - Synced ${stopsToSync.size} stops successfully", requestJsonStr)
                emit(Resource.Success(response))
            } else {
                // ✅ Write background sync status to text file in Downloads
                writeSyncLogEntry(currentTime, "Failed - API error: ${response.message}", requestJsonStr)
                
                emit(Resource.Error(response.message ?: "Failed to sync stops"))
//                fileLogger.log("SyncManager", "${response.message}")
            }

        } catch (e: Exception) {
            writeSyncLogEntry(currentTime, "Failed - Exception: ${e.message}", requestJsonStr)
            emit(Resource.Error("Error: ${e.message ?: "Unknown error"}"))
        } finally {
            isLoggingSessionActive = false
        }
    }
    private fun logSerializedRequest(request: StopSyncRequest) {
        try {
            val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
            val json = gson.toJson(request)
            Log.d("SyncManager", "📤 Request JSON with nulls:")
            Log.d("SyncManager", json)
        } catch (e: Exception) {
            Log.e("SyncManager", "Failed to serialize request: ${e.message}")
        }
    }

    private fun verifyNullEndTimeInRequest(stopRequestList: List<StopRequestData>) {
        stopRequestList.forEachIndexed { index, stop ->
            if (stop.endTime == null) {
                Log.d("SyncManager", "✅ Stop $index has endTime = null (will be sent as null)")
            } else {
                Log.d("SyncManager", "Stop $index has endTime = ${stop.endTime}")
            }
        }
    }

    private fun logRequestDetails(request: StopSyncRequest) {
        if (request.places.isEmpty()) {
            Log.d("SyncManager", "📤 SYNC REQUEST: Empty array (no stops to sync)")
        } else {
            Log.d("SyncManager", "📤 SYNC REQUEST: ${request.places.size} stops")
        }
    }

    private fun logResponseDetails(response: SyncResponse) {
        Log.d("SyncManager", "📥 SYNC RESPONSE: success=${response.success}, message=${response.message}")
    }





    private fun getStartOfDayUTC(): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    suspend fun getAllSuccessfulSyncRecords(): List<SyncDataEntity> {
        return syncDao.getAllSuccessfulSyncRecords()
    }

    suspend fun getLatestSuccessfulSyncRecord(): SyncDataEntity? {
        return syncDao.getLatestSuccessfulSyncRecord()
    }

    // ✅ Get formatted last sync time

    suspend fun cleanupNow(): Int {
        return try {
            val cutoffTime = System.currentTimeMillis() - (DAYS_TO_KEEP * MILLIS_PER_DAY)
            val oldCount = syncDao.getOldRecordsCount(cutoffTime)

            if (oldCount > 0) {
                val deletedCount = syncDao.deleteRecordsOlderThan(cutoffTime)
                Log.d("TAG", "🧹 Cleaned up $deletedCount old records (older than $DAYS_TO_KEEP days)")
                deletedCount
            } else {
                Log.d("TAG", "✅ No old records to clean up")
                0
            }
        } catch (e: Exception) {
            Log.e("TAG", "Cleanup failed: ${e.message}")
            0
        }
    }

    suspend fun cleanupOnAppStart() {
        Log.d("TAG", "Running cleanup on app start")
        cleanupNow()
    }




    suspend fun cleanupOldRecords() {
        val cutoffTime = System.currentTimeMillis() - (DAYS_TO_KEEP * MILLIS_PER_DAY)
        val deletedCount = syncDao.deleteRecordsOlderThan(cutoffTime)

        if (deletedCount > 0) {
            Log.d("SyncCleanup", "🗑️ Deleted $deletedCount old sync records (older than $DAYS_TO_KEEP days)")
        }
    }

    private fun isAppInBackground(): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val appProcesses = activityManager.runningAppProcesses ?: return true
            val packageName = context.packageName
            for (appProcess in appProcesses) {
                if (appProcess.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    appProcess.processName == packageName) {
                    return false
                }
            }
            true
        } catch (e: Exception) {
            true
        }
    }

    private fun getOrCreateSyncLogUri(): Uri? {
        val resolver = context.contentResolver
        val fileName = "sync_history_log.txt"
        
        // Try to find if the file already exists in MediaStore
        val projection = arrayOf(android.provider.MediaStore.MediaColumns._ID)
        val selection = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(fileName, android.os.Environment.DIRECTORY_DOWNLOADS + "/")
        
        val collectionUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            android.provider.MediaStore.Files.getContentUri("external")
        }

        try {
            resolver.query(collectionUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
                    val id = cursor.getLong(idCol)
                    val existingUri = android.content.ContentUris.withAppendedId(collectionUri, id)
                    return existingUri
                }
            }
        } catch (e: Exception) {
            Log.e("SyncManager", "Error querying existing sync history file: ${e.message}")
        }

        // If it does not exist, create it new
        try {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val uri = resolver.insert(collectionUri, contentValues)
            if (uri != null) {
                // Write log file header
                resolver.openOutputStream(uri)?.use { outputStream ->
                    val header = "=== SYNC HISTORY LOG ===\nCreated: ${Date()}\n\n"
                    outputStream.write(header.toByteArray())
                }
                return uri
            }
        } catch (e: Exception) {
            Log.e("SyncManager", "Failed to create new sync history file: ${e.message}")
        }
        return null
    }

    private fun writeSyncLogEntry(currentTime: Long, reason: String, requestJson: String? = null) {
        try {
            val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm:ss a", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            }
            val indianTime = sdf.format(Date(currentTime))
            val isBackground = isAppInBackground()
            
            var logLine = "[$indianTime] [Background: $isBackground] Result: $reason\n"
            if (requestJson != null) {
                logLine += "Request JSON: $requestJson\n"
            }
            logLine += "--------------------------------------------------\n"
            
            val logUri = getOrCreateSyncLogUri()
            if (logUri != null) {
                val resolver = context.contentResolver
                resolver.openOutputStream(logUri, "wa")?.use { outputStream -> // "wa" for append
                    outputStream.write(logLine.toByteArray())
                }
                Log.d("SyncManager", "✅ Logged sync entry to sync_history_log.txt")
            }
        } catch (e: Exception) {
            Log.e("SyncManager", "Failed to write sync log entry: ${e.message}")
        }
    }


}