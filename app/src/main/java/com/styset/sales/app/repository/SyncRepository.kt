package com.styset.sales.app.repository

import android.content.Context
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

    private val fileLogger = MediaStoreFileLogger(context)
    private var currentSyncJob: Job? = null
    @Volatile
    private var isLoggingSessionActive = false



    private val syncDao = AppDatabase.Companion.get(context).syncDao()
    private val stopDao = AppDatabase.Companion.get(context).stopDao()
    private val preferenceManager = PreferenceManager.Companion.getInstance(context)



//    suspend fun performSync(): Flow<Resource<SyncResponse>> = flow {
//        emit(Resource.Loading())
//        val currentTime = System.currentTimeMillis()
//
//        try {
//            // Get last successful sync time
//            val lastSync = syncDao.getLastSuccessfulSync(API_NAME)
//            val fromTime = lastSync?.lastSyncedTime ?: getStartOfDayUTC()
//            val toTime = currentTime
//
//            Log.d("SyncManager", "📅 Sync range: ${formatTime(fromTime)} to ${formatTime(toTime)}")
//
//            // Get stops - includes those with NULL endTime
//            val stopsToSync = stopDao.getStopsInTimeRanges(fromTime, toTime)
//
//            Log.d("SyncManager", "📦 Found ${stopsToSync.size} stops to sync")
//
//            // Log NULL endTime stops
//            val nullEndTimeStops = stopsToSync.filter { it.endWallTimeMillis == null }
//            if (nullEndTimeStops.isNotEmpty()) {
//                Log.d("SyncManager", "⚠️ Found ${nullEndTimeStops.size} stops with NULL endTime")
//                nullEndTimeStops.forEach { stop ->
//                    Log.d("SyncManager", "Stop with NULL endTime found:")
//                    Log.d("SyncManager", "  - ID: ${stop.id}")
//                    Log.d("SyncManager", "  - mappingId: ${stop.mappingId}")
//                    Log.d("SyncManager", "  - endTime: ${stop.endWallTimeMillis}")  // Should print "null"
//                    Log.d("SyncManager", "  - endTime is null? ${stop.endWallTimeMillis == null}")  // Should print "true"
//                }
//            }
//
//            // ✅ CHECK: If no stops to sync, skip API call
//            if (stopsToSync.isEmpty()) {
//                Log.d("SyncManager", "⏭️ No stops to sync, skipping API call")
//
//                val syncRecord = SyncDataEntity(
//                    apiName = API_NAME,
//                    lastSyncedTime = toTime,
//                    status = 1,
//                    errorMessage = null
//                )
//                syncDao.insertSyncRecord(syncRecord)
//
//                emit(
//                    Resource.Success(
//                        SyncResponse(
//                            success = 1,
//                            message = "No stops to sync",
//                            syncedCount = 0,
//                            serverTime = currentTime
//                        )
//                    ))
//                return@flow
//            }
//
//            // Convert to API format - NO filter, include NULL endTimes
//            val stopRequestList = stopsToSync.map { stop ->
//                StopRequestData(
//                    id = stop.id,
//                    mappingId = stop.mappingId,
//                    name = stop.name ?: "Unknown Shop",
//                    coordinates = "${stop.lat},${stop.lng}",
//                    startTime = stop.startWallTimeMillis,
//                    endTime = stop.endWallTimeMillis,  // Can be NULL
//                    durationMinutes = if (stop.endWallTimeMillis == null) null else stop.durationElapsedMinutes,
//                    address = stop.address,
//                    phone = stop.phone
//                )
//            }
//
//            // Get IDs from preferences
//            val salesExecutiveId = preferenceManager.getSalesExecutiveId()
//            val tripId = preferenceManager.getTripId()
//
//            // Validate trip exists
//            if (salesExecutiveId == 0 || tripId.isNullOrBlank()) {
//                val errorMsg = "No active trip found. Please start a trip first."
//                Log.e("SyncManager", "❌ $errorMsg")
//
//                val syncRecord = SyncDataEntity(
//                    apiName = API_NAME,
//                    lastSyncedTime = currentTime,
//                    status = 0,
//                    errorMessage = errorMsg
//                )
//                syncDao.insertSyncRecord(syncRecord)
//
//                emit(Resource.Error(errorMsg))
//                return@flow
//            }
//
//            // Create request with all stops (including NULLs)
//            val request = StopSyncRequest(
//                places = stopRequestList,
//                salesExecutiveId = salesExecutiveId.toString(),
//                tripId = tripId
//            )
//            stopRequestList.forEach { stop ->
//                Log.d("SyncManager", "=== Stop Debug ===")
//                Log.d("SyncManager", "ID: ${stop.id}")
//                Log.d("SyncManager", "endTime: ${stop.endTime}")
//                Log.d("SyncManager", "durationMinutes: ${stop.durationMinutes}")
//                Log.d("SyncManager", "Should duration be null? ${stop.endTime == null}")
//            }
//            val gson = GsonBuilder().serializeNulls().create()
//            val testJson = gson.toJson(request)
//            Log.d("SyncManager", "=== Manual Serialization Test ===")
//            Log.d("SyncManager", "Request JSON: $testJson")
//
//            // 🔍 DEBUG: Log the serialized JSON before sending
//            logSerializedRequest(request)
//
//            // Verify NULL endTime is in the JSON
//            verifyNullEndTimeInRequest(stopRequestList)
//
//            // Call API
//            val response = withContext(Dispatchers.IO) {
//                val apiService = NetworkClient.apiService
//                placesApiService.syncStops(request)
//            }
//
//            // Log response
//            logResponseDetails(response)
//
//            // Save sync record
//            val syncRecord = SyncDataEntity(
//                apiName = API_NAME,
//                lastSyncedTime = toTime,
//                status = if (response.success == 1) 1 else 0,
//                errorMessage = if (response.success == 0) response.message else null
//            )
//            syncDao.insertSyncRecord(syncRecord)
//
//            // After successful sync, update local records
//            if (response.success == 1) {
//                nullEndTimeStops.forEach { stop ->
//                    stopDao.updateStopSyncTime(stop.id, currentTime)
//                }
//                emit(Resource.Success(response))
//            } else {
//                emit(Resource.Error(response.message ?: "Failed to sync stops"))
//            }
//
//        } catch (e: Exception) {
//            Log.e("SyncManager", "❌ Sync error: ${e.message}")
//            emit(Resource.Error("Error: ${e.message ?: "Unknown error"}"))
//        }
//    }

    suspend fun performSync(): Flow<Resource<SyncResponse>> = flow {
        // Prevent multiple concurrent sync sessions
        if (isLoggingSessionActive) {
            Log.w("SyncManager", "⚠️ Sync already in progress, skipping")
            emit(Resource.Error("Sync already in progress"))
            return@flow
        }

        isLoggingSessionActive = true

        // Start log session ONCE at the beginning
        val logUri = fileLogger.startNewLog()
        Log.d("SyncManager", "📁 Single log file created: $logUri")

        fun logToFileAndLogcat(tag: String, message: String) {
            Log.d(tag, message)
            fileLogger.log(tag, message)
        }

        fun logErrorToFileAndLogcat(tag: String, message: String) {
            Log.e(tag, message)
            fileLogger.log(tag, message)
        }

        try {
            logToFileAndLogcat("SyncManager", "🔄 Sync flow started")
            emit(Resource.Loading())

            val currentTime = System.currentTimeMillis()
            fileLogger.log("SyncManager", "Current time: $currentTime (${Date(currentTime)})")

            // Get last successful sync time
            logToFileAndLogcat("SyncManager", "📊 Fetching last successful sync time for API: $API_NAME")
            val lastSync = syncDao.getLastSuccessfulSync(API_NAME)
            val fromTime = lastSync?.lastSyncedTime ?: getStartOfDayUTC()
            val toTime = currentTime

            logToFileAndLogcat("SyncManager", "📅 Sync range: ${formatTime(fromTime)} to ${formatTime(toTime)}")
            fileLogger.log("SyncManager", "From time (epoch): $fromTime")
            fileLogger.log("SyncManager", "To time (epoch): $toTime")

            // Get stops - includes those with NULL endTime
            logToFileAndLogcat("SyncManager", "🔍 Querying stops in time range")
            val stopsToSync = stopDao.getStopsInTimeRanges(fromTime, toTime)

            logToFileAndLogcat("SyncManager", "📦 Found ${stopsToSync.size} stops to sync")
            fileLogger.log("SyncManager", "Stop IDs: ${stopsToSync.map { it.id }.joinToString(", ")}")
            fileLogger.log("SyncManager", "Stop TripID: ${stopsToSync.map { it.tripId }.joinToString(", ")}")
            fileLogger.log("SyncManager", "Stop SalesID: ${stopsToSync.map { it.salesExecutiveId }.joinToString(", ")}")

            // Log NULL endTime stops
            val nullEndTimeStops = stopsToSync.filter { it.endWallTimeMillis == null }
            if (nullEndTimeStops.isNotEmpty()) {
                logToFileAndLogcat("SyncManager", "⚠️ Found ${nullEndTimeStops.size} stops with NULL endTime")
                fileLogger.log("SyncManager", "=== NULL EndTime Stops Details ===")
                nullEndTimeStops.forEach { stop ->
                    fileLogger.log("SyncManager", "Stop with NULL endTime found:")
                    fileLogger.log("SyncManager", "  - ID: ${stop.id}")
                    fileLogger.log("SyncManager", "  - mappingId: ${stop.mappingId}")
                    fileLogger.log("SyncManager", "  - name: ${stop.name}")
                    fileLogger.log("SyncManager", "  - endTime: ${stop.endWallTimeMillis}")
                    fileLogger.log("SyncManager", "  - endTime is null? ${stop.endWallTimeMillis == null}")
                    fileLogger.log("SyncManager", "  - startTime: ${stop.startWallTimeMillis}")
                    fileLogger.log("SyncManager", "  - coordinates: ${stop.lat},${stop.lng}")
                }
                fileLogger.log("SyncManager", "=================================")
            } else {
                logToFileAndLogcat("SyncManager", "✅ No NULL endTime stops found")
            }

            // ✅ CHECK: If no stops to sync, skip API call
            if (stopsToSync.isEmpty()) {
                logToFileAndLogcat("SyncManager", "⏭️ No stops to sync, skipping API call")

                val syncRecord = SyncDataEntity(
                    apiName = API_NAME,
                    lastSyncedTime = toTime,
                    status = 1,
                    errorMessage = null
                )
                syncDao.insertSyncRecord(syncRecord)
                fileLogger.log("SyncManager", "Sync record inserted: no stops to sync")

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
                fileLogger.log("SyncManager", "✅ Success response emitted (no stops)")
                return@flow
            }

            // Convert to API format - NO filter, include NULL endTimes
            logToFileAndLogcat("SyncManager", "🔄 Converting ${stopsToSync.size} stops to API format")
            val stopRequestList = stopsToSync.map { stop ->
                StopRequestData(
                    id = stop.id,
                    mappingId = stop.mappingId,
                    name = stop.name ?: "Unknown Shop",
                    coordinates = "${stop.lat},${stop.lng}",
                    startTime = stop.startWallTimeMillis,
                    endTime = stop.endWallTimeMillis,
                    durationMinutes = if (stop.endWallTimeMillis == null) null else stop.durationElapsedMinutes,
                    address = stop.address,
                    phone = stop.phone,
                    salesExecutiveId = stop.salesExecutiveId.toString(),
                    tripId = stop.tripId.toString()
                )
            }
            fileLogger.log("SyncManager", "Stop request list created with ${stopRequestList.size} items")

            // Get IDs from preferences
            logToFileAndLogcat("SyncManager", "🔑 Fetching sales executive ID and trip ID from preferences")
            val salesExecutiveId = preferenceManager.getSalesExecutiveId()
            val tripId = preferenceManager.getTripId()
            fileLogger.log("SyncManager", "Sales Executive ID: $salesExecutiveId")
            fileLogger.log("SyncManager", "Trip ID: $tripId")

            // Validate trip exists
            if (salesExecutiveId == 0 || tripId.isNullOrBlank()) {
                val errorMsg = "No active trip found. Please start a trip first."
                logErrorToFileAndLogcat("SyncManager", "❌ $errorMsg")
                fileLogger.log("SyncManager", "Validation failed: salesExecutiveId=$salesExecutiveId, tripId=$tripId")

                val syncRecord = SyncDataEntity(
                    apiName = API_NAME,
                    lastSyncedTime = currentTime,
                    status = 0,
                    errorMessage = errorMsg
                )
                syncDao.insertSyncRecord(syncRecord)
                fileLogger.log("SyncManager", "Error sync record inserted")

                emit(Resource.Error(errorMsg))
                fileLogger.log("SyncManager", "❌ Error response emitted")
                return@flow
            }

            // Create request with all stops (including NULLs)
            logToFileAndLogcat("SyncManager", "📦 Creating sync request with ${stopRequestList.size} stops")
            val request = StopSyncRequest(
                places = stopRequestList,
//                salesExecutiveId = salesExecutiveId.toString(),
//                tripId = tripId
            )

            // Detailed stop debugging
            fileLogger.log("SyncManager", "=== Detailed Stop Debugging ===")
            stopRequestList.forEach { stop ->
                fileLogger.log("SyncManager", "Stop Details:")
                fileLogger.log("SyncManager", "  ID: ${stop.id}")
                fileLogger.log("SyncManager", "  mappingId: ${stop.mappingId}")
                fileLogger.log("SyncManager", "  name: ${stop.name}")
                fileLogger.log("SyncManager", "  coordinates: ${stop.coordinates}")
                fileLogger.log("SyncManager", "  startTime: ${stop.startTime}")
                fileLogger.log("SyncManager", "  endTime: ${stop.endTime}")
                fileLogger.log("SyncManager", "  durationMinutes: ${stop.durationMinutes}")
                fileLogger.log("SyncManager", "  address: ${stop.address}")
                fileLogger.log("SyncManager", "  phone: ${stop.phone}")
                fileLogger.log("SyncManager", "  ---")
            }

            // JSON serialization test
            val gson = GsonBuilder().serializeNulls().create()
            val testJson = gson.toJson(request)
            fileLogger.log("SyncManager", "=== Manual Serialization Test ===")
            fileLogger.log("SyncManager", "Request JSON: $testJson")

            // Pretty print JSON for better readability
            try {
                val prettyJson = gson.toJson(request)
                fileLogger.log("SyncManager", "=== Pretty Printed JSON ===")
                fileLogger.log("SyncManager", prettyJson)
            } catch (e: Exception) {
                fileLogger.log("SyncManager", "Failed to pretty print JSON: ${e.message}")
            }

            // 🔍 DEBUG: Log the serialized JSON before sending
            logSerializedRequest(request)
            fileLogger.log("SyncManager", "logSerializedRequest() called")

            // Verify NULL endTime is in the JSON
            verifyNullEndTimeInRequest(stopRequestList)
            fileLogger.log("SyncManager", "verifyNullEndTimeInRequest() completed")

            // Call API
            logToFileAndLogcat("SyncManager", "🌐 Calling API to sync stops")
            fileLogger.log("SyncManager", "API URL: ${NetworkClient.apiService.javaClass.simpleName}")

            val apiStartTime = System.currentTimeMillis()
            val response = withContext(Dispatchers.IO) {
                val apiService = NetworkClient.apiService
                placesApiService.syncStops(request)
            }
            val apiDuration = System.currentTimeMillis() - apiStartTime

            fileLogger.log("SyncManager", "API call completed in ${apiDuration}ms")

            // Log response
            logResponseDetails(response)
            fileLogger.log("SyncManager", "=== Response Details ===")
            fileLogger.log("SyncManager", "Success: ${response.success}")
            fileLogger.log("SyncManager", "Message: ${response.message}")
            fileLogger.log("SyncManager", "Synced Count: ${response.syncedCount}")
            fileLogger.log("SyncManager", "Server Time: ${response.serverTime}")

            // Save sync record
            logToFileAndLogcat("SyncManager", "💾 Saving sync record to database")
            val syncRecord = SyncDataEntity(
                apiName = API_NAME,
                lastSyncedTime = toTime,
                status = if (response.success == 1) 1 else 0,
                errorMessage = if (response.success == 0) response.message else null
            )
            syncDao.insertSyncRecord(syncRecord)
            fileLogger.log("SyncManager", "Sync record saved with status: ${syncRecord.status}")

            // After successful sync, update local records
            if (response.success == 1) {
                logToFileAndLogcat("SyncManager", "✅ Sync successful, updating ${nullEndTimeStops.size} stops with NULL endTime")
                nullEndTimeStops.forEach { stop ->
                    stopDao.updateStopSyncTime(stop.id, currentTime)
                    fileLogger.log("SyncManager", "Updated sync time for stop ID: ${stop.id}")
                }
                emit(Resource.Success(response))
                fileLogger.log("SyncManager", "✅ Success response emitted")
            } else {
                logErrorToFileAndLogcat("SyncManager", "❌ Sync failed: ${response.message}")
                emit(Resource.Error(response.message ?: "Failed to sync stops"))
                fileLogger.log("SyncManager", "❌ Error response emitted")
            }

        } catch (e: Exception) {
            logErrorToFileAndLogcat("SyncManager", "❌ Sync error: ${e.message}")
            fileLogger.log("SyncManager", "Exception type: ${e.javaClass.simpleName}")
            fileLogger.log("SyncManager", "Stack trace:")
            e.stackTrace?.take(20)?.forEach { stackElement ->
                fileLogger.log("SyncManager", "  at $stackElement")
            }
            emit(Resource.Error("Error: ${e.message ?: "Unknown error"}"))
            fileLogger.log("SyncManager", "❌ Error response emitted due to exception")
        } finally {
            fileLogger.log("SyncManager", "🏁 Sync flow completed")
            // Finish log session ONCE at the end
            fileLogger.finishLog()
            fileLogger.log("SyncManager", "📄 Log file saved to Downloads folder")
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

    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }



    private fun getStartOfDayUTC(): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun parseHttpError(e: HttpException): String {
        return try {
            val errorBody = e.response()?.errorBody()?.string()
            // Try to parse error message from response if needed
            "Server error: ${e.code()}"
        } catch (ex: Exception) {
            "Server error: ${e.code()}"
        }
    }

    suspend fun getAllSuccessfulSyncRecords(): List<SyncDataEntity> {
        return syncDao.getAllSuccessfulSyncRecords()
    }

    suspend fun getLatestSuccessfulSyncRecord(): SyncDataEntity? {
        return syncDao.getLatestSuccessfulSyncRecord()
    }

    // ✅ Get formatted last sync time
    suspend fun getFormattedSyncHistory(): String {
        val records = getAllSuccessfulSyncRecords()
        if (records.isEmpty()) {
            return "No sync records found"
        }

        val dateFormat = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault())
        return records.joinToString("\n") { record ->
            "• ${record.apiName}: ${dateFormat.format(Date(record.lastSyncedTime))}"
        }
    }

    suspend fun getSyncHistoryList(): List<Pair<String, String>> {
        val records = getAllSuccessfulSyncRecords()
        val dateFormat = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault())
        return records.map { record ->
            Pair(record.apiName, dateFormat.format(Date(record.lastSyncedTime)))
        }
    }

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


}