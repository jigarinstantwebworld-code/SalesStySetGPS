package com.styset.sales.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stops")
data class StopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mappingId: Long=0,
    val tripId: String? = null,
    val salesExecutiveId: String? = null   ,
    val name: String?,
    val locationLabel: String?,
    val address: String?,
    val phone: String?,
    val imageUri: String?,
    val lat: Double,
    val lng: Double,
    val startWallTimeMillis: Long,
    val endWallTimeMillis: Long?,
    val durationElapsedMinutes: Long,
    val startElapsedRealtimeMillis: Long,
    val endElapsedRealtimeMillis: Long?,
    val lastSyncTime: Long = 0,
    val letter: String? = null  // Add this field
)

