package com.styset.sales.app.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_data")
data class SyncDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val apiName: String,           // e.g., "SYNC_STOPS", "SYNC_ROUTES"
    val lastSyncedTime: Long,      // Timestamp when sync was attempted
    val status: Int,            // "SUCCESS" or "FAIL"
    val errorMessage: String? = null // Error message if failed
){
    fun isWithinLastWeek(): Boolean {
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        return lastSyncedTime >= oneWeekAgo
    }
}
