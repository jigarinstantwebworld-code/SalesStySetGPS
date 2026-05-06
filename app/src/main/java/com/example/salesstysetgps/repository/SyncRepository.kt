package com.example.salesstysetgps.repository

import android.content.Context
import android.util.Log
import com.example.salesstysetgps.data.api.ApiService
import com.example.salesstysetgps.data.local.AppDatabase
import com.example.salesstysetgps.data.local.StopDao
import com.example.salesstysetgps.data.local.SyncDao
import com.example.salesstysetgps.data.network.NetworkClient
import com.example.salesstysetgps.models.Resource
import com.example.salesstysetgps.models.SyncDataEntity
import com.example.salesstysetgps.util.PreferenceManager
import com.example.salesstysetgps.workers.StopRequestData
import com.example.salesstysetgps.workers.StopSyncRequest
import com.example.salesstysetgps.workers.SyncResponse
import com.example.salesstysetgps.workers.SyncResult
import kotlinx.coroutines.Dispatchers
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



    private val syncDao = AppDatabase.get(context).syncDao()
    private val stopDao = AppDatabase.get(context).stopDao()
    private val preferenceManager = PreferenceManager.getInstance(context)



    suspend fun performSync(): Flow<Resource<SyncResponse>> = flow {
        emit(Resource.Loading())
        val currentTime = System.currentTimeMillis()
//        insertOldTestData()
        try {
            // Get last successful sync time
            val lastSync = syncDao.getLastSuccessfulSync(API_NAME)
            val fromTime = lastSync?.lastSyncedTime ?: getStartOfDayUTC()
            val toTime = currentTime

            Log.d("SyncManager", "📅 Sync range: ${formatTime(fromTime)} to ${formatTime(toTime)}")

            // Get stops in time range
            val stopsToSync = stopDao.getStopsInTimeRanges(fromTime, toTime)

            Log.d("SyncManager", "📦 Found ${stopsToSync.size} stops to sync")

            // ✅ CHECK: If no stops to sync, skip API call
            if (stopsToSync.isEmpty()) {
                Log.d("SyncManager", "⏭️ No stops to sync, skipping API call")

                // Save sync record without calling API
                val syncRecord = SyncDataEntity(
                    apiName = API_NAME,
                    lastSyncedTime = toTime,
                    status = 1,  // Mark as success since nothing to sync
                    errorMessage = null
                )
                syncDao.insertSyncRecord(syncRecord)


                // Return success with zero count
                emit(Resource.Success(
                    SyncResponse(
                        success = 1,
                        message = "No stops to sync",
                        syncedCount = 0,
                        serverTime = currentTime
                    )
                ))
                return@flow
            }


            // Convert to API format (only if there are stops)
            val validStops = stopsToSync.filter {
                it.endWallTimeMillis != null && it.endWallTimeMillis > 0
            }

            Log.d("SyncManager", "Valid stops after filter: ${validStops.size}")

            val stopRequestList = validStops.map { stop ->
                StopRequestData(
                    name = stop.name ?: "Unknown Shop",
                    coordinates = "${stop.lat},${stop.lng}",
                    startTime = stop.startWallTimeMillis,
                    endTime = stop.endWallTimeMillis!!, // safe now
                    durationMinutes = stop.durationElapsedMinutes.toLong(),
                    address = stop.address,
                    phone = stop.phone
                )
            }

            // Get IDs from preferences
            val salesExecutiveId = preferenceManager.getSalesExecutiveId()
            val tripId = preferenceManager.getTripId()

            // Validate trip exists
            if (salesExecutiveId == 0 || tripId.isNullOrBlank()) {
                val errorMsg = "No active trip found. Please start a trip first."
                Log.e("SyncManager", "❌ $errorMsg")

                // Save failed sync record
                val syncRecord = SyncDataEntity(
                    apiName = API_NAME,
                    lastSyncedTime = currentTime,
                    status = 0,
                    errorMessage = errorMsg
                )
                syncDao.insertSyncRecord(syncRecord)

                emit(Resource.Error(errorMsg))
                return@flow
            }

            // Create request
            val request = StopSyncRequest(
                places = stopRequestList,
                salesExecutiveId = salesExecutiveId.toString(),
                tripId = tripId
            )

            // Log request details
            logRequestDetails(request)

            // Call API - only when there are stops
            val response = withContext(Dispatchers.IO) {
                val apiService = NetworkClient.apiService
                apiService.syncStops(request)
            }

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

            // Success case
            if (response.success == 1) {
                emit(Resource.Success(response))
            } else {
                // API returned error (success = 0)
                emit(Resource.Error(response.message ?: "Failed to sync stops"))
            }

        } catch (e: SocketTimeoutException) {
            Log.e("SyncManager", "❌ Connection timeout: ${e.message}")

            val syncRecord = SyncDataEntity(
                apiName = API_NAME,
                lastSyncedTime = currentTime,
                status = 0,
                errorMessage = "Connection timeout. Please try again."
            )
            syncDao.insertSyncRecord(syncRecord)

            emit(Resource.Error("Connection timeout. Please try again."))

        } catch (e: HttpException) {
            Log.e("SyncManager", "❌ HTTP error: ${e.message}")

            val errorMessage = parseHttpError(e)

            val syncRecord = SyncDataEntity(
                apiName = API_NAME,
                lastSyncedTime = currentTime,
                status = 0,
                errorMessage = errorMessage
            )
            syncDao.insertSyncRecord(syncRecord)

            emit(Resource.Error(errorMessage))

        } catch (e: IOException) {
            Log.e("SyncManager", "❌ Network error: ${e.message}")

            val syncRecord = SyncDataEntity(
                apiName = API_NAME,
                lastSyncedTime = currentTime,
                status = 0,
                errorMessage = "Network error: ${e.message ?: "Please check your connection"}"
            )
            syncDao.insertSyncRecord(syncRecord)

            emit(Resource.Error("Network error: ${e.message ?: "Please check your connection"}"))

        } catch (e: Exception) {
            Log.e("SyncManager", "❌ Sync error: ${e.message}")
            e.printStackTrace()

            val syncRecord = SyncDataEntity(
                apiName = API_NAME,
                lastSyncedTime = currentTime,
                status = 0,
                errorMessage = e.message ?: "Unknown error"
            )
            syncDao.insertSyncRecord(syncRecord)

            emit(Resource.Error("Error: ${e.message ?: "Unknown error"}"))
        } finally {

            cleanupOldRecords()
        }
    }

    private fun logRequestDetails(request: StopSyncRequest) {
        if (request.places.isEmpty()) {
            Log.d("SyncManager", "📤 SYNC REQUEST: Empty array (no stops to sync)")
        } else {
            Log.d("SyncManager", "📤 SYNC REQUEST: ${request.places.size} stops")
        }
        Log.d("SyncManager", "   Sales Executive: ${request.salesExecutiveId}")
        Log.d("SyncManager", "   Trip ID: ${request.tripId}")
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