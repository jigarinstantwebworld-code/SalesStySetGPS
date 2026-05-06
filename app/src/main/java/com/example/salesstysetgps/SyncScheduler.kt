package com.example.salesstysetgps

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.salesstysetgps.models.Resource
import com.example.salesstysetgps.workers.SyncManager
import com.example.salesstysetgps.workers.SyncResult


class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("SyncWorker", "🔄 Background sync starting...")

            val syncManager = SyncManager(applicationContext)
            var syncResult: SyncResult? = null

            syncManager.performSync().collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        syncResult = if (resource.data?.success == 1) {
                            SyncResult(
                                success = 1,
                                message = resource.data.message,
                                syncedCount = resource.data.syncedCount
                            )
                        } else {
                            // ✅ This captures API error message (success = 0)
                            SyncResult(
                                success = 0,
                                message = resource.data?.message ?: "Sync failed",
                                syncedCount = 0
                            )
                        }
                    }

                    is Resource.Error -> {
                        // ✅ This captures network/exception errors
                        syncResult = SyncResult(
                            success = 0,
                            message = resource.message ?: "Unknown error",
                            syncedCount = 0
                        )
                    }

                    else -> { /* Loading */
                    }
                }
            }

            val finalResult = syncResult

            return if (finalResult?.success == 1) {
                // Success case
                val outputData = workDataOf(
                    "syncedCount" to finalResult.syncedCount,
                    "message" to finalResult.message,
                    "success" to true,
                    "details" to if (finalResult.syncedCount > 0)
                        "Successfully synced ${finalResult.syncedCount} stops"
                    else
                        "No new stops to sync"
                )
                Result.success(outputData)
            } else {
                // ✅ FAILURE CASE - Capture the error message from API
                val errorMessage = finalResult?.message ?: "Sync failed"
                Log.e("SyncWorker", "❌ Background sync failed: $errorMessage")

                val outputData = workDataOf(
                    "syncedCount" to 0,
                    "message" to errorMessage,  // ✅ This will show "places array is required..."
                    "success" to false,
                    "details" to "API returned error: $errorMessage"
                )
                // ✅ Return FAILURE instead of RETRY for bad request (400)
                Result.failure(outputData)
            }

        } catch (e: Exception) {
            Log.e("SyncWorker", "❌ Background sync error: ${e.message}")
            val outputData = workDataOf(
                "syncedCount" to 0,
                "message" to (e.message ?: "Unknown error"),
                "success" to false
            )
            Result.failure(outputData)
        }
    }

}