package com.example.salesstysetgps.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stops")
data class StopEntity(
    @PrimaryKey val id: Long,
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
    val endElapsedRealtimeMillis: Long?
)


