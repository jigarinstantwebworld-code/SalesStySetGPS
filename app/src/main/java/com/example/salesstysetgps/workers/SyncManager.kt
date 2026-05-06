package com.example.salesstysetgps.workers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.salesstysetgps.SyncWorker
import com.example.salesstysetgps.models.Resource
import com.example.salesstysetgps.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume


class SyncManager(private val context: Context) {

    private val syncRepository = SyncRepository(context)

    suspend fun performSync(): Flow<Resource<SyncResponse>> {
        return syncRepository.performSync()
    }

    suspend fun performSyncDirect(): SyncResult {
        var result = SyncResult(0, "Unknown error", 0)

        syncRepository.performSync().collect { resource ->
            when (resource) {
                is Resource.Success -> {
                    if (resource.data?.success == 1) {
                        result = SyncResult(1, resource.data.message, resource.data.syncedCount)
                    } else {
                        result = SyncResult(1, resource.data?.message ?: "Sync failed", 0)
                    }
                }

                is Resource.Error -> {
                    result = SyncResult(0, resource.message ?: "Sync failed", 0)
                }

                else -> { /* Loading or Idle */
                }
            }
        }

        return result
    }

    fun performImmediateSync() {
        Log.d("Sync", "🚀 Starting immediate sync...")

        val workManager = WorkManager.getInstance(context)

        // Cancel any existing immediate sync to avoid duplicates
        workManager.cancelUniqueWork("immediate_sync")

        val immediateSyncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(0, TimeUnit.MILLISECONDS) // Start immediately
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag("immediate_sync")
            .build()

        workManager.enqueueUniqueWork(
            "immediate_sync",
            ExistingWorkPolicy.REPLACE,
            immediateSyncRequest
        )
    }

    suspend fun performImmediateSyncAndAwait(): SyncResult {
        Log.d("Sync", "🚀 Starting immediate sync and awaiting result...")

        return suspendCancellableCoroutine { continuation ->
            val workManager = WorkManager.getInstance(context)

            // Cancel any existing immediate sync to avoid duplicates
            workManager.cancelUniqueWork("immediate_sync")

            val immediateSyncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(0, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .addTag("immediate_sync")
                .build()

            // Observe the work info
            val workInfoFlow = workManager.getWorkInfoByIdLiveData(immediateSyncRequest.id)

            val observer = object : androidx.lifecycle.Observer<WorkInfo?> {
                override fun onChanged(workInfo: WorkInfo?) {
                    if (workInfo == null) return

                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            val outputData = workInfo.outputData
                            val success = outputData.getBoolean("sync_success", false)
                            val message = outputData.getString("sync_message") ?: ""
                            val syncedCount = outputData.getInt("synced_count", 0)

                            workManager.getWorkInfoByIdLiveData(immediateSyncRequest.id).removeObserver(this)

                            if (continuation.isActive) {
                                continuation.resume(SyncResult(
                                    success = if (success) 1 else 0,
                                    message = message,
                                    syncedCount = syncedCount
                                ))
                            }
                            Log.d("Sync", "✅ Immediate sync completed: success=$success, message=$message, count=$syncedCount")
                        }

                        WorkInfo.State.FAILED -> {
                            workManager.getWorkInfoByIdLiveData(immediateSyncRequest.id).removeObserver(this)

                            if (continuation.isActive) {
                                continuation.resume(SyncResult(0, "Sync failed", 0))
                            }
                            Log.e("Sync", "❌ Immediate sync failed")
                        }

                        WorkInfo.State.CANCELLED -> {
                            workManager.getWorkInfoByIdLiveData(immediateSyncRequest.id).removeObserver(this)

                            if (continuation.isActive) {
                                continuation.resume(SyncResult(0, "Sync cancelled", 0))
                            }
                            Log.w("Sync", "⚠️ Immediate sync cancelled")
                        }

                        else -> {
                            // Still running
                            Log.d("Sync", "⏳ Sync in progress... state: ${workInfo.state}")
                        }
                    }
                }
            }

            workManager.getWorkInfoByIdLiveData(immediateSyncRequest.id).observeForever(observer)

            // Enqueue the work
            workManager.enqueueUniqueWork(
                "immediate_sync",
                ExistingWorkPolicy.REPLACE,
                immediateSyncRequest
            )

            // Set timeout after 60 seconds
            continuation.invokeOnCancellation {
                workManager.getWorkInfoByIdLiveData(immediateSyncRequest.id).removeObserver(observer)
                workManager.cancelWorkById(immediateSyncRequest.id)
                Log.w("Sync", "⏰ Sync timeout or cancelled")
            }

            // Optional: Set a timeout
            if (!continuation.isActive) {
                workManager.getWorkInfoByIdLiveData(immediateSyncRequest.id).removeObserver(observer)
            }
        }
    }
}

data class SyncResult(
    val success: Int,
    val message: String,
    val syncedCount: Int
)


data class StopSyncRequest(
    val places: List<StopRequestData>,
    val salesExecutiveId: String,
    val tripId: String
)

data class StopRequestData(
    val name: String,
    val coordinates: String,  // Format: "latitude,longitude"
    val startTime: Long,
    val endTime: Long?,
    val durationMinutes: Long,
    val address: String?,
    val phone: String?
)

data class SyncResponse(
    val success: Int,
    val message: String,
    val syncedCount: Int,
    val serverTime: Long
)